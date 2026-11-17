// The window's link against the real daemon: `boxdd run --listen 127.0.0.1:<port>` (development mode) started here,
// a ready config with a proxy on 127.0.0.1 and a direct outbound, a request through it. Stage 2b of docs/desktop:
// the button starts the config, the status and the rates are seen. Runs where NIKO_BOXDD names the daemon's
// executable (CI: on Windows and on Linux); skipped otherwise.
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:niko_desktop/daemon/daemon.dart';
import 'package:niko_desktop/daemon/endpoint.dart';
import 'package:niko_desktop/state/connection_controller.dart';

Future<int> freePort() async {
  final socket = await ServerSocket.bind(InternetAddress.loopbackIPv4, 0);
  final port = socket.port;
  await socket.close();
  return port;
}

Future<bool> listening(int port) async {
  try {
    final socket = await Socket.connect(InternetAddress.loopbackIPv4, port, timeout: const Duration(seconds: 1));
    socket.destroy();
    return true;
  } catch (_) {
    return false;
  }
}

Future<void> until(FutureOr<bool> Function() condition, String what, {int seconds = 30}) async {
  final deadline = DateTime.now().add(Duration(seconds: seconds));
  while (!await condition()) {
    if (DateTime.now().isAfter(deadline)) fail('timed out waiting for $what');
    await Future<void>.delayed(const Duration(milliseconds: 50));
  }
}

void main() {
  final boxdd = Platform.environment['NIKO_BOXDD'] ?? '';

  test('the real daemon: start a config, see the status and the traffic, stop; the daemon killed and back',
      skip: boxdd.isEmpty ? 'NIKO_BOXDD is not set' : false, timeout: const Timeout(Duration(minutes: 3)), () async {
    final work = await Directory.systemTemp.createTemp('niko-boxdd-');
    final daemonPort = await freePort();
    final proxyPort = await freePort();
    final log = <String>[];
    Process? process;

    Future<void> startDaemon() async {
      // boxdd takes INVOCATION_ID for "started by systemd" and refuses --listen as a service would; a CI runner
      // and a terminal of a systemd session both carry it, and this child is no service
      final environment = Map.of(Platform.environment)..remove('INVOCATION_ID');
      final started = await Process.start(boxdd, ['run', '--listen', '127.0.0.1:$daemonPort', '-D', work.path],
          environment: environment, includeParentEnvironment: false);
      process = started;
      started.stdout.transform(utf8.decoder).transform(const LineSplitter()).listen(log.add);
      started.stderr.transform(utf8.decoder).transform(const LineSplitter()).listen(log.add);
      await until(() => listening(daemonPort), 'the daemon to listen');
    }

    // what the proxy is asked for: 256 KB from a server of this test's own
    final origin = await HttpServer.bind(InternetAddress.loopbackIPv4, 0);
    final body = List<int>.filled(256 * 1024, 0x6e);
    origin.listen((request) async {
      request.response.headers.contentLength = body.length;
      request.response.add(body);
      await request.response.close();
    });

    var config = jsonEncode({
      'log': {'level': 'info'},
      'inbounds': [
        {'type': 'mixed', 'tag': 'in', 'listen': '127.0.0.1', 'listen_port': proxyPort},
      ],
      'outbounds': [
        {'type': 'direct', 'tag': 'direct'},
      ],
    });
    final controller = ConnectionController(
      daemon: GrpcDaemon.dev(DevEndpoint.parse('127.0.0.1:$daemonPort')!),
      loadConfig: () async => config,
      retry: const Duration(milliseconds: 300),
      trafficInterval: const Duration(milliseconds: 200),
    );
    addTearDown(() async {
      controller.dispose();
      process?.kill();
      await origin.close(force: true);
      // ignore: avoid_print
      print('--- the daemon said\n${log.join('\n')}');
      try {
        await work.delete(recursive: true);
      } catch (_) {
        // Windows may still hold a file of the killed daemon
      }
    });

    await startDaemon();
    controller.attach();
    await until(() => controller.link == LinkPhase.ready, 'the link');
    expect(controller.daemonVersion, isNotEmpty);
    // ignore: avoid_print
    print('daemon ${controller.daemonVersion}');
    expect(controller.service.phase, ServicePhase.idle);
    expect(await listening(proxyPort), isFalse);

    // a config the core refuses: its words, nothing started
    final good = config;
    config = '{"inbounds": [{"type": "no-such-inbound"}]}';
    await controller.toggle();
    expect(controller.actionError, isNotEmpty);
    // ignore: avoid_print
    print('a refused config: ${controller.actionError}');
    expect(controller.service.phase, anyOf(ServicePhase.idle, ServicePhase.fatal));
    expect(await listening(proxyPort), isFalse);

    // the button
    config = good;
    await controller.toggle();
    expect(controller.actionError, isEmpty);
    await until(() => controller.service.phase == ServicePhase.started, 'the core up');
    await until(() => controller.startedAt != null, 'the start time');
    expect(DateTime.now().difference(controller.startedAt!).inSeconds.abs(), lessThan(60));
    expect(await listening(proxyPort), isTrue);

    // a request through the proxy the config opened
    final client = HttpClient()..findProxy = (_) => 'PROXY 127.0.0.1:$proxyPort';
    final request = await client.getUrl(Uri.parse('http://127.0.0.1:${origin.port}/'));
    final response = await request.close();
    final received = await response.fold<int>(0, (total, chunk) => total + chunk.length);
    client.close(force: true);
    expect(response.statusCode, 200);
    expect(received, body.length);

    // the traffic the window shows: the total since the start, and a rate while it went
    var peak = 0;
    void watch() {
      final down = controller.traffic?.down ?? 0;
      if (down > peak) peak = down;
    }

    controller.addListener(watch);
    await until(() => (controller.traffic?.downTotal ?? 0) >= body.length, 'the traffic total');
    // a second request, so that a rate falls into an interval that is watched from its start
    final again = HttpClient()..findProxy = (_) => 'PROXY 127.0.0.1:$proxyPort';
    final second = await (await again.getUrl(Uri.parse('http://127.0.0.1:${origin.port}/'))).close();
    await second.drain<void>();
    again.close(force: true);
    await until(() => peak > 0, 'a rate above zero');
    await until(() => (controller.traffic?.downTotal ?? 0) >= 2 * body.length, 'the total of both');
    controller.removeListener(watch);
    // ignore: avoid_print
    print('traffic: total ↓ ${controller.traffic!.downTotal} ↑ ${controller.traffic!.upTotal}, peak ↓ $peak B/s');

    // the button again
    await controller.toggle();
    await until(() => controller.service.phase == ServicePhase.idle, 'the core down');
    expect(controller.traffic, isNull);
    await until(() async => !await listening(proxyPort), 'the proxy port closed');

    // the daemon killed: the window says so and keeps asking; the daemon back: found
    process!.kill(ProcessSignal.sigkill);
    await process!.exitCode;
    await until(() => controller.link == LinkPhase.lost, 'the loss');
    // ignore: avoid_print
    print('the daemon killed: ${controller.linkError}');
    expect(controller.linkError, isNotEmpty);
    await startDaemon();
    await until(() => controller.link == LinkPhase.ready, 'the link again');
    expect(controller.service.phase, ServicePhase.idle);
  });
}

// The window's link against the real daemon: `boxdd run --listen 127.0.0.1:<port>` (development mode) started here,
// a ready config with a proxy on 127.0.0.1 and a direct outbound, a request through it. Stage 2b of docs/desktop:
// the button starts the config, the status and the rates are seen; then the core's log and its connections, one of
// which is closed from here (D-LOG-01, D-CONN-01). Runs where NIKO_BOXDD names the daemon's executable (CI: on
// Windows and on Linux); skipped otherwise.
import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:niko_desktop/daemon/daemon.dart';
import 'package:niko_desktop/daemon/endpoint.dart';
import 'package:niko_desktop/state/connection_controller.dart';
import 'package:niko_desktop/state/connections_feed.dart';
import 'package:niko_desktop/state/log_feed.dart';

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
    // /hold answers when this lets it: a connection that stays open to be seen, and closed
    final release = Completer<void>();
    origin.listen((request) async {
      if (request.uri.path == '/hold') {
        request.response.bufferOutput = false;
        request.response.write('held');
        await request.response.flush();
        await release.future;
        await request.response.close().catchError((Object _) {});
        return;
      }
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
    final logFeed = LogFeed(controller);
    final connections = ConnectionsFeed(controller, interval: const Duration(milliseconds: 200));
    addTearDown(() async {
      if (!release.isCompleted) release.complete();
      logFeed.dispose();
      connections.dispose();
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

    // every change of the link, with what the test was doing then: a drop is told where it happened
    var step = 'the start';
    var lastLink = controller.link;
    controller.addListener(() {
      if (controller.link != lastLink) {
        // ignore: avoid_print
        print('link ${lastLink.name} -> ${controller.link.name} during $step'
            '${controller.linkError.isEmpty ? '' : ': ${controller.linkError}'}');
        lastLink = controller.link;
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

    step = 'a refused config';
    // a config the core refuses: its words, nothing started
    final good = config;
    config = '{"inbounds": [{"type": "no-such-inbound"}]}';
    await controller.toggle();
    expect(controller.actionError, isNotEmpty);
    // ignore: avoid_print
    print('a refused config: ${controller.actionError}');
    expect(controller.service.phase, anyOf(ServicePhase.idle, ServicePhase.fatal));
    expect(await listening(proxyPort), isFalse);

    step = 'the start';
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

    step = 'the log';
    // the core's log: what the daemon kept of this start, without a terminal's colour codes
    logFeed.open();
    await until(() => logFeed.lines.isNotEmpty, 'the log');
    expect(logFeed.lines.any((line) => line.text.contains('sing-box started')), isTrue,
        reason: logFeed.lines.map((line) => line.text).join('\n'));
    expect(logFeed.lines.any((line) => line.text.contains('\x1b')), isFalse);
    // ignore: avoid_print
    print('log: ${logFeed.lines.length} lines, the last: ${logFeed.lines.last.text}');
    final before = logFeed.lines.length;
    step = 'clearing the log';
    await logFeed.clear();
    expect(logFeed.notice, LogNotice.none, reason: logFeed.noticeDetail);
    // the daemon's reports: none after a clean start, and the calls answer
    final reports = await controller.daemon!.reports();
    // ignore: avoid_print
    print('reports of the daemon: ${reports.length}');
    expect(reports.where((r) => !r.read), isEmpty);
    await until(() => logFeed.lines.length < before, 'the cleared log');

    step = 'the connections';
    // the connections: one held open is listed with where it goes, and closing it from here ends it
    connections.show();
    final holder = HttpClient()..findProxy = (_) => 'PROXY 127.0.0.1:$proxyPort';
    final held = await (await holder.getUrl(Uri.parse('http://127.0.0.1:${origin.port}/hold'))).close();
    final ended = held.drain<void>().then((_) => 'ended', onError: (Object _) => 'broken');
    await until(() => connections.open.any((c) => c.destination == '127.0.0.1:${origin.port}'), 'the held connection');
    final shown = connections.open.firstWhere((c) => c.destination == '127.0.0.1:${origin.port}');
    expect(shown.network, 'tcp');
    expect(shown.outbound, 'direct');
    expect(DateTime.now().difference(shown.createdAt).inSeconds.abs(), lessThan(60));
    // ignore: avoid_print
    print('connection: ${shown.destination} ${shown.network} protocol "${shown.protocol}" process "${shown.process}" '
        'rule "${shown.rule}" -> ${shown.outbound} (${shown.outboundType}), ↓ ${shown.down} ↑ ${shown.up}');
    step = 'closing a connection';
    await connections.close(shown.id);
    expect(connections.error, isEmpty);
    await until(() => !connections.open.any((c) => c.id == shown.id), 'the connection gone from the list');
    // ignore: avoid_print
    print('the closed connection ${await ended.timeout(const Duration(seconds: 10))} for the program that held it');
    holder.close(force: true);

    step = 'the stop';
    // the button again
    await controller.toggle();
    await until(() => controller.service.phase == ServicePhase.idle, 'the core down');
    await until(() => connections.open.isEmpty, 'no connections of a stopped core');
    expect(controller.traffic, isNull);
    await until(() async => !await listening(proxyPort), 'the proxy port closed');

    step = 'the end';
    // The link never dropped: the window lets the connections stream end at the stop rather than cancel it. A
    // cancel crossing the daemon's end made package:http2 close the whole connection ("HTTP/2 clients cannot
    // receive HEADER_FRAMEs", seen here against boxdd before the feed let the stream end).
    expect(controller.drops, 0, reason: 'a working link dropped');

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

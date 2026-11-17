import 'package:flutter_test/flutter_test.dart';
import 'package:niko_desktop/daemon/daemon.dart';
import 'package:niko_desktop/daemon/endpoint.dart';
import 'package:niko_desktop/gen/daemon/started_service.pb.dart';
import 'package:niko_desktop/state/connection_controller.dart';

import 'fake_daemon_server.dart';

/// Waits for [condition] with real time: these tests talk gRPC over a socket of 127.0.0.1.
Future<void> until(bool Function() condition, String what) async {
  final deadline = DateTime.now().add(const Duration(seconds: 10));
  while (!condition()) {
    if (DateTime.now().isAfter(deadline)) fail('timed out waiting for $what');
    await Future<void>.delayed(const Duration(milliseconds: 10));
  }
}

void main() {
  late FakeDaemonServer server;
  late ConnectionController controller;

  ConnectionController link(int port, {Future<String> Function()? config}) => ConnectionController(
        daemon: GrpcDaemon.dev(DevEndpoint.parse('127.0.0.1:$port')!),
        loadConfig: config ?? () async => '{"log": {}}',
        retry: const Duration(milliseconds: 100),
        trafficInterval: const Duration(milliseconds: 50),
      );

  setUp(() async {
    server = FakeDaemonServer();
    await server.serve();
  });

  tearDown(() async {
    controller.dispose();
    await server.shutdown();
  });

  test('the button starts the config, the status and the rates follow, a second press stops', () async {
    controller = link(server.port)..attach();
    expect(controller.link, LinkPhase.connecting);
    await until(() => controller.link == LinkPhase.ready, 'the daemon');
    expect(controller.daemonVersion, '1.14.0-fake');
    expect(controller.service.phase, ServicePhase.idle);

    final seen = <ServicePhase>[];
    controller.addListener(() {
      if (seen.isEmpty || seen.last != controller.service.phase) seen.add(controller.service.phase);
    });
    await controller.toggle();
    expect(server.configs, ['{"log": {}}']);
    await until(() => controller.service.phase == ServicePhase.started, 'the core up');
    await until(() => controller.startedAt != null, 'the start time');
    expect(controller.startedAt!.toUtc(), server.startedAt);
    // 4096 bytes down and 1024 up every 50 ms: as bytes a second
    await until(() => (controller.traffic?.down ?? 0) > 0, 'traffic');
    expect(controller.traffic!.down, 4096 * 20);
    expect(controller.traffic!.up, 1024 * 20);
    expect(controller.traffic!.downTotal, greaterThanOrEqualTo(4096));

    await controller.toggle();
    expect(server.stops, 1);
    await until(() => controller.service.phase == ServicePhase.idle, 'the core down');
    expect(controller.traffic, isNull);
    expect(controller.startedAt, isNull);
    // idle first: the press itself is told before the daemon answers it
    expect(seen, [
      ServicePhase.idle,
      ServicePhase.starting,
      ServicePhase.started,
      ServicePhase.stopping,
      ServicePhase.idle,
    ]);
  });

  test('a config the daemon refuses: its words, and nothing started', () async {
    server.startError = 'decode config at line 1: unknown field "outbound"';
    controller = link(server.port)..attach();
    await until(() => controller.link == LinkPhase.ready, 'the daemon');
    await controller.toggle();
    expect(controller.actionError, 'decode config at line 1: unknown field "outbound"');
    expect(controller.busy, isFalse);
    expect(controller.service.phase, ServicePhase.idle);
    // the next press tries again and clears the error
    server.startError = null;
    await controller.toggle();
    expect(controller.actionError, isEmpty);
    await until(() => controller.service.phase == ServicePhase.started, 'the core up');
  });

  test('a config that cannot be read is said, and the daemon is not asked', () async {
    controller = link(server.port, config: () async => throw const FormatException('no such file'))..attach();
    await until(() => controller.link == LinkPhase.ready, 'the daemon');
    await controller.toggle();
    expect(controller.actionError, contains('no such file'));
    expect(server.configs, isEmpty);
  });

  test('a start that fails in the core is the status FATAL with its reason', () async {
    controller = link(server.port)..attach();
    await until(() => controller.link == LinkPhase.ready, 'the daemon');
    server.push(ServiceStatus_Type.FATAL, 'start service: listen tcp 127.0.0.1:2080: bind: address already in use');
    await until(() => controller.service.phase == ServicePhase.fatal, 'the failure');
    expect(controller.service.error, contains('address already in use'));
  });

  test('a daemon that goes away is asked again, and found when it is back', () async {
    controller = link(server.port)..attach();
    await until(() => controller.link == LinkPhase.ready, 'the daemon');
    await controller.toggle();
    await until(() => controller.service.phase == ServicePhase.started, 'the core up');
    final port = server.port;
    await server.shutdown();
    await until(() => controller.link == LinkPhase.lost, 'the loss');
    expect(controller.linkError, isNotEmpty);
    // nothing is claimed about a core that cannot be asked
    expect(controller.service.phase, ServicePhase.idle);
    expect(controller.traffic, isNull);
    // a press does nothing meanwhile
    await controller.toggle();
    expect(controller.busy, isFalse);

    server = FakeDaemonServer();
    await server.serve(port);
    await until(() => controller.link == LinkPhase.ready, 'the daemon again');
    expect(controller.service.phase, ServicePhase.idle);
  });

  test('no daemon at the address: lost, with a reason', () async {
    final port = server.port;
    await server.shutdown();
    controller = link(port)..attach();
    await until(() => controller.link == LinkPhase.lost, 'the refusal');
    expect(controller.linkError, isNotEmpty);
  });

  test('without a daemon there is nothing to ask', () async {
    controller = ConnectionController(daemon: null, loadConfig: null)..attach();
    expect(controller.link, LinkPhase.none);
    await controller.toggle();
    expect(controller.busy, isFalse);
  });
}

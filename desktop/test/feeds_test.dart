import 'dart:async';

import 'package:fixnum/fixnum.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:niko_desktop/daemon/daemon.dart';
import 'package:niko_desktop/daemon/endpoint.dart';
import 'package:niko_desktop/gen/daemon/started_service.pb.dart' as pb;
import 'package:niko_desktop/state/connection_controller.dart';
import 'package:niko_desktop/state/connections_feed.dart';
import 'package:niko_desktop/state/log_feed.dart';

import 'fake_daemon_server.dart';

/// What the window asked of the daemon and what came back, in order, with the time since the test began: printed
/// when a wait times out, since an order of events that only one system shows cannot be read otherwise.
final trace = <String>[];
final _clock = Stopwatch();

void note(String what) => trace.add('${_clock.elapsedMilliseconds.toString().padLeft(6)} ms  $what');

/// Waits for [condition] with real time: these tests talk gRPC over a socket of 127.0.0.1.
Future<void> until(bool Function() condition, String what) async {
  final deadline = DateTime.now().add(const Duration(seconds: 10));
  while (!condition()) {
    if (DateTime.now().isAfter(deadline)) fail('timed out waiting for $what; what happened:\n${trace.join('\n')}');
    await Future<void>.delayed(const Duration(milliseconds: 10));
  }
}

/// The real client, every call and stream event of it noted.
class TracedDaemon implements Daemon {
  TracedDaemon(this._inner);

  final Daemon _inner;

  Stream<T> _traced<T>(String name, Stream<T> stream, String Function(T) describe) {
    note('$name: asked');
    return stream.transform(StreamTransformer.fromHandlers(
      handleData: (value, sink) {
        note('$name: ${describe(value)}');
        sink.add(value);
      },
      handleError: (error, stack, sink) {
        note('$name: error ${daemonError(error)}');
        sink.addError(error, stack);
      },
      handleDone: (sink) {
        note('$name: done');
        sink.close();
      },
    ));
  }

  @override
  Future<String> version() => _inner.version();
  @override
  Stream<ServiceState> serviceStatus() => _traced('status', _inner.serviceStatus(), (s) => s.phase.name);
  @override
  Stream<Traffic> traffic(Duration interval) => _inner.traffic(interval);
  @override
  Future<DateTime> startedAt() => _inner.startedAt();
  @override
  Future<void> start(String config) {
    note('start');
    return _inner.start(config);
  }

  @override
  Future<void> stop() {
    note('stop');
    return _inner.stop();
  }

  @override
  Stream<LogBatch> logs() => _traced('log', _inner.logs(), (b) => '${b.lines.length} lines${b.reset ? ', reset' : ''}');
  @override
  Future<void> clearLogs() => _inner.clearLogs();
  @override
  Stream<ConnectionBatch> connections(Duration interval) => _traced('connections', _inner.connections(interval),
      (b) => '${b.changes.length} changes${b.reset ? ', reset' : ''}');
  @override
  Future<void> closeConnection(String id) => _inner.closeConnection(id);
  @override
  Future<void> closeAllConnections() => _inner.closeAllConnections();
  @override
  Future<void> close() => _inner.close();
}

pb.Connection connection(String id, String destination, {String domain = '', int at = 0}) => pb.Connection(
      id: id,
      network: 'tcp',
      destination: destination,
      domain: domain,
      protocol: 'tls',
      rule: 'domain_suffix=example.com',
      outbound: 'proxy',
      outboundType: 'naive',
      createdAt: Int64(DateTime.utc(2026, 11, 17, 1, 0, at).millisecondsSinceEpoch),
      uplinkTotal: Int64(100),
      downlinkTotal: Int64(1000),
      processInfo: pb.ProcessInfo(processPath: r'C:\Program Files\Browser\browser.exe'),
    );

void main() {
  late FakeDaemonServer server;
  late ConnectionController controller;

  setUp(() async {
    server = FakeDaemonServer()..onEvent = note;
    await server.serve();
    trace.clear();
    _clock
      ..reset()
      ..start();
    controller = ConnectionController(
      daemon: TracedDaemon(GrpcDaemon.dev(DevEndpoint.parse('127.0.0.1:${server.port}')!)),
      loadConfig: () async => '{}',
      retry: const Duration(milliseconds: 100),
      trafficInterval: const Duration(milliseconds: 50),
    )..attach();
    await until(() => controller.link == LinkPhase.ready, 'the daemon');
  });

  tearDown(() async {
    controller.dispose();
    await server.shutdown();
  });

  group('the log', () {
    test('what the daemon keeps, then each line; without the terminal colours; a clearing', () async {
      server.log(pb.LogLevel.INFO, '\x1b[36mINFO\x1b[0m[0000] sing-box started (0.23s)');
      final feed = LogFeed(controller);
      addTearDown(feed.dispose);
      expect(feed.lines, isEmpty);
      feed.open();
      await until(() => feed.lines.length == 1, 'the kept line');
      expect(feed.lines.single.text, 'INFO[0000] sing-box started (0.23s)');
      expect(feed.lines.single.level, LogSeverity.info);
      expect(feed.resets, 1);

      server.log(pb.LogLevel.ERROR, 'ERROR[0011] [\x1b[38;5;120m42\x1b[0m 5.0s] connection: download closed');
      await until(() => feed.lines.length == 2, 'the new line');
      expect(feed.lines.last.level, LogSeverity.error);
      expect(feed.lines.last.text, 'ERROR[0011] [42 5.0s] connection: download closed');

      await feed.clear();
      expect(server.logClears, 1);
      await until(() => feed.lines.isEmpty, 'the clearing');
      expect(feed.resets, 2);
      expect(feed.error, isEmpty);
    });

    test('keeps the last lines only', () async {
      final feed = LogFeed(controller, capacity: 3);
      addTearDown(feed.dispose);
      feed.open();
      await until(() => feed.resets == 1, 'the first batch');
      for (var i = 1; i <= 5; i++) {
        server.log(pb.LogLevel.DEBUG, 'line $i');
      }
      await until(() => feed.lines.isNotEmpty && feed.lines.last.text == 'line 5', 'the last line');
      expect(feed.lines.map((line) => line.text), ['line 3', 'line 4', 'line 5']);
    });

    test('closed: not read; opened again: the daemon\'s lines once, not twice', () async {
      final feed = LogFeed(controller);
      addTearDown(feed.dispose);
      feed.open();
      server.log(pb.LogLevel.INFO, 'one');
      await until(() => feed.lines.length == 1, 'one');
      feed.close();
      server.log(pb.LogLevel.INFO, 'two');
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(feed.lines.length, 1);
      feed.open();
      await until(() => feed.lines.length == 2, 'both');
      expect(feed.lines.map((line) => line.text), ['one', 'two']);
    });

    test('a daemon that went away and came back is read again', () async {
      final feed = LogFeed(controller);
      addTearDown(feed.dispose);
      feed.open();
      await until(() => feed.resets == 1, 'the first batch');
      final port = server.port;
      await server.shutdown();
      await until(() => controller.link == LinkPhase.lost, 'the loss');
      server = FakeDaemonServer();
      server.log(pb.LogLevel.WARN, 'after the restart');
      await server.serve(port);
      await until(() => feed.lines.isNotEmpty && feed.lines.last.text == 'after the restart', 'the new daemon\'s log');
    });
  });

  group('the connections', () {
    test('nothing is asked while the core is not running', () async {
      final feed = ConnectionsFeed(controller, interval: const Duration(milliseconds: 50));
      addTearDown(feed.dispose);
      feed.show();
      await Future<void>.delayed(const Duration(milliseconds: 150));
      expect(feed.open, isEmpty);
      expect(feed.error, isEmpty);
    });

    test('the open ones, then what opens, moves bytes and closes; closing one and all', () async {
      server.connections['a'] = connection('a', '93.184.216.34:443', domain: 'www.example.com', at: 1);
      final feed = ConnectionsFeed(controller, interval: const Duration(milliseconds: 50));
      addTearDown(feed.dispose);
      feed.show();
      await controller.toggle();
      await until(() => feed.open.length == 1, 'the open connection');
      final first = feed.open.single;
      expect(first.destination, '93.184.216.34:443');
      expect(first.domain, 'www.example.com');
      expect(first.process, r'C:\Program Files\Browser\browser.exe');
      expect((first.network, first.protocol, first.outbound, first.outboundType), ('tcp', 'tls', 'proxy', 'naive'));
      expect((first.up, first.down), (100, 1000));
      expect(first.createdAt.toUtc(), DateTime.utc(2026, 11, 17, 1, 0, 1));

      server.open(connection('b', '10.0.0.7:22', at: 2));
      await until(() => feed.open.length == 2, 'the new connection');
      // newest first
      expect(feed.open.map((c) => c.id), ['b', 'a']);

      server.move('a', 50, 4000);
      await until(() => feed.open.last.down == 5000, 'the bytes');
      expect(feed.open.last.up, 150);
      // a change of a connection this does not know is nothing
      server.move('nobody', 1, 1);

      await feed.close('b');
      expect(server.closed, ['b']);
      await until(() => feed.open.length == 1, 'the closing');
      expect(feed.open.single.id, 'a');

      await feed.closeAll();
      expect(server.closedAll, 1);
      await until(() => feed.open.isEmpty, 'all closed');
    });

    test('the core stopped: the list is empty; started again: read again', () async {
      server.connections['a'] = connection('a', '93.184.216.34:443');
      final feed = ConnectionsFeed(controller, interval: const Duration(milliseconds: 50));
      addTearDown(feed.dispose);
      feed.show();
      await controller.toggle();
      await until(() => feed.open.length == 1, 'the open connection');
      await controller.toggle();
      await until(() => controller.service.phase == ServicePhase.idle, 'the core down');
      await until(() => feed.open.isEmpty, 'the empty list');
      server.connections['c'] = connection('c', '10.0.0.9:80');
      await controller.toggle();
      await until(() => feed.open.length == 2, 'the connections of the new run');
      // ignore: avoid_print
      print('the stop and the start again, as this system ordered them:\n${trace.join('\n')}');
    });

    test('hidden: forgotten and not read', () async {
      server.connections['a'] = connection('a', '93.184.216.34:443');
      final feed = ConnectionsFeed(controller, interval: const Duration(milliseconds: 50));
      addTearDown(feed.dispose);
      feed.show();
      await controller.toggle();
      await until(() => feed.open.length == 1, 'the open connection');
      feed.hide();
      expect(feed.open, isEmpty);
      server.open(connection('b', '10.0.0.7:22'));
      await Future<void>.delayed(const Duration(milliseconds: 100));
      expect(feed.open, isEmpty);
    });
  });
}

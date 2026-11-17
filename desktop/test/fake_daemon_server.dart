import 'dart:async';

import 'package:fixnum/fixnum.dart';
import 'package:grpc/grpc.dart';
import 'package:niko_desktop/gen/daemon/managed_service.pbgrpc.dart';
import 'package:niko_desktop/gen/daemon/started_service.pbgrpc.dart';
import 'package:niko_desktop/gen/experimental/boxdd/desktop_service.pbgrpc.dart';
import 'package:protobuf/well_known_types/google/protobuf/empty.pb.dart';

/// A daemon for tests: the three services over gRPC on 127.0.0.1, answering the calls the window makes as boxdd
/// does (the first status at once, then every change; the traffic every interval) and refusing the rest.
class FakeDaemonServer {
  ServiceStatus_Type _phase = ServiceStatus_Type.IDLE;
  String _error = '';
  final _changes = StreamController<void>.broadcast();

  /// The configs the window started, and how many times it stopped.
  final configs = <String>[];
  int stops = 0;

  /// Set: StartService fails with these words, as for a config the core refuses.
  String? startError;

  /// The reports the daemon kept: crashes and memory run out, with their archives' bytes.
  final crashes = <CrashReportEntry>[];
  final outOfMemory = <OOMReportEntry>[];
  final exported = <String>[];

  /// The log the daemon keeps, and what it tells its readers: a batch of lines, or null for a clearing.
  final savedLog = <Log_Message>[];
  final _logChanges = StreamController<Log?>.broadcast();
  int logClears = 0;

  /// The connections of the running core, and their changes for the readers.
  final connections = <String, Connection>{};
  final _connectionChanges = StreamController<ConnectionEvents>.broadcast();
  final closed = <String>[];
  int closedAll = 0;

  /// The STUN servers the window tested, and the steps the daemon answers each test with (the core's numbers: the
  /// mapping keeps 1 reserved); a failed test is a final step with its error.
  final stunServers = <String>[];
  List<STUNTestProgress> stunSteps = [
    STUNTestProgress(phase: 0, externalAddr: '203.0.113.7:40312', latencyMs: 31),
    STUNTestProgress(
      phase: 3,
      externalAddr: '203.0.113.7:40312',
      latencyMs: 31,
      natMapping: 2,
      natFiltering: 3,
      natTypeSupported: true,
      isFinal: true,
    ),
  ];

  void log(LogLevel level, String message) {
    final line = Log_Message(level: level, message: message);
    savedLog.add(line);
    _logChanges.add(Log(messages: [line]));
  }

  void open(Connection connection) {
    connections[connection.id] = connection;
    _connectionChanges.add(ConnectionEvents(events: [
      ConnectionEvent(type: ConnectionEventType.CONNECTION_EVENT_NEW, id: connection.id, connection: connection),
    ]));
  }

  void move(String id, int up, int down) => _connectionChanges.add(ConnectionEvents(events: [
        ConnectionEvent(
          type: ConnectionEventType.CONNECTION_EVENT_UPDATE,
          id: id,
          uplinkDelta: Int64(up),
          downlinkDelta: Int64(down),
        ),
      ]));

  void drop(String id) {
    if (connections.remove(id) == null) return;
    _connectionChanges.add(ConnectionEvents(events: [
      ConnectionEvent(type: ConnectionEventType.CONNECTION_EVENT_CLOSED, id: id),
    ]));
  }
  final startedAt = DateTime.utc(2026, 11, 16, 20, 0, 0);

  Server? _server;
  bool _closing = false;
  int _open = 0;
  int port = 0;

  Future<void> serve([int onPort = 0]) async {
    final server = Server.create(services: [_Desktop(this), _Started(this), _Managed(this)]);
    await server.serve(address: '127.0.0.1', port: onPort);
    _server = server;
    port = server.port!;
  }

  /// The daemon gone. Its streams end first and their ends reach the clients: the package's Server.shutdown cuts
  /// the handlers of open streams without a word to the client and then waits for those streams forever.
  Future<void> shutdown() async {
    _closing = true;
    if (!_changes.isClosed) await _changes.close();
    if (!_logChanges.isClosed) await _logChanges.close();
    if (!_connectionChanges.isClosed) await _connectionChanges.close();
    while (_open > 0) {
      await Future<void>.delayed(const Duration(milliseconds: 5));
    }
    await Future<void>.delayed(const Duration(milliseconds: 50));
    await _server?.shutdown();
    _server = null;
  }

  /// Set: the status stream is refused at once, as an older daemon or a refusal would.
  bool refuseStatus = false;
  int statusAsked = 0;

  /// What the server saw, for a test's trace.
  void Function(String what) onEvent = (_) {};

  void push(ServiceStatus_Type phase, [String error = '']) {
    onEvent('server: ${phase.name}');
    _phase = phase;
    _error = error;
    if (!_changes.isClosed) _changes.add(null);
  }

  ServiceStatus get _status => ServiceStatus(status: _phase, errorMessage: _error);
}

class _Desktop extends DesktopServiceBase {
  _Desktop(this.daemon);

  final FakeDaemonServer daemon;

  @override
  Future<DaemonInfo> getDaemonInfo(ServiceCall call, Empty request) async =>
      DaemonInfo(version: '1.14.0-fake', ownership: DaemonOwnership.DAEMON_OWNERSHIP_AVAILABLE);

  @override
  Future<CrashReportList> listCrashReports(ServiceCall call, Empty request) async =>
      CrashReportList(reports: daemon.crashes);

  @override
  Future<OOMReportList> listOOMReports(ServiceCall call, Empty request) async =>
      OOMReportList(reports: daemon.outOfMemory);

  // as boxdd: a zip named after the report; the config only when asked for, never here
  @override
  Future<CrashReportArchive> exportCrashReport(ServiceCall call, CrashReportExportRequest request) async {
    if (request.withConfiguration) throw GrpcError.invalidArgument('the window never asks for the config');
    daemon.exported.add('crash ${request.name}${request.withLog ? ' with the log' : ''}');
    return CrashReportArchive(fileName: '${request.name}.zip', data: 'PK crash ${request.name}'.codeUnits);
  }

  @override
  Future<CrashReportArchive> exportOOMReport(ServiceCall call, OOMReportExportRequest request) async {
    if (request.withConfiguration) throw GrpcError.invalidArgument('the window never asks for the config');
    daemon.exported.add('memory ${request.name}${request.withLog ? ' with the log' : ''}');
    return CrashReportArchive(fileName: '${request.name}.zip', data: 'PK memory ${request.name}'.codeUnits);
  }

  @override
  Future<Empty> markCrashReportRead(ServiceCall call, CrashReportRequest request) async {
    for (final report in daemon.crashes) {
      if (report.name == request.name) report.isRead = true;
    }
    return Empty();
  }

  @override
  Future<Empty> markOOMReportRead(ServiceCall call, OOMReportRequest request) async {
    for (final report in daemon.outOfMemory) {
      if (report.name == request.name) report.isRead = true;
    }
    return Empty();
  }

  @override
  Future<Empty> startService(ServiceCall call, StartServiceRequest request) async {
    final error = daemon.startError;
    if (error != null) throw GrpcError.unknown(error);
    daemon.configs.add(request.configContent);
    daemon.push(ServiceStatus_Type.STARTING);
    await Future<void>.delayed(const Duration(milliseconds: 20));
    daemon.push(ServiceStatus_Type.STARTED);
    return Empty();
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => throw GrpcError.unimplemented();
}

class _Started extends StartedServiceBase {
  _Started(this.daemon);

  final FakeDaemonServer daemon;

  @override
  Stream<ServiceStatus> subscribeServiceStatus(ServiceCall call, Empty request) async* {
    daemon.statusAsked++;
    if (daemon.refuseStatus) throw GrpcError.unimplemented('no status here');
    daemon._open++;
    try {
      yield daemon._status;
      await for (final _ in daemon._changes.stream) {
        yield daemon._status;
      }
    } finally {
      daemon._open--;
    }
  }

  @override
  Stream<Status> subscribeStatus(ServiceCall call, SubscribeStatusRequest request) async* {
    // nanoseconds, as a Go duration
    final interval = Duration(microseconds: request.interval.toInt() ~/ 1000);
    var up = Int64(0), down = Int64(0);
    daemon._open++;
    try {
      yield Status(trafficAvailable: true);
      while (!call.isCanceled && !daemon._closing) {
        await Future<void>.delayed(interval);
        if (daemon._closing) return;
        // 4096 and 1024 bytes an interval
        up += 1024;
        down += 4096;
        yield Status(
          trafficAvailable: true,
          uplink: Int64(1024),
          downlink: Int64(4096),
          uplinkTotal: up,
          downlinkTotal: down,
        );
      }
    } finally {
      daemon._open--;
    }
  }

  @override
  Future<StartedAt> getStartedAt(ServiceCall call, Empty request) async =>
      StartedAt(startedAt: Int64(daemon.startedAt.millisecondsSinceEpoch));

  // as boxdd: what is kept first, marked as a reset; then each batch; a clearing is a reset with nothing
  @override
  Stream<Log> subscribeLog(ServiceCall call, Empty request) async* {
    daemon._open++;
    try {
      yield Log(messages: daemon.savedLog, reset: true);
      await for (final batch in daemon._logChanges.stream) {
        yield batch ?? Log(reset: true);
      }
    } finally {
      daemon._open--;
    }
  }

  @override
  Future<Empty> clearLogs(ServiceCall call, Empty request) async {
    daemon.logClears++;
    daemon.savedLog.clear();
    daemon._logChanges.add(null);
    return Empty();
  }

  // as boxdd: refused while the core is not running, ended when it stops
  @override
  Stream<ConnectionEvents> subscribeConnections(ServiceCall call, SubscribeConnectionsRequest request) async* {
    if (daemon._phase != ServiceStatus_Type.STARTED) {
      daemon.onEvent('server: connections refused at ${daemon._phase.name}');
      throw GrpcError.unknown('invalid argument');
    }
    daemon.onEvent('server: connections open');
    daemon._open++;
    final stopped = daemon._changes.stream.firstWhere((_) => daemon._phase != ServiceStatus_Type.STARTED).then(
          (_) => null,
          onError: (Object _) => null,
        );
    try {
      yield ConnectionEvents(
        events: [
          for (final connection in daemon.connections.values)
            ConnectionEvent(type: ConnectionEventType.CONNECTION_EVENT_NEW, id: connection.id, connection: connection),
        ],
        reset: true,
      );
      final changes = StreamIterator(daemon._connectionChanges.stream);
      try {
        while (true) {
          final next = await Future.any<Object?>([changes.moveNext(), stopped]);
          if (next != true) {
            daemon.onEvent('server: connections ended');
            return;
          }
          yield changes.current;
        }
      } finally {
        await changes.cancel();
      }
    } finally {
      daemon._open--;
    }
  }

  @override
  Stream<STUNTestProgress> startSTUNTest(ServiceCall call, STUNTestRequest request) async* {
    // as the daemon's waitForStarted: os.ErrInvalid while the core is not running
    if (daemon._phase != ServiceStatus_Type.STARTED) throw GrpcError.unknown('invalid argument');
    daemon.stunServers.add(request.server);
    for (final step in daemon.stunSteps) {
      yield step;
    }
  }

  @override
  Future<Empty> closeConnection(ServiceCall call, CloseConnectionRequest request) async {
    daemon.closed.add(request.id);
    daemon.drop(request.id);
    return Empty();
  }

  @override
  Future<Empty> closeAllConnections(ServiceCall call, Empty request) async {
    daemon.closedAll++;
    for (final id in daemon.connections.keys.toList()) {
      daemon.drop(id);
    }
    return Empty();
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => throw GrpcError.unimplemented();
}

class _Managed extends ManagedServiceBase {
  _Managed(this.daemon);

  final FakeDaemonServer daemon;

  @override
  Future<Empty> stopService(ServiceCall call, Empty request) async {
    daemon.stops++;
    daemon.push(ServiceStatus_Type.STOPPING);
    await Future<void>.delayed(const Duration(milliseconds: 20));
    daemon.push(ServiceStatus_Type.IDLE);
    return Empty();
  }

  @override
  dynamic noSuchMethod(Invocation invocation) => throw GrpcError.unimplemented();
}

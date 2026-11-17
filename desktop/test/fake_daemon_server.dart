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
    while (_open > 0) {
      await Future<void>.delayed(const Duration(milliseconds: 5));
    }
    await Future<void>.delayed(const Duration(milliseconds: 50));
    await _server?.shutdown();
    _server = null;
  }

  void push(ServiceStatus_Type phase, [String error = '']) {
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

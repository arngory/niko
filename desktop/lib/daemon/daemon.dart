import 'dart:async';

import 'package:fixnum/fixnum.dart';
import 'package:grpc/grpc.dart';
import 'package:protobuf/well_known_types/google/protobuf/empty.pb.dart';

import '../gen/daemon/managed_service.pbgrpc.dart';
import '../gen/daemon/started_service.pbgrpc.dart';
import '../gen/experimental/boxdd/desktop_service.pbgrpc.dart';
import 'endpoint.dart';

/// What the core is doing (the daemon's ServiceStatus).
enum ServicePhase { idle, starting, started, stopping, fatal }

class ServiceState {
  const ServiceState(this.phase, [this.error = '']);

  final ServicePhase phase;

  /// The core's own words for a start that failed ([ServicePhase.fatal]).
  final String error;
}

/// The traffic of the running service: bytes a second over the last interval, and since the start.
class Traffic {
  const Traffic({required this.up, required this.down, required this.upTotal, required this.downTotal});

  final int up;
  final int down;
  final int upTotal;
  final int downTotal;
}

/// The daemon as the window uses it. One implementation talks gRPC ([GrpcDaemon]); tests put a server behind it.
abstract interface class Daemon {
  /// The daemon's version: the first call, which also says whether it is there at all.
  Future<String> version();

  /// The service's state now, then every change. Ends or fails when the daemon goes away.
  Stream<ServiceState> serviceStatus();

  /// The traffic every [interval] while the service runs.
  Stream<Traffic> traffic(Duration interval);

  /// When the running service started.
  Future<DateTime> startedAt();

  /// Starts the core with a ready JSON config (D-MAIN-04).
  Future<void> start(String config);

  /// Stops it (D-MAIN-04).
  Future<void> stop();

  Future<void> close();
}

/// A failure of a call as a sentence for the screen: the daemon's own message where it gave one.
String daemonError(Object error) {
  if (error is GrpcError) {
    final message = error.message ?? '';
    if (error.code == StatusCode.unavailable) return message.isEmpty ? 'daemon unavailable' : message;
    return message.isEmpty ? error.codeName : message;
  }
  return error.toString();
}

class GrpcDaemon implements Daemon {
  GrpcDaemon(this._channel)
      : _desktop = DesktopServiceClient(_channel),
        _started = StartedServiceClient(_channel),
        _managed = ManagedServiceClient(_channel);

  /// The development link: plain TCP to 127.0.0.1, which [DevEndpoint] alone can name.
  factory GrpcDaemon.dev(DevEndpoint endpoint) => GrpcDaemon(ClientChannel(
        DevEndpoint.host,
        port: endpoint.port,
        options: const ChannelOptions(
          credentials: ChannelCredentials.insecure(),
          connectTimeout: Duration(seconds: 3),
        ),
      ));

  final ClientChannel _channel;
  final DesktopServiceClient _desktop;
  final StartedServiceClient _started;
  final ManagedServiceClient _managed;

  static final _short = CallOptions(timeout: const Duration(seconds: 5));
  // a start downloads rule sets and opens a TUN: the daemon answers when the core is up or has failed
  static final _long = CallOptions(timeout: const Duration(seconds: 90));

  @override
  Future<String> version() async => (await _desktop.getDaemonInfo(Empty(), options: _short)).version;

  @override
  Stream<ServiceState> serviceStatus() => _started.subscribeServiceStatus(Empty()).map((status) => ServiceState(
        switch (status.status) {
          ServiceStatus_Type.STARTING => ServicePhase.starting,
          ServiceStatus_Type.STARTED => ServicePhase.started,
          ServiceStatus_Type.STOPPING => ServicePhase.stopping,
          ServiceStatus_Type.FATAL => ServicePhase.fatal,
          _ => ServicePhase.idle,
        },
        status.errorMessage,
      ));

  @override
  Stream<Traffic> traffic(Duration interval) {
    // the interval is a Go duration: nanoseconds. The rates are bytes per interval
    final request = SubscribeStatusRequest(interval: Int64(interval.inMicroseconds) * 1000);
    final perSecond = Duration.microsecondsPerSecond / interval.inMicroseconds;
    return _started.subscribeStatus(request).map((status) => Traffic(
          up: (status.uplink.toInt() * perSecond).round(),
          down: (status.downlink.toInt() * perSecond).round(),
          upTotal: status.uplinkTotal.toInt(),
          downTotal: status.downlinkTotal.toInt(),
        ));
  }

  @override
  Future<DateTime> startedAt() async =>
      DateTime.fromMillisecondsSinceEpoch((await _started.getStartedAt(Empty(), options: _short)).startedAt.toInt());

  @override
  Future<void> start(String config) =>
      _desktop.startService(StartServiceRequest(configContent: config), options: _long);

  @override
  Future<void> stop() => _managed.stopService(Empty(), options: _long);

  @override
  Future<void> close() => _channel.shutdown();
}

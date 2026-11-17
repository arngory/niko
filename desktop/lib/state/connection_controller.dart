import 'dart:async';

import 'package:flutter/foundation.dart';

import '../daemon/daemon.dart';

/// The link to the daemon itself.
enum LinkPhase {
  /// No daemon to talk to: this build has no development link and the service is not installed.
  none,
  connecting,
  ready,

  /// It was asked and did not answer, or went away: asked again every few seconds.
  lost,
}

/// The connection as the main screen shows it: the link to the daemon, the core's state, the traffic. D-MAIN-02,
/// D-MAIN-04, D-MAIN-06.
class ConnectionController extends ChangeNotifier {
  ConnectionController({
    required Daemon? daemon,
    required this._loadConfig,
    this.retry = const Duration(seconds: 2),
    this.trafficInterval = const Duration(seconds: 1),
  })  : _daemon = daemon,
        link = daemon == null ? LinkPhase.none : LinkPhase.connecting;

  final Daemon? _daemon;
  final Future<String> Function()? _loadConfig;
  final Duration retry;
  final Duration trafficInterval;

  LinkPhase link;

  /// Why the link is [LinkPhase.lost].
  String linkError = '';
  String daemonVersion = '';

  ServiceState service = const ServiceState(ServicePhase.idle);
  Traffic? traffic;
  DateTime? startedAt;

  /// A press is being carried out: the daemon has not answered the start or the stop yet.
  bool busy = false;

  /// Why the last press failed (the config could not be read, the daemon refused it).
  String actionError = '';

  bool get hasConfig => _loadConfig != null;

  StreamSubscription<ServiceState>? _statusSubscription;
  StreamSubscription<Traffic>? _trafficSubscription;
  Timer? _retryTimer;
  bool _disposed = false;
  int _generation = 0;

  /// Starts asking the daemon; nothing to do without one.
  void attach() {
    if (_daemon == null || _disposed) return;
    unawaited(_connect());
  }

  Future<void> _connect() async {
    final daemon = _daemon!;
    final generation = ++_generation;
    try {
      final version = await daemon.version();
      if (_stale(generation)) return;
      daemonVersion = version;
      link = LinkPhase.ready;
      linkError = '';
      notifyListeners();
      _statusSubscription = daemon.serviceStatus().listen(
            _onService,
            onError: (Object error) => _lost(generation, daemonError(error)),
            onDone: () => _lost(generation, 'the daemon closed the connection'),
            cancelOnError: true,
          );
    } catch (error) {
      _lost(generation, daemonError(error));
    }
  }

  bool _stale(int generation) => _disposed || generation != _generation;

  void _lost(int generation, String why) {
    if (_stale(generation)) return;
    _generation++;
    unawaited(_statusSubscription?.cancel());
    _statusSubscription = null;
    _dropTraffic();
    link = LinkPhase.lost;
    linkError = why;
    // what the core does is not known any more: nothing is claimed about it
    service = const ServiceState(ServicePhase.idle);
    notifyListeners();
    _retryTimer?.cancel();
    _retryTimer = Timer(retry, () {
      if (!_disposed) unawaited(_connect());
    });
  }

  void _onService(ServiceState state) {
    if (_disposed) return;
    service = state;
    if (state.phase == ServicePhase.started) {
      if (_trafficSubscription == null) {
        final generation = _generation;
        _trafficSubscription = _daemon!.traffic(trafficInterval).listen(
          (value) {
            traffic = value;
            notifyListeners();
          },
          // the status stream says when the daemon is gone; the traffic's own failure only stops the numbers
          onError: (Object _) {},
          cancelOnError: true,
        );
        unawaited(_daemon.startedAt().then((value) {
          if (_stale(generation) || service.phase != ServicePhase.started) return;
          startedAt = value;
          notifyListeners();
        }, onError: (Object _) {}));
      }
    } else {
      _dropTraffic();
    }
    notifyListeners();
  }

  void _dropTraffic() {
    unawaited(_trafficSubscription?.cancel());
    _trafficSubscription = null;
    traffic = null;
    startedAt = null;
  }

  /// The connect button: starts the core with the config, or stops it. D-MAIN-04.
  Future<void> toggle() async {
    final daemon = _daemon;
    if (daemon == null || link != LinkPhase.ready || busy) return;
    busy = true;
    actionError = '';
    notifyListeners();
    try {
      switch (service.phase) {
        case ServicePhase.idle || ServicePhase.fatal:
          final load = _loadConfig;
          if (load == null) throw StateError('no config to start with');
          await daemon.start(await load());
        case ServicePhase.starting || ServicePhase.started:
          await daemon.stop();
        case ServicePhase.stopping:
          break;
      }
    } catch (error) {
      if (!_disposed) actionError = error is StateError ? error.message : daemonError(error);
    }
    if (_disposed) return;
    busy = false;
    notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _retryTimer?.cancel();
    unawaited(_statusSubscription?.cancel());
    unawaited(_trafficSubscription?.cancel());
    // the link is this controller's: its channel goes with it
    unawaited(_daemon?.close());
    super.dispose();
  }
}

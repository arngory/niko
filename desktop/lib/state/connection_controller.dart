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

  /// How many times a working link dropped: each is asked again at once, and said only if that fails too.
  int drops = 0;

  // an immediate relink was spent and the status stream has not answered since: the next drop waits [retry]
  bool _quickSpent = false;
  String daemonVersion = '';

  /// The version of the daemon's NikoService (its API); 0 for a daemon without it, or not asked yet.
  int nikoApi = 0;

  /// NikoService's first version that has what the button connects with without a config: Connect in a mode, the
  /// computer's settings with the mode chosen (D-NAV-02).
  static const connectApi = 7;

  ServiceState service = const ServiceState(ServicePhase.idle);
  Traffic? traffic;
  DateTime? startedAt;

  /// A press is being carried out: the daemon has not answered the start or the stop yet.
  bool busy = false;

  /// Why the last press failed (the config could not be read, the daemon refused it).
  String actionError = '';

  bool get hasConfig => _loadConfig != null;

  /// The button connects the selected profile through NikoService (Connect), with no config of the window's.
  bool get connectsProfile => _loadConfig == null && nikoApi >= connectApi;

  /// The button has something to start: a config, or the daemon's selected profile.
  bool get canStart => hasConfig || connectsProfile;

  /// The mode the core was last started in by [connectsProfile] (D-NAV-02); null while none was.
  ConnectMode? startedMode;

  /// The daemon, for what reads it beside this (the log, the connections); they ask only while [link] is ready.
  Daemon? get daemon => _daemon;

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
      // without a config of the window's the button connects the daemon's profile, where it has NikoService (the
      // core's daemon alone has none)
      final api = _loadConfig != null ? 0 : await daemon.nikoInfo().then((info) => info.api, onError: (Object _) => 0);
      if (_stale(generation)) return;
      daemonVersion = version;
      nikoApi = api;
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
    // what the core does is not known any more: nothing is claimed about it
    service = const ServiceState(ServicePhase.idle);
    _retryTimer?.cancel();
    if (link == LinkPhase.ready && !_quickSpent) {
      // A link that worked and dropped is asked again at once, and the window says it is connecting: the gRPC
      // package closes the whole connection when the window lets go of a stream at the moment the daemon ends it
      // (seen on Windows), and the daemon is there all the same. Once a drop: a link that answers its version and then
      // fails its status stream at once (an older daemon, a refusal) waits [retry] like any loss, not a loop.
      drops++;
      _quickSpent = true;
      link = LinkPhase.connecting;
      linkError = why;
      notifyListeners();
      unawaited(_connect());
      return;
    }
    link = LinkPhase.lost;
    linkError = why;
    notifyListeners();
    _retryTimer = Timer(retry, () {
      if (!_disposed) unawaited(_connect());
    });
  }

  void _onService(ServiceState state) {
    if (_disposed) return;
    // the link works end to end again: a later drop may be asked again at once
    _quickSpent = false;
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

  /// The connect button: starts the core with the config, or with the selected profile in [mode] (null: the
  /// settings') where the window has no config of its own; or stops it. D-MAIN-04, D-NAV-02.
  Future<void> toggle({ConnectMode? mode}) => _act(() async {
        final daemon = _daemon!;
        switch (service.phase) {
          case ServicePhase.idle || ServicePhase.fatal:
            final load = _loadConfig;
            if (load != null) {
              await daemon.start(await load());
            } else if (connectsProfile) {
              await daemon.connect(0, mode: mode);
              startedMode = mode;
            } else {
              throw StateError('no config to start with');
            }
          case ServicePhase.starting || ServicePhase.started:
            await daemon.stop();
          case ServicePhase.stopping:
            break;
        }
      });

  /// The mode chosen while the core runs with the selected profile: started again in it, as Connect reloads the core
  /// (D-NAV-02); nothing otherwise, the next press takes it.
  Future<void> switchMode(ConnectMode mode) async {
    final running = service.phase == ServicePhase.starting || service.phase == ServicePhase.started;
    if (!connectsProfile || !running || mode == startedMode) return;
    await _act(() async {
      await _daemon!.connect(0, mode: mode);
      startedMode = mode;
    });
  }

  Future<void> _act(Future<void> Function() action) async {
    final daemon = _daemon;
    if (daemon == null || link != LinkPhase.ready || busy) return;
    busy = true;
    actionError = '';
    notifyListeners();
    try {
      await action();
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

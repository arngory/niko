import 'dart:async';

import 'package:flutter/foundation.dart';

import '../daemon/daemon.dart';
import '../settings/setting_values.dart';
import 'connection_controller.dart';

/// The computer's settings for the section Настройки (B-Settings): the daemon's (NikoService.GetSettings), read while
/// the section is shown and again as Watch says SETTINGS. A change is written at once (UpdateSettings) and shown
/// before the daemon answers; one it refuses is read back as the daemon has it, and its reason is [error]. The keys are
/// the app's names of the settings, as the daemon names them.
class SettingsFeed extends ChangeNotifier implements SettingValues {
  SettingsFeed(this._link);

  final ConnectionController _link;

  Map<String, Object> _values = const {};

  /// The settings have been read once since the section was shown.
  bool get loaded => _values.isNotEmpty;

  /// Why the settings were not read; empty when they were.
  String readError = '';

  @override
  String error = '';

  bool _shown = false;
  bool _disposed = false;
  StreamSubscription<StoreChange>? _subscription;
  Timer? _retry;
  // the reads and writes one after another: a read a change asks for never lands before the write that made it
  Future<void> _queue = Future.value();

  /// The section is shown: read the settings and follow them.
  void show() {
    if (_shown || _disposed) return;
    _shown = true;
    _link.addListener(_sync);
    _sync();
  }

  /// The section is gone: stop following them.
  void hide() {
    if (!_shown) return;
    _shown = false;
    _link.removeListener(_sync);
    _stop();
  }

  void _sync() {
    if (_link.link != LinkPhase.ready) {
      _stop();
      return;
    }
    if (_subscription != null || _retry != null) return;
    final daemon = _link.daemon;
    if (daemon == null) return;
    _subscription = daemon.storeChanges().listen(
          (change) {
            if (change.reset || change.kind == StoreKind.settings) _then(() => _read(daemon));
          },
          onError: (Object e) => _ended(e),
          onDone: () => _ended(null),
          cancelOnError: true,
        );
  }

  void _then(Future<void> Function() step) => _queue = _queue.then((_) => step());

  Future<void> _read(Daemon daemon) async {
    try {
      _values = await daemon.settings();
      readError = '';
    } catch (e) {
      readError = daemonError(e);
    }
    if (!_disposed) notifyListeners();
  }

  void _ended(Object? error) {
    _subscription = null;
    if (error != null) readError = daemonError(error);
    if (!_shown || _disposed) return;
    notifyListeners();
    // the daemon may come back: read again in a while
    _retry = Timer(const Duration(seconds: 2), () {
      _retry = null;
      if (_shown) _sync();
    });
  }

  void _stop() {
    _retry?.cancel();
    _retry = null;
    unawaited(_subscription?.cancel());
    _subscription = null;
  }

  @override
  bool flag(String key) => switch (_values[key]) {
        final bool on => on,
        // a setting the app keeps as a number, 0 for off (trafficSniffing)
        final int value => value != 0,
        _ => false,
      };

  @override
  String text(String key) => _values[key]?.toString() ?? '';

  // a value as the setting's own type: a number's from its digits
  Object? _typed(String key, Object value) {
    final current = _values[key];
    if (current is int) {
      if (value is bool) return value ? 1 : 0;
      return int.tryParse('$value');
    }
    return value;
  }

  @override
  void setFlag(String key, bool value) => _change({key: value});

  @override
  void setText(String key, String value) => _change({key: value});

  void _change(Map<String, Object> values) {
    final typed = <String, Object>{};
    for (final MapEntry(:key, :value) in values.entries) {
      final v = _typed(key, value);
      if (v == null) return;
      typed[key] = v;
    }
    // shown at once, then as the daemon has it
    _values = {..._values, ...typed};
    notifyListeners();
    _then(() async {
      final message = await _write(typed);
      final daemon = _link.daemon;
      if (message != null && daemon != null) await _read(daemon);
      error = message ?? '';
      if (!_disposed) notifyListeners();
    });
  }

  /// Writes the values: null once the daemon keeps them, else its reason.
  Future<String?> _write(Map<String, Object> values) async {
    final daemon = _link.daemon;
    if (daemon == null || _link.link != LinkPhase.ready) return 'daemon unavailable';
    try {
      _values = await daemon.updateSettings(values);
      return null;
    } catch (e) {
      return daemonError(e);
    }
  }

  @override
  Future<String?> save({Map<String, bool> flags = const {}, Map<String, String> texts = const {}}) {
    final typed = <String, Object>{};
    for (final MapEntry(:key, :value) in <String, Object>{...flags, ...texts}.entries) {
      final v = _typed(key, value);
      if (v == null) return Future.value('$key: $value');
      typed[key] = v;
    }
    final done = Completer<String?>();
    _then(() async {
      final message = await _write(typed);
      if (message == null) error = '';
      if (!_disposed) notifyListeners();
      done.complete(message);
    });
    return done.future;
  }

  @override
  void dispose() {
    _disposed = true;
    hide();
    super.dispose();
  }
}

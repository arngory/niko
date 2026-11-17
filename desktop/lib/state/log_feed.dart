import 'dart:async';

import 'package:flutter/foundation.dart';

import '../daemon/daemon.dart';
import 'connection_controller.dart';

/// The core's log for the screen Журнал (D-LOG-01): read from the daemon while the screen is open and the daemon
/// answers, the last [capacity] lines kept.
class LogFeed extends ChangeNotifier {
  LogFeed(this._link, {this.capacity = 2000});

  final ConnectionController _link;
  final int capacity;

  /// Oldest first.
  List<LogLine> get lines => _lines;
  var _lines = <LogLine>[];

  /// Counts the times the log started over (a clearing, a new subscription): the screen goes back to the end then.
  int resets = 0;

  /// Why the last clearing failed.
  String error = '';

  bool _open = false;
  bool _disposed = false;
  StreamSubscription<LogBatch>? _subscription;
  Timer? _retry;

  /// The screen is shown: read the log.
  void open() {
    if (_open || _disposed) return;
    _open = true;
    _link.addListener(_sync);
    _sync();
  }

  /// The screen is gone: stop reading. The lines stay for the next time, when the daemon sends its own again.
  void close() {
    if (!_open) return;
    _open = false;
    _link.removeListener(_sync);
    _stop();
  }

  void _sync() {
    final ready = _link.link == LinkPhase.ready;
    if (!ready) {
      _stop();
    } else if (_subscription == null && _retry == null) {
      _subscribe();
    }
  }

  void _subscribe() {
    final daemon = _link.daemon;
    if (daemon == null || _disposed || !_open) return;
    _subscription = daemon.logs().listen(_take, onError: (Object _) => _ended(), onDone: _ended, cancelOnError: true);
  }

  void _take(LogBatch batch) {
    if (batch.reset) {
      _lines = [];
      resets++;
    }
    if (batch.lines.isNotEmpty) {
      final all = [..._lines, ...batch.lines];
      _lines = all.length > capacity ? all.sublist(all.length - capacity) : all;
    }
    notifyListeners();
  }

  // the stream ended while the daemon still answers: asked again after the link's own pause
  void _ended() {
    _subscription = null;
    if (!_open || _disposed) return;
    _retry = Timer(_link.retry, () {
      _retry = null;
      if (_open && !_disposed) _sync();
    });
  }

  void _stop() {
    _retry?.cancel();
    _retry = null;
    unawaited(_subscription?.cancel());
    _subscription = null;
  }

  /// Empties the daemon's log; the stream then says so with a reset.
  Future<void> clear() async {
    final daemon = _link.daemon;
    if (daemon == null || _link.link != LinkPhase.ready) return;
    try {
      await daemon.clearLogs();
      error = '';
    } catch (failure) {
      error = daemonError(failure);
    }
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    close();
    _disposed = true;
    super.dispose();
  }
}

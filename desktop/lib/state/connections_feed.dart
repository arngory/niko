import 'dart:async';

import 'package:flutter/foundation.dart';

import '../daemon/daemon.dart';
import 'connection_controller.dart';

/// The open connections for the screen Соединения (D-CONN-01): read from the daemon while the screen is open and
/// the core runs. The daemon refuses the stream when the core is not running and ends it when the core stops.
class ConnectionsFeed extends ChangeNotifier {
  ConnectionsFeed(this._link, {this.interval = const Duration(seconds: 1)});

  final ConnectionController _link;
  final Duration interval;

  /// Newest first.
  List<ConnectionInfo> get open => _sorted ??= _byId.values.toList()
    ..sort((a, b) {
      final byTime = b.createdAt.compareTo(a.createdAt);
      return byTime != 0 ? byTime : a.id.compareTo(b.id);
    });
  final _byId = <String, ConnectionInfo>{};
  List<ConnectionInfo>? _sorted;

  /// Why the last closing failed.
  String error = '';

  bool _shown = false;
  bool _disposed = false;
  StreamSubscription<ConnectionBatch>? _subscription;
  Timer? _retry;

  /// The screen is shown: read the connections.
  void show() {
    if (_shown || _disposed) return;
    _shown = true;
    _link.addListener(_sync);
    _sync();
  }

  /// The screen is gone: stop reading, and forget what was open.
  void hide() {
    if (!_shown) return;
    _shown = false;
    _link.removeListener(_sync);
    _stop();
  }

  void _sync() {
    final running = _link.link == LinkPhase.ready && _link.service.phase == ServicePhase.started;
    if (!running) {
      final had = _byId.isNotEmpty;
      _stop();
      if (had && !_disposed) notifyListeners();
    } else if (_subscription == null && _retry == null) {
      final daemon = _link.daemon;
      if (daemon == null) return;
      _subscription = daemon
          .connections(interval)
          .listen(_take, onError: (Object _) => _ended(), onDone: _ended, cancelOnError: true);
    }
  }

  void _take(ConnectionBatch batch) {
    if (batch.reset) _byId.clear();
    for (final change in batch.changes) {
      final opened = change.opened;
      if (opened != null) {
        _byId[opened.id] = opened;
      } else if (change.closed) {
        _byId.remove(change.id);
      } else {
        final known = _byId[change.id];
        if (known != null) _byId[change.id] = known.moved(change.upDelta, change.downDelta);
      }
    }
    _sorted = null;
    notifyListeners();
  }

  void _ended() {
    _subscription = null;
    if (!_shown || _disposed) return;
    _retry = Timer(_link.retry, () {
      _retry = null;
      if (_shown && !_disposed) _sync();
    });
  }

  void _stop() {
    _retry?.cancel();
    _retry = null;
    unawaited(_subscription?.cancel());
    _subscription = null;
    _byId.clear();
    _sorted = null;
  }

  Future<void> close(String id) => _ask((daemon) => daemon.closeConnection(id));

  Future<void> closeAll() => _ask((daemon) => daemon.closeAllConnections());

  Future<void> _ask(Future<void> Function(Daemon daemon) call) async {
    final daemon = _link.daemon;
    if (daemon == null || _link.link != LinkPhase.ready) return;
    try {
      await call(daemon);
      error = '';
    } catch (failure) {
      error = daemonError(failure);
    }
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    hide();
    _disposed = true;
    super.dispose();
  }
}

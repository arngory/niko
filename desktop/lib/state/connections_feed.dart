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
      _retire();
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

  /// The core stopped: the daemon ends the stream itself. It is let end, not cancelled: a cancel that crosses the
  /// daemon's end makes package:http2 close the whole connection, every other call of the window with it (seen
  /// against boxdd: "HTTP/2 clients cannot receive HEADER_FRAMEs", a PROTOCOL_ERROR). Cancelled only if it has not
  /// ended within a second.
  void _retire() {
    final subscription = _subscription;
    _subscription = null;
    _retry?.cancel();
    _retry = null;
    _byId.clear();
    _sorted = null;
    if (subscription == null) return;
    late final Timer grace;
    void forget() {
      grace.cancel();
      _retired.remove(subscription);
    }

    grace = Timer(const Duration(seconds: 1), () {
      _retired.remove(subscription);
      unawaited(subscription.cancel());
    });
    _retired[subscription] = grace;
    subscription
      ..onData((_) {})
      ..onError((Object _) => forget())
      ..onDone(forget);
  }

  // streams let end after a stop, and the timers that cancel them if they do not
  final _retired = <StreamSubscription<ConnectionBatch>, Timer>{};

  void _stop() {
    // the screen is gone: what was let end is cancelled now
    for (final MapEntry(key: subscription, value: grace) in _retired.entries) {
      grace.cancel();
      unawaited(subscription.cancel());
    }
    _retired.clear();
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

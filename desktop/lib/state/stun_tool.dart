import 'dart:async';

import 'package:flutter/foundation.dart';

import '../daemon/daemon.dart';
import 'connection_controller.dart';

/// The NAT behaviour tool beside the log: a STUN test through the core's default way out, its steps as they come.
class StunTool extends ChangeNotifier {
  StunTool(this._link) : _available = false {
    _available = available;
    _link.addListener(_linkChanged);
  }

  final ConnectionController _link;

  /// The latest step or the result; null: not run yet.
  StunProgress? progress;

  /// Why the test could not run (the daemon's words).
  String error = '';
  bool running = false;

  StreamSubscription<StunProgress>? _subscription;
  bool _available;
  bool _disposed = false;

  /// The core runs: the test has a way out to go through.
  bool get available => _link.link == LinkPhase.ready && _link.service.phase == ServicePhase.started;

  // the button follows the core; a test the core stopped under ends with the daemon's error
  void _linkChanged() {
    if (available == _available) return;
    _available = available;
    notifyListeners();
  }

  void run(String server) {
    final daemon = _link.daemon;
    if (daemon == null || running || !available) return;
    running = true;
    progress = null;
    error = '';
    notifyListeners();
    late final StreamSubscription<StunProgress> subscription;
    subscription = daemon
        .stunTest(server.trim())
        .listen(
          (step) {
            if (_subscription != subscription) return;
            progress = step;
            if (step.isFinal) {
              error = step.error;
              _finish(subscription);
            } else {
              notifyListeners();
            }
          },
          onError: (Object failure) {
            if (_subscription != subscription) return;
            error = daemonError(failure);
            _finish(subscription);
          },
          onDone: () => _finish(subscription),
          cancelOnError: true,
        );
    _subscription = subscription;
  }

  void _finish(StreamSubscription<StunProgress> subscription) {
    if (_subscription != subscription) return;
    _subscription = null;
    running = false;
    // the final step ends the test: what the stream still says after it is not read
    unawaited(subscription.cancel());
    if (!_disposed) notifyListeners();
  }

  @override
  void dispose() {
    _disposed = true;
    _link.removeListener(_linkChanged);
    unawaited(_subscription?.cancel());
    _subscription = null;
    super.dispose();
  }
}

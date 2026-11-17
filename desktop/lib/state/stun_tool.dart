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

  /// The core stopped while a test ran: its sockets closed under it, and what it went on to measure would read as a
  /// NAT that answers nothing, so nothing of it is shown.
  bool stopped = false;

  StreamSubscription<StunProgress>? _subscription;
  bool _available;
  bool _disposed = false;

  /// The core runs: the test has a way out to go through.
  bool get available => _link.link == LinkPhase.ready && _link.service.phase == ServicePhase.started;

  // the button follows the core; a test it stopped under is left to end unread (see [_finish])
  void _linkChanged() {
    final now = available;
    if (now == _available) return;
    _available = now;
    // the note is of the stop that just happened: the core back, it is gone
    if (now) stopped = false;
    if (!now && running) {
      _subscription = null;
      running = false;
      progress = null;
      error = '';
      stopped = true;
    }
    notifyListeners();
  }

  void run(String server) {
    final daemon = _link.daemon;
    if (daemon == null || running || !available) return;
    running = true;
    progress = null;
    error = '';
    stopped = false;
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

  // The final step ends the test, and the daemon ends the stream right after it: the stream is let end, not
  // cancelled, as a cancel that crosses the daemon's end makes package:http2 close the whole connection (as with the
  // connections, see ConnectionsFeed._retire). A test always ends, so nothing waits on it for long; what the stream
  // still says is not read.
  void _finish(StreamSubscription<StunProgress> subscription) {
    if (_subscription != subscription) return;
    _subscription = null;
    running = false;
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

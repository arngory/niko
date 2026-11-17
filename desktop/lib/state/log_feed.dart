import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/foundation.dart';

import '../daemon/daemon.dart';
import '../platform/places.dart' as places;
import 'connection_controller.dart';

/// What the log screen says above the log after an action: what happened and the detail (a path, the daemon's words).
enum LogNotice { none, copied, saved, saveFailed, clearFailed, reportFailed }

/// The core's log for the screen Журнал (D-LOG-01): read from the daemon while the screen is open and the daemon
/// answers, the last [capacity] lines kept. With it the daemon's reports of crashes and of memory run out
/// (D-LOG-02), and the saving of both to the user's Downloads.
class LogFeed extends ChangeNotifier {
  LogFeed(this._link, {this.capacity = 2000, Directory? Function()? saveTo, DateTime Function()? now})
      : _saveTo = saveTo ?? places.saveDirectory,
        _now = now ?? DateTime.now;

  final ConnectionController _link;
  final int capacity;
  final Directory? Function() _saveTo;
  final DateTime Function() _now;

  /// The daemon's reports nobody has saved yet, newest first.
  List<ReportEntry> unread = const [];

  /// What the last action came to, and its detail: the file's path, or why.
  LogNotice notice = LogNotice.none;
  String noticeDetail = '';

  /// An export under way: the buttons wait for it.
  bool busy = false;

  /// Oldest first.
  List<LogLine> get lines => _lines;
  var _lines = <LogLine>[];

  /// Counts the times the log started over (a clearing, a new subscription): the screen goes back to the end then.
  int resets = 0;

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
    unawaited(_loadReports(daemon));
  }

  // asked again with each link to the daemon: a crash of the core is followed by the daemon's restart of it
  Future<void> _loadReports(Daemon daemon) async {
    try {
      final all = await daemon.reports();
      if (_disposed) return;
      unread = [for (final report in all) if (!report.read) report];
      notifyListeners();
    } catch (_) {
      // an older daemon, or one that went away: no reports to show, the log goes on
    }
  }

  /// The log as text, a line each, for the clipboard.
  String get text => _lines.map((line) => line.text).join('\n');

  void _say(LogNotice what, String detail) {
    notice = what;
    noticeDetail = detail;
  }

  /// The screen put [text] on the clipboard: said until the next action.
  void copied() {
    _say(LogNotice.copied, '');
    notifyListeners();
  }

  /// Saves the log as it is shown into the user's Downloads.
  Future<void> saveLog() => _save(() async {
        final stamp = _now().toIso8601String().replaceAll(RegExp(r'[^0-9T]'), '').replaceFirst('T', '-');
        return (name: 'niko-log-${stamp.substring(0, stamp.length.clamp(0, 15))}.txt', data: utf8.encode('$text\n'));
      }, LogNotice.saveFailed);

  /// Saves the report's archive into the user's Downloads and marks it read.
  Future<void> saveReport(ReportEntry report) => _save(() async {
        final daemon = _link.daemon;
        if (daemon == null) throw StateError('no daemon');
        final archive = await daemon.exportReport(report);
        return (name: archive.fileName, data: archive.data);
      }, LogNotice.reportFailed, after: () async {
        unread = [for (final other in unread) if (other.name != report.name || other.kind != report.kind) other];
        try {
          await _link.daemon?.markReportRead(report);
        } catch (_) {
          // saved all the same; the banner comes back with the next link
        }
      });

  Future<void> _save(Future<({String name, List<int> data})> Function() produce, LogNotice failure,
      {Future<void> Function()? after}) async {
    if (busy) return;
    busy = true;
    notifyListeners();
    try {
      final directory = _saveTo();
      if (directory == null) throw const FileSystemException('no folder to save into');
      final file = await produce();
      final saved = await places.saveNew(directory, file.name, file.data);
      await after?.call();
      _say(LogNotice.saved, saved.path);
    } catch (error) {
      _say(failure, error is FileSystemException ? error.message : daemonError(error));
    }
    busy = false;
    if (!_disposed) notifyListeners();
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
      if (notice == LogNotice.clearFailed) _say(LogNotice.none, '');
    } catch (failure) {
      _say(LogNotice.clearFailed, daemonError(failure));
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

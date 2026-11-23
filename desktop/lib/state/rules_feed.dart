import 'dart:async';

import 'package:flutter/foundation.dart';

import '../daemon/daemon.dart';
import 'connection_controller.dart';

/// The route rules and their rule-sets for the section Правила (D-RULES-01, -03, -05): read from the daemon's store
/// while the section is shown, and again as NikoService.Watch says RULES or RULE_SETS. Each change is the daemon's:
/// the list follows what it says, not what the window asked.
class RulesFeed extends ChangeNotifier {
  RulesFeed(this._link);

  final ConnectionController _link;

  List<RuleRow> rules = const [];
  RuleSetStatus ruleSets = const RuleSetStatus();

  /// The store has been read once since the section was shown: an empty list is empty, not unread.
  bool loaded = false;

  /// Why the last read or change failed; empty when it did not.
  String error = '';

  /// What the window's last update of the rule-sets (or preset) did, while its notice shows ([noticeFor]): the
  /// daemon's line, or its words for why it failed ([noticeFailed]); null after.
  String? notice;
  bool noticeFailed = false;
  static const noticeFor = Duration(seconds: 6);
  Timer? _noticeTimer;

  /// An update of the rule-sets asked from the window runs.
  bool updating = false;

  bool _shown = false;
  bool _disposed = false;
  StreamSubscription<StoreChange>? _subscription;
  Timer? _retry;
  Future<void> _reading = Future.value();

  void show() {
    if (_shown || _disposed) return;
    _shown = true;
    _link.addListener(_sync);
    _sync();
  }

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
          (change) => _reading = _reading.then((_) => _changed(daemon, change)),
          onError: (Object e) => _ended(e),
          onDone: () => _ended(null),
          cancelOnError: true,
        );
  }

  Future<void> _changed(Daemon daemon, StoreChange change) async {
    final all = change.reset;
    if (!all && change.kind != StoreKind.rules && change.kind != StoreKind.ruleSets) return;
    try {
      // a rule names its rule-sets: a change of the rules changes them too
      if (all || change.kind == StoreKind.rules) rules = await daemon.rules();
      ruleSets = await daemon.ruleSets();
      loaded = true;
      error = '';
    } catch (e) {
      error = daemonError(e);
    }
    if (!_disposed) notifyListeners();
  }

  void _ended(Object? error) {
    _subscription = null;
    if (error != null) this.error = daemonError(error);
    if (!_shown || _disposed) return;
    notifyListeners();
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

  // a change asked of the daemon: its error said, the list read again by what Watch says
  Future<bool> _ask(Future<void> Function(Daemon daemon) call) async {
    final daemon = _link.daemon;
    if (daemon == null) return false;
    try {
      await call(daemon);
      error = '';
      if (!_disposed) notifyListeners();
      return true;
    } catch (e) {
      error = daemonError(e);
      if (!_disposed) notifyListeners();
      return false;
    }
  }

  /// Saves a rule (a new one at the end): null where it was saved, else the daemon's words for why not, which the
  /// editor shows (the list's own error stays as it was).
  Future<String?> save(RuleRow rule) async {
    final daemon = _link.daemon;
    if (daemon == null) return 'no daemon';
    try {
      await daemon.saveRule(rule);
      return null;
    } catch (e) {
      return daemonError(e);
    }
  }

  /// Turns a rule on or off.
  Future<void> setEnabled(RuleRow rule, bool on) => _ask((d) => d.saveRule(rule.copyWith(enabled: on)));

  Future<void> delete(RuleRow rule) => _ask((d) => d.deleteRules([rule.id]));

  /// Moves a rule one place up (earlier: the config takes it first) or down.
  Future<void> move(RuleRow rule, {required bool up}) {
    final at = rules.indexWhere((r) => r.id == rule.id);
    if (at < 0 || (up && at == 0) || (!up && at == rules.length - 1)) return Future.value();
    final before = up ? rules[at - 1].id : (at + 2 < rules.length ? rules[at + 2].id : 0);
    return _ask((d) => d.moveRules([rule.id], before: before));
  }

  /// The Russian preset (D-RULES-05): its rules in place of its earlier ones, then its lists downloaded.
  Future<void> applyRussia({required String blockedName, required String directName}) async {
    final daemon = _link.daemon;
    if (daemon == null || updating) return;
    PresetApplied? applied;
    if (!await _ask((d) async => applied = await d.applyRussiaPreset(blockedName: blockedName, directName: directName))) {
      return;
    }
    await _update(daemon, applied!.ruleSets);
  }

  /// Downloads the enabled rules' rule-sets again, fresh or not (D-RULES-03).
  Future<void> updateAll() async {
    final daemon = _link.daemon;
    if (daemon == null || updating) return;
    await _update(daemon, const []);
  }

  Future<void> _update(Daemon daemon, List<String> items) async {
    updating = true;
    if (!_disposed) notifyListeners();
    try {
      final result = await daemon.updateRuleSets(items: items, force: true);
      _say(result.ok ? result.line : result.error, failed: !result.ok);
    } catch (e) {
      _say(daemonError(e), failed: true);
    }
    updating = false;
    if (!_disposed) notifyListeners();
  }

  void _say(String text, {required bool failed}) {
    _noticeTimer?.cancel();
    notice = text;
    noticeFailed = failed;
    _noticeTimer = Timer(noticeFor, () {
      notice = null;
      if (!_disposed) notifyListeners();
    });
  }

  @override
  void dispose() {
    _disposed = true;
    _noticeTimer?.cancel();
    hide();
    super.dispose();
  }
}

import 'dart:async';

import 'package:flutter/foundation.dart';

import '../daemon/daemon.dart';
import 'connection_controller.dart';

/// The groups and profiles for the section Узлы (D-MAIN-07, B-Profiles): read from the daemon's store while the
/// section is shown, and again as NikoService.Watch says what changed. The group shown is the user's to choose; the
/// selected profile is the daemon's (NikoService.Select), whatever group it is in.
class ProfilesFeed extends ChangeNotifier {
  ProfilesFeed(this._link);

  final ConnectionController _link;

  List<GroupRow> groups = const [];

  /// The group shown: 0 until the groups are read, then the first one, or the one chosen.
  int groupId = 0;
  List<ProfileRow> profiles = const [];

  /// The selected profile's id; 0: none.
  int selected = 0;

  /// The store has been read once since the section was shown: an empty list is empty, not unread.
  bool loaded = false;

  /// Why the last read or selection failed; empty when it did not.
  String error = '';

  /// The profile deleted last, while it can still be put back (D-PROFILE-09's «Вернуть»); null after [undoFor].
  DeletedProfile? deleted;

  /// How long a deletion can be undone.
  static const undoFor = Duration(seconds: 8);
  Timer? _undoTimer;

  bool _shown = false;
  bool _disposed = false;
  StreamSubscription<StoreChange>? _subscription;
  Timer? _retry;
  // the reads a change asks for, one after another: a later change's read never lands before an earlier one's
  Future<void> _reading = Future.value();

  /// The section is shown: read the store and follow it.
  void show() {
    if (_shown || _disposed) return;
    _shown = true;
    _link.addListener(_sync);
    _sync();
  }

  /// The section is gone: stop following the store.
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
    try {
      final all = change.reset;
      if (all || change.kind == StoreKind.groups || change.kind == StoreKind.profiles) {
        // a group's count changes with its profiles
        groups = await daemon.groups();
        if (!groups.any((g) => g.id == groupId)) groupId = groups.isEmpty ? 0 : groups.first.id;
      }
      if (all || change.kind == StoreKind.profiles || change.kind == StoreKind.groups) {
        profiles = groupId == 0 ? const [] : await daemon.profiles(groupId: groupId);
      }
      if (all || change.kind == StoreKind.selected) {
        selected = await daemon.selected();
      }
      loaded = true;
      error = '';
    } catch (e) {
      error = '$e';
    }
    if (!_disposed) notifyListeners();
  }

  void _ended(Object? error) {
    _subscription = null;
    if (error != null) this.error = '$error';
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

  /// Shows another group's profiles.
  Future<void> choose(int id) async {
    if (id == groupId) return;
    groupId = id;
    profiles = const [];
    notifyListeners();
    final daemon = _link.daemon;
    if (daemon == null) return;
    try {
      profiles = await daemon.profiles(groupId: id);
      error = '';
    } catch (e) {
      error = '$e';
    }
    if (!_disposed && groupId == id) notifyListeners();
  }

  /// The group an import from the window goes into: the one shown, unless it is a subscription's.
  int get importGroup {
    final group = groups.where((g) => g.id == groupId).firstOrNull;
    return group == null || group.subscription ? 0 : group.id;
  }

  /// What an input would add; nothing is written.
  Future<ImportPreview> recognize(String text) async {
    final daemon = _link.daemon;
    if (daemon == null || text.trim().isEmpty) return const ImportPreview();
    return daemon.recognize(text);
  }

  /// Imports an input's profiles into [importGroup] and shows that group: their ids.
  Future<List<int>> add(String text) async {
    final daemon = _link.daemon;
    if (daemon == null) return const [];
    final target = importGroup;
    final ids = await daemon.importLinks(text, groupId: target);
    if (target == 0 && ids.isNotEmpty) {
      // the ungrouped group: where the daemon put them
      final ungrouped = groups.where((g) => g.ungrouped).firstOrNull;
      if (ungrouped != null) await choose(ungrouped.id);
    }
    return ids;
  }

  /// Deletes a profile, keeping its bean and place to put it back for [undoFor].
  Future<void> delete(ProfileRow row) async {
    final daemon = _link.daemon;
    if (daemon == null) return;
    try {
      // a profile whose secrets cannot be opened here has no bean to keep: deleted without a way back
      SavedProfile? saved;
      try {
        saved = await daemon.profile(row.id);
      } catch (_) {}
      final at = profiles.indexWhere((p) => p.id == row.id);
      final next = at >= 0 && at + 1 < profiles.length ? profiles[at + 1].id : 0;
      final wasSelected = row.id == selected;
      await daemon.deleteProfiles([row.id]);
      _undoTimer?.cancel();
      deleted = saved == null ? null : DeletedProfile(saved, next, wasSelected);
      if (deleted != null) {
        _undoTimer = Timer(undoFor, () {
          deleted = null;
          if (!_disposed) notifyListeners();
        });
      }
      error = '';
    } catch (e) {
      error = '$e';
    }
    if (!_disposed) notifyListeners();
  }

  /// Puts the profile deleted last back where it was, selected again if it was.
  Future<void> undo() async {
    final daemon = _link.daemon;
    final gone = deleted;
    if (daemon == null || gone == null) return;
    deleted = null;
    _undoTimer?.cancel();
    notifyListeners();
    try {
      final row = gone.saved.row;
      final id = await daemon.addProfile(groupId: row.groupId, type: row.type, beanJson: gone.saved.beanJson);
      if (gone.before != 0) await daemon.moveProfiles([id], groupId: row.groupId, before: gone.before);
      if (gone.selected) await daemon.select(id);
      error = '';
    } catch (e) {
      error = '$e';
      if (!_disposed) notifyListeners();
    }
  }

  /// Selects a profile: the daemon says SELECTED, and the list follows it.
  Future<void> select(int profileId) async {
    final daemon = _link.daemon;
    if (daemon == null || profileId == selected) return;
    try {
      await daemon.select(profileId);
      error = '';
    } catch (e) {
      error = '$e';
      if (!_disposed) notifyListeners();
    }
  }

  @override
  void dispose() {
    _disposed = true;
    _undoTimer?.cancel();
    hide();
    super.dispose();
  }
}

/// A profile deleted from the window: its bean, the profile it stood before (0: the group's last) and whether it was
/// the selected one.
class DeletedProfile {
  const DeletedProfile(this.saved, this.before, this.selected);

  final SavedProfile saved;
  final int before;
  final bool selected;
}

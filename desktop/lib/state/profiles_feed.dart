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

  /// The raycontrol nodes (D-NODE-01…07): each has a group of its own among [groups].
  List<NodeRow> nodes = const [];

  /// The group of the selected profile, for the node it may be of (D-MAIN-13); 0: none read.
  int selectedGroup = 0;

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

  /// The name of the profile whose link was copied last, while its notice shows; null after [noticeFor].
  String? copied;

  /// How long the notice of a copied link shows.
  static const noticeFor = Duration(seconds: 4);
  Timer? _copiedTimer;

  /// What a subscription's update or import from the window did, while its notice shows ([noticeFor]): the daemon's
  /// line, or its words for why it failed ([updateFailed]); null after.
  String? updated;
  bool updateFailed = false;
  Timer? _updatedTimer;

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
        // a group chosen meanwhile (a subscription's import shows its group) reads its own
        final shown = groupId;
        final read = shown == 0 ? const <ProfileRow>[] : await daemon.profiles(groupId: shown);
        if (shown == groupId) profiles = read;
      }
      if (all || change.kind == StoreKind.nodes || change.kind == StoreKind.groups) {
        nodes = await daemon.nodes();
      }
      if (all || change.kind == StoreKind.selected) {
        selected = await daemon.selected();
        selectedGroup = 0;
        if (selected != 0) {
          try {
            selectedGroup = (await daemon.profile(selected)).row.groupId;
          } catch (_) {
            // its secrets sealed elsewhere: no node to show of it
          }
        }
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

  /// The group an import from the window goes into: the one shown, unless it is a subscription's or a node's.
  int get importGroup {
    final group = groups.where((g) => g.id == groupId).firstOrNull;
    return group == null || group.subscription || group.nodeId.isNotEmpty ? 0 : group.id;
  }

  /// The node whose group [id] is; null for none.
  NodeRow? nodeOfGroup(int id) => nodes.where((n) => n.groupId == id && id != 0).firstOrNull;

  /// The node of the selected profile (D-MAIN-13); null for none.
  NodeRow? get selectedNode => nodeOfGroup(selectedGroup);

  /// Imports a node's invite or device package (D-ADD-06): its result, a PIN asked for in [TaskResult.pin]; once
  /// the node is kept, its group is shown and what its first update did is said.
  Future<TaskResult> addNode(String text, {String pin = ''}) async {
    final daemon = _link.daemon;
    if (daemon == null) return const TaskResult(code: 14, error: 'no link to the service');
    final result = await daemon.importNode(text, pin: pin);
    if (result.nodeId.isNotEmpty) {
      groups = await daemon.groups();
      nodes = await daemon.nodes();
      if (result.groupId != 0) await choose(result.groupId);
      _notice(result);
    }
    return result;
  }

  /// Updates a node's subscription (D-NODE-02); what it did is said.
  Future<void> refreshNode(String id) async {
    final daemon = _link.daemon;
    if (daemon == null) return;
    try {
      _notice(await daemon.refreshNode(id));
      error = '';
    } catch (e) {
      error = '$e';
      if (!_disposed) notifyListeners();
    }
  }

  /// Removes a node with its group and key (D-NODE-07): it comes back only with a new invite.
  Future<void> deleteNode(String id) async {
    final daemon = _link.daemon;
    if (daemon == null) return;
    try {
      await daemon.deleteNode(id);
      error = '';
    } catch (e) {
      error = '$e';
      if (!_disposed) notifyListeners();
    }
  }

  /// What an input would add; nothing is written.
  Future<ImportPreview> recognize(String text) async {
    final daemon = _link.daemon;
    if (daemon == null || text.trim().isEmpty) return const ImportPreview();
    return daemon.recognize(text);
  }

  /// Imports a subscription's link (D-SUB-01): the daemon makes its group and updates it; that group is shown, and
  /// what its first update did is said. Fails where the group was not made.
  Future<void> addSubscription(String text) async {
    final daemon = _link.daemon;
    if (daemon == null) return;
    final result = await daemon.importSubscription(text);
    if (result.groupId == 0) throw StateError(result.error.isEmpty ? 'the subscription was not added' : result.error);
    groups = await daemon.groups();
    await choose(result.groupId);
    _notice(result);
  }

  /// Updates a subscription's group (D-SUB-03): the daemon says the group's changes, and what the update did is said.
  Future<void> refresh(int groupId) async {
    final daemon = _link.daemon;
    if (daemon == null) return;
    try {
      _notice(await daemon.refreshSubscription(groupId));
      error = '';
    } catch (e) {
      error = '$e';
      if (!_disposed) notifyListeners();
    }
  }

  void _notice(TaskResult result) {
    _updatedTimer?.cancel();
    updated = result.ok ? result.line : result.error;
    updateFailed = !result.ok;
    _updatedTimer = Timer(noticeFor, () {
      updated = null;
      if (!_disposed) notifyListeners();
    });
    if (!_disposed) notifyListeners();
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

  /// Copies the link to share a profile by (NikoService.ShareProfile) with [copy], and says so for [noticeFor].
  Future<void> share(ProfileRow row, Future<void> Function(String link) copy) async {
    final daemon = _link.daemon;
    if (daemon == null) return;
    try {
      await copy(await daemon.shareLink(row.id));
      _copiedTimer?.cancel();
      copied = row.name;
      _copiedTimer = Timer(noticeFor, () {
        copied = null;
        if (!_disposed) notifyListeners();
      });
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
    _copiedTimer?.cancel();
    _updatedTimer?.cancel();
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

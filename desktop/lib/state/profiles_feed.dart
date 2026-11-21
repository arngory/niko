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

  bool _shown = false;
  bool _disposed = false;
  StreamSubscription<StoreChange>? _subscription;
  Timer? _retry;

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
          (change) => unawaited(_changed(daemon, change)),
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
    if (!_disposed) notifyListeners();
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
    hide();
    super.dispose();
  }
}

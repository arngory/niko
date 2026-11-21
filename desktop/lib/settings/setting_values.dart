import 'package:flutter/foundation.dart';

/// The values a settings screen shows and changes, by their keys in the settings store: switches as flags, fields
/// and choices as text. It tells its listeners of a change, and the screen draws it anew.
abstract class SettingValues implements Listenable {
  bool flag(String key);
  void setFlag(String key, bool value);
  String text(String key);
  void setText(String key, String value);

  /// Several values at once, as a dialog saves them: null once they are kept, else why they were not, in the store's
  /// words; then none of them is.
  Future<String?> save({Map<String, bool> flags = const {}, Map<String, String> texts = const {}});

  /// Why the last change of a switch or a choice was not kept; empty when it was.
  String get error;
}

/// Values held in memory alone, with nothing stored: the screens' tests.
class MemorySettingValues extends ChangeNotifier implements SettingValues {
  MemorySettingValues({Map<String, bool> flags = const {}, Map<String, String> texts = const {}})
      : _flags = {...flags},
        _texts = {...texts};

  final Map<String, bool> _flags;
  final Map<String, String> _texts;

  @override
  bool flag(String key) => _flags[key] ?? false;

  @override
  void setFlag(String key, bool value) {
    _flags[key] = value;
    notifyListeners();
  }

  @override
  String text(String key) => _texts[key] ?? '';

  @override
  void setText(String key, String value) {
    _texts[key] = value;
    notifyListeners();
  }

  @override
  Future<String?> save({Map<String, bool> flags = const {}, Map<String, String> texts = const {}}) async {
    _flags.addAll(flags);
    _texts.addAll(texts);
    notifyListeners();
    return null;
  }

  @override
  String get error => '';
}

import 'package:flutter/foundation.dart';

/// The values a settings screen shows and changes, by their keys in the settings store: switches as flags, fields
/// as text. It tells its listeners of a change, and the screen draws it anew.
abstract class SettingValues implements Listenable {
  bool flag(String key);
  void setFlag(String key, bool value);
  String text(String key);
  void setText(String key, String value);
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
}

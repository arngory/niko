import 'dart:convert';
import 'dart:io';

/// What the window remembers between its launches: the view (Простой or Расширенный). Kept in a file of the
/// user's ([settingsFile]); a file that is not there or does not read gives the defaults, never a failure.
class WindowSettings {
  WindowSettings._(this._file, {this.advanced = false});

  final File? _file;

  /// The view Расширенный.
  bool advanced;

  /// Settings that are kept nowhere: tests, and a system with no home.
  factory WindowSettings.memory({bool advanced = false}) => WindowSettings._(null, advanced: advanced);

  static Future<WindowSettings> load(File? file) async {
    if (file == null) return WindowSettings._(null);
    try {
      final json = jsonDecode(await file.readAsString());
      return WindowSettings._(file, advanced: json is Map && json['view'] == 'advanced');
    } catch (_) {
      return WindowSettings._(file);
    }
  }

  /// Writes the settings: a file beside, then renamed over the old one, so a crash midway leaves one of the two.
  Future<void> save() async {
    final file = _file;
    if (file == null) return;
    try {
      await file.parent.create(recursive: true);
      final temporary = File('${file.path}.new');
      await temporary.writeAsString(jsonEncode({'view': advanced ? 'advanced' : 'simple'}), flush: true);
      await temporary.rename(file.path);
    } catch (_) {
      // a view not remembered is no reason to stop the window
    }
  }
}

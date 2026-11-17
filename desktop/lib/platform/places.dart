import 'dart:io';

/// Where the window keeps its own settings: a folder of the user's, as each system names it. Null: none can be
/// told (no home), and the settings live as long as the window.
File? settingsFile([Map<String, String>? environment, String? os]) {
  final env = environment ?? Platform.environment;
  final system = os ?? Platform.operatingSystem;
  String? present(String? value) => value == null || value.isEmpty ? null : value;
  switch (system) {
    case 'windows':
      final base = present(env['APPDATA']);
      return base == null ? null : File('$base\\Niko\\window.json');
    case 'macos':
      final home = present(env['HOME']);
      return home == null ? null : File('$home/Library/Application Support/Niko/window.json');
    default:
      final home = present(env['HOME']);
      final base = present(env['XDG_CONFIG_HOME']) ?? (home == null ? null : '$home/.config');
      return base == null ? null : File('$base/niko/window.json');
  }
}

/// Where a file the user asked for is saved: their Downloads folder where there is one, else their home.
Directory? saveDirectory([Map<String, String>? environment, String? os]) {
  final env = environment ?? Platform.environment;
  final system = os ?? Platform.operatingSystem;
  final separator = system == 'windows' ? '\\' : '/';
  final home = system == 'windows' ? env['USERPROFILE'] : env['HOME'];
  final named = system == 'windows' || system == 'macos' ? null : env['XDG_DOWNLOAD_DIR'];
  for (final path in [named, if (home != null && home.isNotEmpty) '$home${separator}Downloads', home]) {
    if (path != null && path.isNotEmpty && Directory(path).existsSync()) return Directory(path);
  }
  return null;
}

/// [name] as a file name and nothing else, never a path: the daemon's names and the window's alike.
String safeFileName(String name) {
  final last = name.split(RegExp(r'[\\/]')).last;
  final clean = last.replaceAll(RegExp(r'[^A-Za-z0-9._-]'), '_').replaceAll(RegExp(r'^\.+'), '');
  return clean.isEmpty ? 'niko' : clean;
}

/// Writes [data] as [name] in [directory] and never over a file already there: `name (2).ext` and on.
Future<File> saveNew(Directory directory, String name, List<int> data) async {
  final safe = safeFileName(name);
  final dot = safe.indexOf('.', 1);
  final stem = dot < 0 ? safe : safe.substring(0, dot);
  final extension = dot < 0 ? '' : safe.substring(dot);
  for (var n = 1; n < 1000; n++) {
    final file = File('${directory.path}${Platform.pathSeparator}${n == 1 ? stem : '$stem ($n)'}$extension');
    try {
      // made only where nothing is: two saves at once never share a file
      await file.create(exclusive: true);
    } on FileSystemException {
      continue;
    }
    await file.writeAsBytes(data, flush: true);
    return file;
  }
  throw FileSystemException('no free name for $safe', directory.path);
}

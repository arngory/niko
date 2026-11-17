/// Technical values as the core's log writes them, in Latin: `4.2 MB/s`, `312 KB/s`, `00:42:17`.
String formatBytes(int bytes) {
  const units = ['B', 'KB', 'MB', 'GB', 'TB'];
  var value = bytes < 0 ? 0.0 : bytes.toDouble();
  var unit = 0;
  while (value >= 1000 && unit < units.length - 1) {
    value /= 1024;
    unit++;
  }
  if (unit == 0) return '${value.round()} B';
  // three significant digits at most: 4.2, 42, 312
  return '${value < 10 ? value.toStringAsFixed(1) : value.round()} ${units[unit]}';
}

String formatRate(int bytesPerSecond) => '${formatBytes(bytesPerSecond)}/s';

String formatElapsed(Duration elapsed) {
  final seconds = elapsed.isNegative ? 0 : elapsed.inSeconds;
  String two(int value) => value.toString().padLeft(2, '0');
  return '${two(seconds ~/ 3600)}:${two(seconds % 3600 ~/ 60)}:${two(seconds % 60)}';
}

/// How long a connection has been open, as the canvas writes it: `12 s`, `4 min`, `2 h`.
String formatAge(Duration age) {
  final seconds = age.isNegative ? 0 : age.inSeconds;
  if (seconds < 60) return '$seconds s';
  if (seconds < 3600) return '${seconds ~/ 60} min';
  return '${seconds ~/ 3600} h';
}

/// Where a connection goes, by name where the core knows one: `www.example.com:443`.
String connectionTarget(String destination, String domain) {
  if (domain.isEmpty) return destination;
  final colon = destination.lastIndexOf(':');
  return colon < 0 ? domain : '$domain${destination.substring(colon)}';
}

/// A program by its file: `C:\Program Files\App\app.exe` and `/usr/bin/app` are `app`.
String processName(String path) {
  final file = path.substring(path.lastIndexOf(RegExp(r'[\\/]')) + 1);
  return file.toLowerCase().endsWith('.exe') ? file.substring(0, file.length - 4) : file;
}

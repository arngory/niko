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

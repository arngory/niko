// What dart:io can open towards a local daemon on this system (docs/desktop/README.md, «Что решить», п. 2): the
// daemon's worker listens on a named pipe on Windows and on a unix socket elsewhere. Prints what works; never fails.
import 'dart:io';

Future<void> main() async {
  stdout.writeln('${Platform.operatingSystem} ${Platform.operatingSystemVersion}, Dart ${Platform.version.split(' ').first}');
  final directory = Directory.systemTemp.createTempSync('niko-probe-');
  final path = '${directory.path}${Platform.pathSeparator}probe.sock';
  try {
    final server = await ServerSocket.bind(InternetAddress(path, type: InternetAddressType.unix), 0);
    final client = await Socket.connect(InternetAddress(path, type: InternetAddressType.unix), 0);
    client.destroy();
    await server.close();
    stdout.writeln('unix socket: works');
  } catch (error) {
    stdout.writeln('unix socket: does not work ($error)');
  }
  if (Platform.isWindows) {
    // a pipe every Windows has, opened as a file: reading and writing a pipe this way is not a socket, and
    // overlapped I/O is not there
    for (final name in [r'\\.\pipe\InitShutdown', r'\\.\pipe\lsass']) {
      try {
        final file = await File(name).open(mode: FileMode.append);
        await file.close();
        stdout.writeln('named pipe as a file ($name): opens');
      } catch (error) {
        stdout.writeln('named pipe as a file ($name): does not open ($error)');
      }
    }
  }
  directory.deleteSync(recursive: true);
}

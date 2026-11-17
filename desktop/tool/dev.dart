// Stage 2b's way to run the window against the daemon in development mode (docs/desktop/README.md):
//
//   dart run tool/dev.dart --boxdd <path to boxdd> --config <file.json> [--port 18080]
//
// Starts `boxdd run --listen 127.0.0.1:<port>` and the window built with the development link. That mode of the
// daemon is gRPC over TCP with no check of the client at all, and boxdd itself listens on whatever address it is
// given: this script is the only way the daemon is started in that mode, and it gives it 127.0.0.1 and a port,
// nothing else. A developer's machine only; a release has neither the mode nor this script.
import 'dart:io';

Never usage([String? why]) {
  if (why != null) stderr.writeln(why);
  stderr.writeln('usage: dart run tool/dev.dart --boxdd <path> --config <file.json> [--port <1024..65535>]');
  exit(2);
}

Future<void> main(List<String> arguments) async {
  String? boxdd, config;
  var port = 18080;
  for (var i = 0; i < arguments.length; i += 2) {
    if (i + 1 >= arguments.length) usage('${arguments[i]} takes a value');
    switch (arguments[i]) {
      case '--boxdd':
        boxdd = arguments[i + 1];
      case '--config':
        config = arguments[i + 1];
      case '--port':
        // a number and nothing else: no address can be smuggled in through this argument
        final value = RegExp(r'^[0-9]{4,5}$').hasMatch(arguments[i + 1]) ? int.parse(arguments[i + 1]) : 0;
        if (value < 1024 || value > 65535) usage('--port takes a number from 1024 to 65535');
        port = value;
      default:
        usage('unknown argument ${arguments[i]}');
    }
  }
  if (boxdd == null || config == null) usage();
  if (!File(boxdd).existsSync()) usage('no daemon at $boxdd');
  if (!File(config).existsSync()) usage('no config at $config');

  final address = '127.0.0.1:$port';
  final work = Directory.systemTemp.createTempSync('niko-dev-');
  stdout.writeln('the daemon on $address (development only, no access control), its files in ${work.path}');
  final daemon = await Process.start(boxdd, ['run', '--listen', address, '-D', work.path],
      mode: ProcessStartMode.inheritStdio);
  final device = Platform.isWindows ? 'windows' : 'linux';
  final window = await Process.start(
    Platform.isWindows ? 'flutter.bat' : 'flutter',
    [
      'run', '-d', device, '--dart-define=NIKO_DEV=true',
      '-a', '--daemon', '-a', address, '-a', '--config', '-a', File(config).absolute.path,
    ],
    mode: ProcessStartMode.inheritStdio,
  );
  final code = await window.exitCode;
  daemon.kill();
  await daemon.exitCode;
  exit(code);
}

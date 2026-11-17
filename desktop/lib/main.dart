import 'dart:io';

import 'package:flutter/widgets.dart';

import 'app.dart';
import 'daemon/daemon.dart';
import 'daemon/endpoint.dart';
import 'platform/places.dart';
import 'state/connection_controller.dart';
import 'state/window_settings.dart';

/// The development link is compiled in only when asked for: `--dart-define=NIKO_DEV=true`. A build without it has
/// no way to reach a daemon over TCP at all, whatever its arguments say; a release is built without it.
const _dev = bool.fromEnvironment('NIKO_DEV');

/// `niko_desktop --daemon 127.0.0.1:<port> --config <file.json>` (development builds): the daemon started as
/// `boxdd run --listen 127.0.0.1:<port>`, and the ready config the connect button starts.
Future<void> main(List<String> arguments) async {
  Daemon? daemon;
  var address = '';
  File? config;
  // all of the development link stands in this branch: without NIKO_DEV the compiler drops it whole, and the gRPC
  // client over TCP with it
  if (_dev) {
    for (var i = 0; i + 1 < arguments.length; i++) {
      if (arguments[i] == '--daemon') {
        final endpoint = DevEndpoint.parse(arguments[i + 1]);
        if (endpoint == null) {
          stderr.writeln('--daemon takes 127.0.0.1:<port> and nothing else');
          exit(2);
        }
        daemon = GrpcDaemon.dev(endpoint);
        address = endpoint.toString();
      } else if (arguments[i] == '--config') {
        config = File(arguments[i + 1]);
      }
    }
  }
  final file = config;
  final controller = ConnectionController(
    daemon: daemon,
    // read at each press: the file may be edited between two starts
    loadConfig: file?.readAsString,
  )..attach();
  // the view chosen last time, before the first frame: the window does not open in one view and turn to the other
  final settings = await WindowSettings.load(settingsFile());
  runApp(NikoApp(
    controller: controller,
    settings: settings,
    daemonAddress: address,
    configName: file == null ? '' : file.uri.pathSegments.last,
  ));
}

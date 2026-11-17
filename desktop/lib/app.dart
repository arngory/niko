import 'package:flutter/widgets.dart';

import 'screens/main_screen.dart';
import 'state/connection_controller.dart';
import 'theme/niko_theme.dart';

/// The window: the design system's theme by the system's light or dark, and the main screen.
class NikoApp extends StatelessWidget {
  const NikoApp({
    super.key,
    required this.controller,
    this.daemonAddress = '',
    this.configName = '',
    this.locale,
    this.brightness,
    this.now = DateTime.now,
  });

  final ConnectionController controller;
  final String daemonAddress;
  final String configName;

  /// For tests; the system's otherwise.
  final Locale? locale;
  final Brightness? brightness;
  final DateTime Function() now;

  @override
  Widget build(BuildContext context) {
    return WidgetsApp(
      title: 'Niko',
      debugShowCheckedModeBanner: false,
      color: NikoColors.dark.surface0,
      locale: locale,
      supportedLocales: const [Locale('en'), Locale('ru')],
      textStyle: NikoType.body.style(NikoColors.dark.ink),
      pageRouteBuilder: <T>(RouteSettings settings, WidgetBuilder builder) =>
          PageRouteBuilder<T>(settings: settings, pageBuilder: (context, _, _) => builder(context)),
      home: Builder(
        builder: (context) {
          final colors = NikoTheme.forBrightness(brightness ?? MediaQuery.platformBrightnessOf(context));
          return NikoTheme(
            colors: colors,
            child: DefaultTextStyle(
              style: NikoType.body.style(colors.ink),
              child: MainScreen(
                controller: controller,
                daemonAddress: daemonAddress,
                configName: configName,
                now: now,
              ),
            ),
          );
        },
      ),
    );
  }
}

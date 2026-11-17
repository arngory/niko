import 'package:flutter/widgets.dart';

import 'tokens.g.dart';

export 'tokens.g.dart';

/// The design system's colours for the subtree: dark or light, as the system says.
class NikoTheme extends InheritedWidget {
  const NikoTheme({super.key, required this.colors, required super.child});

  final NikoColors colors;

  static NikoColors of(BuildContext context) => context.dependOnInheritedWidgetOfExactType<NikoTheme>()!.colors;

  static NikoColors forBrightness(Brightness brightness) =>
      brightness == Brightness.dark ? NikoColors.dark : NikoColors.light;

  @override
  bool updateShouldNotify(NikoTheme oldWidget) => colors != oldWidget.colors;
}

extension NikoTextStyle on NikoTextToken {
  /// The token as a text style: the line height and the letter spacing are relative in the tokens.
  TextStyle style(Color color, {FontWeight? weight}) => TextStyle(
        fontFamily: mono ? NikoType.monoFamily : NikoType.sansFamily,
        fontSize: size,
        height: height / size,
        fontWeight: weight ?? this.weight,
        letterSpacing: spacingEm * size,
        color: color,
        leadingDistribution: TextLeadingDistribution.even,
        decoration: TextDecoration.none,
      );
}

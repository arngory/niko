import 'dart:math' as math;

import 'package:flutter/widgets.dart';

import '../theme/niko_theme.dart';

/// The icons of the design system: Lucide's outlines on a 24 grid, as SVG path data, the same strings the canvas
/// draws. A circle or a line of the original is written as a path.
abstract final class NikoIcons {
  static const power = 'M12 2v10 M18.4 6.6a9 9 0 1 1-12.77.04';
  static const alert = 'M2 12a10 10 0 1 0 20 0a10 10 0 1 0-20 0 M12 8v4 M12 16h.01';
  static const refresh = 'M21 12a9 9 0 1 1-9-9c2.52 0 4.93 1 6.74 2.74L21 8 M21 3v5h-5';
  static const trash = 'M3 6h18 M19 6v14c0 1-1 2-2 2H7c-1 0-2-1-2-2V6 M8 6V4c0-1 1-2 2-2h4c1 0 2 1 2 2v2';
  static const close = 'M18 6L6 18 M6 6l12 12';
  static const search = 'M3 11a8 8 0 1 0 16 0a8 8 0 1 0-16 0 M21 21l-4.3-4.3';

  static const all = [power, alert, refresh, trash, close, search];
}

/// An outline icon: [data] stroked with the icon stroke of the tokens, scaled with [size].
class NikoIcon extends StatelessWidget {
  const NikoIcon(this.data, {super.key, this.size = NikoSize.icon, required this.color});

  final String data;
  final double size;
  final Color color;

  @override
  Widget build(BuildContext context) =>
      ExcludeSemantics(child: CustomPaint(size: Size.square(size), painter: _IconPainter(data, color)));
}

class _IconPainter extends CustomPainter {
  _IconPainter(this.data, this.color);

  final String data;
  final Color color;

  @override
  void paint(Canvas canvas, Size size) {
    final scale = size.shortestSide / NikoSize.icon;
    canvas.scale(scale);
    canvas.drawPath(
      parseSvgPath(data),
      Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = NikoStroke.icon
        ..strokeCap = StrokeCap.round
        ..strokeJoin = StrokeJoin.round
        ..color = color,
    );
  }

  @override
  bool shouldRepaint(_IconPainter oldDelegate) => data != oldDelegate.data || color != oldDelegate.color;
}

/// SVG path data as a [Path]: the commands the icons use (M L H V C A Z, absolute and relative). Anything else
/// throws, so an icon copied with a command this does not know fails in a test, not on a user's screen.
Path parseSvgPath(String data) {
  final tokens = RegExp(r'[MmLlHhVvCcAaZz]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?')
      .allMatches(data)
      .map((match) => match.group(0)!)
      .toList();
  final rest = data.replaceAll(RegExp(r'[MmLlHhVvCcAaZz]|-?(?:\d+\.?\d*|\.\d+)(?:[eE][-+]?\d+)?|[\s,]'), '');
  if (rest.isNotEmpty) throw FormatException('path data this does not read: "$rest"', data);
  final path = Path();
  var index = 0;
  var x = 0.0, y = 0.0, startX = 0.0, startY = 0.0;
  var command = '';
  bool isCommand(String token) => token.length == 1 && RegExp(r'[A-Za-z]').hasMatch(token);
  double number() {
    if (index >= tokens.length || isCommand(tokens[index])) throw FormatException('a number is missing', data);
    return double.parse(tokens[index++]);
  }

  // an arc's two flags may stand together with no space ("a9 9 0 1 1-12.77.04" is written with one, "011" is not)
  double flag() {
    final token = tokens[index];
    if (token == '0' || token == '1') {
      index++;
      return double.parse(token);
    }
    if (token.length > 1 && (token[0] == '0' || token[0] == '1')) {
      tokens[index] = token.substring(1);
      return double.parse(token[0]);
    }
    throw FormatException('an arc flag is missing', data);
  }

  while (index < tokens.length) {
    if (isCommand(tokens[index])) {
      command = tokens[index++];
    } else if (command.isEmpty) {
      throw FormatException('path data does not start with a command', data);
    } else if (command == 'M') {
      command = 'L'; // further pairs after a moveto are linetos
    } else if (command == 'm') {
      command = 'l';
    }
    final relative = command == command.toLowerCase();
    switch (command.toUpperCase()) {
      case 'M':
        final nx = number() + (relative ? x : 0), ny = number() + (relative ? y : 0);
        path.moveTo(nx, ny);
        x = startX = nx;
        y = startY = ny;
      case 'L':
        final nx = number() + (relative ? x : 0), ny = number() + (relative ? y : 0);
        path.lineTo(nx, ny);
        x = nx;
        y = ny;
      case 'H':
        x = number() + (relative ? x : 0);
        path.lineTo(x, y);
      case 'V':
        y = number() + (relative ? y : 0);
        path.lineTo(x, y);
      case 'C':
        final ox = relative ? x : 0.0, oy = relative ? y : 0.0;
        final x1 = number() + ox, y1 = number() + oy, x2 = number() + ox, y2 = number() + oy;
        final nx = number() + ox, ny = number() + oy;
        path.cubicTo(x1, y1, x2, y2, nx, ny);
        x = nx;
        y = ny;
      case 'A':
        final rx = number(), ry = number(), rotation = number();
        final large = flag() == 1, sweep = flag() == 1;
        final nx = number() + (relative ? x : 0), ny = number() + (relative ? y : 0);
        path.arcToPoint(Offset(nx, ny),
            radius: Radius.elliptical(rx, ry), rotation: rotation * math.pi / 180, largeArc: large, clockwise: sweep);
        x = nx;
        y = ny;
      case 'Z':
        path.close();
        x = startX;
        y = startY;
    }
  }
  return path;
}

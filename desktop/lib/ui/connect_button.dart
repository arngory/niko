import 'dart:math' as math;

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../theme/niko_theme.dart';
import 'icons.dart';

/// What the connect button shows (m-connect on the canvas).
enum ConnectLook { off, connecting, on, error }

/// The connect button of the view Простой: a circle of [NikoSize.connect] with a hairline halo around it; the halo
/// is a turning arc while connecting. D-MAIN-04.
class ConnectButton extends StatefulWidget {
  const ConnectButton({super.key, required this.look, required this.label, required this.onPressed});

  final ConnectLook look;

  /// What a press does, for a screen reader: the button has no text.
  final String label;

  /// Null: the button does nothing now (no daemon to ask).
  final VoidCallback? onPressed;

  @override
  State<ConnectButton> createState() => _ConnectButtonState();
}

class _ConnectButtonState extends State<ConnectButton> with SingleTickerProviderStateMixin {
  late final AnimationController _spin =
      AnimationController(vsync: this, duration: const Duration(milliseconds: 1200));
  bool _pressed = false;
  bool _focused = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    _syncSpin();
  }

  @override
  void didUpdateWidget(ConnectButton oldWidget) {
    super.didUpdateWidget(oldWidget);
    _syncSpin();
  }

  // the arc turns only while connecting, and stands still for a user who asked the system for less motion
  void _syncSpin() {
    final turn = widget.look == ConnectLook.connecting && !MediaQuery.disableAnimationsOf(context);
    if (turn && !_spin.isAnimating) {
      _spin.repeat();
    } else if (!turn && _spin.isAnimating) {
      _spin.stop();
    }
  }

  @override
  void dispose() {
    _spin.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final enabled = widget.onPressed != null;
    final (fill, ink, halo) = switch (widget.look) {
      ConnectLook.on => (_pressed ? colors.inkPressed : colors.ink, colors.onInk, colors.lineStrong),
      ConnectLook.error => (colors.dangerSoft, colors.danger, colors.danger),
      _ => (_pressed ? colors.surface3 : colors.surface2, colors.ink, colors.line),
    };
    const outer = NikoSize.connect + 2 * NikoSpace.s4;
    return Semantics(
      button: true,
      enabled: enabled,
      label: widget.label,
      child: FocusableActionDetector(
        enabled: enabled,
        mouseCursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
        onShowFocusHighlight: (value) => setState(() => _focused = value),
        shortcuts: const {
          SingleActivator(LogicalKeyboardKey.enter): ActivateIntent(),
          SingleActivator(LogicalKeyboardKey.space): ActivateIntent(),
        },
        actions: {ActivateIntent: CallbackAction<ActivateIntent>(onInvoke: (_) => widget.onPressed?.call())},
        child: GestureDetector(
          behavior: HitTestBehavior.opaque,
          onTapDown: enabled ? (_) => setState(() => _pressed = true) : null,
          onTapUp: enabled ? (_) => setState(() => _pressed = false) : null,
          onTapCancel: enabled ? () => setState(() => _pressed = false) : null,
          onTap: widget.onPressed,
          child: Opacity(
            opacity: enabled ? 1 : NikoOpacity.disabled,
            child: SizedBox.square(
              dimension: outer,
              child: AnimatedBuilder(
                animation: _spin,
                builder: (context, child) => CustomPaint(
                  painter: _HaloPainter(
                    halo: halo,
                    arc: widget.look == ConnectLook.connecting ? colors.ink : null,
                    turn: _spin.value,
                    focus: _focused ? colors.ink : null,
                    gap: colors.surface0,
                  ),
                  child: child,
                ),
                child: Center(
                  child: DecoratedBox(
                    decoration: BoxDecoration(color: fill, shape: BoxShape.circle),
                    child: SizedBox.square(
                      dimension: NikoSize.connect,
                      child: Center(child: NikoIcon(NikoIcons.power, size: 40, color: ink)),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class _HaloPainter extends CustomPainter {
  _HaloPainter({required this.halo, required this.arc, required this.turn, required this.focus, required this.gap});

  final Color halo;
  final Color? arc;
  final double turn;
  final Color? focus;
  final Color gap;

  @override
  void paint(Canvas canvas, Size size) {
    final centre = size.center(Offset.zero);
    final radius = size.shortestSide / 2;
    final line = Paint()
      ..style = PaintingStyle.stroke
      ..strokeWidth = NikoStroke.hairline
      ..color = halo;
    canvas.drawCircle(centre, radius - NikoStroke.hairline / 2, line);
    if (arc != null) {
      // a quarter of the halo in ink, turning: the top border of the canvas' ring
      final rect = Rect.fromCircle(center: centre, radius: radius - NikoStroke.bold / 2);
      canvas.drawArc(
        rect,
        -math.pi * 3 / 4 + turn * 2 * math.pi,
        math.pi / 2,
        false,
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = NikoStroke.bold
          ..strokeCap = StrokeCap.round
          ..color = arc!,
      );
    }
    if (focus != null) {
      // the focus ring of the tokens around the button itself: 2 of the ground, then 2 of ink
      const button = NikoSize.connect / 2;
      canvas.drawCircle(centre, button + 1, Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..color = gap);
      canvas.drawCircle(centre, button + 3, Paint()
        ..style = PaintingStyle.stroke
        ..strokeWidth = 2
        ..color = focus!);
    }
  }

  @override
  bool shouldRepaint(_HaloPainter oldDelegate) =>
      halo != oldDelegate.halo ||
      arc != oldDelegate.arc ||
      turn != oldDelegate.turn ||
      focus != oldDelegate.focus ||
      gap != oldDelegate.gap;
}

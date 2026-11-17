import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../theme/niko_theme.dart';

/// What a control is under the pointer and the keyboard.
typedef PressState = ({bool pressed, bool focused, bool hovered});

/// A control that is pressed: the pointer, Enter and Space, the keyboard focus. What it looks like is the
/// [builder]'s; what a screen reader hears is the [semantics] around it.
class NikoPressable extends StatefulWidget {
  const NikoPressable({super.key, required this.onPressed, required this.builder, this.radius = NikoRadius.full});

  /// Null: the control does nothing now.
  final VoidCallback? onPressed;
  final Widget Function(BuildContext context, PressState state) builder;

  /// The corners of the focus ring.
  final double radius;

  @override
  State<NikoPressable> createState() => _NikoPressableState();
}

class _NikoPressableState extends State<NikoPressable> {
  bool _pressed = false;
  bool _focused = false;
  bool _hovered = false;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final enabled = widget.onPressed != null;
    return FocusableActionDetector(
      enabled: enabled,
      mouseCursor: enabled ? SystemMouseCursors.click : SystemMouseCursors.basic,
      onShowFocusHighlight: (value) => setState(() => _focused = value),
      onShowHoverHighlight: (value) => setState(() => _hovered = value),
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
        child: CustomPaint(
          foregroundPainter: _focused
              ? _FocusRingPainter(ink: colors.ink, gap: colors.surface0, radius: widget.radius)
              : null,
          child: Opacity(
            opacity: enabled ? 1 : NikoOpacity.disabled,
            child: widget.builder(context, (pressed: _pressed && enabled, focused: _focused, hovered: _hovered)),
          ),
        ),
      ),
    );
  }
}

/// The focus ring of the tokens: 2 of the ground, then 2 of ink, outside the control.
class _FocusRingPainter extends CustomPainter {
  _FocusRingPainter({required this.ink, required this.gap, required this.radius});

  final Color ink;
  final Color gap;
  final double radius;

  @override
  void paint(Canvas canvas, Size size) {
    for (final (grow, color) in [(1.0, gap), (3.0, ink)]) {
      final rect = (Offset.zero & size).inflate(grow);
      final corner = Radius.circular((radius + grow).clamp(0, rect.shortestSide / 2));
      canvas.drawRRect(
        RRect.fromRectAndRadius(rect, corner),
        Paint()
          ..style = PaintingStyle.stroke
          ..strokeWidth = 2
          ..color = color,
      );
    }
  }

  @override
  bool shouldRepaint(_FocusRingPainter oldDelegate) =>
      ink != oldDelegate.ink || gap != oldDelegate.gap || radius != oldDelegate.radius;
}

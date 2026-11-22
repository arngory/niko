import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../theme/niko_theme.dart';
import 'controls.dart';
import 'icons.dart';
import 'pressable.dart';

// The design system's components of the settings screens (components/bundle.css: m-field, m-switch, a settings
// page), as the phone's ui/settings/SettingsComponents.kt draws them. The list screens are made of them by hand
// (lib/settings/inbound_screen.dart; docs/ui/platforms.md, variant А).

/// What a field takes: text as typed, a port (digits, five at most), a secret (shown as dots).
/// What a field takes: a line of text, a port's digits, a secret shown as dots, or several lines (a list, a line an
/// item).
enum FieldInput { text, port, secret, lines }

/// m-fields-row > m-field--narrow: the width of a narrow field beside a wide one.
const double narrowField = 104;

/// A settings page: its name in a bar of size-bar, then its parts space-6 apart, scrolled when they do not fit.
class NikoSettingsPage extends StatelessWidget {
  const NikoSettingsPage({super.key, required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return ColoredBox(
      color: colors.surface0,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          SizedBox(
            height: NikoSize.bar,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s5),
              child: Align(
                alignment: AlignmentDirectional.centerStart,
                child: Semantics(header: true, child: Text(title, style: NikoType.headline.style(colors.ink))),
              ),
            ),
          ),
          Expanded(
            child: SingleChildScrollView(
              padding: const EdgeInsets.fromLTRB(NikoSpace.s5, NikoSpace.s2, NikoSpace.s5, NikoSpace.s8),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  for (final (i, child) in children.indexed) ...[
                    if (i > 0) const SizedBox(height: NikoSpace.s6),
                    child,
                  ],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// m-fields-row: fields side by side, space-3 apart; a narrow one [narrowField] wide, the others share the rest.
class NikoFieldsRow extends StatelessWidget {
  const NikoFieldsRow({super.key, required this.fields});

  /// Each field, and whether it is narrow.
  final List<(Widget, bool)> fields;

  @override
  Widget build(BuildContext context) => Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          for (final (i, (field, narrow)) in fields.indexed) ...[
            if (i > 0) const SizedBox(width: NikoSpace.s3),
            narrow ? SizedBox(width: narrowField, child: field) : Expanded(child: field),
          ],
        ],
      );
}

/// m-field: [label] over the control (surface-2, the stroke-bold border in ink while it has the focus), [mono] in
/// Geist Mono (m-field__control--mono), [trail] at its end (m-field__trail).
class NikoField extends StatefulWidget {
  const NikoField({
    super.key,
    required this.label,
    required this.value,
    required this.onChanged,
    this.input = FieldInput.text,
    this.mono = false,
    this.trail,
  });

  final String label;
  final String value;
  final ValueChanged<String> onChanged;
  final FieldInput input;
  final bool mono;
  final Widget? trail;

  @override
  State<NikoField> createState() => _NikoFieldState();
}

class _NikoFieldState extends State<NikoField> {
  late final _controller = TextEditingController(text: widget.value);
  final _focus = FocusNode();

  @override
  void initState() {
    super.initState();
    _focus.addListener(() => setState(() {}));
  }

  @override
  void didUpdateWidget(NikoField oldWidget) {
    super.didUpdateWidget(oldWidget);
    // a value changed elsewhere (a new password): the field shows it
    if (widget.value != _controller.text) _controller.text = widget.value;
  }

  @override
  void dispose() {
    _controller.dispose();
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final style = (widget.mono ? NikoType.code : NikoType.body).style(colors.ink);
    final lines = widget.input == FieldInput.lines;
    return Semantics(
      textField: true,
      label: widget.label,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ExcludeSemantics(child: Text(widget.label, maxLines: 1, style: NikoType.caption.style(colors.ink2))),
          const SizedBox(height: NikoSpace.s2),
          GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: _focus.requestFocus,
            child: Container(
              height: lines ? null : NikoSize.control,
              padding: lines
                  ? const EdgeInsets.symmetric(
                      horizontal: NikoSpace.s4 - NikoStroke.bold,
                      vertical: NikoSpace.s3 - NikoStroke.bold,
                    )
                  : const EdgeInsetsDirectional.only(start: NikoSpace.s4 - NikoStroke.bold, end: NikoSpace.s2 - NikoStroke.bold),
              decoration: BoxDecoration(
                color: colors.surface2,
                borderRadius: BorderRadius.circular(NikoRadius.sm),
                border: Border.all(width: NikoStroke.bold, color: _focus.hasFocus ? colors.ink : const Color(0x00000000)),
              ),
              child: Row(
                children: [
                  Expanded(
                    child: EditableText(
                      controller: _controller,
                      focusNode: _focus,
                      style: style,
                      cursorColor: colors.ink,
                      backgroundCursorColor: colors.ink2,
                      selectionColor: colors.ink.withValues(alpha: 0.24),
                      // a list: six lines seen, more scrolled
                      minLines: lines ? 6 : null,
                      maxLines: lines ? 6 : 1,
                      obscureText: widget.input == FieldInput.secret,
                      obscuringCharacter: '•',
                      keyboardType: switch (widget.input) {
                        FieldInput.port => TextInputType.number,
                        FieldInput.lines => TextInputType.multiline,
                        _ => TextInputType.text,
                      },
                      inputFormatters: widget.input == FieldInput.port
                          ? [FilteringTextInputFormatter.digitsOnly, LengthLimitingTextInputFormatter(5)]
                          : null,
                      onChanged: widget.onChanged,
                    ),
                  ),
                  if (widget.trail != null) ...[const SizedBox(width: NikoSpace.s2), widget.trail!],
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// m-switch: off, a small ink-2 knob on surface-2 in a line-strong ring; on, a larger on-ink knob on ink.
class NikoSwitch extends StatelessWidget {
  const NikoSwitch({super.key, required this.on});

  final bool on;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final knob = on ? 24.0 : 16.0;
    return Container(
      width: 52,
      height: 32,
      decoration: BoxDecoration(
        color: on ? colors.ink : colors.surface2,
        borderRadius: BorderRadius.circular(NikoRadius.full),
        border: Border.all(width: NikoStroke.bold, color: on ? colors.ink : colors.lineStrong),
      ),
      child: Stack(
        children: [
          Positioned(
            left: on ? 22 : 6,
            top: on ? 2 : 6,
            child: Container(
              width: knob,
              height: knob,
              decoration: BoxDecoration(color: on ? colors.onInk : colors.ink2, shape: BoxShape.circle),
            ),
          ),
        ],
      ),
    );
  }
}

/// m-row with m-switch at its end: the whole row turns it, a switch to a screen reader; not [enabled], at
/// opacity-disabled. [sub]: a line under the title (m-row__sub). [divider]: the hairline above it, from space-4 in (a
/// row after another in its group).
class NikoSwitchRow extends StatelessWidget {
  const NikoSwitchRow({
    super.key,
    required this.title,
    this.sub,
    required this.on,
    required this.onChanged,
    this.enabled = true,
    this.divider = false,
  });

  final String title;
  final String? sub;
  final bool on;
  final ValueChanged<bool> onChanged;
  final bool enabled;
  final bool divider;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Semantics(
      toggled: on,
      enabled: enabled,
      label: sub == null ? title : '$title, $sub',
      excludeSemantics: true,
      child: NikoPressable(
        radius: 0,
        onPressed: enabled ? () => onChanged(!on) : null,
        builder: (context, state) => CustomPaint(
          foregroundPainter: divider ? _Hairline(colors.line) : null,
          child: ColoredBox(
            color: state.pressed ? colors.surface2 : const Color(0x00000000),
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 56),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s4, vertical: NikoSpace.s3),
                child: Row(
                  children: [
                    Expanded(child: _RowTitle(title, sub)),
                    const SizedBox(width: NikoSpace.s3),
                    NikoSwitch(on: on),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// m-row + m-row::before: the hairline at the row's top, from space-4 to the end.
class _Hairline extends CustomPainter {
  _Hairline(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) =>
      canvas.drawRect(Rect.fromLTWH(NikoSpace.s4, 0, size.width - NikoSpace.s4, NikoStroke.hairline), Paint()..color = color);

  @override
  bool shouldRepaint(_Hairline oldDelegate) => color != oldDelegate.color;
}

/// The title of a row (m-row__title) and the line under it, where there is one (m-row__sub).
class _RowTitle extends StatelessWidget {
  const _RowTitle(this.title, this.sub);

  final String title;
  final String? sub;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final line = Text(title, maxLines: 1, overflow: TextOverflow.ellipsis, style: NikoType.body.style(colors.ink));
    if (sub == null) return line;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisSize: MainAxisSize.min,
      children: [
        line,
        Text(sub!, maxLines: 1, overflow: TextOverflow.ellipsis, style: NikoType.bodySm.style(colors.ink2)),
      ],
    );
  }
}

/// m-row that opens something: the title, [value] in ink-2 (m-row__mono where [mono]: a number, an address) and a
/// chevron at its end; surface-2 pressed. A value too long for its half of the row ends in an ellipsis.
class NikoLinkRow extends StatelessWidget {
  const NikoLinkRow({
    super.key,
    required this.title,
    this.sub,
    this.value,
    this.mono = false,
    required this.onPressed,
    this.divider = false,
  });

  final String title;
  final String? sub;
  final String? value;
  final bool mono;
  final VoidCallback onPressed;
  final bool divider;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Semantics(
      button: true,
      label: [title, ?sub, ?value].join(', '),
      excludeSemantics: true,
      child: NikoPressable(
        radius: 0,
        onPressed: onPressed,
        builder: (context, state) => CustomPaint(
          foregroundPainter: divider ? _Hairline(colors.line) : null,
          child: ColoredBox(
            color: state.pressed ? colors.surface2 : const Color(0x00000000),
            child: ConstrainedBox(
              constraints: const BoxConstraints(minHeight: 56),
              child: Padding(
                padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s4, vertical: NikoSpace.s3),
                child: Row(
                  children: [
                    // with a value: each half the row, the value at its end
                    Expanded(child: _RowTitle(title, sub)),
                    if (value != null) ...[
                      const SizedBox(width: NikoSpace.s3),
                      Expanded(
                        child: Align(
                          alignment: AlignmentDirectional.centerEnd,
                          child: Text(
                            value!,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: (mono ? NikoType.code : NikoType.bodySm).style(colors.ink2),
                          ),
                        ),
                      ),
                    ],
                    const SizedBox(width: NikoSpace.s2),
                    NikoIcon(NikoIcons.chevronRight, size: 20, color: colors.ink2),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// m-row with m-seg at its end: one choice of several, each [cell] wide; a group of choices to a screen reader.
class NikoChoiceRow<T> extends StatelessWidget {
  const NikoChoiceRow({
    super.key,
    required this.title,
    required this.choices,
    required this.value,
    required this.onChanged,
    this.cell = 104,
    this.divider = false,
  });

  final String title;
  final List<(T, String)> choices;
  final T value;
  final ValueChanged<T> onChanged;
  final double cell;
  final bool divider;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return CustomPaint(
      foregroundPainter: divider ? _Hairline(colors.line) : null,
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 56),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s4, vertical: NikoSpace.s2),
          child: Row(
            children: [
              Expanded(child: ExcludeSemantics(child: _RowTitle(title, null))),
              const SizedBox(width: NikoSpace.s3),
              NikoSegmented<T>(label: title, choices: choices, value: value, onChanged: onChanged, cell: cell),
            ],
          ),
        ),
      ),
    );
  }
}

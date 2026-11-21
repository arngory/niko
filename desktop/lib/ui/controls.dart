import 'package:flutter/widgets.dart';

import '../theme/niko_theme.dart';
import 'icons.dart';
import 'pressable.dart';

/// A button that is an icon (m-iconbtn): [label] is what it does, for a screen reader, since it has no text.
class NikoIconButton extends StatelessWidget {
  const NikoIconButton({
    super.key,
    required this.icon,
    required this.label,
    required this.onPressed,
    this.muted = false,
    this.tonal = false,
    this.small = false,
  });

  final String icon;
  final String label;
  final VoidCallback? onPressed;

  /// The quieter ink, for an action beside the content (m-iconbtn--muted).
  final bool muted;

  /// On a fill of its own, for the actions of a screen's title (m-iconbtn--tonal).
  final bool tonal;

  /// The 20 icon (m-icon--sm); the button stays [NikoSize.tap].
  final bool small;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Semantics(
      container: true,
      button: true,
      enabled: onPressed != null,
      label: label,
      child: NikoPressable(
        onPressed: onPressed,
        builder: (context, state) => DecoratedBox(
          decoration: BoxDecoration(
            color: tonal ? (state.pressed ? colors.surface3 : colors.surface2) : (state.pressed ? colors.surface2 : null),
            shape: BoxShape.circle,
          ),
          child: SizedBox.square(
            dimension: NikoSize.tap,
            child: Center(
              child: NikoIcon(icon, size: small ? 20 : NikoSize.icon, color: muted ? colors.ink2 : colors.ink),
            ),
          ),
        ),
      ),
    );
  }
}

/// A button of words with no fill (m-btn--ghost).
class NikoGhostButton extends StatelessWidget {
  const NikoGhostButton({super.key, required this.label, required this.onPressed, this.danger = false});

  final String label;
  final VoidCallback? onPressed;
  final bool danger;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Semantics(
      container: true,
      button: true,
      enabled: onPressed != null,
      child: NikoPressable(
        onPressed: onPressed,
        builder: (context, state) => DecoratedBox(
          decoration: BoxDecoration(
            color: state.pressed ? colors.surface2 : null,
            borderRadius: BorderRadius.circular(NikoRadius.full),
          ),
          child: SizedBox(
            height: NikoSize.control,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s4),
              child: Center(
                widthFactor: 1,
                child: Text(
                  label,
                  maxLines: 1,
                  style: NikoType.body.style(danger ? colors.danger : colors.ink, weight: FontWeight.w600),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// A button of words on a fill of its own (m-btn--tonal): the action of a tool, not of the screen.
class NikoTonalButton extends StatelessWidget {
  const NikoTonalButton({super.key, required this.label, required this.onPressed});

  final String label;
  final VoidCallback? onPressed;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Semantics(
      container: true,
      button: true,
      enabled: onPressed != null,
      child: NikoPressable(
        onPressed: onPressed,
        builder: (context, state) => DecoratedBox(
          decoration: BoxDecoration(
            color: state.pressed ? colors.surface3 : colors.surface2,
            borderRadius: BorderRadius.circular(NikoRadius.full),
          ),
          child: SizedBox(
            height: NikoSize.control,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s6),
              child: Center(
                widthFactor: 1,
                child: Text(label, maxLines: 1, style: NikoType.body.style(colors.ink, weight: FontWeight.w600)),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// A field with its label above (m-field): a value typed in mono, a placeholder for what it takes.
class NikoTextField extends StatefulWidget {
  const NikoTextField({super.key, required this.label, this.placeholder = '', required this.controller, this.onSubmit});

  final String label;
  final String placeholder;
  final TextEditingController controller;
  final ValueChanged<String>? onSubmit;

  @override
  State<NikoTextField> createState() => _NikoTextFieldState();
}

class _NikoTextFieldState extends State<NikoTextField> {
  final _focus = FocusNode();

  @override
  void initState() {
    super.initState();
    _focus.addListener(_repaint);
    widget.controller.addListener(_repaint);
  }

  @override
  void didUpdateWidget(NikoTextField oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      oldWidget.controller.removeListener(_repaint);
      widget.controller.addListener(_repaint);
    }
  }

  void _repaint() => setState(() {});

  @override
  void dispose() {
    widget.controller.removeListener(_repaint);
    _focus.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Semantics(
      textField: true,
      label: widget.label,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          ExcludeSemantics(child: Text(widget.label, style: NikoType.caption.style(colors.ink2))),
          const SizedBox(height: NikoSpace.s1),
          GestureDetector(
            behavior: HitTestBehavior.opaque,
            onTap: _focus.requestFocus,
            child: DecoratedBox(
              decoration: BoxDecoration(
                color: colors.surface0,
                borderRadius: BorderRadius.circular(NikoRadius.sm),
                border: Border.all(
                  width: NikoStroke.bold,
                  color: _focus.hasFocus ? colors.ink : const Color(0x00000000),
                ),
              ),
              child: SizedBox(
                height: NikoSize.control,
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s4),
                  child: Stack(
                    alignment: AlignmentDirectional.centerStart,
                    children: [
                      if (widget.controller.text.isEmpty)
                        ExcludeSemantics(
                          child: Text(widget.placeholder, maxLines: 1, style: NikoType.code.style(colors.ink2)),
                        ),
                      EditableText(
                        controller: widget.controller,
                        focusNode: _focus,
                        style: NikoType.code.style(colors.ink),
                        cursorColor: colors.ink,
                        backgroundCursorColor: colors.ink2,
                        selectionColor: colors.ink.withValues(alpha: 0.24),
                        maxLines: 1,
                        onSubmitted: widget.onSubmit,
                      ),
                    ],
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

/// A short mark (m-tag): quiet, or in ink for the one that matters (m-tag--strong).
class NikoTag extends StatelessWidget {
  const NikoTag(this.text, {super.key, this.strong = false});

  final String text;
  final bool strong;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: strong ? colors.ink : colors.surface2,
        borderRadius: BorderRadius.circular(NikoRadius.full),
      ),
      child: SizedBox(
        height: 20,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s2),
          child: Center(
            widthFactor: 1,
            child: Text(
              text.toUpperCase(),
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: NikoType.label.style(strong ? colors.onInk : colors.ink2),
            ),
          ),
        ),
      ),
    );
  }
}

/// One choice of several, side by side (m-seg): the chosen one in ink. Every choice is [cell] wide.
class NikoSegmented<T> extends StatelessWidget {
  const NikoSegmented({
    super.key,
    required this.label,
    required this.choices,
    required this.value,
    required this.onChanged,
    this.cell = 120,
  });

  /// What is being chosen, for a screen reader.
  final String label;
  final List<(T, String)> choices;
  final T value;
  final ValueChanged<T> onChanged;
  final double cell;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Semantics(
      container: true,
      explicitChildNodes: true,
      label: label,
      child: DecoratedBox(
        decoration: BoxDecoration(color: colors.surface2, borderRadius: BorderRadius.circular(NikoRadius.full)),
        child: Padding(
          padding: const EdgeInsets.all(NikoSpace.s1),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              for (final (index, (choice, text)) in choices.indexed) ...[
                if (index > 0) const SizedBox(width: NikoSpace.s1),
                Semantics(
                  container: true,
                  button: true,
                  inMutuallyExclusiveGroup: true,
                  checked: choice == value,
                  child: NikoPressable(
                    onPressed: () => onChanged(choice),
                    builder: (context, state) => DecoratedBox(
                      decoration: BoxDecoration(
                        color: choice == value ? colors.ink : null,
                        borderRadius: BorderRadius.circular(NikoRadius.full),
                      ),
                      child: SizedBox(
                        width: cell,
                        height: 40,
                        child: Center(
                          child: Text(
                            text,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: NikoType.bodySm.style(
                              choice == value ? colors.onInk : colors.ink2,
                              weight: FontWeight.w600,
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ],
          ),
        ),
      ),
    );
  }
}

/// A line to type a search into (m-field__control with the search icon).
class NikoSearchField extends StatefulWidget {
  const NikoSearchField({super.key, required this.label, required this.onChanged});

  /// The placeholder, and the field's name for a screen reader.
  final String label;
  final ValueChanged<String> onChanged;

  @override
  State<NikoSearchField> createState() => _NikoSearchFieldState();
}

class _NikoSearchFieldState extends State<NikoSearchField> {
  final _controller = TextEditingController();
  final _focus = FocusNode();

  @override
  void initState() {
    super.initState();
    _focus.addListener(_repaint);
    _controller.addListener(_repaint);
  }

  void _repaint() => setState(() {});

  @override
  void dispose() {
    _focus.dispose();
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final style = NikoType.body.style(colors.ink);
    return Semantics(
      textField: true,
      label: widget.label,
      child: GestureDetector(
        behavior: HitTestBehavior.opaque,
        onTap: _focus.requestFocus,
        child: DecoratedBox(
          decoration: BoxDecoration(
            color: colors.surface2,
            borderRadius: BorderRadius.circular(NikoRadius.sm),
            border: Border.all(width: NikoStroke.bold, color: _focus.hasFocus ? colors.ink : const Color(0x00000000)),
          ),
          child: SizedBox(
            height: NikoSize.control,
            child: Padding(
              padding: const EdgeInsetsDirectional.only(start: NikoSpace.s4, end: NikoSpace.s2),
              child: Row(
                children: [
                  NikoIcon(NikoIcons.search, size: 20, color: colors.ink2),
                  const SizedBox(width: NikoSpace.s2),
                  Expanded(
                    child: Stack(
                      alignment: AlignmentDirectional.centerStart,
                      children: [
                        if (_controller.text.isEmpty)
                          ExcludeSemantics(
                            child: Text(widget.label, maxLines: 1, style: NikoType.body.style(colors.ink2)),
                          ),
                        EditableText(
                          controller: _controller,
                          focusNode: _focus,
                          style: style,
                          cursorColor: colors.ink,
                          backgroundCursorColor: colors.ink2,
                          selectionColor: colors.surface3,
                          maxLines: 1,
                          onChanged: widget.onChanged,
                        ),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// m-radio: a ring, and the dot inside it when [on].
class NikoRadio extends StatelessWidget {
  const NikoRadio({super.key, required this.on});

  final bool on;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Container(
      width: 20,
      height: 20,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        border: Border.all(width: NikoStroke.bold, color: on ? colors.ink : colors.lineStrong),
      ),
      alignment: Alignment.center,
      child: on
          ? Container(width: 10, height: 10, decoration: BoxDecoration(shape: BoxShape.circle, color: colors.ink))
          : null,
    );
  }
}

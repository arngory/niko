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

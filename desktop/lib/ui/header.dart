import 'package:flutter/widgets.dart';

import '../theme/niko_theme.dart';
import 'controls.dart';
import 'pressable.dart';

/// The window's header (n-header): the wordmark, the sections, the view switch. D-NAV-01, D-NAV-03.
///
/// The wordmark's cell and the two choices of the switch are one width, and all of it is [NikoSize.tap] tall
/// (the owner's decision: three equal cells). In a narrow window the sections go under the wordmark.
class NikoHeader<S> extends StatelessWidget {
  const NikoHeader({
    super.key,
    required this.sectionsLabel,
    required this.sections,
    required this.section,
    required this.onSection,
    required this.viewLabel,
    required this.views,
    required this.advanced,
    required this.onAdvanced,
  });

  final String sectionsLabel;
  final List<(S, String)> sections;
  final S section;
  final ValueChanged<S> onSection;
  final String viewLabel;

  /// The names of the two views: Простой, Расширенный.
  final (String, String) views;
  final bool advanced;
  final ValueChanged<bool> onAdvanced;

  static const _cell = 120.0;
  static const _narrow = 720.0;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final wordmark = Semantics(
      header: true,
      child: Text('niko', style: NikoType.title.style(colors.ink).copyWith(letterSpacing: -0.04 * 24)),
    );
    final nav = Semantics(
      container: true,
      explicitChildNodes: true,
      label: sectionsLabel,
      child: Wrap(
        spacing: NikoSpace.s6,
        children: [
          for (final (id, name) in sections)
            _NavItem(name: name, active: id == section, onPressed: () => onSection(id)),
        ],
      ),
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final narrow = constraints.maxWidth < _narrow;
        final gutter = narrow ? NikoSpace.s4 : NikoSpace.s8;
        // a narrow window: the two choices share what the wordmark leaves
        final cell = narrow ? ((constraints.maxWidth - 2 * gutter - 96) / 2).clamp(72.0, _cell) : _cell;
        final view = NikoSegmented<bool>(
          label: viewLabel,
          choices: [(false, views.$1), (true, views.$2)],
          value: advanced,
          onChanged: onAdvanced,
          cell: cell,
        );
        return Padding(
          padding: EdgeInsets.symmetric(horizontal: gutter, vertical: narrow ? NikoSpace.s2 : NikoSpace.s3),
          child: narrow
              ? Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Row(children: [wordmark, const Spacer(), view]),
                    if (sections.length > 1) nav,
                  ],
                )
              : Row(
                  children: [
                    SizedBox(
                      width: _cell + 2 * NikoSpace.s1,
                      height: NikoSize.tap,
                      child: Center(child: wordmark),
                    ),
                    const SizedBox(width: NikoSpace.s6),
                    Expanded(child: sections.length > 1 ? nav : const SizedBox.shrink()),
                    const SizedBox(width: NikoSpace.s6),
                    view,
                  ],
                ),
        );
      },
    );
  }
}

/// A section's name in the header (n-nav__item): quiet, in ink and heavier when it is the one shown.
class _NavItem extends StatelessWidget {
  const _NavItem({required this.name, required this.active, required this.onPressed});

  final String name;
  final bool active;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Semantics(
      container: true,
      button: true,
      selected: active,
      child: NikoPressable(
        onPressed: onPressed,
        radius: NikoRadius.sm,
        builder: (context, state) => SizedBox(
          height: NikoSize.tap,
          child: Center(
            widthFactor: 1,
            child: Text(
              name,
              maxLines: 1,
              style: NikoType.bodySm.style(
                active || state.hovered ? colors.ink : colors.ink2,
                weight: active ? FontWeight.w600 : FontWeight.w400,
              ),
            ),
          ),
        ),
      ),
    );
  }
}

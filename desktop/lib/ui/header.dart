import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../theme/niko_theme.dart';
import 'controls.dart';
import 'icons.dart';
import 'pressable.dart';

/// The window's header (n-header): the wordmark, the sections, the view switch. D-NAV-01, D-NAV-03.
///
/// The wordmark's cell and the two choices of the switch are one width, and all of it is [NikoSize.tap] tall
/// (the owner's decision: three equal cells). A window too narrow for the sections in one line folds them into
/// three bars left of the wordmark, which open them as a list (n-header--compact, n-menu).
class NikoHeader<S> extends StatefulWidget {
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

  /// Narrower than this the sections no longer stand in one line with the wordmark and the switch.
  static const narrow = 760.0;

  @override
  State<NikoHeader<S>> createState() => _NikoHeaderState<S>();
}

class _NikoHeaderState<S> extends State<NikoHeader<S>> {
  static const _cell = 120.0;
  final _menu = OverlayPortalController();
  final _anchor = LayerLink();

  void _toggleMenu() => setState(() => _menu.isShowing ? _menu.hide() : _menu.show());

  void _closeMenu() {
    if (_menu.isShowing) setState(_menu.hide);
  }

  void _choose(S section) {
    _closeMenu();
    widget.onSection(section);
  }

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final wordmark = Semantics(
      header: true,
      child: Text('niko', style: NikoType.title.style(colors.ink).copyWith(letterSpacing: -0.04 * 24)),
    );
    return LayoutBuilder(
      builder: (context, constraints) {
        final narrow = constraints.maxWidth < NikoHeader.narrow;
        if (!narrow && _menu.isShowing) {
          // the window grew wide while the list was open: the sections are in the line again
          WidgetsBinding.instance.addPostFrameCallback((_) => mounted ? _closeMenu() : null);
        }
        final gutter = narrow ? NikoSpace.s4 : NikoSpace.s8;
        // a narrow window: the two choices share what the bars and the wordmark leave, up to 120 each
        final cell = narrow ? ((constraints.maxWidth - 2 * gutter - 128) / 2).clamp(64.0, _cell) : _cell;
        final view = NikoSegmented<bool>(
          label: widget.viewLabel,
          choices: [(false, widget.views.$1), (true, widget.views.$2)],
          value: widget.advanced,
          onChanged: widget.onAdvanced,
          cell: cell,
        );
        if (narrow) {
          return Padding(
            padding: EdgeInsets.symmetric(horizontal: gutter, vertical: NikoSpace.s2),
            child: Row(
              children: [
                if (widget.sections.length > 1) ...[_menuButton(colors), const SizedBox(width: NikoSpace.s2)],
                wordmark,
                const Spacer(),
                view,
              ],
            ),
          );
        }
        return Padding(
          padding: EdgeInsets.symmetric(horizontal: gutter, vertical: NikoSpace.s3),
          child: Row(
            children: [
              SizedBox(width: _cell + 2 * NikoSpace.s1, height: NikoSize.tap, child: Center(child: wordmark)),
              const SizedBox(width: NikoSpace.s6),
              Expanded(
                child: widget.sections.length > 1
                    ? Semantics(
                        container: true,
                        explicitChildNodes: true,
                        label: widget.sectionsLabel,
                        child: Wrap(
                          // the advanced view's six sections stand closer, to keep to one line
                          spacing: widget.sections.length > 5 ? NikoSpace.s4 : NikoSpace.s6,
                          children: [
                            for (final (id, name) in widget.sections)
                              _NavItem(name: name, active: id == widget.section, onPressed: () => widget.onSection(id)),
                          ],
                        ),
                      )
                    : const SizedBox.shrink(),
              ),
              const SizedBox(width: NikoSpace.s6),
              view,
            ],
          ),
        );
      },
    );
  }

  // three bars that open the sections as a list under them; a press outside it, Escape or a choice closes it
  Widget _menuButton(NikoColors colors) {
    return OverlayPortal(
      controller: _menu,
      overlayChildBuilder: (context) => CompositedTransformFollower(
        link: _anchor,
        targetAnchor: Alignment.bottomLeft,
        offset: const Offset(0, NikoSpace.s1),
        child: Align(
          alignment: Alignment.topLeft,
          child: TapRegion(
            groupId: _menu,
            onTapOutside: (_) => _closeMenu(),
            child: CallbackShortcuts(
              bindings: {const SingleActivator(LogicalKeyboardKey.escape): _closeMenu},
              child: FocusScope(
                autofocus: true,
                child: Semantics(
                  container: true,
                  explicitChildNodes: true,
                  label: widget.sectionsLabel,
                  child: DecoratedBox(
                    decoration: BoxDecoration(
                      color: colors.surface1,
                      borderRadius: BorderRadius.circular(NikoRadius.md),
                    ),
                    child: ConstrainedBox(
                      constraints: const BoxConstraints(minWidth: 240),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(vertical: NikoSpace.s2),
                        child: IntrinsicWidth(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              for (final (id, name) in widget.sections)
                                _NavItem(
                                  name: name,
                                  active: id == widget.section,
                                  inMenu: true,
                                  onPressed: () => _choose(id),
                                ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
      child: CompositedTransformTarget(
        link: _anchor,
        child: TapRegion(
          groupId: _menu,
          child: Semantics(
            expanded: _menu.isShowing,
            child: NikoIconButton(icon: NikoIcons.menu, label: widget.sectionsLabel, onPressed: _toggleMenu),
          ),
        ),
      ),
    );
  }
}

/// A section's name (n-nav__item): quiet, in ink and heavier when it is the one shown. In the narrow window's list
/// (n-menu) a row of its own, larger.
class _NavItem extends StatelessWidget {
  const _NavItem({required this.name, required this.active, required this.onPressed, this.inMenu = false});

  final String name;
  final bool active;
  final VoidCallback onPressed;
  final bool inMenu;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final style = (inMenu ? NikoType.body : NikoType.bodySm);
    return Semantics(
      container: true,
      button: true,
      selected: active,
      child: NikoPressable(
        onPressed: onPressed,
        radius: NikoRadius.sm,
        builder: (context, state) => DecoratedBox(
          decoration: BoxDecoration(color: inMenu && state.pressed ? colors.surface2 : null),
          child: SizedBox(
            height: NikoSize.tap,
            child: Padding(
              padding: EdgeInsets.symmetric(horizontal: inMenu ? NikoSpace.s4 : 0),
              child: Align(
                alignment: inMenu ? AlignmentDirectional.centerStart : Alignment.center,
                widthFactor: inMenu ? null : 1,
                child: Text(
                  name,
                  maxLines: 1,
                  style: style.style(
                    active || state.hovered ? colors.ink : colors.ink2,
                    weight: active ? FontWeight.w600 : FontWeight.w400,
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

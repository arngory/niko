import 'package:flutter/widgets.dart';

import '../theme/niko_theme.dart';
import 'icons.dart';
import 'pressable.dart';

/// A banner (m-banner): an icon, a title and what to know under it. An error has the icon and the words, not the
/// colour alone.
class NikoBanner extends StatelessWidget {
  const NikoBanner({
    super.key,
    required this.icon,
    required this.title,
    this.text = '',
    this.danger = false,
    this.action,
    this.onAction,
  });

  final String icon;
  final String title;
  final String text;
  final bool danger;

  /// A word of what can be done about it, at the right (m-banner__action); [onAction] null: shown, not pressable.
  final String? action;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final ink = danger ? colors.danger : colors.ink;
    return Semantics(
      container: true,
      liveRegion: danger,
      child: DecoratedBox(
        decoration: BoxDecoration(
          color: danger ? colors.dangerSoft : colors.surface2,
          borderRadius: BorderRadius.circular(NikoRadius.md),
        ),
        child: Padding(
          // the action is a tap tall and takes the place of the padding beside it (m-banner__action's margins)
          padding: EdgeInsetsDirectional.only(start: NikoSpace.s4, end: action == null ? NikoSpace.s4 : NikoSpace.s2),
          child: Row(
            children: [
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: NikoSpace.s3),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      NikoIcon(icon, size: 20, color: ink),
                      const SizedBox(width: NikoSpace.s3),
                      Expanded(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Text(title, style: NikoType.bodySm.style(ink, weight: FontWeight.w600)),
                            if (text.isNotEmpty) Text(text, style: NikoType.bodySm.style(ink)),
                          ],
                        ),
                      ),
                    ],
                  ),
                ),
              ),
              if (action != null)
                Semantics(
                  container: true,
                  button: true,
                  enabled: onAction != null,
                  child: NikoPressable(
                    onPressed: onAction,
                    builder: (context, state) => DecoratedBox(
                      decoration: BoxDecoration(
                        color: state.pressed ? ink.withValues(alpha: 0.08) : null,
                        borderRadius: BorderRadius.circular(NikoRadius.full),
                      ),
                      child: SizedBox(
                        height: NikoSize.tap,
                        child: Padding(
                          padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s3),
                          child: Center(
                            widthFactor: 1,
                            child: Text(action!, style: NikoType.bodySm.style(ink, weight: FontWeight.w600)),
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}

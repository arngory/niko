import 'package:flutter/widgets.dart';

import '../theme/niko_theme.dart';
import 'icons.dart';

/// A banner (m-banner): an icon, a title and what to know under it. An error has the icon and the words, not the
/// colour alone.
class NikoBanner extends StatelessWidget {
  const NikoBanner({super.key, required this.icon, required this.title, this.text = '', this.danger = false});

  final String icon;
  final String title;
  final String text;
  final bool danger;

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
          padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s4, vertical: NikoSpace.s3),
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
    );
  }
}

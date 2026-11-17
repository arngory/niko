import 'package:flutter/widgets.dart';

import '../theme/niko_theme.dart';

enum StatusTone { on, quiet, error }

/// The status under the connect button (m-status): a word, and under it what ticks, in mono. D-MAIN-02, D-MAIN-06.
class StatusBlock extends StatelessWidget {
  const StatusBlock({super.key, required this.label, this.detail = '', required this.tone});

  final String label;
  final String detail;
  final StatusTone tone;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final labelColor = switch (tone) {
      StatusTone.on => colors.ink,
      StatusTone.quiet => colors.ink2,
      StatusTone.error => colors.danger,
    };
    return Semantics(
      liveRegion: true,
      container: true,
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(label, style: NikoType.title.style(labelColor), textAlign: TextAlign.center),
          const SizedBox(height: NikoSpace.s1),
          // the line keeps its height when there is nothing to say, so the button does not jump
          Text(
            detail.isEmpty ? ' ' : detail,
            style: NikoType.code.style(colors.ink2),
            textAlign: TextAlign.center,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
        ],
      ),
    );
  }
}

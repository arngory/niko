import 'package:flutter/widgets.dart';

import '../theme/niko_theme.dart';

/// A group of rows with its overline (m-section, m-overline, m-list).
class NikoSection extends StatelessWidget {
  const NikoSection({super.key, required this.overline, required this.rows});

  final String overline;
  final List<Widget> rows;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s4),
          child: Semantics(
            header: true,
            child: Text(overline.toUpperCase(), style: NikoType.label.style(colors.ink2)),
          ),
        ),
        const SizedBox(height: NikoSpace.s2),
        ClipRRect(
          borderRadius: BorderRadius.circular(NikoRadius.md),
          child: ColoredBox(color: colors.surface1, child: Column(children: rows)),
        ),
      ],
    );
  }
}

/// A row that only says something (m-row with a value): a title, and the value in mono at the right.
class NikoInfoRow extends StatelessWidget {
  const NikoInfoRow({super.key, required this.title, required this.value});

  final String title;
  final String value;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return MergeSemantics(
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 56),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s4, vertical: NikoSpace.s3),
          child: Row(
            children: [
              Text(title, style: NikoType.body.style(colors.ink)),
              const SizedBox(width: NikoSpace.s3),
              Expanded(
                child: Text(
                  value,
                  style: NikoType.code.style(colors.ink2),
                  textAlign: TextAlign.end,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// A row of a title and a line under it (m-row__title, m-row__sub): a value said in words, or one too long to stand
/// beside its title, in mono.
class NikoTextRow extends StatelessWidget {
  const NikoTextRow({super.key, required this.title, required this.sub, this.mono = false});

  final String title;
  final String sub;
  final bool mono;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return MergeSemantics(
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: 56),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s4, vertical: NikoSpace.s3),
          child: SizedBox(
            width: double.infinity,
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(title, style: NikoType.body.style(colors.ink)),
                Text(sub, style: (mono ? NikoType.code : NikoType.bodySm).style(colors.ink2)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

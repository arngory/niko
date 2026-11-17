import 'package:flutter/widgets.dart';

import '../l10n/strings.dart';
import '../state/stun_tool.dart';
import '../theme/niko_theme.dart';
import '../ui/banner.dart';
import '../ui/controls.dart';
import '../ui/icons.dart';
import '../ui/list.dart';

/// The tools beside the log (B · Log on the canvas): the NAT behaviour, by a STUN test through the core's default
/// way out. Its rows say how the network the tunnel leads to maps and lets in UDP: what calls and games care about.
class ToolsPanel extends StatefulWidget {
  const ToolsPanel({super.key, required this.stun});

  final StunTool stun;

  @override
  State<ToolsPanel> createState() => _ToolsPanelState();
}

class _ToolsPanelState extends State<ToolsPanel> {
  final _server = TextEditingController();

  @override
  void initState() {
    super.initState();
    widget.stun.addListener(_changed);
  }

  @override
  void didUpdateWidget(ToolsPanel oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.stun != widget.stun) {
      oldWidget.stun.removeListener(_changed);
      widget.stun.addListener(_changed);
    }
  }

  @override
  void dispose() {
    widget.stun.removeListener(_changed);
    _server.dispose();
    super.dispose();
  }

  void _changed() => setState(() {});

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final strings = NikoStrings.of(Localizations.localeOf(context));
    final stun = widget.stun;
    final result = stun.progress;
    final rows = <Widget>[
      if (result != null && result.externalAddress.isNotEmpty)
        NikoTextRow(title: strings.natAddress, sub: result.externalAddress, mono: true),
      if (result != null && result.isFinal && result.error.isEmpty) ...[
        NikoTextRow(
          title: strings.natMapping,
          sub: result.natTypeSupported ? strings.nat(result.mapping) : strings.natUnsupported,
        ),
        if (result.natTypeSupported) NikoTextRow(title: strings.natFiltering, sub: strings.nat(result.filtering)),
      ],
      if (result != null && result.latencyMs > 0)
        NikoInfoRow(title: strings.natLatency, value: '${result.latencyMs} ms'),
    ];
    return DecoratedBox(
      decoration: BoxDecoration(color: colors.surface1, borderRadius: BorderRadius.circular(NikoRadius.md)),
      child: Padding(
        padding: const EdgeInsets.all(NikoSpace.s5),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Semantics(header: true, child: Text(strings.tools, style: NikoType.headline.style(colors.ink))),
            const SizedBox(height: NikoSpace.s4),
            Semantics(
              header: true,
              child: Text(strings.natTitle, style: NikoType.body.style(colors.ink, weight: FontWeight.w600)),
            ),
            const SizedBox(height: NikoSpace.s3),
            NikoTextField(
              label: strings.stunServer,
              placeholder: 'host:3478',
              controller: _server,
              onSubmit: stun.available ? stun.run : null,
            ),
            const SizedBox(height: NikoSpace.s3),
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: NikoTonalButton(
                label: strings.stunDetect,
                onPressed: stun.available && !stun.running ? () => stun.run(_server.text) : null,
              ),
            ),
            if (!stun.available) ...[
              const SizedBox(height: NikoSpace.s3),
              Text(
                stun.stopped ? strings.stunStopped : strings.stunNeedsCore,
                style: NikoType.bodySm.style(colors.ink2),
              ),
            ],
            if (stun.error.isNotEmpty) ...[
              const SizedBox(height: NikoSpace.s3),
              NikoBanner(icon: NikoIcons.alert, title: strings.stunFailed, text: stun.error, danger: true),
            ],
            if (rows.isNotEmpty) ...[
              const SizedBox(height: NikoSpace.s3),
              ClipRRect(
                borderRadius: BorderRadius.circular(NikoRadius.md),
                child: ColoredBox(
                  color: colors.surface0,
                  child: Column(children: rows),
                ),
              ),
            ],
          ],
        ),
      ),
    );
  }
}

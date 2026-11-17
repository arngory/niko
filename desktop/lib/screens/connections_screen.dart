import 'dart:async';

import 'package:flutter/widgets.dart';

import '../daemon/daemon.dart';
import '../l10n/strings.dart';
import '../state/connections_feed.dart';
import '../theme/niko_theme.dart';
import '../ui/banner.dart';
import '../ui/controls.dart';
import '../ui/format.dart';
import '../ui/icons.dart';

/// The open connections (D-CONN-01): where each goes, for which program, by which rule and through what, how much
/// it carried and for how long; each can be closed, and all of them at once.
class ConnectionsScreen extends StatefulWidget {
  const ConnectionsScreen({super.key, required this.feed, required this.running, this.now = DateTime.now});

  final ConnectionsFeed feed;

  /// The core runs: there are connections to speak of.
  final bool running;
  final DateTime Function() now;

  @override
  State<ConnectionsScreen> createState() => _ConnectionsScreenState();
}

class _ConnectionsScreenState extends State<ConnectionsScreen> {
  String _query = '';
  Timer? _clock;

  @override
  void initState() {
    super.initState();
    widget.feed.addListener(_changed);
    // the ages move on though nothing else does
    _clock = Timer.periodic(const Duration(seconds: 15), (_) => setState(() {}));
  }

  @override
  void didUpdateWidget(ConnectionsScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.feed != widget.feed) {
      oldWidget.feed.removeListener(_changed);
      widget.feed.addListener(_changed);
    }
  }

  @override
  void dispose() {
    widget.feed.removeListener(_changed);
    _clock?.cancel();
    super.dispose();
  }

  void _changed() => setState(() {});

  bool _matches(ConnectionInfo connection) {
    if (_query.isEmpty) return true;
    final text = [
      connection.destination,
      connection.domain,
      connection.process,
      connection.outbound,
      connection.rule,
      connection.network,
      connection.protocol,
    ].join(' ').toLowerCase();
    return text.contains(_query);
  }

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final strings = NikoStrings.of(Localizations.localeOf(context));
    final all = widget.feed.open;
    final shown = all.where(_matches).toList();
    final now = widget.now();

    final title = Semantics(
      header: true,
      child: Text(strings.sectionConnections, style: NikoType.title.style(colors.ink)),
    );
    final count = Text(strings.connectionsOpen(all.length), style: NikoType.bodySm.style(colors.ink2));
    final search = NikoSearchField(
      label: strings.connectionsSearch,
      onChanged: (value) => setState(() => _query = value.trim().toLowerCase()),
    );
    final closeAll = NikoGhostButton(
      label: strings.connectionsCloseAll,
      danger: true,
      onPressed: all.isEmpty ? null : widget.feed.closeAll,
    );

    return Padding(
      padding: const EdgeInsets.fromLTRB(NikoSpace.s8, NikoSpace.s4, NikoSpace.s8, NikoSpace.s8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          LayoutBuilder(
            builder: (context, constraints) => constraints.maxWidth < 720
                ? Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      Row(
                        children: [
                          title,
                          const SizedBox(width: NikoSpace.s3),
                          Expanded(child: count),
                          closeAll,
                        ],
                      ),
                      const SizedBox(height: NikoSpace.s2),
                      search,
                    ],
                  )
                : Row(
                    children: [
                      title,
                      const SizedBox(width: NikoSpace.s3),
                      Expanded(child: count),
                      SizedBox(width: 320, child: search),
                      const SizedBox(width: NikoSpace.s3),
                      closeAll,
                    ],
                  ),
          ),
          const SizedBox(height: NikoSpace.s4),
          if (widget.feed.error.isNotEmpty) ...[
            NikoBanner(
              icon: NikoIcons.alert,
              title: strings.connectionsCloseFailed,
              text: widget.feed.error,
              danger: true,
            ),
            const SizedBox(height: NikoSpace.s4),
          ],
          if (!widget.running)
            NikoBanner(icon: NikoIcons.alert, title: strings.connectionsIdle)
          else if (shown.isNotEmpty)
            Flexible(
              child: Semantics(
                container: true,
                explicitChildNodes: true,
                label: strings.connectionsList,
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(NikoRadius.md),
                  child: ColoredBox(
                    color: colors.surface1,
                    child: ListView.builder(
                      shrinkWrap: true,
                      itemCount: shown.length,
                      itemBuilder: (context, index) => _ConnectionRow(
                        key: ValueKey(shown[index].id),
                        connection: shown[index],
                        now: now,
                        strings: strings,
                        onClose: () => widget.feed.close(shown[index].id),
                      ),
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

/// A connection as a row (m-row): the target in mono, what it is under it, the bytes and the age at the right.
class _ConnectionRow extends StatelessWidget {
  const _ConnectionRow({
    super.key,
    required this.connection,
    required this.now,
    required this.strings,
    required this.onClose,
  });

  final ConnectionInfo connection;
  final DateTime now;
  final NikoStrings strings;
  final VoidCallback onClose;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final target = connectionTarget(connection.destination, connection.domain);
    final what = [
      if (connection.process.isNotEmpty) processName(connection.process),
      [connection.network.toUpperCase(), if (connection.protocol.isNotEmpty) '(${connection.protocol})'].join(' '),
      '${strings.rule}: ${connection.rule.isEmpty ? strings.ruleFinal : connection.rule} →',
    ].join(' · ');
    // what leaves this computer through a server stands out from what goes directly or nowhere
    final through = !const {'direct', 'block', 'dns', ''}.contains(connection.outboundType);
    final name = Text(target, maxLines: 1, overflow: TextOverflow.ellipsis, style: NikoType.code.style(colors.ink));
    final description = Row(
      children: [
        Flexible(
          flex: 3,
          child: Text(what, maxLines: 1, overflow: TextOverflow.ellipsis, style: NikoType.bodySm.style(colors.ink2)),
        ),
        if (connection.outbound.isNotEmpty) ...[
          const SizedBox(width: NikoSpace.s2),
          Flexible(child: NikoTag(connection.outbound, strong: through)),
        ],
      ],
    );
    final numbers = Text(
      '↓ ${formatBytes(connection.down)} ↑ ${formatBytes(connection.up)} · '
      '${formatAge(now.difference(connection.createdAt))}',
      maxLines: 1,
      overflow: TextOverflow.ellipsis,
      style: NikoType.code.style(colors.ink2),
    );
    final close = NikoIconButton(
      icon: NikoIcons.close,
      label: strings.connectionClose(target),
      muted: true,
      small: true,
      onPressed: onClose,
    );
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 56),
      child: Padding(
        padding: const EdgeInsetsDirectional.only(start: NikoSpace.s4, top: NikoSpace.s1, bottom: NikoSpace.s1),
        child: LayoutBuilder(
          builder: (context, constraints) {
            // a narrow window: the numbers go under the description, not beside it
            final narrow = constraints.maxWidth < 560;
            return Row(
              children: [
                Expanded(
                  child: MergeSemantics(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        name,
                        const SizedBox(height: 2),
                        description,
                        if (narrow) ...[const SizedBox(height: 2), numbers],
                      ],
                    ),
                  ),
                ),
                if (!narrow) ...[const SizedBox(width: NikoSpace.s3), numbers],
                close,
              ],
            );
          },
        ),
      ),
    );
  }
}

import 'package:flutter/widgets.dart';

import '../daemon/daemon.dart';
import '../l10n/strings.dart';
import '../state/rules_feed.dart';
import '../theme/niko_theme.dart';
import '../ui/banner.dart';
import '../ui/controls.dart';
import '../ui/fields.dart';
import '../ui/format.dart';
import '../ui/icons.dart';
import '../ui/list.dart';
import '../ui/pressable.dart';

/// Правила (D-RULES-01, -03, -05; the phone's ui/RouteFragment.kt): the route rules in the order the config takes
/// them, each turned on and off, moved, edited (its common fields: domains, addresses, ports, network, protocol,
/// programs, where it goes, «Обход DPI» on a direct one) or deleted; a new one; the Russian preset; and the rule-sets
/// the rules name, with when each was fetched and the update on demand.
class RulesScreen extends StatefulWidget {
  const RulesScreen({super.key, required this.feed, required this.linked});

  final RulesFeed feed;

  /// The window talks to the daemon: without it the store cannot be read.
  final bool linked;

  @override
  State<RulesScreen> createState() => _RulesScreenState();
}

class _RulesScreenState extends State<RulesScreen> {
  // the rule in the editor: one with id 0 is new; null: the list
  RuleRow? _editing;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final strings = NikoStrings.of(Localizations.localeOf(context));
    final feed = widget.feed;
    final editing = _editing;

    final List<Widget> body;
    if (!widget.linked) {
      body = [NikoBanner(icon: NikoIcons.alert, title: strings.storeNoLink)];
    } else if (editing != null) {
      body = [
        _RuleEditor(
          key: ValueKey(editing.id),
          rule: editing,
          strings: strings,
          onSave: (rule) async {
            final error = await feed.save(rule);
            if (error == null && mounted) setState(() => _editing = null);
            return error;
          },
          onCancel: () => setState(() => _editing = null),
        ),
      ];
    } else {
      body = [
        Wrap(
          spacing: NikoSpace.s3,
          runSpacing: NikoSpace.s3,
          children: [
            NikoTonalButton(label: strings.ruleAdd, onPressed: () => setState(() => _editing = const RuleRow())),
            NikoTonalButton(
              label: strings.presetRussia,
              onPressed: feed.updating
                  ? null
                  : () => feed.applyRussia(
                        blockedName: strings.presetRussiaBlocked,
                        directName: strings.presetRussiaDirect,
                      ),
            ),
          ],
        ),
        const SizedBox(height: NikoSpace.s2),
        Text(strings.presetRussiaSub, style: NikoType.bodySm.style(colors.ink2)),
        const SizedBox(height: NikoSpace.s4),
        if (feed.error.isNotEmpty) ...[
          NikoBanner(icon: NikoIcons.alert, title: strings.rulesUnread, text: feed.error, danger: true),
          const SizedBox(height: NikoSpace.s4),
        ],
        if (!feed.loaded)
          const SizedBox.shrink()
        else if (feed.rules.isEmpty)
          Text(strings.rulesEmpty, style: NikoType.body.style(colors.ink2))
        else
          Semantics(
            container: true,
            explicitChildNodes: true,
            label: strings.rulesList,
            child: NikoGroup(
              rows: [
                for (final (i, rule) in feed.rules.indexed)
                  _RuleTile(
                    key: ValueKey(rule.id),
                    rule: rule,
                    strings: strings,
                    divider: i > 0,
                    first: i == 0,
                    last: i == feed.rules.length - 1,
                    onEdit: () => setState(() => _editing = rule),
                    onEnabled: (on) => feed.setEnabled(rule, on),
                    onMove: (up) => feed.move(rule, up: up),
                    onDelete: () => feed.delete(rule),
                  ),
              ],
            ),
          ),
        const SizedBox(height: NikoSpace.s6),
        _RuleSets(feed: feed, strings: strings),
      ];
    }

    return SingleChildScrollView(
      padding: const EdgeInsets.fromLTRB(NikoSpace.s8, NikoSpace.s4, NikoSpace.s8, NikoSpace.s8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Semantics(header: true, child: Text(strings.sectionRules, style: NikoType.title.style(colors.ink))),
          const SizedBox(height: NikoSpace.s1),
          Text(strings.rulesSub, style: NikoType.bodySm.style(colors.ink2)),
          const SizedBox(height: NikoSpace.s4),
          ...body,
        ],
      ),
    );
  }
}

/// A rule's name: the app's «Rule N» where it has none (RuleEntity.displayName).
String ruleName(RuleRow rule, NikoStrings strings) =>
    rule.name.trim().isEmpty ? strings.ruleDisplayName(rule.id) : rule.name;

/// Where a rule goes, in words.
String ruleOutbound(int outbound, NikoStrings strings) => switch (outbound) {
      RuleOutbound.proxy => strings.routeProxy,
      RuleOutbound.direct => strings.routeDirect,
      RuleOutbound.block => strings.routeBlock,
      _ => strings.routeProfile(outbound),
    };

/// What a rule matches, as the app's summary (RuleEntity.mkSummary): its lists' first items, three at most.
String ruleSummary(RuleRow rule, NikoStrings strings) {
  final lines = <String>[
    if (rule.config.trim().isNotEmpty) strings.ruleOwnJson,
    for (final list in [rule.domains, rule.ip])
      ...list.split('\n').map((l) => l.trim()).where((l) => l.isNotEmpty),
    if (rule.source.trim().isNotEmpty) 'src ip: ${rule.source.trim()}',
    if (rule.sourcePort.trim().isNotEmpty) 'src port: ${rule.sourcePort.trim()}',
    if (rule.port.trim().isNotEmpty) 'dst port: ${rule.port.trim()}',
    if (rule.network.isNotEmpty) 'network: ${rule.network}',
    if (rule.protocol.trim().isNotEmpty) 'protocol: ${rule.protocol.trim()}',
    ...rule.programs,
    if (rule.packages > 0) '${strings.ruleApps}: ${rule.packages}',
  ];
  final shown = lines.take(3).join(', ');
  return lines.length > 3 ? '$shown…' : shown;
}

/// A rule as a row: its switch, its name, where it goes and what it matches; moved up and down, deleted; pressed,
/// edited.
class _RuleTile extends StatelessWidget {
  const _RuleTile({
    super.key,
    required this.rule,
    required this.strings,
    required this.divider,
    required this.first,
    required this.last,
    required this.onEdit,
    required this.onEnabled,
    required this.onMove,
    required this.onDelete,
  });

  final RuleRow rule;
  final NikoStrings strings;
  final bool divider;
  final bool first;
  final bool last;
  final VoidCallback onEdit;
  final ValueChanged<bool> onEnabled;
  final ValueChanged<bool> onMove;
  final VoidCallback onDelete;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final name = ruleName(rule, strings);
    final sub = [
      ruleOutbound(rule.outbound, strings),
      if (rule.dpiBypass && rule.outbound == RuleOutbound.direct) strings.ruleDpi,
      ruleSummary(rule, strings),
    ].where((part) => part.isNotEmpty).join(' · ');
    return CustomPaint(
      foregroundPainter: divider ? _Hairline(colors.line) : null,
      child: Row(
        children: [
          Semantics(
            toggled: rule.enabled,
            label: strings.ruleEnabledLabel(name),
            excludeSemantics: true,
            child: NikoPressable(
              onPressed: () => onEnabled(!rule.enabled),
              builder: (context, state) => Padding(
                padding: const EdgeInsets.all(NikoSpace.s3),
                child: NikoSwitch(on: rule.enabled),
              ),
            ),
          ),
          Expanded(
            child: Semantics(
              button: true,
              label: '$name, $sub',
              excludeSemantics: true,
              child: NikoPressable(
                radius: 0,
                onPressed: onEdit,
                builder: (context, state) => ColoredBox(
                  color: state.pressed ? colors.surface2 : const Color(0x00000000),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: NikoSpace.s3, horizontal: NikoSpace.s2),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(name,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: NikoType.body.style(rule.enabled ? colors.ink : colors.ink2)),
                        Text(sub, maxLines: 1, overflow: TextOverflow.ellipsis, style: NikoType.bodySm.style(colors.ink2)),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
          // the chevron turned up and down: the earlier rule is taken first
          RotatedBox(
            quarterTurns: 3,
            child: NikoIconButton(
              icon: NikoIcons.chevronRight,
              label: strings.ruleUpLabel(name),
              muted: true,
              small: true,
              onPressed: first ? null : () => onMove(true),
            ),
          ),
          RotatedBox(
            quarterTurns: 1,
            child: NikoIconButton(
              icon: NikoIcons.chevronRight,
              label: strings.ruleDownLabel(name),
              muted: true,
              small: true,
              onPressed: last ? null : () => onMove(false),
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(right: NikoSpace.s2),
            child: NikoIconButton(
              icon: NikoIcons.trash,
              label: strings.ruleDeleteLabel(name),
              muted: true,
              small: true,
              onPressed: onDelete,
            ),
          ),
        ],
      ),
    );
  }
}

/// The editor of a rule: its common fields; Save sends it to the daemon, which says why where it refuses it.
class _RuleEditor extends StatefulWidget {
  const _RuleEditor({super.key, required this.rule, required this.strings, required this.onSave, required this.onCancel});

  final RuleRow rule;
  final NikoStrings strings;

  /// The daemon's words for why it was not saved; null where it was.
  final Future<String?> Function(RuleRow rule) onSave;
  final VoidCallback onCancel;

  @override
  State<_RuleEditor> createState() => _RuleEditorState();
}

class _RuleEditorState extends State<_RuleEditor> {
  late RuleRow _draft = widget.rule;
  // the programs as typed: split into the list on saving only, so that a new line or a space in a path stays while
  // typing (the field takes its value back on every change)
  late String _programs = widget.rule.programs.join('\n');
  String? _error;
  bool _saving = false;

  void _set(RuleRow next) => setState(() => _draft = next);

  Future<void> _save() async {
    setState(() => _saving = true);
    final error = await widget.onSave(_draft.copyWith(
      programs: [for (final line in _programs.split('\n')) if (line.trim().isNotEmpty) line.trim()],
    ));
    if (!mounted) return;
    setState(() {
      _saving = false;
      _error = error;
    });
  }

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final strings = widget.strings;
    final rule = _draft;
    final outbounds = <(int, String)>[
      (RuleOutbound.proxy, strings.routeProxy),
      (RuleOutbound.direct, strings.routeDirect),
      (RuleOutbound.block, strings.routeBlock),
      // a profile the rule had: kept as it is, the editor offers no others yet
      if (widget.rule.outbound > 0) (widget.rule.outbound, strings.routeProfile(widget.rule.outbound)),
    ];
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Semantics(
          header: true,
          child: Text(rule.id == 0 ? strings.ruleAdd : ruleName(widget.rule, strings),
              style: NikoType.headline.style(colors.ink)),
        ),
        const SizedBox(height: NikoSpace.s4),
        NikoField(label: strings.ruleName, value: rule.name, onChanged: (v) => _set(_draft.copyWith(name: v))),
        const SizedBox(height: NikoSpace.s4),
        NikoField(
          label: strings.ruleDomains,
          value: rule.domains,
          input: FieldInput.lines,
          mono: true,
          onChanged: (v) => _set(_draft.copyWith(domains: v)),
        ),
        const SizedBox(height: NikoSpace.s1),
        Text(strings.ruleDomainsHint, style: NikoType.bodySm.style(colors.ink2)),
        const SizedBox(height: NikoSpace.s4),
        NikoField(
          label: strings.ruleIp,
          value: rule.ip,
          input: FieldInput.lines,
          mono: true,
          onChanged: (v) => _set(_draft.copyWith(ip: v)),
        ),
        const SizedBox(height: NikoSpace.s4),
        NikoFieldsRow(
          fields: [
            (
              NikoField(
                label: strings.rulePort,
                value: rule.port,
                mono: true,
                onChanged: (v) => _set(_draft.copyWith(port: v)),
              ),
              false,
            ),
            (
              NikoField(
                label: strings.ruleSourcePort,
                value: rule.sourcePort,
                mono: true,
                onChanged: (v) => _set(_draft.copyWith(sourcePort: v)),
              ),
              false,
            ),
          ],
        ),
        const SizedBox(height: NikoSpace.s4),
        NikoFieldsRow(
          fields: [
            (
              NikoField(
                label: strings.ruleSource,
                value: rule.source,
                mono: true,
                onChanged: (v) => _set(_draft.copyWith(source: v)),
              ),
              false,
            ),
            (
              NikoField(
                label: strings.ruleProtocol,
                value: rule.protocol,
                mono: true,
                onChanged: (v) => _set(_draft.copyWith(protocol: v)),
              ),
              false,
            ),
          ],
        ),
        const SizedBox(height: NikoSpace.s4),
        NikoField(
          label: strings.rulePrograms,
          value: _programs,
          input: FieldInput.lines,
          mono: true,
          onChanged: (v) => setState(() => _programs = v),
        ),
        const SizedBox(height: NikoSpace.s1),
        Text(strings.ruleProgramsHint, style: NikoType.bodySm.style(colors.ink2)),
        const SizedBox(height: NikoSpace.s4),
        LayoutBuilder(
          // the choices' cells narrow with the window, the row's title keeping some room
          builder: (context, constraints) {
            double cell(int count) =>
                ((constraints.maxWidth - 2 * NikoSpace.s4 - NikoSpace.s3 - 72) / count).clamp(48, 96);
            return NikoGroup(
              rows: [
                NikoChoiceRow<String>(
                  title: strings.ruleNetwork,
                  choices: [('', strings.ruleNetworkBoth), ('tcp', 'TCP'), ('udp', 'UDP')],
                  value: rule.network,
                  cell: cell(3),
                  onChanged: (v) => _set(_draft.copyWith(network: v)),
                ),
                NikoChoiceRow<int>(
                  title: strings.ruleOutbound,
                  choices: outbounds,
                  value: rule.outbound,
                  cell: cell(outbounds.length),
                  divider: true,
                  onChanged: (v) => _set(_draft.copyWith(outbound: v)),
                ),
                if (rule.outbound == RuleOutbound.direct)
                  NikoSwitchRow(
                    title: strings.ruleDpi,
                    sub: strings.ruleDpiSub,
                    on: rule.dpiBypass,
                    divider: true,
                    onChanged: (v) => _set(_draft.copyWith(dpiBypass: v)),
                  ),
              ],
            );
          },
        ),
        if (_error case final error?) ...[
          const SizedBox(height: NikoSpace.s4),
          NikoBanner(icon: NikoIcons.alert, title: strings.ruleNotSaved, text: error, danger: true),
        ],
        const SizedBox(height: NikoSpace.s4),
        Row(
          children: [
            NikoPrimaryButton(label: strings.ruleSave, onPressed: _saving ? null : _save),
            const SizedBox(width: NikoSpace.s3),
            NikoGhostButton(label: strings.ruleCancel, onPressed: widget.onCancel),
          ],
        ),
      ],
    );
  }
}

/// The rule-sets the rules name: each one's address, when it was fetched and what it holds, or why it is not here;
/// the update of all of them on demand.
class _RuleSets extends StatelessWidget {
  const _RuleSets({required this.feed, required this.strings});

  final RulesFeed feed;
  final NikoStrings strings;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final sets = feed.ruleSets.ruleSets;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Row(
          children: [
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Semantics(header: true, child: Text(strings.ruleSetsTitle, style: NikoType.headline.style(colors.ink))),
                  Text(strings.ruleSetsSub(feed.ruleSets.updateHours), style: NikoType.bodySm.style(colors.ink2)),
                ],
              ),
            ),
            const SizedBox(width: NikoSpace.s3),
            NikoTonalButton(
              label: feed.updating ? strings.ruleSetsUpdating : strings.ruleSetsUpdate,
              onPressed: feed.updating || sets.isEmpty ? null : feed.updateAll,
            ),
          ],
        ),
        const SizedBox(height: NikoSpace.s3),
        if (feed.notice case final notice?) ...[
          Semantics(
            liveRegion: true,
            container: true,
            child: Text(
              feed.noticeFailed ? '${strings.ruleSetsFailed}: $notice' : notice,
              style: NikoType.bodySm.style(feed.noticeFailed ? colors.danger : colors.ink),
            ),
          ),
          const SizedBox(height: NikoSpace.s3),
        ],
        if (sets.isEmpty)
          Text(strings.ruleSetsNone, style: NikoType.body.style(colors.ink2))
        else
          NikoGroup(
            rows: [
              for (final (i, set) in sets.indexed)
                _RuleSetTile(key: ValueKey(set.item), set: set, strings: strings, divider: i > 0),
            ],
          ),
      ],
    );
  }
}

/// A list by its address without its query: a token may be in it.
String ruleSetName(RuleSetRow set) {
  final uri = Uri.tryParse(set.url);
  if (uri == null || uri.host.isEmpty) return set.url;
  return '${uri.host}${uri.path}';
}

/// What is known of a list, in words.
String ruleSetState(RuleSetRow set, NikoStrings strings) => [
      if (!set.fetchable)
        strings.ruleSetNotHttps
      else if (set.updating)
        strings.ruleSetsUpdating
      else if (set.downloaded)
        strings.ruleSetFetched(formatBytes(set.size), formatMoment(DateTime.fromMillisecondsSinceEpoch(set.updated * 1000)))
      else
        strings.ruleSetNotYet,
      if (set.downloaded) strings.ruleSetKinds(set.kinds),
      if (!set.enabled) strings.ruleSetOff,
    ].where((part) => part.isNotEmpty).join(' · ');

class _RuleSetTile extends StatelessWidget {
  const _RuleSetTile({super.key, required this.set, required this.strings, required this.divider});

  final RuleSetRow set;
  final NikoStrings strings;
  final bool divider;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final name = ruleSetName(set);
    final state = ruleSetState(set, strings);
    return Semantics(
      container: true,
      label: [name, state, if (set.error.isNotEmpty) set.error].join(', '),
      excludeSemantics: true,
      child: CustomPaint(
        foregroundPainter: divider ? _Hairline(colors.line) : null,
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s4, vertical: NikoSpace.s3),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(name, maxLines: 1, overflow: TextOverflow.ellipsis, style: NikoType.code.style(colors.ink)),
              Text(state, maxLines: 1, overflow: TextOverflow.ellipsis, style: NikoType.bodySm.style(colors.ink2)),
              if (set.error.isNotEmpty)
                Text(set.error, maxLines: 2, overflow: TextOverflow.ellipsis, style: NikoType.bodySm.style(colors.danger)),
            ],
          ),
        ),
      ),
    );
  }
}

/// m-row + m-row::before: the hairline at the row's top, from space-4 to the end.
class _Hairline extends CustomPainter {
  _Hairline(this.color);

  final Color color;

  @override
  void paint(Canvas canvas, Size size) =>
      canvas.drawRect(Rect.fromLTWH(NikoSpace.s4, 0, size.width - NikoSpace.s4, NikoStroke.hairline), Paint()..color = color);

  @override
  bool shouldRepaint(_Hairline oldDelegate) => color != oldDelegate.color;
}

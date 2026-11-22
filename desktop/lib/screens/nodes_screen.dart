import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../daemon/daemon.dart';
import '../l10n/strings.dart';
import '../state/profiles_feed.dart';
import '../theme/niko_theme.dart';
import '../ui/banner.dart';
import '../ui/controls.dart';
import '../ui/format.dart';
import '../ui/icons.dart';
import '../ui/pressable.dart';

/// Узлы (B-Profiles, D-MAIN-07): the groups beside the profiles of the one chosen; a profile pressed is the one the
/// window connects with; one is added from a link (+) and deleted, with a way back for a while. A subscription's
/// group says its traffic, expiry and last update, and updates on demand (D-SUB-01…03).
class NodesScreen extends StatelessWidget {
  const NodesScreen({super.key, required this.feed, required this.linked, required this.onAdd});

  final ProfilesFeed feed;

  /// The window talks to the daemon: without it the store cannot be read.
  final bool linked;
  final VoidCallback onAdd;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final strings = NikoStrings.of(Localizations.localeOf(context));
    final group = feed.groups.where((g) => g.id == feed.groupId).firstOrNull;

    final Widget profiles;
    if (!linked) {
      profiles = NikoBanner(icon: NikoIcons.alert, title: strings.storeNoLink);
    } else if (!feed.loaded) {
      profiles = const SizedBox.shrink();
    } else if (feed.profiles.isEmpty) {
      profiles = Text(strings.groupEmpty, style: NikoType.body.style(colors.ink2));
    } else {
      profiles = Flexible(
        child: Semantics(
          container: true,
          explicitChildNodes: true,
          label: strings.profilesList,
          child: ClipRRect(
            borderRadius: BorderRadius.circular(NikoRadius.md),
            child: ColoredBox(
              color: colors.surface1,
              child: ListView.builder(
                shrinkWrap: true,
                itemCount: feed.profiles.length,
                itemBuilder: (context, i) {
                  final p = feed.profiles[i];
                  return _ProfileRow(
                    key: ValueKey(p.id),
                    profile: p,
                    selected: p.id == feed.selected,
                    divider: i > 0,
                    onPressed: () => feed.select(p.id),
                    onDelete: () => feed.delete(p),
                    onShare: () => feed.share(p, copyText),
                  );
                },
              ),
            ),
          ),
        ),
      );
    }

    return Padding(
      padding: const EdgeInsets.fromLTRB(NikoSpace.s8, NikoSpace.s4, NikoSpace.s8, NikoSpace.s8),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          SizedBox(
            width: 260,
            child: _Groups(feed: feed, strings: strings, onAdd: linked ? onAdd : null),
          ),
          const SizedBox(width: NikoSpace.s6),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                Semantics(
                  header: true,
                  child: Text(
                    group == null ? strings.sectionNodes : groupName(group, strings),
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: NikoType.title.style(colors.ink),
                  ),
                ),
                if (group?.info case final info? when linked) ...[
                  const SizedBox(height: NikoSpace.s2),
                  _SubscriptionLine(
                    info: info,
                    strings: strings,
                    onUpdate: info.updating ? null : () => feed.refresh(group!.id),
                  ),
                ],
                const SizedBox(height: NikoSpace.s4),
                if (feed.error.isNotEmpty) ...[
                  NikoBanner(icon: NikoIcons.alert, title: strings.storeUnreachable, text: feed.error, danger: true),
                  const SizedBox(height: NikoSpace.s4),
                ],
                profiles,
                if (feed.deleted case final gone?) ...[
                  const SizedBox(height: NikoSpace.s4),
                  _Snackbar(text: strings.profileDeleted(gone.saved.row.name), action: strings.undo, onAction: feed.undo),
                ] else if (feed.copied case final name?) ...[
                  const SizedBox(height: NikoSpace.s4),
                  _Snackbar(text: strings.linkCopied(name)),
                ] else if (feed.updated case final line?) ...[
                  const SizedBox(height: NikoSpace.s4),
                  _Snackbar(text: feed.updateFailed ? '${strings.subscriptionUpdateFailed}: $line' : line),
                ],
              ],
            ),
          ),
        ],
      ),
    );
  }
}

/// A group's name: the ungrouped one is the window's to name.
String groupName(GroupRow group, NikoStrings strings) =>
    group.ungrouped || group.name.isEmpty ? strings.groupOwn : group.name;

/// What a subscription's provider said of it: the traffic used of the allowed (or used alone), the expiry; empty
/// where it said nothing.
String subscriptionUsage(SubscriptionInfo info, NikoStrings strings) => [
      if (info.total > 0)
        strings.subscriptionTraffic(formatBytes(info.used), formatBytes(info.total))
      else if (info.used > 0)
        strings.subscriptionUsed(formatBytes(info.used)),
      if (info.expire > 0) strings.subscriptionExpires(formatDate(DateTime.fromMillisecondsSinceEpoch(info.expire * 1000))),
    ].join(' · ');

/// A subscription's group under its title: what the provider said, when it was updated, its announcement, and the
/// update on demand (disabled while one runs).
class _SubscriptionLine extends StatelessWidget {
  const _SubscriptionLine({required this.info, required this.strings, required this.onUpdate});

  final SubscriptionInfo info;
  final NikoStrings strings;
  final VoidCallback? onUpdate;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final when = info.lastUpdated > 0
        ? strings.subscriptionUpdated(formatMoment(DateTime.fromMillisecondsSinceEpoch(info.lastUpdated * 1000)))
        : strings.subscriptionNever;
    final usage = subscriptionUsage(info, strings);
    return Row(
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(usage.isEmpty ? when : '$usage · $when',
                  maxLines: 1, overflow: TextOverflow.ellipsis, style: NikoType.bodySm.style(colors.ink2)),
              if (info.announce.isNotEmpty)
                Text(info.announce, maxLines: 2, overflow: TextOverflow.ellipsis, style: NikoType.bodySm.style(colors.ink2)),
            ],
          ),
        ),
        const SizedBox(width: NikoSpace.s3),
        NikoTonalButton(
          label: info.updating ? strings.subscriptionUpdating : strings.subscriptionUpdate,
          onPressed: onUpdate,
        ),
      ],
    );
  }
}

/// The panel of groups (m-list with its title): the one shown on surface-2.
class _Groups extends StatelessWidget {
  const _Groups({required this.feed, required this.strings, required this.onAdd});

  final ProfilesFeed feed;
  final NikoStrings strings;
  final VoidCallback? onAdd;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return ClipRRect(
      borderRadius: BorderRadius.circular(NikoRadius.md),
      child: ColoredBox(
        color: colors.surface1,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          mainAxisSize: MainAxisSize.min,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(NikoSpace.s5, NikoSpace.s3, NikoSpace.s3, NikoSpace.s1),
              child: Row(
                children: [
                  Expanded(
                    child: Semantics(header: true, child: Text(strings.groups, style: NikoType.headline.style(colors.ink))),
                  ),
                  NikoIconButton(icon: NikoIcons.plus, label: strings.add, tonal: true, onPressed: onAdd),
                ],
              ),
            ),
            for (final g in feed.groups)
              _GroupRow(
                key: ValueKey(g.id),
                name: groupName(g, strings),
                sub: g.ungrouped
                    ? strings.groupOwnSub(g.profiles)
                    : g.subscription
                        ? [
                            strings.groupSubscriptionSub(g.profiles),
                            if (g.info case final info?) subscriptionUsage(info, strings),
                          ].where((part) => part.isNotEmpty).join(' · ')
                        : strings.groupBasicSub(g.profiles),
                chosen: g.id == feed.groupId,
                onPressed: () => feed.choose(g.id),
              ),
            const SizedBox(height: NikoSpace.s2),
          ],
        ),
      ),
    );
  }
}

class _GroupRow extends StatelessWidget {
  const _GroupRow({super.key, required this.name, required this.sub, required this.chosen, required this.onPressed});

  final String name;
  final String sub;
  final bool chosen;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Semantics(
      selected: chosen,
      button: true,
      label: '$name, $sub',
      excludeSemantics: true,
      child: NikoPressable(
        radius: 0,
        onPressed: onPressed,
        builder: (context, state) => ColoredBox(
          color: chosen || state.pressed ? colors.surface2 : const Color(0x00000000),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s5, vertical: NikoSpace.s3),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: NikoType.body.style(colors.ink, weight: chosen ? FontWeight.w600 : null)),
                Text(sub, maxLines: 1, overflow: TextOverflow.ellipsis, style: NikoType.bodySm.style(colors.ink2)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// The label of a bean's type in a row's tag: the app's names, VMess's bean holding VLESS too.
String typeLabel(String type) => switch (type) {
      'vmess' => 'V2Ray',
      'ss' => 'Shadowsocks',
      'socks' => 'SOCKS',
      'hysteria' => 'Hysteria',
      'trojan' => 'Trojan',
      'trojan_go' => 'Trojan-Go',
      'naive' => 'naive',
      'tuic' => 'TUIC',
      'anytls' => 'AnyTLS',
      'mieru' => 'Mieru',
      'http' => 'HTTP',
      _ => type,
    };

/// A profile as a row (m-row): the radio of the selected one, its name, its type and address under it.
class _ProfileRow extends StatelessWidget {
  const _ProfileRow({
    super.key,
    required this.profile,
    required this.selected,
    required this.divider,
    required this.onPressed,
    required this.onDelete,
    required this.onShare,
  });

  final ProfileRow profile;
  final bool selected;
  final bool divider;
  final VoidCallback onPressed;
  final VoidCallback onDelete;
  final VoidCallback onShare;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final strings = NikoStrings.of(Localizations.localeOf(context));
    final body = Semantics(
      inMutuallyExclusiveGroup: true,
      checked: selected,
      label: '${profile.name}, ${typeLabel(profile.type)}, ${profile.address}',
      excludeSemantics: true,
      child: NikoPressable(
        radius: 0,
        onPressed: onPressed,
        builder: (context, state) => ColoredBox(
          color: state.pressed ? colors.surface2 : const Color(0x00000000),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: 72),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(NikoSpace.s4, NikoSpace.s3, NikoSpace.s2, NikoSpace.s3),
              child: Row(
                children: [
                  NikoRadio(on: selected),
                  const SizedBox(width: NikoSpace.s3),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        Text(profile.name,
                            maxLines: 1,
                            overflow: TextOverflow.ellipsis,
                            style: NikoType.body.style(colors.ink, weight: selected ? FontWeight.w600 : null)),
                        const SizedBox(height: 2),
                        Row(
                          children: [
                            NikoTag(typeLabel(profile.type)),
                            const SizedBox(width: NikoSpace.s2),
                            Flexible(
                              child: Text(profile.address,
                                  maxLines: 1, overflow: TextOverflow.ellipsis, style: NikoType.code.style(colors.ink2)),
                            ),
                          ],
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
    return CustomPaint(
      foregroundPainter: divider ? _Hairline(colors.line) : null,
      child: Row(
        children: [
          Expanded(child: body),
          NikoIconButton(
            icon: NikoIcons.copy,
            label: strings.profileShare(profile.name),
            muted: true,
            small: true,
            onPressed: onShare,
          ),
          Padding(
            padding: const EdgeInsets.only(right: NikoSpace.s2),
            child: NikoIconButton(
              icon: NikoIcons.trash,
              label: strings.profileDelete(profile.name),
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

/// m-snackbar: what was done, on surface-3, and the way back where there is one.
class _Snackbar extends StatelessWidget {
  const _Snackbar({required this.text, this.action, this.onAction});

  final String text;
  final String? action;
  final VoidCallback? onAction;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Semantics(
      liveRegion: true,
      container: true,
      child: Align(
        alignment: AlignmentDirectional.centerStart,
        child: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 560),
          child: DecoratedBox(
            decoration: BoxDecoration(color: colors.surface3, borderRadius: BorderRadius.circular(NikoRadius.md)),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(NikoSpace.s4, NikoSpace.s1, NikoSpace.s1, NikoSpace.s1),
              child: Row(
                children: [
                  Expanded(child: Text(text, maxLines: 2, style: NikoType.bodySm.style(colors.ink))),
                  if (action case final label?)
                    NikoGhostButton(label: label, onPressed: onAction)
                  else
                    const SizedBox(height: NikoSize.control),
                ],
              ),
            ),
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

/// Puts text on the system clipboard.
Future<void> copyText(String text) => Clipboard.setData(ClipboardData(text: text));

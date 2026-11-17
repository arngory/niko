import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../daemon/daemon.dart';
import '../l10n/strings.dart';
import '../state/log_feed.dart';
import '../state/stun_tool.dart';
import '../theme/niko_theme.dart';
import '../ui/banner.dart';
import '../ui/controls.dart';
import '../ui/format.dart';
import '../ui/icons.dart';
import 'tools_panel.dart';

/// The core's log (D-LOG-01): its lines as the core writes them, the newest at the bottom and in view until the
/// user scrolls up; a button empties the daemon's log. The tools stand beside it, or under it in a narrow window.
class LogScreen extends StatefulWidget {
  const LogScreen({super.key, required this.feed, this.stun});

  final LogFeed feed;
  final StunTool? stun;

  /// The content's width from which the tools stand beside the log.
  static const double aside = 760;
  static const double asideWidth = 340;

  @override
  State<LogScreen> createState() => _LogScreenState();
}

class _LogScreenState extends State<LogScreen> {
  final _scroll = ScrollController();

  // the log and the tools move between the wide and the narrow layout, kept: the typed server, the log's place and
  // what is selected in it are not lost when the window crosses the width
  final _boxKey = GlobalKey();
  final _toolsKey = GlobalKey();
  bool? _wide;

  // the end is kept in view while the user is at it
  bool _follow = true;
  int _resets = 0;

  @override
  void initState() {
    super.initState();
    widget.feed.addListener(_changed);
    _resets = widget.feed.resets;
    _toEnd();
  }

  @override
  void didUpdateWidget(LogScreen oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.feed != widget.feed) {
      oldWidget.feed.removeListener(_changed);
      widget.feed.addListener(_changed);
    }
  }

  @override
  void dispose() {
    widget.feed.removeListener(_changed);
    _scroll.dispose();
    super.dispose();
  }

  void _changed() {
    if (widget.feed.resets != _resets) {
      _resets = widget.feed.resets;
      _follow = true;
    }
    setState(() {});
    if (_follow) _toEnd();
  }

  // The list lays out only the lines near what is shown and guesses the height of the rest, so the end it knows of
  // moves once it has been jumped to: a jump, a frame, and a second jump land on the real one.
  void _toEnd([int jumps = 3]) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_follow || !_scroll.hasClients) return;
      final end = _scroll.position.maxScrollExtent;
      if (_scroll.position.pixels >= end) return;
      _scroll.jumpTo(end);
      if (jumps > 1) {
        WidgetsBinding.instance.scheduleFrame();
        _toEnd(jumps - 1);
      }
    });
  }

  // the layout for this width; a change of it keeps the end in view if it was
  bool _layout(bool wide) {
    if (_wide != null && _wide != wide && _follow) _toEnd();
    _wide = wide;
    return wide;
  }

  bool _scrolled(ScrollNotification notification) {
    if (notification is UserScrollNotification || notification is ScrollEndNotification) {
      final position = notification.metrics;
      _follow = position.pixels >= position.maxScrollExtent - NikoSpace.s6;
    }
    return false;
  }

  Future<void> _copy() async {
    await Clipboard.setData(ClipboardData(text: widget.feed.text));
    widget.feed.copied();
  }

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final strings = NikoStrings.of(Localizations.localeOf(context));
    final feed = widget.feed;
    final lines = feed.lines;
    final report = feed.unread.isEmpty ? null : feed.unread.first;
    final notice = switch (feed.notice) {
      LogNotice.none => null,
      LogNotice.copied => (strings.logCopied, '', false),
      LogNotice.saved => (strings.saved, feed.noticeDetail, false),
      LogNotice.saveFailed => (strings.saveFailed, feed.noticeDetail, true),
      LogNotice.clearFailed => (strings.logClearFailed, feed.noticeDetail, true),
      LogNotice.reportFailed => (strings.reportFailed, feed.noticeDetail, true),
    };
    final head = <Widget>[
      Row(
        children: [
          Expanded(
            child: Semantics(
              header: true,
              child: Text(strings.sectionLog, style: NikoType.title.style(colors.ink)),
            ),
          ),
          NikoIconButton(
            icon: NikoIcons.trash,
            label: strings.logClear,
            muted: true,
            onPressed: lines.isEmpty ? null : feed.clear,
          ),
          const SizedBox(width: NikoSpace.s2),
          NikoIconButton(
            icon: NikoIcons.copy,
            label: strings.logCopy,
            tonal: true,
            onPressed: lines.isEmpty ? null : _copy,
          ),
          const SizedBox(width: NikoSpace.s2),
          NikoIconButton(
            icon: NikoIcons.export,
            label: strings.logSave,
            tonal: true,
            onPressed: lines.isEmpty || feed.busy ? null : feed.saveLog,
          ),
        ],
      ),
      const SizedBox(height: NikoSpace.s4),
      // D-LOG-02: the newest report nobody saved yet
      if (report != null) ...[
        NikoBanner(
          icon: NikoIcons.alert,
          title: report.kind == ReportKind.crash ? strings.reportCrash : strings.reportMemory,
          text: formatMoment(report.at),
          action: strings.reportSave,
          onAction: feed.busy ? null : () => feed.saveReport(report),
        ),
        const SizedBox(height: NikoSpace.s4),
      ],
      if (notice != null) ...[
        NikoBanner(icon: NikoIcons.alert, title: notice.$1, text: notice.$2, danger: notice.$3),
        const SizedBox(height: NikoSpace.s4),
      ],
    ];
    final box = Semantics(
      key: _boxKey,
      container: true,
      explicitChildNodes: true,
      label: strings.logCore,
      child: DecoratedBox(
        decoration: BoxDecoration(color: colors.surface1, borderRadius: BorderRadius.circular(NikoRadius.md)),
        child: lines.isEmpty
            ? Padding(
                padding: const EdgeInsets.all(NikoSpace.s4),
                child: Text(strings.logEmpty, style: NikoType.code.style(colors.ink2)),
              )
            // the lines can be selected with the pointer and copied with Ctrl+C, as text anywhere
            : DefaultSelectionStyle(
                selectionColor: colors.ink.withValues(alpha: 0.24),
                cursorColor: colors.ink,
                child: SelectableRegion(
                  selectionControls: emptyTextSelectionControls,
                  child: NotificationListener<ScrollNotification>(
                    onNotification: _scrolled,
                    child: ListView.builder(
                      controller: _scroll,
                      padding: const EdgeInsets.all(NikoSpace.s4),
                      itemCount: lines.length,
                      itemBuilder: (context, index) {
                        final line = lines[index];
                        return Text(
                          line.text,
                          style: NikoType.code.style(switch (line.level) {
                            LogSeverity.panic || LogSeverity.fatal || LogSeverity.error => colors.danger,
                            LogSeverity.debug || LogSeverity.trace => colors.ink2,
                            _ => colors.ink,
                          }),
                        );
                      },
                    ),
                  ),
                ),
              ),
      ),
    );
    final stun = widget.stun;
    return Padding(
      padding: const EdgeInsets.fromLTRB(NikoSpace.s8, NikoSpace.s4, NikoSpace.s8, NikoSpace.s8),
      child: stun == null
          ? Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [...head, Expanded(child: box)],
            )
          : LayoutBuilder(
              builder: (context, constraints) => _layout(constraints.maxWidth >= LogScreen.aside)
                  ? Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [...head, Expanded(child: box)],
                          ),
                        ),
                        const SizedBox(width: NikoSpace.s6),
                        SizedBox(
                          width: LogScreen.asideWidth,
                          child: SingleChildScrollView(child: ToolsPanel(key: _toolsKey, stun: stun)),
                        ),
                      ],
                    )
                  // narrow: the tools under the log, in at most two fifths of what the title and the banners leave,
                  // scrolled within
                  : Column(
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        ...head,
                        Expanded(flex: 3, child: box),
                        const SizedBox(height: NikoSpace.s4),
                        Flexible(flex: 2, child: SingleChildScrollView(child: ToolsPanel(key: _toolsKey, stun: stun))),
                      ],
                    ),
            ),
    );
  }
}

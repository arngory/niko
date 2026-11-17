import 'package:flutter/widgets.dart';

import '../daemon/daemon.dart';
import '../l10n/strings.dart';
import '../state/log_feed.dart';
import '../theme/niko_theme.dart';
import '../ui/banner.dart';
import '../ui/controls.dart';
import '../ui/icons.dart';

/// The core's log (D-LOG-01): its lines as the core writes them, the newest at the bottom and in view until the
/// user scrolls up; a button empties the daemon's log.
class LogScreen extends StatefulWidget {
  const LogScreen({super.key, required this.feed});

  final LogFeed feed;

  @override
  State<LogScreen> createState() => _LogScreenState();
}

class _LogScreenState extends State<LogScreen> {
  final _scroll = ScrollController();

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

  bool _scrolled(ScrollNotification notification) {
    if (notification is UserScrollNotification || notification is ScrollEndNotification) {
      final position = notification.metrics;
      _follow = position.pixels >= position.maxScrollExtent - NikoSpace.s6;
    }
    return false;
  }

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final strings = NikoStrings.of(Localizations.localeOf(context));
    final lines = widget.feed.lines;
    return Padding(
      padding: const EdgeInsets.fromLTRB(NikoSpace.s8, NikoSpace.s4, NikoSpace.s8, NikoSpace.s8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
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
                onPressed: lines.isEmpty ? null : widget.feed.clear,
              ),
            ],
          ),
          const SizedBox(height: NikoSpace.s4),
          if (widget.feed.error.isNotEmpty) ...[
            NikoBanner(icon: NikoIcons.alert, title: strings.logClearFailed, text: widget.feed.error, danger: true),
            const SizedBox(height: NikoSpace.s4),
          ],
          Expanded(
            child: Semantics(
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
                    : NotificationListener<ScrollNotification>(
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
        ],
      ),
    );
  }
}

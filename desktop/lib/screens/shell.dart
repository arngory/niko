import 'dart:async';
import 'dart:io';

import 'package:flutter/widgets.dart';

import '../daemon/daemon.dart';
import '../l10n/strings.dart';
import '../state/connection_controller.dart';
import '../state/connections_feed.dart';
import '../state/log_feed.dart';
import '../state/stun_tool.dart';
import '../state/window_settings.dart';
import '../theme/niko_theme.dart';
import '../ui/header.dart';
import 'connections_screen.dart';
import 'log_screen.dart';
import 'main_screen.dart';

/// The sections the window has so far. Узлы, Проверка маршрута, Правила and Настройки come with NikoService
/// (stage 3): a section is in the header only when its screen exists.
enum AppSection { home, connections, log }

/// The window under its header: the section shown, in the view chosen. D-NAV-01, D-NAV-03.
///
/// Простой shows the main screen and the log; Расширенный adds the connections. Changing the view changes what
/// is shown and nothing about the connection.
class NikoShell extends StatefulWidget {
  const NikoShell({
    super.key,
    required this.controller,
    required this.settings,
    this.saveTo,
    this.daemonAddress = '',
    this.configName = '',
    this.now = DateTime.now,
  });

  final ConnectionController controller;

  /// The view chosen is kept in it, for the next launch.
  final WindowSettings settings;

  /// Where the log and the reports are saved; null: the user's Downloads.
  final Directory? Function()? saveTo;
  final String daemonAddress;
  final String configName;
  final DateTime Function() now;

  @override
  State<NikoShell> createState() => _NikoShellState();
}

class _NikoShellState extends State<NikoShell> {
  late LogFeed _log;
  late ConnectionsFeed _connections;
  late StunTool _stun;
  AppSection _section = AppSection.home;
  late bool _advanced = widget.settings.advanced;

  @override
  void initState() {
    super.initState();
    _attach();
  }

  void _attach() {
    _log = LogFeed(widget.controller, saveTo: widget.saveTo);
    _connections = ConnectionsFeed(widget.controller);
    _stun = StunTool(widget.controller);
    widget.controller.addListener(_changed);
  }

  void _detach(ConnectionController controller) {
    controller.removeListener(_changed);
    _log.dispose();
    _connections.dispose();
    _stun.dispose();
  }

  @override
  void didUpdateWidget(NikoShell oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.controller != widget.controller) {
      _detach(oldWidget.controller);
      _attach();
      _read();
    }
  }

  @override
  void dispose() {
    _detach(widget.controller);
    super.dispose();
  }

  // the connections screen says whether the core runs
  void _changed() {
    if (_section == AppSection.connections) setState(() {});
  }

  // each feed reads the daemon only while its screen is the one shown
  void _read() {
    _section == AppSection.log ? _log.open() : _log.close();
    _section == AppSection.connections ? _connections.show() : _connections.hide();
  }

  void _show(AppSection section) {
    if (section == _section) return;
    setState(() => _section = section);
    _read();
  }

  void _setAdvanced(bool advanced) {
    if (advanced == _advanced) return;
    widget.settings.advanced = advanced;
    unawaited(widget.settings.save());
    setState(() {
      _advanced = advanced;
      // a section the simple view does not have gives way to the main screen
      if (!advanced && _section == AppSection.connections) _section = AppSection.home;
    });
    _read();
  }

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final strings = NikoStrings.of(Localizations.localeOf(context));
    final state = widget.controller;
    return ColoredBox(
      color: colors.surface0,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          NikoHeader<AppSection>(
            sectionsLabel: strings.sections,
            sections: [
              (AppSection.home, strings.sectionHome),
              if (_advanced) (AppSection.connections, strings.sectionConnections),
              (AppSection.log, strings.sectionLog),
            ],
            section: _section,
            onSection: _show,
            viewLabel: strings.view,
            views: (strings.viewSimple, strings.viewAdvanced),
            advanced: _advanced,
            onAdvanced: _setAdvanced,
          ),
          Expanded(
            child: switch (_section) {
              AppSection.home => MainScreen(
                controller: state,
                daemonAddress: widget.daemonAddress,
                configName: widget.configName,
                now: widget.now,
              ),
              AppSection.connections => ConnectionsScreen(
                feed: _connections,
                running: state.link == LinkPhase.ready && state.service.phase == ServicePhase.started,
                now: widget.now,
              ),
              AppSection.log => LogScreen(feed: _log, stun: _stun),
            },
          ),
        ],
      ),
    );
  }
}

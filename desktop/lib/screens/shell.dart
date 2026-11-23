import 'dart:async';
import 'dart:io';

import 'package:flutter/widgets.dart';

import '../daemon/daemon.dart';
import '../l10n/strings.dart';
import '../state/connection_controller.dart';
import '../state/connections_feed.dart';
import '../state/log_feed.dart';
import '../state/profiles_feed.dart';
import '../state/rules_feed.dart';
import '../state/settings_feed.dart';
import '../state/stun_tool.dart';
import '../state/window_settings.dart';
import '../theme/niko_theme.dart';
import '../settings/settings_screen.dart';
import '../ui/header.dart';
import 'add_screen.dart';
import 'connections_screen.dart';
import 'log_screen.dart';
import 'main_screen.dart';
import 'nodes_screen.dart';
import 'rules_screen.dart';

/// The sections the window has so far. Проверка маршрута comes with NikoService (stage 3): a section is in the header
/// only when its screen exists.
enum AppSection { home, nodes, rules, connections, log, settings }

/// The window under its header: the section shown, in the view chosen. D-NAV-01, D-NAV-03.
///
/// Простой shows the main screen and the log; Расширенный adds the rules and the connections. Changing the view
/// changes what is shown and nothing about the connection.
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
  late ProfilesFeed _profiles;
  late RulesFeed _rules;
  late SettingsFeed _settings;
  bool _adding = false;
  AppSection _section = AppSection.home;
  late bool _advanced = widget.settings.advanced;

  @override
  void initState() {
    super.initState();
    _attach();
    _read();
  }

  void _attach() {
    _log = LogFeed(widget.controller, saveTo: widget.saveTo);
    _connections = ConnectionsFeed(widget.controller);
    _stun = StunTool(widget.controller);
    _profiles = ProfilesFeed(widget.controller);
    _rules = RulesFeed(widget.controller);
    _settings = SettingsFeed(widget.controller);
    widget.controller.addListener(_changed);
  }

  void _detach(ConnectionController controller) {
    controller.removeListener(_changed);
    _log.dispose();
    _connections.dispose();
    _stun.dispose();
    _profiles.dispose();
    _rules.dispose();
    _settings.dispose();
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

  // the connections screen says whether the core runs; the settings, whether the daemon is there
  void _changed() {
    if (_section == AppSection.connections || _section == AppSection.settings) setState(() {});
    // the daemon found, or another one: whether the main screen reads the settings
    if (_section == AppSection.home) _read();
  }

  // each feed reads the daemon only while its screen is the one shown
  void _read() {
    _section == AppSection.log ? _log.open() : _log.close();
    _section == AppSection.connections ? _connections.show() : _connections.hide();
    // the main screen too: the node of the selected profile (D-MAIN-13)
    _section == AppSection.nodes || _section == AppSection.home ? _profiles.show() : _profiles.hide();
    _section == AppSection.rules ? _rules.show() : _rules.hide();
    // the main screen shows the mode of the settings where it connects the selected profile (D-NAV-02)
    _section == AppSection.settings || (_section == AppSection.home && widget.controller.connectsProfile)
        ? _settings.show()
        : _settings.hide();
  }

  void _show(AppSection section) {
    if (section == _section) return;
    setState(() {
      _section = section;
      _adding = false;
    });
    // the section Узлы reads the store anew, as when it was not followed on the main screen
    if (section == AppSection.nodes) _profiles.hide();
    _read();
  }

  void _setAdvanced(bool advanced) {
    if (advanced == _advanced) return;
    widget.settings.advanced = advanced;
    unawaited(widget.settings.save());
    setState(() {
      _advanced = advanced;
      // a section the simple view does not have gives way to the main screen
      if (!advanced && (_section == AppSection.connections || _section == AppSection.rules)) _section = AppSection.home;
    });
    _read();
  }

  // the settings' mode, once read
  ConnectMode? _mode() => !_settings.loaded
      ? null
      : _settings.text('serviceMode') == 'vpn'
          ? ConnectMode.vpn
          : ConnectMode.proxy;

  // kept as the settings' mode, and a running core started again in it
  void _setMode(ConnectMode mode) {
    _settings.setText('serviceMode', mode.name);
    unawaited(widget.controller.switchMode(mode));
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
              (AppSection.nodes, strings.sectionNodes),
              if (_advanced) (AppSection.rules, strings.sectionRules),
              if (_advanced) (AppSection.connections, strings.sectionConnections),
              (AppSection.log, strings.sectionLog),
              (AppSection.settings, strings.sectionSettings),
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
              AppSection.home => ListenableBuilder(
                listenable: Listenable.merge([_settings, _profiles]),
                builder: (context, _) => MainScreen(
                  controller: state,
                  node: _profiles.selectedNode,
                  daemonAddress: widget.daemonAddress,
                  configName: widget.configName,
                  now: widget.now,
                  mode: _mode(),
                  onMode: _setMode,
                ),
              ),
              AppSection.connections => ConnectionsScreen(
                feed: _connections,
                running: state.link == LinkPhase.ready && state.service.phase == ServicePhase.started,
                now: widget.now,
              ),
              AppSection.nodes => ListenableBuilder(
                listenable: Listenable.merge([_profiles, state]),
                builder: (context, _) => _adding
                    ? AddScreen(feed: _profiles, onDone: (_) => setState(() => _adding = false))
                    : NodesScreen(
                        feed: _profiles,
                        linked: state.link == LinkPhase.ready,
                        onAdd: () => setState(() => _adding = true),
                      ),
              ),
              AppSection.rules => ListenableBuilder(
                listenable: Listenable.merge([_rules, state]),
                builder: (context, _) => RulesScreen(feed: _rules, linked: state.link == LinkPhase.ready),
              ),
              AppSection.log => LogScreen(feed: _log, stun: _stun),
              AppSection.settings => ListenableBuilder(
                listenable: _settings,
                builder: (context, _) => SettingsScreen(
                  values: _settings,
                  strings: strings,
                  notice: switch (state.link) {
                    LinkPhase.ready when _settings.loaded => null,
                    LinkPhase.ready when _settings.readError.isNotEmpty => (
                      strings.settingsUnread,
                      _settings.readError,
                    ),
                    // read in a moment: nothing to say yet
                    LinkPhase.ready => ('', ''),
                    _ => (strings.storeNoLink, ''),
                  },
                ),
              ),
            },
          ),
        ],
      ),
    );
  }
}

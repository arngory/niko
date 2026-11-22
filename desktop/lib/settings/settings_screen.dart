import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';

import '../l10n/strings.dart';
import '../theme/niko_theme.dart';
import '../ui/banner.dart';
import '../ui/controls.dart';
import '../ui/fields.dart';
import '../ui/icons.dart';
import '../ui/list.dart';
import '../ui/pressable.dart';
import 'setting_values.dart';

/// Настройки (B-Settings), made by hand on the settings components (variant А, decision of 2026-11-21): the
/// computer's settings the daemon's build takes (NikoService.GetSettings), each kept by the daemon as it is changed.
/// The canvas's sections of what the window has not yet (the theme, the language, the start with the system,
/// raycontrol's nodes, naive, Yggdrasil, the backup, the updates) come with their features; of the others only the
/// rows the build of a computer applies are here. A value row opens a dialog; the dialog keeps what the daemon took,
/// or says why not.
class SettingsScreen extends StatefulWidget {
  const SettingsScreen({super.key, required this.values, required this.strings, this.notice});

  /// The settings by the app's names (SettingsFeed: the daemon's).
  final SettingValues values;
  final NikoStrings strings;

  /// What is shown instead of the settings, a title and what to know under it: no link to the daemon, the settings not
  /// read; an empty title: nothing yet (they are being read); null: the settings.
  final (String, String)? notice;

  @override
  State<SettingsScreen> createState() => _SettingsScreenState();
}

/// The sections, in the canvas's order.
enum _Section { general, connection, dns, programs, inbound, experiments, log }

/// A dialog of a value row: its title, its fields (key, label, input) and what it says under them; or the domain
/// strategies' choices.
class _Dialog {
  const _Dialog(this.title, this.fields, {this.hint = '', this.strategies = false});

  final String title;
  final List<(String, String, FieldInput)> fields;
  final String hint;
  final bool strategies;
}

const _strategyKeys = ['domainStrategyForRemote', 'domainStrategyForDirect', 'domainStrategyForServer'];
const _strategies = ['auto', 'prefer_ipv4', 'prefer_ipv6', 'ipv4_only', 'ipv6_only'];

class _SettingsScreenState extends State<SettingsScreen> {
  final _scroll = ScrollController();
  final _keys = {for (final s in _Section.values) s: GlobalKey()};
  _Section _current = _Section.general;
  _Dialog? _dialog;

  @override
  void dispose() {
    _scroll.dispose();
    super.dispose();
  }

  NikoStrings get _s => widget.strings;
  SettingValues get _v => widget.values;

  String _name(_Section section) => switch (section) {
        _Section.general => _s.setGeneral,
        _Section.connection => _s.setConnection,
        _Section.dns => _s.setDns,
        _Section.programs => _s.setPrograms,
        _Section.inbound => _s.setInboundMenu,
        _Section.experiments => _s.setExperiments,
        _Section.log => _s.setLog,
      };

  void _jump(_Section section) {
    setState(() => _current = section);
    final target = _keys[section]!.currentContext;
    if (target != null) {
      Scrollable.ensureVisible(target, duration: const Duration(milliseconds: 200), curve: Curves.easeOut);
    }
  }

  // an unset strategy is the app's "auto" as its menu shows it
  String _strategy(String key) => _v.text(key).isEmpty ? 'auto' : _v.text(key);

  String _strategySummary() {
    final values = {for (final key in _strategyKeys) _strategy(key)};
    return values.length == 1 ? _s.strategy(values.single) : _s.setStrategyMixed;
  }

  // a DNS setting's servers, a line each ("#" a comment), as the phone's list shows them: the first one's host
  // (https://cloudflare-dns.com/dns-query as cloudflare-dns.com), and +1, +2 for the ones the core asks after it
  String _dnsServers(String key) {
    final lines = [
      for (final line in _v.text(key).split('\n'))
        if (line.trim().isNotEmpty && !line.trim().startsWith('#')) line.trim(),
    ];
    if (lines.isEmpty) return _v.text(key).split('\n').first;
    final first = lines.first;
    final address = first.contains('://') ? first.substring(first.indexOf('://') + 3) : first;
    final slash = address.indexOf('/');
    final host = slash < 0 ? address : address.substring(0, slash);
    final shown = host.isEmpty ? first : host;
    return lines.length == 1 ? shown : '$shown +${lines.length - 1}';
  }

  // the programs chosen: each line that names one, once
  String _programs() {
    final count = {for (final line in _v.text('programs').split('\n')) if (line.trim().isNotEmpty) line.trim()}.length;
    return count == 0 ? _s.setProgramsNone : _s.programsCount(count);
  }

  String _login() {
    if (_v.text('mixedPassword').isEmpty) return _s.setLoginNone;
    final user = _v.text('mixedUsername');
    return user.isEmpty ? _s.setLoginSet : user;
  }

  Widget _switch(String key, String title, {String? sub, bool divider = true}) => NikoSwitchRow(
        title: title,
        sub: sub,
        on: _v.flag(key),
        onChanged: (on) => _v.setFlag(key, on),
        divider: divider,
      );

  Widget _choice(String key, String title, List<(String, String)> choices, {double cell = 104, bool divider = true}) =>
      NikoChoiceRow<String>(
        title: title,
        choices: choices,
        value: _v.text(key),
        onChanged: (value) => _v.setText(key, value),
        cell: cell,
        divider: divider,
      );

  Widget _value(String title, String value, _Dialog dialog, {bool mono = true, bool divider = true}) => NikoLinkRow(
        title: title,
        value: value,
        mono: mono,
        divider: divider,
        onPressed: () => setState(() => _dialog = dialog),
      );

  Widget _section(_Section section, String overline, List<Widget> rows) =>
      KeyedSubtree(key: _keys[section], child: NikoSection(overline: overline, rows: rows));

  List<Widget> _sections() => [
        _section(_Section.general, _s.setGeneral, [
          _choice('serviceMode', _s.setMode, [('vpn', _s.setModeVpn), ('proxy', _s.setModeProxy)], cell: 96,
              divider: false),
          _switch('systemProxy', _s.setSystemProxy, sub: _s.setSystemProxySub),
        ]),
        _section(_Section.connection, _s.setConnection, [
          _choice('tunImplementation', _s.setTun, [('0', 'gVisor'), ('1', 'System'), ('2', 'Mixed')], cell: 88,
              divider: false),
          _value(_s.setMtu, _v.text('mtu'), _Dialog(_s.setMtu, [('mtu', _s.setMtu, FieldInput.port)], hint: _s.setMtuHint)),
          _choice('ipv6Mode', _s.setIpv6,
              [('0', _s.setIpv6Off), ('1', _s.setIpv6On), ('2', _s.setIpv6Prefer), ('3', _s.setIpv6Only)], cell: 84),
          _switch('globalAllowInsecure', _s.setInsecure, sub: _s.setDangerous),
        ]),
        _section(_Section.dns, _s.setDns, [
          _value(_s.setRemoteDns, _dnsServers('remoteDns'),
              _Dialog(_s.setRemoteDns, [('remoteDns', _s.setRemoteDns, FieldInput.lines)], hint: _s.setRemoteDnsHint),
              divider: false),
          _value(_s.setDirectDns, _v.text('directDns').split('\n').first,
              _Dialog(_s.setDirectDns, [('directDns', _s.setDirectDns, FieldInput.text)], hint: _s.setDnsHint)),
          _switch('enableFakeDns', _s.setFakeIp, sub: _s.setInVpn),
          _value(_s.setStrategy, _strategySummary(), _Dialog(_s.setStrategy, const [], strategies: true), mono: false),
          _switch('dnsOptimisticCache', _s.setOptimisticCache),
          _switch('bypassLanInCore', _s.setLanDirect),
        ]),
        // D-RULES-06: as the phone's apps through the VPN, a rule by program ahead of the rules (D-RULES-02)
        _section(_Section.programs, _s.setPrograms, [
          _choice('programsMode', _s.setProgramsThrough,
              [('0', _s.setProgramsAll), ('1', _s.setProgramsOnly), ('2', _s.setProgramsExcept)], cell: 148,
              divider: false),
          _value(
            _s.setProgramsList,
            _programs(),
            _Dialog(_s.setProgramsList, [('programs', _s.setProgramsList, FieldInput.lines)], hint: _s.setProgramsHint),
            mono: false,
          ),
        ]),
        _section(_Section.inbound, _s.setInbound, [
          _value(_s.inboundPort, _v.text('mixedPort'),
              _Dialog(_s.inboundPort, [('mixedPort', _s.inboundPort, FieldInput.port)]), divider: false),
          _value(
            _s.setLogin,
            _login(),
            _Dialog(_s.setLogin, [
              ('mixedUsername', _s.inboundUser, FieldInput.text),
              ('mixedPassword', _s.inboundPassword, FieldInput.secret),
            ], hint: _s.setLoginHint),
            mono: false,
          ),
          _switch('allowAccess', _s.setLanAccess),
        ]),
        _section(_Section.experiments, _s.setExperiments, [
          _switch('refuseQuicOverStreams', _s.setRefuseQuic, divider: false),
          _switch('icmpNoLocalReply', _s.setNoPingReply),
          _switch('trafficSniffing', _s.setSniffing),
          _switch('resolveDestination', _s.setResolveDestination),
        ]),
        _section(_Section.log, _s.setLog, [
          _choice('logLevel', _s.setLogLevel,
              [('0', 'none'), ('1', 'warn'), ('2', 'info'), ('3', 'debug'), ('4', 'trace')], cell: 64, divider: false),
        ]),
      ];

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return ColoredBox(
      color: colors.surface0,
      child: ListenableBuilder(
        listenable: _v,
        builder: (context, _) => Stack(
          children: [
            LayoutBuilder(
              builder: (context, constraints) {
                final wide = constraints.maxWidth >= 840;
                final content = Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    Semantics(header: true, child: Text(_s.sectionSettings, style: NikoType.title.style(colors.ink))),
                    if (widget.notice case (final title, final text)) ...[
                      if (title.isNotEmpty) ...[
                        const SizedBox(height: NikoSpace.s6),
                        NikoBanner(icon: NikoIcons.alert, title: title, text: text),
                      ],
                    ] else ...[
                      if (_v.error.isNotEmpty) ...[
                        const SizedBox(height: NikoSpace.s6),
                        NikoBanner(icon: NikoIcons.alert, title: _s.settingsNotKept, text: _v.error, danger: true),
                      ],
                      for (final section in _sections()) ...[const SizedBox(height: NikoSpace.s8), section],
                    ],
                  ],
                );
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (wide && widget.notice == null)
                      Padding(
                        padding: const EdgeInsetsDirectional.only(start: NikoSpace.s8, top: NikoSpace.s4),
                        child: SizedBox(
                          width: 220,
                          child: Semantics(
                            container: true,
                            explicitChildNodes: true,
                            label: _s.settingsMenu,
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.stretch,
                              children: [
                                for (final section in _Section.values)
                                  _MenuItem(
                                    name: _name(section),
                                    active: section == _current,
                                    onPressed: () => _jump(section),
                                  ),
                              ],
                            ),
                          ),
                        ),
                      ),
                    Expanded(
                      child: SingleChildScrollView(
                        controller: _scroll,
                        padding: EdgeInsets.fromLTRB(wide ? NikoSpace.s8 : NikoSpace.s5, NikoSpace.s4,
                            wide ? NikoSpace.s8 : NikoSpace.s5, 48),
                        child: Align(
                          alignment: AlignmentDirectional.topStart,
                          child: ConstrainedBox(constraints: const BoxConstraints(maxWidth: 760), child: content),
                        ),
                      ),
                    ),
                  ],
                );
              },
            ),
            if (_dialog case final dialog?)
              _ValueDialog(
                key: ValueKey(dialog.title),
                dialog: dialog,
                values: _v,
                strings: _s,
                strategy: _strategy,
                onClose: () => setState(() => _dialog = null),
              ),
          ],
        ),
      ),
    );
  }
}

/// A section's name in the settings' menu (n-menu > n-nav__item): quiet, in ink and heavier for the one chosen.
class _MenuItem extends StatelessWidget {
  const _MenuItem({required this.name, required this.active, required this.onPressed});

  final String name;
  final bool active;
  final VoidCallback onPressed;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    return Semantics(
      container: true,
      button: true,
      selected: active,
      child: NikoPressable(
        onPressed: onPressed,
        radius: NikoRadius.sm,
        builder: (context, state) => DecoratedBox(
          decoration: BoxDecoration(
            color: state.pressed ? colors.surface2 : null,
            borderRadius: BorderRadius.circular(NikoRadius.sm),
          ),
          child: SizedBox(
            height: NikoSize.tap,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: NikoSpace.s4),
              child: Align(
                alignment: AlignmentDirectional.centerStart,
                child: Text(
                  name,
                  maxLines: 1,
                  overflow: TextOverflow.ellipsis,
                  style: NikoType.body.style(
                    active || state.hovered ? colors.ink : colors.ink2,
                    weight: active ? FontWeight.w600 : FontWeight.w400,
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// The dialog of a value row (m-dialog): its fields with what the daemon has, Отмена and Сохранить; what the daemon
/// refused said under them, the dialog left open. Escape closes it.
class _ValueDialog extends StatefulWidget {
  const _ValueDialog({
    super.key,
    required this.dialog,
    required this.values,
    required this.strings,
    required this.strategy,
    required this.onClose,
  });

  final _Dialog dialog;
  final SettingValues values;
  final NikoStrings strings;
  final String Function(String key) strategy;
  final VoidCallback onClose;

  @override
  State<_ValueDialog> createState() => _ValueDialogState();
}

class _ValueDialogState extends State<_ValueDialog> {
  late final Map<String, String> _texts = {
    for (final (key, _, _) in widget.dialog.fields) key: widget.values.text(key),
    if (widget.dialog.strategies)
      for (final key in _strategyKeys) key: widget.strategy(key),
  };
  String _error = '';
  bool _saving = false;

  Future<void> _save() async {
    setState(() => _saving = true);
    final error = await widget.values.save(texts: _texts);
    if (!mounted) return;
    if (error == null) {
      widget.onClose();
      return;
    }
    setState(() {
      _saving = false;
      _error = error;
    });
  }

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final s = widget.strings;
    final dialog = widget.dialog;
    return Shortcuts(
      shortcuts: const {SingleActivator(LogicalKeyboardKey.escape): DismissIntent()},
      child: Actions(
        actions: {DismissIntent: CallbackAction<DismissIntent>(onInvoke: (_) => widget.onClose())},
        child: Focus(
          autofocus: true,
          child: Stack(
            children: [
              Positioned.fill(
                child: GestureDetector(onTap: widget.onClose, child: ColoredBox(color: colors.scrim)),
              ),
              Center(
                child: Semantics(
                  scopesRoute: true,
                  namesRoute: true,
                  explicitChildNodes: true,
                  label: dialog.title,
                  child: ConstrainedBox(
                    constraints: BoxConstraints(maxWidth: dialog.strategies ? 640 : 480),
                    child: Padding(
                      padding: const EdgeInsets.all(NikoSpace.s5),
                      child: DecoratedBox(
                        decoration: BoxDecoration(
                          color: colors.surface1,
                          borderRadius: BorderRadius.circular(NikoRadius.md),
                        ),
                        child: Padding(
                          padding: const EdgeInsets.all(NikoSpace.s6),
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            crossAxisAlignment: CrossAxisAlignment.stretch,
                            children: [
                              Semantics(
                                header: true,
                                child: Text(dialog.title, style: NikoType.headline.style(colors.ink)),
                              ),
                              const SizedBox(height: NikoSpace.s5),
                              for (final (key, label, input) in dialog.fields) ...[
                                NikoField(
                                  label: label,
                                  value: _texts[key]!,
                                  input: input,
                                  mono: true,
                                  onChanged: (v) => _texts[key] = v,
                                ),
                                const SizedBox(height: NikoSpace.s4),
                              ],
                              if (dialog.strategies)
                                for (final (key, label) in [
                                  (_strategyKeys[0], s.setStrategyRemote),
                                  (_strategyKeys[1], s.setStrategyDirect),
                                  (_strategyKeys[2], s.setStrategyServer),
                                ]) ...[
                                  ExcludeSemantics(child: Text(label, style: NikoType.caption.style(colors.ink2))),
                                  const SizedBox(height: NikoSpace.s2),
                                  Align(
                                    alignment: AlignmentDirectional.centerStart,
                                    child: NikoSegmented<String>(
                                      label: label,
                                      choices: [for (final v in _strategies) (v, s.strategy(v))],
                                      value: _texts[key]!,
                                      onChanged: (v) => setState(() => _texts[key] = v),
                                      cell: 96,
                                    ),
                                  ),
                                  const SizedBox(height: NikoSpace.s4),
                                ],
                              if (dialog.hint.isNotEmpty) ...[
                                Text(dialog.hint, style: NikoType.bodySm.style(colors.ink2)),
                                const SizedBox(height: NikoSpace.s4),
                              ],
                              if (_error.isNotEmpty) ...[
                                NikoBanner(icon: NikoIcons.alert, title: s.settingsNotKept, text: _error, danger: true),
                                const SizedBox(height: NikoSpace.s4),
                              ],
                              Row(
                                mainAxisAlignment: MainAxisAlignment.end,
                                children: [
                                  NikoGhostButton(label: s.cancel, onPressed: widget.onClose),
                                  const SizedBox(width: NikoSpace.s2),
                                  NikoPrimaryButton(label: s.save, onPressed: _saving ? null : _save),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

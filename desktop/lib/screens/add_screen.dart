import 'dart:async';

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
import '../ui/list.dart';
import 'nodes_screen.dart' show typeLabel;

/// Добавление (B-Add, D-ADD-02…04, -06, D-SUB-01): a link, or several, pasted or typed; the daemon says what they are
/// before anything is written, and the profiles go into the group shown; a subscription's link makes a group of the
/// subscription, which the daemon updates at once; a raycontrol node's invite registers this computer with the node,
/// its PIN asked for where the invite needs one (before the first try, again after a wrong one), and the node's
/// group is shown once its subscription came. QR codes come with their slice.
class AddScreen extends StatefulWidget {
  const AddScreen({super.key, required this.feed, required this.onDone});

  final ProfilesFeed feed;

  /// The screen is done: [added] profiles (a subscription's group counts as one), none for a cancel.
  final void Function(int added) onDone;

  @override
  State<AddScreen> createState() => _AddScreenState();
}

class _AddScreenState extends State<AddScreen> {
  final _text = TextEditingController();
  final _pin = TextEditingController();

  /// The invite asked for its PIN: the tries left, whether the last was wrong.
  PinNeeded? _pinNeeded;
  ImportPreview _preview = const ImportPreview();
  String _recognized = '';
  String _error = '';
  bool _busy = false;
  Timer? _wait;

  @override
  void initState() {
    super.initState();
    _text.addListener(_changed);
    _pin.addListener(() => setState(() {}));
  }

  @override
  void dispose() {
    _wait?.cancel();
    _text.dispose();
    _pin.dispose();
    super.dispose();
  }

  // what the text is, asked of the daemon once typing pauses; an answer for an older text is dropped
  void _changed() {
    _wait?.cancel();
    _wait = Timer(const Duration(milliseconds: 250), () async {
      final text = _text.text;
      try {
        final preview = await widget.feed.recognize(text);
        if (!mounted || text != _text.text) return;
        setState(() {
          _preview = preview;
          _recognized = text;
          _error = '';
          _pinNeeded = null;
        });
      } catch (e) {
        if (mounted && text == _text.text) setState(() => _error = '$e');
      }
    });
  }

  Future<void> _paste() async {
    final data = await Clipboard.getData(Clipboard.kTextPlain);
    if (data?.text != null) _text.text = data!.text!.trim();
  }

  Future<void> _add() async {
    setState(() => _busy = true);
    try {
      if (_preview.kind == ImportKind.subscription) {
        await widget.feed.addSubscription(_text.text);
        if (mounted) widget.onDone(1);
        return;
      }
      if (_preview.kind == ImportKind.node) {
        final result = await widget.feed.addNode(_text.text, pin: _pin.text.trim());
        if (!mounted) return;
        if (result.pin != null) {
          setState(() {
            _busy = false;
            _pinNeeded = result.pin;
            _error = result.pin!.wrong ? '' : _error;
          });
          return;
        }
        // the node kept: its subscription's failure, if any, is the notice in its group
        if (result.nodeId.isNotEmpty) {
          widget.onDone(result.ids.isEmpty ? 1 : result.ids.length);
          return;
        }
        setState(() {
          _busy = false;
          _error = result.error;
        });
        return;
      }
      final ids = await widget.feed.add(_text.text);
      if (mounted) widget.onDone(ids.length);
    } catch (e) {
      if (mounted) {
        setState(() {
          _busy = false;
          _error = '$e';
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final strings = NikoStrings.of(Localizations.localeOf(context));
    final rows = _preview.kind == ImportKind.profiles ? _preview.profiles : const <ProfileRow>[];
    final current = _recognized.trim().isNotEmpty && _recognized == _text.text;
    final subscription = current && _preview.kind == ImportKind.subscription;
    final node = current && _preview.kind == ImportKind.node ? _preview.node : null;
    final askPin = node != null && node.invite && (node.pinRequired || _pinNeeded != null);
    final pinOk = RegExp(r'^\d{6}$').hasMatch(_pin.text.trim());
    return Align(
      alignment: Alignment.topCenter,
      child: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 616),
        child: SingleChildScrollView(
          padding: const EdgeInsets.fromLTRB(NikoSpace.s8, NikoSpace.s6, NikoSpace.s8, NikoSpace.s8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Text(strings.addOverline.toUpperCase(), style: NikoType.label.style(colors.ink2)),
              const SizedBox(height: NikoSpace.s2),
              Semantics(header: true, child: Text(strings.addTitle, style: NikoType.title.style(colors.ink))),
              const SizedBox(height: NikoSpace.s1),
              Text(strings.addSub, style: NikoType.bodySm.style(colors.ink2)),
              const SizedBox(height: NikoSpace.s6),
              Row(
                crossAxisAlignment: CrossAxisAlignment.end,
                children: [
                  Expanded(
                    child: NikoTextField(label: strings.addField, placeholder: 'vless://…', controller: _text),
                  ),
                  const SizedBox(width: NikoSpace.s3),
                  NikoTonalButton(label: strings.addPaste, onPressed: _paste),
                ],
              ),
              const SizedBox(height: NikoSpace.s6),
              if (_error.isNotEmpty) ...[
                NikoBanner(icon: NikoIcons.alert, title: strings.addFailed, text: _error, danger: true),
                const SizedBox(height: NikoSpace.s4),
              ],
              if (current) ...[
                if (subscription)
                  NikoBanner(icon: NikoIcons.plus, title: strings.addSubscriptionFound, text: strings.addSubscriptionText)
                else if (node != null)
                  _NodeFound(
                    node: node,
                    strings: strings,
                    pin: askPin ? _pin : null,
                    pinNeeded: _pinNeeded,
                    pinOk: pinOk,
                    busy: _busy,
                  )
                else if (rows.isEmpty)
                  Text(strings.addNothing, style: NikoType.body.style(colors.ink2))
                else
                  NikoSection(
                    overline: strings.addFound(rows.length),
                    rows: [
                      for (final row in rows) NikoInfoRow(title: row.name, value: '${typeLabel(row.type)} · ${row.address}'),
                    ],
                  ),
                const SizedBox(height: NikoSpace.s6),
              ],
              Row(
                children: [
                  NikoPrimaryButton(
                    label: node != null
                        ? strings.addNodeButton
                        : subscription
                            ? strings.addSubscriptionButton
                            : rows.isEmpty
                                ? strings.add
                                : strings.addButton(rows.length),
                    onPressed: (rows.isEmpty && !subscription && node == null) ||
                            (askPin && !pinOk) ||
                            _busy ||
                            _recognized != _text.text
                        ? null
                        : _add,
                  ),
                  const SizedBox(width: NikoSpace.s3),
                  NikoGhostButton(label: strings.cancel, onPressed: () => widget.onDone(0)),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// What a node's invite or device package names (D-ADD-06, -07): the node, a warning where it replaces one kept, and
/// the PIN where the invite asks for it.
class _NodeFound extends StatelessWidget {
  const _NodeFound({
    required this.node,
    required this.strings,
    required this.pin,
    required this.pinNeeded,
    required this.pinOk,
    required this.busy,
  });

  final NodePreview node;
  final NikoStrings strings;

  /// The PIN's field, where the invite asks for one; null where it does not.
  final TextEditingController? pin;
  final PinNeeded? pinNeeded;
  final bool pinOk;
  final bool busy;

  @override
  Widget build(BuildContext context) {
    final colors = NikoTheme.of(context);
    final text = [
      node.invite ? strings.addNodeInviteText(node.host) : strings.addNodeDeviceText(node.host),
      if (node.dnsName.isNotEmpty) strings.addNodeName(node.dnsName),
      if (node.expires > 0) strings.addNodeExpires(formatMoment(DateTime.fromMillisecondsSinceEpoch(node.expires * 1000))),
    ].join(' · ');
    final needed = pinNeeded;
    final pinNote = needed == null
        ? strings.addNodePinHint
        : needed.wrong
            ? strings.addNodePinWrong(needed.left)
            : needed.left >= 0
                ? '${strings.addNodePinHint} · ${strings.addNodePinLeft(needed.left)}'
                : strings.addNodePinHint;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        NikoBanner(icon: NikoIcons.plus, title: node.invite ? strings.addNodeInvite : strings.addNodeDevice, text: text),
        if (node.replaces) ...[
          const SizedBox(height: NikoSpace.s3),
          NikoBanner(
            icon: NikoIcons.alert,
            title: node.knownHost.isEmpty ? strings.addNodeReplaces : strings.addNodeMoved(node.knownHost, node.host),
            danger: node.knownHost.isNotEmpty,
          ),
        ],
        if (pin case final field?) ...[
          const SizedBox(height: NikoSpace.s4),
          NikoTextField(label: strings.addNodePin, placeholder: '123456', controller: field),
          const SizedBox(height: NikoSpace.s1),
          Text(
            field.text.isNotEmpty && !pinOk ? strings.addNodePinFormat : pinNote,
            style: NikoType.bodySm.style(needed?.wrong == true ? colors.danger : colors.ink2),
          ),
        ],
        if (busy) ...[
          const SizedBox(height: NikoSpace.s3),
          Text(strings.addNodeRegistering, style: NikoType.bodySm.style(colors.ink2)),
        ],
      ],
    );
  }
}

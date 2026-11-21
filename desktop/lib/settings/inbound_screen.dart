import 'package:flutter/widgets.dart';

import '../l10n/strings.dart';
import '../ui/controls.dart';
import '../ui/fields.dart';
import '../ui/icons.dart';
import '../ui/list.dart';
import 'setting_values.dart';

/// Настройки › Вход made by hand: variant А of docs/ui/platforms.md, the same screen as ListScreen draws from
/// shared/screens/set_inbound.json (docs/research/list-screens-probe.md). The window's part of it: the VPN's
/// switches and the phone's network are the phone's alone.
class InboundScreen extends StatelessWidget {
  const InboundScreen({super.key, required this.values, required this.strings, required this.onNewPassword});

  final SettingValues values;
  final NikoStrings strings;
  final VoidCallback onNewPassword;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: values,
        builder: (context, _) {
          Widget field(String key, String label, FieldInput input, {Widget? trail}) => NikoField(
              label: label,
              value: values.text(key),
              onChanged: (v) => values.setText(key, v),
              input: input,
              mono: true,
              trail: trail);

          return NikoSettingsPage(
            title: strings.inboundTitle,
            children: [
              NikoFieldsRow(fields: [
                (field('mixedUsername', strings.inboundUser, FieldInput.text), false),
                (field('mixedPort', strings.inboundPort, FieldInput.port), true),
              ]),
              field('mixedPassword', strings.inboundPassword, FieldInput.secret,
                  trail: NikoIconButton(
                      icon: NikoIcons.rotateCw,
                      label: strings.inboundNewPassword,
                      muted: true,
                      small: true,
                      onPressed: onNewPassword)),
              NikoGroup(rows: [
                NikoSwitchRow(
                  title: strings.inboundLan,
                  on: values.flag('allowAccess'),
                  onChanged: (v) => values.setFlag('allowAccess', v),
                ),
              ]),
            ],
          );
        },
      );
}

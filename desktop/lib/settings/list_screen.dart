import 'package:flutter/widgets.dart';

import '../gen/screens.g.dart';
import '../ui/controls.dart';
import '../ui/fields.dart';
import '../ui/list.dart';
import 'list_spec.dart';
import 'setting_values.dart';

/// Variant В: [spec] drawn over [values], its texts in [locale]'s language (English where screenTexts has none);
/// [onAction] gets the id of a field's action.
class ListScreen extends StatelessWidget {
  const ListScreen({super.key, required this.spec, required this.values, required this.locale, required this.onAction});

  final ListSpec spec;
  final SettingValues values;
  final Locale locale;
  final ValueChanged<String> onAction;

  @override
  Widget build(BuildContext context) => ListenableBuilder(
        listenable: values,
        builder: (context, _) {
          final texts = screenTexts[locale.languageCode] ?? screenTexts['en']!;
          String say(String name) => texts[name] ?? screenTexts['en']![name]!;

          Widget field(FieldSpec f) => NikoField(
                label: say(f.label),
                value: values.text(f.key),
                onChanged: (v) => values.setText(f.key, v),
                input: f.input,
                mono: f.mono,
                trail: switch (f.action) {
                  null => null,
                  final a => NikoIconButton(
                      icon: a.icon, label: say(a.label), muted: true, small: true, onPressed: () => onAction(a.id)),
                },
              );

          List<Widget> items(List<Item> items) => [
                for (final (i, item) in items.indexed)
                  switch (item) {
                    SwitchItem() => NikoSwitchRow(
                        title: say(item.title),
                        on: values.flag(item.key),
                        onChanged: (v) => values.setFlag(item.key, v),
                        enabled: item.enabledBy == null || values.flag(item.enabledBy!),
                        divider: i > 0,
                      ),
                  },
              ];

          return NikoSettingsPage(
            title: say(spec.title),
            children: [
              for (final line in spec.lines)
                switch (line) {
                  FieldsLine() => NikoFieldsRow(fields: [for (final f in line.fields) (field(f), f.narrow)]),
                  FieldLine() => field(line.field),
                  GroupLine() => NikoGroup(rows: items(line.items)),
                  SectionLine() => NikoSection(overline: say(line.title), rows: items(line.items)),
                },
            ],
          );
        },
      );
}

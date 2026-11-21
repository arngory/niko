import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:niko_desktop/l10n/strings.dart';
import 'package:niko_desktop/settings/inbound_screen.dart';
import 'package:niko_desktop/settings/setting_values.dart';
import 'package:niko_desktop/theme/niko_theme.dart';
import 'package:niko_desktop/ui/fields.dart';

import 'fonts.dart';

/// Настройки › Вход (InboundScreen), made by hand on the settings components: its picture in both themes, and what
/// it does (docs/research/list-screens-probe.md).
void main() {
  setUpAll(loadFonts);

  MemorySettingValues values() =>
      MemorySettingValues(texts: {'mixedUsername': 'niko', 'mixedPort': '2080', 'mixedPassword': '000000000000'});

  Widget frame(Brightness brightness, Widget child) => MediaQuery(
        data: const MediaQueryData(),
        child: Directionality(
          textDirection: TextDirection.ltr,
          child: NikoTheme(colors: NikoTheme.forBrightness(brightness), child: child),
        ),
      );

  Widget screen(SettingValues values, void Function() newPassword) =>
      InboundScreen(values: values, strings: NikoStrings.of(const Locale('ru')), onNewPassword: newPassword);

  Future<void> show(WidgetTester tester, Widget screen, Brightness brightness) async {
    tester.view.physicalSize = const Size(560, 420);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(frame(brightness, screen));
  }

  for (final brightness in Brightness.values) {
    testWidgets('${brightness.name}: the picture', (tester) async {
      await show(tester, screen(values(), () {}), brightness);
      // compared on Linux, where they were made: text is drawn a little differently elsewhere
      if (Platform.isLinux) {
        await expectLater(find.byType(NikoSettingsPage), matchesGoldenFile('goldens/set_inbound_${brightness.name}.png'));
      }
    });
  }

  testWidgets('the switch, the port and the action', (tester) async {
    final v = values();
    var asked = 0;
    await show(tester, screen(v, () => asked++), Brightness.light);

    expect(find.text('Доступ из локальной сети'), findsOneWidget);
    expect(find.text('Без пароля в VPN'), findsNothing, reason: 'the VPN is the phone\'s');
    await tester.tap(find.text('Доступ из локальной сети'));
    await tester.pump();
    expect(v.flag('allowAccess'), isTrue);
    expect(tester.widget<NikoSwitch>(find.byType(NikoSwitch)).on, isTrue);

    await tester.enterText(find.byType(EditableText).at(1), '80a80b123');
    expect(v.text('mixedPort'), '80801', reason: 'digits only, five at most');

    await tester.tap(find.bySemanticsLabel('Новый пароль'));
    expect(asked, 1);
  });

  testWidgets('a link row: its value, a chevron, pressed', (tester) async {
    var opened = 0;
    await show(
      tester,
      Column(children: [
        NikoLinkRow(title: 'Inbound', value: '127.0.0.1:2080', onPressed: () => opened++),
        NikoLinkRow(title: 'Access from the LAN', onPressed: () {}, divider: true),
      ]),
      Brightness.light,
    );
    expect(find.text('127.0.0.1:2080'), findsOneWidget);
    expect(find.bySemanticsLabel('Inbound, 127.0.0.1:2080'), findsOneWidget);
    await tester.tap(find.text('Inbound'));
    expect(opened, 1);
  });
}

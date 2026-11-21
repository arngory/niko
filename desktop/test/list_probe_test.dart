import 'dart:io';

import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:niko_desktop/gen/screens.g.dart';
import 'package:niko_desktop/l10n/strings.dart';
import 'package:niko_desktop/settings/inbound_screen.dart';
import 'package:niko_desktop/settings/list_screen.dart';
import 'package:niko_desktop/settings/list_spec.dart';
import 'package:niko_desktop/settings/setting_values.dart';
import 'package:niko_desktop/theme/niko_theme.dart';
import 'package:niko_desktop/ui/fields.dart';

import 'fonts.dart';

/// Настройки › Вход both ways (docs/research/list-screens-probe.md): by hand (variant А) and from
/// shared/screens/set_inbound.json (variant В). Both are held to the same picture, so the description draws what the
/// hand-made screen draws, pixel for pixel; and both behave the same.
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

  final ways = <String, Widget Function(SettingValues values, void Function() newPassword)>{
    'by hand': (values, newPassword) =>
        InboundScreen(values: values, strings: NikoStrings.of(const Locale('ru')), onNewPassword: newPassword),
    'from its description': (values, newPassword) => ListScreen(
        spec: ListScreens.setInbound,
        values: values,
        locale: const Locale('ru'),
        onAction: (id) => id == 'newPassword' ? newPassword() : fail('no action $id')),
  };

  Future<void> show(WidgetTester tester, Widget screen, Brightness brightness) async {
    tester.view.physicalSize = const Size(560, 420);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    await tester.pumpWidget(frame(brightness, screen));
  }

  for (final MapEntry(key: way, value: build) in ways.entries) {
    for (final brightness in Brightness.values) {
      testWidgets('$way, ${brightness.name}: the picture both ways share', (tester) async {
        await show(tester, build(values(), () {}), brightness);
        // compared on Linux, where they were made: text is drawn a little differently elsewhere
        if (Platform.isLinux) {
          await expectLater(find.byType(NikoSettingsPage), matchesGoldenFile('goldens/set_inbound_${brightness.name}.png'));
        }
      });
    }

    testWidgets('$way: the switch, the port and the action', (tester) async {
      final v = values();
      var asked = 0;
      await show(tester, build(v, () => asked++), Brightness.light);

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
  }

  testWidgets('a link row: its summary, a chevron, its id when pressed', (tester) async {
    final opened = <String>[];
    await show(
      tester,
      ListScreen(
        spec: const ListSpec(title: 'inbound_title', back: 'screen_back', lines: [
          GroupLine([LinkItem('inbound', 'inbound_title'), LinkItem('lan', 'inbound_lan')]),
        ]),
        values: MemorySettingValues(summaries: {'inbound': '127.0.0.1:2080'}),
        locale: const Locale('en'),
        onAction: opened.add,
      ),
      Brightness.light,
    );
    expect(find.text('127.0.0.1:2080'), findsOneWidget);
    expect(find.byType(NikoLinkRow), findsNWidgets(2));
    await tester.tap(find.text('Access from the LAN'));
    expect(opened, ['lan']);
  });
}

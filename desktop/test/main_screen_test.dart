import 'dart:async';
import 'dart:io';
import 'dart:ui';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:niko_desktop/app.dart';
import 'package:niko_desktop/daemon/daemon.dart';
import 'package:niko_desktop/state/connection_controller.dart';
import 'package:niko_desktop/ui/connect_button.dart';

/// A daemon in memory: the test says what the core does, and reads what the window asked.
class MemoryDaemon implements Daemon {
  final status = StreamController<ServiceState>.broadcast();
  final rates = StreamController<Traffic>.broadcast();
  final started = <String>[];
  int stopped = 0;
  DateTime at = DateTime.utc(2026, 11, 16, 20);
  Completer<void>? holdStart;

  @override
  Future<String> version() async => '1.14.4-niko';
  @override
  Stream<ServiceState> serviceStatus() => status.stream;
  @override
  Stream<Traffic> traffic(Duration interval) => rates.stream;
  @override
  Future<DateTime> startedAt() async => at;
  @override
  Future<void> start(String config) async {
    started.add(config);
    await holdStart?.future;
  }

  @override
  Future<void> stop() async => stopped++;
  @override
  Future<void> close() async {}
}

Future<void> loadFonts() async {
  for (final MapEntry(key: family, value: files) in {
    'Geist': ['Geist-Regular', 'Geist-Medium', 'Geist-SemiBold'],
    'Geist Mono': ['GeistMono-Regular', 'GeistMono-Medium'],
  }.entries) {
    final loader = FontLoader(family);
    for (final file in files) {
      loader.addFont(File('fonts/$file.ttf').readAsBytes().then((bytes) => ByteData.sublistView(bytes)));
    }
    await loader.load();
  }
}

void main() {
  setUpAll(loadFonts);

  late MemoryDaemon daemon;
  late ConnectionController controller;

  Future<void> show(
    WidgetTester tester, {
    Brightness brightness = Brightness.dark,
    String locale = 'ru',
    bool withDaemon = true,
    bool withConfig = true,
  }) async {
    tester.view.physicalSize = const Size(960, 720);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    daemon = MemoryDaemon();
    controller = ConnectionController(
      daemon: withDaemon ? daemon : null,
      loadConfig: withConfig ? () async => '{}' : null,
    )..attach();
    addTearDown(controller.dispose);
    await tester.pumpWidget(NikoApp(
      controller: controller,
      daemonAddress: withDaemon ? '127.0.0.1:18080' : '',
      configName: withConfig && withDaemon ? 'direct.json' : '',
      locale: Locale(locale),
      brightness: brightness,
      now: () => daemon.at.add(const Duration(minutes: 42, seconds: 17)),
    ));
    await tester.pump();
  }

  Future<void> core(WidgetTester tester, ServicePhase phase, [String error = '']) async {
    daemon.status.add(ServiceState(phase, error));
    await tester.pump();
    await tester.pump();
  }

  ConnectLook look(WidgetTester tester) => tester.widget<ConnectButton>(find.byType(ConnectButton)).look;

  // the pictures are compared on Linux, where they were made: text is drawn a little differently elsewhere
  Future<void> picture(String name) async {
    if (Platform.isLinux) await expectLater(find.byType(NikoApp), matchesGoldenFile('goldens/$name.png'));
  }

  testWidgets('off: the button connects, nothing ticks', (tester) async {
    await show(tester);
    await core(tester, ServicePhase.idle);
    expect(find.text('Отключено'), findsOneWidget);
    expect(look(tester), ConnectLook.off);
    expect(find.bySemanticsLabel('Подключить'), findsOneWidget);
    expect(find.text('127.0.0.1:18080 · 1.14.4-niko'), findsOneWidget);
    await picture('main_off_dark');
  });

  testWidgets('a press starts the config and shows it at once; connected: the time and the rates', (tester) async {
    await show(tester);
    await core(tester, ServicePhase.idle);
    daemon.holdStart = Completer<void>();
    await tester.tap(find.byType(ConnectButton));
    await tester.pump();
    expect(daemon.started, ['{}']);
    // the daemon has not answered yet
    expect(find.text('Подключение'), findsOneWidget);
    expect(look(tester), ConnectLook.connecting);
    await picture('main_connecting_dark');

    daemon.holdStart!.complete();
    await core(tester, ServicePhase.started);
    daemon.rates.add(const Traffic(up: 312 * 1024, down: 4404019, upTotal: 12 * 1024 * 1024, downTotal: 409 * 1024 * 1024));
    await tester.pump();
    await tester.pump();
    expect(find.text('Подключено'), findsOneWidget);
    expect(find.text('00:42:17 · ↓ 4.2 MB/s · ↑ 312 KB/s'), findsOneWidget);
    expect(find.text('↓ 409 MB · ↑ 12 MB'), findsOneWidget);
    expect(look(tester), ConnectLook.on);
    expect(find.bySemanticsLabel('Отключить'), findsOneWidget);
    await picture('main_on_dark');

    await tester.tap(find.byType(ConnectButton));
    await tester.pump();
    expect(daemon.stopped, 1);
    await core(tester, ServicePhase.stopping);
    expect(find.text('Отключение'), findsOneWidget);
    await core(tester, ServicePhase.idle);
    expect(find.text('Отключено'), findsOneWidget);
    expect(find.textContaining('MB/s'), findsNothing);
  });

  testWidgets('the keyboard: Enter on the focused button connects', (tester) async {
    await show(tester);
    await core(tester, ServicePhase.idle);
    await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    await tester.pump();
    await tester.sendKeyEvent(LogicalKeyboardKey.enter);
    await tester.pump();
    expect(daemon.started, ['{}']);
    await core(tester, ServicePhase.started);
  });

  testWidgets('a failed start: the reason in words and an icon, the button retries', (tester) async {
    await show(tester, brightness: Brightness.light);
    await core(tester, ServicePhase.fatal, 'start service: listen tcp 127.0.0.1:2080: bind: address already in use');
    expect(find.text('Ошибка'), findsOneWidget);
    expect(find.text('Ошибка подключения'), findsOneWidget);
    expect(find.textContaining('address already in use'), findsOneWidget);
    expect(look(tester), ConnectLook.error);
    expect(find.bySemanticsLabel('Повторить'), findsOneWidget);
    await picture('main_error_light');
    await tester.tap(find.byType(ConnectButton));
    await tester.pump();
    expect(daemon.started, ['{}']);
  });

  testWidgets('no service: said, and the button does nothing', (tester) async {
    await show(tester, brightness: Brightness.light, withDaemon: false);
    expect(find.text('Служба не установлена'), findsOneWidget);
    expect(tester.widget<ConnectButton>(find.byType(ConnectButton)).onPressed, isNull);
    await picture('main_no_service_light');
  });

  testWidgets('no config: said, and the button does nothing', (tester) async {
    await show(tester, withConfig: false);
    await core(tester, ServicePhase.idle);
    expect(find.text('Нет конфига'), findsOneWidget);
    expect(tester.widget<ConnectButton>(find.byType(ConnectButton)).onPressed, isNull);
  });

  testWidgets('English', (tester) async {
    await show(tester, locale: 'en');
    await core(tester, ServicePhase.idle);
    expect(find.text('Disconnected'), findsOneWidget);
    expect(find.bySemanticsLabel('Connect'), findsOneWidget);
    expect(find.text('DEVELOPMENT'), findsOneWidget);
  });

  testWidgets('a narrow and low window: nothing overflows', (tester) async {
    await show(tester);
    tester.view.physicalSize = const Size(320, 360);
    await core(tester, ServicePhase.fatal, 'a long reason ' * 12);
    expect(tester.takeException(), isNull);
  });
}

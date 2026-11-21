import 'dart:async';
import 'dart:io';

import 'package:flutter/services.dart';
import 'package:flutter/widgets.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:niko_desktop/app.dart';
import 'package:niko_desktop/daemon/daemon.dart';
import 'package:niko_desktop/state/connection_controller.dart';
import 'package:niko_desktop/state/window_settings.dart';
import 'package:niko_desktop/ui/connect_button.dart';
import 'package:niko_desktop/ui/controls.dart';

import 'fonts.dart';

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

  final logBatches = StreamController<LogBatch>.broadcast();
  int cleared = 0;
  final connectionBatches = StreamController<ConnectionBatch>.broadcast();
  final closedConnections = <String>[];
  int closedAll = 0;

  @override
  Stream<LogBatch> logs() => logBatches.stream;
  @override
  Future<void> clearLogs() async => cleared++;
  @override
  Stream<ConnectionBatch> connections(Duration interval) => connectionBatches.stream;
  @override
  Future<void> closeConnection(String id) async => closedConnections.add(id);
  @override
  Future<void> closeAllConnections() async => closedAll++;

  var reportList = <ReportEntry>[];
  final exportedReports = <String>[];
  @override
  Future<List<ReportEntry>> reports() async => reportList;
  @override
  Future<ReportArchive> exportReport(ReportEntry report) async {
    exportedReports.add(report.name);
    return ReportArchive('${report.name}.zip', 'PK'.codeUnits);
  }

  @override
  Future<void> markReportRead(ReportEntry report) async {}

  /// The STUN test: each run gets a stream of its own, fed by the test.
  final stunRuns = <(String, StreamController<StunProgress>)>[];
  @override
  Future<NikoInfo> nikoInfo() async =>
      const NikoInfo(nikoVersion: '1.58.0', coreVersion: '1.14.4-niko', storeSchema: 0, api: 1);

  @override
  Future<RouteVerdict> routeTest(
    String host,
    int port, {
    String network = 'tcp',
    String inbound = '',
    bool resolve = false,
  }) async => const RouteVerdict(index: -1, rule: '', action: 'final', outbound: 'proxy');

  @override
  Stream<StunProgress> stunTest(String server) {
    final steps = StreamController<StunProgress>();
    stunRuns.add((server, steps));
    return steps.stream;
  }

  /// The store: what the test puts in it, and the selection the window makes.
  final storeEvents = StreamController<StoreChange>.broadcast();
  var groupRows = <GroupRow>[const GroupRow(id: 1, name: '', ungrouped: true)];
  var profileRows = <ProfileRow>[];
  int selectedId = 0;
  int _revision = 0;

  @override
  Stream<StoreChange> storeChanges() async* {
    yield StoreChange(revision: _revision, reset: true);
    yield* storeEvents.stream;
  }

  @override
  Future<List<ProfileRow>> profiles({int groupId = 0}) async =>
      [for (final p in profileRows) if (groupId == 0 || p.groupId == groupId) p];
  int _nextId = 100;
  void _changed(StoreKind kind, List<int> ids) => storeEvents.add(StoreChange(revision: ++_revision, kind: kind, ids: ids));

  /// An input as the daemon would see it: a line with :// is a profile named by what follows its #.
  List<ProfileRow> _parse(String text, int groupId) => [
        for (final line in text.split('\n').map((l) => l.trim()).where((l) => l.contains('://')))
          ProfileRow(
            id: 0,
            groupId: groupId,
            order: 0,
            type: line.split('://').first.replaceAll('socks5', 'socks'),
            name: line.contains('#') ? line.split('#').last : line.split('://').first,
            address: '198.51.100.1:443',
          ),
      ];

  @override
  Future<ImportPreview> recognize(String text) async {
    if (text.trim().startsWith('https://')) return ImportPreview(kind: ImportKind.subscription, subscription: text.trim());
    final rows = _parse(text, 0);
    return rows.isEmpty ? const ImportPreview() : ImportPreview(kind: ImportKind.profiles, profiles: rows);
  }

  @override
  Future<List<int>> importLinks(String text, {int groupId = 0}) async {
    final group = groupId == 0 ? groupRows.firstWhere((g) => g.ungrouped).id : groupId;
    final ids = <int>[];
    for (final row in _parse(text, group)) {
      final id = ++_nextId;
      profileRows.add(ProfileRow(id: id, groupId: group, order: id, type: row.type, name: row.name, address: row.address));
      ids.add(id);
    }
    _changed(StoreKind.profiles, ids);
    return ids;
  }

  @override
  Future<String> shareLink(int id) async => 'socks5://shared.example:1080/#$id';

  @override
  Future<SavedProfile> profile(int id) async =>
      SavedProfile(row: profileRows.firstWhere((p) => p.id == id), beanJson: '{"id":$id}');

  @override
  Future<int> addProfile({required int groupId, required String type, required String beanJson}) async {
    final id = ++_nextId;
    final was = beans[beanJson];
    profileRows.add(ProfileRow(
        id: id, groupId: groupId, order: id, type: type, name: was?.name ?? 'new', address: was?.address ?? ''));
    _changed(StoreKind.profiles, [id]);
    return id;
  }

  /// The rows of the beans the window read, to put them back as they were.
  final beans = <String, ProfileRow>{};

  @override
  Future<void> deleteProfiles(List<int> ids) async {
    for (final p in profileRows.where((p) => ids.contains(p.id))) {
      beans['{"id":${p.id}}'] = p;
    }
    profileRows.removeWhere((p) => ids.contains(p.id));
    _changed(StoreKind.profiles, ids);
    if (ids.contains(selectedId)) {
      selectedId = 0;
      _changed(StoreKind.selected, const []);
    }
  }

  @override
  Future<void> moveProfiles(List<int> ids, {required int groupId, int before = 0}) async {
    final moved = profileRows.where((p) => ids.contains(p.id)).toList();
    profileRows.removeWhere((p) => ids.contains(p.id));
    final at = before == 0 ? profileRows.length : profileRows.indexWhere((p) => p.id == before);
    profileRows.insertAll(at < 0 ? profileRows.length : at, moved);
    _changed(StoreKind.profiles, ids);
  }

  @override
  Future<List<GroupRow>> groups() async => groupRows;
  @override
  Future<int> selected() async => selectedId;
  @override
  Future<void> select(int profileId) async {
    selectedId = profileId;
    storeEvents.add(StoreChange(revision: ++_revision, kind: StoreKind.selected, ids: [profileId]));
  }

  @override
  Future<void> close() async {}
}

void main() {
  setUpAll(loadFonts);

  late MemoryDaemon daemon;
  late ConnectionController controller;
  // the clock of the tests: 42 minutes and 17 seconds after the core's start
  final daemonClock = DateTime.utc(2026, 11, 16, 20).add(const Duration(minutes: 42, seconds: 17));

  Future<void> show(
    WidgetTester tester, {
    Brightness brightness = Brightness.dark,
    String locale = 'ru',
    bool withDaemon = true,
    bool withConfig = true,
    WindowSettings? settings,
    Directory? Function()? saveTo,
    Size size = const Size(960, 720),
  }) async {
    tester.view.physicalSize = size;
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    daemon = MemoryDaemon();
    controller = ConnectionController(
      daemon: withDaemon ? daemon : null,
      loadConfig: withConfig ? () async => '{}' : null,
    )..attach();
    addTearDown(controller.dispose);
    await tester.pumpWidget(
      NikoApp(
        controller: controller,
        settings: settings,
        saveTo: saveTo ?? () => null,
        daemonAddress: withDaemon ? '127.0.0.1:18080' : '',
        configName: withConfig && withDaemon ? 'direct.json' : '',
        locale: Locale(locale),
        brightness: brightness,
        now: () => daemon.at.add(const Duration(minutes: 42, seconds: 17)),
      ),
    );
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
    daemon.rates.add(
      const Traffic(up: 312 * 1024, down: 4404019, upTotal: 12 * 1024 * 1024, downTotal: 409 * 1024 * 1024),
    );
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
    // the header first: three sections and the two views; then the button
    for (var stop = 0; stop < 6; stop++) {
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
    }
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

  group('the header', () {
    testWidgets('the simple view has the main screen and the log; the advanced one adds the connections', (
      tester,
    ) async {
      await show(tester);
      await core(tester, ServicePhase.idle);
      expect(find.text('Главная'), findsOneWidget);
      expect(find.text('Журнал'), findsOneWidget);
      expect(find.text('Соединения'), findsNothing);
      expect(find.bySemanticsLabel('Вид интерфейса'), findsOneWidget);

      await tester.tap(find.text('Расширенный'));
      await tester.pump();
      expect(find.text('Соединения'), findsOneWidget);
      // the view changes what is shown, not the connection
      expect(daemon.started, isEmpty);
      expect(daemon.stopped, 0);
      expect(find.byType(ConnectButton), findsOneWidget);

      await tester.tap(find.text('Соединения'));
      await tester.pump();
      expect(find.byType(ConnectButton), findsNothing);
      expect(find.text('Подключение выключено'), findsOneWidget);
      // back to the simple view: the section it does not have gives way to the main screen
      await tester.tap(find.text('Простой'));
      await tester.pump();
      expect(find.text('Соединения'), findsNothing);
      expect(find.byType(ConnectButton), findsOneWidget);
    });

    testWidgets('the keyboard reaches the sections and the view', (tester) async {
      await show(tester);
      await core(tester, ServicePhase.idle);
      // the first stop is the first section, the second Узлы, the third the log
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.sendKeyEvent(LogicalKeyboardKey.tab);
      await tester.pump();
      await tester.sendKeyEvent(LogicalKeyboardKey.enter);
      await tester.pump();
      expect(find.byType(ConnectButton), findsNothing);
      expect(find.text('Журнал пуст'), findsOneWidget);
    });
  });

  group('the narrow window', () {
    testWidgets('the sections fold into three bars, which open them as a list', (tester) async {
      await show(tester, size: const Size(600, 640));
      await core(tester, ServicePhase.idle);
      // the sections are not in the line, the bars are
      expect(find.text('Журнал'), findsNothing);
      expect(find.bySemanticsLabel('Разделы'), findsOneWidget);
      await picture('narrow_dark');
      await tester.tap(find.bySemanticsLabel('Разделы'));
      await tester.pump();
      expect(find.text('Главная'), findsOneWidget);
      expect(find.text('Журнал'), findsOneWidget);
      await picture('narrow_menu_dark');
      // a choice opens the section and closes the list
      await tester.tap(find.text('Журнал'));
      await tester.pump();
      expect(find.text('Журнал пуст'), findsOneWidget);
      expect(find.text('Главная'), findsNothing);
      // Escape closes it too, and so does a press outside it
      await tester.tap(find.bySemanticsLabel('Разделы'));
      await tester.pump();
      expect(find.text('Главная'), findsOneWidget);
      await tester.sendKeyEvent(LogicalKeyboardKey.escape);
      await tester.pump();
      expect(find.text('Главная'), findsNothing);
      await tester.tap(find.bySemanticsLabel('Разделы'));
      await tester.pump();
      await tester.tapAt(const Offset(500, 600));
      await tester.pump();
      expect(find.text('Главная'), findsNothing);
      expect(tester.takeException(), isNull);
    });

    testWidgets('a window grown wide again shows the sections in the line', (tester) async {
      await show(tester, size: const Size(600, 640));
      await core(tester, ServicePhase.idle);
      await tester.tap(find.bySemanticsLabel('Разделы'));
      await tester.pump();
      tester.view.physicalSize = const Size(960, 720);
      await tester.pump();
      await tester.pump();
      expect(find.bySemanticsLabel('Разделы'), findsOneWidget);
      // one of each: the list closed when the line came back
      expect(find.text('Журнал'), findsOneWidget);
    });
  });

  group('the view remembered', () {
    testWidgets('the window opens in the view chosen last time, and keeps a new choice', (tester) async {
      final settings = WindowSettings.memory(advanced: true);
      await show(tester, settings: settings);
      await core(tester, ServicePhase.idle);
      expect(find.text('Соединения'), findsOneWidget);
      await tester.tap(find.text('Простой'));
      await tester.pump();
      expect(settings.advanced, isFalse);
      expect(find.text('Соединения'), findsNothing);
    });
  });

  group('the log', () {
    Future<void> openLog(WidgetTester tester, {Brightness brightness = Brightness.dark}) async {
      await show(tester, brightness: brightness);
      await core(tester, ServicePhase.started);
      await tester.tap(find.text('Журнал'));
      await tester.pump();
    }

    testWidgets('the core\'s lines, an error in the danger colour, and the clearing', (tester) async {
      await openLog(tester);
      expect(find.text('Журнал пуст'), findsOneWidget);
      expect(find.bySemanticsLabel('Журнал ядра'), findsOneWidget);
      daemon.logBatches.add(
        const LogBatch([
          LogLine(LogSeverity.info, 'INFO[0000] sing-box started (0.23s)'),
          LogLine(
            LogSeverity.debug,
            'DEBUG[0000] outbound/knock[proxy-knock]: UDP <node>:443 goes to the slot port 42028',
          ),
          LogLine(LogSeverity.info, 'INFO[0001] outbound/naive[proxy]: outbound connection to www.example.com:443'),
          LogLine(LogSeverity.error, 'ERROR[0011] connection: connection download closed: remote error: timeout'),
          LogLine(LogSeverity.info, 'INFO[0028] raycontrol: switching from naive to hy2, which works on this network'),
        ], reset: true),
      );
      await tester.pump();
      await tester.pump();
      expect(find.text('Журнал пуст'), findsNothing);
      expect(find.textContaining('sing-box started'), findsOneWidget);
      final error = tester.widget<Text>(find.textContaining('remote error: timeout'));
      final quiet = tester.widget<Text>(find.textContaining('slot port 42028'));
      final plain = tester.widget<Text>(find.textContaining('sing-box started'));
      expect({error.style!.color, quiet.style!.color, plain.style!.color}.length, 3);
      await picture('log_dark');

      await tester.tap(find.bySemanticsLabel('Очистить журнал'));
      await tester.pump();
      expect(daemon.cleared, 1);
      // the daemon answers a clearing with a reset
      daemon.logBatches.add(const LogBatch([], reset: true));
      await tester.pump();
      await tester.pump();
      expect(find.text('Журнал пуст'), findsOneWidget);
    });

    testWidgets('the newest line is in view, and stays there as lines come', (tester) async {
      await openLog(tester);
      Future<void> frames() async {
        for (var frame = 0; frame < 5; frame++) {
          await tester.pump();
        }
      }

      daemon.logBatches.add(
        LogBatch([for (var i = 1; i <= 200; i++) LogLine(LogSeverity.info, 'line $i')], reset: true),
      );
      await frames();
      expect(find.text('line 200'), findsOneWidget);
      expect(find.text('line 1'), findsNothing);
      daemon.logBatches.add(const LogBatch([LogLine(LogSeverity.info, 'line 201')]));
      await frames();
      expect(find.text('line 201'), findsOneWidget);

      // the user scrolled up to read: new lines do not pull the view back down
      await tester.drag(find.text('line 195'), const Offset(0, 300));
      await frames();
      expect(find.text('line 201'), findsNothing);
      daemon.logBatches.add(const LogBatch([LogLine(LogSeverity.info, 'line 202')]));
      await frames();
      expect(find.text('line 202'), findsNothing);
      // a clearing starts over at the end
      daemon.logBatches.add(LogBatch([for (var i = 1; i <= 90; i++) LogLine(LogSeverity.info, 'new $i')], reset: true));
      await frames();
      expect(find.text('new 90'), findsOneWidget);
    });

    testWidgets('the NAT tool beside the log: the server typed, the steps, the result in words', (tester) async {
      await openLog(tester);
      expect(find.text('Инструменты'), findsOneWidget);
      expect(find.text('Поведение NAT'), findsOneWidget);
      await tester.enterText(find.byType(EditableText), 'stun.example.net:3478');
      await tester.tap(find.text('Определить'));
      await tester.pump();
      expect(daemon.stunRuns.single.$1, 'stun.example.net:3478');
      final steps = daemon.stunRuns.single.$2;
      // one test at a time
      await tester.tap(find.text('Определить'));
      expect(daemon.stunRuns, hasLength(1));

      steps.add(const StunProgress(externalAddress: '203.0.113.7:40312', latencyMs: 31));
      await tester.pump();
      expect(find.text('203.0.113.7:40312'), findsOneWidget);
      expect(find.text('Сопоставление'), findsNothing);
      steps.add(
        const StunProgress(
          externalAddress: '203.0.113.7:40312',
          latencyMs: 31,
          mapping: NatBehaviour.endpointIndependent,
          filtering: NatBehaviour.addressAndPortDependent,
          natTypeSupported: true,
          isFinal: true,
        ),
      );
      await tester.pump();
      expect(find.text('не зависит от адреса назначения'), findsOneWidget);
      expect(find.text('зависит от адреса и порта'), findsOneWidget);
      expect(find.text('31 ms'), findsOneWidget);
      await picture('log_tools_dark');

      // Enter in the field runs it again; a failure is said in the daemon's words
      await tester.showKeyboard(find.byType(EditableText));
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pump();
      expect(daemon.stunRuns, hasLength(2));
      daemon.stunRuns.last.$2.add(const StunProgress(isFinal: true, error: 'no answer from stun.example.net:3478'));
      await tester.pump();
      expect(find.text('NAT не определён'), findsOneWidget);
      expect(find.text('no answer from stun.example.net:3478'), findsOneWidget);
      expect(find.text('Сопоставление'), findsNothing);
    });

    testWidgets('the core stopped during a check: nothing of it shown, said, its late steps not read', (tester) async {
      await openLog(tester);
      await tester.tap(find.text('Определить'));
      await tester.pump();
      final steps = daemon.stunRuns.single.$2;
      steps.add(const StunProgress(externalAddress: '203.0.113.7:40312', latencyMs: 31));
      await tester.pump();
      expect(find.text('203.0.113.7:40312'), findsOneWidget);
      await core(tester, ServicePhase.stopping);
      expect(find.text('203.0.113.7:40312'), findsNothing);
      expect(find.text('Подключение остановилось во время проверки: подключитесь и проверьте снова'), findsOneWidget);
      // the closed sockets read as a NAT that lets nothing in: not shown as a measurement
      steps.add(
        const StunProgress(
          externalAddress: '203.0.113.7:40312',
          filtering: NatBehaviour.addressAndPortDependent,
          natTypeSupported: true,
          isFinal: true,
        ),
      );
      await tester.pump();
      expect(find.text('Фильтрация'), findsNothing);
      await core(tester, ServicePhase.started);
      expect(find.text('Подключение остановилось во время проверки: подключитесь и проверьте снова'), findsNothing);
      // stopped again with no check running: the plain note, not the interrupted check's
      await core(tester, ServicePhase.stopping);
      expect(find.text('Подключение остановилось во время проверки: подключитесь и проверьте снова'), findsNothing);
      expect(find.text('Проверка идёт через подключение: сначала подключитесь'), findsOneWidget);
      await core(tester, ServicePhase.started);
      await tester.tap(find.text('Определить'));
      await tester.pump();
      expect(daemon.stunRuns, hasLength(2));
    });

    testWidgets('a window crossing the width keeps the typed server and the log', (tester) async {
      await openLog(tester);
      daemon.logBatches.add(
        LogBatch([for (var i = 1; i <= 200; i++) LogLine(LogSeverity.info, 'line $i')], reset: true),
      );
      for (var i = 0; i < 6; i++) {
        await tester.pump();
      }
      await tester.enterText(find.byType(EditableText), 'stun.example.net:3478');
      await tester.pump();
      expect(find.text('line 200'), findsOneWidget);
      tester.view.physicalSize = const Size(800, 720);
      for (var i = 0; i < 6; i++) {
        await tester.pump();
      }
      expect(find.text('stun.example.net:3478'), findsOneWidget);
      expect(find.text('line 200'), findsOneWidget);
      tester.view.physicalSize = const Size(960, 720);
      for (var i = 0; i < 6; i++) {
        await tester.pump();
      }
      expect(find.text('stun.example.net:3478'), findsOneWidget);
      expect(find.text('line 200'), findsOneWidget);
    });

    testWidgets('the NAT tool with the core off: says to connect, and does nothing', (tester) async {
      await show(tester);
      await tester.tap(find.text('Журнал'));
      await tester.pump();
      expect(find.text('Проверка идёт через подключение: сначала подключитесь'), findsOneWidget);
      await tester.tap(find.text('Определить'));
      await tester.pump();
      expect(daemon.stunRuns, isEmpty);
      await core(tester, ServicePhase.started);
      expect(find.text('Проверка идёт через подключение: сначала подключитесь'), findsNothing);
    });

    testWidgets('a narrow window: the tools under the log, nothing overflows', (tester) async {
      await show(tester, brightness: Brightness.light, size: const Size(800, 720));
      daemon.reportList = [ReportEntry(ReportKind.crash, 'crash-1', DateTime(2026, 11, 17, 0, 41))];
      await core(tester, ServicePhase.started);
      await tester.tap(find.text('Журнал'));
      await tester.pump();
      await tester.pump();
      final log = tester.getRect(find.bySemanticsLabel('Журнал ядра'));
      final tools = tester.getRect(find.text('Инструменты'));
      expect(tools.top, greaterThan(log.bottom));
      await picture('log_tools_narrow_light');
      tester.view.physicalSize = const Size(320, 360);
      await tester.pump();
      expect(tester.takeException(), isNull);
    });

    testWidgets('a crash the daemon kept: said above the log, saved by its action', (tester) async {
      late Directory folder;
      await tester.runAsync(() async => folder = await Directory.systemTemp.createTemp('niko-save-'));
      addTearDown(() => folder.deleteSync(recursive: true));
      await show(tester, brightness: Brightness.light, saveTo: () => folder);
      daemon.reportList = [ReportEntry(ReportKind.crash, 'crash-1', DateTime(2026, 11, 17, 0, 41))];
      await core(tester, ServicePhase.started);
      await tester.tap(find.text('Журнал'));
      await tester.pump();
      await tester.pump();
      expect(find.text('Прошлый запуск завершился сбоем'), findsOneWidget);
      expect(find.text('17.11.2026 00:41'), findsOneWidget);
      await picture('log_report_light');
      await tester.runAsync(() async => tester.tap(find.text('Экспорт')));
      // until the window says it saved: the file is there as soon as it is opened (on Windows too), the archive still
      // being written into it and the file still open
      for (var i = 0; i < 150 && find.text('Сохранено').evaluate().isEmpty; i++) {
        await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 20)));
        await tester.pump();
      }
      expect(daemon.exportedReports, ['crash-1']);
      expect(find.text('Сохранено'), findsOneWidget);
      expect(find.textContaining('crash-1.zip'), findsOneWidget);
      expect(find.text('Прошлый запуск завершился сбоем'), findsNothing);
    });

    testWidgets('the log copied whole, and said', (tester) async {
      String? copied;
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') copied = (call.arguments as Map)['text'] as String?;
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      await show(tester);
      await core(tester, ServicePhase.started);
      await tester.tap(find.text('Журнал'));
      await tester.pump();
      daemon.logBatches.add(
        const LogBatch([
          LogLine(LogSeverity.info, 'INFO[0000] one'),
          LogLine(LogSeverity.error, 'ERROR[0001] two'),
        ], reset: true),
      );
      await tester.pump();
      await tester.pump();
      await tester.tap(find.bySemanticsLabel('Скопировать журнал'));
      await tester.pump();
      await tester.pump();
      expect(copied, 'INFO[0000] one\nERROR[0001] two');
      expect(find.text('Журнал скопирован'), findsOneWidget);
    });

    testWidgets('the log is read only while its screen is shown', (tester) async {
      await show(tester);
      await core(tester, ServicePhase.idle);
      expect(daemon.logBatches.hasListener, isFalse);
      await tester.tap(find.text('Журнал'));
      await tester.pump();
      expect(daemon.logBatches.hasListener, isTrue);
      await tester.tap(find.text('Главная'));
      await tester.pump();
      expect(daemon.logBatches.hasListener, isFalse);
    });
  });

  group('the connections', () {
    final opened = daemonClock.subtract(const Duration(minutes: 4));
    ConnectionInfo one(
      String id,
      String destination, {
      String domain = '',
      String process = '',
      String rule = '',
      String outbound = 'arngory · naive',
      String type = 'naive',
      String network = 'tcp',
      String protocol = '',
      int up = 0,
      int down = 0,
      Duration age = const Duration(minutes: 4),
    }) => ConnectionInfo(
      id: id,
      network: network,
      destination: destination,
      domain: domain,
      protocol: protocol,
      process: process,
      rule: rule,
      outbound: outbound,
      outboundType: type,
      createdAt: opened.add(const Duration(minutes: 4)).subtract(age),
      up: up,
      down: down,
    );

    Future<void> openConnections(WidgetTester tester, {Brightness brightness = Brightness.light}) async {
      await show(tester, brightness: brightness);
      await core(tester, ServicePhase.started);
      await tester.tap(find.text('Расширенный'));
      await tester.pump();
      await tester.tap(find.text('Соединения'));
      await tester.pump();
      daemon.connectionBatches.add(
        ConnectionBatch([
          ConnectionChange.opened(
            one(
              'a',
              '93.184.216.34:443',
              domain: 'www.example.com',
              process: r'C:\Program Files\Browser\browser.exe',
              protocol: 'tls',
              up: 1258291,
              down: 50331648,
            ),
          ),
          ConnectionChange.opened(
            one(
              'b',
              '203.0.113.9:443',
              domain: 'video.example.net',
              process: '/usr/bin/browser',
              network: 'udp',
              protocol: 'quic',
              up: 6291456,
              down: 325058560,
              age: const Duration(minutes: 12),
            ),
          ),
          ConnectionChange.opened(
            one(
              'c',
              '198.51.100.4:443',
              domain: 'bank.example.ru',
              process: 'browser',
              rule: 'rule_set=geoip-ru',
              outbound: 'direct',
              type: 'direct',
              up: 98304,
              down: 839680,
              age: const Duration(seconds: 75),
            ),
          ),
          ConnectionChange.opened(
            one(
              'd',
              '10.0.0.7:22',
              process: '/usr/bin/ssh',
              rule: 'domain_suffix=corp.example',
              up: 9216,
              down: 14336,
              age: const Duration(minutes: 38),
            ),
          ),
        ], reset: true),
      );
      await tester.pump();
      await tester.pump();
    }

    testWidgets('each one: the target, the program, the rule, the way, the bytes and the age', (tester) async {
      await openConnections(tester);
      expect(find.text('открыто 4'), findsOneWidget);
      expect(find.text('www.example.com:443'), findsOneWidget);
      expect(find.text('browser · TCP (tls) · правило: остальное →'), findsOneWidget);
      expect(find.text('↓ 48 MB ↑ 1.2 MB · 4 min'), findsOneWidget);
      expect(find.text('browser · UDP (quic) · правило: остальное →'), findsOneWidget);
      expect(find.text('10.0.0.7:22'), findsOneWidget);
      expect(find.text('ssh · TCP · правило: domain_suffix=corp.example →'), findsOneWidget);
      expect(find.text('↓ 820 KB ↑ 96 KB · 1 min'), findsOneWidget);
      expect(find.text('DIRECT'), findsOneWidget);
      expect(find.text('ARNGORY · NAIVE'), findsNWidgets(3));
      await picture('connections_light');
    });

    testWidgets('a change moves the bytes; a closed one goes', (tester) async {
      await openConnections(tester);
      daemon.connectionBatches.add(
        const ConnectionBatch([ConnectionChange.moved('d', 1024, 2048), ConnectionChange.closed('b')]),
      );
      await tester.pump();
      await tester.pump();
      expect(find.text('открыто 3'), findsOneWidget);
      expect(find.text('video.example.net:443'), findsNothing);
      expect(find.text('↓ 16 KB ↑ 10 KB · 38 min'), findsOneWidget);
    });

    testWidgets('closing one, and all', (tester) async {
      await openConnections(tester);
      await tester.tap(find.bySemanticsLabel('Закрыть соединение с bank.example.ru:443'));
      await tester.pump();
      expect(daemon.closedConnections, ['c']);
      await tester.tap(find.text('Закрыть все'));
      await tester.pump();
      expect(daemon.closedAll, 1);
    });

    testWidgets('the search narrows the list by any of its words', (tester) async {
      await openConnections(tester);
      await tester.enterText(find.byType(EditableText), 'SSH');
      await tester.pump();
      expect(find.text('10.0.0.7:22'), findsOneWidget);
      expect(find.text('www.example.com:443'), findsNothing);
      // the count is of what is open, not of what is shown
      expect(find.text('открыто 4'), findsOneWidget);
      await tester.enterText(find.byType(EditableText), 'direct');
      await tester.pump();
      expect(find.text('bank.example.ru:443'), findsOneWidget);
      expect(find.text('10.0.0.7:22'), findsNothing);
    });

    testWidgets('the core stopped: said, and nothing to close', (tester) async {
      await openConnections(tester, brightness: Brightness.dark);
      await core(tester, ServicePhase.idle);
      expect(find.text('Подключение выключено'), findsOneWidget);
      expect(find.text('открыто 0'), findsOneWidget);
      expect(find.text('www.example.com:443'), findsNothing);
      await picture('connections_idle_dark');
    });

    testWidgets('a narrow window: nothing overflows', (tester) async {
      await openConnections(tester);
      tester.view.physicalSize = const Size(420, 480);
      await tester.pump();
      expect(tester.takeException(), isNull);
    });
  });

  testWidgets('Узлы: the groups, the profiles of one, the selected one; a press selects', (tester) async {
    await show(tester, brightness: Brightness.light);
    await core(tester, ServicePhase.idle);
    daemon.groupRows = [
      const GroupRow(id: 1, name: '', ungrouped: true, profiles: 2),
      const GroupRow(id: 2, name: 'Работа', profiles: 1),
    ];
    daemon.profileRows = [
      const ProfileRow(id: 1, groupId: 1, order: 1, type: 'naive', name: 'Хельсинки', address: '203.0.113.7:443'),
      const ProfileRow(id: 2, groupId: 1, order: 2, type: 'ss', name: 'Амстердам', address: '198.51.100.20:8443'),
      const ProfileRow(id: 3, groupId: 2, order: 1, type: 'socks', name: 'Офис', address: '192.0.2.10:1080'),
    ];
    daemon.selectedId = 2;
    Future<void> settle() async {
      for (var i = 0; i < 6; i++) {
        await tester.pump();
      }
    }

    await tester.tap(find.text('Узлы'));
    await settle();
    expect(find.text('Свои профили'), findsNWidgets(2), reason: 'the group in the panel and as the title');
    expect(find.text('вручную и по ссылкам · 2'), findsOneWidget);
    expect(find.text('Хельсинки'), findsOneWidget);
    expect(find.text('Офис'), findsNothing);
    List<bool> radios() => tester.widgetList<NikoRadio>(find.byType(NikoRadio)).map((r) => r.on).toList();
    expect(radios(), [false, true]);
    await picture('nodes_light');

    await tester.tap(find.text('Хельсинки'));
    await settle();
    expect(daemon.selectedId, 1);
    expect(radios(), [true, false], reason: 'the list follows the daemon\'s SELECTED');

    await tester.tap(find.text('Работа'));
    await settle();
    expect(find.text('Офис'), findsOneWidget);
    expect(find.text('Хельсинки'), findsNothing);
    expect(radios(), [false], reason: 'the selected profile is in another group');
  });

  testWidgets('Узлы: a profile added from a link; one deleted and put back where it was', (tester) async {
    await show(tester, brightness: Brightness.light);
    await core(tester, ServicePhase.idle);
    daemon.groupRows = [const GroupRow(id: 1, name: '', ungrouped: true, profiles: 2)];
    daemon.profileRows = [
      const ProfileRow(id: 1, groupId: 1, order: 1, type: 'naive', name: 'Хельсинки', address: '203.0.113.7:443'),
      const ProfileRow(id: 2, groupId: 1, order: 2, type: 'ss', name: 'Амстердам', address: '198.51.100.20:8443'),
    ];
    daemon.selectedId = 1;
    Future<void> settle([Duration wait = Duration.zero]) async {
      await tester.pump(wait);
      for (var i = 0; i < 8; i++) {
        await tester.pump();
      }
    }

    List<bool> radios() => tester.widgetList<NikoRadio>(find.byType(NikoRadio)).map((r) => r.on).toList();
    await tester.tap(find.text('Узлы'));
    await settle();

    // added: the link recognized first, nothing written until the button
    await tester.tap(find.bySemanticsLabel('Добавить'));
    await settle();
    expect(find.text('Вставьте ссылку'), findsOneWidget);
    await tester.enterText(find.byType(EditableText), 'socks5://user:fake-pass@198.51.100.40:1080#Офис');
    await settle(const Duration(milliseconds: 300));
    expect(find.text('ПРОФИЛЕЙ В ССЫЛКЕ: 1'), findsOneWidget, reason: 'the overline, in capitals');
    expect(daemon.profileRows, hasLength(2));
    await picture('add_light');
    await tester.tap(find.text('Добавить: 1'));
    await settle();
    expect(find.text('Вставьте ссылку'), findsNothing);
    expect(find.text('Офис'), findsOneWidget);
    expect(daemon.profileRows.map((p) => p.name), ['Хельсинки', 'Амстердам', 'Офис']);

    // a subscription's link is said to come later, and is not added
    await tester.tap(find.bySemanticsLabel('Добавить'));
    await settle();
    await tester.enterText(find.byType(EditableText), 'https://provider.example/sub/0123456789');
    await settle(const Duration(milliseconds: 300));
    expect(find.textContaining('подписки придут'), findsOneWidget);
    await tester.tap(find.text('Отмена'));
    await settle();

    // deleted: gone, the selection with it; put back: in its place, selected again
    await tester.tap(find.bySemanticsLabel('Удалить «Хельсинки»'));
    await settle();
    expect(find.text('Хельсинки'), findsNothing);
    expect(find.text('Профиль «Хельсинки» удалён'), findsOneWidget);
    expect(radios(), [false, false]);
    await tester.tap(find.text('Вернуть'));
    await settle();
    expect(daemon.profileRows.map((p) => p.name), ['Хельсинки', 'Амстердам', 'Офис']);
    expect(radios(), [true, false, false]);
    expect(find.text('Профиль «Хельсинки» удалён'), findsNothing);

    // the way back lasts a while, then goes
    await tester.tap(find.bySemanticsLabel('Удалить «Офис»'));
    await settle();
    expect(find.text('Профиль «Офис» удалён'), findsOneWidget);
    await settle(const Duration(seconds: 9));
    expect(find.text('Профиль «Офис» удалён'), findsNothing);
  });

  testWidgets('Узлы: a profile\'s link copied, and said for a while', (tester) async {
    String? copied;
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.setData') copied = (call.arguments as Map)['text'] as String?;
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
    await show(tester, brightness: Brightness.light);
    await core(tester, ServicePhase.idle);
    daemon.groupRows = [const GroupRow(id: 1, name: '', ungrouped: true, profiles: 1)];
    daemon.profileRows = [
      const ProfileRow(id: 4, groupId: 1, order: 1, type: 'naive', name: 'Хельсинки', address: '203.0.113.7:443'),
    ];
    Future<void> settle([Duration wait = Duration.zero]) async {
      await tester.pump(wait);
      for (var i = 0; i < 8; i++) {
        await tester.pump();
      }
    }

    await tester.tap(find.text('Узлы'));
    await settle();
    await tester.tap(find.bySemanticsLabel('Скопировать ссылку на «Хельсинки»'));
    await settle();
    expect(copied, 'socks5://shared.example:1080/#4');
    expect(find.text('Ссылка на «Хельсинки» скопирована'), findsOneWidget);
    expect(find.text('Вернуть'), findsNothing, reason: 'nothing to undo in a copy');
    await settle(const Duration(seconds: 5));
    expect(find.text('Ссылка на «Хельсинки» скопирована'), findsNothing);
  });

  testWidgets('a narrow and low window: nothing overflows', (tester) async {
    await show(tester);
    tester.view.physicalSize = const Size(320, 360);
    await core(tester, ServicePhase.fatal, 'a long reason ' * 12);
    expect(tester.takeException(), isNull);
  });
}

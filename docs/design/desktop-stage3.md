# Этап 3 десктопа: `nikod` и `NikoService` (план, решения владельца от 2026-11-17)

Черновик для владельца, 2026-11-17. Ничего из этого ещё не сделано. Проверено по коду: этот репозиторий на `905b14c`,
форк ядра на `489ccbd` (тот, что закреплён в `buildScript/lib/core/get_source_env.sh`), raycontrol на `4e772ac`.
Ссылки `файл:строка` — на эти коммиты. Пути форка даны от его корня; пути Kotlin без префикса (`fmt/…`,
`raycontrol/…`, `database/…`) — от `app/src/main/java/io/niko/`, как в [desktop-requirements.md](../spec/desktop-requirements.md).

Этап 3 по [desktop.md](desktop.md): «`nikod` + `NikoService`: хранилище профилей, импорт ссылок и подписок, сборка
конфига в Go», готово, когда «golden-тесты Go = Kotlin». Ниже: что есть на самом деле, что переносить, набросок API,
хранилище, golden-тесты, сборка `nikod`, порядок работ и что решить владельцу.

## 1. Что есть на самом деле

### Окно

- Flutter, около 4000 строк Dart в `desktop/lib`. Умеет то, что описано в [desktop/README.md](../../desktop/README.md):
  главная с готовым JSON, журнал, отчёты, проверка NAT, соединения. Все вызовы — к трём службам форка:
  `DesktopService`, `StartedService`, `ManagedService` (`desktop/lib/daemon/daemon.dart`).
- **Релизная сборка окна с демоном не связана вовсе.** Связь есть только по TCP и только в сборке с `NIKO_DEV`
  (`desktop/lib/main.dart:14`, `:24`); без флага демона у окна нет. Транспорт для релиза (named pipe через worker или
  AF_UNIX) не сделан, выбор отложен на этап 3 (`docs/design/desktop.md:187`). Значит, всё, что даст этап 3, до этого
  выбора проверяется только в режиме разработки.
- Профилей, ссылок и подписок окно не знает. Конфиг — файл, который передаёт `tool/dev.dart`.

### Демон

- `boxdd` — команда апстрима sing-box, не наша: `experimental/boxdd` есть в sing-box v1.14.x и v1.15.0-alpha
  ([pkg.go.dev](https://pkg.go.dev/github.com/sagernet/sing-box@v1.15.0-alpha.13)). Своя правка форка, судя по
  истории и пометкам `niko:`, одна: `53abe2a` (`--listen` на Windows). Апстрим писал демон под своё окно «sing-box
  for desktop» на Electron (см. комментарии про libuv и Node в `experimental/boxdd/server_windows.go:12-24`). Там
  профили живут в окне, а демону приходит готовый конфиг. У нас профили и фоновая работа должны жить в службе —
  отсюда правки форка в разделе 6.
- 48 файлов, около 7300 строк Go без сгенерированных. Службы gRPC: `DesktopService` и `ApplicationService`
  (`experimental/boxdd/desktop_service.proto`), `StartedService` и `ManagedService` (`daemon/*.proto`).
  `ApplicationService` обслуживает не демон, а worker (`experimental/boxdd/cmd_worker.go:69`).
- Данные демона разложены по пользователям: `<рабочий каталог>/users/<sha256(SID или uid)>/`
  (`experimental/boxdd/snapshot.go:34`), там `config.json` и `start_options.json`; владелец службы — в `owner.json`.
  Рабочий каталог Windows — `C:\ProgramData\sing-box-daemon` с защищённым DACL: SYSTEM, администраторы, SID службы
  (`cmd_service_windows.go:23`, `security_windows.go:101`). Linux — `/var/lib/sing-box-daemon`.
- Файлы вне каталога пользователя ядро без «небезопасного режима» не читает (`insecure_mode_supported.go`,
  `restrictedFileManager`). Наборы правил и прочие файлы `NikoService` должен класть туда же.
- При старте службы демон сам поднимает последнее ядро, если оно работало: из сохранённого `config.json`, без новой
  сборки (`experimental/boxdd/server.go:126-181`).

### Что в документах устарело или неверно

1. `docs/design/desktop.md:64-74` — схема «окно → named pipe → worker → демон с `NikoService`» описывает цель, а
   не то, что есть. Сейчас окно ходит в демон только по TCP в режиме разработки, worker'а окно не запускает, а
   называется окно `niko_desktop.exe` (`desktop/windows/CMakeLists.txt:7`), не `sing-box.exe`. Нужна пометка
   «цель».
2. Объёмы. `docs/design/desktop.md:91-92`: «около 6000 строк в `io.niko.raycontrol`», «около 7000 строк в
   `io.niko.fmt`» (как парсеры ссылок); `docs/ui/platforms.md:25-27`: 6300, 4800 и 2000. На `905b14c`:
   - `raycontrol` — 6524 строки, 25 файлов;
   - `fmt` — 4850 Kotlin и 2239 Java, всего 7089, но из них 1759 — сам `ConfigBuilder.kt`; парсеры и бины — около
     5300;
   - вне `fmt`: обычные подписки `group/` — 1296 (`RawUpdater.kt` — 921), модель конфига `SingBoxOptions.java` —
     5632, хранилище `database/` — 2212.
3. `docs/design/desktop.md:94-96` и `:109` — «один набор входов прогоняется через Kotlin и через Go». Для парсеров
   ссылок это реально: такие тесты уже идут в JVM (`app/src/test/java/io/niko/fmt/LinkParsersTest.kt`). Для сборки
   конфига — нет:
   - `buildConfig` (`fmt/ConfigBuilder.kt:164`) читает `DataStore` (Room, 46 настроек), DAO Room, около 35 функций
     синглтонов raycontrol (`RayNodes`, `RayChannel`, `RayBrutal`, `RayLanNet`, `RayDnsResolvers`), файлы наборов
     правил, `SagerNet.application`, показывает `Toast` и пишет побочные заметки (`notes`);
   - unit-тесты приложения — чистый JUnit без Robolectric (`app/build.gradle.kts:44-46`); ни один не вызывает
     `buildConfig`;
   - даже сборщики исходящих читают `DataStore.globalAllowInsecure` (`fmt/hysteria/HysteriaFmt.kt:330`,
     `fmt/v2ray/V2RayFmt.kt:690`, `fmt/tuic/TuicFmt.kt:95`), а `applyNikoParams` — резолверы по умолчанию
     (`fmt/NikoParams.kt:31-53`).

   Чтобы Kotlin вообще прогнал входы golden-тестов, нужен шов в Kotlin (раздел 5). В плане этой работы нет.
4. `docs/design/desktop.md:97-99` — «свой proto рядом с `boxdd`» расходится с `docs/ui/platforms.md:61`: там
   `NikoService` в proto — часть общего слоя, из которого генерируются Kotlin и Dart. Предложение: proto в
   `shared/proto`, не в форке (раздел 3).
5. `docs/design/desktop.md:30-31`: «Так видно, что нарисовано, что сделано и чего нет». Для этого в матрице колонки
   «Android» и «Окно». Пример: D-MAIN-06 включает задержку (`URLTest`), окно её не показывает: в «Окне» — «частично».
6. Каждый ID компьютера из описи функций (`docs/ui/functions.md`) — строка матрицы, в том числе 38 ID из решения 12.
   Для этапа 3 из них важны D-ADD-09…11 (импорт из файла, проверка импорта, обработчик ссылок), D-PROFILE-03…08
   (тонкая настройка, цепочка, список, проверка группы, «поделиться», перенос), D-SUB-05…09, D-GROUP-01, D-SET-20…32,
   D-STATE-RELOAD. NAT — одна функция под двумя ID: D-LOG-03 в матрице и D-TOOLS-01 в описи; в матрице есть оба. В
   описи под D-LOG-03 — выгрузка отладочных сведений (`raycontrol/RayDiagnostics.kt`), строки для неё в матрице нет.
7. `desktop-requirements.md:48`, `:59`, `:106-108`, `:187` — «Go: `libcore/exitaddress.go`», «`routetest.go`», то есть
   «нужна обёртка». Обёрткой не обойтись: эти функции берут `*libcore.BoxInstance` (`libcore/exitaddress.go:92`,
   `libcore/routetest.go:77`) и достают роутер из контекста ядра. В `boxdd` ядро живёт в `daemon.Instance`, его
   контекст — неэкспортируемое поле (`daemon/instance.go:26-40`), наружу есть только `Box()`, а у `box.Box` контекста
   нет. Нужны правка форка (`Instance.Context()`) и вынос тел функций из корневого пакета `libcore` (он для
   gomobile) в подпакеты.
8. `desktop-requirements.md:128` — D-STATE-SVC «установить или режим «Прокси»», логика «есть». Режима без службы в `boxdd`
   нет. Демон слушает только `\\.\pipe\ProtectedPrefix\Administrators\sing-box`
   (`experimental/boxdd/server_windows.go:26`), а канал под этим префиксом создают только администраторы и SYSTEM.
   Ядро в worker'е без прав не запускается: у `ApplicationService` такого вызова нет. «Прокси без службы» — новая
   работа. Владелец решил вариант оставить (решение 9, срез 16).
9. `desktop-requirements.md:219` — BG-01 «Kotlin: `fmt/ConfigBuilder.kt`». Источник шире: решения raycontrol (п. 3),
   `RuleSetFiles.kt`, `RoutePresets.kt`, `database/ProfileManager.kt`, `bg/proto/TestInstance.kt` (сборка для
   проверки профиля). Без этих входов конфиг узла не собрать.
10. `desktop-requirements.md:228` — BG-10 «в `boxdd` пока не подключено». Верно, и подключать надо в форке: журнал ядра
    попадает в кольцо `StartedService` (`daemon/started_service.go:67`) мимо `libcore/redact.go`.
11. `docs/design/desktop.md:86-89` (имена) верно, но неполно. На апстрим завязаны ещё: имя службы и отображаемое
    имя «sing-box Service» (`cmd_service_windows.go:21`), канал `…\Administrators\sing-box`, каталог
    `C:\ProgramData\sing-box-daemon`, действия polkit `io.nekohasekai.sfl.*` (`authorize_linux.go:24-25`). Если на
    машине стоят и «sing-box for desktop», и Niko, службы, каналы и каталоги совпадут.
12. Задача этапа «импорт подписок» пересекается с этапом 4 («raycontrol в Go: подписка узла»,
    `docs/design/desktop.md:110`). Без узлов raycontrol компьютер умеет только чужие ссылки, а главный сценарий
    Niko — узел по приглашению. Граница — решение 1: узел по приглашению входит в этап 3.

Проверено и верно: сборка `boxdd.exe`, `sing-box.exe`, `libcronet.dll` и тесты Go-пакетов под Windows (`desktop.yml`);
проверки клиента в `boxdd` (`docs/design/desktop.md:76-85`); `--listen` в режиме службы запрещён
(`experimental/boxdd/cmd_run_windows.go:58`).

### Вызовы `boxdd`: что брать готовым и чего нет

**Окно зовёт сам, как сейчас:**

| Что | Вызов |
|---|---|
| Статус, журнал, трафик, соединения, время в сети | `StartedService`: `SubscribeServiceStatus`, `SubscribeLog`, `SubscribeStatus`, `SubscribeConnections`, `GetStartedAt` |
| Смена способа без перезапуска (D-MAIN-11, D-WHY-05) | `SubscribeGroups`, `SelectOutbound`, `URLTest` (группа в работающем ядре) |
| Отключить, системный прокси (D-MAIN-04, D-NAV-02) | `ManagedService.StopService`, `SetSystemProxyEnabled` |
| STUN, качество сети | `StartSTUNTest`, `StartNetworkQualityTest` |
| Отчёты, язык, обновление | `DesktopService` |
| Проверка и формат JSON (D-PROFILE-02) | `ApplicationService.CheckConfig`, `FormatConfig` — у worker'а |

**`NikoService` берёт изнутри процесса (Go, не gRPC):**

- запуск ядра: `Daemon.startServiceLocked` (`experimental/boxdd/server.go:211`) — неэкспортирован; логика
  владельца — в `desktopService.StartService` (`desktop_service.go:55`). Нужен экспорт «запустить конфиг от имени
  вызвавшего»;
- работающее ядро: `StartedService.Instance().Box()` — есть; контекст ядра — нет (см. выше);
- проверка конфига: `StartedService.CheckConfig` — есть;
- ключи устройств для стука: `knock.SetDeviceKey` (`protocol/knock/spa2.go:160`) — есть; в одном процессе с ядром
  ключ, как на Android, в конфиг не попадает.

**Нет:**

- регистрации чужой службы: авторизатор отказывает во всём, кроме трёх служб: «the service is not available»
  (`experimental/boxdd/server.go:372`). `NikoService`, зарегистрированный рядом, вне режима разработки получит
  `PermissionDenied`;
- места в восстановлении: при старте службы и в `ReloadService` берётся сохранённый `config.json`
  (`server.go:171`, `managed.go:39`). `NikoService` должен успеть положить ключи стука, а лучше — пересобрать конфиг;
- проверки профиля без работающего ядра (D-MAIN-08): `URLTest` проверяет группу запущенного ядра, а на Android это
  отдельный экземпляр ядра на каждую проверку (`bg/proto/TestInstance.kt`);
- скрытия секретов в журнале (BG-10);
- обхода туннеля для запросов самой службы. На Android `raysub` и `rayapi` получают `Control`, который защищает
  сокет от VPN (`libcore/raysub.go:120`). В `nikod` при включённом TUN подписки, регистрация и стук пойдут в
  туннель. Нужен `Control`, привязывающий сокет к физическому интерфейсу (по монитору интерфейсов ядра).

## 2. Опись

Пути Kotlin — от `app/src/main/java/io/niko/`. Новые пакеты Go — в модуле `libcore`; имена — предложение.

| Область | Kotlin сейчас | Go сейчас | Написать |
|---|---|---|---|
| Хранилище профилей (BG-02) | `database/`, 2212 строк: `ProxyEntity.kt` 562, `ProxyGroup.kt` 155, `SubscriptionBean.java` 153, `RuleEntity.kt` 109, `ProfileManager.kt` 266, `GroupManager.kt` 115, `DataStore.kt` 391, `preference/*` | нет; `boxdd` хранит только `config.json`, `start_options.json`, `owner.json`, `settings.json` (через `atomicfile`) | `libcore/nikostore`: группы, профили, правила, настройки, схема и переходы, события изменений |
| Ссылки (D-ADD-03, D-PROFILE-07) | `ktx/Formats.kt` (`parseProxies`, 106), `fmt/UniversalFmt.kt` 42, `KryoConverters.java` 190, `BoundedKryoInput.java` 46, `AbstractBean.java` 275, бины Java (2239 вместе с ними); парсеры: `v2ray/V2RayFmt.kt` 783 (vmess, vless, trojan), `v2ray/XHTTP.kt` 112, `hysteria/HysteriaFmt.kt` 390, `naive/NaiveFmt.kt` 136, `shadowsocks/` 126, `socks/` 75, `http/` 45, `tuic/` 99, `trojan_go/` 200, `mieru/` 109, `wireguard/` 159, `trojan/` 18, `proxy/anytls`, `proxy/shadowtls`; параметры Niko: `NikoParams.kt` 185, `DnsTunnel.kt` 268, `PortKnocking.kt` 70, `CertificatePinning.kt` 21, `UdpCarriage.kt` 79 | разбора ссылок нет нигде (ни в `libcore`, ни в форке); есть целевые типы — пакет `option` ядра | `libcore/nikofmt`: бины как структуры Go (имена JSON = имена полей Kotlin), разбор и запись ссылок, чтение `sn://` (Kryo) |
| Обычные подписки (D-SUB, BG-03) | `group/RawUpdater.kt` 921 (base64, Clash YAML, SIP008, `outbounds` sing-box, WireGuard), `group/GroupUpdater.kt` 172, `group/SubscriptionHeaders.kt` 54, `group/SubscriptionLinks.kt` 48, `bg/SubscriptionUpdater.kt` 96 | нет; YAML есть в зависимостях форка (`go.yaml.in/yaml/v3`) | `libcore/nikosub`: загрузка, форматы, `subscription-userinfo`, расписание в службе |
| Подписка узла и приглашения (D-ADD-06, D-NODE, D-MAIN-13) | `raycontrol/Rcdev1.kt` 426 (RCINV1, RCINV2, rcdev1), `RayImport.kt` 111, `RayApi.kt` 255, `RayNodes.kt` 734, `RaySubscription.kt` 753, `RayRows.kt` 130, `RayNodeCard.kt` 102, `RayRelays.kt` 135, `DeviceKeyVault.kt` 52, `DnsAuthKey.kt` 30 | `libcore/rayapi` (543 строки без тестов: регистрация, PIN, вызовы API, личность), `libcore/raysub` (639: загрузка, подписи ed25519 и ML-DSA-65, SPA, файлы приложения), `knock` в форке; RCINV2 читает пока только тест (`libcore/rayapi/interop_test.go`) | `libcore/nikoray`: разбор приглашений, узлы (тот же JSON, что `RayNode.toJson`), payload → профили, обёртка ключей (DPAPI) |
| Сборка конфига (BG-01) | `fmt/ConfigBuilder.kt` 1759, `buildSingBoxOutbound*Bean` в `fmt/*`, `SingBoxOptions.java` 5632, `SingBoxOptionsUtil.kt` 231, `bg/proto/TestInstance.kt` 75 | типы и проверка конфига — пакет `option` ядра; `libcore/options_drift_test.go` сверяет `SingBoxOptions.java` с ядром; `libcore/testdata/config_1_14.json` | `libcore/nikoconf`: `Build(Input) (Result, error)` на типах `option` |
| Наборы правил, пресеты (D-RULES-01, -03, -05, D-SET-08, BG-09) | `RuleSetFiles.kt` 248, `RoutePresets.kt` 66 (только «Россия»), `RuleEntity.kt`, `ProfileManager.kt` (правило рекламы при первом запуске), `ui/AssetsActivity.kt` 387 | `libcore/ruleset_check.go` 84 (`CheckRuleSet`), чтение `.srs` в ядре | в `nikoconf`: элементы `PREFIX+URL`, файл `.kinds`, пресеты; в `nikostore`: загрузка и обновление наборов |
| Настройки (D-SET) | `DataStore.kt` (162 свойства), `Constants.kt` (`Key`, 190 констант); сборка читает 46 из них | `boxdd`: `settings.json` (небезопасный режим, язык), `start_options.json` | подмножество настроек компьютера, типизированное |
| Готовый Go за ядром (D-MAIN-03, D-CHECK, D-NODE-09) | — | `exitaddress.go` 160, `routetest.go` 435, `dohcheck.go` 57, `naive_datagram.go` 134, `redact.go` 108 — все в корневом пакете, на `*BoxInstance` | вынести тела в подпакеты, принимающие `context.Context` ядра; корень оставляет тонкие обёртки для gomobile |

**Соответствие Kotlin → Go:**

- `parseProxies` (`ktx/Formats.kt:106`) → `nikofmt.ParseText`: профили и найденные ссылки подписок;
- `parseV2Ray`, `parseV2RayN`, `parseDuckSoft`, `parseHysteria1/2`, `parseNaive`, `parseShadowsocks`, `parseSOCKS`,
  `parseHttp`, `parseTuic`, `parseTrojanGo`, `parseMieru`, `parseTrojan` → `nikofmt.Parse*`; функции `toUri` →
  `nikofmt.Link`;
- `applyNikoParams` (`fmt/NikoParams.kt:23`) → `nikofmt`, но значения по умолчанию — параметром, а не из
  `DataStore`;
- `parseUniversal`, `toUniversalLink`, `KryoConverters.deserialize` и `deserialize` каждого бина → `nikofmt/kryo`:
  формат Kryo `ByteBufferOutput` (целые big-endian, varint, строки Kryo), версия бина первым `int`;
- `RawUpdater.parseRaw`, `parseJSON`, `parseWireGuard`, `loadClashYaml` → `nikosub.Parse`;
  `SubscriptionHeaders` → `nikosub.UserInfo`; загрузка из `GroupUpdater` → `nikosub.Fetch`;
- `buildSingBoxOutbound*Bean` → `nikoconf.outbound(bean) option.Outbound`; `buildConfig` → `nikoconf.Build`;
- `RuleSetFiles.itemsOf`, `url`, `format`, `file`, `available`, `withoutMissing`, `kinds`, `usableInDns` →
  `nikoconf` (решения) и `nikostore` (файлы); `RoutePresets` → `nikoconf.Presets`;
- `ProxyEntity`, `ProxyGroup`, `SubscriptionBean`, `RuleEntity`, `ProfileManager`, `GroupManager` → типы и методы
  `nikostore`, каждый с событием изменения;
- `Rcinv1.parse`, `Rcinv2.parse`, `Rcdev1.parse` → `nikoray.ParseInvite`, `ParseDevice`; `RayApi.register` →
  `rayapi.Register` (есть) + запись узла; `RayNodes.import`, `update`, `remove`, `forHost`, `ownerOf`, `names`,
  `prepareKnock`, `prepareDeviceKey`, `retainCoreKeys` → `nikoray.Nodes`; `RaySubscription.parse`, `store`, `linksOf`,
  `nextRefresh` → `nikoray.ApplyPayload`; `DeviceKeyVault` → `nikoray/vault`;
- `ExitAddress`, `RouteTest`, `DohCheck`, `NaiveDatagramState` → подпакеты `libcore/exitaddr`, `libcore/routetest` и
  т. д.;
- адаптивная часть raycontrol (`RayChannel`, `RayBrutal`, `RayWatchdog`, `RayDnsResolvers`, `RayLan*`, `RayReport`,
  `RayDatagrams`, `RayYggPeers`) — этап 4. В этапе 3 её решения — входы сборщика со значениями «нет данных».

**Протоколы.** Все исходящие — встроенные в ядро, внешних программ на компьютере нет, кроме slipstream (под Windows
не собирается). Порядок: что раздают узлы raycontrol (naive, Hysteria2, VLESS REALITY и XHTTP, DNS-туннели), потом
trojan, ss, socks, http, tuic, WireGuard и AmneziaWG, anytls, shadowtls, цепочка; mieru, trojan-go, ssh, Hysteria1 —
по запросу. Плагины — те, что ядро не заменило (профили-плагины NekoBox `TYPE_NEKO`, Hysteria1 там, где ядро его не
берёт), — переносятся вместе с API плагинов, запускаются от имени пользователя (решение 10, срез 17).

## 3. `NikoService`: набросок API

Proto — в общем слое: `shared/proto/niko/v1/niko_service.proto`. Из него генерируются Go (`libcore/nikoservice`),
Dart (`desktop/lib/gen/niko`) и позже Kotlin. Сгенерированное лежит в репозитории, CI проверяет его скриптом
`--check`, как сейчас `desktop/tool/gen_proto.sh`.

```proto
syntax = "proto3";
package niko.v1;

service NikoService {
  // сведения и изменения
  rpc GetInfo(Empty) returns (Info);                           // версия Niko, схема хранилища
  rpc Watch(WatchRequest) returns (stream Change);             // что изменилось, ход задач

  // профили и группы (BG-02, D-MAIN-07, -10, D-SUB-01, D-PROFILE-01, -05, -08, D-GROUP-01)
  rpc ListGroups(Empty) returns (GroupList);
  rpc ListProfiles(ListProfilesRequest) returns (ProfileList); // строки списка, без бинов
  rpc GetProfile(ProfileId) returns (Profile);                 // с бином
  rpc SaveProfile(Profile) returns (Profile);                  // ручная форма, правка
  rpc DeleteProfiles(ProfileIds) returns (Empty);
  rpc MoveProfiles(MoveRequest) returns (Empty);               // порядок, другая группа
  rpc SaveGroup(Group) returns (Group);                        // своя группа, настройки подписки (D-SUB-08)
  rpc DeleteGroup(GroupId) returns (Empty);                    // D-SUB-04
  rpc ShareProfile(ProfileId) returns (ShareLink);             // ссылка и sn:// (D-PROFILE-07)

  // импорт (D-ADD-03…10)
  rpc Recognize(ImportInput) returns (ImportPreview);          // без записи: что это, что добавится и что заменит
  rpc Import(ImportRequest) returns (stream TaskProgress);     // с записью; приглашение — с PIN

  // обновление (D-SUB-03, -07, D-NODE-02)
  rpc Refresh(RefreshRequest) returns (stream TaskProgress);   // группа, узел или всё

  // узлы raycontrol, статическая часть (D-NODE-01…07, D-MAIN-13)
  rpc ListNodes(Empty) returns (NodeList);
  rpc DeleteNode(NodeId) returns (Empty);

  // подключение (BG-01, D-MAIN-04, -11)
  rpc Select(ProfileId) returns (Empty);        // ядро идёт: SelectOutbound в группе или перезапуск
  rpc Connect(ConnectRequest) returns (Empty);  // собрать конфиг и запустить ядро, как DesktopService.StartService
  rpc BuildConfig(BuildRequest) returns (BuiltConfig);         // D-PROFILE-02, экспорт; секреты скрыты
  rpc TestProfiles(TestRequest) returns (stream ProfileTest);  // D-MAIN-08, -12

  // правила и наборы (D-RULES-01, -03, -05, D-SET-08, BG-09)
  rpc ListRules(Empty) returns (RuleList);
  rpc SaveRule(Rule) returns (Rule);
  rpc DeleteRule(RuleId) returns (Empty);
  rpc MoveRule(MoveRequest) returns (Empty);
  rpc ApplyPreset(PresetRequest) returns (RuleList);
  rpc ListRuleSets(Empty) returns (RuleSetList);               // версия, дата, источник, интервал
  rpc UpdateRuleSets(RuleSetRequest) returns (stream TaskProgress);

  // настройки, резервная копия (D-SET-05, -15, -16, -18, -19, -32)
  rpc GetSettings(Empty) returns (Settings);
  rpc UpdateSettings(SettingsUpdate) returns (Settings);       // только поля из маски
  rpc ExportBackup(BackupRequest) returns (Backup);            // байты; файл сохраняет окно
  rpc ImportBackup(BackupImport) returns (ImportPreview);      // dry_run или запись

  // готовый Go за работающим ядром
  rpc ExitAddress(ExitAddressRequest) returns (ExitAddress);   // D-MAIN-03, -14
  rpc RouteTest(RouteTestRequest) returns (RouteTestResult);   // D-CHECK-03…06
}

message Change {
  uint64 revision = 1;
  bool reset = 2;              // первая посылка и после пропуска: перечитать всё показанное
  Kind kind = 3;               // PROFILES, GROUPS, NODES, RULES, RULE_SETS, SETTINGS, TESTS, SELECTED, TASK
  repeated int64 ids = 4;      // что изменилось; пусто — вся категория
  TaskProgress task = 5;       // при kind = TASK
}

message TaskProgress {
  string task_id = 1;
  string line = 2;             // строка для пользователя, на языке SetLocale
  bool done = 3;
  int32 code = 4;              // код gRPC итога
  string error = 5;
  PinNeeded pin = 6;           // приглашению нужен PIN: сколько попыток, был ли прошлый неверным
}

message Profile {
  int64 id = 1;
  int64 group_id = 2;
  int64 order = 3;
  string type = 4;             // как TypeMap: naive, hysteria, vmess…
  string name = 5;
  string address = 6;
  string bean_json = 7;        // полный бин: JSON с именами полей Kotlin, те же, что в golden-фикстурах
  int32 ping_ms = 8;
  string node_id = 9;          // профиль узла: правит узел, не пользователь
}

message ImportInput {
  string text = 1;             // вставка, буфер, текст QR
  bytes file = 2;              // импорт из файла (D-ADD-09): содержимое, не путь
  string file_name = 3;
}
```

**Унарные и потоковые вызовы.**

- Потоки только от сервера; двунаправленных нет. В Dart пакета `grpc` они есть, но на этапе 2d отмена потока уже
  роняла всё соединение (ошибка `http2`, `desktop/README.md:37-42`), поэтому потоков меньше и они долгие.
- Долгое (импорт, регистрация, обновление, проверка) — задача демона. Поток показывает её ход, но отмена потока
  задачу не отменяет: на Android ответ регистрации тоже может потеряться (`rayapi.ErrAnswerLost`), и задача должна
  дойти до конца без окна. Итог задачи приходит ещё и в `Watch`, так что окно, открытое заново, его увидит.
- PIN без двунаправленного потока: `Import` кончается итогом с `pin`, окно спрашивает PIN и зовёт `Import` снова,
  как цикл в `RayImport.import` (`raycontrol/RayImport.kt:61-86`).

**Как окно узнаёт об изменениях.** Один поток `Watch` на связь с демоном. События говорят только «что изменилось»
(категория и id), данные окно перечитывает унарными вызовами. Номер `revision` растёт в памяти демона; после
перезапуска демона или пропуска первая посылка идёт с `reset`, и окно перечитывает всё, что показывает. Так
изменения от фоновой работы (обновление подписки по времени, правка из другого окна) доходят без отдельных
подписок. Состояние ядра окно, как и сейчас, берёт из `SubscribeServiceStatus`.

**Бин как JSON, а не типизированные сообщения.** Семнадцать типов бинов (`database/ProxyEntity.kt:57-73`) с
полями Niko — это сотни полей proto, которые меняются почти каждую неделю. Одно описание — структуры Go с именами
полей Kotlin — служит и хранилищу, и golden-фикстурам, и форме. Для строки списка поля типизированы. Типизировать
бины целиком можно позже, когда форма устоится.

**Доступ.** Данные — того пользователя Windows, кто вызвал (каталог по SID). `Connect` проверяет владельца так же,
как `StartService`. Ошибки — кодами gRPC: `InvalidArgument` (ссылка не разобрана), `FailedPrecondition` (ядро не
запущено для `RouteTest`, `ExitAddress`), `PermissionDenied` (служба чужая). Текст — на языке из `SetLocale`, через
те же перехватчики `daemon.UnaryLocaleInterceptor`.

**Правило службы с правами SYSTEM.** Окно передаёт содержимое, никогда не путь: служба не читает файл по просьбе
пользователя, который сам его прочесть не может. Подписки — только `http` и `https`, с пределом размера; YAML — с
пределом псевдонимов и вложенности, как `ClashYamlConstructor` на Android (`group/RawUpdater.kt:914`).

**Как сделано в срезе 4 (2026-11-21).** Вызовы хранилища, `Watch`, `Recognize` и `Import` — в
`shared/proto/niko/v1/niko_service.proto`, `API = 2`. Где набросок не договаривал, решено так:

- `Watch(WatchRequest)`: запрос пуст, первая посылка — всегда `reset` с номером «сейчас», продолжить с номера окна
  нельзя. `revision` своя у каждого пользователя (одно хранилище — одна лента), в памяти демона, с 0 после его
  запуска. Кто отстал больше чем на 256 изменений, получает вместо них `reset`. Виды — все из наброска
  (`Change.Kind`); пока приходят `PROFILES`, `GROUPS` и `TASK`. Поток кончается вместе с демоном (`UNAVAILABLE`).
- `TaskProgress`: поле 6 (`pin`) оставлено узлам (срез 11); добавлено `ids` (7) — что задача добавила, чтобы окно
  сразу знало новые профили. Итог (`done`) — последняя посылка потока, и он же ещё раз в `Watch` как `TASK`. Ошибка
  задачи — в `code` и `error` итога, а поток кончается без ошибки; ошибка до задачи (вход больше 4 МиБ, нет
  хранилища) — статусом потока.
- `Import` для ссылок короткий, но идёт задачей, как пойдут подписки и приглашения: поток ждёт итога, отмена потока
  задачу не отменяет. Найденная ссылка подписки (`clash://install-config?`, `sn://subscription?`, http(s), который не
  прокси) делает подпиской весь ввод, как `SubscriptionFoundException` на Android: итог `UNIMPLEMENTED`, ничего не
  записано, а `Recognize` отвечает `SUBSCRIPTION` с этой ссылкой. Разбор — `nikofmt.ParseText`, это `parseProxies`:
  ссылки по словам и по строкам, берётся чтение, давшее больше профилей. Параметры разбора — значения Android по
  умолчанию, пока у компьютера нет своих настроек (срез 13). За один импорт — не больше 10 000 профилей; больше
  отклоняет уже `Recognize` (`INVALID_ARGUMENT`).
- `ImportInput.file` читается как текст UTF-8 (как `readText`), если `text` пуст; JSON, YAML и `.zip` придут с
  подписками и копиями.
- `ImportPreview`: `kind` (`NOTHING`, `PROFILES`, `SUBSCRIPTION`), профили строками без id и ссылка подписки. Что
  импорт заменит — с подписками и копиями.
- `Profile`: `name` и `address` — `displayName` и `displayAddress` бина, только для чтения; `ListProfiles` отдаёт те
  же `Profile` с пустым `bean_json`. `SaveProfile` с `id` меняет бин того же типа на его месте (группа и порядок — через
  `MoveProfiles`). Бин читается строго (`nikofmt.DecodeBean`: лишнее поле или значение другого типа —
  `INVALID_ARGUMENT`) и получает значения по умолчанию. `ping_ms` и `node_id` придут со своими срезами (12 и 11).
- `MoveRequest`: `ids` по порядку, `group_id` — куда, `before` — перед каким профилем той группы (0 — в конец); группа
  нумеруется заново с 1, как в `GroupManager.rearrange`. Группы подписок — только с подписками: `SaveGroup` с
  `SUBSCRIPTION` — `UNIMPLEMENTED`, переносить и импортировать в них нельзя.
- `Group.ungrouped` — группа по умолчанию (`ProxyGroup.ungrouped`): создаётся с хранилищем и не удаляется;
  `Group.profiles` — сколько в группе профилей.
- `Empty` — своё сообщение, без `google/protobuf/empty.proto`.

**Кто вызвал — пока только в режиме разработки.** Вызовы хранилища — `PolicyCaller`, и у каждого пользователя
хранилище своё. Но `boxdd` не называет добавленной службе того, кого проверил: в `Host` нет ни личности вызвавшего, ни
каталога пользователя (крючки раздела 6). Поэтому `nikoservice` знает вызвавшего только в режиме разработки: это TCP на
127.0.0.1 без учётных данных транспорта, и `boxdd` сам считает его пользователем `local`
(`platformFallbackPeerIdentity`). С учётными данными (служба: канал Windows, сокет Linux) вызовы хранилища отвечают
`UNIMPLEMENTED`, а не открывают одно хранилище на всех. Каталог демона `nikoservice` берёт как текущий: `boxdd`
переходит в него до того, как строит службы (`prepareWorkingDirectory`). Крючок в форке нужен до релиза окна
(срез 14): личность вызвавшего из контекста и каталог пользователя в `Host`.

## 4. Хранилище на компьютере

### Где

| | Каталог | Кто читает |
|---|---|---|
| Windows | `C:\ProgramData\sing-box-daemon\users\<sha256(SID)>\niko\` (после смены имён — каталог Niko) | служба (SYSTEM) и администраторы: DACL каталога демона наследуется |
| Linux | `/var/lib/sing-box-daemon/users/<sha256(uid)>/niko/`, 0700, root | служба |
| Разработка | `<--working-directory>/users/<sha256("local")>/niko/` | тот, кто запустил |
| Окно | `%APPDATA%\Niko`, `~/.config/niko`: только своё — `window.json`, недавние проверки (D-CHECK-07) | пользователь |

Данные в каталоге демона, а не в профиле пользователя: служба работает без окна и до входа в систему, а ядро без
небезопасного режима берёт файлы только оттуда. Удаление всех данных уже есть: `DestroyWorkingDirectory`.

Сделано в срезе 4: `<рабочий каталог демона>/users/<sha256(id)>/niko/`, рядом с файлами `boxdd` того же пользователя
(`userWorkingDirectory`); пока только строка «Разработка» (раздел 3, «Кто вызвал»).

**Граница защиты — пользователь Windows, не подпись окна.** DACL каталога и SID в пути защищают данные от других
пользователей машины. От программы того же пользователя они не защищены: проверка клиента в демоне
(`serverHandshake`, `peer_windows.go:88-262`) смотрит путь, файл на диске, подпись Authenticode, командную строку и
родство процессов, но не код в памяти. Программа с правами того же пользователя может запустить подписанное окно
приостановленным и внедрить в него код или подключиться отладчиком, и тогда пройдёт все проверки и вызовет любой метод
`NikoService`. Отсюда правило для API: **ключи raycontrol (DPAPI) не отдаёт ни один вызов `NikoService`** — служба
пользуется ими сама (подпись запросов к ноде, расшифровка подписки), наружу выходят только результаты. Тогда от
программы того же пользователя ключи защищены, а пароли профилей (`GetProfile`, `ShareProfile`, `ExportBackup`,
`BuildConfig`) — нет, как и на Android, где их видит любой, кто управляет приложением: окну они нужны, чтобы
показать и поделиться профилем. DPAPI на всех секретных полях (решение 6) защищает их на диске, а не от этих
вызовов; хранилище под паролем, пока оно заперто, не отдаёт и их. Это надо сказать и в справке.

### Формат

```
niko/
  store.json     схема, счётчики id, группы, профили (бины JSON), правила
  settings.json  настройки компьютера
  nodes.json     узлы raycontrol: тот же JSON, что RayNode.toJson на Android
  state.json     пинги, трафик профилей, выбранный профиль; потеря не страшна
  rulesets/      скачанные наборы и их .kinds
  backup/        копии перед сменой схемы
```

| Вариант | За | Против |
|---|---|---|
| **JSON-файлы, запись через `atomicfile` (предложение)** | так уже пишет `boxdd`; читается глазами; те же JSON, что в golden и в резервной копии; писатель один — служба, хватает мьютекса | без транзакций между файлами; `store.json` переписывается целиком (при 1000 серверов в подписке — около 1–2 МБ, пишется редко) |
| bbolt | транзакции, запись по частям; уже в зависимостях (`github.com/sagernet/bbolt` через `cachefile`) | двоичный файл; схемы и переходы всё равно свои; разбирать руками неудобно |
| SQLite | запросы | `mattn/go-sqlite3` требует cgo, а CI собирает с `CGO_ENABLED=0`; `modernc.org/sqlite` — транслированный C, медленнее и требует точной версии `modernc.org/libc` |

Часто меняющееся (пинги, трафик) — в `state.json`, его пишем лениво. Целостность группа — профили — правила держит
один файл. Если `store.json` вырастет за 10 МБ или запись станет частой — переход на bbolt; формат значений тот же
JSON.

**Сделано в срезе 4** (`libcore/nikostore`): только `store.json` и `backup/`. `settings.json`, `nodes.json`,
`state.json` и `rulesets/` придут со своими срезами (13, 11, 12, 10), правила в `store.json` — со срезом 10. Схема 1:

```json
{
  "schema": 1,
  "next_group": 3,
  "next_profile": 4,
  "groups": [{"id": 1, "order": 1, "ungrouped": true}, {"id": 2, "order": 2, "name": "work"}],
  "profiles": [{"id": 1, "group": 1, "order": 1, "type": "socks", "bean": {"serverAddress": "…"}, "sealed": "dpapi:…"}]
}
```

- `next_group`, `next_profile` — следующие id: id не выдаётся дважды, и в `Watch` окна удалённый профиль не
  возвращается под чужим номером.
- `type` — имя из `TypeMap` (`vmess` и для VLESS); `bean` — JSON бина, как пишет Gson и как в golden-фикстурах, без
  секретных полей там, где их держит `sealed`. Поля, которых эта сборка не знает, при перезаписи сохраняются.
- Запись — своя функция по образцу `atomicfile` (ради теста обрыва): временный файл рядом (0600), `fsync`,
  переименование поверх, `fsync` каталога; что оставила прерванная запись, удаляется при открытии. Не удалась
  запись — в памяти остаётся прежнее. Писатель один — мьютекс хранилища, и изменения для `Watch` сообщаются под ним
  же, в порядке записей.
- Каталоги 0700, файлы 0600; шире — сужаются при открытии. На Windows права — DACL каталога демона.
- Пишется с отступами: 1000 профилей — около 2 МБ.

### Секреты

- **Что секретно:** пароли и UUID профилей, ключи WireGuard, ссылки подписок с токенами, ключ устройства и ключ
  личности узла raycontrol, секрет Clash API, ключ Yggdrasil.
- **Как на Android:** база в закрытых файлах приложения; ключи узла обёрнуты неизвлекаемым ключом Android Keystore
  (`raycontrol/DeviceKeyVault.kt`; поля `device_key_wrapped` узла и `key_wrapped` личности, `RayNodes.kt:51-54`,
  `:218`).
- **На Windows (решение 6):** всё лежит в каталоге с DACL «SYSTEM и администраторы», и **все секретные поля** из
  списка выше дополнительно обёрнуты DPAPI, не только ключ устройства и ключ личности узла. `CryptProtectData` из
  службы, то есть в контексте SYSTEM, с флагом
  `CRYPTPROTECT_UI_FORBIDDEN`, **без** `CRYPTPROTECT_LOCAL_MACHINE` (с ним расшифровать может любой пользователь
  машины, так прямо в документации). В Go это `golang.org/x/sys/windows.CryptProtectData`, без cgo. Метка
  обёртки (`dpapi:`) отличает её от блоба Keystore.
- **Что DPAPI даёт, честно:** копия каталога или резервная копия ProgramData на другой машине бесполезна. От
  администратора этой машины не защищает: он может стать SYSTEM. С диска без BitLocker вместе с кустами реестра
  SYSTEM и SECURITY ключ DPAPI_SYSTEM тоже достаётся. Граница та же, что у самой службы.
- **Где секреты открыты:** в памяти службы и в `config.json` демона, который ядро читает при запуске, под тем же
  DACL. Обёртывать его нечем: ядру нужен открытый конфиг. Поэтому `config.json` пишется на время работы ядра и
  удаляется при остановке.
- **Хранилище под паролем — по желанию (решение 6),** его предлагают при первом запуске (как у VeraCrypt или
  Bitwarden). Пароль не хранится: из него Argon2id даёт ключ, которым зашифрованы секретные поля поверх DPAPI. Цена,
  которую окно говорит при выборе:
  - после перезагрузки или перезапуска службы хранилище заперто, пока пользователь не введёт пароль в окне: нет
    подключения при загрузке, нет фонового обновления подписок (их ссылки — тоже секрет), стук и подпись запросов к
    узлу ждут;
  - ключ живёт в памяти службы до выхода пользователя из системы (`HandleSessionChange`), блокировки по таймеру
    или кнопки «Запереть»;
  - забытый пароль не восстановить: только сброс хранилища и новое приглашение от узла;
  - на Android такого нет; если понадобится — отдельной задачей.
  Делается после хранилища и узла (срез 18).
- **Сделано в срезе 4.** Секретные поля профиля уходят из `bean` в `sealed`: JSON этих полей одним блобом,
  `dpapi:<base64>`. Поля: `password`, `uuid`, `token`, `authPayload`, `obfuscation`, `encryption` (у Trojan-Go в нём
  пароль слоя Shadowsocks), `realityShortId`, `privateKey`, `privateKeyPassphrase`, `peerPreSharedKey`,
  `dnsTunnelKey`, `dnsTunnelSeal`, `yggKey`, `knockSequence` и то, что пользователь пишет целиком и где может быть
  что угодно из этого: `customOutboundJson`, `customConfigJson`, `customJSON` (TUIC), `config`, `extraHeaders`,
  `plugin` (`nikostore/secrets.go`). Пустые поля остаются в `bean`. `CryptProtectData` с `CRYPTPROTECT_UI_FORBIDDEN`,
  без `CRYPTPROTECT_LOCAL_MACHINE`, с постоянной энтропией (`niko store secrets v1`), в контексте процесса демона:
  SYSTEM у службы, пользователь — в режиме разработки. Блоб считается один раз, когда профиль записан, а не при каждой
  перезаписи `store.json`. Профиль, чьи секреты здесь не открыть (каталог с другой машины), остаётся в списке,
  `GetProfile` отвечает `FAILED_PRECONDITION`, а блоб переписывается как был, пока профиль не сохранят заново.
  Обёртка — за интерфейсом `Sealer`: на Windows DPAPI, на Linux пока никакой (права каталога), `systemd-creds` встанет
  туда же. DPAPI проверяется на `windows-latest` (`desktop.yml`). Ссылки подписок, секрет Clash API и ключи узлов
  обернутся так же, когда их срезы положат их в хранилище.
- **Linux:** файлы root 0600. Обёртка через `systemd-creds` (ключ хоста или TPM2) — решить на этапе 6.
- **TPM** (Platform Crypto Provider) дал бы неизвлекаемый ключ-обёртку, как Keystore. Ключ стука всё равно нужен
  ядру в памяти, так что это только обёртка. Не в этап 3.

### Смена схемы и копии

- В каждом файле `"schema": N`. Переходы — функции Go только вперёд, каждый с фикстурой старой схемы в тестах.
- Перед переходом файлы копируются в `backup/<N>-<дата>/`, хранятся три последние копии.
- Схема новее, чем знает демон (откат версии), — хранилище не открывается и не перезаписывается; окно говорит, что
  установлена более старая версия.
- **Сделано в срезе 4:** схема 1 первая, переходов ещё нет. Переход — функция из `migrations`
  (`nikostore/schema.go`) над полями верхнего уровня `store.json`. Копия перед ним — `backup/<N>-<ГГГГММДД-ччммсс,
  UTC>/store.json`; не удался переход — файл не тронут. Схема новее — `ErrNewerSchema`, окну `FAILED_PRECONDITION`.
  Путь проверен тестовым переходом 1→2 на фикстуре схемы 1 (`nikostore/testdata/store-schema1.json`); ту же
  фикстуру, пока схема 1 текущая, читает тест без перехода. `GetInfo.store_schema` — текущая схема тому, кого
  хранилище обслуживает; тому, кому оно откажет, — 0, как у демона без хранилища.

### Резервная копия и перенос с телефона (D-SET-19)

- Своя копия компьютера: `ExportBackup` даёт JSON (профили, группы, правила, настройки без секретов установки — тот
  же список, что `DataStore.installSecretKeys`), окно сохраняет файл. `ImportBackup` сначала показывает, что
  заменится.
- **Узлы raycontrol не переносятся ни с телефона, ни на телефон.** Android их в копию не кладёт
  (`RAYCONTROL_NODES` в `installSecretKeys`, `database/DataStore.kt:244-251`), ключ устройства привязан к
  устройству. Компьютер получает узел своим приглашением. Это стоит сказать в справке и на экране импорта.
- **Копия Android сейчас (версия 1)** — JSON, где каждая запись — base64 от `Parcel.marshall()`
  (`ui/BackupFragment.kt:138-168`):
  - профили и группы: `writeByteArray` от Kryo-байтов бина — разбирается легко (длина `int32` и байты);
  - правила: `@Parcelize` `RuleEntity` — строки Parcel в UTF-16, раскладка из сгенерированного кода;
  - настройки: свой `writeToParcel` у `KeyValuePair`.

  Android прямо пишет, что Parcel не для хранения: формат может меняться между версиями системы. В JVM-тестах
  `Parcel` — заглушка, фикстуры из Kotlin не получить, только с эмулятора.

  Предложение: копия версии 2 на Android — тот же JSON бинов, что в golden, правила и настройки обычным JSON.
  Компьютер читает только её, версию 1 не читает (решение 7).

## 5. Сборка конфига в Go и golden-тесты

### Шов в Kotlin

Без него Kotlin golden-входы не прогонит (раздел 1, п. 3). Предложение:

- `ConfigInput` — всё, что сборка читает: профиль и цепочка, группа, включённые правила и их профили, 46 настроек,
  решения raycontrol (узел профиля, параметры стука, скорости Brutal, Yggdrasil через узел, резолвер DNS-туннеля,
  устройства lan-v1), какие наборы правил скачаны, платформа;
- `ConfigInput.fromApp()` собирает его из `DataStore`, Room и `Ray*` — так же, как сейчас;
- `buildConfig(input)` — чистая функция. Заметки (`notes`) и предупреждения (сейчас `Toast`) — в результат, их
  применяет вызывающий;
- сборщики исходящих и `applyNikoParams` получают нужные настройки параметром.

Правка механическая, но это 1759 строк, которые меняются чаще всего: 77 коммитов за месяц до 2026-11-17. Делать
короткими коммитами без изменения логики и проверять e2e на эмуляторе (`ci.yml`, узел raycontrol на стенде). Та же
`ConfigInput` — спецификация входов сборщика Go.

### Фикстуры

```
shared/golden/
  links/<имя>.txt           ссылка, sn:// или текст из нескольких строк
  links/<имя>.json          бины после initializeDefaultValues, имена полей Kotlin (Gson)
  subscriptions/<имя>.*     тело и заголовки ответа → бины и userinfo
  raycontrol/<имя>.*        payload подписки узла → профили (RaySubscription.parse), векторы RCINV2
  outbounds/<имя>.json      бин → исходящий ядра
  config/<случай>/input.json     ConfigInput
  config/<случай>/android.json   конфиг от Kotlin
  config/<случай>/windows.json   конфиг компьютера: только Go, проверяется ревью
  DIVERGENCES               известные расхождения: случай и причина
```

- Ожидаемое пишет Kotlin: `app/src/test/java/io/niko/golden/*` с флагом обновления переписывает файлы, без флага
  сверяет. Go читает те же файлы (`libcore/nikofmt/golden_test.go` и др.) и сверяет свой вывод.
- Входы для `links/` и `raycontrol/` — то, что реально раздают узлы: `internal/sub/conformance_test.go` raycontrol
  перечисляет все виды ссылок, а `app/src/test/resources/raycontrol/subscription-v0.11.1.json` уже есть. Векторы
  RCINV2 — из `raycontrol/Rcinv2Test.kt` (они же у raycontrol `TestInvite2Vectors`).
- Kryo в JVM работает (`fmt/LinkParsersTest.kt` пишет и читает бины), поэтому фикстуры `sn://` Kotlin делает сам.

### Как сравнивать

- Бины: JSON по смыслу (числа как числа, порядок ключей не важен).
- Конфиги: обе стороны прогоняются через парсер опций закреплённого ядра (`option.Options.UnmarshalJSONContext` с
  реестрами Niko, как `libcore/config_check_test.go`), затем снова в JSON, и сравниваются они. Так не мешают
  порядок полей и явно записанные значения по умолчанию (Kotlin пишет `false` и `0`, структуры Go их опускают), но
  ловится всё, на что ядро действительно реагирует. Заодно каждый ожидаемый конфиг — рабочий конфиг этого ядра.
- Отличия платформ: Kotlin делает только Android (TUN через `VpnService`, правила по пакетам). Сравнение
  Go = Kotlin — для `platform: android`. Что только у компьютера (TUN с `auto_route` и `strict_route`, режим прокси,
  правила по `process_path`), проверяют снимки `windows.json`, одобренные на ревью.

### CI

- Новая задача `golden` в одном workflow: `./gradlew app:testOssReleaseUnitTest --tests 'io.niko.golden.*'` и
  `go test` пакетов Go с фикстурами. Запуск по путям: `shared/golden/**`, `fmt/**`, `raycontrol/**`, `group/**`,
  `database/**`, `libcore/**`, закреплённое ядро.
- На проверку Go в `build.yml` полагаться нельзя: тесты `libcore` там идут только при промахе кэша, а ключ кэша
  строится из `buildScript`, `libcore` и `SingBoxOptions.java` (`.github/workflows/build.yml:35-40`). Правка Kotlin с
  новыми фикстурами Go там не перепроверит.
- `desktop.yml` прогоняет те же тесты Go на Windows.
- Правка Kotlin, меняющая вывод, обновляет фикстуры в том же PR. Go тогда падает, пока его не догонят, или случай
  явно попадает в `DIVERGENCES` с причиной.

### Один сборщик в итоге

Два сборщика навсегда — это каждая правка конфига дважды. При 77 коммитах в месяц в `ConfigBuilder.kt` golden будет
краснеть постоянно. Варианты:

- **А. Два сборщика и golden навсегда.** Android не трогаем. Цена — двойная работа без конца и расхождения, которые
  golden ловит только на заведённых случаях.
- **Б. Go — единственный сборщик, Android зовёт его через `libcore`** (`Libcore.buildConfig(inputJSON)`). Kotlin
  собирает `ConfigInput` и применяет заметки.
  - За: одна логика; типы и проверка ядра прямо при сборке; не нужны `SingBoxOptions.java` (5632 строки) и
    `options_drift_test.go`; телефон и компьютер ведут себя одинаково.
  - Против: риск для главного продукта; Android-части (адреса `VpnService`, пакеты приложений, slipstream, ключи
    Yggdrasil) надо выразить данными; решения raycontrol до этапа 4 живут в Kotlin и пересекают границу JSON при
    каждой сборке (сборка редкая, это дёшево); правка сборщика требует пересборки `libcore` — цикл разработки
    Android медленнее; ошибки через gomobile беднее.
  - Как снизить риск: «теневой» режим. Android собирает Kotlin'ом, в отладочной сборке и в e2e собирает ещё и Go,
    сравнивает тем же способом, что golden, и пишет расхождения в журнал. Потом флаг переключает на Go. Потом
    сборщик Kotlin удаляется, фикстуры остаются регрессионными тестами Go.
- **В. Один сборщик на Kotlin** — на компьютере нет JVM, и Kotlin/Native этот код не возьмёт. Отпадает.

Решение 2: Б через теневой режим, переключение, только если Go не хуже. Шов из этого раздела нужен в обоих вариантах.

## 6. Упаковка: `nikod`

### Что вынести из `package main` в форке

- Во всех файлах `experimental/boxdd` поменять `package main` на `package boxdd` (по строке на файл). `main()` — в
  новый `cmd/boxdd/main.go` в корне форка, рядом с `cmd/sing-box`, чтобы `boxdd` апстрима собирался как раньше.
  `go_package` в `desktop_service.proto` — на `;boxdd`, перегенерировать `*.pb.go`. **Сделано в форке `1b576e4`.**
- Дифф при слиянии с апстримом: конфликты почти только в строке `package` у новых файлов апстрима, и они видны
  сразу — сборка падает. Перенос файлов в другой каталог дал бы больше конфликтов: так не делаем.
- `desktop.yml` собирает `./cmd/boxdd` форка и `libcore/cmd/nikod`; `tool/dev.dart` запускает тот демон, путь к
  которому ему дали (`--daemon`).

### Какие крючки нужны (около 150–250 новых строк в форке)

- `boxdd.Main(Options)`: корневая команда cobra (`run`, `service`, `worker`, `version`) с параметрами:
  - имена: служба, отображаемое имя, файлы демона и окна, канал, каталог по умолчанию, префикс действий polkit;
  - дополнительные службы gRPC.
- Интерфейс дополнительной службы: зарегистрироваться на `grpc.Server`; сказать политику своих методов
  (`caller` — любой проверенный пользователь, свои данные; `owner` — только владелец); дать конфиг при
  восстановлении и `ReloadService`; закрыться.
- Host для службы:
  - личность вызвавшего из контекста (сейчас `peerIdentityFromContext`);
  - каталог пользователя (эти два нужны уже хранилищу: без них оно работает только в режиме разработки, раздел 3,
    «Кто вызвал»);
  - «запустить конфиг от имени вызвавшего» с логикой владельца из `StartService`;
  - `StartedService`;
  - монитор интерфейсов для `Control`.
- `(*daemon.Instance).Context()` — для `RouteTest`, `ExitAddress`, `DohCheck`, `NaiveDatagramState`.
- Крючок в журнал ядра для скрытия секретов (BG-10).
- `--listen` — только `127.0.0.1:<порт>`, проверка в коде (обещано в `docs/design/desktop.md:149`); сейчас демон
  открывает любой адрес с предупреждением (`server.go:102-108`).

`libcore/cmd/nikod/main.go` — это `boxdd.Main` с именами Niko и `nikoservice`. Один файл — и демон, и worker, как
требует `boxdd`.

### Имена

Сменить вторым PR после выноса в пакет, до первой публичной сборки: служба `niko-daemon` и отображаемое имя «Niko»,
канал `\\.\pipe\ProtectedPrefix\Administrators\niko`, каталог `C:\ProgramData\Niko`, файлы `niko.exe` (окно) и
`resources\daemon\niko-daemon.exe`, действия polkit Niko. Так Niko и «sing-box for desktop» уживаются на одной
машине. Встроенное обновление (`update_windows.go`, NSIS с именем продукта `sing-box`) — этап 5, но параметр туда
же.

### Служба Windows и права

Так `boxdd` делает уже сейчас:

- служба — LocalSystem, автозапуск, зависит от `Tcpip`, перезапуск через 5 с три раза, SID службы unrestricted
  (`cmd_service_windows.go:157-200`);
- `service install` требует администратора: его вызывает установщик (этап 5) или окно через UAC (D-STATE-SVC);
- на каталог установки ставится DACL: SYSTEM и администраторы — полный доступ, прошедшие проверку — чтение и
  запуск (`security_windows.go`);
- канал под `ProtectedPrefix\Administrators`: чужой процесс без прав не займёт имя раньше демона;
- окно и worker работают без повышения. Worker проверяет, что его родитель — установленное окно, и пересылает
  окну проверенное соединение с демоном (`cmd_worker_windows.go`).

Что из этого следует для `NikoService`:

- всё работает как SYSTEM: сеть без прокси пользователя, файлы — только в каталоге пользователя демона, разбор
  чужих подписок — в процессе с наибольшими правами (Go без небезопасной памяти, но пределы размера обязательны);
- служба работает до входа пользователя: обновление подписок по расписанию идёт без окна; ключи DPAPI в контексте
  SYSTEM доступны и при загрузке (у ключа пользователя так не вышло бы);
- `HandleSessionChange` в демоне уже есть: при выходе владельца из системы фоновую работу можно приостановить.

### Транспорт окна в релизе: (а) или (в)

Выбор назначен на этап 3 (`docs/design/desktop.md:163-187`). Что известно:

- (а) свой транспорт для `grpc` в Dart: у пакета есть `ClientTransportConnectorChannel` и абстрактный
  `ClientTransportConnector`, то есть свой поток вместо сокета. Канал Windows придётся открывать через FFI
  (`CreateFile`, перекрывающийся ввод-вывод в отдельном изоляте). Работы больше, но проверка клиента в форке не
  меняется.
- (в) AF_UNIX между окном и worker'ом: в Dart работает и на Windows (проверено на этапе 2b), `grpc` умеет
  `InternetAddressType.unix` сам. Обязательные условия, без которых (в) брать нельзя:
  - файл сокета лежит в каталоге пользователя с DACL только на него (не в `ProgramData`, не в общем `Temp`); путь
    задаёт окно и передаёт его worker'у при запуске;
  - окно проверяет, что на другом конце его собственный дочерний worker: по `SIO_AF_UNIX_GETPEERPID` на стороне
    клиента (PID совпадает с PID процесса, который окно запустило) и по владельцу файла сокета. Иначе процесс другого
    пользователя, заранее создавший сокет там, где писать может каждый, получит от окна приглашение с PIN или
    подписки;
  - worker со своей стороны берёт PID клиента через `SIO_AF_UNIX_GETPEERPID`. Известная странность:
  вызов возвращает верный PID, но сообщает длину ответа 0 (WSL #4676, Windows 10 1903–2004, исправления не видно;
  GLib помечает это «Windows bug»). Код должен брать PID и не верить длине. Подмены PID worker не боится: он держит
  открытый дескриптор родителя, и PID не переиспользуется, пока тот жив. Меняется только звено «окно — worker»
  внутри одного пользователя; «worker — демон» остаётся каналом с проверкой подписи.

Предложение: короткая проба (в) в CI на `windows-latest` (слушатель Go на AF_UNIX, клиент Dart, сверка PID через
`SIO_AF_UNIX_GETPEERPID` с обеих сторон). Прошла — (в) с отдельным ревью безопасности и условиями выше; нет — (а).
(б) остаётся запасным, как и сейчас. **Это отходит от `docs/design/desktop.md`**, где предпочтительным назван (а):
(в) проще (без FFI и своего транспорта), но проверка клиента переезжает в правку форка, а сервер окну надо
проверять самому. **Владелец выбрал (в) после пробы, (а) — если проба не пройдёт** (решение 8); README поправлен.

### CI

- `desktop.yml` собирает `nikod.exe` (amd64, arm64) и `nikod` для Linux рядом с `boxdd` (пока он нужен).
- Тест окна с настоящим демоном (`test/real_daemon_test.dart`) идёт против `nikod`: переменная `NIKO_DAEMON`
  вместо `NIKO_BOXDD`.
- `test` на Windows добавляет новые пакеты Go.

## 7. Нарезка

Каждый срез сливается сам и сам проверяется в CI.

| № | Срез | Что даёт | Чем проверяет CI |
|---|---|---|---|
| 1 | `nikod`: `boxdd` как пакет, пустой `NikoService` с `GetInfo` и `RouteTest` — **сделано 2026-11-17** (форк: `boxdd` пакетом с политиками методов, `Instance.Hold`; `RouteTest` вне блокировки демона, ABORTED после остановки ядра; тест окна против `nikod` на Windows и Linux) | основа всего; D-CHECK-03…05 на стороне демона | тест окна с настоящим `nikod` на Windows и Linux; тест политики авторизатора в форке |
| 2 | Каркас golden и ссылки узлов: naive, Hysteria2, VLESS REALITY и XHTTP с параметрами Niko (`nikofmt`) — **сделано 2026-11-19** (22 ссылки в `shared/golden/links`, `null` — ссылка, которую приложение отбрасывает; Go разбирает URL как OkHttp и `java.net.URI`, с причудами парсеров; хост не ASCII Go отвергает, Kotlin переводит в punycode) | первые профили в Go | задача `golden`: Kotlin пишет и сверяет, Go сверяет |
| 3 | Остальные ссылки и `sn://` (Kryo) — **сделано 2026-11-21** (ещё 97 ссылок: ss, socks, http(s), VMess во всех формах, Trojan, Trojan-Go, mieru, Hysteria 1, TUIC, AnyTLS, `sn://` всех типов, кроме `neko`; base64 — как у Android, JSON v2rayN — как у Gson; целые Kryo 5 — little-endian; ссылки подписок Go отличает от отброшенных) | весь D-ADD-03 для ссылок | `golden` |
| 4 | `nikostore`, CRUD, `Watch`, `Recognize`/`Import` для ссылок — **сделано 2026-11-21** (`store.json`: группы и профили с бинами, атомарная запись, один писатель; секретные поля под DPAPI на Windows за интерфейсом `Sealer`, на Linux — права 0700/0600; схема 1, переходы вперёд с копией в `backup/`, путь проверен тестовым переходом; `Watch` с номером на пользователя и `reset` после пропуска; `Import` задачей, итог и в `Watch`; ссылка подписки распознаётся, импорт — `UNIMPLEMENTED` до среза 9; `nikofmt.ParseText` как `parseProxies`. Осталось: хранилище работает только в режиме разработки, пока форк не назовёт службе вызвавшего (раздел 3); правил, `state.json` и обёртки секретов на Linux нет) | профили живут в службе | тесты Go: хранилище, переходы схемы, gRPC через `bufconn`; тест окна: импорт ссылки виден через `Watch` |
| 5 | Окно: список профилей, экран добавления (D-ADD-02…04, -07, -08), выбор (D-MAIN-07, -10, -11), «поделиться» (D-PROFILE-07). **5а сделано 2026-11-21**: раздел «Узлы» (группы, профили группы, выбор нажатием), `Select`/`GetSelected` и `state.json` в службе, SELECTED в `Watch`, API 3. **5б сделано 2026-11-21**: экран добавления по ссылке (`Recognize` до записи, импорт в показанную группу, подписка — «придёт позже»), удаление с «Вернуть» на прежнее место и с прежним выбором. **5в сделано 2026-11-21**: «поделиться» — ссылка профиля как её пишет приложение (`nikofmt.ShareLink`: `toUri` каждого типа на копии OkHttp `HttpUrl.Builder`, для остальных `sn://` с Kryo; эталоны `<name>.share` из Kotlin, все 100 совпадают, `sn://` — по байтам Kryo), `ShareProfile` в службе (API 4), в окне — «Скопировать ссылку» в строке профиля | первое, что видит пользователь | тесты виджетов и снимки; тест с настоящим `nikod` |
| 6 | Шов `ConfigInput` в Kotlin, без изменения логики — **сделано 2026-11-21** (`fmt/ConfigInput.kt`: `buildConfig(input)` читает только вход — настройки сборки данными, `ConfigSettings` (секреты отдельно, в журнал не попадают), хранилище `ConfigStore`, решения raycontrol `ConfigRay` и то, что знает только телефон, `ConfigDevice`: порты, приложения, Private DNS, плагины, часы, предупреждения вместо `Toast`; `ConfigInput.fromApp` собирает его как раньше; сборщики исходящих получают «разрешать небезопасные» параметром. Осталось: файлы наборов правил — по каталогу `RuleSetFiles`, заметки raycontrol — вызовами `ConfigRay`, а не в результат) | Kotlin может прогнать golden-конфиги | unit-тесты Android; e2e на эмуляторе с узлом |
| 7 | Исходящие и конфиг одного профиля без адаптивного raycontrol (`nikoconf`), `Connect`. **7а сделано 2026-11-21**: исходящие (`nikoconf.Outbound`: сборщики каждого типа как в приложении, с их причудами; эталоны `shared/golden/outbounds/<name>.json` пишет Kotlin — `buildSingBoxOutbound` бина каждой ссылки, кроме профилей с плагином; все 97 совпадают по смыслу). **7б сделано 2026-11-21**: конфиг одного профиля без raycontrol (`nikoconf.Build(Input)`: вход как `ConfigInput`, устройство интерфейсом; эталоны `shared/golden/config/<случай>`: `input.json` пишется руками, `android.json`, `warnings.json` и `error.txt` пишет Kotlin; все 29 совпадают, порядок `HashSet` JVM повторён). Вне сборщика Go пока: профили с плагином, slipstream, цепочки (`ChainBean`), ShadowTLS и всё, что требует узла raycontrol. Осталось: `Connect` | подключение к сохранённому профилю | `golden` (исходящие, конфиги, круг через ядро); тест с `nikod`: профиль `ss://` к `sing-box.exe` из того же CI, запрос через прокси |
| 8 | Части только компьютера: TUN, режим прокси (D-NAV-02), программы (D-RULES-02, -06), локальная сеть (D-SET-16), DNS (D-SET-18) | VPN на компьютере | снимки `windows.json`; тест режима прокси с `SetSystemProxyEnabled` |
| 9 | Обычные подписки (`nikosub`), D-SUB-01…04, обновление по времени | подписки провайдеров | `golden` подписок; тест Go с локальным HTTP-сервером |
| 10 | Правила, пресет «Россия», наборы правил (D-RULES-01, -03, -05, D-SET-08, BG-09, D-CHECK-06) | маршрутизация как на телефоне | `golden` конфигов с правилами; загрузка набора с локального сервера, `CheckRuleSet` |
| 11 | Узел по приглашению, статическая часть: RCINV1, RCINV2, rcdev1, регистрация, подписка, профили узла, DPAPI, стук, обход туннеля (D-ADD-06, D-NODE-01…07, D-MAIN-13) | главный сценарий Niko | `golden` raycontrol; interop против `rayd` по образцу `raycontrol-interop.yml`: `nikod` регистрируется по приглашению и подключается |
| 12 | Проверка профилей без подключения (D-MAIN-08, -12) | пинги в списке | тест с `nikod`: пинг локального сервера |
| 13 | Настройки и резервная копия версии 2 (D-SET-05, -15, -19, -32), сначала на Android | перенос с телефона | `golden` копий: Kotlin пишет, Go читает |
| 14 | Транспорт релиза: проба (в), затем реализация в форке и окне; имена Niko | окно в релизе видит демон | проба на `windows-latest`; ревью безопасности |
| 15 | Android на сборщике Go: теневой режим, затем переключение, только если Go не хуже | один сборщик | e2e: Kotlin и Go дают одинаковый конфиг; потом e2e на Go |
| 16 | Режим «Прокси» без службы: ядро в worker'е без прав, системный прокси, без TUN (D-STATE-SVC) | Niko без прав администратора, переносная версия | тест окна с worker'ом без службы на `windows-latest`: профиль `ss://` к `sing-box.exe`, запрос через системный прокси |
| 17 | Плагины, которые ядро не заменило, и API плагинов; исполняемый файл плагина запускает окно или worker от имени пользователя, ядро в службе ходит к нему на локальный порт | профили-плагины NekoBox | тест: служба не запускает файлов из каталога пользователя; плагин-заглушка от имени пользователя, запрос через него |
| 18 | Хранилище под паролем (по желанию, предложение при первом запуске) | секреты заперты, пока пароль не введён | тесты Go: запертое хранилище не отдаёт секретов, неверный пароль, смена пароля; тест окна: ввод пароля после перезапуска службы |

Срезы 2–3 и 6 не зависят от 1, их можно вести параллельно. Срез 15 — теневой режим, переключение, только если Go не
хуже (решение 2). Срезы 16 и 17 — после 8, срез 18 — после 11.

**Готово для этапа 3:** golden ссылок, подписок, исходящих и конфигов (`platform: android`) зелёные на обеих
сторонах; профиль из ссылки и из подписки подключается в `nikod` в CI на Windows; хранилище переживает перезапуск
службы и смену схемы; узел по приглашению подключается против `rayd` (решение 1).

### Срез 1 подробно

**Цель:** один бинарник `nikod` = `boxdd` + `NikoService`. Поведение окна не меняется, кроме того, что оно говорит с
`nikod`.

**Форк** (отдельный PR, затем перенос закрепления):

1. `package main` → `package boxdd` в `experimental/boxdd/*.go`; `cmd/boxdd/main.go`; `go_package`; перегенерировать
   `desktop_service.pb.go`.
2. `Options` и `Main` с именами по умолчанию как сейчас. `boxdd` апстрима ведёт себя как раньше.
3. Дополнительные службы: регистрация, политика методов в `daemonAuthorizer.Authorize` и `InvokeUnary`
   (`server.go:347-404`), статус в health.
4. `(*daemon.Instance).Context()`.
5. Тесты: политика авторизатора (метод чужой службы с политикой `caller` проходит у проверенного клиента и не
   проходит без личности; без политики — отказ, как сейчас); существующие тесты `boxdd` и `daemon`;
   `GOOS=windows go build` и `GOOS=linux go build` обоих `main`.

**Этот репозиторий:**

1. `shared/proto/niko/v1/niko_service.proto`: `GetInfo` и `RouteTest`. Скрипт генерации Go и Dart с `--check`
   (версии `protoc-gen-go`, `protoc-gen-go-grpc` и `protoc_plugin` закреплены, как сейчас у Dart).
2. `libcore/routetest`: тело `routeTest` из корня, на `context.Context`. Корневой `RouteTest` для Android остаётся
   тонкой обёрткой, его тест переезжает вместе с телом.
3. `libcore/nikoservice`: сервер с `GetInfo` (версия Niko, ядро, схема хранилища — пока 0) и `RouteTest`
   (`FailedPrecondition`, если ядро не запущено).
4. `libcore/cmd/nikod/main.go`. Корневой пакет `libcore` (он для gomobile) `nikod` не импортирует, только
   подпакеты.
5. Окно: `tool/gen_proto.sh` берёт ещё `shared/proto`; `GrpcDaemon` получает `NikoServiceClient`; экранов нет.
6. `test/real_daemon_test.dart`: после запуска конфига с правилом `domain_suffix: example.com → direct` вызвать
   `RouteTest("example.com:443")` и увидеть это правило и `direct`. Без сети: `resolve` не включён.
7. `desktop.yml`: сборка `nikod` вместо `boxdd` для теста окна; `go test ./nikoservice ./routetest` на Windows.

**Готово, когда:** тест окна проходит против `nikod` на `windows-latest` и `ubuntu-latest`; `boxdd` из форка
собирается и проходит свои тесты; релизная сборка окна не изменилась (проверка `desktop.yml` на строки режима
разработки проходит).

**Не входит:** имена Niko, транспорт релиза, хранилище.

**Риск:** форк меняют несколько сессий сразу (за время этой проверки в него пришёл `489ccbd`). PR форка должен быть
коротким и сливаться первым, закрепление в этом репозитории переносится отдельным коммитом.

## 8. Риски и вопросы владельцу

### Риски

- **Шов в Kotlin задевает главный продукт.** Смягчение: коммиты без изменения логики, e2e на каждый, теневой режим
  до переключения.
- **Темп правок сборщика.** 77 коммитов в месяц в `ConfigBuilder.kt`: пока сборщиков два, golden будет падать
  часто. Смягчение: короткий срок двойной поддержки (решение 2).
- **Расхождение с апстримом `boxdd`.** Смягчение: правка только строк `package` и крючков, файлы не переносить.
- **Разбор чужих подписок в процессе SYSTEM.** Смягчение: пределы размера и YAML, только `http(s)`, никаких путей
  от окна.
- **Запросы службы при включённом TUN** уйдут в туннель (регистрация, стук, подписки). Смягчение: `Control` с
  привязкой к физическому интерфейсу, проверка в тесте с TUN на Windows (срез 8 или 11).
- **Несколько ядер в одном процессе** для проверки профилей (D-MAIN-08): общее состояние — ключи стука, журнал.
  На Android так уже работает, но в `boxdd` не проверялось.
- **`SIO_AF_UNIX_GETPEERPID`** — поведение известно по чужим отчётам, описания у Microsoft найти не удалось. Отсюда проба
  до решения.
- **Граница DPAPI**: защищает от копии на другую машину, не от администратора этой машины.

### Решения владельца (2026-11-17)

Вопросы этого плана и ответы на них. Где ответ — «по рекомендации», это сказано.

1. **raycontrol в этапе 3 — да.** Приглашение, регистрация, первая загрузка подписки и статические профили узла —
   в этапе 3 (срез 11). Сторож, канал, Brutal, отчёты, резолверы и lan-v1 — этап 4.
2. **Один сборщик — через теневой режим.** Go-сборщик на Android работает рядом с Kotlin, конфиги сравниваются,
   пользователь этого не видит. Переключение — только если Go не хуже: golden и e2e совпадают, в журнале теневого
   режима нет расхождений. Иначе остаётся Kotlin (срез 15).
3. **Правка форка — да:** `package boxdd` и крючки, затем имена Niko, двумя шагами.
4. **Хранилище — JSON-файлы** (по рекомендации); bbolt — если вырастет за 10 МБ. **Принцип владельца для всего
   этапа:** различать, что клиент делает в общем режиме, а что — с подпиской raycontrol. Главная цель — raycontrol,
   но сторонние конфиги, ссылки и подписки поддерживаются полностью. Возможности узлов raycontrol, которым нужна их
   серверная часть, для сторонних профилей опускаются, только когда их технически не сделать на ядре и клиенте. На
   формат хранилища это не влияет: у профиля raycontrol просто больше полей.
5. **Хранилище на пользователя Windows** (каталог по SID): один пользователь — одно хранилище.
6. **Секреты — DPAPI для всех секретных полей**, а не только для ключей raycontrol; **по желанию — хранилище под
   паролем**, его предлагают при первом запуске (раздел 4, «Секреты»; срез 18).
7. **Перенос с телефона — копия версии 2 в JSON**, сначала на Android (по рекомендации, срез 13). Копию версии 1
   (Parcel) компьютер не читает.
8. **Транспорт — (в) AF_UNIX после пробы в CI**, с условиями раздела 6 и отдельным ревью безопасности; не пройдёт —
   (а), свой транспорт по именованному каналу через FFI (по рекомендации). (б) — запасной.
9. **Режим «Прокси» без службы — оставить** (рекомендация была убрать). Он нужен на компьютерах без прав
   администратора и для переносной версии. Новая работа в форке: ядро в worker'е без прав, системный прокси, без TUN
   (срез 16).
10. **Плагины — поддерживать**, кроме тех, что уже заменены поддержкой в ядре (naive, Hysteria2, mieru, trojan-go —
    в ядре); **API плагинов сохранить**. Условие безопасности: исполняемый файл плагина запускает не служба (она
    работает как SYSTEM, и запуск файла из каталога пользователя стал бы способом поднять права), а окно или worker
    от имени пользователя; ядро в службе ходит к плагину на локальный порт (срез 17).
11. **proto — в `shared/proto`** (по рекомендации); `docs/design/desktop.md` поправлен.
12. **Матрица — да** (по рекомендации): 38 ID из описи и колонка «Окно» в `desktop-requirements.md`, до среза 5.

## Источники

- CryptProtectData (DPAPI), Microsoft:
  https://learn.microsoft.com/en-us/windows/win32/api/dpapi/nf-dpapi-cryptprotectdata
- `golang.org/x/sys/windows` (CryptProtectData, CryptUnprotectData): https://pkg.go.dev/golang.org/x/sys/windows
- DPAPI_SYSTEM и ключи SYSTEM: https://docs.specterops.io/ghostpack-docs/SharpDPAPI-mdx/commands/machinemasterkeys,
  https://specterops.io/blog/2026/03/04/offensive-dpapi-with-nemesis/
- systemd-creds: https://man7.org/linux/man-pages/man1/systemd-creds.1.html, https://systemd.io/CREDENTIALS/
- modernc.org/sqlite: https://pkg.go.dev/modernc.org/sqlite
- Parcel не для хранения: https://developer.android.com/reference/android/os/Parcel,
  https://medium.com/@yukuku/androids-parcel-serialized-format-was-changed-in-android-13-a7017c25a246
- SIO_AF_UNIX_GETPEERPID: https://github.com/microsoft/WSL/issues/4676,
  https://gitlab.gnome.org/GNOME/glib/-/merge_requests/2445
- ProtectedPrefix для каналов: https://tyranidslair.blogspot.com.au/2017/
- grpc для Dart: https://pub.dev/documentation/grpc/latest/,
  https://pub.dev/documentation/grpc/4.2.0/grpc/ClientTransportConnectorChannel-class.html
- `boxdd` в апстриме sing-box: https://pkg.go.dev/github.com/sagernet/sing-box/experimental/boxdd,
  https://pkg.go.dev/github.com/sagernet/sing-box@v1.15.0-alpha.13
- sing-box for desktop: https://sing-box.sagernet.org/clients/desktop/

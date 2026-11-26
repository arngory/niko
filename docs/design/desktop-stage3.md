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

- Flutter, около 4000 строк Dart в `desktop/lib`. Умеет то, что описано в
  [README окна](https://github.com/arngory/niko-desktop/blob/main/README.ru.md): главная с готовым JSON, журнал, отчёты, проверка NAT, соединения. Все вызовы — к трём службам форка:
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
  `SubscriptionHeaders` → `nikosub.ParseUserInfo`, `MoveOf` и др.; загрузка → `nikosub.Client.Fetch`; `doUpdate` →
  `nikosub.Run` (`Prepare`, `Merge`, `ApplyAnswer`);
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
Dart (`lib/gen/niko` окна, arngory/niko-desktop) и позже Kotlin. Сгенерированное лежит в репозитории, CI проверяет
его скриптом `--check`: Go — `shared/tool/gen_proto_go.sh` здесь, Dart — `tool/gen_proto.sh` окна против коммита Niko,
который окно пинит.

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
  умолчанию: это резолверы DNS-туннелей, настройка узлов raycontrol (срез 11). За один импорт — не больше 10 000 профилей; больше
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

**Как сделано в срезе 9 (2026-11-22).** Подписки, `API = 9`:

- `Group.subscription` — подписка группы: настройки пользователя (адрес, дедупликация, принудительное разрешение,
  только при подключении, User-Agent, автообновление и интервал) и то, что сказали обновления (время последнего,
  трафик и срок из `subscription-userinfo`, объявление), `updating` — обновление идёт сейчас. `SaveGroup` с
  `SUBSCRIPTION` создаёт группу подписки (имя по умолчанию — «Subscription #…» приложения, первое обновление меняет его
  на имя провайдера) или меняет имя и настройки; что записали обновления, остаётся; новый адрес забывает запасной
  адрес и хост `x-hwid` старого. Сохранение не обновляет.
- `Refresh(group_id)` — задача, как `Import`: шаги и итог, итог ещё и в `Watch` (`TASK`), а группа — `GROUPS` в начале
  и в конце; `group_id` 0 — все подписки по очереди. Коды итога: `NOT_FOUND`, `FAILED_PRECONDITION` (не подписка; адрес
  запечатан на другой машине), `ABORTED` (группа уже обновляется: одно обновление группы за раз, как
  `GroupUpdater.updating`), `UNAVAILABLE` (провайдер не ответил), `INVALID_ARGUMENT` (в ответе нет профилей, в нём
  чужая ссылка подписки), `PERMISSION_DENIED` (панель отказала устройству). `TaskProgress.group_id` — группа задачи.
- `Import` ссылки подписки (http(s); `clash://install-config`, `sing-box://`, `hiddify://`, `v2rayng://` — как
  `parseSubscriptionDeepLink`) создаёт группу и обновляет её, как `MainActivity.importSubscription`; не обновилась —
  группа остаётся. `Recognize` отвечает `SUBSCRIPTION` и для ссылки другого клиента целиком. Приглашение и пакет
  устройства raycontrol (`rcinv1`, `RCINV2`, `RCINV3`, `rcdev1`) — `ImportPreview.NODE`, импорт — `UNIMPLEMENTED` до
  среза 11; группа целиком в `sn://subscription?<Kryo>` — тоже `UNIMPLEMENTED`.
- Обновление по времени — служба раз в минуту смотрит подписки открытых хранилищ (хранилища на диске открываются при
  старте службы, обновление идёт без окна): автообновление включено и с последнего обновления (или неудачной
  попытки) прошёл интервал, не меньше 15 минут (наименьший период WorkManager); «только при подключении» — только пока
  ядро запущено.
- Запросы службы идут напрямую, с User-Agent приложения (`NekoBox/Android/<версия> (Niko; Prefer ClashMeta Format)`,
  по нему провайдеры отдают формат ClashMeta), пределами `libcore` (16 МиБ, 120 с, 30 с без байта, только ответ 200)
  и проверкой сертификата. Обход TUN для них — `Control` среза 11.

**Как сделано в срезе 10 (2026-11-23).** Правила и наборы правил, `API = 10`:

- `ListRules`, `SaveRule`, `DeleteRules`, `MoveRules` — правила хранилища (`RuleEntity` под именами приложения),
  `RULES` в `Watch`. `SaveRule` с `id` 0 добавляет в конец (порядок — наибольший и один, как `createRule`), с `id`
  меняет поля на месте; порядок меняет только `MoveRules` (нумерует заново с 1), приложения телефона (`packages`)
  окно не задаёт — у правила остаются свои, наружу только их число. Проверка — как редактор приложения: выход —
  прокси, обход, блок или профиль хранилища (или тот, что был, если профиль удалён: сборка о нём предупредит), сеть
  `tcp`/`udp`/пусто, свой JSON — объект, списки до 1 МиБ, UTF-8; иначе `INVALID_ARGUMENT`.
- `ApplyPreset(RUSSIA)` — `RoutePresets.addRussia`: два правила пресета заменяют прежние правила пресета (своё правило
  пользователя с одним из списков runetfreedom остаётся) и встают в конец; имена — на языке окна, по умолчанию
  английские имена приложения. Ответ — правила и их наборы; скачивает их окно вызовом `UpdateRuleSets`.
- `ListRuleSets` — наборы, которые называют правила (`srs:`): включено ли правило, скачан ли файл, размер, время,
  что в нём (домены, адреса), последняя ошибка с запуска демона, идёт ли загрузка; `RULE_SETS` в `Watch`.
- `UpdateRuleSets` — задача, как `Refresh`: названные (только те, что есть в правилах: служба не ходит по адресу,
  которого нет в хранилище) или наборы включённых правил; каждый, если его нет или он старше интервала без 10 минут,
  или любой с `force`. Одно обновление за раз на пользователя (`ABORTED`). Загрузка — `libcore/ruleset`, как
  `RuleSetFiles.download`: только https (и переадресация только на https), ответ 200, 32 МиБ (объявленный больший
  размер — отказ до первого байта), 120 с всего и 30 с без байта, начало файла по формату, затем `CheckRuleSet`; файл
  заменяется переименованием только после проверки, `.kinds` пишется после файла. Коды итога: `UNAVAILABLE` (сервер не
  ответил или ответил не 200), `INVALID_ARGUMENT` (не https, не набор, ядро его не читает). Ни ошибка, ни строка хода
  не называют адрес набора с запросом (в нём может быть токен).
- `Connect` собирает с правилами, их профилями и скачанными наборами, затем в фоне скачивает недостающие и
  устаревшие наборы включённых правил; пока ядро работает, служба раз в минуту делает то же по интервалу
  (`ruleSetUpdateHours`), неудачный набор — снова через 15 минут. Ядро берёт набор, которого не было при запуске, при
  следующем подключении: перезапуска, как у приложения, нет.
- Настройки компьютера (`Settings`): `enable_dns_routing`, `rule_set_update_hours` (1…720), `dpi_direct`,
  `dpi_direct_preset`, `dpi_direct_custom` (строка ByeDPI, которую читает сборка) — без них «Обход DPI» у правил на
  компьютере ничего не делал бы.

**Как сделано в срезе 11 (2026-11-23).** Узлы raycontrol, статическая часть, `API = 11`:

- Разбор — `libcore/nikoray`, как `raycontrol/Rcdev1.kt`, `RayNames.kt`, `RaySubscription.parse` и `RayRows.kt`:
  `rcinv1`, `RCINV2`, `RCINV3` (`ParseInvite`), `rcdev1` (`ParseDevice`, с переносом строк терминала), узел (`Node`,
  JSON — имена `RayNode.toJson`), строки подписки (`Parse`: только схемы спецификации, без своего JSON и
  небезопасного TLS; DoH — удалённый DNS, его пины; имя и заметка с правилами приложения), `NextRefresh`. Ошибки —
  словами приложения и без ключей пакета. JSON узла читается строго (`encoding/json`): что принял бы мягкий разбор
  Gson (комментарии, имена без кавычек), здесь отклоняется.
- `Recognize` приглашения или пакета — `NODE` с `NodePreview`: хост, имя узла, нужен ли PIN, срок, заменит ли узел,
  который уже есть (и его прежний хост, если новый другой: узел переехал или ссылка чужая), как
  `RayImport.replaceQuestion`; что не читается — `INVALID_ARGUMENT`.
- `Import` приглашения — регистрация, как `RayApi.register`: срок по своим часам; PIN — в `ImportRequest.pin`
  (6 цифр, иначе `INVALID_ARGUMENT`); приглашение с PIN без него и неверный PIN дают итог с `pin` (`PinNeeded`: хост,
  остаток попыток, -1 — пока неизвестен, был ли неверным) и кодом `FAILED_PRECONDITION`, окно спрашивает и зовёт
  `Import` снова; `left` 0 — `PERMISSION_DENIED` («приглашение отозвано»). Клиентский ключ создаётся до первой попытки
  и хранится запечатанным по приглашению 30 минут от последней попытки (`pendingIdentity`), так что повтор после
  потерянного ответа получает то же устройство; забывается в той же записи, что сохраняет узел, и когда приглашение
  израсходовано зря (роль бота, пакет другого узла). Коды: `NOT_FOUND` (узел отказал: использовано, истекло,
  отозвано, часы), `FAILED_PRECONDITION` (приглашение интеграции, пакет бота или без подписки), `UNAVAILABLE`
  (не ответил, ответ потерян). `Import` пакета `rcdev1` хранит узел как есть. После — первая загрузка подписки;
  итог называет узел (`node_id`) и его группу.
- Подписка — `raysub.Fetch` (стук SPA, TLS с X25519MLKEM768 и пином, обе подписи), по имени узла с его адресами в
  запасе (names-v1). Строки — в группу узла (`ApplyNodeRows`); исчерпанный трафик и отказ по версии меняют только
  карточку. Следующая загрузка — к ротации или к трём четвертям срока, со сдвигом до 15 минут; неудача — через
  15 минут, удваиваясь до 6 часов, если узел не ответил, иначе через 6 часов; отказ узла (404, чужая подпись)
  останавливает расписание. `Refresh` с `node_id` (или с группой узла) — вручную, задачей; служба раз в минуту
  обновляет узлы, у которых срок настал. Одно обновление узла за раз (`ABORTED`).
- `ListNodes` — то, что показывает карточка (`Node`: имя, заметка, группа и число профилей, имя узла, подписка,
  когда обновлено, до когда действует и следующее обновление, отказ, неудачи подряд и последняя ошибка, трафик,
  минимальная версия, расхождение часов, идёт ли обновление, запечатан ли на другой машине), ключей нет ни в одном
  вызове. `DeleteNode` удаляет узел, его группу с профилями и ключ из ядра. `Group.node_id` и `Profile.node_id`
  называют узел; группа узла не принимает профилей извне (импорт, перенос) и не удаляется сама — только с узлом.
- Сборка — `nikoconf` со статической частью `ConfigRay` (`nikoconf/ray.go`): строка узла (по хосту или имени узла,
  или по ключу строки) получает стук SPA v2 с ключом устройства по ссылке, TLS только с X25519MLKEM768 (uTLS —
  Chrome, REALITY — `key_share: hybrid`), запасное соединение REALITY, keepalive Hysteria2, вращающийся UDP-порт
  службы, DoH узла удалённым DNS, пир Yggdrasil по адресу узла, dnsauth DNS-туннеля; SNI прелюдии, который узел сам
  обслуживает, отбрасывается. Чего нет в данных сети (скорости Brutal, публичные пиры, резолверы и зоны
  DNS-туннелей, LAN) — как у приложения без данных; ретрансляторы не перенесены. `Connect` берёт узлы хранилища и
  кладёт их ключи в ядро по ссылкам (`knock.SetDeviceKey`), ключи удалённых узлов убирает.
- Обход TUN: пока ядро работает, запросы службы к узлу (регистрация, подписка) идут с `Control` сетевого менеджера
  ядра (`AutoDetectInterfaceFunc`: привязка к интерфейсу, как у `auto_detect_interface`), имя узла ищется прямым DNS
  ядра (`dns-direct`, DNS системы мимо TUN), а не FakeIP. Стук профилей — исходящий ядра, его обходит само ядро. Без
  ядра — обычный маршрут и резолвер системы.
- Заголовок `X-Raycontrol-Client` — `niko/<версия> (<система>; <архитектура>)`; у сборки без версии (`dev`) его нет.

**Кто вызвал (срез 14).** Вызовы хранилища — `PolicyCaller`, и у каждого пользователя хранилище своё. `nikoservice` берёт личность вызвавшего через `Host.Caller(ctx)` (`UserID`: Windows SID, Linux uid или `"local"` в режиме разработки `--listen`), корень демона через `Host.WorkingDirectory()` (без зависимости от `os.Chdir`) и каталог пользователя через `Host.UserDirectory(userID)`. Непроверенный вызов без личности получает `UNAUTHENTICATED` и `GetInfo.store_schema = 0`; `API = 12`.

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
`state.json` и `rulesets/` придут со своими срезами (13, 11, 12, 10), правила в `store.json` — со срезом 10 (сделано:
схема 3, ниже). Схема 1:

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

**Сделано раньше среза 13** (2026-11-21, часть компьютера): `settings.json` — что пользователь поменял поверх
значений компьютера (`nikoconf.DesktopSettings`: значения приложения, но режим прокси), по именам приложения:
`{"schema": 1, "settings": {"mixedPort": 2081}, "sealed": "dpapi:…"}`. Секретное (пока пароль локального прокси) —
как секреты профилей: DPAPI на Windows, на Linux поле `"secrets"` файла 0600. Записанное остаётся выбором
пользователя, новое значение по умолчанию его не меняет; имена из новой сборки той же схемы сохраняются и не читаются.

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
  туда же. DPAPI проверяется на `windows-latest` (`desktop.yml`). Ссылки подписок обёрнуты так же со срезом 9
  (`link` и `fallbackLink` подписки — в `sealed` группы); ключи узлов — со
  срезом 11 (схема 4); секрет Clash API обернётся, когда его срез положит его в хранилище.
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
- **Сделано в срезе 10:** схема 3 — правила (`"rules"`, поля `RuleEntity` под именами приложения, и `"next_rule"`,
  `nikostore/rules.go`). Переход 2→3 добавляет пустой список; сама смена номера не даёт сборке схемы 2 открыть
  хранилище и при перезаписи выбросить правила. Тест — на фикстуре схемы 2, как её писала сборка среза 9
  (`nikostore/testdata/store-schema2.json`, с группой подписки), с копией в `backup/2-…`. Наборы правил — в
  `rulesets/` рядом с `store.json`: файл по SHA-256 адреса, как `RuleSetFiles.file`, и `.kinds` рядом.
- **Сделано в срезе 11:** схема 4 — узлы raycontrol и ключи регистраций в работе прямо в `store.json`, а не в
  отдельном `nodes.json`, как задумано выше: узел, его группа и её строки меняются одной записью (приложению для
  этого нужны ключи `stale:` между двумя базами). `"nodes"` — JSON `RayNode.toJson`, но ключ устройства, клиентские
  ключи, учётные данные строк (`links`) и DoH — в `"sealed"` рядом (DPAPI на Windows); `"pending_registrations"` —
  клиентский ключ по приглашению, запечатанный так же. У группы узла `"node"` — id узла. Переход 3→4 добавляет
  пустые списки; тест — на фикстуре схемы 3 (`nikostore/testdata/store-schema3.json`) с копией в `backup/3-…`.
- **Сделано в срезе 9:** схема 2 — у группы подписки (`"type": 1`) её подписка: `"subscription"` с полями
  `SubscriptionBean` под именами приложения и `"sealed"` с её адресами (`nikostore/subscription.go`). Переход 1→2
  настоящий: в схеме 1 групп подписок быть не могло (`SaveGroup` их отклонял), группа типа 1 без подписки становится
  обычной; сама смена номера не даёт сборке схемы 1 открыть хранилище и при перезаписи выбросить подписки. Тест —
  на той же фикстуре схемы 1, с копией в `backup/1-…`; тестовый переход прежнего теста идёт теперь поверх настоящих.

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
- **Сделано в срезе 11:** `raycontrol/invites/<имя>.txt` → `<имя>.json` (приглашение или пакет устройства, как их
  хранит приложение, или его слова отказа; 38 случаев: векторы RCINV2 и RCINV3 из спецификации, rcinv1 и rcdev1 с
  выдуманными ключами) и `raycontrol/payloads/<случай>/` (`payload.json`, `node.json` → `result.json`: строки с
  ключами, бинами и учётными данными, DoH, что берёт запись узла, следующая загрузка; 5 случаев), пишет
  `RaycontrolGoldenTest`. `config/node-*` — 7 конфигов со строками узла: `input.json` с `nodes` (JSON
  `RayNode.toJson`), `ConfigGoldenTest` подаёт их через `ConfigRay` приложения без данных сети; и снимок
  `desktop-node-vpn`. Kotlin и Go совпадают.

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

### Где живёт окно (с 2026-11-23)

Решение владельца от 2026-11-23 ([решения](../decisions.md)): после среза 11 окно на Flutter, его сборки архивом и
их CI ушли в свой репозиторий, [arngory/niko-desktop](https://github.com/arngory/niko-desktop) (публичный, с историей:
`git subtree split` каталога `desktop/` на `c51d3b0`). Здесь остались демон и его Go (`libcore/cmd/nikod`,
`nikoservice`, `nikostore`, `nikoconf`, `nikofmt`, `nikosub`, `nikoray`, `ruleset`) и общий слой `shared/` (proto,
токены, значки, golden) — рядом с Kotlin, с которым их сверяют.

- Окно пинит `niko` по коммиту его `main` в `.github/niko.env`. CI окна (`ci.yml` там) кладёт Niko на этом коммите
  рядом с собой (`niko/`, ядро на пине Niko — `sing-box/`), собирает там `nikod` и `boxdd` теми же тегами и флагами,
  что `desktop.yml` здесь, и проверяет: Dart из `shared/proto` (`tool/gen_proto.sh --check`), тему, шрифты и значки
  окна (`shared/tool/gen_tokens.py` и `gen_icons.py` с `--desktop <checkout окна>`), анализ, тесты и снимки экранов,
  тест окна с настоящим `nikod` на Windows и Linux, режим VPN и системный прокси на Windows, сборки окна.
- Здесь `desktop.yml` — сторона демона: `nikod`, `boxdd` и ядро для Windows amd64/arm64 и Linux (артефакт
  `desktop-windows` — для ручной проверки коммита; окно собирает свои из своего пина), Go на Windows, сгенерированный
  Go (`gen_proto_go.sh --check`). Без `--desktop` генераторы `shared/tool` файлы окна не трогают. Шрифты Geist
  теперь берутся из `app/src/main/res/font`, у окна — их копии.
- Срез, который трогает обе стороны: сначала PR здесь (proto, служба, golden, генераторы), после его слияния — PR в
  niko-desktop, который ставит новый коммит `main` в `.github/niko.env` и делает сторону окна. Пока пин не сдвинут,
  окно собирается и проверяется против прежнего коммита, так что PR здесь окно не ломает. Проверить окно против PR
  здесь до слияния можно ручным запуском `ci.yml` окна с коммитом этого PR (вход `niko`).

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
| 7 | Исходящие и конфиг одного профиля без адаптивного raycontrol (`nikoconf`), `Connect`. **7а сделано 2026-11-21**: исходящие (`nikoconf.Outbound`: сборщики каждого типа как в приложении, с их причудами; эталоны `shared/golden/outbounds/<name>.json` пишет Kotlin — `buildSingBoxOutbound` бина каждой ссылки, кроме профилей с плагином; все 97 совпадают по смыслу). **7б сделано 2026-11-21**: конфиг одного профиля без raycontrol (`nikoconf.Build(Input)`: вход как `ConfigInput`, устройство интерфейсом; эталоны `shared/golden/config/<случай>`: `input.json` пишется руками, `android.json`, `warnings.json` и `error.txt` пишет Kotlin; все 29 совпадают, порядок `HashSet` JVM повторён). Вне сборщика Go пока: профили с плагином, slipstream, цепочки (`ChainBean`), ShadowTLS и всё, что требует узла raycontrol. **7в сделано 2026-11-21**: `Connect` в `nikod` (API 5): профиль хранилища (0 — выбранный) с его группой собирает `nikoconf.Build`, ядро запускает `Host.StartService` форка — то же тело, что у `DesktopService.StartService` без параметров запуска (проверка владельца, конфиг и параметры — на следующий запуск демона); политика вызова `PolicyCaller`, владельца проверяет сам запуск. Настройки — значения приложения по умолчанию, но в режиме прокси: локальный прокси 127.0.0.1:2080 без входа, TUN нет; устройство — свободные порты от системы и часы. Ошибки: ничего не выбрано — `FAILED_PRECONDITION`, сборка не прошла — `INVALID_ARGUMENT`, профиль с плагином — `UNIMPLEMENTED`, служба чужая — `PERMISSION_DENIED`. В окне — `Daemon.connect`, кнопки пока нет. Осталось срезу 8: TUN и режим VPN; свои настройки компьютера вместо значений приложения (порт, вход, DNS) — срез 13 (сделано раньше, см. его строку) | подключение к сохранённому профилю | `golden` (исходящие, конфиги, круг через ядро); тест с `nikod`: профиль `socks5://` к SOCKS-серверу самого теста, запрос через локальный прокси доходит до HTTP-сервера теста через этот SOCKS-сервер |
| 8 | Части только компьютера: TUN, режим прокси (D-NAV-02), программы (D-RULES-02, -06), локальная сеть (D-SET-16), DNS (D-SET-18). **8а сделано 2026-11-21**: `nikoconf` собирает конфиг компьютера (`Input.Platform`: `android` по умолчанию, `windows`, `linux`; `nikoconf/desktop.go`), вывод Android не изменился — все 29 `android.json` совпадают. VPN: TUN `Niko` на Windows, `niko0` на Linux, `auto_route` и `strict_route`, адреса, MTU и стек — как у телефона; без `protect_path` и стража `tun0` (`package_name`), это `VpnService`. Свои соединения ядра мимо TUN ведёт `auto_detect_interface`, кроме клиента masterdnsvpn с его собственными сокетами: его резолверы TUN исключает из маршрутов (`route_exclude_address`). Прокси: mixed и системный прокси по настройке компьютера `systemProxy` (в 8а — `set_system_proxy` у mixed, по умолчанию выключен; в 8б — от имени владельца, см. ниже). Правила по программам (`Rule.programs`: имя — `process_name`, путь — `process_path`, то и другое — одним `process_path_regex`, ведь поля правила ядра складываются по «и»); правило с одними пакетами компьютер пропускает с предупреждением «rule needs Android», на Android `programs` не читаются. D-SET-16 — `bypassLanInCore`: сеть самого компьютера и так идёт мимо TUN по более узким маршрутам (Windows) и главной таблице (Linux), правило ведёт напрямую остальные частные адреса. D-SET-18 — те же настройки: `local` на компьютере — DNS системы (серверы интерфейсов, кроме TUN), запрос — диалером ядра. Снимки: 7 случаев приложения (`windows.json`, `windows-warnings.json`) и 5 своих (в 8б ещё 2) (`desktop-*`, `platform` во входе; тест Kotlin их пропускает), каждый читает парсер опций ядра с реестрами демона. `Connect` берёт режим из запроса (`ConnectRequest.mode`, API 6, в окне `Daemon.connect(mode:)`), платформу — из `runtime.GOOS`; по умолчанию режим прокси. **8б: окно и тест VPN сделаны 2026-11-22**: выбор режима — на главной (переключатель под статусом, где кнопка без `--config` подключает выбранный профиль службы, API ≥ 7) и в настройках; кнопка зовёт `Connect` в выбранном режиме, смена режима при работающем ядре запускает его в новом режиме. Программы через Niko (D-RULES-06) — настройки компьютера, как приложения через VPN на телефоне: `programsMode` (0 — все, 1 — только выбранные, 2 — кроме выбранных) и `programs` (строка — имя или полный путь), API 8, в окне — раздел «Программы» настроек; сборка ставит правило по программам (`Rule.programs`) первым, перед правилами пользователя: при «Только выбранные» — через узел, а `route.final` — `bypass` и `dns.final` — `dns-direct`, при «Кроме выбранных» — напрямую. Снимки `desktop-programs-only` (Windows, VPN) и `desktop-programs-except` (Linux, прокси). Тест режима VPN: `nikod` на `windows-latest` (`NIKO_VPN_TEST=1`) поднимает TUN, запрос к адресу TEST-NET-3 приходит на SOCKS-сервер профиля; программа самого теста — единственная через Niko, затем исключённая; системный прокси раннера сверяется до, во время и после. **Системный прокси от имени владельца (8б, вариант (а) владельца, 2026-11-22)**: у `Host.StartService` форка параметр системного прокси (адрес, порт, исключения — исключения только на Windows), он хранится в стартовых опциях и после перезапуска демона ставится снова. После запуска ядра демон ставит его через свою платформу, как `tun.platform.http_proxy`: от имени владельца (олицетворение на Windows, `executeAsOwner` на Linux), его же переключает `ManagedService.SetSystemProxyEnabled`, снимают остановка и `ResetPlatformOptions`, переносит смена сессии. Перед включением демон сохраняет свои настройки прокси пользователя (значения WinINet в HKCU: `ProxyEnable`, `ProxyServer`, `ProxyOverride`, `AutoConfigURL`, `Connections\DefaultConnectionSettings`) в файле рядом со стартовыми опциями, при выключении и остановке возвращает их в точности (после сброса апстрима) и сообщает WinINet; сбой с включённым прокси их не теряет, а где сохранённых нет — только сброс апстрима. Если поставить не вышло, ядро закрывается, как при отказе в конфиге (запуск, перезагрузка, восстановление); рядом с TUN, у которого есть `platform.http_proxy`, — `INVALID_ARGUMENT`. Тест форка `TestNekoSystemProxy` проверяет вызовы платформы на поддельной платформе, в CI — на Windows. `nikoconf` больше не ставит `set_system_proxy` у mixed: системный прокси идёт рядом с конфигом (`Result.SystemProxy`, снимок `<platform>-system-proxy.json`), `Connect` передаёт его демону. `systemProxy` по умолчанию включён на Windows и выключен на Linux. Тест на `windows-latest` включает прокси через `nikod`, читает значения WinINet в HKCU, выключает и сверяет с прежними; прежние значения раннера сохраняются до шага и возвращаются после него в любом случае. Запросы самого `nikod` (подписки, регистрация: срезы 9 и 11) `auto_detect_interface` не покрывает, см. раздел 8 | VPN на компьютере | снимки `windows.json` и `linux.json` (`TestDesktopSnapshots`, `NIKO_GOLDEN_UPDATE=1` переписывает); тест системного прокси с `SetSystemProxyEnabled` на `windows-latest`; `TestNekoSystemProxy` в форке |
| 9 | Обычные подписки (`nikosub`), D-SUB-01…04, обновление по времени — **сделано 2026-11-22** (`nikosub.Parse` — `RawUpdater.parseRaw`: Clash YAML по SnakeYAML 1.33 — его шаблоны неявных типов из jar (`yes`, `0777`, `1:30`), ключи слияния, последнее значение повторного ключа, пределы псевдонимов и вложенности, `!str`; WireGuard `.conf` как его читает ini4j — все `[Peer]` одна секция, то есть профиль из последних значений; JSON через org.json платформы (Hysteria 1, Shadowsocks, SIP008, Trojan-Go с его причудами, `outbounds` sing-box своими конфигами); base64 ссылок; ссылки — `nikofmt.ParseText`. `Prepare` и `Merge` — слияние `doUpdate`: полный конфиг отброшен, имена с « (1)», дедупликация по адресу+порту+классу (1.2.3.4:443 и 1.2.3.44:43 — один сервер), сопоставление по имени, свои настройки профиля сохраняются; порядок — с причудой приложения: изменённый профиль сохраняет старый номер. Профиль — то, что хранилище приложения вернёт из Kryo: в `nikofmt` Kryo всех бинов (`KryoClone`, `KryoEqual` без имени, как `equals`), бин с `null`, который приложение распаковывает (одиночный JSON Shadowsocks или Hysteria 1), валит обновление, как у приложения. Заголовки: `subscription-userinfo`, `profile-title` (и `base64:`), `content-disposition`, `announce`, `profile-update-interval` при первом обновлении, отказ панели HWID, переезд `new-url`/`new-domain` и запасной `fallback-url`; `forceResolve` с перевёрнутым порядком адресов приложения. Шов Kotlin: слияние и заголовки — `group/SubscriptionMerge.kt` без базы, `doUpdate` зовёт его. `golden`: 26 случаев `shared/golden/subscriptions/<случай>/` (тело и заголовки ответа, группа и её профили → `result.json` или `error.txt` от Kotlin), все совпадают; JSON своих конфигов сравнивается по смыслу — JSON-java в JVM пишет имена в порядке хеша, org.json платформы и Go — в своём, а с текстом меняется и хеш в имени «Custom N» (его Go сверяет по тексту Kotlin). Служба и окно — раздел 3, «Как сделано в срезе 9»; хранилище — схема 2, раздел 4. Окно: у группы подписки — трафик, срок, время обновления, объявление и «Обновить»; ссылка подписки на экране добавления создаёт группу подписки. Осталось: заголовки устройства для панелей HWID (`sendHwid`: нет секрета установки на компьютере, поле хранится, заголовки не уходят); подтверждение обновления по http:// и «только при подключении» вручную (приложение спрашивает, служба обновляет); в окне — настройки подписки (D-SUB-08), удаление группы (D-SUB-04), «Обновить все» (D-SUB-07) — в API есть; импорт файла YAML и `.conf` (D-ADD-09); `sn://subscription` с группой; частичная запись приложения, когда Kryo падает посреди слияния, не повторяется — служба не пишет ничего) | подписки провайдеров | `golden` подписок; тест Go с локальным HTTP-сервером (`nikoservice/subscriptions_test.go`) |
| 10 | Правила, пресет «Россия», наборы правил (D-RULES-01, -03, -05, D-SET-08, BG-09, D-CHECK-06) — **сделано 2026-11-23** (сборка: правила в `nikoconf` уже были со среза 7б — домены, IP, порты, сеть, источник, протокол, выход, DNS-правила, порядок, «вкл.», «Обход DPI» у прямого правила, программы компьютера; теперь и наборы, которые скачивает приложение (`srs:`): скачанный — локальный набор по своему файлу (`nikoconf.RuleSets`, имя файла — `RuleSetFiles.file`), DNS-правило берёт набор по тому, что в нём нашла проверка (только адреса — нет), без сведений — по имени; правило «напрямую» пресета ждёт, пока не скачаны все списки правила заблокированного; `Result.MissingRuleSets` — `missingInConfig` приложения. На компьютере нет geo-ассетов приложения: `geosite:` и `geoip:` (кроме `geoip:private`) он пропускает как нескачанные, с предупреждением «rule needs geo assets». Пресет — `nikoconf.RussiaPreset` и `IsRussia`, как `RoutePresets`. `golden`: `ConfigGoldenTest` читает `rule_sets` во входе (файл и `.kinds` в каталоге теста, пути конфига — `/golden/rule-sets`); 4 новых случая — пресет ждёт списков, всё скачано (наборы каждого вида, JSON-набор, набор по http, отсутствующий, IP-набор среди доменов, «Обход DPI» на правиле «напрямую»), скачано частично, экспорт со сведениями о наборах — Kotlin и Go совпадают; два из них — и снимками Windows. Проверка набора — `libcore/ruleset.Check` (тело `CheckRuleSet` вынесено из корня, корень — обёртка); теперь она не читает файл больше 32 МиБ и двоичный набор, чей zlib распаковывается больше чем в 256 МиБ (ядро читает его целиком при каждом запуске). Хранилище — схема 3, служба — раздел 3, «Как сделано в срезе 10». Окно: раздел «Правила» в виде «Расширенный» (D-NAV-03) — список по порядку, переключатель, выше и ниже, удаление, новое правило и правка общих полей, ошибка службы словами, пресет «Россия» с загрузкой его списков, наборы правил с размером, временем, содержимым и ошибкой, «Обновить»; шесть разделов шапки стоят теснее, чтобы уместиться в строку. Осталось: правило рекламы при первом запуске (D-SET-08) — у приложения это `geosite:category-ads-all` из geo-ассетов, которых на компьютере нет: нужен набор `srs:` (репозиторий списков) или geo-ассеты в `nikod`; перезапуск ядра, когда пришёл набор, которого не было при запуске (приложение перезапускает само; служба — только при следующем `Connect`: перезапуск от её имени требует личности вызвавшего, раздел 3); загрузка через ядро и обход TUN — срез 11 (пока служба качает списки сама, мимо ядра: в режиме прокси с системным прокси — с адреса компьютера, а приложение в таком случае не качает, `RuleSetFiles.mustUseLocalProxy`); в окне — свой JSON правила, выход на профиль (сохраняется, но не выбирается), «Сбросить правила», настройки интервала, DNS-маршрутизации и DPI (в API есть), импорт и удаление наборов, geo `.db` (D-RULES-03); D-CHECK-06 — данные в службе есть (`ListRuleSets`: когда обновлён каждый список, текст правила в `RouteTest`), показ — с разделом «Проверка маршрута»; проверка возможностей `tcp_desync` платформы, как `DpiDirect.capabilities` у приложения) | маршрутизация как на телефоне | `golden` конфигов с правилами; загрузка набора с локального сервера, `CheckRuleSet` (`ruleset/files_test.go`, `nikoservice/rules_test.go`) |
| 11 | Узел по приглашению, статическая часть: RCINV1, RCINV2, rcdev1, регистрация, подписка, профили узла, DPAPI, стук, обход туннеля (D-ADD-06, D-NODE-01…07, D-MAIN-13) — **сделано 2026-11-23** (`libcore/nikoray`: `rcinv1`, `RCINV2`, `RCINV3`, `rcdev1`, names-v1, узел, строки подписки, как в приложении; `golden` raycontrol: 38 пакетов и 5 подписок, Kotlin пишет, Go сверяет. Хранилище — схема 4: узлы, их группы и ключи регистраций в `store.json`, ключи устройства и клиентские, учётные данные строк и DoH запечатаны (DPAPI на Windows). Служба — `API = 11`: регистрация по приглашению с PIN и клиентским ключом, сохранённым до первой попытки; пакет устройства; подписка вручную и по расписанию; `ListNodes`, `DeleteNode`; `Connect` со строками узла — стук SPA с ключом по ссылке, TLS узла, DoH узла, вращающийся порт, dnsauth (`nikoconf/ray.go`, 7 golden-конфигов со строками узла и снимок); запросы службы к узлу мимо TUN при работающем ядре. Раздел 3, «Как сделано в срезе 11»; раздел 4. Окно: приглашение на экране добавления с PIN, карточка узла в «Узлах», строка узла на главной. Осталось: ретрансляторы (relay-v1: строки `via`, стук через ретранслятор, подписка через него); ротация клиентского ключа (`rotateIfDue`) и режим администратора; переход строки на `next` по времени без загрузки (`applyDue`) и откат на другую учётку (`useAlternative`) — со сторожем, этап 4; перезапуск ядра, когда сменилась строка, на которой оно работает (приложение перезапускает; служба — при следующем `Connect`, раздел 3, «Кто вызвал»); прошлые имена узла (`pastNames`) для SNI прелюдии; slipstream с dnsauth (`dnsAuthKeyHex`); скрытие адресов узлов в журнале (BG-10); проверка обхода TUN в тесте с TUN на Windows; обход TUN для подписок и наборов правил; минимальная версия по версии этой сборки (`RayClient.below`) — показывается только отказ узла; обработчик ссылок `rcinv` в системе (D-ADD-11) и QR (D-ADD-05)) | главный сценарий Niko | `golden` raycontrol (приглашения, подписки, конфиги со строками узла); тесты `nikostore` и `nikoservice` с подменой узла; interop `desktop` в `raycontrol-interop.yml`: `nikod` регистрируется по приглашению с PIN от `rayctl device invite` и подключается через каждый профиль узла |
| 12 | Проверка профилей без подключения (D-MAIN-08, -12) | пинги в списке | тест с `nikod`: пинг локального сервера |
| 13 | Настройки и резервная копия версии 2 (D-SET-05, -15, -19, -32), сначала на Android. **Часть компьютера сделана 2026-11-21, раньше среза**: настройки компьютера в `settings.json` (раздел 4), `GetSettings` и `UpdateSettings` (API 7): поля — только то, что сборка компьютера применяет сейчас (режим, системный прокси, стек и MTU TUN, IPv6, «не проверять сертификаты», удалённый и прямой DNS, FakeIP, стратегии доменов, оптимистичный кэш, локальная сеть напрямую, порт, вход и доступ из сети локального прокси, сниффинг, разрешение адреса, QUIC → TCP, ответ на ping, уровень журнала), с именами приложения; меняются поля, названные в запросе; проверка — диапазоны, перечисления, DNS так, как его читает сборка; `SETTINGS` в `Watch`. `Connect` собирает с ними, режим запроса — поверх их; свой вход приложения в локальный прокси — новый на каждый запуск. В окне — раздел «Настройки» руками по B-Settings, каждое изменение сразу в службу. Нет в окне: разделов того, чего у окна ещё нет (тема, язык, автозапуск, узлы raycontrol, naive, Yggdrasil, копия, обновления), и настроек, которых сборка компьютера пока не применяет: Clash API (панель — путь Android), DNS-маршрутизация и фрагментация TLS (нет правил, срез 10), проверка соединения (нет групп с автовыбором), сброс соединений при смене сети (это служба Android), общий свой конфиг (нет многострочного поля). Осталось: копия версии 2 | перенос с телефона | `golden` копий: Kotlin пишет, Go читает |
| 14 | Транспорт релиза: проба (в), затем реализация в форке и окне; имена Niko — **сторона демона и форка сделана 2026-11-26** (форк: `boxdd.Options.Names`, `Host.Caller`/`WorkingDirectory`/`UserDirectory`, жёсткий `--listen 127.0.0.1:<1024..65535>` и флаг `DevelopmentListen`, AF_UNIX worker с `SIO_AF_UNIX_GETPEERPID` + взаимный HMAC-SHA256 + переподключения, безопасная установка из ZIP в `Program Files` через `NtCreateFile` и манифест `resources\files.sha256`, `service status --format json`; `nikod`: имена Niko, `API = 12`, хранилище по `Host.Caller`) | окно в релизе видит демон | проба на `windows-latest`; `go test` форка и `nikod` на Windows и Linux; ревью безопасности |
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
  привязкой к физическому интерфейсу, проверка в тесте с TUN на Windows (срез 8 или 11). Проверено в срезе 8а:
  `route.auto_detect_interface` привязывает к физическому интерфейсу только диалер ядра (исходящие, DNS, стук — он
  теперь исходящий ядра, пиры Yggdrasil, dnstt), а не `net/http` самого `nikod`: его запросы надо вести диалером
  запущенного ядра или с той же привязкой. Сделано в срезе 11 для узлов: `Control` сетевого менеджера работающего
  ядра и его прямой DNS (раздел 3); в тесте с TUN на Windows не проверено. Подписки (срез 9) и наборы правил (срез 10)
  пока идут без него.
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

# Niko для компьютера: требования от экрана до ядра

Каждый элемент интерфейса получает ID. Этот ID стоит в имени узла в `.pen` и в комментарии в коде Flutter (см.
[README.md](README.md)). Строка матрицы говорит, через какой вызов демона элемент работает и где его логика живёт
сейчас.

**Где логика сейчас:**

- **есть** — уже работает в демоне `boxdd` или в ядре. Нужен только интерфейс.
- **Go** — логика в Go (`libcore`). Нужна обёртка в `NikoService`.
- **Kotlin** — логика только в Android-приложении. Переносим в Go и проверяем golden-тестом против Kotlin.
- **UI** — только интерфейс Flutter.
- **нет** — нет нигде, делаем с нуля.

Пути Kotlin даны от `app/src/main/java/io/niko/`. Вызовы демона:

- `boxdd` — `experimental/boxdd/desktop_service.proto` и `daemon/*.proto` форка ядра.
- `NikoService` — новый API, его ещё нет.

## Главная (D)

| ID | Элемент | Вызов демона | Где логика сейчас |
|---|---|---|---|
| D01 | Кнопка питания | `DesktopService.StartService(config)`, `ManagedService.StopService` | есть; конфиг собирается в Kotlin, см. S10 |
| D02 | Статус: подключается, подключено, ошибка с текстом | `StartedService.SubscribeServiceStatus` | есть |
| D03 | Время в сети | `StartedService.GetStartedAt` | есть |
| D04 | Скорость ↑↓ и трафик за сессию | `StartedService.SubscribeStatus` (uplink, downlink, totals) | есть |
| D05 | Текущий сервер и протокол | `SubscribeGroups` + профиль из `NikoService` | Kotlin (хранилище, S01) |
| D06 | Пинг текущего сервера | `StartedService.URLTest` | есть |
| D07 | Режим: Прокси или VPN (TUN) | конфиг (mixed или tun) + `ManagedService.SetSystemProxyEnabled` | Kotlin: `fmt/ConfigBuilder.kt` |
| D08 | IP выхода — только по кнопке, с предупреждением и выбором сервиса | `NikoService.ExitAddress` | Go: `libcore/exitaddress.go` |
| D09 | Предупреждение о подписке: срок, трафик, «исчерпан до» | `NikoService` | Kotlin: `group/SubscriptionHeaders.kt`, `raycontrol/RaySubscription.kt` |

## Серверы (S)

| ID | Элемент | Вызов демона | Где логика сейчас |
|---|---|---|---|
| S01 | Хранилище профилей и групп | `NikoService` | Kotlin: `database/` (Room) |
| S02 | Пинг списка без подключения | `NikoService.TestProfiles` | Kotlin + Go: `bg/proto/TestInstance.kt` собирает тестовый конфиг, `Libcore.urlTest` меряет |
| S03 | Строка сервера: имя, протокол, пинг цветом и числом | — | UI |
| S04 | Выбор сервера | `SelectOutbound` или перезапуск с новым конфигом | есть |
| S05 | Обновить подписку: кнопка и по таймеру | `NikoService` (таймер в службе) | Kotlin: `group/GroupUpdater.kt`, `group/RawUpdater.kt`, `bg/SubscriptionUpdater.kt` |
| S06 | Редактор профиля по протоколу (эксперт) | `NikoService` | Kotlin: 16 форм `ui/profile/*SettingsActivity.kt`, бины `fmt/*` |
| S07 | JSON профиля и конфига (эксперт) | `ApplicationService.CheckConfig`, `FormatConfig` | есть |
| S10 | Сборка конфига ядра из профиля и настроек | `NikoService` | Kotlin: `fmt/ConfigBuilder.kt` — главный перенос |

## Добавление (N)

| ID | Элемент | Вызов демона | Где логика сейчас |
|---|---|---|---|
| N01 | Одно поле «Вставить»: само определяет приглашение, подписку или ссылку профиля | `NikoService.Import` | Kotlin: `raycontrol/RayImport.kt`, `fmt/UniversalFmt.kt` |
| N02 | Ссылки профилей: vless, vmess, trojan, ss, hy2, tuic, naive, wireguard и другие | `NikoService.Import` | Kotlin: `fmt/*`, около 7000 строк |
| N03 | Подписка по URL: base64, Clash YAML, sing-box JSON, SIP008 | `NikoService.Import` | Kotlin: `group/RawUpdater.kt` |
| N04 | Приглашение raycontrol `RCINV2:` с PIN | `NikoService.Register` | Kotlin + Go: разбор в `raycontrol/RayImport.kt`, регистрация в `libcore/rayapi` |
| N05 | QR из файла или снимка экрана | — | нет; на Android камера (`ui/ScannerActivity.kt`) |
| N06 | Вставка из буфера обмена | — | UI, дальше N01 |
| N07 | Просмотр перед импортом: что добавится | `NikoService.Import` (dry run) | Kotlin: `ui/ImportReview.kt` |

## Узел raycontrol (R)

Всё в этом разделе, кроме R11 и R13, работает в фоне. Поэтому эта логика живёт в службе, а не в окне.

| ID | Элемент | Вызов демона | Где логика сейчас |
|---|---|---|---|
| R01 | Карточка узла: имя, заметка, техинфо, «Обновить» | `NikoService` | Kotlin: `raycontrol/RayNodeCard.kt`, `RayNodes.kt` |
| R02 | Подписка узла: ключи строк, время обновления, подсказки | `NikoService` | Kotlin + Go: `raycontrol/RaySubscription.kt`, `libcore/raysub` |
| R03 | Сторож: проверки, DoH, перезагрузка подписки | в службе | Kotlin + Go: `raycontrol/RayWatchdog.kt`, `libcore/dohcheck.go` |
| R04 | Адаптивность канала, Brutal по измерению | в службе | Kotlin + Go: `raycontrol/RayChannel.kt`, `RayBrutal.kt`, `libcore/rayprobe`, `libcore/probe` |
| R05 | Отчёты: согласие, сводки | в службе | Kotlin: `raycontrol/RayReport.kt` |
| R06 | DNS-туннели: выбор резолвера в каждой сети | в службе | Kotlin + Go: `raycontrol/RayDnsResolvers.kt`, `libcore/dnsprobe` |
| R07 | Yggdrasil: публичные пиры | в службе | Kotlin + Go: `raycontrol/RayYggPeers.kt`, `libcore/box_yggdrasil.go` |
| R08 | Ретрансляторы (relays, via) | `NikoService` | Kotlin: `raycontrol/RayRelays.kt` |
| R09 | Свои устройства (lan-v1) | `NikoService` | Kotlin: `raycontrol/RayLan.kt`, `ui/RayLanDialog.kt` |
| R10 | Обновление приложения с узла (app-update-v1) | `NikoService` + `DesktopService.InstallUpdate` | Kotlin + Go: `raycontrol/RayAppMirror.kt`, `libcore/raysub.go`, `minisign`; узел пока раздаёт только APK — нужен сервер |
| R11 | Админ-панель (только для админа узла) | `NikoService.ApiCall` | Go: `libcore/rayapi`; интерфейс `ui/RayAdminActivity.kt` переписывается во Flutter |
| R12 | Ключ устройства в защищённом хранилище | в службе | Kotlin: `raycontrol/DeviceKeyVault.kt` (Android Keystore); на Windows DPAPI — нет |
| R13 | «Почему не работает»: проверка и выводы | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestRun.kt`, `RayTestFindings.kt` |

## Маршрутизация (M)

| ID | Элемент | Вызов демона | Где логика сейчас |
|---|---|---|---|
| M01 | Пресеты-переключатели («Российские сайты напрямую» и т. п.) | `NikoService` | Kotlin: `RoutePresets.kt`, `RuleSetFiles.kt` |
| M02 | Обновление наборов правил | в службе | Kotlin + Go: `RuleSetFiles.kt`, `libcore/ruleset_check.go` |
| M03 | Редактор правил (эксперт) | `NikoService` | Kotlin: `ui/RouteSettingsActivity.kt`, `fmt/ConfigBuilder.kt` |
| M04 | Проверка маршрута: куда пойдёт домен или адрес | `NikoService.RouteTest` | Go: `libcore/routetest.go` |
| M05 | Правила по программам | конфиг (`process_name`, `process_path`) | ядро умеет; на Android это список приложений — на Windows нужен свой выбор программ (UI) |

## Журнал (L)

| ID | Элемент | Вызов демона | Где логика сейчас |
|---|---|---|---|
| L01 | Журнал ядра, очистка | `StartedService.SubscribeLog`, `ClearLogs` | есть |
| L02 | Секреты скрыты в журнале | в службе | Go: `libcore/redact.go`; в `boxdd` пока не подключено |
| L03 | Соединения: список, закрыть | `SubscribeConnections`, `CloseConnection`, `CloseAllConnections` | есть |
| L04 | Отчёты о падениях и нехватке памяти | `DesktopService` crash и OOM reports | есть |

## Настройки (P)

| ID | Элемент | Вызов демона | Где логика сейчас |
|---|---|---|---|
| P01 | Язык | `DesktopService.SetLocale` | есть |
| P02 | Тема: светлая, тёмная, как в системе | — | UI |
| P03 | Режим эксперта | — | UI |
| P04 | Корневые сертификаты naive: Chrome, Mozilla, системные | конфиг + `CRONET_WINDOWS_SYSTEM_ROOTS` | Go в ядре; настройка — Kotlin |
| P05 | DNS: удалённый, прямой, FakeIP | конфиг | Kotlin: `fmt/ConfigBuilder.kt` |
| P06 | Обновления с GitHub, подпись minisign | `DesktopService.InstallUpdate` | Go: `libcore/update.go`; сборки Windows в релизе нет |
| P07 | Служба Windows, автозапуск | `boxdd` в режиме службы | есть; установщика нет |
| P08 | Резервная копия, перенос с телефона | `NikoService` | Kotlin: `ui/BackupFragment.kt` |

## Трей (T)

| ID | Элемент | Вызов демона | Где логика сейчас |
|---|---|---|---|
| T01 | Значок в трее с состоянием | D02 | UI |
| T02 | Меню трея: подключить или отключить, режим, сервер, выход | D01, D07, S04 | UI |
| T03 | Компактное окно у трея: кнопка, сервер, скорость, список избранных | D01–D06 | UI |
| T04 | Уведомления: подписка истекает, ошибка подключения | D02, D09 | UI |

## Состояния (E)

| ID | Состояние | Откуда известно |
|---|---|---|
| E01 | Первый запуск: одно поле «Вставьте приглашение или ссылку» | S01 пусто |
| E02 | Ошибка подключения: текст ошибки и кнопка «Проверить» (R13) | D02 FATAL |
| E03 | Служба не установлена, для VPN (TUN) нужны права администратора | `DesktopService.GetDaemonInfo` |
| E04 | Нет сети | `boxdd` platform interface |
| E05 | Трафик исчерпан до даты | D09 |

## Протоколы под Windows

Ядро для Windows собирается с теми же тегами, что и для Android (`desktop.yml`): naive, knock, AmneziaWG, dnstt,
masterdns, Yggdrasil. Отличия:

- **naive:** `libcronet.dll` рядом с exe. Тесты под Windows проходят.
- **Slipstream:** отдельная программа. Для Android она собирается как `libslipstream.so`; сборки под Windows нет.
- **naive UDP через HTTP/3** (naive-udp-v1, #129) появится в ядре сразу для обеих платформ.

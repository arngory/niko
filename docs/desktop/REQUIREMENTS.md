# Niko для компьютера: требования от экрана до ядра

ID — в схеме дизайн-сессии: `D-<ЭКРАН>-<номер>`, у экрана без номера (`D-MAIN`). Этот ID стоит в имени узла в
`.pen` и в комментарии в коде Flutter (`// D-MAIN-12`). Элементы, которых на макете ещё нет, получают следующий
свободный номер своего экрана. Фоновая работа службы без экрана — `BG-<номер>`.

Строка матрицы говорит три вещи:

- через какой вызов демона элемент работает;
- где его логика живёт сейчас;
- совпадает ли с этим макет (раунд от 2026-11-16; D-SUB и D-MAIN-16/17 — по отчёту дизайн-сессии о третьем раунде, файл ещё не сверен).

**Где логика сейчас:**

- **есть** — уже работает в демоне `boxdd` или в ядре. Нужен только интерфейс.
- **Go** — логика в Go (`libcore`). Нужна обёртка в `NikoService`.
- **Kotlin** — логика только в Android-приложении. Переносим в Go и проверяем golden-тестом против Kotlin.
- **UI** — только интерфейс Flutter.
- **нет** — нет нигде, делаем с нуля.

**Макет:**

- **есть** — нарисовано и совпадает с данными.
- **нет** — ещё не нарисовано.
- **расходится** — нарисовано, но с данными не сходится. Что именно — коротко в ячейке.

Пути Kotlin даны от `app/src/main/java/io/niko/`. Вызовы демона:

- `boxdd` — `experimental/boxdd/desktop_service.proto` и `daemon/*.proto` форка ядра.
- `NikoService` — новый API, его ещё нет.

## Верхняя панель (D-NAV)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-NAV-01 | Разделы: Главная, Узлы, Проверка маршрута, Настройки (+ Админ, Журнал) | — | UI | есть; Журнала нет |
| D-NAV-02 | Режим: Прокси или VPN (TUN) | конфиг (mixed или tun) + `ManagedService.SetSystemProxyEnabled` | Kotlin: `fmt/ConfigBuilder.kt` | есть |

## Главная (D-MAIN)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-MAIN-01 | Заголовок «Трафик идёт через …»: узел и способ подключения | `SubscribeGroups` + профиль из `NikoService` | Kotlin (хранилище, BG-02) | расходится: город вместо узла |
| D-MAIN-02 | Статус: подключается, подключено, ошибка; время в сети | `SubscribeServiceStatus`, `GetStartedAt` | есть | расходится: «Защищено» вместо «Подключено» |
| D-MAIN-03 | Кнопка «Узнать IP выхода» | `NikoService.ExitAddress` | Go: `libcore/exitaddress.go` | есть |
| D-MAIN-04 | Подключить или отключить | `DesktopService.StartService(config)`, `ManagedService.StopService` | есть; конфиг — BG-01 | есть |
| D-MAIN-05 | Схема маршрута: остальное, пресеты, реклама | конфиг + `NikoService` (пресеты) | Kotlin: `RoutePresets.kt`, `database/ProfileManager.kt` | расходится: счётчика блокировок нет |
| D-MAIN-06 | Показатели: входящий, исходящий, задержка, за сессию | `SubscribeStatus` (uplink, downlink, totals), `URLTest` | есть | есть |
| D-MAIN-07 | Список способов подключения узла | `NikoService` | Kotlin (хранилище, BG-02) | расходится: города вместо способов |
| D-MAIN-08 | Проверить пинг всех строк без подключения | `NikoService.TestProfiles` | Kotlin + Go: `bg/proto/TestInstance.kt`, `Libcore.urlTest` | есть |
| D-MAIN-09 | Добавить | — | UI, дальше D-ADD | есть |
| D-MAIN-10 | Узел над списком, переход в карточку | — | UI | есть |
| D-MAIN-11 | Строка: способ (naive, Hysteria2, REALITY, через ретранслятор, DNS-туннель) и выбор | `SelectOutbound` или перезапуск с новым конфигом | есть; ретрансляторы — Kotlin `raycontrol/RayRelays.kt` | расходится: города |
| D-MAIN-12 | Пинг строки: число, цвет, полоски | `NikoService.TestProfiles` | см. D-MAIN-08 | есть |
| D-MAIN-13 | Срок и трафик узла, когда обновлён | `NikoService` | Kotlin: `raycontrol/RaySubscription.kt`, `group/SubscriptionHeaders.kt` | есть |
| D-MAIN-14 | Подтверждение «Узнать IP»: какой сервис спросят | `NikoService.ExitAddress` | Go: `libcore/exitaddress.go` (Cloudflare, ipinfo.io, ipwho.is, ipapi.co) | расходится: ifconfig.me |
| D-MAIN-16 | Причина ошибки подключения (на главной) | `SubscribeServiceStatus` (FATAL, текст ошибки) | есть | есть |
| D-MAIN-17 | «Почему не работает» с главной: открывает D-WHY | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestRun.kt` | есть |

## Добавление (D-ADD)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-ADD-02 | Поле «Вставить» | — | UI | расходится: пример RCINV2 в base64, нужен base32 |
| D-ADD-03 | Распознано: приглашение, подписка или ссылка профиля | `NikoService.Import` | Kotlin: `raycontrol/RayImport.kt`, `fmt/UniversalFmt.kt`, парсеры `fmt/*`, `group/RawUpdater.kt` | есть |
| D-ADD-04 | Из буфера | — | UI, дальше D-ADD-03 | есть |
| D-ADD-05 | QR из файла или снимка экрана | — | нет; на Android камера (`ui/ScannerActivity.kt`) | есть |
| D-ADD-06 | PIN приглашения, 6 цифр | `NikoService.Register` | Kotlin + Go: `raycontrol/RayImport.kt`, `libcore/rayapi` | расходится: 5 ячеек |
| D-ADD-07 | Что будет добавлено | `NikoService.Import` (dry run) | Kotlin: `ui/ImportReview.kt` | расходится: до регистрации состав узла неизвестен |
| D-ADD-08 | Добавить | `NikoService.Register` / `Import` | Kotlin + Go | есть |

## Карточка узла (D-NODE)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-NODE-01 | Имя узла | `NikoService` | Kotlin: `raycontrol/RayNodes.kt` | есть |
| D-NODE-02 | Обновить | `NikoService.Refresh` | Kotlin + Go: `raycontrol/RaySubscription.kt`, `libcore/raysub` | есть |
| D-NODE-03 | Заметка узла | `NikoService` | Kotlin: `raycontrol/RayNodeCard.kt` | есть |
| D-NODE-04 | Срок доступа, трафик до сброса | `NikoService` | Kotlin: `raycontrol/RayNodes.kt` (`RayTraffic`: used, limit, since, reset) | расходится: «за месяц» вместо «до сброса» |
| D-NODE-05 | Сведения: последнее и следующее обновление, подпись | `NikoService` | Kotlin: `raycontrol/RayNodeCard.kt` | расходится: «раз в 12 часов», «пресет узла» |
| D-NODE-06 | Способы подключения узла | `NikoService` | Kotlin | расходится: города |
| D-NODE-07 | Удалить узел: с подтверждением, что вернуть его можно только новым приглашением | `NikoService` | Kotlin (хранилище) | есть |
| D-NODE-08 | Свои устройства в сети узла (lan-v1) | `NikoService` | Kotlin: `raycontrol/RayLan.kt`, `ui/RayLanDialog.kt` | нет |

## Обычная подписка (D-SUB)

Подписка провайдера по ссылке (base64, Clash YAML, sing-box JSON, SIP008), не raycontrol. В списке — отдельной
группой рядом с узлом; здесь серверы действительно бывают в разных городах.

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-SUB-01 | Группа подписки в списке: имя, серверы с пингом | `NikoService` | Kotlin (хранилище, BG-02) | есть |
| D-SUB-02 | Срок и трафик подписки (`subscription-userinfo`) | `NikoService` | Kotlin: `group/SubscriptionHeaders.kt` | есть |
| D-SUB-03 | Обновить подписку вручную | `NikoService.Refresh` | Kotlin: `group/GroupUpdater.kt`, `group/RawUpdater.kt` | есть |
| D-SUB-04 | Удалить подписку: в меню «⋯», с подтверждением | `NikoService` | Kotlin (хранилище) | расходится: кнопка в шапке группы |

## Проверка маршрута (D-CHECK)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-CHECK-02 | Поле адреса | — | UI | есть |
| D-CHECK-03 | Проверить | `NikoService.RouteTest` | Go: `libcore/routetest.go` | есть |
| D-CHECK-04 | Результат | `NikoService.RouteTest` | Go | есть |
| D-CHECK-05 | Путь: сайт → правило → выход | `NikoService.RouteTest` | Go | есть |
| D-CHECK-06 | Почему: какой список и когда обновлён | `NikoService.RouteTest` + BG-09 | Go + Kotlin: `RuleSetFiles.kt` | расходится: набора «ru-sites» нет |
| D-CHECK-07 | Недавние | — | UI | есть |

## Почему не работает (D-WHY)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-WHY-02 | Ошибка подключения | `SubscribeServiceStatus` (FATAL) | есть | есть |
| D-WHY-03 | Шаги проверки | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestRun.kt`, `RayTestFindings.kt` | есть |
| D-WHY-04 | Что сделать | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestFindings.kt` | есть |
| D-WHY-05 | Переключиться на другой способ | `SelectOutbound` | есть | расходится: «Подключить Helsinki» |
| D-WHY-06 | Обновить узел | `NikoService.Refresh` | см. D-NODE-02 | есть |
| D-WHY-07 | Отчёт для админа | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestRun.kt` (журнал с run ID) | есть |

## Состояния (D-STATE-…)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-ADD | Первый запуск: узлов и подписок нет, открыт экран добавления | BG-02 (пусто) | UI | есть («Добавление (первый запуск)») |
| D-STATE-SVC | Служба не установлена: установить или режим «Прокси» | `DesktopService.GetDaemonInfo` | есть; установщика нет | есть |
| D-STATE-QUOTA | Трафик узла исчерпан до сброса | `NikoService` | Kotlin: `raycontrol/RaySubscription.kt` (traffic-limit-v1) | есть; «за этот месяц» → «до сброса» |
| D-STATE-NET | Нет сети | `boxdd` platform interface | есть | нет |

## Админ-панель (D-ADMIN)

Видна только админу узла. Вызовы — `NikoService.ApiCall`, логика в Go: `libcore/rayapi`. Интерфейс Android —
`ui/RayAdminActivity.kt`.

| ID | Элемент | Вызов API узла | Макет |
|---|---|---|---|
| D-ADMIN-01 | Имя узла | `GetStatus`, `SetNodeName` | есть |
| D-ADMIN-02 | Изменить заметку | `SetNodeNote` | есть |
| D-ADMIN-03 | Создать приглашение, PIN по выбору | `CreateInvite` | есть |
| D-ADMIN-04 | Сводка: участники, активные устройства, трафик, серверы | `GetStatus`, `GetMetrics` | расходится: «серверы в сети» — уточнить у сервера |
| D-ADMIN-05 | Участники: устройства, трафик, срок, включён | `ListUsers`, `ListDevices`, `RenewUser`, `SetUserEnabled`, `DeleteUser` | расходится: «в сети» по участнику узел не отдаёт; трафик по участнику — уточнить |
| D-ADMIN-06 | Отозвать устройство | `RevokeDevice` | нет |
| D-ADMIN-07 | Службы участника | `ListServices`, `SetUserServices` | нет |
| D-ADMIN-08 | Обновление узла | `GetUpdateStatus`, `StartUpdate` | нет |
| D-ADMIN-09 | Зеркало приложения, сводки, доступ к API | `SetAppMirror`, `SetSummary`, `SetAdminAccess` | нет |

## Трей (D-TRAY)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-TRAY-01 | Значок с состоянием | `SubscribeServiceStatus` | UI | есть |
| D-TRAY-02 | Меню: отключить, режим, способ подключения, открыть, выход | D-MAIN-04, D-NAV-02, D-MAIN-11 | UI | расходится: города в подменю |
| D-TRAY-03 | Компактное окно | — | UI | есть |
| D-TRAY-04 | Кнопка и текущий способ | D-MAIN-04, D-MAIN-01 | UI | есть |
| D-TRAY-05 | Скорость и пинг | `SubscribeStatus` | есть | есть |
| D-TRAY-06 | Избранные | `NikoService` | нет | есть |
| D-TRAY-07 | Открыть Niko | — | UI | есть |
| D-TRAY-08 | Уведомления: срок или трафик кончается, ошибка подключения | D-MAIN-13, D-MAIN-02 | UI | нет |

## Настройки (D-SET)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-SET-03 | Тема: как в системе, светлая, тёмная | — | UI | есть |
| D-SET-04 | Запускать вместе с системой | `boxdd` в режиме службы + автозапуск окна | есть; установщика нет | есть |
| D-SET-05 | Подключаться при запуске | `NikoService` | Kotlin: `BootReceiver.kt`; на компьютере — служба | есть |
| D-SET-07 | Пресет «Россия» | `NikoService` | Kotlin: `RoutePresets.kt`, `RuleSetFiles.kt` | есть |
| D-SET-08 | Блокировать рекламу (`geosite:category-ads-all`) | `NikoService` | Kotlin: `database/ProfileManager.kt` | есть |
| D-SET-10 | Режим эксперта | — | UI | есть |
| D-SET-12 | О программе: версия, ядро, обновления (GitHub и узел, подпись minisign) | `DesktopService.InstallUpdate` + `NikoService` | Go: `libcore/update.go`, `minisign`; с узла — Kotlin `raycontrol/RayAppMirror.kt` | есть |
| D-SET-13 | Язык | `DesktopService.SetLocale` | есть | нет |
| D-SET-14 | Сервис для IP выхода | `NikoService.ExitAddress` | Go: `libcore/exitaddress.go` | нет |
| D-SET-15 | Корневые сертификаты naive: Chrome, Mozilla, системные | конфиг + `CRONET_WINDOWS_SYSTEM_ROOTS` | Go в ядре; настройка — Kotlin | нет |
| D-SET-16 | Локальная сеть напрямую | конфиг | Kotlin: `bypassLanInCore` в `fmt/ConfigBuilder.kt` | нет |
| D-SET-17 | Отчёты для админа узла: согласие | `NikoService` | Kotlin: `raycontrol/RayReport.kt` | нет |
| D-SET-18 | DNS: удалённый, прямой, FakeIP (эксперт) | конфиг | Kotlin: `fmt/ConfigBuilder.kt` | нет |
| D-SET-19 | Резервная копия, перенос с телефона | `NikoService` | Kotlin: `ui/BackupFragment.kt` | нет |

## Экраны режима эксперта (ещё не нарисованы)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-LOG-01 | Журнал ядра, очистка | `SubscribeLog`, `ClearLogs` | есть | нет |
| D-LOG-02 | Отчёты о падениях и нехватке памяти | `DesktopService` crash и OOM reports | есть | нет |
| D-CONN-01 | Соединения: список, закрыть | `SubscribeConnections`, `CloseConnection`, `CloseAllConnections` | есть | нет |
| D-RULES-01 | Редактор правил | `NikoService` | Kotlin: `ui/RouteSettingsActivity.kt`, `fmt/ConfigBuilder.kt` | нет |
| D-RULES-02 | Правило по программам (`process_name`, `process_path`) | конфиг | ядро умеет; на Android это список приложений | нет |
| D-PROFILE-01 | Ручная форма профиля по протоколу | `NikoService` | Kotlin: 16 форм `ui/profile/*SettingsActivity.kt`, бины `fmt/*` | нет |
| D-PROFILE-02 | JSON профиля и конфига | `ApplicationService.CheckConfig`, `FormatConfig` | есть | нет |

## Фон службы, без экрана (BG)

Это работает, даже когда окно закрыто, поэтому живёт в службе.

| ID | Что | Где логика сейчас |
|---|---|---|
| BG-01 | Сборка конфига ядра из профиля и настроек — главный перенос | Kotlin: `fmt/ConfigBuilder.kt` |
| BG-02 | Хранилище профилей и узлов | Kotlin: `database/` (Room) |
| BG-03 | Обновление подписок по времени: узлы raycontrol и обычные подписки | Kotlin: `bg/SubscriptionUpdater.kt`, `raycontrol/RaySubscription.kt`, `group/GroupUpdater.kt`, `group/RawUpdater.kt` |
| BG-04 | Сторож: проверки, DoH, перезагрузка подписки | Kotlin + Go: `raycontrol/RayWatchdog.kt`, `libcore/dohcheck.go` |
| BG-05 | Адаптивность канала, Brutal по измерению | Kotlin + Go: `raycontrol/RayChannel.kt`, `RayBrutal.kt`, `libcore/rayprobe`, `libcore/probe` |
| BG-06 | Отчёты для админа | Kotlin: `raycontrol/RayReport.kt` |
| BG-07 | DNS-туннели: выбор резолвера в каждой сети | Kotlin + Go: `raycontrol/RayDnsResolvers.kt`, `libcore/dnsprobe` |
| BG-08 | Yggdrasil: публичные пиры | Kotlin + Go: `raycontrol/RayYggPeers.kt`, `libcore/box_yggdrasil.go` |
| BG-09 | Наборы правил: загрузка и проверка | Kotlin + Go: `RuleSetFiles.kt`, `libcore/ruleset_check.go` |
| BG-10 | Секреты скрыты в журнале | Go: `libcore/redact.go`; в `boxdd` пока не подключено |
| BG-11 | Ключ устройства в защищённом хранилище | Kotlin: `raycontrol/DeviceKeyVault.kt` (Android Keystore); на Windows DPAPI — нет |
| BG-12 | Обновление приложения с узла | Kotlin + Go: `raycontrol/RayAppMirror.kt`, `libcore/raysub.go`; узел пока раздаёт только APK — нужен сервер |

## Протоколы под Windows

Ядро для Windows собирается с теми же тегами, что и для Android (`desktop.yml`): naive, knock, AmneziaWG, dnstt,
masterdns, Yggdrasil. Отличия:

- **naive:** `libcronet.dll` рядом с exe. Тесты под Windows проходят.
- **Slipstream:** отдельная программа. Для Android она собирается как `libslipstream.so`; сборки под Windows нет.
- **naive UDP через HTTP/3** (naive-udp-v1, #129) появится в ядре сразу для обеих платформ.

# Niko для компьютера: требования от экрана до ядра

ID — в схеме дизайн-сессии: `D-<ЭКРАН>-<номер>`, у экрана без номера (`D-MAIN`). Этот ID стоит в названии артборда и
элемента на холсте Claude Design и в комментарии в коде Flutter (`// D-MAIN-12`). Элементы, которых на макете ещё нет, получают следующий
свободный номер своего экрана. Фоновая работа службы без экрана — `BG-<номер>`.

Строка матрицы говорит три вещи:

- через какой вызов демона элемент работает;
- где его логика живёт сейчас;
- совпадает ли с этим макет. Отметки «расходится» сняты с третьего раунда pen.dev (2026-11-16, по его выгрузке);
  дизайн с тех пор ведётся в Claude Design с теми же ID, и отметки «есть» у строк, добавленных позже, сделаны уже
  по его холсту.

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
| D-NAV-01 | Разделы: Главная, Узлы, Проверка маршрута, Настройки (+ Админ, Журнал) | — | UI | есть |
| D-NAV-02 | Режим: Прокси или VPN (TUN) | конфиг (mixed или tun) + `ManagedService.SetSystemProxyEnabled` | Kotlin: `fmt/ConfigBuilder.kt` | расходится: был в шапке; в Простом — плитка, в Расширенном — переключатель в карточке подключения |
| D-NAV-03 | Вид интерфейса: Простой или Расширенный, переключатель в шапке (как в mdll). Простой: кнопка подключения, статус, 2–4 плитки, текущий узел. Расширенный: всё, плюс разделы «Соединения» и «Правила», JSON и ручная форма. Не сбрасывает подключение | — | UI (настройка приложения) | есть (Claude Design, вариант B) |

## Главная (D-MAIN)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-MAIN-01 | Заголовок «Трафик идёт через …»: узел и способ подключения | `SubscribeGroups` + профиль из `NikoService` | Kotlin (хранилище, BG-02) | есть |
| D-MAIN-02 | Статус: подключается, подключено, ошибка; время в сети | `SubscribeServiceStatus`, `GetStartedAt` | есть | есть |
| D-MAIN-03 | Кнопка «Узнать IP выхода» | `NikoService.ExitAddress` | Go: `libcore/exitaddress.go` | есть |
| D-MAIN-04 | Подключить или отключить | `DesktopService.StartService(config)`, `ManagedService.StopService` | есть; конфиг — BG-01 | есть |
| D-MAIN-05 | Схема маршрута: остальное, пресет, правила | конфиг + `NikoService` (пресеты) | Kotlin: `RoutePresets.kt`, `database/ProfileManager.kt` | есть |
| D-MAIN-06 | Показатели: входящий, исходящий, задержка, за сессию | `SubscribeStatus` (uplink, downlink, totals), `URLTest` | есть | есть |
| D-MAIN-07 | Список способов подключения узла | `NikoService` | Kotlin (хранилище, BG-02) | есть |
| D-MAIN-08 | Проверить пинг всех строк без подключения | `NikoService.TestProfiles` | Kotlin + Go: `bg/proto/TestInstance.kt`, `Libcore.urlTest` | есть |
| D-MAIN-09 | Добавить | — | UI, дальше D-ADD | есть |
| D-MAIN-10 | Узел над списком, переход в карточку | — | UI | есть |
| D-MAIN-11 | Строка: способ (naive, Hysteria2, REALITY, через ретранслятор, DNS-туннель) и выбор | `SelectOutbound` или перезапуск с новым конфигом | есть; ретрансляторы — Kotlin `raycontrol/RayRelays.kt` | есть |
| D-MAIN-12 | Пинг строки: число, цвет, полоски | `NikoService.TestProfiles` | см. D-MAIN-08 | есть |
| D-MAIN-13 | Срок и трафик узла, когда обновлён | `NikoService` | Kotlin: `raycontrol/RaySubscription.kt`, `group/SubscriptionHeaders.kt` | есть |
| D-MAIN-14 | Подтверждение «Узнать IP»: какой сервис спросят | `NikoService.ExitAddress` | Go: `libcore/exitaddress.go` (Cloudflare, ipinfo.io, ipwho.is, ipapi.co) | есть |
| D-MAIN-16 | Причина ошибки подключения (на главной) | `SubscribeServiceStatus` (FATAL, текст ошибки) | есть | расходится: текст баннера обрезан кнопкой «Почему не работает» |
| D-MAIN-17 | «Почему не работает» с главной: открывает D-WHY | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestRun.kt` | есть |

## Добавление (D-ADD)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-ADD-02 | Поле «Вставить» | — | UI | есть |
| D-ADD-03 | Распознано: приглашение, подписка или ссылка профиля | `NikoService.Import` | Kotlin: `raycontrol/RayImport.kt`, `fmt/UniversalFmt.kt`, парсеры `fmt/*`, `group/RawUpdater.kt` | есть |
| D-ADD-04 | Из буфера | — | UI, дальше D-ADD-03 | есть |
| D-ADD-05 | QR из файла или снимка экрана | — | нет; на Android камера (`ui/ScannerActivity.kt`) | есть |
| D-ADD-06 | PIN приглашения, 6 цифр | `NikoService.Register` | Kotlin + Go: `raycontrol/RayImport.kt`, `libcore/rayapi` | есть |
| D-ADD-07 | Что будет добавлено | `NikoService.Import` (dry run) | Kotlin: `ui/ImportReview.kt` | расходится: подсказка внизу обрезана кнопкой |
| D-ADD-08 | Добавить | `NikoService.Register` / `Import` | Kotlin + Go | есть |

## Карточка узла (D-NODE)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-NODE-01 | Имя узла | `NikoService` | Kotlin: `raycontrol/RayNodes.kt` | есть |
| D-NODE-02 | Обновить | `NikoService.Refresh` | Kotlin + Go: `raycontrol/RaySubscription.kt`, `libcore/raysub` | есть |
| D-NODE-03 | Заметка узла | `NikoService` | Kotlin: `raycontrol/RayNodeCard.kt` | есть |
| D-NODE-04 | Срок доступа, трафик до сброса | `NikoService` | Kotlin: `raycontrol/RayNodes.kt` (`RayTraffic`: used, limit, since, reset) | есть |
| D-NODE-05 | Сведения: последнее и следующее обновление, подпись | `NikoService` | Kotlin: `raycontrol/RayNodeCard.kt` | есть |
| D-NODE-06 | Способы подключения узла | `NikoService` | Kotlin | есть |
| D-NODE-07 | Удалить узел: с подтверждением, что вернуть его можно только новым приглашением | `NikoService` | Kotlin (хранилище) | есть |
| D-NODE-08 | Свои устройства в сети узла (lan-v1) | `NikoService` | Kotlin: `raycontrol/RayLan.kt`, `ui/RayLanDialog.kt` | расходится: «2 из 3 разрешённых» — предела устройств узел не отдаёт |
| D-NODE-09 | UDP у способа naive в этой сети: QUIC, через TCP пока идёт проверка, через TCP потому что QUIC не проходит (naive-udp-v1) | `NikoService` (состояние ядра `NaiveDatagramState` и вердикт пробы сети) | Go: `libcore/naive_datagram.go`; проба и память по сетям — Kotlin: `raycontrol/RayDatagrams.kt` | есть |

## Обычная подписка (D-SUB)

Подписка провайдера по ссылке (base64, Clash YAML, sing-box JSON, SIP008), не raycontrol. В списке — отдельной
группой рядом с узлом; здесь серверы действительно бывают в разных городах.

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-SUB-01 | Группа подписки в списке: имя, серверы с пингом | `NikoService` | Kotlin (хранилище, BG-02) | есть |
| D-SUB-02 | Срок и трафик подписки (`subscription-userinfo`) | `NikoService` | Kotlin: `group/SubscriptionHeaders.kt` | есть |
| D-SUB-03 | Обновить подписку вручную | `NikoService.Refresh` | Kotlin: `group/GroupUpdater.kt`, `group/RawUpdater.kt` | есть |
| D-SUB-04 | Удалить подписку: в меню «⋯», с подтверждением | `NikoService` | Kotlin (хранилище) | есть |

## Проверка маршрута (D-CHECK)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-CHECK-02 | Поле адреса | — | UI | есть |
| D-CHECK-03 | Проверить | `NikoService.RouteTest` | Go: `libcore/routetest.go` | есть |
| D-CHECK-04 | Результат | `NikoService.RouteTest` | Go | есть |
| D-CHECK-05 | Путь: сайт → правило → выход | `NikoService.RouteTest` | Go | есть |
| D-CHECK-06 | Почему: какой список и когда обновлён | `NikoService.RouteTest` + BG-09 | Go + Kotlin: `RuleSetFiles.kt` | есть |
| D-CHECK-07 | Недавние | — | UI | есть |

## Почему не работает (D-WHY)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-WHY-02 | Ошибка подключения | `SubscribeServiceStatus` (FATAL) | есть | есть |
| D-WHY-03 | Шаги проверки | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestRun.kt`, `RayTestFindings.kt` | есть |
| D-WHY-04 | Что сделать | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestFindings.kt` | есть |
| D-WHY-05 | Переключиться на другой способ | `SelectOutbound` | есть | есть |
| D-WHY-06 | Обновить узел | `NikoService.Refresh` | см. D-NODE-02 | есть |
| D-WHY-07 | Отчёт для админа | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestRun.kt` (журнал с run ID) | есть |

## Состояния (D-STATE-…)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-ADD | Первый запуск: узлов и подписок нет, открыт экран добавления | BG-02 (пусто) | UI | есть («Добавление (первый запуск)») |
| D-STATE-SVC | Служба не установлена: установить или режим «Прокси» | `DesktopService.GetDaemonInfo` | есть; установщика нет | есть |
| D-STATE-QUOTA | Трафик узла исчерпан до сброса | `NikoService` | Kotlin: `raycontrol/RaySubscription.kt` (traffic-limit-v1) | есть |
| D-STATE-NET | Нет сети | `boxdd` platform interface | есть | есть |

## Админ-панель (D-ADMIN)

Видна только админу узла. Вызовы — `NikoService.ApiCall`, логика в Go: `libcore/rayapi`. Интерфейс Android —
`ui/RayAdminActivity.kt`.

| ID | Элемент | Вызов API узла | Макет |
|---|---|---|---|
| D-ADMIN-01 | Имя узла | `GetStatus`, `SetNodeName` | есть |
| D-ADMIN-02 | Изменить заметку | `SetNodeNote` | есть |
| D-ADMIN-03 | Создать приглашение, PIN по выбору | `CreateInvite` | есть |
| D-ADMIN-04 | Сводка: участники, активные устройства, трафик, серверы | `GetStatus`, `GetMetrics` | расходится: «трафик за месяц» и «серверы 4 / 5» — уточнить у сервера |
| D-ADMIN-05 | Участники: устройства, трафик, срок, включён | `ListUsers`, `ListDevices`, `RenewUser`, `SetUserEnabled`, `DeleteUser` | есть |
| D-ADMIN-06 | Отозвать устройство | `RevokeDevice` | есть |
| D-ADMIN-07 | Службы участника | `ListServices`, `SetUserServices` | есть |
| D-ADMIN-08 | Обновление узла | `GetUpdateStatus`, `StartUpdate` | есть |
| D-ADMIN-09 | Зеркало приложения, сводки, доступ к API | `SetAppMirror`, `SetSummary`, `SetAdminAccess` | есть |
| D-ADMIN-10 | Состояние: это устройство, узел (хост, версия, id), счётчики, открытые порты SPA | `GetSelf`, `GetStatus`, `GetPorts` | есть |
| D-ADMIN-11 | Порты, метрики | `GetPorts`, `GetMetrics` | есть |
| D-ADMIN-12 | Аварийный SSH: адреса (CIDR) | `GetAdminAccess`, `SetAdminAccess` | есть |
| D-ADMIN-13 | Новый участник: имя, дни, ГБ, заметка, службы | `CreateUser`, `SetUserServices` | есть |
| D-ADMIN-14 | Ссылки для других приложений; новые учётные данные участника | `ExportUser`, `RenewUser` | есть |
| D-ADMIN-15 | Службы узла: список, создать, включить или выключить, удалить (последний SSH — с подтверждением) | `ListServices`, `CreateService`, `SetServiceEnabled`, `DeleteService` | есть |
| D-ADMIN-16 | Настройки службы | `SetServiceOption` | есть |
| D-ADMIN-17 | Смена ключей службы: по плану или сейчас | `RotateService` | есть |
| D-ADMIN-18 | Сеть: DNS-провайдер, проверка зон туннелей, ретрансляторы и SSH к ним | `GetDNS`, `SetDNSProvider`, `SyncDNS`, `RemoveDNS`, `CheckDNSZones`, `ListRelays`, `SetRelayAccess` | есть |
| D-ADMIN-19 | Журнал аудита | `ListAudit` | есть |
| D-ADMIN-20 | Проверка приложения: режим (выкл., учитывать, строго, только RKP), мин. версия, патч Android, кто не прошёл | новое: вызовы из спеки сервера app-attest-v1 | есть |
| D-ADMIN-21 | Обязательные правила узла: набор, путь (`block`, `node`, `relay`, `direct`), запасной путь | новое: вызовы из спеки сервера rules-v1 | есть |

## Трей (D-TRAY)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-TRAY-01 | Значок с состоянием | `SubscribeServiceStatus` | UI | есть |
| D-TRAY-02 | Меню: отключить, режим, способ подключения, открыть, выход | D-MAIN-04, D-NAV-02, D-MAIN-11 | UI | есть |
| D-TRAY-03 | Компактное окно | — | UI | есть |
| D-TRAY-04 | Кнопка и текущий способ | D-MAIN-04, D-MAIN-01 | UI | есть |
| D-TRAY-05 | Скорость и пинг | `SubscribeStatus` | есть | есть |
| D-TRAY-06 | Избранные | `NikoService` | нет | есть, помечено «позже»: функции нет, решает владелец |
| D-TRAY-07 | Открыть Niko | — | UI | есть |
| D-TRAY-08 | Уведомления: срок или трафик кончается, ошибка подключения | D-MAIN-13, D-MAIN-02 | UI | есть |

## Настройки (D-SET)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-SET-03 | Тема: как в системе, светлая, тёмная | — | UI | есть |
| D-SET-04 | Запускать вместе с системой | `boxdd` в режиме службы + автозапуск окна | есть; установщика нет | есть |
| D-SET-05 | Подключаться при запуске | `NikoService` | Kotlin: `BootReceiver.kt`; на компьютере — служба | есть |
| D-SET-07 | Пресет «Россия»: заменён выбором пресета страны в правилах (D-RULES-05) | `NikoService` | Kotlin: `RoutePresets.kt`, `RuleSetFiles.kt` | расходится: выбор пресета теперь в «Правилах», а не в настройках |
| D-SET-08 | Реклама и трекеры: обычное правило «Блок» по `geosite:category-ads-all` в списке правил, отдельного переключателя и плитки нет (решение владельца) | `NikoService` | Kotlin: правило создаётся при первом запуске, `database/ProfileManager.kt` | есть |
| D-SET-10 | Режим эксперта: заменён видом «Расширенный» (D-NAV-03) | — | UI | расходится: отдельной настройки больше нет |
| D-SET-12 | О программе: версия, ядро, обновления (GitHub и узел, подпись minisign) | `DesktopService.InstallUpdate` + `NikoService` | Go: `libcore/update.go`, `minisign`; с узла — Kotlin `raycontrol/RayAppMirror.kt` | есть |
| D-SET-33 | Проверять обновления самому: отдельная настройка, по умолчанию выключена (решение владельца); включённая только сообщает о новой версии | `NikoService` + `DesktopService.InstallUpdate` | Go: `libcore/update.go` для проверки; расписание и настройка — новое в Kotlin и в службе | есть |
| D-SET-13 | Язык | `DesktopService.SetLocale` | есть | есть |
| D-SET-14 | Сервис для IP выхода | `NikoService.ExitAddress` | Go: `libcore/exitaddress.go` | есть |
| D-SET-15 | Корневые сертификаты naive: Chrome, Mozilla, системные | конфиг + `CRONET_WINDOWS_SYSTEM_ROOTS` | Go в ядре; настройка — Kotlin | есть |
| D-SET-16 | Локальная сеть напрямую | конфиг | Kotlin: `bypassLanInCore` в `fmt/ConfigBuilder.kt` | есть |
| D-SET-17 | Отчёты для админа узла: согласие | `NikoService` | Kotlin: `raycontrol/RayReport.kt` | есть |
| D-SET-18 | DNS: удалённый, прямой, FakeIP (эксперт) | конфиг | Kotlin: `fmt/ConfigBuilder.kt` | есть |
| D-SET-19 | Резервная копия, перенос с телефона | `NikoService` | Kotlin: `ui/BackupFragment.kt` | есть |

## Экраны вида «Расширенный» (бывший режим эксперта)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет |
|---|---|---|---|---|
| D-LOG-01 | Журнал ядра, очистка | `SubscribeLog`, `ClearLogs` | есть | есть |
| D-LOG-02 | Отчёты о падениях и нехватке памяти | `DesktopService` crash и OOM reports | есть | есть |
| D-CONN-01 | Соединения: список, закрыть | `SubscribeConnections`, `CloseConnection`, `CloseAllConnections` | есть | есть |
| D-RULES-01 | Редактор правил | `NikoService` | Kotlin: `ui/RouteSettingsActivity.kt`, `fmt/ConfigBuilder.kt` | есть |
| D-RULES-02 | Правило по программам (`process_name`, `process_path`) | конфиг | ядро умеет; на Android это список приложений | есть |
| D-RULES-03 | Наборы правил: версии, обновить, удалить, импорт; источник и интервал обновления | `NikoService` | Kotlin: `ui/AssetsActivity.kt`, `RuleSetFiles.kt` | есть |
| D-RULES-04 | Свой корневой сертификат из .pem, с предупреждением | `NikoService` | Kotlin: `ui/AssetsActivity.kt` | есть |
| D-RULES-05 | Пресет страны: один из «Нет, Россия, Китай, Иран» («ок» владельца 2026-11-16). Китай и Иран — по образцу «России»: заблокированное через узел, доступное только изнутри и адреса страны напрямую, из наборов с обновлением | `NikoService` | частично: в Kotlin `RoutePresets.kt` есть только «Россия». Китай и Иран — новое, из репозитория списков: Loyalsoldier и Chocolate4U входят туда исходными данными через его CI ([схема](../design/ROUTING-APPS.md) §2) | есть |
| D-RULES-06 | Программы через Niko: «Все, Только выбранные, Кроме выбранных» и список — то же, что приложения на телефоне (M-RULES-01) | конфиг | новое: «Кроме выбранных» — правило по `process_path` «Напрямую» первым в списке; «Только выбранные» — такое же правило через узел, а остальное напрямую | есть |
| D-RULES-07 | Списки приложений из репозитория списков: включить, версия и дата, путь каждого имени сервиса ([схема](../design/ROUTING-APPS.md)) | `NikoService` + конфиг | новое: снимок в установщике, обновление через ноду с подписью | есть |
| D-RULES-08 | Обязательные правила от ноды (`enforce`), с замком «Задано подпиской»: `block`, `node`, `relay` (только наборы inside-only), `direct` — у администратора все возможности, клиент недоверенный (решение владельца 2026-11-16), все правила ноды выше своих правил пользователя, в том числе над приложением, которое он сам отправил через узел; справка объясняет цену «напрямую» и «через relay» ([схема](../design/ROUTING-APPS.md) §5) | `NikoService` | новое, вместе с сервером | есть |
| D-PROFILE-01 | Ручная форма профиля по протоколу; у naive переключатель «UDP: QUIC, через TCP если не проходит» | `NikoService` | Kotlin: 16 форм `ui/profile/*SettingsActivity.kt`, бины `fmt/*` | расходится: переключатель UDP нарисован в форме VLESS, он только у naive |
| D-PROFILE-02 | JSON профиля и конфига | `ApplicationService.CheckConfig`, `FormatConfig` | есть | есть |

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

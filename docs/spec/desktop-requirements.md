# Niko для компьютера: требования от экрана до ядра

ID — в схеме дизайн-сессии: `D-<ЭКРАН>-<номер>`, у экрана без номера (`D-MAIN`). Этот ID стоит в названии артборда и
элемента на холсте Claude Design и в комментарии в коде Flutter (`// D-MAIN-12`). Элементы, которых на макете ещё нет, получают следующий
свободный номер своего экрана. Фоновая работа службы без экрана — `BG-<номер>`.

Строка матрицы говорит пять вещей:

- через какой вызов демона элемент работает;
- где его логика живёт сейчас;
- совпадает ли с этим макет. Отметки «расходится» сняты с третьего раунда pen.dev (2026-11-16, по его выгрузке);
  дизайн с тех пор ведётся в Claude Design с теми же ID, и отметки «есть» у строк, добавленных позже, сделаны уже
  по его холсту;
- есть ли функция в приложении для Android;
- сделана ли она в окне Flutter (`desktop/`).

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

**Android:**

- **есть** — приложение для Android это умеет: по [описи функций](../ui/functions.md) и коду Kotlin.
- **нет** — на Android этого нет: функция только для компьютера (трей) или ещё не сделана.
- **—** — к Android не относится.

**Окно:**

- **есть** — окно Flutter это делает.
- **частично** — делает не всё. Чего нет — коротко в ячейке.
- **нет** — в окне этого нет.

Что окно умеет, описано в [desktop/README.md](../../desktop/README.md). Связь с демоном у окна пока есть только в
сборке разработки (`NIKO_DEV`).

Пути Kotlin даны от `app/src/main/java/io/niko/`. Вызовы демона:

- `boxdd` — `experimental/boxdd/desktop_service.proto` и `daemon/*.proto` форка ядра.
- `NikoService` — новый API: `shared/proto/niko/v1/niko_service.proto`, пока в нём `GetInfo` и `RouteTest`, отвечает
  демон `nikod` (этап 3, срез 1). Остальные вызовы матрицы в нём — план.

## Верхняя панель (D-NAV)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-NAV-01 | Разделы: Главная, Узлы, Проверка маршрута, Настройки (+ Админ, Журнал) | — | UI | есть | есть | частично: нет Узлов, Проверки маршрута, Настроек и Админа |
| D-NAV-02 | Режим: Прокси или VPN (TUN) | конфиг (mixed или tun) + `ManagedService.SetSystemProxyEnabled` | Kotlin: `fmt/ConfigBuilder.kt` | расходится: был в шапке; в Простом — плитка, в Расширенном — переключатель в карточке подключения | есть | частично: выбор на главной (переключатель под статусом, когда кнопка подключает профиль службы) и в настройках, `Connect` в выбранном режиме, работающее ядро запускается в нём заново; системный прокси режима прокси ставит служба от имени пользователя (по умолчанию на Windows) |
| D-NAV-03 | Вид интерфейса: Простой или Расширенный, переключатель в шапке (как в mdll). Простой: кнопка подключения, статус, 2–4 плитки, текущий узел. Расширенный: всё, плюс разделы «Соединения» и «Правила», JSON и ручная форма. Не сбрасывает подключение | — | UI (настройка приложения) | есть (Claude Design, вариант B) | нет | есть |

## Главная (D-MAIN)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-MAIN-01 | Заголовок «Трафик идёт через …»: узел и способ подключения | `SubscribeGroups` + профиль из `NikoService` | Kotlin (хранилище, BG-02) | есть | есть: выбранный профиль в списке и в уведомлении | нет |
| D-MAIN-02 | Статус: подключается, подключено, ошибка; время в сети | `SubscribeServiceStatus`, `GetStartedAt` | есть | есть | есть: без времени в сети | есть |
| D-MAIN-03 | Кнопка «Узнать IP выхода» | `NikoService.ExitAddress` | Go: `libcore/exitaddress.go` | есть | есть | нет |
| D-MAIN-04 | Подключить или отключить | `DesktopService.StartService(config)`, `ManagedService.StopService` | есть; конфиг — BG-01 | есть | есть | частично: нет профилей, запускает готовый JSON-файл |
| D-MAIN-05 | Схема маршрута: остальное, пресет, правила | конфиг + `NikoService` (пресеты) | Kotlin: `RoutePresets.kt`, `database/ProfileManager.kt` | есть | нет | нет |
| D-MAIN-06 | Показатели: входящий, исходящий, задержка, за сессию | `SubscribeStatus` (uplink, downlink, totals), `URLTest` | есть | есть | есть | частично: нет задержки |
| D-MAIN-07 | Список способов подключения узла | `NikoService` | Kotlin (хранилище, BG-02) | есть | есть | нет |
| D-MAIN-08 | Проверить пинг всех строк без подключения | `NikoService.TestProfiles` | Kotlin + Go: `bg/proto/TestInstance.kt`, `Libcore.urlTest` | есть | есть | нет |
| D-MAIN-09 | Добавить | — | UI, дальше D-ADD | есть | есть | нет |
| D-MAIN-10 | Узел над списком, переход в карточку | — | UI | есть | есть | нет |
| D-MAIN-11 | Строка: способ (naive, Hysteria2, REALITY, через ретранслятор, DNS-туннель) и выбор | `SelectOutbound` или перезапуск с новым конфигом | есть; ретрансляторы — Kotlin `raycontrol/RayRelays.kt` | есть | есть | нет |
| D-MAIN-12 | Пинг строки: число, цвет, полоски | `NikoService.TestProfiles` | см. D-MAIN-08 | есть | есть | нет |
| D-MAIN-13 | Срок и трафик узла, когда обновлён | `NikoService` | Kotlin: `raycontrol/RaySubscription.kt`, `group/SubscriptionHeaders.kt` | есть | есть | нет |
| D-MAIN-14 | Подтверждение «Узнать IP»: какой сервис спросят | `NikoService.ExitAddress` | Go: `libcore/exitaddress.go` (Cloudflare, ipinfo.io, ipwho.is, ipapi.co) | есть | есть | нет |
| D-MAIN-16 | Причина ошибки подключения (на главной) | `SubscribeServiceStatus` (FATAL, текст ошибки) | есть | расходится: текст баннера обрезан кнопкой «Почему не работает» | есть | есть |
| D-MAIN-17 | «Почему не работает» с главной: открывает D-WHY | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestRun.kt` | есть | нет: прогон — в меню узла и в журнале | нет |
| D-MAIN-18 | Проверка соединения: задержка, код HTTP, ошибка | `NikoService.TestConnection` | Kotlin + Go: `widget/StatsBar.kt`, `Libcore.urlTest` | есть | есть | нет |

## Добавление (D-ADD)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-ADD-02 | Поле «Вставить» | — | UI | есть | есть: импорт из буфера | нет |
| D-ADD-03 | Распознано: приглашение, подписка или ссылка профиля | `NikoService.Import` | Kotlin: `raycontrol/RayImport.kt`, `fmt/UniversalFmt.kt`, парсеры `fmt/*`, `group/RawUpdater.kt` | есть | есть | нет |
| D-ADD-04 | Из буфера | — | UI, дальше D-ADD-03 | есть | есть | нет |
| D-ADD-05 | QR из файла или снимка экрана | — | Kotlin: `ui/ScannerActivity.kt`, QR с камеры и из картинки | есть | есть: QR из картинки (`ui/ScannerActivity.kt`) | нет |
| D-ADD-06 | PIN приглашения, 6 цифр | `NikoService.Register` | Kotlin + Go: `raycontrol/RayImport.kt`, `libcore/rayapi` | есть | есть | нет |
| D-ADD-07 | Что будет добавлено | `NikoService.Import` (dry run) | Kotlin: `ui/ImportReview.kt` | расходится: подсказка внизу обрезана кнопкой | есть | нет |
| D-ADD-08 | Добавить | `NikoService.Register` / `Import` | Kotlin + Go | есть | есть | нет |
| D-ADD-09 | Импорт из файла: текст, JSON, WireGuard .zip, пакет устройства `rayctl` | `NikoService.Import` | Kotlin: `ui/ConfigurationFragment.kt`, `group/RawUpdater.kt`, `raycontrol/RayImport.kt` | есть | есть | нет |
| D-ADD-10 | Проверка импорта: что заменит весь конфиг, с ключами | `NikoService.Import` (dry run) | Kotlin: `ui/ImportReview.kt` | есть | есть | нет |
| D-ADD-11 | Ссылки-схемы подписок (sn, clash, sing-box, hiddify, v2rayng), профилей (ss, vmess, vless, trojan, naive+, hy2, tuic, anytls…) и приглашений rcinv; на компьютере — обработчик ссылок в системе | `NikoService.Import` | Kotlin: `ui/MainActivity.kt` (intent-фильтры), разбор — см. D-ADD-03; обработчика ссылок на компьютере нет | есть | есть | нет |

## Карточка узла (D-NODE)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-NODE-01 | Имя узла | `NikoService` | Kotlin: `raycontrol/RayNodes.kt` | есть | есть | нет |
| D-NODE-02 | Обновить | `NikoService.Refresh` | Kotlin + Go: `raycontrol/RaySubscription.kt`, `libcore/raysub` | есть | есть | нет |
| D-NODE-03 | Заметка узла | `NikoService` | Kotlin: `raycontrol/RayNodeCard.kt` | есть | есть | нет |
| D-NODE-04 | Срок доступа, трафик до сброса | `NikoService` | Kotlin: `raycontrol/RayNodes.kt` (`RayTraffic`: used, limit, since, reset) | есть | есть | нет |
| D-NODE-05 | Сведения: последнее и следующее обновление, подпись | `NikoService` | Kotlin: `raycontrol/RayNodeCard.kt` | есть | есть | нет |
| D-NODE-06 | Способы подключения узла | `NikoService` | Kotlin | есть | есть | нет |
| D-NODE-07 | Удалить узел: с подтверждением, что вернуть его можно только новым приглашением | `NikoService` | Kotlin (хранилище) | есть | есть | нет |
| D-NODE-08 | Свои устройства в сети узла (lan-v1) | `NikoService` | Kotlin: `raycontrol/RayLan.kt`, `ui/RayLanDialog.kt` | расходится: «2 из 3 разрешённых» — предела устройств узел не отдаёт | есть | нет |
| D-NODE-09 | UDP у способа naive в этой сети: QUIC, через TCP пока идёт проверка, через TCP потому что QUIC не проходит (naive-udp-v1) | `NikoService` (состояние ядра `NaiveDatagramState` и вердикт пробы сети) | Go: `libcore/naive_datagram.go`; проба и память по сетям — Kotlin: `raycontrol/RayDatagrams.kt` | есть | есть | нет |
| D-NODE-10 | Строки состояния карточки: обновления отклонены или не проходят N раз, путь прямой или через ретранслятор, резолвер DNS-туннеля, часы неверны | `NikoService` | Kotlin: `raycontrol/RayNodeCard.kt` | есть | есть | нет |
| D-NODE-11 | Проверить канал: прямой путь (носитель SPA, MTU UDP, квота), по способам — скорость, первый байт, зависание, потери и RTT UDP; пиры Yggdrasil | `NikoService.CheckChannel` | Kotlin + Go: `raycontrol/RayChannel.kt`, `libcore/rayprobe` | есть | есть | нет |
| D-NODE-12 | Сводка и подсказки сети: избегать, UDP заблокирован, замирание, MTU | `NikoService` | Kotlin: `raycontrol/RayChannel.kt`, `raycontrol/RayReport.kt` | есть | есть | нет |
| D-NODE-13 | Локальная сеть узла, две настройки: «Показывать это устройство моим», «Порты, открытые моим устройствам» | `NikoService` | Kotlin: `raycontrol/RayLan.kt`, `ui/RayLanDialog.kt` | есть | есть | нет |

## Обычная подписка (D-SUB)

Подписка провайдера по ссылке (base64, Clash YAML, sing-box JSON, SIP008), не raycontrol. В списке — отдельной
группой рядом с узлом; здесь серверы действительно бывают в разных городах.

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-SUB-01 | Группа подписки в списке: имя, серверы с пингом | `NikoService` | Kotlin (хранилище, BG-02) | есть | есть | нет |
| D-SUB-02 | Срок и трафик подписки (`subscription-userinfo`) | `NikoService` | Kotlin: `group/SubscriptionHeaders.kt` | есть | есть | нет |
| D-SUB-03 | Обновить подписку вручную | `NikoService.Refresh` | Kotlin: `group/GroupUpdater.kt`, `group/RawUpdater.kt` | есть | есть | нет |
| D-SUB-04 | Удалить подписку: в меню «⋯», с подтверждением | `NikoService` | Kotlin (хранилище) | есть | есть | нет |
| D-SUB-05 | Меню подписки «⋯» | — | UI | есть | есть: меню группы | нет |
| D-SUB-06 | Скопировать ссылку подписки | `NikoService` | UI; ссылка — в хранилище (BG-02) | есть | нет: только ссылка `sn://subscription` (D-SUB-09) | нет |
| D-SUB-07 | Обновить все подписки | `NikoService.Refresh` | Kotlin: `ui/GroupFragment.kt`, `group/GroupUpdater.kt` | есть | есть | нет |
| D-SUB-08 | Настройки подписки: ссылка, автообновление и интервал, только при подключении, User-Agent, HWID, дедупликация, принудительное разрешение | `NikoService` | Kotlin: `ui/GroupSettingsActivity.kt`, `group/RawUpdater.kt`, `bg/SubscriptionUpdater.kt` | есть | есть | нет |
| D-SUB-09 | Поделиться подпиской (QR, буфер), экспорт группы | `NikoService` | Kotlin: `ui/GroupFragment.kt`, `fmt/UniversalFmt.kt` | есть | есть | нет |

## Своя группа (D-GROUP)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-GROUP-01 | Своя группа: создать, имя, порядок, селектор, авто по наименьшей задержке, front и landing proxy | `NikoService` | Kotlin: `ui/GroupSettingsActivity.kt`, `database/ProxyGroup.kt`; в конфиге — `fmt/ConfigBuilder.kt` | есть | есть | нет |

## Проверка маршрута (D-CHECK)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-CHECK-02 | Поле адреса | — | UI | есть | есть | нет |
| D-CHECK-03 | Проверить | `NikoService.RouteTest` | Go: `libcore/routetest.go` | есть | есть | нет: есть только вызов `RouteTest` в клиенте окна |
| D-CHECK-04 | Результат | `NikoService.RouteTest` | Go | есть | есть | нет: есть только вызов `RouteTest` в клиенте окна |
| D-CHECK-05 | Путь: сайт → правило → выход | `NikoService.RouteTest` | Go | есть | есть | нет: есть только вызов `RouteTest` в клиенте окна |
| D-CHECK-06 | Почему: какой список и когда обновлён | `NikoService.RouteTest` + BG-09 | Go + Kotlin: `RuleSetFiles.kt` | есть | нет: виден только текст правила | нет |
| D-CHECK-07 | Недавние | — | UI | есть | нет | нет |

## Почему не работает (D-WHY)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-WHY-02 | Ошибка подключения | `SubscribeServiceStatus` (FATAL) | есть | есть | есть | нет |
| D-WHY-03 | Шаги проверки | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestRun.kt`, `RayTestFindings.kt` | есть | есть | нет |
| D-WHY-04 | Что сделать | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestFindings.kt` | есть | есть | нет |
| D-WHY-05 | Переключиться на другой способ | `SelectOutbound` | есть | есть | есть: выбором в списке (D-MAIN-11) | нет |
| D-WHY-06 | Обновить узел | `NikoService.Refresh` | см. D-NODE-02 | есть | есть: в меню узла (D-NODE-02) | нет |
| D-WHY-07 | Отчёт для админа | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestRun.kt` (журнал с run ID) | есть | есть | нет |
| D-WHY-08 | Последний прогон: что он нашёл | `NikoService.TestRun` | Kotlin: `raycontrol/RayTestRun.kt` (`lastFindings`) | есть | есть | нет |

## Состояния (D-STATE-…)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-ADD | Первый запуск: узлов и подписок нет, открыт экран добавления | BG-02 (пусто) | UI | есть («Добавление (первый запуск)») | нет | нет |
| D-STATE-SVC | Служба не установлена: установить или режим «Прокси» | `DesktopService.GetDaemonInfo` | проверка службы есть; установщика нет; режима «Прокси» без службы нет — ядро в worker'е без прав, этап 3, срез 16 (desktop-stage3.md, решение 9) | есть | — | частично: только баннер, нет установки и режима «Прокси» |
| D-STATE-QUOTA | Трафик узла исчерпан до сброса | `NikoService` | Kotlin: `raycontrol/RaySubscription.kt` (traffic-limit-v1) | есть | есть | нет |
| D-STATE-NET | Нет сети | `boxdd` platform interface | есть | есть | нет: только в итогах прогона (D-WHY) | нет |
| D-STATE-RELOAD | Настройка изменена: перезапустить ядро | `NikoService.Reload` (пересборка конфига, BG-01) | Kotlin: `ktx/Utils.kt` (`needReload`), `ui/MainActivity.kt` | есть | есть | нет |
| D-STATE-SWITCHED | Сторож сам сменил способ: с какого на какой и почему (+ D-TRAY-08) | `NikoService` (событие смены) | Kotlin: `raycontrol/RayWatchdog.kt` меняет молча, показа нет | есть | нет: смена видна только в журнале | нет |

## Админ-панель (D-ADMIN)

Видна только админу узла. Вызовы — `NikoService.ApiCall`, логика в Go: `libcore/rayapi`. Интерфейс Android —
`ui/RayAdminActivity.kt`.

| ID | Элемент | Вызов API узла | Макет | Android | Окно |
|---|---|---|---|---|---|
| D-ADMIN-01 | Имя узла | `GetStatus`, `SetNodeName` | есть | есть | нет |
| D-ADMIN-02 | Изменить заметку | `SetNodeNote` | есть | есть | нет |
| D-ADMIN-03 | Создать приглашение, PIN по выбору | `CreateInvite` | есть | есть | нет |
| D-ADMIN-04 | Сводка: участники, активные устройства, трафик, серверы | `GetStatus`, `GetMetrics` | расходится: «трафик за месяц» и «серверы 4 / 5» — уточнить у сервера | есть | нет |
| D-ADMIN-05 | Участники: устройства, трафик, срок, включён | `ListUsers`, `ListDevices`, `RenewUser`, `SetUserEnabled`, `DeleteUser` | есть | есть | нет |
| D-ADMIN-06 | Отозвать устройство | `RevokeDevice` | есть | есть | нет |
| D-ADMIN-07 | Службы участника | `ListServices`, `SetUserServices` | есть | есть | нет |
| D-ADMIN-08 | Обновление узла | `GetUpdateStatus`, `StartUpdate` | есть | есть | нет |
| D-ADMIN-09 | Зеркало приложения, сводки, доступ к API | `SetAppMirror`, `SetSummary`, `SetAdminAccess` | есть | есть | нет |
| D-ADMIN-10 | Состояние: это устройство, узел (хост, версия, id), счётчики, открытые порты SPA | `GetSelf`, `GetStatus`, `GetPorts` | есть | есть | нет |
| D-ADMIN-11 | Порты, метрики | `GetPorts`, `GetMetrics` | есть | есть | нет |
| D-ADMIN-12 | Аварийный SSH: адреса (CIDR) | `GetAdminAccess`, `SetAdminAccess` | есть | есть | нет |
| D-ADMIN-13 | Новый участник: имя, дни, ГБ, заметка, службы | `CreateUser`, `SetUserServices` | есть | есть | нет |
| D-ADMIN-14 | Ссылки для других приложений; новые учётные данные участника | `ExportUser`, `RenewUser` | есть | есть | нет |
| D-ADMIN-15 | Службы узла: список, создать, включить или выключить, удалить (последний SSH — с подтверждением) | `ListServices`, `CreateService`, `SetServiceEnabled`, `DeleteService` | есть | есть | нет |
| D-ADMIN-16 | Настройки службы | `SetServiceOption` | есть | есть | нет |
| D-ADMIN-17 | Смена ключей службы: по плану или сейчас | `RotateService` | есть | есть | нет |
| D-ADMIN-18 | Сеть: DNS-провайдер, проверка зон туннелей, ретрансляторы и SSH к ним | `GetDNS`, `SetDNSProvider`, `SyncDNS`, `RemoveDNS`, `CheckDNSZones`, `ListRelays`, `SetRelayAccess` | есть | есть | нет |
| D-ADMIN-19 | Журнал аудита | `ListAudit` | есть | есть | нет |
| D-ADMIN-20 | Проверка приложения: режим (выкл., учитывать, строго, только RKP), мин. версия, патч Android, кто не прошёл | новое: вызовы из спеки сервера app-attest-v1 | есть | нет | нет |
| D-ADMIN-21 | Обязательные правила узла: набор, путь (`block`, `node`, `relay`, `direct`), запасной путь | новое: вызовы из спеки сервера rules-v1 | есть | нет | нет |

## Трей (D-TRAY)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-TRAY-01 | Значок с состоянием | `SubscribeServiceStatus` | UI | есть | нет | нет |
| D-TRAY-02 | Меню: отключить, режим, способ подключения, открыть, выход | D-MAIN-04, D-NAV-02, D-MAIN-11 | UI | есть | нет | нет |
| D-TRAY-03 | Компактное окно | — | UI | есть | нет | нет |
| D-TRAY-04 | Кнопка и текущий способ | D-MAIN-04, D-MAIN-01 | UI | есть | нет | нет |
| D-TRAY-05 | Скорость и пинг | `SubscribeStatus` | есть | есть | нет | нет |
| D-TRAY-06 | Избранные | `NikoService` | нет | есть, помечено «позже»: функции нет, решает владелец | нет | нет |
| D-TRAY-07 | Открыть Niko | — | UI | есть | нет | нет |
| D-TRAY-08 | Уведомления: срок или трафик кончается, ошибка подключения | D-MAIN-13, D-MAIN-02 | UI | есть | нет | нет |

## Настройки (D-SET)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-SET-03 | Тема: как в системе, светлая, тёмная | — | UI | есть | есть | частично: нет выбора, только как в системе |
| D-SET-04 | Запускать вместе с системой | `boxdd` в режиме службы + автозапуск окна | есть; установщика нет | есть | есть: вместе с D-SET-05 (`BootReceiver.kt`) | нет |
| D-SET-05 | Подключаться при запуске | `NikoService` | Kotlin: `BootReceiver.kt`; на компьютере — служба | есть | есть | нет |
| D-SET-07 | Пресет «Россия»: заменён выбором пресета страны в правилах (D-RULES-05) | `NikoService` | Kotlin: `RoutePresets.kt`, `RuleSetFiles.kt` | расходится: выбор пресета теперь в «Правилах», а не в настройках | есть | нет |
| D-SET-08 | Реклама и трекеры: обычное правило «Блок» по `geosite:category-ads-all` в списке правил, отдельного переключателя и плитки нет (решение владельца) | `NikoService` | Kotlin: правило создаётся при первом запуске, `database/ProfileManager.kt` | есть | есть | нет |
| D-SET-10 | Режим эксперта: заменён видом «Расширенный» (D-NAV-03) | — | UI | расходится: отдельной настройки больше нет | нет | есть: как D-NAV-03 |
| D-SET-12 | О программе: версия, ядро, обновления (GitHub и узел, подпись minisign) | `DesktopService.InstallUpdate` + `NikoService` | Go: `libcore/update.go`, `minisign`; с узла — Kotlin `raycontrol/RayAppMirror.kt` | есть | есть | нет |
| D-SET-33 | Проверять обновления самому: отдельная настройка, по умолчанию выключена (решение владельца); включённая только сообщает о новой версии | `NikoService` + `DesktopService.InstallUpdate` | Go: `libcore/update.go` для проверки; расписание и настройка — новое в Kotlin и в службе | есть | нет | нет |
| D-SET-13 | Язык | `DesktopService.SetLocale` | есть | есть | есть: язык приложения в настройках системы | частично: нет выбора, язык системы |
| D-SET-14 | Сервис для IP выхода | `NikoService.ExitAddress` | Go: `libcore/exitaddress.go` | есть | нет: сервис выбирают в диалоге (D-MAIN-14) | нет |
| D-SET-15 | Корневые сертификаты naive: Chrome, Mozilla, системные | конфиг + `CRONET_WINDOWS_SYSTEM_ROOTS` | Go в ядре; настройка — Kotlin | есть | есть | нет |
| D-SET-16 | Локальная сеть напрямую | конфиг | Kotlin: `bypassLanInCore` в `fmt/ConfigBuilder.kt` | есть | есть | нет |
| D-SET-17 | Отчёты для админа узла: согласие | `NikoService` | Kotlin: `raycontrol/RayReport.kt` | есть | есть | нет |
| D-SET-18 | DNS: удалённый, прямой, FakeIP (эксперт) | конфиг | Kotlin: `fmt/ConfigBuilder.kt` | есть | есть | нет |
| D-SET-19 | Резервная копия, перенос с телефона | `NikoService` | Kotlin: `ui/BackupFragment.kt` | есть | есть | нет |
| D-SET-20 | Переключать способы узла автоматически | `NikoService` | Kotlin: `raycontrol/RayWatchdog.kt` | есть | есть | нет |
| D-SET-21 | Brutal по измеренной скорости; резолверы DNS-туннелей dnstt и MasterDnsVPN | конфиг | Kotlin: `raycontrol/RayBrutal.kt`, `fmt/NikoParams.kt` | есть | есть | нет |
| D-SET-22 | Стратегии доменов, DNS-маршрутизация, оптимистичный кэш | конфиг | Kotlin: `fmt/ConfigBuilder.kt` | есть | есть | нет |
| D-SET-23 | Реализация TUN, MTU | конфиг | Kotlin: `fmt/ConfigBuilder.kt`, `bg/VpnService.kt` | есть | есть | нет |
| D-SET-24 | Интервал скорости, статистика трафика профилей, показывать адрес | `SubscribeStatus` (интервал) + `NikoService` | Kotlin: `bg/proto/TrafficLooper.kt`, `ui/ConfigurationFragment.kt` | есть | есть | нет |
| D-SET-25 | Уровень журнала и размер буфера | конфиг + `NikoService` | Kotlin: `fmt/ConfigBuilder.kt`; буфер — `SagerNet.kt` (`Libcore.initCore`) | есть | есть | нет |
| D-SET-26 | Общий свой конфиг (JSON) | конфиг | Kotlin: `fmt/ConfigBuilder.kt` | есть | есть | нет |
| D-SET-27 | Вход: порт, пользователь, пароль, доступ из локальной сети; «без пароля в VPN» и «HTTP-прокси в VPN» — только телефон | конфиг | Kotlin: `fmt/ConfigBuilder.kt` | есть | есть | нет |
| D-SET-28 | Опыты: обход локальной сети в ядре, отказ QUIC поверх потоков, честное подключение, ICMP через узел, без местного ответа на ping, фрагментация TLS для обхода, сниффинг, разрешение адреса назначения, IPv6 | конфиг | Kotlin: `fmt/ConfigBuilder.kt` | есть | есть | нет |
| D-SET-29 | Yggdrasil: включить, пиры, через прокси, TLS как Chrome, входящие, порты, адрес устройства | конфиг | Kotlin + Go: `fmt/ConfigBuilder.kt`, `libcore/box_yggdrasil.go` | есть | есть | нет |
| D-SET-30 | URL проверки соединения и параллельность; Clash API; сброс соединений при смене сети и пробуждении; небезопасное; мин. TLS подписок | конфиг + `NikoService` | Kotlin: `fmt/ConfigBuilder.kt`, `bg/BaseService.kt`, `group/RawUpdater.kt` | есть | есть | нет |
| D-SET-31 | FAQ: что значат технические величины и термины (решение владельца) | — | нет | есть | нет: только ссылка на README | нет |
| D-SET-32 | Сброс настроек | `NikoService` | Kotlin: `ui/BackupFragment.kt` | есть | есть | нет |

## Экраны вида «Расширенный» (бывший режим эксперта)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-LOG-01 | Журнал ядра, очистка | `SubscribeLog`, `ClearLogs` | есть | есть | есть | есть |
| D-LOG-02 | Отчёты о падениях и нехватке памяти | `DesktopService` crash и OOM reports | есть | есть | есть: только падение приложения (`utils/CrashHandler.kt`) | есть |
| D-LOG-03 | Инструменты рядом с журналом: поведение NAT (сопоставление и фильтрация по RFC 4787, внешний адрес, задержка) через основной исходящий; в описи функций — D-TOOLS-01 | `StartSTUNTest` | есть (`common/stun` ядра) | есть | есть: `ui/StunActivity.kt` | есть |
| D-CONN-01 | Соединения: список, закрыть | `SubscribeConnections`, `CloseConnection`, `CloseAllConnections` | есть | есть | есть: в панели sing-box (D-TOOLS-02) | есть |
| D-RULES-01 | Редактор правил | `NikoService` | Kotlin: `ui/RouteSettingsActivity.kt`, `fmt/ConfigBuilder.kt` | есть | есть | нет |
| D-RULES-02 | Правило по программам (`process_name`, `process_path`) | конфиг | ядро умеет; на Android это список приложений | есть | есть: по пакетам приложений | частично: сборка (`Rule.programs`) и правило D-RULES-06; редактора правил нет (D-RULES-01) |
| D-RULES-03 | Наборы правил: версии, обновить, удалить, импорт; источник и интервал обновления | `NikoService` | Kotlin: `ui/AssetsActivity.kt`, `RuleSetFiles.kt` | есть | есть | нет |
| D-RULES-04 | Свой корневой сертификат из .pem, с предупреждением | `NikoService` | Kotlin: `ui/AssetsActivity.kt` | есть | есть | нет |
| D-RULES-05 | Пресет страны: один из «Нет, Россия, Китай, Иран» («ок» владельца 2026-11-16). Китай и Иран — по образцу «России»: заблокированное через узел, доступное только изнутри и адреса страны напрямую, из наборов с обновлением | `NikoService` | частично: в Kotlin `RoutePresets.kt` есть только «Россия». Китай и Иран — новое, из репозитория списков: Loyalsoldier и Chocolate4U входят туда исходными данными через его CI ([схема](../research/routing-apps.md) §2) | есть | есть: только «Россия» | нет |
| D-RULES-06 | Программы через Niko: «Все, Только выбранные, Кроме выбранных» и список — то же, что приложения на телефоне (M-RULES-01) | конфиг | новое: «Кроме выбранных» — правило по `process_path` «Напрямую» первым в списке; «Только выбранные» — такое же правило через узел, а остальное напрямую | есть | есть: приложения через VPN (M-RULES-01) | есть: Настройки → Программы, список — имя или полный путь в строке; в службе — `programsMode` и `programs` (API 8) |
| D-RULES-07 | Списки приложений из репозитория списков: включить, версия и дата, путь каждого имени сервиса ([схема](../research/routing-apps.md)) | `NikoService` + конфиг | новое: снимок в установщике, обновление через ноду с подписью | есть | нет | нет |
| D-RULES-08 | Обязательные правила от ноды (`enforce`), с замком «Задано подпиской»: `block`, `node`, `relay` (только наборы inside-only), `direct` — у администратора все возможности, клиент недоверенный (решение владельца 2026-11-16), все правила ноды выше своих правил пользователя, в том числе над приложением, которое он сам отправил через узел; справка объясняет цену «напрямую» и «через relay» ([схема](../research/routing-apps.md) §5) | `NikoService` | новое, вместе с сервером | есть | нет | нет |
| D-PROFILE-01 | Ручная форма профиля по протоколу; у naive переключатель «UDP: QUIC, через TCP если не проходит» | `NikoService` | Kotlin: 16 форм `ui/profile/*SettingsActivity.kt`, бины `fmt/*` | расходится: переключатель UDP нарисован в форме VLESS, он только у naive | есть | нет |
| D-PROFILE-02 | JSON профиля и конфига | `ApplicationService.CheckConfig`, `FormatConfig` | есть | есть | есть | нет |
| D-PROFILE-03 | Тонкая настройка подключения: фрагментация TLS, пиннинг сертификата, DNS-туннель, обход DPI для QUIC, стук портов | `NikoService` | Kotlin: `ui/profile/ProfileSettingsActivity.kt`, `fmt/PortKnocking.kt`, `fmt/CertificatePinning.kt`, `fmt/DnsTunnel.kt` | есть | есть | нет |
| D-PROFILE-04 | Цепочка прокси: добавить, порядок, проверка кольца | `NikoService` | Kotlin: `ui/profile/ChainSettingsActivity.kt`, `fmt/ConfigBuilder.kt` | есть | есть | нет |
| D-PROFILE-05 | Список профилей: поиск, порядок (исходный, по имени, по задержке), карточка с трафиком и задержкой | `NikoService` | Kotlin: `ui/ConfigurationFragment.kt` | есть | есть | нет |
| D-PROFILE-06 | Проверить группу (TCPing, URL-тест), очистить результаты и недоступные, удалить дубликаты, обнулить статистику | `NikoService.TestProfiles` + `NikoService` | Kotlin + Go: `ui/ConfigurationFragment.kt`, см. D-MAIN-08 | есть | есть | нет |
| D-PROFILE-07 | Поделиться профилем: QR, ссылка; конфиг в буфер или файл | `NikoService.Export` | Kotlin: `ui/ConfigurationFragment.kt`, ссылки — `fmt/*`, `fmt/UniversalFmt.kt` | есть | есть | нет |
| D-PROFILE-08 | Перенести профиль в группу, удалить с отменой, перетащить | `NikoService` | Kotlin: `ui/ConfigurationFragment.kt`, `database/ProfileManager.kt` | есть | есть | нет |

## Инструменты (D-TOOLS)

| ID | Элемент | Вызов демона | Где логика сейчас | Макет | Android | Окно |
|---|---|---|---|---|---|---|
| D-TOOLS-01 | Тип NAT (STUN): тот же инструмент, что D-LOG-03 | `StartSTUNTest` | см. D-LOG-03 | есть | есть: `ui/StunActivity.kt` | есть |
| D-TOOLS-02 | Панель sing-box (Clash API) | конфиг (`experimental.clash_api`) | есть: Clash API в ядре; панель на Android — `ui/WebviewFragment.kt` | есть | есть | нет |

## Фон службы, без экрана (BG)

Это работает, даже когда окно закрыто, поэтому живёт в службе.

| ID | Что | Где логика сейчас | Android | Окно |
|---|---|---|---|---|
| BG-01 | Сборка конфига ядра из профиля и настроек — главный перенос | Kotlin: `fmt/ConfigBuilder.kt` | есть | нет |
| BG-02 | Хранилище профилей и узлов | Kotlin: `database/` (Room) | есть | нет |
| BG-03 | Обновление подписок по времени: узлы raycontrol и обычные подписки | Kotlin: `bg/SubscriptionUpdater.kt`, `raycontrol/RaySubscription.kt`, `group/GroupUpdater.kt`, `group/RawUpdater.kt` | есть | нет |
| BG-04 | Сторож: проверки, DoH, перезагрузка подписки | Kotlin + Go: `raycontrol/RayWatchdog.kt`, `libcore/dohcheck.go` | есть | нет |
| BG-05 | Адаптивность канала, Brutal по измерению | Kotlin + Go: `raycontrol/RayChannel.kt`, `RayBrutal.kt`, `libcore/rayprobe`, `libcore/probe` | есть | нет |
| BG-06 | Отчёты для админа | Kotlin: `raycontrol/RayReport.kt` | есть | нет |
| BG-07 | DNS-туннели: выбор резолвера в каждой сети | Kotlin + Go: `raycontrol/RayDnsResolvers.kt`, `libcore/dnsprobe` | есть | нет |
| BG-08 | Yggdrasil: публичные пиры | Kotlin + Go: `raycontrol/RayYggPeers.kt`, `libcore/box_yggdrasil.go` | есть | нет |
| BG-09 | Наборы правил: загрузка и проверка | Kotlin + Go: `RuleSetFiles.kt`, `libcore/ruleset_check.go` | есть | нет |
| BG-10 | Секреты скрыты в журнале | Go: `libcore/redact.go`; в `boxdd` пока не подключено | есть | нет |
| BG-11 | Ключ устройства в защищённом хранилище | Kotlin: `raycontrol/DeviceKeyVault.kt` (Android Keystore); на Windows DPAPI — нет | есть | нет |
| BG-12 | Обновление приложения с узла | Kotlin + Go: `raycontrol/RayAppMirror.kt`, `libcore/raysub.go`; узел пока раздаёт только APK — нужен сервер | есть | нет |

## Протоколы под Windows

Ядро для Windows собирается с теми же тегами, что и для Android (`desktop.yml`): naive, knock, AmneziaWG, dnstt,
masterdns, Yggdrasil. Отличия:

- **naive:** `libcronet.dll` рядом с exe. Тесты под Windows проходят.
- **Slipstream:** отдельная программа. Для Android она собирается как `libslipstream.so`; сборки под Windows нет.
- **naive UDP через HTTP/3** (naive-udp-v1) — в ядре (`libcore/naive_datagram.go`), для обеих платформ.

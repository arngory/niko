# Опись старых экранов NekoBox: что есть и куда это уходит

Снято с кода `main` 2026-11-24 (d8652fe). Каждый перенос экрана на Compose сверяется с этой описью: старый экран удаляется, когда у каждой его строки есть место на новом или решение её снять; строка тогда помечается «перенесено» и номером PR.

**Как читать.**

- Пути в «Файл:строка»: `ui/…`, `proxy/…`, `widget/…`, `ktx/…`, `database/…` идут от `app/src/main/java/io/niko/`;
  `xml/…`, `menu/…`, `layout/…`, `values/…` идут от `app/src/main/res/`.
- «Что»: подпись так, как её видит пользователь (русская строка из `values-ru`, если она есть), затем ключ
  настройки или id пункта меню либо виджета в `код`.
- «Куда»: ID из [описи функций](functions.md) или [матрицы компьютера](../spec/desktop-requirements.md),
  либо артборд холста (`P-…`) из кода, где он уже назван. **GAP**: ни ID, ни макет этого не называют. **DEAD**: код
  показывает, что пункт не работает или до него нельзя добраться (причина в заметке). **СНЯТЬ**: работает, но
  владелец решил его убрать (roadmap.md, «Android до MVP»).
- Пункт считается покрытым, если текст ID его называет или явно включает (например, «ручная форма профиля по
  протоколу» включает каждое поле формы). Если ID называет только часть экрана (D-CHECK-02 называет «поле адреса»),
  то остальные поля этого экрана получают GAP.
- Пометка «уже в P-…» значит, что двойник на Compose уже есть и старый пункт можно снимать без потерь.

---

## 1. MainActivity: нижняя панель, её настройка и вопросы о ссылках

Нижняя панель показывается только на старых экранах и только при включённой настройке (`ui/MainActivity.kt:650-660`).
Главная, карточка узла, настройки и журнал её не показывают.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Кнопка подключения FAB (`fab`) | layout/layout_main.xml:93; ui/MainActivity.kt:329-333 | D-MAIN-04 | на главной уже есть: P-Simple и P-Advanced, кнопка ConnectButton |
| Полоса статуса: состояние, ↑ и ↓ скорость (`stats`, `tx`, `rx`, `status`) | layout/layout_main.xml:17-61; ui/MainActivity.kt:702, 772 | D-MAIN-02, D-MAIN-06 | на главной уже есть |
| Нажатие на полосу: проверка соединения (задержка, код HTTP, ошибка) | ui/MainActivity.kt:334 | D-MAIN-18 | на P-Simple и P-Advanced этого **ещё нет**: если снять панель, функция пропадёт |
| Долгое нажатие на полосу: «IP выхода», выбор сервиса, ответ | ui/MainActivity.kt:335-338; ui/ExitAddress.kt:31-80 | D-MAIN-03, D-MAIN-14 | на главной **ещё нет**: тот же риск |
| Настройка «Нижняя панель» (`showBottomBar`), Настройки › Журнал и вид | ui/settings/SettingsScreen.kt:461; database/DataStore.kt:165; ktx/Layouts.kt:26 | СНЯТЬ | уходит вместе с панелью; сама страница настроек уже на Compose |
| Snackbar «Ошибка VPN: …» | ui/MainActivity.kt:703 | D-MAIN-16 | |
| Snackbar «Нет разрешения VPN» | ui/MainActivity.kt:765-767 | M-STATE-PERM | |
| Запрос разрешения на уведомления (SDK 33+) | ui/MainActivity.kt:357-366 | M-STATE-PERM | |
| Snackbar «Строгий Частный DNS …» | ui/MainActivity.kt:743-748 | M-STATE-PRIVDNS | |
| Snackbar «Нужно перезапустить» с кнопкой «Применить» (приложения через VPN изменены) | ui/MainActivity.kt:798-806 | D-STATE-RELOAD | |
| Диалог предварительной версии при запуске (`preview_version_hint`) | ui/MainActivity.kt:368-374 | GAP | канал предварительных сборок есть в D-SET-12, этого предупреждения нет нигде |
| Ссылка rcinv1/2/3, rcdev1: «Добавить узел …?», затем PIN | ui/MainActivity.kt:410-433 | D-ADD-06, D-ADD-07, D-ADD-08 | |
| Ссылка подписки (sn://subscription, clash://, ссылки других клиентов): «Импорт подписки» | ui/MainActivity.kt:474-532 | D-ADD-11, D-ADD-07 | сначала открывает старый список групп (`nav_group`, :518) |
| Ссылка профиля: «Импорт профиля», с ключами, если профиль переписывает весь конфиг | ui/MainActivity.kt:539-568 | D-ADD-11, D-ADD-10 | после импорта открывает старый список профилей (:576) |
| Пакет QR-кодов от сканера, который переписывает весь конфиг (`ACTION_REVIEW`) | ui/MainActivity.kt:384-403 | D-ADD-10 | |
| «Нет плагина»: Play, F-Droid или «Скачать» | ui/MainActivity.kt:582-633 | GAP | M-SET-03 описывает только «доверять» и «отозвать»; про загрузку недостающего плагина нет ничего |
| Строка «Справка» на главной ведёт в README на GitHub (`nav_faq`) | ui/MainActivity.kt:682-685 | D-SET-31 | экраном это не является, это ссылка; FAQ ещё не сделан |

## 2. ConfigurationFragment: старый список профилей

Сюда ведут кнопка «+» на P-Advanced (`actions::profiles`, ui/home/HomeScreen.kt:315), «добавить» в P-Configs
(ui/MainActivity.kt:243-245), импорт ссылки (ui/MainActivity.kt:576) и «Закрыть» в панели sing-box.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Поиск (`action_search`): по имени, типу, адресу | menu/add_profile_menu.xml:9; ui/ConfigurationFragment.kt:235-245, 1377-1390 | D-PROFILE-05 | |
| Вкладки групп; при одной группе скрыты | layout/layout_group_list.xml:16; ui/ConfigurationFragment.kt:256-263, 1045-1047 | D-PROFILE-05 | уже в P-Configs (группы на листе) |
| Подзаголовок: трафик и срок подписки текущей группы | ui/ConfigurationFragment.kt:191-199 | D-SUB-02 | |
| Нажатие на заголовок: прокрутка к выбранному профилю или к началу | ui/ConfigurationFragment.kt:265-287 | GAP | мелочь удобства; на новом экране: arngory/niko#NNN (повторное нажатие на вкладку «Конфиги») |
| «Добавить профиль» › «Сканировать QR» (`action_scan_qr_code`) | menu/add_profile_menu.xml:21; ui/ConfigurationFragment.kt:434-436 | M-ADD-01 | |
| «Импорт из буфера» (`action_import_clipboard`): ссылки, приглашение raycontrol (буфер после него очищается), подписка | menu/add_profile_menu.xml:24; ui/ConfigurationFragment.kt:438-464 | D-ADD-04 | |
| «Импорт из файла» (`action_import_file`): текст, JSON, WireGuard .zip, пакет `rayctl` | menu/add_profile_menu.xml:27; ui/ConfigurationFragment.kt:329-376 | D-ADD-09 | |
| Вопрос перед импортом из буфера или файла, если профиль переписывает весь конфиг | ui/ConfigurationFragment.kt:412-430 | D-ADD-10 | |
| Вопрос «заменить узел» и PIN при импорте raycontrol | ui/ConfigurationFragment.kt:382-410 | D-ADD-06, D-ADD-07 | |
| «Вручную» › SOCKS, HTTP, Shadowsocks, VMess, VLESS, Trojan, Trojan-Go, Mieru, naive, Hysteria, TUIC, ShadowTLS, AnyTLS, SSH, WireGuard, «Пользовательская конфигурация», «Прокси-цепочка» (`action_new_*`) | menu/add_profile_menu.xml:29-81; ui/ConfigurationFragment.kt:470-538 | D-PROFILE-01 (цепочка: D-PROFILE-04, свой конфиг: D-PROFILE-02) | 17 пунктов; экран выбора протокола нужен в P-Add |
| «Обновить подписку» для текущей группы (`action_update_subscription`) | menu/add_profile_menu.xml:90; ui/ConfigurationFragment.kt:540-550 | D-SUB-03 | уже в P-Subs |
| «Очистить статистику трафика» (`action_clear_traffic_statistics`) | menu/add_profile_menu.xml:93; ui/ConfigurationFragment.kt:552-565 | D-PROFILE-06 | на новом экране: arngory/niko#NNN (⋮ группы › «Обнулить статистику») |
| «Удалить дубликаты», с подтверждением и списком до 20 имён (`action_remove_duplicate`) | menu/add_profile_menu.xml:96; ui/ConfigurationFragment.kt:622-672 | D-PROFILE-06 | на новом экране: arngory/niko#NNN (⋮ группы) |
| «TCP-пинг» группы (`action_connection_tcp_ping`) | menu/add_profile_menu.xml:99; ui/ConfigurationFragment.kt:674-676, 782-920 | D-PROFILE-06 | в P-Configs есть только URL-тест (ui/home/ConfigsTest.kt); на новом экране: arngory/niko#NNN (⋮ группы; ход — как у URL-теста, ui/home/ConfigsTest.kt) |
| «URL-тест» группы (`action_connection_url_test`) | menu/add_profile_menu.xml:102; ui/ConfigurationFragment.kt:678-680, 923-999 | D-PROFILE-06, D-MAIN-08 | уже в P-Configs |
| Диалог хода проверки: текущий профиль, N из M, «Свернуть» в уведомление, «Отмена» | layout/layout_progress_list.xml; ui/ConfigurationFragment.kt:685-778, 912-919 | D-PROFILE-06, M-SYS-03 | |
| «Очистить результаты» (`action_connection_test_clear_results`) | menu/add_profile_menu.xml:105; ui/ConfigurationFragment.kt:567-581 | D-PROFILE-06 | на новом экране: arngory/niko#NNN (⋮ группы) |
| «Удалить недоступные», с подтверждением (`action_connection_test_delete_unavailable`) | menu/add_profile_menu.xml:108; ui/ConfigurationFragment.kt:583-620 | D-PROFILE-06 | на новом экране: arngory/niko#NNN (⋮ группы) |
| «Сортировка»: «По источнику», «По имени», «По задержке» (`action_order_*`) | menu/add_profile_menu.xml:111-125; ui/ConfigurationFragment.kt:1209-1254 | D-PROFILE-05 | уже есть в P-SubSettings (ui/group/GroupForm.kt:20); на новом экране: arngory/niko#NNN (лист конфигов теперь показывает группу в её порядке, ui/home/ConfigsState.kt) |
| Нажатие на строку: выбрать профиль; при работающей службе — перезапуск | ui/ConfigurationFragment.kt:1608-1626 | D-MAIN-11 | уже в P-Configs |
| ТВ: нажатие на уже выбранный профиль включает или выключает службу | ui/ConfigurationFragment.kt:1627-1633 | M-SYS-05 | |
| Строка: имя, тип с цветом, трафик ↑↓, адрес (при «Показывать адрес»), отметка выбранного | ui/ConfigurationFragment.kt:1639-1672, 1593-1596 | D-PROFILE-05, D-SET-24 | |
| Состояние строки: «Доступно N мс», ошибка; нажатие на ошибку показывает полный текст | ui/ConfigurationFragment.kt:1674-1703 | D-PROFILE-05, D-MAIN-12 | полного текста ошибки нет ни в ID, ни в P-Configs; на новом экране: arngory/niko#NNN (нажатие на «не отвечает» — лист с полным текстом) |
| Кнопка «Изменить» (`edit`), недоступна у профиля, на котором работает служба | layout/layout_profile.xml:62; ui/ConfigurationFragment.kt:1705-1711, 1731-1737 | D-PROFILE-01 | в P-Configs редактор открывает долгое нажатие; на новом экране: arngory/niko#NNN (долгое нажатие › «Изменить», и у профиля, на котором работает служба) |
| Кнопка «Удалить» (`remove`) с отменой в snackbar | layout/layout_profile.xml:99; ui/ConfigurationFragment.kt:1713-1719, 1426-1443 | D-PROFILE-08 | на новом экране: arngory/niko#NNN (долгое нажатие › «Удалить», с «Отменить»; не у профиля, на котором работает служба) |
| Перетаскивание профиля (порядок); не работает, пока служба подключается | ui/ConfigurationFragment.kt:1272-1307, 1392-1418, 1169-1172 | D-PROFILE-08 | на новом экране: arngory/niko#NNN (долгое нажатие › «Выше» / «Ниже» вместо перетаскивания; только у группы в порядке «По источнику») |
| «Поделиться» › «QR-код» › «Стандарт» / «SN Link» | menu/profile_share_menu.xml:4-13; ui/ConfigurationFragment.kt:1800, 1802 | D-PROFILE-07 | нет у цепочки, у строк узла и у профиля NekoBox (:1721, 1767, 1726); на новом экране: arngory/niko#NNN (долгое нажатие › «Поделиться») |
| «Поделиться» › «Экспорт в буфер» › «Стандарт» / «SN Link» | menu/profile_share_menu.xml:16-25; ui/ConfigurationFragment.kt:1801, 1803 | D-PROFILE-07 | «Стандарт» скрыт, если у типа нет стандартной ссылки (:1745-1756); на новом экране: arngory/niko#NNN (долгое нажатие › «Поделиться») |
| «Поделиться» › «Конфигурация» › «Экспорт в буфер» / «Экспорт в файл» | menu/profile_share_menu.xml:28-38; ui/ConfigurationFragment.kt:1807-1814, 1827-1849 | D-PROFILE-07 | на новом экране: arngory/niko#NNN (долгое нажатие › «Поделиться») |

## 3. GroupFragment: старый список групп

Сюда ведут «Подписки» без листа (ui/home/HomeScreen.kt:94), «Узлы», когда узлов не один (ui/MainActivity.kt:143-150), и
импорт подписки по ссылке. P-Subs (ui/home/SubsList.kt) уже повторяет почти всё отсюда.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Обновить все подписки» (`action_update_all`), с подтверждением | menu/add_group_menu.xml:5; ui/GroupFragment.kt:119; ui/RayNodeActions.kt:82-98 | D-SUB-07 | уже в P-Subs |
| «Создать группу» (`action_new_group`) | menu/add_group_menu.xml:10; ui/GroupFragment.kt:115-117 | D-GROUP-01 | уже в P-Subs («+») |
| Нажатие на группу узла открывает карточку узла | ui/GroupFragment.kt:387-389 | D-NODE-01…13 (P-Node) | уже в P-Subs |
| Кнопка «Изменить» (`edit`) открывает настройки группы или подписки | layout/layout_group_item.xml:59; ui/GroupFragment.kt:395-399 | D-SUB-08, D-GROUP-01 | P-SubSettings уже на Compose |
| Кнопка «Обновить» (`group_update`): подписка или узел | layout/layout_group_item.xml:123; ui/GroupFragment.kt:401-403 | D-SUB-03, D-NODE-02 | уже в P-Subs |
| Полоса хода обновления подписки | layout/layout_group_item.xml:17; ui/GroupFragment.kt:424-451 | D-SUB-02 | |
| Трафик подписки (SIP008/OOC), `subscription-userinfo`, сообщение провайдера (`announce`) | ui/GroupFragment.kt:456-489 | D-SUB-02 | announce уже в P-Subs (ui/home/SubsList.kt:115) |
| Имя пользователя подписки (`group_user`) | layout/layout_group_item.xml:50; ui/GroupFragment.kt:491 | DEAD | `SubscriptionBean.username` нигде не заполняется (database/SubscriptionBean.java:33, 147), строка всегда пуста |
| Состояние: «N профилей», «обновлено М-Д», «пусто» | ui/GroupFragment.kt:493-523 | D-SUB-01 | |
| Строки карточки узла и пометка «админ» | ui/GroupFragment.kt:525-532 | D-NODE-10 | |
| Нажатие на строки, если узел требует более новый Niko: «О программе» с проверкой обновления | ui/GroupFragment.kt:533-537; ui/AboutFragment.kt:67 | D-NODE-09, D-SET-12 | client-min-v1 (ui/node/NodeState.kt:16) |
| Смахнуть группу: удалить с отменой (не «без группы» и не во время обновления) | ui/GroupFragment.kt:68-92, 224-237 | D-SUB-04 | в P-Subs удаление идёт через меню с подтверждением |
| Перетащить группу: порядок групп (`userOrder`) | ui/GroupFragment.kt:78-108, 192-217 | GAP | в P-Subs порядок не меняется (ui/home/SubsList.kt), ни один ID не называет порядок групп; на новом экране: arngory/niko#NNN (⋮ группы › «Выше» / «Ниже» вместо перетаскивания) |
| ⋯ › «Управление нодой» (`action_ray_admin`), только админу | menu/group_action_menu.xml:4; ui/GroupFragment.kt:330-333, 417 | D-ADMIN-* | уже в P-Subs |
| ⋯ › «Локальная сеть» (`action_ray_lan`), если узел её даёт | menu/group_action_menu.xml:8; ui/GroupFragment.kt:335, 419 | D-NODE-08, D-NODE-13 | уже в P-Subs |
| ⋯ › «Поделиться подпиской» › «Экспорт в буфер» (`action_universal_clipboard`) | menu/group_action_menu.xml:16; ui/GroupFragment.kt:343-345 | D-SUB-06, D-SUB-09 | только у подписки (:411-413); уже в P-Subs |
| ⋯ › «Поделиться подпиской» › «QR-код» (`action_universal_qr`) | menu/group_action_menu.xml:19; ui/GroupFragment.kt:337-341 | D-SUB-09 | уже в P-Subs |
| ⋯ › «Экспортировать» › «Экспорт в буфер» (`action_export_clipboard`) | menu/group_action_menu.xml:28; ui/GroupFragment.kt:347-356 | D-SUB-09 | у группы узла скрыт (:415); уже в P-Subs |
| ⋯ › «Экспортировать» › «Экспорт в файл» (`action_export_file`) | menu/group_action_menu.xml:31; ui/GroupFragment.kt:358-360, 126-150 | D-SUB-09 | уже в P-Subs |
| ⋯ › «Очистить» (`action_clear`): удалить профили группы, с подтверждением | menu/group_action_menu.xml:36; ui/GroupFragment.kt:362-372 | D-SUB-05 | своего ID нет, это пункт меню D-SUB-05; уже в P-Subs (`SubAction.CLEAR`) |

## 4. ProfileSettingsActivity: общее для всех редакторов профиля

**Перенесено** в arngory/niko#155 (`ui/profile/ProfileActivity.kt`, `ProfileScreen.kt`, `ProfileForm.kt`, P-Profile): каждая строка ниже — на новом редакторе или снята как DEAD. «Применить» — кнопка «Сохранить» внизу, «Переместить» и «Удалить» — в ⋮, ярлык — строка внизу, «Тонкая настройка» — страница редактора (пиннинг, DNS-туннель и QUIC — её страницы, стук портов — на ней), пароли — точками с «глазом». JSON исходящего и конфига — строки «Подключения»; у нового профиля они теперь работают: JSON сохраняется вместе с ним. Отступления от макета — в описании PR.

Меню `menu/profile_config_menu.xml` и диалог «Настройки подключения» (`layout/layout_connection_tweaks.xml`).

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Заголовок «Настройки профиля», ✕ слева | ui/profile/ProfileSettingsActivity.kt:100-105 | D-PROFILE-01 | |
| «Применить» ✓ (`action_apply`): сохранить и выйти; профиль, на котором работает служба, её останавливает | menu/profile_config_menu.xml:11; ui/profile/ProfileSettingsActivity.kt:144-170, 459-464 | D-PROFILE-01 | |
| «Удалить» (`action_delete`), с подтверждением; у нового профиля просто закрывает | menu/profile_config_menu.xml:5; ui/profile/ProfileSettingsActivity.kt:70-81, 443-457 | D-PROFILE-08 | |
| «Есть несохранённые изменения. Сохранить?»: да, нет, отмена | ui/profile/ProfileSettingsActivity.kt:53-66, 97-99 | D-PROFILE-01 | поведение формы, в ID не названо |
| «Создать ярлык» (`action_create_shortcut`), только у существующего профиля | menu/profile_config_menu.xml:17; ui/profile/ProfileSettingsActivity.kt:350-354, 503-510 | M-PROFILE-01 | |
| «Переместить» (`action_move`) в другую свою группу; только своя группа и только если их больше одной | menu/profile_config_menu.xml:21; ui/profile/ProfileSettingsActivity.kt:343-349, 512-548 | D-PROFILE-08 | |
| «Пользовательский исходящий JSON» (`action_custom_outbound_json`) | menu/profile_config_menu.xml:28; ui/profile/ProfileSettingsActivity.kt:358, 471-485 | D-PROFILE-02 | у **нового** профиля ничего не делает: `proxyEntity` = null (:92, 472) |
| «Пользовательская конфигурация JSON» (`action_custom_config_json`) | menu/profile_config_menu.xml:32; ui/profile/ProfileSettingsActivity.kt:359, 487-501 | D-PROFILE-02 | у нового профиля тоже ничего не делает (:488) |
| «Настройки подключения» (`action_connection_tweaks`); у цепочки скрыт | menu/profile_config_menu.xml:24; ui/profile/ProfileSettingsActivity.kt:357, 466-469 | D-PROFILE-03 | |
| Тонкая настройка › «Фрагментация TLS»: выкл., по TLS-записям, по TCP-сегментам (`serverTlsFragment`) | layout/layout_connection_tweaks.xml:22-42; ui/profile/ProfileSettingsActivity.kt:228-234, 323-327 | D-PROFILE-03 | |
| Тонкая настройка › «Пиннинг сертификата» (`serverCertPins`), с проверкой | layout/layout_connection_tweaks.xml:65; ui/profile/ProfileSettingsActivity.kt:239, 281-287 | D-PROFILE-03 | |
| Тонкая настройка › «DNS-туннель»: движок dnstt или MasterDnsVPN (`serverDnsTunnel`) | layout/layout_connection_tweaks.xml:87-103; ui/profile/ProfileSettingsActivity.kt:250-257 | D-PROFILE-03 | |
| Тонкая настройка › резолвер туннеля (`serverDnsTunnelResolver`) | layout/layout_connection_tweaks.xml:112 | D-PROFILE-03 | |
| Тонкая настройка › домен туннеля (`serverDnsTunnelDomain`), с проверкой | layout/layout_connection_tweaks.xml:125; ui/profile/ProfileSettingsActivity.kt:300-304 | D-PROFILE-03 | |
| Тонкая настройка › ключ туннеля (`serverDnsTunnelKey`) | layout/layout_connection_tweaks.xml:138 | D-PROFILE-03 | |
| Тонкая настройка › «Шифрование MasterDnsVPN» 0–5 (`serverDnsTunnelMethod`), только у MasterDnsVPN | layout/layout_connection_tweaks.xml:146-152; ui/profile/ProfileSettingsActivity.kt:251 | D-PROFILE-03 | |
| Тонкая настройка › «Сервер MasterDnsVPN в режиме TCP» (`serverDnsTunnelFixedUpstream`), только у MasterDnsVPN | layout/layout_connection_tweaks.xml:159; ui/profile/ProfileSettingsActivity.kt:252 | D-PROFILE-03 | |
| Туннель slipstream из ссылки: поля видны, но заблокированы | ui/profile/ProfileSettingsActivity.kt:258-267 | D-PROFILE-03 | |
| Тонкая настройка › «UDP (QUIC): обход DPI» › «Фейковые пакеты перед первым (0–8)» (`serverUdpPrelude`) | layout/layout_connection_tweaks.xml:184; ui/profile/ProfileSettingsActivity.kt:318 | D-PROFILE-03 | «обход DPI для QUIC» |
| Тонкая настройка › «Фейки как QUIC с этим именем сервера» (`serverUdpPreludeSni`), с проверкой | layout/layout_connection_tweaks.xml:196; ui/profile/ProfileSettingsActivity.kt:305-309 | D-PROFILE-03 | |
| Тонкая настройка › «TTL фейков» (`serverUdpPreludeTtl`) | layout/layout_connection_tweaks.xml:208 | D-PROFILE-03 | |
| Тонкая настройка › «Отправлять второй QUIC Initial первым» (`serverUdpReorderInitial`) | layout/layout_connection_tweaks.xml:215 | D-PROFILE-03 | |
| Тонкая настройка › стук портов: последовательность (`serverKnockSequence`), с проверкой | layout/layout_connection_tweaks.xml:240; ui/profile/ProfileSettingsActivity.kt:274-280 | D-PROFILE-03 | |
| Тонкая настройка › «Пауза между стуками, мс» (`serverKnockInterval`, 10–10000) | layout/layout_connection_tweaks.xml:253; ui/profile/ProfileSettingsActivity.kt:329-330 | D-PROFILE-03 | |
| Тонкая настройка › «Стучать повторно через, секунд» (`serverKnockTtl`, 1–3600) | layout/layout_connection_tweaks.xml:265; ui/profile/ProfileSettingsActivity.kt:331-332 | D-PROFILE-03 | |
| Тонкая настройка › «Перестучать, если UDP-сессия без ответа» (`serverKnockSilence`, 0–3600) | layout/layout_connection_tweaks.xml:277; ui/profile/ProfileSettingsActivity.kt:333-334 | D-PROFILE-03 | |
| Пароли в списке показаны точками (`PasswordSummaryProvider`) | ui/profile/ProfileSettingsActivity.kt:562-573 | D-PROFILE-01 | |
| Флаг «профиль из подписки» (`EXTRA_IS_SUBSCRIPTION`) | ui/profile/ProfileSettingsActivity.kt:93, 109 | DEAD | записывается и нигде не читается: профиль подписки правится как свой |

## 5. Редакторы по типам

**Перенесено** в arngory/niko#155 (`ui/profile/ProfileSpecs.kt`: поля каждого типа, один редактор P-Profile): каждая строка ниже, цепочка (5.14) тоже, — на новом редакторе или снята как DEAD. Поля пишутся в те же поля бинов, с теми же значениями по умолчанию и тем же разбором, что в старых экранах; показ и скрытие — те же. Мультиплекс V2Ray — своя страница из «Подключения»; звенья цепочки перетаскиваются за ручку, × убирает звено, нажатие заменяет его. Отступления от макета — в описании PR.

Все поля ниже входят в D-PROFILE-01 («ручная форма профиля по протоколу»). Заметки говорят, когда поле видно.

### 5.1 SOCKS (`SocksSettingsActivity`, xml/socks_preferences.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`profileName`) | xml/socks_preferences.xml:5 | D-PROFILE-01 | |
| «Версия»: SOCKS4, SOCKS4A, SOCKS5 (`serverProtocol`) | xml/socks_preferences.xml:15 | D-PROFILE-01 | |
| «Сервер» (`serverAddress`) | xml/socks_preferences.xml:21 | D-PROFILE-01 | |
| «Удалённый порт» (`serverPort`) | xml/socks_preferences.xml:26 | D-PROFILE-01 | |
| «Имя пользователя (необязательно)» (`serverUsername`) | xml/socks_preferences.xml:31 | D-PROFILE-01 | |
| «Пароль (необязательно)» (`serverPassword`), только у SOCKS5 | xml/socks_preferences.xml:37; ui/profile/SocksSettingsActivity.kt:53-61 | D-PROFILE-01 | |
| «UDP over TCP» (`sUoT`) | xml/socks_preferences.xml:43 | D-PROFILE-01 | |

### 5.2 HTTP, VMess, VLESS, Trojan (`StandardV2RaySettingsActivity`, xml/standard_v2ray_preferences.xml)

HTTP, VMess и Trojan различаются классом (ui/profile/HttpSettingsActivity.kt:5, VMessSettingsActivity.kt:5,
TrojanSettingsActivity.kt:5), VLESS — флагом `vless` (VMessSettingsActivity.kt:8-10).

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`name`) | xml/standard_v2ray_preferences.xml:5 | D-PROFILE-01 | |
| «Сервер» (`serverAddress`) | :13 | D-PROFILE-01 | |
| «Удалённый порт» (`serverPort`) | :18 | D-PROFILE-01 | |
| «Имя пользователя (необязательно)» (`username`), только HTTP | :23; ui/profile/StandardV2RaySettingsActivity.kt:116 | D-PROFILE-01 | |
| «Пароль (необязательно)» (`password`), только HTTP | :29; StandardV2RaySettingsActivity.kt:117 | D-PROFILE-01 | |
| «Логин пользователя» UUID (`uuid`), не у HTTP; у Trojan называется «Пароль» | :34; StandardV2RaySettingsActivity.kt:111, 119-121 | D-PROFILE-01 | |
| «Изменить ID» alterId (`alterId`), только VMess | :38; StandardV2RaySettingsActivity.kt:113 | D-PROFILE-01 | |
| «Шифрование» (`encryption`): у VMess это шифрование, у VLESS — «flow» XTLS | :45; StandardV2RaySettingsActivity.kt:114, 123-134 | D-PROFILE-01 | |
| «Шифрование VLESS» (`vlessEncryption`), только VLESS | :51; StandardV2RaySettingsActivity.kt:115 | D-PROFILE-01 | |
| «Кодирование пакетов»: none, packet, xudp (`packetEncoding`), VMess и VLESS | :58; StandardV2RaySettingsActivity.kt:112 | D-PROFILE-01 | |
| «Сеть»: tcp, ws, http, quic, grpc, httpupgrade, xhttp (`type`), не у HTTP | :65; StandardV2RaySettingsActivity.kt:110, 138-145 | D-PROFILE-01 | |
| Хост (`host`): «HTTP-хост», «Хост WebSocket», «Хост HTTPUpgrade», по сети | :70; StandardV2RaySettingsActivity.kt:157-204 | D-PROFILE-01 | виден у http, ws, httpupgrade, xhttp |
| Путь (`path`): «HTTP-путь», путь WS, «имя сервиса gRPC», путь HTTPUpgrade | :75; StandardV2RaySettingsActivity.kt:157-204 | D-PROFILE-01 | у tcp и quic скрыт |
| «Шифрование транспортного уровня»: none, tls (`security`) | :82; StandardV2RaySettingsActivity.kt:147-154, 206-211 | D-PROFILE-01 | |
| XHTTP › «Режим»: auto, packet-up, stream-up, stream-one (`xhttpMode`) | :95 | D-PROFILE-01 | только у xhttp |
| XHTTP › «Extra (JSON)» (`xhttpExtra`) | :101 | D-PROFILE-01 | только у xhttp |
| WebSocket › «Максимальный объём данных» (`wsMaxEarlyData`) | :112 | D-PROFILE-01 | только у ws |
| WebSocket › «Имя заголовка ранних данных» (`earlyDataHeaderName`) | :118 | D-PROFILE-01 | только у ws |
| Безопасность › «SNI» (`sni`) | :128 | D-PROFILE-01 | только при tls |
| Безопасность › ALPN (`alpn`) | :133 | D-PROFILE-01 | только при tls |
| Безопасность › «Сертификаты» (`certificates`) | :138 | D-PROFILE-01 | только при tls |
| Безопасность › «Разрешить небезопасное подключение» (`allowInsecure`) | :143 | D-PROFILE-01 | только при tls |
| Защита TLS › «Отпечаток uTLS» (`utlsFingerprint`) | :156 | D-PROFILE-01 | только при tls |
| Защита TLS › «Reality Public Key» (`realityPubKey`) | :161 | D-PROFILE-01 | только при tls |
| Защита TLS › «Reality ShortId» (`realityShortId`) | :166 | D-PROFILE-01 | только при tls |
| Защита TLS › «Обмен ключами REALITY»: авто, гибридный, классический (`realityKeyShare`) | :174 | D-PROFILE-01 | только при tls |
| Защита TLS › «Ключ ML-DSA-65 для REALITY» (`realityMldsa65Verify`) | :179 | D-PROFILE-01 | только при tls |
| Мультиплекс › «Включить мультиплексор» (`enableMux`) | :189 | D-PROFILE-01 | |
| Мультиплекс › протокол: h2mux, smux, yamux (`muxType`) | :196 | D-PROFILE-01 | |
| Мультиплекс › «Число одновременных подключений Mux» (`muxConcurrency`) | :202 | D-PROFILE-01 | |
| Мультиплекс › «Отступ» (`muxPadding`) | :207 | D-PROFILE-01 | |
| ECH › «Включить» (`enableECH`) | :216 | D-PROFILE-01 | только при tls |
| ECH › «Конфигурация ECH» (`echConfig`) | :220 | D-PROFILE-01 | только при tls |

### 5.3 Shadowsocks (xml/shadowsocks_preferences.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`name`) | xml/shadowsocks_preferences.xml:5 | D-PROFILE-01 | |
| «Сервер» (`serverAddress`) | :12 | D-PROFILE-01 | |
| «Удалённый порт» (`serverPort`) | :17 | D-PROFILE-01 | |
| «Метод шифрования»: 18 вариантов, от 2022-blake3 до xchacha20 (`method`) | :24 | D-PROFILE-01 | |
| «Пароль» (`password`) | :30 | D-PROFILE-01 | |
| Плагин: нет, obfs-local, v2ray-plugin (`pluginName`) | :40; ui/profile/ShadowsocksSettingsActivity.kt:33-42 | D-PROFILE-01 | |
| «Настроить…» — строка плагина (`pluginConfig`) | :45 | D-PROFILE-01 | |
| «UDP over TCP» (`sUoT`) | :52 | D-PROFILE-01 | |

### 5.4 Trojan-Go (xml/trojan_go_preferences.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`profileName`) | xml/trojan_go_preferences.xml:5 | D-PROFILE-01 | |
| «Сервер» (`serverAddress`) | :13 | D-PROFILE-01 | |
| «Удалённый порт» (`serverPort`) | :18 | D-PROFILE-01 | |
| «Пароль» (`serverPassword`) | :24 | D-PROFILE-01 | |
| «SNI» (`serverSNI`) | :28 | D-PROFILE-01 | |
| «Разрешить небезопасное подключение» (`serverAllowInsecure`) | :33 | D-PROFILE-01 | |
| «Включить мультиплексор» Trojan-Go (`trojanGoMux`) | :37 | D-PROFILE-01 | |
| «Сеть»: NONE, WEBSOCKET (`serverNetwork`) | :45; ui/profile/TrojanGoSettingsActivity.kt:87-97, 106-115 | D-PROFILE-01 | |
| «Шифрование»: NONE, SHADOWSOCKS (`serverEncryption`) | :52; TrojanGoSettingsActivity.kt:98-103, 117-130 | D-PROFILE-01 | |
| WebSocket › «Хост WebSocket» (`serverHost`) | :62 | D-PROFILE-01 | только у WEBSOCKET |
| WebSocket › «Путь к WebSocket» (`serverPath`) | :67 | D-PROFILE-01 | только у WEBSOCKET |
| Shadowsocks › «Метод шифрования»: AES-128-GCM, AES-256-GCM, CHACHA20-IETF-POLY1305 (`serverMethod`) | :81 | D-PROFILE-01 | только у SHADOWSOCKS |
| Shadowsocks › «Пароль» (`serverPassword1`) | :87 | D-PROFILE-01 | только у SHADOWSOCKS |

### 5.5 Mieru (xml/mieru_preferences.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`profileName`) | xml/mieru_preferences.xml:5 | D-PROFILE-01 | |
| «Сервер» (`serverAddress`) | :13 | D-PROFILE-01 | |
| «Удалённый порт» (`serverPort`) | :18 | D-PROFILE-01 | |
| «Протокол»: TCP, UDP (`serverProtocol`) | :26 | D-PROFILE-01 | |
| «Имя пользователя» (`serverUsername`) | :31 | D-PROFILE-01 | |
| «Пароль» (`serverPassword`) | :37 | D-PROFILE-01 | |
| «MTU» (`serverMTU`), только у UDP | :41; ui/profile/MieruSettingsActivity.kt:85-91 | D-PROFILE-01 | |
| «Мультиплексирование»: по умолчанию, выкл., низкое, среднее, высокое (`mieruMultiplexing`) | :49 | D-PROFILE-01 | |
| «Рукопожатие»: по умолчанию, обычное, без ожидания (`mieruHandshake`) | :57 | D-PROFILE-01 | |
| «Шаблон трафика» (`mieruTrafficPattern`) | :63 | D-PROFILE-01 | |

### 5.6 naive (xml/naive_preferences.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`profileName`) | xml/naive_preferences.xml:6 | D-PROFILE-01 | |
| «Сервер» (`serverAddress`) | :14 | D-PROFILE-01 | |
| «Удалённый порт» (`serverPort`) | :19 | D-PROFILE-01 | |
| «Имя пользователя (необязательно)» (`serverUsername`) | :24 | D-PROFILE-01 | |
| «Пароль (необязательно)» (`serverPassword`) | :30 | D-PROFILE-01 | |
| «Протокол»: HTTPS, QUIC (`serverProtocol`) | :38 | D-PROFILE-01 | |
| «Дополнительные заголовки» (`serverHeaders`) | :43 | D-PROFILE-01 | |
| «SNI» (`serverSNI`) | :48 | D-PROFILE-01 | |
| «Сертификаты» (`serverCertificates`) | :53 | D-PROFILE-01 | |
| «Небезопасный параллелизм» (`serverInsecureConcurrency`) | :59 | D-PROFILE-01 | |
| «UDP: QUIC, через TCP если не проходит» (`sUoT`) | :66 | D-PROFILE-01 | в матрице переключатель нарисован в форме VLESS, а он только у naive (spec/desktop-requirements.md, строка D-PROFILE-01) |
| Скрытое: имя профиля «喵要打开隐藏功能» или «喵要关闭隐藏功能» включает или выключает `isExpert` | ui/profile/NaiveSettingsActivity.kt:60-67 | DEAD | `isExpert` нигде не читается (ktx/Utils.kt:277, database/DataStore.kt:177) |

### 5.7 Hysteria (xml/hysteria_preferences.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`profileName`) | xml/hysteria_preferences.xml:5 | D-PROFILE-01 | |
| «Версия протокола»: 1, 2 (`protocolVersion`) | :14; ui/profile/HysteriaSettingsActivity.kt:80-116 | D-PROFILE-01 | |
| «Сервер» (`serverAddress`) | :22 | D-PROFILE-01 | |
| «Удалённый порт» (`serverPorts`): порт или диапазоны | :27 | D-PROFILE-01 | |
| «Обфускация пароля» (`serverObfs`) | :33 | D-PROFILE-01 | |
| «Тип аутентификации»: отключено, STRING, BASE64 (`serverAuthType`), только v1 | :40; HysteriaSettingsActivity.kt:69-75, 84 | D-PROFILE-01 | |
| «Полезные данные аутентификации» (`serverPassword`); у v2 называется «Пароль» | :46; HysteriaSettingsActivity.kt:95, 109 | D-PROFILE-01 | |
| «Протокол»: UDP, FakeTCP, WeChat Video (`serverProtocol`), только v1 | :53; HysteriaSettingsActivity.kt:85 | D-PROFILE-01 | `defaultValue="https"` не входит в список |
| «SNI» (`serverSNI`) | :58 | D-PROFILE-01 | |
| ALPN (`serverALPN`), только v1 | :63; HysteriaSettingsActivity.kt:86 | D-PROFILE-01 | |
| «Сертификаты» (`serverCertificates`) | :68 | D-PROFILE-01 | |
| «Разрешить небезопасное подключение» (`serverAllowInsecure`) | :73 | D-PROFILE-01 | |
| Скорость отдачи, Мбит/с (`serverUploadSpeed`) | :78 | D-PROFILE-01 | по-русски она и скорость приёма подписаны одинаково: «Максимальная скорость загрузки» (`hysteria_upload_mbps`) |
| Скорость приёма, Мбит/с (`serverDownloadSpeed`) | :83 | D-PROFILE-01 | |
| «Окно приёма потока QUIC» (`serverStreamReceiveWindow`), только v1 | :88 | D-PROFILE-01 | |
| «Окно получения соединения QUIC» (`serverConnectionReceiveWindow`), только v1 | :93 | D-PROFILE-01 | |
| «Отключить обнаружение MTU пути» (`serverDisableMtuDiscovery`), только v1 | :98 | D-PROFILE-01 | |
| «Интервал переключения портов, секунд» (`hopInterval`) | :101 | D-PROFILE-01 | |
| «Макс. интервал смены порта, секунд (0 — фиксированный)» (`hopIntervalMax`) | :106 | D-PROFILE-01 | |

### 5.8 SSH (xml/ssh_preferences.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`profileName`) | xml/ssh_preferences.xml:5 | D-PROFILE-01 | |
| «Сервер» (`serverAddress`) | :13 | D-PROFILE-01 | |
| «Удалённый порт» (`serverPort`) | :18 | D-PROFILE-01 | |
| «Имя пользователя» (`serverUsername`) | :23 | D-PROFILE-01 | |
| «Тип аутентификации»: без, пароль, открытый ключ (`serverAuthType`) | :31; ui/profile/SSHSettingsActivity.kt:64-74 | D-PROFILE-01 | |
| «Пароль» (`serverPassword`), только при пароле | :38 | D-PROFILE-01 | |
| «Закрытый ключ» (`serverPrivateKey`), только при ключе | :43 | D-PROFILE-01 | |
| «Парольная фраза закрытого ключа» (`serverPassword1`), только при ключе | :50 | D-PROFILE-01 | |
| «Открытый ключ» хоста (`serverCertificates`) | :55 | D-PROFILE-01 | |

### 5.9 WireGuard (xml/wireguard_preferences.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`name`) | xml/wireguard_preferences.xml:5 | D-PROFILE-01 | |
| «Сервер» (`serverAddress`) | :12 | D-PROFILE-01 | |
| «Удалённый порт» (`serverPort`) | :17 | D-PROFILE-01 | |
| «Частный адрес» (`localAddress`) | :22 | D-PROFILE-01 | |
| «Закрытый ключ» (`privateKey`) | :27 | D-PROFILE-01 | |
| «Открытый ключ сервера» (`peerPublicKey`) | :32 | D-PROFILE-01 | |
| «Пароль сервера», PSK (`peerPreSharedKey`) | :38 | D-PROFILE-01 | |
| «MTU» (`mtu`, по умолчанию 1420) | :43 | D-PROFILE-01 | |
| «Reserved» (`reserved`) | :48 | D-PROFILE-01 | |
| «Persistent keepalive, секунд» (`persistentKeepalive`) | :54 | D-PROFILE-01 | |
| AmneziaWG › «Параметры AmneziaWG» (`amneziaWG`), моноширинный ввод | :63; ui/profile/WireGuardSettingsActivity.kt:51-52 | D-PROFILE-01 | |

### 5.10 TUIC (xml/tuic_preferences.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`profileName`) | xml/tuic_preferences.xml:5 | D-PROFILE-01 | |
| «Сервер» (`serverAddress`) | :13 | D-PROFILE-01 | |
| «Удалённый порт» (`serverPort`) | :18 | D-PROFILE-01 | |
| «Логин пользователя» UUID (`serverUsername`) | :23 | D-PROFILE-01 | |
| «Пароль», токен (`serverPassword`) | :29 | D-PROFILE-01 | |
| ALPN (`serverALPN`) | :33 | D-PROFILE-01 | |
| «Сертификаты» (`serverCertificates`) | :38 | D-PROFILE-01 | |
| «Режим UDP-ретранслятора»: native, quic (`serverUDPRelayMode`) | :46 | D-PROFILE-01 | `defaultValue="https"` не входит в список |
| «Контроллер перегрузки»: cubic, new_reno, bbr (`serverCongestionController`) | :54 | D-PROFILE-01 | то же |
| «Отключить SNI» (`serverDisableSNI`); блокирует поле SNI | :59; ui/profile/TuicSettingsActivity.kt:55-61 | D-PROFILE-01 | |
| «SNI» (`serverSNI`) | :63 | D-PROFILE-01 | |
| «Включить 0-RTT QUIC-соединение» (`serverReduceRTT`) | :68 | D-PROFILE-01 | |
| «Разрешить небезопасное подключение» (`serverAllowInsecure`) | :72 | D-PROFILE-01 | |

### 5.11 ShadowTLS (`proxy/shadowtls/ShadowTLSSettingsActivity.kt`, xml/shadowtls_preferences.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`name`) | xml/shadowtls_preferences.xml:4 | D-PROFILE-01 | |
| «Сервер» (`serverAddress`) | :11 | D-PROFILE-01 | |
| «Удалённый порт» (`serverPort`) | :16 | D-PROFILE-01 | |
| «Версия протокола»: 2, 3 (`version`) | :23 | D-PROFILE-01 | |
| «Пароль» (`password`) | :29 | D-PROFILE-01 | |
| Безопасность › «SNI» (`sni`) | :38 | D-PROFILE-01 | |
| Безопасность › ALPN (`alpn`) | :43 | D-PROFILE-01 | |
| Безопасность › «Сертификаты» (`certificates`) | :48 | D-PROFILE-01 | |
| Безопасность › «Разрешить небезопасное подключение» (`allowInsecure`) | :53 | D-PROFILE-01 | |
| Безопасность › «Отпечаток uTLS» (`utlsFingerprint`) | :61 | D-PROFILE-01 | |

### 5.12 AnyTLS (`proxy/anytls/AnyTLSSettingsActivity.kt`, xml/anytls_preferences.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`name`) | xml/anytls_preferences.xml:5 | D-PROFILE-01 | |
| «Сервер» (`serverAddress`) | :12 | D-PROFILE-01 | |
| «Удалённый порт» (`serverPort`) | :17 | D-PROFILE-01 | |
| «Пароль» (`password`) | :22 | D-PROFILE-01 | |
| Безопасность › «SNI» (`sni`) | :29 | D-PROFILE-01 | |
| Безопасность › «Разрешить небезопасное подключение» (`allowInsecure`) | :34 | D-PROFILE-01 | |
| Безопасность › ALPN (`alpn`) | :38 | D-PROFILE-01 | |
| Безопасность › «Сертификаты» (`certificates`) | :43 | D-PROFILE-01 | |
| Безопасность › «Отпечаток uTLS» (`utlsFingerprint`) | :51 | D-PROFILE-01 | |

### 5.13 Свой конфиг (`proxy/config/ConfigSettingActivity.kt`, xml/config_preferences.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`profileName`) | xml/config_preferences.xml:5 | D-PROFILE-01 | |
| «Сделать JSON исходящим» (`isOutboundOnly`): только outbound или весь конфиг | :11; proxy/config/ConfigSettingActivity.kt:23, 30 | D-PROFILE-02 | |
| «Пользовательская конфигурация» (`serverConfig`): «N строк», открывает редактор JSON | :16; ui/EditConfigPreference.kt:47-56 | D-PROFILE-02 | |

### 5.14 Цепочка (`ChainSettingsActivity`, layout/layout_chain_settings.xml)

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Имя профиля» (`profileName`) | xml/name_preferences.xml:5; ui/profile/ChainSettingsActivity.kt:53 | D-PROFILE-04 | |
| Заголовок «Настройки цепочки» | ui/profile/ChainSettingsActivity.kt:64 | D-PROFILE-04 | |
| Список звеньев: имя, тип с цветом, трафик | layout/layout_chain_settings.xml:25; ChainSettingsActivity.kt:262-291 | D-PROFILE-04 | |
| Строка «Добавить профиль» открывает выбор профиля | layout/layout_add_entity.xml:19; ChainSettingsActivity.kt:239-251 | D-PROFILE-04 | |
| Кнопка «Изменить» у звена: заменить его другим профилем | ChainSettingsActivity.kt:281-288 | D-PROFILE-04 | |
| Перетащить звено (порядок) | ChainSettingsActivity.kt:71-99, 137-143 | D-PROFILE-04 | |
| Смахнуть звено: убрать (без отмены) | ChainSettingsActivity.kt:101-103, 145-149 | D-PROFILE-04 | |
| «Циклическая ссылка»: сам профиль или цепочка, которая его содержит | ChainSettingsActivity.kt:181-204, 219-224 | D-PROFILE-04 | |

Меню у цепочки то же, что в разделе 4, только без «Настроек подключения» (ui/profile/ProfileSettingsActivity.kt:357); здесь его пункты не повторяются.

## 6. ConfigEditActivity: редактор JSON

**Перенесено** в arngory/niko#162 (`ui/profile/JsonScreen.kt`, P-Json): отмена, повтор и форматирование — в шапке; ряд `Tab { } , : _ "` и «Сохранить» — над клавиатурой; ошибка разбора — плашкой на месте с номером строки, строка подсвечена; вопрос о несохранённом — листом. Подсветки синтаксиса нет (в палитре Niko она сводилась к двум оттенкам), библиотека EditorKit ушла из сборки.

Его открывают: свой JSON outbound и конфига у профиля (раздел 4), свой конфиг (5.13), JSON правила (раздел 11) и
общий свой конфиг в настройках (ui/SettingsFragment.kt:157-163, D-SET-26).

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Поле JSON с подсветкой, шрифт Geist Mono, горизонтальная прокрутка | layout/layout_edit_config.xml:24; ui/profile/ConfigEditActivity.kt:116-132 | D-PROFILE-02 | |
| Кнопка Tab (`action_tab`) | layout/layout_edit_config.xml:50; ConfigEditActivity.kt:134-139 | D-PROFILE-02 | |
| Ряд символов `{ } , : _ "` (`extended_keyboard`) | layout/layout_edit_config.xml:60; ConfigEditActivity.kt:158-166 | D-PROFILE-02 | |
| «Отменить» (`action_undo`) | layout/layout_edit_config.xml:69; ConfigEditActivity.kt:140-145 | D-PROFILE-02 | |
| «Повторить» (`action_redo`) | layout/layout_edit_config.xml:79; ConfigEditActivity.kt:146-151 | D-PROFILE-02 | |
| «Форматировать» (`action_format`); ошибка JSON показывается диалогом | layout/layout_edit_config.xml:89; ConfigEditActivity.kt:152-156, 188-200 | D-PROFILE-02 | |
| «Применить» ✓ (`action_apply`): проверить, отформатировать, сохранить | menu/profile_apply_menu.xml:4; ConfigEditActivity.kt:202-211, 218-231 | D-PROFILE-02 | |
| «Есть несохранённые изменения. Сохранить?» | ConfigEditActivity.kt:39-50, 97-99 | D-PROFILE-02 | |

## 7. RouteFragment: правила

**Перенесено** в arngory/niko#152 (`ui/route/`, P-Rules, P-Rule, P-Check, P-Lists): каждая строка ниже — на новом экране или снята как DEAD; отступления от макета — в описании PR.

Сюда ведут плитка и строка «Маршруты» на главной (ui/MainActivity.kt:132-134).

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Заголовок «Маршруты» | ui/RouteFragment.kt:42 | D-RULES-01 | |
| «+» добавить правило (`action_new_route`) | menu/add_route_menu.xml:5; ui/RouteFragment.kt:110-112 | D-RULES-01 | |
| «Какое правило сработает» (`action_route_test`) | menu/add_route_menu.xml:10; ui/RouteFragment.kt:113 | D-CHECK-02…05 | поля диалога — в разделе 9 |
| «Сброс» (`action_reset_route`): стандартные правила, с подтверждением | menu/add_route_menu.xml:14; ui/RouteFragment.kt:114-126 | D-RULES-01 | |
| «Пресет: Россия» (`action_route_preset_ru`): вопрос, загрузка списков, «готово / частично» | menu/add_route_menu.xml:18; ui/RouteFragment.kt:127-134, 143-162 | D-RULES-05 | Китай и Иран ещё не сделаны |
| «Применить» в snackbar после пресета | ui/RouteFragment.kt:153-156 | D-STATE-RELOAD | |
| «Управление ресурсами маршрутов» (`action_manage_assets`) | menu/add_route_menu.xml:22; ui/RouteFragment.kt:135-137 | D-RULES-03 | |
| Карточка «Прочтите документацию…» открывает руководство по маршрутизации (ru или en) | layout/layout_empty_route.xml:19; ui/RouteFragment.kt:300-309 | GAP | ссылки на руководство нет в D-RULES; может войти в FAQ D-SET-31 |
| Строка правила: имя, сводка условий, выход | ui/RouteFragment.kt:321-325 | D-RULES-01 | |
| Переключатель правила (`enable`); нажатие на строку тоже переключает | layout/layout_route_item.xml:132; ui/RouteFragment.kt:326-338 | D-RULES-01 | |
| Кнопка «Изменить» (`edit`) | layout/layout_route_item.xml:57; ui/RouteFragment.kt:339-343 | D-RULES-01 | |
| Смахнуть: удалить с отменой | ui/RouteFragment.kt:73-77, 242-259 | D-RULES-01 | |
| Перетащить: порядок правил | ui/RouteFragment.kt:79-97, 216-240 | D-RULES-01 | |
| После правки: «нужно перезапустить» | ui/RouteFragment.kt:238, 265, 275, 283, 296, 335 | D-STATE-RELOAD | |

## 8. RouteSettingsActivity: редактор правила (xml/route_preferences.xml)

**Перенесено** в arngory/niko#152 (`ui/route/`, P-Rules, P-Rule, P-Check, P-Lists): каждая строка ниже — на новом экране или снята как DEAD; отступления от макета — в описании PR.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Название маршрута» (`routeName`) | xml/route_preferences.xml:6 | D-RULES-01 | |
| «Пользовательская конфигурация» правила, JSON (`serverConfig`) | :11 | D-RULES-01 | окно: «нет своего JSON» |
| «Приложения» (`routePackages`): открывает выбор приложений | :17; ui/RouteSettingsActivity.kt:173-180 | D-RULES-01, D-RULES-02 | |
| domain (`routeDomain`) | :22 | D-RULES-01 | подписи полей на английском |
| dst ip (`routeIP`) | :28 | D-RULES-01 | |
| dst port (`routePort`) | :35 | D-RULES-01 | |
| src ip (`routeSource`) | :40 | D-RULES-01 | в описи названы только «IP, порты» |
| src port (`routeSourcePort`) | :45 | D-RULES-01 | |
| network: TCP и UDP, TCP, UDP (`routeNetwork`) | :52 | D-RULES-01 | |
| protocol (`routeProtocol`) | :57 | D-RULES-01 | |
| outbound: «Прокси», «Обход», «Блокировать», «Выбрать профиль…» (`routeOutbound`) | :62; widget/OutboundPreference.kt:15-30; ui/RouteSettingsActivity.kt:159-171, 127-141 | D-RULES-01 | у выбранного профиля в сводке его имя |
| «Обход DPI» (`routeDpiBypass`), только при «Обход» | :67; ui/RouteSettingsActivity.kt:155-160 | D-RULES-01 | |
| «Применить» ✓ (`action_apply`) | menu/profile_config_menu.xml:11; ui/RouteSettingsActivity.kt:366-371, 266-295 | D-RULES-01 | сохранение без правок даже у существующего правила говорит «Установите какие-либо правила» (:268-276) |
| «Удалить» (`action_delete`), с подтверждением | menu/profile_config_menu.xml:5; ui/RouteSettingsActivity.kt:204-215, 354-364 | D-RULES-01 | |
| «Есть несохранённые изменения. Сохранить?» | ui/RouteSettingsActivity.kt:187-200, 224-226 | D-RULES-01 | |
| Пункт «Настройки подключения» в меню | menu/profile_config_menu.xml:24; ui/RouteSettingsActivity.kt:299-302 | DEAD | виден, потому что `android:visible` не задан, но в `onOptionsItemSelected` для него нет ветки (:353-374): нажатие ничего не делает |
| Правило «для приложения» (`EXTRA_PACKAGE_NAME`, имя «Маршрут для …») | ui/RouteSettingsActivity.kt:50-57, 219, 239, 280-282 | DEAD | никто не передаёт этот extra (grep по `java/`); в описи это «маршрут для приложения без вызова» |

## 9. Вспомогательные экраны выбора

### 9.1 RouteTest: «Какое правило сработает»

**Перенесено** в arngory/niko#152 (`ui/route/`, P-Rules, P-Rule, P-Check, P-Lists): каждая строка ниже — на новом экране или снята как DEAD; отступления от макета — в описании PR.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Поле «Хост или адрес и порт» | ui/RouteTest.kt:33-36, 117-133 | D-CHECK-02 | |
| Поле «Из приложения (необязательно)» с подсказками из установленных приложений | ui/RouteTest.kt:37-52, 71, 78-81 | GAP | D-CHECK-02 называет только адрес |
| Флажок «UDP» | ui/RouteTest.kt:53, 74 | GAP | то же |
| Флажок «Спросить DNS ядра, как спросило бы приложение» (по умолчанию включён) | ui/RouteTest.kt:56-59, 77 | GAP | то же |
| «Проверить» | ui/RouteTest.kt:64 | D-CHECK-03 | |
| «Неверный адрес» | ui/RouteTest.kt:66-69 | D-CHECK-02 | |
| Результат: правило #N, выход или ошибка | ui/RouteTest.kt:140-164 | D-CHECK-04, D-CHECK-05 | |
| Результат: ответ DNS, что видели правила, FakeDNS | ui/RouteTest.kt:165-175 | D-CHECK-04 | |
| Результат: шаги до решающего правила, заметки ядра | ui/RouteTest.kt:176-192 | D-CHECK-05 | |
| Без запущенной службы — ошибка «не запущено» | ui/MainActivity.kt:459-464 | D-CHECK-04 | |

### 9.2 ProfileSelectActivity: выбор профиля

**Перенесено** в arngory/niko#164 (`ui/pick/PickScreen.kt`, P-Pick): профили по группам, как на листе конфигов, без отметки выбора; под заголовком — для чего выбирают; поиск; список открывается на профиле, который заменяют. Экран на всю высоту, а не листом над вызывающим.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Экран «Выбрать профиль»: это тот же список профилей в режиме выбора — вкладки групп, поиск, отметка текущего, ✕, без кнопок и «Поделиться» | ui/ProfileSelectActivity.kt:16-28; ui/ConfigurationFragment.kt:224-233, 1721-1724 | GAP | его используют D-PROFILE-04 (цепочка), D-RULES-01 (выход правила) и D-GROUP-01 (front и landing proxy, ui/GroupSettingsActivity.kt:172). Своего ID и макета нет; можно взять лист P-Configs |
| Нажатие на профиль возвращает его вызвавшему экрану | ui/ProfileSelectActivity.kt:30-35; ui/ConfigurationFragment.kt:1603-1606 | GAP | то же |

### 9.3 AppListActivity: приложения для правила

**Перенесено** в arngory/niko#156 (`ui/apps/`, P-Apps): выбор приложений правила — тот же экран без режима (M-RULES-02), каждая строка ниже — на новом экране; отступления от макета — в описании PR.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Заголовок «Выберите приложения» | ui/AppListActivity.kt:207 | D-RULES-01, D-RULES-02 | |
| «Показать системные приложения» (по умолчанию выключено) | layout/layout_app_list.xml:59; ui/AppListActivity.kt:223-227, 232 | D-RULES-02 | |
| Поиск: имя, пакет, uid | layout/layout_app_list.xml:72; ui/AppListActivity.kt:219-221 | D-RULES-02 | |
| Строка: значок, имя, «пакет (uid)», флажок; нажатие отмечает | ui/AppListActivity.kt:77-78, 89-93 | D-RULES-02 | |
| «Инвертировать выбранное» (`action_invert_selections`) | menu/app_list_menu.xml:4; ui/AppListActivity.kt:241-259 | GAP | для правила ни один ID не называет |
| «Очистить выбранное» (`action_clear_selections`) | menu/app_list_menu.xml:7; ui/AppListActivity.kt:261-270 | GAP | то же |
| «Экспорт в буфер» (`action_export_clipboard`) | menu/app_list_menu.xml:11; ui/AppListActivity.kt:272-280 | GAP | то же |
| «Импорт из буфера» (`action_import_clipboard`) | menu/app_list_menu.xml:14; ui/AppListActivity.kt:282-300 | GAP | то же |
| Нет доступа к списку приложений: «Открыть настройки приложения» | layout/layout_app_placeholder.xml:11-24; ui/AppListActivity.kt:197-203 | GAP | M-STATE-PERM говорит только о VPN и уведомлениях |

## 10. ScannerActivity: QR камерой

**Перенесено** в arngory/niko#161 (`ui/add/`, P-Scan и P-ScanDenied): экран «Добавить» — видоискатель с фонариком, «Из буфера», «QR из файла», «Импорт файла» и «Вручную» (17 типов из старого «+»); при отказе в камере экран остаётся, «Разрешить» спрашивает снова или ведёт в настройки системы. «+» листа конфигов открывает его.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Видоискатель камеры, весь кадр, тёмный и светлый QR | layout/layout_scanner.xml:13-20; ui/ScannerActivity.kt:183-193 | M-ADD-01 | |
| Фонарик (`ivFlashlight`) | layout/layout_scanner.xml:32; ui/ScannerActivity.kt:62, 219-223 | GAP | в M-ADD-01 не назван |
| «Импорт из файла» (`action_import_file`): несколько картинок, перевёрнутый QR | menu/scanner_menu.xml:4; ui/ScannerActivity.kt:70-118 | D-ADD-05 | |
| Запрос разрешения на камеру; отказ закрывает экран | ui/ScannerActivity.kt:198-207, 227-250 | GAP | M-STATE-PERM камеру не называет |
| Код raycontrol передаётся главной, а она спрашивает | ui/ScannerActivity.kt:138-143 | D-ADD-06, D-ADD-07 | |
| Профили импортируются сразу, а те, что переписывают весь конфиг, — после вопроса | ui/ScannerActivity.kt:144-157 | D-ADD-10 | |
| Найдена ссылка подписки | ui/ScannerActivity.kt:163-167 | D-ADD-11 | |
| «Не удалось импортировать» и текст ошибки | ui/ScannerActivity.kt:158-175 | D-ADD-03 | |
| Ярлык «Сканировать» (`reportShortcutUsed("scan")`) | ui/ScannerActivity.kt:50 | M-SYS-01 | |

## 11. AssetsActivity: ресурсы маршрутов

**Перенесено** в arngory/niko#152 (`ui/route/`, P-Rules, P-Rule, P-Check, P-Lists): каждая строка ниже — на новом экране или снята как DEAD; отступления от макета — в описании PR.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Заголовок «Ресурсы», ✕ | ui/AssetsActivity.kt:45-47 | D-RULES-03 | |
| Строки geoip.db и geosite.db, затем свои .db и ca.pem | ui/AssetsActivity.kt:176-188 | D-RULES-03 | наборов `srs:` из пресетов здесь нет |
| Версия: тег выпуска, Custom или «Unknown-дата» | ui/AssetsActivity.kt:233-250 | D-RULES-03 | |
| «Обновить» у geoip и geosite: последний выпуск из выбранного источника | layout/layout_asset_item.xml:80; ui/AssetsActivity.kt:252-275, 298-360 | D-RULES-03 | источник (Official, Loyalsoldier/soffchen, Iran, Antizapret) выбирается в настройках, уже на Compose (ui/settings/SettingsScreen.kt:296) |
| «Обновлений нет», «Обновлено» | ui/AssetsActivity.kt:322-327, 354-356 | D-RULES-03 | |
| Потянуть вниз: перечитать список | ui/AssetsActivity.kt:54-58 | D-RULES-03 | |
| Смахнуть: удалить свой .db или ca.pem, с отменой (geoip и geosite нельзя) | ui/AssetsActivity.kt:62-86, 202-219 | D-RULES-03, D-RULES-04 | |
| «Импорт из файла» (`action_import_file`): .db | menu/import_asset_menu.xml:6; ui/AssetsActivity.kt:105-133, 157-165 | D-RULES-03 | |
| Импорт .pem: «Добавить корневой сертификат?» с предупреждением, «Сертификат добавлен» | ui/AssetsActivity.kt:119-127, 150 | D-RULES-04 | |
| «Не ресурс маршрута» для другого файла | ui/AssetsActivity.kt:130 | D-RULES-03 | |

## 12. AppManagerActivity: приложения через VPN

**Перенесено** в arngory/niko#156 (`ui/apps/`, P-Apps): каждая строка ниже — на новом экране; отступления от макета — в описании PR.

Сюда ведут плитка и строка «Приложения» на главной (ui/MainActivity.kt:136).

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Заголовок «Режим VPN для приложений», ✕ | ui/AppManagerActivity.kt:211-216 | M-RULES-01 | |
| Режим: «Выключенный» (закрывает экран) / «Прокси» / «Обход» (`bypassGroup`) | layout/layout_apps.xml:53-78; ui/AppManagerActivity.kt:222-233 | M-RULES-01 | открытие экрана само включает режим по приложениям (`proxyApps = true`, :218-220) |
| «Показать системные приложения» (по умолчанию включено) | layout/layout_apps.xml:88; ui/AppManagerActivity.kt:247-251, 257 | M-RULES-01 | |
| «Автоматический выбор прокси-приложений»: список из assets, текущий выбор сбрасывается | layout/layout_apps.xml:97; ui/AppManagerActivity.kt:234, 337-389 | GAP | в M-RULES-01 нет; может заменить D-RULES-07 (списки из репозитория) |
| Поиск: имя, пакет, uid | layout/layout_apps.xml:111; ui/AppManagerActivity.kt:243-245, 133-150 | M-RULES-01 | |
| Строка: значок, имя, «пакет (uid)», флажок; нажатие отмечает; отмеченные сверху | ui/AppManagerActivity.kt:82-100, 113 | M-RULES-01 | |
| Быстрая прокрутка по первой букве | ui/AppManagerActivity.kt:105, 154-156 | M-RULES-01 | |
| «Инвертировать выбранное» (`action_invert_selections`) | menu/per_app_proxy_menu.xml:4; ui/AppManagerActivity.kt:266-285 | M-RULES-01 | |
| «Очистить выбранное» (`action_clear_selections`) | menu/per_app_proxy_menu.xml:8; ui/AppManagerActivity.kt:287-296 | GAP | в M-RULES-01 нет |
| «Экспорт в буфер»: режим и список (`action_export_clipboard`) | menu/per_app_proxy_menu.xml:12; ui/AppManagerActivity.kt:298-307 | M-RULES-01 | |
| «Импорт из буфера» (`action_import_clipboard`) | menu/per_app_proxy_menu.xml:17; ui/AppManagerActivity.kt:309-332 | GAP | в M-RULES-01 назван только экспорт |
| Нет доступа к списку приложений: «Открыть настройки приложения» | layout/layout_app_placeholder.xml:21; ui/AppManagerActivity.kt:203-209 | GAP | M-STATE-PERM этого не называет |
| ТВ: клавиша Menu открывает меню | ui/AppManagerActivity.kt:394-396 | M-SYS-05 | |
| Выбор применяется на ходу: snackbar «Применить» | ui/MainActivity.kt:800-805 | D-STATE-RELOAD | |

## 13. AboutFragment: «О программе»

**Перенесено** в arngory/niko#157 (`ui/about/`, P-About): каждая строка ниже — на новом экране. Проверка обновлений и её диалоги перенесены как были; две кнопки проверки стали одной кнопкой «Проверить» в строке «Обновление» и переключателем «Предварительные сборки». Пробелы 18–22 (D-SET-34) — в группе проекта. Отступления от макета — в описании PR.

Сюда ведёт строка «О программе» в настройках (ui/SettingsFragment.kt:169-171). Назад возвращает в настройки.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Шапка: имя и описание приложения | layout/layout_about.xml:22-54 | D-SET-12 | |
| «Версия»: номер, нажатие открывает выпуски на GitHub | ui/AboutFragment.kt:146-155 | D-SET-12 | |
| «Проверить наличие обновлений релизной версии» | ui/AboutFragment.kt:157-162, 342+ | D-SET-12 | |
| «Проверить наличие обновлений предварительной версии» | ui/AboutFragment.kt:163-169 | D-SET-12 | канал предварительных сборок |
| Диалог обновления: «Установить», «Страница выпуска», «Нет» / обязательное без «Нет» | ui/AboutFragment.kt:443-470, 505-511 | D-SET-12 | |
| Обновление с узла, если GitHub недоступен (app-update-v1) | ui/AboutFragment.kt:369-374, 525-545, 549-575 | D-SET-12, BG-12 | |
| GitHub не открылся напрямую: проверка через выбранный профиль, тост | ui/AboutFragment.kt:322-340 | D-SET-12 | |
| «Нужен Android …», «Уже скачивается», «Разрешите установку», «Обновлений нет» | ui/AboutFragment.kt:344-347, 514-522, 580-601, 468 | D-SET-12 | |
| «Версия sing-box» (ядро) | ui/AboutFragment.kt:170-176 | D-SET-12 | |
| Плагины: версия, «доверен / не доверен»; диалог «Доверять», «Отозвать», «О приложении» | ui/AboutFragment.kt:177-203, 109-138 | M-SET-03 | |
| «Игнорировать оптимизации батареи» (если не отключены) | ui/AboutFragment.kt:205-221 | M-SET-02 | |
| «Работа в фоне ограничена» | ui/AboutFragment.kt:224-232 | M-SET-02 | |
| «Собственный менеджер фона телефона» | ui/AboutFragment.kt:233-241 | M-SET-02 | |
| «Проект» › «Исходный код» (GitHub) | ui/AboutFragment.kt:248-255 | GAP | D-SET-12 этого не называет |
| «Проект» › «Лицензии» | ui/AboutFragment.kt:256-264 | GAP | см. раздел 14 |
| «Проект» › «Оригинальный проект: NekoBox for Android» | ui/AboutFragment.kt:265-274 | GAP | указание на форк по GPL; в ID нет |
| Текст лицензии (GPL-3.0 из assets) с активными ссылками | layout/layout_about.xml:83-87; ui/AboutFragment.kt:87-93 | GAP | юридически нужное; в ID нет |
| Вход с проверкой обновления (узел требует более новый Niko) | ui/AboutFragment.kt:66-67, 286-290 | D-SET-12 | |

«Поддержать проект» (ссылка на donotpromise.space/donate, D-SET-12) приходит в этот экран с arngory/niko#148.

## 14. LicensesActivity и LicenseTextActivity

**Перенесено** в arngory/niko#157 (`ui/about/LicensesActivity.kt`): список по разделам, строка, текст с выделением и «Сайт» в шапке.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Список «Лицензии» по разделам: NOTICE, приложение, библиотеки, нативное, данные, шрифты, значки, прочее, модули Go | ui/LicensesActivity.kt:39-151, 160-192 | GAP | ни ID, ни макета |
| Строка: имя и подробность; нажатие открывает текст | ui/LicensesActivity.kt:208-224 | GAP | |
| Текст лицензии (несколько файлов подряд, выделяемый), имя и подробность в заголовке | ui/LicensesActivity.kt:231-305 | GAP | |
| «Сайт» в меню, если у записи есть адрес | ui/LicensesActivity.kt:307-321 | GAP | |

## 15. BackupFragment: резервная копия

**Перенесено** в arngory/niko#159 (`ui/backup/`): страница настроек по разделу «Резервная копия» десктопного макета (B-Settings), у телефона своей страницы на холсте нет. Файл и его чтение прежние (`Backup`, версия 1); диалог импорта стал листом «Восстановить» с теми же флажками; каждая строка ниже — на новой странице.

Сюда ведёт строка «Резервная копия» в настройках (ui/SettingsFragment.kt:165-167).

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Восстановить настройки по умолчанию», с подтверждением; узлы остаются; перезапуск | layout/layout_backup.xml:9; ui/BackupFragment.kt:67-77 | D-SET-32 | |
| Флажок «Группы и конфигурации» | layout/layout_backup.xml:16 | D-SET-19 | |
| Флажок «Правила маршрутизации» | layout/layout_backup.xml:24 | D-SET-19 | |
| Флажок «Настройки» (без секретов этой установки) | layout/layout_backup.xml:33; ui/BackupFragment.kt:161-167 | D-SET-19 | |
| «Экспортировать» в файл `niko_backup_….json` | layout/layout_backup.xml:41; ui/BackupFragment.kt:79-92, 39-60 | D-SET-19 | |
| «Поделиться» копией через системное меню | layout/layout_backup.xml:48; ui/BackupFragment.kt:94-121 | D-SET-19 | |
| Пояснение (`backup_summary`) | layout/layout_backup.xml:59 | D-SET-19 | |
| «Импорт из файла»: только .json версии 1 | layout/layout_backup.xml:63; ui/BackupFragment.kt:123-125, 180-216 | D-SET-19 | |
| Диалог импорта: те же три флажка, отсутствующие в файле части выключены | layout/layout_import.xml:9-37; ui/BackupFragment.kt:218-262 | D-SET-19 | «импорт по частям» |
| «Импортирую…», затем полный перезапуск | ui/BackupFragment.kt:232-259 | D-SET-19 | |

## 16. RayAdminActivity: панель администратора узла

Вкладки: Статус, Пользователи, Службы, Сеть, Аудит (ui/RayAdminActivity.kt:107-120). Сюда ведут карточка узла
(P-Node), P-Subs и старый список групп.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Вкладки; повторное нажатие на вкладку перечитывает её | ui/RayAdminActivity.kt:107-120 | D-ADMIN-* | |
| «Добавить» в меню на вкладках «Пользователи» и «Службы» | ui/RayAdminActivity.kt:124-145 | D-ADMIN-13, D-ADMIN-15 | |
| Ошибки API: нет прав, не авторизован, ответ потерян | ui/RayAdminActivity.kt:150-180 | D-ADMIN-* | общий текст ошибок, в ID не назван |
| Статус › «Это устройство»: пользователь/имя · роль | ui/RayAdminActivity.kt:251-252 | D-ADMIN-10 | |
| Статус › «Нода»: хост · версия, id | ui/RayAdminActivity.kt:253 | D-ADMIN-10 | |
| Статус › «Предупреждения ноды» (raycontrol v0.35.1) | ui/RayAdminActivity.kt:256-257, 1523 | GAP | D-ADMIN-10 предупреждения не называет |
| Статус › «Имя ноды», правка | ui/RayAdminActivity.kt:237-241, 273-291 | D-ADMIN-01 | |
| Статус › «Заметка», правка с пределом длины | ui/RayAdminActivity.kt:243-246, 302-329 | D-ADMIN-02 | |
| Статус › «Обновление»: установлена, последняя, патч, удалённо вкл. или выкл., автообновление, удержано | ui/RayAdminActivity.kt:247, 331-346 | D-ADMIN-08 | |
| Обновление › «До N» / «До последней» / «До версии…», с подтверждением | ui/RayAdminActivity.kt:373-425 | D-ADMIN-08 | |
| Обновление › «Ретрансляторы»: состояния и причины | ui/RayAdminActivity.kt:348-356, 399-401, 1445-1563 | D-ADMIN-08 | |
| Обновление › ход: запрошено, идёт, готово, откат, сбой, узел недоступен | ui/RayAdminActivity.kt:357-371, 427-480 | D-ADMIN-08 | |
| Статус › «Релизы приложения на ноде»: вкл. или выкл., что лежит, проверено, ошибка | ui/RayAdminActivity.kt:248, 482-500 | D-ADMIN-09 | |
| Статус › «Сводки клиентов»: вкл. или выкл., «Сети» | ui/RayAdminActivity.kt:249, 502-526 | D-ADMIN-09 | |
| Статус › счётчики: пользователи, службы, активные устройства | ui/RayAdminActivity.kt:259 | D-ADMIN-04 | |
| Статус › «Порты SPA» | ui/RayAdminActivity.kt:260 | D-ADMIN-10 | |
| Статус › «Порты» (для файрвола провайдера) | ui/RayAdminActivity.kt:262-263, 538-549, 1399-1425 | D-ADMIN-11 | |
| Статус › «Метрики» | ui/RayAdminActivity.kt:264, 528-536 | D-ADMIN-11 | |
| Статус › «Аварийный доступ по SSH»: адреса CIDR, правка | ui/RayAdminActivity.kt:265-268, 551-565 | D-ADMIN-12 | |
| Пользователи › «＋ Новый пользователь»: имя, дни, ГБ, заметка, службы | ui/RayAdminActivity.kt:571, 608-650 | D-ADMIN-13 | |
| Пользователи › строка: службы, срок, лимит, выключен | ui/RayAdminActivity.kt:572-580 | D-ADMIN-05 | |
| Пользователь › «Пригласить»: имя устройства, роль (пользователь или админ), срок, PIN → QR, «Поделиться», «Копировать», FLAG_SECURE | ui/RayAdminActivity.kt:586, 712-744, 797-830 | D-ADMIN-03 | |
| Пользователь › «Устройства»: активные и отозванные, ожидающие приглашения, «Пригласить» | ui/RayAdminActivity.kt:587, 674-698 | D-ADMIN-06 | |
| Устройства › отозвать устройство / отозвать приглашение, с подтверждением | ui/RayAdminActivity.kt:700-710, 1338-1349 | D-ADMIN-06 | |
| Пользователь › «Службы»: выбор флажками | ui/RayAdminActivity.kt:588-590, 660-672 | D-ADMIN-07 | |
| Пользователь › «Ссылки для других приложений»: «Поделиться», «Копировать», FLAG_SECURE | ui/RayAdminActivity.kt:591, 769-794 | D-ADMIN-14 | |
| Пользователь › «Новые учётные данные», с подтверждением | ui/RayAdminActivity.kt:592, 654-658, 1351-1355 | D-ADMIN-14 | |
| Пользователь › «Включить» / «Выключить» | ui/RayAdminActivity.kt:593-595 | D-ADMIN-07 | в описи: «включить или выключить, удалить» |
| Пользователь › «Удалить», с подтверждением | ui/RayAdminActivity.kt:596-598 | D-ADMIN-07 | |
| Службы › список: имя (вид), активна, id, дата | ui/RayAdminActivity.kt:839-846 | D-ADMIN-15 | |
| Службы › «Добавить»: вид, затем имя, «применится к ноде» | ui/RayAdminActivity.kt:1103-1124 | D-ADMIN-15 | |
| Служба › «Включить» / «Выключить»; последний SSH — с подтверждением | ui/RayAdminActivity.kt:852, 871-882, 1361 | D-ADMIN-15 | |
| Служба › «Настройки» dnstt и masterdns: auth, auth_hint, seal, spare_zones, zone | ui/RayAdminActivity.kt:855, 986-995, 1022-1066 | D-ADMIN-16 | |
| Служба › «Настройки» slipstream: max_connections, device_connections | ui/RayAdminActivity.kt:996-999 | D-ADMIN-16 | |
| Служба › «Настройки» site: answer (404, 403, silent, pages) | ui/RayAdminActivity.kt:1000 | D-ADMIN-16 | |
| Служба › «Настройки» REALITY: rotate_sni, open, target (с проверкой) | ui/RayAdminActivity.kt:1001-1008, 1069-1101, 1367 | D-ADMIN-16 | |
| Служба › «Настройки» hy2-masq и hy2-obfs: up_mbps, down_mbps | ui/RayAdminActivity.kt:1009-1012 | D-ADMIN-16 | |
| Служба › «Настройки» yggdrasil: peers | ui/RayAdminActivity.kt:1013-1016 | D-ADMIN-16 | |
| Служба › «Сменить ключи» REALITY: по плану или сейчас | ui/RayAdminActivity.kt:856, 896-911 | D-ADMIN-17 | |
| Служба › ключ seal (dnstt): с запасом или сейчас | ui/RayAdminActivity.kt:858, 913-962 | D-ADMIN-17 | |
| Служба › ключ службы (masterdns): с запасом или сейчас | ui/RayAdminActivity.kt:859, 913-962 | D-ADMIN-17 | |
| Служба › «Удалить», с подтверждением; последний SSH — ещё раз | ui/RayAdminActivity.kt:860, 884-894 | D-ADMIN-15 | |
| Сеть › DNS-провайдер: состояние и записи | ui/RayAdminActivity.kt:1127-1142 | D-ADMIN-18 | |
| DNS › «Синхронизировать» | ui/RayAdminActivity.kt:1164-1171 | D-ADMIN-18 | |
| DNS › «Токен API» | ui/RayAdminActivity.kt:1172, 1186-1200 | D-ADMIN-18 | |
| DNS › «Удалить», с подтверждением | ui/RayAdminActivity.kt:1173-1177 | D-ADMIN-18 | |
| Сеть › «Проверить зоны туннелей» | ui/RayAdminActivity.kt:1143-1149, 1202-1226 | D-ADMIN-18 | |
| Сеть › ретранслятор: адрес, дата, probe; SSH ретранслятора: порт и CIDR | ui/RayAdminActivity.kt:1151-1156, 1228-1246 | D-ADMIN-18 | |
| Сеть › «Добавлять и удалять ретрансляторы может только root на ноде» | ui/RayAdminActivity.kt:1158 | D-ADMIN-18 | |
| Аудит › 200 последних записей | ui/RayAdminActivity.kt:1248-1252 | D-ADMIN-19 | |

## 17. StunActivity: поведение NAT

**Перенесено** в arngory/niko#160 (`ui/tools/NatScreen.kt`, P-NAT): поле сервера, «Проверить», результат по строкам (тип, сопоставление, фильтрация, внешний адрес), прочие строки ядра — под ними; ошибка остаётся на экране, а не закрывает его.

Сюда ведёт строка «NAT» в журнале (ui/log/LogFragment.kt:68).

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Поле «STUN сервер» (по умолчанию stun.voipgate.com:3478) | layout/layout_stun.xml:62-72; ui/StunActivity.kt:37 | D-TOOLS-01, D-LOG-03 | на компьютере запрос идёт через основной исходящий, поля сервера в описи нет |
| Кнопка «Старт» | layout/layout_stun.xml:117; ui/StunActivity.kt:28-30 | D-TOOLS-01 | |
| Ожидание | layout/layout_stun.xml:19-29; ui/StunActivity.kt:34 | D-TOOLS-01 | |
| Результат (сопоставление, фильтрация, внешний адрес) | layout/layout_stun.xml:99; ui/StunActivity.kt:58-61 | D-TOOLS-01, D-LOG-03 | |
| Ошибка: диалог, по «OK» экран закрывается | ui/StunActivity.kt:43-56 | D-TOOLS-01 | |

## 18. WebviewFragment: панель sing-box (Clash API)

**Перенесено** в arngory/niko#160 (`ui/tools/PanelScreen.kt`, P-Panel): адрес панели и ключ API — в листе ⋮; «Закрыть» снято, «назад» ведёт в журнал.

Сюда ведёт строка «Панель» в журнале, если включён Clash API (ui/log/LogFragment.kt:71).

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Заголовок «Панель», WebView с yacd, секрет API подставлен | ui/WebviewFragment.kt:33-71 | D-TOOLS-02, D-CONN-01 | |
| «Установить URL панели» (`action_set_url`) | menu/yacd_menu.xml:4; ui/WebviewFragment.kt:82-95 | D-TOOLS-02 | |
| «Скопировать ключ API» (`action_copy_secret`) | menu/yacd_menu.xml:7; ui/WebviewFragment.kt:96-100 | D-TOOLS-02 | |
| «Закрыть» (`close`) | menu/yacd_menu.xml:10; ui/WebviewFragment.kt:101-104 | D-TOOLS-02 | открывает старый список профилей (`nav_configuration`), а «назад» ведёт в журнал (ui/MainActivity.kt:324) |

## 19. Диалоги на Views, которые вызывают и новые экраны

### 19.1 ui/Dialogs.kt, ui/ImportReview.kt

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «PIN приглашения на …»: 6 цифр, остаток попыток после неверного | ui/Dialogs.kt:37-64 | D-ADD-06 | |
| Вопрос перед импортом: имена, «переписывает весь конфиг», верхние ключи | ui/ImportReview.kt:21-43 | D-ADD-10 | |
| `Dialogs.logExceptionAndShow` (ошибка с «OK») | ui/Dialogs.kt:18-30 | DEAD | нигде не вызывается (grep по `app/src`) |
| `Dialogs.message` (выделяемый текст) | ui/Dialogs.kt:66-77 | DEAD | нигде не вызывается |

### 19.2 ui/RayLanDialog.kt: «Локальная сеть» узла

**Перенесено** в arngory/niko#165 (`ui/node/LanScreen.kt`, P-Lan): страница вместо диалога; каждое изменение сохраняется сразу, а не по «OK»; порты и способ узнавать сеть — в листах.

Его открывают P-Node (ui/node/NodeFragment.kt:187), P-Subs и старый список групп.

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| «Показывать это устройство моим другим устройствам» | ui/RayLanDialog.kt:45-48, 97-104 | D-NODE-13 | |
| Состояние: не предложено, нет профиля, ждём, скорость ретранслятора, устройства и их адреса или имена | ui/RayLanDialog.kt:51-72 | D-NODE-08 | |
| «Порты, открытые моим устройствам», с проверкой; ядро перезапускается | ui/RayLanDialog.kt:74-79, 86-95 | D-NODE-13 | |
| «Напрямую в доверенной Wi-Fi» (`lanDirect`) | ui/RayLanDialog.kt:117-121, 222-233 | GAP | lan-v1 §3; D-NODE-13 называет только две настройки |
| «Узнавать Wi-Fi по роутеру и DNS» / «по имени» (`lanDirectBy`) | ui/RayLanDialog.kt:122-133 | GAP | то же |
| Список доверенных Wi-Fi, «Забыть …» | ui/RayLanDialog.kt:157-176 | GAP | то же |
| «Доверять этой Wi-Fi» | ui/RayLanDialog.kt:139-142, 202-208 | GAP | то же |
| «Разрешить» доступ к местоположению (SSID), фоновое отдельно; подсказки «не Wi-Fi», «эта сеть доверенная», «местоположение выключено» | ui/RayLanDialog.kt:136-138, 178-201 | GAP | то же; M-STATE-PERM про местоположение молчит |

### 19.3 ui/RayNodeActions.kt: действия узла

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Меню узла «⋯» из карточки: заголовок — имя узла | ui/RayNodeActions.kt:27-49; ui/node/NodeFragment.kt:172 | D-NODE-* | |
| ⋯ › «Последний прогон: что нашёл, журнал»; «Поделиться журналом» | ui/RayNodeActions.kt:35-40, 172-199 | D-WHY-08 | |
| ⋯ › «Сводка и подсказки»: что ушло админу, подсказки сети (порядок, избегать, UDP заблокирован, замирание, MTU) | ui/RayNodeActions.kt:41-42, 293-324 | D-NODE-12, D-SET-17 | |
| ⋯ › «Удалить ноду», с подтверждением | ui/RayNodeActions.kt:43, 326-338 | D-NODE-07 | |
| ⋯ в списке (`card = false`): «Управление нодой», «Обновить подписку», «Проверить канал», «Тестовый прогон» | ui/RayNodeActions.kt:29-34 | DEAD | `showRayNodeActions` вызывается только с `card = true` (ui/node/NodeFragment.kt:172); эти четыре действия есть на P-Node своими строками |
| `updateRaySubscription`: «Обновляю подписку…» | ui/RayNodeActions.kt:100-108 | DEAD | вызывается только из мёртвой ветки выше |
| Обновить узел (подписку узла), с ходом | ui/RayNodeActions.kt:59-80 | D-NODE-02 | вызывают P-Node, P-Subs и старый список групп |
| «Обновить все подписки?» | ui/RayNodeActions.kt:82-98 | D-SUB-07 | |
| «Тестовый прогон»: номер, проверка номера, ход, «что нашёл прогон» | ui/RayNodeActions.kt:110-170 | D-WHY-02…07 | вызывает P-Node (`why`) |
| «Проверить канал»: прямой путь (SPA, MTU UDP, квота), способы, пиры Yggdrasil | ui/RayNodeActions.kt:201-278 | D-NODE-11 | вызывает P-Node (`channel`) |

## 20. Мёртвое без экрана: ресурсы и классы

| Что | Файл:строка | Куда | Заметка |
|---|---|---|---|
| Форма балансировщика: «Тип» (List, группы), «Стратегия» (Random, Ping), «Группы» (`balancerType`, `balancerStrategy`, `balancerGroup`) | xml/balancer_preferences.xml:5-31; widget/GroupPreference.kt | DEAD | на `R.xml.balancer_preferences` ничто не ссылается; GroupPreference используется только там |
| Форма NekoBox-плагина: «Имя профиля» (`name`) | xml/neko_preferences.xml:5 | DEAD | на `R.xml.neko_preferences` ничто не ссылается |
| Профиль NekoBox-плагина (`TYPE_NEKO`): нет «Поделиться», нет редактора | ui/ConfigurationFragment.kt:1726, 1758; database/ProxyEntity.kt:481 | DEAD | создать его нельзя (есть только чтение старых данных, fmt/KryoConverters.java:179); кнопка «Изменить» у такого профиля упадёт с `IllegalArgumentException` |
| ICMP-пинг (`pingTest(icmpPing = true)`) | ui/ConfigurationFragment.kt:782-830 | DEAD | вызывается только с `false` (:675); ветка помечена «removed» |
| layout_debug: «Crash From Kt», «Reset Settings» | layout/layout_debug.xml | DEAD | ни одной ссылки |
| layout_mtu_help (`mtu_help`) | layout/layout_mtu_help.xml | DEAD | ни одной ссылки |
| layout_edit_group: имя группы, ссылка подписки, дедупликация | layout/layout_edit_group.xml | DEAD | ни одной ссылки; группа уже на Compose (P-SubSettings) |
| layout_icon_list_item_2, layout_loading | layout/layout_icon_list_item_2.xml, layout/layout_loading.xml | DEAD | ни одной ссылки |
| LongClickMenuPreference, LongClickSwitchPreference, LinkOrContentPreference | ui/LongClickMenuPreference.kt:9; ui/LongClickSwitchPreference.kt:11; widget/LinkOrContentPreference.kt | DEAD | не используются ни в xml, ни в коде |

---

## Пробелы

Это пункты, которые старые экраны умеют, но которых нет ни в одном ID и ни на одном макете. Если удалить старый
экран, не дописав их в макеты, они пропадут.

**Решено владельцем 2026-11-24: все остаются**, каждому — ID и место (они же в [описи функций](functions.md),
раздел 10). Экран, который их переносит, ставит в строке пробела «перенесено» и номер PR.

| Пробел | ID | Что | Где |
|---|---|---|---|
| 1 | **M-STATE-PREVIEW** | Диалог «предварительная версия» при первом запуске такой сборки | диалог поверх главной |
| 2 | **M-SET-04** | «Нет плагина»: загрузка из Play, F-Droid или по ссылке | диалог, рядом с доверием плагину M-SET-03 |
| — | **D-MAIN-18, D-MAIN-03, D-MAIN-14** | Проверка соединения и IP выхода (сейчас только с нижней панели) | карточка состояния в «Расширенном» (P-Advanced): нажатие — проверка, IP выхода — в её меню |
| 3 (перенесено, arngory/niko#NNN) | **D-PROFILE-05** | Прокрутка к выбранному профилю по нажатию на заголовок | лист конфигов (P-Configs) |
| 4 (перенесено, arngory/niko#NNN) | **D-SUB-10** | Порядок групп | вкладка «Подписки» (P-Subs): ⋮ группы › «Выше» / «Ниже» |
| 5 (перенесено, arngory/niko#164) | **D-PICK-01** | Выбрать профиль: для цепочки, выхода правила, front и landing proxy группы | лист конфигов в режиме выбора, без своего экрана |
| 6 (перенесено, arngory/niko#152) | **D-RULES-09** | Предупреждение над правилами со ссылкой на руководство по маршрутизации | заголовок экрана правил |
| 7–9 (перенесено, arngory/niko#152) | **D-CHECK-08** | Проверка маршрута: «Из приложения», «UDP», «Спросить DNS ядра» | проверка маршрута (D-CHECK-02), под «Ещё» |
| 10–11 (перенесено, arngory/niko#156) | **M-RULES-02** | Выбор приложений для правила: инвертировать, очистить, экспорт и импорт через буфер, «нет доступа к списку» и «открыть настройки» | экран приложений (M-RULES-01) в режиме выбора, редкое — в меню ⋮ |
| 12–15 (перенесено, arngory/niko#156) | **M-RULES-03** | Приложения через VPN: автоподбор прокси-приложений, очистить выбранное, импорт из буфера, «нет доступа к списку» | меню ⋮ экрана приложений (M-RULES-01) |
| 16–17 (перенесено, arngory/niko#161) | **M-ADD-02** | Сканер: фонарик, разрешение на камеру и что делать при отказе | экран сканера (M-ADD-01) |
| 18–22 (перенесено, arngory/niko#157) | **D-SET-34** | «Проект»: исходный код, лицензии (список, строка, текст, «Сайт»), оригинальный проект NekoBox, текст GPL-3.0, «Поддержать проект» | страница «О программе» (D-SET-12); нужно по GPL |
| 23 | **D-ADMIN-22** | Предупреждения ноды (raycontrol v0.35.1) | вкладка «Статус» панели администратора (B-AdminStatus) |
| 24 (перенесено, arngory/niko#165) | **D-NODE-14** | Локальная сеть узла: «Напрямую в доверенной Wi-Fi», как узнавать сеть, доверенные сети с «Забыть», «Доверять этой Wi-Fi», разрешение на местоположение | страница локальной сети узла, развёрнутая из D-NODE-13 |

**Главная и нижняя панель**

1. Диалог «предварительная версия» при запуске (ui/MainActivity.kt:368-374).
2. «Нет плагина»: загрузка из Play, F-Droid или по ссылке (ui/MainActivity.kt:582-633). M-SET-03 описывает только
   «доверять» и «отозвать».

Два пункта покрыты ID, но на Compose ещё не сделаны, а кроме панели их нигде нет: проверка соединения по нажатию на
полосу (**D-MAIN-18**) и «IP выхода» по долгому нажатию (**D-MAIN-03 и 14**). Пока их нет на P-Simple и P-Advanced,
панель снимать нельзя.

**Профили и группы**

3. Прокрутка к выбранному профилю по нажатию на заголовок списка (ui/ConfigurationFragment.kt:265-287). Мелочь.
4. Порядок групп перетаскиванием (ui/GroupFragment.kt:78-108). В P-Subs этого нет.
5. Экран «Выбрать профиль» для цепочки, выхода правила и front или landing proxy группы (ui/ProfileSelectActivity.kt,
   два пункта). Своего ID и макета нет; можно взять лист P-Configs.

**Маршрутизация**

6. Ссылка на руководство по маршрутизации с предупреждением над правилами (ui/RouteFragment.kt:300-309). Может войти в
   FAQ D-SET-31.
7. Проверка маршрута: поле «Из приложения» (ui/RouteTest.kt:37-52).
8. Проверка маршрута: флажок «UDP» (ui/RouteTest.kt:53).
9. Проверка маршрута: флажок «Спросить DNS ядра» (ui/RouteTest.kt:56-59). D-CHECK-02 называет только поле адреса.
10. Выбор приложений для правила: «Инвертировать», «Очистить», «Экспорт в буфер», «Импорт из буфера»
    (ui/AppListActivity.kt:241-300, четыре пункта).
11. Выбор приложений для правила: «Нет доступа к списку приложений» и «Открыть настройки приложения»
    (ui/AppListActivity.kt:197-203).

**Приложения через VPN (M-RULES-01)**

12. «Автоматический выбор прокси-приложений» (ui/AppManagerActivity.kt:337-389). Может уступить место D-RULES-07.
13. «Очистить выбранное» (ui/AppManagerActivity.kt:287-296).
14. «Импорт из буфера» (ui/AppManagerActivity.kt:309-332). В описи назван только экспорт.
15. «Нет доступа к списку приложений» и «Открыть настройки приложения» (ui/AppManagerActivity.kt:203-209).

**Сканер**

16. Фонарик (ui/ScannerActivity.kt:219-223).
17. Запрос разрешения на камеру и что делать при отказе (ui/ScannerActivity.kt:198-250). M-STATE-PERM камеру не
    называет.

**О программе и лицензии**

18. «Исходный код» (GitHub) (ui/AboutFragment.kt:248-255).
19. «Лицензии» (ui/AboutFragment.kt:256-264).
20. «Оригинальный проект: NekoBox for Android» (ui/AboutFragment.kt:265-274).
21. Текст лицензии GPL-3.0 (layout/layout_about.xml:83-87). Пункты 18–21 юридически нужны для форка по GPL.
22. Экраны лицензий: список по разделам, строка, текст, «Сайт» (ui/LicensesActivity.kt, четыре пункта). Ни ID, ни
    макета.

**Админ узла**

23. «Предупреждения ноды» (raycontrol v0.35.1) на вкладке «Статус» (ui/RayAdminActivity.kt:256-257). D-ADMIN-10 их
    не называет.

**Локальная сеть узла (lan-v1 §3)**

24. «Напрямую в доверенной Wi-Fi», способ узнавать сеть (по роутеру и DNS или по имени), список доверенных сетей с
    «Забыть», «Доверять этой Wi-Fi», разрешение на местоположение и подсказки (ui/RayLanDialog.kt:113-233, пять
    пунктов). D-NODE-13 называет только две настройки.

Мелкие оговорки при покрытых ID; GAP они не получили, но в макеты их стоит дописать:

- ~~полный текст ошибки по нажатию на состояние профиля (ui/ConfigurationFragment.kt:1694-1700)~~ (в P-Configs, arngory/niko#NNN);
- ~~поле «STUN сервер» (layout/layout_stun.xml:66)~~ (на P-NAT, arngory/niko#160);
- ~~в P-Configs из проверок группы есть только URL-тест, TCP-пинга нет~~ (в ⋮ группы, arngory/niko#NNN);
- ~~подписи «Скорость отдачи» и «Скорость приёма» у Hysteria по-русски одинаковы: «Максимальная скорость загрузки»~~
  (исправлено вместе с этой описью: отдачи и загрузки);
- у нового профиля пункты «Пользовательский исходящий JSON» и «Пользовательская конфигурация JSON» ничего не делают
  (ui/profile/ProfileSettingsActivity.kt:472, 488);
- редактор правила при сохранении без правок пишет «Установите какие-либо правила» (ui/RouteSettingsActivity.kt:268-276);
- ~~«Закрыть» в панели sing-box открывает старый список профилей (ui/WebviewFragment.kt:101-104)~~ (пункт снят, arngory/niko#160).

## Итог

Строка таблицы — один пункт. Строку «Вручную › 17 протоколов» в разделе 2 я считал одним пунктом. «Покрыто» значит, что у пункта есть ID, в том числе ID, который на Compose ещё не сделан.

| Экран | Пунктов | Покрыто ID | GAP | DEAD | СНЯТЬ |
|---|---|---|---|---|---|
| 1. MainActivity: панель и вопросы о ссылках | 17 | 14 | 2 | 0 | 1 |
| 2. ConfigurationFragment | 29 | 28 | 1 | 0 | 0 |
| 3. GroupFragment | 20 | 18 | 1 | 1 | 0 |
| 4. ProfileSettingsActivity (общее) | 28 | 27 | 0 | 1 | 0 |
| 5.1 SOCKS | 7 | 7 | 0 | 0 | 0 |
| 5.2 HTTP, VMess, VLESS, Trojan | 33 | 33 | 0 | 0 | 0 |
| 5.3 Shadowsocks | 8 | 8 | 0 | 0 | 0 |
| 5.4 Trojan-Go | 13 | 13 | 0 | 0 | 0 |
| 5.5 Mieru | 10 | 10 | 0 | 0 | 0 |
| 5.6 naive | 12 | 11 | 0 | 1 | 0 |
| 5.7 Hysteria | 19 | 19 | 0 | 0 | 0 |
| 5.8 SSH | 9 | 9 | 0 | 0 | 0 |
| 5.9 WireGuard | 11 | 11 | 0 | 0 | 0 |
| 5.10 TUIC | 13 | 13 | 0 | 0 | 0 |
| 5.11 ShadowTLS | 10 | 10 | 0 | 0 | 0 |
| 5.12 AnyTLS | 9 | 9 | 0 | 0 | 0 |
| 5.13 Свой конфиг | 3 | 3 | 0 | 0 | 0 |
| 5.14 Цепочка | 8 | 8 | 0 | 0 | 0 |
| 6. ConfigEditActivity | 8 | 8 | 0 | 0 | 0 |
| 7. RouteFragment | 14 | 13 | 1 | 0 | 0 |
| 8. RouteSettingsActivity | 17 | 15 | 0 | 2 | 0 |
| 9.1 RouteTest | 10 | 7 | 3 | 0 | 0 |
| 9.2 ProfileSelectActivity | 2 | 0 | 2 | 0 | 0 |
| 9.3 AppListActivity | 9 | 4 | 5 | 0 | 0 |
| 10. ScannerActivity | 9 | 7 | 2 | 0 | 0 |
| 11. AssetsActivity | 10 | 10 | 0 | 0 | 0 |
| 12. AppManagerActivity | 14 | 10 | 4 | 0 | 0 |
| 13. AboutFragment | 18 | 14 | 4 | 0 | 0 |
| 14. Лицензии | 4 | 0 | 4 | 0 | 0 |
| 15. BackupFragment | 10 | 10 | 0 | 0 | 0 |
| 16. RayAdminActivity | 50 | 49 | 1 | 0 | 0 |
| 17. StunActivity | 5 | 5 | 0 | 0 | 0 |
| 18. WebviewFragment | 4 | 4 | 0 | 0 | 0 |
| 19.1 Dialogs, ImportReview | 4 | 2 | 0 | 2 | 0 |
| 19.2 RayLanDialog | 8 | 3 | 5 | 0 | 0 |
| 19.3 RayNodeActions | 10 | 8 | 0 | 2 | 0 |
| 20. Мёртвое без экрана | 9 | 0 | 0 | 9 | 0 |
| **Всего** | **474** | **420** | **35** | **18** | **1** |

Из 35 GAP юридически нужны пять (пункты 18–22 в «Пробелах»: исходный код, лицензии, указание на NekoBox, текст GPL);
больше всего пробелов в «Локальной сети» узла (прямая связь в доверенной Wi-Fi, lan-v1 §3) и в выборе приложений.
Два пункта покрыты ID, но кроме нижней панели их нигде нет: D-MAIN-18 (проверка соединения) и D-MAIN-03/14 (IP выхода).

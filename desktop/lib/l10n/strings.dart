import 'dart:ui' show Locale;

import '../daemon/daemon.dart' show NatBehaviour;

/// The window's words, in Russian and English. A stopgap: the texts move to the shared layer with Android's
/// (docs/ui/platforms.md, one file a language), and this file is then generated from it.
class NikoStrings {
  const NikoStrings._(this._ru);

  factory NikoStrings.of(Locale locale) => NikoStrings._(locale.languageCode == 'ru');

  final bool _ru;

  String get statusOff => _ru ? 'Отключено' : 'Disconnected';
  String get statusConnecting => _ru ? 'Подключение' : 'Connecting';
  String get statusOn => _ru ? 'Подключено' : 'Connected';
  String get statusStopping => _ru ? 'Отключение' : 'Disconnecting';
  String get statusError => _ru ? 'Ошибка' : 'Error';

  String get connect => _ru ? 'Подключить' : 'Connect';
  String get disconnect => _ru ? 'Отключить' : 'Disconnect';
  String get retry => _ru ? 'Повторить' : 'Retry';

  String get errorTitle => _ru ? 'Ошибка подключения' : 'Connection error';
  String get noServiceTitle => _ru ? 'Служба не установлена' : 'The service is not installed';
  String get serviceSilentTitle => _ru ? 'Служба не отвечает' : 'The service does not answer';
  String get noConfigTitle => _ru ? 'Нет конфига' : 'No config';

  String get sections => _ru ? 'Разделы' : 'Sections';
  String get sectionHome => _ru ? 'Главная' : 'Home';
  String get sectionConnections => _ru ? 'Соединения' : 'Connections';
  String get sectionLog => _ru ? 'Журнал' : 'Log';
  String get sectionNodes => _ru ? 'Узлы' : 'Nodes';

  String get groups => _ru ? 'Группы' : 'Groups';
  String get groupOwn => _ru ? 'Свои профили' : 'My profiles';
  String groupOwnSub(int count) => _ru ? 'вручную и по ссылкам · $count' : 'by hand and from links · $count';
  String groupBasicSub(int count) => _ru ? 'своя группа · $count' : 'own group · $count';
  String groupSubscriptionSub(int count) => _ru ? 'подписка · $count' : 'subscription · $count';
  String get groupEmpty => _ru ? 'В группе нет профилей' : 'No profiles in this group';
  String get profilesList => _ru ? 'Профили группы' : 'The group\'s profiles';
  String get storeUnreachable => _ru ? 'Профили не прочитаны' : 'The profiles were not read';
  String get storeNoLink => _ru ? 'Нет связи со службой' : 'No link to the service';
  String get add => _ru ? 'Добавить' : 'Add';
  String get addOverline => _ru ? 'Добавление' : 'Adding';
  String get addTitle => _ru ? 'Вставьте ссылку' : 'Paste a link';
  String get addSub => _ru ? 'Ссылка на профиль или несколько, по одной в строке' : 'A profile\'s link, or several, one a line';
  String get addField => _ru ? 'Ссылка' : 'Link';
  String get addPaste => _ru ? 'Вставить' : 'Paste';
  String addFound(int count) => _ru ? 'Профилей в ссылке: $count' : 'Profiles in the link: $count';
  String addButton(int count) => _ru ? 'Добавить: $count' : 'Add $count';
  String get addSubscriptionFound => _ru ? 'Это ссылка подписки' : 'This is a subscription\'s link';
  String get addSubscriptionText => _ru
      ? 'Добавится группа подписки, профили придут с её первым обновлением'
      : 'A group of the subscription is added, its profiles come with its first update';
  String get addSubscriptionButton => _ru ? 'Добавить подписку' : 'Add the subscription';
  String get addNodeInvite => _ru ? 'Приглашение узла' : 'A node\'s invite';
  String get addNodeDevice => _ru ? 'Пакет устройства узла' : 'A node\'s device package';
  String addNodeInviteText(String host) => _ru
      ? 'Компьютер зарегистрируется на узле $host и получит его способы подключения'
      : 'This computer registers with the node $host and gets its ways to connect';
  String addNodeDeviceText(String host) =>
      _ru ? 'Узел $host добавится с этим пакетом устройства' : 'The node $host is added with this device package';
  String addNodeName(String name) => _ru ? 'имя узла: $name' : 'the node\'s name: $name';
  String addNodeExpires(String at) => _ru ? 'приглашение действует до $at' : 'the invite works until $at';
  String get addNodeReplaces => _ru
      ? 'Этот узел уже добавлен: у устройства будет новый ключ, его группа и профили останутся'
      : 'This node is added already: the device gets a new key, its group and profiles stay';
  String addNodeMoved(String known, String host) => _ru
      ? 'Узел добавлен с адресом $known, приглашение — на $host. Узел мог переехать; если ссылка не от его администратора, не добавляйте'
      : 'The node is added with the address $known, the invite is for $host. The node may have moved; if the link is not its administrator\'s, do not add it';
  String get addNodePin => _ru ? 'PIN приглашения' : 'The invite\'s PIN';
  String get addNodePinHint => _ru ? '6 цифр от администратора узла' : '6 digits from the node\'s administrator';
  String get addNodePinFormat => _ru ? 'PIN — 6 цифр' : 'The PIN is 6 digits';
  String addNodePinWrong(int left) =>
      _ru ? 'PIN неверный, попыток осталось: $left' : 'The PIN is wrong, tries left: $left';
  String addNodePinLeft(int left) => _ru ? 'Попыток: $left' : 'Tries: $left';
  String get addNodeButton => _ru ? 'Добавить узел' : 'Add the node';
  String get addNodeRegistering => _ru ? 'Регистрация на узле…' : 'Registering with the node…';
  String groupNodeSub(int count) => _ru ? 'узел · $count' : 'node · $count';
  String get nodeNever => _ru ? 'подписка ещё не получена' : 'no subscription yet';
  String nodeUpdated(String at) => _ru ? 'обновлено $at' : 'updated $at';
  String nodeNext(String at) => _ru ? 'следующее обновление $at' : 'next update $at';
  String get nodeSigned => _ru
      ? 'Подписка заверена ключами узла ed25519 и ML-DSA-65, обе подписи проверены'
      : 'The subscription is signed by the node\'s ed25519 and ML-DSA-65 keys, both signatures checked';
  String nodeTraffic(String used, String limit) => _ru ? 'трафик $used из $limit' : 'traffic $used of $limit';
  String nodeTrafficReset(String at) => _ru ? 'сброс $at' : 'reset $at';
  String nodeTrafficExhausted(String at) => _ru
      ? 'трафик израсходован, профили вернутся $at'
      : 'the traffic is used up, the profiles come back $at';
  String get nodeTrafficExhaustedAdmin =>
      _ru ? 'трафик израсходован: лимит поднимает администратор' : 'the traffic is used up: the administrator raises the limit';
  String nodeExpires(String at) => _ru ? 'подписка действует до $at' : 'the subscription holds until $at';
  String nodeExpired(String at) => _ru ? 'подписка истекла $at' : 'the subscription expired $at';
  String get nodeRefused => _ru
      ? 'Узел отказал этому устройству: добавьте узел заново по новому приглашению'
      : 'The node refused this device: add it again by a new invite';
  String nodeFailing(int times) => _ru ? 'обновление не проходит: $times раз подряд' : 'the update fails: $times times in a row';
  String nodeClock(int skew) => _ru
      ? 'часы компьютера расходятся с узлом на $skew с: подключение по SPA не пройдёт'
      : 'this computer\'s clock is $skew s off the node\'s: SPA will fail';
  String nodeMinVersion(String version) =>
      _ru ? 'узел требует Niko $version или новее' : 'the node requires Niko $version or later';
  String get nodeUnreadable => _ru
      ? 'Ключи узла запечатаны на другом компьютере: добавьте узел заново по новому приглашению'
      : 'The node\'s keys were sealed on another computer: add it again by a new invite';
  String get mainNode => _ru ? 'Узел' : 'Node';
  String get nodeDelete => _ru ? 'Удалить узел' : 'Remove the node';
  String nodeDeleteConfirm(String name) => _ru
      ? 'Удалить узел «$name» и его ключ устройства? Вернуть его можно только новым приглашением.'
      : 'Remove the node “$name” and its device key? It comes back only with a new invite.';
  String get nodeUpdateFailed => _ru ? 'Узел не обновлён' : 'The node was not updated';
  String get subscriptionUpdate => _ru ? 'Обновить' : 'Update';
  String get subscriptionUpdating => _ru ? 'Обновление…' : 'Updating…';
  String get subscriptionUpdateFailed => _ru ? 'Подписка не обновлена' : 'The subscription was not updated';
  String get subscriptionNever => _ru ? 'ещё не обновлялась' : 'not updated yet';
  String subscriptionUpdated(String at) => _ru ? 'обновлена $at' : 'updated $at';
  String subscriptionTraffic(String used, String total) => _ru ? '$used из $total' : '$used of $total';
  String subscriptionUsed(String used) => _ru ? 'израсходовано $used' : '$used used';
  String subscriptionExpires(String date) => _ru ? 'до $date' : 'until $date';
  String get addNothing => _ru ? 'Ссылок на профили не найдено' : 'No profile links found';
  String get addFailed => _ru ? 'Не добавлено' : 'Not added';
  String get cancel => _ru ? 'Отмена' : 'Cancel';
  String profileDelete(String name) => _ru ? 'Удалить «$name»' : 'Delete “$name”';
  String profileShare(String name) => _ru ? 'Скопировать ссылку на «$name»' : 'Copy the link to “$name”';
  String linkCopied(String name) => _ru ? 'Ссылка на «$name» скопирована' : 'The link to “$name” copied';
  String profileDeleted(String name) => _ru ? 'Профиль «$name» удалён' : 'Profile “$name” deleted';
  String get undo => _ru ? 'Вернуть' : 'Undo';
  String get view => _ru ? 'Вид интерфейса' : 'Interface view';
  String get viewSimple => _ru ? 'Простой' : 'Simple';
  String get viewAdvanced => _ru ? 'Расширенный' : 'Advanced';

  String get logCore => _ru ? 'Журнал ядра' : 'The core\'s log';
  String get logClear => _ru ? 'Очистить журнал' : 'Clear the log';
  String get logEmpty => _ru ? 'Журнал пуст' : 'The log is empty';
  String get logClearFailed => _ru ? 'Журнал не очищен' : 'The log was not cleared';
  String get logCopy => _ru ? 'Скопировать журнал' : 'Copy the log';
  String get logCopied => _ru ? 'Журнал скопирован' : 'The log is copied';
  String get logSave => _ru ? 'Сохранить журнал в файл' : 'Save the log to a file';
  String get tools => _ru ? 'Инструменты' : 'Tools';
  String get natTitle => _ru ? 'Поведение NAT' : 'NAT behaviour';
  String get stunServer => _ru ? 'STUN-сервер' : 'STUN server';
  String get stunDetect => _ru ? 'Определить' : 'Detect';
  String get stunNeedsCore =>
      _ru ? 'Проверка идёт через подключение: сначала подключитесь' : 'The check goes through the connection: connect first';
  String get stunFailed => _ru ? 'NAT не определён' : 'NAT not detected';
  String get stunStopped => _ru
      ? 'Подключение остановилось во время проверки: подключитесь и проверьте снова'
      : 'The connection stopped during the check: connect and check again';
  String get natAddress => _ru ? 'Внешний адрес' : 'External address';
  String get natMapping => _ru ? 'Сопоставление' : 'Mapping';
  String get natFiltering => _ru ? 'Фильтрация' : 'Filtering';
  String get natLatency => _ru ? 'Задержка' : 'Latency';
  String get natUnsupported => _ru ? 'сервер говорит только адрес' : 'the server tells the address only';
  String nat(NatBehaviour behaviour) => switch (behaviour) {
        NatBehaviour.endpointIndependent => _ru ? 'не зависит от адреса назначения' : 'independent of the destination',
        NatBehaviour.addressDependent => _ru ? 'зависит от адреса' : 'depends on the address',
        NatBehaviour.addressAndPortDependent => _ru ? 'зависит от адреса и порта' : 'depends on the address and port',
        NatBehaviour.unknown => _ru ? 'не определено' : 'not known',
      };
  String get saved => _ru ? 'Сохранено' : 'Saved';
  String get saveFailed => _ru ? 'Не сохранено' : 'Not saved';
  String get reportCrash => _ru ? 'Прошлый запуск завершился сбоем' : 'The last run crashed';
  String get reportMemory => _ru ? 'Ядру не хватило памяти' : 'The core ran out of memory';
  String get reportSave => _ru ? 'Экспорт' : 'Export';
  String get reportFailed => _ru ? 'Отчёт не сохранён' : 'The report was not saved';

  String connectionsOpen(int count) => _ru ? 'открыто $count' : '$count open';
  String get connectionsList => _ru ? 'Открытые соединения' : 'Open connections';
  String get connectionsSearch => _ru ? 'Поиск' : 'Search';
  String get connectionsCloseAll => _ru ? 'Закрыть все' : 'Close all';
  String connectionClose(String name) => _ru ? 'Закрыть соединение с $name' : 'Close the connection to $name';
  String get connectionsIdle => _ru ? 'Подключение выключено' : 'Not connected';
  String get connectionsCloseFailed => _ru ? 'Соединение не закрыто' : 'The connection was not closed';
  String get rule => _ru ? 'правило' : 'rule';
  String get ruleFinal => _ru ? 'остальное' : 'final';

  String get development => _ru ? 'Разработка' : 'Development';
  String get service => _ru ? 'Служба' : 'Service';
  String get config => _ru ? 'Конфиг' : 'Config';
  String get session => _ru ? 'За сессию' : 'Session';

  String get sectionSettings => _ru ? 'Настройки' : 'Settings';
  String get settingsMenu => _ru ? 'Разделы настроек' : 'Settings sections';
  String get settingsUnread => _ru ? 'Настройки не прочитаны' : 'The settings were not read';
  String get settingsNotKept => _ru ? 'Не сохранено' : 'Not saved';
  String get save => _ru ? 'Сохранить' : 'Save';
  String get setGeneral => _ru ? 'Общие' : 'General';
  String get setMode => _ru ? 'Режим' : 'Mode';
  String get setModeVpn => 'VPN';
  String get setModeProxy => _ru ? 'Прокси' : 'Proxy';
  String get setSystemProxy => _ru ? 'Системный прокси' : 'System proxy';
  String get setSystemProxySub => _ru ? 'В режиме прокси' : 'In proxy mode';
  String get setConnection => _ru ? 'Подключение' : 'Connection';
  String get setTun => _ru ? 'Реализация TUN' : 'TUN implementation';
  String get setMtu => 'MTU';
  String get setMtuHint => _ru ? 'От 1000 до 10000' : 'From 1000 to 10000';
  String get setIpv6 => 'IPv6';
  String get setIpv6Off => _ru ? 'Нет' : 'No';
  String get setIpv6On => _ru ? 'Да' : 'Yes';
  String get setIpv6Prefer => _ru ? 'Сначала' : 'First';
  String get setIpv6Only => _ru ? 'Только' : 'Only';
  String get setInsecure => _ru ? 'Не проверять сертификаты' : 'Do not check certificates';
  String get setDangerous => _ru ? 'Опасно' : 'Dangerous';
  String get setDns => _ru ? 'DNS и маршрут' : 'DNS and routing';
  String get setRemoteDns => _ru ? 'Удалённый DNS' : 'Remote DNS';
  String get setDirectDns => _ru ? 'Прямой DNS' : 'Direct DNS';
  String get setDnsHint => _ru ? 'Сервер: https://, tls://, quic://, udp:// или local' : 'A server: https://, tls://, quic://, udp:// or local';
  String get setRemoteDnsHint => _ru
      ? 'По серверу в строке: https://, tls://, quic:// или udp://. Когда сервер не отвечает или отвечает ошибкой, спрашивается следующий'
      : 'A server a line: https://, tls://, quic:// or udp://. When a server fails or keeps silent, the next one is asked';
  String get setFakeIp => 'FakeIP';
  String get setInVpn => _ru ? 'В режиме VPN' : 'In VPN mode';
  String get setStrategy => _ru ? 'Стратегия доменов' : 'Domain strategy';
  String get setStrategyRemote => _ru ? 'Для удалённого DNS' : 'For the remote DNS';
  String get setStrategyDirect => _ru ? 'Для прямого DNS' : 'For the direct DNS';
  String get setStrategyServer => _ru ? 'Для адресов серверов' : 'For the servers\' addresses';
  String get setStrategyMixed => _ru ? 'разные' : 'mixed';
  String strategy(String value) => switch (value) {
        'prefer_ipv4' => _ru ? 'IPv4 сначала' : 'IPv4 first',
        'prefer_ipv6' => _ru ? 'IPv6 сначала' : 'IPv6 first',
        'ipv4_only' => _ru ? 'Только IPv4' : 'IPv4 only',
        'ipv6_only' => _ru ? 'Только IPv6' : 'IPv6 only',
        _ => _ru ? 'Авто' : 'Auto',
      };
  String get setOptimisticCache => _ru ? 'Оптимистичный кэш' : 'Optimistic cache';
  String get setLanDirect => _ru ? 'Локальная сеть напрямую' : 'The LAN direct';
  String get setInbound => _ru ? 'Вход для других программ' : 'Inbound for other programs';
  String get setInboundMenu => _ru ? 'Вход для программ' : 'Inbound';
  String get setLogin => _ru ? 'Пользователь и пароль' : 'User and password';
  String get setLoginNone => _ru ? 'не заданы' : 'not set';
  String get setLoginSet => _ru ? 'заданы' : 'set';
  String get setLoginHint =>
      _ru ? 'Без пароля вход открыт. Пустой пользователь — user' : 'With no password, no login is asked. An empty user is “user”';
  String get setLanAccess => _ru ? 'Пускать из локальной сети' : 'Let the LAN in';
  String get setExperiments => _ru ? 'Опыты' : 'Experiments';
  String get setRefuseQuic => _ru ? 'QUIC → TCP на потоковых профилях' : 'QUIC → TCP on stream profiles';
  String get setNoPingReply => _ru ? 'Не отвечать на ping за сервер' : 'No ping reply for the server';
  String get setSniffing => _ru ? 'Сниффинг' : 'Sniffing';
  String get setResolveDestination => _ru ? 'Разрешать адрес назначения' : 'Resolve the destination';
  String get setPrograms => _ru ? 'Программы' : 'Programs';
  String get setProgramsThrough => _ru ? 'Через Niko' : 'Through Niko';
  String get setProgramsAll => _ru ? 'Все' : 'All';
  String get setProgramsOnly => _ru ? 'Только выбранные' : 'Only chosen';
  String get setProgramsExcept => _ru ? 'Кроме выбранных' : 'All but chosen';
  String get setProgramsList => _ru ? 'Выбранные программы' : 'Chosen programs';
  String get setProgramsNone => _ru ? 'не выбраны' : 'none';
  String programsCount(int count) {
    if (!_ru) return count == 1 ? '1 program' : '$count programs';
    final tens = count % 100, ones = count % 10;
    final word = tens >= 11 && tens <= 14
        ? 'программ'
        : ones == 1
            ? 'программа'
            : ones >= 2 && ones <= 4
                ? 'программы'
                : 'программ';
    return '$count $word';
  }

  String get setProgramsHint => _ru
      ? 'По одной в строке: имя (firefox.exe) или полный путь. Остальные программы идут напрямую или через Niko'
      : 'One a line: a name (firefox.exe) or a full path. The other programs go direct or through Niko';
  String get setLog => _ru ? 'Журнал' : 'Log';
  String get setLogLevel => _ru ? 'Уровень журнала' : 'Log level';

  String get inboundTitle => _ru ? 'Вход' : 'Inbound';
  String get inboundUser => _ru ? 'Пользователь' : 'User';
  String get inboundPort => _ru ? 'Порт' : 'Port';
  String get inboundPassword => _ru ? 'Пароль' : 'Password';
  String get inboundNewPassword => _ru ? 'Новый пароль' : 'New password';
  String get inboundLan => _ru ? 'Доступ из локальной сети' : 'Access from the LAN';

  String get sectionRules => _ru ? 'Правила' : 'Rules';
  String get rulesList => _ru ? 'Правила маршрутизации' : 'Route rules';
  String get rulesEmpty => _ru
      ? 'Правил нет: всё идёт через прокси'
      : 'No rules: everything goes through the proxy';
  String get rulesSub =>
      _ru ? 'Сверху вниз: первое подошедшее правило решает' : 'From the top: the first rule that matches decides';
  String get rulesUnread => _ru ? 'Правила не прочитаны' : 'The rules were not read';
  String get ruleAdd => _ru ? 'Новое правило' : 'New rule';
  String ruleDisplayName(int id) => _ru ? 'Правило $id' : 'Rule $id';
  String ruleUpLabel(String name) => _ru ? 'Поднять: $name' : 'Move up: $name';
  String ruleDownLabel(String name) => _ru ? 'Опустить: $name' : 'Move down: $name';
  String ruleDeleteLabel(String name) => _ru ? 'Удалить правило: $name' : 'Delete the rule: $name';
  String ruleEnabledLabel(String name) => _ru ? 'Включено: $name' : 'On: $name';
  String get ruleEdit => _ru ? 'Правило' : 'Rule';
  String get ruleName => _ru ? 'Название' : 'Name';
  String get ruleDomains => _ru ? 'Домены' : 'Domains';
  String get ruleDomainsHint => _ru
      ? 'По одному в строке: суффикс, full:, domain:, keyword:, regexp:; srs:https://… — набор правил'
      : 'One a line: a suffix, full:, domain:, keyword:, regexp:; srs:https://… a rule-set';
  String get ruleIp => 'IP';
  String get rulePort => _ru ? 'Порт' : 'Port';
  String get ruleSourcePort => _ru ? 'Порт источника' : 'Source port';
  String get ruleSource => _ru ? 'Адрес источника' : 'Source address';
  String get ruleProtocol => _ru ? 'Протокол' : 'Protocol';
  String get ruleNetwork => _ru ? 'Сеть' : 'Network';
  String get ruleNetworkBoth => 'TCP + UDP';
  String get rulePrograms => _ru ? 'Программы' : 'Programs';
  String get ruleProgramsHint =>
      _ru ? 'По одной в строке: имя (firefox.exe) или полный путь' : 'One a line: a name (firefox.exe) or a full path';
  String get ruleOutbound => _ru ? 'Куда' : 'Where';
  String get routeProxy => _ru ? 'Прокси' : 'Proxy';
  String get routeDirect => _ru ? 'Напрямую' : 'Direct';
  String get routeBlock => _ru ? 'Блок' : 'Block';
  String routeProfile(int id) => _ru ? 'Профиль $id' : 'Profile $id';
  String get ruleDpi => _ru ? 'Обход DPI' : 'DPI bypass';
  String get ruleDpiSub => _ru
      ? 'Стратегия из настроек на прямых соединениях этого правила'
      : 'The settings\' strategy on this rule\'s direct connections';
  String get ruleApps => _ru ? 'приложения телефона' : 'the phone\'s apps';
  String get ruleOwnJson => _ru ? 'свой JSON' : 'own JSON';
  String get ruleSave => _ru ? 'Сохранить' : 'Save';
  String get ruleCancel => _ru ? 'Отмена' : 'Cancel';
  String get ruleNotSaved => _ru ? 'Правило не сохранено' : 'The rule was not saved';
  String get presetRussia => _ru ? 'Пресет «Россия»' : 'Preset: Russia';
  String get presetRussiaSub => _ru
      ? 'Заблокированное в России — через прокси, российские сервисы — напрямую (списки runetfreedom). Повторное добавление заменит правила пресета'
      : 'What is blocked in Russia through the proxy, Russian services directly (runetfreedom\'s lists). Adding it again replaces its rules';
  String get presetRussiaBlocked => _ru ? 'Заблокированное в России — через прокси' : 'Blocked in Russia: through the proxy';
  String get presetRussiaDirect => _ru ? 'Российские сервисы — напрямую' : 'Russian services: directly';
  String get ruleSetsTitle => _ru ? 'Наборы правил' : 'Rule-sets';
  String ruleSetsSub(int hours) => _ru
      ? 'Служба скачивает их сама и обновляет раз в $hours ч, пока ядро работает'
      : 'The service fetches them itself, and again every $hours h while the core runs';
  String get ruleSetsNone => _ru ? 'Правила не называют наборов' : 'No rule names a rule-set';
  String get ruleSetsUpdate => _ru ? 'Обновить' : 'Update';
  String get ruleSetsUpdating => _ru ? 'Обновление…' : 'Updating…';
  String get ruleSetsFailed => _ru ? 'Наборы не обновлены' : 'The rule-sets were not updated';
  String get ruleSetNotYet => _ru ? 'ещё не скачан' : 'not fetched yet';
  String get ruleSetNotHttps => _ru ? 'не https: не скачивается' : 'not https: never fetched';
  String get ruleSetOff => _ru ? 'правило выключено' : 'its rule is off';
  String ruleSetFetched(String size, String when) => _ru ? '$size, скачан $when' : '$size, fetched $when';
  String ruleSetKinds(int kinds) => switch (kinds) {
        1 => _ru ? 'домены' : 'domains',
        2 => _ru ? 'адреса' : 'addresses',
        3 => _ru ? 'домены и адреса' : 'domains and addresses',
        _ => '',
      };
}

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
  String get addNodeLater =>
      _ru ? 'Это приглашение узла: узлы придут в следующих выпусках' : 'This is a node\'s invite: nodes come in later releases';
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
}

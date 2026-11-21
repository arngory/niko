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

  String get inboundTitle => _ru ? 'Вход' : 'Inbound';
  String get inboundUser => _ru ? 'Пользователь' : 'User';
  String get inboundPort => _ru ? 'Порт' : 'Port';
  String get inboundPassword => _ru ? 'Пароль' : 'Password';
  String get inboundNewPassword => _ru ? 'Новый пароль' : 'New password';
  String get inboundLan => _ru ? 'Доступ из локальной сети' : 'Access from the LAN';
}

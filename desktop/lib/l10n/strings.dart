import 'dart:ui' show Locale;

/// The window's words, in Russian and English. A stopgap: the texts move to the shared layer with Android's
/// (docs/design/PLATFORMS.md, one file a language), and this file is then generated from it.
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
}

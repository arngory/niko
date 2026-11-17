# Niko для компьютера: окно

Flutter, Windows сначала, Linux потом. Ядро и логика Niko живут в демоне (`boxdd` из форка ядра, дальше `nikod`);
окно только показывает и просит. Как устроено целиком — [docs/desktop/README.md](../docs/desktop/README.md), что
за каждым элементом экрана — [REQUIREMENTS.md](../docs/desktop/REQUIREMENTS.md).

Сейчас — **этап 2b**: связка окна с демоном. Кнопка запускает готовый JSON-конфиг, видны статус, время и скорость
(D-MAIN-02, D-MAIN-04, D-MAIN-06, D-MAIN-16). Узла, списка способов, разделов шапки и настроек ещё нет: они
приходят с `NikoService` (этап 3).

## Что где

| Путь | Что |
|---|---|
| `lib/theme/tokens.g.dart` | Цвета, размеры и стили текста. **Сгенерирован** из `shared/tokens.json` (`shared/tool/gen_tokens.py`), руками не правится |
| `lib/ui/` | Компоненты дизайн-системы под теми же именами, что на холсте: кнопка подключения (`m-connect`), статус (`m-status`), баннер, строки |
| `lib/screens/main_screen.dart` | Главная в виде «Простой». ID требований стоят в комментариях (`// D-MAIN-04`) |
| `lib/state/connection_controller.dart` | Состояние подключения: связь с демоном, что делает ядро, трафик |
| `lib/daemon/` | Клиент демона (gRPC) и адрес для режима разработки |
| `lib/gen/` | API демона на Dart. **Сгенерирован** из proto форка ядра (`tool/gen_proto.sh`) |
| `lib/l10n/strings.dart` | Тексты, русский и английский. Временно здесь: переедут в общий слой с текстами Android |
| `test/` | Тесты; `test/goldens/` — снимки экрана в состояниях |
| `windows/runner/resources/app_icon.ico` | Значок окна: знак «Маршрут». Собирается `tool/make_icon.js` и `tool/make_icon.py` |

Material не используется: компоненты свои, на слое `widgets`. Так окно легче, и выглядит как макет, а не как Android.

## Режим разработки

`boxdd run --listen 127.0.0.1:<порт>` — это gRPC по TCP **без какой-либо проверки клиента**: любой процесс машины,
дошедший до порта, управляет ядром. Поэтому:

- демон в этом режиме запускает только `tool/dev.dart`, и только на `127.0.0.1`;
- окно собирается со связью по TCP только с `--dart-define=NIKO_DEV=true`. Без него кода этой связи в сборке нет,
  что бы ни стояло в аргументах; релиз собирается без него;
- адрес разбирает `DevEndpoint`: `127.0.0.1:<порт>` и больше ничего — ни имени, ни другого адреса.

```
dart run tool/dev.dart --boxdd <путь к boxdd> --config <файл.json>
```

`boxdd` для Windows собирает `desktop.yml` (артефакт `desktop-windows`). Конфиг — обычный JSON ядра; для пробы
хватит входа `mixed` на `127.0.0.1` и выхода `direct`.

## Проверки

```
flutter analyze
flutter test                      # всё, кроме теста с настоящим демоном
NIKO_BOXDD=<путь к boxdd> flutter test test/real_daemon_test.dart
python3 ../shared/tool/gen_tokens.py --check
tool/gen_proto.sh --check <checkout форка ядра>
```

- `test/daemon_link_test.dart` — клиент и состояние против поддельного демона по настоящему gRPC на `127.0.0.1`.
- `test/main_screen_test.dart` — экран в состояниях, клавиатура, подписи для диктора, узкое окно; снимки
  сравниваются с `test/goldens/` (на Linux; обновить — `flutter test --update-goldens`).
- `test/real_daemon_test.dart` — настоящий `boxdd`: запуск конфига, запрос через его прокси, счётчики трафика,
  остановка, убитый и вернувшийся демон. В CI идёт на Windows и на Linux.

Шрифты Geist и Geist Mono — SIL Open Font License 1.1, текст в `fonts/OFL.txt`.

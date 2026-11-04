# Проверка Niko на настоящей ноде (VPS)

Чек-лист первой проверки клиента против ноды raycontrol в настоящей сети: телефон на мобильной сети и на Wi-Fi, нода
на VPS. CI проверяет форматы и протоколы на loopback. Здесь проверяются сеть, DPI и долгая работа.

Версии: клиент Niko v1.22.0 или новее, raycontrol v0.5.1 или новее (niko-server v0.7.0 в его составе).

## 1. Нода

По `docs/10-quickstart-debian13.md` (raycontrol), плюс:

| Что | Команда (`docs/cheatsheet.md` raycontrol) |
|---|---|
| REALITY | `rayctl service add reality reality` |
| Hysteria2 | `rayctl service add hy2-masq hy2-masq` и `rayctl service add hy2-obfs hy2-obfs` |
| NaiveProxy | `rayctl service add naive naive` |
| DoH ноды | `rayctl service add doh doh` |
| DNS-туннели | `rayctl service add dnstt dns` и `rayctl service add masterdns mdns` |
| Yggdrasil с публичными пирами | `rayctl service add yggdrasil ygg`, затем `rayctl service set ygg peers=tls://host:443,quic://<IP>:443` (`quic://` только с IP) |
| Удалённый API | `rayctl service add api api` |
| Сводки | `rayctl summary on` |

Затем `rayctl apply`. Пользователю — все сервисы:
`rayctl user add -services reality,hy2-masq,hy2-obfs,naive,doh,dns,mdns,ygg`. Приглашения: одно для телефона
(`rayctl device invite <user> phone`), одно с правами администратора (`-role admin`).

## 2. Телефон

Перед началом: «Настройки → raycontrol»: включить «Переключать профили ноды» (по умолчанию включено), «Hysteria2:
Brutal по замеру» и «Делиться обезличенной сводкой с нодой».

| # | Что сделать | Что должно быть |
|---|---|---|
| 1 | Добавить ноду по приглашению (`rcinv1:`) | «Нода raycontrol … добавлена», группа с профилями всех сервисов |
| 2 | Каждый профиль: подключиться, тест задержки, открыть сайты | Работают все, которые пропускает эта сеть |
| 3 | Действия ноды → «Проверить канал» на мобильной сети, затем на Wi-Fi | По строке на профиль: скорость TCP, потери и RTT UDP; в конце — «Сводка этой сети отправлена ноде» |
| 4 | Hysteria2 со включённым Brutal, через минуту после старта | В журнале: «Brutal on this network: down … up …»; скорость не ниже, чем с BBR |
| 5 | Заблокировать на ноде порт текущего профиля (`nft`), подождать 2–3 мин | В журнале watchdog: профиль не работает → подписка → переключение на лучший рабочий |
| 6 | Профиль Yggdrasil: закрыть на ноде TCP 443 для адреса телефона | Watchdog добавляет публичные пиры: «adding the node's public peers»; mesh-адрес ноды доступен |
| 7 | `rayctl service rotate reality` (ротация по расписанию), затем `-now` | Клиент берёт `next` без разрыва; после `-now` — новая подписка, соединение восстанавливается |
| 8 | Режим администратора: пригласить ещё одно устройство с телефона | Приглашение создаётся, второе устройство регистрируется |
| 9 | Сутки работы с выключенным экраном, смена сетей | Без зависаний; переподключения после смены сети; расход батареи в норме |

На ноде после п. 3: `rayctl summary` показывает отчёты (в сумме малых сетей, пока из AS меньше 10 отчётов).

## 3. Что прислать

- «Настройки → Экспорт отладочной информации» после пп. 3, 5, 6 и 9.
- На ноде: `rayctl status`, `rayctl probe @phone.bundle` с другой машины, `journalctl -u rayd --since …` за время
  проверки.
- Таблица: пункт, сеть (оператор, Wi-Fi), результат, что пошло не так.

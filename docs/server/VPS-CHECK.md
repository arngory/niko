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

### 1.1. Публичные пиры Yggdrasil (для п. 6)

1. Из [publicpeers.neilalexander.dev](https://publicpeers.neilalexander.dev/) или
   [yggdrasil-network/public-peers](https://github.com/yggdrasil-network/public-peers) взять живые пиры близко к
   VPS: один `tls://`, один `quic://`.
2. У `quic://` имя заменить адресом: `dig +short A host`. С именем `rayctl` его не примет.
3. Проверить `tls://` с ноды: `timeout 5 openssl s_client -connect host:port </dev/null` доходит до рукопожатия.
4. `rayctl service set ygg peers=tls://host:port,quic://<IP>:port`, затем `rayctl apply`: юнит бэкенда получает сеть
   только с пирами.

### 1.2. Устройство для эталонного замера (для §3)

Телефон, зарегистрированный по приглашению, своего пакета не отдаёт: ключ остаётся на телефоне. Для
`rayctl probe` — отдельное устройство того же пользователя: `rayctl device add <user> probe -o /root/probe.bundle`.
Файл передать на другую машину по доверенному каналу. `rayctl` там — из того же релиза, бинарник статический.

### 1.3. Закрыть порт для адреса телефона (для пп. 5 и 6)

Своя таблица перед периметром raycontrol (его `input` на приоритете −10, эта — на −20); `drop` окончательный, в том
числе для открытых соединений:

```bash
nft add table inet rc-test
nft add chain inet rc-test block '{ type filter hook input priority -20; policy accept; }'
nft add rule inet rc-test block ip saddr <адрес телефона> tcp dport 443 drop
```

- Адрес телефона, каким его видит нода, — поле `address` в «Проверить канал» или `nft list set inet raycontrol
  allow4`. За CGNAT мобильной сети он может смениться: если блок «не взялся», посмотреть адрес снова. С одним
  телефоном на ноде можно закрыть порт для всех: правило без `ip saddr`.
- UDP 443 — `udp dport 443`, DNS-туннели — `udp dport 53`, IPv6 — `ip6 saddr`.
- Снять: `nft delete table inet rc-test`.
- Порт закрывает группу сервисов, они делят его за фронтом:

| Порт | Сервисы |
|---|---|
| TCP 443 | REALITY, naive (h2), DoH, Yggdrasil |
| UDP 443 | hy2-masq, hy2-obfs, naive (QUIC), DoH3 |
| UDP 53 | dnstt, masterdns |

## 2. Телефон

Перед началом: «Настройки → raycontrol»: включить «Переключать профили ноды» (по умолчанию включено), «Hysteria2:
Brutal по замеру» и «Делиться обезличенной сводкой с нодой».

| # | Что сделать | Что должно быть |
|---|---|---|
| 1 | Добавить ноду по приглашению (`rcinv1:`) | «Нода raycontrol … добавлена», группа с профилями всех сервисов |
| 2 | Каждый профиль: подключиться, тест задержки, открыть сайты | Работают все, которые пропускает эта сеть |
| 3 | Действия ноды → «Проверить канал» на мобильной сети, затем на Wi-Fi | По строке на профиль: скорость TCP, потери и RTT UDP; в конце — «Сводка этой сети отправлена ноде» |
| 4 | Hysteria2 со включённым Brutal, через минуту после старта | В журнале: «Brutal on this network: down … up …»; скорость не ниже, чем с BBR |
| 5 | Закрыть на ноде порт текущего профиля (§1.3: TCP 443 для REALITY, UDP 443 для Hysteria2), подождать 2–3 мин | В журнале watchdog: профиль не работает → подписка → переключение на лучший рабочий с другого порта |
| 6 | Профиль Yggdrasil: закрыть TCP 443 для адреса телефона (§1.3) | Watchdog добавляет публичные пиры (§1.1): «adding the node's public peers»; mesh-адрес ноды доступен |
| 7 | `rayctl service rotate reality` (ротация по расписанию), затем `-now` | Клиент берёт `next` без разрыва; после `-now` — новая подписка, соединение восстанавливается |
| 8 | Режим администратора: пригласить ещё одно устройство с телефона | Приглашение создаётся, второе устройство регистрируется |
| 9 | Сутки работы с выключенным экраном, смена сетей | Без зависаний; переподключения после смены сети; расход батареи в норме |

На ноде после п. 3: `rayctl summary` показывает отчёты (в сумме малых сетей, пока из AS меньше 10 отчётов).

## 3. Что прислать

- «Настройки → Экспорт отладочной информации» после пп. 3, 5, 6 и 9.
- На ноде: `rayctl status`, `journalctl -u rayd --since …` за время проверки.
- Эталон для «Проверить канал»: `rayctl probe -channel -redact @probe.bundle` (§1.2) из той же сети, что телефон
  (ноутбук на том же Wi-Fi, для мобильной сети — через раздачу с телефона). `-redact` скрывает адрес ноды, имена и
  ключи. После — `rayctl device revoke <ID>`.
- Таблица: пункт, сеть (оператор, Wi-Fi), результат, что пошло не так.

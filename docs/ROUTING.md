# Routing in Niko

[Русская версия ниже](#маршрутизация-в-niko)

A route rule sends the connections it matches to an outbound: **Proxy** (the selected profile), **Bypass** (directly,
past the proxy), **Block**, or another profile of your list. The rules are checked from the top of the list down;
the first one that matches decides, and a connection no rule matches goes through the proxy.

What a rule can match (all of the fields you fill must match):

- **Apps** — the apps whose connections it takes (VPN mode only).
- **Domain** — one per line: `example.com` (with its subdomains), `full:`, `keyword:`, `regexp:`, `geosite:ru`, or a
  remote rule-set `rule-set:https://…/geosite-ru.srs`. A domain is known when the app asked for it by name (DNS,
  the TLS or HTTP name of the connection).
- **IP** — `1.2.3.0/24`, `geoip:ru`, `geoip:private`, or a remote rule-set `rule-set:https://…/geoip-ru.srs`.
- **Port**, **source**, **source port**, **network** (tcp, udp) and **protocol** (tls, http, quic, dns, bittorrent…,
  as the core recognises it from the first bytes).

Things worth knowing before you add rules:

- A rule that bypasses the proxy for a wide range (all of `geoip:ru`, say) shows your real address to those sites.
- **Block** on DNS or on the proxy's own server breaks the connection altogether: keep the defaults until a rule is
  needed.
- **Preset: Russia** adds two ready rules: what is blocked in Russia goes through the proxy, Russian services directly.
  Rules above it come first; its lists update by themselves.
- A raycontrol node's own rows, its DNS and the local network of your devices are routed by the app itself, above
  your rules.

---

# Маршрутизация в Niko

Правило маршрута отправляет подходящие под него соединения в выход: **Прокси** (выбранный профиль), **Напрямую**
(мимо прокси), **Блокировать** или другой профиль из списка. Правила проверяются сверху вниз; решает первое
подходящее, а соединение, под которое не подошло ни одно, идёт через прокси.

Что правило может проверять (должны совпасть все заполненные поля):

- **Приложения** — соединения каких приложений оно берёт (только в режиме VPN).
- **Домен** — по одному на строку: `example.com` (с поддоменами), `full:`, `keyword:`, `regexp:`, `geosite:ru` или
  удалённый набор правил `rule-set:https://…/geosite-ru.srs`. Домен известен, когда приложение обратилось по имени
  (DNS, имя TLS или HTTP в соединении).
- **IP** — `1.2.3.0/24`, `geoip:ru`, `geoip:private` или удалённый набор `rule-set:https://…/geoip-ru.srs`.
- **Порт**, **источник**, **порт источника**, **сеть** (tcp, udp) и **протокол** (tls, http, quic, dns, bittorrent…,
  как ядро распознаёт его по первым байтам).

Что стоит знать до того, как добавлять правила:

- Правило «напрямую» для широкого диапазона (например, всего `geoip:ru`) показывает этим сайтам ваш настоящий адрес.
- **Блокировать** DNS или сервер самого прокси — значит сломать подключение целиком: не трогайте настройки по
  умолчанию, пока правило не нужно.
- **Шаблон: Россия** добавляет два готовых правила: заблокированное в России — через прокси, российские сервисы —
  напрямую. Правила выше него проверяются раньше; его списки обновляются сами.
- Строки ноды raycontrol, её DNS и локальная сеть ваших устройств маршрутизируются самим приложением, выше ваших
  правил.

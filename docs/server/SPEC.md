# niko-server: спецификация

Документ для отдельной сессии, которая делает серверную часть Niko. Клиент (приложение Niko и его ядро) уже умеет всё, что здесь описано. Исключение — пункты из раздела «Что доделать в клиенте».

## 0. Исходные данные

| Что | Где |
|---|---|
| Приложение (клиент) | `arngory/niko`, ветка `claude/intelligent-allen-0oa8na` |
| Ядро (форк sing-box 1.14.4) | `arngory/sing-box`, та же ветка; коммит, на котором собран клиент, записан в `buildScript/lib/core/get_source_env.sh` (`COMMIT_SING_BOX`) |
| Xray (эталонный сервер REALITY) | `arngory/xray-core`, ветка `main` (v26.10.31) |
| Проверка совместимости клиента с Xray | `.github/workflows/xray-interop.yml` в `arngory/niko` |
| Проверка Yggdrasil на публичных пирах | `.github/workflows/ygg-probe.yml` в `arngory/niko` |

Что уже есть в форке sing-box и пригодится серверу:
- входящие `vless` (REALITY через `sagernet/reality`), `hysteria2`, `naive` (вместе с QUIC), `trojan`, `shadowsocks`, `tuic`, `anytls`, `socks`, `mixed`;
- endpoint `yggdrasil` (`protocol/yggdrasil`) с опцией `listen`;
- клиенты dnstt (`transport/dnstt`) и MasterDnsVPN (`third_party/masterdnsvpn`);
- сниферы TLS и QUIC (`common/sniff`): по первому пакету они достают SNI и ALPN.

Правила работы (те же, что у клиента):
- всё собирается из наших форков и наших релизов, сторонних бинарников нет;
- все версии зафиксированы (коммит или тег), обновление — отдельным коммитом;
- в release notes по одной строке на правку: сначала английские строки, потом те же строки на русском;
- после каждого законченного блока — релиз, а не только зелёный CI.

## 1. Цель

Один процесс `niko-server` на одном IP обслуживает все протоколы клиента. Наружу открыто минимум портов:

| Порт | Что на нём |
|---|---|
| TCP 443 | REALITY (Vision, с ML-DSA-65), NaiveProxy, DoH, сайт-приманка, линки Yggdrasil, ACME TLS-ALPN-01 |
| UDP 443 | Hysteria2 с masquerade, Hysteria2 с salamander, HTTP/3 (DoH3, naive+quic), SPA |
| UDP 53 | dnstt и MasterDnsVPN, каждый в своей делегированной зоне |
| TCP 80 (необязательно) | ACME HTTP-01 и редирект на https |
| TCP/UDP 853 (необязательно) | DoT/DoQ для системного «Частного DNS» Android: он ходит только на 853 |

Остальное закрыто файрволом (политика `drop`).

## 2. Архитектура

```
              ┌───────────── niko-server (форк sing-box) ─────────────┐
TCP 443 ──► tcpmux ─┬─ SNI r.example.com ──► (PROXY v2) Xray REALITY 127.0.0.1:8443
                    ├─ SNI n.example.com ──► naive inbound + DoH /dns-query/<token> + сайт
                    ├─ SNI y.example.com ──► yggdrasil listen (tls-линк)
                    ├─ ALPN acme-tls/1   ──► ACME
                    └─ остальное         ──► как r.example.com (REALITY уведёт в приманку)
UDP 443 ──► udpmux ─┬─ QUIC, SNI h.example.com ──► hysteria2 (masquerade)
                    ├─ QUIC, SNI n.example.com ──► naive QUIC / DoH3
                    ├─ salamander             ──► hysteria2 (obfs)
                    ├─ пакет SPA               ──► spa gate
                    └─ остальное              ──► drop
UDP 53  ──► dnsmux ─┬─ *.t.example.com ──► dnstt server (библиотека) ──► upstream
                    ├─ *.v.example.com ──► MasterDnsVPN server (библиотека)
                    └─ остальное       ──► REFUSED (не открытый резолвер)
              └───────────────────────────────────────────────────────┘
```

### 2.1. Почему REALITY отдаётся Xray, а остальное нет

- Xray — эталон REALITY. Только в нём есть `mldsa65Seed`, `minClientVer` и правильная работа с гибридным X25519MLKEM768.
- `sagernet/reality` в sing-box отстаёт. В клиенте есть комментарий: старые серверы не умеют принимать гибридный ключ, когда приманка его выбирает.
- Поэтому Xray из `arngory/xray-core` работает отдельным процессом на `127.0.0.1:8443` с `sockopt.acceptProxyProtocol: true`. tcpmux передаёт ему сырой TCP: сначала заголовок PROXY v2, затем уже прочитанные байты ClientHello, затем остальное.
- Остальные протоколы tcpmux передаёт внутрь процесса как `net.Conn` с уже прочитанными байтами. Механизм тот же, что у сниффера sing-box: `bufio.CachedConn`.

### 2.2. Домены: «свои поддомены» (по умолчанию) и «чужая приманка»

**Вариант A, по умолчанию.** На IP видны только наши поддомены:
- `r.example.com` — REALITY в режиме «сам у себя»: `target` = внутренний HTTPS-сайт niko-server на `127.0.0.1:8444` с настоящим ACME-сертификатом `r.example.com`, TLS 1.3 + h2;
- `n.example.com` — naive, DoH и сайт;
- `h.example.com` — Hysteria2 с masquerade;
- `y.example.com` — линки Yggdrasil.

Проверяющий видит один сайт с валидными сертификатами на всех именах.

**Вариант B, домена нет.** REALITY с чужим `target` (TLS 1.3 + h2, не за CDN). naive и DoH в этом варианте на этом IP не поднимаются: свой сертификат рядом с чужим сайтом — заметный признак. tcpmux отдаёт всё в Xray.

Конфиг выбирает вариант; смешивать их нельзя.

## 3. Компоненты

### 3.1. tcpmux (TCP 443)

- Читает первый TLS-рекорд (до 16 КБ, таймаут 5 с), достаёт SNI и ALPN. Парсер — `common/sniff`.
- Таблица маршрутов SNI → backend задаётся в конфиге. Без SNI и с неизвестным SNI соединение идёт в маршрут `default` (REALITY).
- Не TLS: сразу закрыть соединение. Без ответа, без RST-ловушек.
- Шлюз SPA (п. 3.5): если сервис помечен `spa: required` и IP клиента не авторизован, соединение уходит в `default`. Для чужого клиента это приманка, не отказ.
- Адрес клиента передаётся в Xray через PROXY v2, во внутренние inbound — через метаданные sing-box (`InboundContext.Source`).

### 3.2. udpmux (UDP 443)

Один сокет, разбор по потокам (адрес:порт клиента). Классификация выполняется по первому пакету потока и запоминается на время жизни потока (бездействие 60 с). Порядок проверок:

1. **QUIC long header с известной версией.** Расшифровать Initial на initial-ключах (это уже умеет QUIC-сниффер sing-box), достать SNI, выбрать backend по SNI.
2. **Salamander.** Пакет: 8 байт соли + (полезная нагрузка XOR поток ключа `BLAKE2b-256(пароль || соль)`). Снять обфускацию паролем из конфига. Если внутри QUIC long header с известной версией — это поток Hysteria2-obfs. Проверка стоит одного хеша на пакет и выполняется только для новых потоков.
3. **SPA** (п. 3.5): длина 56–119 байт и `spaVerify` проходит хотя бы для одного ключа клиента. Авторизовать IP, ответа не отправлять.
4. Иначе пакет отбрасывается.

Inbound hysteria2 и naive-quic в sing-box сами открывают свой UDP-сокет. Нужен хук: inbound должен уметь принять готовый `net.PacketConn` (виртуальный, от udpmux), а не вызывать listen. Это отдельный патч в форке: опция `listen` в виде «внутреннего» адреса или регистрация PacketConn по тегу.

Два конфига Hysteria2 — это два inbound с разными тегами:
- `hy2-masq`: TLS с сертификатом `h.example.com` и `masquerade`;
- `hy2-obfs`: `obfs.type = salamander`, самоподписанный сертификат. Клиент пинит его публичный ключ: в Niko это «Настройки соединения → Пиннинг сертификата», а не `pinSHA256`.

### 3.3. dnsmux (UDP 53)

- Разбирает DNS-запрос и выбирает получателя по суффиксу QNAME (без учёта регистра, по меткам): `t.example.com` → dnstt, `v.example.com` → MasterDnsVPN. Список зон задаётся в конфиге.
- Остальные имена получают `REFUSED`. Открытым резолвером сервер не становится.
- Ответ уходит с того же сокета на адрес отправителя. Отправитель — это рекурсивный резолвер, а не клиент. Поэтому SPA и любые ограничения по IP клиента к порту 53 не применяются. Аутентификация здесь — ключ самого туннеля: Noise-ключ у dnstt, общий ключ у MasterDnsVPN.

**dnstt.** `dnstt-server` в апстриме — пакет `main`. Нужно:
1. В форке `arngory/dnstt` вынести серверную часть в библиотеку: `server.Run(ctx, Options{Domain, PrivKey, PacketConn, Upstream func() (net.Conn, error)})`. Апстрим живёт не на GitHub (`https://www.bamsoftware.com/git/dnstt.git`), поэтому форк — это зеркало, залитое в новый репозиторий.
2. `PacketConn` — виртуальный, от dnsmux. Upstream — внутреннее соединение с inbound в том же процессе (shadowsocks или vless на внутреннем адресе). Порт на `127.0.0.1` не нужен.
3. Клиент Niko уже совместим: dnstt v1.20260501.0, резолверы DoH, DoT и UDP.

**MasterDnsVPN.** Сервер апстрима (masterking32/MasterDnsVPN) читает TOML и сам открывает `UDP_HOST:UDP_PORT`. Нужно:
1. В форке `arngory/MasterDnsVPN` добавить для сервера `SetPacketConnFactory`. Для клиента такой же патч уже сделан: `SetListenerFactory` в `sing-box/third_party/masterdnsvpn`.
2. Режим `PROTOCOL_TYPE = "SOCKS5"`: сервер сам SOCKS5-прокси, клиент в Niko указывает в профиле конечный сервер. Режим `"TCP"` + `FORWARD_IP/PORT`: соединения идут во внутренний inbound; в клиенте отмечается «Фиксированный upstream».
3. `DATA_ENCRYPTION_METHOD`: 0 — нет, 1 — XOR, 2 — ChaCha20, 3–5 — AES-GCM. Ключ из `ENCRYPTION_KEY_FILE`. Значение по умолчанию для конфига: 2.

**DNS-записи.** Зону `example.com` держит регистратор, сервер отвечает только за делегированные зоны:
```
ns1.example.com.  A   <IP>
t.example.com.    NS  ns1.example.com.
v.example.com.    NS  ns1.example.com.
```

### 3.4. Yggdrasil

- В niko-server работает endpoint `yggdrasil` из форка: у него есть `listen`, `peers`, `allowed_public_keys`. Ключ узла хранится в конфиге сервера.
- Входящие линки идут через tcpmux по SNI `y.example.com`. Клиент указывает пир `tls://<IP или домен>:443?sni=y.example.com`.
  - Нужно проверить, что yggdrasil-go 0.5.14 передаёт `sni` из URI в TLS. Нужно проверить, что `parsePeer` в клиенте пропускает этот параметр (сейчас он проверяет только `key`). Это задача и для клиента, и для сервера.
- Выход в интернет для клиентов Yggdrasil. Встроенного выхода в yggdrasil-go 0.5 нет (CKR удалён). Входящие соединения endpoint передаёт в роутер с адресом назначения `127.0.0.1:<порт>`. Маршрут: `inbound: [yggdrasil]` + `port: [<порт выхода>]` → внутренний shadowsocks inbound; всё остальное из `yggdrasil` → `reject`.
- Публичные пиры: 2–3 из github.com/yggdrasil-network/public-peers. Живые пиры смотреть на publicpeers.neilalexander.dev. Без пиров сервер всё равно работает как точка прямого подключения.

### 3.5. SPA (single packet authorization)

Клиент уже отправляет пакеты (с этого релиза): шаг `ПОРТ/spa:КЛЮЧ` в последовательности простука профиля. Реализация — `libcore/knock/spa.go`.

**Формат пакета Niko SPA v1** — один UDP-датаграм:

| Поле | Размер | Содержимое |
|---|---|---|
| nonce | 16 байт | случайные байты |
| time | 8 байт | unix-секунды, big endian |
| mac | 32 байта | `HMAC-SHA256(key, "niko-spa-v1" ‖ nonce ‖ time)` |
| padding | 0–63 байта | случайные байты случайной длины |

Ключ — 32 байта, у каждого клиента свой (в клиенте задаётся 64 hex-символами). В пакете нет постоянных байтов.

**Проверка на сервере:**
1. Длина от 56 до 119 байт.
2. Для каждого ключа сравнить MAC за постоянное время (`hmac.Equal`).
3. `|now − time| ≤ 30 с`.
4. nonce не встречался за последние 60 с (кэш с вытеснением; лимит размера, например 100 000).
5. Авторизовать адрес отправителя на TTL (по умолчанию 10 мин, продлевается новым пакетом). Ответ не отправляется.
6. Ограничение частоты: не больше N пакетов в секунду на IP. Лишние отбрасываются до проверки HMAC.

**Тестовый вектор** (совпадает с `TestSPAVector` в клиенте):
```
key     = 11 × 32
nonce   = 22 × 16
time    = 1767225600
padding = aa bb
packet  = 22222222222222222222222222222222 000000006955b900
          96884244d2f8c829abd0b8b1a4c28af18da31cdfb79f8530cb215247c1b7b72f aabb
```

**Применение:**
- Шлюз в tcpmux и udpmux: для каждого сервиса в конфиге задаётся `spa: off | required`. Неавторизованный IP получает поведение «чужого» клиента: приманку для TCP, drop для UDP.
- Для отдельных портов вне мультиплексоров (например AmneziaWG, если он появится) — режим nftables: адрес добавляется в set с таймаутом, порт открыт только для этого set.
- Ограничение: на пути можно перехватить пакет и успеть отправить его со своего IP раньше клиента. Кэш nonce защищает от повтора, но не от такой гонки. Для порта 53 SPA бессмысленна (см. 3.3).

Клиент стучит перед подключением, после смены сети и после неудачного подключения. На UDP 443 в профиле это шаг `443/spa:<ключ>`.

### 3.6. REALITY в Xray

```json
{"inbounds":[{"listen":"127.0.0.1","port":8443,"protocol":"vless",
  "settings":{"clients":[{"id":"<uuid>","flow":"xtls-rprx-vision"}],"decryption":"none"},
  "streamSettings":{"network":"raw","security":"reality",
    "sockopt":{"acceptProxyProtocol":true},
    "realitySettings":{"target":"127.0.0.1:8444","serverNames":["r.example.com"],
      "privateKey":"<xray x25519>","shortIds":["<8 hex>"],
      "mldsa65Seed":"<xray mldsa65: Seed>"}}}],
 "outbounds":[{"protocol":"freedom"}]}
```

- **`mldsa65Seed`.** Клиент Niko проверяет подпись, если в профиле задан ключ (поле «Ключ ML-DSA-65 для REALITY», в ссылке `pqv=`). Без ключа клиент подключается без проверки. Совместимость проверяет `xray-interop.yml`.
- **`minClientVer`.** Не задавать (у Xray с 26.7.11 по умолчанию 26.3.27) или задать ровно `"26.3.27"`. Niko сообщает серверу именно эту версию, поэтому любое большее значение отрежет Niko.
- **Отпечаток клиента** — `chrome`, «Обмен ключами REALITY» — «Авто». Xray начиная с 26.9.8 требует гибридный ключ X25519MLKEM768.
- **VLESS encryption** (`xray vlessenc`, вариант ML-KEM-768) — необязательный второй слой. Совместимость тоже проверяется в CI.

### 3.7. NaiveProxy

- Используется inbound `naive` из форка (TCP h2 и QUIC). Caddy не нужен.
- TLS: ACME-сертификат `n.example.com`.
- Неавторизованные запросы и запросы не по CONNECT получают сайт-приманку. Это аналог `probe_resistance`. Обязательно проверить: ответ на запрос без авторизации должен быть неотличим от обычного сайта (без `407`).
- В клиенте Niko naive работает через плагин: в самом ядре клиента naive-outbound не включён. Проверить совместимость с клиентом naive (klzgrad/naiveproxy, наш форк) в CI.

### 3.8. DoH и DoT

- `https://n.example.com/dns-query/<token>` (h2) и то же по HTTP/3. Токен в пути отсекает чужих. Резолвер — DNS-роутер sing-box.
- DoT/DoQ — только если нужен системный «Частный DNS» Android, на 853, с тем же токеном в виде отдельного имени хоста. Иначе 853 не открывать.

## 4. Конфиг и команды

Один файл `niko-server.yaml`: домены, вариант A или B, ключи, пользователи (UUID, пароли, SPA-ключи, ключи Yggdrasil), список сервисов с флагами `spa`.

Команды:
- `niko-server init` — сгенерировать все ключи (x25519, mldsa65, vlessenc, SPA, dnstt, MasterDnsVPN, Yggdrasil) и конфиги Xray и sing-box.
- `niko-server run` — запустить. Xray запускается как дочерний процесс и перезапускается при падении.
- `niko-server export <user>` — ссылки для клиента: `vless://` с `pqv`, `hysteria2://` ×2, `naive+https://`, профили DNS-туннелей, пиры Yggdrasil. Плюс подписка (base64-список ссылок).
- `niko-server check` — проверить конфиг, DNS-делегирование и открытые порты (с самого сервера).

Деплой:
- systemd unit, `CAP_NET_BIND_SERVICE` вместо root;
- nftables: `drop` по умолчанию, открыты только порты из таблицы в разделе 1;
- systemd-resolved stub выключен, чтобы освободить UDP 53.

## 5. Форки

| Репозиторий | Зачем | Статус |
|---|---|---|
| `arngory/sing-box` | ядро клиента и niko-server | есть |
| `arngory/xray-core` | REALITY-сервер, эталон для тестов | есть |
| `arngory/dnstt` | серверная библиотека (п. 3.3) | создать: зеркало `https://www.bamsoftware.com/git/dnstt.git` |
| `arngory/MasterDnsVPN` | сервер с внешним PacketConn | сделать форк masterking32/MasterDnsVPN |
| `arngory/yggdrasil-go` | патч `LinkDialer` (сейчас он вендорен в `sing-box/third_party`) | желательно: перенести патч в форк и вендорить из него |
| `arngory/naiveproxy` | клиент naive для тестов и плагин Android из своего релиза | желательно: форк klzgrad/naiveproxy |
| apernet/hysteria | официальный клиент для тестов совместимости | форк не нужен, собирать тег в CI |

Сторонние модули, уже вендоренные в `sing-box/third_party`: `sing-quic`, `wireguard-go` (AmneziaWG), `masterdnsvpn` (клиент), `yggdrasil-go`. После появления форков `replace` переключаются на них.

## 6. Проверки (критерии готовности)

Каждый пункт — job в CI на GitHub Actions. Сеть там открыта, контейнер сессии к внешним узлам не пускает.

1. **Клиент Niko ↔ niko-server** для каждого протокола: REALITY с ML-DSA-65 и без, VLESS encryption, Hysteria2 masq, Hysteria2 obfs, naive h2 и quic, dnstt через DoH (локальный рекурсор), MasterDnsVPN, Yggdrasil через `y.example.com`. Ожидается `204` от `generate_204` через каждый профиль.
2. **Активное зондирование:**
   - TLS с чужим SNI получает приманку;
   - запрос к naive без авторизации получает сайт;
   - UDP-мусор на 443 остаётся без ответа;
   - DNS-запрос к чужому имени получает `REFUSED`;
   - SPA, отправленная повторно, отклоняется;
   - SPA старше 30 с отклоняется.
3. **Совместимость со сторонними клиентами:** официальный клиент hysteria (salamander), Xray-клиент (REALITY), naive (из форка), dnstt-client.
4. **Нагрузка:** 200 одновременных потоков через каждый мультиплексор. Без утечек горутин (`goleak`) и без роста памяти за 10 минут.

## 7. Порядок работы

1. Форки dnstt и MasterDnsVPN, серверные библиотеки с внешним PacketConn, unit-тесты на них.
2. Каркас `niko-server` (форк sing-box, `cmd/niko-server`): конфиг, `init`, `run`, dnsmux. CI-проверка DNS-туннелей. Релиз.
3. tcpmux, интеграция с Xray, приманка, naive, DoH. CI-проверки. Релиз.
4. udpmux: Hysteria2 ×2, naive-quic, SPA. CI-проверки. Релиз.
5. Yggdrasil через tcpmux и выход. `export` и подписка. Релиз.

## 8. Что доделать в клиенте

- Ссылки и подписка пока не переносят простук (SPA) и DNS-туннель профиля. Нужны параметры, например `niko-knock=`, `niko-dns=` в query ссылки. Формат утвердить вместе с `export`.
- Пир Yggdrasil с `?sni=` (п. 3.4).
- Импорт подписки niko-server одной кнопкой.

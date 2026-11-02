# Документы для сессии серверной части

Что передать в сессию, которая делает niko-server, и в каком порядке читать.

## Обязательно

| № | Документ | Где | Зачем |
|---|---|---|---|
| 1 | [`docs/08-decisions.md`](https://github.com/arngory/raycontrol/blob/claude/clever-feynman-w0e2v1/docs/08-decisions.md) | raycontrol, ветка `claude/clever-feynman-w0e2v1` | решения, главнее всего остального |
| 2 | [`SPEC.md`](SPEC.md) | niko, ветка `claude/intelligent-allen-0oa8na` | что строить: процессы, мультиплексоры, бэкенды, порядок этапов |
| 3 | [`CONFIG.md`](CONFIG.md), [`IPC.md`](IPC.md) | niko | черновики контрактов, замораживаются на R1 |
| 4 | [`docs/spec/spa-v2.md`](https://github.com/arngory/raycontrol/blob/claude/clever-feynman-w0e2v1/docs/spec/spa-v2.md) + [`vectors.json`](https://github.com/arngory/raycontrol/blob/claude/clever-feynman-w0e2v1/internal/spa/testdata/vectors.json) | raycontrol | периметр: что пропускает nftables |
| 5 | [`RAYCONTROL.md`](RAYCONTROL.md) | niko | ответы для raycontrol: `inner` на Xray, постквант в Yggdrasil, ссылки и подпись подписки; §8 — что сделано по R1 |
| 6 | [`docs/handoff/niko-r1-reply.md`](https://github.com/arngory/raycontrol/blob/claude/clever-feynman-w0e2v1/docs/handoff/niko-r1-reply.md) | raycontrol | ответ raycontrol на R1: что принято, замечания к CONFIG/IPC, окно ACME, `tcp.window_clamp` |
| 7 | [`docs/spec/subscription-v1.md`](https://github.com/arngory/raycontrol/blob/claude/clever-feynman-w0e2v1/docs/spec/subscription-v1.md) | raycontrol | подписка и ссылки `niko-*`, которые рендерит raycontrol (клиент их уже понимает) |

## Для контекста

| Документ | Где | Зачем |
|---|---|---|
| `docs/01-architecture.md`, `02-security-model.md`, `03-protocols.md`, `04-roadmap.md` | raycontrol | архитектура ноды, модель угроз, состав протоколов, этапы R0–R5 |
| `docs/handoff/niko-session.md` | raycontrol | исходный запрос к сессии Niko |
| [`../research/anti-dpi.md`](../research/anti-dpi.md) | niko | что должен уметь udpmux (фейки клиента), риск SPA только по UDP, окно TCP |

## Код, на который опираться

| Что | Где |
|---|---|
| Форк sing-box (сюда же `cmd/niko-server`) | `arngory/sing-box`, ветка `claude/intelligent-allen-0oa8na`; коммит клиента — `COMMIT_SING_BOX` в `buildScript/lib/core/get_source_env.sh` |
| Клиент SPA v2 (эталон отправителя) | `libcore/knock/spa2.go`, тесты на векторы — `libcore/knock/spa2_test.go` |
| Проверка клиента против Xray (REALITY + ML-DSA-65, VLESS encryption) | `.github/workflows/xray-interop.yml`, версия Xray — `.github/xray-interop.env` |
| Проверка Yggdrasil на публичных пирах | `.github/workflows/ygg-probe.yml` |
| Форки | `arngory/xray-core`, `arngory/dnstt`, `arngory/MasterDnsVPN`, `arngory/yggdrasil-go`, `arngory/naiveproxy` (статусы — SPEC §5) |

## Где остановились (2 ноября 2026)

| Что | Состояние |
|---|---|
| R1, niko-server | готово: релиз **`niko-server-v0.1.1`** (`arngory/niko`, releases; `linux-amd64`, `linux-arm64`, `SHA256SUMS`). `front` (DNS, UDP 53), `backend` для `dnstt` и `masterdns`, `check` (коды 0/1/2, `--json`), `keygen dnstt`. v0.1.0 не использовать (резолвила DNS клиентов с ноды) |
| Код niko-server | `arngory/sing-box`, ветка `claude/intelligent-allen-0oa8na`, коммит `34eaa8c`: `cmd/niko-server`, `nikoserver/`, `transport/dnstt/dnsttserver/`, `third_party/masterdnsvpn/embed/` |
| Проверка | `.github/workflows/niko-server.yml` в `arngory/niko`: фронт + оба бэкенда + `inner` на Xray + клиент Niko через оба туннеля до `204`, `REFUSED` на чужое имя, `go test -race` |
| R1, клиент | готово: подписка subscription-v1 (v1.13.0–v1.13.2), ссылки `niko-dns`/`niko-dnskey`/`niko-pin`, SPA v2 по TCP как запасной носитель (v1.12.1) |
| SPA по TCP на ноде | сделано в raycontrol (`rayspa`, слушающий сокет, `spa-v2.md` §10); niko-server это не касается |
| R2 и дальше (фронт TCP/UDP 443, `site`, naive, DoH, Hysteria2) | **не начато**: следующий этап для этой сессии, SPEC §3.1–§3.2, §7 |
| Нагрузочный тест (SPEC §6.6) | не выполнен, нужен вместе с фронтом 443 |

Как выпускать: пуш тегов из сессий запрещён (HTTP 403), поэтому релиз — `release.yml` через workflow_dispatch с входом
`tag` = `niko-server-vX.Y.Z`; он сверяет, что тег стоит на выпускаемом коммите, и вызывает `niko-server.yml`. Новый
коммит ядра для клиента — `COMMIT_SING_BOX` в `buildScript/lib/core/get_source_env.sh`.

Просьбы raycontrol к niko-server после R4 — [`docs/handoff/niko-r4-note.md`](https://github.com/arngory/raycontrol/blob/claude/clever-feynman-w0e2v1/docs/handoff/niko-r4-note.md)
§6: tcpmux с маршрутом по SNI (перекрытие ключей REALITY), бэкенд `site` «только TLS и пустой ответ», датаграммы фронта
в пределах MTU (периметр отбрасывает фрагменты). Ответ клиента на ту же заметку — [`RAYCONTROL.md`](RAYCONTROL.md) §9.

## Открытые вопросы к raycontrol

- **`services[].port_key`** (и `port_base`/`port_range`) в пакете регистрации — ключ расписания UDP-порта сервиса
  (RAYCONTROL.md §8, п. §3.5). Без него клиент не может следовать ротации портов; клиентская часть небольшая и
  делается сразу после появления поля.
- Пакет для QR без ключей ноды (`node_keys_sha256` + `GET /sub/v1/keys`) — предложение в RAYCONTROL.md §9.
- Заморозка формата подписки вместе с эндпоинтом (niko-r1-reply.md §1): клиент уже реализует текущий текст
  `subscription-v1.md`, включая поле `time`.

Первая фраза для новой сессии: «Прочитай `docs/server/README.md` в `arngory/niko` (ветка `claude/intelligent-allen-0oa8na`): раздел „Где остановились“, затем документы из раздела „Обязательно“ по порядку. R1 готов (`niko-server-v0.1.1`); начинай этап R2 из SPEC §7».

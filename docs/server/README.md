# Документы для сессии серверной части

Что передать в сессию, которая делает niko-server, и в каком порядке читать.

## Обязательно

| № | Документ | Где | Зачем |
|---|---|---|---|
| 1 | [`docs/08-decisions.md`](https://github.com/arngory/raycontrol/blob/claude/clever-feynman-w0e2v1/docs/08-decisions.md) | raycontrol, ветка `claude/clever-feynman-w0e2v1` | решения, главнее всего остального |
| 2 | [`SPEC.md`](SPEC.md) | niko, ветка `claude/intelligent-allen-0oa8na` | что строить: процессы, мультиплексоры, бэкенды, порядок этапов |
| 3 | [`CONFIG.md`](CONFIG.md), [`IPC.md`](IPC.md) | niko | черновики контрактов, замораживаются на R1 |
| 4 | [`docs/spec/spa-v2.md`](https://github.com/arngory/raycontrol/blob/claude/clever-feynman-w0e2v1/docs/spec/spa-v2.md) + [`vectors.json`](https://github.com/arngory/raycontrol/blob/claude/clever-feynman-w0e2v1/internal/spa/testdata/vectors.json) | raycontrol | периметр: что пропускает nftables |
| 5 | [`RAYCONTROL.md`](RAYCONTROL.md) | niko | ответы для raycontrol: `inner` на Xray, постквант в Yggdrasil, ссылки и подпись подписки |

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

Первая фраза для новой сессии: «Прочитай `docs/server/README.md` в `arngory/niko` (ветка `claude/intelligent-allen-0oa8na`) и документы из раздела „Обязательно“ по порядку, затем начинай этап R1 из SPEC §7».

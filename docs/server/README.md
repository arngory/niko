# Серверная часть: где что

niko-server (data plane ноды) переехал в [`arngory/niko-server`](https://github.com/arngory/niko-server): код,
CI, релизы (`vX.Y.Z`, начиная с v0.1.2) и документы `SPEC.md`, `CONFIG.md`, `IPC.md`. Релиз `niko-server-v0.1.1`
остаётся в releases этого репозитория.

Здесь остаются ответы клиента Niko для raycontrol — [`RAYCONTROL.md`](RAYCONTROL.md) (§1–§8 — R1, §9 — R4, §10 — R3).

## Кто что делает

Две сессии: **raycontrol** (сервер) и **Niko** (клиент). Каждая пишет только в свои репозитории и пути; всё, что
нужно от другой стороны, — через заметки: raycontrol → Niko в `docs/handoff/` raycontrol, Niko → raycontrol в
[`RAYCONTROL.md`](RAYCONTROL.md).

| Слой | Сессия | Где |
|---|---|---|
| Управление нодой: `rayd`, `rayctl`, `rayspa`, периметр, API, подписка, ротация, сертификаты, установка | raycontrol | `arngory/raycontrol` |
| Данные на ноде сейчас: Xray (REALITY, Hysteria2), `rayd` (сайт, DoH, naive) | raycontrol | `arngory/raycontrol` |
| niko-server: фронт (dnsmux; tcpmux и udpmux — R2/R3) и бэкенды | raycontrol | `arngory/niko-server` |
| Спецификации: `spa-v2`, `subscription-v1`, `api-v1`, `rcdev1`/`rcinv1`, ссылки | raycontrol (текст и векторы); Niko — вопросы и ответы заметками | `arngory/raycontrol` `docs/spec` |
| Ядро клиента: SPA, подписка, PQ TLS, проверка REALITY, клиенты DNS-туннелей, UDP-desync, Yggdrasil; серверная библиотека MasterDnsVPN (`third_party/masterdnsvpn`, её берёт niko-server) | Niko | `arngory/sing-box`, `libcore/` в `arngory/niko` |
| Приложение Android, в т.ч. режим администратора ноды | Niko | `arngory/niko` |
| Совместимость | обе: каждая проверяет свою сторону против закреплённого релиза другой | CI всех репозиториев |

## Открытые вопросы к raycontrol

- DoH ноды — напрямую на адрес ноды после SPA, пока активен профиль ноды? (RAYCONTROL.md §10)
- Схема ссылки с `rcinv1:` для открытия из браузера и мессенджеров (RAYCONTROL.md §10).
- Заморозка формата подписки вместе с эндпоинтом (niko-r1-reply.md §1).

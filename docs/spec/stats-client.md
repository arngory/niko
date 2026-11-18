# Статистика за час: сторона клиента

Общая обёртка и поля ноды — в спецификации raycontrol `docs/spec/stats-v1.md`. Здесь только то, что пишет клиент:
`side` = `client`, файл `niko-stats-<run>.json` из «Журнал → ⋮ → Статистика за час». Run ID тот же, что у тест-прогона
и у `rayctl stats -id` на ноде.

## Что записывается

Только слова из фиксированных списков и числа. Адресов, ключей, доменов и имён нет. Вид профиля — тип (`naive`,
`hysteria2`, `vless`), не его имя. Записи хранятся два часа, в `no_backup/stats/`.

- **События** — оба процесса:
  - запуск, сбой и его вид, остановка службы;
  - смена сети и её вид, потеря сети;
  - шаги сторожа;
  - проверки URL со временем.
- **Снимок раз в минуту** — служба, пока работает:
  - трафик через прокси с начала работы ядра;
  - предупреждения и ошибки журнала;
  - память процесса (RSS).

## Поля отчёта

| Поле | Что |
|---|---|
| `app` | `product`, `version`, `version_code`, `android_api`, `abi`, `core` |
| `state` | `service`, `mode` (`vpn`/`proxy`), `profile` (вид), `from_node` |
| `settings` | `auto_switch`, `brutal`, `reset_on_network_change`, `ipv6_mode`, `traffic_counted` |
| `coverage` | `window_minutes`; `service_minutes` — минуты окна, когда служба работала |
| `counters[]` | `{name, labels, delta, reset, new}`, как у ноды |
| `gauges[]` | `{name, labels, end, min, max}`, как у ноды |
| `timings[]` | `{name, labels, count, p50, p90, max}` в мс |

`partial` = `true`, когда записи начинаются внутри окна. Это бывает на первом часе после установки 1.60.0 или после
очистки данных.

## Счётчики и замеры

| Имя | Метки |
|---|---|
| `niko_service_start_total` | `mode`, `profile` |
| `niko_service_fail_total` | `why`: `invalid_server`, `plugin_missing`, `core`, `process`, `other`; `profile` |
| `niko_service_stop_total` | — |
| `niko_network_change_total`, `niko_network_back_total` | `to`: `wifi`, `cellular`, `ethernet`, `vpn`, `other` |
| `niko_network_lost_total` | — |
| `niko_watchdog_total` | `action`: `recover`, `switch`, `no_alternative`, `ygg_peers`, `credential`, `dns_resolvers`, `doh_token`, `connections_failing` |
| `niko_url_test_total` | `profile`, `outcome`: `ok`, `fail` |
| `niko_traffic_bytes_total` | `dir`: `up`, `down`. Только когда включено отображение скорости (`traffic_counted`) |
| `niko_log_lines_total` | `level`: `warning`, `error`; `process` |
| `niko_process_rss_mb` (замер) | `process` |
| `niko_url_test_ms` (время) | `profile`, `outcome` |

Снимки идут раз в минуту, поэтому трафик за последнюю минуту окна может не попасть в отчёт.

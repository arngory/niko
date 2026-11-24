# Документация Niko

[English](README.md) | Русский

Документ говорит, как есть сейчас; что и когда менялось — в заметках выпусков и в git.

## Пользоваться Niko

- [Правила маршрутизации](guide/routing.ru.md)
- [Port knocking](guide/port-knocking.md)
- [Проверка Niko на настоящей ноде (VPS)](guide/vps-check.md)

## Как устроено

- [Отпечаток TLS: держать его равным Chrome](design/fingerprint.md)
- [Доверенные корни: что какой список берёт](design/trust.md)
- [Обход DPI без сервера: zapret и ByeDPI внутри Niko](design/dpi.md) (проект)
- [Niko для компьютера](design/desktop.md) и [его этап 3: `nikod` и `NikoService`](design/desktop-stage3.md)

## Контракты

- [Требования компьютера от экрана до ядра](spec/desktop-requirements.md)
- [Статистика за час, сторона клиента](spec/stats-client.md)
- Протоколы ноды (подписка, API, пробы, relay, обновления и остальное) описаны в raycontrol:
  [docs/spec](https://github.com/arngory/raycontrol/tree/main/docs/spec)

## Визуальный дизайн

- [Все функции против дизайна](ui/functions.md)
- [Старые экраны по каждой настройке и куда она уходит](ui/old-screens.md)
- [Один дизайн на всех платформах](ui/platforms.md)

## Сопровождение

- [Выпуск и подпись](guide/release.md)
- [CONTRIBUTING.ru.md](../CONTRIBUTING.ru.md): ветки, pull request, сообщения, закрепления
- [Решения](decisions.md) и [что впереди](roadmap.md)

## Исследования (с датой, в том виде, как написаны)

- [Анти-DPI: TCP и UDP](research/anti-dpi.md), 2026-11-01
- [Ревью: безопасность, блокировки, производительность, качество](research/review-2026-11.md), 2026-11
- [Ответы клиента на заметки raycontrol](research/raycontrol-handoff.md), 2026-11
- [Цена keepalive в покое](research/keepalive.md), 2026-11-17
- [Память на эмуляторе](research/memory.md), 2026-11
- [Маршрутизация по приложениям: списки, нода, обновление](research/routing-apps.md), 2026-11-16
- [Проба: экраны Android на Views или на Compose](research/ui-probe.md), 2026-11-20
- [Проба: экраны-списки из общего описания или руками](research/list-screens-probe.md), 2026-11-21

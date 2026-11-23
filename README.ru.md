# Niko

[English](README.md) | Русский

Niko — прокси- и VPN-клиент для Android на ядре [sing-box](https://github.com/SagerNet/sing-box). Принимает обычные
ссылки и подписки, а лучше всего работает с узлом [raycontrol](https://github.com/arngory/raycontrol): приглашение
регистрирует устройство, а узел сам держит его профили, имена и ключи в актуальном виде. Форк
[NekoBox for Android](https://github.com/MatsuriDayo/NekoBoxForAndroid).

## Скачать

Последняя версия — на странице [Releases](../../releases/latest): APK для arm64-v8a, armeabi-v7a, x86 и x86_64,
рядом `SHA256SUMS` с подписью minisign. Android 10 и новее. Приложение обновляется само: из релизов или через свой
узел raycontrol, если GitHub недоступен.

## Быстрый старт

1. Поставьте APK для своего телефона (у большинства — arm64-v8a).
2. Откройте ссылку-приглашение от администратора узла или добавьте профиль: **＋** → сканировать QR-код, импорт из
   буфера или файла, вручную. Подписка добавляется в **Группах**.
3. Нажмите кнопку подключения.

Правила маршрутизации: [docs/guide/routing.ru.md](docs/guide/routing.ru.md).

## Протоколы

SOCKS 4/4a/5, HTTP(S), SSH, Shadowsocks, VMess, VLESS (REALITY, XHTTP), Trojan, Trojan-Go, AnyTLS, ShadowTLS, TUIC,
Hysteria 1/2, WireGuard, AmneziaWG, NaïveProxy, Mieru, Yggdrasil и DNS-туннели (dnstt, MasterDnsVPN, slipstream) —
всё встроено. Подписки: Shadowsocks, ClashMeta, v2rayN и outbound sing-box.

## Сборка

Библиотеку на Go и APK собирает GitHub Actions ([build.yml](.github/workflows/build.yml)), там же подписываются
релизы ([docs/guide/release.md](docs/guide/release.md)). Окно для компьютера — в своём репозитории,
[arngory/niko-desktop](https://github.com/arngory/niko-desktop); его демон `nikod` — здесь (`libcore/cmd/nikod`).

## Дальше

- [docs/](docs/README.ru.md): инструкции, как устроено, спецификации, исследования
- [CONTRIBUTING.ru.md](CONTRIBUTING.ru.md): как вносить изменения

## Лицензия

GPL-3.0, см. [LICENSE](LICENSE). Niko — изменённая версия проектов ниже; их авторские права остаются за авторами.

| Проект | Авторы | Лицензия |
| --- | --- | --- |
| [NekoBox for Android](https://github.com/MatsuriDayo/NekoBoxForAndroid) | MatsuriDayo | GPL-3.0 |
| [SagerNet](https://github.com/SagerNet/SagerNet), [sing-box](https://github.com/SagerNet/sing-box), [sing-quic](https://github.com/SagerNet/sing-quic) | nekohasekai | GPL-3.0 |
| [sing-box-lx](https://github.com/Leadaxe/sing-box-lx) (изменения XHTTP и REALITY) | Leadaxe | GPL-3.0 |
| [shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android) | shadowsocks | GPL-3.0 |
| [Yacd-meta](https://github.com/MetaCubeX/Yacd-meta) | MetaCubeX | MIT |

Всё остальное, что несёт приложение (модули Go в ядре, Chromium, клиент slipstream, базы правил, библиотеки Android,
шрифты и значки), с лицензией каждого: [NOTICE](NOTICE).

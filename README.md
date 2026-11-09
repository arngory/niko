# Niko

Proxy client for Android built on [sing-box](https://github.com/SagerNet/sing-box).
A fork of [NekoBox for Android](https://github.com/MatsuriDayo/NekoBoxForAndroid).

[Русская версия ниже](#niko-ru)

## Download

APKs for arm64-v8a, armeabi-v7a, x86 and x86_64 are on the [Releases](../../releases) page, with
`SHA256SUMS` next to them. Android 6.0+.

## Protocols

SOCKS 4/4a/5, HTTP(S), SSH, Shadowsocks, VMess, VLESS (REALITY, XHTTP), Trojan, Trojan-Go, AnyTLS, ShadowTLS,
TUIC, Hysteria 1/2, WireGuard, AmneziaWG, NaïveProxy, Mieru, Yggdrasil, DNS tunnels (dnstt, MasterDnsVPN,
slipstream). All built in: no plugins to install.

## What the fork adds

- sing-box 1.14 core with XHTTP and REALITY with post-quantum key exchange
- Port knocking before connecting
- TLS ClientHello fragmentation and certificate pinning
- Remote rule-sets (`.srs`/`.json`), subscription headers, HWID for panels
- Hysteria2 UDP and many smaller fixes; see [Releases](../../releases)

## Subscriptions

Shadowsocks, ClashMeta, v2rayN and sing-box outbound formats. Only servers are imported, routing
rules from the subscription are ignored.

## Build

See [docs/RELEASE_SIGNING.md](docs/RELEASE_SIGNING.md) (Russian).

---

<a id="niko-ru"></a>

# Niko (RU)

Прокси-клиент для Android на [sing-box](https://github.com/SagerNet/sing-box). Форк
[NekoBox for Android](https://github.com/MatsuriDayo/NekoBoxForAndroid).

- **Скачать:** APK для arm64-v8a, armeabi-v7a, x86 и x86_64 на странице [Releases](../../releases), рядом
  `SHA256SUMS`. Android 6.0+.
- **Протоколы:** SOCKS 4/4a/5, HTTP(S), SSH, Shadowsocks, VMess, VLESS (REALITY, XHTTP), Trojan, Trojan-Go,
  AnyTLS, ShadowTLS, TUIC, Hysteria 1/2, WireGuard, AmneziaWG, NaïveProxy, Mieru, Yggdrasil, DNS-туннели (dnstt,
  MasterDnsVPN, slipstream). Все встроены: плагины ставить не нужно.
- **Что добавлено в форке:** ядро sing-box 1.14 с XHTTP и постквантовым REALITY, port knocking,
  фрагментация TLS ClientHello и пиннинг сертификатов, remote rule-set, заголовки подписок и HWID,
  исправления UDP в Hysteria2 и многое другое (см. [Releases](../../releases)).
- **Подписки:** форматы Shadowsocks, ClashMeta, v2rayN и outbound sing-box. Импортируются только серверы.

---

## License / Лицензия

GPL-3.0, see [LICENSE](LICENSE). Niko is a modified version of the projects below; their copyrights
stay with their authors.

| Project | Authors | License |
| --- | --- | --- |
| [NekoBox for Android](https://github.com/MatsuriDayo/NekoBoxForAndroid) | MatsuriDayo | GPL-3.0 |
| [libneko](https://github.com/MatsuriDayo/libneko) | MatsuriDayo | not stated, used as in NekoBox |
| [SagerNet](https://github.com/SagerNet/SagerNet), [sing-box](https://github.com/SagerNet/sing-box), [sing-quic](https://github.com/SagerNet/sing-quic) | nekohasekai | GPL-3.0 |
| [sing-box-lx](https://github.com/Leadaxe/sing-box-lx) (XHTTP, REALITY changes) | Leadaxe | GPL-3.0 |
| [shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android) | shadowsocks | GPL-3.0 |
| [Yacd-meta](https://github.com/MetaCubeX/Yacd-meta) | MetaCubeX | MIT |

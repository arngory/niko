# Niko

English | [Русский](README.ru.md)

Niko is a proxy and VPN client for Android on the [sing-box](https://github.com/SagerNet/sing-box) core. It takes
ordinary links and subscriptions, and works best with a [raycontrol](https://github.com/arngory/raycontrol) node: an
invite registers the device, and the node keeps its profiles, names and keys up to date. A fork of
[NekoBox for Android](https://github.com/MatsuriDayo/NekoBoxForAndroid).

## Download

The latest version is on the [Releases](../../releases/latest) page: APKs for arm64-v8a, armeabi-v7a, x86 and
x86_64, with `SHA256SUMS` signed by minisign next to them. Android 10 and newer. The app updates itself from the
releases, or through its raycontrol node where GitHub is out of reach.

## Quick start

1. Install the APK for your phone (most phones: arm64-v8a).
2. Open the invite link your node's administrator sent, or add a profile: **＋** → scan a QR code, import from the
   clipboard or a file, or enter it by hand. A subscription goes in **Groups**.
3. Tap the connect button.

Routing rules: [docs/guide/routing.md](docs/guide/routing.md).

## Protocols

SOCKS 4/4a/5, HTTP(S), SSH, Shadowsocks, VMess, VLESS (REALITY, XHTTP), Trojan, Trojan-Go, AnyTLS, ShadowTLS, TUIC,
Hysteria 1/2, WireGuard, AmneziaWG, NaïveProxy, Mieru, Yggdrasil and DNS tunnels (dnstt, MasterDnsVPN, slipstream),
all built in. Subscriptions: Shadowsocks, ClashMeta, v2rayN and sing-box outbounds.

## Building

GitHub Actions builds the Go library and the APKs ([build.yml](.github/workflows/build.yml)) and signs releases
([docs/guide/release.md](docs/guide/release.md)). The window for the computer is in its own repository,
[arngory/niko-desktop](https://github.com/arngory/niko-desktop); its daemon `nikod` is here (`libcore/cmd/nikod`).

## More

- [docs/](docs/README.md): guides, how it works, specs, research
- [CONTRIBUTING.md](CONTRIBUTING.md): how to make a change

## Support the project

Niko is given as is, free. If it helps you, you can support its development: [donotpromise.space/donate](https://donotpromise.space/donate).

## License

GPL-3.0, see [LICENSE](LICENSE). Niko is a modified version of the projects below; their copyrights stay with their
authors.

| Project | Authors | License |
| --- | --- | --- |
| [NekoBox for Android](https://github.com/MatsuriDayo/NekoBoxForAndroid) | MatsuriDayo | GPL-3.0 |
| [SagerNet](https://github.com/SagerNet/SagerNet), [sing-box](https://github.com/SagerNet/sing-box), [sing-quic](https://github.com/SagerNet/sing-quic) | nekohasekai | GPL-3.0 |
| [sing-box-lx](https://github.com/Leadaxe/sing-box-lx) (XHTTP, REALITY changes) | Leadaxe | GPL-3.0 |
| [shadowsocks-android](https://github.com/shadowsocks/shadowsocks-android) | shadowsocks | GPL-3.0 |
| [Yacd-meta](https://github.com/MetaCubeX/Yacd-meta) | MetaCubeX | MIT |

Everything else the app carries (the Go modules of the core, Chromium, the slipstream client, the rule databases,
the Android libraries, the fonts and icons), each with its license: [NOTICE](NOTICE).

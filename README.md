# Niko

English | [Русский](README.ru.md)

Niko is a proxy and VPN client for Android on the [sing-box](https://github.com/SagerNet/sing-box) core. It takes
ordinary links and subscriptions, and works best with a [raycontrol](https://github.com/arngory/raycontrol) node: an
invite registers the device, and the node keeps its profiles, names and keys up to date. A fork of
[NekoBox for Android](https://github.com/MatsuriDayo/NekoBoxForAndroid).

<p>
<img src="app/src/test/snapshots/images/io.niko.ui.home_HomeSnapshotTest_simpleConnected%5Blight%5D.png" width="200" alt="Simple"> <img src="app/src/test/snapshots/images/io.niko.ui.home_HomeSnapshotTest_advancedConnected%5Bdark%5D.png" width="200" alt="Advanced"> <img src="app/src/test/snapshots/images/io.niko.ui.node_NodeSnapshotTest_node%5Blight%5D.png" width="200" alt="A raycontrol node"> <img src="app/src/test/snapshots/images/io.niko.ui.log_LogSnapshotTest_log%5Bdark%5D.png" width="200" alt="Log">
</p>

The pictures are the app's own snapshot tests (Paparazzi), so they show the screens as they are now.

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

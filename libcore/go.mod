module libcore

go 1.26.0

// the toolchain the APK is built with (setup-go reads it): the standard library's security fixes (net/http,
// crypto/tls, crypto/x509, net/url), checked by .github/workflows/govulncheck.yml
toolchain go1.26.11

require (
	github.com/matsuridayo/libneko v1.0.0 // replaced
	github.com/miekg/dns v1.1.72
	github.com/oschwald/maxminddb-golang v1.13.1
	github.com/sagernet/quic-go v0.61.0-sing-box-mod.12
	github.com/sagernet/sing v0.9.7-0.20261023115459-5dc452e537bf
	github.com/sagernet/sing-box v1.0.0 // replaced
	github.com/sagernet/sing-tun v0.9.7-0.20261023115432-040db71fcd33
	github.com/ulikunitz/xz v0.5.15
	golang.org/x/mobile v0.0.0-20231108233038-35478a0c49da
	golang.org/x/sys v0.49.0
)

require (
	github.com/cloudflare/circl v1.6.5
	github.com/metacubex/utls v1.8.7
	github.com/yggdrasil-network/yggdrasil-go v0.5.14
	golang.org/x/crypto v0.58.0
)

require github.com/enfein/mieru/v3 v3.39.0 // indirect

require (
	filippo.io/edwards25519 v1.2.0 // indirect
	github.com/Arceliar/ironwood v0.0.0-20261023231900-0712eccbb962 // indirect
	github.com/Arceliar/phony v0.0.0-20220903101357-530938a4b13d // indirect
	github.com/BurntSushi/toml v1.4.1-0.20240526193622-a339e1f7089c // indirect
	github.com/ajg/form v1.5.1 // indirect
	github.com/andybalholm/brotli v1.2.7 // indirect
	github.com/anytls/sing-anytls v0.0.11 // indirect
	github.com/bits-and-blooms/bitset v1.26.0 // indirect
	github.com/bits-and-blooms/bloom/v3 v3.7.1 // indirect
	github.com/caddyserver/certmagic v0.25.3-0.20260421143802-60d9d8b415d6 // indirect
	github.com/caddyserver/zerossl v0.1.5 // indirect
	github.com/coder/websocket v1.8.15 // indirect
	github.com/database64128/netx-go v0.1.1 // indirect
	github.com/database64128/tfo-go/v2 v2.3.2 // indirect
	github.com/ebitengine/purego v0.10.0 // indirect
	github.com/florianl/go-nfqueue/v2 v2.1.0 // indirect
	github.com/flynn/noise v1.0.0 // indirect
	github.com/fsnotify/fsnotify v1.9.0 // indirect
	github.com/go-chi/chi/v5 v5.2.5 // indirect
	github.com/go-chi/render v1.0.3 // indirect
	github.com/go-ole/go-ole v1.3.0 // indirect
	github.com/gobwas/httphead v0.1.0 // indirect
	github.com/gobwas/pool v0.2.1 // indirect
	github.com/godbus/dbus/v5 v5.2.2 // indirect
	github.com/gofrs/uuid/v5 v5.5.1 // indirect
	github.com/gologme/log v1.3.0 // indirect
	github.com/google/btree v1.1.3 // indirect
	github.com/google/go-cmp v0.7.0 // indirect
	github.com/google/gopacket v1.1.19 // indirect
	github.com/hashicorp/yamux v0.1.2 // indirect
	github.com/huin/goupnp v1.3.0 // indirect
	github.com/jackpal/go-nat-pmp v1.0.2 // indirect
	github.com/jsimonetti/rtnetlink v1.4.1 // indirect
	github.com/klauspost/compress v1.20.2 // indirect
	github.com/klauspost/cpuid/v2 v2.3.0 // indirect
	github.com/klauspost/reedsolomon v1.12.0 // indirect
	github.com/koron/go-ssdp v0.0.4 // indirect
	github.com/libdns/acmedns v0.5.0 // indirect
	github.com/libdns/alidns v1.0.6 // indirect
	github.com/libdns/cloudflare v0.2.2 // indirect
	github.com/libdns/libdns v1.1.1 // indirect
	github.com/libp2p/go-nat v1.0.1-0.20250821073202-01afc089f138 // indirect
	github.com/libp2p/go-netroute v0.2.1 // indirect
	github.com/logrusorgru/aurora v2.0.3+incompatible // indirect
	github.com/mdlayher/netlink v1.11.2 // indirect
	github.com/mdlayher/socket v0.6.0 // indirect
	github.com/mholt/acmez/v3 v3.1.6 // indirect
	github.com/pierrec/lz4/v4 v4.1.26 // indirect
	github.com/pkg/errors v0.9.1 // indirect
	github.com/quic-go/qpack v0.6.0 // indirect
	github.com/quic-go/quic-go v0.64.1 // indirect
	github.com/sagernet/bbolt v0.0.0-20260915102804-500ee1e84832 // indirect
	github.com/sagernet/cors v1.2.1 // indirect
	github.com/sagernet/cronet-go v0.0.0-20261103043844-095cb9e06046 // indirect
	github.com/sagernet/cronet-go/all v0.0.0-20261103043844-095cb9e06046 // indirect
	github.com/sagernet/cronet-go/lib/android_386 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/android_amd64 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/android_arm v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/android_arm64 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/darwin_amd64 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/darwin_arm64 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/ios_amd64_simulator v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/ios_arm64 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/ios_arm64_simulator v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_386 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_386_musl v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_amd64 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_amd64_musl v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_arm v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_arm64 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_arm64_musl v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_arm_musl v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_loong64 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_loong64_musl v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_mips64le v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_mipsle v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_mipsle_musl v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_riscv64 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/linux_riscv64_musl v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/tvos_amd64_simulator v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/tvos_arm64 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/tvos_arm64_simulator v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/windows_amd64 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/cronet-go/lib/windows_arm64 v0.0.0-20261103043047-3aac4ed44c5c // indirect
	github.com/sagernet/fswatch v0.1.2 // indirect
	github.com/sagernet/gvisor v0.0.0-20260727.0-sing-box-mod.1 // indirect
	github.com/sagernet/netlink v0.0.0-20260814022025-64455d367bbf // indirect
	github.com/sagernet/nftables v0.3.0-mod.4 // indirect
	github.com/sagernet/sing-mux v0.3.10-0.20260929204512-caf09fe32475 // indirect
	github.com/sagernet/sing-quic v0.7.2-0.20261020082049-7e0a5869a5f4 // indirect
	github.com/sagernet/sing-shadowsocks v0.2.9-0.20260929204512-65740e0f0e3e // indirect
	github.com/sagernet/sing-shadowsocks2 v0.2.2-0.20260929152114-a69d1086332b // indirect
	github.com/sagernet/sing-shadowtls v0.2.2-0.20260928201441-a9c0127d5c99 // indirect
	github.com/sagernet/sing-snell v0.0.0-20260904135315-bc5a12ac736f // indirect
	github.com/sagernet/sing-usbip v0.0.0-20260817040617-28bd42667eca // indirect
	github.com/sagernet/sing-vmess v0.2.9-0.20261019223527-a0c58542bd9d // indirect
	github.com/sagernet/smux v1.5.50-sing-box-mod.1 // indirect
	github.com/sagernet/wireguard-go v0.0.8-0.20261022215841-e4d85ef2ed38 // indirect
	github.com/sagernet/ws v0.0.0-20231204124109-acfe8907c854 // indirect
	github.com/templexxx/cpu v0.1.0 // indirect
	github.com/templexxx/xorsimd v0.4.2 // indirect
	github.com/tjfoc/gmsm v1.4.1 // indirect
	github.com/vishvananda/netns v0.0.5 // indirect
	github.com/xtaci/kcp-go/v5 v5.6.8 // indirect
	github.com/xtaci/smux v1.5.24 // indirect
	github.com/zeebo/blake3 v0.2.4 // indirect
	go.uber.org/multierr v1.11.0 // indirect
	go.uber.org/zap v1.27.1 // indirect
	go.uber.org/zap/exp v0.3.0 // indirect
	go4.org/netipx v0.0.0-20231129151722-fdeea329fbba // indirect
	golang.org/x/exp v0.0.0-20260410095643-746e56fc9e2f // indirect
	golang.org/x/mod v0.41.0 // indirect
	golang.org/x/net v0.61.0 // indirect
	golang.org/x/sync v0.24.0 // indirect
	golang.org/x/text v0.43.0 // indirect
	golang.org/x/time v0.15.0 // indirect
	golang.org/x/tools v0.51.0 // indirect
	golang.zx2c4.com/wintun v0.0.0-20230126152724-0fa3db229ce2 // indirect
	google.golang.org/genproto/googleapis/rpc v0.0.0-20251202230838-ff82c1b0f217 // indirect
	google.golang.org/grpc v1.79.1 // indirect
	google.golang.org/protobuf v1.36.11
	lukechampine.com/blake3 v1.3.0 // indirect
	masterdnsvpn-go v0.0.0 // indirect
	www.bamsoftware.com/git/dnstt.git v1.20260501.0 // indirect
)

replace github.com/matsuridayo/libneko => ../../libneko

replace github.com/sagernet/sing-box => ../../sing-box

replace github.com/sagernet/sing-quic => ../../sing-box/third_party/sing-quic

replace github.com/sagernet/wireguard-go => ../../sing-box/third_party/wireguard-go

replace masterdnsvpn-go => ../../sing-box/third_party/masterdnsvpn

replace github.com/yggdrasil-network/yggdrasil-go => github.com/arngory/yggdrasil-go v0.5.16-0.20261108201637-5afe92f15200

replace github.com/sagernet/cronet-go => github.com/arngory/cronet-go v0.0.0-20261114140215-0b682fcacd50

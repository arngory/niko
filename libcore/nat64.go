package libcore

import "github.com/sagernet/sing-box/protocol/knock"

// SetNAT64Prefix: the NAT64 prefix of the network the app's own sockets use (Android's LinkProperties.getNat64Prefix,
// API 30+), "" for none. A SPA v3 knock at an address in it names the IPv4 the address embeds (spa-v3 §3), as one
// in 64:ff9b::/96 always does. Per process: the core's and libcore's own requests both read it.
func SetNAT64Prefix(prefix string) {
	knock.SetNAT64Prefix(prefix)
}

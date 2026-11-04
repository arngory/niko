//go:build with_dnstt

package libcore

import (
	"libcore/knock"

	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/protocol/dnstt"
	dnsttTransport "github.com/sagernet/sing-box/transport/dnstt"
)

// the DNS tunnel outbound (experimental), only in builds with with_dnstt
func registerDNSTT(registry *outbound.Registry) {
	// dnsauth-v1 signs with the node's device key, by the reference SPA uses: the key stays out of the config
	dnsttTransport.DeviceKeys = knock.DeviceKey
	dnstt.RegisterOutbound(registry)
}

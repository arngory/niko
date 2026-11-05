//go:build with_naive_outbound

package libcore

import (
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/protocol/naive"
)

// the naive outbound on cronet (Chromium's network stack in the core), only in builds with with_naive_outbound:
// it replaces the naive plugin (review S8)
func registerNaive(registry *outbound.Registry) {
	naive.RegisterOutbound(registry)
}

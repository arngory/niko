//go:build !with_naive_outbound

package libcore

import "github.com/sagernet/sing-box/adapter/outbound"

func registerNaive(registry *outbound.Registry) {}

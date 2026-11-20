// Package raynames is names-v1 (raycontrol docs/spec/names-v1.md §5) for the requests libcore makes to a node
// outside the core: the subscription, the remote API and the channel probe. The core's knock outbound does the same
// for the profiles (protocol/knock/names.go in the core).
package raynames

import (
	"context"
	"net"
	"net/netip"
	"slices"
	"strings"
	"time"
)

const (
	// a lookup of the node's name holds a request this long at most (§5 p. 3)
	LookupTimeout = 3 * time.Second
	// a connection to one of the node's addresses gives up after this long when another is left to try
	DialTimeout = 5 * time.Second
)

// Target is where a request goes: an address, and the name the knock's ClientHello and the TLS of the request
// carry ("" when the node is reached by address).
type Target struct {
	Address    string
	ServerName string
	// Signed: the address is the node's signed data's (its addrs, or the host given as an address), not only a DNS
	// answer: SPA v2 may follow a v3 that opened nothing only here (spa-v3 §6 p. 3, 4)
	Signed bool
}

// Lookup resolves a name: in the app the core's "local" DNS (libcore's NodeLookup: Android's resolver on the network
// under the VPN, never through the app's own tunnel, which may answer from FakeIP), a stub in tests; nil is
// net.DefaultResolver.
type Lookup func(ctx context.Context, name string) ([]netip.Addr, error)

// Targets: host resolved once, the addresses of the answer that are the node's (known, its signed addresses), in
// the answer's order; else, and when host does not resolve in LookupTimeout, the node's own (§5 p. 3–5). Without
// known addresses (a node before raycontrol v0.34.0, or one whose -host is a name), or for an address, host alone
// as before; but a node that takes SPA v3 (v3) and has a name alone gets its answer's addresses, each named by the
// knock, which is never v2 at the name (spa-v3 §6 p. 2, 4).
func Targets(ctx context.Context, lookup Lookup, host string, known []string, v3 bool) (targets []Target, warning string) {
	host = strings.TrimSuffix(strings.Trim(host, "[]"), ".")
	var addresses []netip.Addr
	for _, raw := range known {
		if addr, err := netip.ParseAddr(strings.Trim(strings.TrimSpace(raw), "[]")); err == nil && addr.Zone() == "" &&
			!slices.Contains(addresses, addr.Unmap()) {
			addresses = append(addresses, addr.Unmap())
		}
	}
	_, err := netip.ParseAddr(host)
	if err == nil {
		return []Target{{Address: host, Signed: true}}, ""
	}
	if len(addresses) == 0 && !v3 {
		return []Target{{Address: host}}, ""
	}
	if lookup == nil {
		lookup = func(ctx context.Context, name string) ([]netip.Addr, error) {
			return net.DefaultResolver.LookupNetIP(ctx, "ip", name)
		}
	}
	lookupCtx, cancel := context.WithTimeout(ctx, LookupTimeout)
	answer, err := lookup(lookupCtx, host)
	cancel()
	if len(addresses) == 0 {
		// SPA v3, a name alone: every address of the answer
		for _, addr := range answer {
			if addr = addr.Unmap(); addr.IsValid() && !addr.IsUnspecified() && !addr.IsMulticast() && !slices.Contains(addresses, addr) {
				addresses = append(addresses, addr)
			}
		}
		for _, addr := range addresses {
			targets = append(targets, Target{Address: addr.String(), ServerName: host})
		}
		if len(targets) == 0 {
			// the knock refuses a name: no v2 where the lookup failed
			return []Target{{Address: host}}, "no address for " + host + " to knock at with SPA v3"
		}
		return targets, ""
	}
	var picked []netip.Addr
	for _, addr := range answer {
		if addr = addr.Unmap(); slices.Contains(addresses, addr) && !slices.Contains(picked, addr) {
			picked = append(picked, addr)
		}
	}
	switch {
	case len(picked) > 0:
		addresses = picked
	case err != nil:
		warning = host + " did not resolve (" + err.Error() + "), the node's own addresses instead"
	case len(answer) == 0:
		warning = "no address for " + host + ", the node's own addresses instead"
	default:
		warning = "DNS answered " + joinAddrs(answer) + " for " + host + ", none of the node's addresses: taken as " +
			"forged, the knock goes to the node's own"
	}
	for _, addr := range addresses {
		targets = append(targets, Target{Address: addr.String(), ServerName: host, Signed: true})
	}
	return targets, warning
}

func joinAddrs(addresses []netip.Addr) string {
	parts := make([]string, len(addresses))
	for i, addr := range addresses {
		parts[i] = addr.String()
	}
	return strings.Join(parts, ", ")
}

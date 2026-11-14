package libcore

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"net"
	"time"

	"libcore/device"
	"libcore/dnsprobe"
)

type dnsProbeRequest struct {
	Resolver  string `json:"resolver"`
	Zone      string `json:"zone"`
	Key       string `json:"key"` // base64url, the subscription's dns_probe.key
	Large     bool   `json:"large"`
	TimeoutMs int    `json:"timeout_ms,omitempty"`
}

// RaycontrolDnsProbe checks a resolver for a node's DNS tunnel (raycontrol dnsprobe-v1): request as
// dnsProbeRequest, result as dnsprobe.Result, in JSON. Its sockets are protected from the app's own VPN: the
// resolver is the network's path, not the tunnel's.
func RaycontrolDnsProbe(requestJSON string) (result string, err error) {
	defer device.DeferPanicToError("raycontrol.DnsProbe", func(err_ error) { err = err_ })
	var r dnsProbeRequest
	if err = json.Unmarshal([]byte(requestJSON), &r); err != nil {
		return "", err
	}
	key, err := base64.RawURLEncoding.DecodeString(r.Key)
	if err != nil {
		return "", err
	}
	timeout := time.Duration(r.TimeoutMs) * time.Millisecond
	if timeout <= 0 || timeout > 15*time.Second {
		timeout = 4 * time.Second
	}
	// its DoT and DoH checks verify against Go's roots: the list of the setting (certs.go)
	applyRootStore()
	ctx, cancel := context.WithTimeout(context.Background(), timeout)
	defer cancel()
	dialer := &net.Dialer{Control: protectControl()}
	out := dnsprobe.Probe(ctx, dialer.DialContext, r.Resolver, r.Zone, key, r.Large, time.Now())
	raw, err := json.Marshal(out)
	return string(raw), err
}

package libcore

import (
	"context"
	"errors"
	"time"

	"github.com/miekg/dns"
	"github.com/sagernet/sing-box/adapter"
	sbdns "github.com/sagernet/sing-box/dns"
	"github.com/sagernet/sing/service"
)

// What DohCheck says besides an HTTP status.
const (
	DohAnswered  int32 = 0
	DohNoWord    int32 = -1
	DohNoServer  int32 = -2
	dohCheckName       = "www.gstatic.com."
)

// DohCheck asks the DNS server [tag] of a running core (the node's DoH is "dns-remote" while a node's profile runs)
// for one name, straight through that server's transport, past the DNS cache. It says how the server answered: 0 an
// answer of any rcode, the HTTP status when the server refused the query (raycontrol v0.27.0: 404 for a device token
// it no longer takes, or 403 or 404 by the mode of its site), -1 anything else (a timeout, a path that fails, a
// site that keeps silent: no word about the token), -2 no such server in the core.
func DohCheck(i *BoxInstance, tag string, timeoutMs int32) int32 {
	if i == nil {
		return DohNoServer
	}
	manager := service.FromContext[adapter.DNSTransportManager](i.ctx)
	if manager == nil {
		return DohNoServer
	}
	transport, found := manager.Transport(tag)
	if !found || transport.Type() != "https" {
		return DohNoServer
	}
	ctx, cancel := context.WithTimeout(i.ctx, time.Duration(timeoutMs)*time.Millisecond)
	defer cancel()
	query := new(dns.Msg)
	query.SetQuestion(dohCheckName, dns.TypeA)
	query.RecursionDesired = true
	_, err := transport.Exchange(ctx, query)
	return dohOutcome(err)
}

func dohOutcome(err error) int32 {
	if err == nil {
		return DohAnswered
	}
	var status *sbdns.HTTPStatusError
	if errors.As(err, &status) {
		return int32(status.StatusCode)
	}
	return DohNoWord
}

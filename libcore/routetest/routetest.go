// Package routetest says which rule of a running core a connection would match and where it would go: the route
// tester (after ThroneForAndroid's, the owner's decision of 2026-11-15), for the app's core (libcore.RouteTest) and the
// desktop daemon's (nikoservice). The connection is prepared and the rules walked as the router does (route.Router's
// prepareMatchMetadata and matchRule): through the VPN an app asks the core's DNS for a domain first and connects to
// the address it gets, a fake one standing for the domain; a sniff action takes what the port suggests; a resolve
// action resolves as the router's does. No connection is opened, but with resolve the core's DNS is really asked,
// through its DNS rules, and keeps what an app's query would leave: a FakeDNS address for the domain, an entry in its
// cache, a reverse mapping. Without resolve nothing is asked.
package routetest

import (
	"context"
	"errors"
	"fmt"
	"net"
	"net/netip"
	"os"
	"slices"
	"strings"
	"time"

	"github.com/miekg/dns"
	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/common/sniff"
	C "github.com/sagernet/sing-box/constant"
	R "github.com/sagernet/sing-box/route/rule"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/service"
)

// timeLimit bounds the lookups of one test: the app's own query and a resolve action's.
const timeLimit = 20 * time.Second

// timeoutError: a lookup that failed for time (a context's deadline, a socket's), not for what the DNS answered or
// for a missing server.
func timeoutError(err error) bool {
	if errors.Is(err, context.DeadlineExceeded) || errors.Is(err, os.ErrDeadlineExceeded) {
		return true
	}
	var netErr net.Error
	return errors.As(err, &netErr) && netErr.Timeout()
}

// ErrTimeLimit: a lookup of the test failed with all of timeLimit spent; the answer is not known, and no rule or DNS
// decided it (wraps context.DeadlineExceeded).
var ErrTimeLimit = fmt.Errorf("the lookups took the route tester's %v: %w", timeLimit, context.DeadlineExceeded)

// Query is a connection to try against the running core's rules: no connection is made.
type Query struct {
	// tcp or udp
	Network string `json:"network"`
	// a domain or an address
	Host string `json:"host"`
	Port uint16 `json:"port"`
	// the inbound it comes in by: tun-in (the VPN, the default) or mixed-in (the local proxy); the service says which
	Inbound string `json:"inbound,omitempty"`
	// the app's uid and package names, when the connection is an app's
	UID      int32    `json:"uid,omitempty"`
	Packages []string `json:"packages,omitempty"`
	// DNS queries allowed, through the core's DNS: the app's own query for a domain through the VPN, a resolve action's
	Resolve bool `json:"resolve,omitempty"`
}

// Step is a rule the connection matched on its way.
type Step struct {
	Index  int    `json:"index"`
	Rule   string `json:"rule"`
	Action string `json:"action"`
	// a resolve action's addresses
	Resolved []string `json:"resolved,omitempty"`
	// what the action did, or what of it was taken on trust
	Note string `json:"note,omitempty"`
}

type Result struct {
	// the rule that decided, -1: none, the final outbound took it (or a lookup before the rules failed)
	Index    int    `json:"index"`
	Rule     string `json:"rule,omitempty"`
	Action   string `json:"action"`
	Outbound string `json:"outbound,omitempty"`
	// a lookup the connection cannot do without failed, and it goes nowhere: the app's own DNS query through the VPN,
	// a resolve action's, a fake address the core no longer remembers
	Error string `json:"error,omitempty"`
	// the connection as the rules first saw it: the address the app's DNS query gave it, or the domain its fake
	// address stands for
	Destination string `json:"destination,omitempty"`
	FakeIP      bool   `json:"fake_ip,omitempty"`
	// what the core's DNS answered the app's own query through the VPN
	Resolved []string `json:"resolved,omitempty"`
	// what the rules did not see as they would see a real connection
	Notes []string `json:"notes,omitempty"`
	Steps []Step   `json:"steps"`
}

func Run(ctx context.Context, q Query) (*Result, error) {
	router := service.FromContext[adapter.Router](ctx)
	outbounds := service.FromContext[adapter.OutboundManager](ctx)
	if router == nil || outbounds == nil {
		return nil, fmt.Errorf("the core has no router")
	}
	network := strings.ToLower(strings.TrimSpace(q.Network))
	if network == "" {
		network = N.NetworkTCP
	}
	if network != N.NetworkTCP && network != N.NetworkUDP {
		return nil, fmt.Errorf("network %q: tcp or udp", q.Network)
	}
	host := strings.ToLower(strings.TrimSuffix(strings.TrimSpace(q.Host), "."))
	if host == "" {
		return nil, fmt.Errorf("no host")
	}
	inbound, inboundType, err := inbound(ctx, q.Inbound)
	if err != nil {
		return nil, err
	}
	// the lookups below: the binder's caller waits for them, at most this long. A core stopped or a caller gone ends
	// the walk with the caller's error. A lookup that fails with the limit run out ends it with ErrTimeLimit, never as
	// a verdict (it would read as the app's DNS failing, or the router ending the connection there); a lookup that
	// answered just before the limit still gives its answer, the rules after it need no time
	caller := ctx
	ctx, cancel := context.WithTimeout(ctx, timeLimit)
	defer cancel()
	// a lookup's failure that is the limit's: a timeout, with the limit spent and the caller still there
	timedOut := func(err error) bool { return timeoutError(err) && ctx.Err() != nil && caller.Err() == nil }

	metadata := adapter.InboundContext{Inbound: inbound, InboundType: inboundType, Network: network}
	if q.UID > 0 || len(q.Packages) > 0 {
		metadata.ProcessInfo = &adapter.ConnectionOwner{UserId: q.UID, AndroidPackageNames: q.Packages}
	}
	result := &Result{Index: -1, Steps: []Step{}}
	addr, err := netip.ParseAddr(strings.Trim(host, "[]"))
	if err != nil && !M.IsDomainName(host) {
		return nil, fmt.Errorf("host %q: a domain or an address", q.Host)
	}
	if err == nil {
		metadata.Destination = M.SocksaddrFrom(addr.Unmap(), q.Port)
	} else if inboundType == C.TypeTun && q.Resolve {
		// through the VPN an app asks DNS first, and the VPN's DNS is the core's: the address it gets is what the
		// rules see, a fake one standing for the domain
		addresses, err := appQuery(ctx, metadata, host)
		if err != nil && timedOut(err) {
			return nil, ErrTimeLimit
		}
		if err != nil {
			result.Action, result.Error, result.Destination = "dns", err.Error(), host
			return result, nil
		}
		for _, address := range addresses {
			result.Resolved = append(result.Resolved, address.String())
		}
		metadata.Destination = M.SocksaddrFrom(addresses[0], q.Port)
	} else {
		// through the local proxy an app hands it the domain
		metadata.Destination = M.Socksaddr{Fqdn: host, Port: q.Port}
		if inboundType == C.TypeTun {
			result.Notes = append(result.Notes, "dns: not asked; through the VPN an app would connect to an address, the address rules saw none")
		}
	}
	if err = prepare(ctx, &metadata); err != nil {
		result.Action, result.Error, result.Destination = "fakeip", err.Error(), metadata.Destination.String()
		return result, nil
	}
	result.Destination, result.FakeIP = metadata.Destination.String(), metadata.FakeIP
	// the name a sniff would find in a TLS, HTTP or QUIC: the one asked for, or the one its address stands for
	name := metadata.Destination.Fqdn
	if name == "" {
		name = metadata.Domain
	}
	if !addr.IsValid() {
		name = host
	}

	for index, rule := range router.Rules() {
		// a core stopped or a caller gone: no answer from rules being closed (their rule sets emptied)
		if err := caller.Err(); err != nil {
			return nil, err
		}
		metadata.ResetRuleCache()
		if !rule.Match(&metadata) {
			continue
		}
		action := rule.Action()
		step := Step{Index: index, Rule: rule.String(), Action: action.String()}
		var routeOptions *R.RuleActionRouteOptions
		switch a := action.(type) {
		case *R.RuleActionRoute:
			routeOptions = &a.RuleActionRouteOptions
		case *R.RuleActionRouteOptions:
			routeOptions = a
		case *R.RuleActionBypass:
			if a.Outbound != "" {
				routeOptions = &a.RuleActionRouteOptions
			}
		}
		if routeOptions != nil {
			step.Note = override(&metadata, routeOptions)
		}
		switch a := action.(type) {
		case *R.RuleActionSniff:
			step.Note = sniffAction(&metadata, a, name)
		case *R.RuleActionResolve:
			// as the router: only a domain is resolved
			if !metadata.Destination.IsDomain() {
				break
			}
			if !q.Resolve {
				step.Note = "resolve: skipped, no DNS queries asked"
				break
			}
			addresses, err := resolve(ctx, &metadata, a)
			if err != nil && timedOut(err) {
				return nil, ErrTimeLimit
			}
			if err != nil {
				// the router ends the connection on it
				result.Steps = append(result.Steps, step)
				result.Index, result.Rule, result.Action, result.Error = index, step.Rule, step.Action, err.Error()
				return result, nil
			}
			metadata.DestinationAddresses = addresses
			for _, address := range addresses {
				step.Resolved = append(step.Resolved, address.String())
			}
		}
		result.Steps = append(result.Steps, step)
		switch action.Type() {
		case C.RuleActionTypeRoute, C.RuleActionTypeReject, C.RuleActionTypeHijackDNS:
			result.Index, result.Rule, result.Action = index, step.Rule, step.Action
			if route, ok := action.(*R.RuleActionRoute); ok {
				result.Outbound = route.Outbound
			}
			return result, nil
		case C.RuleActionTypeBypass:
			if bypass, ok := action.(*R.RuleActionBypass); ok && bypass.Outbound != "" {
				result.Index, result.Rule, result.Action, result.Outbound = index, step.Rule, step.Action, bypass.Outbound
				return result, nil
			}
		}
	}
	result.Action = "final"
	if final := outbounds.Default(); final != nil {
		result.Outbound = final.Tag()
	}
	return result, nil
}

// inbound is the inbound's tag and type: the app's two, or another of the running core's.
func inbound(ctx context.Context, tag string) (string, string, error) {
	switch tag {
	case "", "tun-in":
		return "tun-in", C.TypeTun, nil
	case "mixed-in":
		return "mixed-in", C.TypeMixed, nil
	}
	if manager := service.FromContext[adapter.InboundManager](ctx); manager != nil {
		if inbound, found := manager.Get(tag); found {
			return tag, inbound.Type(), nil
		}
	}
	return "", "", fmt.Errorf("no inbound %q", tag)
}

// appQuery is an app's own DNS query through the VPN, which the core answers (hijack-dns): its DNS rules see
// the app's inbound and owner, as they would the query. A first, then AAAA; the app connects to the first address.
func appQuery(ctx context.Context, connection adapter.InboundContext, domain string) ([]netip.Addr, error) {
	dnsRouter := service.FromContext[adapter.DNSRouter](ctx)
	if dnsRouter == nil {
		return nil, fmt.Errorf("the core has no DNS")
	}
	query := connection
	query.Network = N.NetworkUDP
	var (
		addresses []netip.Addr
		failure   error
	)
	for _, queryType := range []uint16{dns.TypeA, dns.TypeAAAA} {
		message := new(dns.Msg)
		message.SetQuestion(dns.Fqdn(domain), queryType)
		response, err := dnsRouter.Exchange(adapter.WithContext(ctx, &query), message, adapter.DNSQueryOptions{})
		if err == nil && response.Rcode != dns.RcodeSuccess {
			err = fmt.Errorf("%s %s: %s", dns.TypeToString[queryType], domain, dns.RcodeToString[response.Rcode])
		}
		if err != nil {
			if failure == nil {
				failure = err
			}
			continue
		}
		for _, answer := range response.Answer {
			switch record := answer.(type) {
			case *dns.A:
				addresses = append(addresses, M.AddrFromIP(record.A))
			case *dns.AAAA:
				addresses = append(addresses, M.AddrFromIP(record.AAAA))
			}
		}
	}
	if len(addresses) > 0 {
		return addresses, nil
	}
	if failure != nil {
		return nil, failure
	}
	return nil, fmt.Errorf("%s: no address", domain)
}

// prepare is the router's prepareMatchMetadata: a fake address stands for its domain, an address the DNS
// gave for a domain (dns.reverse_mapping) names it, and the address's version.
func prepare(ctx context.Context, metadata *adapter.InboundContext) error {
	addr := metadata.Destination.Addr
	transports := service.FromContext[adapter.DNSTransportManager](ctx)
	if addr.IsValid() && transports != nil && transports.FakeIP() != nil && transports.FakeIP().Store().Contains(addr) {
		domain, loaded := transports.FakeIP().Store().Lookup(addr)
		if !loaded {
			return fmt.Errorf("%s is a fake address the core does not remember", addr)
		}
		if domain != "" {
			metadata.OriginDestination = metadata.Destination
			metadata.Destination = M.Socksaddr{Fqdn: domain, Port: metadata.Destination.Port}
			metadata.FakeIP = true
		}
	} else if addr.IsValid() && metadata.Domain == "" {
		if dnsRouter := service.FromContext[adapter.DNSRouter](ctx); dnsRouter != nil {
			if domain, loaded := dnsRouter.LookupReverseMapping(addr); loaded {
				metadata.Domain = domain
			}
		}
	}
	if metadata.Destination.IsIPv4() {
		metadata.IPVersion = 4
	} else if metadata.Destination.IsIPv6() {
		metadata.IPVersion = 6
	}
	return nil
}

// sniffAction stands for a sniff action, which reads the connection's first bytes, here unknown: the protocol is
// what the network and port suggest (TLS on TCP 443, HTTP on TCP 80, QUIC on UDP 443, DNS on 53), and a TLS, HTTP or
// QUIC carries the name asked for. As the router's actionSniff, an action of packet sniffers alone leaves TCP alone and
// one of stream sniffers alone leaves UDP alone. On a TCP port of no known protocol the name is taken (a TLS or HTTP
// on another port carries it); on such a UDP port it is not (only a QUIC would, and games, STUN or WireGuard do not).
// The note says what was taken.
func sniffAction(metadata *adapter.InboundContext, action *R.RuleActionSniff, name string) string {
	if sniff.Skip(metadata) {
		return "sniff: skipped, the server speaks first on this port"
	}
	if metadata.Protocol != "" {
		return "sniff: done before"
	}
	tcp := metadata.Network == N.NetworkTCP
	if tcp && len(action.StreamSniffers) == 0 && len(action.PacketSniffers) > 0 {
		return "sniff: its sniffers are for UDP, TCP is not sniffed"
	}
	if !tcp && len(action.PacketSniffers) == 0 && len(action.StreamSniffers) > 0 {
		return "sniff: its sniffers are for TCP, UDP is not sniffed"
	}
	// no names: the router's default sniffers, all of them
	sniffs := func(protocol string) bool {
		return len(action.SnifferNames) == 0 || slices.Contains(action.SnifferNames, protocol)
	}
	var protocol string
	port := metadata.Destination.Port
	switch {
	case port == 53:
		protocol = C.ProtocolDNS
	case tcp && port == 443:
		protocol = C.ProtocolTLS
	case tcp && port == 80:
		protocol = C.ProtocolHTTP
	case !tcp && port == 443:
		protocol = C.ProtocolQUIC
	}
	if protocol != "" && !sniffs(protocol) {
		return "sniff: " + protocol + " is not among its sniffers"
	}
	unknown := "sniff: what runs on " + metadata.Network + "/" + fmt.Sprint(port) + " is not known here"
	var note string
	switch {
	case protocol == C.ProtocolDNS:
		// the name of a query is the one asked of the server, not the server's
		metadata.Protocol = protocol
		return "sniff: taken as dns"
	case protocol != "":
		metadata.Protocol = protocol
		if name == "" {
			return "sniff: taken as " + protocol + ", its name not known here"
		}
		note = "sniff: taken as " + protocol + " with the name asked for"
	case !tcp:
		return unknown + ": taken as not QUIC, so without a name; protocol rules were not checked"
	case name == "" || !sniffs(C.ProtocolTLS) && !sniffs(C.ProtocolHTTP):
		return unknown + ": protocol rules were not checked"
	default:
		note = unknown + ": the name taken as a TLS or HTTP would carry it, protocol rules were not checked"
	}
	metadata.Domain = name
	if action.OverrideDestination && M.IsDomainName(metadata.Domain) {
		metadata.Destination = M.Socksaddr{Fqdn: metadata.Domain, Port: metadata.Destination.Port}
	}
	return note
}

// override is the router's applyRouteOptionsOverride: an address or port a route option puts in place of the
// destination, which the later rules see.
func override(metadata *adapter.InboundContext, options *R.RuleActionRouteOptions) string {
	if !options.OverrideAddress.IsValid() && options.OverridePort == 0 {
		return ""
	}
	if options.OverrideAddress.IsValid() {
		metadata.DestinationAddresses = nil
		metadata.Destination = M.Socksaddr{
			Addr: options.OverrideAddress.Addr,
			Port: metadata.Destination.Port,
			Fqdn: options.OverrideAddress.Fqdn,
		}
	}
	if options.OverridePort > 0 {
		metadata.Destination = M.Socksaddr{
			Addr: metadata.Destination.Addr,
			Port: options.OverridePort,
			Fqdn: metadata.Destination.Fqdn,
		}
	}
	return "destination now " + metadata.Destination.String()
}

// resolve is the router's actionResolve: the action's server and options, the connection's metadata for the
// DNS rules; a failure is the router's end of the connection.
func resolve(ctx context.Context, metadata *adapter.InboundContext, action *R.RuleActionResolve) ([]netip.Addr, error) {
	dnsRouter := service.FromContext[adapter.DNSRouter](ctx)
	if dnsRouter == nil {
		return nil, fmt.Errorf("the core has no DNS")
	}
	var transport adapter.DNSTransport
	if action.Server != "" {
		var loaded bool
		if manager := service.FromContext[adapter.DNSTransportManager](ctx); manager != nil {
			transport, loaded = manager.Transport(action.Server)
		}
		if !loaded {
			return nil, fmt.Errorf("DNS server not found: %s", action.Server)
		}
	}
	return dnsRouter.Lookup(adapter.WithContext(ctx, metadata), metadata.Destination.Fqdn, adapter.DNSQueryOptions{
		Transport:              transport,
		Strategy:               action.Strategy,
		DisableCache:           action.DisableCache,
		DisableOptimisticCache: action.DisableOptimisticCache,
		RewriteTTL:             action.RewriteTTL,
		Timeout:                action.Timeout,
		ClientSubnet:           action.ClientSubnet,
	})
}

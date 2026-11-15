package libcore

import (
	"context"
	"encoding/json"
	"fmt"
	"net/netip"
	"strings"
	"time"

	"github.com/sagernet/sing-box/adapter"
	C "github.com/sagernet/sing-box/constant"
	R "github.com/sagernet/sing-box/route/rule"
	M "github.com/sagernet/sing/common/metadata"
	"github.com/sagernet/sing/service"
)

// routeTestQuery is a connection to try against the running core's rules: no connection is made.
type routeTestQuery struct {
	// tcp or udp
	Network string `json:"network"`
	// a domain or an address
	Host string `json:"host"`
	Port uint16 `json:"port"`
	// the app's uid and package names, when the connection is an app's
	UID      int32    `json:"uid,omitempty"`
	Packages []string `json:"packages,omitempty"`
	// resolve a domain where a rule says so (the resolve action), through the core's DNS
	Resolve bool `json:"resolve,omitempty"`
}

// routeTestStep is a rule the connection matched on its way.
type routeTestStep struct {
	Index  int    `json:"index"`
	Rule   string `json:"rule"`
	Action string `json:"action"`
	// what came of a resolve action: the addresses, or why there are none
	Resolved []string `json:"resolved,omitempty"`
	Note     string   `json:"note,omitempty"`
}

type routeTestResult struct {
	// the rule that decided, -1: none, the final outbound took it
	Index    int             `json:"index"`
	Rule     string          `json:"rule,omitempty"`
	Action   string          `json:"action"`
	Outbound string          `json:"outbound,omitempty"`
	Steps    []routeTestStep `json:"steps"`
}

// RouteTest says which rule of the running core [i] a connection would match and where it would go: the route
// tester of the app (after ThroneForAndroid's, the owner's decision of 2026-11-15). [query] is JSON: network, host,
// port, an app's uid and package names, resolve. The rules are walked as the router walks them; a sniff action is
// taken as done (the domain asked for is the one a sniff would find), a resolve action resolves only with resolve set.
// Nothing is dialled.
func RouteTest(i *BoxInstance, query string) (string, error) {
	if i == nil {
		return "", fmt.Errorf("the core does not run")
	}
	var q routeTestQuery
	if err := json.Unmarshal([]byte(query), &q); err != nil {
		return "", fmt.Errorf("query: %w", err)
	}
	result, err := routeTest(i.ctx, q)
	if err != nil {
		return "", err
	}
	raw, err := json.Marshal(result)
	return string(raw), err
}

func routeTest(ctx context.Context, q routeTestQuery) (*routeTestResult, error) {
	router := service.FromContext[adapter.Router](ctx)
	outbounds := service.FromContext[adapter.OutboundManager](ctx)
	if router == nil || outbounds == nil {
		return nil, fmt.Errorf("the core has no router")
	}
	network := strings.ToLower(strings.TrimSpace(q.Network))
	if network == "" {
		network = "tcp"
	}
	if network != "tcp" && network != "udp" {
		return nil, fmt.Errorf("network %q: tcp or udp", q.Network)
	}
	host := strings.TrimSuffix(strings.TrimSpace(q.Host), ".")
	if host == "" {
		return nil, fmt.Errorf("no host")
	}
	metadata := adapter.InboundContext{Inbound: "tun-in", InboundType: C.TypeTun, Network: network}
	if addr, err := netip.ParseAddr(strings.Trim(host, "[]")); err == nil {
		metadata.Destination = M.SocksaddrFrom(addr.Unmap(), q.Port)
	} else {
		metadata.Destination = M.Socksaddr{Fqdn: host, Port: q.Port}
		metadata.Domain = host
	}
	if q.UID > 0 || len(q.Packages) > 0 {
		metadata.ProcessInfo = &adapter.ConnectionOwner{UserId: q.UID, AndroidPackageNames: q.Packages}
	}
	result := &routeTestResult{Index: -1, Steps: []routeTestStep{}}
	for index, rule := range router.Rules() {
		metadata.ResetRuleCache()
		if !rule.Match(&metadata) {
			continue
		}
		action := rule.Action()
		step := routeTestStep{Index: index, Rule: rule.String(), Action: action.String()}
		switch a := action.(type) {
		case *R.RuleActionSniff:
			step.Note = "sniff: taken as done"
		case *R.RuleActionResolve:
			if !q.Resolve || metadata.Domain == "" {
				step.Note = "resolve: skipped"
				break
			}
			step.Resolved, step.Note = routeTestResolve(ctx, metadata.Domain, a)
			for _, address := range step.Resolved {
				if addr, err := netip.ParseAddr(address); err == nil {
					metadata.DestinationAddresses = append(metadata.DestinationAddresses, addr)
				}
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

// routeTestResolve resolves a domain through the core's DNS as a resolve action would.
func routeTestResolve(ctx context.Context, domain string, action *R.RuleActionResolve) ([]string, string) {
	dnsRouter := service.FromContext[adapter.DNSRouter](ctx)
	if dnsRouter == nil {
		return nil, "resolve: the core has no DNS"
	}
	ctx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	options := adapter.DNSQueryOptions{Strategy: action.Strategy, DisableCache: action.DisableCache, ClientSubnet: action.ClientSubnet}
	if action.Server != "" {
		if manager := service.FromContext[adapter.DNSTransportManager](ctx); manager != nil {
			transport, found := manager.Transport(action.Server)
			if !found {
				return nil, "resolve: no DNS server " + action.Server
			}
			options.Transport = transport
		}
	}
	addresses, err := dnsRouter.Lookup(ctx, domain, options)
	if err != nil {
		return nil, "resolve: " + err.Error()
	}
	resolved := make([]string, 0, len(addresses))
	for _, address := range addresses {
		resolved = append(resolved, address.String())
	}
	return resolved, ""
}

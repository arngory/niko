package nikoservice

import (
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"net"
	"os"
	"slices"
	"sort"
	"strings"
	"sync"
	"time"

	"libcore/dnsprobe"
	"libcore/nikoconf"
	"libcore/nikofmt"
	"libcore/nikoray"
)

// DNS tunnel resolver dynamics (BG-07 / Stage 3): port of Android's RayDnsResolvers.kt onto
// libcore/dnsprobe. Selects working DNS resolvers per network for dnstt and MasterDnsVPN,
// supports dnszones-v1 spare zones, caches positive picks for 24h and negative picks for 1h,
// and exposes a redacted kind summary ("net", "udp", "tcp", "dot", "doh") for D-NODE-10.

const (
	dnsPickPositiveTTL = 24 * time.Hour
	dnsPickNegativeTTL = 1 * time.Hour
	dnsProbeTimeout    = 4 * time.Second
)

type dnsCandidate struct {
	Resolver string
	Kind     string // "net", "udp", "tcp", "dot", "doh"
}

type dnsPick struct {
	Resolver  string
	Kind      string
	Zone      string
	Large     bool
	EDNS      int
	ExpiresAt time.Time
}

var countryDNSResolvers = map[string][]dnsCandidate{
	"ru": {
		{Resolver: "195.208.5.1", Kind: "udp"},
		{Resolver: "195.208.4.1", Kind: "udp"},
		{Resolver: "62.76.76.62", Kind: "udp"},
		{Resolver: "77.88.8.8", Kind: "udp"},
		{Resolver: "77.88.8.1", Kind: "udp"},
		{Resolver: "tls://common.dot.dns.yandex.net", Kind: "dot"},
		{Resolver: "https://common.dot.dns.yandex.net/dns-query", Kind: "doh"},
	},
	"cn": {
		{Resolver: "119.29.29.29", Kind: "udp"},
		{Resolver: "223.5.5.5", Kind: "udp"},
		{Resolver: "1.2.4.8", Kind: "udp"},
	},
}

var yandexUDPResolvers = map[string]bool{
	"77.88.8.8": true,
	"77.88.8.1": true,
}

type dnsResolverCache struct {
	mu          sync.Mutex
	picks       map[string]dnsPick // key: engine + ":" + nodeID + "@" + networkKey
	probeFn     func(ctx context.Context, resolver, zone string, key []byte, large bool) dnsprobe.Result
	networkKey  func() string
	systemDNSFn func() []string
}

func newDNSResolverCache(probeFn func(ctx context.Context, resolver, zone string, key []byte, large bool) dnsprobe.Result) *dnsResolverCache {
	return &dnsResolverCache{
		picks:   map[string]dnsPick{},
		probeFn: probeFn,
	}
}

func (c *dnsResolverCache) currentNetworkKey() string {
	if c.networkKey != nil {
		return c.networkKey()
	}
	return desktopNetworkFingerprint()
}

func (c *dnsResolverCache) systemResolvers() []string {
	if c.systemDNSFn != nil {
		return c.systemDNSFn()
	}
	return readResolvConfNameservers("/etc/resolv.conf")
}

// isVirtualInterface filters local tunnels, container bridges, and VM adapters that come and go
// independently of the host's actual upstream network.
func isVirtualInterface(name string) bool {
	lower := strings.ToLower(name)
	for _, prefix := range []string{
		"niko", "tun", "utun", "tap",
		"docker", "virbr", "veth", "br-", "cni", "flannel", "podman",
		"wg", "tailscale", "wsl", "vethernet", "vmnet", "vboxnet", "lo",
	} {
		if strings.HasPrefix(lower, prefix) {
			return true
		}
	}
	return false
}

// desktopNetworkFingerprint builds a deterministic, non-reversible hash of active non-loopback
// physical interface names and IP prefixes so DNS/datagram picks refresh when switching networks.
func desktopNetworkFingerprint() string {
	ifaces, err := net.Interfaces()
	if err != nil {
		return "default"
	}
	var parts []string
	for _, iface := range ifaces {
		if iface.Flags&net.FlagUp == 0 || iface.Flags&net.FlagLoopback != 0 {
			continue
		}
		if isVirtualInterface(iface.Name) {
			continue
		}
		addrs, err := iface.Addrs()
		if err != nil {
			continue
		}
		for _, a := range addrs {
			if ipNet, ok := a.(*net.IPNet); ok && !ipNet.IP.IsLinkLocalUnicast() && !ipNet.IP.IsLoopback() {
				parts = append(parts, iface.Name+":"+ipNet.String())
			}
		}
	}
	if len(parts) == 0 {
		return "default"
	}
	sort.Strings(parts)
	sum := sha256.Sum256([]byte(strings.Join(parts, "|")))
	return hex.EncodeToString(sum[:8])
}

func readResolvConfNameservers(path string) []string {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil
	}
	var out []string
	for _, line := range strings.Split(string(data), "\n") {
		line = strings.TrimSpace(line)
		if !strings.HasPrefix(line, "nameserver") {
			continue
		}
		fields := strings.Fields(line)
		if len(fields) < 2 {
			continue
		}
		ip := net.ParseIP(fields[1])
		if ip == nil || ip.IsLoopback() || ip.IsLinkLocalUnicast() {
			continue
		}
		addr := ip.String()
		if strings.Contains(addr, ":") {
			addr = "[" + addr + "]"
		}
		out = append(out, addr)
	}
	return out
}

func buildDNSCandidates(systemDNS []string, country string, hintKind string) []dnsCandidate {
	var raw []dnsCandidate
	for _, addr := range systemDNS {
		if addr != "" {
			raw = append(raw, dnsCandidate{Resolver: addr, Kind: "net"})
		}
	}
	if list, ok := countryDNSResolvers[strings.ToLower(country)]; ok {
		raw = append(raw, list...)
	} else {
		raw = append(raw, countryDNSResolvers["ru"]...)
	}
	// Expand each UDP candidate with a TCP 53 fallback right after it (matches RayDnsResolvers.withTcp).
	var expanded []dnsCandidate
	for _, cand := range raw {
		expanded = append(expanded, cand)
		if cand.Kind == "net" || cand.Kind == "udp" {
			expanded = append(expanded, dnsCandidate{Resolver: "tcp://" + cand.Resolver, Kind: "tcp"})
		}
	}
	if hintKind != "" {
		sort.SliceStable(expanded, func(i, j int) bool {
			rank := func(k string) int {
				if k == "net" {
					return 0
				}
				if k == hintKind {
					return 1
				}
				return 2
			}
			return rank(expanded[i].Kind) < rank(expanded[j].Kind)
		})
	}
	seen := map[string]bool{}
	var out []dnsCandidate
	for _, cand := range expanded {
		if !seen[cand.Resolver] {
			seen[cand.Resolver] = true
			out = append(out, cand)
		}
	}
	return out
}

func masterdnsAutoResolvers(systemDNS []string, country string, includeFallback bool) string {
	var primary []string
	seen := map[string]bool{}
	add := func(addr string) {
		clean := strings.TrimPrefix(strings.TrimSuffix(strings.TrimSpace(addr), "]"), "[")
		if clean == "" || seen[clean] {
			return
		}
		seen[clean] = true
		primary = append(primary, clean)
	}
	for _, addr := range systemDNS {
		if !yandexUDPResolvers[addr] {
			add(addr)
		}
	}
	list := countryDNSResolvers[strings.ToLower(country)]
	if len(list) == 0 {
		list = countryDNSResolvers["ru"]
	}
	for _, cand := range list {
		if cand.Kind == "udp" && !yandexUDPResolvers[cand.Resolver] {
			add(cand.Resolver)
		}
	}
	if includeFallback || len(primary) == 0 {
		add("77.88.8.8")
		add("77.88.8.1")
	}
	return strings.Join(primary, "\n")
}

func decodeDNSProbeKey(raw string) []byte {
	trimmed := strings.TrimSpace(raw)
	if trimmed == "" {
		return nil
	}
	if b, err := hex.DecodeString(trimmed); err == nil && len(b) == 32 {
		return b
	}
	for _, enc := range []*base64.Encoding{base64.RawURLEncoding, base64.URLEncoding, base64.StdEncoding, base64.RawStdEncoding} {
		if b, err := enc.DecodeString(trimmed); err == nil && len(b) == 32 {
			return b
		}
	}
	return nil
}

// ProbeNodeDnstt probes candidate DNS resolvers against the node's primary and spare zones using dnsprobe-v1,
// caching the best working pick (preferring Large EDNS) for the current network.
func (c *dnsResolverCache) ProbeNodeDnstt(ctx context.Context, node *nikoray.Node, primaryZone string, spareZones []string, now time.Time) (dnsPick, bool) {
	if node == nil || primaryZone == "" || node.Probe == nil {
		return dnsPick{}, false
	}
	key := decodeDNSProbeKey(node.Probe.DNSKey)
	if len(key) != 32 {
		return dnsPick{}, false
	}
	netKey := c.currentNetworkKey()
	cacheKey := "dnstt:" + node.ID() + "@" + netKey

	c.mu.Lock()
	if existing, ok := c.picks[cacheKey]; ok && now.Before(existing.ExpiresAt) {
		c.mu.Unlock()
		return existing, existing.Kind != "none" && existing.Resolver != ""
	}
	c.mu.Unlock()

	hintKind := ""
	if node.Hints != nil {
		hintKind = node.Hints.DNS
	}
	candidates := buildDNSCandidates(c.systemResolvers(), "ru", hintKind)
	zones := append([]string{primaryZone}, spareZones...)

	var bestSmall *dnsPick
	for _, zone := range zones {
		zone = strings.TrimSpace(zone)
		if zone == "" {
			continue
		}
		for _, cand := range candidates {
			if ctx.Err() != nil {
				break
			}
			pCtx, cancel := context.WithTimeout(ctx, dnsProbeTimeout)
			res := c.probeFn(pCtx, cand.Resolver, zone, key, true)
			cancel()
			if !res.OK {
				continue
			}
			pick := dnsPick{
				Resolver:  cand.Resolver,
				Kind:      cand.Kind,
				Zone:      zone,
				Large:     res.Large,
				EDNS:      res.EDNS,
				ExpiresAt: now.Add(dnsPickPositiveTTL),
			}
			if res.Large {
				c.mu.Lock()
				c.picks[cacheKey] = pick
				c.mu.Unlock()
				return pick, true
			}
			if bestSmall == nil {
				cp := pick
				bestSmall = &cp
			}
		}
		if bestSmall != nil {
			break
		}
	}

	if bestSmall != nil {
		c.mu.Lock()
		c.picks[cacheKey] = *bestSmall
		c.mu.Unlock()
		return *bestSmall, true
	}

	nonePick := dnsPick{Kind: "none", ExpiresAt: now.Add(dnsPickNegativeTTL)}
	c.mu.Lock()
	c.picks[cacheKey] = nonePick
	c.mu.Unlock()
	return nonePick, false
}

func (c *dnsResolverCache) Get(engine, nodeID string, now time.Time) (dnsPick, bool) {
	if c == nil || nodeID == "" {
		return dnsPick{}, false
	}
	netKey := c.currentNetworkKey()
	cacheKey := engine + ":" + nodeID + "@" + netKey
	c.mu.Lock()
	defer c.mu.Unlock()
	pick, ok := c.picks[cacheKey]
	if !ok || !now.Before(pick.ExpiresAt) || pick.Resolver == "" || pick.Kind == "none" {
		return dnsPick{}, false
	}
	return pick, true
}

func (c *dnsResolverCache) SummaryForNode(nodeID string, now time.Time) string {
	if c == nil || nodeID == "" {
		return ""
	}
	if pick, ok := c.Get("dnstt", nodeID, now); ok {
		if pick.Large {
			return pick.Kind
		}
		return pick.Kind + " (small EDNS)"
	}
	return ""
}

func (s *server) getDNSResolvers() *dnsResolverCache {
	s.dynMu.Lock()
	defer s.dynMu.Unlock()
	return s.dnsResolvers
}

func (s *server) ensureDNSResolvers() *dnsResolverCache {
	s.dynMu.Lock()
	defer s.dynMu.Unlock()
	if s.dnsResolvers == nil {
		s.dnsResolvers = newDNSResolverCache(func(ctx context.Context, resolver, zone string, key []byte, large bool) dnsprobe.Result {
			control, _ := s.bypass()
			dialer := &net.Dialer{Timeout: dnsProbeTimeout, Control: control}
			return dnsprobe.Probe(ctx, dialer.DialContext, resolver, zone, key, large, s.clock())
		})
	}
	return s.dnsResolvers
}

// probeNodeDNSResolversSoon triggers background dnsprobe-v1 checks for nodes that carry a DNS probe key
// and a dnstt profile, respecting per-network TTL caching (BG-07 / H6).
func (s *server) probeNodeDNSResolversSoon(u *user, nodes []nikoray.Node) {
	if u == nil || len(nodes) == 0 {
		return
	}
	cache := s.ensureDNSResolvers()
	type target struct {
		node    nikoray.Node
		primary string
		spares  []string
	}
	var targets []target
	for _, n := range nodes {
		if n.Probe == nil || n.Probe.DNSKey == "" || n.GroupID == 0 {
			continue
		}
		rows, err := u.store.Profiles(n.GroupID)
		if err != nil {
			continue
		}
		for _, row := range rows {
			full, err := u.store.Profile(row.ID)
			if err != nil {
				continue
			}
			bean, err := nikofmt.DecodeBean(full.Type, full.Bean)
			if err != nil {
				continue
			}
			base := nikofmt.Base(bean)
			if base == nil || base.DnsTunnelDomain == nil || strings.TrimSpace(*base.DnsTunnelDomain) == "" {
				continue
			}
			engine := "dnstt"
			if base.DnsTunnel != nil && strings.TrimSpace(*base.DnsTunnel) != "" {
				engine = strings.TrimSpace(*base.DnsTunnel)
			}
			if engine != "dnstt" {
				continue
			}
			var spares []string
			if base.DnsTunnelSpare != nil && strings.TrimSpace(*base.DnsTunnelSpare) != "" {
				for _, part := range strings.Split(*base.DnsTunnelSpare, ",") {
					if p := strings.TrimSpace(part); p != "" {
						spares = append(spares, p)
					}
				}
			}
			targets = append(targets, target{
				node:    n,
				primary: strings.TrimSpace(*base.DnsTunnelDomain),
				spares:  spares,
			})
			break
		}
	}
	if len(targets) == 0 {
		return
	}
	go func() {
		ctx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
		defer cancel()
		for _, t := range targets {
			if ctx.Err() != nil {
				return
			}
			cache.ProbeNodeDnstt(ctx, &t.node, t.primary, t.spares, s.clock())
		}
	}()
}

// currentDNSResolvers returns a map suitable for nikoconf.Input.DnsResolvers for all provided nodes.
func (s *server) currentDNSResolvers(nodes []nikoray.Node) map[string]string {
	cache := s.ensureDNSResolvers()
	now := s.clock()
	out := make(map[string]string, len(nodes)*3)
	sysDNS := cache.systemResolvers()
	for i := range nodes {
		id := nodes[i].ID()
		if id == "" {
			continue
		}
		if pick, ok := cache.Get("dnstt", id, now); ok {
			out["dnstt:"+id] = pick.Resolver
		}
		if m := masterdnsAutoResolvers(sysDNS, "ru", false); m != "" {
			out["masterdnsvpn:"+id] = m
		}
		if sl := masterdnsAutoResolvers(sysDNS, "ru", true); sl != "" {
			out["slipstream:"+id] = sl
		}
	}
	if len(out) == 0 {
		return nil
	}
	return out
}

// Ensure slice usage stays deterministic if callers inspect available kinds.
var validDNSResolverKinds = []string{"net", "udp", "tcp", "dot", "doh"}

func isValidDNSKind(kind string) bool {
	return slices.Contains(validDNSResolverKinds, kind)
}

var _ = nikoconf.DefaultDnsttResolver

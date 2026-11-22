package nikoconf

import (
	"bytes"
	"encoding/json"
	"errors"
	"fmt"
	"sort"
	"strconv"
	"strings"

	"libcore/nikofmt"
)

// The whole config of a profile (ConfigBuilder.kt buildConfig), with no raycontrol node imported: every ConfigRay
// lookup gives nothing (ConfigRay.None), so the branches of a node (its knock and SPA, Brutal rates, keepalive,
// prewarm, the node's DoH, its relays, LAN and public mesh peers) are dead and left out; a DNS tunnel stays as its
// profile has it. The build's notes (notes = false) change nothing in the config.

// The build's tags and fixed values.
const (
	tagProxy       = "proxy"
	tagDirect      = "direct"
	tagBypass      = "bypass"
	tagBlock       = "block"
	tagYggdrasil   = "yggdrasil"
	tagMixed       = "mixed-in"
	tagTun         = "tun-in"
	localhost      = "127.0.0.1"
	yggdrasilRange = "200::/7"
	lanZone        = "niko.internal"

	localProxyUser          = "nekobox-app"
	exportSecretPlaceholder = "<a secret of the app, not exported>"
	outsideVPNPackage       = "niko:outside-vpn"
	privateVLAN4Client      = "172.19.0.1"
	privateVLAN6Client      = "fdfe:dcba:9876::1"
)

var (
	tlsFragmentTypes = setOf("vmess", "vless", "trojan", "http", "anytls", "shadowtls")
	tlsPinTypes      = setOf("vmess", "vless", "trojan", "http", "anytls", "shadowtls", "hysteria", "hysteria2", "tuic",
		"naive")
	udpDesyncTypes = setOf("hysteria2", "tuic")
)

// Build is buildConfig(input, forTest, forExport, notes = false) of the app: the same config of the same input, its
// quirks included, and the same warnings. On an error, Result holds the warnings told before it.
func Build(in Input) (Result, error) {
	b := &builder{in: in, s: &in.Settings, dev: in.Device, forTest: in.ForTest, forExport: in.ForExport}
	if b.dev == nil {
		b.dev = NewFixedDevice(in.Apps, in.PrivateDNS)
	}
	switch in.Platform {
	case "", PlatformAndroid:
	case PlatformWindows, PlatformLinux:
		b.desktop = true
	default:
		return Result{}, fmt.Errorf("unknown platform %q", in.Platform)
	}
	config, err := b.build()
	if err != nil {
		return Result{Warnings: b.warnings}, err
	}
	b.result.Config, b.result.Warnings = config, b.warnings
	return b.result, nil
}

// option is a SingBoxOption on its way to the config: its own map (the builder's, as Outbound gives it, or the typed
// fields), _hack_config_map merged over it and _hack_custom_config merged last (mergeJSON), as the app's Gson adapter
// writes it.
type option struct {
	base   object
	hack   object
	custom string
	// wireGuard: an Endpoint_WireGuardOptions
	wireGuard bool
}

func newOption(base object) *option { return &option{base: base, hack: object{}} }

// asMap is SingBoxOption.asMap: what the config gets of it.
func (o *option) asMap() (object, error) {
	m := deepCopy(o.base).(object)
	mergeMap(m, deepCopy(o.hack).(object))
	if err := mergeJSON(m, o.custom); err != nil {
		return nil, err
	}
	if err := asMap(m); err != nil {
		return nil, err
	}
	return m, nil
}

// builder is one build: buildConfig's state.
type builder struct {
	in        Input
	s         *Settings
	dev       Device
	forTest   bool
	forExport bool
	// desktop: the config of a computer's (Windows or Linux), not the phone's (desktop.go)
	desktop bool
	// result: what the build gives beside the config (its system proxy)
	result   Result
	warnings []string

	all      []*Profile
	byID     map[int64]*Profile
	groups   map[int64]*Group
	proxy    *Profile
	group    *Group
	isVPN    bool
	ipv6Mode int

	buildAuto, buildSelector bool
	useFakeDNS, needSniff    bool

	tagMap          javaLongMap
	globalOutbounds map[int64]string
	selectorNames   []string
	// domainListDNSDirectForce in its order: a HashSet of it goes into the DNS rule
	directForce []string
	// bypassDNSBeans: the entry hops' beans, a HashSet (addBypassDNSBean)
	bypassDNSBeans []nikofmt.Bean
	userDNSRules   []object

	inbounds   []object
	outbounds  []*option
	endpoints  []*option
	routeRules []*option
	ruleSets   []object

	// tun is VPN mode's inbound; tunnelResolvers are masterdnsvpn's resolvers, which a computer's TUN leaves out
	tun             object
	tunnelResolvers []string

	yggdrasil         bool
	yggdrasilEndpoint *option
	// the proxy reaches its server through the mesh: then the mesh cannot reach its peers through the proxy
	proxyThroughYggdrasil bool
	tunnelledHop          bool
	buildingProxy         bool
	// the mesh nodes of Yggdrasil profiles (niko-ygg), one per device key: key → tag and peer, in their order
	yggProfileKeys []string
	yggProfileTags map[string][2]string
}

func (b *builder) warn(format string, rule *Rule) {
	b.warnings = append(b.warnings, fmt.Sprintf(format, rule.displayName()))
}

// The store (ConfigStore.Of): the profiles, groups and rules in the database's orders.

func (b *builder) profile(id int64) *Profile { return b.byID[id] }

// profilesByIDs is ConfigStore.profiles: those of the ids, by id.
func (b *builder) profilesByIDs(ids map[int64]bool) []*Profile {
	sorted := append([]*Profile(nil), b.all...)
	sort.SliceStable(sorted, func(i, j int) bool { return sorted[i].ID < sorted[j].ID })
	var out []*Profile
	for _, p := range sorted {
		if ids[p.ID] {
			out = append(out, p)
		}
	}
	return out
}

// profilesOf is a group's profiles in their order.
func (b *builder) profilesOf(groupID int64) []*Profile {
	var out []*Profile
	for _, p := range b.all {
		if p.Group == groupID {
			out = append(out, p)
		}
	}
	sort.SliceStable(out, func(i, j int) bool { return out[i].Order < out[j].Order })
	return out
}

// enabledRules are the enabled rules in their order.
func (b *builder) enabledRules() []*Rule {
	var out []*Rule
	for i := range b.in.Rules {
		if b.in.Rules[i].Enabled {
			out = append(out, &b.in.Rules[i])
		}
	}
	sort.SliceStable(out, func(i, j int) bool { return out[i].UserOrder < out[j].UserOrder })
	return out
}

func (b *builder) build() ([]byte, error) {
	in := b.in
	s := b.s
	b.byID = map[int64]*Profile{}
	for i := range in.Profiles {
		p := &in.Profiles[i]
		if p.Bean == nil {
			return nil, fmt.Errorf("profile %d has no bean", p.ID)
		}
		b.all = append(b.all, p)
		b.byID[p.ID] = p
	}
	b.groups = map[int64]*Group{}
	for i := range in.Groups {
		b.groups[in.Groups[i].ID] = &in.Groups[i]
	}
	for _, p := range b.all {
		if p.ID == in.Profile {
			b.proxy = p
			break
		}
	}
	if b.proxy == nil {
		return nil, fmt.Errorf("%w: %d", errNoProfile, in.Profile)
	}

	// a custom config (ConfigBean of type 0) is the config, as it is
	if c, ok := b.proxy.Bean.(*nikofmt.ConfigBean); ok && c.Type != nil && *c.Type == 0 {
		return []byte(str(c.Config)), nil
	}

	b.globalOutbounds = map[int64]string{}
	b.group = b.groups[b.proxy.Group]
	var extraRules []*Rule
	extraProxies := []*Profile{}
	if !b.forTest {
		extraRules = b.enabledRules()
		// a computer's programs through Niko or not ahead of the rules (D-RULES-06)
		if rule := b.programsRule(); rule != nil {
			extraRules = append([]*Rule{rule}, extraRules...)
		}
		ids := map[int64]bool{}
		for _, r := range extraRules {
			if r.Outbound > 0 && r.Outbound != b.proxy.ID {
				ids[r.Outbound] = true
			}
		}
		extraProxies = b.profilesByIDs(ids)
	}
	// auto select (urltest) takes precedence over the manual selector
	b.buildAuto = !b.forTest && b.group != nil && b.group.AutoSelect && !b.forExport
	b.buildSelector = !b.forTest && b.group != nil && b.group.IsSelector && !b.forExport && !b.buildAuto
	b.isVPN = s.isVPN()
	bind := localhost
	if !b.forTest && s.AllowAccess {
		bind = "0.0.0.0"
	}
	remoteDNS := dnsLines(s.RemoteDNS)
	directDNS := dnsLines(s.DirectDNS)
	b.useFakeDNS = s.EnableFakeDNS && !b.forTest
	b.needSniff = s.TrafficSniffing > 0
	b.ipv6Mode = s.IPv6Mode
	if b.forTest {
		b.ipv6Mode = ipv6Enable
	}

	// an exported config is shared: it carries no secret of this install
	var experimental object
	if !b.forTest && !b.forExport && s.EnableClashAPI {
		experimental = object{"clash_api": object{"external_controller": "127.0.0.1:9090",
			"external_ui": "../files/yacd", "secret": s.Secrets.ClashAPI}}
	}
	// a test core's log level, set while a test run goes, is never set here (testLogLevel null)
	logLevel := map[int]string{0: "panic", 1: "warn", 2: "info", 3: "debug", 4: "trace"}[s.LogLevel]
	if logLevel == "" {
		logLevel = "info"
	}
	dns := object{}
	// answer from an expired cache entry at once and refresh it in the background
	if !b.forTest && s.DNSOptimisticCache {
		dns["optimistic"] = true
	}

	if !b.forTest {
		b.addInbounds(bind)
	}

	route := object{"auto_detect_interface": true, "default_domain_resolver": "dns-direct"}

	if !b.forTest {
		// quirk kept: the local proxy's tag is there also where its inbound is not (VPN mode without it)
		inboundTags := []any{tagMixed}
		if b.isVPN {
			inboundTags = []any{tagTun, tagMixed}
		}
		if s.ResolveDestination {
			b.addRule(object{"inbound": inboundTags, "action": "resolve", "strategy": b.genDomainStrategy()})
		}
	}

	// Yggdrasil (experimental): the mesh endpoint, 200::/7 through it ahead of user rules
	b.yggdrasil = s.YggdrasilEnabled
	var yggdrasilInboundRule object
	if b.yggdrasil && !b.forTest {
		key := s.Secrets.YggdrasilKey
		if b.forExport {
			key = exportSecretPlaceholder
		}
		b.addYggdrasilEndpoint(key)
		yggdrasilInboundRule = object{"inbound": []any{tagYggdrasil}}
		if s.YggdrasilAllowInbound {
			yggdrasilInboundRule["outbound"] = tagDirect
		} else {
			yggdrasilInboundRule["action"] = "reject"
		}
		b.addRule(object{"ip_cidr": []any{yggdrasilRange}, "outbound": tagYggdrasil})
	}

	// build outbounds
	b.buildingProxy = true
	var final string
	switch {
	case b.buildSelector:
		for _, p := range b.profilesOf(b.group.ID) {
			tag, err := b.buildChain(p.ID, p)
			if err != nil {
				return nil, err
			}
			b.tagMap.put(p.ID, tag)
		}
		selector := object{"type": "selector", "tag": tagProxy, "outbounds": strings2any(b.tagMap.values())}
		if tag, ok := b.tagMap.get(b.proxy.ID); ok {
			selector["default"] = tag
		}
		b.outbounds = append([]*option{newOption(selector)}, b.outbounds...)
		final = tagProxy
	case b.buildAuto:
		for _, p := range b.profilesOf(b.group.ID) {
			tag, err := b.buildChain(p.ID, p)
			if err != nil {
				return nil, err
			}
			b.tagMap.put(p.ID, tag)
		}
		urltest := object{"type": "urltest", "tag": tagProxy, "outbounds": strings2any(b.tagMap.values()),
			"url": s.ConnectionTestURL, "interval": "3m", "tolerance": int64(50)}
		b.outbounds = append([]*option{newOption(urltest)}, b.outbounds...)
		final = tagProxy
	default:
		tag, err := b.buildChain(0, b.proxy)
		if err != nil {
			return nil, err
		}
		final = tag
	}
	// only the programs chosen go through Niko: the rest direct
	if b.programsOnly() {
		final = tagBypass
	}
	route["final"] = final
	// a single profile that carries UDP inside a stream: QUIC refused, so browsers go to TCP at once
	refuseQuic := !b.forTest && !b.buildSelector && !b.buildAuto && s.RefuseQuicOverStreams &&
		udpRidesAStream(b.resolveChain(b.proxy))
	b.buildingProxy = false
	// Yggdrasil peers through the proxy, unless the proxy itself needs the mesh
	if b.yggdrasil && !b.forTest && s.YggdrasilPeersViaProxy && !b.proxyThroughYggdrasil && b.yggdrasilEndpoint != nil {
		b.yggdrasilEndpoint.hack["detour"] = tagProxy
	}
	// the rules' profiles
	for _, p := range extraProxies {
		tag, err := b.buildChain(p.ID, p)
		if err != nil {
			return nil, err
		}
		b.tagMap.put(p.ID, tag)
	}
	if b.desktop {
		b.excludeTunnelResolvers()
	}

	// lan-v1 reads the clock for the LAN of raycontrol nodes: none here
	_ = b.dev.Now()
	// the mesh nodes of Yggdrasil profiles: no answers about their peers, no flow in (no LAN without a node)
	for _, e := range b.endpoints {
		if b.isYggProfileTag(e.base["tag"]) {
			e.hack["debug_privacy"] = true
			e.hack["inbound_listed"] = true
		}
	}

	// WireGuard requires a real destination for UDP, FakeDNS addresses included: resolved before routing
	hasWireGuard := false
	for _, e := range b.endpoints {
		hasWireGuard = hasWireGuard || e.wireGuard
	}
	if !b.forTest && !s.ResolveDestination && hasWireGuard {
		inbound := []any{tagMixed}
		if b.isVPN {
			inbound = []any{tagTun, tagMixed}
		}
		b.addRule(object{"inbound": inbound, "network": []any{"udp"}, "action": "resolve",
			"strategy": b.genDomainStrategy()})
	}

	if err := b.userRules(extraRules, refuseQuic); err != nil {
		return nil, err
	}
	b.ruleSets = distinctByTag(b.ruleSets)

	// remote rule-sets and FakeDNS's addresses live in the cache file across restarts
	hasRemote := false
	for _, r := range b.ruleSets {
		hasRemote = hasRemote || r["type"] == "remote"
	}
	if !b.forTest && (b.useFakeDNS || hasRemote) {
		if experimental == nil {
			experimental = object{}
		}
		cache := object{"enabled": true, "path": "cache.db"}
		if b.useFakeDNS {
			cache["store_fakeip"] = true
		}
		experimental["cache_file"] = cache
	}

	for _, freedom := range []string{tagDirect, tagBypass} {
		b.outbounds = append(b.outbounds, newOption(object{"tag": freedom, "type": "direct"}))
	}

	// Bypass Lookup for the first profile
	for _, bean := range b.bypassDNSBeans {
		serverAddr := str(nikofmt.Base(bean).ServerAddress)
		if c, ok := bean.(*nikofmt.ConfigBean); ok {
			config, err := nikofmt.GsonMap(str(c.Config))
			if err != nil {
				return nil, fmt.Errorf("%s: custom config: %w", nikofmt.DisplayName(bean), err)
			}
			if config == nil {
				// a null read into a map Kotlin holds as not null
				return nil, errors.New("fromJson(...) must not be null")
			}
			if server, ok := config["server"]; ok && server != nil {
				serverAddr = javaToString(server)
			}
		}
		if !nikofmt.IsIPAddress(serverAddr) {
			b.directForce = append(b.directForce, "full:"+serverAddr)
		}
	}
	for _, address := range remoteDNS {
		if strings.Contains(address, "://") {
			address = substringAfter(address, "://")
		}
		if host, ok := nikofmt.OkHTTPHost("https://" + address); ok && !nikofmt.IsIPAddress(host) {
			b.directForce = append(b.directForce, "full:"+host)
		}
	}

	dnsServers, err := b.dnsServers(remoteDNS, directDNS)
	if err != nil {
		return nil, err
	}

	// server address lookups go straight to a transport, so they need the direct strategy
	if strategy, ok := b.autoDNSDomainStrategy(s.domainStrategy("dns-direct")); ok {
		for _, server := range dnsServers {
			if resolver, ok := server["domain_resolver"]; ok {
				server["domain_resolver"] = object{"server": resolver, "strategy": strategy}
			}
		}
		route["default_domain_resolver"] = object{"server": "dns-direct", "strategy": strategy}
	}

	// an echo no outbound carries is refused instead of answered by the TUN (a field of the core fork's)
	if !b.forTest && !b.forExport && s.IcmpNoLocalReply {
		route["icmp_no_local_reply"] = true
	}

	dnsFinal := "dns-remote"
	if b.forTest || b.programsOnly() {
		dnsFinal = "dns-direct"
	}
	dns["final"] = dnsFinal

	var dnsRules []object
	if s.EnableDNSRouting {
		for _, r := range b.userDNSRules {
			if !dnsRuleEmpty(r) {
				dnsRules = append(dnsRules, r)
			}
		}
	}

	// nothing under niko.internal ever goes out (no LAN without a node)
	if !b.forTest {
		dnsRules = append([]object{{"domain_suffix": []any{lanZone}, "action": "predefined",
			"rcode": "NXDOMAIN"}}, dnsRules...)
	}

	if b.forTest {
		dnsRules = nil
	} else {
		// per-app proxy: an app left out of the VPN, bound to tun0, refused (below the DNS hijack); the phone's
		// VpnService alone leaves apps out, a computer's programs go by their rules
		if b.isVPN && s.ProxyApps && !b.desktop {
			b.prependRules(outsideVPNGuard())
		}
		// built-in DNS rules
		b.prependRules(object{"protocol": []any{"dns"}, "action": "hijack-dns"})
		b.prependRules(object{"port": []any{int64(53)}, "action": "hijack-dns"})
		// sniff must run before protocol matching
		if b.needSniff {
			inbound := []any{tagMixed}
			if b.isVPN {
				inbound = []any{tagTun, tagMixed}
			}
			b.prependRules(object{"inbound": inbound, "action": "sniff"})
		}
		if yggdrasilInboundRule != nil {
			var meshRules []object
			if yggdrasilInboundRule["outbound"] == tagDirect {
				if own := b.ownListenPorts(experimental); len(own) > 0 {
					meshRules = append(meshRules, object{"inbound": []any{tagYggdrasil}, "port": own,
						"action": "reject"})
				}
				// the ports the user opened, or every other one
				if open := parseMeshPorts(s.YggdrasilInboundPorts); open != nil {
					rule := object{"inbound": []any{tagYggdrasil}, "outbound": tagDirect}
					open.put(rule)
					meshRules = append(meshRules, rule, object{"inbound": []any{tagYggdrasil}, "action": "reject"})
				} else {
					meshRules = append(meshRules, yggdrasilInboundRule)
				}
			} else {
				meshRules = append(meshRules, yggdrasilInboundRule)
			}
			b.prependRules(meshRules...)
		}
		// LAN direct (D-SET-16) on a computer is this rule: auto_route leaves the networks the computer is on to
		// their own routes (more specific than the TUN's on Windows, the main table's first on Linux), and what of a
		// private network reaches the core (one behind a router, a request to the local proxy) goes direct by it.
		// The phone's other way, the VPN's routes without the LAN (bypassLan), is VpnService's
		if s.BypassLanInCore {
			b.addRule(object{"outbound": tagBypass, "ip_is_private": true})
		}
		// block mcast
		b.addRule(object{"ip_cidr": []any{"224.0.0.0/3", "ff00::/8"}, "source_ip_cidr": []any{"224.0.0.0/3",
			"ff00::/8"}, "action": "reject"})
		// QUIC that would reach the final outbound, the proxy, refused
		if refuseQuic {
			b.addRule(quicRefusal(b.needSniff))
		}
		if b.useFakeDNS {
			dnsServers = append(dnsServers, object{"type": "fakeip", "tag": "dns-fake",
				"inet4_range": "198.18.0.0/15", "inet6_range": "fc00::/18"})
			dnsRules = append(dnsRules, object{"inbound": []any{tagTun}, "server": "dns-fake",
				"disable_cache": true})
			// the host of strict Private DNS resolved for real, above every rule that would give it a fake address;
			// not in an exported config
			if !b.forExport {
				if host, ok := b.dev.StrictPrivateDNSHost(); ok {
					if host = strings.TrimRight(nikofmt.KtLowercase(host), "."); host != "" {
						dnsRules = append([]object{{"domain": []any{host}, "server": "dns-remote"}}, dnsRules...)
					}
				}
			}
		}
		// force bypass (always top DNS rule)
		if len(b.directForce) > 0 {
			rule := object{"server": "dns-direct"}
			makeDNSRule(rule, javaHashSetOrder(b.directForce, javaStringHash), false)
			dnsRules = append([]object{rule}, dnsRules...)
		}
	}
	// niko.internal goes nowhere, never to the proxy (no LAN without a node)
	if !b.forTest {
		b.prependRules(object{"domain_suffix": []any{lanZone}, "action": "reject"})
	}
	// nothing reaches the device through the mesh node of a Yggdrasil profile (no LAN without a node)
	if len(b.yggProfileKeys) > 0 {
		tags := []any{}
		for _, key := range b.yggProfileKeys {
			tags = append(tags, b.yggProfileTags[key][0])
		}
		b.prependRules(object{"inbound": tags, "action": "reject"})
	}

	if strategy, ok := b.autoDNSDomainStrategy(s.domainStrategy(dnsFinal)); ok {
		dns["strategy"] = strategy
	}
	for _, rule := range dnsRules {
		delete(rule, "strategy")
		switch server := rule["server"]; server {
		case "dns-direct", "dns-remote":
			if strategy, ok := b.autoDNSDomainStrategy(s.domainStrategy(server.(string))); ok {
				rule["strategy"] = strategy
			}
		case "dns-fake":
			rule["strategy"] = "ipv4_only"
		}
	}

	// the config: each option as it writes itself, then the settings' custom config, then the profile's
	dns["servers"] = objects2any(dnsServers)
	dns["rules"] = objects2any(dnsRules)
	routeRules, err := optionMaps(b.routeRules)
	if err != nil {
		return nil, err
	}
	route["rules"] = routeRules
	route["rule_set"] = objects2any(b.ruleSets)
	outbounds, err := optionMaps(b.outbounds)
	if err != nil {
		return nil, err
	}
	endpoints, err := optionMaps(b.endpoints)
	if err != nil {
		return nil, err
	}
	config := object{"log": object{"level": logLevel}, "dns": dns, "inbounds": objects2any(b.inbounds),
		"outbounds": outbounds, "endpoints": endpoints, "route": route}
	if experimental != nil {
		config["experimental"] = experimental
	}
	if !b.forTest {
		if err := mergeJSON(config, s.GlobalCustomConfig); err != nil {
			return nil, err
		}
	}
	if err := asMap(config); err != nil {
		return nil, err
	}
	if err := mergeJSON(config, str(nikofmt.Base(b.proxy.Bean).CustomConfigJson)); err != nil {
		return nil, err
	}
	if err := asMap(config); err != nil {
		return nil, err
	}
	var out bytes.Buffer
	enc := json.NewEncoder(&out)
	enc.SetEscapeHTML(false)
	enc.SetIndent("", "  ")
	if err := enc.Encode(config); err != nil {
		return nil, err
	}
	return bytes.TrimSuffix(out.Bytes(), []byte("\n")), nil
}

// addInbounds are the TUN (VPN mode) and the local proxy, where it opens.
func (b *builder) addInbounds(bind string) {
	s := b.s
	if b.isVPN {
		stack := "mixed"
		switch s.TunImplementation {
		case tunGVisor:
			stack = "gvisor"
		case tunSystem:
			stack = "system"
		}
		address := []any{privateVLAN4Client + "/28", privateVLAN6Client + "/126"}
		switch b.ipv6Mode {
		case ipv6Disable:
			address = []any{privateVLAN4Client + "/28"}
		case ipv6Only:
			address = []any{privateVLAN6Client + "/126"}
		}
		b.tun = object{"type": "tun", "tag": tagTun, "stack": stack, "mtu": int64(s.MTU), "address": address}
		if b.desktop {
			b.desktopTun(b.tun)
		}
		b.inbounds = append(b.inbounds, b.tun)
	}
	// in VPN mode the port only serves what the user set it up for
	if localProxyWanted(b.isVPN, s) {
		mixed := object{"type": "mixed", "tag": tagMixed, "listen": bind, "listen_port": int64(s.MixedPort)}
		if b.desktop && !b.isVPN && !b.forTest && !b.forExport && b.systemProxy() {
			b.result.SystemProxy = &SystemProxy{Server: localhost, Port: s.MixedPort}
		}
		if users := localProxyUsers(b.isVPN, s); users != nil {
			list := []any{}
			for _, u := range users {
				if b.forExport {
					u["password"] = exportSecretPlaceholder
				}
				list = append(list, u)
			}
			mixed["users"] = list
		}
		b.inbounds = append(b.inbounds, mixed)
	}
}

// dnsServers are the local, direct and (but in a test) remote DNS servers of the settings.
func (b *builder) dnsServers(remoteDNS, directDNS []string) ([]object, error) {
	var dnsServers []object
	local, err := dnsServerFromAddress("local", "dns-local", nil, nil)
	if err != nil {
		return nil, err
	}
	dnsServers = append(dnsServers, local)
	if len(directDNS) == 0 {
		return nil, errors.New("No direct DNS, check your settings!")
	}
	direct, err := dnsServerFromAddress(directDNS[0], "dns-direct", nil, ptrTo("dns-local"))
	if err != nil {
		return nil, err
	}
	dnsServers = append(dnsServers, direct)
	if !b.forTest {
		if len(remoteDNS) == 0 {
			return nil, errors.New("No remote DNS, check your settings!")
		}
		// a line each: one is dns-remote itself; more are dns-remote-1..N, asked in order by dns-remote, a fallback
		// server of the core fork's that moves on when one fails or keeps silent. Not in an export: a core of
		// sing-box's own has no fallback, an export keeps the first line
		lines := remoteDNS
		if b.forExport {
			lines = lines[:1]
		}
		if len(lines) == 1 {
			remote, err := b.remoteDNSServer(lines[0], "dns-remote")
			if err != nil {
				return nil, err
			}
			dnsServers = append(dnsServers, remote)
		} else {
			members := make([]object, 0, len(lines))
			tags := make([]any, 0, len(lines))
			for i, line := range lines {
				tag := "dns-remote-" + strconv.Itoa(i+1)
				member, err := b.remoteDNSServer(line, tag)
				if err != nil {
					return nil, err
				}
				members = append(members, member)
				tags = append(tags, tag)
			}
			dnsServers = append(dnsServers, object{"type": "fallback", "tag": "dns-remote", "servers": tags})
			dnsServers = append(dnsServers, members...)
		}
	}
	return dnsServers, nil
}

// remoteDNSServer is a line of the remote DNS as a server of its own: through the proxy, its name by dns-direct.
func (b *builder) remoteDNSServer(address, tag string) (object, error) {
	remote, err := dnsServerFromAddress(address, tag, ptrTo(tagProxy), ptrTo("dns-direct"))
	if err != nil {
		return nil, err
	}
	// the same resolver over a TCP transport: UDP, DoQ and DoH3 cannot pass a DNS tunnel
	if b.tunnelledHop {
		switch remote["type"] {
		case "udp":
			remote["type"] = "tcp"
		case "quic":
			remote["type"] = "tls"
			delete(remote, "server_port")
		case "h3":
			remote["type"] = "https"
			delete(remote, "server_port")
		}
	}
	return remote, nil
}

// addRule and prependRules add route rules at the end, and at the start in their order.
func (b *builder) addRule(rule object) { b.routeRules = append(b.routeRules, newOption(rule)) }

func (b *builder) prependRules(rules ...object) {
	head := make([]*option, len(rules))
	for i, r := range rules {
		head[i] = newOption(r)
	}
	b.routeRules = append(head, b.routeRules...)
}

// genDomainStrategy is genDomainStrategy(true): the strategy of the resolve actions.
func (b *builder) genDomainStrategy() string {
	switch b.ipv6Mode {
	case ipv6Disable:
		return "ipv4_only"
	case ipv6Prefer:
		return "prefer_ipv6"
	case ipv6Only:
		return "ipv6_only"
	}
	return "prefer_ipv4"
}

// autoDNSDomainStrategy is autoDnsDomainStrategy: the user's strategy, else the IPv6 mode's; none for a mode the app
// does not know.
func (b *builder) autoDNSDomainStrategy(s string) (string, bool) {
	if s != "" {
		return s, true
	}
	switch b.ipv6Mode {
	case ipv6Disable:
		return "ipv4_only", true
	case ipv6Enable:
		return "prefer_ipv4", true
	case ipv6Prefer:
		return "prefer_ipv6", true
	case ipv6Only:
		return "ipv6_only", true
	}
	return "", false
}

// addYggdrasilEndpoint is the experimental mesh endpoint, once.
func (b *builder) addYggdrasilEndpoint(privateKey string) {
	if b.yggdrasilEndpoint != nil {
		return
	}
	peers := []any{}
	for _, line := range nikofmt.KtLines(b.s.YggdrasilPeers) {
		peer := nikofmt.KtTrim(line)
		if peer == "" || strings.HasPrefix(peer, "#") {
			continue
		}
		// the core resolves a quic:// name with the system resolver, and outside a VPN that is the network's DNS:
		// in proxy mode such a peer goes, an address stays
		name := substringAfter(peer, "://")
		name = substringBefore(substringBefore(name, "/"), "?")
		if i := strings.LastIndex(name, ":"); i >= 0 {
			name = name[:i]
		}
		name = strings.Trim(name, "[]")
		if !b.isVPN && startsWithIgnoreCase(peer, "quic://") && !nikofmt.IsIPAddress(name) {
			continue
		}
		peers = append(peers, peer)
	}
	e := newOption(object{"type": "yggdrasil", "tag": tagYggdrasil})
	e.hack["private_key"] = privateKey
	e.hack["peers"] = peers
	e.hack["node_info_privacy"] = true
	// a Chrome ClientHello on tls:// links unless turned off (core default: chrome)
	if !b.s.YggdrasilTLSChrome {
		e.hack["tls_fingerprint"] = "go"
	}
	b.yggdrasilEndpoint = e
	b.endpoints = append(b.endpoints, e)
}

// ownListenPorts are this app's own listeners: the local proxy, the Clash API (no plugin or slipstream here).
func (b *builder) ownListenPorts(experimental object) []any {
	var ports []any
	seen := map[int64]bool{}
	add := func(p int64) {
		if !seen[p] {
			seen[p] = true
			ports = append(ports, p)
		}
	}
	for _, in := range b.inbounds {
		if in["type"] == "mixed" || in["type"] == "direct" {
			if p, ok := in["listen_port"].(int64); ok {
				add(p)
			}
		}
	}
	if experimental != nil && experimental["clash_api"] != nil {
		add(9090)
	}
	return ports
}

func (b *builder) isYggProfileTag(tag any) bool {
	for _, key := range b.yggProfileKeys {
		if b.yggProfileTags[key][0] == tag {
			return true
		}
	}
	return false
}

// selectorName is a selector member's tag: the profile's name, numbered where taken.
func (b *builder) selectorName(name string) string {
	taken := func(n string) bool {
		for _, s := range b.selectorNames {
			if s == n {
				return true
			}
		}
		return false
	}
	result := name
	for count := 1; taken(result); count++ {
		result = name + "-" + strconv.Itoa(count)
	}
	b.selectorNames = append(b.selectorNames, result)
	return result
}

// resolveChain is ProxyEntity.resolveChain: the profile, its group's front proxy after it and landing proxy before
// it. A chain profile (ChainBean) has no bean here.
func (b *builder) resolveChain(p *Profile) []*Profile {
	list := []*Profile{p}
	if g := b.groups[p.Group]; g != nil {
		if front := b.profile(g.FrontProxy); front != nil {
			list = append(list, front)
		}
		if landing := b.profile(g.LandingProxy); landing != nil {
			list = append([]*Profile{landing}, list...)
		}
	}
	return list
}

// localProxyUsers is the local mixed inbound's users: the app's own, and the user's; none where it needs no login.
func localProxyUsers(isVPN bool, s *Settings) []object {
	var custom object
	if !nikofmt.KtIsBlank(s.Secrets.MixedPassword) {
		username := s.MixedUsername
		if nikofmt.KtIsBlank(username) {
			username = "user"
		}
		custom = object{"username": username, "password": s.Secrets.MixedPassword}
	}
	if custom == nil && (!isVPN || s.LocalProxyNoAuthInVpn) {
		return nil
	}
	users := []object{{"username": localProxyUser, "password": s.Secrets.LocalProxyPassword}}
	if custom != nil {
		users = append(users, custom)
	}
	return users
}

// localProxyWanted: the local proxy port opens always in proxy mode; in VPN mode when the user set it up.
func localProxyWanted(isVPN bool, s *Settings) bool {
	return !isVPN || !nikofmt.KtIsBlank(s.Secrets.MixedPassword) || s.LocalProxyNoAuthInVpn
}

// outsideVPNGuard refuses an app left out of the VPN that binds to tun0 (TCP alone, where the owner is known).
func outsideVPNGuard() object {
	return object{"inbound": []any{tagTun}, "network": []any{"tcp"}, "package_name": []any{outsideVPNPackage},
		"action": "reject"}
}

// dnsLines are the DNS servers of a setting: a line each, "#" a comment.
func dnsLines(text string) []string {
	var out []string
	for _, line := range strings.Split(text, "\n") {
		if line = nikofmt.KtTrim(line); !nikofmt.KtIsBlank(line) && !strings.HasPrefix(line, "#") {
			out = append(out, line)
		}
	}
	return out
}

func optionMaps(options []*option) ([]any, error) {
	out := []any{}
	for _, o := range options {
		m, err := o.asMap()
		if err != nil {
			return nil, err
		}
		out = append(out, m)
	}
	return out, nil
}

func objects2any(list []object) []any {
	out := []any{}
	for _, o := range list {
		out = append(out, o)
	}
	return out
}

func ptrTo[T any](v T) *T { return &v }

func itoa64(n int64) string { return strconv.FormatInt(n, 10) }

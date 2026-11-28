package nikoconf

import (
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"strconv"
	"strings"

	"libcore/nikofmt"
	"libcore/nikoray"

	dnsttTransport "github.com/sagernet/sing-box/transport/dnstt"
)

// buildChain is buildConfig's buildChain: the outbounds of a profile's chain, the exit first; its tag. A hop to a
// raycontrol node has the node's options (ray.go); a relay in front of a node is not ported. A plugin profile
// (ProxyEntity.needExternal) is not ported: the desktop has no plugins, so its build fails.
func (b *builder) buildChain(chainID int64, entity *Profile) (string, error) {
	s := b.s
	profileList := b.resolveChain(entity)
	if len(profileList) == 0 {
		return "", fmt.Errorf("chain profile %q has no valid hops", nikofmt.DisplayName(entity.Bean))
	}
	last := len(profileList) - 1
	var pastOutbound, pastDetourHolder *option
	var pastEntity *Profile
	chainTagOut := ""
	chainTag := fmt.Sprintf("c-%d", chainID)
	muxApplied := false
	defaultServerDomainStrategy := s.domainStrategy("server")

	for index, p := range profileList {
		bean := p.Bean
		base := nikofmt.Base(bean)
		name := nikofmt.DisplayName(bean)

		// profile2 (in) (global): g-(id); profile1: (chainTag)-(id); profile0 (out): (chainTag)-(id), single "proxy"
		tagOut := fmt.Sprintf("%s-%d", chainTag, p.ID)
		needGlobal := false
		if index == last {
			needGlobal = true
			tagOut = fmt.Sprintf("g-%d", p.ID)
			b.addBypassDNSBean(bean)
		}
		if chainID == 0 && index == 0 {
			tagOut = tagProxy
		}
		// selector human readable name; quirk kept: also for a rule's profile outside the group
		if b.buildSelector && index == 0 {
			tagOut = b.selectorName(name)
		}
		if index > 0 {
			holder := pastDetourHolder
			if holder == nil {
				holder = pastOutbound
			}
			holder.hack["detour"] = tagOut
		} else {
			chainTagOut = tagOut
		}
		if needGlobal {
			if tag, ok := b.globalOutbounds[p.ID]; ok {
				if index == 0 {
					chainTagOut = tag // single, duplicate chain
				}
				continue
			}
			b.globalOutbounds[p.ID] = tagOut
		}

		if needsPlugin(bean) {
			return "", fmt.Errorf("%s: %w", name, ErrNeedsPlugin)
		}
		out, err := Outbound(bean, s.GlobalAllowInsecure)
		if err != nil {
			return "", err
		}
		current := newOption(out)
		_, isConfig := bean.(*nikofmt.ConfigBean)
		_, current.wireGuard = bean.(*nikofmt.WireGuardBean)
		// the type of a typed outbound; a custom one (CustomSingBoxOption) has none
		outboundType := ""
		if !isConfig {
			outboundType, _ = out["type"].(string)
		}

		// internal mux
		if !muxApplied {
			if mux := singMux(bean); mux != nil && mux["enabled"] == true {
				muxApplied = true
				current.hack["multiplex"] = mux
				// keep the old Neko core behaviour: mux replaces VLESS flow
				if v, ok := bean.(*nikofmt.VMessBean); ok && v.IsVLESS() {
					delete(current.base, "flow")
				}
			}
		}
		// udp over tcp, of the beans with sUoT
		if flag(sUoT(bean)) {
			current.hack["udp_over_tcp"] = true
		}
		// domain_strategy: don't loopback
		if pastEntity != nil {
			if address := str(nikofmt.Base(pastEntity.Bean).ServerAddress); defaultServerDomainStrategy != "" &&
				!nikofmt.IsIPAddress(address) {
				b.directForce = append(b.directForce, "full:"+address)
			}
		}
		// only the entry hop dials the server itself, inner hops resolve on their detour
		if !b.forTest && defaultServerDomainStrategy != "" && index == last {
			current.hack["domain_resolver"] = object{"server": "dns-direct", "strategy": defaultServerDomainStrategy}
		}
		current.hack["tag"] = tagOut
		current.custom = str(base.CustomOutboundJson)

		// a raycontrol Yggdrasil profile (niko-ygg) as the entry hop: the VLESS server is the node's mesh address, the
		// link to the mesh goes to the node itself
		yggPeer := ""
		if !blank(base.YggPeer) && index == last {
			yggPeer = *base.YggPeer
		}
		tunnel := dnsTunnelOf(base)
		// the row's node: its peer names the service, not the node, so the row's owner when the name is not a node's
		// host; the peer is dialled at the node's address with its name as the TLS name, unless the node has a name
		// (names-v1), which the knock resolves to one of its addresses
		var yggNode *nikoray.Node
		if yggPeer != "" {
			if host, ok := yggPeerHost(yggPeer); ok {
				yggNode = b.forHost(host)
			}
			if yggNode == nil {
				yggNode, _ = b.ownerOf(p.ID)
			}
			if yggNode != nil && yggNode.ReachName() == "" {
				yggPeer = yggPeerAtNode(yggPeer, yggNode.Host)
			}
		}
		// a raycontrol node this hop dials (a row of its subscription whatever its host: naive dials a name of its
		// own); a DNS tunnel never reaches its address
		var rayNode *nikoray.Node
		switch {
		case tunnel.enabled():
		case yggPeer != "":
			rayNode = yggNode
		default:
			if rayNode = b.forHost(str(base.ServerAddress)); rayNode == nil {
				rayNode, _ = b.ownerOf(p.ID)
			}
		}

		// per-profile TLS tweaks, merged into the outbound's tls object
		tlsTweaks := object{}
		if rayNode != nil {
			view, err := current.asMap()
			if err != nil {
				return "", err
			}
			tls, _ := view["tls"].(object)
			for k, v := range nodeTLSTweaks(outboundType, tls) {
				tlsTweaks[k] = v
			}
		}
		// fragmentation is only meaningful for TCP-based TLS outbounds
		if num(base.TlsFragment) > 0 && tlsFragmentTypes[outboundType] {
			if num(base.TlsFragment) == 2 {
				tlsTweaks["fragment"] = true
			} else {
				tlsTweaks["record_fragment"] = true
			}
		}
		if !blank(base.CertPins) {
			// pins that would be silently ignored fail the start instead
			view, err := current.asMap()
			if err != nil {
				return "", err
			}
			tls, _ := view["tls"].(object)
			if reason := pinRefusal(outboundType, false, tls); reason != "" {
				return "", fmt.Errorf("Certificate pinning (%s): %s", name, reason)
			}
			pins, err := pinTLSTweaks(outboundType, *base.CertPins)
			if err != nil {
				return "", err
			}
			for k, v := range pins {
				tlsTweaks[k] = v
			}
		}
		if len(tlsTweaks) > 0 {
			current.hack["tls"] = mergeTLS(asObject(current.hack["tls"]), tlsTweaks)
		}
		// REALITY straight to the node: one connection opened ahead for the next request, closed after a short idle.
		// A test measures a profile's own first connection, so it runs without
		if !b.forTest && rayNode != nil {
			view, err := current.asMap()
			if err != nil {
				return "", err
			}
			if idle := nodePrewarm(outboundType, view); idle != "" {
				current.hack["prewarm"] = idle
			}
		}
		// an idle QUIC connection to the node pings every 25 s, not 10 (P1); under salamander the Chrome parrot hides
		// nothing and pins Chrome's idle timeout, so it goes there (docs/research/keepalive.md)
		if h, ok := bean.(*nikofmt.HysteriaBean); ok && rayNode != nil && num(h.ProtocolVersion) == 2 {
			current.hack["keep_alive_period"] = "25s"
			current.hack["idle_timeout"] = "60s"
			if !nikofmt.KtIsBlank(str(h.Obfuscation)) {
				current.hack["disable_chrome_parrot"] = true
			}
			if !b.forTest && !b.forExport && b.in.BrutalRates != nil {
				if rates, ok := b.in.BrutalRates[rayNode.ID()]; ok {
					if rates.DownKbps > 0 {
						current.hack["down_mbps"] = max(int64(1), rates.DownKbps/1000)
					}
					if rates.UpKbps > 0 {
						current.hack["up_mbps"] = max(int64(1), rates.UpKbps/1000)
					}
				}
			}
		}
		// honest connect (experiment): fields of the core fork's, not in an export
		if !b.forExport && s.HonestConnect {
			if h, ok := bean.(*nikofmt.HysteriaBean); ok && num(h.ProtocolVersion) == 2 && flag(h.NikoAck) {
				current.hack["await_dial"] = true
			}
			if n, ok := bean.(*nikofmt.NaiveBean); ok && flag(n.NikoAck) {
				current.hack["await_dial"] = true
			}
		}
		if n, ok := bean.(*nikofmt.NaiveBean); ok && !b.forExport {
			// UDP as HTTP/3 datagrams (raycontrol naive-udp-v1, udp=1), beside udp_over_tcp
			if flag(n.NikoUdp) && flag(n.SUoT) {
				current.hack["udp_datagram"] = true
				if b.result.NaiveDatagramTags == nil {
					b.result.NaiveDatagramTags = map[string]int64{}
				}
				b.result.NaiveDatagramTags[tagOut] = p.ID
			}
			// naive over QUIC with a root of the phone's
			if s.NaiveQuicPhoneRoots && s.RootStore == "system" && str(n.Proto) == "quic" {
				current.hack["quic_locally_trusted_roots"] = true
			}
			// ping and traceroute through the node (icmp=1)
			if s.IcmpThroughNode && flag(n.NikoIcmp) {
				current.hack["icmp"] = true
			}
		}
		// UDP desync of QUIC clients (core common/udpdesync)
		if udpDesyncApplies(outboundType, bean, current.hack["udp_datagram"] == true) &&
			(num(base.UdpPrelude) > 0 || flag(base.UdpReorderInitial)) {
			desync := object{}
			if prelude := coerceIn(num(base.UdpPrelude), 0, 8); prelude > 0 {
				desync["prelude"] = int64(prelude)
			}
			// never a name of the node: its front would pin the flow to that backend, the fakes are random bytes
			if base.UdpPreludeSni != nil {
				if sni := nikofmt.KtTrim(*base.UdpPreludeSni); !nikofmt.KtIsBlank(sni) {
					node := b.forHost(str(base.ServerAddress))
					if node == nil || !b.nodeNames(node)[nikofmt.KtLowercase(sni)] {
						desync["prelude_sni"] = sni
					}
				}
			}
			if ttl := coerceIn(num(base.UdpPreludeTtl), 0, 255); ttl > 0 {
				desync["prelude_ttl"] = int64(ttl)
			}
			// under Hysteria2 obfs the core never sees a QUIC Initial: reordering is moot
			h, isHysteria := bean.(*nikofmt.HysteriaBean)
			if flag(base.UdpReorderInitial) && !(isHysteria && !nikofmt.KtIsBlank(str(h.Obfuscation))) {
				desync["reorder_initial"] = true
			}
			current.hack["udp_desync"] = desync
		}

		// port knocking: a libcore "knock" outbound dials the server for this hop. A raycontrol node gets SPA v2
		// instead of the profile's own sequence, its device key by reference
		knockTag := ""
		var knockHolder *option
		pastDetourHolder = nil
		if rayNode != nil || !blank(base.KnockSequence) {
			knockTag = tagOut + "-knock"
			knock := newOption(object{"type": "knock", "tag": knockTag})
			var options object
			isRelayRow := false
			if rayNode != nil {
				if _, ownerKey := b.ownerOf(p.ID); ownerKey != "" {
					if relayID := nikoray.RelayOfKey(ownerKey); relayID != "" {
						if relay := rayNode.RelayByID(relayID); relay != nil {
							options = rayNode.KnockOptionsForRelay(*relay)
							isRelayRow = true
						}
					}
				}
				if options == nil {
					options = rayNode.KnockOptions()
				}
			} else if options, err = knockOutboundOptions(bean); err != nil {
				return "", err
			}
			for k, v := range options {
				knock.hack[k] = v
			}
			knockHolder = knock
			// the node's UDP service port rotates (spa-v2.md §11): the knock outbound follows it
			if rayNode != nil {
				if ports := b.rotatingUDPPorts(rayNode, p); ports != nil {
					knock.hack["udp_ports"] = ports
				}
			}
			// only for direct TCP 443 profiles (REALITY, naive): relay rows knock at the relay, not node.Host
			_, isNaive := bean.(*nikofmt.NaiveBean)
			if !isRelayRow && b.buildingProxy && entity.ID == b.proxy.ID && index == last && rayNode != nil && rayNode.DoH != "" &&
				(standardV2Ray(bean) != nil || isNaive) {
				b.nodeDoH, b.nodeDoHKnock = rayNode, knockTag
			}
			if resolver, ok := current.hack["domain_resolver"]; ok {
				delete(current.hack, "domain_resolver")
				knock.hack["domain_resolver"] = resolver
			}
			current.hack["detour"] = knockTag
			pastDetourHolder = knock
			b.outbounds = append(b.outbounds, knock)
		}

		// the Yggdrasil profile's own mesh node: the device's key, the peer as the profile has it
		if yggPeer != "" {
			seed := str(base.YggKey)
			// a second mesh node with the device's key would claim its address next to the running one
			if b.forTest && b.dev.YggKeyRunning(seed) {
				return "", fmt.Errorf("%s: its Yggdrasil key is in use by the running connection; a second link "+
					"would disturb it", name)
			}
			// one mesh node per key
			if known, ok := b.yggProfileTags[seed]; ok && known[1] != yggPeer {
				return "", fmt.Errorf("%s: its Yggdrasil key is used with another node in this configuration", name)
			}
			// only the node gets its SPA: the knock outbound knocks at the node's own names (and what they resolve to)
			// and dials anything else plainly; no public peer is taken here (RayChannel's, stage 4)
			if knockHolder != nil {
				knockHolder.hack["hosts"] = knockHosts(rayNode, yggPeer)
			}
			known, ok := b.yggProfileTags[seed]
			if !ok {
				tag := tagOut + "-ygg"
				endpoint := newOption(object{"type": "yggdrasil", "tag": tag})
				// the device's key never leaves the app
				if b.forExport {
					endpoint.hack["private_key"] = "<the device key stays in the app>"
				} else {
					endpoint.hack["private_key"] = seed
				}
				endpoint.hack["peers"] = []any{yggPeer}
				endpoint.hack["node_info_privacy"] = true
				endpoint.hack["pq"] = "on"
				if knockTag != "" {
					endpoint.hack["detour"] = knockTag
				}
				b.endpoints = append(b.endpoints, endpoint)
				if b.yggProfileTags == nil {
					b.yggProfileTags = map[string][2]string{}
				}
				known = [2]string{tag, yggPeer}
				b.yggProfileTags[seed] = known
				b.yggProfileKeys = append(b.yggProfileKeys, seed)
			}
			delete(current.hack, "domain_resolver")
			current.hack["detour"] = known[0]
			pastDetourHolder = nil
		}

		// an entry hop whose server is a Yggdrasil address dials through the mesh; with port knocking the knocks go
		// through it too
		if b.yggdrasil && yggPeer == "" && index == last && !tunnel.enabled() &&
			isYggdrasilAddress(str(base.ServerAddress)) {
			// a latency test runs next to the main node: a node of its own, with a one-time key
			if b.forTest {
				b.addYggdrasilEndpoint(b.dev.YggdrasilKey())
			}
			dialing := pastDetourHolder
			if dialing == nil {
				dialing = current
			}
			delete(dialing.hack, "domain_resolver")
			dialing.hack["detour"] = tagYggdrasil
			if b.buildingProxy {
				b.proxyThroughYggdrasil = true
			}
		}

		// DNS tunnel (experimental): the entry hop reaches its server through the core's dnstt or masterdnsvpn
		// outbound, as the profile has it
		if tunnel.enabled() {
			prefix := "DNS tunnel (" + name + ")"
			// Resolve "auto" resolver (set by raycontrol subscriptions when the node leaves resolver choice
			// to the client): explicit user setting takes precedence over probed per-network overrides,
			// which in turn take precedence over static defaults (H2, H6).
			if strings.EqualFold(nikofmt.KtTrim(tunnel.resolver), "auto") {
				var nodeID string
				if auth, ok := tunnel.authNode(); ok {
					nodeID = auth[0]
				} else if owner, _ := b.ownerOf(p.ID); owner != nil {
					nodeID = owner.ID()
				}
				resolved := ""
				switch tunnel.engine {
				case dnsTunnelDNSTT:
					if v := nikofmt.KtTrim(b.s.RaycontrolDnsttResolver); v != "" && !strings.EqualFold(v, "auto") {
						resolved = v
					}
				case dnsTunnelMasterDNS:
					if v := nikofmt.KtTrim(b.s.RaycontrolMasterDnsResolvers); v != "" && !strings.EqualFold(v, "auto") {
						resolved = v
					}
				}
				if resolved == "" && nodeID != "" && len(b.in.DnsResolvers) > 0 {
					if v := nikofmt.KtTrim(b.in.DnsResolvers[tunnel.engine+":"+nodeID]); v != "" {
						resolved = v
					} else if tunnel.engine != dnsTunnelSlipstream {
						if v := nikofmt.KtTrim(b.in.DnsResolvers[nodeID]); v != "" {
							resolved = v
						}
					}
				}
				if resolved == "" {
					switch tunnel.engine {
					case dnsTunnelDNSTT:
						resolved = DefaultDnsttResolver
					case dnsTunnelMasterDNS:
						resolved = DefaultMasterDnsResolvers
					case dnsTunnelSlipstream:
						resolved = DefaultSlipstreamResolvers
					}
				}
				if resolved != "" {
					tunnel.resolver = resolved
				}
			}
			if reason := tunnel.validate(); reason != "" {
				return "", fmt.Errorf("%s: %s", name, reason)
			}
			if knockTag != "" {
				return "", errors.New(prefix + ": not together with port knocking")
			}
			// the tunnels carry TCP only
			v2ray := standardV2Ray(bean)
			switch bean.(type) {
			case *nikofmt.HysteriaBean, *nikofmt.TuicBean, *nikofmt.WireGuardBean:
				return "", errors.New(prefix + ": the tunnel carries TCP, this profile needs UDP")
			}
			if v2ray != nil && str(v2ray.Type) == "quic" {
				return "", errors.New(prefix + ": the tunnel carries TCP, this profile needs UDP")
			}
			if isConfig {
				return "", errors.New(prefix + ": not supported for custom configs")
			}
			if index != last {
				return "", errors.New(prefix + ": only on the first hop of a chain")
			}
			if b.buildingProxy {
				b.tunnelledHop = true
			}
			if tunnel.engine == dnsTunnelSlipstream {
				if b.forExport {
					return "", errors.New(prefix + ": slipstream runs a client of its own, not in an exported config")
				}
				var authNode *nikoray.Node
				var authServiceID int
				if auth, ok := tunnel.authNode(); ok {
					if authNode = b.rayNode(auth[0]); authNode == nil {
						return "", errors.New(prefix + ": the raycontrol node of this tunnel is not imported (dnsauth)")
					}
					authServiceID, _ = strconv.Atoi(auth[1])
				}
				if b.forTest {
					// Ephemeral test instances do not spawn external slipstream-client helper processes.
					return "", errors.New(prefix + ": slipstream cannot be measured in an ephemeral test instance")
				}
				if b.desktop {
					// Variant A: bundled slipstream-client child process with core UDP direct mappings (ConfigBuilder.kt:947-990).
					mapped := make([]string, 0, len(tunnel.slipResolvers()))
					for i, hp := range tunnel.slipResolvers() {
						mappingPort := allocateUDPPort(b.dev)
						mappingTag := fmt.Sprintf("%s-slipstream-%d", tagOut, i)
						b.inbounds = append(b.inbounds, object{
							"type":             "direct",
							"listen":           localhost,
							"listen_port":      int64(mappingPort),
							"tag":              mappingTag,
							"network":          "udp",
							"override_address": hp.Host,
							"override_port":    int64(hp.Port),
						})
						b.routeRules = append(b.routeRules, newOption(object{
							"inbound":  []any{mappingTag},
							"outbound": tagDirect,
						}))
						mapped = append(mapped, fmt.Sprintf("%s:%d", localhost, mappingPort))
					}
					env := map[string]string{}
					var clockOffset int64
					if authNode != nil {
						devKeyBytes, decErr := hex.DecodeString(authNode.DeviceKey)
						if decErr != nil {
							return "", fmt.Errorf("%s: decode node device key: %w", prefix, decErr)
						}
						derived, keyErr := dnsttTransport.AuthKey(devKeyBytes, authNode.ID(), uint16(authServiceID))
						if keyErr != nil {
							return "", fmt.Errorf("%s: derive dnsauth key: %w", prefix, keyErr)
						}
						env["SLIPSTREAM_DNSAUTH_KEY"] = hex.EncodeToString(derived)
						clockOffset = authNode.ClockSkew
					}
					localPort := b.dev.Port()
					b.result.SlipstreamLaunches = append(b.result.SlipstreamLaunches, SlipstreamLaunch{
						Args:      tunnel.slipstreamArgs(localPort, mapped, clockOffset),
						Env:       env,
						LocalPort: localPort,
					})
					if tlsObj, ok := current.base["tls"].(object); ok && tlsObj["enabled"] == true {
						if sni, _ := tlsObj["server_name"].(string); nikofmt.KtIsBlank(sni) && base.ServerAddress != nil && !nikofmt.IsIPAddress(*base.ServerAddress) {
							tlsObj["server_name"] = *base.ServerAddress
						}
					}
					delete(current.hack, "domain_resolver")
					current.hack["server"] = localhost
					current.hack["server_port"] = int64(localPort)
				} else {
					return "", errors.New(prefix + ": slipstream is not supported here")
				}
			} else {
			tunnelTag := tagOut + "-dnstunnel"
			// libcore's protect server runs with the main instance; a latency test without one must not point the
			// tunnel's own sockets at a missing socket. A computer has no such server: its TUN leaves the tunnel's
			// resolvers out instead (excludeTunnelResolvers)
			protectPath := ""
			if !b.desktop && (!b.forTest || b.dev.ProtectPathReady()) {
				protectPath = "protect_path"
			}
			if tunnel.engine == dnsTunnelMasterDNS {
				b.tunnelResolvers = append(b.tunnelResolvers, tunnel.resolvers()...)
			}
			// dnsauth-v1: the node's device key goes into the core by reference, never into the config; the tag's epoch
			// is the node's, by the subscription's skew
			var authNode *nikoray.Node
			if auth, ok := tunnel.authNode(); ok {
				if authNode = b.rayNode(auth[0]); authNode == nil {
					return "", errors.New(prefix + ": the raycontrol node of this tunnel is not imported (dnsauth)")
				}
			}
			tunnelOutbound := newOption(object{"tag": tunnelTag})
			for k, v := range tunnel.outboundOptions(protectPath) {
				tunnelOutbound.hack[k] = v
			}
			if auth := tunnel.dnsAuthOptions(authNode); auth != nil {
				tunnelOutbound.hack["auth"] = auth
			}
			b.outbounds = append(b.outbounds, tunnelOutbound)
			// the tunnel server resolves or ignores the hop's server name
			delete(current.hack, "domain_resolver")
			current.hack["detour"] = tunnelTag
			}
		}

		if current.wireGuard {
			b.endpoints = append(b.endpoints, current)
		} else {
			b.outbounds = append(b.outbounds, current)
		}
		pastOutbound = current
		pastEntity = p
	}
	return chainTagOut, nil
}

// addBypassDNSBean adds the entry hop's bean to bypassDNSBeans, a HashSet: once for equal beans (the app's
// equality is the beans' Kryo bytes without the name; here their JSON without it). Its order is the profiles', not
// the set's (the Kryo bytes' hash): only server names that meet in one bucket of the names' own HashSet could tell.
func (b *builder) addBypassDNSBean(bean nikofmt.Bean) {
	key := beanKey(bean)
	for _, known := range b.bypassDNSBeans {
		if beanKey(known) == key {
			return
		}
	}
	b.bypassDNSBeans = append(b.bypassDNSBeans, bean)
}

func beanKey(bean nikofmt.Bean) string {
	data, _ := json.Marshal(bean)
	var m map[string]any
	_ = json.Unmarshal(data, &m)
	delete(m, "name")
	data, _ = json.Marshal(m)
	return nikofmt.TypeName(bean) + string(data)
}

// singMux is ProxyEntity.singMux: the multiplex options of a VMess, VLESS or Trojan profile.
func singMux(bean nikofmt.Bean) object {
	var s *nikofmt.StandardV2RayBean
	switch b := bean.(type) {
	case *nikofmt.VMessBean:
		s = &b.StandardV2RayBean
	case *nikofmt.TrojanBean:
		s = &b.StandardV2RayBean
	default:
		return nil
	}
	mux := object{}
	putBool(mux, "enabled", s.EnableMux)
	putBool(mux, "padding", s.MuxPadding)
	putInt(mux, "max_streams", s.MuxConcurrency)
	switch num(s.MuxType) {
	case 1:
		mux["protocol"] = "smux"
	case 2:
		mux["protocol"] = "yamux"
	default:
		mux["protocol"] = "h2mux"
	}
	return mux
}

// sUoT is a bean's public sUoT field, the beans that have one.
func sUoT(bean nikofmt.Bean) *bool {
	switch b := bean.(type) {
	case *nikofmt.NaiveBean:
		return b.SUoT
	case *nikofmt.SOCKSBean:
		return b.SUoT
	case *nikofmt.ShadowsocksBean:
		return b.SUoT
	}
	return nil
}

// standardV2Ray is the bean's StandardV2RayBean, nil for other beans.
func standardV2Ray(bean nikofmt.Bean) *nikofmt.StandardV2RayBean {
	switch b := bean.(type) {
	case *nikofmt.VMessBean:
		return &b.StandardV2RayBean
	case *nikofmt.TrojanBean:
		return &b.StandardV2RayBean
	case *nikofmt.HttpBean:
		return &b.StandardV2RayBean
	}
	return nil
}

// udpDesyncApplies: the outbound opens a QUIC connection of its own, which the core can shape: Hysteria2 and TUIC
// always, naive over QUIC or with UDP as HTTP/3 datagrams.
func udpDesyncApplies(outboundType string, bean nikofmt.Bean, naiveDatagrams bool) bool {
	if udpDesyncTypes[outboundType] {
		return true
	}
	n, ok := bean.(*nikofmt.NaiveBean)
	return ok && (str(n.Proto) == "quic" || naiveDatagrams)
}

// carriesUDPAsDatagrams is ProxyEntity.carriesUdpAsDatagrams: UDP as datagrams of its own (true), inside a stream or
// not at all (false), not known (nil: a custom config, Mieru).
func carriesUDPAsDatagrams(p *Profile) *bool {
	base := nikofmt.Base(p.Bean)
	if dnsTunnelOf(base).enabled() || !blank(base.YggPeer) {
		return ptrTo(false)
	}
	switch b := p.Bean.(type) {
	case *nikofmt.HysteriaBean, *nikofmt.TuicBean, *nikofmt.WireGuardBean:
		return ptrTo(true)
	case *nikofmt.ShadowsocksBean:
		return ptrTo(!flag(b.SUoT))
	case *nikofmt.SOCKSBean:
		return ptrTo(num(b.Protocol) == nikofmt.ProtocolSOCKS5 && !flag(b.SUoT))
	case *nikofmt.VMessBean, *nikofmt.TrojanBean, *nikofmt.NaiveBean, *nikofmt.HttpBean, *nikofmt.SSHBean,
		*nikofmt.TrojanGoBean, *nikofmt.AnyTLSBean:
		return ptrTo(false)
	}
	return nil
}

// udpRidesAStream: every hop is known and one carries UDP inside a stream.
func udpRidesAStream(chain []*Profile) bool {
	if len(chain) == 0 {
		return false
	}
	anyStream := false
	for _, p := range chain {
		c := carriesUDPAsDatagrams(p)
		if c == nil {
			return false
		}
		anyStream = anyStream || !*c
	}
	return anyStream
}

// pinRefusal is why an outbound cannot be held to key pins, "" when it can.
func pinRefusal(outboundType string, external bool, tls object) string {
	switch {
	case external || !tlsPinTypes[outboundType]:
		return "not supported for this profile type"
	case tls == nil || tls["enabled"] != true:
		return "the profile does not use TLS"
	case asObject(tls["reality"]) != nil && asObject(tls["reality"])["enabled"] == true:
		return "not supported with REALITY"
	}
	// Chromium pins a key to a name, never to an address
	if name, _ := tls["server_name"].(string); outboundType == "naive" && nikofmt.KtIsBlank(name) {
		return "naive needs a server name (SNI)"
	}
	return ""
}

// pinTLSTweaks are the TLS options that hold an outbound to the key pins of its server: Go TLS takes the pin in
// place of CA verification (a custom CA goes); naive checks it besides the chain.
func pinTLSTweaks(outboundType, pins string) (object, error) {
	parsed, err := parseCertificatePins(pins)
	if err != nil {
		return nil, err
	}
	tweaks := object{"certificate_public_key_sha256": parsed}
	if outboundType != "naive" {
		tweaks["certificate"] = []any{}
	}
	return tweaks, nil
}

// mergeTLS is the tweaks over an outbound's own TLS map among its raw options; a typed outbound has none there.
func mergeTLS(own, tweaks object) object {
	if own == nil {
		return tweaks
	}
	merged := object{}
	for k, v := range own {
		merged[k] = v
	}
	for k, v := range tweaks {
		merged[k] = v
	}
	return merged
}

func asObject(v any) object {
	o, _ := v.(object)
	return o
}

func coerceIn(n, low, high int) int { return max(low, min(n, high)) }

type udpPortAllocator interface {
	UDPPort() int
}

func allocateUDPPort(dev Device) int {
	if alloc, ok := dev.(udpPortAllocator); ok {
		if port := alloc.UDPPort(); port > 0 {
			return port
		}
	}
	return dev.Port()
}

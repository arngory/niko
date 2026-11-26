package nikoconf

import (
	"errors"
	"slices"
	"strconv"
	"strings"

	"libcore/nikofmt"
	"libcore/nikoray"
)

// The raycontrol nodes of a build (ConfigRay), the static part (slice 11): the nodes the store keeps, their rows by
// the profiles' ids. A row of a node gets the node's knock with SPA v2, the TLS a node needs, the spare connection of
// REALITY, Hysteria2's keepalive, its rotating UDP port, the node's DoH as the remote DNS and the dnsauth of its DNS
// tunnels, as the app's build gives them. What the app decides by the network (Brutal's rates, the public mesh
// peers, the DNS tunnels' resolvers and zones, the LAN, the relays) is stage 4's: here as ConfigRay's "no data".
// The device keys are not in the config: the knock takes each by its reference (Node.KeyRef), which the daemon hands
// to the core beside it.

// forHost is RayNodes.forHost: the node whose host or name address is.
func (b *builder) forHost(address string) *nikoray.Node {
	if nikofmt.KtIsBlank(address) {
		return nil
	}
	for i := range b.in.Nodes {
		if b.in.Nodes[i].MatchesHost(address) {
			return &b.in.Nodes[i]
		}
	}
	return nil
}

// ownerOf is RayNodes.ownerOf: the node whose row the profile is, and the row's key.
func (b *builder) ownerOf(id int64) (*nikoray.Node, string) {
	for i := range b.in.Nodes {
		if key := b.in.Nodes[i].OwnerOf(id); key != "" {
			return &b.in.Nodes[i], key
		}
	}
	return nil, ""
}

// rayNode is RayNodes.get: the node of an id.
func (b *builder) rayNode(id string) *nikoray.Node {
	for i := range b.in.Nodes {
		if b.in.Nodes[i].ID() == id {
			return &b.in.Nodes[i]
		}
	}
	return nil
}

// nodeNames is RayNodes.names: the names the node serves (its host and name, its DoH's host, the names of its rows,
// those its rows' links carry, current and next); a fake QUIC Initial naming one would pin the flow to that backend
// of the node's front. The names earlier subscriptions carried are not kept here.
func (b *builder) nodeNames(node *nikoray.Node) map[string]bool {
	names := map[string]bool{nikofmt.KtLowercase(node.Host): true}
	if node.DNSName != "" {
		names[node.DNSName] = true
	}
	for _, past := range node.PastNames {
		if past != "" {
			names[nikofmt.KtLowercase(past)] = true
		}
	}
	if node.DoH != "" {
		if u, err := nikofmt.ParseJavaURI(node.DoH); err == nil && u.Host != "" {
			names[nikofmt.KtLowercase(u.Host)] = true
		}
	}
	for _, id := range node.ProfileIDs {
		if p := b.profile(id); p != nil {
			for _, name := range servedNames(p.Bean) {
				names[name] = true
			}
		}
	}
	for _, links := range node.Links {
		for _, link := range []string{links.Link, links.Next} {
			if link == "" {
				continue
			}
			if bean, err := nikoray.ParseRow(link, nikofmt.DefaultSettings); err == nil {
				bean.InitializeDefaultValues()
				for _, name := range servedNames(bean) {
					names[name] = true
				}
			}
		}
	}
	return names
}

// servedNames is RayNodes.servedNames: the names a profile reaches its server by, the TLS server name and the
// address, its own Yggdrasil peer's host.
func servedNames(bean nikofmt.Bean) []string {
	var names []string
	add := func(s *string) {
		if s != nil {
			if name := nikofmt.KtLowercase(nikofmt.KtTrim(*s)); name != "" {
				names = append(names, name)
			}
		}
	}
	switch v := bean.(type) {
	case *nikofmt.HysteriaBean:
		add(v.Sni)
	case *nikofmt.NaiveBean:
		add(v.Sni)
	default:
		if s := standardV2Ray(bean); s != nil {
			add(s.Sni)
		}
	}
	base := nikofmt.Base(bean)
	add(base.ServerAddress)
	if peer := str(base.YggPeer); peer != "" {
		if host, ok := yggPeerHost(peer); ok {
			add(&host)
		}
	}
	return names
}

// yggPeerHost is yggPeerHost: the host of a peer link, java.net.URI's (an IPv6 one in brackets).
func yggPeerHost(peer string) (string, bool) {
	u, err := nikofmt.ParseJavaURI(peer)
	if err != nil || u.Host == "" {
		return "", false
	}
	return u.Host, true
}

// yggPeerAtNode is yggPeerAtNode: a node's Yggdrasil peer dialled at the node's own address, its name kept as the TLS
// name; a peer at an address already, or one that does not parse, as it is.
func yggPeerAtNode(peer, address string) string {
	u, err := nikofmt.ParseJavaURI(peer)
	if err != nil || u.Host == "" {
		return peer
	}
	name := strings.Trim(u.Host, "[]")
	node := strings.Trim(nikofmt.KtTrim(address), "[]")
	if node == "" || strings.EqualFold(name, node) || nikofmt.IsIPAddress(name) {
		return peer
	}
	query := ""
	if u.RawQuery != nil {
		query = *u.RawQuery
	}
	hasSni := false
	for _, part := range strings.Split(query, "&") {
		hasSni = hasSni || strings.HasPrefix(part, "sni=")
	}
	newQuery := query
	if !hasSni {
		var parts []string
		for _, part := range []string{query, "sni=" + name} {
			if part != "" {
				parts = append(parts, part)
			}
		}
		newQuery = strings.Join(parts, "&")
	}
	host := node
	if strings.Contains(node, ":") {
		host = "[" + node + "]"
	}
	path := ""
	if u.RawPath != nil {
		path = *u.RawPath
	}
	out := u.Scheme + "://" + host + ":" + strconv.Itoa(u.Port) + path
	if newQuery != "" {
		out += "?" + newQuery
	}
	return out
}

// nodeTLSTweaks is nodeTlsTweaks: what a node needs on an outbound's TLS, post-quantum key exchange only. naive
// (Chromium) offers it by default and takes none of these.
func nodeTLSTweaks(outboundType string, tls object) object {
	if outboundType == "naive" || tls["enabled"] != true {
		return nil
	}
	tweaks := object{}
	// only the Chrome fingerprint of the core's uTLS carries an X25519MLKEM768 share
	if utls, _ := tls["utls"].(object); utls["enabled"] == true && utls["fingerprint"] != "chrome" {
		tweaks["utls"] = object{"fingerprint": "chrome"}
	}
	if reality, _ := tls["reality"].(object); reality["enabled"] == true {
		tweaks["reality"] = object{"key_share": "hybrid"}
	} else {
		tweaks["curve_preferences"] = []any{"X25519MLKEM768"}
	}
	return tweaks
}

// nodePrewarmIdle is how long the spare connection to a node stays open unused (NODE_PREWARM_IDLE).
const nodePrewarmIdle = "12s"

// nodePrewarm is nodePrewarm: the core's prewarm for VLESS with REALITY straight over TCP to a node, "" for anything
// else.
func nodePrewarm(outboundType string, outbound object) string {
	if outboundType != "vless" || outbound["transport"] != nil {
		return ""
	}
	tls, _ := outbound["tls"].(object)
	reality, _ := tls["reality"].(object)
	if tls["enabled"] != true || reality["enabled"] != true {
		return ""
	}
	return nodePrewarmIdle
}

// rotatingUDPPorts is rotatingUdpPorts: the rotating port of a node's UDP service (spa-v2.md §11) for a Hysteria2 row
// of the node's subscription, its key naming the service; nil for other profiles, or one that hops ports itself.
func (b *builder) rotatingUDPPorts(node *nikoray.Node, p *Profile) []any {
	h, ok := p.Bean.(*nikofmt.HysteriaBean)
	if !ok {
		return nil
	}
	ports := str(h.ServerPorts)
	if strings.ContainsAny(ports, ",-") {
		return nil
	}
	owner, key := b.ownerOf(p.ID)
	if owner == nil || owner.ID() != node.ID() || nikoray.RelayOfKey(key) != "" {
		return nil
	}
	svc, ok := nikofmt.KtToIntOrNull(substringBefore(key, "#"))
	if !ok {
		return nil
	}
	var schedule *nikoray.PortSchedule
	for _, s := range node.Services {
		if s.ID == svc {
			schedule = s.Ports
			break
		}
	}
	if schedule == nil {
		return nil
	}
	port, ok := nikofmt.KtToIntOrNull(nikofmt.KtTrim(ports))
	if !ok {
		return nil
	}
	return []any{object{"port": int64(port), "port_key": schedule.PortKey, "port_base": int64(schedule.PortBase),
		"port_range": int64(schedule.PortRange)}}
}

// nodeDoHServer is nodeDohServer: the node's DoH as the remote DNS, RFC 8484 over h2 to the node through the knock
// that opens it, its name as SNI, post-quantum key exchange only and its key pins where its row has them.
func nodeDoHServer(node *nikoray.Node, knockTag string) (object, error) {
	u, err := nikofmt.ParseOkURL(node.DoH)
	if err != nil {
		return nil, errors.New("raycontrol node " + node.Host + ": bad DoH URL")
	}
	host, resolver := nodeDoHDial(node.Host, node.ReachName())
	path := u.EncodedPath
	if u.RawQuery != nil {
		path += "?" + *u.RawQuery
	}
	server := object{"type": "https", "tag": "dns-remote", "server": host, "server_port": int64(u.Port), "path": path,
		"detour": knockTag}
	if resolver != "" {
		server["domain_resolver"] = resolver
	}
	tls := object{"enabled": true, "server_name": u.Host, "curve_preferences": []any{"X25519MLKEM768"}}
	if !nikofmt.KtIsBlank(node.DoHPins) {
		pins, err := parseCertificatePins(node.DoHPins)
		if err != nil {
			return nil, err
		}
		tls["certificate_public_key_sha256"] = pins
	}
	server["tls"] = tls
	return server, nil
}

// nodeDoHDial is nodeDohDial: where the node's DoH dials and who resolves it, the node's name for the knock to
// resolve, else its host, a name of which only the direct DNS resolves, an address none.
func nodeDoHDial(host, reachName string) (string, string) {
	switch {
	case reachName != "":
		return reachName, ""
	case nikofmt.IsIPAddress(host):
		return host, ""
	}
	return host, "dns-direct"
}

// dnsAuthOptions are DnsTunnel.outboundOptions' auth: the node's service, the device key by its reference, the
// node's clock skew, dnsauth-v2's version.
func (t dnsTunnel) dnsAuthOptions(node *nikoray.Node) object {
	auth, ok := t.authNode()
	if !ok || node == nil {
		return nil
	}
	svc, _ := nikofmt.KtToIntOrNull(auth[1])
	o := object{"node_id": auth[0], "service_id": int64(svc), "device_key_ref": node.KeyRef()}
	if node.ClockSkew != 0 {
		o["clock_skew"] = node.ClockSkew
	}
	if strings.HasPrefix(t.auth, "v2;") {
		o["version"] = int64(2)
	}
	return o
}

// knockHosts is the knock's hosts of a node's Yggdrasil row: the node's host and the peer's, without brackets, once.
func knockHosts(node *nikoray.Node, peer string) []any {
	var hosts []string
	if node != nil {
		hosts = append(hosts, strings.Trim(node.Host, "[]"))
	}
	if host, ok := yggPeerHost(peer); ok {
		hosts = append(hosts, strings.Trim(host, "[]"))
	}
	out := []any{}
	var seen []string
	for _, h := range hosts {
		if !slices.Contains(seen, h) {
			seen = append(seen, h)
			out = append(out, h)
		}
	}
	return out
}

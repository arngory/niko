package nikofmt

import (
	"errors"
	"regexp"
	"strconv"
	"strings"
)

// Settings are what the app's parsers read from its settings while they parse (DataStore): the resolvers a DNS
// tunnel goes through when its link leaves them to the client (niko-dns=dnstt;;zone;key), as a node's links do.
type Settings struct {
	// DnsttResolver is DataStore.raycontrolDnsttResolver (Settings → raycontrol nodes).
	DnsttResolver string
	// MasterDnsResolvers is DataStore.raycontrolMasterDnsResolvers, for MasterDnsVPN and slipstream.
	MasterDnsResolvers string
}

// DefaultSettings are the app's defaults: "auto", the resolver picked by network when the tunnel starts.
var DefaultSettings = Settings{DnsttResolver: "auto", MasterDnsResolvers: "auto"}

// The DNS tunnels' engines (fmt/DnsTunnel.kt).
const (
	DnsTunnelDNSTT        = "dnstt"
	DnsTunnelMasterDNSVPN = "masterdnsvpn"
	DnsTunnelSlipstream   = "slipstream"
	SealOptional          = "optional"
	SealRequired          = "required"
)

var (
	nodeID   = regexp.MustCompile(`^[0-9a-f]{32}$`)
	dnsZone  = regexp.MustCompile(`^[a-z0-9.-]+$`) // a zone as the tunnel's own is checked
	sealKey  = regexp.MustCompile(`^[A-Za-z0-9_-]{43}$`)
	maxSpare = 4
	// the peer schemes yggdrasil-go dials (subscription-v1.md §4)
	yggViaSchemes = []string{"tls", "quic", "wss", "ws", "tcp"}
)

// applyNikoParams is fmt/NikoParams.kt AbstractBean.applyNikoParams: a raycontrol link's DNS tunnel (niko-dns and the
// parameters that go with it), certificate pins (niko-pin) and Yggdrasil profile (niko-ygg, niko-ygg-via). param is
// a parameter's first value, params all of a repeated one's. An error is the app's require() failing: the link is
// dropped.
func applyNikoParams(b *AbstractBean, settings Settings, param func(string) *string, params func(string) []*string) error {
	if spec := param("niko-dns"); spec != nil && !ktIsBlank(*spec) {
		parts := strings.Split(*spec, ";")
		if len(parts) != 4 {
			return errors.New("niko-dns: want engine;resolver;zone;key-or-method")
		}
		engine, resolver, zone, last := parts[0], parts[1], parts[2], parts[3]
		switch engine {
		case DnsTunnelDNSTT:
			b.DnsTunnel = ptr(DnsTunnelDNSTT)
			if ktIsBlank(resolver) {
				resolver = settings.DnsttResolver
			}
			b.DnsTunnelResolver = &resolver
			b.DnsTunnelDomain = &zone
			b.DnsTunnelKey = &last
		case "masterdns", DnsTunnelMasterDNSVPN:
			b.DnsTunnel = ptr(DnsTunnelMasterDNSVPN)
			b.DnsTunnelResolver = ptr(resolverLines(resolver, settings.MasterDnsResolvers))
			b.DnsTunnelDomain = &zone
			method, ok := javaParseInt(last, 10)
			if !ok {
				method = 5
			}
			b.DnsTunnelMethod = ptr(int(method))
			// the node's MasterDnsVPN runs in TCP mode with the inner stage as its one upstream
			b.DnsTunnelFixedUpstream = ptr(true)
			if key := param("niko-dnskey"); key != nil {
				b.DnsTunnelKey = ptr(ktTrim(*key))
			}
		case DnsTunnelSlipstream:
			// raycontrol slipstream-v1 §4: niko-dns=slipstream;<resolvers>;<zone>;<service id>,
			// niko-slip=v1;<pin>;<ALPN>;<SNI>
			b.DnsTunnel = ptr(DnsTunnelSlipstream)
			b.DnsTunnelResolver = ptr(resolverLines(resolver, settings.MasterDnsResolvers))
			b.DnsTunnelDomain = &zone
			slip := param("niko-slip")
			if slip == nil {
				return errors.New("niko-slip: want v1;pin;alpn;sni")
			}
			fields := strings.Split(*slip, ";")
			for i := range fields {
				fields[i] = ktTrim(fields[i])
			}
			if len(fields) != 4 || fields[0] != "v1" {
				return errors.New("niko-slip: want v1;pin;alpn;sni")
			}
			b.DnsTunnelKey = &fields[1]
			b.DnsTunnelTls = ptr(fields[2] + ";" + fields[3])
		default:
			return errors.New("niko-dns: unknown engine " + engine)
		}
		// dnsauth-v1: the queries carry a tag of the device's key for this node and service; another version is
		// unknown and the tunnel goes without
		b.DnsTunnelAuth = ptr("")
		if v := param("niko-dnsauth"); v != nil && ktTrim(*v) == "v1" {
			var node *string
			if v := param("niko-node"); v != nil {
				node = ptr(ktLowercase(ktTrim(*v)))
			}
			// slipstream's service id is niko-dns's last field too
			svcText := param("niko-svc")
			if svcText == nil && engine == DnsTunnelSlipstream {
				svcText = &last
			}
			var svc int32
			svcOK := false
			if svcText != nil {
				svc, svcOK = javaParseInt(ktTrim(*svcText), 10)
			}
			if node == nil || !nodeID.MatchString(*node) || !svcOK || svc < 0 || svc > 0xffff {
				return errors.New("niko-dnsauth: needs niko-node and niko-svc")
			}
			// slipstream's front takes dnsauth-v2 only (slipstream-v1 §2)
			version := "v1;"
			if engine == DnsTunnelSlipstream {
				version = "v2;"
			}
			b.DnsTunnelAuth = ptr(version + *node + ";" + strconv.Itoa(int(svc)))
		}
		// dnszones-v1: the spare zones of the same service in their order, at most 4; a name that is not one, or the
		// main zone again, is left out
		mainZone := ""
		for _, z := range strings.FieldsFunc(*b.DnsTunnelDomain, func(r rune) bool { return r == ',' || r == ' ' || r == '\n' }) {
			if z = ktLowercase(ktTrim(z)); z != "" {
				mainZone = z
				break
			}
		}
		var spare []string
		if v := param("niko-dns-spare"); v != nil {
			for _, z := range strings.Split(*v, ",") {
				z = ktLowercase(ktTrim(z))
				if z != "" && dnsZone.MatchString(z) && z != mainZone && !contains(spare, z) && len(spare) < maxSpare {
					spare = append(spare, z)
				}
			}
		}
		b.DnsTunnelSpare = ptr(strings.Join(spare, ","))
		// dnsseal-v1: dnstt's framing sealed with the zone's key; mdseal-v1: masterdns's answers sealed with its key,
		// the node's mode with it. Another version is unknown and the tunnel goes open
		b.DnsTunnelSeal = ptr("")
		switch *b.DnsTunnel {
		case DnsTunnelDNSTT:
			if v := param("niko-dnsseal"); v != nil {
				if spec := ktTrim(*v); strings.HasPrefix(spec, "v1;") {
					key := ktTrim(strings.TrimPrefix(spec, "v1;"))
					if !sealKey.MatchString(key) {
						return errors.New("niko-dnsseal: the key is 32 bytes in base64url")
					}
					b.DnsTunnelSeal = &key
				}
			}
		case DnsTunnelMasterDNSVPN:
			if v := param("niko-mdseal"); v != nil {
				fields := strings.Split(*v, ";")
				for i := range fields {
					fields[i] = ktTrim(fields[i])
				}
				if fields[0] == "v1" {
					if contains(fields[1:], "required") {
						b.DnsTunnelSeal = ptr(SealRequired)
					} else {
						b.DnsTunnelSeal = ptr(SealOptional)
					}
				}
			}
		}
	}
	if pin := param("niko-pin"); pin != nil && !ktIsBlank(*pin) {
		b.CertPins = ptr(ktTrim(*pin))
	}
	if spec := param("niko-ygg"); spec != nil && !ktIsBlank(*spec) {
		peer, seed, err := parseYggParam(*spec)
		if err != nil {
			return err
		}
		b.YggPeer = &peer
		b.YggKey = &seed
		// the node's public peers: a way through the public mesh when the direct peer does not connect. A quic://
		// peer only by address: yggdrasil-go looks its name up past the core's DNS
		var via []string
		for _, v := range params("niko-ygg-via") {
			v := ktTrim(*v)
			// a peer java.net.URI gives a host (a server authority that parses), its brackets trimmed
			u, err := parseJavaURI(v)
			if err != nil || u.host == "" || !hasYggViaScheme(v) {
				continue
			}
			if strings.HasPrefix(v, "quic://") && !isIPAddress(strings.Trim(u.host, "[]")) {
				continue
			}
			if !contains(via, v) {
				via = append(via, v)
			}
		}
		b.YggVia = ptr(strings.Join(via, "\n"))
	}
	return nil
}

func hasYggViaScheme(v string) bool {
	for _, s := range yggViaSchemes {
		if strings.HasPrefix(v, s+"://") {
			return true
		}
	}
	return false
}

// resolverLines is the resolvers of a MasterDnsVPN or slipstream niko-dns: comma separated in the link, a line each
// in the profile; none means the setting's.
func resolverLines(resolver, setting string) string {
	var lines []string
	for _, r := range strings.Split(resolver, ",") {
		if r = ktTrim(r); r != "" {
			lines = append(lines, r)
		}
	}
	joined := strings.Join(lines, "\n")
	if ktIsBlank(joined) {
		return setting
	}
	return joined
}

// parseYggParam is NikoParams.kt parseYggParam: niko-ygg's "<peer>;<seed>", a tls:// peer with the node's key and a
// 32-byte seed in hex.
func parseYggParam(spec string) (peer, seed string, err error) {
	parts := strings.Split(spec, ";")
	if len(parts) != 2 {
		return "", "", errors.New("niko-ygg: want peer;seed")
	}
	peer = ktTrim(parts[0])
	seed = ktLowercase(ktTrim(parts[1]))
	u, err := parseJavaURI(peer)
	if err != nil {
		return "", "", err // URISyntaxException
	}
	if u.scheme != "tls" || ktIsBlank(u.host) || u.port <= 0 {
		return "", "", errors.New("niko-ygg: the peer must be tls://host:port")
	}
	key := ""
	found := false
	if u.rawQuery != nil {
		for _, part := range strings.Split(*u.rawQuery, "&") {
			if strings.HasPrefix(part, "key=") {
				key, found = strings.TrimPrefix(part, "key="), true
				break
			}
		}
	}
	if !found || len(key) != 64 || !onlyOf(key, "0123456789abcdefABCDEF") {
		return "", "", errors.New("niko-ygg: the peer needs the node's key")
	}
	if len(seed) != 64 || !onlyOf(seed, "0123456789abcdef") {
		return "", "", errors.New("niko-ygg: the seed must be 64 hex digits")
	}
	return peer, seed, nil
}

// onlyOf reports whether every character of s is one of set (ASCII), so its length in bytes is Java's too.
func onlyOf(s, set string) bool {
	for _, r := range s {
		if !strings.ContainsRune(set, r) {
			return false
		}
	}
	return true
}

var (
	ipv4Address = regexp.MustCompile(`^(?:^([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])\.([01]?[0-9]?[0-9]|2[0-4][0-9]|25[0-5])$)$`)
	ipv6Address = regexp.MustCompile(`^(?:^((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*::((?:[0-9A-Fa-f]{1,4}))?((?::[0-9A-Fa-f]{1,4}))*|((?:[0-9A-Fa-f]{1,4}))((?::[0-9A-Fa-f]{1,4})){7}$)$`)
)

// isIPAddress is ktx String.isIpAddress (NGUtil.isIpv4Address or isIpv6Address): the app's own patterns, matched
// whole as Kotlin's Regex.matches does. IPv6 with "::" or eight groups; no dotted tail, no zone.
func isIPAddress(s string) bool {
	if ipv4Address.MatchString(s) {
		return true
	}
	v6 := s
	if strings.Index(v6, "[") == 0 && strings.LastIndex(v6, "]") > 0 {
		v6 = v6[1:]
		v6 = v6[:strings.LastIndex(v6, "]")]
	}
	return ipv6Address.MatchString(v6)
}

package nikoconf

import (
	"errors"
	"fmt"
	"math"
	"regexp"
	"strconv"
	"strings"
	"unicode"
	"unicode/utf16"

	"libcore/nikofmt"
)

// What the build takes from beside ConfigBuilder.kt: a DNS server of an address (SingBoxOptions.java), a profile's
// DNS tunnel (DnsTunnel.kt), port knocking (PortKnocking.kt), key pins (CertificatePinning.kt), the mesh's open
// ports (MeshPorts.kt), and Util.mergeMap/mergeJSON.

// dnsServerFromAddress is DNSServerOptions.fromAddress: a DNS server of the setting's address, java.net.URI's reading
// of it. "local" and dhcp:// are the system's resolver.
func dnsServerFromAddress(address, tag string, detour, resolver *string) (object, error) {
	o := object{"tag": tag}
	if detour != nil {
		o["detour"] = *detour
	}
	if address == "local" || strings.HasPrefix(address, "dhcp://") {
		// the system resolver already uses the DHCP-provided servers on Android. On a computer (D-SET-18) "local" is
		// the system's DNS as well, DHCP's among it: the core asks the system's servers itself (Windows: each
		// adapter's but its TUN's; Linux: those systemd-resolved has, else resolv.conf's) through its dialer, which
		// auto_detect_interface keeps off the TUN
		o["type"] = "local"
		return o, nil
	}
	if resolver != nil {
		o["domain_resolver"] = *resolver
	}
	uriAddress := address
	if !strings.Contains(address, "://") {
		if strings.Index(address, ":") != strings.LastIndex(address, ":") && !strings.HasPrefix(address, "[") {
			uriAddress = "[" + address + "]"
		}
		uriAddress = "udp://" + uriAddress
	}
	// Java's message names the reason and the index, which the parser here does not keep
	u, err := nikofmt.ParseJavaURI(uriAddress)
	if err != nil {
		return nil, fmt.Errorf("%w: %s", err, uriAddress)
	}
	switch u.Scheme {
	case "udp", "tcp", "tls", "quic", "https", "h3":
	case "":
		return nil, errors.New("java.lang.NullPointerException: a DNS server address without a scheme")
	default:
		return nil, errors.New("Unsupported DNS transport: " + u.Scheme)
	}
	o["type"] = u.Scheme
	if u.Host == "" || u.RawUserInfo != nil || u.RawFragment != nil {
		return nil, errors.New("Invalid DNS server address")
	}
	o["server"] = strings.TrimSuffix(strings.TrimPrefix(u.Host, "["), "]")
	if u.Port > 65535 || u.Port == 0 {
		return nil, errors.New("Invalid DNS server port")
	}
	if u.Port != -1 {
		o["server_port"] = int64(u.Port)
	}
	if u.Scheme == "https" || u.Scheme == "h3" {
		path := str(u.RawPath)
		if u.RawQuery != nil {
			path += "?" + *u.RawQuery
		}
		o["path"] = path
	} else if u.RawPath != nil && *u.RawPath != "" || u.RawQuery != nil {
		return nil, errors.New("DNS transport does not support a URL path")
	}
	return o, nil
}

// The DNS tunnel's engines and default fallback resolvers when a profile specifies "auto".
const (
	dnsTunnelDNSTT       = "dnstt"
	dnsTunnelMasterDNS   = "masterdnsvpn"
	dnsTunnelSlipstream  = "slipstream"
	dnsTunnelSealOption  = "optional"
	dnsTunnelSealRequire = "required"

	// DefaultDnsttResolver matches Android RayDnsResolvers.DNSTT_DEFAULT until a network probe picks one.
	DefaultDnsttResolver = "https://common.dot.dns.yandex.net/dns-query"
	// DefaultMasterDnsResolvers matches Android RayDnsResolvers country public UDP resolvers.
	DefaultMasterDnsResolvers = "195.208.5.1\n195.208.4.1\n62.76.76.62\n77.88.8.8\n77.88.8.1"
	// DefaultSlipstreamResolvers matches Android RayDnsResolvers.slipList (first 3 non-Yandex UDP IPs).
	DefaultSlipstreamResolvers = "195.208.5.1\n195.208.4.1\n62.76.76.62"

	slipstreamMaxZone     = 134
	slipstreamMaxZoneOpen = 148
	maxSlipResolvers      = 4
)

// Java's \s and \S: \x0B is one of its spaces, not of Go's.
const javaSpace = ` \t\n\x0B\f\r`

var (
	dnsTunnelDomain   = regexp.MustCompile(`^[A-Za-z0-9.-]+$`)
	dnsttKey          = regexp.MustCompile(`^[0-9a-fA-F]{64}$`)
	dnsTunnelSealKey  = regexp.MustCompile(`^[A-Za-z0-9_-]{43}$`)
	dnsttResolver     = regexp.MustCompile(`^(https://[^` + javaSpace + `]+|(tls|tcp|udp)://[^` + javaSpace + `]+|[^` + javaSpace + `/:]+(:[0-9]+)?|\[[0-9a-fA-F:]+\](:[0-9]+)?)$`)
	masterDNSResolver = regexp.MustCompile(`^([0-9.]+(/[0-9]+)?(:[0-9]+)?|\[[0-9a-fA-F:]+\](:[0-9]+)?|[0-9a-fA-F:]+(/[0-9]+)?)$`)
	slipPin           = regexp.MustCompile(`^[A-Za-z0-9_-]{43}$`)
	slipAlpn          = regexp.MustCompile(`^[!-~]{1,255}$`)
	slipSni           = regexp.MustCompile(`^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$`)
	slipResolver      = regexp.MustCompile(`^([0-9]{1,3}(\.[0-9]{1,3}){3}(:[0-9]{1,5})?|\[[0-9a-fA-F:]+\](:[0-9]{1,5})?|[0-9a-fA-F:]*:[0-9a-fA-F:]+)$`)
)

// dnsTunnel is DnsTunnel: the tunnel a profile dials through, off where its domain is blank.
type dnsTunnel struct {
	engine, resolver, domain, key string
	method                        int
	fixedUpstream                 bool
	auth, seal, spare, tls        string
}

func dnsTunnelOf(b *nikofmt.AbstractBean) dnsTunnel {
	t := dnsTunnel{engine: str(b.DnsTunnel), resolver: str(b.DnsTunnelResolver), domain: str(b.DnsTunnelDomain),
		key: str(b.DnsTunnelKey), method: 2, fixedUpstream: flag(b.DnsTunnelFixedUpstream), auth: str(b.DnsTunnelAuth),
		seal: str(b.DnsTunnelSeal), spare: str(b.DnsTunnelSpare), tls: str(b.DnsTunnelTls)}
	if t.engine == "" {
		t.engine = dnsTunnelDNSTT
	}
	if b.DnsTunnelMethod != nil {
		t.method = *b.DnsTunnelMethod
	}
	return t
}

func (t dnsTunnel) enabled() bool { return !nikofmt.KtIsBlank(t.domain) }

// splitTrimmed splits at any of seps, each part trimmed, the empty ones left out.
func splitTrimmed(s, seps string) []string {
	var out []string
	for _, part := range strings.FieldsFunc(s, func(r rune) bool { return strings.ContainsRune(seps, r) }) {
		if part = nikofmt.KtTrim(part); part != "" {
			out = append(out, part)
		}
	}
	return out
}

func (t dnsTunnel) domains() []string    { return splitTrimmed(t.domain, ", \n") }
func (t dnsTunnel) spareZones() []string { return splitTrimmed(t.spare, ",") }

// resolvers are masterdnsvpn's, a line each: an IPv6 address in brackets goes bare.
func (t dnsTunnel) resolvers() []string {
	var out []string
	for _, line := range nikofmt.KtLines(t.resolver) {
		line = nikofmt.KtTrim(line)
		if line == "" || strings.HasPrefix(line, "#") {
			continue
		}
		if len(line) >= 2 && strings.HasPrefix(line, "[") && strings.HasSuffix(line, "]") {
			line = line[1 : len(line)-1]
		}
		out = append(out, line)
	}
	return out
}

// authNode is dnsauth's node and service ("v1;node;svc" or "v2;…"), ok false where it is off.
func (t dnsTunnel) authNode() ([2]string, bool) {
	parts := strings.Split(t.auth, ";")
	if len(parts) != 3 || parts[0] != "v1" && parts[0] != "v2" {
		return [2]string{}, false
	}
	if _, ok := nikofmt.KtToIntOrNull(parts[2]); !ok {
		return [2]string{}, false
	}
	return [2]string{parts[1], parts[2]}, true
}

// validate is "" where the tunnel is usable or off, otherwise what is wrong. slipstream is not ported.
func (t dnsTunnel) validate() string {
	if !t.enabled() {
		return ""
	}
	for _, d := range t.domains() {
		if !dnsTunnelDomain.MatchString(d) {
			return "DNS tunnel: the domain is a name, like t.example.com"
		}
	}
	for _, d := range t.spareZones() {
		if !dnsTunnelDomain.MatchString(d) {
			return "DNS tunnel: a spare zone is a name, like t2.example.com"
		}
	}
	switch t.engine {
	case dnsTunnelDNSTT:
		switch {
		case len(t.domains()) != 1:
			return "DNS tunnel: dnstt takes one domain"
		case nikofmt.KtIsBlank(t.resolver):
			return "DNS tunnel: the resolver is missing"
		case !dnsttResolver.MatchString(t.resolver):
			return "DNS tunnel: the resolver must be https://…, tls://host, tcp://host or udp://host"
		case !dnsttKey.MatchString(t.key):
			return "DNS tunnel: the public key must be 64 hex digits"
		case t.seal != "" && !dnsTunnelSealKey.MatchString(t.seal):
			return "DNS tunnel: the seal key must be 32 bytes in base64url"
		}
	case dnsTunnelMasterDNS:
		_, auth := t.authNode()
		resolvers := t.resolvers()
		badResolver := false
		for _, r := range resolvers {
			badResolver = badResolver || !masterDNSResolver.MatchString(r)
		}
		switch {
		case len(resolvers) == 0:
			return "DNS tunnel: no resolvers, one IP per line"
		case badResolver:
			return "DNS tunnel: MasterDnsVPN resolvers are IPs (or IP/prefix), with an optional :port"
		case t.method < 0 || t.method > 5:
			return "DNS tunnel: the encryption method is 0-5"
		case t.method != 0 && nikofmt.KtIsBlank(t.key):
			return "DNS tunnel: the key is missing"
		case t.seal != "" && t.seal != dnsTunnelSealOption && t.seal != dnsTunnelSealRequire:
			return "DNS tunnel: unknown seal mode " + t.seal
		case t.seal != "" && (t.method == 0 || nikofmt.KtIsBlank(t.key)):
			return "DNS tunnel: the seal needs the key"
		// a tag under a cipher that does not authenticate the query says nothing
		case auth && (t.method < 3 || t.method > 5):
			return "DNS tunnel: dnsauth needs encryption method 3-5"
		}
	case dnsTunnelSlipstream:
		domains := t.domains()
		resolvers := t.resolvers()
		maxZone := slipstreamMaxZoneOpen
		_, hasAuth := t.authNode()
		if hasAuth {
			maxZone = slipstreamMaxZone
		}
		badResolver := false
		for _, r := range resolvers {
			badResolver = badResolver || !slipResolver.MatchString(r)
		}
		switch {
		case len(domains) != 1:
			return "DNS tunnel: slipstream takes one zone"
		case len(t.spareZones()) > 0:
			return "DNS tunnel: slipstream has no spare zones"
		case len(domains[0]) > maxZone:
			return fmt.Sprintf("DNS tunnel: slipstream's zone is %d characters at most (a query must hold a packet)", maxZone)
		case len(resolvers) == 0:
			return "DNS tunnel: no resolvers, one IP per line"
		case len(resolvers) > maxSlipResolvers:
			return fmt.Sprintf("DNS tunnel: slipstream takes %d resolvers at most", maxSlipResolvers)
		case badResolver:
			return "DNS tunnel: slipstream resolvers are IP addresses, with an optional :port"
		case !slipPin.MatchString(t.key):
			return "DNS tunnel: slipstream's pin is the SHA-256 of the server's key in base64url"
		case !slipAlpn.MatchString(t.slipAlpn()):
			return "DNS tunnel: slipstream's ALPN is missing"
		case !slipSni.MatchString(t.slipSni()):
			return "DNS tunnel: slipstream's server name is a name, like cdn.example.net"
		case hasAuth && !strings.HasPrefix(t.auth, "v2;"):
			return "DNS tunnel: slipstream signs by dnsauth-v2 only"
		}
	default:
		return "DNS tunnel: unknown engine " + t.engine
	}
	return ""
}

func (t dnsTunnel) slipAlpn() string { return substringBefore(t.tls, ";") }
func (t dnsTunnel) slipSni() string  { return substringAfterOr(t.tls, ";", "") }

type slipHostPort struct {
	Host string
	Port int
}

func (t dnsTunnel) slipResolvers() []slipHostPort {
	var out []slipHostPort
	for _, line := range t.resolvers() {
		if strings.HasPrefix(line, "[") {
			host := substringBefore(strings.TrimPrefix(line, "["), "]")
			port := 53
			if p, ok := nikofmt.KtToIntOrNull(substringAfterOr(line, "]:", "")); ok && p > 0 && p <= 65535 {
				port = p
			}
			out = append(out, slipHostPort{Host: host, Port: port})
		} else if strings.Count(line, ":") > 1 {
			out = append(out, slipHostPort{Host: line, Port: 53})
		} else if strings.Contains(line, ":") {
			host := substringBefore(line, ":")
			port := 53
			if p, ok := nikofmt.KtToIntOrNull(substringAfterOr(line, ":", "")); ok && p > 0 && p <= 65535 {
				port = p
			}
			out = append(out, slipHostPort{Host: host, Port: port})
		} else {
			out = append(out, slipHostPort{Host: line, Port: 53})
		}
	}
	return out
}

func (t dnsTunnel) slipstreamArgs(localPort int, mappedResolvers []string, clockOffset int64) []string {
	args := []string{
		"--quiet",
		"--tcp-listen-host=" + localhost,
		"--tcp-listen-port=" + strconv.Itoa(localPort),
		"--domain=" + t.domains()[0],
	}
	for _, r := range mappedResolvers {
		args = append(args, "--resolver="+r)
	}
	args = append(args,
		"--pin="+t.key,
		"--alpn="+t.slipAlpn(),
		"--sni="+t.slipSni(),
	)
	if _, ok := t.authNode(); ok {
		args = append(args, "--dnsauth", "--clock-offset="+strconv.FormatInt(clockOffset, 10))
	}
	return args
}

// outboundOptions is the core outbound of the tunnel, with no dnsauth (no node's device key here).
func (t dnsTunnel) outboundOptions(protectPath string) object {
	if t.engine == dnsTunnelMasterDNS {
		o := object{"type": dnsTunnelMasterDNS, "domains": strings2any(t.domains()),
			"resolvers": strings2any(t.resolvers()), "encryption_method": int64(t.method), "mode": "socks5"}
		if !nikofmt.KtIsBlank(t.key) {
			o["encryption_key"] = t.key
		}
		if t.fixedUpstream {
			o["mode"] = "tcp"
		}
		// its own sockets must stay outside the VPN
		if protectPath != "" {
			o["protect_path"] = protectPath
		}
		if t.seal != "" {
			o["seal"] = t.seal
		}
		return o
	}
	o := object{"type": dnsTunnelDNSTT, "domain": nikofmt.KtTrim(t.domain), "public_key": nikofmt.KtLowercase(t.key),
		"resolver": nikofmt.KtTrim(t.resolver)}
	if t.seal != "" {
		o["seal"] = object{"key": t.seal}
	}
	return o
}

// knockOutboundOptions is AbstractBean.buildKnockOutboundOptions: the libcore "knock" outbound's options.
func knockOutboundOptions(bean nikofmt.Bean) (object, error) {
	base := nikofmt.Base(bean)
	steps, err := parseKnockSequence(str(base.KnockSequence))
	if err != nil {
		return nil, fmt.Errorf("Port knocking (%s): %s", nikofmt.DisplayName(bean), err)
	}
	orDefault := func(n *int, def int) int {
		if n == nil {
			return def
		}
		return *n
	}
	return object{
		"sequence": steps,
		"interval": strconv.Itoa(coerceIn(orDefault(base.KnockInterval, 200), 10, 10_000)) + "ms",
		"ttl":      strconv.Itoa(coerceIn(orDefault(base.KnockTtl, 10), 1, 3600)) + "s",
		"silence":  strconv.Itoa(coerceIn(orDefault(base.KnockSilence, 15), 0, 3600)) + "s",
	}, nil
}

// parseKnockSequence is parseKnockSequence: steps like "7000, 8000/udp, 1234/udp:68656c6c6f, 62201/spa:<key>", as
// the knock outbound's sequence.
func parseKnockSequence(sequence string) ([]any, error) {
	items := splitTrimmed(sequence, ", \n\t")
	if len(items) > 32 {
		return nil, errors.New("too many steps (max 32)")
	}
	steps := []any{}
	for _, item := range items {
		payload := substringAfterOr(item, ":", "")
		target := substringBefore(item, ":")
		port, ok := nikofmt.KtToIntOrNull(substringBefore(target, "/"))
		if !ok || port < 1 || port > 65535 {
			return nil, errors.New("invalid port: " + item)
		}
		network := nikofmt.KtLowercase(substringAfterOr(target, "/", "tcp"))
		step := object{"port": int64(port)}
		if network == "spa" || network == "spa1m" {
			if utf16Len(payload) != 64 || !ktHexDigits(payload) {
				return nil, fmt.Errorf("spa needs a 64 hex digit key: %d/%s", port, network)
			}
			step["network"] = network
			step["payload"] = nikofmt.KtLowercase(payload)
			steps = append(steps, step)
			continue
		}
		var udp bool
		switch network {
		case "tcp":
		case "udp":
			udp = true
		default:
			return nil, errors.New("unknown network: " + item)
		}
		if payload != "" {
			switch {
			case !udp:
				return nil, errors.New("payload is only supported for udp: " + item)
			case utf16Len(payload)%2 != 0 || !ktHexDigits(payload):
				return nil, errors.New("payload must be hex: " + item)
			case utf16Len(payload)/2 > 1024:
				return nil, errors.New("payload too large: " + item)
			}
			step["payload"] = nikofmt.KtLowercase(payload)
		}
		step["network"] = network
		steps = append(steps, step)
	}
	return steps, nil
}

// ktHexDigits: each char (UTF-16) a digit (Kotlin's isDigit, any script's) or a to f in either case.
func ktHexDigits(s string) bool {
	for _, c := range utf16.Encode([]rune(s)) {
		r := rune(c)
		if utf16.IsSurrogate(r) || !unicode.IsDigit(r) && !(r >= 'a' && r <= 'f' || r >= 'A' && r <= 'F') {
			return false
		}
	}
	return true
}

func utf16Len(s string) int { return len(utf16.Encode([]rune(s))) }

// parseCertificatePins is parseCertificatePins (nikofmt.ParseCertificatePins), as sing-box takes the pins.
func parseCertificatePins(text string) ([]any, error) {
	pins, err := nikofmt.ParseCertificatePins(text)
	if err != nil {
		return nil, err
	}
	out := []any{}
	for _, pin := range pins {
		out = append(out, pin)
	}
	return out, nil
}

// meshPorts is MeshPorts: the ports other Yggdrasil nodes may reach on this device.
type meshPorts struct{ ports, ranges []any }

// parseMeshPorts is MeshPorts.parse: single ports and ranges ("22, 8000-8080"); nil where the text names no valid
// port.
func parseMeshPorts(text string) *meshPorts {
	m := &meshPorts{}
	seenPort, seenRange := map[int]bool{}, map[string]bool{}
	for _, part := range splitTrimmed(text, ", \n;") {
		// split at '-' and ':', the empty parts kept
		bounds := strings.Split(strings.ReplaceAll(part, ":", "-"), "-")
		switch len(bounds) {
		case 1:
			if p, ok := nikofmt.KtToIntOrNull(bounds[0]); ok && p >= 1 && p <= 65535 && !seenPort[p] {
				seenPort[p] = true
				m.ports = append(m.ports, int64(p))
			}
		case 2:
			from, ok1 := nikofmt.KtToIntOrNull(bounds[0])
			to, ok2 := nikofmt.KtToIntOrNull(bounds[1])
			if !ok1 || !ok2 {
				continue
			}
			if r := fmt.Sprintf("%d:%d", from, to); from >= 1 && from <= to && to <= 65535 && !seenRange[r] {
				seenRange[r] = true
				m.ranges = append(m.ranges, r)
			}
		}
	}
	if len(m.ports) == 0 && len(m.ranges) == 0 {
		return nil
	}
	return m
}

// put sets the rule's ports and port ranges, those it has.
func (m *meshPorts) put(rule object) {
	if len(m.ports) > 0 {
		rule["port"] = m.ports
	}
	if len(m.ranges) > 0 {
		rule["port_range"] = m.ranges
	}
}

// isYggdrasilAddress is String.isYggdrasilAddress: a literal address in 200::/7.
func isYggdrasilAddress(s string) bool {
	host := strings.TrimSuffix(strings.TrimPrefix(nikofmt.KtTrim(s), "["), "]")
	if !strings.Contains(host, ":") {
		return false
	}
	first := substringBefore(host, ":")
	if first == "" {
		return false
	}
	value, ok := nikofmt.JavaParseLong(first, 16)
	// Kotlin's toIntOrNull(16): within an Int
	return ok && value >= math.MinInt32 && value <= math.MaxInt32 && value >= 0x200 && value <= 0x3ff &&
		nikofmt.IsIPAddress(host)
}

// mergeMap is Util.mergeMap: src's members over dst's, an object into an object member by member; a list under
// "+name" goes before dst's name, under "name+" after it. Members go in Go's order, not the JSON's: only names that
// meet in one dst member ("x" and "+x") could tell.
func mergeMap(dst, src object) object {
	for k, v := range src {
		if vm, ok := v.(object); ok {
			if dm, ok := dst[k].(object); ok {
				dst[k] = mergeMap(dm, vm)
				continue
			}
		}
		list, isList := v.([]any)
		switch {
		case isList && strings.HasPrefix(k, "+"):
			key := strings.TrimPrefix(k, "+")
			current, _ := dst[key].([]any)
			dst[key] = append(append([]any{}, list...), current...)
		case isList && strings.HasSuffix(k, "+"):
			key := strings.TrimSuffix(k, "+")
			current, _ := dst[key].([]any)
			dst[key] = append(append([]any{}, current...), list...)
		default:
			dst[k] = v
		}
	}
	return dst
}

// mergeJSON is Util.mergeJSON: a JSON object's members over dst's (nothing for a blank text), read by the app's
// lenient Gson.
func mergeJSON(dst object, text string) error {
	if nikofmt.KtIsBlank(text) {
		return nil
	}
	src, err := nikofmt.GsonMap(text)
	if err != nil {
		return fmt.Errorf("custom JSON: %w", err)
	}
	if src == nil {
		// a null passed for Kotlin's not null Map
		return errors.New("Parameter specified as non-null is null: method io.niko.utils.Util.mergeMap, parameter src")
	}
	mergeMap(dst, src)
	return nil
}

// deepCopy copies the objects and lists of a value.
func deepCopy(v any) any {
	switch v := v.(type) {
	case object:
		c := make(object, len(v))
		for k, e := range v {
			c[k] = deepCopy(e)
		}
		return c
	case []any:
		c := make([]any, len(v))
		for i, e := range v {
			c[i] = deepCopy(e)
		}
		return c
	}
	return v
}

// javaToString is a Gson value's toString (Long, Double, Boolean, String; a map's or list's roughly).
func javaToString(v any) string {
	switch v := v.(type) {
	case string:
		return v
	case int64:
		return strconv.FormatInt(v, 10)
	case float64:
		return javaDoubleString(v)
	case bool:
		return strconv.FormatBool(v)
	case nil:
		return "null"
	}
	return fmt.Sprint(v)
}

// javaDoubleString is Double.toString (nikofmt.JavaDoubleString).
func javaDoubleString(f float64) string { return nikofmt.JavaDoubleString(f) }

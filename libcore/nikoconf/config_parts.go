package nikoconf

import (
	"encoding/base64"
	"encoding/hex"
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

// The DNS tunnel's engines.
const (
	dnsTunnelDNSTT       = "dnstt"
	dnsTunnelMasterDNS   = "masterdnsvpn"
	dnsTunnelSlipstream  = "slipstream"
	dnsTunnelSealOption  = "optional"
	dnsTunnelSealRequire = "required"
)

// Java's \s and \S: \x0B is one of its spaces, not of Go's.
const javaSpace = ` \t\n\x0B\f\r`

var (
	dnsTunnelDomain   = regexp.MustCompile(`^[A-Za-z0-9.-]+$`)
	dnsttKey          = regexp.MustCompile(`^[0-9a-fA-F]{64}$`)
	dnsTunnelSealKey  = regexp.MustCompile(`^[A-Za-z0-9_-]{43}$`)
	dnsttResolver     = regexp.MustCompile(`^(https://[^` + javaSpace + `]+|(tls|tcp|udp)://[^` + javaSpace + `]+|[^` + javaSpace + `/:]+(:[0-9]+)?|\[[0-9a-fA-F:]+\](:[0-9]+)?)$`)
	masterDNSResolver = regexp.MustCompile(`^([0-9.]+(/[0-9]+)?(:[0-9]+)?|\[[0-9a-fA-F:]+\](:[0-9]+)?|[0-9a-fA-F:]+(/[0-9]+)?)$`)
)

// dnsTunnel is DnsTunnel: the tunnel a profile dials through, off where its domain is blank.
type dnsTunnel struct {
	engine, resolver, domain, key string
	method                        int
	fixedUpstream                 bool
	auth, seal, spare             string
}

func dnsTunnelOf(b *nikofmt.AbstractBean) dnsTunnel {
	t := dnsTunnel{engine: str(b.DnsTunnel), resolver: str(b.DnsTunnelResolver), domain: str(b.DnsTunnelDomain),
		key: str(b.DnsTunnelKey), method: 2, fixedUpstream: flag(b.DnsTunnelFixedUpstream), auth: str(b.DnsTunnelAuth),
		seal: str(b.DnsTunnelSeal), spare: str(b.DnsTunnelSpare)}
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
		// checked when its build fails: a client process of its own, not ported
	default:
		return "DNS tunnel: unknown engine " + t.engine
	}
	return ""
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

// parseCertificatePins is parseCertificatePins: SHA-256 hashes of the server's public key, as base64 or hex (colons
// allowed), one per line or comma separated; base64 for sing-box.
func parseCertificatePins(text string) ([]any, error) {
	pins := []any{}
	for _, pin := range splitTrimmed(text, ",\n \t") {
		h := strings.ReplaceAll(pin, ":", "")
		var raw []byte
		if utf16Len(h) == 64 && ktHexDigits(h) {
			// okio's decodeHex: another script's digit passes the check above, then fails here
			for _, r := range h {
				if r > unicode.MaxASCII {
					return nil, fmt.Errorf("Unexpected hex digit: %c", r)
				}
			}
			raw, _ = hex.DecodeString(h)
		} else {
			raw = okioDecodeBase64(pin)
		}
		if len(raw) != 32 {
			return nil, errors.New("not a SHA-256 hash: " + pin)
		}
		pins = append(pins, base64.StdEncoding.EncodeToString(raw))
	}
	return pins, nil
}

// okioDecodeBase64 is okio's decodeBase64: standard or URL-safe letters, padding and whitespace at the end left out,
// whitespace inside skipped; nil where okio gives null.
func okioDecodeBase64(s string) []byte {
	chars := utf16.Encode([]rune(s))
	limit := len(chars)
	for limit > 0 {
		if c := chars[limit-1]; c != '=' && c != '\n' && c != '\r' && c != ' ' && c != '\t' {
			break
		}
		limit--
	}
	var out []byte
	word, count := 0, 0
	for _, c := range chars[:limit] {
		var bits int
		switch {
		case c >= 'A' && c <= 'Z':
			bits = int(c) - 65
		case c >= 'a' && c <= 'z':
			bits = int(c) - 71
		case c >= '0' && c <= '9':
			bits = int(c) + 4
		case c == '+' || c == '-':
			bits = 62
		case c == '/' || c == '_':
			bits = 63
		case c == '\n' || c == '\r' || c == ' ' || c == '\t':
			continue
		default:
			return nil
		}
		word = word<<6 | bits
		if count++; count%4 == 0 {
			out = append(out, byte(word>>16), byte(word>>8), byte(word))
		}
	}
	switch count % 4 {
	case 1:
		return nil // 6 bits are a truncated byte
	case 2:
		word <<= 12
		out = append(out, byte(word>>16))
	case 3:
		word <<= 6
		out = append(out, byte(word>>16), byte(word>>8))
	}
	return out
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

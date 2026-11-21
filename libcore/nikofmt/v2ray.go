package nikofmt

import (
	"errors"
	"regexp"
	"strings"
)

// XHTTPModes are the modes the core's XHTTP client takes (fmt/v2ray/XHTTP.kt XHTTP_MODES).
var XHTTPModes = []string{"auto", "packet-up", "stream-up", "stream-one"}

// v2rayKind is which StandardV2RayBean parseDuckSoft fills: its username is a Trojan password, else a UUID; VLESS
// (a VMessBean whose alterId is -1) takes flow and the post-quantum encryption, VMess its security.
type v2rayKind int

const (
	kindVMess v2rayKind = iota
	kindVLESS
	kindTrojan
)

// parseV2RayStandard is V2RayFmt.kt parseV2RayStandard: the "std" forms of a vmess:// or vless:// link, v2fly's (a
// password in the user info) and Xray's share link (parseDuckSoft). The defaults are not applied (the app applies
// them after the parser, as parseProxies does).
func parseV2RayStandard(link string, settings Settings) (*VMessBean, error) {
	b := &VMessBean{}
	kind := kindVMess
	if strings.HasPrefix(link, "vless://") {
		b.AlterId = ptr(-1)
		kind = kindVLESS
	}
	// every occurrence, as Kotlin's replace does: a "vmess://" or "vless://" in the name or a parameter changes too
	u, err := parseOkURL(strings.ReplaceAll(strings.ReplaceAll(link, "vmess://", "https://"), "vless://", "https://"))
	if err != nil {
		return nil, err
	}
	if !ktIsBlank(u.password) {
		err = b.parseV2fly(u)
	} else {
		err = b.parseDuckSoft(u, settings, kind, &b.Uuid)
	}
	if err != nil {
		return nil, err
	}
	return b, nil
}

// parseV2fly is parseV2Ray's branch for v2fly's form (github.com/v2fly/v2fly-github-io/issues/26), a VMess form:
// "<transport>[+tls]:<uuid>-<alterId>@host". Taken as it is for a vless:// link too, so the alterId of the link
// replaces VLESS's -1, and the type keeps "+tls".
func (b *VMessBean) parseV2fly(u *okURL) error {
	b.ServerAddress = ptr(u.host)
	b.ServerPort = ptr(u.port)
	b.Name = u.fragment
	protocol := u.username
	b.Type = ptr(protocol)
	alterID, ok := javaParseInt(u.password[strings.LastIndexByte(u.password, '-')+1:], 10)
	if !ok {
		return errors.New("v2fly link: the alterId is not a number")
	}
	b.AlterId = ptr(int(alterID))
	b.Uuid = ptr(substringBeforeLast(u.password, "-"))
	if strings.HasSuffix(protocol, "+tls") {
		b.Security = ptr("tls")
		protocol = strings.TrimSuffix(protocol, "+tls")
		if v := u.queryParameter("tlsServerName"); v != nil && !ktIsBlank(*v) {
			b.Sni = v
		}
	}
	switch protocol {
	case "http":
		if v := u.queryParameter("path"); v != nil {
			b.Path = v
		}
		if v := u.queryParameter("host"); v != nil {
			b.Host = ptr(strings.ReplaceAll(*v, "|", ","))
		}
	case "ws", "httpupgrade":
		if v := u.queryParameter("path"); v != nil {
			b.Path = v
		}
		if v := u.queryParameter("host"); v != nil {
			b.Host = v
		}
	case "grpc":
		if v := u.queryParameter("serviceName"); v != nil {
			b.Path = v
		}
	}
	return nil
}

// substringBeforeLast is Kotlin's: the part before the last delimiter, or all of s without one.
func substringBeforeLast(s, delimiter string) string {
	if i := strings.LastIndex(s, delimiter); i >= 0 {
		return s[:i]
	}
	return s
}

// parseDuckSoft is V2RayFmt.kt StandardV2RayBean.parseDuckSoft (Xray's share link, Xray-core issue 91): the URL's
// username goes to user, a Trojan password or a UUID.
func (b *StandardV2RayBean) parseDuckSoft(u *okURL, settings Settings, kind v2rayKind, user **string) error {
	param := u.queryParameter
	b.ServerAddress = ptr(u.host)
	b.ServerPort = ptr(u.port)
	b.Name = u.fragment
	*user = ptr(u.username)

	// a path in the URL (not Xray's form): its segments, decoded, joined without the leading slash
	if len(u.pathSegments) > 1 || !ktIsBlank(u.pathSegments[0]) {
		b.Path = ptr(strings.Join(u.pathSegments, "/"))
	}

	typ := "tcp"
	if t := param("type"); t != nil {
		typ = *t
	}
	if typ == "raw" { // Xray's name for plain TCP
		typ = "tcp"
	}
	if typ == "h2" || equals(param("headerType"), "http") {
		typ = "http"
	}
	if typ == "splithttp" { // the old Xray name
		typ = "xhttp"
	}
	b.Type = &typ

	b.Security = param("security")
	if b.Security == nil || ktIsBlank(*b.Security) {
		if kind == kindTrojan {
			b.Security = ptr("tls")
		} else {
			b.Security = ptr("none")
		}
	}
	switch *b.Security {
	case "tls", "reality":
		b.Security = ptr("tls")
		if v := param("allowInsecure"); v != nil {
			b.AllowInsecure = ptr(*v == "1" || *v == "true")
		}
		if v := param("insecure"); v != nil && (*v == "1" || *v == "true") {
			b.AllowInsecure = ptr(true)
		}
		// REALITY and ECH exclude each other in sing-box
		if v := param("ech"); v != nil && !ktIsBlank(*v) && !equals(param("security"), "reality") {
			b.applyEchParameter(*v)
		}
		if v := param("sni"); v != nil {
			b.Sni = v
		}
		if v := param("host"); v != nil && (b.Sni == nil || ktIsBlank(*b.Sni)) {
			b.Sni = v
		}
		if v := param("alpn"); v != nil {
			b.Alpn = v
		}
		if v := param("cert"); v != nil {
			b.Certificates = v
		}
		if v := param("pbk"); v != nil {
			b.RealityPubKey = v
		}
		if v := param("sid"); v != nil {
			b.RealityShortId = v
		}
		// Xray share links: pqv = mldsa65Verify
		if v := param("pqv"); v != nil {
			b.RealityMldsa65Verify = v
		}
	}

	switch typ {
	case "http", "httpupgrade":
		if v := param("host"); v != nil {
			b.Host = v
		}
		if v := param("path"); v != nil {
			b.Path = v
		}
	case "ws":
		if v := param("host"); v != nil {
			b.Host = v
		}
		if v := param("path"); v != nil {
			b.Path = v
		}
		if ed := param("ed"); ed != nil {
			v, ok := javaParseInt(*ed, 10)
			if !ok {
				return errors.New("ed: not a number") // Kotlin's toInt() throws
			}
			b.WsMaxEarlyData = ptr(int(v))
			if eh := param("eh"); eh != nil {
				b.EarlyDataHeaderName = eh
			}
		}
	case "grpc":
		if v := param("serviceName"); v != nil {
			b.Path = v
		}
	case "xhttp":
		if v := param("host"); v != nil {
			b.Host = v
		}
		if v := param("path"); v != nil {
			b.Path = v
		}
		if v := param("mode"); v != nil && contains(XHTTPModes, *v) {
			b.XhttpMode = v
		}
		if v := param("extra"); v != nil && !ktIsBlank(*v) {
			b.XhttpExtra = v
		}
	}

	// maybe from matsuri's VMess export
	if kind == kindVMess {
		if v := param("encryption"); v != nil {
			b.Encryption = v
		}
	}
	if v := param("packetEncoding"); v != nil {
		switch *v {
		case "packet":
			b.PacketEncoding = ptr(1)
		case "xudp":
			b.PacketEncoding = ptr(2)
		}
	}
	if v := param("flow"); v != nil && kind == kindVLESS {
		b.Encryption = ptr(strings.TrimSuffix(*v, "-udp443"))
	}
	// VLESS post-quantum encryption: Xray links carry its spec in "encryption", "none" = off
	if kind == kindVLESS {
		if v := param("encryption"); v != nil {
			if e := ktTrim(*v); e != "" && e != "none" {
				b.VlessEncryption = &e
			}
		}
	}
	if v := param("fp"); v != nil {
		b.UtlsFingerprint = v
	}
	// raycontrol links (subscription-v1.md §4): DNS tunnel, certificate pin, Yggdrasil
	return applyNikoParams(&b.AbstractBean, settings, param, func(name string) []*string {
		var out []*string
		for _, v := range u.queryParameterValues(name) {
			if v != nil {
				out = append(out, v)
			}
		}
		return out
	})
}

const (
	echPEMBegin = "-----BEGIN ECH CONFIGS-----"
	echPEMEnd   = "-----END ECH CONFIGS-----"
)

var echConfigList = regexp.MustCompile(`^[A-Za-z0-9+/=_-]{16,}$`)

// applyEchParameter is V2RayFmt.kt applyEchParameter: Xray's "ech", a base64 ECHConfigList (kept as PEM) or
// "<domain>+<DNS server>" to look it up, which is not carried.
func (b *StandardV2RayBean) applyEchParameter(value string) {
	config := strings.ReplaceAll(value, " ", "+") // an unescaped "+" in a query arrives as a space
	if strings.Contains(config, ".") || !echConfigList.MatchString(config) {
		return
	}
	base64 := strings.TrimRight(strings.NewReplacer("-", "+", "_", "/").Replace(config), "=")
	b.EnableECH = ptr(true)
	b.EchConfig = ptr(echPEMBegin + "\n" + base64 + strings.Repeat("=", (4-len(base64)%4)%4) + "\n" + echPEMEnd)
}

func contains(list []string, s string) bool {
	for _, v := range list {
		if v == s {
			return true
		}
	}
	return false
}

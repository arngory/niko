package nikofmt

import (
	"encoding/base64"
	"errors"
	"strconv"
	"strings"
)

// ShareLink is ProxyEntity.toStdLink: the link the app shares for a profile ("share" copies it or shows it as a QR
// code). SOCKS, HTTP, Shadowsocks, VMess, VLESS, Trojan, Trojan-Go, naive, Hysteria, TUIC and AnyTLS have links of
// their own (each type's toUri); the rest an sn:// one, the bean in Kryo (toUniversalLink). shared/golden/links
// holds what the app writes for each golden bean (<name>.share); share_test.go checks against it.
func ShareLink(b Bean) (string, error) {
	switch b := b.(type) {
	case *SOCKSBean:
		return b.shareLink()
	case *HttpBean:
		return b.shareLink()
	case *ShadowsocksBean:
		return b.shareLink()
	case *VMessBean:
		if !b.IsVLESS() {
			return b.v2rayN(), nil
		}
		return b.StandardV2RayBean.ducksoftLink(b.Uuid, true, false)
	case *TrojanBean:
		return b.StandardV2RayBean.ducksoftLink(b.Password, false, true)
	case *TrojanGoBean:
		return b.shareLink()
	case *NaiveBean:
		return b.shareLink()
	case *HysteriaBean:
		return b.shareLink()
	case *TuicBean:
		return b.shareLink()
	case *AnyTLSBean:
		return b.shareLink()
	}
	return UniversalLink(b)
}

func str(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

func num(n *int) int {
	if n == nil {
		return 0
	}
	return *n
}

func flag(b *bool) bool { return b != nil && *b }

// fragment: the profile's name as the link's fragment, where it is not blank (name.urlSafe()).
func (b *okBuilder) name(base *AbstractBean) *okBuilder {
	if name := str(base.Name); !ktIsBlank(name) {
		b.encodedFragment(urlSafe(name))
	}
	return b
}

func (b *SOCKSBean) shareLink() (string, error) {
	u := newOkBuilder("http").hostname(str(b.ServerAddress)).setPort(num(b.ServerPort))
	if !ktIsBlank(str(b.Username)) {
		u.username(*b.Username)
	}
	if !ktIsBlank(str(b.Password)) {
		u.password(*b.Password)
	}
	u.name(&b.AbstractBean)
	version := "5"
	if p := num(b.Protocol); p == ProtocolSOCKS4 || p == ProtocolSOCKS4A {
		version = "4"
	}
	return u.toLink("socks"+version, true)
}

func (b *HttpBean) shareLink() (string, error) {
	scheme := "http"
	if str(b.Security) == "tls" {
		scheme = "https"
	}
	u := newOkBuilder(scheme).hostname(str(b.ServerAddress))
	if p := num(b.ServerPort); p >= 1 && p <= 65535 {
		u.setPort(p)
	}
	if !ktIsBlank(str(b.Username)) {
		u.username(*b.Username)
	}
	if !ktIsBlank(str(b.Password)) {
		u.password(*b.Password)
	}
	if !ktIsBlank(str(b.Sni)) {
		u.addQueryParameter("sni", *b.Sni)
	}
	u.name(&b.AbstractBean)
	return u.build()
}

func (b *ShadowsocksBean) shareLink() (string, error) {
	user := base64.RawURLEncoding.EncodeToString([]byte(str(b.Method) + ":" + str(b.Password)))
	u := linkBuilder().username(user).hostname(str(b.ServerAddress)).setPort(num(b.ServerPort))
	if !ktIsBlank(str(b.Plugin)) {
		u.addQueryParameter("plugin", *b.Plugin)
	}
	u.name(&b.AbstractBean)
	link, err := u.toLink("ss", true)
	if err != nil {
		return "", err
	}
	port := strconv.Itoa(num(b.ServerPort))
	return strings.ReplaceAll(link, port+"/", port), nil
}

func (b *TrojanGoBean) shareLink() (string, error) {
	u := linkBuilder().username(str(b.Password)).hostname(str(b.ServerAddress)).setPort(num(b.ServerPort))
	if !ktIsBlank(str(b.Sni)) {
		u.addQueryParameter("sni", *b.Sni)
	}
	typ := str(b.Type)
	if !ktIsBlank(typ) && typ != "original" {
		u.addQueryParameter("type", typ)
		if typ == "ws" {
			if !ktIsBlank(str(b.Host)) {
				u.addQueryParameter("host", *b.Host)
			}
			if !ktIsBlank(str(b.Path)) {
				u.addQueryParameter("path", *b.Path)
			}
		}
	}
	if !ktIsBlank(typ) && typ != "none" {
		u.addQueryParameter("encryption", str(b.Encryption))
	}
	if !ktIsBlank(str(b.Plugin)) {
		u.addQueryParameter("plugin", *b.Plugin)
	}
	u.name(&b.AbstractBean)
	return u.toLink("trojan-go", true)
}

// shareLink is NaiveBean.toUri(): finalAddress and finalPort are the server's for a bean not yet in a config.
func (b *NaiveBean) shareLink() (string, error) {
	u := linkBuilder().hostname(str(b.ServerAddress)).setPort(num(b.ServerPort))
	if !ktIsBlank(str(b.Username)) {
		u.username(*b.Username)
		if !ktIsBlank(str(b.Password)) {
			u.password(*b.Password)
		}
	}
	if !ktIsBlank(str(b.Sni)) {
		u.addQueryParameter("sni", *b.Sni)
	}
	if !ktIsBlank(str(b.Certificates)) {
		u.addQueryParameter("cert", *b.Certificates)
	}
	if !ktIsBlank(str(b.ExtraHeaders)) {
		u.addQueryParameter("extra-headers", *b.ExtraHeaders)
	}
	u.name(&b.AbstractBean)
	if c := num(b.InsecureConcurrency); c > 0 {
		u.addQueryParameter("insecure-concurrency", strconv.Itoa(c))
	}
	if flag(b.SUoT) {
		u.addQueryParameter("uot", "2")
	}
	if flag(b.NikoUdp) {
		u.addQueryParameter("udp", "1")
	}
	return u.toLink("naive+"+str(b.Proto), false)
}

// firstPort is HysteriaFmt.kt getFirstPort: the ports up to the first ':' or ',' as Kotlin's toIntOrNull reads it,
// else 443.
func firstPort(ports string) int {
	if i := strings.IndexByte(ports, ':'); i >= 0 {
		ports = ports[:i]
	}
	if i := strings.IndexByte(ports, ','); i >= 0 {
		ports = ports[:i]
	}
	if n, ok := ktToIntOrNull(ports); ok {
		return n
	}
	return 443
}

// ktToIntOrNull is Kotlin's String.toIntOrNull: an optional sign and decimal digits, within an Int.
func ktToIntOrNull(s string) (int, bool) {
	digits := strings.TrimPrefix(strings.TrimPrefix(s, "+"), "-")
	if digits == "" || len(s)-len(digits) > 1 {
		return 0, false
	}
	for i := 0; i < len(digits); i++ {
		if digits[i] < '0' || digits[i] > '9' {
			return 0, false
		}
	}
	n, err := strconv.ParseInt(s, 10, 32)
	if err != nil {
		return 0, false
	}
	return int(n), true
}

func (b *HysteriaBean) shareLink() (string, error) {
	version := num(b.ProtocolVersion)
	var user, pass string
	if version == 2 {
		auth := str(b.AuthPayload)
		if i := strings.IndexByte(auth, ':'); i >= 0 {
			user, pass = auth[:i], auth[i+1:]
		} else {
			user = auth
		}
	}
	u := linkBuilder().hostname(str(b.ServerAddress)).setPort(firstPort(str(b.ServerPorts))).username(user).password(pass)
	if isMultiPort(DisplayAddress(b)) {
		u.addQueryParameter("mport", str(b.ServerPorts))
	}
	u.name(&b.AbstractBean)
	if flag(b.AllowInsecure) {
		u.addQueryParameter("insecure", "1")
	}
	if version == 1 {
		if !ktIsBlank(str(b.Sni)) {
			u.addQueryParameter("peer", *b.Sni)
		}
		if !ktIsBlank(str(b.AuthPayload)) {
			u.addQueryParameter("auth", *b.AuthPayload)
		}
		u.addQueryParameter("upmbps", strconv.Itoa(num(b.UploadMbps)))
		u.addQueryParameter("downmbps", strconv.Itoa(num(b.DownloadMbps)))
		if !ktIsBlank(str(b.Alpn)) {
			u.addQueryParameter("alpn", *b.Alpn)
		}
		if !ktIsBlank(str(b.Obfuscation)) {
			u.addQueryParameter("obfs", "xplus")
			u.addQueryParameter("obfsParam", *b.Obfuscation)
		}
		switch num(b.Protocol) {
		case hysteriaProtocolFakeTCP:
			u.addQueryParameter("protocol", "faketcp")
		case hysteriaProtocolWeChatVideo:
			u.addQueryParameter("protocol", "wechat-video")
		}
	} else {
		if !ktIsBlank(str(b.Sni)) {
			u.addQueryParameter("sni", *b.Sni)
		}
		if !ktIsBlank(str(b.Obfuscation)) {
			u.addQueryParameter("obfs", "salamander")
			u.addQueryParameter("obfs-password", *b.Obfuscation)
		}
	}
	scheme := "hysteria"
	if version == 2 {
		scheme = "hy2"
	}
	return u.toLink(scheme, true)
}

func (b *TuicBean) shareLink() (string, error) {
	u := linkBuilder().username(str(b.Uuid)).password(str(b.Token)).hostname(str(b.ServerAddress)).setPort(num(b.ServerPort))
	u.addQueryParameter("congestion_control", str(b.CongestionController))
	u.addQueryParameter("udp_relay_mode", str(b.UdpRelayMode))
	if !ktIsBlank(str(b.Sni)) {
		u.addQueryParameter("sni", *b.Sni)
	}
	if !ktIsBlank(str(b.Alpn)) {
		u.addQueryParameter("alpn", *b.Alpn)
	}
	if flag(b.AllowInsecure) {
		u.addQueryParameter("allow_insecure", "1")
	}
	if flag(b.DisableSNI) {
		u.addQueryParameter("disable_sni", "1")
	}
	u.name(&b.AbstractBean)
	return u.toLink("tuic", true)
}

func (b *AnyTLSBean) shareLink() (string, error) {
	u := linkBuilder().hostname(str(b.ServerAddress)).setPort(num(b.ServerPort)).username(str(b.Password))
	u.name(&b.AbstractBean)
	if flag(b.AllowInsecure) {
		u.addQueryParameter("insecure", "1")
	}
	if !ktIsBlank(str(b.Sni)) {
		u.addQueryParameter("sni", *b.Sni)
	}
	if !ktIsBlank(str(b.UtlsFingerprint)) {
		u.addQueryParameter("fp", *b.UtlsFingerprint)
	}
	return u.toLink("anytls", true)
}

// ducksoftLink is toUriVMessVLESSTrojan for VLESS and Trojan: the user is the UUID or the password.
func (b *StandardV2RayBean) ducksoftLink(user *string, vless, trojan bool) (string, error) {
	u := linkBuilder().username(str(user)).hostname(str(b.ServerAddress)).setPort(num(b.ServerPort))
	typ := str(b.Type)
	u.addQueryParameter("type", typ)
	tls := str(b.Security) == "tls"
	if vless {
		encryption := str(b.VlessEncryption)
		if ktIsBlank(encryption) {
			encryption = "none"
		}
		u.addQueryParameter("encryption", encryption)
		if str(b.Encryption) != "auto" {
			u.addQueryParameter("flow", str(b.Encryption))
		}
	}
	switch typ {
	case "ws", "http", "httpupgrade":
		if !ktIsBlank(str(b.Host)) {
			u.addQueryParameter("host", *b.Host)
		}
		if !ktIsBlank(str(b.Path)) {
			u.addQueryParameter("path", *b.Path)
		}
		if typ == "ws" {
			if ed := num(b.WsMaxEarlyData); ed > 0 {
				u.addQueryParameter("ed", strconv.Itoa(ed))
				if !ktIsBlank(str(b.EarlyDataHeaderName)) {
					u.addQueryParameter("eh", *b.EarlyDataHeaderName)
				}
			}
		} else if typ == "http" && !tls {
			u.setQueryParameter("type", "tcp")
			u.addQueryParameter("headerType", "http")
		}
	case "grpc":
		if !ktIsBlank(str(b.Path)) {
			u.setQueryParameter("serviceName", *b.Path)
		}
	case "xhttp":
		if !ktIsBlank(str(b.Host)) {
			u.addQueryParameter("host", *b.Host)
		}
		if !ktIsBlank(str(b.Path)) {
			u.addQueryParameter("path", *b.Path)
		}
		if !ktIsBlank(str(b.XhttpMode)) {
			u.addQueryParameter("mode", *b.XhttpMode)
		}
		if !ktIsBlank(str(b.XhttpExtra)) {
			u.addQueryParameter("extra", *b.XhttpExtra)
		}
	}
	if security := str(b.Security); !ktIsBlank(security) && security != "none" {
		u.addQueryParameter("security", security)
		if security == "tls" {
			if !ktIsBlank(str(b.Sni)) {
				u.addQueryParameter("sni", *b.Sni)
			}
			if !ktIsBlank(str(b.Alpn)) {
				u.addQueryParameter("alpn", strings.ReplaceAll(*b.Alpn, "\n", ","))
			}
			if !ktIsBlank(str(b.Certificates)) {
				u.addQueryParameter("cert", *b.Certificates)
			}
			if flag(b.AllowInsecure) {
				u.addQueryParameter("allowInsecure", "1")
			}
			if !ktIsBlank(str(b.UtlsFingerprint)) {
				u.addQueryParameter("fp", *b.UtlsFingerprint)
			}
			if ech, ok := b.echShareParameter(); ok {
				u.addQueryParameter("ech", ech)
			}
			if !ktIsBlank(str(b.RealityPubKey)) {
				u.setQueryParameter("security", "reality")
				u.addQueryParameter("pbk", *b.RealityPubKey)
				u.addQueryParameter("sid", str(b.RealityShortId))
				if !ktIsBlank(str(b.RealityMldsa65Verify)) {
					u.addQueryParameter("pqv", *b.RealityMldsa65Verify)
				}
			}
		}
	}
	switch num(b.PacketEncoding) {
	case 1:
		u.addQueryParameter("packetEncoding", "packetaddr")
	case 2:
		u.addQueryParameter("packetEncoding", "xudp")
	}
	u.name(&b.AbstractBean)
	scheme := "vless"
	if trojan {
		scheme = "trojan"
	}
	return u.toLink(scheme, true)
}

// echShareParameter is StandardV2RayBean.echShareParameter: the base64 inside the ECH PEM, its lines trimmed and
// joined, where ECH is on.
func (b *StandardV2RayBean) echShareParameter() (string, bool) {
	config := str(b.EchConfig)
	if !flag(b.EnableECH) || b.EchConfig == nil || !strings.Contains(config, echPEMBegin) {
		return "", false
	}
	inner := config[strings.Index(config, echPEMBegin)+len(echPEMBegin):]
	if i := strings.Index(inner, echPEMEnd); i >= 0 {
		inner = inner[:i]
	}
	var joined strings.Builder
	for _, line := range ktLines(inner) {
		joined.WriteString(strings.TrimFunc(line, ktIsWhitespace))
	}
	if joined.Len() == 0 {
		return "", false
	}
	return joined.String(), true
}

// v2rayN is VMessBean.toV2rayN: the VmessQRCode as Gson writes it, in base64 (NO_WRAP, with padding).
func (b *VMessBean) v2rayN() string {
	tls := str(b.Security) == "tls"
	net, typ := str(b.Type), ""
	switch net {
	case "http":
		if !tls {
			typ, net = "http", "tcp"
		}
	case "xhttp":
		typ = str(b.XhttpMode)
	}
	tlsName := ""
	if tls {
		tlsName = "tls"
		if !ktIsBlank(str(b.RealityPubKey)) {
			tlsName = "reality"
		}
	}
	aid := "null"
	if b.AlterId != nil {
		aid = strconv.Itoa(*b.AlterId)
	}
	port := "null"
	if b.ServerPort != nil {
		port = strconv.Itoa(*b.ServerPort)
	}
	fields := []struct{ name, value string }{
		{"v", "2"}, {"ps", str(b.Name)}, {"add", str(b.ServerAddress)}, {"port", port}, {"id", str(b.Uuid)},
		{"aid", aid}, {"scy", str(b.Encryption)}, {"net", net}, {"type", typ}, {"host", str(b.Host)},
		{"path", str(b.Path)}, {"tls", tlsName}, {"sni", str(b.Sni)}, {"alpn", strings.ReplaceAll(str(b.Alpn), "\n", ",")},
		{"fp", str(b.UtlsFingerprint)},
	}
	var json strings.Builder
	json.WriteByte('{')
	for i, f := range fields {
		if i > 0 {
			json.WriteByte(',')
		}
		gsonWriteString(&json, f.name)
		json.WriteByte(':')
		gsonWriteString(&json, f.value)
	}
	json.WriteByte('}')
	return "vmess://" + base64.StdEncoding.EncodeToString([]byte(json.String()))
}

// gsonWriteString is Gson's JsonWriter.string with HTML escaping on (new Gson()): '"' and '\\' escaped, \t \b \n \r
// \f as such, other control characters, <, >, &, =, ' and U+2028, U+2029 as \uXXXX (lower case).
func gsonWriteString(out *strings.Builder, s string) {
	out.WriteByte('"')
	for _, r := range s {
		switch r {
		case '"':
			out.WriteString(`\"`)
		case '\\':
			out.WriteString(`\\`)
		case '\t':
			out.WriteString(`\t`)
		case '\b':
			out.WriteString(`\b`)
		case '\n':
			out.WriteString(`\n`)
		case '\r':
			out.WriteString(`\r`)
		case '\f':
			out.WriteString(`\f`)
		case '<', '>', '&', '=', '\'', ' ', ' ':
			out.WriteString(`\u` + leftPad4(strconv.FormatInt(int64(r), 16)))
		default:
			if r < 0x20 {
				out.WriteString(`\u` + leftPad4(strconv.FormatInt(int64(r), 16)))
			} else {
				out.WriteRune(r)
			}
		}
	}
	out.WriteByte('"')
}

func leftPad4(s string) string { return strings.Repeat("0", 4-len(s)) + s }

var errNoUniversalLink = errors.New("no link for this kind of profile")

var _ = hysteriaProtocolUDP

// HysteriaBean's protocols (Hysteria 1).
const (
	hysteriaProtocolUDP         = 0
	hysteriaProtocolFakeTCP     = 1
	hysteriaProtocolWeChatVideo = 2
)

// ktLines is Kotlin's String.lines(): split at \r\n, \n and \r.
func ktLines(s string) []string {
	return strings.Split(strings.ReplaceAll(strings.ReplaceAll(s, "\r\n", "\n"), "\r", "\n"), "\n")
}

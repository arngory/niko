package nikofmt

import (
	"errors"
	"strconv"
	"strings"
)

// The other share links the app imports (ktx/Formats.kt parseProxies): Shadowsocks, SOCKS, HTTP, Trojan, Trojan-Go,
// Hysteria 1, TUIC and AnyTLS, each as its parser in app/src/main/java/io/niko/fmt reads it. The defaults are not
// applied here (ParseLink applies them, as parseProxies does).

// ParseShadowsocks is fmt/shadowsocks/ShadowsocksFmt.kt parseShadowsocks: SIP002 (base64 of "method:password" in
// the user info) or method and password in the clear, justmysocks' base64 of a whole "method:password@host:port"
// behind an '@', or v2rayN's base64 of all of it. A v2rayN link without a '#' takes the whole link for its name, as
// the app's substringAfter does.
func ParseShadowsocks(link string) (*ShadowsocksBean, error) {
	b := &ShadowsocksBean{}
	if !strings.Contains(substringBefore(link, "#"), "@") {
		// v2rayN style
		v2URL := link
		if strings.Contains(v2URL, "#") {
			v2URL = substringBefore(v2URL, "#")
		}
		decoded, err := decodeBase64UrlSafe(substringAfter(v2URL, "ss://"))
		if err != nil {
			return nil, err
		}
		u, err := parseOkURL("https://" + decoded)
		if err != nil {
			return nil, errors.New("invalid v2rayN link")
		}
		b.ServerAddress = ptr(u.host)
		b.ServerPort = ptr(u.port)
		b.Method = ptr(u.username)
		b.Password = ptr(u.password)
		b.Plugin = ptr("")
		remarks, ok := javaURLDecode(substringAfter(link, "#"))
		if !ok {
			remarks = substringAfter(link, "#") // NGUtil.urlDecode gives the text back where it fails
		}
		if !ktIsBlank(remarks) {
			b.Name = &remarks
		}
		return b, nil
	}
	u, err := parseOkURL(strings.ReplaceAll(link, "ss://", "https://"))
	if err != nil {
		return nil, errors.New("invalid ss-android link")
	}
	if ktIsBlank(u.username) {
		// justmysocks: the fragment set anew (all of the link without a '#') keeps its text
		decoded, err := decodeBase64UrlSafe(substringBefore(substringAfter(link, "ss://"), "#"))
		if err != nil {
			return nil, err
		}
		if u, err = parseOkURL("https://" + decoded); err != nil {
			return nil, errors.New("invalid jms link")
		}
		u.fragment = ptr(substringAfter(link, "#"))
	}
	b.ServerAddress = ptr(u.host)
	b.ServerPort = ptr(u.port)
	if !ktIsBlank(u.password) {
		b.Method = ptr(u.username)
		b.Password = ptr(u.password)
	} else {
		methodAndPassword, err := decodeBase64UrlSafe(u.username)
		if err != nil {
			return nil, err
		}
		b.Method = ptr(substringBefore(methodAndPassword, ":"))
		b.Password = ptr(substringAfter(methodAndPassword, ":"))
	}
	b.Plugin = ptr("")
	if plugin := u.queryParameter("plugin"); plugin != nil {
		b.Plugin = plugin
	}
	b.Name = u.fragment
	// fixPluginName
	if strings.HasPrefix(*b.Plugin, "simple-obfs") {
		b.Plugin = ptr("obfs-local" + strings.TrimPrefix(*b.Plugin, "simple-obfs"))
	}
	return b, nil
}

// ParseSOCKS is fmt/socks/SOCKSFmt.kt parseSOCKS: socks://, socks4://, socks4a://, socks5://, read as an http:// URL
// (so port 80 without one). A user without a password is tried as v2rayN's base64 of "user:password"; where it
// decodes it replaces both, even to text that was never base64 ("test" is three bytes of it).
func ParseSOCKS(link string) (*SOCKSBean, error) {
	u, err := parseOkURL("http://" + substringAfter(link, "://"))
	if err != nil {
		return nil, errors.New("not supported link")
	}
	b := &SOCKSBean{}
	switch {
	case strings.HasPrefix(link, "socks4://"):
		b.Protocol = ptr(ProtocolSOCKS4)
	case strings.HasPrefix(link, "socks4a://"):
		b.Protocol = ptr(ProtocolSOCKS4A)
	default:
		b.Protocol = ptr(ProtocolSOCKS5)
	}
	b.Name = u.fragment
	b.ServerAddress = ptr(u.host)
	b.ServerPort = ptr(u.port)
	b.Username = ptr(u.username)
	b.Password = ptr(u.password)
	if ktIsBlank(u.password) && !ktIsBlank(u.username) {
		if n, err := decodeBase64UrlSafe(u.username); err == nil {
			b.Username = ptr(substringBefore(n, ":"))
			b.Password = ptr(substringAfter(n, ":"))
		}
	}
	return b, nil
}

// ParseHTTP is fmt/http/HttpFmt.kt parseHttp: an http:// or https:// proxy, whose path must be "/". Any other path
// is an error, where the app takes the link for a subscription's instead.
func ParseHTTP(link string) (*HttpBean, error) {
	u, err := parseOkURL(link)
	if err != nil {
		return nil, errors.New("invalid http(s) link")
	}
	if len(u.pathSegments) != 1 || u.pathSegments[0] != "" {
		return nil, ErrSubscriptionLink // "Not http proxy"
	}
	b := &HttpBean{}
	b.ServerAddress = ptr(u.host)
	b.ServerPort = ptr(u.port)
	b.Username = ptr(u.username)
	b.Password = ptr(u.password)
	b.Sni = u.queryParameter("sni")
	b.Name = u.fragment
	// setTLS
	if u.scheme == "https" {
		b.Security = ptr("tls")
	} else {
		b.Security = ptr("")
	}
	return b, nil
}

// ParseTrojan is fmt/trojan/TrojanFmt.kt parseTrojan: Xray's share link (parseDuckSoft, the Niko parameters with
// it), then allowInsecure and peer, the SNI of older links.
func ParseTrojan(link string, settings Settings) (*TrojanBean, error) {
	u, err := parseOkURL(strings.ReplaceAll(link, "trojan://", "https://"))
	if err != nil {
		return nil, errors.New("invalid trojan link")
	}
	b := &TrojanBean{}
	if err := b.parseDuckSoft(u, settings, kindTrojan, &b.Password); err != nil {
		return nil, err
	}
	if v := u.queryParameter("allowInsecure"); v != nil && (*v == "1" || *v == "true") {
		b.AllowInsecure = ptr(true)
	}
	if v := u.queryParameter("peer"); v != nil && !ktIsBlank(*v) {
		b.Sni = v
	}
	return b, nil
}

// ParseTrojanGo is fmt/trojan_go/TrojanGoFmt.kt parseTrojanGo: sni, type (ws with host and path), encryption,
// plugin, a name that is not blank.
func ParseTrojanGo(link string) (*TrojanGoBean, error) {
	u, err := parseOkURL(strings.ReplaceAll(link, "trojan-go://", "https://"))
	if err != nil {
		return nil, errors.New("invalid trojan-go link")
	}
	b := &TrojanGoBean{}
	b.ServerAddress = ptr(u.host)
	b.ServerPort = ptr(u.port)
	b.Password = ptr(u.username)
	if v := u.queryParameter("sni"); v != nil {
		b.Sni = v
	}
	if v := u.queryParameter("type"); v != nil {
		b.Type = v
		if *v == "ws" {
			if h := u.queryParameter("host"); h != nil {
				b.Host = h
			}
			if p := u.queryParameter("path"); p != nil {
				b.Path = p
			}
		}
	}
	if v := u.queryParameter("encryption"); v != nil {
		b.Encryption = v
	}
	if v := u.queryParameter("plugin"); v != nil {
		b.Plugin = v
	}
	if u.fragment != nil && !ktIsBlank(*u.fragment) {
		b.Name = u.fragment
	}
	return b, nil
}

// ParseHysteria1 is fmt/hysteria/HysteriaFmt.kt parseHysteria1: hysteria://host:port with mport, peer (the SNI),
// auth, insecure, upmbps and downmbps (a number or ignored), alpn, obfsParam and protocol (faketcp, wechat-video).
func ParseHysteria1(link string) (*HysteriaBean, error) {
	u, err := parseOkURL(strings.ReplaceAll(link, "hysteria://", "https://"))
	if err != nil {
		return nil, errHysteriaLink
	}
	b := &HysteriaBean{ProtocolVersion: ptr(1)}
	b.ServerAddress = ptr(u.host)
	b.ServerPorts = ptr(strconv.Itoa(u.port))
	b.Name = u.fragment
	if v := u.queryParameter("mport"); v != nil {
		b.ServerPorts = v
	}
	if v := u.queryParameter("peer"); v != nil {
		b.Sni = v
	}
	if v := u.queryParameter("auth"); v != nil && !ktIsBlank(*v) {
		b.AuthPayloadType = ptr(HysteriaAuthString)
		b.AuthPayload = v
	}
	if v := u.queryParameter("insecure"); v != nil {
		b.AllowInsecure = ptr(*v == "1" || *v == "true")
	}
	if v := u.queryParameter("upmbps"); v != nil {
		if n := toIntOrNull(*v); n != nil {
			b.UploadMbps = n
		}
	}
	if v := u.queryParameter("downmbps"); v != nil {
		if n := toIntOrNull(*v); n != nil {
			b.DownloadMbps = n
		}
	}
	if v := u.queryParameter("alpn"); v != nil {
		b.Alpn = v
	}
	if v := u.queryParameter("obfsParam"); v != nil {
		b.Obfuscation = v
	}
	switch v := u.queryParameter("protocol"); {
	case equals(v, "faketcp"):
		b.Protocol = ptr(HysteriaProtocolFakeTCP)
	case equals(v, "wechat-video"):
		b.Protocol = ptr(HysteriaProtocolWechatVideo)
	}
	return b, nil
}

// HysteriaBean's authentication payloads and Hysteria 1's protocols.
const (
	HysteriaAuthNone            = 0
	HysteriaAuthString          = 1
	HysteriaAuthBase64          = 2
	HysteriaProtocolUDP         = 0
	HysteriaProtocolFakeTCP     = 1
	HysteriaProtocolWechatVideo = 2
)

// ParseTuic is fmt/tuic/TuicFmt.kt parseTuic: TUIC v5, the UUID and the token as the user and the password, or both
// in the user split at its first ':'.
func ParseTuic(link string) (*TuicBean, error) {
	u, err := parseOkURL(strings.ReplaceAll(link, "tuic://", "https://"))
	if err != nil {
		return nil, errors.New("invalid tuic link")
	}
	b := &TuicBean{ProtocolVersion: ptr(5)}
	b.Name = u.fragment
	b.ServerAddress = ptr(u.host)
	b.ServerPort = ptr(u.port)
	if uuid, token, ok := strings.Cut(u.username, ":"); ok {
		b.Uuid = ptr(uuid)
		b.Token = ptr(token)
	} else {
		b.Uuid = ptr(u.username)
		b.Token = ptr(u.password)
	}
	if v := u.queryParameter("sni"); v != nil {
		b.Sni = v
	}
	if v := u.queryParameter("congestion_control"); v != nil {
		b.CongestionController = v
	}
	if v := u.queryParameter("udp_relay_mode"); v != nil {
		b.UdpRelayMode = v
	}
	if v := u.queryParameter("alpn"); v != nil {
		b.Alpn = v
	}
	if equals(u.queryParameter("allow_insecure"), "1") {
		b.AllowInsecure = ptr(true)
	}
	if equals(u.queryParameter("disable_sni"), "1") {
		b.DisableSNI = ptr(true)
	}
	return b, nil
}

// ParseAnyTLS is proxy/anytls/AnyTLSFmt.kt parseAnytls (anytls-go's docs/uri_scheme.md): the password as the user,
// sni, insecure, fp.
func ParseAnyTLS(link string) (*AnyTLSBean, error) {
	u, err := parseOkURL(strings.ReplaceAll(link, "anytls://", "https://"))
	if err != nil {
		return nil, errors.New("invalid anytls link")
	}
	b := &AnyTLSBean{}
	b.ServerAddress = ptr(u.host)
	b.ServerPort = ptr(u.port)
	b.Name = u.fragment
	b.Password = ptr(u.username)
	b.Sni = ptr("")
	if v := u.queryParameter("sni"); v != nil {
		b.Sni = v
	}
	if v := u.queryParameter("insecure"); v != nil {
		b.AllowInsecure = ptr(*v == "1" || *v == "true")
	}
	if v := u.queryParameter("fp"); v != nil {
		b.UtlsFingerprint = v
	}
	return b, nil
}

// Package nikofmt reads share links in Go as the app reads them in Kotlin (app/src/main/java/io/niko/fmt): the same
// profile beans, field for field, for the desktop daemon's profiles (docs/design/desktop-stage3.md). The fixtures the
// app's own parsers write (shared/golden/links, the app's io.niko.golden tests) are what golden_test.go holds this
// package to. Every link the app's import takes (ktx/Formats.kt parseProxies): naive, Hysteria 1 and 2, VLESS and
// VMess in all their forms, Trojan, Trojan-Go, Shadowsocks, SOCKS, HTTP, TUIC, AnyTLS and mieru, sn:// to come; the
// Niko parameters of a raycontrol node's links with them (DNS tunnels, Yggdrasil, certificate pins).
//
// The parsing is ported as the app does it, its quirks included (each says so where it is); the URL parsing under it
// is OkHttp's and java.net.URI's, base64 is Android's, JSON is Gson's, not Go's own. The package is for the daemon,
// not gomobile: it does not import the root package libcore.
package nikofmt

import (
	"errors"
	"strings"
)

// ErrUnsupported is a link of a kind this package does not read: one the app does not import, or sn:// (not yet).
var ErrUnsupported = errors.New("not a link this package reads")

// ErrSubscriptionLink is a link the app takes for a subscription's, not a profile (parseProxies'
// SubscriptionFoundException): clash://install-config?, sn://subscription?, and an http(s) URL that is no proxy.
var ErrSubscriptionLink = errors.New("a subscription's link, not a profile")

// ParseLink reads one share link as the app's import does (ktx/Formats.kt parseProxies): the parser by the link's
// scheme, in the app's order, then the bean's defaults. An error is a link the app drops, or one of the two above.
func ParseLink(link string, settings Settings) (Bean, error) {
	var b Bean
	var err error
	// each parser's own pointer type: a nil one in an interface would not be nil
	set := func(bean Bean, e error) {
		if e != nil {
			err = e
		} else {
			b = bean
		}
	}
	switch {
	case strings.HasPrefix(link, "clash://install-config?") || strings.HasPrefix(link, "sn://subscription?"):
		return nil, ErrSubscriptionLink
	case strings.HasPrefix(link, "socks://") || strings.HasPrefix(link, "socks4://") ||
		strings.HasPrefix(link, "socks4a://") || strings.HasPrefix(link, "socks5://"):
		v, e := ParseSOCKS(link)
		set(v, e)
	case isHTTPLink(link):
		v, e := ParseHTTP(link)
		if e != nil {
			// whatever fails, the app takes the URL for a subscription's (clash://install-config?url=…)
			return nil, ErrSubscriptionLink
		}
		set(v, nil)
	case strings.HasPrefix(link, "vmess://") || strings.HasPrefix(link, "vless://"):
		v, e := ParseV2Ray(link, settings)
		set(v, e)
	case strings.HasPrefix(link, "trojan://"):
		v, e := ParseTrojan(link, settings)
		set(v, e)
	case strings.HasPrefix(link, "trojan-go://"):
		v, e := ParseTrojanGo(link)
		set(v, e)
	case strings.HasPrefix(link, "mierus://"):
		v, e := ParseMieru(link)
		set(v, e)
	case strings.HasPrefix(link, "ss://"):
		v, e := ParseShadowsocks(link)
		set(v, e)
	case strings.HasPrefix(link, "naive+"):
		v, e := ParseNaive(link)
		set(v, e)
	case strings.HasPrefix(link, "hysteria://"):
		v, e := ParseHysteria1(link)
		set(v, e)
	case strings.HasPrefix(link, "hysteria2://") || strings.HasPrefix(link, "hy2://"):
		v, e := ParseHysteria2(link, settings)
		set(v, e)
	case strings.HasPrefix(link, "tuic://"):
		v, e := ParseTuic(link)
		set(v, e)
	case strings.HasPrefix(link, "anytls://"):
		v, e := ParseAnyTLS(link)
		set(v, e)
	default:
		return nil, ErrUnsupported
	}
	if err != nil {
		return nil, err
	}
	b.InitializeDefaultValues()
	return b, nil
}

// isHTTPLink is parseProxies' matches("(http|https)://.*"): Java's '.' takes no line terminator.
func isHTTPLink(link string) bool {
	if !strings.HasPrefix(link, "http://") && !strings.HasPrefix(link, "https://") {
		return false
	}
	return !strings.ContainsAny(link, "\n\r\u0085\u2028\u2029")
}

// Package nikofmt reads share links in Go as the app reads them in Kotlin (app/src/main/java/io/niko/fmt): the same
// profile beans, field for field, for the desktop daemon's profiles (docs/design/desktop-stage3.md). The fixtures the
// app's own parsers write (shared/golden/links, the app's io.niko.golden tests) are what golden_test.go holds this
// package to. So far naive, Hysteria2, VLESS and VMess in all their forms; the Niko parameters of a raycontrol node's
// links with them (DNS tunnels, Yggdrasil, certificate pins).
//
// The parsing is ported as the app does it, its quirks included (each says so where it is); the URL parsing under it
// is OkHttp's and java.net.URI's, base64 is Android's, JSON is Gson's, not Go's own. The package is for the daemon,
// not gomobile: it does not import the root package libcore.
package nikofmt

import (
	"errors"
	"strings"
)

// ErrUnsupported is a link of a kind this package does not read yet.
var ErrUnsupported = errors.New("not a link this package reads")

// ParseLink reads one share link as the app's import does (ktx/Formats.kt parseProxies): the parser by the link's
// scheme, in the app's order, then the bean's defaults. An error is a link the app drops.
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
	case strings.HasPrefix(link, "vmess://") || strings.HasPrefix(link, "vless://"):
		v, e := ParseV2Ray(link, settings)
		set(v, e)
	case strings.HasPrefix(link, "naive+"):
		v, e := ParseNaive(link)
		set(v, e)
	case strings.HasPrefix(link, "hysteria2://") || strings.HasPrefix(link, "hy2://"):
		v, e := ParseHysteria2(link, settings)
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

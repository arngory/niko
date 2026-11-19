// Package nikofmt reads share links in Go as the app reads them in Kotlin (app/src/main/java/io/niko/fmt): the same
// profile beans, field for field, for the desktop daemon's profiles (docs/design/desktop-stage3.md). The fixtures the
// app's own parsers write (shared/golden/links, the app's io.niko.golden tests) are what golden_test.go holds this
// package to. So far the links of a raycontrol node: naive, Hysteria2 and VLESS (REALITY, XHTTP, the DNS tunnels and
// Yggdrasil of the Niko parameters).
//
// The parsing is ported as the app does it, its quirks included (each says so where it is); the URL parsing under it
// is OkHttp's and java.net.URI's, not net/url's. The package is for the daemon, not gomobile: it does not import the
// root package libcore.
package nikofmt

import (
	"errors"
	"strings"
)

// ErrUnsupported is a link of a kind this package does not read yet.
var ErrUnsupported = errors.New("not a link this package reads")

// ParseLink reads one share link as the app's import does (ktx/Formats.kt parseProxies: the parser by the link's
// scheme, then the bean's defaults): naive+https://, naive+quic://, hysteria2://, hy2://, vless://. An error is a
// link the app drops.
func ParseLink(link string, settings Settings) (Bean, error) {
	var b Bean
	switch {
	case strings.HasPrefix(link, "vless://"):
		v, err := ParseVLESS(link, settings)
		if err != nil {
			return nil, err
		}
		b = v
	case strings.HasPrefix(link, "naive+"):
		v, err := ParseNaive(link)
		if err != nil {
			return nil, err
		}
		b = v
	case strings.HasPrefix(link, "hysteria2://") || strings.HasPrefix(link, "hy2://"):
		v, err := ParseHysteria2(link, settings)
		if err != nil {
			return nil, err
		}
		b = v
	default:
		return nil, ErrUnsupported
	}
	b.InitializeDefaultValues()
	return b, nil
}

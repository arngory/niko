package nikofmt

import (
	"errors"
	"math"
	"strconv"
	"strings"
)

// What libcore/nikoconf, the app's config build in Go, and libcore/nikosub, its subscriptions, share with the
// parsers: the same Kotlin and JVM behaviour for the same beans, so the packages read a field alike.

// KtTrim is Kotlin's String.trim().
func KtTrim(s string) string { return ktTrim(s) }

// KtIsBlank is Kotlin's String.isBlank(): empty or whitespace as Kotlin's Char.isWhitespace takes it.
func KtIsBlank(s string) bool { return ktIsBlank(s) }

// KtLowercase is Kotlin's String.lowercase() (Locale.ROOT).
func KtLowercase(s string) string { return ktLowercase(s) }

// KtLines is Kotlin's String.lines(): split at \r\n, \n and \r.
func KtLines(s string) []string { return ktLines(s) }

// KtToIntOrNull is Kotlin's String.toIntOrNull(): an optional sign and digits as Character.digit takes them (other
// scripts' decimal digits too), within an Int.
func KtToIntOrNull(s string) (int, bool) {
	n, ok := javaParseInt(s, 10)
	return int(n), ok
}

// JavaParseLong is Long.parseLong(s, radix) for radix 8, 10 or 16, and Kotlin's toLongOrNull() for 10: KtToIntOrNull's
// rules within a Long; ok false where Java throws.
func JavaParseLong(s string, radix int) (int64, bool) { return javaParseLong(s, radix) }

// IsIPAddress is ktx String.isIpAddress: the app's own IPv4 and IPv6 patterns.
func IsIPAddress(s string) bool { return isIPAddress(s) }

// Base is the bean's AbstractBean: the server and what every profile has.
func Base(b Bean) *AbstractBean { return b.base() }

// JavaURI is what java.net.URI gives of a URI: getScheme, getHost (an IPv6 literal in its brackets, "" for none),
// getPort (-1 for none), and the raw user info, path, query and fragment, nil where Java's are null.
type JavaURI struct {
	Scheme                                      string
	Host                                        string
	Port                                        int
	RawUserInfo, RawPath, RawQuery, RawFragment *string
}

// ParseJavaURI is new URI(s) (URI.create): an error where Java throws URISyntaxException.
func ParseJavaURI(s string) (JavaURI, error) {
	u, err := parseJavaURI(s)
	if err != nil {
		return JavaURI{}, err
	}
	return JavaURI{Scheme: u.scheme, Host: u.host, Port: u.port, RawUserInfo: u.rawUserInfo, RawPath: u.rawPath,
		RawQuery: u.rawQuery, RawFragment: u.rawFragment}, nil
}

// OkHTTPHost is OkHttp's toHttpUrlOrNull()?.host: the URL's canonical host (an IPv6 address without brackets), ok
// false where OkHttp gives null.
func OkHTTPHost(s string) (string, bool) {
	u, err := parseOkURL(s)
	if err != nil {
		return "", false
	}
	return u.host, true
}

// JavaDoubleString is Double.toString: a decimal point always, scientific notation outside 10^-3 to 10^7.
func JavaDoubleString(f float64) string {
	switch {
	case math.IsNaN(f):
		return "NaN"
	case math.IsInf(f, 1):
		return "Infinity"
	case math.IsInf(f, -1):
		return "-Infinity"
	case f == 0:
		if math.Signbit(f) {
			return "-0.0"
		}
		return "0.0"
	}
	if a := math.Abs(f); a >= 1e-3 && a < 1e7 {
		s := strconv.FormatFloat(f, 'f', -1, 64)
		if !strings.Contains(s, ".") {
			s += ".0"
		}
		return s
	}
	mantissa, exponent, _ := strings.Cut(strconv.FormatFloat(f, 'E', -1, 64), "E")
	if !strings.Contains(mantissa, ".") {
		mantissa += ".0"
	}
	e, _ := strconv.Atoi(exponent)
	return mantissa + "E" + strconv.Itoa(e)
}

// JavaURLDecode is URLDecoder.decode(s, "UTF-8"); ok false where Java throws.
func JavaURLDecode(s string) (string, bool) { return javaURLDecode(s) }

// DecodeBase64UrlSafe is ktx String.decodeBase64UrlSafe: Util.b64Decode (Android's decoder, either alphabet) as UTF-8
// text; an error where Util.b64Decode throws.
func DecodeBase64UrlSafe(s string) (string, error) { return decodeBase64UrlSafe(s) }

// OkURL is what OkHttp's HttpUrl gives of an http(s) URL (toHttpUrl): its scheme, canonical host and port, its path
// as given (EncodedPath, "/" for none) and its query's parameters, decoded, and as given (RawQuery, nil without '?').
type OkURL struct {
	Scheme      string
	Host        string
	Port        int
	EncodedPath string
	RawQuery    *string
	u           *okURL
}

// ParseOkURL is HttpUrl.toHttpUrl: an error where OkHttp throws.
func ParseOkURL(s string) (*OkURL, error) {
	u, err := parseOkURL(s)
	if err != nil {
		return nil, err
	}
	path := u.rawPath
	if path == "" {
		path = "/"
	}
	return &OkURL{Scheme: u.scheme, Host: u.host, Port: u.port, EncodedPath: path, RawQuery: u.rawQuery, u: u}, nil
}

// QueryParameter is HttpUrl.queryParameter: the first value of the name, nil without one.
func (u *OkURL) QueryParameter(name string) *string { return u.u.queryParameter(name) }

// QueryParameterValues is HttpUrl.queryParameterValues.
func (u *OkURL) QueryParameterValues(name string) []*string { return u.u.queryParameterValues(name) }

// OkQueryValues is toHttpUrl(s).queryParameterValues(name); ok false where OkHttp takes no URL of s.
func OkQueryValues(s, name string) ([]*string, bool) {
	u, err := parseOkURL(s)
	if err != nil {
		return nil, false
	}
	return u.queryParameterValues(name), true
}

// ParseVLESSDuckSoft is what RayRows.parse makes of a node's vless:// row: the link's standard form alone (OkHttp's
// URL of https:// and what follows "://"), a VMessBean with alterId -1 filled by parseDuckSoft. An error where the
// URL does not read, or where it has a password (not the standard form).
func ParseVLESSDuckSoft(link string, settings Settings) (*VMessBean, error) {
	u, err := parseOkURL("https://" + substringAfter(link, "://"))
	if err != nil {
		return nil, err
	}
	if u.password != "" {
		return nil, errors.New("vless: not the standard link form")
	}
	b := &VMessBean{}
	b.AlterId = ptr(-1)
	if err = b.parseDuckSoft(u, settings, kindVLESS, &b.Uuid); err != nil {
		return nil, err
	}
	return b, nil
}

// WithoutParams is u.newBuilder() with every query parameter whose name drop takes removed and no fragment, built
// again (HttpUrl.toString): what RaySubscription.dohUrl makes of a node's DoH row. The path and the parameters kept
// are as the URL has them.
func (u *OkURL) WithoutParams(drop func(name string) bool) string {
	b := newOkBuilder(u.Scheme)
	b.username(u.u.username)
	b.password(u.u.password)
	b.host = u.Host
	if u.Port != okDefaultPort(u.Scheme) {
		b.port = u.Port
	}
	s := b.String()
	s = strings.TrimSuffix(s, "/") + u.EncodedPath
	if u.RawQuery != nil {
		var kept []string
		for _, part := range strings.Split(*u.RawQuery, "&") {
			name, _, _ := strings.Cut(part, "=")
			if drop(okPercentDecode(name, true)) {
				continue
			}
			kept = append(kept, part)
		}
		// HttpUrl.Builder drops the query once no parameter is left
		if len(kept) > 0 {
			s += "?" + strings.Join(kept, "&")
		}
	}
	return s
}

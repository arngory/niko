package nikofmt

// What libcore/nikoconf, the app's config build in Go, shares with the parsers: the same Kotlin and JVM behaviour
// for the same beans, so the two packages read a field alike.

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

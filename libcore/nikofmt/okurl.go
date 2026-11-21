package nikofmt

import (
	"errors"
	"net/netip"
	"strings"
)

// okURL is an http or https URL as OkHttp 4.12's HttpUrl parses it (toHttpUrl, toHttpUrlOrNull), which the app's link
// parsers stand on: what they read of it, decoded as its getters decode. Go's net/url differs in what it refuses, how
// it decodes and how it writes a host, so this follows OkHttp's HttpUrl.Builder.parse instead.
type okURL struct {
	scheme       string // "http" or "https"
	host         string
	port         int
	username     string
	password     string
	pathSegments []string
	query        []okParam // nil without a '?'
	fragment     *string
}

// okParam is a name and its value; a name without '=' has no value (OkHttp's null).
type okParam struct {
	name  string
	value *string
}

var errOkURL = errors.New("not a URL OkHttp takes")

// parseOkURL is HttpUrl.toHttpUrl: an http: or https: URL (most parsers build the https:// prefix themselves).
func parseOkURL(input string) (*okURL, error) {
	// leading and trailing ASCII whitespace
	input = strings.TrimFunc(input, func(r rune) bool {
		return r == '\t' || r == '\n' || r == '\f' || r == '\r' || r == ' '
	})
	u := &okURL{}
	var pos, defaultPort int
	switch {
	case len(input) >= 6 && strings.EqualFold(input[:6], "https:"):
		u.scheme, pos, defaultPort = "https", 6, 443
	case len(input) >= 5 && strings.EqualFold(input[:5], "http:"):
		u.scheme, pos, defaultPort = "http", 5, 80
	default:
		return nil, errors.New("expected URL scheme 'http' or 'https'")
	}
	// any number of slashes, backslashes too
	for pos < len(input) && (input[pos] == '/' || input[pos] == '\\') {
		pos++
	}
	// the authority ends at the first of "/\?#"; its user info at the last '@' before that
	end := pos + strings.IndexAny(input[pos:], "/\\?#")
	if end < pos {
		end = len(input)
	}
	authority := input[pos:end]
	if at := strings.LastIndexByte(authority, '@'); at >= 0 {
		user, password, hasPassword := strings.Cut(authority[:at], ":")
		u.username = okPercentDecode(okStripTabs(user), false)
		if hasPassword {
			u.password = okPercentDecode(okStripTabs(password), false)
		}
		authority = authority[at+1:]
	}
	hostPart, portPart, hasPort := okSplitPort(authority)
	host, ok := okCanonicalHost(okPercentDecode(hostPart, false))
	if !ok {
		return nil, errors.New("invalid URL host")
	}
	u.host = host
	u.port = defaultPort
	if hasPort && portPart != "" {
		if u.port = okParsePort(portPart); u.port < 0 {
			return nil, errors.New("invalid URL port")
		}
	}
	pos = end

	pathEnd := pos + strings.IndexAny(input[pos:], "?#")
	if pathEnd < pos {
		pathEnd = len(input)
	}
	u.pathSegments = okResolvePath(input[pos:pathEnd])
	pos = pathEnd

	if pos < len(input) && input[pos] == '?' {
		queryEnd := pos + strings.IndexByte(input[pos:], '#')
		if queryEnd < pos {
			queryEnd = len(input)
		}
		u.query = okQueryNamesAndValues(okStripTabs(input[pos+1 : queryEnd]))
		pos = queryEnd
	}
	if pos < len(input) && input[pos] == '#' {
		fragment := okPercentDecode(okStripTabs(input[pos+1:]), false)
		u.fragment = &fragment
	}
	return u, nil
}

// okSplitPort splits host and port at the first ':' outside brackets (HttpUrl's portColonOffset).
func okSplitPort(authority string) (host, port string, hasPort bool) {
	for i := 0; i < len(authority); i++ {
		switch authority[i] {
		case '[':
			for i++; i < len(authority) && authority[i] != ']'; i++ {
			}
		case ':':
			return authority[:i], authority[i+1:], true
		}
	}
	return authority, "", false
}

// okParsePort is HttpUrl's parsePort: the port canonicalized without encoding (so ASCII only), Integer.parseInt, 1 to
// 65535; -1 otherwise. A sign is taken ("+80").
func okParsePort(s string) int {
	for i := 0; i < len(s); i++ {
		if c := s[i]; c < '0' && c != '+' && c != '-' || c > '9' {
			return -1
		}
	}
	v, ok := javaParseInt(s, 10)
	if !ok || v < 1 || v > 65535 {
		return -1
	}
	return int(v)
}

// okStripTabs drops what OkHttp's canonicalize drops from an already encoded component: tabs and line ends.
func okStripTabs(s string) string {
	if !strings.ContainsAny(s, "\t\n\f\r") {
		return s
	}
	return strings.Map(func(r rune) rune {
		if r == '\t' || r == '\n' || r == '\f' || r == '\r' {
			return -1
		}
		return r
	}, s)
}

// okPercentDecode is OkHttp's percentDecode: %XY with two hex digits is a byte, a '%' without them stays, '+' is a
// space where plusIsSpace (the query); the bytes are then UTF-8 as the JVM decodes it.
func okPercentDecode(s string, plusIsSpace bool) string {
	if !strings.Contains(s, "%") && !(plusIsSpace && strings.Contains(s, "+")) {
		return s
	}
	b := make([]byte, 0, len(s))
	for i := 0; i < len(s); i++ {
		c := s[i]
		if c == '%' && i+2 < len(s) {
			if d1, d2 := hexValue(s[i+1]), hexValue(s[i+2]); d1 >= 0 && d2 >= 0 {
				b = append(b, byte(d1<<4|d2))
				i += 2
				continue
			}
		} else if c == '+' && plusIsSpace {
			b = append(b, ' ')
			continue
		}
		b = append(b, c)
	}
	return javaUTF8(b)
}

func hexValue(c byte) int {
	switch {
	case c >= '0' && c <= '9':
		return int(c - '0')
	case c >= 'a' && c <= 'f':
		return int(c-'a') + 10
	case c >= 'A' && c <= 'F':
		return int(c-'A') + 10
	}
	return -1
}

// okCanonicalHost is OkHttp's toCanonicalHost of a decoded host. An IPv6 literal (any ':') in brackets or not, written
// as RFC 5952 has it, an IPv4-mapped one as IPv4 (InetAddress makes it an Inet4Address); a name through
// java.net.IDN.toASCII and lower case, refused with an empty label (but a last one: "a." stays), a label over 63
// characters or a character no host has.
//
// A name that is not ASCII is refused here: OkHttp converts it to punycode (IDNA 2003 on the JVM, ICU's IDNA on
// Android), which this package does not carry.
func okCanonicalHost(host string) (string, bool) {
	if strings.Contains(host, ":") {
		if strings.HasPrefix(host, "[") && strings.HasSuffix(host, "]") {
			host = host[1 : len(host)-1]
		}
		if strings.Contains(host, "%") {
			return "", false // no zone
		}
		addr, err := netip.ParseAddr(host)
		if err != nil || !addr.Is6() {
			return "", false
		}
		return addr.Unmap().String(), true
	}
	for i := 0; i < len(host); i++ {
		if host[i] >= 0x80 {
			return "", false
		}
	}
	if host != "." {
		labels := strings.Split(host, ".")
		for i, label := range labels {
			if len(label) > 63 || label == "" && i != len(labels)-1 {
				return "", false
			}
		}
	}
	host = strings.ToLower(host)
	if host == "" {
		return "", false
	}
	for i := 0; i < len(host); i++ {
		if c := host[i]; c <= 0x1f || c >= 0x7f || strings.IndexByte(" #%/:?@[\\]", c) >= 0 {
			return "", false
		}
	}
	return host, true
}

// okResolvePath is HttpUrl's resolvePath on an absolute URL's path: segments split at '/' and '\', "." dropped, ".."
// taking the one before, then decoded (pathSegments). An empty path is one empty segment.
func okResolvePath(path string) []string {
	segments := []string{""}
	if path == "" {
		return segments
	}
	pos := 0
	if path[0] == '/' || path[0] == '\\' {
		pos = 1
	}
	for pos < len(path) {
		next := pos + strings.IndexAny(path[pos:], "/\\")
		trailingSlash := next >= pos
		if !trailingSlash {
			next = len(path)
		}
		segment := okStripTabs(path[pos:next])
		switch {
		case isDotSegment(segment):
		case isDotDotSegment(segment):
			removed := segments[len(segments)-1]
			segments = segments[:len(segments)-1]
			if removed == "" && len(segments) > 0 {
				segments[len(segments)-1] = ""
			} else {
				segments = append(segments, "")
			}
		default:
			if segments[len(segments)-1] == "" {
				segments[len(segments)-1] = segment
			} else {
				segments = append(segments, segment)
			}
			if trailingSlash {
				segments = append(segments, "")
			}
		}
		pos = next
		if trailingSlash {
			pos++
		}
	}
	for i, s := range segments {
		segments[i] = okPercentDecode(s, false)
	}
	return segments
}

func isDotSegment(s string) bool { return s == "." || strings.EqualFold(s, "%2e") }

func isDotDotSegment(s string) bool {
	switch strings.ToLower(s) {
	case "..", "%2e.", ".%2e", "%2e%2e":
		return true
	}
	return false
}

// okQueryNamesAndValues splits an encoded query at '&' and each part at its first '=', then decodes names and values
// ('+' a space). An empty query is one name "" without a value, as is a trailing '&'.
func okQueryNamesAndValues(query string) []okParam {
	var out []okParam
	for _, part := range strings.Split(query, "&") {
		name, value, hasValue := strings.Cut(part, "=")
		p := okParam{name: okPercentDecode(name, true)}
		if hasValue {
			v := okPercentDecode(value, true)
			p.value = &v
		}
		out = append(out, p)
	}
	return out
}

// queryParameter is HttpUrl.queryParameter: the first value of the name, nil when the name is not there or has no
// value.
func (u *okURL) queryParameter(name string) *string {
	for _, p := range u.query {
		if p.name == name {
			return p.value
		}
	}
	return nil
}

// queryParameterValues is HttpUrl.queryParameterValues: every value of the name in order, nil for one without '='.
func (u *okURL) queryParameterValues(name string) []*string {
	var out []*string
	for _, p := range u.query {
		if p.name == name {
			out = append(out, p.value)
		}
	}
	return out
}

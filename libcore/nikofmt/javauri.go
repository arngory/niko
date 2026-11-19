package nikofmt

import (
	"errors"
	"net/netip"
	"strings"
	"unicode"
)

// javaURI is what fmt/NikoParams.kt reads of a java.net.URI (RFC 2396 as Java parses it): a Yggdrasil peer's scheme,
// host, port and raw query. A server authority that does not parse (a name with "_", "1.2.3", a port that is not a
// number) is a registry one, with no host and port -1, not an error; what is no URI at all is an error
// (URISyntaxException).
type javaURI struct {
	scheme   string
	host     string // "" for none; an IPv6 literal keeps its brackets, as getHost() does
	port     int    // -1 for none
	rawQuery *string
}

var errJavaURI = errors.New("not a URI java.net.URI takes")

// The character classes of java.net.URI.Parser.
const (
	uriAlpha      = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
	uriDigit      = "0123456789"
	uriAlphanum   = uriAlpha + uriDigit
	uriMark       = "-_.!~*'()"
	uriUnreserved = uriAlphanum + uriMark
	uriReserved   = ";/?:@&=+$,[]"
	uriUric       = uriReserved + uriUnreserved          // and escapes
	uriPchar      = uriUnreserved + ":@&=+$,"            // and escapes
	uriPath       = uriPchar + ";/"                      // and escapes
	uriUserinfo   = uriUnreserved + ";:&=+$,"            // and escapes
	uriRegName    = uriUnreserved + "$,;:@&=+"           // and escapes
	uriServer     = uriUserinfo + uriAlphanum + "-.:@[]" // and escapes
	uriScheme     = uriAlphanum + "+-."
)

type uriParser struct {
	s []rune // Java works on chars; runes differ only outside the BMP
	u javaURI
}

func parseJavaURI(input string) (*javaURI, error) {
	p := &uriParser{s: []rune(input), u: javaURI{port: -1}}
	if err := p.parse(); err != nil {
		return nil, err
	}
	return &p.u, nil
}

// scan moves past the characters of set and, with escapes, past %XY and the visible characters outside ASCII; a '%'
// without two hex digits after it is an error where escapes are allowed.
func (p *uriParser) scan(start, end int, set string, escapes bool) (int, error) {
	i := start
	for i < end {
		c := p.s[i]
		if c != 0 && c < 0x80 && strings.ContainsRune(set, c) {
			i++
			continue
		}
		if escapes {
			if c == '%' {
				if i+3 <= end && isHexRune(p.s[i+1]) && isHexRune(p.s[i+2]) {
					i += 3
					continue
				}
				return 0, errJavaURI // malformed escape pair
			}
			if c > 128 && !unicode.In(c, unicode.Zs, unicode.Zl, unicode.Zp) && !isISOControl(c) {
				i++
				continue
			}
		}
		break
	}
	return i, nil
}

func isISOControl(c rune) bool { return c <= 0x1f || c >= 0x7f && c <= 0x9f }

func isHexRune(c rune) bool { return c < 0x80 && hexValue(byte(c)) >= 0 }

// scanUntil is the parser's scan(start, end, err, stop): the index of the first of stop, -1 at one of err first.
func (p *uriParser) scanUntil(start, end int, err, stop string) int {
	for i := start; i < end; i++ {
		if strings.ContainsRune(err, p.s[i]) {
			return -1
		}
		if strings.ContainsRune(stop, p.s[i]) {
			return i
		}
	}
	return end
}

func (p *uriParser) checkChars(start, end int, set string, escapes bool) error {
	i, err := p.scan(start, end, set, escapes)
	if err != nil {
		return err
	}
	if i < end {
		return errJavaURI
	}
	return nil
}

func (p *uriParser) at(i int, c rune) bool { return i < len(p.s) && p.s[i] == c }

func (p *uriParser) parse() error {
	n := len(p.s)
	i := p.scanUntil(0, n, "/?#", ":")
	if i >= 0 && p.at(i, ':') {
		if i == 0 || !strings.ContainsRune(uriAlpha, p.s[0]) {
			return errJavaURI
		}
		if err := p.checkChars(1, i, uriScheme, false); err != nil {
			return err
		}
		p.u.scheme = string(p.s[:i])
		i++
		if p.at(i, '/') {
			var err error
			if i, err = p.parseHierarchical(i, n); err != nil {
				return err
			}
		} else {
			// opaque: no host, port or query
			q := p.scanUntil(i, n, "", "#")
			if q <= i {
				return errJavaURI
			}
			if err := p.checkChars(i, q, uriUric, true); err != nil {
				return err
			}
			i = q
		}
	} else {
		var err error
		if i, err = p.parseHierarchical(0, n); err != nil {
			return err
		}
	}
	if p.at(i, '#') {
		if err := p.checkChars(i+1, n, uriUric, true); err != nil {
			return err
		}
		i = n
	}
	if i < n {
		return errJavaURI
	}
	return nil
}

func (p *uriParser) parseHierarchical(start, n int) (int, error) {
	i := start
	if p.at(i, '/') && p.at(i+1, '/') {
		i += 2
		q := p.scanUntil(i, n, "", "/?#")
		if q > i {
			var err error
			if i, err = p.parseAuthority(i, q); err != nil {
				return 0, err
			}
		} else if q >= n {
			return 0, errJavaURI // an authority is expected
		}
	}
	q := p.scanUntil(i, n, "", "?#")
	if err := p.checkChars(i, q, uriPath, true); err != nil {
		return 0, err
	}
	i = q
	if p.at(i, '?') {
		i++
		q = p.scanUntil(i, n, "", "#")
		if err := p.checkChars(i, q, uriUric, true); err != nil {
			return 0, err
		}
		query := string(p.s[i:q])
		p.u.rawQuery = &query
		i = q
	}
	return i, nil
}

func (p *uriParser) parseAuthority(start, n int) (int, error) {
	serverSet := uriServer
	if p.scanUntil(start, n, "", "]") > start {
		serverSet += "%" // an IPv6 literal may have a scope
	}
	end, err := p.scan(start, n, serverSet, true)
	if err != nil {
		return 0, err
	}
	serverChars := end == n
	end, err = p.scan(start, n, uriRegName, true)
	if err != nil {
		return 0, err
	}
	regChars := end == n
	if regChars && !serverChars {
		return n, nil // registry-based
	}
	q := start
	var serverErr error
	if serverChars {
		if q, serverErr = p.parseServer(start, n); serverErr == nil && q < n {
			serverErr = errJavaURI
		}
		if serverErr != nil {
			p.u.host, p.u.port = "", -1
			q = start
		}
	}
	if q < n {
		if regChars {
			return n, nil // registry-based
		}
		if serverErr != nil {
			return 0, serverErr
		}
		return 0, errJavaURI
	}
	return n, nil
}

func (p *uriParser) parseServer(start, n int) (int, error) {
	i := start
	if q := p.scanUntil(i, n, "/?#", "@"); q >= i && p.at(q, '@') {
		if err := p.checkChars(i, q, uriUserinfo, true); err != nil {
			return 0, err
		}
		i = q + 1
	}
	if p.at(i, '[') {
		i++
		q := p.scanUntil(i, n, "/?#", "]")
		if q <= i || !p.at(q, ']') {
			return 0, errJavaURI
		}
		literal := string(p.s[i:q])
		if address, scope, ok := strings.Cut(literal, "%"); ok {
			if scope == "" || p.checkChars(i+len([]rune(address))+1, q, uriAlphanum+"_.", false) != nil {
				return 0, errJavaURI
			}
			literal = address
		}
		if addr, err := netip.ParseAddr(literal); err != nil || !addr.Is6() {
			return 0, errJavaURI
		}
		p.u.host = string(p.s[i-1 : q+1])
		i = q + 1
	} else {
		q := p.parseIPv4Address(i, n)
		if q <= i {
			var err error
			if q, err = p.parseHostname(i, n); err != nil {
				return 0, err
			}
		}
		i = q
	}
	if p.at(i, ':') {
		i++
		q, _ := p.scan(i, n, uriDigit, false)
		if q > i {
			port, ok := javaParseInt(string(p.s[i:q]), 10)
			if !ok {
				return 0, errJavaURI // a malformed port number
			}
			p.u.port = int(port)
			i = q
		}
	}
	if i < n {
		return 0, errJavaURI
	}
	return i, nil
}

// parseIPv4Address takes four dotted bytes of at most 255 (any number of digits), followed by the end or ':'.
func (p *uriParser) parseIPv4Address(start, n int) int {
	m, _ := p.scan(start, n, uriDigit+".", false)
	if m <= start {
		return -1
	}
	i := start
	for part := 0; part < 4; part++ {
		if part > 0 {
			if !p.at(i, '.') || i >= m {
				return -1
			}
			i++
		}
		q, _ := p.scan(i, m, uriDigit, false)
		if q <= i {
			return -1
		}
		if v, ok := javaParseInt(string(p.s[i:q]), 10); !ok || v > 255 {
			return -1
		}
		i = q
	}
	if i < m {
		return -1
	}
	if i < n && p.s[i] != ':' {
		return -1
	}
	p.u.host = string(p.s[start:i])
	return i
}

// parseHostname takes labels of letters, digits and inner hyphens; the last one, after a dot, starts with a letter.
func (p *uriParser) parseHostname(start, n int) (int, error) {
	i, last := start, -1
	for i < n {
		q, _ := p.scan(i, n, uriAlphanum, false)
		if q <= i {
			break
		}
		last = i
		i = q
		if q, _ = p.scan(i, n, uriAlphanum+"-", false); q > i {
			if p.s[q-1] == '-' {
				return 0, errJavaURI
			}
			i = q
		}
		if !p.at(i, '.') {
			break
		}
		i++
	}
	if i < n && !p.at(i, ':') {
		return 0, errJavaURI
	}
	if last < 0 {
		return 0, errJavaURI
	}
	if last > start && !strings.ContainsRune(uriAlpha, p.s[last]) {
		return 0, errJavaURI
	}
	p.u.host = string(p.s[start:i])
	return i, nil
}

package nikofmt

import (
	"errors"
	"strconv"
	"strings"
	"unicode/utf8"
)

// The encode sets of OkHttp 4.12's HttpUrl (its companion's constants, read from the jar the app ships).
const (
	okUsernameEncodeSet       = " \"':;<=>@[]^`{}|/\\?#"
	okPasswordEncodeSet       = " \"':;<=>@[]^`{}|/\\?#"
	okQueryComponentEncodeSet = " !\"#$&'(),/:;<=>?@[]\\^`{|}~"
	okFragmentEncodeSet       = ""
)

// okBuilder is OkHttp's HttpUrl.Builder as the app's link writers use it (fmt/*Fmt.kt toUri, ktx/Nets.kt toLink):
// a scheme, the user and password, a host, a port, the query and the fragment, each kept encoded as the builder
// keeps it; the path is the default "/". The first error is kept: where the builder throws (a host it refuses, a
// port out of range), the writer fails as the app's does.
type okBuilder struct {
	scheme   string
	user     string
	pass     string
	host     string
	port     int // -1: the scheme's default
	query    []okParam
	hasQuery bool
	fragment *string
	err      error
}

func newOkBuilder(scheme string) *okBuilder { return &okBuilder{scheme: scheme, port: -1} }

// linkBuilder is ktx linkBuilder(): an https builder.
func linkBuilder() *okBuilder { return newOkBuilder("https") }

func okDefaultPort(scheme string) int {
	switch scheme {
	case "http":
		return 80
	case "https":
		return 443
	}
	return -1
}

// okCanonicalize is OkHttp's String.canonicalize: code points below 0x20, 0x7f, those at 0x80 and up unless
// unicodeAllowed, those in encodeSet and a '%' (unless alreadyEncoded and, if strict, a valid escape) become %XY
// of their UTF-8 bytes, upper case; '+' is %2B where plusIsSpace ("+" if alreadyEncoded); an already encoded
// component loses its tabs and line ends.
func okCanonicalize(input, encodeSet string, alreadyEncoded, strict, plusIsSpace, unicodeAllowed bool) string {
	var out strings.Builder
	for i := 0; i < len(input); {
		r, size := utf8.DecodeRuneInString(input[i:])
		switch {
		case alreadyEncoded && (r == '\t' || r == '\n' || r == '\f' || r == '\r'):
		case r == '+' && plusIsSpace:
			if alreadyEncoded {
				out.WriteByte('+')
			} else {
				out.WriteString("%2B")
			}
		case r < 0x20 || r == 0x7f || (r >= 0x80 && !unicodeAllowed) || strings.ContainsRune(encodeSet, r) ||
			(r == '%' && (!alreadyEncoded || strict && !okIsPercentEncoded(input, i))):
			for _, c := range []byte(input[i : i+size]) {
				out.WriteByte('%')
				out.WriteByte("0123456789ABCDEF"[c>>4])
				out.WriteByte("0123456789ABCDEF"[c&0xf])
			}
		default:
			out.WriteString(input[i : i+size])
		}
		i += size
	}
	return out.String()
}

func okIsPercentEncoded(s string, i int) bool {
	return i+2 < len(s) && s[i] == '%' && hexValue(s[i+1]) != -1 && hexValue(s[i+2]) != -1
}

func (b *okBuilder) fail(err error) {
	if b.err == nil {
		b.err = err
	}
}

// username and password: HttpUrl.Builder.username and password, encoded with their sets.
func (b *okBuilder) username(s string) *okBuilder {
	b.user = okCanonicalize(s, okUsernameEncodeSet, false, false, false, false)
	return b
}

func (b *okBuilder) password(s string) *okBuilder {
	b.pass = okCanonicalize(s, okPasswordEncodeSet, false, false, false, false)
	return b
}

// hostname is HttpUrl.Builder.host: percent-decoded, then canonical (toCanonicalHost); one it refuses is the
// builder's IllegalArgumentException.
func (b *okBuilder) hostname(s string) *okBuilder {
	host, ok := okCanonicalHost(okPercentDecode(s, false))
	if !ok {
		b.fail(errors.New("unexpected host: " + s))
		return b
	}
	b.host = host
	return b
}

// setPort is HttpUrl.Builder.port: 1 to 65535, or the builder throws.
func (b *okBuilder) setPort(port int) *okBuilder {
	if port <= 0 || port > 65535 {
		b.fail(errors.New("unexpected port: " + strconv.Itoa(port)))
		return b
	}
	b.port = port
	return b
}

// addQueryParameter is HttpUrl.Builder.addQueryParameter: the name and the value encoded as a query component.
func (b *okBuilder) addQueryParameter(name, value string) *okBuilder {
	b.hasQuery = true
	v := okCanonicalize(value, okQueryComponentEncodeSet, false, false, true, false)
	b.query = append(b.query, okParam{name: okCanonicalize(name, okQueryComponentEncodeSet, false, false, true, false), value: &v})
	return b
}

// setQueryParameter is HttpUrl.Builder.setQueryParameter: every parameter of the name removed, then the name added
// at the end.
func (b *okBuilder) setQueryParameter(name, value string) *okBuilder {
	canonical := okCanonicalize(name, okQueryComponentEncodeSet, false, false, true, false)
	kept := b.query[:0]
	for _, p := range b.query {
		if p.name != canonical {
			kept = append(kept, p)
		}
	}
	b.query = kept
	if len(b.query) == 0 {
		b.hasQuery = false
	}
	return b.addQueryParameter(name, value)
}

// encodedFragment is HttpUrl.Builder.encodedFragment: taken as encoded, with unicode.
func (b *okBuilder) encodedFragment(s string) *okBuilder {
	f := okCanonicalize(s, okFragmentEncodeSet, true, false, false, true)
	b.fragment = &f
	return b
}

func (b *okBuilder) effectivePort() int {
	if b.port != -1 {
		return b.port
	}
	return okDefaultPort(b.scheme)
}

// String is HttpUrl.Builder.toString (and so HttpUrl.toString of what it builds): userinfo where either part is
// there, an IPv6 host in brackets, the port where it is not the scheme's default, the path "/", the query and the
// fragment.
func (b *okBuilder) String() string {
	var out strings.Builder
	out.WriteString(b.scheme)
	out.WriteString("://")
	if b.user != "" || b.pass != "" {
		out.WriteString(b.user)
		if b.pass != "" {
			out.WriteByte(':')
			out.WriteString(b.pass)
		}
		out.WriteByte('@')
	}
	if strings.Contains(b.host, ":") {
		out.WriteString("[" + b.host + "]")
	} else {
		out.WriteString(b.host)
	}
	if port := b.effectivePort(); port != okDefaultPort(b.scheme) {
		out.WriteByte(':')
		out.WriteString(strconv.Itoa(port))
	}
	out.WriteByte('/')
	if b.hasQuery {
		out.WriteByte('?')
		for i, p := range b.query {
			if i > 0 {
				out.WriteByte('&')
			}
			out.WriteString(p.name)
			if p.value != nil {
				out.WriteByte('=')
				out.WriteString(*p.value)
			}
		}
	}
	if b.fragment != nil {
		out.WriteByte('#')
		out.WriteString(*b.fragment)
	}
	return out.String()
}

// build is HttpUrl.Builder.build: the URL, or the builder's error (a host is required).
func (b *okBuilder) build() (string, error) {
	if b.err != nil {
		return "", b.err
	}
	if b.host == "" {
		return "", errors.New("host == null")
	}
	return b.String(), nil
}

// toLink is ktx HttpUrl.Builder.toLink: the URL with the scheme's name in place of http(s); a port that is the
// default is still written where appendDefaultPort (through a placeholder port, as the app does).
func (b *okBuilder) toLink(scheme string, appendDefaultPort bool) (string, error) {
	if _, err := b.build(); err != nil {
		return "", err
	}
	defaultPort := okDefaultPort(b.scheme)
	replace := false
	saved := b.port
	if appendDefaultPort && b.effectivePort() == defaultPort {
		b.port = 14514
		replace = true
	}
	url := b.String()
	b.port = saved
	url = strings.ReplaceAll(url, b.scheme+"://", scheme+"://")
	if replace {
		url = strings.ReplaceAll(url, ":14514", ":"+strconv.Itoa(defaultPort))
	}
	return url, nil
}

// javaURLEncode is URLEncoder.encode(s, "UTF-8"): letters, digits and ".-*_" as they are, a space '+', every other
// byte of the UTF-8 %XY, upper case. ktx urlSafe then writes the '+' as %20.
func javaURLEncode(s string) string {
	var out strings.Builder
	for i := 0; i < len(s); i++ {
		c := s[i]
		switch {
		case 'a' <= c && c <= 'z', 'A' <= c && c <= 'Z', '0' <= c && c <= '9', c == '.', c == '-', c == '*', c == '_':
			out.WriteByte(c)
		case c == ' ':
			out.WriteByte('+')
		default:
			out.WriteByte('%')
			out.WriteByte("0123456789ABCDEF"[c>>4])
			out.WriteByte("0123456789ABCDEF"[c&0xf])
		}
	}
	return out.String()
}

// urlSafe is ktx String.urlSafe.
func urlSafe(s string) string { return strings.ReplaceAll(javaURLEncode(s), "+", "%20") }

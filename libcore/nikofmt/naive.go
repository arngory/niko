package nikofmt

import (
	"errors"
	"strings"
)

// ParseNaive is fmt/naive/NaiveFmt.kt parseNaive: naive+https:// or naive+quic://, with the parameters of a
// raycontrol node (naive-uot-v1, icmp-v1, naive-udp-v1, cert-pin-v1). The defaults are applied, as the app's parser
// applies them itself. naive takes niko-pin only of the Niko parameters (not applyNikoParams).
func ParseNaive(link string) (*NaiveBean, error) {
	// the scheme's part after "+" up to ":"; Kotlin's substringAfter/substringBefore keep all without the delimiter
	proto := substringBefore(substringAfter(link, "+"), ":")
	u, err := parseOkURL("https://" + substringAfter(link, "://"))
	if err != nil {
		return nil, errors.New("invalid naive link")
	}
	b := &NaiveBean{Proto: &proto}
	b.ServerAddress = ptr(u.host)
	b.ServerPort = ptr(u.port)
	b.Username = ptr(u.username)
	b.Password = ptr(u.password)
	b.Sni = u.queryParameter("sni")
	b.Certificates = u.queryParameter("cert")
	if h := u.queryParameter("extra-headers"); h != nil {
		// decoded twice: the URL's query, then URLDecoder (unUrlSafe), which makes a '+' that came as %2B a space and
		// gives the text back as it was where it fails
		decoded, _ := javaURLDecode(*h)
		b.ExtraHeaders = ptr(strings.ReplaceAll(decoded, "\r\n", "\n"))
	}
	if c := u.queryParameter("insecure-concurrency"); c != nil {
		if v, ok := javaParseInt(*c, 10); ok {
			b.InsecureConcurrency = ptr(int(v))
		}
	}
	// UDP over TCP v2 only: "1" may mean v1
	b.SUoT = ptr(equals(u.queryParameter("uot"), "2"))
	b.NikoIcmp = ptr(equals(u.queryParameter("icmp"), "1"))
	b.NikoAck = ptr(equals(u.queryParameter("niko-ack"), "1"))
	b.NikoUdp = ptr(equals(u.queryParameter("udp"), "1"))
	if pin := u.queryParameter("niko-pin"); pin != nil && !ktIsBlank(*pin) {
		b.CertPins = ptr(ktTrim(*pin))
	}
	b.Name = u.fragment
	b.InitializeDefaultValues()
	return b, nil
}

// substringAfter is Kotlin's: the part after the first delimiter, or all of s without one.
func substringAfter(s, delimiter string) string {
	if _, after, ok := strings.Cut(s, delimiter); ok {
		return after
	}
	return s
}

// substringBefore is Kotlin's: the part before the first delimiter, or all of s without one.
func substringBefore(s, delimiter string) string {
	if before, _, ok := strings.Cut(s, delimiter); ok {
		return before
	}
	return s
}

// equals is Kotlin's "value == text" for a nullable value.
func equals(value *string, text string) bool { return value != nil && *value == text }

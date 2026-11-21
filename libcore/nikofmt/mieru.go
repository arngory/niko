package nikofmt

import (
	"errors"
	"strings"
)

// ParseMieru is fmt/mieru/MieruFmt.kt parseMieru, mieru's own link (pkg/appctl/url.go):
// mierus://USER:PASS@HOST?profile=NAME&mtu=N&multiplexing=…&handshake-mode=…&traffic-pattern=…&port=P&protocol=TCP,
// read by java.net.URI and URLDecoder. The first port of the link counts (of a range, its first), its first
// protocol; the profile's name, or the fragment.
func ParseMieru(link string) (*MieruBean, error) {
	u, err := parseJavaURI(ktTrim(link))
	if err != nil {
		return nil, err
	}
	if !strings.EqualFold(u.scheme, "mierus") {
		return nil, errors.New("not a mierus:// link")
	}
	if u.rawUserInfo == nil {
		return nil, errors.New("mierus: no user")
	}
	user := *u.rawUserInfo
	type pair struct{ name, value string }
	var params []pair
	if u.rawQuery != nil {
		for _, part := range strings.Split(*u.rawQuery, "&") {
			if !strings.Contains(part, "=") {
				continue
			}
			name, ok1 := javaURLDecode(substringBefore(part, "="))
			value, ok2 := javaURLDecode(substringAfter(part, "="))
			if !ok1 || !ok2 {
				return nil, errors.New("mierus: a parameter is not URL-encoded")
			}
			params = append(params, pair{name, value})
		}
	}
	param := func(name string) *string {
		for _, p := range params {
			if p.name == name {
				return &p.value
			}
		}
		return nil
	}
	port := (*int)(nil)
	if v := param("port"); v != nil {
		port = toIntOrNull(ktTrim(substringBefore(substringBefore(*v, "-"), ":")))
	}
	if port == nil {
		return nil, errors.New("mierus: no port")
	}
	if *port < 1 || *port > 65535 {
		return nil, errors.New("mierus: the port is out of range")
	}
	if u.host == "" {
		return nil, errors.New("mierus: no host")
	}
	username, ok1 := javaURLDecode(substringBefore(user, ":"))
	password, ok2 := javaURLDecode(substringAfterOr(user, ":", ""))
	if !ok1 || !ok2 {
		return nil, errors.New("mierus: the user is not URL-encoded")
	}
	b := &MieruBean{}
	b.ServerAddress = ptr(strings.Trim(u.host, "[]"))
	b.ServerPort = port
	b.Username = &username
	b.Password = &password
	b.Protocol = ptr("TCP")
	if v := param("protocol"); v != nil && strings.EqualFold(*v, "UDP") {
		b.Protocol = ptr("UDP")
	}
	if v := param("mtu"); v != nil {
		if mtu := toIntOrNull(*v); mtu != nil && *mtu >= 1280 && *mtu <= 1500 {
			b.Mtu = mtu
		}
	}
	b.Multiplexing = ptr(orEmpty(param("multiplexing")))
	b.HandshakeMode = ptr(orEmpty(param("handshake-mode")))
	b.TrafficPattern = ptr(orEmpty(param("traffic-pattern")))
	name := orEmpty(param("profile"))
	if ktIsBlank(name) {
		name = ""
		if u.rawFragment != nil {
			if name, ok1 = javaURLDecode(*u.rawFragment); !ok1 {
				return nil, errors.New("mierus: the fragment is not URL-encoded")
			}
		}
	}
	b.Name = &name
	return b, nil
}

// substringAfterOr is Kotlin's substringAfter(delimiter, missing): the part after the first delimiter, or missing.
func substringAfterOr(s, delimiter, missing string) string {
	if _, after, ok := strings.Cut(s, delimiter); ok {
		return after
	}
	return missing
}

func orEmpty(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

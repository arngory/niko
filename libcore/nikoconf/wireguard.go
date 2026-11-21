package nikoconf

import (
	"encoding/base64"
	"errors"
	"regexp"
	"strconv"
	"strings"

	"libcore/nikofmt"
)

// WireGuard (fmt/wireguard/WireGuardFmt.kt, AmneziaWG.kt): the core's endpoint, AmneziaWG's parameters at its root.

// wireGuardEndpoint is buildSingBoxEndpointWireguardBean: one peer, the server, all addresses through it.
func wireGuardEndpoint(b *nikofmt.WireGuardBean) (object, error) {
	awg, err := parseAmneziaWG(str(b.AmneziaWG))
	if err != nil {
		return nil, errors.New(nikofmt.DisplayName(b) + ": " + err.Error())
	}
	peer := object{"allowed_ips": []any{"0.0.0.0/0", "::/0"}}
	putString(peer, "address", b.ServerAddress)
	putInt(peer, "port", b.ServerPort)
	putString(peer, "public_key", b.PeerPublicKey)
	putString(peer, "pre_shared_key", b.PeerPreSharedKey)
	if !blank(b.Reserved) {
		peer["reserved"] = genReserved(*b.Reserved)
	}
	// keeps the NAT mapping (and the server's conntrack entry) alive while idle
	if n := num(b.PersistentKeepalive); n > 0 {
		peer["persistent_keepalive_interval"] = int64(min(n, 65535)) // uint16
	}
	// AmneziaWG 3.x: a "min-max" range re-picked at every keepalive
	if awg.keepalive != nil {
		if n, ok := nikofmt.JavaParseLong(*awg.keepalive, 10); ok {
			peer["persistent_keepalive_interval"] = n
		} else {
			peer["persistent_keepalive_interval"] = *awg.keepalive
		}
	}
	e := object{"type": "wireguard", "address": listByLineOrComma(str(b.LocalAddress)), "peers": []any{peer}}
	putString(e, "private_key", b.PrivateKey)
	putInt(e, "mtu", b.Mtu)
	// the core reads the AmneziaWG fields at the endpoint's root, as an awg .conf has them in [Interface]
	for k, v := range awg.fields {
		e[k] = v
	}
	return e, nil
}

// genReserved: three numbers ("1,2,3", "[1, 2, 3]") as the base64 of their bytes, anything else as it is.
func genReserved(s string) string {
	list := listByLineOrComma(s)
	if len(list) != 3 {
		return s
	}
	var bytes [3]byte
	for i, item := range list {
		n, ok := nikofmt.KtToIntOrNull(strings.NewReplacer("[", "", "]", "", " ", "").Replace(item.(string)))
		if !ok {
			return s
		}
		bytes[i] = byte(n) // Int.toByte: the low eight bits
	}
	return base64.StdEncoding.EncodeToString(bytes[:])
}

// amneziaWG is AmneziaWG.Parsed: the endpoint's fields, and the peer's keepalive range.
type amneziaWG struct {
	fields    object
	keepalive *string
}

// The .conf keys (lower case, without '_' and '-') and their endpoint fields.
var (
	awgNumberKeys = map[string]string{"jc": "jc", "jmin": "jmin", "jmax": "jmax", "s1": "s1", "s2": "s2", "s3": "s3",
		"s4": "s4"}
	awgRangeKeys = map[string]string{"h1": "h1", "h2": "h2", "h3": "h3", "h4": "h4",
		"contentpaddingaddition": "content_padding_addition", "rekeyaftertime": "rekey_after_time",
		"rekeytimeout": "rekey_timeout", "rejectaftertime": "reject_after_time",
		"keepalivetimeout": "keepalive_timeout", "maxhandshakeattempts": "max_handshake_attempts"}
	awgStringKeys = map[string]string{"i1": "i1", "i2": "i2", "i3": "i3", "i4": "i4", "i5": "i5", "id": "id",
		"ip": "ip", "ib": "ib", "headerprotectionkey": "header_protection_key"}
	awgSwitchKeys = map[string]string{"randomtrailers": "random_trailers", "disablecookies": "disable_cookies"}
	// server-side knobs a client .conf may carry; the client does not need them
	awgIgnoredKeys = setOf("advancedsecurity")
)

// awgKeepaliveKey: a peer keepalive range (AWG 3.x) travels in the same text.
const awgKeepaliveKey = "persistentkeepalive"

const uint32Max = 0xFFFFFFFF

// awgRange is AmneziaWG.RANGE: Java's \d and \s, ASCII only.
var awgRange = regexp.MustCompile(`^([0-9]+)[ \t\n\x0B\f\r]*(?:-[ \t\n\x0B\f\r]*([0-9]+))?$`)

// parseAmneziaWG is AmneziaWG.parse: the "Key = value" lines, '#' and ';' comments, a value left empty skipped. A
// wrong key or value is an error naming the line: a typo must not silently make plain WireGuard of the profile.
func parseAmneziaWG(text string) (amneziaWG, error) {
	awg := amneziaWG{fields: object{}}
	for _, rawLine := range nikofmt.KtLines(text) {
		line := nikofmt.KtTrim(rawLine)
		if line == "" || strings.HasPrefix(line, "#") || strings.HasPrefix(line, ";") {
			continue
		}
		separator := strings.IndexByte(line, '=')
		if separator <= 0 {
			return awg, errors.New(`AmneziaWG: expected "Key = value": ` + line)
		}
		rawKey := line[:separator]
		name := nikofmt.KtTrim(rawKey)
		key := strings.NewReplacer("_", "", "-", "").Replace(nikofmt.KtLowercase(name))
		// I1..I5 are case-sensitive tag strings: the value as it is
		value := nikofmt.KtTrim(line[separator+1:])
		if value == "" {
			continue
		}
		switch {
		case awgNumberKeys[key] != "":
			n, ok := nikofmt.JavaParseLong(value, 10)
			if !ok || n < 0 || n > uint32Max {
				return awg, errors.New("AmneziaWG: " + name + " must be a number: " + value)
			}
			awg.fields[awgNumberKeys[key]] = n
		case awgRangeKeys[key] != "":
			v, err := amneziaRange(name, value)
			if err != nil {
				return awg, err
			}
			awg.fields[awgRangeKeys[key]] = v
		case key == awgKeepaliveKey:
			v, err := amneziaRange(name, value)
			if err != nil {
				return awg, err
			}
			s := ""
			switch v := v.(type) {
			case int64:
				s = strconv.FormatInt(v, 10)
			case string:
				s = v
			}
			awg.keepalive = &s
		case awgStringKeys[key] != "":
			awg.fields[awgStringKeys[key]] = value
		case awgSwitchKeys[key] != "":
			switch nikofmt.KtLowercase(value) {
			case "on", "true", "1", "yes":
				awg.fields[awgSwitchKeys[key]] = true
			case "off", "false", "0", "no":
			default:
				return awg, errors.New("AmneziaWG: " + name + " must be on or off: " + value)
			}
		case awgIgnoredKeys[key]:
		default:
			return awg, errors.New("AmneziaWG: unknown parameter " + name)
		}
	}
	return awg, nil
}

// amneziaRange: "N" as a number, "N-M" as the string the core takes for a range (just N where M is N).
func amneziaRange(name, value string) (any, error) {
	bad := errors.New("AmneziaWG: " + name + " must be a number or min-max: " + value)
	m := awgRange.FindStringSubmatch(value)
	if m == nil {
		return nil, bad
	}
	start, err := strconv.ParseInt(m[1], 10, 64)
	if err != nil || start > uint32Max {
		return nil, bad
	}
	if m[2] == "" {
		return start, nil
	}
	end, err := strconv.ParseInt(m[2], 10, 64)
	if err != nil || end > uint32Max || end < start {
		return nil, bad
	}
	if end == start {
		return start, nil
	}
	return strconv.FormatInt(start, 10) + "-" + strconv.FormatInt(end, 10), nil
}

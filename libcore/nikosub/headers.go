package nikosub

import (
	"crypto/hmac"
	"crypto/sha256"
	"encoding/hex"
	"regexp"
	"strconv"
	"strings"

	"libcore/nikofmt"
)

// What a subscription's answer says besides its profiles, as group/SubscriptionHeaders.kt and
// SubscriptionLinks.kt read it: the traffic and expiry (subscription-userinfo), the provider's name and update
// interval, an announcement, a panel's refusal of this device, the provider's move to another address.

// Usage is SubscriptionUsage: the traffic used and allowed (bytes) and the expiry (unix seconds), 0 for unknown.
type Usage struct {
	Used, Total, Expire int64
}

// Remaining is the traffic left.
func (u Usage) Remaining() int64 { return u.Total - u.Used }

// RunningOut is SubscriptionUsage.runningOut: it ends within days or has less than percent % of the traffic left.
func (u Usage) RunningOut(nowSeconds int64, days int, percent int) bool {
	return (u.Expire > 0 && u.Expire-nowSeconds < int64(days)*86400) ||
		(u.Total > 0 && u.Remaining()*100 < u.Total*int64(percent))
}

// ParseUserInfo is parseSubscriptionUserinfo: "upload=…; download=…; total=…; expire=…", each the first match of its
// name (a value past a Long is 0); ok false for a blank header or one of all zeros.
func ParseUserInfo(header string) (Usage, bool) {
	if nikofmt.KtIsBlank(header) {
		return Usage{}, false
	}
	value := func(name string) int64 {
		// Java's \s and \d: ASCII
		m := regexp.MustCompile(`(?:^|[;\t\n\x0B\f\r ])` + name + `[\t\n\x0B\f\r ]*=[\t\n\x0B\f\r ]*([0-9]+)`).
			FindStringSubmatch(header)
		if m == nil {
			return 0
		}
		n, err := strconv.ParseInt(m[1], 10, 64)
		if err != nil {
			return 0
		}
		return n
	}
	u := Usage{Used: value("upload") + value("download"), Total: value("total"), Expire: value("expire")}
	return u, u.Used > 0 || u.Total > 0 || u.Expire > 0
}

// DecodeHeader is decodeSubscriptionHeader: a text header (profile-title, announce) plain or "base64:<data>".
func DecodeHeader(value string) string {
	text := nikofmt.KtTrim(value)
	if len(text) < 7 || !strings.EqualFold(text[:7], "base64:") {
		return text
	}
	data, ok := okioDecodeBase64(nikofmt.KtTrim(text[7:]))
	if !ok {
		return ""
	}
	return nikofmt.KtTrim(nikofmt.DecodeUTF8(data))
}

// okioDecodeBase64 is okio's String.decodeBase64(): either alphabet, whitespace and trailing '=' skipped, a lone last
// character or anything else refused.
func okioDecodeBase64(s string) ([]byte, bool) {
	limit := len(s)
	for limit > 0 {
		c := s[limit-1]
		if c != '=' && c != '\n' && c != '\r' && c != ' ' && c != '\t' {
			break
		}
		limit--
	}
	out := make([]byte, 0, limit*6/8)
	word, in := 0, 0
	for i := 0; i < limit; i++ {
		c := s[i]
		var bits int
		switch {
		case c >= 'A' && c <= 'Z':
			bits = int(c) - 65
		case c >= 'a' && c <= 'z':
			bits = int(c) - 71
		case c >= '0' && c <= '9':
			bits = int(c) + 4
		case c == '+' || c == '-':
			bits = 62
		case c == '/' || c == '_':
			bits = 63
		case c == '\n' || c == '\r' || c == ' ' || c == '\t':
			continue
		default:
			return nil, false
		}
		word = word<<6 | bits
		in++
		if in%4 == 0 {
			out = append(out, byte(word>>16), byte(word>>8), byte(word))
		}
	}
	switch in % 4 {
	case 1:
		return nil, false
	case 2:
		word <<= 12
		out = append(out, byte(word>>16))
	case 3:
		word <<= 6
		out = append(out, byte(word>>16), byte(word>>8))
	}
	return out, true
}

// UpdateIntervalMinutes is subscriptionUpdateIntervalMinutes: profile-update-interval (hours) as minutes, from 1 hour
// to 30 days; ok false for none or another value.
func UpdateIntervalMinutes(value string) (int, bool) {
	n, ok := nikofmt.KtToIntOrNull(nikofmt.KtTrim(value))
	if !ok || n < 1 || n > 24*30 {
		return 0, false
	}
	return n * 60, true
}

// Refusal is HwidRefusal: why a panel that limits devices (Remnawave) gave this device no subscription.
type Refusal int

const (
	NoRefusal Refusal = iota
	// LimitReached: x-hwid-max-devices-reached (x-hwid-limit of older panels), the user's devices are all taken
	LimitReached
	// IDRequired: x-hwid-not-supported, the limit is on and the device sent no id the panel takes
	IDRequired
)

// RefusalOf is hwidRefusal of an answer's headers.
func RefusalOf(header func(string) string) Refusal {
	is := func(name string) bool { return strings.EqualFold(nikofmt.KtTrim(header(name)), "true") }
	switch {
	case is("x-hwid-max-devices-reached") || is("x-hwid-limit"):
		return LimitReached
	case is("x-hwid-not-supported"):
		return IDRequired
	}
	return NoRefusal
}

// refusalOfError is hwidRefusal of a refused answer's error, which names each such header set to true in brackets.
func refusalOfError(message string) Refusal {
	switch {
	case strings.Contains(message, "[x-hwid-max-devices-reached]") || strings.Contains(message, "[x-hwid-limit]"):
		return LimitReached
	case strings.Contains(message, "[x-hwid-not-supported]"):
		return IDRequired
	}
	return NoRefusal
}

// DeviceID is subscriptionDeviceId: x-hwid for one provider, the first 16 bytes of HMAC-SHA256 of the install
// secret over the subscription's host, lowercased, in hex.
func DeviceID(installSecret, subscriptionLink string) string {
	host := subscriptionLink
	if u, err := nikofmt.ParseJavaURI(subscriptionLink); err == nil && u.Host != "" {
		host = u.Host
	}
	mac := hmac.New(sha256.New, []byte(installSecret))
	mac.Write([]byte(nikofmt.KtLowercase(host)))
	return hex.EncodeToString(mac.Sum(nil)[:16])
}

// Move is SubscriptionMove: where the provider moves the subscription (To, "" where it stays) and its spare address
// (Spare, "" for none).
type Move struct {
	To, Spare string
}

var moveParams = []string{"new-url", "new-domain", "fallback-url"}

// MoveOf is subscriptionMove: new-url (the whole new address), new-domain (the same address on another domain),
// fallback-url (tried when the address fails), the names Happ reads; from the answer's headers, else from "#name:
// value" lines of its body (or of the body base64-decoded). Each is checked as SubscriptionAddress checks it.
func MoveOf(current string, header func(string) string, body string) Move {
	var lines map[string]string
	value := func(name string) string {
		if v := nikofmt.KtTrim(header(name)); v != "" {
			return v
		}
		if lines == nil {
			lines = bodyParams(body)
		}
		return lines[name]
	}
	to := SubscriptionAddress(current, value("new-url"))
	if to == "" {
		to = withDomain(current, value("new-domain"))
	}
	spare := SubscriptionAddress(current, value("fallback-url"))
	if to == current {
		to = ""
	}
	if spare == current {
		spare = ""
	}
	return Move{To: to, Spare: spare}
}

// bodyParams is bodyParams: the "#name: value" lines of a body for the move's names, the first of each name; of the
// base64-decoded body where it has none.
func bodyParams(body string) map[string]string {
	scan := func(text string) map[string]string {
		found := map[string]string{}
		for _, raw := range ktLineSequence(text) {
			line := nikofmt.KtTrim(raw)
			if !strings.HasPrefix(line, "#") {
				continue
			}
			colon := strings.IndexByte(line, ':')
			if colon < 0 {
				continue
			}
			name := nikofmt.KtLowercase(nikofmt.KtTrim(line[1:colon]))
			for _, p := range moveParams {
				if name == p {
					if _, ok := found[name]; !ok {
						found[name] = nikofmt.KtTrim(line[colon+1:])
					}
				}
			}
		}
		return found
	}
	if found := scan(body); len(found) > 0 {
		return found
	}
	trimmed := nikofmt.KtTrim(body)
	if trimmed == "" {
		return map[string]string{}
	}
	data, ok := okioDecodeBase64(trimmed)
	if !ok {
		return map[string]string{}
	}
	return scan(nikofmt.DecodeUTF8(data))
}

// ktLineSequence is lineSequence(): lines at \r\n, \n and \r.
func ktLineSequence(s string) []string { return nikofmt.KtLines(s) }

// SubscriptionAddress is subscriptionAddress: a provider's address for a subscription, or "": absolute, http(s) with
// a host and no user info, at most 2048 characters, and no step down from https to http.
func SubscriptionAddress(current, candidate string) string {
	text := nikofmt.KtTrim(candidate)
	if text == "" || javaLength(text) > 2048 {
		return ""
	}
	u, err := nikofmt.ParseJavaURI(text)
	if err != nil {
		return ""
	}
	scheme := nikofmt.KtLowercase(u.Scheme)
	if scheme != "https" && scheme != "http" {
		return ""
	}
	if nikofmt.KtIsBlank(u.Host) || u.RawUserInfo != nil {
		return ""
	}
	currentScheme := ""
	if c, err := nikofmt.ParseJavaURI(nikofmt.KtTrim(current)); err == nil {
		currentScheme = nikofmt.KtLowercase(c.Scheme)
	}
	if scheme == "http" && currentScheme != "http" {
		return ""
	}
	return text
}

var domainPattern = regexp.MustCompile(`^([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$`)

// withDomain is withDomain: the current address on another domain (new-domain), the scheme, port, path, query and
// fragment as they were.
func withDomain(current, domain string) string {
	host := strings.TrimRight(nikofmt.KtTrim(domain), ".")
	if len(host) < 1 || len(host) > 253 || !domainPattern.MatchString(host) {
		return ""
	}
	u, err := nikofmt.ParseJavaURI(nikofmt.KtTrim(current))
	if err != nil || u.Scheme == "" || nikofmt.KtIsBlank(u.Host) {
		return ""
	}
	var b strings.Builder
	b.WriteString(u.Scheme + "://" + nikofmt.KtLowercase(host))
	if u.Port != -1 {
		b.WriteString(":" + strconv.Itoa(u.Port))
	}
	if u.RawPath != nil {
		b.WriteString(*u.RawPath)
	}
	if u.RawQuery != nil {
		b.WriteString("?" + *u.RawQuery)
	}
	if u.RawFragment != nil {
		b.WriteString("#" + *u.RawFragment)
	}
	return SubscriptionAddress(current, b.String())
}

// SpareAfterAnswer is spareAfterAnswer: the spare address to keep after an answer, the one the main address's answer
// names now ("" when it names none); an answer of the spare itself keeps the one kept.
func SpareAfterAnswer(kept, named string, answeredBySpare bool) string {
	if answeredBySpare {
		return kept
	}
	return named
}

var filenameStar = regexp.MustCompile(`filename\*=[^']*''([^\n\r\x{85}\x{2028}\x{2029}]+)`)

// decodeFilename is Util.decodeFilename: the filename*= of a content-disposition, URL-decoded; ok false where
// URLDecoder throws (the app's update then fails).
func decodeFilename(header string) (string, bool) {
	encoded := ""
	if m := filenameStar.FindStringSubmatch(header); m != nil {
		encoded = m[1]
	}
	return nikofmt.JavaURLDecode(encoded)
}

// DeepLink is SubscriptionImport: a subscription's address and name ("" for none) from another client's "add
// subscription" link.
type DeepLink struct {
	URL, Name string
}

// ParseDeepLink is parseSubscriptionDeepLink: clash/clashmeta://install-config?url=…&name=…,
// sing-box://import-remote-profile?url=…#name, hiddify://import/<url>#name, v2rayng://install-config|install-sub?
// url=…&name=…; ok false for anything else, sn://subscription with an exported group among them, and for one whose
// address is no http(s) one.
func ParseDeepLink(link string) (DeepLink, bool) {
	u, err := nikofmt.ParseJavaURI(link)
	if err != nil || u.Scheme == "" {
		return DeepLink{}, false
	}
	scheme := nikofmt.KtLowercase(u.Scheme)
	host := nikofmt.KtLowercase(u.Host)
	if host == "" {
		// the raw authority, for a host Java's URI does not take for a server's
		rest := link[strings.Index(link, "://")+3:]
		if end := strings.IndexAny(rest, "/?#"); end >= 0 {
			rest = rest[:end]
		}
		if strings.Contains(link, "://") {
			host = nikofmt.KtLowercase(rest)
		}
	}
	query := map[string]string{}
	raw := ""
	if u.RawQuery != nil {
		raw = *u.RawQuery
	}
	for _, part := range strings.Split(raw, "&") {
		if !strings.Contains(part, "=") {
			continue
		}
		key, value, _ := strings.Cut(part, "=")
		decoded, ok := nikofmt.JavaURLDecode(value)
		if !ok {
			return DeepLink{}, false // URLDecoder throws
		}
		query[key] = decoded
	}
	fragment := ""
	if u.RawFragment != nil {
		fragment = javaURIDecode(*u.RawFragment)
	}
	if nikofmt.KtIsBlank(fragment) {
		fragment = ""
	}
	var result DeepLink
	found := false
	switch {
	case scheme == "sn" && host == "subscription",
		(scheme == "clash" || scheme == "clashmeta") && host == "install-config",
		scheme == "v2rayng" && (host == "install-config" || host == "install-sub"):
		if url, ok := query["url"]; ok {
			name := query["name"]
			if nikofmt.KtIsBlank(name) {
				name = fragment
			}
			result, found = DeepLink{URL: url, Name: name}, true
		}
	case scheme == "sing-box" && host == "import-remote-profile":
		if url, ok := query["url"]; ok {
			name := fragment
			if name == "" {
				name = query["name"]
			}
			result, found = DeepLink{URL: url, Name: name}, true
		}
	case scheme == "hiddify" && host == "import":
		// the URL follows as the path, sometimes percent-encoded
		rest := link[strings.Index(link, "://")+3:]
		_, rest, _ = strings.Cut(rest, "/")
		raw, _, _ := strings.Cut(rest, "#")
		lower := strings.ToLower(raw)
		url := raw
		if strings.HasPrefix(lower, "http%3a") || strings.HasPrefix(lower, "https%3a") {
			decoded, ok := nikofmt.JavaURLDecode(raw)
			if !ok {
				return DeepLink{}, false
			}
			url = decoded
		}
		result, found = DeepLink{URL: url, Name: fragment}, true
	}
	if !found {
		return DeepLink{}, false
	}
	lower := strings.ToLower(result.URL)
	if !strings.HasPrefix(lower, "https://") && !strings.HasPrefix(lower, "http://") {
		return DeepLink{}, false
	}
	return result, true
}

// javaURIDecode is URI.decode: %XX runs as UTF-8, what is not UTF-8 replaced; '+' stays.
func javaURIDecode(s string) string {
	if !strings.Contains(s, "%") {
		return s
	}
	var out strings.Builder
	var run []byte
	flush := func() {
		if run != nil {
			out.WriteString(nikofmt.DecodeUTF8(run))
			run = nil
		}
	}
	for i := 0; i < len(s); i++ {
		if s[i] == '%' && i+2 < len(s) {
			if n, err := strconv.ParseUint(s[i+1:i+3], 16, 8); err == nil {
				run = append(run, byte(n))
				i += 2
				continue
			}
		}
		flush()
		out.WriteByte(s[i])
	}
	flush()
	return out.String()
}

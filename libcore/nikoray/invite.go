package nikoray

import (
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/netip"
	"regexp"
	"strconv"
	"strings"
	"unicode"

	"libcore/nikofmt"
)

// The packages of a raycontrol node a device imports (raycontrol docs/spec/spa-v2.md §8, api-v1.md §3–§3b), read as
// raycontrol/Rcdev1.kt reads them: an invite (rcinv1, the short RCINV2, RCINV3 by the node's name) that registers the
// device, and the device package (rcdev1) a registration answers. The errors are the app's words, for the golden
// fixtures (shared/golden/raycontrol/invites); none of them repeats a key or a token of the package.

// SPAParams are SPA v2's parameters of a node (RaySpaParams): Versions are those the node takes, [2] or [2, 3].
type SPAParams struct {
	NodeID    string `json:"node_id"`
	EpochLenS int64  `json:"epoch_len_s"`
	SlotLenS  int64  `json:"slot_len_s"`
	PortKey   string `json:"port_key"`
	PortBase  int    `json:"port_base"`
	PortRange int    `json:"port_range"`
	LenMin    int    `json:"len_min"`
	LenMax    int    `json:"len_max"`
	Versions  []int  `json:"versions,omitempty"`
}

// MarshalJSON is RaySpaParams.toJson: versions only where the node takes v3.
func (p SPAParams) MarshalJSON() ([]byte, error) {
	type plain SPAParams
	q := plain(p)
	if !p.V3() {
		q.Versions = nil
	}
	return json.Marshal(q)
}

// UnmarshalJSON reads the stored form: versions [2] where there are none.
func (p *SPAParams) UnmarshalJSON(data []byte) error {
	type plain SPAParams
	var q plain
	if err := json.Unmarshal(data, &q); err != nil {
		return err
	}
	*p = SPAParams(q)
	p.Versions = normalVersions(p.Versions)
	return nil
}

// V3: the node takes SPA v3 (spa-v3 §5).
func (p SPAParams) V3() bool {
	for _, v := range p.Versions {
		if v == 3 {
			return true
		}
	}
	return false
}

func spaFromJSON(o map[string]any) (SPAParams, error) {
	var p SPAParams
	var err error
	if p.NodeID, err = hexField(o, "node_id", 16); err != nil {
		return p, err
	}
	if p.EpochLenS, err = long(o, "epoch_len_s"); err != nil {
		return p, err
	}
	if p.SlotLenS, err = long(o, "slot_len_s"); err != nil {
		return p, err
	}
	if p.PortKey, err = hexField(o, "port_key", 32); err != nil {
		return p, err
	}
	ints := []struct {
		name string
		to   *int
	}{{"port_base", &p.PortBase}, {"port_range", &p.PortRange}, {"len_min", &p.LenMin}, {"len_max", &p.LenMax}}
	for _, f := range ints {
		l, err := long(o, f.name)
		if err != nil {
			return p, err
		}
		*f.to = int(int32(l)) // Kotlin's toInt()
	}
	p.Versions = spaVersions(o)
	return p, p.validate()
}

func (p SPAParams) validate() error {
	if p.EpochLenS <= 0 || p.SlotLenS <= 0 {
		return errors.New("epoch_len_s and slot_len_s must be positive")
	}
	if p.PortRange <= 0 || p.PortBase <= 0 || int32(p.PortBase)+int32(p.PortRange) > 65536 {
		return errors.New("invalid SPA port range")
	}
	// nonce 24 + tag 16 + header 13
	if p.LenMin < 53 || p.LenMax < p.LenMin || p.LenMax > 512 {
		return errors.New("invalid SPA packet length bounds")
	}
	return nil
}

// spaVersions is spaVersions of spa: the versions the node takes, 2 always; an unknown one is left out.
func spaVersions(spa map[string]any) []int {
	versions := []int{2}
	if list, ok := array(spa, "versions"); ok {
		for _, v := range list {
			if n, ok := asInt(v); ok {
				versions = append(versions, int(n))
			}
		}
	}
	return normalVersions(versions)
}

// normalVersions: 2 and 3 of them, once each, in order; [2] at least.
func normalVersions(versions []int) []int {
	has3 := false
	for _, v := range versions {
		has3 = has3 || v == 3
	}
	if has3 {
		return []int{2, 3}
	}
	return []int{2}
}

// PortSchedule is a UDP service whose port rotates every slot (spa-v2.md §11).
type PortSchedule struct {
	PortKey   string `json:"port_key"`
	PortBase  int    `json:"port_base"`
	PortRange int    `json:"port_range"`
}

// Service is a service of the node's the device may use (RayService).
type Service struct {
	ID    int           `json:"id"`
	Kind  string        `json:"kind"`
	Name  string        `json:"name"`
	Ports *PortSchedule `json:"-"`
}

// MarshalJSON is RayService.toJson: the schedule's fields beside the others.
func (s Service) MarshalJSON() ([]byte, error) {
	o := map[string]any{"id": s.ID, "kind": s.Kind, "name": s.Name}
	if s.Ports != nil {
		o["port_key"], o["port_base"], o["port_range"] = s.Ports.PortKey, s.Ports.PortBase, s.Ports.PortRange
	}
	return json.Marshal(o)
}

// UnmarshalJSON reads the stored form, as MarshalJSON writes it.
func (s *Service) UnmarshalJSON(data []byte) error {
	var v struct {
		ID        int     `json:"id"`
		Kind      string  `json:"kind"`
		Name      string  `json:"name"`
		PortKey   *string `json:"port_key"`
		PortBase  int     `json:"port_base"`
		PortRange int     `json:"port_range"`
	}
	if err := json.Unmarshal(data, &v); err != nil {
		return err
	}
	*s = Service{ID: v.ID, Kind: v.Kind, Name: v.Name}
	if v.PortKey != nil {
		s.Ports = &PortSchedule{PortKey: *v.PortKey, PortBase: v.PortBase, PortRange: v.PortRange}
	}
	return nil
}

func serviceFromJSON(o map[string]any) (Service, error) {
	var s Service
	_, a := o["port_key"]
	_, b := o["port_base"]
	_, c := o["port_range"]
	if a || b || c {
		key, err := hexField(o, "port_key", 32)
		if err != nil {
			return s, err
		}
		base, err := long(o, "port_base")
		if err != nil {
			return s, err
		}
		rng, err := long(o, "port_range")
		if err != nil {
			return s, err
		}
		ports := PortSchedule{PortKey: key, PortBase: int(int32(base)), PortRange: int(int32(rng))}
		if ports.PortRange <= 0 || ports.PortBase <= 0 || int32(ports.PortBase)+int32(ports.PortRange) > 65536 {
			return s, errors.New("invalid service port range")
		}
		s.Ports = &ports
	}
	id, err := long(o, "id")
	if err != nil {
		return s, err
	}
	s.ID = int(int32(id))
	if s.Kind, err = str(o, "kind"); err != nil {
		return s, err
	}
	if s.Name, err = str(o, "name"); err != nil {
		return s, err
	}
	return s, nil
}

// SubscriptionInfo is the device's subscription endpoint (spa-v2.md §8 sub) and the node's signing keys (node_keys).
type SubscriptionInfo struct {
	Port    int    `json:"port"`
	Pin     string `json:"pin"`
	Ed25519 string `json:"ed25519"`
	MLDSA65 string `json:"mldsa65"`
}

var base64URL = regexp.MustCompile(`^[A-Za-z0-9_-]+$`)

// subscriptionFromRegistration is RaySubscriptionInfo.fromRegistration: both sub and node_keys, or neither.
func subscriptionFromRegistration(o map[string]any) (*SubscriptionInfo, error) {
	sub := object(o, "sub")
	if sub == nil {
		return nil, nil
	}
	keys := object(o, "node_keys")
	if keys == nil {
		return nil, errors.New("rcdev1: sub without node_keys, the subscription could not be verified")
	}
	port, err := long(sub, "port")
	if err != nil {
		return nil, err
	}
	info := &SubscriptionInfo{Port: int(int32(port))}
	if info.Port < 1 || info.Port > 65535 {
		return nil, errors.New("rcdev1: invalid sub.port")
	}
	if info.Pin, err = str(sub, "pin"); err != nil {
		return nil, err
	}
	if utf16Len(info.Pin) != 43 || !base64URL.MatchString(info.Pin) {
		return nil, errors.New("rcdev1: sub.pin must be base64url SHA-256")
	}
	if info.Ed25519, err = hexField(keys, "ed25519", 32); err != nil {
		return nil, err
	}
	if info.MLDSA65, err = str(keys, "mldsa65"); err != nil {
		return nil, err
	}
	if utf16Len(info.MLDSA65) != 2603 || !base64URL.MatchString(info.MLDSA65) {
		return nil, errors.New("rcdev1: node_keys.mldsa65 must be a base64url ML-DSA-65 key")
	}
	return info, nil
}

// APIInfo is the node's remote API (api-v1.md §1): in packages of admin devices and in invites.
type APIInfo struct {
	Port int    `json:"port"`
	Pin  string `json:"pin"`
}

var apiPin = regexp.MustCompile(`^[A-Za-z0-9_-]{43}$`)

func apiFromJSON(o map[string]any) (APIInfo, error) {
	port, err := long(o, "port")
	if err != nil {
		return APIInfo{}, err
	}
	info := APIInfo{Port: int(int32(port))}
	if info.Port < 1 || info.Port > 65535 {
		return APIInfo{}, errors.New("api.port is invalid")
	}
	if info.Pin, err = str(o, "pin"); err != nil {
		return APIInfo{}, err
	}
	if !apiPin.MatchString(info.Pin) {
		return APIInfo{}, errors.New("api.pin must be base64url SHA-256")
	}
	return info, nil
}

// Registration is a device package rcdev1 (RayRegistration): it holds the device key.
type Registration struct {
	Host         string
	SPA          SPAParams
	DeviceID     string
	DeviceKey    string
	Services     []Service
	Subscription *SubscriptionInfo
	API          *APIInfo
	// Role is the device's role as its package names it (admin, or a bot's); "" for user and before roles
	Role string
	// DNSName and Addrs: names-v1, none from a node before raycontrol v0.34.0
	DNSName string
	Addrs   []string
}

// Invite is a one-time invite (RayInvite): rcinv1's fields, RCINV2's and RCINV3's alike.
type Invite struct {
	Host        string
	SPA         SPAParams
	InviteID    string
	InviteKey   string
	Services    []Service
	API         APIInfo
	Expires     int64
	PinRequired bool
	DNSName     string
	Addrs       []string
}

// tokenRe is RayToken's pattern without its look-behind, which matchToken checks.
func tokenRe(prefix string) *regexp.Regexp {
	return regexp.MustCompile(regexp.QuoteMeta(prefix) + `[A-Za-z0-9_-]+`)
}

var (
	rcinv1Token = tokenRe("rcinv1:")
	rcdev1Token = tokenRe("rcdev1:")
	// ASCII case alone, as Java's CASE_INSENSITIVE: Go's (?i) would fold ſ to s and the Kelvin sign to k
	rcinv2Token = regexp.MustCompile(`[Rr][Cc][Ii][Nn][Vv]2:[A-Za-z2-7]+`)
	rcinv3Token = regexp.MustCompile(`[Rr][Cc][Ii][Nn][Vv]3:[A-Za-z2-7]+`)
	// a line that may continue a wrapped token
	continuation = regexp.MustCompile(`^[A-Za-z0-9_-]+$`)
)

// findToken is the first match of re in text not right after a letter, digit, '_' or '-' (the app's look-behind
// (?<![A-Za-z0-9_-])): its bounds, or nil.
func findToken(re *regexp.Regexp, text string) []int {
	for _, at := range re.FindAllStringIndex(text, -1) {
		if at[0] > 0 {
			c := text[at[0]-1]
			if c >= 'A' && c <= 'Z' || c >= 'a' && c <= 'z' || c >= '0' && c <= '9' || c == '_' || c == '-' {
				continue
			}
		}
		return at
	}
	return nil
}

// candidates is RayToken.candidates: the token in text, then, where a terminal wrapped it, the token with each line
// after it that is base64url alone joined on, shortest first; the whole text trimmed where there is no token.
func candidates(re *regexp.Regexp, text string) []string {
	at := findToken(re, text)
	if at == nil {
		return []string{nikofmt.KtTrim(text)}
	}
	match := text[at[0]:at[1]]
	out := []string{match}
	rest := text[at[1]:]
	if strings.HasPrefix(rest, "\n") || strings.HasPrefix(rest, "\r\n") {
		joined := match
		lines := nikofmt.KtLines(rest)
		if len(lines) > 0 {
			lines = lines[1:]
		}
		for i, line := range lines {
			if i >= 64 {
				break
			}
			part := nikofmt.KtTrim(line)
			if !continuation.MatchString(part) {
				break
			}
			joined += part
			out = append(out, joined)
		}
	}
	return out
}

// parseToken is RayToken.parse: the first candidate that reads, else the first one's error.
func parseToken[T any](re *regexp.Regexp, prefix, text string, read func(map[string]any) (T, error)) (T, error) {
	var first error
	for _, candidate := range candidates(re, text) {
		o, err := decodeToken(prefix, candidate)
		if err == nil {
			var v T
			if v, err = read(o); err == nil {
				return v, nil
			}
		}
		if first == nil {
			first = err
		}
	}
	var zero T
	return zero, first
}

// decodeToken is RayToken.decode: the base64url JSON after the prefix.
func decodeToken(prefix, text string) (map[string]any, error) {
	name := strings.TrimSuffix(prefix, ":")
	body := nikofmt.KtTrim(strings.TrimPrefix(nikofmt.KtTrim(text), prefix))
	if body == "" {
		return nil, errors.New(name + ": not base64url")
	}
	for _, c := range body {
		if !unicode.IsLetter(c) && !unicode.IsDigit(c) && c != '-' && c != '_' {
			return nil, errors.New(name + ": not base64url")
		}
	}
	raw, err := base64.RawURLEncoding.DecodeString(body)
	if err != nil {
		return nil, errors.New(name + ": not base64url")
	}
	o, err := decodeObject(raw)
	if err != nil {
		return nil, errors.New(name + ": not JSON")
	}
	return o, nil
}

// fields is Rcdev1.fields: what an rcdev1 package and an rcinv1 invite share.
func fields(o map[string]any, name string) (Registration, error) {
	var r Registration
	if v, err := long(o, "v"); err != nil {
		return r, err
	} else if v != 1 {
		return r, fmt.Errorf("%s: unsupported version %s", name, gsonText(o["v"]))
	}
	host, err := str(o, "host")
	if err != nil {
		return r, err
	}
	if nikofmt.KtIsBlank(host) || strings.ContainsFunc(host, func(c rune) bool { return ktWhitespace(c) || c == '/' }) {
		return r, errors.New(name + ": invalid host")
	}
	spa := object(o, "spa")
	if spa == nil {
		return r, errors.New(name + ": no spa")
	}
	if r.SPA, err = spaFromJSON(spa); err != nil {
		return r, fmt.Errorf("%s: %s", name, err)
	}
	if list, ok := array(o, "services"); ok {
		for _, v := range list {
			so, ok := v.(map[string]any)
			if !ok {
				return r, errors.New(name + ": services: not an object")
			}
			s, err := serviceFromJSON(so)
			if err != nil {
				return r, fmt.Errorf("%s: services: %s", name, err)
			}
			r.Services = append(r.Services, s)
		}
	}
	r.Host = strings.TrimSuffix(strings.TrimPrefix(nikofmt.KtTrim(host), "["), "]")
	if r.DeviceID, err = hexField(o, "device_id", 8); err != nil {
		return r, err
	}
	if r.DeviceKey, err = hexField(o, "device_key", 32); err != nil {
		return r, err
	}
	return r, nil
}

// gsonText is a value as Gson writes a JsonElement (the version in the app's message).
func gsonText(v any) string {
	data, err := json.Marshal(v)
	if err != nil {
		return "null"
	}
	return string(data)
}

// ktWhitespace is Kotlin's Char.isWhitespace.
func ktWhitespace(c rune) bool { return nikofmt.KtIsBlank(string(c)) }

func utf16Len(s string) int {
	n := 0
	for _, r := range s {
		if r >= 0x10000 {
			n += 2
		} else {
			n++
		}
	}
	return n
}

// roleOf is roleOf: the role o names, any value as text, trimmed; "" for none or a blank one.
func roleOf(o map[string]any) string {
	s, ok := primitiveString(o, "role")
	if !ok {
		return ""
	}
	return nikofmt.KtTrim(s)
}

// ParseDevice is Rcdev1.parse: the device package in text.
func ParseDevice(text string) (Registration, error) {
	return parseToken(rcdev1Token, "rcdev1:", text, func(o map[string]any) (Registration, error) {
		r, err := fields(o, "rcdev1")
		if err != nil {
			return r, err
		}
		if r.Subscription, err = subscriptionFromRegistration(o); err != nil {
			return r, err
		}
		if api := object(o, "api"); api != nil {
			info, err := apiFromJSON(api)
			if err != nil {
				return r, fmt.Errorf("rcdev1: %s", err)
			}
			r.API = &info
		}
		r.Role = roleOf(o)
		r.DNSName, r.Addrs = namesOf(o)
		return r, nil
	})
}

// inviteFromJSON is Rcinv1.fromJson: an invite's JSON to the invite, checked; rcinv2's and rcinv3's as rcinv1's.
func inviteFromJSON(o map[string]any, name string) (Invite, error) {
	r, err := fields(o, name)
	if err != nil {
		return Invite{}, err
	}
	api := object(o, "api")
	if api == nil {
		return Invite{}, errors.New(name + ": no api")
	}
	info, err := apiFromJSON(api)
	if err != nil {
		return Invite{}, fmt.Errorf("%s: %s", name, err)
	}
	dnsName, addrs := namesOf(o)
	expires, err := long(o, "expires")
	if err != nil {
		return Invite{}, err
	}
	pin, _ := o["pin"].(bool)
	return Invite{Host: r.Host, SPA: r.SPA, InviteID: r.DeviceID, InviteKey: r.DeviceKey, Services: r.Services,
		API: info, Expires: expires, PinRequired: pin, DNSName: dnsName, Addrs: addrs}, nil
}

// IsInvite is RayImport.isInvite: an invite in text, any format.
func IsInvite(text string) bool {
	return findToken(rcinv3Token, text) != nil || findToken(rcinv2Token, text) != nil ||
		findToken(rcinv1Token, text) != nil
}

// Matches is RayImport.matches: an invite or a device package in text.
func Matches(text string) bool { return IsInvite(text) || findToken(rcdev1Token, text) != nil }

// ParseInvite is RayImport.parseInvite: the invite in text, RCINV3, RCINV2 or rcinv1.
func ParseInvite(text string) (Invite, error) {
	if at := findToken(rcinv3Token, text); at != nil {
		return readShort(substringAfter(text[at[0]:at[1]], ":"), 3)
	}
	if at := findToken(rcinv2Token, text); at != nil {
		return readShort(substringAfter(text[at[0]:at[1]], ":"), 2)
	}
	return parseToken(rcinv1Token, "rcinv1:", text, func(o map[string]any) (Invite, error) {
		return inviteFromJSON(o, "rcinv1")
	})
}

var hostLabel = regexp.MustCompile(`^[A-Za-z0-9-]{1,63}$`)

// shortReader reads the binary layout of RCINV2 and RCINV3.
type shortReader struct {
	b    []byte
	name string
}

var errCut = errors.New("cut short")

func (r *shortReader) bytes(n int) ([]byte, error) {
	if len(r.b) < n {
		return nil, errors.New(r.name + ": cut short")
	}
	out := r.b[:n]
	r.b = r.b[n:]
	return out, nil
}

func (r *shortReader) u16() (int64, error) {
	b, err := r.bytes(2)
	if err != nil {
		return 0, err
	}
	return int64(binary.BigEndian.Uint16(b)), nil
}

func (r *shortReader) u32() (int64, error) {
	b, err := r.bytes(4)
	if err != nil {
		return 0, err
	}
	return int64(binary.BigEndian.Uint32(b)), nil
}

func (r *shortReader) hex(n int) (string, error) {
	b, err := r.bytes(n)
	if err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}

// hostName is a length and the ASCII of a host name, each label letters, digits and hyphens.
func (r *shortReader) hostName(what string) (string, error) {
	n, err := r.bytes(1)
	if err != nil {
		return "", err
	}
	raw, err := r.bytes(int(n[0]))
	if err != nil {
		return "", err
	}
	text := string(raw)
	ok := len(text) >= 1 && len(text) <= 253
	for _, label := range strings.Split(text, ".") {
		ok = ok && hostLabel.MatchString(label)
	}
	if !ok {
		return "", fmt.Errorf("%s: invalid %s", r.name, what)
	}
	return text, nil
}

// readShort is Rcinv2.read: the binary layout of rcinv2 (version 2) and rcinv3 (3: the node's name after the host,
// and whether the node takes SPA v3), in base32 code, read into the rcinv1 JSON and checked as one.
func readShort(code string, version int) (Invite, error) {
	name := "rcinv" + strconv.Itoa(version)
	raw, err := base32Decode(code)
	if err != nil {
		return Invite{}, errors.New(name + ": not base32")
	}
	r := &shortReader{b: raw, name: name}
	head, err := r.bytes(2)
	if err != nil {
		return Invite{}, err
	}
	if int(int8(head[0])) != version {
		return Invite{}, errors.New(name + ": unsupported version")
	}
	flags := int(head[1])
	known := 0x07
	if version == 3 {
		// rcinv3: bit 3, the node's name follows the host; bit 4, the node takes SPA v3
		known = 0x1f
	}
	if flags&^known != 0 {
		return Invite{}, errors.New(name + ": unknown flags")
	}
	hostKind := (flags >> 1) & 0x03
	var host string
	switch hostKind {
	case 0:
		b, err := r.bytes(4)
		if err != nil {
			return Invite{}, err
		}
		host = netip.AddrFrom4([4]byte(b)).String()
	case 1:
		b, err := r.bytes(16)
		if err != nil {
			return Invite{}, err
		}
		host = ipv6Short(b)
	case 2:
		if host, err = r.hostName("host name"); err != nil {
			return Invite{}, err
		}
	default:
		return Invite{}, errors.New(name + ": unknown kind of host")
	}
	dnsName := ""
	if flags&0x08 != 0 {
		// only beside an address, which is its fallback; never one that reads as an address
		if hostKind == 2 {
			return Invite{}, errors.New(name + ": a node name beside a host name")
		}
		if dnsName, err = r.hostName("node name"); err != nil {
			return Invite{}, err
		}
		last := dnsName[strings.LastIndexByte(dnsName, '.')+1:]
		if Name(dnsName) == "" || strings.Trim(last, "0123456789") == "" {
			return Invite{}, errors.New(name + ": invalid node name")
		}
	}
	spa := map[string]any{}
	read := []struct {
		key string
		get func() (any, error)
	}{
		{"node_id", func() (any, error) { return r.hex(16) }},
		{"epoch_len_s", func() (any, error) { return num(r.u32()) }},
		{"slot_len_s", func() (any, error) { return num(r.u32()) }},
		{"port_key", func() (any, error) { return r.hex(32) }},
		{"port_base", func() (any, error) { return num(r.u16()) }},
		{"port_range", func() (any, error) { return num(r.u16()) }},
		{"len_min", func() (any, error) { return num(r.u16()) }},
		{"len_max", func() (any, error) { return num(r.u16()) }},
	}
	for _, f := range read {
		v, err := f.get()
		if err != nil {
			return Invite{}, err
		}
		spa[f.key] = v
	}
	deviceID, err := r.hex(8)
	if err != nil {
		return Invite{}, err
	}
	deviceKey, err := r.hex(32)
	if err != nil {
		return Invite{}, err
	}
	// one service, the API: kind and name are not sent
	serviceID, err := num(r.u16())
	if err != nil {
		return Invite{}, err
	}
	apiPort, err := r.u16()
	if err != nil {
		return Invite{}, err
	}
	pinBytes, err := r.bytes(32)
	if err != nil {
		return Invite{}, err
	}
	expires, err := r.u32()
	if err != nil {
		return Invite{}, err
	}
	if expires == 0 || apiPort == 0 {
		return Invite{}, errors.New(name + ": no expiry or no API port")
	}
	o := map[string]any{
		"v":          json.Number("1"),
		"host":       host,
		"spa":        spa,
		"device_id":  deviceID,
		"device_key": deviceKey,
		"services":   []any{map[string]any{"id": serviceID, "kind": "api", "name": "api"}},
		"api": map[string]any{"port": json.Number(strconv.FormatInt(apiPort, 10)),
			"pin": base64.RawURLEncoding.EncodeToString(pinBytes)},
		"expires": json.Number(strconv.FormatInt(expires, 10)),
	}
	if flags&0x01 != 0 {
		o["pin"] = true
	}
	// the package's dns_name with the host for its address (names-v1 §3)
	if dnsName != "" {
		o["dns_name"] = nikofmt.KtLowercase(dnsName)
		o["addrs"] = []any{host}
	}
	if flags&0x10 != 0 {
		spa["versions"] = []any{json.Number("2"), json.Number("3")}
	}
	if len(r.b) > 0 {
		return Invite{}, errors.New(name + ": bytes after its end")
	}
	return inviteFromJSON(o, name)
}

func num(v int64, err error) (any, error) {
	if err != nil {
		return nil, err
	}
	return json.Number(strconv.FormatInt(v, 10)), nil
}

// base32Decode is Rcinv2.base32: RFC 4648 without padding, either case, the last character's spare bits zero.
func base32Decode(text string) ([]byte, error) {
	const alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
	var out []byte
	buffer, bits := 0, 0
	for i := 0; i < len(text); i++ {
		c := text[i]
		if c >= 'a' && c <= 'z' {
			c -= 'a' - 'A'
		}
		v := strings.IndexByte(alphabet, c)
		if v < 0 {
			return nil, errCut
		}
		buffer = (buffer<<5 | v) & 0xffff
		bits += 5
		if bits >= 8 {
			bits -= 8
			out = append(out, byte(buffer>>bits))
		}
	}
	// the rest must be the padding bits of the last character, zero
	if bits >= 5 || buffer&(1<<bits-1) != 0 {
		return nil, errCut
	}
	return out, nil
}

// ipv6Short is an IPv6 address in the app's short form: the longest run of two zero groups or more as "::", the
// first such run where two are as long, the groups in lower-case hex without leading zeros (no IPv4 tail).
func ipv6Short(raw []byte) string {
	groups := make([]int, 8)
	for i := range groups {
		groups[i] = int(raw[2*i])<<8 | int(raw[2*i+1])
	}
	bestStart, bestLen := -1, 0
	for i := 0; i < 8; {
		if groups[i] != 0 {
			i++
			continue
		}
		j := i
		for j < 8 && groups[j] == 0 {
			j++
		}
		if j-i > bestLen && j-i >= 2 {
			bestStart, bestLen = i, j-i
		}
		i = j
	}
	part := func(from, to int) string {
		var s []string
		for _, g := range groups[from:to] {
			s = append(s, strconv.FormatInt(int64(g), 16))
		}
		return strings.Join(s, ":")
	}
	if bestStart < 0 {
		return part(0, 8)
	}
	return part(0, bestStart) + "::" + part(bestStart+bestLen, 8)
}

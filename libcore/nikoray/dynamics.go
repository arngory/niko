package nikoray

import (
	"encoding/json"
	"net/netip"
	"net/url"
	"regexp"
	"slices"
	"sort"
	"strings"
)

// Relay is a relay in front of a node (relay-v1 §4): its address, its own SPA parameters,
// optional pinned subscription certificate, and optional probe-v1 endpoint.
type Relay struct {
	ID     string      `json:"id"`
	Host   string      `json:"host"`
	SPA    SPAParams   `json:"spa"`
	SubPin string      `json:"sub_pin,omitempty"`
	Probe  *RelayProbe `json:"probe,omitempty"`
}

// RelayProbe is relays[].probe (relay-v1 §9.2): the relay's pinned probe endpoint.
type RelayProbe struct {
	V      int    `json:"v"`
	Port   int    `json:"port"`
	Pin    string `json:"pin"`
	UDP    bool   `json:"udp,omitempty"`
	Report bool   `json:"report,omitempty"`
	Svc    *int   `json:"svc,omitempty"`
}

// RelayViaSep separates a profile row's service key from its relay ID.
const RelayViaSep = "#via-"

// ViaKey returns the row key for a service profile routed through relayID.
func ViaKey(key, relayID string) string { return key + RelayViaSep + relayID }

// RelayOfKey extracts the relay ID from a relay row key, or "" for a direct row.
func RelayOfKey(key string) string {
	_, relay, ok := strings.Cut(key, RelayViaSep)
	if !ok {
		return ""
	}
	return relay
}

// ViaLink rewrites a direct profile link to dial through relay (relay-v1 §4):
// replaces the host with relay.Host, pins Hysteria2 UDP port to 443 and drops mport hopping.
func ViaLink(link string, relay Relay) (string, bool) {
	scheme, rest, ok := strings.Cut(link, "://")
	if !ok || scheme == "" || strings.TrimSpace(relay.Host) == "" {
		return "", false
	}
	if _, err := url.Parse("https://" + rest); err != nil {
		return "", false
	}
	end := strings.IndexAny(rest, "/?#")
	if end < 0 {
		end = len(rest)
	}
	authority := rest[:end]
	tail := rest[end:]

	var userInfo, hostPort string
	if at := strings.LastIndex(authority, "@"); at >= 0 {
		userInfo = authority[:at]
		hostPort = authority[at+1:]
	} else {
		hostPort = authority
	}

	var port string
	if strings.HasPrefix(hostPort, "[") {
		if _, after, found := strings.Cut(hostPort, "]"); found {
			port = strings.TrimPrefix(after, ":")
		}
	} else if _, p, found := strings.Cut(hostPort, ":"); found {
		port = p
	}

	udp := scheme == "hysteria2" || scheme == "hy2"
	host := strings.Trim(strings.TrimSpace(relay.Host), "[]")
	if strings.Contains(host, ":") {
		host = "[" + host + "]"
	}
	newPort := port
	if udp {
		newPort = "443"
	}

	if qIdx := strings.IndexByte(tail, '?'); qIdx >= 0 {
		prefix := tail[:qIdx]
		afterQ := tail[qIdx+1:]
		query, frag, hasFrag := strings.Cut(afterQ, "#")
		var kept []string
		for _, part := range strings.Split(query, "&") {
			k, _, _ := strings.Cut(part, "=")
			if k != "mport" && part != "" {
				kept = append(kept, part)
			}
		}
		tail = prefix
		if len(kept) > 0 {
			tail += "?" + strings.Join(kept, "&")
		}
		if hasFrag {
			tail += "#" + frag
		}
	}

	var b strings.Builder
	b.WriteString(scheme)
	b.WriteString("://")
	if userInfo != "" {
		b.WriteString(userInfo)
		b.WriteByte('@')
	}
	b.WriteString(host)
	if newPort != "" {
		b.WriteByte(':')
		b.WriteString(newPort)
	}
	b.WriteString(tail)
	return b.String(), true
}

// RelaySPAJSON builds the SPA JSON parameters for knocking at a relay with K_relay derivation.
func (r Relay) SPAJSON(node *Node) map[string]any {
	p := r.SPA
	o := map[string]any{
		"node_id":     p.NodeID,
		"epoch_len_s": p.EpochLenS,
		"slot_len_s":  p.SlotLenS,
		"port_key":    p.PortKey,
		"port_base":   p.PortBase,
		"port_range":  p.PortRange,
		"len_min":     p.LenMin,
		"len_max":     p.LenMax,
		"device_key":  node.DeviceKey,
		"relay": map[string]any{
			"exit_node_id": node.SPA.NodeID,
			"id":           r.ID,
		},
	}
	if p.V3() {
		o["versions"] = p.Versions
	}
	return o
}

// KnockOptionsForRelay builds the sing-box knock outbound options for a profile routed through relay.
func (n *Node) KnockOptionsForRelay(relay Relay) map[string]any {
	p := relay.SPA
	spa := map[string]any{
		"node_id":        p.NodeID,
		"epoch_len_s":    p.EpochLenS,
		"slot_len_s":     p.SlotLenS,
		"port_key":       p.PortKey,
		"port_base":      int64(p.PortBase),
		"port_range":     int64(p.PortRange),
		"len_min":        int64(p.LenMin),
		"len_max":        int64(p.LenMax),
		"device_key_ref": n.KeyRef(),
		"relay": map[string]any{
			"exit_node_id": n.SPA.NodeID,
			"id":           relay.ID,
		},
	}
	if p.V3() {
		list := make([]any, 0, len(p.Versions))
		for _, v := range p.Versions {
			list = append(list, int64(v))
		}
		spa["versions"] = list
	}
	return map[string]any{"spa_v2": spa, "silence": "15s"}
}

func parseRelays(list []any) []Relay {
	var out []Relay
	seen := map[string]bool{}
	for _, raw := range list {
		o, ok := raw.(map[string]any)
		if !ok {
			continue
		}
		id, _ := primitiveString(o, "id")
		host, _ := primitiveString(o, "host")
		if strings.TrimSpace(id) == "" || strings.TrimSpace(host) == "" || seen[id] {
			continue
		}
		if strings.ContainsAny(host, " \t\r\n?/#@") {
			continue
		}
		spaObj := object(o, "spa")
		if spaObj == nil {
			continue
		}
		spaBytes, err := json.Marshal(spaObj)
		if err != nil {
			continue
		}
		var spa SPAParams
		if json.Unmarshal(spaBytes, &spa) != nil || spa.NodeID == "" {
			continue
		}
		r := Relay{ID: id, Host: host, SPA: spa}
		if pin, ok := primitiveString(o, "sub_pin"); ok {
			r.SubPin = pin
		}
		if po := object(o, "probe"); po != nil {
			if v, ok := asInt(po["v"]); ok && v == 1 {
				port, _ := asInt(po["port"])
				pin, _ := primitiveString(po, "pin")
				if port >= 1 && port <= 65535 && strings.TrimSpace(pin) != "" {
					udp, _ := asBool(po["udp"])
					rep, _ := asBool(po["report"])
					rp := &RelayProbe{V: 1, Port: int(port), Pin: pin, UDP: udp, Report: rep}
					if svc, ok := asInt(po["svc"]); ok {
						s := int(svc)
						rp.Svc = &s
					}
					r.Probe = rp
				}
			}
		}
		seen[id] = true
		out = append(out, r)
		if len(out) >= 16 {
			break
		}
	}
	return out
}

// ProbeInfo is the subscription's `probe` descriptor (probe-v1 & dnsprobe-v1).
type ProbeInfo struct {
	V        int      `json:"v"`
	Tunnel   string   `json:"tunnel,omitempty"`
	UDP      bool     `json:"udp,omitempty"`
	DNSKey   string   `json:"dns_key,omitempty"`
	Report   []string `json:"report,omitempty"`
	Services []int    `json:"services,omitempty"`
}

// ReachesThrough returns true if the node accepts tunnel probes through service svc.
func (p *ProbeInfo) ReachesThrough(svc int) bool {
	if p == nil || p.Tunnel == "" {
		return false
	}
	if len(p.Services) == 0 {
		return true
	}
	return slices.Contains(p.Services, svc)
}

func parseProbe(o map[string]any) *ProbeInfo {
	if o == nil {
		return nil
	}
	v, ok := asInt(o["v"])
	if !ok || v != 1 {
		return nil
	}
	p := &ProbeInfo{V: 1}
	p.Tunnel, _ = primitiveString(o, "tunnel")
	p.UDP, _ = asBool(o["udp"])
	p.DNSKey, _ = primitiveString(o, "dns_key")
	if rep, ok := array(o, "report"); ok {
		for _, item := range rep {
			if s, ok := gsonString(item); ok && s != "" {
				p.Report = append(p.Report, s)
			}
		}
	}
	if svcs, ok := array(o, "services"); ok {
		for _, item := range svcs {
			if n, ok := asInt(item); ok {
				p.Services = append(p.Services, int(n))
			}
		}
	}
	return p
}

// Hints holds network-level hints published by the node in the subscription.
type Hints struct {
	UDPBlocked bool   `json:"udp_blocked,omitempty"`
	UDPMTU     string `json:"udp_mtu,omitempty"`
	ViaRelay   bool   `json:"via_relay,omitempty"`
	DNS        string `json:"dns,omitempty"`
}

func parseHints(o map[string]any) *Hints {
	if o == nil {
		return nil
	}
	h := &Hints{}
	h.UDPBlocked, _ = asBool(o["udp_blocked"])
	h.UDPMTU, _ = primitiveString(o, "udp_mtu")
	h.ViaRelay, _ = asBool(o["via_relay"])
	h.DNS, _ = primitiveString(o, "dns")
	if !h.UDPBlocked && h.UDPMTU == "" && !h.ViaRelay && h.DNS == "" {
		return nil
	}
	return h
}

// AppRelease is one release advertised by a raycontrol node's app mirror (app-update-v1:
// {"app":{"niko-desktop":[{"tag":"v0.2.0","prerelease":false}, ...]}}).
type AppRelease struct {
	Tag        string `json:"tag"`
	Prerelease bool   `json:"prerelease,omitempty"`
}

const maxAppReleases = 2

var appReleaseTagPattern = regexp.MustCompile(`^v\d{1,6}\.\d{1,6}\.\d{1,6}(-[0-9A-Za-z][0-9A-Za-z.-]{0,31})?$`)

// parseAppReleases parses app-update-v1 releases for desktop ("niko-desktop") matching RayAppRelease.of:
// strict semver tag regex, prerelease flag matching presence of '-', deduped, capped at 2 entries.
func parseAppReleases(o map[string]any) []AppRelease {
	if o == nil {
		return nil
	}
	arr, ok := array(o, "niko-desktop")
	if !ok {
		return nil
	}
	seen := map[string]bool{}
	var out []AppRelease
	for _, item := range arr {
		m, ok := item.(map[string]any)
		if !ok {
			continue
		}
		tag, ok := primitiveString(m, "tag")
		if !ok || !appReleaseTagPattern.MatchString(tag) {
			continue
		}
		wantPre := strings.Contains(tag, "-")
		gotPre, ok := asBool(m["prerelease"])
		if !ok || gotPre != wantPre {
			continue
		}
		if seen[tag] {
			continue
		}
		seen[tag] = true
		out = append(out, AppRelease{Tag: tag, Prerelease: wantPre})
		if len(out) >= maxAppReleases {
			break
		}
	}
	return out
}

// --- LAN (lan-v1) ---

const (
	LanZone          = "niko.internal"
	LanPreviousFor   = 2 * 86400
	LanDefaultRelay  = 20
	lanMaxDevices    = 256
)

var (
	lanDeviceIDPattern = regexp.MustCompile(`^[0-9a-f]{8,64}$`)
	lanLabelPattern    = regexp.MustCompile(`^[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?$`)
)

// LanDevice is one peer device of the user on the node's Yggdrasil LAN (lan-v1 §1.2).
type LanDevice struct {
	ID       string `json:"id"`
	Name     string `json:"name"`
	Label    string `json:"label"`
	Addr     string `json:"addr"`
	NextAddr string `json:"next_addr,omitempty"`
	NextFrom int64  `json:"next_from,omitempty"`
}

// AddressAt returns the active Yggdrasil address at nodeNow.
func (d LanDevice) AddressAt(nodeNow int64) string {
	if d.NextAddr != "" && d.NextFrom > 0 && d.NextFrom <= nodeNow {
		return d.NextAddr
	}
	return d.Addr
}

// LanPrevious is a recently rotated peer address still accepted for LanPreviousFor seconds.
type LanPrevious struct {
	ID    string `json:"id"`
	Addr  string `json:"addr"`
	Until int64  `json:"until"`
}

// Lan is the subscription's `lan` object (lan-v1 §1.2).
type Lan struct {
	V            int         `json:"v"`
	Svc          int         `json:"svc"`
	RelayMbps    int         `json:"relay"`
	Label        string      `json:"label"`
	Password     string      `json:"password,omitempty"`
	NextPassword string      `json:"next_password,omitempty"`
	NextFrom     int64       `json:"next_from,omitempty"`
	Devices      []LanDevice `json:"devices,omitempty"`
}

// Shown reports whether this device is currently visible on the LAN (node sent password).
func (l *Lan) Shown() bool { return l != nil && l.Password != "" }

// PasswordAt returns the active multicast password at nodeNow.
func (l *Lan) PasswordAt(nodeNow int64) string {
	if l == nil {
		return ""
	}
	if l.NextPassword != "" && l.NextFrom > 0 && l.NextFrom <= nodeNow {
		return l.NextPassword
	}
	return l.Password
}

// Others returns all LAN peer devices except deviceID.
func (l *Lan) Others(deviceID string) []LanDevice {
	if l == nil {
		return nil
	}
	low := strings.ToLower(deviceID)
	var out []LanDevice
	for _, d := range l.Devices {
		if d.ID != low {
			out = append(out, d)
		}
	}
	return out
}

func validYggAddr(raw string) string {
	ip, err := netip.ParseAddr(strings.TrimSpace(raw))
	if err != nil || !ip.Is6() {
		return ""
	}
	b := ip.As16()
	// Yggdrasil 0200::/7
	if b[0]&0xfe != 0x02 {
		return ""
	}
	return ip.String()
}

func validLanPassword(s string) bool {
	if len(s) < 16 || len(s) > 64 {
		return false
	}
	for _, c := range s {
		if !((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '-' || c == '_') {
			return false
		}
	}
	return true
}

func parseLan(o map[string]any) *Lan {
	if o == nil {
		return nil
	}
	v, ok := asInt(o["v"])
	if !ok || v != 1 {
		return nil
	}
	svc, ok := asInt(o["svc"])
	if !ok || svc <= 0 {
		return nil
	}
	label, _ := primitiveString(o, "label")
	if !lanLabelPattern.MatchString(label) {
		return nil
	}
	relay := LanDefaultRelay
	if r, ok := asInt(o["relay"]); ok {
		relay = max(0, min(100_000, int(r)))
	}
	l := &Lan{V: 1, Svc: int(svc), RelayMbps: relay, Label: label}
	if pw, ok := primitiveString(o, "password"); ok && validLanPassword(pw) {
		l.Password = pw
	}
	if next := object(o, "next"); next != nil {
		if pw, ok := primitiveString(next, "password"); ok && validLanPassword(pw) {
			l.NextPassword = pw
			if from, ok := asLong(next["from"]); ok {
				l.NextFrom = from
			}
		}
	}
	if devs, ok := array(o, "devices"); ok {
		seen := map[string]bool{}
		for _, item := range devs {
			do, ok := item.(map[string]any)
			if !ok {
				continue
			}
			id, _ := primitiveString(do, "id")
			id = strings.ToLower(id)
			if !lanDeviceIDPattern.MatchString(id) || seen[id] {
				continue
			}
			addrStr, _ := primitiveString(do, "addr")
			addr := validYggAddr(addrStr)
			if addr == "" {
				continue
			}
			name, _ := primitiveString(do, "name")
			name = NodeName(name)
			if name == "" {
				name = id
			}
			devLabel, _ := primitiveString(do, "label")
			if !lanLabelPattern.MatchString(devLabel) {
				devLabel = "d-" + id[:min(8, len(id))]
			}
			d := LanDevice{ID: id, Name: name, Label: devLabel, Addr: addr}
			if next := object(do, "next"); next != nil {
				if na, ok := primitiveString(next, "addr"); ok {
					if valid := validYggAddr(na); valid != "" {
						d.NextAddr = valid
						if from, ok := asLong(next["from"]); ok {
							d.NextFrom = from
						}
					}
				}
			}
			seen[id] = true
			l.Devices = append(l.Devices, d)
			if len(l.Devices) >= lanMaxDevices {
				break
			}
		}
	}
	return l
}

// ActiveLan returns the node's LAN if enabled by the user and active on the node.
func (n *Node) ActiveLan() *Lan {
	if n == nil || !n.LanShow || n.Subscription == nil || !n.Lan.Shown() {
		return nil
	}
	return n.Lan
}

// LanNames computes the `<device>.<node>.niko.internal` -> Yggdrasil IPv6 address map across nodes (lan-v1 §1.3).
func LanNames(nodes []Node, now int64) map[string]string {
	var active []*Node
	for i := range nodes {
		if nodes[i].ActiveLan() != nil {
			active = append(active, &nodes[i])
		}
	}
	if len(active) == 0 {
		return nil
	}
	out := map[string]string{}
	for _, node := range active {
		lan := node.ActiveLan()
		clash := false
		for _, other := range active {
			if other != node && other.ActiveLan().Label == lan.Label && other.ID() < node.ID() {
				clash = true
				break
			}
		}
		nodeLabel := lan.Label
		if clash {
			prefix := lan.Label
			if len(prefix) > 58 {
				prefix = prefix[:58]
			}
			nodeLabel = prefix + "-" + strings.ToLower(node.ID()[:min(4, len(node.ID()))])
		}
		for _, dev := range lan.Others(node.DeviceID) {
			fqdn := dev.Label + "." + nodeLabel + "." + LanZone
			if _, exists := out[fqdn]; !exists {
				out[fqdn] = dev.AddressAt(now + node.ClockSkew)
			}
		}
	}
	return out
}

// --- Test Run Findings (RayTestFindings.kt parity) ---

const (
	CauseStalls      = "stalls"
	CauseTimeout     = "timeout"
	CauseReset       = "reset"
	CauseRefused     = "refused"
	CauseTLS         = "tls"
	CauseName        = "name"
	CauseUnreachable = "unreachable"
	CauseBusy        = "busy"
	CauseHTTP        = "http"
	CauseLimit       = "limit"
	CauseAuth        = "auth"
	CauseInUse       = "in_use"
	CauseOther       = "other"

	NoteNoNetwork      = "no_network"
	NoteClock          = "clock"
	NoteNoSubscription = "no_subscription"
	NoteNoDirect       = "no_direct"
	NoteUDPBlocked     = "udp_blocked"
	NoteUDPSmall       = "udp_small"
	NoteDNSNone        = "dns_none"
	NoteDNSSpare       = "dns_spare"
	NoteRelayOnly      = "relay_only"
	NoteDNSOnly        = "dns_only"
)

type ProfileFinding struct {
	Key         string  `json:"key"`
	Name        string  `json:"name"`
	Type        string  `json:"type"`
	Carrier     string  `json:"carrier"`
	Relay       string  `json:"relay,omitempty"`
	Works       bool    `json:"works"`
	Kbps        int64   `json:"kbps,omitempty"`
	FirstByteMs int64   `json:"first_byte_ms,omitempty"`
	Cause       string  `json:"cause,omitempty"`
	Error       string  `json:"error,omitempty"`
	StalledAtKB int64   `json:"stalled_at_kb,omitempty"`
	UDPOk       *bool   `json:"udp_ok,omitempty"`
	UDPLossPct  float64 `json:"udp_loss_pct,omitempty"`
}

type FindingNote struct {
	Note   string `json:"note"`
	Number int64  `json:"number,omitempty"`
	Detail string `json:"detail,omitempty"`
}

type TestFindings struct {
	Profiles []ProfileFinding `json:"profiles"`
	Notes    []FindingNote    `json:"notes"`
}

var (
	httpStatusRe  = regexp.MustCompile(`^\d{3} [a-z]`)
	naiveStatusRe = regexp.MustCompile(`unexpected response status: (\d{3})`)
)

// ClassifyProbeError maps a Go/core probe error string to a RayTestFindings cause.
func ClassifyProbeError(errStr string) string {
	low := strings.ToLower(strings.TrimSpace(errStr))
	if low == "" {
		return CauseOther
	}
	if m := naiveStatusRe.FindStringSubmatch(low); len(m) == 2 {
		switch m[1] {
		case "429":
			return CauseLimit
		case "401", "403", "404", "407":
			return CauseAuth
		default:
			return CauseHTTP
		}
	}
	switch {
	case strings.Contains(low, "too many connections"):
		return CauseLimit
	case strings.Contains(low, "429"):
		return CauseBusy
	case strings.Contains(low, "no such host") || strings.Contains(low, "lookup "):
		return CauseName
	case strings.Contains(low, "network is unreachable") || strings.Contains(low, "no route to host"):
		return CauseUnreachable
	case strings.Contains(low, "connection refused"):
		return CauseRefused
	case strings.Contains(low, "tls") || strings.Contains(low, "x509") || strings.Contains(low, "certificate") || strings.Contains(low, "reality"):
		return CauseTLS
	case strings.Contains(low, "reset") || strings.Contains(low, "eof") || strings.Contains(low, "broken pipe") || strings.Contains(low, "closed"):
		return CauseReset
	case strings.Contains(low, "timeout") || strings.Contains(low, "deadline exceeded"):
		return CauseTimeout
	case httpStatusRe.MatchString(low):
		return CauseHTTP
	default:
		return CauseOther
	}
}

func stepInt64(v any) (int64, bool) {
	switch n := v.(type) {
	case int:
		return int64(n), true
	case int32:
		return int64(n), true
	case int64:
		return n, true
	case float64:
		return int64(n), true
	case json.Number:
		i, err := n.Int64()
		return i, err == nil
	default:
		return asLong(v)
	}
}

// EvaluateTestSteps analyzes JSON step maps from a diagnostic test run into structured TestFindings.
func EvaluateTestSteps(steps []map[string]any) TestFindings {
	var notes []FindingNote
	var profiles []ProfileFinding
	started := map[string]map[string]any{}
	hasNote := func(n string) bool {
		for _, item := range notes {
			if item.Note == n {
				return true
			}
		}
		return false
	}

	for _, step := range steps {
		kind, _ := step["step"].(string)
		switch kind {
		case "start":
			if netKind, _ := step["net"].(string); netKind == "none" && !hasNote(NoteNoNetwork) {
				notes = append(notes, FindingNote{Note: NoteNoNetwork})
			}
		case "end":
			if errStr, _ := step["error"].(string); errStr == "no network" && !hasNote(NoteNoNetwork) {
				notes = append(notes, FindingNote{Note: NoteNoNetwork})
			}
		case "direct":
			carrier, _ := step["spa_carrier"].(string)
			if carrier == "" {
				errStr, _ := step["error"].(string)
				notes = append(notes, FindingNote{Note: NoteNoDirect, Detail: errStr})
			} else if udpProbed, _ := step["udp_probed"].(bool); udpProbed {
				if mtu, ok := stepInt64(step["udp_mtu"]); ok {
					if mtu <= 0 {
						notes = append(notes, FindingNote{Note: NoteUDPBlocked})
					} else if mtu < 1200 {
						notes = append(notes, FindingNote{Note: NoteUDPSmall, Number: mtu})
					}
				}
			}
		case "subscription":
			if errStr, _ := step["error"].(string); errStr != "" {
				notes = append(notes, FindingNote{Note: NoteNoSubscription, Detail: errStr})
			}
			if skew, ok := stepInt64(step["clock_skew_s"]); ok && (skew > 30 || skew < -30) {
				notes = append(notes, FindingNote{Note: NoteClock, Number: skew})
			}
		case "dns_pick":
			dnsKind, _ := step["kind"].(string)
			if dnsKind == "" {
				notes = append(notes, FindingNote{Note: NoteDNSNone})
			} else if zone, ok := stepInt64(step["zone"]); ok && zone > 0 {
				notes = append(notes, FindingNote{Note: NoteDNSSpare, Number: zone})
			}
		case "profile_start":
			if key, _ := step["key"].(string); key != "" {
				started[key] = step
			}
		case "profile":
			key, _ := step["key"].(string)
			if key == "" {
				continue
			}
			st := started[key]
			name, _ := st["name"].(string)
			if name == "" {
				name = key
			}
			typ, _ := st["type"].(string)
			carrier, _ := st["carrier"].(string)
			if carrier == "" {
				carrier = "tcp"
			}
			relay, _ := st["relay"].(string)
			if errStr, _ := step["error"].(string); errStr != "" {
				profiles = append(profiles, ProfileFinding{
					Key: key, Name: name, Type: typ, Carrier: carrier, Relay: relay,
					Works: false, Cause: CauseInUse, Error: errStr, StalledAtKB: -1,
				})
				continue
			}
			works, _ := step["tcp_ok"].(bool)
			stalledAt := int64(-1)
			if v, ok := stepInt64(step["stalled_at"]); ok {
				stalledAt = v
			}
			tcpErr, _ := step["tcp_error"].(string)
			kbps, _ := stepInt64(step["kbps"])
			firstByte, _ := stepInt64(step["first_byte_ms"])
			cause := ""
			if !works {
				if stalledAt >= 0 {
					cause = CauseStalls
				} else {
					cause = ClassifyProbeError(tcpErr)
				}
			}
			stalledKB := int64(-1)
			if stalledAt >= 0 {
				stalledKB = stalledAt / 1024
			}
			pf := ProfileFinding{
				Key: key, Name: name, Type: typ, Carrier: carrier, Relay: relay,
				Works: works, Kbps: kbps, FirstByteMs: firstByte, Cause: cause, Error: tcpErr,
				StalledAtKB: stalledKB,
			}
			if uok, ok := step["udp_ok"].(bool); ok {
				pf.UDPOk = &uok
			}
			if uloss, ok := step["udp_loss_pct"].(float64); ok {
				pf.UDPLossPct = uloss
			}
			profiles = append(profiles, pf)
		}
	}

	var directCount, directWorks, relayWorks, dnsWorks int
	for _, p := range profiles {
		if p.Cause == CauseInUse {
			continue
		}
		if p.Relay == "" && p.Carrier != "dns" {
			directCount++
			if p.Works {
				directWorks++
			}
		} else if p.Relay != "" && p.Works {
			relayWorks++
		} else if p.Carrier == "dns" && p.Works {
			dnsWorks++
		}
	}
	if directCount > 0 && directWorks == 0 {
		if relayWorks > 0 {
			notes = append(notes, FindingNote{Note: NoteRelayOnly})
		} else if dnsWorks > 0 {
			notes = append(notes, FindingNote{Note: NoteDNSOnly})
		}
	}

	// Sort working profiles fastest first (measured kbps desc, else first-byte latency asc).
	sort.SliceStable(profiles, func(i, j int) bool {
		if profiles[i].Works != profiles[j].Works {
			return profiles[i].Works
		}
		if profiles[i].Works {
			if profiles[i].Kbps != profiles[j].Kbps {
				return profiles[i].Kbps > profiles[j].Kbps
			}
			return profiles[i].FirstByteMs < profiles[j].FirstByteMs
		}
		return false
	})

	return TestFindings{Profiles: profiles, Notes: notes}
}

// SummaryLine formats the redacted JSON summary line appended to a diagnostic report.
func (f TestFindings) SummaryLine(runID string) string {
	var working, dnsFailing, noUDP []string
	failing := map[string][]string{}
	for _, p := range f.Profiles {
		if p.Works {
			working = append(working, p.Key)
			if p.UDPOk != nil && !*p.UDPOk {
				noUDP = append(noUDP, p.Key)
			}
		} else if p.Cause != CauseInUse {
			if p.Carrier == "dns" {
				dnsFailing = append(dnsFailing, p.Key)
			} else {
				c := p.Cause
				if c == "" {
					c = CauseOther
				}
				failing[c] = append(failing[c], p.Key)
			}
		}
	}
	if working == nil {
		working = []string{}
	}
	out := map[string]any{
		"run":     runID,
		"step":    "findings",
		"working": working,
		"failing": failing,
		"notes":   f.Notes,
	}
	if len(dnsFailing) > 0 {
		out["dns_failing"] = dnsFailing
	}
	if len(noUDP) > 0 {
		out["no_udp"] = noUDP
	}
	raw, _ := json.Marshal(out)
	return string(raw)
}

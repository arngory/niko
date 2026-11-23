package nikoray

import (
	"errors"
	"slices"
	"strings"
	"unicode"
	"unicode/utf8"

	"libcore/nikofmt"
)

// A node the desktop keeps (RayNodes.kt RayNode), the static part: what its package and its last subscription say,
// the device's credentials, the node's group and the rows it holds. As JSON the app's names (RayNode.toJson), but
// for the device's key and client keys, which the app keeps wrapped by its Keystore (device_key_wrapped, key_wrapped)
// and the desktop's store seals (nikostore): here they are in the clear, the store's sealer keeps them on the disk.
// The adaptive part (the channel's state, Brutal, the watchdog, the LAN, relays, the probe) is stage 4's.

// Stopped is RefreshAt of a node that refused this device: no fetch is planned, a new package is to be imported.
const Stopped = -1

// Links are a subscription row's credentials (RayLinks): the row holds Link or, from From on (the node's clock),
// Next; NextActive says which one it holds now.
type Links struct {
	Name       string `json:"name"`
	Link       string `json:"link"`
	Next       string `json:"next,omitempty"`
	From       int64  `json:"from"`
	NextActive bool   `json:"next_active"`
}

// ActiveLink is the link the row holds now.
func (l Links) ActiveLink() string {
	if l.NextActive && l.Next != "" {
		return l.Next
	}
	return l.Link
}

// Identity is the device's client certificate for the node's remote API (RayIdentity), the key in the clear.
type Identity struct {
	Cert string `json:"cert"`
	// Key is the private key, base64 PKCS #8: a secret of the device's
	Key       string `json:"key"`
	Pin       string `json:"pin"`
	CreatedAt int64  `json:"created_at"`
}

// Traffic is the subscription's traffic (traffic-limit-v1 §4): bytes used since Since, the limit, the next reset
// (0: none, until the admin raises it), and whether the node says it is used up.
type Traffic struct {
	V         int   `json:"v"`
	Used      int64 `json:"used"`
	Limit     int64 `json:"limit"`
	Since     int64 `json:"since"`
	Reset     int64 `json:"reset"`
	Exhausted bool  `json:"exhausted,omitempty"`
}

// NearLimit: 90 % of the limit used, not used up (RayTraffic.nearLimit).
func (t Traffic) NearLimit() bool { return !t.Exhausted && t.Limit > 0 && t.Used >= t.Limit/10*9 }

// ClientMin is the subscription's client (client-min-v1 §3): the node's minimum versions by product, from when they
// hold (0: already), and whether it refused this device for its version.
type ClientMin struct {
	V       int               `json:"v"`
	Min     map[string]string `json:"min"`
	From    int64             `json:"from,omitempty"`
	Refused bool              `json:"refused,omitempty"`
}

// Node is a node the desktop keeps.
type Node struct {
	Host string    `json:"host"`
	SPA  SPAParams `json:"spa"`
	// DeviceID and DeviceKey: the device's at the node; the key (hex) a secret
	DeviceID     string            `json:"device_id"`
	DeviceKey    string            `json:"device_key"`
	Services     []Service         `json:"services"`
	Subscription *SubscriptionInfo `json:"sub,omitempty"`
	// SubSeq is the last subscription taken (seq never goes back); GroupID the group its rows live in
	SubSeq  uint64 `json:"sub_seq"`
	GroupID int64  `json:"group_id"`
	// ProfileIDs: a row's key (the service id, svc#n for its n-th, ProfileKeys) to its profile
	ProfileIDs map[string]int64 `json:"profile_ids"`
	// RefreshAt is when to fetch the subscription again (unix s): 0 not planned, Stopped refused
	RefreshAt int64 `json:"refresh_at"`
	// Links: a row's key to its credentials, a secret
	Links map[string]Links `json:"links"`
	// ClockSkew is the node's clock minus ours at the last fetch (s)
	ClockSkew int64 `json:"clock_skew"`
	// DoH is the node's DoH (https://<name>/dns-query/<token>), the remote DNS while a row of the node runs, a secret;
	// DoHPins its key's pins (cert-pin-v1)
	DoH     string `json:"doh,omitempty"`
	DoHPins string `json:"doh_pins,omitempty"`
	// API and Identity: an admin device's remote API and client certificate; IdentityNext a rotation under way
	API          *APIInfo  `json:"api,omitempty"`
	Identity     *Identity `json:"identity,omitempty"`
	IdentityNext *Identity `json:"identity_next,omitempty"`
	// SPACarriers: the SPA carriers the node takes, in order; nil an older node's
	SPACarriers []string `json:"spa_carriers,omitempty"`
	// SPAVersions: the last subscription's spa.versions; nil before the first one
	SPAVersions []int `json:"spa_versions,omitempty"`
	// FailedFetches: fetches failed in a row, 0 after a success
	FailedFetches int `json:"failed_fetches,omitempty"`
	// SubUpdatedAt and SubExpires: when the last subscription was taken and until when it holds (this device's
	// clock); 0 unknown
	SubUpdatedAt int64 `json:"sub_updated_at,omitempty"`
	SubExpires   int64 `json:"sub_expires,omitempty"`
	// Name is the name the node's admin gave it, Note what it tells its users
	Name    string     `json:"name,omitempty"`
	Note    string     `json:"note,omitempty"`
	Traffic *Traffic   `json:"traffic,omitempty"`
	Client  *ClientMin `json:"client,omitempty"`
	// DNSName and Addrs: names-v1, the package's (or the last subscription's) name and addresses; SubAddrs the last
	// subscription's addresses
	DNSName  string   `json:"dns_name,omitempty"`
	Addrs    []string `json:"addrs,omitempty"`
	SubAddrs []string `json:"sub_addrs,omitempty"`
}

// ID is the node's id (spa.node_id).
func (n *Node) ID() string { return n.SPA.NodeID }

// KnownAddrs are the node's addresses its signed data names (RayNode.knownAddrs): the package's and the last
// subscription's, with its host when that is an address; none for a node before raycontrol v0.34.0.
func (n *Node) KnownAddrs() []string {
	if len(n.Addrs) == 0 && len(n.SubAddrs) == 0 {
		return nil
	}
	return distinct(append(append(append([]string{}, n.Addrs...), n.SubAddrs...), Addrs([]string{n.Host})...))
}

// ReachName is the node's name when it has addresses to fall back to, "" otherwise (reached by its host).
func (n *Node) ReachName() string {
	if len(n.KnownAddrs()) == 0 {
		return ""
	}
	return n.DNSName
}

// DisplayName is what the node is called: its admin's name, else "raycontrol · <host>".
func (n *Node) DisplayName() string { return GroupName(n.Host, n.Name) }

// GroupName is RayNode.groupName: the group of the node at host called name ("" none).
func GroupName(host, name string) string {
	if name != "" {
		return name
	}
	return "raycontrol · " + host
}

// KnockVersions are the SPA versions the node takes: the last subscription's, else the package's.
func (n *Node) KnockVersions() []int {
	if n.SPAVersions != nil {
		return n.SPAVersions
	}
	return n.SPA.Versions
}

// KeyRef is the key reference handed to the core (RayNode.keyRef): the key itself never enters the config.
func (n *Node) KeyRef() string { return "raycontrol:" + n.SPA.NodeID + ":" + n.DeviceID }

// MatchesHost is RayNode.matchesHost: address is the node's host or its name.
func (n *Node) MatchesHost(address string) bool {
	if nikofmt.KtIsBlank(address) {
		return false
	}
	a := strings.TrimSuffix(strings.TrimSuffix(strings.TrimPrefix(nikofmt.KtTrim(address), "["), "]"), ".")
	return strings.EqualFold(a, n.Host) || n.DNSName != "" && strings.EqualFold(a, n.DNSName)
}

// SPAJSON is the node's SPA parameters for libcore's own requests (RayNode.spaJson), with the carriers and the
// versions it takes, and the device's key.
func (n *Node) SPAJSON() map[string]any {
	p := n.SPA
	p.Versions = n.KnockVersions()
	o := map[string]any{"node_id": p.NodeID, "epoch_len_s": p.EpochLenS, "slot_len_s": p.SlotLenS,
		"port_key": p.PortKey, "port_base": p.PortBase, "port_range": p.PortRange, "len_min": p.LenMin,
		"len_max": p.LenMax, "device_key": n.DeviceKey}
	if p.V3() {
		o["versions"] = p.Versions
	}
	if n.SPACarriers != nil {
		o["carriers"] = n.SPACarriers
	}
	return o
}

// KnockOptions are the knock outbound's options for the node (knockOptions without a relay): SPA v2 with the device
// key by its reference, the carriers and versions it takes, its own addresses.
func (n *Node) KnockOptions() map[string]any {
	p := n.SPA
	spa := map[string]any{"node_id": p.NodeID, "epoch_len_s": p.EpochLenS, "slot_len_s": p.SlotLenS,
		"port_key": p.PortKey, "port_base": int64(p.PortBase), "port_range": int64(p.PortRange),
		"len_min": int64(p.LenMin), "len_max": int64(p.LenMax), "device_key_ref": n.KeyRef()}
	if versions := n.KnockVersions(); slices.Contains(versions, 3) {
		list := []any{}
		for _, v := range versions {
			list = append(list, int64(v))
		}
		spa["versions"] = list
	}
	if n.SPACarriers != nil {
		list := []any{}
		for _, c := range n.SPACarriers {
			list = append(list, c)
		}
		spa["carriers"] = list
	}
	o := map[string]any{"spa_v2": spa, "silence": "15s"}
	if addrs := n.KnownAddrs(); len(addrs) > 0 {
		list := []any{}
		for _, a := range addrs {
			list = append(list, a)
		}
		o["node_addresses"] = list
	}
	return o
}

// OwnerOf is RayNodes.ownerOf for this node: the key of its row with that profile id, "" for none.
func (n *Node) OwnerOf(profile int64) string {
	if profile <= 0 {
		return ""
	}
	for key, id := range n.ProfileIDs {
		if id == profile && !strings.HasPrefix(key, "stale:") {
			return key
		}
	}
	return ""
}

// distinct keeps each value once, in order (Kotlin's distinct): a set, not a scan of what is kept, which a node's
// list of addresses made quadratic.
func distinct(values []string) []string {
	var out []string
	seen := make(map[string]bool, len(values))
	for _, v := range values {
		if !seen[v] {
			seen[v] = true
			out = append(out, v)
		}
	}
	return out
}

// The app's roles of a device; others (billing, support: integrations-v1) are a bot's, with no subscription.
var appRoles = map[string]bool{"user": true, "admin": true}

// ErrNotForApp is a registration or package that is not for this app: nothing of it is kept.
var ErrNotForApp = errors.New("raycontrol: not for this app")

// IntegrationRoleError is a role that is not an app's: a bot's (integrations-v1).
type IntegrationRoleError struct{ Role string }

func (e *IntegrationRoleError) Error() string {
	return "raycontrol: the invite is for an integration (" + e.Role + "), not for an app"
}

func (e *IntegrationRoleError) Unwrap() error { return ErrNotForApp }

// ErrNoSubscription: a package with no subscription for a node whose kept package has one, a bot's from a node before
// roles were named.
var ErrNoSubscription = errors.New("raycontrol: the package has no subscription, and the one kept for its node has")

// AppRole is RayApi.appRole of a registration's answer's role: none or a blank one is user; one not an app's is an
// IntegrationRoleError.
func AppRole(role string) (string, error) {
	role = nikofmt.KtTrim(role)
	if role == "" {
		role = "user"
	}
	if !appRoles[role] {
		return "", &IntegrationRoleError{Role: role}
	}
	return role, nil
}

// CheckForApp is RayNodes.checkForApp: refused unless r is a package for this app, before anything is kept.
func CheckForApp(r Registration, old *Node) error {
	if r.Role != "" && !appRoles[r.Role] {
		return &IntegrationRoleError{Role: r.Role}
	}
	if r.Subscription == nil && old != nil && old.Subscription != nil {
		return ErrNoSubscription
	}
	return nil
}

// Import is RayNodes.import: the node of a registration, the same node's old record (nil: none) replaced, its group
// and rows kept. identity is the client certificate a registration through the API made; a package of the same
// device keeps the one it had.
func Import(r Registration, identity *Identity, old *Node) (*Node, error) {
	if err := CheckForApp(r, old); err != nil {
		return nil, err
	}
	n := &Node{Host: r.Host, SPA: r.SPA, DeviceID: r.DeviceID, DeviceKey: r.DeviceKey, Services: r.Services,
		Subscription: r.Subscription, API: r.API, Identity: identity, ProfileIDs: map[string]int64{},
		Links: map[string]Links{}, DNSName: r.DNSName, Addrs: r.Addrs}
	if old != nil {
		n.GroupID = old.GroupID
		n.ProfileIDs = old.ProfileIDs
		if identity == nil && old.DeviceID == r.DeviceID && r.API != nil {
			n.Identity = old.Identity
		}
		// an older package of the same node (same signing and TLS keys) must not let an older subscription in; a
		// node with new keys starts its seq anew
		if a, b := old.Subscription, r.Subscription; a != nil && b != nil && a.Pin == b.Pin && a.Ed25519 == b.Ed25519 &&
			a.MLDSA65 == b.MLDSA65 {
			n.SubSeq = old.SubSeq
		}
	}
	return n, nil
}

// maxName is how many characters (code points) of a node's name are shown.
const maxName = 48

// NodeName is RayNode.nodeName: raw as a name to show, "" for none. Control and format characters go, whitespace
// runs become one space, at most 48 characters stay.
func NodeName(raw string) string {
	var out strings.Builder
	space := false
	count := 0
	for _, cp := range raw {
		if count >= maxName {
			break
		}
		switch {
		case javaSpace(cp):
			space = out.Len() > 0
		case dropped(cp):
		default:
			if space {
				out.WriteByte(' ')
				count++
				space = false
			}
			if count < maxName {
				out.WriteRune(cp)
				count++
			}
		}
	}
	return strings.TrimFunc(out.String(), func(r rune) bool { return nikofmt.KtIsBlank(string(r)) })
}

// MaxNote is how many characters (code points) of a node's note are shown, as the node bounds it.
const MaxNote = 200

// maxMarks is how many marks one character of a note keeps.
const maxMarks = 4

func isMark(cp rune) bool { return unicode.In(cp, unicode.Mn, unicode.Me, unicode.Mc) }

func isTag(cp rune) bool { return cp >= 0xE0020 && cp <= 0xE007E }

// NodeNote is RayNode.nodeNote: raw as the node's note to show, "" for none; the name's rules and what emoji and
// scripts need, at most max characters, a longer note ending in "…".
func NodeNote(raw string, max int) string {
	runes := []rune(raw)
	var out []rune
	count := 0
	space := false
	var joiner rune
	marks := -1
	boundary := 0
	regional := 0
	cut := false
	put := func(cp rune) bool {
		if count >= max {
			cut = true
			return false
		}
		out = append(out, cp)
		count++
		return true
	}
	i := 0
loop:
	for i < len(runes) && !cut {
		cp := runes[i]
		i++
		switch {
		case javaSpace(cp):
			space = len(out) > 0
			joiner = 0
			marks = -1
			regional = 0
		case cp == 0x200C || cp == 0x200D:
			if marks >= 0 && !space && joiner == 0 {
				joiner = cp
			}
		case isMark(cp):
			if marks >= 0 && marks < maxMarks && joiner == 0 && !space && put(cp) {
				marks++
			}
		case dropped(cp):
		default:
			if cp >= 0x1F1E6 && cp <= 0x1F1FF {
				regional++
			} else {
				regional = 0
			}
			// a new character, unless it holds to the last: joined to it, a skin tone, a flag's second letter
			holds := joiner != 0 || cp >= 0x1F3FB && cp <= 0x1F3FF || regional > 0 && regional%2 == 0
			if !holds {
				boundary = utf16Count(out)
			}
			if space {
				if !put(' ') {
					break loop
				}
				space = false
			}
			if joiner != 0 {
				if !put(joiner) {
					break loop
				}
				joiner = 0
			}
			if !put(cp) {
				break loop
			}
			marks = 0
			// a subdivision flag: U+1F3F4, its tags and their end, whole or without the tags
			if cp == 0x1F3F4 {
				j := i
				for j < len(runes) && isTag(runes[j]) {
					j++
				}
				if j > i && j < len(runes) && runes[j] == 0xE007F {
					tags := runes[i : j+1]
					if count+len(tags) > max {
						cut = true
						break loop
					}
					out = append(out, tags...)
					count += len(tags)
					i = j + 1
				}
			}
		}
	}
	if cut {
		out = truncateUTF16(out, boundary)
	}
	text := strings.TrimFunc(string(out), func(r rune) bool { return nikofmt.KtIsBlank(string(r)) })
	if text == "" {
		return ""
	}
	if cut {
		return text + "…"
	}
	return text
}

// utf16Count is how many UTF-16 units runes are (StringBuilder.length).
func utf16Count(runes []rune) int {
	n := 0
	for _, r := range runes {
		if r >= 0x10000 {
			n += 2
		} else {
			n++
		}
	}
	return n
}

// truncateUTF16 is StringBuilder.setLength(n) at a code point's start.
func truncateUTF16(runes []rune, n int) []rune {
	units := 0
	for i, r := range runes {
		if units >= n {
			return runes[:i]
		}
		if r >= 0x10000 {
			units += 2
		} else {
			units++
		}
	}
	return runes
}

// javaSpace is Character.isWhitespace(cp) || Character.isSpaceChar(cp).
func javaSpace(cp rune) bool {
	switch {
	case cp == '\t' || cp == '\n' || cp == '\v' || cp == '\f' || cp == '\r' || cp >= 0x1C && cp <= 0x1F:
		return true
	}
	return unicode.In(cp, unicode.Zs, unicode.Zl, unicode.Zp)
}

// dropped is a character a name or a note leaves out: control, format, private use, surrogates; unassigned ones too,
// but not where emoji are added (U+1F000-U+1FAFF).
func dropped(cp rune) bool {
	if cp == utf8.RuneError {
		return false
	}
	if unicode.In(cp, unicode.Cc, unicode.Cf, unicode.Co, unicode.Cs) {
		return true
	}
	assigned := unicode.In(cp, unicode.L, unicode.M, unicode.N, unicode.P, unicode.S, unicode.Z)
	return !assigned && !(cp >= 0x1F000 && cp <= 0x1FAFF)
}

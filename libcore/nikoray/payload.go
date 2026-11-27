package nikoray

import (
	"encoding/json"
	"errors"
	"fmt"
	"maps"
	"regexp"
	"slices"
	"strings"

	"libcore/nikofmt"
)

// A node's subscription (raycontrol docs/spec/subscription-v1.md) as RaySubscription.parse reads its payload, the
// static part: the rows (a profile each, by its key, with its credentials), the node's DoH, and what the node record
// takes (its name, note, traffic, minimum version, names and addresses, SPA carriers and versions). The relays' rows
// (relay-v1, a draft), the probe, the hints, the LAN and the app's releases are stage 4's and left out.

// Row is a profile of the subscription: its key (ProfileKeys), its bean and its credentials.
type Row struct {
	Key   string
	Bean  nikofmt.Bean
	Links Links
}

// Parsed is what a payload gives the node and its rows.
type Parsed struct {
	Rows    []Row
	DoH     string
	DoHPins string
	// Skipped: rows that broke the spec, left out
	Skipped int
	// Carriers: the SPA carriers the node takes, nil an older node's
	Carriers []string
	Name     string
	Note     string
	Traffic  *Traffic
	Client   *ClientMin
	// DNSName and Addrs: names-v1, the payload's and its links' niko-ip; the name only with addresses
	DNSName     string
	Addrs       []string
	SPAVersions []int
	// Phase P6 dynamic fields parsed from subscription payload.
	Relays    []Relay
	Probe     *ProbeInfo
	Hints     *Hints
	Lan         *Lan
	AppReleases []AppRelease
}

// Parse is RaySubscription.parse: the rows of payload and what the node record takes of it; kinds are the node's
// service kinds by id, nodeNow the node's clock. An error where the payload is not one (the app's parse throws).
func Parse(payload []byte, kinds map[int]string, nodeNow int64, settings nikofmt.Settings) (*Parsed, error) {
	o, err := decodeObject(payload)
	if err != nil {
		return nil, errors.New("raycontrol subscription: bad payload")
	}
	return parseObject(o, kinds, nodeNow, settings)
}

func parseObject(payload map[string]any, kinds map[int]string, nodeNow int64, settings nikofmt.Settings) (*Parsed, error) {
	list, _ := array(payload, "profiles")
	var profiles []map[string]any
	for _, v := range list {
		p, ok := v.(map[string]any)
		if !ok {
			return nil, errors.New("raycontrol subscription: a profile is not an object")
		}
		profiles = append(profiles, p)
	}
	parsed := &Parsed{}
	// dnsauth-v2 (raycontrol v0.15): the node's niko-server checks the hint; its links still say niko-dnsauth=v1
	dnsAuthV2 := false
	if versions, ok := array(object(payload, "dnsauth"), "v"); ok {
		for _, v := range versions {
			if n, ok := asInt(v); ok && n == 2 {
				dnsAuthV2 = true
			}
		}
	}
	var linkAddrs []string
	for i, key := range ProfileKeys(profiles) {
		profile := profiles[i]
		row, addrs, isDoH, err := parseRow(profile, key, kinds, nodeNow, settings)
		// the addresses a row's links name count, whether the row is taken or not
		linkAddrs = append(linkAddrs, addrs...)
		if err != nil {
			parsed.Skipped++
			continue
		}
		if isDoH {
			if parsed.DoH == "" {
				// the pins first: a row whose list does not read is not taken without them
				pins, err := dohPins(row.Links.ActiveLink())
				if err != nil {
					parsed.Skipped++
					continue
				}
				doh, err := dohURL(row.Links.ActiveLink())
				if err != nil {
					parsed.Skipped++
					continue
				}
				parsed.DoH, parsed.DoHPins = doh, pins
			}
			continue
		}
		signWithHint(row.Bean, dnsAuthV2)
		parsed.Rows = append(parsed.Rows, row)
	}
	if carriers, ok := array(object(payload, "spa"), "carriers"); ok {
		for _, v := range carriers {
			if s, ok := gsonString(v); ok {
				parsed.Carriers = append(parsed.Carriers, s)
			}
		}
	}
	if name, ok := primitiveString(payload, "name"); ok {
		parsed.Name = NodeName(name)
	}
	if note, ok := primitiveString(payload, "note"); ok {
		parsed.Note = NodeNote(note, MaxNote)
	}
	parsed.Traffic = trafficOf(object(payload, "traffic"))
	parsed.Client = clientOf(object(payload, "client"))
	dnsName, payloadAddrs := namesOf(payload)
	parsed.Addrs = distinct(append(payloadAddrs, linkAddrs...))
	if len(parsed.Addrs) > 0 {
		parsed.DNSName = dnsName
	}
	parsed.SPAVersions = spaVersions(object(payload, "spa"))
	if relayList, ok := array(payload, "relays"); ok {
		parsed.Relays = parseRelays(relayList)
	}
	parsed.Probe = parseProbe(object(payload, "probe"))
	parsed.Hints = parseHints(object(payload, "hints"))
	parsed.Lan = parseLan(object(payload, "lan"))
	parsed.AppReleases = parseAppReleases(object(payload, "app"))

	// Generate relay profile rows (#via-<relayId>) for each eligible direct row (relay-v1 §4).
	if len(parsed.Relays) > 0 && len(parsed.Rows) > 0 {
		directRows := append([]Row(nil), parsed.Rows...)
		for _, relay := range parsed.Relays {
			for _, direct := range directRows {
				active := direct.Links.ActiveLink()
				via, ok := ViaLink(active, relay)
				if !ok {
					continue
				}
				bean, err := ParseRow(via, settings)
				if err != nil {
					continue
				}
				baseName := direct.Links.Name
				if nikofmt.KtIsBlank(baseName) {
					baseName = deref(nikofmt.Base(direct.Bean).Name)
				}
				relayRowName := strings.TrimSpace(baseName + " · " + relay.ID)
				nikofmt.Base(bean).Name = &relayRowName
				bean.InitializeDefaultValues()
				signWithHint(bean, dnsAuthV2)
				viaNext := ""
				if direct.Links.Next != "" {
					if vn, ok := ViaLink(direct.Links.Next, relay); ok {
						viaNext = vn
					}
				}
				viaOrig, _ := ViaLink(direct.Links.Link, relay)
				parsed.Rows = append(parsed.Rows, Row{
					Key:  ViaKey(direct.Key, relay.ID),
					Bean: bean,
					Links: Links{
						Name:       relayRowName,
						Link:       viaOrig,
						Next:       viaNext,
						From:       direct.Links.From,
						NextActive: direct.Links.NextActive,
					},
				})
			}
		}
	}
	return parsed, nil
}

// parseRow is one profile of the payload: its row, the addresses its links name, whether it is the node's DoH.
func parseRow(profile map[string]any, key string, kinds map[int]string, nodeNow int64,
	settings nikofmt.Settings) (Row, []string, bool, error) {
	links, err := LinksOf(profile, nodeNow)
	if err != nil {
		return Row{}, nil, false, err
	}
	addrs := append(OfLink(links.Link), OfLink(links.Next)...)
	// DoH is not a proxy: it becomes the remote DNS while a profile of the node runs
	kind := ""
	if svc, ok := profile["svc"]; ok && isPrimitive(svc) {
		n, ok := asInt(svc)
		if !ok {
			return Row{}, addrs, false, errors.New("svc is not a number")
		}
		kind = kinds[int(n)]
	}
	if IsDoH(links.ActiveLink(), kind) {
		return Row{Key: key, Links: links}, addrs, true, nil
	}
	// the spec's schemes only, without sent JSON (RayRows): a row that breaks it is skipped
	bean, err := ParseRow(links.ActiveLink(), settings)
	if err != nil {
		return Row{}, addrs, false, err
	}
	if !nikofmt.KtIsBlank(links.Name) {
		nikofmt.Base(bean).Name = &links.Name
	}
	bean.InitializeDefaultValues()
	return Row{Key: key, Bean: bean, Links: links}, addrs, false, nil
}

// LinksOf is RaySubscription.linksOf: a profile's credentials, its next one from next.from on (the node's clock).
func LinksOf(profile map[string]any, nodeNow int64) (Links, error) {
	var l Links
	next, hasNext := profile["next"].(map[string]any)
	if hasNext {
		if from, ok := next["from"]; ok {
			n, ok := asLong(from)
			if !ok {
				return l, errors.New("next.from is not a number")
			}
			l.From = n
		}
	}
	l.Name, _ = primitiveString(profile, "name")
	link, ok := gsonString(profile["link"])
	if !ok {
		return l, errors.New("no link")
	}
	l.Link = link
	if hasNext {
		if v, ok := next["link"]; ok {
			s, ok := gsonString(v)
			if !ok {
				return l, errors.New("next.link is not a string")
			}
			l.Next = s
		}
	}
	l.NextActive = hasNext && nodeNow >= l.From
	return l, nil
}

// gsonString is JsonElement.getAsString: a primitive's text, an array of one element its element's.
func gsonString(v any) (string, bool) {
	if a, ok := v.([]any); ok {
		if len(a) == 1 {
			return gsonString(a[0])
		}
		return "", false
	}
	return asString(v)
}

// ProfileKeys is RaySubscription.profileKeys: the service id of each profile, svc#n for its n-th when a service has
// several.
func ProfileKeys(profiles []map[string]any) []string {
	seen := map[string]int{}
	var keys []string
	for _, p := range profiles {
		svc, ok := primitiveString(p, "svc")
		if !ok {
			svc = "-"
		}
		seen[svc]++
		if n := seen[svc]; n == 1 {
			keys = append(keys, svc)
		} else {
			keys = append(keys, fmt.Sprintf("%s#%d", svc, n))
		}
	}
	return keys
}

// IsDoH is RaySubscription.isDoh: a DoH row (subscription-v1.md §4), by the service's kind or the RFC 8484 path.
func IsDoH(link, kind string) bool {
	if kind == "doh" {
		return true
	}
	if !strings.HasPrefix(link, "https://") {
		return false
	}
	u, err := nikofmt.ParseOkURL(link)
	return err == nil && strings.HasPrefix(u.EncodedPath, "/dns-query")
}

// dohURL is RaySubscription.dohUrl: the DoH URL to query, the link without niko-* parameters and the fragment.
func dohURL(link string) (string, error) {
	u, err := nikofmt.ParseOkURL(link)
	if err != nil {
		return "", errors.New("DoH: not a URL")
	}
	if u.Scheme != "https" {
		return "", errors.New("DoH: not https")
	}
	return u.WithoutParams(func(name string) bool { return strings.HasPrefix(name, "niko-") }), nil
}

// dohPins is RaySubscription.dohPins: the key pins of a DoH row (niko-pin), "" without; a list that does not read is
// an error.
func dohPins(link string) (string, error) {
	u, err := nikofmt.ParseOkURL(link)
	if err != nil {
		return "", nil
	}
	p := u.QueryParameter("niko-pin")
	if p == nil {
		return "", nil
	}
	pins := nikofmt.KtTrim(*p)
	if pins == "" {
		return "", nil
	}
	if parsed, err := nikofmt.ParseCertificatePins(pins); err != nil || len(parsed) == 0 {
		return "", errors.New("niko-pin: no pin")
	}
	return pins, nil
}

// signWithHint is DnsTunnel.signWithHint: dnsauth-v2's tag where the node checks it.
func signWithHint(bean nikofmt.Bean, v2 bool) {
	base := nikofmt.Base(bean)
	if v2 && base.DnsTunnelAuth != nil && strings.HasPrefix(*base.DnsTunnelAuth, "v1;") {
		auth := "v2;" + strings.TrimPrefix(*base.DnsTunnelAuth, "v1;")
		base.DnsTunnelAuth = &auth
	}
}

// ParseRow is RayRows.parse: a row's profile, only of the spec's schemes and without anything outside the link's own
// fields: no full config, no custom JSON, no insecure TLS. The error names what the link breaks.
func ParseRow(link string, settings nikofmt.Settings) (nikofmt.Bean, error) {
	var bean nikofmt.Bean
	switch {
	case strings.HasPrefix(link, "vless://"):
		b, err := nikofmt.ParseVLESSDuckSoft(link, settings)
		if err != nil {
			return nil, err
		}
		// every VLESS profile of a node carries the ML-KEM-768 second layer (R1, R2, R4)
		if b.VlessEncryption == nil || nikofmt.KtIsBlank(*b.VlessEncryption) || *b.VlessEncryption == "none" {
			return nil, errors.New("vless: no encryption")
		}
		// the bean keeps REALITY as security=tls with a public key
		u, _ := nikofmt.ParseOkURL("https://" + substringAfter(link, "://"))
		security := u.QueryParameter("security")
		if security != nil && *security == "reality" || b.RealityPubKey != nil && !nikofmt.KtIsBlank(*b.RealityPubKey) {
			// without pqv the profile is not post-quantum and the client must not take it (§4 R2)
			if b.RealityMldsa65Verify == nil || nikofmt.KtIsBlank(*b.RealityMldsa65Verify) {
				return nil, errors.New("reality: no pqv")
			}
		}
		if b.AllowInsecure != nil && *b.AllowInsecure {
			return nil, errors.New("vless: insecure TLS")
		}
		b.Certificates = ptr("")
		bean = b
	case strings.HasPrefix(link, "hysteria2://") || strings.HasPrefix(link, "hy2://"):
		b, err := nikofmt.ParseHysteria2(link, settings)
		if err != nil {
			return nil, err
		}
		if b.ProtocolVersion == nil || *b.ProtocolVersion != 2 {
			return nil, errors.New("hysteria2: not version 2")
		}
		// hy2-obfs is self-signed but pinned by niko-pin; insecure is never sent (§4 R3)
		if b.AllowInsecure != nil && *b.AllowInsecure {
			return nil, errors.New("hysteria2: insecure TLS")
		}
		b.CaText = ptr("")
		b.Protocol = ptr(nikofmt.HysteriaProtocolUDP)
		bean = b
	case strings.HasPrefix(link, "naive+https://") || strings.HasPrefix(link, "naive+quic://"):
		b, err := nikofmt.ParseNaive(link)
		if err != nil {
			return nil, err
		}
		b.Certificates = ptr("")
		b.ExtraHeaders = ptr("")
		// Chromium pins a key to a name, never to an address (cert-pin-v1: the node's rows carry sni)
		if !blank(b.CertPins) && blank(b.Sni) && nikofmt.IsIPAddress(deref(b.ServerAddress)) {
			return nil, errors.New("naive: a pin without a server name")
		}
		bean = b
	default:
		return nil, errors.New("not a raycontrol profile scheme: " + safeScheme(link))
	}
	base := nikofmt.Base(bean)
	// a pin list that does not read would fail the start of the profile, and must not be dropped: no row
	if !blank(base.CertPins) {
		if pins, err := nikofmt.ParseCertificatePins(*base.CertPins); err != nil || len(pins) == 0 {
			return nil, errors.New("niko-pin: no pin")
		}
	}
	// nothing outside the link's own fields: no config merged over the whole config, no raw outbound
	base.CustomConfigJson = ptr("")
	base.CustomOutboundJson = ptr("")
	base.KnockSequence = ptr("")
	return bean, nil
}

var schemeWord = regexp.MustCompile(`^[A-Za-z][A-Za-z0-9+.-]{0,31}$`)

// safeScheme is the scheme of a link for an error: a word alone, never more of the link (a token may follow).
func safeScheme(link string) string {
	scheme := substringBefore(link, "://")
	if !schemeWord.MatchString(scheme) {
		return "?"
	}
	return scheme
}

func ptr[T any](v T) *T { return &v }

func blank(s *string) bool { return s == nil || nikofmt.KtIsBlank(*s) }

func deref(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

// NextRefresh is RaySubscription.nextRefresh: when to fetch again, at the earliest rotation still ahead or once
// three quarters of the subscription's life have passed, never sooner than a minute from now.
func NextRefresh(payload []byte, now int64) (int64, error) {
	o, err := decodeObject(payload)
	if err != nil {
		return 0, err
	}
	issued := now
	if v, ok := o["issued"]; ok {
		if issued, ok = asLong(v); !ok {
			return 0, errors.New("issued is not a number")
		}
	}
	expires, ok := asLong(o["expires"])
	if !ok {
		return 0, errors.New("no expires")
	}
	next := issued + (expires-issued)*3/4
	list, _ := array(o, "profiles")
	for _, v := range list {
		p, ok := v.(map[string]any)
		if !ok {
			return 0, errors.New("a profile is not an object")
		}
		if n, ok := p["next"].(map[string]any); ok {
			if f, ok := n["from"]; ok {
				from, ok := asLong(f)
				if !ok {
					return 0, errors.New("next.from is not a number")
				}
				if from > now && from < next {
					next = from
				}
			}
		}
	}
	return max(now+60, next), nil
}

var productName = regexp.MustCompile(`^[a-z][a-z0-9-]{0,15}$`)

// trafficOf is RayTraffic.fromJson: traffic of version 1, else nil.
func trafficOf(o map[string]any) *Traffic {
	if o == nil {
		return nil
	}
	if v, ok := asInt(o["v"]); !ok || v != 1 {
		return nil
	}
	long := func(name string) int64 {
		n, ok := asLong(o[name])
		if !ok || n < 0 {
			return 0
		}
		return n
	}
	exhausted, _ := asBool(o["exhausted"])
	return &Traffic{V: 1, Used: long("used"), Limit: long("limit"), Since: long("since"), Reset: long("reset"),
		Exhausted: exhausted}
}

// clientOf is RayClientMin.fromJson: client of version 1, else nil; up to 8 products, a version up to 64 characters.
func clientOf(o map[string]any) *ClientMin {
	if o == nil {
		return nil
	}
	if v, ok := asInt(o["v"]); !ok || v != 1 {
		return nil
	}
	c := &ClientMin{V: 1, Min: map[string]string{}}
	// the app keeps the first 8 in the document's order; encoding/json does not keep it, so here they go by name, the
	// same 8 each time (and "niko" not dropped by chance): only a node sending over 8 products sees the difference
	products := object(o, "min")
	for _, product := range slices.Sorted(maps.Keys(products)) {
		version := products[product]
		v, ok := version.(string)
		if !ok || !productName.MatchString(product) || v == "" || utf16Len(v) > 64 || len(c.Min) >= 8 {
			continue
		}
		c.Min[product] = v
	}
	if from, ok := asLong(o["from"]); ok && from > 0 {
		c.From = from
	}
	c.Refused, _ = asBool(o["refused"])
	return c
}

// Kinds are a node's service kinds by id, for Parse.
func (n *Node) Kinds() map[int]string {
	kinds := map[int]string{}
	for _, s := range n.Services {
		kinds[s.ID] = s.Kind
	}
	return kinds
}

// rawJSON is a value back as JSON, for the store.
func rawJSON(v any) json.RawMessage {
	data, _ := json.Marshal(v)
	return data
}

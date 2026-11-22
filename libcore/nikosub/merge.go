package nikosub

import (
	"context"
	"net"
	"sort"
	"strconv"
	"strings"

	"libcore/nikofmt"
)

// The profiles an update leaves in the group, as RawUpdater.doUpdate leaves them: a full sing-box config refused, the
// provider's custom configs dropped, names made unique, duplicates of a server dropped where the subscription says
// so, then each profile matched to the group's by its name. A matched one takes the new bean and keeps the user's
// own settings of it (a custom outbound or config, knocking, TLS fragmentation, pins, a DNS tunnel the new one has
// none of, UDP desync); the others are added, and the group's profiles no name matches are deleted.
//
// The app's order is kept as it is, its quirk too: the profiles are numbered from 1 in the subscription's order, but a
// changed one keeps its old number (only an unchanged one in another place takes its new one), so numbers may repeat
// until the next update; the list orders them by number, then by id. Two of the group's profiles of one name are
// matched by the later one; the earlier stays as it was.

// Prepare is the beans an update takes of a body's: a ConfigBean of a full config (type 0) dropped, every other
// bean's customConfigJson emptied, and a name made unique by " (1)", " (2)"… after it, as the app's loop does.
func Prepare(parsed []nikofmt.Bean) []nikofmt.Bean {
	var proxies []nikofmt.Bean
	for _, bean := range parsed {
		if c, ok := bean.(*nikofmt.ConfigBean); ok && c.Type != nil && *c.Type == 0 {
			continue // a subscription can not ship a full sing-box config
		}
		nikofmt.Base(bean).CustomConfigJson = ptrTo("")
		proxies = append(proxies, bean)
	}
	names := map[string]bool{}
	// where the loop stopped for a name: the names it went through stay taken, so the next of that name goes on from
	// there, the same names in linear time (thousands of one name were quadratic: a provider's body is anyone's)
	type stop struct {
		index int
		name  string
	}
	stopped := map[string]stop{}
	var out []nikofmt.Bean
	for _, proxy := range proxies {
		index := 0
		start := nikofmt.DisplayName(proxy)
		name := start
		if at, ok := stopped[start]; ok {
			index, name = at.index, at.name
		}
		for names[name] {
			index++
			name = strings.ReplaceAll(name, " ("+strconv.Itoa(index-1)+")", "")
			name = name + " (" + strconv.Itoa(index) + ")"
			nikofmt.Base(proxy).Name = ptrTo(name)
		}
		stopped[start] = stop{index, name}
		names[nikofmt.DisplayName(proxy)] = true
		out = append(out, proxy)
	}
	return out
}

// javaClasses are the beans' classes, which Protocols.Deduplication's key ends with.
var javaClasses = map[string]string{
	"socks":     "class io.niko.fmt.socks.SOCKSBean",
	"http":      "class io.niko.fmt.http.HttpBean",
	"ss":        "class io.niko.fmt.shadowsocks.ShadowsocksBean",
	"vmess":     "class io.niko.fmt.v2ray.VMessBean",
	"trojan":    "class io.niko.fmt.trojan.TrojanBean",
	"trojan-go": "class io.niko.fmt.trojan_go.TrojanGoBean",
	"mieru":     "class io.niko.fmt.mieru.MieruBean",
	"naive":     "class io.niko.fmt.naive.NaiveBean",
	"hysteria":  "class io.niko.fmt.hysteria.HysteriaBean",
	"ssh":       "class io.niko.fmt.ssh.SSHBean",
	"wg":        "class io.niko.fmt.wireguard.WireGuardBean",
	"tuic":      "class io.niko.fmt.tuic.TuicBean",
	"anytls":    "class io.niko.proxy.anytls.AnyTLSBean",
	"config":    "class io.niko.proxy.config.ConfigBean",
}

// dedupKey is Protocols.Deduplication.hash: a custom config's text, else the address, the port and the class run
// together (so 1.2.3.4 443 and 1.2.3.44 43 are one server, as in the app).
func dedupKey(b nikofmt.Bean) string {
	if c, ok := b.(*nikofmt.ConfigBean); ok {
		return "config\x00" + javaString(c.Config)
	}
	base := nikofmt.Base(b)
	port := "null"
	if base.ServerPort != nil {
		port = strconv.Itoa(*base.ServerPort)
	}
	return javaString(base.ServerAddress) + port + javaClasses[nikofmt.TypeName(b)]
}

func javaString(s *string) string {
	if s == nil {
		return "null"
	}
	return *s
}

// Deduplicate is the deduplication option: the first profile of each server, and the lines the app shows of the
// others (a name and the place of the one kept, as its message words them).
func Deduplicate(proxies []nikofmt.Bean) ([]nikofmt.Bean, []string) {
	var kept []nikofmt.Bean
	index := map[string]int{}
	names := map[string]string{}
	var duplicate []string
	for _, p := range proxies {
		key := dedupKey(p)
		at, seen := index[key]
		if !seen {
			index[key] = len(kept)
			kept = append(kept, p)
			names[key] = nikofmt.DisplayName(p)
			continue
		}
		suffix := " (" + strconv.Itoa(at) + ")"
		if name := strings.ReplaceAll(names[key], suffix, ""); !nikofmt.KtIsBlank(name) {
			duplicate = append(duplicate, name+suffix)
			names[key] = ""
		}
		duplicate = append(duplicate, nikofmt.DisplayName(p)+suffix)
	}
	return kept, duplicate
}

// Existing is one of the group's profiles before the update, in the order the app reads them (by number, then id).
type Existing struct {
	ID    int64
	Order int64
	Bean  nikofmt.Bean
}

// What became of a profile.
const (
	// Kept: no change (the same bean, the same place), or one of two of a name, the earlier
	Kept = "kept"
	// Updated: a new bean, the old number
	Updated = "updated"
	// Reordered: the same bean in another place, its new number
	Reordered = "reordered"
	// Added: a new profile
	Added = "added"
)

// Planned is a profile after the update: ID 0 for a new one.
type Planned struct {
	ID    int64
	Order int64
	// Bean is the bean as the app's store has it back (its Kryo read again, nikofmt.KryoClone)
	Bean  nikofmt.Bean
	State string
}

// Plan is what an update does to the group: the group's profiles in their order then the new ones (Deleted not
// among them), and what the app's message tells.
type Plan struct {
	Profiles []Planned
	Deleted  []int64
	// Changed counts the profiles added, updated or deleted (the app's "changed")
	Changed int
	// Added are the new profiles' names; Updated the changed ones', old and new (the same); DeletedNames the
	// deleted ones'; Duplicate the lines of the deduplication
	Added, DeletedNames, Duplicate []string
	Updated                        [][2]string
}

// Merge is the update of a group with the profiles of a subscription (Prepare's, deduplicated where the
// subscription says so; the app resolves the servers' names in between when it says forceResolve, Resolve here). An
// error where the app's own code throws: a bean its store cannot write (a null it unboxes).
func Merge(exists []Existing, proxies []nikofmt.Bean, deduplication bool) (Plan, error) {
	var plan Plan
	if deduplication {
		proxies, plan.Duplicate = Deduplicate(proxies)
	}
	// nameMap: proxies by display name, the first one's place, the last one's bean
	var names []string
	byName := map[string]nikofmt.Bean{}
	for _, p := range proxies {
		name := nikofmt.DisplayName(p)
		if _, ok := byName[name]; !ok {
			names = append(names, name)
		}
		byName[name] = p
	}
	// toReplace: the group's profile of each name, the later one of two
	replace := map[string]int{}
	deleted := map[int]bool{}
	for i, e := range exists {
		name := nikofmt.DisplayName(e.Bean)
		if _, ok := byName[name]; ok {
			replace[name] = i
		} else {
			deleted[i] = true
		}
	}
	states := make([]Planned, len(exists))
	for i, e := range exists {
		states[i] = Planned{ID: e.ID, Order: e.Order, Bean: e.Bean, State: Kept}
	}
	plan.Changed = len(deleted)
	var added []Planned
	order := int64(1)
	for _, name := range names {
		bean := byName[name]
		if i, ok := replace[name]; ok {
			old := exists[i].Bean
			keepUsers(bean, old)
			same, err := nikofmt.KryoEqual(old, bean)
			if err != nil {
				return Plan{}, err
			}
			switch {
			case !same:
				plan.Changed++
				clone, err := nikofmt.KryoClone(bean)
				if err != nil {
					return Plan{}, err
				}
				states[i].Bean, states[i].State = clone, Updated
				plan.Updated = append(plan.Updated, [2]string{nikofmt.DisplayName(bean), name})
			case exists[i].Order != order:
				clone, err := nikofmt.KryoClone(bean)
				if err != nil {
					return Plan{}, err
				}
				states[i].Bean, states[i].Order, states[i].State = clone, order, Reordered
			}
		} else {
			plan.Changed++
			clone, err := nikofmt.KryoClone(bean)
			if err != nil {
				return Plan{}, err
			}
			added = append(added, Planned{Order: order, Bean: clone, State: Added})
			plan.Added = append(plan.Added, name)
		}
		order++
	}
	for i, e := range exists {
		if deleted[i] {
			plan.Deleted = append(plan.Deleted, e.ID)
			plan.DeletedNames = append(plan.DeletedNames, nikofmt.DisplayName(e.Bean))
			continue
		}
		plan.Profiles = append(plan.Profiles, states[i])
	}
	plan.Profiles = append(plan.Profiles, added...)
	return plan, nil
}

// keepUsers is what the update keeps of the user's own on a profile it replaces ("保留自定义覆写设置").
func keepUsers(bean, old nikofmt.Bean) {
	b, o := nikofmt.Base(bean), nikofmt.Base(old)
	b.CustomOutboundJson = o.CustomOutboundJson
	b.CustomConfigJson = o.CustomConfigJson
	b.KnockSequence = o.KnockSequence
	b.KnockInterval = o.KnockInterval
	b.KnockTtl = o.KnockTtl
	b.KnockSilence = o.KnockSilence
	b.TlsFragment = o.TlsFragment
	b.CertPins = o.CertPins
	// keepDnsTunnelOf: the old tunnel where the new bean has none (no domain)
	if b.DnsTunnelDomain == nil || nikofmt.KtIsBlank(*b.DnsTunnelDomain) {
		b.DnsTunnel = o.DnsTunnel
		b.DnsTunnelResolver = o.DnsTunnelResolver
		b.DnsTunnelDomain = o.DnsTunnelDomain
		b.DnsTunnelKey = o.DnsTunnelKey
		b.DnsTunnelMethod = o.DnsTunnelMethod
		b.DnsTunnelFixedUpstream = o.DnsTunnelFixedUpstream
		b.DnsTunnelAuth = o.DnsTunnelAuth
		b.DnsTunnelSeal = o.DnsTunnelSeal
		b.DnsTunnelSpare = o.DnsTunnelSpare
		b.DnsTunnelTls = o.DnsTunnelTls
	}
	b.UdpPrelude = o.UdpPrelude
	b.UdpPreludeSni = o.UdpPreludeSni
	b.UdpPreludeTtl = o.UdpPreludeTtl
	b.UdpReorderInitial = o.UdpReorderInitial
}

// Resolver looks a server's name up: the system's (net.DefaultResolver) or a test's.
type Resolver interface {
	LookupIPAddr(ctx context.Context, host string) ([]net.IPAddr, error)
}

type defaultResolver struct{}

func (defaultResolver) LookupIPAddr(ctx context.Context, host string) ([]net.IPAddr, error) {
	return net.DefaultResolver.LookupIPAddr(ctx, host)
}

// Resolve is GroupUpdater.forceResolve: each server named by a domain (not naive's, which cannot rewrite its SNI)
// takes its first address; a TLS profile without an SNI keeps the name as its SNI. The app's order of the addresses is
// kept, its quirk too: IPv6 first unless the IPv6 setting prefers it (ipv6First), then IPv4 first. A name that does not
// resolve stays.
func Resolve(ctx context.Context, resolver Resolver, proxies []nikofmt.Bean, ipv6First bool) {
	for _, p := range proxies {
		if _, naive := p.(*nikofmt.NaiveBean); naive {
			continue
		}
		base := nikofmt.Base(p)
		if base.ServerAddress == nil || nikofmt.IsIPAddress(*base.ServerAddress) {
			continue
		}
		addresses, err := resolver.LookupIPAddr(ctx, *base.ServerAddress)
		if err != nil || len(addresses) == 0 {
			continue
		}
		sort.SliceStable(addresses, func(i, j int) bool {
			key := func(a net.IPAddr) bool { return (a.IP.To4() != nil) != ipv6First }
			return !key(addresses[i]) && key(addresses[j])
		})
		rewriteAddress(p, addresses[0].IP.String())
	}
}

// rewriteAddress is GroupUpdater.rewriteAddress.
func rewriteAddress(p nikofmt.Bean, address string) {
	base := nikofmt.Base(p)
	name := javaString(base.ServerAddress)
	setSNI := func(sni **string) {
		if blank(*sni) {
			*sni = ptrTo(name)
		}
	}
	switch b := p.(type) {
	case *nikofmt.HttpBean:
		if equalsStr(b.Security, "tls") {
			setSNI(&b.Sni)
		}
	case *nikofmt.VMessBean:
		if equalsStr(b.Security, "tls") {
			setSNI(&b.Sni)
		}
	case *nikofmt.TrojanBean:
		// a StandardV2RayBean: the app's when takes that branch first
		if equalsStr(b.Security, "tls") {
			setSNI(&b.Sni)
		}
	case *nikofmt.TrojanGoBean:
		setSNI(&b.Sni)
	case *nikofmt.HysteriaBean:
		setSNI(&b.Sni)
	}
	base.ServerAddress = ptrTo(address)
}

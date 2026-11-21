package nikoconf

import (
	"fmt"
	"strings"

	"libcore/nikofmt"
)

// The user's route rules (buildConfig's "apply user rules", SingBoxOptionsUtil.kt), with no rule-set downloaded:
// RuleSetFiles has no file here, so a running config leaves every "srs:" list out (an export names it by its URL).

// The rule-set items the app downloads and the remote ones (RuleSetFiles, SingBoxOptionsUtil.kt).
const (
	ruleSetFilePrefix   = "srs:"
	remoteRuleSetPrefix = "rule-set:"

	runetFreedom    = "https://raw.githubusercontent.com/runetfreedom/russia-v2ray-rules-dat/release/sing-box"
	ruBlockedDomain = ruleSetFilePrefix + runetFreedom + "/rule-set-geosite/geosite-ru-blocked.srs"
	ruBlockedIPs    = ruleSetFilePrefix + runetFreedom + "/rule-set-geoip/geoip-ru-blocked.srs"
	ruInsideDomains = ruleSetFilePrefix + runetFreedom + "/rule-set-geosite/geosite-ru-available-only-inside.srs"
	ruIPs           = ruleSetFilePrefix + runetFreedom + "/rule-set-geoip/geoip-ru.srs"
)

func (b *builder) userRules(rules []*Rule, refuseQuic bool) error {
	s := b.s
	// an exported config keeps the rules as they are: the rule-sets go by their URLs
	russiaDirectWaits := !b.forExport && russiaDirectWaits(rules)
	for _, rule := range rules {
		if russiaDirectWaits && isRussiaDirect(rule) {
			continue // waits for the blocked lists (Russian services go through the proxy meanwhile)
		}
		// the uids of its apps, a HashSet's order (Integer's hash, null's 0)
		var uids []javaInt
		for _, pkg := range distinctStrings(rule.Packages) {
			// quirk kept: told once for each of its apps
			if !b.isVPN {
				b.warn("rule needs VPN: %s", rule)
			}
			if uid, ok := b.dev.UID(pkg); ok && uid >= 1000 {
				uids = append(uids, javaInt{value: uid})
			} else {
				uids = append(uids, javaInt{null: true})
			}
		}
		uidList := []any{}
		for _, uid := range javaHashSetOrder(uids, javaIntegerHash) {
			if !uid.null {
				uidList = append(uidList, int64(uid.value))
			}
		}

		ruleObj := object{}
		if len(uidList) > 0 {
			ruleObj["user_id"] = uidList
		}
		var domainList []string
		// a rule-set the app has not downloaded is left out; a rule it leaves without any address is left out whole
		missingRuleSet := false
		downloaded := func(list []string) []string {
			kept := []string{}
			for _, item := range list {
				// an export: those the app would fetch (https), by their URLs; the running config: none is here
				if !isRuleSetFile(item) || b.forExport && ruleSetFetchable(item) {
					kept = append(kept, item)
				} else {
					missingRuleSet = true
				}
			}
			return kept
		}
		if !nikofmt.KtIsBlank(rule.Domains) {
			domainList = downloaded(listOf(rule.Domains))
			makeRouteRule(ruleObj, domainList, false)
		}
		if !nikofmt.KtIsBlank(rule.IP) {
			makeRouteRule(ruleObj, downloaded(listOf(rule.IP)), true)
		}
		dropRule := missingRuleSet && !routeRuleHasAddress(ruleObj)

		var ruleSets []object
		if list, ok := ruleObj["rule_set"].([]any); ok {
			ruleSets = generateRuleSet(list, s.RuleSetUpdateHours, b.forExport)
		}
		if !nikofmt.KtIsBlank(rule.Port) {
			ruleObj["port"], ruleObj["port_range"] = ports(rule.Port)
		}
		if !nikofmt.KtIsBlank(rule.SourcePort) {
			ruleObj["source_port"], ruleObj["source_port_range"] = ports(rule.SourcePort)
		}
		if !nikofmt.KtIsBlank(rule.Network) {
			ruleObj["network"] = []any{rule.Network}
		}
		if !nikofmt.KtIsBlank(rule.Source) {
			ruleObj["source_ip_cidr"] = listByLineOrComma(rule.Source)
		}
		if !nikofmt.KtIsBlank(rule.Protocol) {
			ruleObj["protocol"] = listByLineOrComma(rule.Protocol)
		}

		makeDNSRuleObj := func() object {
			dnsRule := object{}
			if len(uidList) > 0 {
				dnsRule["user_id"] = uidList
			}
			if domainList != nil {
				makeDNSRule(dnsRule, domainList, b.forExport)
			}
			return dnsRule
		}
		// a rule given domains whose DNS rule is left with none has no DNS rule; a rule without domains has one only
		// where it is the app alone
		addDNSRule := func(dnsRule object) {
			follows := dnsRuleHasDomain(dnsRule)
			if nikofmt.KtIsBlank(rule.Domains) {
				follows = true
				for _, field := range []string{rule.IP, rule.Port, rule.SourcePort, rule.Network, rule.Source,
					rule.Protocol} {
					follows = follows && nikofmt.KtIsBlank(field)
				}
			}
			if follows {
				b.userDNSRules = append(b.userDNSRules, dnsRule)
			}
		}
		if !dropRule {
			switch rule.Outbound {
			case -1:
				r := makeDNSRuleObj()
				r["server"] = "dns-direct"
				addDNSRule(r)
			case 0:
				if b.useFakeDNS {
					r := makeDNSRuleObj()
					r["server"] = "dns-fake"
					r["inbound"] = []any{tagTun}
					addDNSRule(r)
				}
				r := makeDNSRuleObj()
				r["server"] = "dns-remote"
				addDNSRule(r)
			case -2:
				r := makeDNSRuleObj()
				r["action"] = "predefined"
				r["rcode"] = "NOERROR"
				addDNSRule(r)
			}
		}

		switch outID := rule.Outbound; {
		case outID == 0:
			ruleObj["outbound"] = tagProxy
		case outID == -1:
			ruleObj["outbound"] = tagBypass
		case outID == -2:
			ruleObj["outbound"] = tagBlock
		case outID == b.proxy.ID && !b.buildAuto:
			// in Auto mode "proxy" is the urltest: a rule naming a profile means that profile
			ruleObj["outbound"] = tagProxy
		default:
			tag, _ := b.tagMap.get(outID)
			ruleObj["outbound"] = tag
		}
		routeRule := newOption(ruleObj)
		routeRule.custom = rule.Config
		if dropRule || routeRuleEmpty(routeRule) {
			continue
		}
		if out, _ := ruleObj["outbound"].(string); nikofmt.KtIsBlank(out) {
			b.warn("no such outbound: %s", rule)
			continue
		}
		if ruleObj["outbound"] == tagBlock {
			delete(ruleObj, "outbound")
			ruleObj["action"] = "reject"
		}
		// split ClientHello of directly routed TLS, e.g. for SNI-throttled sites
		if ruleObj["outbound"] == tagBypass {
			switch s.TLSFragmentBypass {
			case 1:
				routeRule.hack["tls_record_fragment"] = true
			case 2:
				routeRule.hack["tls_fragment"] = true
			}
		}
		// its QUIC refused before it takes it into the proxy (refuseQuic)
		if refuseQuic && ruleObj["outbound"] == tagProxy {
			refusal, err := quicRefusalBefore(routeRule, b.needSniff)
			if err != nil {
				return err
			}
			if refusal != nil {
				b.addRule(refusal)
			}
		}
		b.routeRules = append(b.routeRules, routeRule)
		b.ruleSets = append(b.ruleSets, ruleSets...)
	}
	return nil
}

// ports are a rule's ports and ranges: "a:b" a range, a number a port, anything else left out. Quirk kept: both
// lists go into the rule, an empty one too.
func ports(text string) ([]any, []any) {
	ports, ranges := []any{}, []any{}
	for _, item := range listOf(text) {
		if strings.Contains(item, ":") {
			ranges = append(ranges, item)
		} else if n, ok := nikofmt.KtToIntOrNull(item); ok {
			ports = append(ports, int64(n))
		}
	}
	return ports, ranges
}

// makeRouteRule is Rule_DefaultOptions.makeSingBoxRule: a rule's domains or IPs into its fields. Quirk kept: its
// rule_set stays, empty, once set (the core takes an empty list as none).
func makeRouteRule(r object, list []string, isIP bool) {
	if r["rule_set"] == nil {
		r["rule_set"] = []any{}
	}
	if isIP {
		r["ip_cidr"] = []any{}
	} else {
		for _, k := range []string{"domain", "domain_suffix", "domain_regex", "domain_keyword"} {
			r[k] = []any{}
		}
	}
	add := func(key, v string) { r[key] = append(r[key].([]any), v) }
	for _, item := range list {
		if strings.HasPrefix(item, remoteRuleSetPrefix) || isRuleSetFile(item) {
			add("rule_set", item)
			continue
		}
		if isIP {
			switch {
			case item == "geoip:private":
				r["ip_is_private"] = true
			case strings.HasPrefix(item, "geoip:"):
				add("rule_set", item)
			default:
				add("ip_cidr", item)
			}
			continue
		}
		if strings.HasPrefix(item, "geosite:") {
			add("rule_set", item)
		} else {
			key, name := domainField(item)
			add(key, name)
		}
	}
	for _, k := range []string{"ip_cidr", "rule_set", "domain", "domain_suffix", "domain_regex", "domain_keyword"} {
		dropBlank(r, k, k != "rule_set")
	}
}

// makeDNSRule is DNSRule_DefaultOptions.makeSingBoxRule: domains, and the rule-sets a DNS rule can use.
func makeDNSRule(r object, list []string, forExport bool) {
	for _, k := range []string{"rule_set", "domain", "domain_suffix", "domain_regex", "domain_keyword"} {
		r[k] = []any{}
	}
	add := func(key, v string) { r[key] = append(r[key].([]any), v) }
	for _, item := range list {
		switch {
		case strings.HasPrefix(item, "geosite:"):
			add("rule_set", item)
		case isRuleSetFile(item):
			// a downloaded one by what it holds (none is here); an export's by its name
			if ruleSetFetchable(item) && forExport && !isIPRuleSetName(item) {
				add("rule_set", item)
			}
		case strings.HasPrefix(item, remoteRuleSetPrefix):
			// an IP-only rule-set makes a DNS rule invalid in sing-box 1.14; skip what looks like one
			if !isIPRuleSetName(item) {
				add("rule_set", item)
			}
		default:
			key, name := domainField(item)
			add(key, name)
		}
	}
	for _, k := range []string{"rule_set", "domain", "domain_suffix", "domain_regex", "domain_keyword"} {
		dropBlank(r, k, true)
	}
}

// domainField is where a domain of a list goes: full:, domain:, regexp:, keyword: or a suffix, in lower case.
func domainField(item string) (string, string) {
	for _, p := range [][2]string{{"full:", "domain"}, {"domain:", "domain_suffix"}, {"regexp:", "domain_regex"},
		{"keyword:", "domain_keyword"}} {
		if strings.HasPrefix(item, p[0]) {
			return p[1], nikofmt.KtLowercase(strings.TrimPrefix(item, p[0]))
		}
	}
	return "domain_suffix", nikofmt.KtLowercase(item)
}

// dropBlank removes a list's blank items, and with dropEmpty the list where none is left.
func dropBlank(r object, key string, dropEmpty bool) {
	list, ok := r[key].([]any)
	if !ok {
		return
	}
	kept := []any{}
	for _, v := range list {
		if s, _ := v.(string); !nikofmt.KtIsBlank(s) {
			kept = append(kept, v)
		}
	}
	if len(kept) == 0 && dropEmpty {
		delete(r, key)
	} else {
		r[key] = kept
	}
}

// generateRuleSet is generateRuleSet: the rule-sets a rule names.
func generateRuleSet(items []any, updateHours int, forExport bool) []object {
	interval := fmt.Sprintf("%dh", coerceIn(updateHours, 1, 24*30))
	var out []object
	for _, v := range items {
		item, _ := v.(string)
		switch {
		// an exported config names a downloaded rule-set by its URL
		case forExport && isRuleSetFile(item):
			if ruleSetFetchable(item) {
				out = append(out, object{"type": "remote", "tag": item, "format": ruleSetFormat(ruleSetURL(item)),
					"url": ruleSetURL(item), "update_interval": interval})
			}
		case strings.HasPrefix(item, remoteRuleSetPrefix):
			url := strings.TrimPrefix(item, remoteRuleSetPrefix)
			out = append(out, object{"type": "remote", "tag": item, "format": ruleSetFormat(url), "url": url,
				"update_interval": interval})
		case isRuleSetFile(item):
			// the app's download: none is here, and the build left it out before
		case strings.HasPrefix(item, "geoip:"), strings.HasPrefix(item, "geosite:"):
			out = append(out, object{"type": "local", "tag": item, "format": "binary", "path": item})
		}
	}
	return out
}

// ruleSetFormat is "source" for a JSON rule-set, "binary" (.srs) otherwise.
func ruleSetFormat(url string) string {
	if strings.HasSuffix(substringBefore(url, "?"), ".json") {
		return "source"
	}
	return "binary"
}

func isRuleSetFile(item string) bool { return strings.HasPrefix(item, ruleSetFilePrefix) }

func ruleSetURL(item string) string {
	return nikofmt.KtTrim(strings.TrimPrefix(item, ruleSetFilePrefix))
}

// ruleSetFetchable: only https, a list fetched in the clear could be any list.
func ruleSetFetchable(item string) bool {
	return startsWithIgnoreCase(ruleSetURL(item), "https://")
}

// startsWithIgnoreCase is Kotlin's startsWith(prefix, ignoreCase = true), for an ASCII prefix.
func startsWithIgnoreCase(s, prefix string) bool {
	runes := []rune(s)
	n := len(prefix)
	return len(runes) >= n && strings.EqualFold(string(runes[:n]), prefix)
}

// isIPRuleSetName: rule-sets named geoip-…, ip-… or ipcidr… usually hold IP CIDRs only.
func isIPRuleSetName(item string) bool {
	name := item
	if i := strings.LastIndex(name, "/"); i >= 0 {
		name = name[i+1:]
	}
	name = nikofmt.KtLowercase(substringBefore(name, "?"))
	return strings.HasPrefix(name, "geoip") || strings.HasPrefix(name, "ip-") || strings.HasPrefix(name, "ipcidr")
}

func nonEmptyList(r object, key string) bool {
	list, ok := r[key].([]any)
	return ok && len(list) > 0
}

// dnsRuleEmpty is DNSRule_DefaultOptions.checkEmpty.
func dnsRuleEmpty(r object) bool {
	for _, k := range []string{"rule_set", "domain", "domain_suffix", "domain_regex", "domain_keyword", "user_id"} {
		if nonEmptyList(r, k) {
			return false
		}
	}
	return true
}

// dnsRuleHasDomain: the DNS rule matches by a name at all.
func dnsRuleHasDomain(r object) bool {
	for _, k := range []string{"domain", "domain_suffix", "domain_keyword", "domain_regex", "rule_set"} {
		if nonEmptyList(r, k) {
			return true
		}
	}
	return false
}

// routeRuleEmpty is Rule_DefaultOptions.checkEmpty. Quirk kept: a network, a protocol or source ports alone are
// empty, and such a rule is left out without a word (its DNS rule, if any, stays).
func routeRuleEmpty(o *option) bool {
	for _, k := range []string{"ip_cidr", "domain", "rule_set", "domain_suffix", "domain_regex", "domain_keyword",
		"user_id", "port", "port_range", "source_ip_cidr"} {
		if nonEmptyList(o.base, k) {
			return false
		}
	}
	return nikofmt.KtIsBlank(o.custom)
}

// routeRuleHasAddress: the rule matches by an address at all.
func routeRuleHasAddress(r object) bool {
	for _, k := range []string{"domain", "domain_suffix", "domain_keyword", "domain_regex", "ip_cidr", "rule_set"} {
		if nonEmptyList(r, k) {
			return true
		}
	}
	return r["ip_is_private"] == true
}

// quicRefusal refuses QUIC (an ICMP unreachable each time, never a silent drop): by the sniffed protocol, else UDP
// 443.
func quicRefusal(sniffed bool) object {
	r := object{"network": []any{"udp"}, "action": "reject", "no_drop": true}
	if sniffed {
		r["protocol"] = []any{"quic"}
	} else {
		r["port"] = []any{int64(443)}
	}
	return r
}

// quicRefusalBefore is a copy of the user's rule to the proxy that refuses its QUIC, to go just before it; nil where
// a copy would not mean "this rule's QUIC".
func quicRefusalBefore(rule *option, sniffed bool) (object, error) {
	if !nikofmt.KtIsBlank(rule.custom) {
		return nil, nil
	}
	match, err := rule.asMap()
	if err != nil {
		return nil, err
	}
	values := func(key string) ([]string, bool) {
		switch v := match[key].(type) {
		case nil:
			return nil, false
		case []any:
			out := []string{}
			for _, e := range v {
				out = append(out, javaToString(e))
			}
			return out, true
		default:
			return []string{javaToString(v)}, true
		}
	}
	contains := func(list []string, s string) bool {
		for _, e := range list {
			if e == s {
				return true
			}
		}
		return false
	}
	if t, ok := match["type"]; match["invert"] == true || ok && t != "default" {
		return nil, nil
	}
	if v, ok := values("network"); ok && len(v) > 0 && !contains(v, "udp") {
		return nil, nil
	}
	if v, ok := values("protocol"); ok && len(v) > 0 && !contains(v, "quic") {
		return nil, nil
	}
	if !sniffed {
		portsGiven, _ := values("port")
		rangesGiven, _ := values("port_range")
		if len(portsGiven) > 0 || len(rangesGiven) > 0 {
			return nil, nil
		}
	}
	for _, k := range []string{"outbound", "action", "network", "protocol", "tls_fragment", "tls_record_fragment"} {
		delete(match, k)
	}
	for k, v := range quicRefusal(sniffed) {
		match[k] = v
	}
	return match, nil
}

// The Russian preset (RoutePresets): its direct rule waits while its blocked rule's lists are not all downloaded,
// which here is never.

func isRussiaBlocked(r *Rule) bool {
	return strings.Contains(r.Domains, ruBlockedDomain) && strings.Contains(r.IP, ruBlockedIPs)
}

func isRussiaDirect(r *Rule) bool {
	return strings.Contains(r.Domains, ruInsideDomains) && strings.Contains(r.IP, ruIPs) && r.Outbound == -1
}

// russiaDirectWaits: a blocked rule is on, and some of its lists are not downloaded (none is).
func russiaDirectWaits(rules []*Rule) bool {
	for _, r := range rules {
		if !isRussiaBlocked(r) {
			continue
		}
		for _, item := range append(listOf(r.Domains), listOf(r.IP)...) {
			if isRuleSetFile(item) {
				return true
			}
		}
	}
	return false
}

// distinctByTag keeps the first rule-set of each tag.
func distinctByTag(sets []object) []object {
	seen := map[any]bool{}
	var out []object
	for _, s := range sets {
		if !seen[s["tag"]] {
			seen[s["tag"]] = true
			out = append(out, s)
		}
	}
	return out
}

// listOf is listByLineOrComma as strings.
func listOf(s string) []string {
	var out []string
	for _, v := range listByLineOrComma(s) {
		out = append(out, v.(string))
	}
	return out
}

func distinctStrings(list []string) []string {
	seen := map[string]bool{}
	var out []string
	for _, s := range list {
		if !seen[s] {
			seen[s] = true
			out = append(out, s)
		}
	}
	return out
}

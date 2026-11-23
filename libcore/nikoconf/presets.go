package nikoconf

import "strings"

// Ready-made rules (RoutePresets.kt). Russia: what is blocked there through the proxy, Russian services directly, the
// rest as before. The lists are runetfreedom's (russia-v2ray-rules-dat, updated every 6 hours), as rule-sets the app
// downloads; its direct rule waits for the blocked rule's lists (config_rules.go).

// RussiaPreset is RoutePresets.russia: the preset's rules in their order, enabled, without ids and orders. The
// blocked first: a blocked site under .ru, or a service that a Russian address of its CDN serves, still goes through
// the proxy.
func RussiaPreset(blockedName, directName string) []Rule {
	return []Rule{
		{Name: blockedName, Domains: ruBlockedDomain, IP: ruBlockedIPs, Outbound: 0, Enabled: true},
		{Name: directName, Domains: strings.Join([]string{ruInsideDomains, "domain:ru", "domain:su", "domain:xn--p1ai"},
			"\n"), IP: ruIPs, Outbound: -1, Enabled: true},
	}
}

// IsRussia is RoutePresets.isRussia: a rule of the Russian preset's (by both lists of either rule), which adding the
// preset again replaces; a rule of the user's that names one of runetfreedom's lists stays.
func IsRussia(r *Rule) bool {
	return isRussiaBlocked(r) || strings.Contains(r.Domains, ruInsideDomains) && strings.Contains(r.IP, ruIPs)
}

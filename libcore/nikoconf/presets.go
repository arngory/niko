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

// Phase P7 regional and ad-blocking rule-set presets (srs:https://...).
const (
	cnNonCNDomains = "srs:https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-geolocation-!cn.srs"
	cnDomains      = "srs:https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-cn.srs"
	cnIPs          = "srs:https://raw.githubusercontent.com/SagerNet/sing-geoip/rule-set/geoip-cn.srs"

	irDomains = "srs:https://raw.githubusercontent.com/chocolate4u/Iran-sing-box-rules/rule-set/geosite-ir.srs"
	irIPs     = "srs:https://raw.githubusercontent.com/chocolate4u/Iran-sing-box-rules/rule-set/geoip-ir.srs"

	adsDomains = "srs:https://raw.githubusercontent.com/SagerNet/sing-geosite/rule-set/geosite-category-ads-all.srs"
)

// ChinaPreset routes non-China sites through the proxy and China domains/IPs directly.
func ChinaPreset(proxyName, directName string) []Rule {
	return []Rule{
		{Name: proxyName, Domains: cnNonCNDomains, Outbound: 0, Enabled: true},
		{Name: directName, Domains: strings.Join([]string{cnDomains, "domain:cn"}, "\n"), IP: cnIPs, Outbound: -1, Enabled: true},
	}
}

// IsChina identifies rules created by ChinaPreset.
func IsChina(r *Rule) bool {
	return strings.Contains(r.Domains, cnNonCNDomains) ||
		(strings.Contains(r.Domains, cnDomains) && strings.Contains(r.IP, cnIPs))
}

// IranPreset routes Iranian domains and IP ranges directly while other traffic uses the default outbound.
func IranPreset(directName string) []Rule {
	return []Rule{
		{Name: directName, Domains: strings.Join([]string{irDomains, "domain:ir"}, "\n"), IP: irIPs, Outbound: -1, Enabled: true},
	}
}

// IsIran identifies rules created by IranPreset.
func IsIran(r *Rule) bool {
	return strings.Contains(r.Domains, irDomains) && strings.Contains(r.IP, irIPs)
}

// AdsPreset blocks ad and tracker domains via the SagerNet geosite-category-ads-all rule-set.
func AdsPreset(blockName string) []Rule {
	return []Rule{
		{Name: blockName, Domains: adsDomains, Outbound: -2, Enabled: true},
	}
}

// IsAds identifies rules created by AdsPreset.
func IsAds(r *Rule) bool {
	return strings.Contains(r.Domains, adsDomains) && r.Outbound == -2
}

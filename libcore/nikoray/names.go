package nikoray

import (
	"regexp"
	"strings"

	"libcore/nikofmt"
)

// names-v1 (raycontrol docs/spec/names-v1.md) as RayNames.kt reads it: a node's name and its addresses, from its
// device package, its invite (RCINV3) and its subscription.

var nameLabel = regexp.MustCompile(`^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$`)

// Name is RayNames.name: a node's name, two labels or more of letters, digits and hyphens, in lower case; "" for
// anything else.
func Name(raw string) string {
	name := strings.TrimSuffix(nikofmt.KtLowercase(nikofmt.KtTrim(raw)), ".")
	if len(name) > 253 || nikofmt.IsIPAddress(name) {
		return ""
	}
	labels := strings.Split(name, ".")
	if len(labels) < 2 {
		return ""
	}
	for _, l := range labels {
		if !nameLabel.MatchString(l) {
			return ""
		}
	}
	return name
}

// Addrs is RayNames.addrs: the addresses of values (IPv6 bare or in brackets), once each in their order; anything
// else is left out.
func Addrs(values []string) []string {
	var out []string
	seen := map[string]bool{}
	for _, v := range values {
		a := nikofmt.KtLowercase(strings.TrimSuffix(strings.TrimPrefix(nikofmt.KtTrim(v), "["), "]"))
		if a == "" || !nikofmt.IsIPAddress(a) || seen[a] {
			continue
		}
		seen[a] = true
		out = append(out, a)
	}
	return out
}

// addrsParam is RayNames.addrs of a link's niko-ip: a comma-separated list.
func addrsParam(param string) []string { return Addrs(strings.Split(param, ",")) }

// OfLink is RayNames.ofLink: the niko-ip addresses of a subscription's link, none for anything else.
func OfLink(link string) []string {
	if nikofmt.KtIsBlank(link) || !strings.Contains(link, "niko-ip=") {
		return nil
	}
	values, ok := nikofmt.OkQueryValues("https://"+substringAfter(link, "://"), "niko-ip")
	if !ok {
		return nil
	}
	var all []string
	for _, v := range values {
		if v != nil {
			all = append(all, addrsParam(*v)...)
		}
	}
	return Addrs(all)
}

// namesOf is RayNames.of: the node's name and addresses in o (a device package, a payload). The name comes only with
// addresses: without them it is left out.
func namesOf(o map[string]any) (string, []string) {
	var raw []string
	if list, ok := array(o, "addrs"); ok {
		for _, v := range list {
			if s, ok := v.(string); ok {
				raw = append(raw, s)
			}
		}
	}
	addrs := Addrs(raw)
	name := ""
	if s, ok := stringValue(o, "dns_name"); ok {
		name = Name(s)
	}
	if len(addrs) == 0 {
		return "", nil
	}
	return name, addrs
}

// substringAfter is Kotlin's: what follows the first delimiter, the whole string without one.
func substringAfter(s, delimiter string) string {
	if i := strings.Index(s, delimiter); i >= 0 {
		return s[i+len(delimiter):]
	}
	return s
}

// substringBefore is Kotlin's: what precedes the first delimiter, the whole string without one.
func substringBefore(s, delimiter string) string {
	if i := strings.Index(s, delimiter); i >= 0 {
		return s[:i]
	}
	return s
}

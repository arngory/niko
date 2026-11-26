package nikoconf

import (
	"errors"
	"net/netip"
	"regexp"
	"strings"

	"libcore/nikofmt"
)

// A computer's parts of the config (Input.Platform windows or linux), which the app has no branch for: it builds
// Android's alone, where VpnService makes the TUN, routes it and keeps the app's own sockets out of it. On a computer
// the core does that itself (docs/design/desktop-stage3.md §5): the TUN with its routes, the system proxy of proxy
// mode, rules by program. The rest of the config is the phone's.

// The TUN's name: on Windows its network adapter's, as the system lists it; on Linux a tunN-like one (15 bytes at
// most).
const (
	tunNameWindows = "Niko"
	tunNameLinux   = "niko0"
)

// desktopTun is what a computer's TUN has beside the phone's fields. Its addresses stay the phone's: private ones of
// Niko's own (172.19.0.1/28, fdfe:dcba:9876::1/126), its DNS the next one, 172.19.0.2, which dns_mode hijack (the
// default with auto_route) gives the system as the TUN's resolver. No package or uid lists: those are Android's.
func (b *builder) desktopTun(tun object) {
	name := tunNameLinux
	if b.in.Platform == PlatformWindows {
		name = tunNameWindows
	}
	tun["interface_name"] = name
	// the system's default routes into the TUN; the core's own connections leave by the physical interface, as
	// route.auto_detect_interface (every config has it) binds its dialer there
	tun["auto_route"] = true
	// a family the TUN does not carry (IPv6 with IPv6 off) unreachable rather than past the TUN; on Windows also DNS
	// by another interface blocked, which the system's multihomed resolution would leak (the core's own process is
	// let through); on Linux ICMP into the TUN too, which otherwise passes it
	tun["strict_route"] = true
}

// excludeTunnelResolvers leaves masterdnsvpn's resolvers out of the TUN's routes. Its client sends its queries from
// sockets of its own, not by the core's dialer, so auto_detect_interface does not bind them: on the phone they are
// protected from the VPN (protect_path); here they would come back into the TUN and its DNS hijack. dnstt dials by
// the core's dialer and needs nothing.
func (b *builder) excludeTunnelResolvers() {
	if b.tun == nil || len(b.tunnelResolvers) == 0 {
		return
	}
	var routes []any
	seen := map[netip.Prefix]bool{}
	for _, resolver := range b.tunnelResolvers {
		if p, ok := resolverPrefix(resolver); ok && !seen[p] {
			seen[p] = true
			routes = append(routes, p.String())
		}
	}
	if len(routes) > 0 {
		b.tun["route_exclude_address"] = routes
	}
}

// resolverPrefix is the addresses of a masterdnsvpn resolver line ("1.2.3.4", "1.2.3.0/30", "1.2.3.4:5353",
// "2001:db8::1", "[2001:db8::1]:53"): its prefix, or a single address's; false for one that is none.
func resolverPrefix(line string) (netip.Prefix, bool) {
	host := line
	if strings.HasPrefix(host, "[") {
		end := strings.Index(host, "]")
		if end < 0 {
			return netip.Prefix{}, false
		}
		host = host[1:end]
	} else if strings.Count(host, ":") == 1 {
		host = substringBefore(host, ":")
	}
	if strings.Contains(host, "/") {
		p, err := netip.ParsePrefix(host)
		return p.Masked(), err == nil
	}
	addr, err := netip.ParseAddr(host)
	if err != nil {
		return netip.Prefix{}, false
	}
	return netip.PrefixFrom(addr, addr.BitLen()), true
}

// systemProxy: in proxy mode the local proxy is the system's while the core runs (D-NAV-02), where the settings say
// so (SystemProxyOn). The build does not have the core set it (the mixed inbound's set_system_proxy sets it for the
// user the core runs as, which in the service is SYSTEM or root, and on Linux root with no SUDO_USER refuses it and
// the inbound does not start): it gives it beside the config (Result.SystemProxy), and the daemon has its platform set
// it for its owner (the fork's Host.StartService), as a TUN's platform.http_proxy; ManagedService.SetSystemProxyEnabled
// turns it off and on.
func (b *builder) systemProxy() bool {
	return b.s.SystemProxyOn(b.in.Platform)
}

// SystemProxyOn: the system proxy of proxy mode, for a computer of the platform: the setting, else on Windows on, as
// a desktop client's proxy mode is there, off elsewhere.
func (s *Settings) SystemProxyOn(platform string) bool {
	if s.SystemProxy != nil {
		return *s.SystemProxy
	}
	return platform == PlatformWindows
}

// programMatch is how the core matches a rule's programs (D-RULES-02), each once, in their order: names by
// process_name, which the core holds against the path's last element; full paths, those with a separator, by
// process_path; a rule of both by process_path_regex, as each item of a core rule must match and only within one
// item's list is it either. Nil for none. The core compares them as they are, case included.
func programMatch(programs []string) object {
	var names, paths, either []any
	seen := map[string]bool{}
	for _, p := range programs {
		if p = nikofmt.KtTrim(p); p == "" || seen[p] {
			continue
		}
		seen[p] = true
		if strings.ContainsAny(p, `/\`) {
			paths = append(paths, p)
			either = append(either, "^"+regexp.QuoteMeta(p)+"$")
		} else {
			names = append(names, p)
			either = append(either, `(^|[/\\])`+regexp.QuoteMeta(p)+"$")
		}
	}
	switch {
	case len(either) == 0:
		return nil
	case len(paths) == 0:
		return object{"process_name": names}
	case len(names) == 0:
		return object{"process_path": paths}
	}
	return object{"process_path_regex": either}
}

// programsRule is the rule of the programs chosen to go through Niko or not (D-RULES-06), the first of the rules, as
// the phone's per-app proxy is ahead of them all: those of Programs through the proxy where only they go through it
// (ProgramsOnly, and the rest direct: programsOnly), else direct (ProgramsExcept). Nil for all programs, or for none
// chosen.
func (b *builder) programsRule() *Rule {
	if !b.desktop || b.s.ProgramsMode == ProgramsAll {
		return nil
	}
	programs := strings.Split(b.s.Programs, "\n")
	if programMatch(programs) == nil {
		return nil
	}
	rule := &Rule{Name: "programs", Enabled: true, Programs: programs, Outbound: -1}
	if b.s.ProgramsMode == ProgramsOnly {
		rule.Outbound = 0
	}
	return rule
}

// programsOnly: only the programs chosen go through Niko, everything else direct, its DNS too.
func (b *builder) programsOnly() bool {
	return !b.forTest && b.programsRule() != nil && b.s.ProgramsMode == ProgramsOnly
}

// putPrograms puts a rule's programs (programMatch) into a route or DNS rule.
func putPrograms(r object, match object) {
	for k, v := range match {
		r[k] = append([]any(nil), v.([]any)...)
	}
}

// DesktopSettings are a computer's defaults (nikostore's settings.json holds what the user changed over them): the
// app's, but in proxy mode, which needs no privileges of the daemon's. The system's proxy is the platform's default
// (SystemProxyOn).
func DesktopSettings() Settings {
	s := DefaultSettings()
	s.ServiceMode = ModeProxy
	s.RaycontrolAutoSwitch = true
	return s
}

// CheckDNS is the error the build would give for a DNS setting (remoteDns, directDns): its servers a line each, "#" a
// comment, each read as the build reads the one it takes; nil where it takes them.
func CheckDNS(text string) error {
	lines := dnsLines(text)
	if len(lines) == 0 {
		return errors.New("no DNS server")
	}
	for _, line := range lines {
		if _, err := dnsServerFromAddress(line, "dns", nil, nil); err != nil {
			return err
		}
	}
	return nil
}

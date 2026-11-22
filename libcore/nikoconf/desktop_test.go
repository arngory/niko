package nikoconf

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"regexp"
	"slices"
	"testing"
)

// The snapshots of a computer's configs (docs/design/desktop-stage3.md §5): Go's alone, approved in review. Of the
// app's cases below, the same input built for Windows (windows.json); of a computer's own cases (an input with its
// platform), that platform's (windows.json, linux.json). <platform>-warnings.json is what the build told, where it
// told anything; <platform>-system-proxy.json the system proxy it gave for the daemon to set, where it gave one. NIKO_GOLDEN_UPDATE=1 writes them, as the app's golden tests do; without it each must be there and
// the same by meaning. Each is also a config the core reads, with the registries its daemon has.
var desktopCases = []string{"dns-tunnel-dnstt-noauth", "dns-tunnel-masterdns-noauth", "group-selector", "rules-vpn",
	"ss-proxy-mode-login", "ss-vpn-defaults", "yggdrasil"}

func TestDesktopSnapshots(t *testing.T) {
	update := os.Getenv("NIKO_GOLDEN_UPDATE") == "1"
	entries, err := os.ReadDir(goldenConfigs)
	if err != nil {
		t.Fatal(err)
	}
	listed, own := 0, 0
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		dir := filepath.Join(goldenConfigs, e.Name())
		raw, err := os.ReadFile(filepath.Join(dir, "input.json"))
		if err != nil {
			t.Fatalf("%s: a case without its input: %v", e.Name(), err)
		}
		platform := desktopPlatform(t, raw)
		switch {
		case platform != "":
			own++
			if _, err := os.Stat(filepath.Join(dir, "android.json")); err == nil {
				t.Errorf("%s: a computer's case with an android.json", e.Name())
			}
		case slices.Contains(desktopCases, e.Name()):
			listed++
			platform = PlatformWindows
		default:
			// a snapshot of a case no longer built for a computer
			for _, p := range []string{PlatformWindows, PlatformLinux} {
				for _, name := range []string{p + ".json", p + "-warnings.json", p + "-system-proxy.json"} {
					if _, err := os.Stat(filepath.Join(dir, name)); err == nil {
						t.Errorf("%s/%s: a snapshot of a case not built for a computer", e.Name(), name)
					}
				}
			}
			continue
		}
		t.Run(e.Name(), func(t *testing.T) {
			in := goldenInput(t, raw)
			in.Platform = platform
			result, err := Build(in)
			if err != nil {
				t.Fatalf("Build: %v", err)
			}
			checkSnapshot(t, filepath.Join(dir, platform+".json"), result.Config, update)
			var warnings []byte
			if len(result.Warnings) > 0 {
				if warnings, err = json.MarshalIndent(result.Warnings, "", "  "); err != nil {
					t.Fatal(err)
				}
			}
			checkSnapshot(t, filepath.Join(dir, platform+"-warnings.json"), warnings, update)
			// the system proxy the daemon sets for its owner, where the build gives one
			var proxy []byte
			if result.SystemProxy != nil {
				if proxy, err = json.MarshalIndent(result.SystemProxy, "", "  "); err != nil {
					t.Fatal(err)
				}
			}
			checkSnapshot(t, filepath.Join(dir, platform+"-system-proxy.json"), proxy, update)
			if coreReads != nil {
				coreReads(t, result.Config)
			}
		})
	}
	if listed != len(desktopCases) {
		t.Errorf("%d of the %d cases listed for a computer are there", listed, len(desktopCases))
	}
	if own == 0 {
		t.Error("no case of a computer's own")
	}
}

// desktopPlatform is the platform of a computer's case, "" for Android's.
func desktopPlatform(t *testing.T, input []byte) string {
	t.Helper()
	var head struct {
		Platform string `json:"platform"`
	}
	if err := json.Unmarshal(input, &head); err != nil {
		t.Fatal(err)
	}
	switch head.Platform {
	case "", PlatformAndroid:
		return ""
	case PlatformWindows, PlatformLinux:
		return head.Platform
	}
	t.Fatalf("unknown platform %q", head.Platform)
	return ""
}

// checkSnapshot: the file holds what the build gave (by meaning), or is not there where it gave nothing (nil);
// with update, it is made so.
func checkSnapshot(t *testing.T, path string, got []byte, update bool) {
	t.Helper()
	want := readOptional(t, path)
	switch {
	case update && got == nil:
		if want != nil {
			if err := os.Remove(path); err != nil {
				t.Fatal(err)
			}
		}
	case update:
		if err := os.WriteFile(path, append(got, '\n'), 0o644); err != nil {
			t.Fatal(err)
		}
	case got == nil && want != nil:
		t.Errorf("%s: the build gives none now", path)
	case got != nil && want == nil:
		t.Errorf("%s: no snapshot; NIKO_GOLDEN_UPDATE=1 writes it, to check and commit:\n%s", path, got)
	case got != nil:
		if diff := configDifferences(t, want, got); diff != "" {
			t.Errorf("%s:\n%s\nthe build's:\n%s", path, diff, got)
		}
	}
}

// coreReads: the core's own check of a snapshot, where the build has the core (desktop_core_test.go); nil without
// it (ci.yml's golden job has no checkout of the core fork)
var coreReads func(t *testing.T, config []byte)

// The desktop's own parts, field by field: what the snapshots show, said here once.
func TestDesktopParts(t *testing.T) {
	ss := goldenInput(t, []byte(`{"profile": 1, "profiles": [{"id": 1, "group": 1, "order": 1, "link": "ss-sip002"}],
		"groups": [{"id": 1, "order": 1, "name": "main"}]}`))
	var proxy *SystemProxy
	build := func(platform, mode string, systemProxy *bool) map[string]any {
		t.Helper()
		in := ss
		in.Platform = platform
		in.Settings.ServiceMode = mode
		in.Settings.SystemProxy = systemProxy
		result, err := Build(in)
		if err != nil {
			t.Fatal(err)
		}
		var config map[string]any
		if err := json.Unmarshal(result.Config, &config); err != nil {
			t.Fatal(err)
		}
		proxy = result.SystemProxy
		return config["inbounds"].([]any)[0].(map[string]any)
	}
	off := false
	// the system proxy is the daemon's to set for its owner (Result.SystemProxy), never the core's: on Windows by
	// default, elsewhere where set, in proxy mode alone
	local := &SystemProxy{Server: "127.0.0.1", Port: 2080}
	for _, c := range []struct {
		platform, mode string
		systemProxy    *bool
		want           map[string]any
		proxy          *SystemProxy
	}{
		{PlatformAndroid, ModeVPN, nil, map[string]any{"interface_name": nil, "auto_route": nil,
			"strict_route": nil}, nil},
		{PlatformWindows, ModeVPN, nil, map[string]any{"interface_name": "Niko", "auto_route": true,
			"strict_route": true}, nil},
		{PlatformWindows, ModeVPN, ptrTo(true), map[string]any{"interface_name": "Niko"}, nil},
		{PlatformLinux, ModeVPN, nil, map[string]any{"interface_name": "niko0", "auto_route": true,
			"strict_route": true}, nil},
		{PlatformAndroid, ModeProxy, nil, map[string]any{"set_system_proxy": nil}, nil},
		{PlatformAndroid, ModeProxy, ptrTo(true), map[string]any{"set_system_proxy": nil}, nil},
		{PlatformWindows, ModeProxy, nil, map[string]any{"set_system_proxy": nil}, local},
		{PlatformWindows, ModeProxy, &off, map[string]any{"set_system_proxy": nil}, nil},
		{PlatformWindows, ModeProxy, ptrTo(true), map[string]any{"set_system_proxy": nil}, local},
		{PlatformLinux, ModeProxy, nil, map[string]any{"set_system_proxy": nil}, nil},
		{PlatformLinux, ModeProxy, ptrTo(true), map[string]any{"set_system_proxy": nil}, local},
	} {
		inbound := build(c.platform, c.mode, c.systemProxy)
		for k, v := range c.want {
			if got := inbound[k]; !reflect.DeepEqual(got, v) {
				t.Errorf("%s, %s mode, system proxy %v: %s %v, want %v", c.platform, c.mode, c.systemProxy, k, got, v)
			}
		}
		if !reflect.DeepEqual(proxy, c.proxy) {
			t.Errorf("%s, %s mode, system proxy %v: the system proxy %+v, want %+v", c.platform, c.mode,
				c.systemProxy, proxy, c.proxy)
		}
	}
	if _, err := Build(Input{Platform: "darwin"}); err == nil {
		t.Error("an unknown platform built")
	}
}

// masterdnsvpn's resolver lines as the TUN leaves them out; one that is no address, not at all.
func TestResolverPrefix(t *testing.T) {
	for line, want := range map[string]string{
		"198.51.100.53": "198.51.100.53/32", "192.0.2.1/30": "192.0.2.0/30", "192.0.2.9:5353": "192.0.2.9/32",
		"192.0.2.9/30:53": "192.0.2.8/30", "2001:db8::1": "2001:db8::1/128", "2001:db8::1/64": "2001:db8::/64",
		"[2001:db8::2]:53": "2001:db8::2/128", "999.1.1.1": "", "[2001:db8::2": "",
	} {
		p, ok := resolverPrefix(line)
		if got := map[bool]string{true: p.String(), false: ""}[ok]; got != want {
			t.Errorf("%q: %q, want %q", line, got, want)
		}
	}
}

// A rule's programs: names, paths, or both as one list of regular expressions, which the core takes as either.
func TestProgramMatch(t *testing.T) {
	for _, c := range []struct {
		programs []string
		want     object
	}{
		{nil, nil},
		{[]string{" ", ""}, nil},
		{[]string{"firefox.exe", " firefox.exe ", "Telegram.exe"}, object{"process_name": []any{"firefox.exe",
			"Telegram.exe"}}},
		{[]string{"/usr/bin/curl", `C:\Tools\curl.exe`}, object{"process_path": []any{"/usr/bin/curl",
			`C:\Tools\curl.exe`}}},
		{[]string{`C:\Program Files\App (x86)\app.exe`, "curl"}, object{"process_path_regex": []any{
			`^C:\\Program Files\\App \(x86\)\\app\.exe$`, `(^|[/\\])curl$`}}},
	} {
		if got := programMatch(c.programs); !reflect.DeepEqual(got, c.want) {
			t.Errorf("%q: %v, want %v", c.programs, got, c.want)
		}
	}
	re := regexp.MustCompile(programMatch([]string{"curl", "/opt/x"})["process_path_regex"].([]any)[0].(string))
	for path, want := range map[string]bool{"/usr/bin/curl": true, `C:\bin\curl`: true, "curl": true,
		"/usr/bin/xcurl": false, "/usr/bin/curl.exe": false} {
		if re.MatchString(path) != want {
			t.Errorf("curl and %s: %v", path, !want)
		}
	}
}

// The programs through Niko or not (D-RULES-06): a rule ahead of the user's, and with only those through Niko the rest
// direct, its DNS too; nothing for all programs, for none chosen, or on the phone, whose per-app proxy is VpnService's.
func TestProgramsRule(t *testing.T) {
	ss := goldenInput(t, []byte(`{"profile": 1, "profiles": [{"id": 1, "group": 1, "order": 1, "link": "ss-sip002"}],
		"groups": [{"id": 1, "order": 1, "name": "main"}],
		"rules": [{"id": 7, "enabled": true, "userOrder": 1, "domains": "domain:example.org", "outbound": -2}]}`))
	for _, c := range []struct {
		platform, programs string
		mode               int
		first              object
		final, dnsFinal    string
	}{
		{PlatformWindows, "curl.exe", ProgramsOnly, object{"process_name": []any{"curl.exe"}, "outbound": "proxy"},
			"bypass", "dns-direct"},
		{PlatformLinux, "/usr/bin/curl\n", ProgramsExcept, object{"process_path": []any{"/usr/bin/curl"},
			"outbound": "bypass"}, "proxy", "dns-remote"},
		{PlatformWindows, "curl.exe", ProgramsAll, nil, "proxy", "dns-remote"},
		{PlatformWindows, " \n", ProgramsOnly, nil, "proxy", "dns-remote"},
		{PlatformAndroid, "curl.exe", ProgramsOnly, nil, "proxy", "dns-remote"},
	} {
		in := ss
		in.Platform = c.platform
		in.Settings.ServiceMode = ModeProxy
		in.Settings.ProgramsMode, in.Settings.Programs = c.mode, c.programs
		result, err := Build(in)
		if err != nil {
			t.Fatal(err)
		}
		var config struct {
			Route struct {
				Rules []object `json:"rules"`
				Final string   `json:"final"`
			} `json:"route"`
			DNS struct {
				Final string `json:"final"`
			} `json:"dns"`
		}
		if err := json.Unmarshal(result.Config, &config); err != nil {
			t.Fatal(err)
		}
		// the user's first rule is the first after the built-in ones (niko.internal, sniff, the DNS hijacks)
		var first object
		for _, r := range config.Route.Rules {
			if _, ok := r["action"]; !ok {
				first = r
				break
			}
		}
		if c.first == nil {
			c.first = object{"outbound": nil}
		}
		for k, v := range c.first {
			if !reflect.DeepEqual(first[k], v) {
				t.Errorf("%s, %q in mode %d: the first rule's %s %v, want %v", c.platform, c.programs, c.mode, k,
					first[k], v)
			}
		}
		if config.Route.Final != c.final || config.DNS.Final != c.dnsFinal {
			t.Errorf("%s, %q in mode %d: final %s, DNS %s; want %s, %s", c.platform, c.programs, c.mode,
				config.Route.Final, config.DNS.Final, c.final, c.dnsFinal)
		}
	}
}

// CheckDNS takes what the build takes of a DNS setting and refuses what it would refuse.
func TestCheckDNS(t *testing.T) {
	for _, good := range []string{"local", "dhcp://auto", "https://dns.google/dns-query", "1.1.1.1", "2001:db8::1",
		"tls://dns.example:853", "quic://dns.example", "h3://dns.example/dns-query", "# mine\n\ntcp://9.9.9.9\n"} {
		if err := CheckDNS(good); err != nil {
			t.Errorf("%q: %v", good, err)
		}
	}
	for _, bad := range []string{"", " \n# none", "ftp://dns.example", "udp://1.1.1.1:0", "tcp://dns.example/x",
		"https://user@dns.example/", "local\nsftp://x"} {
		if CheckDNS(bad) == nil {
			t.Errorf("%q taken", bad)
		}
	}
	if s := DesktopSettings(); s.ServiceMode != ModeProxy || s.SystemProxy != nil || s.MixedPort != 2080 {
		t.Errorf("a computer's defaults: %+v", s)
	}
}

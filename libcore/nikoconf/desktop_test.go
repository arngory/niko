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
// told anything. NIKO_GOLDEN_UPDATE=1 writes them, as the app's golden tests do; without it each must be there and
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
				for _, name := range []string{p + ".json", p + "-warnings.json"} {
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
		return config["inbounds"].([]any)[0].(map[string]any)
	}
	off := false
	for _, c := range []struct {
		platform, mode string
		systemProxy    *bool
		want           map[string]any
	}{
		{PlatformAndroid, ModeVPN, nil, map[string]any{"interface_name": nil, "auto_route": nil,
			"strict_route": nil}},
		{PlatformWindows, ModeVPN, nil, map[string]any{"interface_name": "Niko", "auto_route": true,
			"strict_route": true}},
		{PlatformLinux, ModeVPN, nil, map[string]any{"interface_name": "niko0", "auto_route": true,
			"strict_route": true}},
		{PlatformAndroid, ModeProxy, nil, map[string]any{"set_system_proxy": nil}},
		{PlatformWindows, ModeProxy, nil, map[string]any{"set_system_proxy": nil}},
		{PlatformWindows, ModeProxy, &off, map[string]any{"set_system_proxy": nil}},
		{PlatformWindows, ModeProxy, ptrTo(true), map[string]any{"set_system_proxy": true}},
		{PlatformLinux, ModeProxy, nil, map[string]any{"set_system_proxy": nil}},
		{PlatformLinux, ModeProxy, ptrTo(true), map[string]any{"set_system_proxy": true}},
	} {
		inbound := build(c.platform, c.mode, c.systemProxy)
		for k, v := range c.want {
			if got := inbound[k]; !reflect.DeepEqual(got, v) {
				t.Errorf("%s, %s mode, system proxy %v: %s %v, want %v", c.platform, c.mode, c.systemProxy, k, got, v)
			}
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

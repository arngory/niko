package nikoconf

import (
	"os"
	"path/filepath"
	"reflect"
	"testing"
)

// What a running config left out, for the daemon to fetch and start the core again with (the app's
// missingInConfig): each list not here once, in the rules' order; the preset's direct rule, which waits, says none of
// its own. An export leaves out none of the app's lists.
func TestMissingRuleSets(t *testing.T) {
	build := func(name string, export bool) Result {
		raw, err := os.ReadFile(filepath.Join(goldenConfigs, name, "input.json"))
		if err != nil {
			t.Fatal(err)
		}
		in := goldenInput(t, raw)
		in.ForExport = export
		result, err := Build(in)
		if err != nil {
			t.Fatal(err)
		}
		return result
	}
	const lists = "srs:https://raw.githubusercontent.com/runetfreedom/russia-v2ray-rules-dat/release/sing-box/"
	if got, want := build("rules-russia-waits", false).MissingRuleSets, []string{
		lists + "rule-set-geosite/geosite-ru-blocked.srs", lists + "rule-set-geoip/geoip-ru-blocked.srs",
	}; !reflect.DeepEqual(got, want) {
		t.Errorf("waits: %q", got)
	}
	if got, want := build("rules-russia-downloaded", false).MissingRuleSets, []string{
		"srs:http://lists.example.com/plain.srs", "srs:https://lists.example.com/absent.srs",
	}; !reflect.DeepEqual(got, want) {
		t.Errorf("downloaded: %q", got)
	}
	if got := build("rules-russia-waits", true).MissingRuleSets; got != nil {
		t.Errorf("an export: %q", got)
	}
}

// The items of rules, as RuleSetFiles.itemsOf: the downloadable ones of the domains and IPs, each once.
func TestRuleSetItems(t *testing.T) {
	rules := []Rule{
		{Domains: "srs:https://a.example/x.srs\ngeosite:ads,rule-set:https://a.example/r.srs", IP: "srs:https://a.example/ip.srs"},
		{Domains: "srs:https://a.example/x.srs", IP: "203.0.113.0/24"},
	}
	want := []string{"srs:https://a.example/x.srs", "srs:https://a.example/ip.srs"}
	if got := RuleSetItems(rules); !reflect.DeepEqual(got, want) {
		t.Fatalf("%q", got)
	}
	// the format is of the URL without its query; the name of the URL trimmed, as RuleSetFiles.url
	if RuleSetFileName("srs: https://a.example/list.json?v=1 ") != RuleSetFileName("srs:https://a.example/list.json?v=1") {
		t.Fatal("a URL with blanks around it named apart")
	}
	if got := RuleSetFileName("srs:https://a.example/list.json?v=1"); filepath.Ext(got) != ".json" || len(got) != 37 {
		t.Fatalf("%s", got)
	}
}

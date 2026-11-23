package nikostore

import (
	"bytes"
	"errors"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"libcore/nikoconf"
)

// testdata/store-schema2.json is a store of schema 2 as this package wrote it: schema 1's fixture opened, a
// subscription's group saved (unsealed).
const fixtureSchema2 = "testdata/store-schema2.json"

// Schema 2 to 3, the real migration: the fixture of schema 2 copied into backup/ and opened as it was (groups, the
// subscription, profiles), with no rules and the first rule's id 1.
func TestMigration2to3(t *testing.T) {
	dir := t.TempDir()
	original, err := os.ReadFile(fixtureSchema2)
	if err != nil {
		t.Fatal(err)
	}
	os.WriteFile(filepath.Join(dir, storeFile), original, 0o600)
	at := time.Date(2026, 11, 23, 9, 0, 0, 0, time.UTC)
	s, err := Open(dir, Options{Sealer: NoSealer{}, Now: func() time.Time { return at }})
	if err != nil {
		t.Fatal(err)
	}
	groups := s.Groups()
	if len(groups) != 3 || groups[2].Type != GroupSubscription || groups[2].Subscription == nil ||
		groups[2].Subscription.Link != "https://sub.example.com/s/fake-token" {
		t.Fatalf("groups: %+v", groups)
	}
	if rows, _ := s.Profiles(0); len(rows) != 2 {
		t.Fatalf("profiles: %+v", rows)
	}
	if rules := s.Rules(); len(rules) != 0 {
		t.Fatalf("rules: %+v", rules)
	}
	if saved, err := s.SaveRule(nikoconf.Rule{Name: "first", Domains: "example.com", Enabled: true}); err != nil || saved.ID != 1 || saved.UserOrder != 1 {
		t.Fatalf("%+v %v", saved, err)
	}
	copied, err := os.ReadFile(filepath.Join(dir, backupDir, "2-20261123-090000", storeFile))
	if err != nil || !bytes.Equal(copied, original) {
		t.Fatalf("the backup: %v", err)
	}
	data, _ := os.ReadFile(filepath.Join(dir, storeFile))
	if schema, _ := schemaOf(data); schema != Schema() {
		t.Fatalf("written as schema %d", schema)
	}
}

// The rules: saved at the end with the next id and order, changed where they are (their apps kept), moved and
// numbered again, deleted; each write a change of KindRules; all of it there once the store opens again; an id is
// not given twice.
func TestRules(t *testing.T) {
	dir := t.TempDir()
	var changes []Change
	s := open(t, dir, NoSealer{}, &changes)
	profile, err := s.AddProfiles(0, beans(t, socksLink))
	if err != nil {
		t.Fatal(err)
	}
	save := func(r nikoconf.Rule) nikoconf.Rule {
		t.Helper()
		saved, err := s.SaveRule(r)
		if err != nil {
			t.Fatal(err)
		}
		return saved
	}
	a := save(nikoconf.Rule{Name: "a", Domains: "domain:example.com", Outbound: -1, Enabled: true, DpiBypass: true})
	b := save(nikoconf.Rule{Name: "b", IP: "203.0.113.0/24", Port: "443", Network: "udp", Outbound: -2})
	c := save(nikoconf.Rule{Name: "c", Domains: "via.example", Outbound: profile[0].ID, Programs: []string{"app.exe"},
		Config: `{"invert": false}`})
	if a.ID != 1 || b.ID != 2 || c.ID != 3 || c.UserOrder != 3 {
		t.Fatalf("%+v %+v %+v", a, b, c)
	}
	// the window gives no apps: a new rule has none, a changed one keeps its own
	if r := save(nikoconf.Rule{Name: "d", Domains: "d.example", Packages: []string{"org.example"}}); r.Packages != nil {
		t.Fatalf("%+v", r)
	}
	s.mu.Lock()
	s.state.rules[0].Packages = []string{"org.example.app"}
	s.mu.Unlock()
	a.Name, a.Enabled = "a again", false
	if r := save(a); r.UserOrder != 1 || !reflect.DeepEqual(r.Packages, []string{"org.example.app"}) {
		t.Fatalf("%+v", r)
	}
	if err = s.MoveRules([]int64{3, 1}, 2); err != nil {
		t.Fatal(err)
	}
	var names []string
	for _, r := range s.Rules() {
		names = append(names, r.Name)
	}
	if strings.Join(names, ",") != "c,a again,b,d" {
		t.Fatalf("moved: %v", names)
	}
	if deleted, err := s.DeleteRules([]int64{2, 99}); err != nil || !reflect.DeepEqual(deleted, []int64{2}) {
		t.Fatalf("%v %v", deleted, err)
	}
	if !reflect.DeepEqual(changes[len(changes)-1], Change{Kind: KindRules, IDs: []int64{2}}) {
		t.Fatalf("changes: %+v", changes)
	}
	before := s.Rules()

	again := open(t, dir, NoSealer{}, nil)
	if got := again.Rules(); !reflect.DeepEqual(got, before) {
		t.Fatalf("opened again:\n%+v\n%+v", got, before)
	}
	if r, err := again.SaveRule(nikoconf.Rule{Name: "e"}); err != nil || r.ID != 5 || r.UserOrder != 5 {
		t.Fatalf("%+v %v", r, err)
	}
	// a rule whose profile was deleted keeps it when changed, but no rule takes a profile that is not there
	if _, err = again.DeleteProfiles([]int64{profile[0].ID}); err != nil {
		t.Fatal(err)
	}
	moved := again.Rules()[0]
	moved.Name = "c again"
	if _, err = again.SaveRule(moved); err != nil {
		t.Fatalf("a rule of a deleted profile: %v", err)
	}
	if _, err = again.SaveRule(nikoconf.Rule{Name: "f", Outbound: 77}); !errors.Is(err, ErrInvalid) {
		t.Fatalf("no such profile: %v", err)
	}
}

// What a rule may not be is refused, and nothing written.
func TestRuleInvalid(t *testing.T) {
	s := open(t, t.TempDir(), NoSealer{}, nil)
	for name, r := range map[string]nikoconf.Rule{
		"network":      {Network: "sctp"},
		"outbound":     {Outbound: -3},
		"config":       {Config: "[1]"},
		"not JSON":     {Config: "{"},
		"a key twice":  {Config: `{"a":1,"a":2}`},
		"a long list":  {Domains: strings.Repeat("a", maxRuleText+1)},
		"not UTF-8":    {Domains: "\xff"},
		"a long name":  {Name: strings.Repeat("n", maxName+1)},
		"a program":    {Programs: []string{"a\nb"}},
		"many":         {Programs: make([]string, maxRulePrograms+1)},
		"no such rule": {ID: 42},
	} {
		if _, err := s.SaveRule(r); !errors.Is(err, ErrInvalid) && !errors.Is(err, ErrNotFound) {
			t.Errorf("%s: %v", name, err)
		}
	}
	if len(s.Rules()) != 0 {
		t.Fatal("written")
	}
	if err := s.MoveRules([]int64{1}, 0); !errors.Is(err, ErrNotFound) {
		t.Fatalf("move: %v", err)
	}
	if err := s.MoveRules(make([]int64, maxRules+1), 0); !errors.Is(err, ErrInvalid) {
		t.Fatalf("move of more ids than rules kept: %v", err)
	}
}

// The Russian preset replaces its rules there are, a rule of the user's naming one of its lists stays; its rule-sets
// are those to download.
func TestRussiaPreset(t *testing.T) {
	s := open(t, t.TempDir(), NoSealer{}, nil)
	first, err := s.ApplyRussiaPreset("blocked", "direct")
	if err != nil || len(first) != 2 || first[0].Name != "blocked" || first[1].Outbound != -1 || !first[1].Enabled {
		t.Fatalf("%+v %v", first, err)
	}
	preset := nikoconf.RussiaPreset("", "")
	mine, err := s.SaveRule(nikoconf.Rule{Name: "mine", Domains: preset[0].Domains, Outbound: -2, Enabled: true})
	if err != nil {
		t.Fatal(err)
	}
	again, err := s.ApplyRussiaPreset("blocked 2", "direct 2")
	if err != nil {
		t.Fatal(err)
	}
	rules := s.Rules()
	if len(rules) != 3 || rules[0].ID != mine.ID || rules[1].ID != again[0].ID || rules[2].Name != "direct 2" ||
		again[0].ID != 4 || again[0].UserOrder != 4 {
		t.Fatalf("%+v", rules)
	}
	items := s.RuleSetItems(true)
	if len(items) != 4 || !nikoconf.IsRuleSetItem(items[0]) {
		t.Fatalf("%q", items)
	}
}

// The rules' settings: the rule-sets' update interval from an hour to 30 days, «Обход DPI» a preset of the app's or
// a ByeDPI string the build reads.
func TestRuleSettings(t *testing.T) {
	s := open(t, t.TempDir(), NoSealer{}, nil)
	from := nikoconf.DesktopSettings()
	from.RuleSetUpdateHours, from.DpiDirect, from.DpiDirectPreset, from.EnableDNSRouting = 6, nikoconf.DpiPreset, "split_sni", false
	got, err := s.UpdateSettings(from, []string{"ruleSetUpdateHours", "dpiDirect", "dpiDirectPreset", "enableDnsRouting"})
	if err != nil || got.RuleSetUpdateHours != 6 || got.DpiDirect != nikoconf.DpiPreset || got.DpiDirectPreset != "split_sni" ||
		got.EnableDNSRouting {
		t.Fatalf("%+v %v", got, err)
	}
	for name, change := range map[string]func(*nikoconf.Settings){
		"ruleSetUpdateHours": func(s *nikoconf.Settings) { s.RuleSetUpdateHours = 0 },
		"dpiDirect":          func(s *nikoconf.Settings) { s.DpiDirect = 3 },
		"dpiDirectPreset":    func(s *nikoconf.Settings) { s.DpiDirectPreset = "none" },
		"dpiDirectCustom":    func(s *nikoconf.Settings) { s.DpiDirectCustom = "ciadpi --no-such-option" },
	} {
		bad := nikoconf.DesktopSettings()
		change(&bad)
		if _, err := s.UpdateSettings(bad, []string{name}); !errors.Is(err, ErrInvalid) {
			t.Errorf("%s: %v", name, err)
		}
	}
	from.DpiDirectCustom = "ciadpi -s 1 -q 1+sm"
	if got, err = s.UpdateSettings(from, []string{"dpiDirectCustom"}); err != nil || got.DpiDirectCustom != from.DpiDirectCustom {
		t.Fatalf("%+v %v", got, err)
	}
}

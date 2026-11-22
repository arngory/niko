package nikostore

import (
	"bytes"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"libcore/nikofmt"
	"libcore/nikosub"
)

// Schema 1 to 2, the real migration: the fixture of schema 1 copied into backup/ and opened as it was (groups,
// profiles, the next ids); a group of type 1 that schema 1 could not have becomes a basic one.
func TestMigration1to2(t *testing.T) {
	dir := t.TempDir()
	original := copyFixture(t, dir)
	at := time.Date(2026, 11, 22, 9, 0, 0, 0, time.UTC)
	s, err := Open(dir, Options{Sealer: NoSealer{}, Now: func() time.Time { return at }})
	if err != nil {
		t.Fatal(err)
	}
	if g := s.Groups(); len(g) != 2 || !g[0].Ungrouped || g[1].Name != "work" || g[1].Type != GroupBasic {
		t.Fatalf("groups: %+v", g)
	}
	rows, err := s.Profiles(0)
	if err != nil || len(rows) != 2 || rows[0].Name != "SOCKS5" || rows[1].Address != "203.0.113.7:443" {
		t.Fatalf("profiles: %+v %v", rows, err)
	}
	if added, err := s.AddProfiles(0, beans(t, ssLink)); err != nil || added[0].ID != 4 {
		t.Fatalf("%+v %v", added, err)
	}
	copied, err := os.ReadFile(filepath.Join(dir, backupDir, "1-20261122-090000", storeFile))
	if err != nil || !bytes.Equal(copied, original) {
		t.Fatalf("the backup: %v", err)
	}
	data, _ := os.ReadFile(filepath.Join(dir, storeFile))
	if schema, _ := schemaOf(data); schema != 2 {
		t.Fatalf("written as schema %d", schema)
	}

	// a group of type 1 without a subscription
	other := t.TempDir()
	var doc map[string]any
	_ = json.Unmarshal(original, &doc)
	doc["groups"] = append(doc["groups"].([]any), map[string]any{"id": 3, "order": 3, "name": "odd", "type": 1})
	data, _ = json.Marshal(doc)
	if err = os.WriteFile(filepath.Join(other, storeFile), data, 0o600); err != nil {
		t.Fatal(err)
	}
	s, err = Open(other, Options{Sealer: NoSealer{}})
	if err != nil {
		t.Fatal(err)
	}
	if g := s.Groups(); len(g) != 3 || g[2].Type != GroupBasic || g[2].Subscription != nil {
		t.Fatalf("groups: %+v", g)
	}
}

const subLink = "https://sub.example.com/s/fake-token?flag=clash"

// A subscription's group: made with the app's default name and defaults, its address sealed on the disk and back
// when opened; another sealer cannot open it (the group cannot update until its address is given again); its
// profiles are its subscription's alone.
func TestSubscriptionGroup(t *testing.T) {
	dir := t.TempDir()
	var changes []Change
	when := time.UnixMilli(1790000000123)
	s, err := Open(dir, Options{Sealer: testSealer{"test"}, Now: func() time.Time { return when },
		OnChange: func(c Change) { changes = append(changes, c) }})
	if err != nil {
		t.Fatal(err)
	}
	for _, bad := range []string{"", "ftp://sub.example.com/x", "https://", "sub.example.com/x", "content://x/y"} {
		if _, err = s.SaveSubscriptionGroup(0, "", SubscriptionSettings{Link: bad}); !errors.Is(err, ErrInvalid) {
			t.Errorf("%q: %v", bad, err)
		}
	}
	if _, err = s.SaveSubscriptionGroup(0, "", SubscriptionSettings{Link: subLink, AutoUpdateDelay: 5}); !errors.Is(err, ErrInvalid) {
		t.Errorf("an interval of 5 minutes: %v", err)
	}
	g, err := s.SaveSubscriptionGroup(0, "", SubscriptionSettings{Link: " " + subLink + " ", Deduplication: true})
	if err != nil {
		t.Fatal(err)
	}
	if g.Name != "Subscription #1790000000123" || g.Type != GroupSubscription || g.Subscription.Link != subLink ||
		!g.Subscription.Deduplication || g.Subscription.AutoUpdateDelay != 1440 || g.Subscription.AutoUpdate {
		t.Fatalf("%+v %+v", g, g.Subscription)
	}
	if len(changes) != 1 || changes[0].Kind != KindGroups || changes[0].IDs[0] != g.ID {
		t.Fatalf("changes: %+v", changes)
	}
	data, _ := os.ReadFile(filepath.Join(dir, storeFile))
	if bytes.Contains(data, []byte("fake-token")) || !bytes.Contains(data, []byte(`"sealed": "test:`)) {
		t.Fatalf("the address in the clear:\n%s", data)
	}
	if _, err = s.AddProfiles(g.ID, beans(t, socksLink)); !errors.Is(err, ErrRefused) {
		t.Errorf("an import into it: %v", err)
	}
	if _, err = s.SaveGroup(Group{ID: g.ID, Name: "x"}); !errors.Is(err, ErrRefused) {
		t.Errorf("a basic save of it: %v", err)
	}
	// its settings: what its updates set stays, another address forgets the provider's spare
	if _, err = s.ApplyUpdate(g.ID, nikosub.Update{Name: "Provider", Subscription: nikosub.Subscription{Link: subLink,
		AutoUpdateDelay: 720, AutoUpdate: true, LastUpdated: 1790000500, UserInfo: "upload=1; total=10",
		FallbackLink: "https://spare.example.com/s/fake-token"}, FromName: g.Name, From: *g.Subscription}); err != nil {
		t.Fatal(err)
	}
	g, err = s.SaveSubscriptionGroup(g.ID, "", SubscriptionSettings{Link: subLink, AutoUpdate: true, AutoUpdateDelay: 60})
	if err != nil || g.Name != "Provider" || g.Subscription.LastUpdated != 1790000500 || g.Subscription.AutoUpdateDelay != 60 ||
		g.Subscription.Deduplication || g.Subscription.FallbackLink == "" || g.Subscription.UserInfo != "upload=1; total=10" {
		t.Fatalf("%+v %+v %v", g, g.Subscription, err)
	}
	g, err = s.SaveSubscriptionGroup(g.ID, "Renamed", SubscriptionSettings{Link: subLink + "&x=1"})
	if err != nil || g.Name != "Renamed" || g.Subscription.FallbackLink != "" {
		t.Fatalf("%+v %+v %v", g, g.Subscription, err)
	}

	// opened again: the address is back
	s = open(t, dir, testSealer{"test"}, nil)
	got, _, err := s.SubscriptionOf(g.ID)
	if err != nil || got.Subscription.Link != subLink+"&x=1" || got.Name != "Renamed" {
		t.Fatalf("%+v %v", got, err)
	}
	// by another sealer: kept as it is, not updated, until the address is given again
	s = open(t, dir, testSealer{"other"}, nil)
	if _, _, err = s.SubscriptionOf(g.ID); !errors.Is(err, ErrRefused) {
		t.Fatalf("an unreadable subscription: %v", err)
	}
	if groups := s.Subscriptions(); len(groups) != 1 || groups[0].Subscription.Link != "" {
		t.Fatalf("%+v", groups)
	}
	before, _ := os.ReadFile(filepath.Join(dir, storeFile))
	if _, err = s.AddProfiles(0, beans(t, socksLink)); err != nil {
		t.Fatal(err)
	}
	after, _ := os.ReadFile(filepath.Join(dir, storeFile))
	if !bytes.Contains(after, []byte(sealedOf(t, before, g.ID))) {
		t.Fatal("the sealed address was not kept")
	}
	if _, err = s.SaveSubscriptionGroup(g.ID, "", SubscriptionSettings{Link: subLink}); err != nil {
		t.Fatal(err)
	}
	if _, _, err = s.SubscriptionOf(g.ID); err != nil {
		t.Fatal(err)
	}
	if err = s.DeleteGroup(g.ID); err != nil || len(s.Subscriptions()) != 0 {
		t.Fatalf("deleted: %v", err)
	}
}

func sealedOf(t *testing.T, data []byte, id int64) string {
	var doc document
	if err := json.Unmarshal(data, &doc); err != nil {
		t.Fatal(err)
	}
	for _, g := range doc.Groups {
		if g.ID == id {
			return g.Sealed
		}
	}
	t.Fatalf("no group %d", id)
	return ""
}

// An update written in one go: the profiles as the plan has them, in the app's order, the deleted ones gone and the
// selection with them; a profile the user deleted meanwhile stays deleted.
func TestApplyUpdate(t *testing.T) {
	dir := t.TempDir()
	var changes []Change
	s := open(t, dir, NoSealer{}, &changes)
	g, err := s.SaveSubscriptionGroup(0, "provider", SubscriptionSettings{Link: subLink})
	if err != nil {
		t.Fatal(err)
	}
	links := func(list ...string) []nikofmt.Bean { return nikosub.Prepare(beans(t, list...)) }
	update := func(proxies []nikofmt.Bean) Applied {
		t.Helper()
		group, exists, err := s.SubscriptionOf(g.ID)
		if err != nil {
			t.Fatal(err)
		}
		plan, err := nikosub.Merge(exists, proxies, false)
		if err != nil {
			t.Fatal(err)
		}
		applied, err := s.ApplyUpdate(g.ID, nikosub.Update{Name: group.Name, Subscription: *group.Subscription, Plan: plan,
			FromName: group.Name, From: *group.Subscription})
		if err != nil {
			t.Fatal(err)
		}
		return applied
	}
	first := update(links("socks5://192.0.2.1:1080#a", "socks5://192.0.2.2:1080#b", "socks5://192.0.2.3:1080#c"))
	if len(first.Added) != 3 {
		t.Fatalf("%+v", first)
	}
	if err = s.Select(first.Added[2]); err != nil {
		t.Fatal(err)
	}
	names := func() string {
		rows, _ := s.Profiles(g.ID)
		var out []string
		for _, r := range rows {
			out = append(out, r.Name+":"+r.Address)
		}
		return strings.Join(out, " ")
	}
	if got := names(); got != "a:192.0.2.1:1080 b:192.0.2.2:1080 c:192.0.2.3:1080" {
		t.Fatal(got)
	}
	// the user deletes b meanwhile; the provider drops c (the selected one), changes a, adds d
	if _, err = s.DeleteProfiles([]int64{first.Added[1]}); err != nil {
		t.Fatal(err)
	}
	group, exists, _ := s.SubscriptionOf(g.ID)
	plan, err := nikosub.Merge(exists, links("socks5://192.0.2.4:1080#d", "socks5://192.0.2.1:1081#a", "socks5://192.0.2.2:1080#b"), false)
	if err != nil {
		t.Fatal(err)
	}
	// b came back as a new profile; the plan's c is deleted
	changes = nil
	applied, err := s.ApplyUpdate(g.ID, nikosub.Update{Name: "renamed", Subscription: *group.Subscription, Plan: plan,
		FromName: group.Name, From: *group.Subscription})
	if err != nil {
		t.Fatal(err)
	}
	if len(applied.Added) != 2 {
		t.Fatalf("%+v", applied)
	}
	// a changed keeps its number (1), d is 1 too and newer, b is 3
	if got := names(); got != "a:192.0.2.1:1081 d:192.0.2.4:1080 b:192.0.2.2:1080" {
		t.Fatal(got)
	}
	if s.Selected() != 0 {
		t.Fatal("the deleted profile stays selected")
	}
	if gs := s.Subscriptions(); gs[0].Name != "renamed" || gs[0].Profiles != 3 {
		t.Fatalf("%+v", gs)
	}
	var kinds []Kind
	for _, c := range changes {
		kinds = append(kinds, c.Kind)
	}
	if len(kinds) != 3 || kinds[0] != KindGroups || kinds[1] != KindProfiles || kinds[2] != KindSelected {
		t.Fatalf("changes: %+v", changes)
	}
	// into a basic group: refused
	if _, err = s.ApplyUpdate(s.DefaultGroup(), nikosub.Update{}); !errors.Is(err, ErrRefused) {
		t.Fatal(err)
	}
}

// An update runs for a while: what the user changed meanwhile stays, what the update changed and the user did not is
// the update's, a link changed meanwhile refuses the update.
func TestApplyUpdateMeanwhile(t *testing.T) {
	s := open(t, t.TempDir(), NoSealer{}, nil)
	g, err := s.SaveSubscriptionGroup(0, "", SubscriptionSettings{Link: subLink})
	if err != nil {
		t.Fatal(err)
	}
	from, fromName := *g.Subscription, g.Name
	updated := from
	updated.UserInfo, updated.LastUpdated, updated.AutoUpdate, updated.AutoUpdateDelay = "upload=1", 1790000500, true, 720
	// meanwhile the user renames the group and sets the delay and deduplication
	if _, err = s.SaveSubscriptionGroup(g.ID, "mine", SubscriptionSettings{Link: subLink, AutoUpdateDelay: 60,
		Deduplication: true}); err != nil {
		t.Fatal(err)
	}
	if _, err = s.ApplyUpdate(g.ID, nikosub.Update{Name: "Provider", Subscription: updated, FromName: fromName,
		From: from}); err != nil {
		t.Fatal(err)
	}
	got, _, err := s.SubscriptionOf(g.ID)
	if err != nil {
		t.Fatal(err)
	}
	sub := got.Subscription
	if got.Name != "mine" || sub.AutoUpdateDelay != 60 || !sub.Deduplication || sub.UserInfo != "upload=1" ||
		sub.LastUpdated != 1790000500 {
		t.Fatalf("%q %+v", got.Name, sub)
	}
	// another link meanwhile: the update read the old provider's
	from = *got.Subscription
	if _, err = s.SaveSubscriptionGroup(g.ID, "mine", SubscriptionSettings{Link: subLink + "&new=1"}); err != nil {
		t.Fatal(err)
	}
	if _, err = s.ApplyUpdate(g.ID, nikosub.Update{Name: "mine", Subscription: from, FromName: "mine",
		From: from}); !errors.Is(err, ErrRefused) {
		t.Fatalf("an update of the old link: %v", err)
	}
	// every field of a subscription is merged
	if n := reflect.TypeOf(nikosub.Subscription{}).NumField(); n != 13 {
		t.Fatalf("%d fields: merge the new ones in mergeSubscription", n)
	}
}

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

	"libcore/nikofmt"
)

const (
	naiveLink = "naive+https://niko:examplepass0000000000000@203.0.113.7:443?sni=naive.example.com#naive"
	socksLink = "socks5://user:fake-pass@198.51.100.40:1080#SOCKS5"
	ssLink    = "ss://2022-blake3-aes-128-gcm:AAAAAAAAAAAAAAAAAAAAAA%3D%3D@203.0.113.20:8443#SS%202022"
	vlessLink = "vless://00000000-0000-4000-8000-000000000001@[2001:db8::7]:443?security=tls&sni=v.example#"
)

func beans(t *testing.T, links ...string) []nikofmt.Bean {
	t.Helper()
	var out []nikofmt.Bean
	for _, link := range links {
		bean, err := nikofmt.ParseLink(link, nikofmt.DefaultSettings)
		if err != nil {
			t.Fatalf("%s: %v", link, err)
		}
		out = append(out, bean)
	}
	return out
}

// testSealer seals by flipping bits behind its name: what DPAPI does, as far as the store can tell.
type testSealer struct{ name string }

func (s testSealer) Name() string { return s.name }

func (s testSealer) Seal(plain []byte) ([]byte, error) {
	out := append([]byte(s.name+"/"), plain...)
	for i := len(s.name) + 1; i < len(out); i++ {
		out[i] ^= 0x5a
	}
	return out, nil
}

func (s testSealer) Open(sealed []byte) ([]byte, error) {
	if !bytes.HasPrefix(sealed, []byte(s.name+"/")) {
		return nil, errors.New("not mine")
	}
	out := append([]byte(nil), sealed[len(s.name)+1:]...)
	for i := range out {
		out[i] ^= 0x5a
	}
	return out, nil
}

func open(t *testing.T, dir string, sealer Sealer, changes *[]Change) *Store {
	t.Helper()
	s, err := Open(dir, Options{Sealer: sealer, OnChange: func(c Change) {
		if changes != nil {
			*changes = append(*changes, c)
		}
	}})
	if err != nil {
		t.Fatal(err)
	}
	return s
}

func sameJSON(t *testing.T, a, b []byte) bool {
	t.Helper()
	var x, y any
	if err := json.Unmarshal(a, &x); err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal(b, &y); err != nil {
		t.Fatal(err)
	}
	return reflect.DeepEqual(x, y)
}

// A store made, filled and opened again: the same groups and profiles, the beans the parser gave, the ids going on
// where they were.
func TestRoundTrip(t *testing.T) {
	dir := filepath.Join(t.TempDir(), "users", "x", "niko")
	var changes []Change
	s := open(t, dir, NoSealer{}, &changes)
	groups := s.Groups()
	if len(groups) != 1 || !groups[0].Ungrouped || groups[0].ID != 1 || s.DefaultGroup() != 1 {
		t.Fatalf("a new store's groups: %+v", groups)
	}
	parsed := beans(t, naiveLink, socksLink, ssLink, vlessLink)
	added, err := s.AddProfiles(0, parsed[:3])
	if err != nil || len(added) != 3 {
		t.Fatalf("%v %v", added, err)
	}
	work, err := s.SaveGroup(Group{Name: "work"})
	if err != nil || work.ID != 2 || work.Order != 2 {
		t.Fatalf("%+v %v", work, err)
	}
	vless, _ := json.Marshal(parsed[3])
	saved, err := s.SaveProfile(Profile{Group: work.ID, Type: "vmess", Bean: vless})
	if err != nil || saved.ID != 4 || saved.Group != work.ID || saved.Order != 1 || saved.Name != "[2001:db8::7]:443" {
		t.Fatalf("%+v %v", saved, err)
	}
	if _, err = s.DeleteProfiles([]int64{2}); err != nil {
		t.Fatal(err)
	}
	want := []Change{
		{KindProfiles, []int64{1, 2, 3}}, {KindGroups, []int64{2}}, {KindProfiles, []int64{4}}, {KindProfiles, []int64{2}},
	}
	if !reflect.DeepEqual(changes, want) {
		t.Fatalf("changes %+v", changes)
	}

	again := open(t, dir, NoSealer{}, nil)
	if got := again.Groups(); len(got) != 2 || got[0].Profiles != 2 || got[1].Name != "work" || got[1].Profiles != 1 {
		t.Fatalf("groups again: %+v", got)
	}
	rows, err := again.Profiles(0)
	if err != nil || len(rows) != 3 {
		t.Fatalf("%+v %v", rows, err)
	}
	for i, wantRow := range []Profile{
		{ID: 1, Group: 1, Order: 1, Type: "naive", Name: "naive", Address: "203.0.113.7:443"},
		{ID: 3, Group: 1, Order: 3, Type: "ss", Name: "SS 2022", Address: "203.0.113.20:8443"},
		{ID: 4, Group: 2, Order: 1, Type: "vmess", Name: "[2001:db8::7]:443", Address: "[2001:db8::7]:443"},
	} {
		if !reflect.DeepEqual(rows[i], wantRow) {
			t.Errorf("row %d: %+v, want %+v", i, rows[i], wantRow)
		}
	}
	for _, c := range []struct {
		id   int64
		bean nikofmt.Bean
	}{{1, parsed[0]}, {3, parsed[2]}, {4, parsed[3]}} {
		p, err := again.Profile(c.id)
		if err != nil {
			t.Fatal(err)
		}
		bean, _ := json.Marshal(c.bean)
		if !sameJSON(t, p.Bean, bean) {
			t.Errorf("profile %d: %s, want %s", c.id, p.Bean, bean)
		}
	}
	// a deleted id is not given again
	next, err := again.AddProfiles(work.ID, parsed[1:2])
	if err != nil || next[0].ID != 5 || next[0].Order != 2 {
		t.Fatalf("%+v %v", next, err)
	}
	if _, err = again.Profile(2); !errors.Is(err, ErrNotFound) {
		t.Fatalf("deleted: %v", err)
	}
}

// The secret fields sealed on the disk, the rest readable; a store that cannot open them keeps the profile as it is
// and refuses its bean.
func TestSealed(t *testing.T) {
	dir := t.TempDir()
	sealer := testSealer{"test"}
	s := open(t, dir, sealer, nil)
	if _, err := s.AddProfiles(0, beans(t, naiveLink, socksLink)); err != nil {
		t.Fatal(err)
	}
	data, err := os.ReadFile(filepath.Join(dir, storeFile))
	if err != nil {
		t.Fatal(err)
	}
	for _, secret := range []string{"examplepass0000000000000", "fake-pass"} {
		if bytes.Contains(data, []byte(secret)) {
			t.Errorf("%q on the disk:\n%s", secret, data)
		}
	}
	for _, open := range []string{"naive.example.com", "198.51.100.40", `"sealed": "test:`, `"name": "SOCKS5"`} {
		if !bytes.Contains(data, []byte(open)) {
			t.Errorf("no %q on the disk:\n%s", open, data)
		}
	}
	p, err := open(t, dir, sealer, nil).Profile(2)
	if err != nil || !bytes.Contains(p.Bean, []byte(`"password":"fake-pass"`)) {
		t.Fatalf("opened again: %s %v", p.Bean, err)
	}

	// another machine's: listed, not opened, kept through a write
	other := open(t, dir, testSealer{"other"}, nil)
	rows, _ := other.Profiles(0)
	if len(rows) != 2 || rows[1].Name != "SOCKS5" || rows[1].Address != "198.51.100.40:1080" {
		t.Fatalf("rows: %+v", rows)
	}
	if _, err = other.Profile(2); !errors.Is(err, ErrRefused) {
		t.Fatalf("unreadable: %v", err)
	}
	if _, err = other.SaveGroup(Group{Name: "x"}); err != nil {
		t.Fatal(err)
	}
	if p, err = open(t, dir, sealer, nil).Profile(2); err != nil || !bytes.Contains(p.Bean, []byte("fake-pass")) {
		t.Fatalf("kept: %s %v", p.Bean, err)
	}
	// none: the secrets in the bean
	plain := t.TempDir()
	if _, err = open(t, plain, NoSealer{}, nil).AddProfiles(0, beans(t, socksLink)); err != nil {
		t.Fatal(err)
	}
	if data, _ = os.ReadFile(filepath.Join(plain, storeFile)); !bytes.Contains(data, []byte(`"password": "fake-pass"`)) ||
		bytes.Contains(data, []byte(`"sealed"`)) {
		t.Fatalf("unsealed:\n%s", data)
	}
	// that file opened where secrets are sealed: sealed at once, not left open until the profile changes
	if p, err = open(t, plain, sealer, nil).Profile(1); err != nil || !bytes.Contains(p.Bean, []byte("fake-pass")) {
		t.Fatalf("%s %v", p.Bean, err)
	}
	if data, _ = os.ReadFile(filepath.Join(plain, storeFile)); bytes.Contains(data, []byte("fake-pass")) ||
		!bytes.Contains(data, []byte(`"sealed": "test:`)) {
		t.Fatalf("not sealed when opened:\n%s", data)
	}
}

// A crash between the temporary file and the rename: the store on the disk is the one before, the write refused, the
// store in memory too; opened again, the old one, and the temporary file gone.
func TestCrashBeforeRename(t *testing.T) {
	dir := t.TempDir()
	s := open(t, dir, NoSealer{}, nil)
	if _, err := s.AddProfiles(0, beans(t, naiveLink)); err != nil {
		t.Fatal(err)
	}
	before, _ := os.ReadFile(filepath.Join(dir, storeFile))
	var temporary string
	crashBeforeRename = func(path string) bool { temporary = path; return true }
	defer func() { crashBeforeRename = nil }()
	if _, err := s.AddProfiles(0, beans(t, socksLink)); !errors.Is(err, errCrashed) {
		t.Fatalf("the write: %v", err)
	}
	crashBeforeRename = nil
	if data, _ := os.ReadFile(filepath.Join(dir, storeFile)); !bytes.Equal(data, before) {
		t.Fatalf("store.json changed:\n%s", data)
	}
	if written, err := os.ReadFile(temporary); err != nil || !bytes.Contains(written, []byte("SOCKS5")) {
		t.Fatalf("the temporary file: %v", err)
	}
	if rows, _ := s.Profiles(0); len(rows) != 1 {
		t.Fatalf("in memory: %+v", rows)
	}
	again := open(t, dir, NoSealer{}, nil)
	if rows, _ := again.Profiles(0); len(rows) != 1 || rows[0].Name != "naive" {
		t.Fatalf("opened again: %+v", rows)
	}
	if _, err := os.Stat(temporary); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("the temporary file left: %v", err)
	}
	// and the next write goes through
	if added, err := again.AddProfiles(0, beans(t, socksLink)); err != nil || added[0].ID != 2 {
		t.Fatalf("%+v %v", added, err)
	}
}

func TestProfiles(t *testing.T) {
	var changes []Change
	s := open(t, t.TempDir(), NoSealer{}, &changes)
	if _, err := s.AddProfiles(0, beans(t, naiveLink, socksLink, ssLink, naiveLink)); err != nil {
		t.Fatal(err)
	}
	work, _ := s.SaveGroup(Group{Name: "work"})
	changes = nil

	// a profile edited: its new bean, defaults given, where it was
	p, _ := s.Profile(2)
	edited := bytes.Replace(p.Bean, []byte(`"name":"SOCKS5"`), []byte(`"name":"edited","serverPort":null`), 1)
	saved, err := s.SaveProfile(Profile{ID: 2, Type: "socks", Bean: edited, Group: work.ID})
	if err != nil || saved.Name != "edited" || saved.Address != "198.51.100.40:1080" || saved.Group != 1 || saved.Order != 2 {
		t.Fatalf("%+v %v", saved, err)
	}
	for _, c := range []struct {
		p    Profile
		want error
	}{
		{Profile{ID: 2, Type: "naive", Bean: edited}, ErrInvalid},
		{Profile{Type: "socks", Bean: []byte(`{"serverAddres": "192.0.2.1"}`)}, ErrInvalid},
		{Profile{Type: "neko", Bean: []byte(`{}`)}, ErrInvalid},
		{Profile{Type: "socks", Bean: []byte(`{}`), Group: 99}, ErrNotFound},
		{Profile{ID: 99, Type: "socks", Bean: []byte(`{}`)}, ErrNotFound},
		{Profile{Type: "socks", Bean: []byte(`{"name": "` + strings.Repeat("x", maxBean) + `"}`)}, ErrInvalid},
	} {
		if _, err := s.SaveProfile(c.p); !errors.Is(err, c.want) {
			t.Errorf("%d %s %.40s: %v", c.p.ID, c.p.Type, c.p.Bean, err)
		}
	}

	// moved: 4 and 1 into work, then 3 before 1 there; the rest of the ungrouped group keeps its order
	if err = s.MoveProfiles([]int64{4, 1, 4}, work.ID, 0); err != nil {
		t.Fatal(err)
	}
	if err = s.MoveProfiles([]int64{3}, work.ID, 1); err != nil {
		t.Fatal(err)
	}
	order := func(group int64) (ids []int64) {
		rows, err := s.Profiles(group)
		if err != nil {
			t.Fatal(err)
		}
		for _, r := range rows {
			ids = append(ids, r.ID)
		}
		return ids
	}
	if got := order(work.ID); !reflect.DeepEqual(got, []int64{4, 3, 1}) {
		t.Fatalf("work: %v", got)
	}
	if got := order(0); !reflect.DeepEqual(got, []int64{2, 4, 3, 1}) {
		t.Fatalf("all: %v", got)
	}
	// nothing to change: no write, no change
	if err = s.MoveProfiles([]int64{1}, work.ID, 0); err != nil {
		t.Fatal(err)
	}
	for _, c := range []struct {
		ids           []int64
		group, before int64
		want          error
	}{
		{nil, work.ID, 0, ErrInvalid},
		{[]int64{9}, work.ID, 0, ErrNotFound},
		{[]int64{1}, 9, 0, ErrNotFound},
		{[]int64{1}, work.ID, 2, ErrInvalid},
		{[]int64{1}, work.ID, 1, ErrInvalid},
	} {
		if err := s.MoveProfiles(c.ids, c.group, c.before); !errors.Is(err, c.want) {
			t.Errorf("%v into %d before %d: %v", c.ids, c.group, c.before, err)
		}
	}

	// a group: renamed, then deleted with its profiles; the ungrouped one stays
	if g, err := s.SaveGroup(Group{ID: work.ID, Name: "office"}); err != nil || g.Name != "office" || g.Profiles != 3 {
		t.Fatalf("%+v %v", g, err)
	}
	if _, err = s.SaveGroup(Group{Name: "sub", Type: GroupSubscription}); !errors.Is(err, ErrUnsupported) {
		t.Fatalf("a subscription's group: %v", err)
	}
	if _, err = s.SaveGroup(Group{ID: 9}); !errors.Is(err, ErrNotFound) {
		t.Fatalf("no group: %v", err)
	}
	if err = s.DeleteGroup(1); !errors.Is(err, ErrRefused) {
		t.Fatalf("the ungrouped group: %v", err)
	}
	if err = s.DeleteGroup(work.ID); err != nil {
		t.Fatal(err)
	}
	if got := order(0); !reflect.DeepEqual(got, []int64{2}) {
		t.Fatalf("after the group: %v", got)
	}
	if _, err = s.Profiles(work.ID); !errors.Is(err, ErrNotFound) {
		t.Fatalf("the group's list: %v", err)
	}
	if deleted, err := s.DeleteProfiles([]int64{7, 8}); err != nil || deleted != nil {
		t.Fatalf("none there: %v %v", deleted, err)
	}

	want := []Change{
		{KindProfiles, []int64{2}},
		{KindProfiles, []int64{1, 4}},
		{KindProfiles, []int64{1, 3}},
		{KindGroups, []int64{work.ID}},
		{KindGroups, []int64{work.ID}},
		{KindProfiles, []int64{1, 3, 4}},
	}
	if !reflect.DeepEqual(changes, want) {
		t.Fatalf("changes %+v", changes)
	}
}

// What is not the store's is not opened as one, nor written over.
func TestCorrupt(t *testing.T) {
	for _, text := range []string{
		`{`,
		`{"groups": []}`,
		`{"schema": 1, "groups": []}`,
		`{"schema": 1, "groups": [{"id": 1, "ungrouped": true}], "profiles": [{"id": 1, "group": 2, "type": "socks", "bean": {}}]}`,
		`{"schema": 1, "groups": [{"id": 1, "ungrouped": true}], "profiles": [{"id": 1, "group": 1, "type": "neko", "bean": {}}]}`,
		`{"schema": 1, "groups": [{"id": 1, "ungrouped": true}, {"id": 1}]}`,
	} {
		dir := t.TempDir()
		path := filepath.Join(dir, storeFile)
		if err := os.WriteFile(path, []byte(text), 0o600); err != nil {
			t.Fatal(err)
		}
		if _, err := Open(dir, Options{Sealer: NoSealer{}}); err == nil {
			t.Errorf("%s: opened", text)
		}
		if data, _ := os.ReadFile(path); string(data) != text {
			t.Errorf("%s: written over", text)
		}
	}
}

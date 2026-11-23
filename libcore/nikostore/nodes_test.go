package nikostore

import (
	"bytes"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"libcore/nikofmt"
	"libcore/nikoray"
)

// testdata/store-schema3.json is a store of schema 3 as this package wrote it: schema 2's fixture with a rule.
const fixtureSchema3 = "testdata/store-schema3.json"

const goldenRaycontrol = "../../shared/golden/raycontrol"

// Schema 3 to 4, the real migration: the fixture copied into backup/ and opened as it was, no nodes, a node's group
// then made by its first subscription.
func TestMigration3to4(t *testing.T) {
	dir := t.TempDir()
	original, err := os.ReadFile(fixtureSchema3)
	if err != nil {
		t.Fatal(err)
	}
	os.WriteFile(filepath.Join(dir, storeFile), original, 0o600)
	at := time.Date(2026, 11, 23, 12, 0, 0, 0, time.UTC)
	s, err := Open(dir, Options{Sealer: NoSealer{}, Now: func() time.Time { return at }})
	if err != nil {
		t.Fatal(err)
	}
	if len(s.Groups()) != 3 || len(s.Rules()) != 1 || len(s.Nodes()) != 0 {
		t.Fatalf("groups %+v rules %+v nodes %+v", s.Groups(), s.Rules(), s.Nodes())
	}
	copied, err := os.ReadFile(filepath.Join(dir, backupDir, "3-20261123-120000", storeFile))
	if err != nil || !bytes.Equal(copied, original) {
		t.Fatalf("the backup: %v", err)
	}
	data, _ := os.ReadFile(filepath.Join(dir, storeFile))
	if schema, _ := schemaOf(data); schema != 4 {
		t.Fatalf("written as schema %d", schema)
	}
}

func goldenDevice(t *testing.T, name string) nikoray.Registration {
	t.Helper()
	raw, err := os.ReadFile(filepath.Join(goldenRaycontrol, "invites", name+".txt"))
	if err != nil {
		t.Fatal(err)
	}
	r, err := nikoray.ParseDevice(string(raw))
	if err != nil {
		t.Fatal(err)
	}
	return r
}

func goldenRows(t *testing.T, name string) *nikoray.Parsed {
	t.Helper()
	payload, err := os.ReadFile(filepath.Join(goldenRaycontrol, "payloads", name, "payload.json"))
	if err != nil {
		t.Fatal(err)
	}
	parsed, err := nikoray.Parse(payload, nil, 1_800_000_000, nikofmt.DefaultSettings)
	if err != nil {
		t.Fatal(err)
	}
	return parsed
}

// A node: kept with its device key sealed, its rows written into a group of its own on its first subscription and
// matched by their keys on the next (ids, the selection and the user's tweaks kept; a row gone deleted and the
// selection moved to the node's first), an older subscription refused, the node and its rows there once the store
// opens again, its secrets unread under another sealer; removed with its group and rows.
func TestNodes(t *testing.T) {
	dir := t.TempDir()
	var changes []Change
	sealer := testSealer{"test"}
	s := open(t, dir, sealer, &changes)
	device := goldenDevice(t, "rcdev1-user")
	node, err := s.ImportNode(device, nil, "")
	if err != nil {
		t.Fatal(err)
	}
	id := node.ID()
	if node.DeviceKey != device.DeviceKey || node.GroupID != 0 {
		t.Fatalf("%+v", node)
	}
	data, _ := os.ReadFile(filepath.Join(dir, storeFile))
	if strings.Contains(string(data), device.DeviceKey) {
		t.Fatal("the device key in the clear")
	}

	parsed := goldenRows(t, "every-kind")
	applied, err := s.ApplyNodeRows(id, "", 7, parsed.Rows, func(n *nikoray.Node) { n.Name = parsed.Name })
	if err != nil {
		t.Fatal(err)
	}
	if !applied.GroupCreated || len(applied.Added) != len(parsed.Rows) || len(applied.Deleted) != 0 {
		t.Fatalf("%+v", applied)
	}
	group, _ := s.state.group(applied.Group)
	if group.Name != "Home node one" || group.Node != id {
		t.Fatalf("%+v", group)
	}
	if _, err = s.AddProfiles(applied.Group, beans(t, socksLink)); !errors.Is(err, ErrRefused) {
		t.Fatalf("added to a node's group: %v", err)
	}
	if err = s.DeleteGroup(applied.Group); !errors.Is(err, ErrRefused) {
		t.Fatalf("deleted a node's group: %v", err)
	}
	stored, _ := s.Node(id)
	first, second := stored.ProfileIDs["3"], stored.ProfileIDs["4"]
	if first == 0 || second == 0 || stored.Links != nil && len(stored.Links) != 0 {
		// the links are the caller's to set (update); none here
		t.Fatalf("%+v", stored.ProfileIDs)
	}
	// the user's tweak on a row, the row selected
	p, _ := s.Profile(second)
	var bean map[string]any
	json.Unmarshal(p.Bean, &bean)
	bean["tlsFragment"] = 1
	tweaked, _ := json.Marshal(bean)
	if _, err = s.SaveProfile(Profile{ID: second, Type: p.Type, Bean: tweaked}); err != nil {
		t.Fatal(err)
	}
	if err = s.Select(first); err != nil {
		t.Fatal(err)
	}

	// the next subscription: row 3 gone, the others as they were
	rows := parsed.Rows[1:]
	changes = nil
	applied, err = s.ApplyNodeRows(id, "", 8, rows, func(n *nikoray.Node) { n.Name = "Renamed" })
	if err != nil {
		t.Fatal(err)
	}
	if applied.GroupCreated || len(applied.Added) != 0 || len(applied.Deleted) != 1 || applied.Deleted[0] != first {
		t.Fatalf("%+v", applied)
	}
	if group, _ = s.state.group(applied.Group); group.Name != "Renamed" {
		t.Fatalf("the group's name: %q", group.Name)
	}
	if s.Selected() != second {
		t.Fatalf("selected %d, not the node's first row %d", s.Selected(), second)
	}
	p, _ = s.Profile(second)
	json.Unmarshal(p.Bean, &bean)
	if bean["tlsFragment"] != float64(1) || p.Order != 1 {
		t.Fatalf("the tweak or the order went: %v %d", bean["tlsFragment"], p.Order)
	}
	// an older subscription changes nothing
	if applied, err = s.ApplyNodeRows(id, "", 7, parsed.Rows, func(n *nikoray.Node) {}); err != nil || !applied.Stale {
		t.Fatalf("%+v %v", applied, err)
	}
	// nor does one fetched for another registration of the node (a device the node no longer is)
	if applied, err = s.ApplyNodeRows(id, "another-device", 9, parsed.Rows, func(n *nikoray.Node) { n.Name = "Other" }); err != nil || !applied.Stale {
		t.Fatalf("another device's: %+v %v", applied, err)
	}
	if n, _ := s.Node(id); n == nil || n.Name == "Other" || n.SubSeq != 8 {
		t.Fatalf("another device's subscription written: %+v", n)
	}

	// a registration under way: its key kept 30 minutes from its last try
	made := 0
	make := func() (nikoray.Identity, error) {
		made++
		return nikoray.Identity{Cert: "cert", Key: strings.Repeat("K", 16), Pin: "pin"}, nil
	}
	a, _ := s.PendingIdentity("invite", 1000, make)
	b, _ := s.PendingIdentity("invite", 1000+29*60, make)
	c, _ := s.PendingIdentity("invite", 1000+29*60+30*60, make)
	if made != 2 || a.Key != b.Key || b.CreatedAt != 1000+29*60 || c.CreatedAt != 1000+59*60 {
		t.Fatalf("made %d: %+v %+v %+v", made, a, b, c)
	}
	if data, _ = os.ReadFile(filepath.Join(dir, storeFile)); strings.Contains(string(data), strings.Repeat("K", 16)) {
		t.Fatal("a client key in the clear")
	}

	// opened again: all of it; under another sealer, the node unread
	again := open(t, dir, sealer, nil)
	if n, err := again.Node(id); err != nil || n.DeviceKey != device.DeviceKey || n.Name != "Renamed" ||
		len(n.ProfileIDs) != len(rows) {
		t.Fatalf("%+v %v", n, err)
	}
	other := open(t, dir, testSealer{"other"}, nil)
	if list := other.Nodes(); len(list) != 1 || !list[0].Unreadable || list[0].Node.DeviceKey != "" {
		t.Fatalf("%+v", list)
	}
	if _, err = other.Node(id); !errors.Is(err, ErrRefused) {
		t.Fatalf("%v", err)
	}

	// removed: its group and rows with it
	if err = again.DeleteNode(id); err != nil {
		t.Fatal(err)
	}
	if len(again.Nodes()) != 0 || len(again.Groups()) != 1 {
		t.Fatalf("%+v %+v", again.Nodes(), again.Groups())
	}
	if rows, _ := again.Profiles(0); len(rows) != 0 {
		t.Fatalf("%+v", rows)
	}
}

// A new package of the same node keeps its group, rows and seq where its keys are the same; a bot's package is
// refused before anything is kept.
func TestNodeImportAgain(t *testing.T) {
	s := open(t, t.TempDir(), NoSealer{}, nil)
	device := goldenDevice(t, "rcdev1-user")
	node, _ := s.ImportNode(device, nil, "")
	parsed := goldenRows(t, "rotation-due")
	applied, err := s.ApplyNodeRows(node.ID(), "", 5, parsed.Rows, func(*nikoray.Node) {})
	if err != nil {
		t.Fatal(err)
	}
	device.DeviceKey = strings.Repeat("9", 64)
	again, err := s.ImportNode(device, nil, "")
	if err != nil || again.GroupID != applied.Group || again.SubSeq != 5 || len(again.ProfileIDs) != 3 ||
		again.DeviceKey != device.DeviceKey {
		t.Fatalf("%+v %v", again, err)
	}
	if _, err = s.ImportNode(goldenDevice(t, "rcdev1-bot-role"), nil, ""); !errors.Is(err, nikoray.ErrNotForApp) {
		t.Fatalf("a bot's package: %v", err)
	}
	if _, err = s.ImportNode(goldenDevice(t, "rcdev1-no-sub"), nil, ""); !errors.Is(err, nikoray.ErrNoSubscription) {
		t.Fatalf("a package without a subscription: %v", err)
	}
}

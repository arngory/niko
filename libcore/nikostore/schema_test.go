package nikostore

import (
	"bytes"
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"sort"
	"testing"
	"time"
)

// testdata/store-schema1.json is a store of schema 1 as this package wrote it: a group of the user's, a profile in
// each, a deleted one's id not to be given again.
const fixtureSchema1 = "testdata/store-schema1.json"

func copyFixture(t *testing.T, dir string) []byte {
	t.Helper()
	data, err := os.ReadFile(fixtureSchema1)
	if err != nil {
		t.Fatal(err)
	}
	if err = os.WriteFile(filepath.Join(dir, storeFile), data, 0o600); err != nil {
		t.Fatal(err)
	}
	return data
}

// The fixture of schema 1 opens as it is, while that is the schema.
func TestSchema1(t *testing.T) {
	if Schema() != 1 {
		t.Skip("schema 1 is migrated now: its migration's test holds it")
	}
	dir := t.TempDir()
	copyFixture(t, dir)
	s := open(t, dir, NoSealer{}, nil)
	rows, err := s.Profiles(0)
	if err != nil || len(rows) != 2 || rows[0].Name != "SOCKS5" || rows[1].Group != 2 || rows[1].Address != "203.0.113.7:443" {
		t.Fatalf("%+v %v", rows, err)
	}
	if added, err := s.AddProfiles(0, beans(t, ssLink)); err != nil || added[0].ID != 4 {
		t.Fatalf("%+v %v", added, err)
	}
	if entries, _ := os.ReadDir(filepath.Join(dir, backupDir)); len(entries) != 0 {
		t.Fatalf("a backup without a migration: %v", entries)
	}
}

// A migration of the test's own, 1 to 2 (it names the ungrouped group): the file of schema 1 copied into backup/
// first, then migrated and written as schema 2; three copies kept; a failing migration leaves the file as it was.
func TestMigration(t *testing.T) {
	saved := migrations
	defer func() { migrations = saved }()
	migrations = []migration{func(document map[string]json.RawMessage) error {
		var groups []map[string]any
		if err := json.Unmarshal(document["groups"], &groups); err != nil {
			return err
		}
		for _, g := range groups {
			if g["ungrouped"] == true {
				g["name"] = "migrated"
			}
		}
		data, err := json.Marshal(groups)
		document["groups"] = data
		return err
	}}
	dir := t.TempDir()
	original := copyFixture(t, dir)
	at := time.Date(2026, 11, 21, 10, 0, 0, 0, time.UTC)
	s, err := Open(dir, Options{Sealer: NoSealer{}, Now: func() time.Time { return at }})
	if err != nil {
		t.Fatal(err)
	}
	if g := s.Groups(); g[0].Name != "migrated" {
		t.Fatalf("groups: %+v", g)
	}
	if rows, _ := s.Profiles(0); len(rows) != 2 {
		t.Fatalf("profiles: %+v", rows)
	}
	copied, err := os.ReadFile(filepath.Join(dir, backupDir, "1-20261121-100000", storeFile))
	if err != nil || !bytes.Equal(copied, original) {
		t.Fatalf("the backup: %v", err)
	}
	data, _ := os.ReadFile(filepath.Join(dir, storeFile))
	if schema, _ := schemaOf(data); schema != 2 {
		t.Fatalf("written as schema %d", schema)
	}
	// opened again: no migration, no backup
	if _, err = Open(dir, Options{Sealer: NoSealer{}}); err != nil {
		t.Fatal(err)
	}
	// three more migrations of schema 1, a minute apart: the first copy goes
	for i := 1; i <= 3; i++ {
		copyFixture(t, dir)
		when := at.Add(time.Duration(i) * time.Minute)
		if _, err = Open(dir, Options{Sealer: NoSealer{}, Now: func() time.Time { return when }}); err != nil {
			t.Fatal(err)
		}
	}
	entries, _ := os.ReadDir(filepath.Join(dir, backupDir))
	var names []string
	for _, e := range entries {
		names = append(names, e.Name())
	}
	sort.Strings(names)
	if len(names) != 3 || names[0] != "1-20261121-100100" || names[2] != "1-20261121-100300" {
		t.Fatalf("backups: %v", names)
	}

	// a migration that fails: the file as it was
	migrations = []migration{func(map[string]json.RawMessage) error { return errors.New("no") }}
	failing := t.TempDir()
	copyFixture(t, failing)
	if _, err = Open(failing, Options{Sealer: NoSealer{}}); err == nil {
		t.Fatal("opened")
	}
	if data, _ = os.ReadFile(filepath.Join(failing, storeFile)); !bytes.Equal(data, original) {
		t.Fatal("written over")
	}
}

// A store of a newer Niko: not opened, not written over, no backup.
func TestNewerSchema(t *testing.T) {
	dir := t.TempDir()
	newer := []byte(`{"schema": 99, "groups": [], "what": "a newer Niko's"}`)
	if err := os.WriteFile(filepath.Join(dir, storeFile), newer, 0o600); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(dir, Options{Sealer: NoSealer{}}); !errors.Is(err, ErrNewerSchema) {
		t.Fatalf("opened: %v", err)
	}
	if data, _ := os.ReadFile(filepath.Join(dir, storeFile)); !bytes.Equal(data, newer) {
		t.Fatal("written over")
	}
	if _, err := os.Stat(filepath.Join(dir, backupDir)); !errors.Is(err, os.ErrNotExist) {
		t.Fatalf("a backup: %v", err)
	}
}

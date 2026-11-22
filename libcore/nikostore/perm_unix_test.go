//go:build !windows

package nikostore

import (
	"encoding/json"
	"os"
	"path/filepath"
	"syscall"
	"testing"
	"time"
)

// The store is the daemon's alone, whatever the umask: its directories 0700, its files 0600, a directory or a file of
// it that was wider narrowed (store.json and settings.json); the backups too.
func TestPermissions(t *testing.T) {
	old := syscall.Umask(0)
	defer syscall.Umask(old)
	root := t.TempDir()
	dir := filepath.Join(root, "users", "u", "niko")
	s := open(t, dir, NoSealer{}, nil)
	if _, err := s.AddProfiles(0, beans(t, socksLink)); err != nil {
		t.Fatal(err)
	}
	if _, err := s.UpdateSettings(s.settingsNow(t), []string{"mixedPort"}); err != nil {
		t.Fatal(err)
	}
	mode := func(path string) os.FileMode {
		info, err := os.Stat(path)
		if err != nil {
			t.Fatal(err)
		}
		return info.Mode().Perm()
	}
	for _, path := range []string{filepath.Join(root, "users"), filepath.Join(root, "users", "u"), dir} {
		if m := mode(path); m != 0o700 {
			t.Errorf("%s: %v", path, m)
		}
	}
	for _, name := range []string{storeFile, settingsFile} {
		if m := mode(filepath.Join(dir, name)); m != 0o600 {
			t.Errorf("%s: %v", name, m)
		}
	}

	// wider ones, as copied in by hand
	if err := os.Chmod(dir, 0o755); err != nil {
		t.Fatal(err)
	}
	for _, name := range []string{storeFile, settingsFile} {
		if err := os.Chmod(filepath.Join(dir, name), 0o644); err != nil {
			t.Fatal(err)
		}
	}
	open(t, dir, NoSealer{}, nil)
	if m := mode(dir); m != 0o700 {
		t.Errorf("narrowed directory: %v", m)
	}
	for _, name := range []string{storeFile, settingsFile} {
		if m := mode(filepath.Join(dir, name)); m != 0o600 {
			t.Errorf("narrowed %s: %v", name, m)
		}
	}

	// a backup before a migration
	saved := migrations
	defer func() { migrations = saved }()
	migrations = []migration{func(map[string]json.RawMessage) error { return nil }}
	at := time.Date(2026, 11, 21, 10, 0, 0, 0, time.UTC)
	if _, err := Open(dir, Options{Sealer: NoSealer{}, Now: func() time.Time { return at }}); err != nil {
		t.Fatal(err)
	}
	copyDir := filepath.Join(dir, backupDir, "1-20261121-100000")
	for path, want := range map[string]os.FileMode{
		filepath.Join(dir, backupDir):     0o700,
		copyDir:                           0o700,
		filepath.Join(copyDir, storeFile): 0o600,
		filepath.Join(dir, storeFile):     0o600,
	} {
		if m := mode(path); m != want {
			t.Errorf("%s: %v, want %v", path, m, want)
		}
	}
}

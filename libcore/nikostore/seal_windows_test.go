//go:build windows

package nikostore

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"
)

// DPAPI in this process's context: what it seals it opens again, the plain text nowhere in the blob, a changed blob
// refused; the store's own default on Windows, so no secret reaches its file.
func TestDPAPI(t *testing.T) {
	sealer := DefaultSealer()
	if sealer.Name() != "dpapi" {
		t.Fatalf("the default: %q", sealer.Name())
	}
	plain := []byte(`{"password":"fake-pass"}`)
	sealed, err := sealer.Seal(plain)
	if err != nil {
		t.Fatal(err)
	}
	if bytes.Contains(sealed, []byte("fake-pass")) {
		t.Fatal("the plain text in the blob")
	}
	opened, err := sealer.Open(sealed)
	if err != nil || !bytes.Equal(opened, plain) {
		t.Fatalf("opened %q: %v", opened, err)
	}
	sealed[len(sealed)-1] ^= 1
	if _, err = sealer.Open(sealed); err == nil {
		t.Fatal("a changed blob opened")
	}

	dir := t.TempDir()
	s, err := Open(dir, Options{})
	if err != nil {
		t.Fatal(err)
	}
	if _, err = s.AddProfiles(0, beans(t, naiveLink, socksLink)); err != nil {
		t.Fatal(err)
	}
	data, _ := os.ReadFile(filepath.Join(dir, storeFile))
	if bytes.Contains(data, []byte("fake-pass")) || bytes.Contains(data, []byte("examplepass")) ||
		!bytes.Contains(data, []byte(`"sealed": "dpapi:`)) {
		t.Fatalf("on the disk:\n%s", data)
	}
	again, err := Open(dir, Options{})
	if err != nil {
		t.Fatal(err)
	}
	if p, err := again.Profile(2); err != nil || !bytes.Contains(p.Bean, []byte("fake-pass")) {
		t.Fatalf("opened again: %s %v", p.Bean, err)
	}
}

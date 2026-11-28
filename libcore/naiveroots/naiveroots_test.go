package naiveroots

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"math/big"
	"os"
	"path/filepath"
	"testing"
	"time"
)

func TestSyncChromeAndSystem(t *testing.T) {
	key, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	tmpl := &x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject:      pkix.Name{CommonName: "test-root"},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(time.Hour),
		IsCA:         true,
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &key.PublicKey, key)
	if err != nil {
		t.Fatal(err)
	}
	cert, err := x509.ParseCertificate(der)
	if err != nil {
		t.Fatal(err)
	}

	// Refuses insecure world/group-readable directory (0755).
	insecureDir := t.TempDir()
	_ = os.Chmod(insecureDir, 0o755)
	state.Lock()
	oldPath, oldSys, oldSaid := state.path, state.system, state.said
	state.path = ""
	state.said = ""
	state.Unlock()
	t.Cleanup(func() {
		state.Lock()
		state.path, state.system, state.said = oldPath, oldSys, oldSaid
		state.Unlock()
	})
	Init(insecureDir)
	if Path() != "" {
		t.Fatalf("expected Init to refuse insecure dir %s, got Path()=%q", insecureDir, Path())
	}

	dir := filepath.Join(t.TempDir(), "roots")
	Init(dir)
	state.Lock()
	state.system = func() []*x509.Certificate { return []*x509.Certificate{cert} }
	state.Unlock()

	Sync(RootStoreChrome)
	data, err := os.ReadFile(Path())
	if err != nil {
		t.Fatal(err)
	}
	if len(data) != 0 {
		t.Fatalf("chrome store should produce empty bundle, got %d bytes", len(data))
	}

	Sync(RootStoreSystem)
	data, err = os.ReadFile(Path())
	if err != nil {
		t.Fatal(err)
	}
	if len(data) == 0 {
		t.Fatal("system store should write PEM certificates")
	}

	Sync(RootStoreMozilla)
	data, err = os.ReadFile(Path())
	if err != nil {
		t.Fatal(err)
	}
	if len(data) != 0 {
		t.Fatalf("mozilla store should produce empty bundle, got %d bytes", len(data))
	}
}

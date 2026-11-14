package libcore

import (
	"crypto/x509"
	"encoding/pem"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"
)

// What naive's Chromium is given besides Chrome's list (naive_roots.go): nothing under Chrome's and Mozilla's lists,
// the platform's roots under the system's store, and only certificates that parse. The environment is not touched
// here: the variable is the process's, set once by InitCore.
func TestNaiveRootsFile(t *testing.T) {
	server := httptest.NewTLSServer(nil)
	defer server.Close()
	root := server.Certificate()

	savedIntf := intfNiko
	savedPath, savedSystem, savedSaid := naiveRoots.path, naiveRoots.system, naiveRoots.said
	defer func() {
		intfNiko = savedIntf
		naiveRoots.path, naiveRoots.system, naiveRoots.said = savedPath, savedSystem, savedSaid
	}()
	path := filepath.Join(t.TempDir(), "naive-roots.pem")
	naiveRoots.path = path
	naiveRoots.said = ""
	asked := 0
	naiveRoots.system = func() []*x509.Certificate {
		asked++
		// one that does not parse among them: left out, and those after it are kept
		return []*x509.Certificate{root, {Raw: []byte("not a certificate")}, nil, root}
	}
	blocks := func() int {
		t.Helper()
		content, err := os.ReadFile(path)
		if err != nil {
			t.Fatal(err)
		}
		n := 0
		for {
			var block *pem.Block
			block, content = pem.Decode(content)
			if block == nil {
				break
			}
			if _, err := x509.ParseCertificate(block.Bytes); err != nil {
				t.Fatalf("a block of the file does not parse: %v", err)
			}
			n++
		}
		return n
	}

	for _, name := range []string{RootStoreChrome, RootStoreMozilla} {
		intfNiko = rootStoreSetting{name}
		writeNaiveRoots()
		if n := blocks(); n != 0 {
			t.Fatalf("%s: %d certificates in naive's file, want none", name, n)
		}
	}
	if asked != 0 {
		t.Fatalf("the platform's roots were read %d times under lists that do not want them", asked)
	}
	intfNiko = rootStoreSetting{RootStoreSystem}
	writeNaiveRoots()
	if n := blocks(); n != 2 {
		t.Fatalf("the system's store: %d certificates in naive's file, want the two that parse", n)
	}
	// back to a list: the file is emptied, not left with the phone's roots
	intfNiko = rootStoreSetting{RootStoreChrome}
	writeNaiveRoots()
	if n := blocks(); n != 0 {
		t.Fatalf("back to Chrome's list: %d certificates left in naive's file", n)
	}
	// a platform that gives no roots: an empty file, and no failure
	naiveRoots.system = func() []*x509.Certificate { return nil }
	intfNiko = rootStoreSetting{RootStoreSystem}
	writeNaiveRoots()
	if n := blocks(); n != 0 {
		t.Fatalf("no roots from the platform: %d certificates in naive's file", n)
	}
	// before the process named a file nothing is written anywhere
	naiveRoots.path = ""
	writeNaiveRoots()
}

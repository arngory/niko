package libcore

import (
	"bytes"
	"crypto/x509"
	"encoding/pem"
	"log"
	"os"
	"path/filepath"
	"strconv"
	"sync"

	"github.com/sagernet/sing-box/common/certificate"
)

// The roots naive takes besides Chrome's list.
//
// naive is Chromium, and Chromium verifies a chain itself: against the copy of Chrome's list it was built with, and
// on Android against nothing else unless SSL_CERT_FILE names a file of roots (the library of arngory/cronet-go from
// d5ec437: before it Chromium read Android's /system roots whatever the app's setting). The file is read when an
// engine is made, so each start of the core takes it as it is then.
//
// The variable is set once, when the process starts, and never changed: setenv beside Chromium's threads, which
// read the environment, is a race bionic does not guard. What changes is the file: empty under Chrome's and
// Mozilla's lists (naive then takes Chrome's list alone: Chromium has no way to be given Mozilla's without leaving
// its own verifier), the system's roots with those installed on the phone under the system's store. Roots from the
// file count as locally trusted: over HTTP/2 Chromium takes them, over QUIC it wants a root of Chrome's list.
//
// An imported ca.pem is not put there (the owner's decision of 2026-11-14): a root of one's own for a naive server
// goes into the profile, where it takes the place of every list.
var naiveRoots struct {
	sync.Mutex
	path string
	// the platform's roots: the core's, or a test's
	system func() []*x509.Certificate
	// what the file was last written from, said once per change
	said string
}

// initNaiveRoots names the file to Chromium, once per process and before any engine of it, and leaves it empty:
// nothing besides Chrome's list until a core instance says otherwise. Each of the app's processes has a file of
// its own (the service's and the interface's both make core instances): one file between them would be emptied
// by the start of one under an engine the other is about to make.
func initNaiveRoots(dir string, bg bool) {
	naiveRoots.Lock()
	defer naiveRoots.Unlock()
	if naiveRoots.path != "" {
		return
	}
	name := "naive-roots-ui.pem"
	if bg {
		name = "naive-roots-bg.pem"
	}
	naiveRoots.path = filepath.Join(dir, name)
	naiveRoots.system = certificate.SystemCertificates
	if err := writeFileAtomic(naiveRoots.path, nil); err != nil {
		log.Println("naive roots:", err)
	}
	if err := os.Setenv("SSL_CERT_FILE", naiveRoots.path); err != nil {
		log.Println("naive roots:", err)
	}
}

// writeNaiveRoots puts into the file what the setting says, before a core instance is made. A file that cannot be
// written is left empty where it can be: less trust, never more than the setting.
func writeNaiveRoots() {
	naiveRoots.Lock()
	defer naiveRoots.Unlock()
	if naiveRoots.path == "" {
		return
	}
	name := rootStoreName()
	var bundle []byte
	count := 0
	if name == RootStoreSystem && naiveRoots.system != nil {
		bundle, count = rootsBundle(naiveRoots.system())
	}
	if err := writeFileAtomic(naiveRoots.path, bundle); err != nil {
		log.Println("naive roots:", err)
		_ = os.Truncate(naiveRoots.path, 0)
		return
	}
	if said := name + " " + strconv.Itoa(count); said != naiveRoots.said {
		naiveRoots.said = said
		if name == RootStoreSystem {
			log.Printf("naive roots: Chrome's list and %d of the system's store (over QUIC Chrome's list alone)", count)
		} else {
			log.Println("naive roots: Chrome's list alone")
		}
	}
}

// rootsBundle is the certificates as PEM blocks one after another, and how many: only what parses again, since
// Chromium drops everything after a block it cannot read.
func rootsBundle(certificates []*x509.Certificate) ([]byte, int) {
	var out bytes.Buffer
	count := 0
	for _, c := range certificates {
		if c == nil || len(c.Raw) == 0 {
			continue
		}
		if _, err := x509.ParseCertificate(c.Raw); err != nil {
			continue
		}
		if pem.Encode(&out, &pem.Block{Type: "CERTIFICATE", Bytes: c.Raw}) == nil {
			count++
		}
	}
	return out.Bytes(), count
}

// writeFileAtomic writes the file whole or not at all: an engine made meanwhile reads the list before or the list
// after, never a part. The temporary file has a name of its own each time, so two writers never share one.
func writeFileAtomic(path string, content []byte) error {
	tmp, err := os.CreateTemp(filepath.Dir(path), filepath.Base(path)+".*.tmp")
	if err != nil {
		return err
	}
	_, err = tmp.Write(content)
	if closeErr := tmp.Close(); err == nil {
		err = closeErr
	}
	if err == nil {
		err = os.Rename(tmp.Name(), path)
	}
	if err != nil {
		_ = os.Remove(tmp.Name())
	}
	return err
}

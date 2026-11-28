// Package naiveroots manages the PEM root certificate bundle passed to Chromium's Cronet
// verifier via SSL_CERT_FILE (naive-roots parity between Android libcore and desktop nikod).
//
// SSL_CERT_FILE is set once at process startup before any Cronet engine is created so Chromium
// threads never race with setenv. Whenever the core starts or reloads, Sync atomically updates
// the file: empty for "chrome"/"mozilla" (so Chromium trusts only its built-in roots, which
// sing-box protocol/naive narrows via certificate.StorePublicKeyHashes), or the system root
// certificates for "system".
package naiveroots

import (
	"bytes"
	"crypto/x509"
	"encoding/pem"
	"log"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"sync"

	"github.com/sagernet/sing-box/common/certificate"
)

const (
	RootStoreChrome  = "chrome"
	RootStoreMozilla = "mozilla"
	RootStoreSystem  = "system"
)

var state struct {
	sync.Mutex
	path   string
	system func() []*x509.Certificate
	said   string
}

// Init creates an empty PEM bundle in a private 0700 directory owned by the current user
// (creating an unpredictable temp dir via os.MkdirTemp when dir is empty) and points
// SSL_CERT_FILE and SSL_CERT_DIR once per process before any Cronet engine starts.
// Setting SSL_CERT_DIR to an empty private subdirectory prevents Chromium on desktop Linux
// from falling back to /etc/ssl/certs when SSL_CERT_FILE is empty ("chrome"/"mozilla" modes).
// Fails closed if the directory cannot be created or fails ownership/mode checks.
func Init(dir string) {
	state.Lock()
	defer state.Unlock()
	if state.path != "" {
		return
	}
	if dir == "" {
		created, err := os.MkdirTemp("", "niko-roots-")
		if err != nil {
			log.Println("naive roots: mkdirtemp:", err)
			return
		}
		dir = created
	} else {
		if err := os.MkdirAll(dir, 0o700); err != nil {
			log.Println("naive roots: mkdir:", err)
			return
		}
	}
	if err := verifyPrivateDir(dir); err != nil {
		log.Println("naive roots: refusing unsafe dir:", err)
		return
	}
	emptyDir := filepath.Join(dir, "empty-certs")
	if err := os.MkdirAll(emptyDir, 0o700); err != nil {
		log.Println("naive roots: mkdir empty-certs:", err)
		return
	}
	if err := verifyPrivateDir(emptyDir); err != nil {
		log.Println("naive roots: refusing unsafe empty-certs:", err)
		return
	}
	bundlePath := filepath.Join(dir, "naive-roots.pem")
	if err := writeFileAtomic(bundlePath, nil); err != nil {
		log.Println("naive roots: init:", err)
		return
	}
	state.path = bundlePath
	state.system = certificate.SystemCertificates
	if runtime.GOOS != "windows" {
		if err := os.Setenv("SSL_CERT_FILE", state.path); err != nil {
			log.Println("naive roots: setenv SSL_CERT_FILE:", err)
		}
		if err := os.Setenv("SSL_CERT_DIR", emptyDir); err != nil {
			log.Println("naive roots: setenv SSL_CERT_DIR:", err)
		}
	}
}

// Sync atomically writes the active root store's certificates into the bundle read by Cronet.
// Under "chrome" and "mozilla" the file is emptied; under "system" it holds system roots.
// On failure the file is removed (never Truncate, which follows symlinks) so trust never widens.
func Sync(rootStore string) {
	state.Lock()
	defer state.Unlock()
	if state.path == "" {
		return
	}
	name := normalizeStore(rootStore)
	var bundle []byte
	count := 0
	if name == RootStoreSystem && state.system != nil {
		bundle, count = rootsBundle(state.system())
	}
	if err := writeFileAtomic(state.path, bundle); err != nil {
		log.Println("naive roots: sync:", err)
		_ = os.Remove(state.path)
		return
	}
	if said := name + " " + strconv.Itoa(count); said != state.said {
		state.said = said
		if name == RootStoreSystem {
			log.Printf("naive roots: Chrome's list and %d system roots", count)
		} else {
			log.Printf("naive roots: Chromium built-in roots only, narrowed to %s store", name)
		}
	}
}

// Path returns the active bundle file path (empty before Init).
func Path() string {
	state.Lock()
	defer state.Unlock()
	return state.path
}

func normalizeStore(s string) string {
	switch s {
	case RootStoreSystem:
		return RootStoreSystem
	case RootStoreMozilla:
		return RootStoreMozilla
	default:
		return RootStoreChrome
	}
}

func rootsBundle(certs []*x509.Certificate) ([]byte, int) {
	var out bytes.Buffer
	count := 0
	for _, c := range certs {
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

func writeFileAtomic(path string, content []byte) error {
	tmp, err := os.CreateTemp(filepath.Dir(path), filepath.Base(path)+".*.tmp")
	if err != nil {
		return err
	}
	if err := tmp.Chmod(0o600); err != nil {
		_ = tmp.Close()
		_ = os.Remove(tmp.Name())
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

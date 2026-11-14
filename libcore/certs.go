package libcore

import (
	"context"
	"crypto/x509"
	"log"
	"os"
	"strings"
	"sync"
	_ "unsafe" // for go:linkname

	"github.com/sagernet/sing-box/common/certificate"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json"
	"github.com/sagernet/sing/common/json/badoption"
	"github.com/sagernet/sing/common/logger"
)

// The roots a certificate chain is verified against, everywhere this app verifies one: the core's outbounds and DNS
// servers (their certificate store), and Go's own roots, which every TLS client of the process that names none
// takes: the HTTP client of subscriptions, updates and rule sets, the probes.
//
//   - chrome (the default): Chrome's root store as the core carries it (common/certificate/chrome.go; the .pem
//     files beside it are not compiled in). A root installed on the phone, by its user or for them, opens none of
//     these connections.
//   - mozilla: Mozilla's list.
//   - system: Android's own store, with the roots its user installed (AndroidCAStore, through the core's JNI).
//
// The ca.pem imported in the app (Assets) is added under any of them; it never replaces a list. A profile's own
// certificate and key pins are the profile's, whatever the list.
//
// A naive outbound verifies inside Chromium, against the root store Chromium was built with, and besides it only
// what naive_roots.go gives it under the system's store. Under the lists it is held to the list of the setting too:
// the keys of the list's roots are its key pins (the core's protocol/naive). A profile's own certificate takes the
// place of all of it, and its own pins stay alone.
const (
	RootStoreChrome  = C.CertificateStoreChrome
	RootStoreMozilla = C.CertificateStoreMozilla
	RootStoreSystem  = C.CertificateStoreSystem
)

//go:linkname systemRoots crypto/x509.systemRoots
var systemRoots *x509.CertPool

//go:linkname systemRootsMu crypto/x509.systemRootsMu
var systemRootsMu sync.RWMutex

var rootStore struct {
	sync.Mutex
	// held over a whole applyRootStore: two of them, across a change of the setting, would otherwise set their
	// lists in either order
	applying sync.Mutex
	// what Go's roots were set from: the list's name and the imported certificates
	applied string
	// Go's own system roots, as they were before the first list took their place
	original *x509.CertPool
	// a ca.pem that held no certificate, said once
	refused string
}

// rootStoreName is the list the app's setting names; chrome for a value it does not know, and where nothing can
// be asked (a test).
func rootStoreName() string {
	if intfNiko != nil {
		switch name := intfNiko.RootStore(); name {
		case RootStoreMozilla, RootStoreSystem:
			return name
		}
	}
	return RootStoreChrome
}

// importedRoots are the certificates of the ca.pem imported in the app, empty when there is none or it holds no
// certificate: a file that does not read would otherwise stop the core at its start.
func importedRoots() string {
	pem, err := os.ReadFile(externalAssetsPath + "ca.pem")
	if err != nil {
		return ""
	}
	if !x509.NewCertPool().AppendCertsFromPEM(pem) {
		rootStore.Lock()
		said := rootStore.refused == string(pem)
		rootStore.refused = string(pem)
		rootStore.Unlock()
		if !said {
			log.Println("the imported ca.pem holds no certificate: left out")
		}
		return ""
	}
	return string(pem)
}

// rootStoreOptions is the certificate store of a core instance: the list of the setting and the imported roots.
func rootStoreOptions() *option.CertificateOptions {
	options := &option.CertificateOptions{Store: rootStoreName()}
	if pem := importedRoots(); pem != "" {
		options.Certificate = badoption.Listable[string]{pem}
	}
	return options
}

// rootStoreFor is the certificate store of a core instance whose config names own: the app's where it names none.
// A config that names certificates but no list gets the list of the setting and the imported roots besides its
// own (the core would give it the system's store, whatever the setting); one that names a list keeps it.
func rootStoreFor(own *option.CertificateOptions, named bool) *option.CertificateOptions {
	defaults := rootStoreOptions()
	if own == nil {
		return defaults
	}
	if named {
		return own
	}
	merged := *own
	merged.Store = defaults.Store
	merged.Certificate = append(append(badoption.Listable[string]{}, own.Certificate...), defaults.Certificate...)
	return &merged
}

// namesRootStore: whether a config names its list of roots (certificate.store). The decoded options do not say: an
// absent list reads as the system's.
func namesRootStore(config string) bool {
	var named struct {
		Certificate struct {
			Store string `json:"store"`
		} `json:"certificate"`
	}
	return json.Unmarshal([]byte(config), &named) == nil && named.Certificate.Store != ""
}

// applyRootStore makes Go's own roots the list of the setting, with the imported roots: called where a core
// instance or an HTTP client is made, so each process of the app follows the setting without being told.
func applyRootStore() {
	rootStore.applying.Lock()
	defer rootStore.applying.Unlock()
	options := rootStoreOptions()
	key := options.Store + "\x00" + strings.Join(options.Certificate, "\x00")
	rootStore.Lock()
	defer rootStore.Unlock()
	// the system's store is read each time: a root taken off the phone is to stop being trusted by a process that
	// lives on, and the store says nothing when it changes
	if key == rootStore.applied && options.Store != RootStoreSystem {
		return
	}
	if rootStore.original == nil {
		// loads them, once: a list set before that would be overwritten by the load
		original, err := x509.SystemCertPool()
		if err != nil || original == nil {
			original = x509.NewCertPool()
		}
		rootStore.original = original
	}
	if options.Store == RootStoreSystem {
		// where the platform gives the core no list of its own it takes Go's: the system's, not the list set before
		systemRootsMu.Lock()
		systemRoots = rootStore.original
		systemRootsMu.Unlock()
	}
	store, err := certificate.NewStore(context.Background(), logger.NOP(), *options)
	if err != nil {
		log.Println("roots:", err)
		return
	}
	systemRootsMu.Lock()
	systemRoots = store.Pool()
	systemRootsMu.Unlock()
	if key == rootStore.applied {
		return
	}
	rootStore.applied = key
	log.Printf("roots: the %s list, %d imported", options.Store, strings.Count(strings.Join(options.Certificate, ""), "BEGIN CERTIFICATE"))
}

package libcore

import (
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"errors"
	"net/http"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/miekg/dns"
)

// the app's side of the setting, as a test sets it
type rootStoreSetting struct{ name string }

func (rootStoreSetting) UseOfficialAssets() bool                 { return true }
func (rootStoreSetting) Selector_OnProxySelected(string, string) {}
func (s rootStoreSetting) RootStore() string                     { return s.name }

// The roots chains are verified against follow the app's setting everywhere: Go's own roots (the HTTP client of
// subscriptions and updates, the probes) and a core instance's certificate store. A certificate nobody vouches for
// passes nowhere under the lists; the ca.pem imported in the app adds its roots under any of them, to Go's clients
// and to the core's outbounds alike; a ca.pem that holds no certificate is left out and stops nothing.
func TestRootStore(t *testing.T) {
	oldNiko, oldAssets := intfNiko, externalAssetsPath
	assets := t.TempDir() + string(filepath.Separator)
	externalAssetsPath = assets
	set := func(name string) {
		intfNiko = rootStoreSetting{name}
		applyRootStore()
	}
	t.Cleanup(func() {
		intfNiko, externalAssetsPath = oldNiko, oldAssets
		rootStore.Lock()
		original := rootStore.original
		rootStore.applied = ""
		rootStore.Unlock()
		if original != nil {
			systemRootsMu.Lock()
			systemRoots = original
			systemRootsMu.Unlock()
		}
	})

	server, port, _ := dohTestServer(t)
	serverPEM := pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: server.Certificate().Raw})
	imported := func(content []byte) {
		t.Helper()
		if content == nil {
			os.Remove(assets + "ca.pem")
			return
		}
		if err := os.WriteFile(assets+"ca.pem", content, 0o600); err != nil {
			t.Fatal(err)
		}
	}
	// a client of Go's that names no roots, as libcore's own and the probes'
	throughGo := func() error {
		client := &http.Client{Transport: &http.Transport{}}
		defer client.CloseIdleConnections()
		response, err := client.Post(server.URL+"/dns-query/token", "application/dns-message", strings.NewReader(""))
		if err == nil {
			response.Body.Close()
		}
		return err
	}
	// libcore's HTTP client, which the app fetches subscriptions and updates with
	throughLibcore := func() error {
		client := NewHttpClient()
		defer client.Close()
		request := client.NewRequest()
		if err := request.SetURL(server.URL + "/dns-query/token"); err != nil {
			t.Fatal(err)
		}
		_, err := request.Execute()
		// the server's 400 to a request that is not a DNS query is an answer: the handshake went through
		if err != nil && strings.Contains(err.Error(), "HTTP 400") {
			return nil
		}
		return err
	}
	// a core instance with the certificate store libcore gives every instance, its DNS server verified by its chain
	throughCore := func() bool {
		raw, _ := json.Marshal(rootStoreOptions())
		var store any
		json.Unmarshal(raw, &store)
		answer, err := askThroughCore(t, port, map[string]any{"enabled": true, "server_name": "example.com"}, store)
		return err == nil && answer.Rcode == dns.RcodeSuccess && len(answer.Answer) == 1
	}
	unknownAuthority := func(err error) bool {
		var unknown x509.UnknownAuthorityError
		return err != nil && (errors.As(err, &unknown) || strings.Contains(err.Error(), "unknown authority"))
	}

	for _, name := range []string{RootStoreChrome, RootStoreMozilla, RootStoreSystem} {
		imported(nil)
		set(name)
		if got := rootStoreOptions(); got.Store != name || len(got.Certificate) != 0 {
			t.Fatalf("%s: a core instance takes store %q with %d certificates", name, got.Store, len(got.Certificate))
		}
		if err := throughGo(); !unknownAuthority(err) {
			t.Fatalf("%s: a certificate nobody vouches for, through a client of Go's: %v", name, err)
		}
		if err := throughLibcore(); !unknownAuthority(err) {
			t.Fatalf("%s: a certificate nobody vouches for, through libcore's HTTP client: %v", name, err)
		}
		if throughCore() {
			t.Fatalf("%s: a certificate nobody vouches for answered through the core", name)
		}

		// the imported root: an addition under this list too
		imported(serverPEM)
		set(name)
		if got := rootStoreOptions(); got.Store != name || len(got.Certificate) != 1 {
			t.Fatalf("%s with ca.pem: a core instance takes store %q with %d certificates", name, got.Store, len(got.Certificate))
		}
		if err := throughGo(); err != nil {
			t.Fatalf("%s with ca.pem, through a client of Go's: %v", name, err)
		}
		if err := throughLibcore(); err != nil {
			t.Fatalf("%s with ca.pem, through libcore's HTTP client: %v", name, err)
		}
		if !throughCore() {
			t.Fatalf("%s with ca.pem: no answer through the core", name)
		}
	}

	// the lists are lists: Chrome's and Mozilla's hold roots, and they differ
	sizes := map[string]int{}
	imported(nil)
	for _, name := range []string{RootStoreChrome, RootStoreMozilla} {
		set(name)
		systemRootsMu.RLock()
		sizes[name] = len(systemRoots.Subjects()) //nolint:staticcheck // not the system's pool: the subjects are there
		systemRootsMu.RUnlock()
	}
	// the numbers a release's notes give for the lists come from here
	t.Logf("the lists: Chrome's %d roots, Mozilla's %d", sizes[RootStoreChrome], sizes[RootStoreMozilla])
	if sizes[RootStoreChrome] < 50 || sizes[RootStoreMozilla] < 50 || sizes[RootStoreChrome] == sizes[RootStoreMozilla] {
		t.Fatalf("the lists: Chrome's %d roots, Mozilla's %d", sizes[RootStoreChrome], sizes[RootStoreMozilla])
	}
	// back to the system's own after a list: Go's roots as they were, not the list before
	set(RootStoreSystem)
	rootStore.Lock()
	original := rootStore.original
	rootStore.Unlock()
	systemRootsMu.RLock()
	same := systemRoots.Equal(original)
	systemRootsMu.RUnlock()
	if !same {
		t.Fatal("the system's roots after Chrome's list are not the system's own")
	}

	// a setting this version does not know, and no app to ask: Chrome's list
	set("none")
	if got := rootStoreName(); got != RootStoreChrome {
		t.Fatalf("an unknown setting gave %q", got)
	}
	intfNiko = nil
	if got := rootStoreName(); got != RootStoreChrome {
		t.Fatalf("no app to ask gave %q", got)
	}

	// a ca.pem that holds no certificate: left out, and a core instance starts
	intfNiko = rootStoreSetting{RootStoreChrome}
	imported([]byte("not a certificate"))
	applyRootStore()
	if got := rootStoreOptions(); len(got.Certificate) != 0 {
		t.Fatal("a ca.pem without a certificate went into the store")
	}
	if throughCore() {
		t.Fatal("a ca.pem without a certificate let the server through")
	}
}

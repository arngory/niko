package nikoservice

import (
	"context"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"libcore/nikopb"
	"libcore/nikoray"
	"libcore/redact"
)

func TestSyncLogRedactionsAndBypassTransport(t *testing.T) {
	defer redact.SetLiterals("")

	raw, err := os.ReadFile(filepath.Join("..", "..", "shared", "golden", "raycontrol", "invites", "rcdev1-user.txt"))
	if err != nil {
		t.Fatalf("read golden rcdev1-user.txt: %v", err)
	}
	pkg, err := nikoray.ParseDevice(string(raw))
	if err != nil {
		t.Fatalf("ParseDevice: %v", err)
	}
	pkg.Host = "secret-node.internal.example"
	pkg.DNSName = "edge.niko.internal"

	root := t.TempDir()
	u := newUsers(root, func(context.Context) (string, error) { return "alice", nil }, nil, nil)
	s := &server{users: u}
	u.onNodesChanged = s.syncLogRedactions

	userObj, err := u.user(context.Background())
	if err != nil {
		t.Fatal(err)
	}

	imported, err := userObj.store.ImportNode(pkg, nil, "")
	if err != nil {
		t.Fatalf("ImportNode: %v", err)
	}
	s.syncLogRedactions()

	line := "connecting to secret-node.internal.example and edge.niko.internal"
	redacted := redact.Log(line)
	if strings.Contains(redacted, "secret-node.internal.example") || strings.Contains(redacted, "edge.niko.internal") {
		t.Fatalf("node host/dns leaked after syncLogRedactions: %q", redacted)
	}

	// Reopening an existing store on disk populates redactions automatically via openDir.
	redact.SetLiterals("")
	u2 := newUsers(root, func(context.Context) (string, error) { return "alice", nil }, nil, nil)
	s2 := &server{users: u2}
	u2.onNodesChanged = s2.syncLogRedactions
	if _, err := u2.user(context.Background()); err != nil {
		t.Fatal(err)
	}
	redacted = redact.Log(line)
	if strings.Contains(redacted, "secret-node.internal.example") || strings.Contains(redacted, "edge.niko.internal") {
		t.Fatalf("node host/dns leaked after store reopen: %q", redacted)
	}

	// Deleting the node through the service clears its literals from redaction.
	if _, err := s2.DeleteNode(context.Background(), &nikopb.NodeId{Id: imported.ID()}); err != nil {
		t.Fatalf("DeleteNode: %v", err)
	}
	if got := redact.Log("secret-node.internal.example"); got != "secret-node.internal.example" {
		t.Fatalf("expected host unredacted after DeleteNode, got %q", got)
	}

	// Bypass transport reaches a local HTTP server directly when no test transport is injected.
	ts := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		_, _ = w.Write([]byte("ok"))
	}))
	defer ts.Close()

	rt := s.bypassTransport()
	req, err := http.NewRequestWithContext(context.Background(), http.MethodGet, ts.URL, nil)
	if err != nil {
		t.Fatal(err)
	}
	resp, err := rt.RoundTrip(req)
	if err != nil {
		t.Fatalf("bypassTransport RoundTrip failed: %v", err)
	}
	resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		t.Fatalf("unexpected status %d", resp.StatusCode)
	}
}

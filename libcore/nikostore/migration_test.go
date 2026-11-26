package nikostore

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"libcore/nikoconf"
	"libcore/nikoray"
)

func TestSealerContextMismatchRefused(t *testing.T) {
	dir := t.TempDir()
	s := open(t, dir, testSealer{"dpapi-user"}, nil)
	if _, err := s.AddProfiles(0, beans(t, socksLink)); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(dir, Options{Sealer: testSealer{"dpapi"}}); !errors.Is(err, ErrSealerMismatch) {
		t.Fatalf("expected ErrSealerMismatch opening dpapi-user store with dpapi, got %v", err)
	}
	if _, err := Open(dir, Options{Sealer: NoSealer{}}); !errors.Is(err, ErrSealerMismatch) {
		t.Fatalf("expected ErrSealerMismatch opening dpapi-user store with NoSealer, got %v", err)
	}

	svcDir := t.TempDir()
	svc := open(t, svcDir, testSealer{"dpapi"}, nil)
	if _, err := svc.AddProfiles(0, beans(t, socksLink)); err != nil {
		t.Fatal(err)
	}
	if _, err := Open(svcDir, Options{Sealer: testSealer{"dpapi-user"}}); !errors.Is(err, ErrSealerMismatch) {
		t.Fatalf("expected ErrSealerMismatch opening dpapi store with dpapi-user, got %v", err)
	}
}

func TestExportImportSnapshotResealsAllSecrets(t *testing.T) {
	userDir := t.TempDir()
	userStore := open(t, userDir, testSealer{"dpapi-user"}, nil)

	// Basic group + profiles + selection
	work, err := userStore.SaveGroup(Group{Name: "Work"})
	if err != nil {
		t.Fatal(err)
	}
	addedDefault, err := userStore.AddProfiles(0, beans(t, socksLink))
	if err != nil {
		t.Fatal(err)
	}
	addedWork, err := userStore.AddProfiles(work.ID, beans(t, naiveLink))
	if err != nil {
		t.Fatal(err)
	}
	if err := userStore.Select(addedWork[0].ID); err != nil {
		t.Fatal(err)
	}

	// Subscription group with secret link
	subGroup, err := userStore.SaveSubscriptionGroup(0, "Provider", SubscriptionSettings{
		Link:       "https://sub.example.com/secret-token-123",
		AutoUpdate: true,
	})
	if err != nil {
		t.Fatal(err)
	}
	_ = subGroup

	// Rule targeting a profile outbound
	if _, err := userStore.SaveRule(nikoconf.Rule{
		Name:     "Route to naive",
		Enabled:  true,
		Domains:  "example.org",
		Outbound: addedWork[0].ID,
	}); err != nil {
		t.Fatal(err)
	}

	// Settings including secret mixedPassword
	customSettings := nikoconf.DesktopSettings()
	customSettings.MixedPort = 2089
	customSettings.Secrets.MixedPassword = "super-secret-proxy-pass"
	if _, err := userStore.UpdateSettings(customSettings, []string{"mixedPort", "mixedPassword"}); err != nil {
		t.Fatal(err)
	}

	// Raycontrol node + group + row
	reg := goldenDevice(t, "rcdev1-user")
	node, err := userStore.ImportNode(reg, nil, "")
	if err != nil {
		t.Fatal(err)
	}
	nodeRows, err := userStore.ApplyNodeRows(node.ID(), reg.DeviceID, 1, []nikoray.Row{
		{Key: "row-1", Bean: beans(t, ssLink)[0]},
	}, func(n *nikoray.Node) {
		n.Name = "MyNode"
	})
	if err != nil || nodeRows.Group == 0 {
		t.Fatalf("ApplyNodeRows: %+v %v", nodeRows, err)
	}

	snapshot, err := userStore.ExportSnapshot()
	if err != nil {
		t.Fatalf("ExportSnapshot: %v", err)
	}

	// Target service store already has 1 profile so IDs MUST be remapped.
	svcDir := t.TempDir()
	svcStore := open(t, svcDir, testSealer{"dpapi"}, nil)
	if _, err := svcStore.AddProfiles(0, beans(t, vlessLink)); err != nil {
		t.Fatal(err)
	}

	stats, err := svcStore.ImportSnapshot(snapshot)
	if err != nil {
		t.Fatalf("ImportSnapshot: %v", err)
	}
	if stats.Groups != 3 || stats.Profiles != 3 || stats.Rules != 1 || stats.Nodes != 1 {
		t.Fatalf("unexpected stats: %+v", stats)
	}

	// Verify disk files are sealed under dpapi, never dpapi-user or plaintext secrets.
	storeBytes, err := os.ReadFile(filepath.Join(svcDir, storeFile))
	if err != nil {
		t.Fatal(err)
	}
	storeText := string(storeBytes)
	if strings.Contains(storeText, "dpapi-user:") {
		t.Fatalf("target store.json still contains dpapi-user prefix")
	}
	if !strings.Contains(storeText, `"dpapi:`) {
		t.Fatalf("target store.json missing dpapi sealed fields")
	}
	for _, secret := range []string{"examplepass0000000000000", "fake-pass", "secret-token-123", reg.DeviceKey} {
		if strings.Contains(storeText, secret) {
			t.Fatalf("target store.json leaked plaintext secret %q", secret)
		}
	}

	settingsBytes, err := os.ReadFile(filepath.Join(svcDir, settingsFile))
	if err != nil {
		t.Fatal(err)
	}
	if strings.Contains(string(settingsBytes), "super-secret-proxy-pass") {
		t.Fatalf("target settings.json leaked plaintext mixedPassword")
	}

	// Re-open target store and verify all decrypted data and remapped IDs.
	reopened := open(t, svcDir, testSealer{"dpapi"}, nil)
	sel := reopened.Selected()
	if sel == 0 || sel == addedWork[0].ID && addedDefault[0].ID == 0 {
		t.Fatalf("selected profile not preserved: %d", sel)
	}
	selProf, err := reopened.Profile(sel)
	if err != nil || selProf.Type != "naive" || !strings.Contains(string(selProf.Bean), "examplepass0000000000000") {
		t.Fatalf("selected profile mismatch: %+v %v", selProf, err)
	}

	rules := reopened.Rules()
	if len(rules) != 1 || rules[0].Outbound != sel {
		t.Fatalf("rule outbound not remapped to %d: %+v", sel, rules)
	}

	gotSettings, err := reopened.Settings()
	if err != nil || gotSettings.MixedPort != 2089 || gotSettings.Secrets.MixedPassword != "super-secret-proxy-pass" {
		t.Fatalf("settings mismatch: %+v %v", gotSettings, err)
	}

	gotNode, err := reopened.Node(node.ID())
	if err != nil || gotNode.DeviceKey != reg.DeviceKey || gotNode.GroupID == 0 {
		t.Fatalf("node mismatch: %+v %v", gotNode, err)
	}
	nodeRowID := gotNode.ProfileIDs["row-1"]
	if nodeRowID == 0 {
		t.Fatalf("node profile id missing: %+v", gotNode.ProfileIDs)
	}
	nodeProf, err := reopened.Profile(nodeRowID)
	if err != nil || nodeProf.Group != gotNode.GroupID {
		t.Fatalf("node row profile mismatch: %+v %v", nodeProf, err)
	}

	var foundSub string
	for _, g := range reopened.Groups() {
		if g.Type == GroupSubscription && g.Subscription != nil {
			foundSub = g.Subscription.Link
		}
	}
	if foundSub != "https://sub.example.com/secret-token-123" {
		t.Fatalf("subscription link not preserved: %q", foundSub)
	}

	// Re-importing the same snapshot must be a clean idempotent no-op (zero duplicates).
	stats2, err := reopened.ImportSnapshot(snapshot)
	if err != nil {
		t.Fatalf("second ImportSnapshot: %v", err)
	}
	if stats2.Groups != 0 || stats2.Profiles != 0 || stats2.Rules != 0 || stats2.Nodes != 0 {
		t.Fatalf("expected all-zero stats on idempotent re-import, got %+v", stats2)
	}
	if len(reopened.Nodes()) != 1 {
		t.Fatalf("expected 1 node total, got %d", len(reopened.Nodes()))
	}
	if len(reopened.Rules()) != 1 {
		t.Fatalf("expected 1 rule total, got %d", len(reopened.Rules()))
	}

	// Invalid snapshot rejected cleanly.
	var bad json.RawMessage = []byte(`{"version":99}`)
	if _, err := reopened.ImportSnapshot(bad); !errors.Is(err, ErrInvalid) {
		t.Fatalf("expected ErrInvalid for bad version, got %v", err)
	}
}

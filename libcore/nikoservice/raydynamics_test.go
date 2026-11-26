package nikoservice

import (
	"context"
	"encoding/json"
	"io"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"libcore/nikoconf"
	"libcore/nikofmt"
	"libcore/nikopb"
	"libcore/nikoray"
	"libcore/rayapi"
	"libcore/raysub"
	"libcore/redact"

	"github.com/sagernet/sing-box/experimental/boxdd"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// Fake-node integration suite for Phase P6 raycontrol dynamics (run against an in-process fake raycontrol
// node because local `rayd` is not installed on this workstation).

func sampleRayNode(t *testing.T, id string) (*nikoray.Node, *nikoray.Identity) {
	t.Helper()
	ident, err := rayapi.NewIdentity()
	if err != nil {
		t.Fatal(err)
	}
	ni := &nikoray.Identity{
		Cert:      ident.Cert,
		Key:       ident.Key,
		Pin:       ident.Pin,
		CreatedAt: time.Now().Add(-100 * 24 * time.Hour).Unix(),
	}
	return &nikoray.Node{
		Host:      "203.0.113.10",
		DNSName:   "new-node.example.net",
		PastNames: []string{"old-node.example.net"},
		SPA: nikoray.SPAParams{
			NodeID:    id,
			EpochLenS: 3600,
			SlotLenS:  10,
			PortKey:   strings.Repeat("ab", 32),
			PortBase:  20000,
			PortRange: 1000,
			LenMin:    64,
			LenMax:    128,
		},
		DeviceID:  "aabbccdd11223344",
		DeviceKey: strings.Repeat("11", 32),
		Services:  []nikoray.Service{{ID: 1, Kind: "hysteria2"}, {ID: 2, Kind: "vless"}},
		Subscription: &nikoray.SubscriptionInfo{
			Port:    8443,
			Pin:     "pin-sub",
			Ed25519: "ed",
			MLDSA65: "ml",
		},
		API:      &nikoray.APIInfo{Port: 8444, Pin: "pin-api"},
		Identity: ni,
		Relays: []nikoray.Relay{{
			ID:   "eu-relay",
			Host: "198.51.100.77",
			SPA: nikoray.SPAParams{
				NodeID:    strings.Repeat("22", 16),
				EpochLenS: 3600,
				SlotLenS:  10,
				PortKey:   strings.Repeat("cd", 32),
				PortBase:  30000,
				PortRange: 1000,
				LenMin:    64,
				LenMax:    128,
			},
		}},
		Probe: &nikoray.ProbeInfo{V: 1, Tunnel: "probe.internal", UDP: true, Report: []string{"path", "udp_mtu"}},
		Lan: &nikoray.Lan{
			V:         1,
			Svc:       1,
			RelayMbps: 20,
			Label:     "home",
			Password:  "0123456789abcdef0123456789abcdef",
			Devices: []nikoray.LanDevice{
				{ID: "aabbccdd11223344", Name: "ThisPC", Label: "pc", Addr: "200::1"},
				{ID: "deadbeef55667788", Name: "Phone", Label: "phone", Addr: "200::2"},
			},
		},
		ProfileIDs: map[string]int64{},
		Links:      map[string]nikoray.Links{},
	}, ni
}

func importSampleNode(t *testing.T, srv *server, u *user) *nikoray.Node {
	t.Helper()
	nodeID := strings.Repeat("aa", 16)
	node, ident := sampleRayNode(t, nodeID)
	reg := nikoray.Registration{
		Host:         node.Host,
		DNSName:      node.DNSName,
		SPA:          node.SPA,
		DeviceID:     node.DeviceID,
		DeviceKey:    node.DeviceKey,
		Services:     node.Services,
		Subscription: node.Subscription,
		API:          node.API,
		Role:         "admin",
	}
	if _, err := u.store.ImportNode(reg, ident, ""); err != nil {
		t.Fatal(err)
	}
	// Populate dynamic subscription fields (relays, probe, lan, pastNames, rows).
	link1 := "hysteria2://pass1@203.0.113.10:443/?sni=s.example#Hy2"
	link1Next := "hysteria2://pass2@203.0.113.10:443/?sni=s.example#Hy2"
	link2 := "naive+https://u:p@203.0.113.10:443/?sni=s.example#Naive"
	payload := map[string]any{
		"issued":  1_700_000_000,
		"expires": 1_900_000_000,
		"name":    "HomeNode",
		"dns_name": "new-node.example.net",
		"addrs":   []string{"203.0.113.10"},
		"profiles": []any{
			map[string]any{"svc": 1, "name": "Hy2", "link": link1, "next": map[string]any{"link": link1Next, "from": 1_800_000_000}},
			map[string]any{"svc": 2, "name": "Naive", "link": link2},
		},
		"relays": []any{
			map[string]any{
				"id":   "eu-relay",
				"host": "198.51.100.77",
				"spa": map[string]any{
					"node_id":     strings.Repeat("22", 16),
					"epoch_len_s": 3600,
					"slot_len_s":  10,
					"port_key":    strings.Repeat("cd", 32),
					"port_base":   30000,
					"port_range":  1000,
					"len_min":     64,
					"len_max":     128,
				},
			},
		},
		"probe": map[string]any{"v": 1, "tunnel": "probe.internal", "udp": true, "report": []string{"path", "udp_mtu"}},
		"lan": map[string]any{
			"v":        1,
			"svc":      1,
			"relay":    20,
			"label":    "home",
			"password": "0123456789abcdef0123456789abcdef",
			"devices": []any{
				map[string]any{"id": "aabbccdd11223344", "name": "ThisPC", "label": "pc", "addr": "200::1"},
				map[string]any{"id": "deadbeef55667788", "name": "Phone", "label": "phone", "addr": "200::2"},
			},
		},
	}
	rawPayload, _ := json.Marshal(payload)
	parsed, err := nikoray.Parse(rawPayload, node.Kinds(), 1_750_000_000, nikofmt.DefaultSettings)
	if err != nil {
		t.Fatalf("nikoray.Parse: %v", err)
	}
	if len(parsed.Relays) != 1 || len(parsed.Rows) != 4 {
		t.Fatalf("expected 1 relay and 4 rows (2 direct + 2 relay), got relays=%d rows=%d", len(parsed.Relays), len(parsed.Rows))
	}
	links := map[string]nikoray.Links{}
	for _, row := range parsed.Rows {
		links[row.Key] = row.Links
	}
	_, err = u.store.ApplyNodeRows(nodeID, node.DeviceID, 1, parsed.Rows, func(n *nikoray.Node) {
		n.Links = links
		n.Relays = parsed.Relays
		n.Probe = parsed.Probe
		n.Lan = parsed.Lan
		n.RecordPastName("old-node.example.net")
	})
	if err != nil {
		t.Fatalf("ApplyNodeRows: %v", err)
	}
	stored, err := u.store.Node(nodeID)
	if err != nil {
		t.Fatal(err)
	}
	return stored
}

func TestRaycontrolRelaysLanBrutalAndPastNamesConfig(t *testing.T) {
	srv := storeServer(t.TempDir())
	u, err := srv.users.user(context.Background())
	if err != nil {
		t.Fatal(err)
	}
	node := importSampleNode(t, srv, u)

	// PastNames, Addrs, and relay host matching and log redaction.
	if !node.MatchesHost("old-node.example.net") {
		t.Fatal("expected MatchesHost to match PastNames")
	}
	srv.syncLogRedactions()
	redacted := redact.Log("dial old-node.example.net via 198.51.100.77 and 203.0.113.10")
	if strings.Contains(redacted, "old-node.example.net") || strings.Contains(redacted, "198.51.100.77") || strings.Contains(redacted, "203.0.113.10") {
		t.Fatalf("expected past name, node addr, and relay host to be redacted, got %q", redacted)
	}

	// Enable LAN and set Brutal rates -> verify config contains dns-lan + relay knock + down_mbps/up_mbps.
	if err := u.store.SetNodeLanShow(node.ID(), true); err != nil {
		t.Fatal(err)
	}
	srv.recordBrutalRates(node.ID(), 50_000, 10_000)

	// Test direct Hysteria2 profile with Brutal + LAN.
	directHy2ID := node.ProfileIDs["1"]
	settings, _ := u.store.Settings()
	settings.RaycontrolBrutal = true
	in, err := connectInput(u.store, directHy2ID, connectSettings(settings))
	if err != nil {
		t.Fatal(err)
	}
	in.BrutalRates = srv.currentBrutalRates()
	built, err := nikoconf.Build(in)
	if err != nil {
		t.Fatalf("Build direct Hy2: %v", err)
	}
	cfg := string(built.Config)
	if !strings.Contains(cfg, `"down_mbps": 50`) || !strings.Contains(cfg, `"up_mbps": 10`) {
		t.Fatalf("expected Brutal down_mbps/up_mbps in config: %s", cfg)
	}
	if !strings.Contains(cfg, `"phone.home.niko.internal"`) {
		t.Fatalf("expected lan-v1 DNS entry in config: %s", cfg)
	}

	// Test relay profile (#via-eu-relay) -> verify knock uses relay exit_node_id + relay id.
	relayHy2ID := node.ProfileIDs["1#via-eu-relay"]
	if relayHy2ID == 0 {
		t.Fatalf("missing relay profile row: %+v", node.ProfileIDs)
	}
	inRelay, err := connectInput(u.store, relayHy2ID, connectSettings(settings))
	if err != nil {
		t.Fatal(err)
	}
	builtRelay, err := nikoconf.Build(inRelay)
	if err != nil {
		t.Fatalf("Build relay Hy2: %v", err)
	}
	if !strings.Contains(string(builtRelay.Config), `"exit_node_id"`) || !strings.Contains(string(builtRelay.Config), `"eu-relay"`) {
		t.Fatalf("expected relay SPA derivation in knock outbound: %s", builtRelay.Config)
	}

	// Brutal lowering on poor ack: 1st strike -25%, 2nd strike -> 0 (BBR).
	if down, ok := srv.lowerBrutalOnPoorAck(node.ID()); !ok || down != 37500 {
		t.Fatalf("1st Brutal lowering = (%d, %v), want (37500, true)", down, ok)
	}
	if down, ok := srv.lowerBrutalOnPoorAck(node.ID()); !ok || down != 0 {
		t.Fatalf("2nd Brutal lowering = (%d, %v), want (0, true)", down, ok)
	}
}

func TestRaycontrolAdminCallAndKeyRotation(t *testing.T) {
	ctx := context.Background()
	srv := storeServer(t.TempDir())
	u, err := srv.users.user(ctx)
	if err != nil {
		t.Fatal(err)
	}
	node := importSampleNode(t, srv, u)
	oldPin := node.Identity.Pin

	// Fake raycontrol node tracking current active pin and admin calls.
	var activePin atomic.Value
	activePin.Store(oldPin)
	var adminCalls []string

	srv.apiCall = func(_ context.Context, ep rayapi.Endpoint, method string, body []byte) ([]byte, error) {
		if ep.Identity.Pin != activePin.Load().(string) {
			return nil, &rayapi.Error{Code: "unauthenticated", Message: "unknown key"}
		}
		adminCalls = append(adminCalls, method)
		switch method {
		case rayapi.NodeService + "GetStatus":
			return []byte(`{"version":"0.36.0","host":"node.example","warnings":["test warning"]}`), nil
		case rayapi.NodeService + "CreateInvite":
			return []byte(`{"inviteCodeShort":"rcinv2:test-invite-token","pin":"123456"}`), nil
		case rayapi.NodeService + "SetServiceEnabled":
			return []byte(`{}`), nil
		case rayapi.NodeService + "ListAudit":
			return []byte(`{"entries":[]}`), nil
		case rayapi.DeviceService + "GetSelf":
			return []byte(`{"role":"admin"}`), nil
		default:
			return []byte(`{}`), nil
		}
	}
	srv.rotateCert = func(_ context.Context, ep rayapi.Endpoint, next rayapi.Identity) error {
		// Simulate the node switching to next key, then dropping the response (ErrAnswerLost)!
		activePin.Store(next.Pin)
		return rayapi.ErrAnswerLost
	}

	client := serve(t, srv)

	// Disallowed method must be rejected.
	if _, err := client.NodeAdminCall(ctx, &nikopb.NodeAdminRequest{NodeId: node.ID(), Method: "DropDatabase"}); code(err) != codes.InvalidArgument {
		t.Fatalf("disallowed admin method = %v, want InvalidArgument", err)
	}
	// Allowed method succeeds via daemon's sealed identity.
	resp, err := client.NodeAdminCall(ctx, &nikopb.NodeAdminRequest{NodeId: node.ID(), Method: "GetStatus"})
	if err != nil || !strings.Contains(resp.BodyJson, "0.36.0") {
		t.Fatalf("GetStatus = (%+v, %v)", resp, err)
	}

	// Trigger 90-day key rotation where the node switches key but drops the response.
	if rotated, rotErr := srv.rotateNodeIdentityIfDue(ctx, u, node.ID()); rotated || rotErr == nil {
		t.Fatalf("expected ErrAnswerLost on first rotation attempt, got (%v, %v)", rotated, rotErr)
	}
	// Next admin call gets `unauthenticated` on old key -> automatically retries IdentityNext & promotes it!
	invResp, err := client.NodeAdminCall(ctx, &nikopb.NodeAdminRequest{
		NodeId:   node.ID(),
		Method:   "CreateInvite",
		BodyJson: `{"user":"alice","name":"desktop","role":"user","ttlSeconds":"86400","pin":true}`,
	})
	if err != nil || !strings.Contains(invResp.BodyJson, "rcinv2:test-invite-token") {
		t.Fatalf("CreateInvite after lost rotation = (%+v, %v)", invResp, err)
	}
	if _, err := client.NodeAdminCall(ctx, &nikopb.NodeAdminRequest{NodeId: node.ID(), Method: "SetServiceEnabled", BodyJson: `{"name":"hy2","enabled":false}`}); err != nil {
		t.Fatalf("SetServiceEnabled = %v", err)
	}
	if _, err := client.NodeAdminCall(ctx, &nikopb.NodeAdminRequest{NodeId: node.ID(), Method: "ListAudit", BodyJson: `{"limit":10}`}); err != nil {
		t.Fatalf("ListAudit = %v", err)
	}
	updated, _ := u.store.Node(node.ID())
	if updated.Identity.Pin == oldPin || updated.IdentityNext != nil {
		t.Fatalf("expected IdentityNext to be promoted to Identity: old=%s cur=%s next=%v", oldPin, updated.Identity.Pin, updated.IdentityNext)
	}
}

func TestRaycontrolApplyDueAlternativeAndWatchdogFailover(t *testing.T) {
	ctx := context.Background()
	srv := storeServer(t.TempDir())
	u, err := srv.users.user(ctx)
	if err != nil {
		t.Fatal(err)
	}
	node := importSampleNode(t, srv, u)
	hy2ID := node.ProfileIDs["1"]
	naiveID := node.ProfileIDs["2"]

	// 1. ApplyDueLinks promotes scheduled `next` credential once nodeNow >= from.
	changed, err := u.store.ApplyDueLinks(node.ID(), 1_800_000_001)
	if err != nil || len(changed) == 0 {
		t.Fatalf("ApplyDueLinks = (%v, %v)", changed, err)
	}
	p, _ := u.store.Profile(hy2ID)
	if !strings.Contains(string(p.Bean), "pass2") {
		t.Fatalf("expected promoted next credential pass2, got %s", p.Bean)
	}

	// 2. UseAlternativeLink toggles back to `link` (pass1).
	pid, switched, err := u.store.UseAlternativeLink(node.ID(), "1")
	if err != nil || !switched || pid != hy2ID {
		t.Fatalf("UseAlternativeLink = (%d, %v, %v)", pid, switched, err)
	}
	p, _ = u.store.Profile(hy2ID)
	if !strings.Contains(string(p.Bean), "pass1") {
		t.Fatalf("expected toggled credential pass1, got %s", p.Bean)
	}

	// 3a. Failed watchdog failover must NOT mutate selected profile or D-STATE-SWITCHED!
	_ = u.store.Select(naiveID)
	srv.start = func(_ context.Context, _ string, _ *boxdd.SystemProxy) error {
		return status.Error(codes.Unavailable, "core start refused")
	}
	srv.fetch = func(context.Context, raysub.Request) (*raysub.Result, error) {
		return nil, raysub.ErrNoAnswer
	}
	w := &watchdogRun{nodeID: node.ID(), profileID: naiveID, key: "2"}
	srv.testProfile = func(_ context.Context, _ *user, id int64, _ string, _ time.Duration) (int32, error) {
		if id == hy2ID {
			return 25, nil
		}
		return 0, status.Error(codes.DeadlineExceeded, "timeout")
	}
	settings, _ := u.store.Settings()
	settings.RaycontrolAutoSwitch = true
	if ok := srv.recoverRayNode(ctx, u, w, settings); ok {
		t.Fatal("expected recoverRayNode to return false when core start fails")
	}
	if u.store.Selected() != naiveID {
		t.Fatalf("failed failover mutated selected profile to %d", u.store.Selected())
	}
	if afterFail, _ := u.store.Node(node.ID()); afterFail.SwitchedTo != "" || afterFail.SwitchedAt != 0 {
		t.Fatalf("failed failover populated D-STATE-SWITCHED: %+v", afterFail)
	}

	// 3b. Successful watchdog failover switches to fastest working profile and records D-STATE-SWITCHED!
	var startedConfigs []string
	srv.start = func(_ context.Context, cfg string, _ *boxdd.SystemProxy) error {
		startedConfigs = append(startedConfigs, cfg)
		return nil
	}
	if ok := srv.recoverRayNode(ctx, u, w, settings); !ok {
		t.Fatal("expected recoverRayNode to fail over to working Hy2 profile")
	}
	if u.store.Selected() != hy2ID {
		t.Fatalf("expected selected profile %d, got %d", hy2ID, u.store.Selected())
	}
	updated, _ := u.store.Node(node.ID())
	if updated.SwitchedFrom == "" || updated.SwitchedTo == "" || updated.SwitchedAt == 0 {
		t.Fatalf("expected D-STATE-SWITCHED fields populated: %+v", updated)
	}
	// ClearNodeSwitched RPC dismisses banner.
	client := serve(t, srv)
	if _, err := client.ClearNodeSwitched(ctx, &nikopb.NodeId{Id: node.ID()}); err != nil {
		t.Fatal(err)
	}
	cleared, _ := u.store.Node(node.ID())
	if cleared.SwitchedFrom != "" || cleared.SwitchedTo != "" || cleared.SwitchedAt != 0 {
		t.Fatalf("expected D-STATE-SWITCHED cleared: %+v", cleared)
	}
}

func TestRunNodeTestStreamingFindingsAndRedaction(t *testing.T) {
	ctx := context.Background()
	srv := storeServer(t.TempDir())
	u, err := srv.users.user(ctx)
	if err != nil {
		t.Fatal(err)
	}
	node := importSampleNode(t, srv, u)
	relayID := node.ProfileIDs["1#via-eu-relay"]

	srv.directProbe = func(_ context.Context, _ *nikoray.Node) (*directProbeOutcome, error) {
		return &directProbeOutcome{SPACarrier: "udp", UDPProbed: true, UDPMeasured: true, UDPMTU: 900}, nil
	}
	srv.testProfile = func(_ context.Context, _ *user, id int64, _ string, _ time.Duration) (int32, error) {
		if id == relayID {
			return 35, nil
		}
		return 0, status.Error(codes.DeadlineExceeded, "i/o timeout")
	}
	// Avoid hitting real network for subscription refresh in this test.
	_, _ = u.store.UpdateNode(node.ID(), func(n *nikoray.Node) bool {
		n.Subscription = nil
		return true
	})

	client := serve(t, srv)
	stream, err := client.RunNodeTest(ctx, &nikopb.NodeTestRequest{NodeId: node.ID(), RunId: "r-test-01"})
	if err != nil {
		t.Fatal(err)
	}
	var final *nikopb.NodeTestEvent
	steps := 0
	for {
		ev, err := stream.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		steps++
		if ev.Done {
			final = ev
		}
	}
	if steps < 4 || final == nil || final.Findings == nil {
		t.Fatalf("incomplete RunNodeTest stream: steps=%d final=%+v", steps, final)
	}
	if final.ReportName != "niko-r-test-01.log" {
		t.Fatalf("unexpected report name: %q", final.ReportName)
	}
	// Report must redact node host & relay host.
	if strings.Contains(final.ReportText, "203.0.113.10") || strings.Contains(final.ReportText, "198.51.100.77") {
		t.Fatalf("RunNodeTest report leaked node/relay address: %s", final.ReportText)
	}
	// Findings should note udp_small (900 < 1200) and relay_only (only relay profile worked).
	notes := map[string]int64{}
	for _, n := range final.Findings.Notes {
		notes[n.Note] = n.Number
	}
	if notes[nikoray.NoteUDPSmall] != 900 {
		t.Fatalf("expected udp_small=900 note, got %+v", notes)
	}
	if _, ok := notes[nikoray.NoteRelayOnly]; !ok {
		t.Fatalf("expected relay_only note, got %+v", notes)
	}
}

func TestDatagramsVerdictCacheAndAnonymousReport(t *testing.T) {
	if v := EvaluateDatagramEcho(429, 0, 0); v != DatagramAgain {
		t.Fatalf("429 = %v, want again", v)
	}
	if v := EvaluateDatagramEcho(200, 30, 22); v != DatagramPassed {
		t.Fatalf("22/30 = %v, want passed", v)
	}
	if v := EvaluateDatagramEcho(200, 30, 15); v != DatagramFailed {
		t.Fatalf("15/30 = %v, want failed", v)
	}
	cache := &datagramCache{}
	cache.Remember("wifi-1", "svc-1", true, 1000)
	if ok, found := cache.Lookup("wifi-1", "svc-1", 2000); !found || !ok {
		t.Fatalf("cache lookup = (%v, %v), want (true, true)", ok, found)
	}
	if _, found := cache.Lookup("wifi-1", "svc-1", 1000+datagramHoldPass+1); found {
		t.Fatal("expected expired cache entry")
	}

	raw := BuildAnonymousReport(1, true, 30, true, []string{"path"})
	var parsed map[string]any
	if err := json.Unmarshal(raw, &parsed); err != nil {
		t.Fatal(err)
	}
	if parsed["path"] != "relay" || parsed["net"] != "desktop" {
		t.Fatalf("unexpected anonymous report: %s", raw)
	}
}

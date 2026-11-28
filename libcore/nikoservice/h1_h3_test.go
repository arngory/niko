package nikoservice

import (
	"context"
	"encoding/json"
	"strings"
	"testing"

	"libcore/nikoconf"
	"libcore/nikofmt"
	"libcore/nikoray"

	"github.com/sagernet/sing-box/experimental/boxdd"
	"github.com/sagernet/sing-box/protocol/knock"
	dnsttTransport "github.com/sagernet/sing-box/transport/dnstt"
)

func strPtr(s string) *string { return &s }
func intPtr(i int) *int       { return &i }

func TestH1DnsttDeviceKeysWired(t *testing.T) {
	if dnsttTransport.DeviceKeys == nil {
		t.Fatal("dnsttTransport.DeviceKeys is nil; dnsauth will fail in nikod")
	}
	const ref = "test-node-h1"
	const keyHex = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"
	knock.SetDeviceKey(ref, keyHex)
	t.Cleanup(func() { knock.RetainDeviceKeys(nil) })

	resolved, err := dnsttTransport.ResolveDeviceKey("", ref)
	if err != nil {
		t.Fatalf("ResolveDeviceKey(%q) failed: %v", ref, err)
	}
	if len(resolved) != 32 {
		t.Fatalf("resolved key length = %d, want 32", len(resolved))
	}
}

func TestH2AutoDnsTunnelResolversResolved(t *testing.T) {
	nodeID := "0123456789abcdef0123456789abcdef"
	node := nikoray.Node{
		Host:       "198.51.100.10",
		DeviceKey:  "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		SPA:        nikoray.SPAParams{NodeID: nodeID},
		ProfileIDs: map[string]int64{"1#0": 10, "2#0": 20},
	}

	dnsttBean := &nikofmt.VMessBean{
		StandardV2RayBean: nikofmt.StandardV2RayBean{
			AbstractBean: nikofmt.AbstractBean{
				Name:              strPtr("node-dnstt"),
				ServerAddress:     strPtr("198.51.100.10"),
				ServerPort:        intPtr(443),
				DnsTunnel:         strPtr("dnstt"),
				DnsTunnelDomain:   strPtr("t.example.com"),
				DnsTunnelKey:      strPtr("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
				DnsTunnelResolver: strPtr("auto"),
			},
			Uuid:     strPtr("11111111-1111-1111-1111-111111111111"),
			Security: strPtr("none"),
		},
		AlterId: intPtr(-1),
	}
	dnsttBean.InitializeDefaultValues()

	masterBean := &nikofmt.VMessBean{
		StandardV2RayBean: nikofmt.StandardV2RayBean{
			AbstractBean: nikofmt.AbstractBean{
				Name:              strPtr("node-masterdns"),
				ServerAddress:     strPtr("198.51.100.10"),
				ServerPort:        intPtr(443),
				DnsTunnel:         strPtr("masterdnsvpn"),
				DnsTunnelDomain:   strPtr("m.example.com"),
				DnsTunnelKey:      strPtr("secret-key"),
				DnsTunnelMethod:   intPtr(3),
				DnsTunnelResolver: strPtr("auto"),
			},
			Uuid:     strPtr("11111111-1111-1111-1111-111111111111"),
			Security: strPtr("none"),
		},
		AlterId: intPtr(-1),
	}
	masterBean.InitializeDefaultValues()

	// 1. Static defaults replace "auto" for both dnstt and masterdnsvpn.
	for _, tc := range []struct {
		name      string
		profileID int64
		bean      nikofmt.Bean
		wantSub   string
	}{
		{"dnstt default", 10, dnsttBean, nikoconf.DefaultDnsttResolver},
		{"masterdns default", 20, masterBean, "195.208.5.1"},
	} {
		t.Run(tc.name, func(t *testing.T) {
			in := nikoconf.Input{
				Profile:  tc.profileID,
				Profiles: []nikoconf.Profile{{ID: tc.profileID, Group: 1, Bean: tc.bean}},
				Groups:   []nikoconf.Group{{ID: 1}},
				Settings: nikoconf.DesktopSettings(),
				Platform: nikoconf.PlatformLinux,
				Nodes:    []nikoray.Node{node},
			}
			res, err := nikoconf.Build(in)
			if err != nil {
				t.Fatalf("Build failed: %v", err)
			}
			if !strings.Contains(string(res.Config), tc.wantSub) {
				t.Fatalf("config missing %q:\n%s", tc.wantSub, string(res.Config))
			}
			if strings.Contains(string(res.Config), `"auto"`) {
				t.Fatalf("config still contains literal auto resolver:\n%s", string(res.Config))
			}
		})
	}

	// 2. Per-node probed DnsResolvers override takes precedence over static default.
	in := nikoconf.Input{
		Profile:      10,
		Profiles:     []nikoconf.Profile{{ID: 10, Group: 1, Bean: dnsttBean}},
		Groups:       []nikoconf.Group{{ID: 1}},
		Settings:     nikoconf.DesktopSettings(),
		Platform:     nikoconf.PlatformDarwin,
		Nodes:        []nikoray.Node{node},
		DnsResolvers: map[string]string{"dnstt:" + nodeID: "tcp://198.51.100.53"},
	}
	res, err := nikoconf.Build(in)
	if err != nil {
		t.Fatalf("Build with override failed: %v", err)
	}
	if !strings.Contains(string(res.Config), "tcp://198.51.100.53") {
		t.Fatalf("expected probed resolver in config:\n%s", string(res.Config))
	}
}

func TestH3BeforeRestoreRebuildsConfigAndLoadsDeviceKeys(t *testing.T) {
	var _ boxdd.RestorePreparer = (*server)(nil)
	var _ boxdd.StopHook = (*server)(nil)
	root := t.TempDir()
	const owner = "1000"
	users := newUsers(root, func(context.Context) (string, error) { return owner, nil }, nil, nil)
	s := &server{users: users}

	u, err := users.userByID(owner)
	if err != nil {
		t.Fatalf("userByID: %v", err)
	}
	bean := &nikofmt.SOCKSBean{
		AbstractBean: nikofmt.AbstractBean{
			Name:          strPtr("socks-restore"),
			ServerAddress: strPtr("198.51.100.20"),
			ServerPort:    intPtr(1080),
		},
	}
	bean.InitializeDefaultValues()
	raw, _ := json.Marshal(bean)
	saved, err := u.store.AddProfiles(0, []nikofmt.Bean{bean})
	if err != nil || len(saved) != 1 {
		t.Fatalf("AddProfiles (%s): %v", raw, err)
	}
	if err := u.store.Select(saved[0].ID); err != nil {
		t.Fatalf("Select: %v", err)
	}

	// Without an active NikoService.Connect session, BeforeRestore must NOT hijack raw configs (M1).
	if _, handled, err := s.BeforeRestore(context.Background(), owner); err != nil || handled {
		t.Fatalf("expected unhandled BeforeRestore without active connect: handled=%v err=%v", handled, err)
	}

	// Record an active Connect in VPN mode and verify BeforeRestore rebuilds that exact profile & mode.
	if err := u.store.SetActiveConnection(saved[0].ID, nikoconf.ModeVPN); err != nil {
		t.Fatalf("SetActiveConnection: %v", err)
	}
	cfg, handled, err := s.BeforeRestore(context.Background(), owner)
	if err != nil {
		t.Fatalf("BeforeRestore: %v", err)
	}
	if !handled || !strings.Contains(cfg, "198.51.100.20") || !strings.Contains(cfg, `"type": "tun"`) {
		t.Fatalf("unexpected BeforeRestore output: handled=%v len=%d", handled, len(cfg))
	}

	// AfterStop clears active connection so a subsequent restore does not resurrect a stopped session.
	s.AfterStop(owner)
	if id, mode := u.store.ActiveConnection(); id != 0 || mode != "" {
		t.Fatalf("expected cleared active connection after AfterStop, got id=%d mode=%q", id, mode)
	}
}

func TestYggdrasilChainClashAndResolverPrecedence(t *testing.T) {
	root := t.TempDir()
	const owner = "1000"
	users := newUsers(root, func(context.Context) (string, error) { return owner, nil }, nil, nil)
	u, err := users.userByID(owner)
	if err != nil {
		t.Fatalf("userByID: %v", err)
	}

	// H1 + M2: enabling Yggdrasil persists a 128-hex key and setting clashApi enables clash_api in config.
	hop1 := &nikofmt.SOCKSBean{
		AbstractBean: nikofmt.AbstractBean{Name: strPtr("hop1"), ServerAddress: strPtr("198.51.100.1"), ServerPort: intPtr(1080)},
	}
	hop2 := &nikofmt.SOCKSBean{
		AbstractBean: nikofmt.AbstractBean{Name: strPtr("hop2"), ServerAddress: strPtr("198.51.100.2"), ServerPort: intPtr(1081)},
	}
	hop1.InitializeDefaultValues()
	hop2.InitializeDefaultValues()
	added, err := u.store.AddProfiles(0, []nikofmt.Bean{hop1, hop2})
	if err != nil || len(added) != 2 {
		t.Fatalf("AddProfiles hops: %v", err)
	}
	chain := &nikofmt.ChainBean{
		AbstractBean: nikofmt.AbstractBean{Name: strPtr("my-chain")},
		Proxies:      []int64{added[0].ID, added[1].ID},
	}
	chain.InitializeDefaultValues()
	chainAdded, err := u.store.AddProfiles(0, []nikofmt.Bean{chain})
	if err != nil || len(chainAdded) != 1 {
		t.Fatalf("AddProfiles chain: %v", err)
	}

	settings := nikoconf.DesktopSettings()
	settings.YggdrasilEnabled = true
	settings.YggdrasilPeers = "tls://ygg.example.net:62486"
	settings.Secrets.ClashAPI = "my-secret-token"
	settings.EnableClashAPI = true
	if _, err := u.store.UpdateSettings(settings, []string{"yggdrasilEnabled", "yggdrasilPeers", "clashApi", "enableClashAPI"}); err != nil {
		t.Fatalf("UpdateSettings: %v", err)
	}
	loadedSettings, err := u.store.Settings()
	if err != nil {
		t.Fatalf("Settings: %v", err)
	}
	in, err := connectInput(u.store, chainAdded[0].ID, connectSettings(loadedSettings))
	if err != nil {
		t.Fatalf("connectInput(chain+yggdrasil): %v", err)
	}
	if len(in.Settings.Secrets.YggdrasilKey) != 128 {
		t.Fatalf("expected persisted 128-hex Yggdrasil key, got len=%d", len(in.Settings.Secrets.YggdrasilKey))
	}
	res, err := nikoconf.Build(in)
	if err != nil {
		t.Fatalf("nikoconf.Build(chain+yggdrasil+clash): %v", err)
	}
	cfg := string(res.Config)
	if !strings.Contains(cfg, `"type": "yggdrasil"`) || !strings.Contains(cfg, in.Settings.Secrets.YggdrasilKey) {
		t.Fatalf("expected yggdrasil endpoint with valid private_key:\n%s", cfg)
	}
	if !strings.Contains(cfg, `"clash_api"`) || !strings.Contains(cfg, `"my-secret-token"`) {
		t.Fatalf("expected clash_api enabled with secret:\n%s", cfg)
	}
	if !strings.Contains(cfg, `"198.51.100.1"`) || !strings.Contains(cfg, `"198.51.100.2"`) || !strings.Contains(cfg, `"detour"`) {
		t.Fatalf("expected chain hops and detour in config:\n%s", cfg)
	}

	// Empty chain must fail cleanly (H2).
	emptyChain := &nikofmt.ChainBean{AbstractBean: nikofmt.AbstractBean{Name: strPtr("empty-chain")}}
	emptyChain.InitializeDefaultValues()
	_, err = nikoconf.Build(nikoconf.Input{
		Profile:  999,
		Profiles: []nikoconf.Profile{{ID: 999, Group: 1, Bean: emptyChain}},
		Groups:   []nikoconf.Group{{ID: 1}},
		Settings: nikoconf.DesktopSettings(),
		Platform: nikoconf.PlatformLinux,
	})
	if err == nil {
		t.Fatal("expected empty ChainBean Build to return an error")
	}

	// H6: explicit user RaycontrolMasterDnsResolvers must override probed DnsResolvers map.
	masterBean := &nikofmt.VMessBean{
		StandardV2RayBean: nikofmt.StandardV2RayBean{
			AbstractBean: nikofmt.AbstractBean{
				Name:              strPtr("node-masterdns"),
				ServerAddress:     strPtr("198.51.100.10"),
				ServerPort:        intPtr(443),
				DnsTunnel:         strPtr("masterdnsvpn"),
				DnsTunnelDomain:   strPtr("m.example.com"),
				DnsTunnelKey:      strPtr("secret-key"),
				DnsTunnelMethod:   intPtr(3),
				DnsTunnelResolver: strPtr("auto"),
			},
			Uuid:     strPtr("11111111-1111-1111-1111-111111111111"),
			Security: strPtr("none"),
		},
		AlterId: intPtr(-1),
	}
	masterBean.InitializeDefaultValues()
	const nodeID = "0123456789abcdef0123456789abcdef"
	node := nikoray.Node{Host: "198.51.100.10", SPA: nikoray.SPAParams{NodeID: nodeID}, ProfileIDs: map[string]int64{"2#0": 20}}
	overrideSettings := nikoconf.DesktopSettings()
	overrideSettings.RaycontrolMasterDnsResolvers = "203.0.113.53"
	resOver, err := nikoconf.Build(nikoconf.Input{
		Profile:      20,
		Profiles:     []nikoconf.Profile{{ID: 20, Group: 1, Bean: masterBean}},
		Groups:       []nikoconf.Group{{ID: 1}},
		Settings:     overrideSettings,
		Platform:     nikoconf.PlatformLinux,
		Nodes:        []nikoray.Node{node},
		DnsResolvers: map[string]string{"masterdnsvpn:" + nodeID: "195.208.5.1"},
	})
	if err != nil {
		t.Fatalf("masterdns override Build: %v", err)
	}
	if !strings.Contains(string(resOver.Config), "203.0.113.53") || strings.Contains(string(resOver.Config), "195.208.5.1") {
		t.Fatalf("expected user RaycontrolMasterDnsResolvers to override probed map:\n%s", string(resOver.Config))
	}
}

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

	cfg, handled, err := s.BeforeRestore(context.Background(), owner)
	if err != nil {
		t.Fatalf("BeforeRestore: %v", err)
	}
	if !handled || !strings.Contains(cfg, "198.51.100.20") {
		t.Fatalf("unexpected BeforeRestore output: handled=%v len=%d", handled, len(cfg))
	}
}

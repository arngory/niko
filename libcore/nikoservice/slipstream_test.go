package nikoservice

import (
	"encoding/json"
	"strings"
	"testing"

	"libcore/nikoconf"
	"libcore/nikofmt"
	"libcore/nikoray"
)

func TestSlipstreamVariantAConfigBuild(t *testing.T) {
	nodeID := "0123456789abcdef0123456789abcdef"
	node := nikoray.Node{
		Host:       "198.51.100.10",
		DeviceKey:  "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		SPA:        nikoray.SPAParams{NodeID: nodeID},
		ClockSkew:  7,
		ProfileIDs: map[string]int64{"5#0": 50},
	}

	bean := &nikofmt.VMessBean{
		StandardV2RayBean: nikofmt.StandardV2RayBean{
			AbstractBean: nikofmt.AbstractBean{
				Name:              strPtr("node-slipstream"),
				ServerAddress:     strPtr("node.example.net"),
				ServerPort:        intPtr(443),
				DnsTunnel:         strPtr("slipstream"),
				DnsTunnelDomain:   strPtr("s.example.com"),
				DnsTunnelKey:      strPtr("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA"),
				DnsTunnelResolver: strPtr("auto"),
				DnsTunnelAuth:     strPtr("v2;" + nodeID + ";5"),
				DnsTunnelTls:      strPtr("h3;cdn.example.net"),
			},
			Uuid:     strPtr("11111111-1111-1111-1111-111111111111"),
			Security: strPtr("tls"),
		},
		AlterId: intPtr(-1),
	}
	bean.InitializeDefaultValues()

	// Variant A: external slipstream-client with UDP direct mapping inbounds and SLIPSTREAM_DNSAUTH_KEY in env.
	// Even if user sets MasterDNS CIDR resolvers, slipstream "auto" must NOT inherit MasterDNS CIDRs (H6).
	settings := nikoconf.DesktopSettings()
	settings.RaycontrolMasterDnsResolvers = "198.51.100.0/24"
	inA := nikoconf.Input{
		Profile:  50,
		Profiles: []nikoconf.Profile{{ID: 50, Group: 1, Bean: bean}},
		Groups:   []nikoconf.Group{{ID: 1}},
		Settings: settings,
		Platform: nikoconf.PlatformLinux,
		Device:   desktopDevice{},
		Nodes:    []nikoray.Node{node},
	}
	resA, err := nikoconf.Build(inA)
	if err != nil {
		t.Fatalf("Variant A Build failed: %v", err)
	}
	if len(resA.SlipstreamLaunches) != 1 {
		t.Fatalf("expected 1 SlipstreamLaunch, got %d", len(resA.SlipstreamLaunches))
	}
	launch := resA.SlipstreamLaunches[0]
	if launch.LocalPort <= 0 {
		t.Fatalf("invalid LocalPort: %d", launch.LocalPort)
	}
	if key := launch.Env["SLIPSTREAM_DNSAUTH_KEY"]; len(key) != 64 {
		t.Fatalf("SLIPSTREAM_DNSAUTH_KEY len = %d, want 64 hex chars", len(key))
	}
	joinedArgs := strings.Join(launch.Args, " ")
	for _, want := range []string{"--domain=s.example.com", "--pin=AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA", "--alpn=h3", "--sni=cdn.example.net", "--dnsauth", "--clock-offset=7"} {
		if !strings.Contains(joinedArgs, want) {
			t.Fatalf("launch args missing %q: %s", want, joinedArgs)
		}
	}
	var cfg map[string]any
	if err := json.Unmarshal(resA.Config, &cfg); err != nil {
		t.Fatalf("invalid config JSON: %v", err)
	}
	if !strings.Contains(string(resA.Config), `"override_address": "195.208.5.1"`) {
		t.Fatalf("expected UDP mapping inbound for default resolver:\n%s", string(resA.Config))
	}

	// ForTest must refuse slipstream cleanly instead of producing unstarted launches (H4).
	inTest := inA
	inTest.ForTest = true
	if _, err := nikoconf.Build(inTest); err == nil {
		t.Fatal("expected ForTest Build with slipstream to return an error")
	}
}

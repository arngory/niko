package nikoservice

import (
	"encoding/json"
	"strings"
	"testing"

	"libcore/nikoconf"
	"libcore/nikofmt"
	"libcore/nikoray"
)

func TestSlipstreamVariantAAndVariantBConfigBuild(t *testing.T) {
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
	inA := nikoconf.Input{
		Profile:  50,
		Profiles: []nikoconf.Profile{{ID: 50, Group: 1, Bean: bean}},
		Groups:   []nikoconf.Group{{ID: 1}},
		Settings: nikoconf.DesktopSettings(),
		Platform: nikoconf.PlatformLinux,
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
	// Verify JSON config contains UDP direct inbound mappings and hop rewritten to 127.0.0.1.
	var cfg map[string]any
	if err := json.Unmarshal(resA.Config, &cfg); err != nil {
		t.Fatalf("invalid config JSON: %v", err)
	}
	if !strings.Contains(string(resA.Config), `"override_address": "195.208.5.1"`) {
		t.Fatalf("expected UDP mapping inbound for default resolver:\n%s", string(resA.Config))
	}

	// Variant B: native in-core `type: "slipstream"` outbound.
	inB := inA
	inB.SlipstreamNative = true
	resB, err := nikoconf.Build(inB)
	if err != nil {
		t.Fatalf("Variant B Build failed: %v", err)
	}
	if len(resB.SlipstreamLaunches) != 0 {
		t.Fatalf("expected 0 external launches in Variant B, got %d", len(resB.SlipstreamLaunches))
	}
	if !strings.Contains(string(resB.Config), `"type": "slipstream"`) {
		t.Fatalf("expected native slipstream outbound in Variant B config:\n%s", string(resB.Config))
	}
}

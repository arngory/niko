package nikoservice

import (
	"slices"
	"strings"
	"testing"

	"libcore/nikoconf"
	"libcore/nikofmt"
)

func TestYggPeerValidationAndSelection(t *testing.T) {
	if !ValidYggPeerURI("tls://peer.example.net:443?key=" + strings.Repeat("a", 64)) {
		t.Fatal("valid tls peer rejected")
	}
	if ValidYggPeerURI("tls://peer.example.net:443?key=short") {
		t.Fatal("short key should be rejected")
	}
	if ValidYggPeerURI("ftp://peer.example.net:21") {
		t.Fatal("unsupported scheme should be rejected")
	}

	picked := PickYggPeers([]YggPeerCheck{
		{URI: "tcp://fast-plain.example.net:1234", Up: true, LatencyMs: 5},
		{URI: "tls://slow-tls.example.net:443", Up: true, LatencyMs: 80},
		{URI: "quic://fast-quic.example.net:443", Up: true, LatencyMs: 25},
		{URI: "wss://mid-wss.example.net:443", Up: true, LatencyMs: 50},
		{URI: "tls://dead.example.net:443", Up: false, LatencyMs: 1},
		{URI: "tls://extra.example.net:443", Up: true, LatencyMs: 120},
	})
	want := []string{
		"quic://fast-quic.example.net:443",
		"wss://mid-wss.example.net:443",
		"tls://slow-tls.example.net:443",
	}
	if !slices.Equal(picked, want) {
		t.Fatalf("PickYggPeers = %v, want %v", picked, want)
	}
}

func TestShadowTLSAndChainBeanBuild(t *testing.T) {
	stls := &nikofmt.ShadowTLSBean{
		Version:  intPtr(3),
		Password: strPtr("secret"),
		Sni:      strPtr("cdn.example.net"),
		AbstractBean: nikofmt.AbstractBean{
			Name:          strPtr("stls"),
			ServerAddress: strPtr("198.51.100.30"),
			ServerPort:    intPtr(443),
		},
	}
	stls.InitializeDefaultValues()
	kind, tr := nikofmt.Kind(stls)
	if kind != "shadowtls" || tr != "tcp" {
		t.Fatalf("Kind(ShadowTLSBean) = (%q, %q)", kind, tr)
	}
	out, err := nikoconf.Outbound(stls, false)
	if err != nil {
		t.Fatalf("Outbound(ShadowTLSBean): %v", err)
	}
	if out["type"] != "shadowtls" || out["version"] != int64(3) {
		t.Fatalf("unexpected shadowtls outbound: %+v", out)
	}
}

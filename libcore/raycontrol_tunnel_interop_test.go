package libcore

import (
	"context"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"net"
	"net/url"
	"os"
	"slices"
	"strconv"
	"strings"
	"testing"
	"time"

	"libcore/knock"
	"libcore/rayprobe"
	"libcore/raysub"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter/certificate"
	"github.com/sagernet/sing-box/option"
)

// TestRaycontrolTunnelInterop: a node's hy2-obfs as rayd renders it (a niko-server backend behind its front), run
// by raycontrol-interop.yml. The client builds the profile from the subscription's link, measures the channel
// through the tunnel at the probe name (probe-v1.md §1.1) with RaycontrolProbeTunnel, and asks link directly while
// the tunnel is up: the node lists the device's own Hysteria2 session (own, niko-server v0.7) with the rate the
// client declared, the field the app's Brutal watches. RAYCONTROL_BUNDLE is the device's rcdev1 package,
// RAYCONTROL_ADDRESS and RAYCONTROL_FRONT_PORT where the front listens (the runner's own address, not loopback).
func TestRaycontrolTunnelInterop(t *testing.T) {
	path := os.Getenv("RAYCONTROL_BUNDLE")
	if path == "" {
		t.Skip("RAYCONTROL_BUNDLE not set (raycontrol-interop.yml sets it)")
	}
	frontPort, err := strconv.Atoi(os.Getenv("RAYCONTROL_FRONT_PORT"))
	if err != nil {
		t.Fatal("RAYCONTROL_FRONT_PORT: ", err)
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	body, err := base64.RawURLEncoding.DecodeString(strings.TrimPrefix(strings.TrimSpace(string(raw)), "rcdev1:"))
	if err != nil {
		t.Fatal("package: ", err)
	}
	var b struct {
		Host      string             `json:"host"`
		DeviceID  string             `json:"device_id"`
		DeviceKey string             `json:"device_key"`
		SPA       knock.SPAv2Options `json:"spa"`
		Sub       struct {
			Port int    `json:"port"`
			Pin  string `json:"pin"`
		} `json:"sub"`
		NodeKeys struct {
			Ed25519 string `json:"ed25519"`
			MLDSA65 string `json:"mldsa65"`
		} `json:"node_keys"`
	}
	if err = json.Unmarshal(body, &b); err != nil || b.Sub.Port == 0 {
		t.Fatalf("package: %v", err)
	}
	b.SPA.DeviceKey = b.DeviceKey
	deviceKey, err := hex.DecodeString(b.DeviceKey)
	if err != nil {
		t.Fatal(err)
	}
	// the address the front listens on, which the node sees the client come from
	own := os.Getenv("RAYCONTROL_ADDRESS")
	if own == "" {
		own = runnerAddress(t)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()

	// the subscription, asked from the runner's address as the app asks it
	result, err := raysub.Fetch(ctx, raysub.Request{
		Host: own, Port: b.Sub.Port, Pin: b.Sub.Pin, DeviceID: b.DeviceID, NodeID: b.SPA.NodeID,
		Ed25519: b.NodeKeys.Ed25519, MLDSA65: b.NodeKeys.MLDSA65, SPA: b.SPA,
	})
	if err != nil {
		t.Fatalf("subscription: %v", err)
	}
	probeInfo := result.Payload.Probe
	if probeInfo == nil || probeInfo.Tunnel == "" {
		t.Fatalf("subscription without probe: %+v", result.Payload)
	}
	var link string
	var svc uint16
	for _, p := range result.Payload.Profiles {
		if strings.HasPrefix(p.Link, "hysteria2://") && slices.Contains(probeInfo.TunnelSvc, p.Svc) {
			link, svc = p.Link, p.Svc
		}
	}
	if link == "" {
		t.Fatalf("no Hysteria2 profile among the probe's tunnels %v", probeInfo.TunnelSvc)
	}

	// the profile as the app builds a raycontrol hy2-obfs link: the key pin (niko-pin) instead of a CA,
	// X25519MLKEM768 only, salamander; Brutal at a declared download rate
	u, err := url.Parse(link)
	if err != nil {
		t.Fatal(err)
	}
	q := u.Query()
	pin, err := base64.RawURLEncoding.DecodeString(strings.TrimRight(q.Get("niko-pin"), "="))
	if err != nil || len(pin) != 32 {
		t.Fatalf("niko-pin %q: %v", q.Get("niko-pin"), err)
	}
	const downMbps = 50
	outbound := map[string]any{
		"type": "hysteria2", "tag": "proxy", "server": own, "server_port": frontPort, "password": u.User.Username(),
		"up_mbps": 20, "down_mbps": downMbps,
		"obfs": map[string]any{"type": q.Get("obfs"), "password": q.Get("obfs-password")},
		"tls": map[string]any{
			"enabled": true, "server_name": q.Get("sni"), "alpn": []string{"h3"},
			"curve_preferences":             []string{"X25519MLKEM768"},
			"certificate_public_key_sha256": []string{base64.StdEncoding.EncodeToString(pin)},
		},
	}
	config, _ := json.Marshal(map[string]any{
		"log":       map[string]any{"level": "warn"},
		"outbounds": []any{outbound},
		"route":     map[string]any{"final": "proxy"},
	})
	instance := startTestBox(t, string(config))

	// through the tunnel: down with the server's trailer and the UDP echo; link and the summary are dropped
	// there (directly only)
	request, _ := json.Marshal(probeRequest{
		Host: b.Host, Port: b.Sub.Port, Pin: b.Sub.Pin, DeviceID: b.DeviceID, SPA: b.SPA, Tunnel: probeInfo.Tunnel,
		DownBytes: 1 << 20, EchoCount: 20, EchoSize: 1200, Link: true,
		Report: json.RawMessage(`{"v":1,"net":"wifi","obs":[]}`),
	})
	out, err := RaycontrolProbeTunnel(instance, string(request))
	if err != nil {
		t.Fatalf("probe through the tunnel: %v", err)
	}
	var r probeResult
	if err = json.Unmarshal([]byte(out), &r); err != nil {
		t.Fatal(err)
	}
	if r.Down == nil || !r.Down.OK || r.Down.Bytes != 1<<20 || r.Down.Server["bytes"] != float64(1<<20) {
		t.Fatalf("down through the tunnel: %s", out)
	}
	if r.Echo == nil || !r.Echo.OK || r.Echo.Received == 0 {
		t.Fatalf("echo through the tunnel: %s", out)
	}
	if r.Link != nil || r.LinkError != "" || r.ReportStatus != 0 || r.ReportError != "" {
		t.Fatalf("link or the summary went through the tunnel: %s", out)
	}
	t.Logf("through the tunnel (svc %d): down %d kbit/s, server %v; %s", svc, r.Down.Kbps, r.Down.Server, r.Echo)

	// directly, while the tunnel is up: the device's own session on the service, with the declared rate
	pc := rayprobe.NewClient(&rayprobe.Endpoint{
		Address: net.JoinHostPort(own, strconv.Itoa(b.Sub.Port)), Pin: b.Sub.Pin,
		DeviceID: b.DeviceID, DeviceKey: deviceKey, Dial: (&net.Dialer{}).DialContext,
	})
	defer pc.Close()
	rawLink, err := pc.Link(ctx)
	if err != nil {
		t.Fatalf("link: %v", err)
	}
	var view struct {
		Hysteria2 map[string]struct {
			Connections int `json:"connections"`
			Own         []struct {
				Brutal *struct {
					RateBps float64  `json:"rate_bps"`
					AckRate *float64 `json:"ack_rate"`
				} `json:"brutal"`
			} `json:"own"`
		} `json:"hysteria2"`
	}
	if err = json.Unmarshal(rawLink, &view); err != nil {
		t.Fatalf("link: %s: %v", rawLink, err)
	}
	service, ok := view.Hysteria2[strconv.Itoa(int(svc))]
	if !ok || len(service.Own) == 0 {
		t.Fatalf("link without the device's own session on %d: %s", svc, rawLink)
	}
	// the app picks its session by the rate it declared and reads its ack rate (RayBrutal.ackRate); the ack rate
	// covers the last seconds and may be absent on an idle session
	found := false
	for _, s := range service.Own {
		if s.Brutal != nil && s.Brutal.RateBps == downMbps*1e6 {
			found = true
			if s.Brutal.AckRate == nil {
				t.Logf("own session at %d Mbit/s without an ack rate (idle)", downMbps)
			}
		}
	}
	if !found {
		t.Fatalf("no own session at the declared %d Mbit/s: %s", downMbps, rawLink)
	}
	t.Logf("link: %s", rawLink)
}

// runnerAddress: an IPv4 address of this machine besides loopback; the node sees requests to it as coming from it,
// so link and the summary take them as direct.
func runnerAddress(t *testing.T) string {
	t.Helper()
	addrs, _ := net.InterfaceAddrs()
	for _, a := range addrs {
		if ip, ok := a.(*net.IPNet); ok && ip.IP.To4() != nil && !ip.IP.IsLoopback() {
			return ip.IP.String()
		}
	}
	t.Fatal("no address besides loopback")
	return ""
}

// startTestBox runs a core with libcore's protocols, without the Android platform (protect, interface monitor).
func startTestBox(t *testing.T, config string) *BoxInstance {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	ctx = box.Context(ctx,
		nekoboxAndroidInboundRegistry(), nekoboxAndroidOutboundRegistry(), nekoboxAndroidEndpointRegistry(),
		nekoboxAndroidDNSTransportRegistry(nil), nekoboxAndroidServiceRegistry(), certificate.NewRegistry(),
	)
	var options option.Options
	if err := options.UnmarshalJSONContext(ctx, []byte(config)); err != nil {
		cancel()
		t.Fatalf("config: %v", err)
	}
	instance, err := box.New(box.Options{Options: options, Context: ctx})
	if err != nil {
		cancel()
		t.Fatalf("core: %v", err)
	}
	if err = instance.Start(); err != nil {
		cancel()
		t.Fatalf("core start: %v", err)
	}
	t.Cleanup(func() {
		instance.Close()
		cancel()
	})
	return &BoxInstance{Box: instance, cancel: cancel}
}

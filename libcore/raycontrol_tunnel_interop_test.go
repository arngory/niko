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

// TestRaycontrolTunnelInterop: a node under rayd serve -supervise, the data plane as a node runs it (Xray for REALITY
// and inner, niko-server for Hysteria2, Yggdrasil and the front), run by raycontrol-interop.yml. The client takes
// the subscription, builds each profile of the probe's tunnels from its link as the app does and measures the
// channel through it at the probe name (probe-v1.md §1.1) with RaycontrolProbeTunnel. Then link, asked directly
// while the tunnels are up, lists the device's own Hysteria2 session (own, niko-server v0.7) with the rate the client
// declared, the field the app's Brutal watches.
//
// RAYCONTROL_BUNDLE is the device's rcdev1 package, RAYCONTROL_ADDRESS the node's address (not loopback: the node
// takes the client's address for link), RAYCONTROL_CA the test CA of the node's site (hy2-masq), RAYCONTROL_EXPECT
// the kinds of profiles that must be among them (hysteria2-pin, hysteria2-ca, reality, yggdrasil).
func TestRaycontrolTunnelInterop(t *testing.T) {
	path := os.Getenv("RAYCONTROL_BUNDLE")
	if path == "" {
		t.Skip("RAYCONTROL_BUNDLE not set (raycontrol-interop.yml sets it)")
	}
	node := os.Getenv("RAYCONTROL_ADDRESS")
	if node == "" {
		t.Fatal("RAYCONTROL_ADDRESS not set")
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
	ctx, cancel := context.WithTimeout(context.Background(), 3*time.Minute)
	defer cancel()

	result, err := raysub.Fetch(ctx, raysub.Request{
		Host: node, Port: b.Sub.Port, Pin: b.Sub.Pin, DeviceID: b.DeviceID, NodeID: b.SPA.NodeID,
		Ed25519: b.NodeKeys.Ed25519, MLDSA65: b.NodeKeys.MLDSA65, SPA: b.SPA,
	})
	if err != nil {
		t.Fatalf("subscription: %v", err)
	}
	probeInfo := result.Payload.Probe
	if probeInfo == nil || probeInfo.Tunnel == "" {
		t.Fatalf("subscription without probe: %+v", result.Payload)
	}

	const downMbps = 50
	tested := map[string]bool{}
	var hysteriaSvc []uint16
	for _, p := range result.Payload.Profiles {
		if !slices.Contains(probeInfo.TunnelSvc, p.Svc) {
			continue
		}
		kind, config := profileConfig(t, p.Link, node, os.Getenv("RAYCONTROL_CA"), b.DeviceKey, downMbps)
		if config == "" {
			t.Logf("svc %d: %s not measured here", p.Svc, kind)
			continue
		}
		instance := startTestBox(t, config)
		// the first link of a mesh node or a QUIC handshake takes a moment: a small probe until one answers, 90 s
		// at most for the profile
		warm, _ := json.Marshal(probeRequest{Host: b.Host, Port: b.Sub.Port, Pin: b.Sub.Pin, DeviceID: b.DeviceID, SPA: b.SPA,
			Tunnel: probeInfo.Tunnel, DownBytes: 1024, WindowMs: 5000})
		for deadline := time.Now().Add(90 * time.Second); ; {
			out, err := RaycontrolProbeTunnel(instance, string(warm))
			var r probeResult
			if err == nil && json.Unmarshal([]byte(out), &r) == nil && r.Down != nil && r.Down.OK {
				break
			}
			if time.Now().After(deadline) {
				t.Fatalf("%s %s (svc %d): no answer through the tunnel in 90 s: %s %v", kind, p.Name, p.Svc, out, err)
			}
			time.Sleep(3 * time.Second)
		}
		// a DNS tunnel carries kilobytes, not megabytes, in a window
		down := int64(1 << 20)
		if strings.HasPrefix(kind, "dnstt") || kind == "masterdns" {
			down = 64 << 10
		}
		probeThrough(t, kind+" "+p.Name, instance, b.Host, b.Sub.Port, b.Sub.Pin, b.DeviceID, b.SPA, probeInfo.Tunnel, p.Svc, down)
		tested[kind] = true
		if strings.HasPrefix(kind, "hysteria2") {
			hysteriaSvc = append(hysteriaSvc, p.Svc)
		}
	}
	// relay-v1: the subscription and the profiles with via through the relay in front of the node
	if relay := os.Getenv("RAYCONTROL_RELAY"); relay != "" {
		relayThrough(t, relay, result.Payload, b.Host, b.Sub.Port, b.Sub.Pin, b.DeviceID, b.DeviceKey, b.SPA,
			b.NodeKeys.Ed25519, b.NodeKeys.MLDSA65, node, probeInfo, downMbps, tested)
	}
	// the summary with its path (relay-v1 §9.3, raycontrol v0.10.2): to the node directly only path direct; a relay
	// network's summary goes through a relay, the node refuses it here (400)
	if slices.Contains(probeInfo.Report, "path") {
		rc := rayprobe.NewClient(&rayprobe.Endpoint{
			Address: net.JoinHostPort(node, strconv.Itoa(b.Sub.Port)), Pin: b.Sub.Pin,
			DeviceID: b.DeviceID, DeviceKey: deviceKey, Dial: (&net.Dialer{}).DialContext,
		})
		reportCtx, reportCancel := context.WithTimeout(context.Background(), 30*time.Second)
		svc := strconv.Itoa(int(result.Payload.Profiles[0].Svc))
		summary := func(path string) []byte {
			return []byte(`{"v":1,"net":"wifi","path":"` + path + `","obs":[{"svc":` + svc + `,"result":"works"}]}`)
		}
		if err := rc.Report(reportCtx, summary("relay")); err == nil {
			t.Fatal("the node took a relay path summary sent to it directly")
		} else if status, ok := err.(*rayprobe.StatusError); !ok || status.Status != 400 {
			t.Fatalf("relay path summary directly: want 400, got %v", err)
		}
		// once a day per device and network for both paths: after the relay's summary the node says 429
		if err := rc.Report(reportCtx, summary("direct")); err != nil {
			if status, ok := err.(*rayprobe.StatusError); !ok || status.Status != 429 || !tested["relay-report"] {
				t.Fatalf("direct path summary: %v", err)
			}
		}
		reportCancel()
		rc.Close()
		tested["report-path"] = true
	}
	for _, kind := range strings.Split(os.Getenv("RAYCONTROL_EXPECT"), ",") {
		if kind != "" && !tested[kind] {
			t.Fatalf("no %s profile measured (tested %v, tunnels %v)", kind, tested, probeInfo.TunnelSvc)
		}
	}
	if len(hysteriaSvc) == 0 {
		return
	}

	// directly, while the tunnels are up: the device's own session on each Hysteria2 service, at the declared rate
	pc := rayprobe.NewClient(&rayprobe.Endpoint{
		Address: net.JoinHostPort(node, strconv.Itoa(b.Sub.Port)), Pin: b.Sub.Pin,
		DeviceID: b.DeviceID, DeviceKey: deviceKey, Dial: (&net.Dialer{}).DialContext,
	})
	defer pc.Close()
	// a context of its own: the tunnels' probes before may have taken minutes
	linkCtx, linkCancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer linkCancel()
	rawLink, err := pc.Link(linkCtx)
	if err != nil {
		t.Fatalf("link: %v", err)
	}
	var view struct {
		Hysteria2 map[string]struct {
			Own []struct {
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
	for _, svc := range hysteriaSvc {
		// the app picks its session by the rate it declared and reads its ack rate (RayBrutal.ackRate); the ack rate
		// covers the last seconds and may be absent on an idle session
		found := false
		for _, s := range view.Hysteria2[strconv.Itoa(int(svc))].Own {
			if s.Brutal != nil && s.Brutal.RateBps == downMbps*1e6 {
				found = true
				if s.Brutal.AckRate == nil {
					t.Logf("svc %d: own session without an ack rate (idle)", svc)
				}
			}
		}
		if !found {
			t.Fatalf("svc %d: no own session at the declared %d Mbit/s: %s", svc, downMbps, rawLink)
		}
	}
	t.Logf("link: %s", rawLink)
}

// relayThrough asks the subscription through the relay (its SPA with K_relay, the node's pin and signatures) and
// measures each profile whose via names the relay at the relay's address, as the app builds such a row: the link
// with the relay's host, Hysteria2 on UDP 443, a knock outbound with the relay's SPA in front.
func relayThrough(t *testing.T, relayAddr string, payload raysub.Payload, host string, subPort int,
	pin, deviceID, deviceKey string, spa knock.SPAv2Options, ed25519, mldsa65, node string, probeInfo *raysub.ProbeInfo,
	downMbps int, tested map[string]bool) {
	t.Helper()
	// the relay's address as the subscription gives it: the app dials relays[].host, not an address of its own
	i := slices.IndexFunc(payload.Relays, func(r raysub.Relay) bool { return r.Host == relayAddr })
	if i < 0 {
		t.Fatalf("no relay at %s in the subscription: %+v", relayAddr, payload.Relays)
	}
	relay := payload.Relays[i]
	relaySPA := relay.SPA
	relaySPA.DeviceKey = deviceKey
	relaySPA.Relay = &knock.SPARelay{ExitNodeID: spa.NodeID, ID: relay.ID}

	// a context of its own: the tunnels measured before may have taken minutes
	ctx, cancel := context.WithTimeout(context.Background(), time.Minute)
	defer cancel()
	viaSub, err := raysub.Fetch(ctx, raysub.Request{
		Host: relay.Host, Port: subPort, Pin: pin, DeviceID: deviceID, NodeID: spa.NodeID,
		Ed25519: ed25519, MLDSA65: mldsa65, SPA: relaySPA,
	})
	if err != nil {
		t.Fatalf("subscription through relay %s: %v", relay.ID, err)
	}
	if viaSub.Payload.NodeID != payload.NodeID {
		t.Fatalf("subscription through the relay: node %s, want %s", viaSub.Payload.NodeID, payload.NodeID)
	}
	tested["relay-subscription"] = true

	// the exit lists the relay's probe while the relay's feed is up (relay-v1 §9.2): a fresh subscription until it
	// does, then a clear failure rather than a skipped step
	if relay.Probe == nil && slices.Contains(strings.Split(os.Getenv("RAYCONTROL_EXPECT"), ","), "relay-probe") {
		waitCtx, waitCancel := context.WithTimeout(context.Background(), 90*time.Second)
		defer waitCancel()
		for deadline := time.Now().Add(60 * time.Second); relay.Probe == nil && time.Now().Before(deadline); {
			time.Sleep(3 * time.Second)
			again, err := raysub.Fetch(waitCtx, raysub.Request{
				Host: relay.Host, Port: subPort, Pin: pin, DeviceID: deviceID, NodeID: spa.NodeID,
				Ed25519: ed25519, MLDSA65: mldsa65, SPA: relaySPA,
			})
			if err != nil {
				continue
			}
			viaSub = again
			if j := slices.IndexFunc(again.Payload.Relays, func(r raysub.Relay) bool { return r.ID == relay.ID }); j >= 0 {
				relay.Probe = again.Payload.Relays[j].Probe
			}
		}
		if relay.Probe == nil {
			t.Fatalf("relay %s: the subscription lists no probe for it (rayd relay's feed not up?): %+v", relay.ID, viaSub.Payload.Relays)
		}
	}

	// R6-c: the relay's own probe endpoint, as the app measures it (RaycontrolProbe with the relay's SPA: K_relay for
	// the knock and for RC1), and the network's summary through it with path relay
	if p := relay.Probe; p != nil && p.V == 1 {
		request := probeRequest{Host: relay.Host, Port: p.Port, Pin: p.Pin, DeviceID: deviceID, SPA: relaySPA,
			DownBytes: 1 << 20, WindowMs: 30000}
		if p.UDP {
			request.EchoCount, request.EchoSize = 20, 1200
		}
		if p.Report {
			svc := strconv.Itoa(int(payload.Profiles[0].Svc))
			request.Report = json.RawMessage(`{"v":1,"net":"wifi","path":"relay","obs":[{"svc":` + svc + `,"result":"works"}]}`)
		}
		raw, _ := json.Marshal(request)
		out, err := RaycontrolProbe(string(raw))
		if err != nil {
			t.Fatalf("relay %s probe: %v", relay.ID, err)
		}
		var r probeResult
		if err = json.Unmarshal([]byte(out), &r); err != nil {
			t.Fatal(err)
		}
		if r.Down == nil || !r.Down.OK || r.Down.Bytes != 1<<20 {
			t.Fatalf("relay %s probe: down %s", relay.ID, out)
		}
		if p.UDP && (r.Echo == nil || !r.Echo.OK || r.Echo.Received == 0) {
			t.Fatalf("relay %s probe: echo %s", relay.ID, out)
		}
		t.Logf("relay %s probe: down %d kbit/s over %s, echo %+v", relay.ID, r.Down.Kbps, r.SPACarrier, r.Echo)
		tested["relay-probe"] = true
		if p.Report {
			if r.ReportStatus != 204 && r.ReportStatus != 200 {
				t.Fatalf("relay %s: summary through the relay: %d %s", relay.ID, r.ReportStatus, r.ReportError)
			}
			tested["relay-report"] = true
		}
	}

	for _, p := range payload.Profiles {
		if !slices.Contains(p.Via, relay.ID) || !slices.Contains(probeInfo.TunnelSvc, p.Svc) {
			continue
		}
		u, err := url.Parse(p.Link)
		if err != nil {
			t.Fatal(err)
		}
		// as RayRelays.viaLink: the relay's host, Hysteria2 on UDP 443 without hopping
		port := u.Port()
		if u.Scheme == "hysteria2" || u.Scheme == "hy2" {
			port = "443"
			q := u.Query()
			q.Del("mport")
			u.RawQuery = q.Encode()
		}
		u.Host = net.JoinHostPort(relay.Host, port)
		kind, config := profileConfig(t, u.String(), node, os.Getenv("RAYCONTROL_CA"), deviceKey, downMbps)
		// a relay forwards the node's 443: REALITY and Hysteria2; a tunnel or the mesh has a path of its own
		if config == "" || (kind != "reality" && !strings.HasPrefix(kind, "hysteria2")) {
			t.Logf("relay %s: svc %d (%s) has via but is not measured through a relay", relay.ID, p.Svc, kind)
			continue
		}
		var c map[string]any
		if err = json.Unmarshal([]byte(config), &c); err != nil {
			t.Fatal(err)
		}
		outbounds := c["outbounds"].([]any)
		outbounds[0].(map[string]any)["detour"] = "relay-knock"
		c["outbounds"] = append(outbounds, map[string]any{"type": knock.TypeKnock, "tag": "relay-knock", "spa_v2": relaySPA})
		raw, _ := json.Marshal(c)
		instance := startTestBox(t, string(raw))
		warm, _ := json.Marshal(probeRequest{Host: host, Port: subPort, Pin: pin, DeviceID: deviceID, SPA: spa,
			Tunnel: probeInfo.Tunnel, DownBytes: 1024, WindowMs: 5000})
		for deadline := time.Now().Add(90 * time.Second); ; {
			out, err := RaycontrolProbeTunnel(instance, string(warm))
			var r probeResult
			if err == nil && json.Unmarshal([]byte(out), &r) == nil && r.Down != nil && r.Down.OK {
				break
			}
			if time.Now().After(deadline) {
				t.Fatalf("relay %s, %s %s (svc %d): no answer through the relay in 90 s: %s %v", relay.ID, kind, p.Name, p.Svc, out, err)
			}
			time.Sleep(3 * time.Second)
		}
		probeThrough(t, "relay "+kind+" "+p.Name, instance, host, subPort, pin, deviceID, spa, probeInfo.Tunnel, p.Svc, 1<<20)
		tested["relay-"+kind] = true
	}
}

// profileConfig is a core config for one profile of the subscription, built from its link as the app builds a
// raycontrol profile: Hysteria2 with the key pin (niko-pin) or the site's certificate, X25519MLKEM768 only, Brutal
// at downMbps; VLESS over REALITY with Vision, ML-KEM-768 encryption and the ML-DSA-65 key; Yggdrasil as the device's
// own mesh node with the node as its only peer and VLESS to the node's mesh address. The empty config for a kind not
// measured here (DNS tunnels, naive: a plugin, not the core). The node's names do not resolve here: a peer's host is
// the node's address.
func profileConfig(t *testing.T, link, node, ca, deviceKey string, downMbps int) (kind, config string) {
	t.Helper()
	u, err := url.Parse(link)
	if err != nil {
		t.Fatal(err)
	}
	q := u.Query()
	port, _ := strconv.Atoi(u.Port())
	var endpoints, extra []any
	var outbound map[string]any
	switch {
	case u.Scheme == "hysteria2":
		// hy2-obfs is pinned by its key, hy2-masq verified against the site's CA: two paths of the app
		kind = "hysteria2-ca"
		tls := map[string]any{
			"enabled": true, "server_name": q.Get("sni"), "alpn": []string{"h3"},
			"curve_preferences": []string{"X25519MLKEM768"},
		}
		if pin := q.Get("niko-pin"); pin != "" {
			sum, err := base64.RawURLEncoding.DecodeString(strings.TrimRight(pin, "="))
			if err != nil || len(sum) != 32 {
				t.Fatalf("niko-pin %q: %v", pin, err)
			}
			tls["certificate_public_key_sha256"] = []string{base64.StdEncoding.EncodeToString(sum)}
			kind = "hysteria2-pin"
		} else if ca != "" {
			tls["certificate_path"] = ca
		}
		outbound = map[string]any{
			"type": "hysteria2", "server": u.Hostname(), "server_port": port, "password": u.User.Username(),
			"up_mbps": 20, "down_mbps": downMbps, "tls": tls,
		}
		if q.Get("obfs") != "" {
			outbound["obfs"] = map[string]any{"type": q.Get("obfs"), "password": q.Get("obfs-password")}
		}
	case u.Scheme == "vless" && q.Get("security") == "reality":
		kind = "reality"
		outbound = map[string]any{
			"type": "vless", "server": u.Hostname(), "server_port": port, "uuid": u.User.Username(),
			"flow": q.Get("flow"), "encryption": q.Get("encryption"), "packet_encoding": "",
			"tls": map[string]any{
				"enabled": true, "server_name": q.Get("sni"),
				// only our uTLS Chrome carries the X25519MLKEM768 share the node wants
				"utls": map[string]any{"enabled": true, "fingerprint": "chrome"},
				"reality": map[string]any{
					"enabled": true, "public_key": q.Get("pbk"), "short_id": q.Get("sid"),
					"mldsa65_verify": q.Get("pqv"), "key_share": "hybrid",
				},
			},
		}
	case u.Scheme == "vless" && q.Get("niko-dns") != "":
		// a DNS tunnel to the inner stage, as the app builds a raycontrol niko-dns profile: the resolver here is the
		// node's own UDP 53 (the client picks one when the link leaves it empty)
		spec := strings.Split(q.Get("niko-dns"), ";")
		if len(spec) != 4 {
			t.Fatalf("niko-dns %q", q.Get("niko-dns"))
		}
		var tunnel map[string]any
		switch spec[0] {
		case "dnstt":
			kind = "dnstt"
			tunnel = map[string]any{
				"type": "dnstt", "domain": spec[2], "public_key": strings.ToLower(spec[3]),
				"resolver": "udp://" + net.JoinHostPort(node, "53"),
			}
			// dnsauth-v1 (raycontrol v0.10.0, the node's dnstt with auth=required): queries signed by the device
			if q.Get("niko-dnsauth") == "v1" {
				kind = "dnstt-auth"
				svc, _ := strconv.Atoi(q.Get("niko-svc"))
				tunnel["auth"] = map[string]any{"node_id": q.Get("niko-node"), "service_id": svc, "device_key": deviceKey}
			}
		case "masterdns":
			kind = "masterdns"
			method, _ := strconv.Atoi(spec[3])
			tunnel = map[string]any{
				"type": "masterdnsvpn", "domains": []string{spec[2]}, "resolvers": []string{net.JoinHostPort(node, "53")},
				"encryption_method": method, "encryption_key": q.Get("niko-dnskey"), "mode": "tcp",
			}
		default:
			return spec[0], ""
		}
		tunnel["tag"] = "tunnel"
		extra = append(extra, tunnel)
		outbound = map[string]any{
			"type": "vless", "server": u.Hostname(), "server_port": port, "uuid": u.User.Username(),
			"encryption": q.Get("encryption"), "detour": "tunnel",
		}
	case u.Scheme == "vless" && q.Get("niko-ygg") != "":
		kind = "yggdrasil"
		spec := strings.SplitN(q.Get("niko-ygg"), ";", 2)
		if len(spec) != 2 {
			t.Fatalf("niko-ygg %q", q.Get("niko-ygg"))
		}
		peer, err := url.Parse(spec[0])
		if err != nil {
			t.Fatal(err)
		}
		peer.Host = net.JoinHostPort(node, peer.Port())
		endpoints = []any{map[string]any{
			"type": "yggdrasil", "tag": "ygg", "private_key": spec[1], "node_info_privacy": true,
			"peers": []string{peer.String()},
		}}
		outbound = map[string]any{
			"type": "vless", "server": u.Hostname(), "server_port": port, "uuid": u.User.Username(),
			"encryption": q.Get("encryption"), "detour": "ygg",
		}
	default:
		return u.Scheme, ""
	}
	outbound["tag"] = "proxy"
	c := map[string]any{
		"log":       map[string]any{"level": "warn"},
		"outbounds": append([]any{outbound}, extra...),
		"route":     map[string]any{"final": "proxy"},
	}
	if endpoints != nil {
		c["endpoints"] = endpoints
	}
	raw, _ := json.Marshal(c)
	return kind, string(raw)
}

// probeThrough measures the channel through instance's tunnel at the probe name as the app does
// (RaycontrolProbeTunnel): down with the server's trailer and the UDP echo; link and the summary are dropped there
// (directly only).
func probeThrough(t *testing.T, name string, instance *BoxInstance, host string, port int, pin, deviceID string,
	spa knock.SPAv2Options, tunnel string, svc uint16, down int64) {
	t.Helper()
	request, _ := json.Marshal(probeRequest{
		Host: host, Port: port, Pin: pin, DeviceID: deviceID, SPA: spa, Tunnel: tunnel,
		DownBytes: down, EchoCount: 20, EchoSize: 1200, Link: true, WindowMs: 30000,
		Report: json.RawMessage(`{"v":1,"net":"wifi","obs":[]}`),
	})
	out, err := RaycontrolProbeTunnel(instance, string(request))
	if err != nil {
		t.Fatalf("%s: probe through the tunnel: %v", name, err)
	}
	var r probeResult
	if err = json.Unmarshal([]byte(out), &r); err != nil {
		t.Fatal(err)
	}
	if r.Down == nil || !r.Down.OK || r.Down.Bytes != down || r.Down.Server["bytes"] != float64(down) {
		t.Fatalf("%s: down through the tunnel: %s", name, out)
	}
	if r.Echo == nil || !r.Echo.OK || r.Echo.Received == 0 {
		t.Fatalf("%s: echo through the tunnel: %s", name, out)
	}
	if r.Link != nil || r.LinkError != "" || r.ReportStatus != 0 || r.ReportError != "" {
		t.Fatalf("%s: link or the summary went through the tunnel: %s", name, out)
	}
	t.Logf("%s (svc %d): down %d kbit/s, server %v; %s", name, svc, r.Down.Kbps, r.Down.Server, r.Echo)
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
	return &BoxInstance{Box: instance, ctx: ctx, cancel: cancel}
}

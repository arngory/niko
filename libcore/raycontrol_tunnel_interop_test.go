package libcore

import (
	"bytes"
	"cmp"
	"context"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"net"
	"net/url"
	"os"
	"os/exec"
	"slices"
	"strconv"
	"strings"
	"testing"
	"time"

	"libcore/dnsprobe"
	"libcore/knock"
	"libcore/rayprobe"
	"libcore/raysub"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/adapter/certificate"
	"github.com/sagernet/sing-box/option"
	dnsttTransport "github.com/sagernet/sing-box/transport/dnstt"
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
	// the subscription as the app takes it: RaycontrolFetchSubscription's JSON, which the app reads (a struct of the
	// core once dropped fields the app needs on the way); the core's own steps below read its struct
	request, _ := json.Marshal(raysub.Request{
		Host: node, Port: b.Sub.Port, Pin: b.Sub.Pin, DeviceID: b.DeviceID, NodeID: b.SPA.NodeID,
		Ed25519: b.NodeKeys.Ed25519, MLDSA65: b.NodeKeys.MLDSA65, SPA: b.SPA,
	})
	appJSON, err := RaycontrolFetchSubscription(string(request))
	if err != nil {
		t.Fatalf("subscription: %v", err)
	}
	var result raysub.Result
	if err = json.Unmarshal([]byte(appJSON), &result); err != nil {
		t.Fatalf("subscription JSON: %v", err)
	}
	announced := appPayload(t, appJSON)
	// dnsauth-v2 (raycontrol v0.15): the node's niko-server checks the hint; the tunnels sign by it, as the app does
	if announced.dnsAuthV2 {
		interopAuthVersion = 2
	}
	t.Logf("subscription: dnsauth v%d, the node's name %q", interopAuthVersion, announced.name)
	if os.Getenv("RAYCONTROL_DNSAUTH_V2") != "" && !announced.dnsAuthV2 {
		t.Fatal("the node takes dnsauth-v2 only (auth_hint=required), its subscription does not announce it")
	}
	if want := os.Getenv("RAYCONTROL_NODE_NAME"); want != "" && announced.name != want {
		t.Fatalf("the node's name in the subscription: %q, want %q", announced.name, want)
	}
	probeInfo := result.Payload.Probe
	if probeInfo == nil || probeInfo.Tunnel == "" {
		t.Fatalf("subscription without probe: %+v", result.Payload)
	}

	const downMbps = 50
	tested := map[string]bool{}
	var hysteriaSvc []uint16
	// RAYCONTROL_ONLY: a later step on the same node (a service changed by rayctl) measures only these kinds, and
	// no more than that (the relay, the summaries, dnsprobe ran in the first)
	var only map[string]bool
	if list := os.Getenv("RAYCONTROL_ONLY"); list != "" {
		only = map[string]bool{}
		for _, kind := range strings.Split(list, ",") {
			only[kind] = true
		}
	}
	for _, p := range result.Payload.Profiles {
		if !slices.Contains(probeInfo.TunnelSvc, p.Svc) {
			continue
		}
		kind, config := profileConfig(t, p.Link, node, "", os.Getenv("RAYCONTROL_CA"), b.DeviceKey, downMbps)
		if only != nil && !only[kind] {
			t.Logf("svc %d: %s skipped (RAYCONTROL_ONLY)", p.Svc, kind)
			continue
		}
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
		if strings.HasPrefix(kind, "dnstt") || strings.HasPrefix(kind, "masterdns") || strings.HasPrefix(kind, "slipstream") {
			down = 64 << 10
		}
		probeThrough(t, kind+" "+p.Name, instance, b.Host, b.Sub.Port, b.Sub.Pin, b.DeviceID, b.SPA, probeInfo.Tunnel, p.Svc, down)
		tested[kind] = true
		// a profile a node must not answer (RAYCONTROL_ONLY runs): nothing through it in 15 s, through resolver ("" the
		// node's UDP 53)
		refused := func(what, link, resolver string) {
			_, config := profileConfig(t, link, node, resolver, os.Getenv("RAYCONTROL_CA"), b.DeviceKey, downMbps)
			refusing := startTestBox(t, config)
			// a handshake that gets no answer ends with it (the dnstt client's Close does not wait for it)
			defer refusing.Close()
			for deadline := time.Now().Add(15 * time.Second); time.Now().Before(deadline); time.Sleep(3 * time.Second) {
				out, err := RaycontrolProbeTunnel(refusing, string(warm))
				var r probeResult
				if err == nil && json.Unmarshal([]byte(out), &r) == nil && r.Down != nil && r.Down.OK {
					t.Fatalf("%s %s: %s, and the node answered through it", kind, p.Name, what)
				}
			}
			t.Logf("%s %s: %s, no answer in 15 s, as it must", kind, p.Name, what)
		}
		// a profile a node must still answer: through it within 90 s, as the profile itself
		carries := func(what, link string) {
			_, config := profileConfig(t, link, node, "", os.Getenv("RAYCONTROL_CA"), b.DeviceKey, downMbps)
			carrying := startTestBox(t, config)
			defer carrying.Close()
			for deadline := time.Now().Add(90 * time.Second); ; time.Sleep(3 * time.Second) {
				out, err := RaycontrolProbeTunnel(carrying, string(warm))
				var r probeResult
				if err == nil && json.Unmarshal([]byte(out), &r) == nil && r.Down != nil && r.Down.OK {
					break
				}
				if time.Now().After(deadline) {
					t.Fatalf("%s %s: %s, and no answer through it in 90 s: %s %v", kind, p.Name, what, out, err)
				}
			}
			t.Logf("%s %s: %s, it carries, as it must", kind, p.Name, what)
		}
		// dnsauth-v2 only at the node (auth_hint=required): the same tunnel signed by v1 answered nothing
		if only == nil && os.Getenv("RAYCONTROL_DNSAUTH_V2") != "" && strings.HasPrefix(kind, "dnstt-auth") {
			refused("signed by dnsauth-v1", linkWithParam(t, p.Link, "x-dnsauth-version", "1"), "")
		}
		// a sealed dnstt on a node with seal=required: its key kept for the next run, the profile unsealed refused,
		// and after a rotation a new key, the one before refused
		if only != nil && strings.HasPrefix(kind, "dnstt") && strings.HasSuffix(kind, "-sealed") && os.Getenv("RAYCONTROL_CUT_MAIN") == "" {
			key := sealKeyOf(t, p.Link)
			if out := os.Getenv("RAYCONTROL_SEAL_KEY_OUT"); out != "" {
				if err := os.WriteFile(out, []byte(key), 0o600); err != nil {
					t.Fatal(err)
				}
			}
			if os.Getenv("RAYCONTROL_SEAL_REFUSED") != "" {
				refused("unsealed", linkWithSeal(t, p.Link, ""), "")
			}
			if previous := os.Getenv("RAYCONTROL_SEAL_OLD_KEY"); previous != "" {
				raw, err := os.ReadFile(previous)
				if err != nil {
					t.Fatal(err)
				}
				if old := strings.TrimSpace(string(raw)); old == key {
					t.Fatalf("%s %s: the same seal key after the rotation", kind, p.Name)
				} else {
					refused("sealed with the key before the rotation", linkWithSeal(t, p.Link, old), "")
				}
			}
		}
		// masterdns with seal and auth required (raycontrol v0.14.0): the same profile open, or untagged, answered
		// nothing
		if only != nil && strings.HasPrefix(kind, "masterdns") && os.Getenv("RAYCONTROL_MDNS_REFUSED") != "" {
			if kind != "masterdns-auth-sealed" {
				t.Fatalf("%s %s: want the profile sealed and tagged: %s", kind, p.Name, p.Link)
			}
			refused("open", linkWithout(t, p.Link, "niko-mdseal"), "")
			refused("untagged", linkWithout(t, p.Link, "niko-dnsauth"), "")
			if os.Getenv("RAYCONTROL_DNSAUTH_V2") != "" {
				refused("signed by dnsauth-v1", linkWithParam(t, p.Link, "x-dnsauth-version", "1"), "")
			}
		}
		// masterdns-key-rotation (raycontrol v0.15.0, niko-server v0.13.0): the subscription's service key kept for the
		// next run; after a rotation a new key, the one before carrying for the grace, and refused after -now
		if only != nil && strings.HasPrefix(kind, "masterdns") {
			key := linkParam(t, p.Link, "niko-dnskey")
			if out := os.Getenv("RAYCONTROL_MDNS_KEY_OUT"); out != "" {
				if err := os.WriteFile(out, []byte(key), 0o600); err != nil {
					t.Fatal(err)
				}
			}
			before := func(env string) string {
				raw, err := os.ReadFile(os.Getenv(env))
				if err != nil {
					t.Fatal(err)
				}
				old := strings.TrimSpace(string(raw))
				if old == key {
					t.Fatalf("%s %s: the same service key after the rotation", kind, p.Name)
				}
				return old
			}
			if os.Getenv("RAYCONTROL_MDNS_GRACE_KEY") != "" {
				carries("with the key before the rotation, in its grace",
					linkWithParam(t, p.Link, "niko-dnskey", before("RAYCONTROL_MDNS_GRACE_KEY")))
			}
			if os.Getenv("RAYCONTROL_MDNS_OLD_KEY") != "" {
				refused("with the key before a rotation with -now",
					linkWithParam(t, p.Link, "niko-dnskey", before("RAYCONTROL_MDNS_OLD_KEY")), "")
			}
		}
		// slipstream-v1 (raycontrol v0.16.0): the node's front takes only queries tagged by the device's key, the
		// client only the node's key: the row without dnsauth, or with another pin, gets nothing through
		if only != nil && strings.HasPrefix(kind, "slipstream") && os.Getenv("RAYCONTROL_SLIP_REFUSED") != "" {
			refused("untagged", linkWithout(t, p.Link, "niko-dnsauth"), "")
			slip := strings.Split(linkParam(t, p.Link, "niko-slip"), ";")
			other := []byte(slip[1])
			other[0] = map[bool]byte{true: 'B', false: 'A'}[other[0] == 'A']
			slip[1] = string(other)
			refused("with another pin", linkWithParam(t, p.Link, "niko-slip", strings.Join(slip, ";")), "")
		}
		// dnstt's spare zone (dnszones-v1, raycontrol v0.14.0), the main zone cut by the network's resolver
		if only != nil && strings.HasPrefix(kind, "dnstt") && os.Getenv("RAYCONTROL_CUT_MAIN") != "" {
			spareZoneInterop(t, spareZoneStep{
				kind: kind, profile: p, node: node, host: b.Host, subPort: b.Sub.Port, subPin: b.Sub.Pin,
				deviceID: b.DeviceID, spa: b.SPA, deviceKey: deviceKey, probe: probeInfo,
				dnsProbe: result.Payload.DNSProbe, warm: warm, refused: refused,
				start: func(link, resolver string) *BoxInstance {
					_, config := profileConfig(t, link, node, resolver, os.Getenv("RAYCONTROL_CA"), b.DeviceKey, downMbps)
					return startTestBox(t, config)
				},
			})
		}
		if strings.HasPrefix(kind, "hysteria2") {
			hysteriaSvc = append(hysteriaSvc, p.Svc)
		}
	}
	if only != nil {
		for kind := range only {
			if !tested[kind] {
				t.Fatalf("no %s profile measured (tested %v, tunnels %v)", kind, tested, probeInfo.TunnelSvc)
			}
		}
		return
	}
	// dnsprobe-v1 (raycontrol v0.11.1): the key comes to a user with a dnstt profile
	if dp := result.Payload.DNSProbe; dp != nil && dp.V == 1 {
		dnsProbeInterop(t, result.Payload, node, dp.Key)
		tested["dnsprobe"] = true
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

// appPayload checks the subscription's JSON as the app reads it (RaySubscription) for what a v0.11.1 node sends this
// device: the probe and what its summary takes, the relays with their probe, the dnstt probe key; and writes it to
// RAYCONTROL_PAYLOAD_OUT when set (the app's tests take a copy of it as their fixture).
// interopAuthVersion is the dnsauth version the tunnels sign by: 2 when the subscription announces it (appPayload)
var interopAuthVersion = 1

// payloadAnnounced is what the app takes from the payload beyond the profiles: dnsauth-v2, the node's name
type payloadAnnounced struct {
	dnsAuthV2 bool
	name      string
}

func appPayload(t *testing.T, appJSON string) payloadAnnounced {
	var app struct {
		Payload struct {
			Name    string `json:"name"`
			DNSAuth *struct {
				V []int `json:"v"`
			} `json:"dnsauth"`
			Probe *struct {
				V      int      `json:"v"`
				Report []string `json:"report"`
			} `json:"probe"`
			Relays []struct {
				ID    string          `json:"id"`
				Probe json.RawMessage `json:"probe"`
			} `json:"relays"`
			DNSProbe *struct {
				V   int    `json:"v"`
				Key string `json:"key"`
			} `json:"dns_probe"`
			Profiles []struct {
				Link string `json:"link"`
			} `json:"profiles"`
		} `json:"payload"`
	}
	if err := json.Unmarshal([]byte(appJSON), &app); err != nil {
		t.Fatal(err)
	}
	p := app.Payload
	// dnsprobe-v1's key comes to a user with a dnstt profile, and only to one (the client probes dnstt's zones alone)
	dnstt := slices.ContainsFunc(p.Profiles, func(profile struct {
		Link string `json:"link"`
	}) bool {
		u, err := url.Parse(profile.Link)
		return err == nil && strings.HasPrefix(u.Query().Get("niko-dns"), "dnstt;")
	})
	switch {
	case p.Probe == nil || p.Probe.V != 1 || !slices.Contains(p.Probe.Report, "dns") || !slices.Contains(p.Probe.Report, "path"):
		t.Fatalf("the app's payload: probe %+v", p.Probe)
	case dnstt && (p.DNSProbe == nil || p.DNSProbe.V != 1 || p.DNSProbe.Key == ""):
		t.Fatalf("the app's payload: no dns_probe")
	case !dnstt && p.DNSProbe != nil:
		t.Fatalf("the app's payload: dns_probe for a user without dnstt")
	}
	if os.Getenv("RAYCONTROL_RELAY") != "" && (len(p.Relays) == 0 || len(p.Relays[0].Probe) == 0) {
		// the relay's feed may come up after the first fetch: its probe is checked through the relay below
		t.Logf("the app's payload: relays %d, the first without its probe yet", len(p.Relays))
	}
	if out := os.Getenv("RAYCONTROL_PAYLOAD_OUT"); out != "" {
		if err := os.WriteFile(out, []byte(appJSON), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	return payloadAnnounced{dnsAuthV2: p.DNSAuth != nil && slices.Contains(p.DNSAuth.V, 2), name: p.Name}
}

// linkParam is link's param, which must be there
func linkParam(t *testing.T, link, param string) string {
	t.Helper()
	u, err := url.Parse(link)
	if err != nil {
		t.Fatal(err)
	}
	value := u.Query().Get(param)
	if value == "" {
		t.Fatalf("no %s in the link of %s", param, u.Fragment)
	}
	return value
}

// linkWithParam is link with param set to value (x-…: the test's own, profileConfig reads them)
func linkWithParam(t *testing.T, link, param, value string) string {
	t.Helper()
	u, err := url.Parse(link)
	if err != nil {
		t.Fatal(err)
	}
	q := u.Query()
	q.Set(param, value)
	u.RawQuery = q.Encode()
	return u.String()
}

// authOptions is a DNS tunnel's auth block: the device's key for the node and service, by the version the
// subscription announced (x-dnsauth-version: another, for a refused case)
func authOptions(q url.Values, deviceKey string) map[string]any {
	svc, _ := strconv.Atoi(q.Get("niko-svc"))
	auth := map[string]any{"node_id": q.Get("niko-node"), "service_id": svc, "device_key": deviceKey}
	version := interopAuthVersion
	if v, err := strconv.Atoi(q.Get("x-dnsauth-version")); err == nil {
		version = v
	}
	if version == 2 {
		auth["version"] = 2
	}
	return auth
}

// dnsProbeInterop asks the node's front a small and a large probe in its dnstt zone through RaycontrolDnsProbe, as the
// app asks of each resolver (here the node's own UDP 53), and one with another key: to the node that is no probe, the
// tunnel's backend answers it, which the app takes as unclear (dnsprobe-v1 §6).
func dnsProbeInterop(t *testing.T, payload raysub.Payload, node, key string) {
	zone := ""
	for _, p := range payload.Profiles {
		u, err := url.Parse(p.Link)
		if err != nil {
			continue
		}
		if spec := strings.Split(u.Query().Get("niko-dns"), ";"); len(spec) == 4 && spec[0] == "dnstt" {
			zone = strings.Split(spec[2], ",")[0]
			break
		}
	}
	if zone == "" {
		t.Fatal("dns_probe in the subscription, no dnstt profile")
	}
	ask := func(key string, large bool) dnsprobe.Result {
		request, _ := json.Marshal(map[string]any{"resolver": "udp://" + net.JoinHostPort(node, "53"), "zone": zone,
			"key": key, "large": large, "timeout_ms": 4000})
		out, err := RaycontrolDnsProbe(string(request))
		if err != nil {
			t.Fatal(err)
		}
		var r dnsprobe.Result
		if err = json.Unmarshal([]byte(out), &r); err != nil {
			t.Fatal(err)
		}
		return r
	}
	// the node saw the query's EDNS size and the runner's address
	small := ask(key, false)
	if !small.OK || small.Large || small.EDNS != 1232 || small.From == "" {
		t.Fatalf("dnsprobe small: %+v", small)
	}
	if r := ask(key, true); !r.OK || !r.Large {
		t.Fatalf("dnsprobe large: %+v", r)
	}
	if r := ask(base64.RawURLEncoding.EncodeToString(make([]byte, 32)), false); r.OK || !r.Unclear {
		t.Fatalf("dnsprobe with another key: %+v", r)
	}
	t.Logf("dnsprobe: the node's front answers small and large (seen edns %d, case %v), another key is unclear",
		small.EDNS, small.Case)
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
		kind, config := profileConfig(t, u.String(), node, "", os.Getenv("RAYCONTROL_CA"), deviceKey, downMbps)
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
func profileConfig(t *testing.T, link, node, resolver, ca, deviceKey string, downMbps int) (kind, config string) {
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
		// node's own UDP 53 (the client picks one when the link leaves it empty), or the step's
		spec := strings.Split(q.Get("niko-dns"), ";")
		if len(spec) != 4 {
			t.Fatalf("niko-dns %q", q.Get("niko-dns"))
		}
		if resolver == "" {
			resolver = net.JoinHostPort(node, "53")
		}
		var tunnel map[string]any
		switch spec[0] {
		case "dnstt":
			kind = "dnstt"
			tunnel = map[string]any{
				"type": "dnstt", "domain": spec[2], "public_key": strings.ToLower(spec[3]),
				"resolver": "udp://" + resolver,
			}
			// dnsauth-v1 (raycontrol v0.10.0, the node's dnstt with auth=required): queries signed by the device
			if q.Get("niko-dnsauth") == "v1" {
				kind = "dnstt-auth"
				tunnel["auth"] = authOptions(q, deviceKey)
			}
			// dnsseal-v1 (raycontrol v0.13, the node's dnstt with seal=…): the framing sealed with the zone's key
			if seal, ok := strings.CutPrefix(q.Get("niko-dnsseal"), "v1;"); ok {
				kind += "-sealed"
				tunnel["seal"] = map[string]any{"key": seal}
			}
		case "masterdns":
			kind = "masterdns"
			method, _ := strconv.Atoi(spec[3])
			tunnel = map[string]any{
				"type": "masterdnsvpn", "domains": []string{spec[2]}, "resolvers": []string{resolver},
				"encryption_method": method, "encryption_key": q.Get("niko-dnskey"), "mode": "tcp",
			}
			// dnsauth for masterdns (raycontrol v0.14.0): the device's tag in every query, its key as dnstt's
			if q.Get("niko-dnsauth") == "v1" {
				kind += "-auth"
				tunnel["auth"] = authOptions(q, deviceKey)
			}
			// mdseal-v1 (raycontrol v0.14.0): the node's answers sealed with the service's key, in the node's mode
			if mdseal := strings.Split(q.Get("niko-mdseal"), ";"); mdseal[0] == "v1" {
				kind += "-sealed"
				tunnel["seal"] = "optional"
				if slices.Contains(mdseal[1:], "required") {
					tunnel["seal"] = "required"
				}
			}
		case "slipstream":
			// slipstream-v1 (raycontrol v0.16.0): the bundled client's linux build, as the app runs it
			kind = "slipstream"
			if q.Get("niko-dnsauth") == "v1" {
				kind += "-auth"
			}
			client := os.Getenv("RAYCONTROL_SLIPSTREAM_CLIENT")
			if client == "" {
				return kind, ""
			}
			listen := startSlipstreamClient(t, client, q, spec, resolver, deviceKey)
			outbound = map[string]any{
				"type": "vless", "server": "127.0.0.1", "server_port": listen, "uuid": u.User.Username(),
				"encryption": q.Get("encryption"),
			}
		default:
			return spec[0], ""
		}
		if tunnel != nil {
			tunnel["tag"] = "tunnel"
			extra = append(extra, tunnel)
			outbound = map[string]any{
				"type": "vless", "server": u.Hostname(), "server_port": port, "uuid": u.User.Username(),
				"encryption": q.Get("encryption"), "detour": "tunnel",
			}
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

// startSlipstreamClient runs the slipstream client at client for a niko-dns=slipstream link, with the arguments the
// app gives it (DnsTunnel.slipstreamArgs): its one resolver, the pin, ALPN and SNI of niko-slip, and with
// niko-dnsauth=v1 --dnsauth and K in its environment (dnsauth-v1 §2, as the app derives it); it is killed when the
// test ends. Its TCP port on 127.0.0.1, which the VLESS hop dials.
func startSlipstreamClient(t *testing.T, client string, q url.Values, spec []string, resolver, deviceKey string) int {
	t.Helper()
	slip := strings.Split(q.Get("niko-slip"), ";")
	if len(slip) != 4 || slip[0] != "v1" {
		t.Fatalf("niko-slip %q", q.Get("niko-slip"))
	}
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	listen := l.Addr().(*net.TCPAddr).Port
	l.Close()
	args := []string{"--quiet", "--tcp-listen-host=127.0.0.1", "--tcp-listen-port=" + strconv.Itoa(listen),
		"--domain=" + spec[2], "--resolver=" + resolver, "--pin=" + slip[1], "--alpn=" + slip[2], "--sni=" + slip[3]}
	env := os.Environ()
	if q.Get("niko-dnsauth") == "v1" {
		svc, err := strconv.Atoi(cmp.Or(q.Get("niko-svc"), spec[3]))
		if err != nil || svc < 0 || svc > 0xffff {
			t.Fatalf("slipstream: the service id %q", cmp.Or(q.Get("niko-svc"), spec[3]))
		}
		device, err := hex.DecodeString(deviceKey)
		if err != nil {
			t.Fatal(err)
		}
		key, err := dnsttTransport.AuthKey(device, strings.ToLower(q.Get("niko-node")), uint16(svc))
		if err != nil {
			t.Fatal(err)
		}
		// the node's clock is the runner's
		args = append(args, "--dnsauth", "--clock-offset=0")
		env = append(env, "SLIPSTREAM_DNSAUTH_KEY="+hex.EncodeToString(key))
	}
	var stderr bytes.Buffer
	cmd := exec.Command(client, args...)
	cmd.Env = env
	cmd.Stderr = &stderr
	if err := cmd.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() {
		_ = cmd.Process.Kill()
		_ = cmd.Wait()
		if t.Failed() && stderr.Len() > 0 {
			t.Logf("slipstream client on %d: %s", listen, stderr.String())
		}
	})
	return listen
}

// sealKeyOf is the dnsseal-v1 key of a dnstt link (niko-dnsseal=v1;<key>).
func sealKeyOf(t *testing.T, link string) string {
	t.Helper()
	u, err := url.Parse(link)
	if err != nil {
		t.Fatal(err)
	}
	key, ok := strings.CutPrefix(u.Query().Get("niko-dnsseal"), "v1;")
	if !ok || key == "" {
		t.Fatalf("no seal key in %s", u.Redacted())
	}
	return key
}

// linkWithSeal is a dnstt link with another seal key, or without its seal ("").
func linkWithSeal(t *testing.T, link, key string) string {
	t.Helper()
	u, err := url.Parse(link)
	if err != nil {
		t.Fatal(err)
	}
	q := u.Query()
	if key == "" {
		q.Del("niko-dnsseal")
	} else {
		q.Set("niko-dnsseal", "v1;"+key)
	}
	u.RawQuery = q.Encode()
	return u.String()
}

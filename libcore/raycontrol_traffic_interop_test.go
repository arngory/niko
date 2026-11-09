package libcore

import (
	"encoding/json"
	"os"
	"os/exec"
	"slices"
	"strings"
	"testing"
	"time"

	"libcore/raysub"
)

// TestRaycontrolTrafficLimit (traffic-limit-v1, raycontrol v0.22.0), run by raycontrol-interop.yml last on its node:
// with a limit the subscription carries `traffic`; set below what the user has used, the user's Hysteria2 session
// stops carrying within seconds (the node closes the QUIC connection and takes no new one) and the subscription
// answers exhausted with no profiles, expiring within the hour (the app keeps its rows then); reset-usage brings the
// profiles back and the tunnel carries again.
//
// RAYCONTROL_RAYCTL runs rayctl on the node ("docker exec rc-node /opt/rc/rayctl"), RAYCONTROL_USER is the device's
// user; RAYCONTROL_BUNDLE, RAYCONTROL_ADDRESS and RAYCONTROL_CA as for TestRaycontrolTunnelInterop.
func TestRaycontrolTrafficLimit(t *testing.T) {
	rayctlCommand := strings.Fields(os.Getenv("RAYCONTROL_RAYCTL"))
	user := os.Getenv("RAYCONTROL_USER")
	if len(rayctlCommand) == 0 || user == "" {
		t.Skip("RAYCONTROL_RAYCTL and RAYCONTROL_USER not set (raycontrol-interop.yml sets them)")
	}
	b, node, request := interopDevice(t)
	rayctlRun := func(args ...string) ([]byte, error) {
		return exec.Command(rayctlCommand[0], append(slices.Clone(rayctlCommand[1:]), args...)...).CombinedOutput()
	}
	rayctl := func(args ...string) {
		t.Helper()
		out, err := rayctlRun(args...)
		if err != nil {
			t.Fatalf("rayctl %s: %v: %s", strings.Join(args, " "), err, out)
		}
		t.Logf("rayctl %s: %s", strings.Join(args, " "), strings.TrimSpace(string(out)))
	}
	// the node as the other steps had it, whatever happens here
	t.Cleanup(func() {
		if out, err := rayctlRun("user", "limit", user, "off"); err != nil {
			t.Logf("rayctl user limit %s off: %v: %s", user, err, out)
		}
	})

	type traffic struct {
		V         int   `json:"v"`
		Used      int64 `json:"used"`
		Limit     int64 `json:"limit"`
		Reset     int64 `json:"reset"`
		Exhausted bool  `json:"exhausted"`
	}
	// the subscription as the app reads it (RaycontrolFetchSubscription's JSON), until ok or the time is up
	subscription := func(what string, within time.Duration, ok func(raysub.Result, *traffic) bool) (raysub.Result, *traffic) {
		t.Helper()
		var (
			result raysub.Result
			tr     *traffic
		)
		for deadline := time.Now().Add(within); ; time.Sleep(3 * time.Second) {
			appJSON, err := RaycontrolFetchSubscription(string(request))
			if err == nil {
				var app struct {
					Payload struct {
						Traffic *traffic `json:"traffic"`
					} `json:"payload"`
				}
				result, tr = raysub.Result{}, nil
				if json.Unmarshal([]byte(appJSON), &result) == nil && json.Unmarshal([]byte(appJSON), &app) == nil {
					tr = app.Payload.Traffic
					if ok(result, tr) {
						t.Logf("%s: %d profiles, traffic %+v", what, len(result.Payload.Profiles), tr)
						return result, tr
					}
				}
			}
			if time.Now().After(deadline) {
				t.Fatalf("%s: not in %v (%d profiles, traffic %+v, %v)", what, within, len(result.Payload.Profiles), tr, err)
			}
		}
	}
	// a Hysteria2 profile of the probe's tunnels: its QUIC connection carries every probe, so a cut shows in the next
	hysteria := func(result raysub.Result) (string, string) {
		t.Helper()
		probeInfo := result.Payload.Probe
		if probeInfo == nil || probeInfo.Tunnel == "" {
			t.Fatalf("subscription without probe: %+v", result.Payload)
		}
		for _, p := range result.Payload.Profiles {
			if !slices.Contains(probeInfo.TunnelSvc, p.Svc) {
				continue
			}
			kind, config := profileConfig(t, p.Link, node, "", os.Getenv("RAYCONTROL_CA"), b.DeviceKey, 50)
			if strings.HasPrefix(kind, "hysteria2") && config != "" {
				return config, probeInfo.Tunnel
			}
		}
		t.Fatal("no Hysteria2 profile among the probe's tunnels")
		return "", ""
	}
	download := func(instance *BoxInstance, tunnel string, bytes int64) bool {
		probe, _ := json.Marshal(probeRequest{Host: b.Host, Port: b.Sub.Port, Pin: b.Sub.Pin, DeviceID: b.DeviceID, SPA: b.SPA,
			Tunnel: tunnel, DownBytes: bytes, WindowMs: 10000})
		out, err := RaycontrolProbeTunnel(instance, string(probe))
		var r probeResult
		return err == nil && json.Unmarshal([]byte(out), &r) == nil && r.Down != nil && r.Down.OK
	}
	carries := func(instance *BoxInstance, tunnel string) bool { return download(instance, tunnel, 1024) }
	carriesWithin := func(what string, instance *BoxInstance, tunnel string, within time.Duration) {
		t.Helper()
		for deadline := time.Now().Add(within); !carries(instance, tunnel); time.Sleep(3 * time.Second) {
			if time.Now().After(deadline) {
				t.Fatalf("%s: no answer through Hysteria2 in %v", what, within)
			}
		}
	}

	// a limit far above what is used: announced, nothing cut. The first limit turns the node's count on, from zero
	rayctl("user", "limit", user, "100G")
	result, _ := subscription("a limit", 90*time.Second, func(r raysub.Result, tr *traffic) bool {
		return tr != nil && tr.V == 1 && tr.Limit > 0 && !tr.Exhausted && len(r.Payload.Profiles) > 0
	})
	config, tunnel := hysteria(result)
	instance := startTestBox(t, config)
	carriesWithin("under the limit", instance, tunnel, 90*time.Second)
	// a few megabytes through Hysteria2, until the node counts them (its count lags up to a minute)
	const counted = 2 << 20
	for deadline := time.Now().Add(150 * time.Second); ; {
		download(instance, tunnel, 1<<20)
		_, tr := subscription("counting", 10*time.Second, func(raysub.Result, *traffic) bool { return true })
		if tr != nil && tr.Used >= counted {
			t.Logf("counted: %d bytes used", tr.Used)
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("the node counted %+v after megabytes through Hysteria2, want %d bytes at least", tr, counted)
		}
		time.Sleep(5 * time.Second)
	}

	// below what is used now: the session through the tunnel stops carrying
	rayctl("user", "limit", user, "1M")
	crossed := time.Now()
	for carries(instance, tunnel) {
		if time.Since(crossed) > 60*time.Second {
			t.Fatal("over the limit: Hysteria2 still carries 60 s after the crossing")
		}
		time.Sleep(2 * time.Second)
	}
	t.Logf("over the limit: Hysteria2 stopped carrying %v after the crossing", time.Since(crossed).Round(time.Second))
	instance.Close()
	// exhausted: no profiles, an answer that expires within the hour (plus the node's jitter of up to 10 min)
	result, tr := subscription("used up", 60*time.Second, func(r raysub.Result, tr *traffic) bool {
		return tr != nil && tr.Exhausted && len(r.Payload.Profiles) == 0
	})
	if left := result.Payload.Expires - result.Payload.Time; left > 70*60 {
		t.Fatalf("used up: the answer holds %d s, want an hour at most", left)
	}
	if tr.Used < tr.Limit {
		t.Logf("used up with used %d under limit %d: the node's word, as the client takes it", tr.Used, tr.Limit)
	}

	// the count from zero: the profiles back, the tunnel carries again
	rayctl("user", "reset-usage", user)
	result, _ = subscription("after reset-usage", 90*time.Second, func(r raysub.Result, tr *traffic) bool {
		return tr != nil && !tr.Exhausted && len(r.Payload.Profiles) > 0
	})
	config, tunnel = hysteria(result)
	again := startTestBox(t, config)
	defer again.Close()
	carriesWithin("after reset-usage", again, tunnel, 90*time.Second)
}

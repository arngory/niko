//go:build with_naive_outbound

package libcore

import (
	"encoding/json"
	"testing"
	"time"

	"libcore/rayprobe"
)

func init() { naiveUDPProbe = probeNaiveUDP }

// probeNaiveUDP runs the app's probe of a network for UDP as HTTP/3 datagrams (naive-udp-v1 §9.4, RayDatagrams.kt)
// against the node: a core in this process on the udp=1 line with datagrams not yet allowed, the probe's echo at the
// probe name through RaycontrolProbeDatagram (its HTTP part through the same outbound over TCP), then the app's
// calls on the outbound: allow, and a new network that takes the allowance back.
func probeNaiveUDP(t *testing.T, config string, request []byte) {
	t.Helper()
	instance := startTestBox(t, config)
	const tag = "naive"
	if state := NaiveDatagramState(instance, tag); state != "probing" {
		t.Fatalf("datagrams before the app's probe: %q, want probing", state)
	}
	// a probe that sent nothing measured nothing (the session over TCP did not start): the app tries again too
	var echo *rayprobe.EchoResult
	for try := 1; try <= 3; try++ {
		raw, err := RaycontrolProbeDatagram(instance, tag, string(request))
		if err != nil {
			t.Fatalf("probe: %v", err)
		}
		var out probeResult
		if err = json.Unmarshal([]byte(raw), &out); err != nil {
			t.Fatalf("probe's result %s: %v", raw, err)
		}
		echo = out.Echo
		t.Logf("probe %d: %s", try, raw)
		if echo != nil && echo.Sent > 0 {
			break
		}
		time.Sleep(5 * time.Second)
	}
	// the app's pass (RayDatagrams.ECHO_PASS): 22 of 30 back, over 24 KB
	if echo == nil || echo.Received < 22 {
		t.Fatalf("the probe's echo through datagrams did not pass: %+v", echo)
	}
	t.Logf("the probe passed: %d of %d echoes of %d bytes back through datagrams, rtt %.1f ms", echo.Received, echo.Sent, echo.Size, echo.RTTAvgMs)
	if err := SetNaiveDatagramAllowed(instance, tag, true); err != nil {
		t.Fatal(err)
	}
	if state := NaiveDatagramState(instance, tag); state != "allowed" {
		t.Fatalf("after the app allowed datagrams: %q", state)
	}
	if err := ResetNaiveDatagram(instance, tag); err != nil {
		t.Fatal(err)
	}
	if state := NaiveDatagramState(instance, tag); state != "probing" {
		t.Fatalf("after the app told a new network: %q", state)
	}
}

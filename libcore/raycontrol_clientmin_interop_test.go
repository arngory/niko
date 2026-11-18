package libcore

import (
	"encoding/json"
	"os"
	"os/exec"
	"slices"
	"strings"
	"testing"
	"time"

	"libcore/rayclient"
	"libcore/raysub"
)

// TestRaycontrolClientMin (client-min-v1, raycontrol v0.32.0), run by raycontrol-interop.yml on its node: the app's
// X-Raycontrol-Client reaches the node (rayctl client counts the device by it); a minimum above the app's version
// answers refused with no profiles and an answer of an hour at most (the app keeps its rows then); at the minimum the
// profiles come with `client.min`; a preview of the minimum is below it; no header is let through (-unknown allow);
// a minimum from a day ahead keeps the profiles and says `from`, the answer held no later than it (plus the node's
// 10 min); none takes the field away.
//
// RAYCONTROL_RAYCTL runs rayctl on the node ("docker exec rc-node /opt/rc/rayctl"); RAYCONTROL_BUNDLE,
// RAYCONTROL_ADDRESS and RAYCONTROL_CA as for TestRaycontrolTunnelInterop.
func TestRaycontrolClientMin(t *testing.T) {
	rayctlCommand := strings.Fields(os.Getenv("RAYCONTROL_RAYCTL"))
	if len(rayctlCommand) == 0 {
		t.Skip("RAYCONTROL_RAYCTL not set (raycontrol-interop.yml sets it)")
	}
	_, _, request := interopDevice(t)
	rayctlRun := func(args ...string) ([]byte, error) {
		return exec.Command(rayctlCommand[0], append(slices.Clone(rayctlCommand[1:]), args...)...).CombinedOutput()
	}
	rayctl := func(args ...string) []byte {
		t.Helper()
		out, err := rayctlRun(args...)
		if err != nil {
			t.Fatalf("rayctl %s: %v: %s", strings.Join(args, " "), err, out)
		}
		t.Logf("rayctl %s: %s", strings.Join(args, " "), strings.TrimSpace(string(out)))
		return out
	}
	// the node and this process as the other steps had them, whatever happens here
	t.Cleanup(func() {
		rayclient.Set("")
		if out, err := rayctlRun("client", "min", "niko", "none"); err != nil {
			t.Logf("rayctl client min niko none: %v: %s", err, out)
		}
	})

	type client struct {
		V       int               `json:"v"`
		Min     map[string]string `json:"min"`
		From    int64             `json:"from"`
		Refused bool              `json:"refused"`
	}
	// the subscription as the app reads it (RaycontrolFetchSubscription's JSON) with the app naming itself as
	// header ("" none), until ok or the time is up
	subscription := func(what, header string, within time.Duration, ok func(raysub.Result, *client) bool) (raysub.Result, *client) {
		t.Helper()
		if header != "" && !rayclient.Set(header) {
			t.Fatalf("%s: the header %q is not one", what, header)
		} else if header == "" {
			rayclient.Set("")
		}
		var (
			result raysub.Result
			c      *client
			err    error
		)
		for deadline := time.Now().Add(within); ; time.Sleep(3 * time.Second) {
			var appJSON string
			appJSON, err = RaycontrolFetchSubscription(string(request))
			if err == nil {
				var app struct {
					Payload struct {
						Client *client `json:"client"`
					} `json:"payload"`
				}
				result, c = raysub.Result{}, nil
				if json.Unmarshal([]byte(appJSON), &result) == nil && json.Unmarshal([]byte(appJSON), &app) == nil {
					c = app.Payload.Client
					if ok(result, c) {
						t.Logf("%s: %d profiles, client %+v, the answer for %d s", what, len(result.Payload.Profiles), c,
							result.Payload.Expires-result.Payload.Time)
						return result, c
					}
				}
			}
			if time.Now().After(deadline) {
				t.Fatalf("%s: not in %v (%d profiles, client %+v, %v)", what, within, len(result.Payload.Profiles), c, err)
			}
		}
	}
	const (
		app     = "niko/1.59.0 (android; x86_64)"
		newer   = "niko/1.60.0 (android; x86_64)"
		preview = "niko/1.60.0-pre.20261120.1 (android; x86_64)"
	)

	// no policy: no field, the profiles as always
	subscription("no minimum", app, 30*time.Second, func(r raysub.Result, c *client) bool {
		return c == nil && len(r.Payload.Profiles) > 0
	})
	// the node counts the device by the client it named (client-min-v1 §5)
	type count struct {
		Client  string `json:"client"`
		Devices uint32 `json:"devices"`
	}
	for deadline := time.Now().Add(30 * time.Second); ; time.Sleep(3 * time.Second) {
		var policy struct {
			Clients []count `json:"clients"`
		}
		out := rayctl("-json", "client")
		if json.Unmarshal(out, &policy) == nil && slices.ContainsFunc(policy.Clients, func(c count) bool {
			return c.Client == "niko/1.59.0" && c.Devices > 0
		}) {
			break
		}
		if time.Now().After(deadline) {
			t.Fatalf("rayctl client does not count a device on niko/1.59.0: %s", out)
		}
	}

	// above the app: refused, no profiles, an hour at most (and the node's jitter)
	rayctl("client", "min", "niko", "1.60.0")
	refused, _ := subscription("below the minimum", app, 30*time.Second, func(r raysub.Result, c *client) bool {
		return c != nil && c.V == 1 && c.Refused && c.Min["niko"] == "1.60.0" && len(r.Payload.Profiles) == 0
	})
	if left := refused.Payload.Expires - refused.Payload.Time; left > 70*60 {
		t.Fatalf("refused: the answer holds %d s, want an hour at most", left)
	}
	// at the minimum: the profiles and the minimum for the app's information
	subscription("at the minimum", newer, 30*time.Second, func(r raysub.Result, c *client) bool {
		return c != nil && !c.Refused && c.Min["niko"] == "1.60.0" && c.From == 0 && len(r.Payload.Profiles) > 0
	})
	// a preview of the minimum is below it (semver: a pre-release before its release)
	subscription("a preview of the minimum", preview, 30*time.Second, func(r raysub.Result, c *client) bool {
		return c != nil && c.Refused && len(r.Payload.Profiles) == 0
	})
	// no header: -unknown allow, as the node starts
	subscription("no header", "", 30*time.Second, func(r raysub.Result, c *client) bool {
		return !(c != nil && c.Refused) && len(r.Payload.Profiles) > 0
	})

	// from a day ahead: the profiles stay, `from` says when, the answer held no later than it (plus 10 min)
	day := time.Now().UTC().AddDate(0, 0, 2).Truncate(24 * time.Hour)
	rayctl("client", "min", "niko", "1.61.0", "-from", day.Format(time.DateOnly))
	ahead, _ := subscription("a minimum ahead", newer, 30*time.Second, func(r raysub.Result, c *client) bool {
		return c != nil && !c.Refused && c.Min["niko"] == "1.61.0" && c.From == day.Unix() && len(r.Payload.Profiles) > 0
	})
	if ahead.Payload.Expires > day.Unix()+10*60 {
		t.Fatalf("a minimum ahead: the answer holds until %d, past the start %d and its 10 min", ahead.Payload.Expires, day.Unix())
	}

	// none: no field, the profiles as always
	rayctl("client", "min", "niko", "none")
	subscription("the minimum taken away", app, 30*time.Second, func(r raysub.Result, c *client) bool {
		return c == nil && len(r.Payload.Profiles) > 0
	})
}

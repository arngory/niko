package libcore

import (
	"encoding/json"
	"os"
	"slices"
	"strings"
	"testing"
	"time"

	"libcore/raysub"
)

// TestRaycontrolRelayAudience (raycontrol v0.36.0, a user's subscription with only their relays), run by
// raycontrol-interop.yml on its node: the subscription as the app reads it lists exactly the relays named in
// RAYCONTROL_RELAYS (comma-separated, "" none). A relay that forwards none of the user's services is not theirs, nor
// is one an admin gave other users (-users).
//
// RAYCONTROL_BUNDLE, RAYCONTROL_ADDRESS and RAYCONTROL_CA as for TestRaycontrolTunnelInterop.
func TestRaycontrolRelayAudience(t *testing.T) {
	want, set := os.LookupEnv("RAYCONTROL_RELAYS")
	if !set {
		t.Skip("RAYCONTROL_RELAYS not set (raycontrol-interop.yml sets it)")
	}
	_, _, request := interopDevice(t)
	var wantNames []string
	for _, name := range strings.Split(want, ",") {
		if name = strings.TrimSpace(name); name != "" {
			wantNames = append(wantNames, name)
		}
	}
	slices.Sort(wantNames)
	var got []string
	for deadline := time.Now().Add(60 * time.Second); ; time.Sleep(3 * time.Second) {
		appJSON, err := RaycontrolFetchSubscription(string(request))
		var result raysub.Result
		if err == nil && json.Unmarshal([]byte(appJSON), &result) == nil && len(result.Payload.Profiles) > 0 {
			got = got[:0]
			for _, r := range result.Payload.Relays {
				got = append(got, r.ID)
			}
			slices.Sort(got)
			if slices.Equal(got, wantNames) {
				t.Logf("relays %q, %d profiles", got, len(result.Payload.Profiles))
				// no profile goes through a relay the user does not have
				for _, p := range result.Payload.Profiles {
					for _, via := range p.Via {
						if !slices.Contains(wantNames, via) {
							t.Fatalf("a profile goes via %q, not one of the user's relays %q", via, wantNames)
						}
					}
				}
				return
			}
		}
		if time.Now().After(deadline) {
			t.Fatalf("relays %q, want %q (%v)", got, wantNames, err)
		}
	}
}

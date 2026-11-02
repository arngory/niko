package libcore

import (
	"context"
	"encoding/json"
	"os"
	"time"

	"libcore/raysub"

	"github.com/sagernet/sing/common/control"
)

const protectPath = "protect_path"

// RaycontrolFetchSubscription fetches and verifies a raycontrol subscription
// (raysub.Request as JSON) and returns raysub.Result as JSON. Its sockets are
// protected from the app's own VPN when the service runs.
func RaycontrolFetchSubscription(requestJSON string) (string, error) {
	var request raysub.Request
	if err := json.Unmarshal([]byte(requestJSON), &request); err != nil {
		return "", err
	}
	if _, err := os.Stat(protectPath); err == nil {
		request.Control = control.ProtectPath(protectPath)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	result, err := raysub.Fetch(ctx, request)
	if err != nil {
		return "", err
	}
	raw, err := json.Marshal(result)
	return string(raw), err
}

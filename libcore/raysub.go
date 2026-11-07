package libcore

import (
	"context"
	"encoding/json"
	"os"
	"syscall"
	"time"

	"libcore/rayapi"
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
	request.Control = protectControl()
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	result, err := raysub.Fetch(ctx, request)
	if err != nil {
		return "", err
	}
	raw, err := json.Marshal(result)
	return string(raw), err
}

// RaycontrolAppFile writes the file name of the app's release tag, as the node of the subscription request
// (raysub.Request as JSON, the subscription's) holds it (app-update-v1), to outPath. With etag (the file's sha256 in
// quotes, from the release's signed SHA256SUMS) a part already in outPath is gone on from. A transfer goes on while it
// moves, for two hours at most. Its sockets are protected from the app's own VPN when the service runs.
func RaycontrolAppFile(requestJSON, tag, name, outPath, etag string) error {
	var request raysub.Request
	if err := json.Unmarshal([]byte(requestJSON), &request); err != nil {
		return err
	}
	request.Control = protectControl()
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Hour)
	defer cancel()
	return raysub.AppFile(ctx, request, "niko", tag, name, outPath, etag)
}

// RaycontrolRegister turns an invite (rayapi.Invite as JSON) into a device
// with a new client certificate and returns rayapi.Registered as JSON.
func RaycontrolRegister(inviteJSON string) (string, error) {
	var invite rayapi.Invite
	if err := json.Unmarshal([]byte(inviteJSON), &invite); err != nil {
		return "", err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	registered, err := rayapi.Register(ctx, invite, protectControl())
	if err != nil {
		return "", err
	}
	raw, err := json.Marshal(registered)
	return string(raw), err
}

// RaycontrolApiCall makes one call to a node's remote API (rayapi.Endpoint as
// JSON; method "raycontrol.node.v1.NodeService/ListUsers"; body in protobuf
// JSON) and returns the JSON answer.
func RaycontrolApiCall(endpointJSON, method, bodyJSON string) (string, error) {
	endpoint, err := apiEndpoint(endpointJSON)
	if err != nil {
		return "", err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	answer, err := rayapi.Call(ctx, endpoint, method, []byte(bodyJSON))
	return string(answer), err
}

// RaycontrolNewIdentity makes a client key and certificate (rayapi.Identity
// as JSON) for a later RaycontrolRotateCertificate.
func RaycontrolNewIdentity() (string, error) {
	identity, err := rayapi.NewIdentity()
	if err != nil {
		return "", err
	}
	raw, err := json.Marshal(identity)
	return string(raw), err
}

// RaycontrolRotateCertificate moves the device to the client key of
// nextIdentityJSON (rayapi.Identity), which the app stored before the call.
func RaycontrolRotateCertificate(endpointJSON, nextIdentityJSON string) error {
	endpoint, err := apiEndpoint(endpointJSON)
	if err != nil {
		return err
	}
	var next rayapi.Identity
	if err = json.Unmarshal([]byte(nextIdentityJSON), &next); err != nil {
		return err
	}
	ctx, cancel := context.WithTimeout(context.Background(), 90*time.Second)
	defer cancel()
	return rayapi.RotateCertificate(ctx, endpoint, next)
}

func apiEndpoint(endpointJSON string) (rayapi.Endpoint, error) {
	var endpoint rayapi.Endpoint
	if err := json.Unmarshal([]byte(endpointJSON), &endpoint); err != nil {
		return endpoint, err
	}
	endpoint.Control = protectControl()
	return endpoint, nil
}

// protectControl keeps the sockets out of the app's own VPN when the service runs.
func protectControl() func(network, address string, conn syscall.RawConn) error {
	if _, err := os.Stat(protectPath); err == nil {
		return control.ProtectPath(protectPath)
	}
	return nil
}

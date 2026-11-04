package rayapi

import (
	"context"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net"
	"os"
	"strconv"
	"strings"
	"testing"
	"time"

	"libcore/probe"
	"libcore/rayprobe"
)

// TestRaycontrolInterop registers through a real rayd (arngory/raycontrol), run by
// .github/workflows/raycontrol-interop.yml: RAYCONTROL_INVITE_FILE holds an admin
// invite from `rayctl device invite USER NAME -role admin -o FILE`.
func TestRaycontrolInterop(t *testing.T) {
	path := os.Getenv("RAYCONTROL_INVITE_FILE")
	if path == "" {
		t.Skip("RAYCONTROL_INVITE_FILE not set (raycontrol-interop.yml sets it)")
	}
	raw, err := os.ReadFile(path)
	if err != nil {
		t.Fatal(err)
	}
	code := strings.TrimSpace(string(raw))
	body, err := base64.RawURLEncoding.DecodeString(strings.TrimPrefix(code, "rcinv1:"))
	if err != nil {
		t.Fatalf("invite: %v", err)
	}
	var invite Invite
	var fields struct {
		DeviceKey string `json:"device_key"`
		Expires   int64  `json:"expires"`
	}
	if err = json.Unmarshal(body, &invite); err != nil {
		t.Fatal(err)
	}
	if err = json.Unmarshal(body, &fields); err != nil {
		t.Fatal(err)
	}
	// rcinv1 keeps the key next to spa, as rcdev1 does (spa-v2.md §8)
	invite.SPA.DeviceKey = fields.DeviceKey
	if invite.API.Port == 0 || invite.API.Pin == "" || fields.Expires < time.Now().Unix() {
		t.Fatalf("invite without api or expired: %+v", invite.API)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()

	registered, err := Register(ctx, invite, nil)
	if err != nil {
		t.Fatalf("Register: %v", err)
	}
	if !strings.HasPrefix(registered.Bundle, "rcdev1:") || registered.Role != "admin" {
		t.Fatalf("Register answered %q, role %q", registered.Bundle[:min(len(registered.Bundle), 12)], registered.Role)
	}
	t.Logf("registered device %s as %s", registered.DeviceID, registered.Role)

	// the device's own package: its SPA key and the api endpoint
	bundle, err := base64.RawURLEncoding.DecodeString(strings.TrimPrefix(registered.Bundle, "rcdev1:"))
	if err != nil {
		t.Fatal(err)
	}
	var device struct {
		Host      string `json:"host"`
		DeviceKey string `json:"device_key"`
		API       struct {
			Port int    `json:"port"`
			Pin  string `json:"pin"`
		} `json:"api"`
		Sub struct {
			Port int    `json:"port"`
			Pin  string `json:"pin"`
		} `json:"sub"`
	}
	if err = json.Unmarshal(bundle, &device); err != nil || device.API.Port == 0 {
		t.Fatalf("package without api: %v", err)
	}
	endpoint := Endpoint{Host: device.Host, Port: device.API.Port, Pin: device.API.Pin, Identity: registered.Identity, SPA: invite.SPA}
	endpoint.SPA.DeviceKey = device.DeviceKey

	self, err := Call(ctx, endpoint, DeviceService+"GetSelf", nil)
	if err != nil {
		t.Fatalf("GetSelf: %v", err)
	}
	if !strings.Contains(string(self), `"admin"`) {
		t.Fatalf("GetSelf: %s", self)
	}
	status, err := Call(ctx, endpoint, NodeService+"GetStatus", nil)
	if err != nil {
		t.Fatalf("GetStatus: %v", err)
	}
	t.Logf("GetStatus: %s", status)
	users, err := Call(ctx, endpoint, NodeService+"ListUsers", nil)
	if err != nil || !strings.Contains(string(users), `"interop"`) {
		t.Fatalf("ListUsers: %s, %v", users, err)
	}
	// what the admin mode sends: int64 as strings, lower camel case
	invited, err := Call(ctx, endpoint, NodeService+"CreateInvite",
		[]byte(`{"user":"interop","name":"second","role":"user","ttlSeconds":"3600"}`))
	if err != nil || !strings.Contains(string(invited), `"inviteCode":"rcinv1:`) {
		t.Fatalf("CreateInvite: %s, %v", invited, err)
	}

	// the channel probe (probe-v1) on the subscription endpoint, directly: down with the server's Probe-Result,
	// up with the server's count, the UDP echo with its session count, link
	if device.Sub.Port == 0 {
		t.Fatal("package without sub")
	}
	deviceKey, err := hex.DecodeString(device.DeviceKey)
	if err != nil {
		t.Fatal(err)
	}
	serverName := device.Host
	if net.ParseIP(serverName) != nil {
		serverName = ""
	}
	pc := rayprobe.NewClient(&rayprobe.Endpoint{
		Address: net.JoinHostPort(device.Host, strconv.Itoa(device.Sub.Port)), Pin: device.Sub.Pin, ServerName: serverName,
		DeviceID: registered.DeviceID, DeviceKey: deviceKey, Dial: (&net.Dialer{}).DialContext,
	})
	defer pc.Close()
	down := pc.Down(ctx, 1<<20, probe.Options{WindowMs: 20000})
	if !down.OK || down.Bytes != 1<<20 || down.Server["bytes"] != int64(1<<20) {
		t.Fatalf("probe down: %+v", down)
	}
	t.Logf("probe down: %d kbit/s, server %v", down.Kbps, down.Server)
	up := pc.Up(ctx, 512<<10, 20*time.Second)
	if !up.OK || up.Bytes != 512<<10 {
		t.Fatalf("probe up: %+v", up)
	}
	t.Logf("probe up: %d kbit/s (server), tcp %v", up.Kbps, up.TCP)
	echo := pc.Echo(ctx, 20, 1200, 20*time.Millisecond)
	if !echo.OK || !echo.Counted || echo.LostUp != 0 {
		t.Fatalf("probe echo: %+v", echo)
	}
	t.Logf("probe echo: %s", echo)
	link, err := pc.Link(ctx)
	if err != nil {
		t.Fatalf("probe link: %v", err)
	}
	t.Logf("probe link: %s", link)

	// the invite is spent: a second Register is refused
	again := invite
	if _, err = Register(ctx, again, nil); !isCode(err, "not_found") {
		t.Fatalf("second Register: %v, want not_found", err)
	}

	// a new client key; the old one stops working at once
	next, err := NewIdentity()
	if err != nil {
		t.Fatal(err)
	}
	if err = RotateCertificate(ctx, endpoint, next); err != nil {
		t.Fatalf("RotateCertificate: %v", err)
	}
	if _, err = Call(ctx, endpoint, DeviceService+"GetSelf", nil); !isCode(err, "unauthenticated") && !errors.Is(err, ErrNoAnswer) {
		t.Fatalf("old key after rotation: %v", err)
	}
	endpoint.Identity = next
	if _, err = Call(ctx, endpoint, DeviceService+"GetSelf", nil); err != nil {
		t.Fatalf("new key: %v", err)
	}
}

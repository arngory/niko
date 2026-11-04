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
	// link is the server's view of the device's address; from the node's own address (here loopback, as through a
	// tunnel) the answer is 409 (probe-v1.md §4.1)
	link, err := pc.Link(ctx)
	if ip := net.ParseIP(device.Host); ip != nil && ip.IsLoopback() {
		if status, ok := err.(*rayprobe.StatusError); !ok || status.Status != 409 {
			t.Fatalf("probe link from loopback: %s, %v, want 409", link, err)
		}
		t.Logf("probe link from loopback: 409, as through a tunnel")
	} else if err != nil {
		t.Fatalf("probe link: %v", err)
	} else {
		t.Logf("probe link: %s", link)
	}

	// the anonymous summary (probe-v1.md §6.1): about the network it comes from, so from loopback (as through a
	// tunnel) 409; from the runner's own address 404 while the node takes none, 204, then 429 for the day
	checkReport(ctx, t, endpoint, pc, net.ParseIP(device.Host).IsLoopback(), &rayprobe.Endpoint{
		Address: net.JoinHostPort(device.Host, strconv.Itoa(device.Sub.Port)), Pin: device.Sub.Pin, ServerName: serverName,
		DeviceID: registered.DeviceID, DeviceKey: deviceKey, Dial: (&net.Dialer{}).DialContext,
	}, device.Sub.Port)

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

// checkReport sends a summary for the user's api service, the admin turning the node's summaries on in between.
func checkReport(ctx context.Context, t *testing.T, admin Endpoint, nodeClient *rayprobe.Client, nodeLoopback bool, e *rayprobe.Endpoint, port int) {
	t.Helper()
	services, err := Call(ctx, admin, NodeService+"ListServices", []byte(`{}`))
	if err != nil {
		t.Fatalf("ListServices: %v", err)
	}
	var listed struct {
		Services []struct {
			ID   int64  `json:"id"`
			Name string `json:"name"`
		} `json:"services"`
	}
	if err = json.Unmarshal(services, &listed); err != nil {
		t.Fatalf("ListServices: %s: %v", services, err)
	}
	svc := int64(0)
	for _, s := range listed.Services {
		if s.Name == "api" {
			svc = s.ID
		}
	}
	if svc == 0 {
		t.Fatalf("no api service in %s", services)
	}
	// the node takes observations of the user's own services only
	if _, err = Call(ctx, admin, NodeService+"SetUserServices", []byte(`{"name":"interop","services":["api"]}`)); err != nil {
		t.Fatalf("SetUserServices: %v", err)
	}
	report := []byte(`{"v":1,"net":"wifi","obs":[{"svc":` + strconv.FormatInt(svc, 10) + `,"result":"works","udp":"0-1","mbps":"5-20"}]}`)
	status := func(err error) int {
		if s, ok := err.(*rayprobe.StatusError); ok {
			return s.Status
		}
		if err != nil {
			t.Fatalf("report: %v", err)
		}
		return 204
	}
	// to the node's address on loopback the request comes from loopback too, as through a tunnel
	if nodeLoopback {
		if got := status(nodeClient.Report(ctx, report)); got != 409 {
			t.Fatalf("report from loopback: %d, want 409", got)
		}
	}
	// the runner's own address: not loopback, a private one (the node counts it under AS 0)
	var own string
	addrs, _ := net.InterfaceAddrs()
	for _, a := range addrs {
		if ip, ok := a.(*net.IPNet); ok && ip.IP.To4() != nil && !ip.IP.IsLoopback() {
			own = ip.IP.String()
			break
		}
	}
	if own == "" {
		t.Fatal("no address of the runner besides loopback")
	}
	direct := *e
	direct.Address = net.JoinHostPort(own, strconv.Itoa(port))
	direct.ServerName = ""
	c := rayprobe.NewClient(&direct)
	defer c.Close()
	if got := status(c.Report(ctx, report)); got != 404 {
		t.Fatalf("report while the node takes none: %d, want 404", got)
	}
	if _, err = Call(ctx, admin, NodeService+"SetSummary", []byte(`{"enabled":"on"}`)); err != nil {
		t.Fatalf("SetSummary on: %v", err)
	}
	if got := status(c.Report(ctx, report)); got != 204 {
		t.Fatalf("report: %d, want 204", got)
	}
	err = c.Report(ctx, report)
	if s, ok := err.(*rayprobe.StatusError); !ok || s.Status != 429 || s.RetryAfter <= 0 {
		t.Fatalf("second report of the day: %v, want 429 with Retry-After", err)
	}
	// a service the user does not have: 400, and the day's report stays the one taken
	bad := []byte(`{"v":1,"net":"wifi","obs":[{"svc":` + strconv.FormatInt(svc+1000, 10) + `,"result":"fails"}]}`)
	if got := status(c.Report(ctx, bad)); got != 400 {
		t.Fatalf("report of another service: %d, want 400", got)
	}
	summary, err := Call(ctx, admin, NodeService+"GetSummary", []byte(`{}`))
	if err != nil || !strings.Contains(string(summary), `"otherReports":"1"`) {
		t.Fatalf("GetSummary: %s, %v", summary, err)
	}
	t.Logf("report from %s: 404 while off, 204, 429 (Retry-After), 400 for another service; GetSummary %s", own, summary)
	if _, err = Call(ctx, admin, NodeService+"SetSummary", []byte(`{"enabled":"off"}`)); err != nil {
		t.Fatalf("SetSummary off: %v", err)
	}
}

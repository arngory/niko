package rayapi

import (
	"context"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net"
	"os"
	"slices"
	"strconv"
	"strings"
	"testing"
	"time"

	"libcore/knock"
	"libcore/probe"
	"libcore/rayprobe"
	"libcore/raysub"
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
	// the path's MTU by echo sizes (loopback carries them all)
	largest, steps := pc.PathMTU(ctx, []int{1452, 1200, 576}, 3, 10*time.Second)
	if largest != 1452 || len(steps) != 3 || steps[2].Received == 0 {
		t.Fatalf("probe path MTU: %d %+v", largest, steps)
	}
	t.Logf("probe path MTU: %d %+v", largest, steps)
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
	svc, own := checkReport(ctx, t, endpoint, pc, net.ParseIP(device.Host).IsLoopback(), &rayprobe.Endpoint{
		Address: net.JoinHostPort(device.Host, strconv.Itoa(device.Sub.Port)), Pin: device.Sub.Pin, ServerName: serverName,
		DeviceID: registered.DeviceID, DeviceKey: deviceKey, Dial: (&net.Dialer{}).DialContext,
	}, device.Sub.Port)
	// with a test AS database on the node (RAYCONTROL_HINTS, raycontrol v0.5.1): k = 10 reports give the hint
	if os.Getenv("RAYCONTROL_HINTS") != "" {
		checkHints(ctx, t, endpoint, registered, svc, own)
	}
	if _, err = Call(ctx, endpoint, NodeService+"SetSummary", []byte(`{"enabled":"off"}`)); err != nil {
		t.Fatalf("SetSummary off: %v", err)
	}

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

// checkReport sends a summary for the user's hy2-obfs service, the admin turning the node's summaries on in between.
// A service with a profile in the subscription: the node gives hints only for those (internal/sub, R4-21); hy2-obfs
// needs no domain (its certificate is its own).
// It returns the service reported on and the runner's own address, the summaries left on.
func checkReport(ctx context.Context, t *testing.T, admin Endpoint, nodeClient *rayprobe.Client, nodeLoopback bool, e *rayprobe.Endpoint, port int) (int64, string) {
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
		if s.Name == "hy2-obfs" {
			svc = s.ID
		}
	}
	if svc == 0 {
		t.Fatalf("no hy2-obfs service in %s", services)
	}
	// the node takes observations of the user's own services only
	if _, err = Call(ctx, admin, NodeService+"SetUserServices", []byte(`{"name":"interop","services":["api","hy2-obfs"]}`)); err != nil {
		t.Fatalf("SetUserServices: %v", err)
	}
	// udp_mtu: an optional field the node lists in probe.report (raycontrol v0.8), checked with the hint
	report := []byte(`{"v":1,"net":"wifi","udp_mtu":"1350-1472","obs":[{"svc":` + strconv.FormatInt(svc, 10) + `,"result":"works","udp":"0-1","mbps":"5-20"}]}`)
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
	return svc, own
}

// bundle is what a test needs of an rcdev1 package.
type bundle struct {
	Host      string             `json:"host"`
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

func parseBundle(t *testing.T, code string) bundle {
	t.Helper()
	raw, err := base64.RawURLEncoding.DecodeString(strings.TrimPrefix(code, "rcdev1:"))
	if err != nil {
		t.Fatal(err)
	}
	var b bundle
	if err = json.Unmarshal(raw, &b); err != nil || b.Sub.Port == 0 {
		t.Fatalf("package: %v", err)
	}
	b.SPA.DeviceKey = b.DeviceKey
	return b
}

// newDevice invites a device of the user interop and registers it.
func newDevice(ctx context.Context, t *testing.T, admin Endpoint, name string) (*Registered, bundle) {
	t.Helper()
	raw, err := Call(ctx, admin, NodeService+"CreateInvite",
		[]byte(`{"user":"interop","name":"`+name+`","role":"user","ttlSeconds":"3600"}`))
	if err != nil {
		t.Fatalf("CreateInvite %s: %v", name, err)
	}
	var created struct {
		InviteCode string `json:"inviteCode"`
	}
	if err = json.Unmarshal(raw, &created); err != nil {
		t.Fatal(err)
	}
	body, err := base64.RawURLEncoding.DecodeString(strings.TrimPrefix(created.InviteCode, "rcinv1:"))
	if err != nil {
		t.Fatal(err)
	}
	var invite Invite
	var key struct {
		DeviceKey string `json:"device_key"`
	}
	if err = json.Unmarshal(body, &invite); err != nil {
		t.Fatal(err)
	}
	if err = json.Unmarshal(body, &key); err != nil {
		t.Fatal(err)
	}
	invite.SPA.DeviceKey = key.DeviceKey
	registered, err := Register(ctx, invite, nil)
	if err != nil {
		t.Fatalf("Register %s: %v", name, err)
	}
	return registered, parseBundle(t, registered.Bundle)
}

// checkHints: nine more devices report from the runner's address, which the node's test AS database puts in an AS;
// with the first device's report that is k = 10, and the subscription asked from that address carries the hint
// (probe-v1.md §6.2). Asked from the node's own address it carries none.
func checkHints(ctx context.Context, t *testing.T, admin Endpoint, first *Registered, svc int64, own string) {
	t.Helper()
	report := []byte(`{"v":1,"net":"cellular","udp_mtu":"1350-1472","obs":[{"svc":` + strconv.FormatInt(svc, 10) + `,"result":"works","udp":"none","mbps":"1-5"}]}`)
	for i := 2; i <= 10; i++ {
		registered, b := newDevice(ctx, t, admin, "hint-"+strconv.Itoa(i))
		key, err := hex.DecodeString(b.DeviceKey)
		if err != nil {
			t.Fatal(err)
		}
		c := rayprobe.NewClient(&rayprobe.Endpoint{
			Address: net.JoinHostPort(own, strconv.Itoa(b.Sub.Port)), Pin: b.Sub.Pin,
			DeviceID: registered.DeviceID, DeviceKey: key, Dial: (&net.Dialer{}).DialContext,
		})
		err = c.Report(ctx, report)
		c.Close()
		if err != nil {
			t.Fatalf("report of device %d: %v", i, err)
		}
	}
	b := parseBundle(t, first.Bundle)
	request := raysub.Request{
		Host: own, Port: b.Sub.Port, Pin: b.Sub.Pin, DeviceID: first.DeviceID, NodeID: b.SPA.NodeID,
		Ed25519: b.NodeKeys.Ed25519, MLDSA65: b.NodeKeys.MLDSA65, SPA: b.SPA,
	}
	result, err := raysub.Fetch(ctx, request)
	if err != nil {
		t.Fatalf("subscription from %s: %v", own, err)
	}
	h := result.Payload.Hints
	// nine of the ten said UDP does not pass: over the 60 % majority; all ten carried the same UDP MTU bucket
	if h == nil || h.Devices < 10 || !slices.Contains(h.Order, uint16(svc)) || !h.UDPBlocked || h.UDPMTU != "1350-1472" {
		t.Fatalf("subscription from %s: hints %+v, want order with %d, udp_blocked and udp_mtu over 10 reports", own, h, svc)
	}
	// the node lists the optional field the client may send
	if p := result.Payload.Probe; p == nil || !slices.Contains(p.Report, "udp_mtu") {
		t.Fatalf("subscription's probe without report [udp_mtu]: %+v", p)
	}
	t.Logf("hints from %s after 10 reports: %+v", own, *h)
	// from the node's own address (as through a tunnel): no hint
	request.Host = b.Host
	if result, err = raysub.Fetch(ctx, request); err != nil {
		t.Fatalf("subscription from %s: %v", b.Host, err)
	}
	if net.ParseIP(b.Host).IsLoopback() && result.Payload.Hints != nil {
		t.Fatalf("subscription from the node's own address carries hints: %+v", *result.Payload.Hints)
	}
}

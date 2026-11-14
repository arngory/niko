package libcore

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"os"
	"os/exec"
	"slices"
	"strings"
	"testing"
	"time"

	"libcore/raysub"

	M "github.com/sagernet/sing/common/metadata"
	"github.com/yggdrasil-network/yggdrasil-go/src/core"
)

// lanInfo is the subscription's `lan` (lan-v1 §1.2) as the app reads it.
type lanInfo struct {
	V        int             `json:"v"`
	Svc      uint16          `json:"svc"`
	Relay    int             `json:"relay"`
	Label    string          `json:"label"`
	Password string          `json:"password"`
	Devices  []lanDeviceInfo `json:"devices"`
}

type lanDeviceInfo struct {
	ID    string `json:"id"`
	Name  string `json:"name"`
	Label string `json:"label"`
	Addr  string `json:"addr"`
	Next  *struct {
		Addr string `json:"addr"`
	} `json:"next"`
}

// String is the lan for the log: the password never in it (no secrets in CI logs), only whether there is one.
func (l *lanInfo) String() string {
	if l == nil {
		return "none"
	}
	ids := make([]string, 0, len(l.Devices))
	for _, d := range l.Devices {
		ids = append(ids, d.ID+"="+d.Label+"@"+d.Addr)
	}
	return fmt.Sprintf("v%d svc %d relay %d label %s password %v devices [%s]", l.V, l.Svc, l.Relay, l.Label,
		l.Password != "", strings.Join(ids, " "))
}

func (l *lanInfo) has(id string) bool {
	return l != nil && slices.ContainsFunc(l.Devices, func(d lanDeviceInfo) bool { return d.ID == id })
}

// addresses are the device's current and next addresses as the list gives them.
func (l *lanInfo) addresses(id string) []string {
	for _, d := range l.Devices {
		if d.ID == id {
			if d.Next != nil && d.Next.Addr != "" {
				return []string{d.Addr, d.Next.Addr}
			}
			return []string{d.Addr}
		}
	}
	return nil
}

// lanDevice is a device of the interop as the app runs it on a LAN: its package, the subscription request with its
// X-Raycontrol-LAN, the Yggdrasil profile of `lan.svc`.
type lanDevice struct {
	name    string
	bundle  interopBundle
	node    string
	request raysub.Request
}

func newLANDevice(t *testing.T, name, path string) *lanDevice {
	t.Helper()
	b, node, raw := interopDeviceAt(t, path)
	d := &lanDevice{name: name, bundle: b, node: node}
	if err := json.Unmarshal(raw, &d.request); err != nil {
		t.Fatal(err)
	}
	return d
}

// fetch asks the subscription with lan ("on", "off", "" none) until ok or the time is up: the payload and its `lan`.
func (d *lanDevice) fetch(t *testing.T, what, lan string, within time.Duration, ok func(raysub.Result, *lanInfo) bool) (raysub.Result, *lanInfo) {
	t.Helper()
	d.request.LAN = lan
	request, _ := json.Marshal(d.request)
	var (
		result raysub.Result
		info   *lanInfo
		err    error
	)
	for deadline := time.Now().Add(within); ; time.Sleep(5 * time.Second) {
		var appJSON string
		appJSON, err = RaycontrolFetchSubscription(string(request))
		if err == nil {
			var app struct {
				Payload struct {
					LAN *lanInfo `json:"lan"`
				} `json:"payload"`
			}
			result, info = raysub.Result{}, nil
			if json.Unmarshal([]byte(appJSON), &result) == nil && json.Unmarshal([]byte(appJSON), &app) == nil {
				info = app.Payload.LAN
				if ok(result, info) {
					t.Logf("%s (%s): lan %s", what, d.name, info)
					return result, info
				}
			}
		}
		if time.Now().After(deadline) {
			t.Fatalf("%s (%s): not in %v (lan %s, %v)", what, d.name, within, info, err)
		}
	}
}

// config is the device's core as the app builds it for the LAN: the mesh node of the svc's Yggdrasil profile with
// DebugPrivacy, flows only from allowed (nil: every one, the node's filter alone), and the mesh's connections to
// port on 127.0.0.1, nothing else.
func (d *lanDevice) config(t *testing.T, result raysub.Result, svc uint16, allowed []string, port int) string {
	t.Helper()
	for _, p := range result.Payload.Profiles {
		if p.Svc != svc || !strings.Contains(p.Link, "niko-ygg=") {
			continue
		}
		kind, raw := profileConfig(t, p.Link, d.node, "", os.Getenv("RAYCONTROL_CA"), d.bundle.DeviceKey, 50)
		if kind != "yggdrasil" || raw == "" {
			t.Fatalf("%s: svc %d gives %s", d.name, svc, kind)
		}
		var c map[string]any
		if err := json.Unmarshal([]byte(raw), &c); err != nil {
			t.Fatal(err)
		}
		endpoint := c["endpoints"].([]any)[0].(map[string]any)
		endpoint["debug_privacy"] = true
		if allowed != nil {
			sources := []string{}
			for _, a := range allowed {
				sources = append(sources, a+"/128")
			}
			endpoint["inbound_listed"] = true
			endpoint["inbound_sources"] = sources
		}
		c["outbounds"] = append(c["outbounds"].([]any), map[string]any{"type": "direct", "tag": "direct"})
		c["route"] = map[string]any{"final": "proxy", "rules": []any{
			map[string]any{"inbound": []string{"ygg"}, "port": []int{port}, "outbound": "direct"},
			map[string]any{"inbound": []string{"ygg"}, "action": "reject"},
		}}
		out, _ := json.Marshal(c)
		return string(out)
	}
	t.Fatalf("%s: no Yggdrasil profile of svc %d", d.name, svc)
	return ""
}

// TestRaycontrolLAN (lan-v1, raycontrol v0.23.0 with niko-server v0.16.0), run by raycontrol-interop.yml: two
// devices of one user show themselves and get each other's addresses; through the node each reaches a port the
// other opened, on its 127.0.0.1. A device of another user does not, even where the device itself would let it in
// (the node's filter); lan_relay=0 closes the path through the node; a device that hides leaves the other's list.
//
// RAYCONTROL_BUNDLE and RAYCONTROL_LAN_BUNDLE are the user's two devices, RAYCONTROL_LAN_STRANGER a device of
// another user with Yggdrasil (RAYCONTROL_LAN_STRANGER_USER, with a LAN of their own); RAYCONTROL_RAYCTL runs rayctl on
// the node, RAYCONTROL_USER is the user, RAYCONTROL_LAN_SERVICE the Yggdrasil service.
func TestRaycontrolLAN(t *testing.T) {
	rayctlCommand := strings.Fields(os.Getenv("RAYCONTROL_RAYCTL"))
	user, service := os.Getenv("RAYCONTROL_USER"), os.Getenv("RAYCONTROL_LAN_SERVICE")
	second, stranger, strangerUser := os.Getenv("RAYCONTROL_LAN_BUNDLE"), os.Getenv("RAYCONTROL_LAN_STRANGER"), os.Getenv("RAYCONTROL_LAN_STRANGER_USER")
	if len(rayctlCommand) == 0 || user == "" || service == "" || second == "" || stranger == "" || strangerUser == "" || os.Getenv("RAYCONTROL_BUNDLE") == "" {
		t.Skip("RAYCONTROL_RAYCTL, RAYCONTROL_USER, RAYCONTROL_LAN_* and RAYCONTROL_BUNDLE not set (raycontrol-interop.yml sets them)")
	}
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
	t.Cleanup(func() {
		for _, args := range [][]string{{"service", "set", service, "lan_relay=20"}, {"user", "lan", user, "off"}, {"user", "lan", strangerUser, "off"}} {
			if out, err := rayctlRun(args...); err != nil {
				t.Logf("rayctl %s: %v: %s", strings.Join(args, " "), err, out)
			}
		}
	})

	phone := newLANDevice(t, "phone", os.Getenv("RAYCONTROL_BUNDLE"))
	laptop := newLANDevice(t, "laptop", second)
	other := newLANDevice(t, "stranger", stranger)

	// not allowed yet: no `lan`, whatever the device says
	phone.fetch(t, "before user lan on", "on", 30*time.Second, func(_ raysub.Result, l *lanInfo) bool { return l == nil })
	rayctl("user", "lan", user, "on")
	// allowed and shown: the password and the list; each sees the other once both have shown themselves
	phone.fetch(t, "phone shows itself", "on", 90*time.Second, func(_ raysub.Result, l *lanInfo) bool {
		return l != nil && l.V == 1 && l.Password != "" && l.has(phone.bundle.DeviceID)
	})
	laptopResult, laptopLAN := laptop.fetch(t, "laptop sees the phone", "on", 90*time.Second, func(_ raysub.Result, l *lanInfo) bool {
		return l != nil && l.Password != "" && l.has(phone.bundle.DeviceID) && l.has(laptop.bundle.DeviceID)
	})
	phoneResult, phoneLAN := phone.fetch(t, "phone sees the laptop", "on", 90*time.Second, func(_ raysub.Result, l *lanInfo) bool {
		return l != nil && l.has(laptop.bundle.DeviceID)
	})
	if phoneLAN.Password != laptopLAN.Password || phoneLAN.Svc != laptopLAN.Svc || phoneLAN.Label != laptopLAN.Label {
		t.Fatalf("one user, two LANs: %s / %s (one password: %v)", phoneLAN, laptopLAN, phoneLAN.Password == laptopLAN.Password)
	}
	// symmetry (§1.1): a device that never showed itself gets no password and no list
	_, strangerLAN := other.fetch(t, "another user, not shown", "", 30*time.Second, func(raysub.Result, *lanInfo) bool { return true })
	if strangerLAN != nil && (strangerLAN.Password != "" || len(strangerLAN.Devices) > 0) {
		t.Fatalf("another user's device got %s", strangerLAN)
	}
	// the other user with a LAN of their own, shown: a group of its own on the node, which must not reach alice's
	rayctl("user", "lan", strangerUser, "on")
	strangerResult, strangerLAN := other.fetch(t, "another user shows itself", "on", 90*time.Second, func(_ raysub.Result, l *lanInfo) bool {
		return l != nil && l.Password != "" && l.has(other.bundle.DeviceID)
	})
	if strangerLAN.has(phone.bundle.DeviceID) || strangerLAN.has(laptop.bundle.DeviceID) || strangerLAN.Password == phoneLAN.Password {
		t.Fatalf("another user's LAN holds alice's: %s", strangerLAN)
	}

	// the port each opens to its other devices, on 127.0.0.1
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer listener.Close()
	go func() {
		for {
			conn, err := listener.Accept()
			if err != nil {
				return
			}
			go func() {
				defer conn.Close()
				io.Copy(conn, conn)
			}()
		}
	}()
	port := listener.Addr().(*net.TCPAddr).Port
	phoneAddrs := phoneLAN.addresses(phone.bundle.DeviceID)
	laptopAddrs := laptopLAN.addresses(laptop.bundle.DeviceID)
	phoneBox := startTestBox(t, phone.config(t, phoneResult, phoneLAN.Svc, laptopAddrs, port))
	laptopBox := startTestBox(t, laptop.config(t, laptopResult, laptopLAN.Svc, phoneAddrs, port))

	// why the last attempt did not reach, for the failure's message
	var unreached error
	reaches := func(from *BoxInstance, addr string) bool {
		endpoint, ok := from.Box.Endpoint().Get("ygg")
		if !ok {
			t.Fatal("no endpoint ygg")
		}
		ctx, cancel := context.WithTimeout(context.Background(), 8*time.Second)
		defer cancel()
		conn, err := endpoint.DialContext(ctx, "tcp", M.ParseSocksaddrHostPort(addr, uint16(port)))
		if err != nil {
			unreached = err
			return false
		}
		defer conn.Close()
		conn.SetDeadline(time.Now().Add(8 * time.Second))
		if _, err = conn.Write([]byte("lan-v1")); err != nil {
			unreached = err
			return false
		}
		echo := make([]byte, 6)
		_, err = io.ReadFull(conn, echo)
		unreached = err
		return err == nil && string(echo) == "lan-v1"
	}
	// the links of a mesh node as its peers show them: up or not, and for how long
	links := func(b *BoxInstance) string {
		endpoint, _ := b.Box.Endpoint().Get("ygg")
		var out []string
		for _, p := range endpoint.(interface{ Peers() []core.PeerInfo }).Peers() {
			out = append(out, fmt.Sprintf("up %v for %v", p.Up, p.Uptime.Round(time.Second)))
		}
		return fmt.Sprintf("%d links [%s]", len(out), strings.Join(out, "; "))
	}
	reachesWithin := func(what string, from *BoxInstance, addr string, within time.Duration) {
		t.Helper()
		for deadline := time.Now().Add(within); !reaches(from, addr); time.Sleep(2 * time.Second) {
			if time.Now().After(deadline) {
				t.Fatalf("%s: [%s]:%d not reached in %v (the last attempt: %v; the dialing side's %s)",
					what, addr, port, within, unreached, links(from))
			}
		}
		t.Logf("%s: [%s]:%d answers", what, addr, port)
	}
	// both mesh nodes' links to the node up again, for a while: a cut while the node's backend restarts is not what a
	// check after it should see
	linksUp := func(what string, boxes ...*BoxInstance) {
		t.Helper()
		for deadline := time.Now().Add(120 * time.Second); ; time.Sleep(2 * time.Second) {
			up := true
			for _, b := range boxes {
				endpoint, _ := b.Box.Endpoint().Get("ygg")
				peers := endpoint.(interface{ Peers() []core.PeerInfo }).Peers()
				up = up && slices.ContainsFunc(peers, func(p core.PeerInfo) bool { return p.Up && p.Uptime > 10*time.Second })
			}
			if up {
				return
			}
			if time.Now().After(deadline) {
				t.Fatalf("%s: the links to the node not up in 120 s", what)
			}
		}
	}
	// never: not reached for as long as a path that is there takes to show (a minute: after the same events the
	// positive checks answered within seconds)
	never := func(what string, from *BoxInstance, addr string) {
		t.Helper()
		for deadline := time.Now().Add(60 * time.Second); time.Now().Before(deadline); time.Sleep(2 * time.Second) {
			if reaches(from, addr) {
				t.Fatalf("%s: [%s]:%d answered", what, addr, port)
			}
		}
		t.Logf("%s: [%s]:%d not reached in a minute, as it should be", what, addr, port)
	}
	reachesWithin("phone → laptop through the node", phoneBox, laptopAddrs[0], 120*time.Second)
	reachesWithin("laptop → phone through the node", laptopBox, phoneAddrs[0], 60*time.Second)

	// the laptop's own filter: it lets the phone in no more (an empty list), the path to it warm: the phone's flows
	// get nothing; then the phone again, which must answer within the same budget
	laptopBox.Close()
	closed := startTestBox(t, laptop.config(t, laptopResult, laptopLAN.Svc, []string{}, port))
	linksUp("the laptop closed", phoneBox, closed)
	never("phone → laptop, the laptop letting none in", phoneBox, laptopAddrs[0])
	closed.Close()

	// another user's device: the laptop lets every flow in now, only the node's filter stands between them
	strangerSvc := uint16(0)
	for _, p := range strangerResult.Payload.Profiles {
		if strings.Contains(p.Link, "niko-ygg=") {
			strangerSvc = p.Svc
		}
	}
	if strangerSvc == 0 {
		t.Fatal("another user without a Yggdrasil profile")
	}
	open := startTestBox(t, laptop.config(t, laptopResult, laptopLAN.Svc, nil, port))
	strangerBox := startTestBox(t, other.config(t, strangerResult, strangerSvc, []string{}, port))
	// the laptop's link to the node first, as after its restart above: a path through a link not yet up is not there
	linksUp("the laptop open", phoneBox, open)
	reachesWithin("phone → laptop, the laptop open to all", phoneBox, laptopAddrs[0], 120*time.Second)
	linksUp("another user", strangerBox, open)
	never("another user → laptop", strangerBox, laptopAddrs[0])
	reachesWithin("phone → laptop, the other user's check done", phoneBox, laptopAddrs[0], 60*time.Second)
	open.Close()
	strangerBox.Close()

	// the laptop as the app runs it again; through the node closed (lan_relay=0): only one Wi-Fi would do
	laptopBox = startTestBox(t, laptop.config(t, laptopResult, laptopLAN.Svc, phoneAddrs, port))
	reachesWithin("phone → laptop again", phoneBox, laptopAddrs[0], 120*time.Second)
	rayctl("service", "set", service, "lan_relay=0")
	phone.fetch(t, "relay 0 announced", "on", 90*time.Second, func(_ raysub.Result, l *lanInfo) bool { return l != nil && l.Relay == 0 })
	for deadline := time.Now().Add(90 * time.Second); reaches(phoneBox, laptopAddrs[0]); time.Sleep(3 * time.Second) {
		if time.Now().After(deadline) {
			t.Fatal("lan_relay=0: the node still carries phone → laptop after 90 s")
		}
	}
	linksUp("lan_relay=0", phoneBox, laptopBox)
	never("lan_relay=0, the links up", phoneBox, laptopAddrs[0])
	rayctl("service", "set", service, "lan_relay=20")
	reachesWithin("lan_relay=20 again", phoneBox, laptopAddrs[0], 180*time.Second)

	// the laptop hides: gone from the phone's list with the phone's next fetch, and no longer reached
	laptop.fetch(t, "laptop hides", "off", 60*time.Second, func(_ raysub.Result, l *lanInfo) bool {
		return l != nil && l.Password == "" && len(l.Devices) == 0
	})
	phone.fetch(t, "phone's list without the laptop", "on", 60*time.Second, func(_ raysub.Result, l *lanInfo) bool {
		return l != nil && l.Password != "" && !l.has(laptop.bundle.DeviceID)
	})
	for deadline := time.Now().Add(90 * time.Second); reaches(phoneBox, laptopAddrs[0]); time.Sleep(3 * time.Second) {
		if time.Now().After(deadline) {
			t.Fatal("hidden: the node still carries phone → laptop after 90 s")
		}
	}
	linksUp("hidden", phoneBox, laptopBox)
	never("hidden, the links up", phoneBox, laptopAddrs[0])
}

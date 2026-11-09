package libcore

import (
	"context"
	"encoding/json"
	"errors"
	"io"
	"net"
	"os"
	"strings"
	"testing"
	"time"

	"libcore/raysub"

	M "github.com/sagernet/sing/common/metadata"
)

// TestRaycontrolConnLimit (conn-limit-v1 parts 2 and 3, raycontrol v0.25.0), run by raycontrol-interop.yml on its
// node: a user holds as many TCP connections open through Hysteria2, then through REALITY, as the node lets one user
// have, until new ones are refused. Through Hysteria2 the refusal reads "too many connections" (the words the app's
// test run takes for the node's limit); through REALITY the connection closes without data, as the node has no code
// to say more. Meanwhile another user's connections go through, the held ones still carry, and with a few closed a
// new one goes through again: the profile is not broken.
//
// RAYCONTROL_LIMIT_TARGET is an address of the runner that the node's exits dial (outside the ranges they keep
// out); RAYCONTROL_LIMIT_OTHER is the package of another user with the same services; RAYCONTROL_BUNDLE,
// RAYCONTROL_ADDRESS and RAYCONTROL_CA as for TestRaycontrolTunnelInterop.
func TestRaycontrolConnLimit(t *testing.T) {
	target, otherPath := os.Getenv("RAYCONTROL_LIMIT_TARGET"), os.Getenv("RAYCONTROL_LIMIT_OTHER")
	if target == "" || otherPath == "" {
		t.Skip("RAYCONTROL_LIMIT_TARGET and RAYCONTROL_LIMIT_OTHER not set (raycontrol-interop.yml sets them)")
	}
	// the destination: an echo on every address of the runner
	listener, err := net.Listen("tcp", ":0")
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
	destination := M.ParseSocksaddrHostPort(target, uint16(listener.Addr().(*net.TCPAddr).Port))

	b, node, request := interopDevice(t)
	_, _, otherRequest := interopDeviceAt(t, otherPath)
	configs := func(request []byte) map[string]string {
		t.Helper()
		appJSON, err := RaycontrolFetchSubscription(string(request))
		if err != nil {
			t.Fatalf("subscription: %v", err)
		}
		var result raysub.Result
		if err = json.Unmarshal([]byte(appJSON), &result); err != nil {
			t.Fatal(err)
		}
		found := map[string]string{}
		for _, p := range result.Payload.Profiles {
			kind, config := profileConfig(t, p.Link, node, "", os.Getenv("RAYCONTROL_CA"), b.DeviceKey, 50)
			// the first of a kind: hy2-obfs and hy2-masq are one process's users alike
			if strings.HasPrefix(kind, "hysteria2") {
				kind = "hysteria2"
			}
			if config != "" && found[kind] == "" {
				found[kind] = config
			}
		}
		return found
	}
	mine, theirs := configs(request), configs(otherRequest)

	// open dials destination through the profile and has it echo: a connection the node let through and holds
	open := func(instance *BoxInstance) (net.Conn, error) {
		outbound, _ := instance.Box.Outbound().Outbound("proxy")
		ctx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		conn, err := outbound.DialContext(ctx, "tcp", destination)
		if err != nil {
			return nil, err
		}
		conn.SetDeadline(time.Now().Add(5 * time.Second))
		echo := make([]byte, 2)
		if _, err = conn.Write([]byte("ok")); err == nil {
			_, err = io.ReadFull(conn, echo)
		}
		if err == nil && string(echo) != "ok" {
			err = errors.New("not the echo")
		}
		if err != nil {
			conn.Close()
			return nil, err
		}
		conn.SetDeadline(time.Time{})
		return conn, nil
	}
	carries := func(conn net.Conn) bool {
		conn.SetDeadline(time.Now().Add(5 * time.Second))
		defer conn.SetDeadline(time.Time{})
		echo := make([]byte, 4)
		if _, err := conn.Write([]byte("more")); err != nil {
			return false
		}
		_, err := io.ReadFull(conn, echo)
		return err == nil && string(echo) == "more"
	}

	for _, kind := range []string{"hysteria2", "reality"} {
		if mine[kind] == "" || theirs[kind] == "" {
			t.Fatalf("%s: no profile of the kind for both users (%d and %d profiles)", kind, len(mine), len(theirs))
		}
		instance := startTestBox(t, mine[kind])
		var held []net.Conn
		var refused []error
		// the attempts' bucket holds 600 and comes back at 30 a second: past its first part, a pace under that
		for len(held) < 1200 && len(refused) < 3 {
			conn, err := open(instance)
			if err != nil {
				if len(held) == 0 {
					t.Fatalf("%s: the first connection: %v", kind, err)
				}
				refused = append(refused, err)
				continue
			}
			held = append(held, conn)
			if len(held) > 500 {
				time.Sleep(40 * time.Millisecond)
			}
		}
		closeAll := func() {
			for _, conn := range held {
				conn.Close()
			}
			held = nil
		}
		if len(refused) < 3 {
			closeAll()
			t.Fatalf("%s: %d connections held and none refused: no limit of one user's connections", kind, len(held))
		}
		t.Logf("%s: %d connections held, then refused: %v", kind, len(held), refused)
		if len(held) < 512 {
			closeAll()
			t.Fatalf("%s: refused at %d connections, want the limit (1024) or near it", kind, len(held))
		}
		if kind == "hysteria2" {
			said := false
			for _, err := range refused {
				said = said || strings.Contains(err.Error(), "too many connections")
			}
			if !said {
				closeAll()
				t.Fatalf("%s: refused without \"too many connections\": %v", kind, refused)
			}
		}
		// the held ones still carry; another user's go through
		if !carries(held[0]) || !carries(held[len(held)-1]) {
			closeAll()
			t.Fatalf("%s: the held connections stopped carrying at the limit", kind)
		}
		other := startTestBox(t, theirs[kind])
		conn, err := open(other)
		if err != nil {
			closeAll()
			t.Fatalf("%s: another user's connection while this one is at the limit: %v", kind, err)
		}
		conn.Close()
		other.Close()
		// a few closed: a new one goes through again, within seconds
		for _, conn := range held[:32] {
			conn.Close()
		}
		held = held[32:]
		var again net.Conn
		for deadline := time.Now().Add(20 * time.Second); again == nil; time.Sleep(time.Second) {
			if again, err = open(instance); err != nil && time.Now().After(deadline) {
				closeAll()
				t.Fatalf("%s: 32 closed, still refused 20 s on: %v", kind, err)
			}
		}
		again.Close()
		closeAll()
		instance.Close()
		t.Logf("%s: another user went through at the limit; with 32 closed, a new one did", kind)
	}
}

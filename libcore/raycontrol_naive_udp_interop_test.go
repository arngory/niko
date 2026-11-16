package libcore

import (
	"bytes"
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"net"
	"net/url"
	"os"
	"os/exec"
	"path/filepath"
	"slices"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/protocol/socks"

	"libcore/raysub"
)

// lockedBuffer collects a process's output while the test reads it.
type lockedBuffer struct {
	access sync.Mutex
	buffer bytes.Buffer
}

func (b *lockedBuffer) Write(p []byte) (int, error) {
	b.access.Lock()
	defer b.access.Unlock()
	return b.buffer.Write(p)
}

func (b *lockedBuffer) String() string {
	b.access.Lock()
	defer b.access.Unlock()
	return b.buffer.String()
}

// TestRaycontrolNaiveUDP checks UDP as HTTP/3 datagrams (raycontrol naive-udp-v1) between the core's naive outbound
// (cronet with datagram_stream, a sing-box built here) and the node's handler, run by raycontrol-interop.yml on its
// supervised node:
//   - with the service's udp flag on, the naive line says udp=1, and a datagram and one longer than a QUIC datagram
//     (a capsule) come back from an echo through it, as datagrams (not UoT);
//   - a pair whose first datagram is QUIC sends one over 1252 bytes only where a QUIC datagram carries it (§4.4);
//   - with the flag off the node answers a request as a site does: that socket ends, UDP of the network goes back
//     over UoT, and an echo through UoT comes back.
//
// RAYCONTROL_NAIVE_UDP_BUNDLE is a device of a user with the naive service; RAYCONTROL_RAYCTL, RAYCONTROL_SING_BOX,
// RAYCONTROL_CA, RAYCONTROL_ADDRESS and RAYCONTROL_LIMIT_TARGET as for TestRaycontrolNaiveSNI.
func TestRaycontrolNaiveUDP(t *testing.T) {
	rayctlCommand := strings.Fields(os.Getenv("RAYCONTROL_RAYCTL"))
	bundlePath, singBox, caPath := os.Getenv("RAYCONTROL_NAIVE_UDP_BUNDLE"), os.Getenv("RAYCONTROL_SING_BOX"), os.Getenv("RAYCONTROL_CA")
	if len(rayctlCommand) == 0 || bundlePath == "" || singBox == "" || caPath == "" {
		t.Skip("RAYCONTROL_RAYCTL, RAYCONTROL_NAIVE_UDP_BUNDLE, RAYCONTROL_SING_BOX and RAYCONTROL_CA not set (raycontrol-interop.yml sets them)")
	}
	_, _, request := interopDeviceAt(t, bundlePath)
	rayctl := func(args ...string) {
		t.Helper()
		out, err := exec.Command(rayctlCommand[0], append(slices.Clone(rayctlCommand[1:]), args...)...).CombinedOutput()
		if err != nil {
			t.Fatalf("rayctl %s: %v: %s", strings.Join(args, " "), err, out)
		}
		t.Logf("rayctl %s: %s", strings.Join(args, " "), strings.TrimSpace(string(out)))
	}

	// UDP echoes on every address of the runner
	echo := func() uint16 {
		conn, err := net.ListenPacket("udp", ":0")
		if err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() { conn.Close() })
		go func() {
			b := make([]byte, 65535)
			for {
				n, from, err := conn.ReadFrom(b)
				if err != nil {
					return
				}
				conn.WriteTo(b[:n], from)
			}
		}()
		return uint16(conn.LocalAddr().(*net.UDPAddr).Port)
	}
	target := os.Getenv("RAYCONTROL_LIMIT_TARGET")
	plain := M.ParseSocksaddrHostPort(target, echo())
	quicPair := M.ParseSocksaddrHostPort(target, echo())
	afterOff := M.ParseSocksaddrHostPort(target, echo())

	// the naive line of the subscription, once it says udp=1 (or no longer does)
	line := func(udp bool) string {
		t.Helper()
		deadline := time.Now().Add(90 * time.Second)
		for {
			appJSON, err := RaycontrolFetchSubscription(string(request))
			if err != nil {
				t.Fatalf("subscription: %v", err)
			}
			var result raysub.Result
			if err = json.Unmarshal([]byte(appJSON), &result); err != nil {
				t.Fatal(err)
			}
			for _, p := range result.Payload.Profiles {
				u, err := url.Parse(p.Link)
				if err != nil || u.Scheme != "naive+https" {
					continue
				}
				if (u.Query().Get("udp") == "1") == udp {
					return p.Link
				}
			}
			if time.Now().After(deadline) {
				t.Fatalf("no naive line with udp=1 %v within 90 s", udp)
			}
			time.Sleep(3 * time.Second)
		}
	}

	// a sing-box on the line: a local mixed port, the naive outbound with UDP over TCP and datagrams allowed from the
	// start (no app probes here)
	start := func(link string) (*socks.Client, *lockedBuffer) {
		t.Helper()
		u, err := url.Parse(link)
		if err != nil {
			t.Fatal(err)
		}
		q := u.Query()
		port, _ := strconv.Atoi(u.Port())
		password, _ := u.User.Password()
		tlsOptions := map[string]any{"enabled": true, "server_name": q.Get("sni"), "certificate_path": caPath}
		if pin := q.Get("niko-pin"); pin != "" {
			var pins []string
			for _, one := range strings.Split(pin, ",") {
				sum, err := base64.RawURLEncoding.DecodeString(strings.TrimRight(one, "="))
				if err != nil || len(sum) != 32 {
					t.Fatalf("niko-pin %q: %v", pin, err)
				}
				pins = append(pins, base64.StdEncoding.EncodeToString(sum))
			}
			tlsOptions["certificate_public_key_sha256"] = pins
		}
		local, err := net.Listen("tcp", "127.0.0.1:0")
		if err != nil {
			t.Fatal(err)
		}
		localPort := local.Addr().(*net.TCPAddr).Port
		local.Close()
		config, _ := json.Marshal(map[string]any{
			"log":      map[string]any{"level": "info"},
			"inbounds": []any{map[string]any{"type": "mixed", "tag": "in", "listen": "127.0.0.1", "listen_port": localPort}},
			"outbounds": []any{map[string]any{
				"type": "naive", "tag": "naive", "server": u.Hostname(), "server_port": port,
				"username": u.User.Username(), "password": password, "tls": tlsOptions,
				"udp_over_tcp": map[string]any{"enabled": true, "version": 2},
				"udp_datagram": true, "udp_datagram_allowed": true,
			}},
			"route": map[string]any{"final": "naive"},
		})
		path := filepath.Join(t.TempDir(), "naive-udp.json")
		if err := os.WriteFile(path, config, 0o600); err != nil {
			t.Fatal(err)
		}
		logs := &lockedBuffer{}
		process := exec.Command(singBox, "run", "-c", path)
		process.Stdout, process.Stderr = logs, logs
		if err := process.Start(); err != nil {
			t.Fatal(err)
		}
		t.Cleanup(func() {
			process.Process.Kill()
			process.Wait()
			t.Logf("sing-box: %s", strings.TrimSpace(logs.String()))
		})
		for i := 0; i < 50; i++ {
			if conn, err := net.DialTimeout("tcp", net.JoinHostPort("127.0.0.1", strconv.Itoa(localPort)), time.Second); err == nil {
				conn.Close()
				break
			}
			time.Sleep(100 * time.Millisecond)
		}
		return socks.NewClient(N.SystemDialer, M.ParseSocksaddrHostPort("127.0.0.1", uint16(localPort)), socks.Version5, "", ""), logs
	}
	open := func(client *socks.Client, destination M.Socksaddr) net.PacketConn {
		t.Helper()
		conn, err := client.ListenPacket(context.Background(), destination)
		if err != nil {
			t.Fatalf("UDP associate: %v", err)
		}
		t.Cleanup(func() { conn.Close() })
		return conn
	}
	// roundTrip sends payload and waits for it back; false when nothing came within wait
	roundTrip := func(conn net.PacketConn, destination M.Socksaddr, payload []byte, wait time.Duration) bool {
		t.Helper()
		if _, err := conn.WriteTo(payload, destination.UDPAddr()); err != nil {
			t.Fatalf("write %d bytes: %v", len(payload), err)
		}
		conn.SetReadDeadline(time.Now().Add(wait))
		b := make([]byte, 65535)
		for {
			n, _, err := conn.ReadFrom(b)
			if err != nil {
				var timeout net.Error
				if errors.As(err, &timeout) && timeout.Timeout() {
					return false
				}
				t.Logf("read: %v", err)
				return false
			}
			if bytes.Equal(b[:n], payload) {
				return true
			}
		}
	}
	patterned := func(size int, seed byte) []byte {
		b := make([]byte, size)
		for i := range b {
			b[i] = seed + byte(i)
		}
		return b
	}
	// a datagram through the tunnel before the node's answer goes as a capsule: send until one comes back
	firstEcho := func(conn net.PacketConn, destination M.Socksaddr, payload []byte) {
		t.Helper()
		for i := 0; i < 10; i++ {
			if roundTrip(conn, destination, payload, 3*time.Second) {
				return
			}
		}
		t.Fatalf("no echo of %d bytes from %s", len(payload), destination)
	}

	rayctl("service", "set", "naive", "udp=on")
	rayctl("apply")
	on := line(true)
	t.Logf("the naive line says udp=1: %s", on[strings.LastIndex(on, "@")+1:])
	client, logs := start(on)

	// a datagram, then one longer than a QUIC datagram carries (1206 bytes to IPv4): a capsule
	conn := open(client, plain)
	firstEcho(conn, plain, []byte("niko naive-udp-v1 datagram"))
	if !roundTrip(conn, plain, patterned(1400, 7), 10*time.Second) {
		t.Fatal("no echo of 1400 bytes (a capsule)")
	}
	if !strings.Contains(logs.String(), "outbound datagram packet connection") {
		t.Fatal("the socket did not go as HTTP/3 datagrams")
	}
	if strings.Contains(logs.String(), "outbound UoT packet connection") {
		t.Fatal("a socket went over UoT with datagrams allowed")
	}
	t.Log("a datagram and a capsule came back as HTTP/3 datagrams")

	// §4.4: a pair whose first datagram is QUIC (a long header, version 1) sends one over 1252 bytes only where a
	// QUIC datagram carries it, and up to 1252 as a capsule
	quic := open(client, quicPair)
	initial := patterned(1200, 1)
	initial[0], initial[1], initial[2], initial[3], initial[4] = 0xc3, 0, 0, 0, 1
	firstEcho(quic, quicPair, initial)
	if roundTrip(quic, quicPair, patterned(1300, 3), 4*time.Second) {
		t.Fatal("a QUIC pair's datagram of 1300 bytes went as a capsule")
	}
	if !roundTrip(quic, quicPair, patterned(1252, 5), 10*time.Second) {
		t.Fatal("a QUIC pair's datagram of 1252 bytes did not come back")
	}
	t.Log("a QUIC pair: 1300 bytes dropped, 1252 bytes carried as a capsule")

	// the flag off: the node answers as a site does, UDP of the network goes back over UoT
	rayctl("service", "set", "naive", "udp=off")
	rayctl("apply")
	off := line(false)
	client, logs = start(off)
	refused := open(client, afterOff)
	if roundTrip(refused, afterOff, []byte("refused"), 5*time.Second) {
		t.Log("the first socket's datagram came back before the answer (a capsule the node took): not expected")
	}
	deadline := time.Now().Add(20 * time.Second)
	for !strings.Contains(logs.String(), "UDP back over TCP on this network") {
		if time.Now().After(deadline) {
			t.Fatal("no refusal of the request with the flag off")
		}
		time.Sleep(200 * time.Millisecond)
	}
	overTCP := open(client, afterOff)
	firstEcho(overTCP, afterOff, []byte("niko naive over UoT"))
	if !strings.Contains(logs.String(), "outbound UoT packet connection") {
		t.Fatal("the next socket did not go over UoT")
	}
	t.Log("with the flag off: the request refused, the next socket over UoT, its echo back")
}

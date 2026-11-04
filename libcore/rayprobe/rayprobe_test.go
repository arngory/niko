package rayprobe

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strconv"
	"strings"
	"sync"
	"testing"
	"time"

	"libcore/probe"
	"libcore/raysub"
)

// node is a fake probe endpoint after probe-v1.md: the auth of the subscription, down with its trailer, up,
// the UDP echo with its session and count, link.
type node struct {
	t        *testing.T
	key      []byte
	deviceID string
	server   *httptest.Server
	udp      net.PacketConn

	mu       sync.Mutex
	token    []byte
	received int64
	echoed   int64
	dropUp   func(seq uint32) bool
	links    int
	noCount  bool
	closed   int
}

func newNode(t *testing.T) *node {
	n := &node{t: t, key: make([]byte, 32), deviceID: "dev-1"}
	rand.Read(n.key)
	var err error
	if n.udp, err = net.ListenPacket("udp", "127.0.0.1:0"); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { n.udp.Close() })
	go n.echo()
	mux := http.NewServeMux()
	mux.HandleFunc("GET /probe/v1/down", n.auth(n.down))
	mux.HandleFunc("POST /probe/v1/up", n.auth(n.up))
	mux.HandleFunc("POST /probe/v1/udp", n.auth(n.openUDP))
	mux.HandleFunc("GET /probe/v1/udp", n.auth(n.closeUDP))
	mux.HandleFunc("GET /probe/v1/link", n.auth(n.link))
	n.server = httptest.NewUnstartedServer(mux)
	n.server.EnableHTTP2 = true
	n.server.StartTLS()
	t.Cleanup(n.server.Close)
	return n
}

func (n *node) auth(h http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if r.Header.Get("X-Raycontrol-Device") != n.deviceID || r.Header.Get("Authorization") != raysub.Authorization(n.key, time.Now()) {
			http.NotFound(w, r)
			return
		}
		h(w, r)
	}
}

func (n *node) down(w http.ResponseWriter, r *http.Request) {
	size, _ := strconv.Atoi(r.URL.Query().Get("bytes"))
	w.Header().Set("Trailer", "Probe-Result")
	start := time.Now()
	w.Write(make([]byte, size))
	w.Header().Set("Probe-Result", "bytes="+strconv.Itoa(size)+"; ms="+strconv.FormatInt(time.Since(start).Milliseconds(), 10)+"; rtt_us=41250; retrans=3")
}

func (n *node) up(w http.ResponseWriter, r *http.Request) {
	got, _ := io.Copy(io.Discard, r.Body)
	json.NewEncoder(w).Encode(map[string]any{"bytes": got, "ms": 10, "tcp": map[string]any{"rtt_us": 40000}})
}

func (n *node) openUDP(w http.ResponseWriter, r *http.Request) {
	n.mu.Lock()
	n.token = make([]byte, 16)
	rand.Read(n.token)
	n.received, n.echoed = 0, 0
	token := base64.RawURLEncoding.EncodeToString(n.token)
	n.mu.Unlock()
	json.NewEncoder(w).Encode(map[string]any{"token": token, "port": n.udp.LocalAddr().(*net.UDPAddr).Port,
		"seconds": 30, "max_rate": 1000, "budget": 1 << 20})
}

func (n *node) closeUDP(w http.ResponseWriter, r *http.Request) {
	n.mu.Lock()
	defer n.mu.Unlock()
	n.closed++
	if n.noCount {
		http.Error(w, "gone", http.StatusInternalServerError)
		return
	}
	json.NewEncoder(w).Encode(map[string]any{"received": n.received, "echoed": n.echoed, "dropped": 0, "bytes": 0})
}

func (n *node) link(w http.ResponseWriter, r *http.Request) {
	n.mu.Lock()
	n.links++
	n.mu.Unlock()
	w.Write([]byte(`{"address":"127.0.0.1","connections":1,"hysteria2":{"4":{"connections":1,"brutal":{"connections":1,"ack_rate":{"min":0.8,"median":0.85,"max":0.9}}}}}`))
}

// echo returns the session's datagrams with its receive time in bytes 28-35; dropUp loses some on the way up
func (n *node) echo() {
	buf := make([]byte, 65536)
	for {
		k, addr, err := n.udp.ReadFrom(buf)
		if err != nil {
			return
		}
		n.mu.Lock()
		ok := k >= echoHeader && n.token != nil && string(buf[:16]) == string(n.token)
		seq := binary.BigEndian.Uint32(buf[16:20])
		drop := ok && n.dropUp != nil && n.dropUp(seq)
		if ok && !drop {
			n.received++
			n.echoed++
		}
		n.mu.Unlock()
		if !ok || drop {
			continue
		}
		binary.BigEndian.PutUint64(buf[28:36], uint64(time.Now().UnixNano()))
		n.udp.WriteTo(buf[:k], addr)
	}
}

func (n *node) endpoint(t *testing.T) *Endpoint {
	cert, err := x509.ParseCertificate(n.server.TLS.Certificates[0].Certificate[0])
	if err != nil {
		t.Fatal(err)
	}
	sum := sha256.Sum256(cert.RawSubjectPublicKeyInfo)
	return &Endpoint{
		Address: n.server.Listener.Addr().String(), Pin: base64.RawURLEncoding.EncodeToString(sum[:]),
		DeviceID: n.deviceID, DeviceKey: n.key, Dial: (&net.Dialer{}).DialContext,
	}
}

func TestDown(t *testing.T) {
	n := newNode(t)
	c := NewClient(n.endpoint(t))
	defer c.Close()
	r := c.Down(context.Background(), 1<<20, probe.Options{WindowMs: 10000})
	if !r.OK || r.Bytes != 1<<20 || r.StalledAt != -1 {
		t.Fatalf("%+v", r)
	}
	if r.Server["bytes"] != int64(1<<20) || r.Server["rtt_us"] != int64(41250) {
		t.Fatalf("server result %v", r.Server)
	}
}

func TestUp(t *testing.T) {
	n := newNode(t)
	c := NewClient(n.endpoint(t))
	defer c.Close()
	r := c.Up(context.Background(), 1<<20, 10*time.Second)
	if !r.OK || r.Sent != 1<<20 || r.Bytes != 1<<20 || r.Ms != 10 || r.Kbps != (1<<20)*8/10 {
		t.Fatalf("%+v", r)
	}
}

func TestEcho(t *testing.T) {
	n := newNode(t)
	// every fourth datagram lost on the way up
	n.dropUp = func(seq uint32) bool { return seq%4 == 3 }
	c := NewClient(n.endpoint(t))
	defer c.Close()
	r := c.Echo(context.Background(), 8, 1200, 5*time.Millisecond)
	if !r.OK || r.Sent != 8 || r.Received != 6 || r.LostUp != 2 || r.LostDown != 0 || r.Size != 1200 {
		t.Fatalf("%+v", r)
	}
	if r.ServerReceived != 6 || r.ServerEchoed != 6 {
		t.Fatalf("server count %+v", r)
	}
}

func TestLinkAndAuth(t *testing.T) {
	n := newNode(t)
	c := NewClient(n.endpoint(t))
	defer c.Close()
	raw, err := c.Link(context.Background())
	if err != nil || !strings.Contains(string(raw), `"ack_rate"`) {
		t.Fatalf("%s %v", raw, err)
	}
	// another device key: the node does not know it
	e := n.endpoint(t)
	e.DeviceKey = make([]byte, 32)
	bad := NewClient(e)
	defer bad.Close()
	_, err = bad.Link(context.Background())
	if status, ok := err.(*StatusError); !ok || status.Status != 404 {
		t.Fatalf("want 404, got %v", err)
	}
	if d := bad.Down(context.Background(), 1024, probe.Options{}); d.Status != 404 || d.OK {
		t.Fatalf("down with a bad key: %+v", d)
	}
}

func TestWrongPin(t *testing.T) {
	n := newNode(t)
	e := n.endpoint(t)
	e.Pin = base64.RawURLEncoding.EncodeToString(make([]byte, 32))
	c := NewClient(e)
	defer c.Close()
	if _, err := c.Link(context.Background()); err == nil {
		t.Fatal("a wrong pin must fail")
	}
}

// the echo's window spent before the last datagram: the session is still closed and counted
func TestEchoWindowSpent(t *testing.T) {
	n := newNode(t)
	c := NewClient(n.endpoint(t))
	defer c.Close()
	ctx, cancel := context.WithTimeout(context.Background(), 300*time.Millisecond)
	defer cancel()
	r := c.Echo(ctx, 100, 64, 20*time.Millisecond)
	if !r.OK || r.Sent >= 100 || !r.Counted || r.LostUp != 0 || r.Lost != r.Sent-r.Received {
		t.Fatalf("%+v", r)
	}
	n.mu.Lock()
	defer n.mu.Unlock()
	if n.closed != 1 {
		t.Fatalf("session closed %d times", n.closed)
	}
}

// no count from the server: the loss stays whole, not all of it put on the way up
func TestEchoNoCount(t *testing.T) {
	n := newNode(t)
	n.noCount = true
	n.dropUp = func(seq uint32) bool { return seq == 2 }
	c := NewClient(n.endpoint(t))
	defer c.Close()
	r := c.Echo(context.Background(), 5, 64, 5*time.Millisecond)
	if !r.OK || r.Counted || r.Lost != 1 || r.LostUp != -1 || r.LostDown != -1 {
		t.Fatalf("%+v", r)
	}
}

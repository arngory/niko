package probe

import (
	"context"
	"net"
	"os"
	"net/http"
	"net/http/httptest"
	"strings"
	"sync"
	"testing"
	"time"
)

func server(t *testing.T, h http.HandlerFunc) string {
	s := httptest.NewServer(h)
	t.Cleanup(s.Close)
	return s.URL
}

func TestTCPFull(t *testing.T) {
	url := server(t, func(w http.ResponseWriter, r *http.Request) {
		w.Write(make([]byte, 300<<10))
	})
	r := TCP(context.Background(), http.DefaultClient, Options{TCPURL: url})
	if !r.OK || r.Bytes < defaultTCPBytes || r.StalledAt != -1 || r.Error != "" {
		t.Fatalf("%+v", r)
	}
}

// the symptom of a filter that freezes a connection after its first ~16-20 KB
func TestTCPStall(t *testing.T) {
	url := server(t, func(w http.ResponseWriter, r *http.Request) {
		w.Write(make([]byte, 20<<10))
		w.(http.Flusher).Flush()
		select {
		case <-r.Context().Done():
		case <-time.After(5 * time.Second):
		}
	})
	start := time.Now()
	r := TCP(context.Background(), http.DefaultClient, Options{TCPURL: url, StallMs: 500})
	if r.OK || r.Error != "stalled" || r.StalledAt != 20<<10 {
		t.Fatalf("%+v", r)
	}
	if time.Since(start) > 2*time.Second {
		t.Fatalf("took %v: the stall must end the probe", time.Since(start))
	}
}

func TestTCPWindowEndsSlowDownload(t *testing.T) {
	url := server(t, func(w http.ResponseWriter, r *http.Request) {
		for i := 0; i < 50; i++ {
			if _, err := w.Write(make([]byte, 1<<10)); err != nil {
				return
			}
			w.(http.Flusher).Flush()
			time.Sleep(100 * time.Millisecond)
		}
	})
	r := TCP(context.Background(), http.DefaultClient, Options{TCPURL: url, WindowMs: 800})
	// slow but moving: a result with its speed, not a stall
	if !r.OK || r.StalledAt != -1 || r.Bytes == 0 || r.Bytes >= 50<<10 || r.Kbps == 0 {
		t.Fatalf("%+v", r)
	}
}

func TestTCPStatus(t *testing.T) {
	url := server(t, func(w http.ResponseWriter, r *http.Request) { http.NotFound(w, r) })
	r := TCP(context.Background(), http.DefaultClient, Options{TCPURL: url})
	if r.OK || !strings.Contains(r.Error, "404") {
		t.Fatalf("%+v", r)
	}
}

// dnsServer answers queries; drop says which (by arrival order) get no answer, hold delays one
func dnsServer(t *testing.T, drop func(n int) bool, hold map[int]time.Duration) string {
	pc, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { pc.Close() })
	var mu sync.Mutex
	go func() {
		buf := make([]byte, 1500)
		for n := 0; ; n++ {
			k, addr, err := pc.ReadFrom(buf)
			if err != nil {
				return
			}
			if drop != nil && drop(n) {
				continue
			}
			answer := append([]byte(nil), buf[:k]...)
			answer[2] |= 0x80 // QR
			send := func() { mu.Lock(); pc.WriteTo(answer, addr); mu.Unlock() }
			if d, ok := hold[n]; ok {
				time.AfterFunc(d, send)
			} else {
				send()
			}
		}
	}()
	return pc.LocalAddr().String()
}

func dial(t *testing.T, addr string) net.Conn {
	c, err := net.Dial("udp", addr)
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { c.Close() })
	return c
}

func TestUDPAll(t *testing.T) {
	addr := dnsServer(t, nil, nil)
	r := UDP(context.Background(), dial(t, addr), Options{UDPServer: addr, UDPCount: 5, UDPIntervalMs: 20})
	if !r.OK || r.Sent != 5 || r.Received != 5 || r.LossPct != 0 || r.Reordered != 0 || r.RTTAvgMs <= 0 {
		t.Fatalf("%+v", r)
	}
}

func TestUDPLossAndReorder(t *testing.T) {
	// every third query lost; the first answer held back past the second
	addr := dnsServer(t, func(n int) bool { return n%3 == 2 }, map[int]time.Duration{0: 300 * time.Millisecond})
	r := UDP(context.Background(), dial(t, addr), Options{UDPServer: addr, UDPCount: 6, UDPIntervalMs: 20})
	if !r.OK || r.Sent != 6 || r.Received != 4 || r.LossPct != 33.3 || r.Reordered != 1 {
		t.Fatalf("%+v", r)
	}
}

func TestUDPNoAnswer(t *testing.T) {
	addr := dnsServer(t, func(int) bool { return true }, nil)
	start := time.Now()
	r := UDP(context.Background(), dial(t, addr), Options{UDPServer: addr, UDPCount: 3, UDPIntervalMs: 20})
	if r.OK || r.Received != 0 || r.LossPct != 100 || r.Error != "no answer" {
		t.Fatalf("%+v", r)
	}
	if time.Since(start) > 2*time.Second {
		t.Fatalf("took %v", time.Since(start))
	}
}

func TestDNSQuery(t *testing.T) {
	q := dnsQuery(0x1234, "www.gstatic.com")
	want := "\x12\x34\x01\x00\x00\x01\x00\x00\x00\x00\x00\x00\x03www\x07gstatic\x03com\x00\x00\x01\x00\x01"
	if string(q) != want {
		t.Fatalf("%q", q)
	}
}

// a packet conn whose deadlines do nothing (sing-quic's): the probe still ends, by closing it
type noDeadlines struct{ net.Conn }

func (noDeadlines) SetDeadline(time.Time) error     { return os.ErrInvalid }
func (noDeadlines) SetReadDeadline(time.Time) error { return os.ErrInvalid }

func TestUDPConnIgnoringDeadlines(t *testing.T) {
	addr := dnsServer(t, func(int) bool { return true }, nil)
	start := time.Now()
	r := UDP(context.Background(), noDeadlines{dial(t, addr)}, Options{UDPServer: addr, UDPCount: 3, UDPIntervalMs: 20})
	if r.OK || r.Error != "no answer" {
		t.Fatalf("%+v", r)
	}
	if time.Since(start) > 3*time.Second {
		t.Fatalf("took %v: the reader must end without deadlines", time.Since(start))
	}
}

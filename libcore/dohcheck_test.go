package libcore

import (
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strconv"
	"sync/atomic"
	"testing"

	"github.com/miekg/dns"
	sbdns "github.com/sagernet/sing-box/dns"
)

// The watchdog's DoH check against a server that answers, then refuses the token as a raycontrol node does (an empty
// 404, or 403 by the mode of its site), then stalls: the status comes back as the code, the stall as no word.
func TestDohCheck(t *testing.T) {
	var answer atomic.Int32
	answer.Store(http.StatusOK)
	stall := make(chan struct{})
	t.Cleanup(func() { close(stall) })
	server := httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		code := int(answer.Load())
		if code == 0 {
			select {
			case <-stall:
			case <-r.Context().Done():
			}
			return
		}
		if code != http.StatusOK {
			w.WriteHeader(code)
			return
		}
		body, _ := io.ReadAll(r.Body)
		query := new(dns.Msg)
		if r.URL.Path != "/dns-query/token" || query.Unpack(body) != nil {
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		reply := new(dns.Msg)
		reply.SetReply(query)
		packed, _ := reply.Pack()
		w.Header().Set("Content-Type", "application/dns-message")
		w.Write(packed)
	}))
	server.EnableHTTP2 = true
	server.StartTLS()
	t.Cleanup(server.Close)
	_, portText, _ := net.SplitHostPort(server.Listener.Addr().String())
	port, _ := strconv.Atoi(portText)
	config := map[string]any{
		"log": map[string]any{"level": "error"},
		"dns": map[string]any{"servers": []any{
			map[string]any{
				"type": "https", "tag": "dns-remote", "server": "127.0.0.1", "server_port": port, "path": "/dns-query/token",
				"tls": map[string]any{"enabled": true, "insecure": true},
			},
			map[string]any{"type": "udp", "tag": "dns-direct", "server": "127.0.0.1", "server_port": 9},
		}},
		"outbounds": []any{map[string]any{"type": "direct", "tag": "direct"}},
	}
	raw, _ := json.Marshal(config)
	instance := startTestBox(t, string(raw))
	defer instance.Close()

	if got := DohCheck(instance, "dns-remote", 3000); got != DohAnswered {
		t.Fatalf("a server that answers: %d", got)
	}
	for _, code := range []int32{http.StatusNotFound, http.StatusForbidden} {
		answer.Store(code)
		if got := DohCheck(instance, "dns-remote", 3000); got != code {
			t.Fatalf("a server that answers %d: %d", code, got)
		}
	}
	answer.Store(0)
	if got := DohCheck(instance, "dns-remote", 500); got != DohNoWord {
		t.Fatalf("a server that keeps silent: %d", got)
	}
	if got := DohCheck(instance, "dns-direct", 500); got != DohNoServer {
		t.Fatalf("a server that is no DoH: %d", got)
	}
	if got := DohCheck(instance, "nowhere", 500); got != DohNoServer {
		t.Fatalf("no such server: %d", got)
	}
	if got := DohCheck(nil, "dns-remote", 500); got != DohNoServer {
		t.Fatalf("no core: %d", got)
	}
	// wrapped as a transport may wrap it, the code still reads
	if got := dohOutcome(fmt.Errorf("exchange: %w", &sbdns.HTTPStatusError{StatusCode: 404, Status: "404 Not Found"})); got != 404 {
		t.Fatalf("a wrapped status: %d", got)
	}
	if got := dohOutcome(errors.New("unexpected status: 404 Not Found")); got != DohNoWord {
		t.Fatalf("a status only in the text: %d", got)
	}
}

package libcore

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"io"
	"net"
	"net/http"
	"net/http/httptest"
	"strconv"
	"sync/atomic"
	"testing"
	"time"

	"github.com/miekg/dns"
)

// dohTestServer answers pinned.example over HTTPS at /dns-query/token with httptest's certificate: self-signed for
// example.com, under no CA anyone knows. It counts the queries that reached it.
func dohTestServer(t *testing.T) (server *httptest.Server, port int, queries *atomic.Int32) {
	t.Helper()
	queries = new(atomic.Int32)
	server = httptest.NewUnstartedServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		queries.Add(1)
		body, _ := io.ReadAll(r.Body)
		query := new(dns.Msg)
		if r.URL.Path != "/dns-query/token" || query.Unpack(body) != nil {
			w.WriteHeader(http.StatusBadRequest)
			return
		}
		answer := new(dns.Msg)
		answer.SetReply(query)
		record, _ := dns.NewRR("pinned.example. 60 IN A 203.0.113.9")
		answer.Answer = []dns.RR{record}
		packed, _ := answer.Pack()
		w.Header().Set("Content-Type", "application/dns-message")
		w.Write(packed)
	}))
	server.EnableHTTP2 = true
	server.StartTLS()
	t.Cleanup(server.Close)
	_, portText, _ := net.SplitHostPort(server.Listener.Addr().String())
	port, _ = strconv.Atoi(portText)
	return server, port, queries
}

// askThroughCore asks the DoH server at port for pinned.example through a core instance whose one DNS server it is,
// with tls as that server's TLS options and certificate, when not nil, as the config's certificate store.
func askThroughCore(t *testing.T, port int, tls map[string]any, certificate any) (*dns.Msg, error) {
	t.Helper()
	local, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	localPort := local.LocalAddr().(*net.UDPAddr).Port
	local.Close()
	config := map[string]any{
		"log": map[string]any{"level": "error"},
		"dns": map[string]any{"servers": []any{map[string]any{
			"type": "https", "tag": "dns-remote", "server": "127.0.0.1", "server_port": port, "path": "/dns-query/token",
			"tls": tls,
		}}},
		"inbounds": []any{map[string]any{
			"type": "direct", "tag": "dns-in", "listen": "127.0.0.1", "listen_port": localPort, "network": "udp",
			"override_address": "1.1.1.1", "override_port": 53,
		}},
		"outbounds": []any{map[string]any{"type": "direct", "tag": "direct"}},
		"route": map[string]any{
			"rules": []any{map[string]any{"inbound": []string{"dns-in"}, "action": "hijack-dns"}},
			"final": "direct",
		},
	}
	if certificate != nil {
		config["certificate"] = certificate
	}
	raw, _ := json.Marshal(config)
	instance := startTestBox(t, string(raw))
	defer instance.Close()
	query := new(dns.Msg)
	query.SetQuestion("pinned.example.", dns.TypeA)
	client := &dns.Client{Timeout: 3 * time.Second}
	answer, _, err := client.Exchange(query, net.JoinHostPort("127.0.0.1", strconv.Itoa(localPort)))
	return answer, err
}

// The node's DoH as the app makes it its remote DNS (ConfigBuilder's nodeDohServer) with the key pins of its row
// (raycontrol cert-pin-v1): the core's DoH transport takes the pin in place of the chain, so a server with the pinned
// key answers whatever signed its certificate, and one with another key gets no query.
func TestDoHHeldToPins(t *testing.T) {
	server, port, queries := dohTestServer(t)
	key := sha256.Sum256(server.Certificate().RawSubjectPublicKeyInfo)
	ask := func(pins ...[]byte) (*dns.Msg, error) {
		var hashes []string
		for _, pin := range pins {
			hashes = append(hashes, base64.StdEncoding.EncodeToString(pin))
		}
		return askThroughCore(t, port, map[string]any{
			"enabled": true, "server_name": "doh.node.example", "curve_preferences": []string{"X25519MLKEM768"},
			"certificate_public_key_sha256": hashes,
		}, nil)
	}

	other := sha256.Sum256([]byte("another key"))
	// the server's key among the pins: answered, though nothing vouches for the certificate or its name
	answer, err := ask(other[:], key[:])
	if err != nil || answer.Rcode != dns.RcodeSuccess || len(answer.Answer) != 1 {
		t.Fatalf("with the server's key among the pins: %v, %v", answer, err)
	}
	asked := queries.Load()
	// pins of other keys only: no answer, and the query never reached the server
	if answer, err = ask(other[:]); err == nil && answer.Rcode == dns.RcodeSuccess && len(answer.Answer) > 0 {
		t.Fatalf("with a pin of another key: answered %v", answer)
	}
	if queries.Load() != asked {
		t.Fatal("with a pin of another key the query was sent to the server")
	}
}

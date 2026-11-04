//go:build with_yggdrasil

package libcore

import (
	"encoding/json"
	"net"
	"strconv"
	"strings"
	"testing"
	"time"
)

// YggdrasilPeers: a node of the client's own with a reachable peer and a closed one; the first comes up with its
// latency, the second reports its error.
func TestYggdrasilPeers(t *testing.T) {
	l, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	port := l.Addr().(*net.TCPAddr).Port
	l.Close()
	closed, _ := net.Listen("tcp", "127.0.0.1:0")
	closedPort := closed.Addr().(*net.TCPAddr).Port
	closed.Close()

	key := func() string {
		k, err := YggdrasilGenerateKey()
		if err != nil {
			t.Fatal(err)
		}
		return k
	}
	config := func(endpoint map[string]any) string {
		raw, _ := json.Marshal(map[string]any{
			"log":       map[string]any{"level": "warn"},
			"endpoints": []any{endpoint},
			"outbounds": []any{map[string]any{"type": "direct", "tag": "direct"}},
		})
		return string(raw)
	}
	// a public peer
	startTestBox(t, config(map[string]any{
		"type": "yggdrasil", "tag": "peer", "private_key": key(),
		"listen": []string{"tls://127.0.0.1:" + strconv.Itoa(port)},
	}))
	up := "tls://127.0.0.1:" + strconv.Itoa(port)
	down := "tls://127.0.0.1:" + strconv.Itoa(closedPort)
	checker := startTestBox(t, config(map[string]any{
		"type": "yggdrasil", "tag": "check", "private_key": key(), "node_info_privacy": true,
		"peers": []string{up, down},
	}))
	var peers []yggPeer
	for deadline := time.Now().Add(15 * time.Second); time.Now().Before(deadline); time.Sleep(200 * time.Millisecond) {
		raw, err := YggdrasilPeers(checker, "check")
		if err != nil {
			t.Fatal(err)
		}
		peers = nil
		if err = json.Unmarshal([]byte(raw), &peers); err != nil {
			t.Fatal(err)
		}
		var gotUp, gotDown bool
		for _, p := range peers {
			gotUp = gotUp || strings.HasPrefix(p.URI, up) && p.Up && p.LatencyMs > 0
			gotDown = gotDown || strings.HasPrefix(p.URI, down) && !p.Up && p.Error != ""
		}
		if gotUp && gotDown {
			t.Logf("peers: %s", raw)
			return
		}
	}
	t.Fatalf("peers: %+v", peers)
}

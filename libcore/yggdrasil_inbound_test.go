package libcore

import (
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net"
	"testing"
	"time"

	M "github.com/sagernet/sing/common/metadata"
)

// Two mesh nodes in this process, one peering with the other over tls:// on 127.0.0.1, as the app runs a LAN's
// (lan-v1 §5): the dialled node takes connections only from the addresses its inbound_sources lists, to the port its
// route rules open, on 127.0.0.1. Unlisted, nothing comes back; listed, the port answers. An empty list too: the
// options' parser drops it, inbound_listed keeps its meaning (the first LAN build let every flow in with one).
func TestNekoYggdrasilInboundSourcesLocal(t *testing.T) {
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
	probe, _ := net.Listen("tcp", "127.0.0.1:0")
	peerPort := probe.Addr().(*net.TCPAddr).Port
	probe.Close()

	keyA, _ := YggdrasilGenerateKey()
	keyB, _ := YggdrasilGenerateKey()
	addrA, addrB := YggdrasilAddress(keyA), YggdrasilAddress(keyB)
	config := func(key string, extra map[string]any) string {
		endpoint := map[string]any{"type": "yggdrasil", "tag": "ygg", "private_key": key, "debug_privacy": true}
		for k, v := range extra {
			endpoint[k] = v
		}
		raw, _ := json.Marshal(map[string]any{
			"log":       map[string]any{"level": "warn"},
			"endpoints": []any{endpoint},
			"outbounds": []any{map[string]any{"type": "direct", "tag": "direct"}},
			"route": map[string]any{"final": "direct", "rules": []any{
				map[string]any{"inbound": []string{"ygg"}, "port": []int{port}, "outbound": "direct"},
				map[string]any{"inbound": []string{"ygg"}, "action": "reject"},
			}},
		})
		return string(raw)
	}
	a := startTestBox(t, config(keyA, map[string]any{"listen": []string{fmt.Sprintf("tls://127.0.0.1:%d", peerPort)}}))
	reaches := func(from *BoxInstance, addr string) bool {
		endpoint, _ := from.Box.Endpoint().Get("ygg")
		ctx, cancel := context.WithTimeout(context.Background(), 3*time.Second)
		defer cancel()
		conn, err := endpoint.DialContext(ctx, "tcp", M.ParseSocksaddrHostPort(addr, uint16(port)))
		if err != nil {
			return false
		}
		defer conn.Close()
		conn.SetDeadline(time.Now().Add(3 * time.Second))
		if _, err = conn.Write([]byte("lan-v1")); err != nil {
			return false
		}
		echo := make([]byte, 6)
		_, err = io.ReadFull(conn, echo)
		return err == nil && string(echo) == "lan-v1"
	}
	within := func(what string, from *BoxInstance, addr string, want bool) {
		t.Helper()
		// an answer comes within a second or two here; nothing for ten is nothing
		deadline := time.Now().Add(10 * time.Second)
		if want {
			deadline = time.Now().Add(20 * time.Second)
		}
		for {
			got := reaches(from, addr)
			if got && !want {
				t.Fatalf("%s: answered", what)
			}
			if got && want {
				return
			}
			if time.Now().After(deadline) {
				if want {
					t.Fatalf("%s: no answer in 20 s", what)
				}
				return
			}
			time.Sleep(500 * time.Millisecond)
		}
	}

	// B lets nobody in: A's flows get nothing, also once A is known to B as a peer
	closed := startTestBox(t, config(keyB, map[string]any{
		"peers": []string{fmt.Sprintf("tls://127.0.0.1:%d", peerPort)}, "inbound_listed": true, "inbound_sources": []string{}}))
	within("A → B listing none", a, addrB, false)
	closed.Close()
	// B lists A: the port answers; B's own dial to A comes back (its answers are let in)
	open := startTestBox(t, config(keyB, map[string]any{
		"peers": []string{fmt.Sprintf("tls://127.0.0.1:%d", peerPort)}, "inbound_listed": true, "inbound_sources": []string{addrA + "/128"}}))
	within("A → B listing A", a, addrB, true)
	within("B → A, A without a list", open, addrA, true)
	open.Close()
	// B lists another: nothing again, also after B dialled A (a dialled address answers, it opens nothing)
	other := startTestBox(t, config(keyB, map[string]any{
		"peers": []string{fmt.Sprintf("tls://127.0.0.1:%d", peerPort)}, "inbound_listed": true, "inbound_sources": []string{"201::1/128"}}))
	within("B → A, B listing another", other, addrA, true)
	within("A → B listing another", a, addrB, false)
}

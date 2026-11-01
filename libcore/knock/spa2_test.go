package knock

import (
	"bytes"
	"context"
	"encoding/binary"
	"encoding/hex"
	"encoding/json"
	"net"
	"os"
	"testing"
	"time"

	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"golang.org/x/crypto/chacha20poly1305"
)

// testdata/spa_v2_vectors.json is raycontrol internal/spa/testdata/vectors.json
// (branch claude/clever-feynman-w0e2v1, commit 55535a0), unchanged.
type spa2Vectors struct {
	Params SPAv2Options `json:"params"`
	Ports  []struct {
		Time int64  `json:"time"`
		Slot uint64 `json:"slot"`
		Port uint16 `json:"port"`
	} `json:"ports"`
	Packets []struct {
		DeviceKey string   `json:"device_key"`
		Time      int64    `json:"time"`
		TTL       uint16   `json:"ttl_s"`
		Scope     []uint16 `json:"scope"`
		Nonce     string   `json:"nonce"`
		Length    int      `json:"length"`
		Epoch     uint64   `json:"epoch"`
		EpochKey  string   `json:"epoch_key"`
		Packet    string   `json:"packet"`
	} `json:"packets"`
}

func loadSPA2Vectors(t *testing.T) spa2Vectors {
	raw, err := os.ReadFile("testdata/spa_v2_vectors.json")
	if err != nil {
		t.Fatal(err)
	}
	var v spa2Vectors
	if err = json.Unmarshal(raw, &v); err != nil {
		t.Fatal(err)
	}
	return v
}

func TestSPA2Vectors(t *testing.T) {
	v := loadSPA2Vectors(t)
	base := v.Params
	base.DeviceKey = hex.EncodeToString(make([]byte, 32))
	p, err := parseSPAv2(base)
	if err != nil {
		t.Fatal(err)
	}
	for _, c := range v.Ports {
		now := time.Unix(c.Time, 0)
		if slot := p.slot(now); slot != c.Slot {
			t.Fatalf("time %d: slot %d, want %d", c.Time, slot, c.Slot)
		}
		if port := p.port(c.Slot); port != c.Port {
			t.Fatalf("slot %d: port %d, want %d", c.Slot, port, c.Port)
		}
	}
	for i, c := range v.Packets {
		options := v.Params
		options.DeviceKey = c.DeviceKey
		options.Scope = c.Scope
		options.TTL = c.TTL
		p, err := parseSPAv2(options)
		if err != nil {
			t.Fatal(err)
		}
		now := time.Unix(c.Time, 0)
		if epoch := p.epoch(now); epoch != c.Epoch {
			t.Fatalf("packet %d: epoch %d, want %d", i, epoch, c.Epoch)
		}
		key, err := p.epochKey(c.Epoch)
		if err != nil || hex.EncodeToString(key) != c.EpochKey {
			t.Fatalf("packet %d: epoch key %x, want %s (%v)", i, key, c.EpochKey, err)
		}
		nonce, _ := hex.DecodeString(c.Nonce)
		packet, err := p.seal(now, nonce, c.Length)
		if err != nil {
			t.Fatal(err)
		}
		if got := hex.EncodeToString(packet); got != c.Packet {
			t.Fatalf("packet %d:\n got %s\nwant %s", i, got, c.Packet)
		}
	}
}

// spa2Open is the server side check of spa-v2.md §6 steps 3-5, for the tests.
func spa2Open(t *testing.T, p *spa2Params, packet []byte, now time.Time) (sent time.Time, ttl uint16, scope []uint16) {
	t.Helper()
	if len(packet) < p.lenMin || len(packet) > p.lenMax {
		t.Fatalf("length %d outside [%d, %d]", len(packet), p.lenMin, p.lenMax)
	}
	aad := append([]byte(spa2AADLabel), p.nodeID[:]...)
	for _, epoch := range []uint64{p.epoch(now) - 1, p.epoch(now), p.epoch(now) + 1} {
		key, _ := p.epochKey(epoch)
		aead, _ := chacha20poly1305.NewX(key)
		plaintext, err := aead.Open(nil, packet[:spa2NonceSize], packet[spa2NonceSize:], aad)
		if err != nil {
			continue
		}
		if plaintext[0] != spa2Version || plaintext[11] != 0 {
			t.Fatal("version or flags")
		}
		sent = time.Unix(int64(binary.BigEndian.Uint64(plaintext[1:9])), 0)
		if p.epoch(sent) != epoch {
			t.Fatal("epoch of the time field differs from the key's")
		}
		ttl = binary.BigEndian.Uint16(plaintext[9:11])
		for i := range int(plaintext[12]) {
			scope = append(scope, binary.BigEndian.Uint16(plaintext[spa2Header+2*i:]))
		}
		return
	}
	t.Fatal("packet does not authenticate")
	return
}

func TestSPA2BeforeDialAndAfterNetworkChange(t *testing.T) {
	spaConn, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer spaConn.Close()
	spaPort := uint16(spaConn.LocalAddr().(*net.UDPAddr).Port)
	packets := make(chan []byte, 8)
	go func() {
		buffer := make([]byte, 1024)
		for {
			n, _, err := spaConn.ReadFrom(buffer)
			if err != nil {
				return
			}
			packets <- bytes.Clone(buffer[:n])
		}
	}()
	r := &recorder{}
	server := listenTCP(t, r, "server")

	options := SPAv2Options{
		NodeID: "33333333333333333333333333333333", EpochLen: 86400, SlotLen: 600,
		PortKey:  "4444444444444444444444444444444444444444444444444444444444444444",
		PortBase: spaPort, PortRange: 1, LenMin: 96, LenMax: 160,
		DeviceKey: "1111111111111111111111111111111111111111111111111111111111111111",
		Scope:     []uint16{1, 3}, TTL: 600,
	}
	outbound := newBox(t, Options{SPAv2: &options})
	destination := M.ParseSocksaddrHostPort("127.0.0.1", server)
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()

	started := time.Now()
	conn, err := outbound.DialContext(ctx, N.NetworkTCP, destination)
	if err != nil {
		t.Fatal(err)
	}
	if waited := time.Since(started); waited < 150*time.Millisecond {
		t.Fatalf("connected %v after the packet, want at least 150ms", waited)
	}
	select {
	case packet := <-packets:
		sent, ttl, scope := spa2Open(t, outbound.spa2, packet, time.Now())
		if time.Since(sent) > 5*time.Second || ttl != 600 || len(scope) != 2 || scope[0] != 1 || scope[1] != 3 {
			t.Fatalf("plaintext: %v %d %v", sent, ttl, scope)
		}
	case <-time.After(2 * time.Second):
		t.Fatal("no SPA packet")
	}

	// in use: tracked, and no new packet within the access time
	if !outbound.inUse(outbound.target("127.0.0.1")) {
		t.Fatal("host not in use")
	}
	conn2, err := outbound.DialContext(ctx, N.NetworkTCP, destination)
	if err != nil {
		t.Fatal(err)
	}
	conn2.Close()
	conn.Close()
	select {
	case <-packets:
		t.Fatal("a second packet within the access time")
	case <-time.After(300 * time.Millisecond):
	}

	// a network change sends a packet at once for a host in use
	outbound.InterfaceUpdated(context.Background())
	select {
	case <-packets:
	case <-time.After(2 * time.Second):
		t.Fatal("no packet after a network change")
	}
}

func TestSPA2RetryAfterFailedDial(t *testing.T) {
	spaConn, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer spaConn.Close()
	closed, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	closedPort := uint16(closed.Addr().(*net.TCPAddr).Port)
	closed.Close()
	options := SPAv2Options{
		NodeID: "33333333333333333333333333333333", EpochLen: 86400, SlotLen: 600,
		PortKey:  "4444444444444444444444444444444444444444444444444444444444444444",
		PortBase: uint16(spaConn.LocalAddr().(*net.UDPAddr).Port), PortRange: 1, LenMin: 96, LenMax: 160,
		DeviceKey: "1111111111111111111111111111111111111111111111111111111111111111",
	}
	outbound := newBox(t, Options{SPAv2: &options})
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	if _, err = outbound.DialContext(ctx, N.NetworkTCP, M.ParseSocksaddrHostPort("127.0.0.1", closedPort)); err == nil {
		t.Fatal("dial to a closed port succeeded")
	}
	// the first packet, then one more after the failed connection
	buffer := make([]byte, 1024)
	for i := range 2 {
		spaConn.SetReadDeadline(time.Now().Add(2 * time.Second))
		if _, _, err = spaConn.ReadFrom(buffer); err != nil {
			t.Fatalf("packet %d: %v", i+1, err)
		}
	}
	spaConn.SetReadDeadline(time.Now().Add(300 * time.Millisecond))
	if _, _, err = spaConn.ReadFrom(buffer); err == nil {
		t.Fatal("a third packet")
	}
}

func TestSPA2Options(t *testing.T) {
	good := SPAv2Options{
		NodeID: "33333333333333333333333333333333", EpochLen: 60, SlotLen: 60,
		PortKey:  "4444444444444444444444444444444444444444444444444444444444444444",
		PortBase: 40000, PortRange: 4096, LenMin: 96, LenMax: 160,
		DeviceKey: "1111111111111111111111111111111111111111111111111111111111111111",
	}
	if _, err := parseSPAv2(good); err != nil {
		t.Fatal(err)
	}
	for i, mutate := range []func(*SPAv2Options){
		func(o *SPAv2Options) { o.NodeID = "33" },
		func(o *SPAv2Options) { o.DeviceKey = "zz" },
		func(o *SPAv2Options) { o.SlotLen = 0 },
		func(o *SPAv2Options) { o.PortBase, o.PortRange = 65000, 1000 },
		func(o *SPAv2Options) { o.LenMax = 600 },
		func(o *SPAv2Options) { o.LenMin = 40 },
		func(o *SPAv2Options) { o.Scope = make([]uint16, 17); o.LenMin, o.LenMax = 200, 300 },
	} {
		o := good
		mutate(&o)
		if _, err := parseSPAv2(o); err == nil {
			t.Fatalf("case %d accepted", i)
		}
	}
	if _, err := New(context.Background(), nil, nil, "x", Options{SPAv2: &good, Sequence: []Step{{Port: 1}}}); err == nil {
		t.Fatal("spa_v2 with a sequence accepted")
	}
	// packets are random: different nonce, length within bounds
	p, _ := parseSPAv2(good)
	_, a, _ := p.packet(time.Now())
	_, b, _ := p.packet(time.Now())
	if bytes.Equal(a[:spa2NonceSize], b[:spa2NonceSize]) || len(a) < 96 || len(a) > 160 {
		t.Fatal("packet not random")
	}
}

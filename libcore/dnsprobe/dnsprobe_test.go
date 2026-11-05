package dnsprobe

import (
	"context"
	"net"
	"strconv"
	"strings"
	"testing"
	"time"

	"github.com/miekg/dns"
)

// dnsprobe-v1 §6 vectors (raycontrol), checked outside Go too
func TestVectors(t *testing.T) {
	key := make([]byte, 32)
	for i := range key {
		key[i] = byte(i)
	}
	var nonce [8]byte
	for i := range nonce {
		nonce[i] = 0xa0 + byte(i)
	}
	label, q := Label(key, "T.Example.com.", 29000000, nonce, false)
	if label != "aeaadoubicqkdivduss2nj2inuqqx7vwla4ifvi" || len(label) != 39 {
		t.Fatalf("label %s", label)
	}
	if got := Answer(key, q); got != "v1 7wdaqgi6x2divio2oavsyoqvg4" {
		t.Fatalf("answer %s", got)
	}
}

// a fake front over UDP: answers a valid probe label as the node does, TC for a large one at a small EDNS
func TestProbeUDP(t *testing.T) {
	key := make([]byte, 32)
	key[0] = 7
	pc, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer pc.Close()
	go func() {
		buf := make([]byte, 4096)
		for {
			n, addr, err := pc.ReadFrom(buf)
			if err != nil {
				return
			}
			m := new(dns.Msg)
			if m.Unpack(buf[:n]) != nil {
				continue
			}
			label := strings.SplitN(m.Question[0].Name, ".", 2)[0]
			q, err := b32.DecodeString(strings.ToUpper(label))
			resp := new(dns.Msg)
			resp.SetReply(m)
			resp.Authoritative = true
			if err == nil && len(q) == labelSize {
				size := 0
				if opt := m.IsEdns0(); opt != nil {
					size = int(opt.UDPSize())
				}
				txt := []string{Answer(key, q), "seen edns=" + strconv.Itoa(size) + " case=0 from=192.0.2.1"}
				if q[1] == 1 {
					if size < 1200 {
						resp.Truncated = true
						out, _ := resp.Pack()
						pc.WriteTo(out, addr)
						continue
					}
					for range 4 {
						txt = append(txt, strings.Repeat("0123456789", 24))
					}
				}
				resp.Answer = []dns.RR{&dns.TXT{Hdr: dns.RR_Header{Name: m.Question[0].Name, Rrtype: dns.TypeTXT, Class: dns.ClassINET}, Txt: txt}}
			}
			out, _ := resp.Pack()
			pc.WriteTo(out, addr)
		}
	}()
	dial := func(ctx context.Context, network, address string) (net.Conn, error) {
		return (&net.Dialer{}).DialContext(ctx, network, address)
	}
	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	addr := "udp://" + pc.LocalAddr().String()
	small := Probe(ctx, dial, addr, "t.example.com", key, false, time.Now())
	if !small.OK || small.EDNS != ednsSize || small.From != "192.0.2.1" {
		t.Fatalf("small %+v", small)
	}
	large := Probe(ctx, dial, addr, "t.example.com", key, true, time.Now())
	if !large.OK {
		t.Fatalf("large %+v", large)
	}
	// another key: the answer's MAC does not match, as with a resolver answering the zone itself
	other := make([]byte, 32)
	if r := Probe(ctx, dial, addr, "t.example.com", other, false, time.Now()); r.OK {
		t.Fatalf("a wrong key passed: %+v", r)
	}
}

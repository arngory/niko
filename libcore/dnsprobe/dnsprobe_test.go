package dnsprobe

import (
	"context"
	"crypto/hmac"
	"crypto/sha256"
	"encoding/binary"
	"encoding/hex"
	"net"
	"net/netip"
	"strings"
	"sync/atomic"
	"testing"
	"time"

	"github.com/miekg/dns"
)

// dnsprobe-v1 §7 vectors (raycontrol e646e21), checked outside Go too
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
	// the node saw EDNS 1232, case 1, from 198.51.100.7
	plain := plaintext(key, q, 1232, true, netip.MustParseAddr("198.51.100.7"), false)
	sealed := Crypt(key, q, plain)
	if got := hex.EncodeToString(sealed); got != "42c10f1d94c67a0d8a02d86b2d81e9f1e2733f6dbdb92d439b28a37af4894debbcd0a1" {
		t.Fatalf("answer %s", got)
	}
	if !hmac.Equal(Crypt(key, q, sealed), plain) {
		t.Fatal("decrypting does not give the plaintext back")
	}
	// a resolver flipping a bit of what the node saw (the stream is a plain XOR): the token no longer holds
	tampered := append([]byte{}, sealed...)
	tampered[17] ^= 1
	if p := Crypt(key, q, tampered); hmac.Equal(p[:16], Token(key, q, p[16:smallPlain])) {
		t.Fatal("a changed EDNS size passed")
	}
}

// plaintext is the node's answer before encryption (§4).
func plaintext(key, q []byte, edns int, upper bool, from netip.Addr, large bool) []byte {
	seen := binary.BigEndian.AppendUint16(nil, uint16(edns))
	if upper {
		seen = append(seen, 1)
	} else {
		seen = append(seen, 0)
	}
	addr := from.As16()
	seen = append(seen, addr[:]...)
	p := append(Token(key, q, seen), seen...)
	if large {
		p = append(p, make([]byte, largePlain-smallPlain)...)
	}
	return p
}

// txt is data as dnstt puts it in a TXT record: 255-byte strings and the rest.
func txt(name string, data []byte) dns.RR {
	var rdata []byte
	for len(data) > 0 {
		n := min(len(data), 255)
		rdata = append(append(rdata, byte(n)), data[:n]...)
		data = data[n:]
	}
	return &dns.RFC3597{Hdr: dns.RR_Header{Name: name, Rrtype: dns.TypeTXT, Class: dns.ClassINET, Ttl: 60}, Rdata: hex.EncodeToString(rdata)}
}

// a fake front over UDP: answers a valid probe label as the node does (a large probe small when the EDNS it sees
// has no room), anything else as the tunnel's backend does (NOERROR, no records)
func TestProbeUDP(t *testing.T) {
	key := make([]byte, 32)
	key[0] = 7
	var limit atomic.Int32 // the EDNS the front takes the resolver to have, 0: the query's
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
			resp := new(dns.Msg)
			resp.SetReply(m)
			resp.Authoritative = true
			resp.RecursionDesired = false
			name := m.Question[0].Name
			labels := strings.SplitN(name, ".", 2)
			q, err := b32.DecodeString(strings.ToUpper(labels[0]))
			if err == nil && len(q) == labelSize && strings.EqualFold(labels[1], "t.example.com.") {
				mac := hmac.New(sha256.New, key)
				mac.Write([]byte("dnsprobe-v1 q" + "t.example.com"))
				mac.Write(q[:14])
				if hmac.Equal(mac.Sum(nil)[:10], q[14:]) {
					size := 0
					if opt := m.IsEdns0(); opt != nil {
						size = int(opt.UDPSize())
					}
					if l := int(limit.Load()); l != 0 {
						size = l
					}
					large := q[1] == 1 && size >= 1000
					from := netip.MustParseAddrPort(addr.String()).Addr()
					resp.Answer = []dns.RR{txt(name, Crypt(key, q, plaintext(key, q, size, false, from, large)))}
					resp.SetEdns0(4096, false)
				}
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
	if !small.OK || small.Large || small.EDNS != ednsSize || small.From != "127.0.0.1" {
		t.Fatalf("small %+v", small)
	}
	large := Probe(ctx, dial, addr, "t.example.com", key, true, time.Now())
	if !large.OK || !large.Large {
		t.Fatalf("large %+v", large)
	}
	// the resolver told the node a smaller EDNS: the small answer to a large probe, the size in it
	limit.Store(512)
	if r := Probe(ctx, dial, addr, "t.example.com", key, true, time.Now()); !r.OK || r.Large || r.EDNS != 512 {
		t.Fatalf("large at 512 %+v", r)
	}
	limit.Store(0)
	// another key (a stale one, as the node sees it): not a probe there, the backend's empty answer
	other := make([]byte, 32)
	if r := Probe(ctx, dial, addr, "t.example.com", other, false, time.Now()); r.OK || !r.Unclear {
		t.Fatalf("a wrong key passed: %+v", r)
	}
}

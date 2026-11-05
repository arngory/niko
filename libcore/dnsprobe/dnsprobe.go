// Package dnsprobe checks a resolver for a DNS tunnel (raycontrol dnsprobe-v1): one TXT query in the tunnel's zone
// with a label the node's front answers itself, MAC'd with the subscription's key. 1-2 round trips, no tunnel
// session: does the resolver bring the zone's queries to the node, and does it pass a large answer. The answer is
// built as dnstt's, its TXT data an answer nonce and, encrypted with it, a token under the key over what the node saw
// of the resolver. Only dnstt zones answer probes (masterdns's answers look otherwise).
package dnsprobe

import (
	"bytes"
	"context"
	"crypto/hmac"
	"crypto/rand"
	"crypto/sha256"
	"crypto/tls"
	"encoding/base32"
	"encoding/binary"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"strings"
	"time"

	"github.com/miekg/dns"
)

const (
	version   = 1
	labelSize = 24
	// EDNS asks for this much: the node sends a large answer (up to 1232 bytes on the wire) from 1206 on
	ednsSize = 1232
	// the answer's plaintext (§4): token 16, EDNS 2, 0x20 1, the resolver's address 16; a large one zeros to 900
	smallPlain = 35
	largePlain = 900
	// the answer's nonce before the ciphertext, new in every answer
	answerNonce = 8
)

var b32 = base32.StdEncoding.WithPadding(base32.NoPadding)

// Label is the query label for zone at minute with nonce (dnsprobe-v1 §3), and its 24 bytes.
func Label(key []byte, zone string, minute uint32, nonce [8]byte, large bool) (string, []byte) {
	q := make([]byte, 14, labelSize)
	q[0] = version
	if large {
		q[1] = 1
	}
	binary.BigEndian.PutUint32(q[2:6], minute)
	copy(q[6:14], nonce[:])
	mac := hmac.New(sha256.New, key)
	mac.Write([]byte("dnsprobe-v1 q"))
	mac.Write([]byte(normalZone(zone)))
	mac.Write(q)
	q = append(q, mac.Sum(nil)[:10]...)
	return strings.ToLower(b32.EncodeToString(q)), q
}

// Token is the first 16 bytes of the answer's plaintext for the label's bytes q (§4), over what the node saw of the
// resolver (the plaintext's bytes 16-34): a resolver cannot change them unseen.
func Token(key, q, seen []byte) []byte {
	mac := hmac.New(sha256.New, key)
	mac.Write([]byte("dnsprobe-v1 a"))
	mac.Write(q)
	mac.Write(seen)
	return mac.Sum(nil)[:16]
}

// Crypt encrypts or decrypts the answer's plaintext for the label's bytes q under the answer's nonce n (§4): XOR with
// the blocks HMAC-SHA256(Ke, q || n || uint32_be(i)), Ke = HMAC-SHA256(key, "dnsprobe-v1 e").
func Crypt(key, q, n, data []byte) []byte {
	ke := hmac.New(sha256.New, key)
	ke.Write([]byte("dnsprobe-v1 e"))
	stream := hmac.New(sha256.New, ke.Sum(nil))
	out := make([]byte, len(data))
	var block []byte
	for i := range data {
		if i%sha256.Size == 0 {
			stream.Reset()
			stream.Write(q)
			stream.Write(n)
			stream.Write(binary.BigEndian.AppendUint32(nil, uint32(i/sha256.Size)))
			block = stream.Sum(block[:0])
		}
		out[i] = data[i] ^ block[i%sha256.Size]
	}
	return out
}

func normalZone(zone string) string {
	return strings.ToLower(strings.TrimSuffix(strings.TrimSpace(zone), "."))
}

// Result of one probe: OK when the node's answer came with a valid token; Large when it was the large one (a large
// probe answered small: the resolver told the node a smaller EDNS, the tunnel goes slower); what the node saw of the
// resolver (its EDNS size, 0x20, its address). Unclear: an answer came, not the node's (the resolver answers the zone
// itself, or the key is stale: §6 has the client refresh the subscription and ask once more).
type Result struct {
	OK      bool   `json:"ok"`
	Large   bool   `json:"large"`
	Unclear bool   `json:"unclear,omitempty"`
	RTTMs   int64  `json:"rtt_ms"`
	EDNS    int    `json:"edns,omitempty"`
	Case    bool   `json:"case,omitempty"`
	From    string `json:"from,omitempty"`
	Error   string `json:"error,omitempty"`
}

// Dialer opens the connections of a probe (protected from the app's VPN by the caller).
type Dialer func(ctx context.Context, network, address string) (net.Conn, error)

// Probe asks resolver once: udp://host[:53] (or a bare address), tls://host[:853] (DoT), https://... (DoH).
func Probe(ctx context.Context, dial Dialer, resolver, zone string, key []byte, large bool, now time.Time) Result {
	var r Result
	if len(key) != 32 {
		r.Error = "no probe key"
		return r
	}
	var nonce [8]byte
	rand.Read(nonce[:])
	label, q := Label(key, zone, uint32(now.Unix()/60), nonce, large)
	m := new(dns.Msg)
	m.SetQuestion(dns.Fqdn(label+"."+normalZone(zone)), dns.TypeTXT)
	m.RecursionDesired = true
	m.SetEdns0(ednsSize, false)
	m.Id = dns.Id()
	start := time.Now()
	resp, err := exchange(ctx, dial, resolver, m)
	r.RTTMs = time.Since(start).Milliseconds()
	if err != nil {
		r.Error = err.Error()
		return r
	}
	if resp.Id != m.Id || len(resp.Question) != 1 || !strings.EqualFold(resp.Question[0].Name, m.Question[0].Name) ||
		resp.Question[0].Qtype != dns.TypeTXT {
		r.Error = "an answer to another query"
		return r
	}
	if resp.Rcode != dns.RcodeSuccess {
		r.Error = "rcode " + dns.RcodeToString[resp.Rcode]
		// NXDOMAIN is an answer for the zone, not a failure to reach it: the resolver's own, or the tunnel's backend
		// (a stale key is no probe at the node)
		r.Unclear = resp.Rcode == dns.RcodeNameError
		return r
	}
	for _, rr := range resp.Answer {
		txt, ok := rr.(*dns.TXT)
		if !ok {
			continue
		}
		data, err := txtData(txt)
		if err != nil || (len(data) != answerNonce+smallPlain && len(data) != answerNonce+largePlain) {
			continue
		}
		p := Crypt(key, q, data[:answerNonce], data[answerNonce:])
		if !hmac.Equal(p[:16], Token(key, q, p[16:smallPlain])) {
			continue
		}
		if len(p) == largePlain {
			if !large || !zeros(p[smallPlain:]) {
				continue
			}
			r.Large = true
		}
		r.OK = true
		r.EDNS = int(binary.BigEndian.Uint16(p[16:18]))
		r.Case = p[18] == 1
		r.From = netip.AddrFrom16([16]byte(p[19:35])).Unmap().String()
		return r
	}
	r.Unclear = true
	r.Error = "no answer of the node (the resolver answers the zone itself, or the probe key is stale)"
	return r
}

// txtData is the TXT record's strings as bytes, joined (dnstt splits its data into 255-byte strings): miekg/dns holds
// them escaped, packing them again undoes that.
func txtData(txt *dns.TXT) ([]byte, error) {
	buf := make([]byte, 65535)
	off, err := dns.PackRR(txt, buf, 0, nil, false)
	if err != nil {
		return nil, err
	}
	rdata := buf[off-int(txt.Hdr.Rdlength) : off]
	var out []byte
	for len(rdata) > 0 {
		n := int(rdata[0])
		if 1+n > len(rdata) {
			return nil, errors.New("a TXT string past its record")
		}
		out = append(out, rdata[1:1+n]...)
		rdata = rdata[1+n:]
	}
	return out, nil
}

func zeros(b []byte) bool {
	for _, c := range b {
		if c != 0 {
			return false
		}
	}
	return true
}

func exchange(ctx context.Context, dial Dialer, resolver string, m *dns.Msg) (*dns.Msg, error) {
	packed, err := m.Pack()
	if err != nil {
		return nil, err
	}
	switch {
	case strings.HasPrefix(resolver, "https://"):
		return doh(ctx, dial, resolver, packed)
	case strings.HasPrefix(resolver, "tls://"):
		return stream(ctx, dial, hostPort(strings.TrimPrefix(resolver, "tls://"), "853"), packed, true)
	case strings.HasPrefix(resolver, "tcp://"):
		return stream(ctx, dial, hostPort(strings.TrimPrefix(resolver, "tcp://"), "53"), packed, false)
	default:
		return udp(ctx, dial, hostPort(strings.TrimPrefix(resolver, "udp://"), "53"), packed, m.Id)
	}
}

func hostPort(s, port string) string {
	s = strings.TrimSuffix(s, "/")
	if _, _, err := net.SplitHostPort(s); err == nil {
		return s
	}
	return net.JoinHostPort(strings.Trim(s, "[]"), port)
}

func udp(ctx context.Context, dial Dialer, address string, packed []byte, id uint16) (*dns.Msg, error) {
	conn, err := dial(ctx, "udp", address)
	if err != nil {
		return nil, err
	}
	defer conn.Close()
	if deadline, ok := ctx.Deadline(); ok {
		conn.SetDeadline(deadline)
	}
	if _, err = conn.Write(packed); err != nil {
		return nil, err
	}
	buf := make([]byte, 65535)
	for {
		n, err := conn.Read(buf)
		if err != nil {
			return nil, err
		}
		resp := new(dns.Msg)
		// another id is a stray answer: wait for ours
		if resp.Unpack(buf[:n]) == nil && resp.Id == id {
			return resp, nil
		}
	}
}

func stream(ctx context.Context, dial Dialer, address string, packed []byte, useTLS bool) (*dns.Msg, error) {
	conn, err := dial(ctx, "tcp", address)
	if err != nil {
		return nil, err
	}
	defer conn.Close()
	if deadline, ok := ctx.Deadline(); ok {
		conn.SetDeadline(deadline)
	}
	if useTLS {
		host, _, _ := net.SplitHostPort(address)
		tlsConn := tls.Client(conn, &tls.Config{ServerName: host, MinVersion: tls.VersionTLS12})
		if err = tlsConn.HandshakeContext(ctx); err != nil {
			return nil, err
		}
		conn = tlsConn
	}
	frame := binary.BigEndian.AppendUint16(nil, uint16(len(packed)))
	if _, err = conn.Write(append(frame, packed...)); err != nil {
		return nil, err
	}
	var size [2]byte
	if _, err = io.ReadFull(conn, size[:]); err != nil {
		return nil, err
	}
	body := make([]byte, binary.BigEndian.Uint16(size[:]))
	if _, err = io.ReadFull(conn, body); err != nil {
		return nil, err
	}
	resp := new(dns.Msg)
	return resp, resp.Unpack(body)
}

func doh(ctx context.Context, dial Dialer, endpoint string, packed []byte) (*dns.Msg, error) {
	u, err := url.Parse(endpoint)
	if err != nil {
		return nil, err
	}
	client := &http.Client{
		Transport: &http.Transport{DialContext: dial, ForceAttemptHTTP2: true},
		// a resolver that redirects is not one to tunnel through
		CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
	}
	defer client.CloseIdleConnections()
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, u.String(), bytes.NewReader(packed))
	if err != nil {
		return nil, err
	}
	req.Header.Set("Content-Type", "application/dns-message")
	req.Header.Set("Accept", "application/dns-message")
	resp, err := client.Do(req)
	if err != nil {
		return nil, err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("DoH: %s", resp.Status)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, 65535))
	if err != nil {
		return nil, err
	}
	m := new(dns.Msg)
	if err = m.Unpack(body); err != nil {
		return nil, errors.New("DoH: not a DNS message")
	}
	return m, nil
}

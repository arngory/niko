// Package dnsprobe checks a resolver for a DNS tunnel (raycontrol dnsprobe-v1): one TXT query in the tunnel's zone
// with a label the node's front answers itself, MAC'd with the subscription's key. 1-2 round trips, no tunnel
// session: does the resolver bring the zone's queries to the node, and does it pass a large answer.
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
	"net/url"
	"strconv"
	"strings"
	"time"

	"github.com/miekg/dns"
)

const (
	version   = 1
	labelSize = 24
	// EDNS asks for this much: a large answer (~1150 bytes) needs it
	ednsSize = 1232
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

// Answer is the first TXT string the node answers for the label's bytes q (§4).
func Answer(key, q []byte) string {
	mac := hmac.New(sha256.New, key)
	mac.Write([]byte("dnsprobe-v1 a"))
	mac.Write(q)
	return "v1 " + strings.ToLower(b32.EncodeToString(mac.Sum(nil)[:16]))
}

func normalZone(zone string) string {
	return strings.ToLower(strings.TrimSuffix(strings.TrimSpace(zone), "."))
}

// Result of one probe: OK when the node's answer came with a valid MAC; TC when the resolver had no room for a large
// answer (the tunnel still goes, slower); what the node saw of the resolver (its EDNS size, 0x20, its address).
type Result struct {
	OK    bool   `json:"ok"`
	Large bool   `json:"large"`
	TC    bool   `json:"tc,omitempty"`
	RTTMs int64  `json:"rtt_ms"`
	EDNS  int    `json:"edns,omitempty"`
	Case  bool   `json:"case,omitempty"`
	From  string `json:"from,omitempty"`
	Error string `json:"error,omitempty"`
}

// Dialer opens the connections of a probe (protected from the app's VPN by the caller).
type Dialer func(ctx context.Context, network, address string) (net.Conn, error)

// Probe asks resolver once: udp://host[:53] (or a bare address), tls://host[:853] (DoT), https://... (DoH).
func Probe(ctx context.Context, dial Dialer, resolver, zone string, key []byte, large bool, now time.Time) Result {
	r := Result{Large: large}
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
	if resp.Truncated && len(resp.Answer) == 0 {
		// no room for the large answer at the resolver's EDNS: usable, slower
		r.TC = true
		return r
	}
	if resp.Rcode != dns.RcodeSuccess {
		r.Error = "rcode " + dns.RcodeToString[resp.Rcode]
		return r
	}
	want := Answer(key, q)
	for _, rr := range resp.Answer {
		txt, ok := rr.(*dns.TXT)
		if !ok || len(txt.Txt) == 0 || txt.Txt[0] != want {
			continue
		}
		r.OK = true
		if len(txt.Txt) > 1 {
			r.EDNS, r.Case, r.From = seen(txt.Txt[1])
		}
		if large && len(strings.Join(txt.Txt, "")) < 900 {
			r.OK, r.Error = false, "the large answer came short"
		}
		return r
	}
	r.Error = "no answer of the node (the resolver answers the zone itself, or rewrites it)"
	return r
}

// seen parses "seen edns=<N> case=<0|1> from=<address>".
func seen(s string) (edns int, upper bool, from string) {
	for _, f := range strings.Fields(s) {
		k, v, _ := strings.Cut(f, "=")
		switch k {
		case "edns":
			edns, _ = strconv.Atoi(v)
		case "case":
			upper = v == "1"
		case "from":
			from = v
		}
	}
	return
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

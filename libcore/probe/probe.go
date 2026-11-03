// Package probe measures a channel through a tunnel: a TCP download (time to
// connect, to TLS, to the first byte, throughput, and after how many bytes the
// transfer stalled) and UDP round trips (DNS queries: RTT, jitter, loss,
// reordering). The app runs it in a test instance of one profile and ranks the
// profiles of a node by the results. TCP and UDP are measured apart: a network
// may pass one and not the other.
package probe

import (
	"context"
	"crypto/rand"
	"crypto/tls"
	"encoding/binary"
	"errors"
	"io"
	"math"
	"net"
	"net/http"
	"net/http/httptrace"
	"sync"
	"sync/atomic"
	"time"
)

// Options of a probe. Zero values take the defaults.
type Options struct {
	// TCPURL is a download of at least TCPBytes; empty skips TCP.
	TCPURL string `json:"tcp_url"`
	// TCPBytes ends the download after this many bytes (default 256 KiB): enough to pass the ~16-20 KB at which
	// some filters freeze a connection, small enough for a mobile network.
	TCPBytes int64 `json:"tcp_bytes"`
	// UDPServer is a DNS server (host:port) asked over UDP; empty skips UDP.
	UDPServer string `json:"udp_server"`
	// UDPCount queries, one every UDPIntervalMs (defaults 10 and 200).
	UDPCount      int `json:"udp_count"`
	UDPIntervalMs int `json:"udp_interval_ms"`
	// WindowMs limits each of TCP and UDP (default 5000, at most 30000).
	WindowMs int `json:"window_ms"`
	// StallMs without a byte is a stall (default 2000).
	StallMs int `json:"stall_ms"`
}

// TCPResult of a download. Times are from the start of the request.
type TCPResult struct {
	OK          bool   `json:"ok"`
	Error       string `json:"error,omitempty"`
	ConnectMs   int64  `json:"connect_ms"`
	TLSMs       int64  `json:"tls_ms"`
	FirstByteMs int64  `json:"first_byte_ms"`
	Bytes       int64  `json:"bytes"`
	// DurationMs from the first byte to the last one read
	DurationMs int64 `json:"duration_ms"`
	Kbps       int64 `json:"kbps"`
	// StalledAt is the byte count when no byte came for StallMs; -1 when the transfer did not stall
	StalledAt int64 `json:"stalled_at"`
}

// UDPResult of the DNS round trips.
type UDPResult struct {
	OK        bool    `json:"ok"`
	Error     string  `json:"error,omitempty"`
	Sent      int     `json:"sent"`
	Received  int     `json:"received"`
	Reordered int     `json:"reordered"`
	LossPct   float64 `json:"loss_pct"`
	RTTMinMs  float64 `json:"rtt_min_ms"`
	RTTAvgMs  float64 `json:"rtt_avg_ms"`
	RTTMaxMs  float64 `json:"rtt_max_ms"`
	// JitterMs is the mean difference between consecutive round trips
	JitterMs float64 `json:"jitter_ms"`
}

const (
	defaultTCPBytes = 256 << 10
	defaultWindow   = 5 * time.Second
	maxWindow       = 30 * time.Second
	defaultStall    = 2 * time.Second
	defaultCount    = 10
	defaultInterval = 200 * time.Millisecond
)

func (o Options) window() time.Duration {
	w := time.Duration(o.WindowMs) * time.Millisecond
	if w <= 0 {
		return defaultWindow
	}
	return min(w, maxWindow)
}

func (o Options) stall() time.Duration {
	if o.StallMs <= 0 {
		return defaultStall
	}
	return time.Duration(o.StallMs) * time.Millisecond
}

// TCP downloads o.TCPURL with client (its transport dials through the tunnel).
func TCP(ctx context.Context, client *http.Client, o Options) *TCPResult {
	r := &TCPResult{StalledAt: -1}
	limit := o.TCPBytes
	if limit <= 0 {
		limit = defaultTCPBytes
	}
	ctx, cancel := context.WithTimeout(ctx, o.window())
	defer cancel()
	start := time.Now()
	since := func() int64 { return time.Since(start).Milliseconds() }
	var firstByte atomic.Int64
	trace := &httptrace.ClientTrace{
		// the transport dials, then does TLS itself: the dial through the tunnel is done when TLS starts
		TLSHandshakeStart: func() { r.ConnectMs = since() },
		TLSHandshakeDone:  func(tls.ConnectionState, error) { r.TLSMs = since() },
		GotFirstResponseByte: func() {
			firstByte.Store(since())
		},
	}
	req, err := http.NewRequestWithContext(httptrace.WithClientTrace(ctx, trace), http.MethodGet, o.TCPURL, nil)
	if err != nil {
		r.Error = err.Error()
		return r
	}
	req.Header.Set("Cache-Control", "no-cache")
	resp, err := client.Do(req)
	if err != nil {
		r.Error = err.Error()
		return r
	}
	defer resp.Body.Close()
	r.FirstByteMs = firstByte.Load()
	if resp.StatusCode != http.StatusOK {
		r.Error = resp.Status
		return r
	}

	// the body is read in a goroutine; this one watches progress, the window and the limit
	var read atomic.Int64
	var lastRead atomic.Int64
	lastRead.Store(time.Now().UnixNano())
	done := make(chan error, 1)
	go func() {
		buf := make([]byte, 32<<10)
		for {
			n, err := resp.Body.Read(buf)
			if n > 0 {
				lastRead.Store(time.Now().UnixNano())
				if read.Add(int64(n)) >= limit {
					done <- nil
					return
				}
			}
			if err != nil {
				if errors.Is(err, io.EOF) {
					err = nil
				}
				done <- err
				return
			}
		}
	}()
	tick := time.NewTicker(100 * time.Millisecond)
	defer tick.Stop()
	stall := o.stall()
	for finished := false; !finished; {
		select {
		case err = <-done:
			finished = true
			if err != nil && ctx.Err() == nil {
				r.Error = err.Error()
			}
		case <-tick.C:
			if time.Since(time.Unix(0, lastRead.Load())) >= stall {
				r.StalledAt = read.Load()
				r.Error = "stalled"
				finished = true
			}
		case <-ctx.Done():
			finished = true
		}
	}
	cancel()
	end := time.Unix(0, lastRead.Load())
	r.Bytes = read.Load()
	r.DurationMs = max(end.Sub(start).Milliseconds()-r.FirstByteMs, 0)
	if r.DurationMs > 0 {
		r.Kbps = r.Bytes * 8 / r.DurationMs
	}
	// the window ending a slow download is a result, not a failure: the speed says it
	r.OK = r.Error == "" && r.Bytes > 0
	return r
}

// UDP sends DNS queries over conn (a UDP connection through the tunnel to o.UDPServer) and matches the answers.
// It closes conn when it is done.
func UDP(ctx context.Context, conn net.Conn, o Options) *UDPResult {
	r := &UDPResult{}
	count := o.UDPCount
	if count <= 0 {
		count = defaultCount
	}
	interval := time.Duration(o.UDPIntervalMs) * time.Millisecond
	if interval <= 0 {
		interval = defaultInterval
	}
	ctx, cancel := context.WithTimeout(ctx, o.window())
	defer cancel()
	// the reader ends when conn closes: some packet conns ignore deadlines (sing-quic's SetDeadline is a no-op)
	stop := sync.OnceFunc(func() {
		conn.SetReadDeadline(time.Now())
		conn.Close()
	})
	go func() {
		<-ctx.Done()
		stop()
	}()

	var base [2]byte
	rand.Read(base[:])
	first := binary.BigEndian.Uint16(base[:])
	var mu sync.Mutex
	sentAt := make([]time.Time, count)
	rtts := make([]float64, 0, count)
	got := make([]bool, count)
	highest := -1

	readerDone := make(chan struct{})
	go func() {
		defer close(readerDone)
		buf := make([]byte, 1500)
		for {
			n, err := conn.Read(buf)
			if err != nil {
				return
			}
			if n < 12 {
				continue
			}
			seq := int(binary.BigEndian.Uint16(buf[:2]) - first)
			mu.Lock()
			if seq >= 0 && seq < count && !sentAt[seq].IsZero() && !got[seq] {
				got[seq] = true
				rtts = append(rtts, float64(time.Since(sentAt[seq]).Microseconds())/1000)
				if seq < highest {
					r.Reordered++
				}
				highest = max(highest, seq)
			}
			all := len(rtts) == count
			mu.Unlock()
			if all {
				return
			}
		}
	}()

	for seq := 0; seq < count && ctx.Err() == nil; seq++ {
		mu.Lock()
		sentAt[seq] = time.Now()
		mu.Unlock()
		if _, err := conn.Write(dnsQuery(first+uint16(seq), "www.gstatic.com")); err != nil {
			r.Error = err.Error()
			break
		}
		r.Sent++
		if seq < count-1 {
			select {
			case <-time.After(interval):
			case <-ctx.Done():
			}
		}
	}
	// late answers: up to a second after the last query, within the window
	select {
	case <-readerDone:
	case <-time.After(time.Second):
	case <-ctx.Done():
	}
	stop()
	select {
	case <-readerDone:
	case <-time.After(time.Second):
		// a conn whose Read outlives Close: the reader is left behind, the results so far stand
	}

	mu.Lock()
	defer mu.Unlock()
	r.Received = len(rtts)
	if r.Sent > 0 {
		r.LossPct = math.Round(float64(r.Sent-r.Received)*1000/float64(r.Sent)) / 10
	}
	if len(rtts) > 0 {
		r.RTTMinMs, r.RTTMaxMs = rtts[0], rtts[0]
		var sum, diff float64
		for i, v := range rtts {
			sum += v
			r.RTTMinMs = min(r.RTTMinMs, v)
			r.RTTMaxMs = max(r.RTTMaxMs, v)
			if i > 0 {
				diff += math.Abs(v - rtts[i-1])
			}
		}
		r.RTTAvgMs = round1(sum / float64(len(rtts)))
		if len(rtts) > 1 {
			r.JitterMs = round1(diff / float64(len(rtts)-1))
		}
		r.RTTMinMs, r.RTTMaxMs = round1(r.RTTMinMs), round1(r.RTTMaxMs)
	}
	r.OK = r.Received > 0
	if !r.OK && r.Error == "" {
		r.Error = "no answer"
	}
	return r
}

func round1(v float64) float64 { return math.Round(v*10) / 10 }

// dnsQuery is a recursive query for the A record of name.
func dnsQuery(id uint16, name string) []byte {
	q := make([]byte, 12, 12+len(name)+6)
	binary.BigEndian.PutUint16(q[0:], id)
	binary.BigEndian.PutUint16(q[2:], 0x0100) // RD
	binary.BigEndian.PutUint16(q[4:], 1)      // QDCOUNT
	start := 0
	for i := 0; i <= len(name); i++ {
		if i == len(name) || name[i] == '.' {
			q = append(q, byte(i-start))
			q = append(q, name[start:i]...)
			start = i + 1
		}
	}
	return append(q, 0, 0, 1, 0, 1) // root, A, IN
}

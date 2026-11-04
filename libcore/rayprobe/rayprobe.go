// Package rayprobe is the client of a raycontrol node's channel probe
// (raycontrol docs/spec/probe-v1.md): TCP down and up with the server's view of
// the connection (TCP_INFO), the UDP echo (RTT, loss each way, reordering), and
// the server's view of the device's connections (link). The probe shares the
// subscription endpoint: its TLS with the key pin and the device's RC1 token.
// It runs directly (after SPA, to host:sub.port) or through a profile's tunnel
// (to the probe name), as the Endpoint's Dial decides.
package rayprobe

import (
	"bytes"
	"context"
	"crypto/rand"
	"encoding/base64"
	"encoding/binary"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"math"
	"net"
	"net/http"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"libcore/probe"
	"libcore/raysub"
)

// Endpoint is where the probe goes and as whom.
type Endpoint struct {
	// Address is host:port: the node's host and sub.port directly, the probe name (any port) through a tunnel
	Address string
	// Pin is sub.pin; ServerName the TLS name (the host, or the probe name), empty for an IP
	Pin        string
	ServerName string
	DeviceID   string
	DeviceKey  []byte
	// Dial reaches Address (TCP) and the echo port (UDP): a protected dialer directly, the tunnel's otherwise
	Dial func(ctx context.Context, network, address string) (net.Conn, error)
	Now  func() time.Time
}

func (e *Endpoint) now() time.Time {
	if e.Now != nil {
		return e.Now()
	}
	return time.Now()
}

// Client is one probe session's HTTP client (one connection, reused across requests).
type Client struct {
	e    *Endpoint
	http *http.Client
}

func NewClient(e *Endpoint) *Client {
	return &Client{e: e, http: &http.Client{Transport: &http.Transport{
		DialContext:       e.Dial,
		TLSClientConfig:   raysub.TLSConfig(e.ServerName, e.Pin),
		ForceAttemptHTTP2: true,
		Proxy:             nil,
	}}}
}

func (c *Client) Close() { c.http.CloseIdleConnections() }

func (c *Client) request(ctx context.Context, method, path string, body io.Reader) (*http.Request, error) {
	req, err := http.NewRequestWithContext(ctx, method, "https://"+c.e.Address+path, body)
	if err != nil {
		return nil, err
	}
	req.Header.Set("X-Raycontrol-Device", c.e.DeviceID)
	req.Header.Set("Authorization", raysub.Authorization(c.e.DeviceKey, c.e.now()))
	return req, nil
}

// StatusError is an answer other than 200: 404 (unknown device or bad token), 409 (link through a tunnel),
// 429 (a probe running, the daily volume spent, or link too often; RetryAfter in seconds when given).
type StatusError struct {
	Status     int
	RetryAfter int
}

func (e *StatusError) Error() string { return "probe: HTTP " + strconv.Itoa(e.Status) }

func statusError(resp *http.Response) error {
	e := &StatusError{Status: resp.StatusCode}
	if s := resp.Header.Get("Retry-After"); s != "" {
		e.RetryAfter, _ = strconv.Atoi(s)
	}
	return e
}

func (c *Client) do(ctx context.Context, method, path string, body io.Reader, out any) error {
	req, err := c.request(ctx, method, path, body)
	if err != nil {
		return err
	}
	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return statusError(resp)
	}
	if out == nil {
		return nil
	}
	return json.NewDecoder(io.LimitReader(resp.Body, 1<<20)).Decode(out)
}

// Down receives n bytes (probe-v1.md §3): the client's view (time to the first byte, speed, a stall) and the
// server's in Server (the Probe-Result trailer: bytes, ms, rtt_us, retrans, delivery_bps, ...).
func (c *Client) Down(ctx context.Context, n int64, o probe.Options) *DownResult {
	req, err := c.request(context.Background(), http.MethodGet, "/probe/v1/down?bytes="+strconv.FormatInt(n, 10), nil)
	if err != nil {
		return &DownResult{TCPResult: probe.TCPResult{StalledAt: -1, Error: err.Error()}}
	}
	// one byte past n: the read goes on to the end of the body, where the trailer is
	o.TCPBytes = n + 1
	r := probe.Download(ctx, c.http, req, o)
	out := &DownResult{TCPResult: *r}
	// an answer other than 200 comes as its status line ("429 Too Many Requests")
	if code, err := strconv.Atoi(strings.SplitN(r.Error, " ", 2)[0]); err == nil && code >= 400 {
		out.Status = code
	}
	if v := r.Trailer["Probe-Result"]; v != "" {
		out.Server = parseResult(v)
	}
	return out
}

// DownResult is Down's: the client's TCPResult and the server's Probe-Result.
type DownResult struct {
	probe.TCPResult
	Status int            `json:"status,omitempty"`
	Server map[string]any `json:"server,omitempty"`
}

// parseResult reads "key=value; key=value" with numbers as numbers.
func parseResult(s string) map[string]any {
	out := map[string]any{}
	for _, part := range strings.Split(s, ";") {
		k, v, ok := strings.Cut(strings.TrimSpace(part), "=")
		if !ok {
			continue
		}
		if n, err := strconv.ParseInt(v, 10, 64); err == nil {
			out[k] = n
		} else {
			out[k] = v
		}
	}
	return out
}

// UpResult is Up's: what the client sent and the server's answer (bytes and ms between the first and the last
// byte it got, and its TCP_INFO).
type UpResult struct {
	OK       bool           `json:"ok"`
	Error    string         `json:"error,omitempty"`
	Status   int            `json:"status,omitempty"`
	Sent     int64          `json:"sent"`
	ClientMs int64          `json:"client_ms"`
	Bytes    int64          `json:"bytes"`
	Ms       int64          `json:"ms"`
	Kbps     int64          `json:"kbps"`
	TCP      map[string]any `json:"tcp,omitempty"`
}

// Up sends n bytes (probe-v1.md §3); the speed is the server's: bytes over ms.
func (c *Client) Up(ctx context.Context, n int64, window time.Duration) *UpResult {
	r := &UpResult{}
	ctx, cancel := context.WithTimeout(ctx, window)
	defer cancel()
	body := &counter{r: io.LimitReader(zeros{}, n)}
	var answer struct {
		Bytes int64          `json:"bytes"`
		Ms    int64          `json:"ms"`
		TCP   map[string]any `json:"tcp"`
	}
	start := time.Now()
	req, err := c.request(ctx, http.MethodPost, "/probe/v1/up?bytes="+strconv.FormatInt(n, 10), body)
	if err == nil {
		req.ContentLength = n
		var resp *http.Response
		if resp, err = c.http.Do(req); err == nil {
			defer resp.Body.Close()
			if resp.StatusCode != http.StatusOK {
				err = statusError(resp)
			} else {
				err = json.NewDecoder(io.LimitReader(resp.Body, 1<<20)).Decode(&answer)
			}
		}
	}
	r.Sent, r.ClientMs = body.n.Load(), time.Since(start).Milliseconds()
	if err != nil {
		var status *StatusError
		if errors.As(err, &status) {
			r.Status = status.Status
		}
		r.Error = err.Error()
		return r
	}
	r.Bytes, r.Ms, r.TCP = answer.Bytes, answer.Ms, answer.TCP
	// the server's time from its first byte to its last; under a millisecond (a short body on a fast path) the
	// client's whole request time is the bound
	switch {
	case r.Ms > 0:
		r.Kbps = r.Bytes * 8 / r.Ms
	case r.ClientMs > 0:
		r.Kbps = r.Bytes * 8 / r.ClientMs
	}
	r.OK = r.Bytes > 0
	return r
}

type zeros struct{}

func (zeros) Read(b []byte) (int, error) {
	clear(b)
	return len(b), nil
}

// counter counts what the transport read of a request body; atomic, since the transport may still read it after
// Do returned (a deadline, an early answer)
type counter struct {
	r io.Reader
	n atomic.Int64
}

func (c *counter) Read(b []byte) (int, error) {
	n, err := c.r.Read(b)
	c.n.Add(int64(n))
	return n, err
}

// EchoResult is Echo's (probe-v1.md §4): round trips by the client's clock, losses each way from the server's
// count, reordering.
type EchoResult struct {
	OK        bool   `json:"ok"`
	Error     string `json:"error,omitempty"`
	Status    int    `json:"status,omitempty"`
	Size      int    `json:"size"`
	Sent      int    `json:"sent"`
	Received  int    `json:"received"`
	Reordered int    `json:"reordered"`
	// Lost is Sent - Received; LostUp and LostDown split it by the server's count, -1 when the count did not come
	Lost     int     `json:"lost"`
	LostUp   int     `json:"lost_up"`
	LostDown int     `json:"lost_down"`
	RTTMinMs float64 `json:"rtt_min_ms"`
	RTTAvgMs float64 `json:"rtt_avg_ms"`
	RTTMaxMs float64 `json:"rtt_max_ms"`
	JitterMs float64 `json:"jitter_ms"`
	// the server's count (GET /probe/v1/udp?token=): what it received and echoed
	Counted        bool  `json:"counted"`
	ServerReceived int64 `json:"server_received"`
	ServerEchoed   int64 `json:"server_echoed"`
}

const echoHeader = 36

// Echo opens a UDP session and sends count datagrams of size bytes (36 at least), one every interval, to the echo
// port at the Address's host, then closes the session for the server's count.
func (c *Client) Echo(ctx context.Context, count, size int, interval time.Duration) *EchoResult {
	r := &EchoResult{Size: max(size, echoHeader)}
	var session struct {
		Token   string `json:"token"`
		Port    int    `json:"port"`
		Seconds int    `json:"seconds"`
		MaxRate int    `json:"max_rate"`
	}
	if err := c.do(ctx, http.MethodPost, "/probe/v1/udp", nil, &session); err != nil {
		r.fail(err)
		return r
	}
	token, err := base64.RawURLEncoding.DecodeString(session.Token)
	if err != nil || len(token) != 16 {
		r.Error = "probe: bad session token"
		return r
	}
	if session.MaxRate > 0 {
		interval = max(interval, time.Second/time.Duration(session.MaxRate))
	}
	host, _, _ := net.SplitHostPort(c.e.Address)
	conn, err := c.e.Dial(ctx, "udp", net.JoinHostPort(host, strconv.Itoa(session.Port)))
	if err != nil {
		r.Error = err.Error()
		c.closeSession(ctx, session.Token, r)
		return r
	}
	r.exchange(ctx, conn, token, count, interval)
	c.closeSession(ctx, session.Token, r)
	r.Lost = max(r.Sent-r.Received, 0)
	r.LostUp, r.LostDown = -1, -1
	if r.Counted {
		r.LostUp = max(r.Sent-int(r.ServerReceived), 0)
		r.LostDown = max(int(r.ServerEchoed)-r.Received, 0)
	}
	r.OK = r.Received > 0
	if !r.OK && r.Error == "" {
		r.Error = "no echo"
	}
	return r
}

func (r *EchoResult) fail(err error) {
	var status *StatusError
	if errors.As(err, &status) {
		r.Status = status.Status
	}
	r.Error = err.Error()
}

func (c *Client) closeSession(ctx context.Context, token string, r *EchoResult) {
	var count struct {
		Received int64 `json:"received"`
		Echoed   int64 `json:"echoed"`
	}
	// a context of its own: the echo's window may be spent by now, and a session left open holds the device's one
	// probe until its seconds run out. A lost count leaves the losses unsplit (Counted false)
	ctx, cancel := context.WithTimeout(context.WithoutCancel(ctx), 5*time.Second)
	defer cancel()
	if err := c.do(ctx, http.MethodGet, "/probe/v1/udp?token="+token, nil, &count); err == nil {
		r.Counted = true
		r.ServerReceived, r.ServerEchoed = count.Received, count.Echoed
	}
}

func (r *EchoResult) exchange(ctx context.Context, conn net.Conn, token []byte, count int, interval time.Duration) {
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()
	// the reader ends when conn closes: some packet conns ignore deadlines
	stop := sync.OnceFunc(func() {
		conn.SetReadDeadline(time.Now())
		conn.Close()
	})
	defer stop()
	go func() {
		<-ctx.Done()
		stop()
	}()
	var mu sync.Mutex
	got := make([]bool, count)
	var rtts []float64
	highest := -1
	readerDone := make(chan struct{})
	go func() {
		defer close(readerDone)
		buf := make([]byte, 65536)
		for {
			n, err := conn.Read(buf)
			if err != nil {
				return
			}
			if n < echoHeader || !bytes.Equal(buf[:16], token) {
				continue
			}
			seq := int(binary.BigEndian.Uint32(buf[16:20]))
			sent := int64(binary.BigEndian.Uint64(buf[20:28]))
			mu.Lock()
			if seq >= 0 && seq < count && !got[seq] {
				got[seq] = true
				rtts = append(rtts, float64(time.Now().UnixNano()-sent)/1e6)
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
	datagram := make([]byte, r.Size)
	copy(datagram, token)
	rand.Read(datagram[echoHeader:])
	for seq := 0; seq < count && ctx.Err() == nil; seq++ {
		binary.BigEndian.PutUint32(datagram[16:20], uint32(seq))
		binary.BigEndian.PutUint64(datagram[20:28], uint64(time.Now().UnixNano()))
		clear(datagram[28:36])
		if _, err := conn.Write(datagram); err != nil {
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
	// late echoes: up to a second after the last datagram
	select {
	case <-readerDone:
	case <-time.After(time.Second):
	case <-ctx.Done():
	}
	stop()
	select {
	case <-readerDone:
	case <-time.After(time.Second):
	}
	mu.Lock()
	defer mu.Unlock()
	r.Received = len(rtts)
	if len(rtts) == 0 {
		return
	}
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

func round1(v float64) float64 { return math.Round(v*10) / 10 }

// Link is the server's view of the device's address (probe-v1.md §4.1), as the server sends it. Directly only:
// through a tunnel the server answers 409; not more often than every 5 s (429).
func (c *Client) Link(ctx context.Context) (json.RawMessage, error) {
	var raw json.RawMessage
	if err := c.do(ctx, http.MethodGet, "/probe/v1/link", nil, &raw); err != nil {
		return nil, err
	}
	return raw, nil
}

// MTUStep is one datagram size of PathMTU and how many of its echoes came back.
type MTUStep struct {
	Size     int    `json:"size"`
	Sent     int    `json:"sent"`
	Received int    `json:"received"`
	Error    string `json:"error,omitempty"`
}

// PathMTU sends a few echoes of each of sizes (UDP payload bytes, 36 at least) and reports what came back, and
// the largest size that came back at all: the node drops IP fragments (probe-v1.md §4, R4-9), so a datagram over
// the path's MTU is lost. QUIC (Hysteria2, naive-quic, DoH3) needs 1200 bytes and more. 0: none came back.
func (c *Client) PathMTU(ctx context.Context, sizes []int, count int) (int, []MTUStep) {
	largest := 0
	steps := make([]MTUStep, 0, len(sizes))
	for _, size := range sizes {
		if ctx.Err() != nil {
			break
		}
		r := c.Echo(ctx, count, max(size, echoHeader), 20*time.Millisecond)
		steps = append(steps, MTUStep{Size: r.Size, Sent: r.Sent, Received: r.Received, Error: r.Error})
		if r.Received > 0 && r.Size > largest {
			largest = r.Size
		}
	}
	return largest, steps
}

// MaxReport is the largest summary the node takes (probe-v1.md §6.1).
const MaxReport = 4 << 10

// Report sends the anonymous summary (probe-v1.md §6.1). nil: taken (204). A StatusError otherwise: 404 the node
// takes no summaries (or does not know the device), 409 not directly (through a tunnel), 429 this network's report
// of the UTC day is in (RetryAfter until the day's end), 400 a field or service the node does not take.
func (c *Client) Report(ctx context.Context, body []byte) error {
	if len(body) > MaxReport {
		return fmt.Errorf("probe: report of %d bytes, over %d", len(body), MaxReport)
	}
	req, err := c.request(ctx, http.MethodPost, "/probe/v1/report", bytes.NewReader(body))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	resp, err := c.http.Do(req)
	if err != nil {
		return err
	}
	defer resp.Body.Close()
	_, _ = io.Copy(io.Discard, io.LimitReader(resp.Body, 4096))
	if resp.StatusCode != http.StatusNoContent && resp.StatusCode != http.StatusOK {
		return statusError(resp)
	}
	return nil
}

// String is a short line for logs.
func (r *EchoResult) String() string {
	return fmt.Sprintf("udp %d/%d, lost %d (up %d, down %d), rtt %.1f ms", r.Received, r.Sent, r.Lost, r.LostUp, r.LostDown, r.RTTAvgMs)
}

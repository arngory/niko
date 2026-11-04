package libcore

import (
	"context"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net"
	"strconv"
	"time"

	"libcore/device"
	"libcore/knock"
	"libcore/probe"
	"libcore/rayprobe"

	"github.com/sagernet/sing-box/boxapi"
)

// probeRequest is a channel probe of a raycontrol node (probe-v1): the subscription's endpoint and device as for
// the subscription itself, and what to measure. Zero steps are skipped.
type probeRequest struct {
	Host     string             `json:"host"`
	Port     int                `json:"port"`
	Pin      string             `json:"pin"`
	DeviceID string             `json:"device_id"`
	SPA      knock.SPAv2Options `json:"spa"` // device_key set
	// through a tunnel: the probe name (probe.tunnel), dialled through the instance
	Tunnel string `json:"tunnel,omitempty"`

	DownBytes int64 `json:"down_bytes,omitempty"`
	UpBytes   int64 `json:"up_bytes,omitempty"`
	EchoCount int   `json:"echo_count,omitempty"`
	EchoSize  int   `json:"echo_size,omitempty"`
	Link      bool  `json:"link,omitempty"`
	// the anonymous summary to send (probe-v1.md §6.1), directly only
	Report json.RawMessage `json:"report,omitempty"`
	// echo datagram sizes for the path's MTU (rayprobe.Client.PathMTU), a few of each
	MTUSizes []int `json:"mtu_sizes,omitempty"`
	// each step's limit (default 10 s, at most the server's 30 s)
	WindowMs int `json:"window_ms,omitempty"`
}

type probeResult struct {
	Down      *rayprobe.DownResult `json:"down,omitempty"`
	Up        *rayprobe.UpResult   `json:"up,omitempty"`
	Echo      *rayprobe.EchoResult `json:"echo,omitempty"`
	Link      json.RawMessage      `json:"link,omitempty"`
	LinkError string               `json:"link_error,omitempty"`
	// the summary: 204 taken, or the node's status (404, 409, 429 with ReportRetryAfter, 400); 0 with ReportError
	// when no answer came
	ReportStatus     int    `json:"report_status,omitempty"`
	ReportRetryAfter int    `json:"report_retry_after,omitempty"`
	ReportError      string `json:"report_error,omitempty"`
	// the largest echo size that came back (0: none), and each size's count
	UDPMTU int                `json:"udp_mtu,omitempty"`
	MTU    []rayprobe.MTUStep `json:"mtu,omitempty"`
	// directly: the SPA carrier that opened the port, "udp" or "tcp" (spa-v2.md §10)
	SPACarrier string `json:"spa_carrier,omitempty"`
}

func (r *probeRequest) window() time.Duration {
	w := time.Duration(r.WindowMs) * time.Millisecond
	if w <= 0 {
		return 10 * time.Second
	}
	return min(w, 30*time.Second)
}

func (r *probeRequest) run(ctx context.Context, e *rayprobe.Endpoint) *probeResult {
	c := rayprobe.NewClient(e)
	defer c.Close()
	out := &probeResult{}
	window := r.window()
	if r.Link {
		raw, err := c.Link(ctx)
		if err != nil {
			out.LinkError = err.Error()
		} else {
			out.Link = raw
		}
	}
	if r.DownBytes > 0 {
		out.Down = c.Down(ctx, r.DownBytes, probe.Options{WindowMs: int(window.Milliseconds())})
	}
	if r.UpBytes > 0 {
		// past the server's 30 s: on a slow uplink the server stops at its limit and answers what it got
		out.Up = c.Up(ctx, r.UpBytes, max(window, 35*time.Second))
	}
	if r.EchoCount > 0 {
		echoCtx, cancel := context.WithTimeout(ctx, window)
		out.Echo = c.Echo(echoCtx, r.EchoCount, r.EchoSize, 20*time.Millisecond)
		cancel()
	}
	if len(r.MTUSizes) > 0 {
		// each size within the window: a lost one waits for late echoes, and on a slow path several of them would
		// spend a shared window before the small sizes that pass
		out.UDPMTU, out.MTU = c.PathMTU(ctx, r.MTUSizes, 4, window)
	}
	if len(r.Report) > 0 {
		reportCtx, cancel := context.WithTimeout(ctx, window)
		err := c.Report(reportCtx, r.Report)
		cancel()
		var status *rayprobe.StatusError
		switch {
		case err == nil:
			out.ReportStatus = 204
		case errors.As(err, &status):
			out.ReportStatus, out.ReportRetryAfter = status.Status, status.RetryAfter
		default:
			out.ReportError = err.Error()
		}
	}
	return out
}

// answered: some step got an HTTP answer, so the port was open
func (o *probeResult) answered() bool {
	return o.Link != nil || o.Down != nil && (o.Down.OK || o.Down.Status != 0) || o.Up != nil && (o.Up.OK || o.Up.Status != 0) ||
		o.Echo != nil && (o.Echo.OK || o.Echo.Status != 0) || o.ReportStatus != 0 || o.UDPMTU > 0
}

func parseProbeRequest(requestJSON string) (*probeRequest, []byte, error) {
	var r probeRequest
	if err := json.Unmarshal([]byte(requestJSON), &r); err != nil {
		return nil, nil, err
	}
	key, err := hex.DecodeString(r.SPA.DeviceKey)
	if err != nil || len(key) != 32 {
		return nil, nil, errors.New("raycontrol probe: bad device key")
	}
	return &r, key, nil
}

// RaycontrolProbe measures the channel to a node directly (probe-v1.md: the subscription endpoint after SPA v2,
// over UDP and, when nothing answers, over the TCP carrier). Request and result as probeRequest and probeResult
// in JSON. Its sockets are protected from the app's own VPN.
func RaycontrolProbe(requestJSON string) (result string, err error) {
	defer device.DeferPanicToError("raycontrol.Probe", func(err_ error) { err = err_ })
	r, key, err := parseProbeRequest(requestJSON)
	if err != nil {
		return "", err
	}
	dialer := &net.Dialer{Timeout: 10 * time.Second, Control: protectControl()}
	serverName := ""
	if net.ParseIP(r.Host) == nil {
		serverName = r.Host
	}
	e := &rayprobe.Endpoint{
		Address: net.JoinHostPort(r.Host, strconv.Itoa(r.Port)), Pin: r.Pin, ServerName: serverName,
		DeviceID: r.DeviceID, DeviceKey: key, Dial: dialer.DialContext,
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()
	var out *probeResult
	for _, carrier := range knock.Carriers {
		if err = knock.SendSPAv2(ctx, dialer, r.Host, r.SPA, carrier, time.Now()); err != nil {
			return "", err
		}
		if out = r.run(ctx, e); out.answered() {
			out.SPACarrier = carrier.String()
			break
		}
	}
	raw, err := json.Marshal(out)
	return string(raw), err
}

// RaycontrolProbeTunnel measures the channel through the default outbound of i, a test instance of one of the
// node's profiles, at the probe name (probe-v1.md §1.1): the tunnel as a whole, its exit included.
func RaycontrolProbeTunnel(i *BoxInstance, requestJSON string) (result string, err error) {
	defer device.DeferPanicToError("raycontrol.ProbeTunnel", func(err_ error) { err = err_ })
	if i == nil || i.Box == nil {
		return "", errors.New("no instance")
	}
	r, key, err := parseProbeRequest(requestJSON)
	if err != nil {
		return "", err
	}
	if r.Tunnel == "" {
		return "", errors.New("raycontrol probe: no probe name for the tunnel")
	}
	// link is the server's view of the device's address, and the summary is about the network it comes from: through
	// a tunnel both would be the node's own (409)
	r.Link = false
	r.Report = nil
	e := &rayprobe.Endpoint{
		Address: net.JoinHostPort(r.Tunnel, "443"), Pin: r.Pin, ServerName: r.Tunnel,
		DeviceID: r.DeviceID, DeviceKey: key,
		Dial: func(ctx context.Context, network, address string) (net.Conn, error) {
			return boxapi.DialContext(ctx, i.Box, nil, network, address)
		},
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()
	raw, err := json.Marshal(r.run(ctx, e))
	return string(raw), err
}

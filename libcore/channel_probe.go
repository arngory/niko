package libcore

import (
	"context"
	"encoding/json"
	"errors"
	"net"
	"net/http"
	"time"

	"libcore/device"
	"libcore/probe"

	"github.com/sagernet/sing-box/boxapi"
)

type channelProbeResult struct {
	TCP *probe.TCPResult `json:"tcp,omitempty"`
	UDP *probe.UDPResult `json:"udp,omitempty"`
}

// ChannelProbe measures the channel through the default outbound of i, a test instance of one profile: a TCP
// download and UDP round trips, one after the other (a network may pass one and not the other). options is
// probe.Options as JSON; the result is {"tcp": probe.TCPResult, "udp": probe.UDPResult} as JSON.
func ChannelProbe(i *BoxInstance, options string) (result string, err error) {
	defer device.DeferPanicToError("box.ChannelProbe", func(err_ error) { err = err_ })
	if i == nil || i.Box == nil {
		return "", errors.New("no instance")
	}
	var o probe.Options
	if err = json.Unmarshal([]byte(options), &o); err != nil {
		return "", err
	}
	var out channelProbeResult
	ctx := context.Background()
	if o.TCPURL != "" {
		// the window bounds every step: no transport timeouts of its own
		transport := &http.Transport{
			DialContext: func(ctx context.Context, network, addr string) (net.Conn, error) {
				return boxapi.DialContext(ctx, i.Box, nil, network, addr)
			},
			ForceAttemptHTTP2: true,
		}
		// a redirect would take the measurement to another host, outside the probe's
		out.TCP = probe.TCP(ctx, &http.Client{Transport: transport, CheckRedirect: func(*http.Request, []*http.Request) error {
			return http.ErrUseLastResponse
		}}, o)
		transport.CloseIdleConnections()
	}
	if o.UDPServer != "" {
		dialCtx, cancel := context.WithTimeout(ctx, 10*time.Second)
		conn, err := boxapi.DialContext(dialCtx, i.Box, nil, "udp", o.UDPServer)
		cancel()
		if err != nil {
			// e.g. a protocol without UDP
			out.UDP = &probe.UDPResult{Error: err.Error()}
		} else {
			out.UDP = probe.UDP(ctx, conn, o)
			conn.Close()
		}
	}
	b, err := json.Marshal(out)
	return string(b), err
}

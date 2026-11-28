// Package naiveprobe runs naive-udp-v1 §9.4 HTTP/3 datagram echo probes through a running
// sing-box naive outbound without importing the gomobile root libcore package.
package naiveprobe

import (
	"context"
	"net"
	"time"

	"libcore/rayprobe"

	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
)

// DatagramOutbound is the subset of sing-box protocol/naive.Outbound used to manage
// HTTP/3 UDP datagram delivery and run naive-udp-v1 §9.4 probes.
type DatagramOutbound interface {
	SetDatagramAllowed(allowed bool)
	DatagramNetworkChanged()
	DatagramState() string
	DatagramProbe(ctx context.Context) (net.PacketConn, error)
}

// DialFunc builds a rayprobe Endpoint.Dial function whose TCP dials traverse tcp and whose
// UDP dials open a dedicated HTTP/3 datagram probe tunnel via d.DatagramProbe.
func DialFunc(d DatagramOutbound, tcp N.Dialer) func(ctx context.Context, network, address string) (net.Conn, error) {
	return func(ctx context.Context, network, address string) (net.Conn, error) {
		destination := M.ParseSocksaddr(address)
		if !destination.IsValid() || destination.Port == 0 {
			return nil, E.New("naiveprobe: bad address ", address)
		}
		switch network {
		case "udp", "udp4", "udp6":
			packetConn, err := d.DatagramProbe(ctx)
			if err != nil {
				return nil, err
			}
			return bufio.NewBindPacketConn(packetConn, destination), nil
		case "tcp", "tcp4", "tcp6":
			return tcp.DialContext(ctx, N.NetworkTCP, destination)
		default:
			return nil, E.New("naiveprobe: unsupported network ", network)
		}
	}
}

// Echo executes a probe-v1 UDP echo session over HTTP/3 datagrams of the given naive outbound.
func Echo(
	ctx context.Context,
	d DatagramOutbound,
	tcp N.Dialer,
	ep rayprobe.Endpoint,
	count int,
	size int,
	interval time.Duration,
) *rayprobe.EchoResult {
	ep.Dial = DialFunc(d, tcp)
	client := rayprobe.NewClient(&ep)
	defer client.Close()
	return client.Echo(ctx, count, size, interval)
}

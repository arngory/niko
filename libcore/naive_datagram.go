package libcore

import (
	"context"
	"encoding/json"
	"errors"
	"net"
	"time"

	"libcore/device"
	"libcore/rayprobe"

	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
)

// naiveDatagram is what the core's naive outbound offers for UDP as HTTP/3 datagrams (raycontrol naive-udp-v1,
// the udp_datagram option of the core fork).
type naiveDatagram interface {
	SetDatagramAllowed(allowed bool)
	DatagramNetworkChanged()
	DatagramState() string
	DatagramProbe(ctx context.Context) (net.PacketConn, error)
}

func naiveDatagramOutbound(i *BoxInstance, tag string) (naiveDatagram, error) {
	d, _, err := naiveDatagramDialer(i, tag)
	return d, err
}

// naiveDatagramDialer is the naive outbound tag, as what it offers for datagrams and as a dialer of its own.
func naiveDatagramDialer(i *BoxInstance, tag string) (naiveDatagram, N.Dialer, error) {
	if i == nil || i.Box == nil {
		return nil, nil, errors.New("no instance")
	}
	outbound, ok := i.Outbound().Outbound(tag)
	if !ok {
		return nil, nil, E.New("no outbound ", tag)
	}
	d, ok := outbound.(naiveDatagram)
	if !ok {
		return nil, nil, E.New("outbound ", tag, " carries no UDP datagrams")
	}
	return d, outbound, nil
}

// NaiveDatagramState tells how new UDP sockets of the naive outbound tag go: "off", "unsupported", "refused",
// "allowed" or "probing" (the core's DatagramState); empty where the outbound is none such.
func NaiveDatagramState(i *BoxInstance, tag string) string {
	d, err := naiveDatagramOutbound(i, tag)
	if err != nil {
		return ""
	}
	return d.DatagramState()
}

// SetNaiveDatagramAllowed lets new UDP sockets of the naive outbound tag go as HTTP/3 datagrams on this network,
// or sends them over UDP over TCP: the app allows it once its probe has passed (naive-udp-v1 §9.4), or from a result
// it remembers for the network. It holds until ResetNaiveDatagram.
func SetNaiveDatagramAllowed(i *BoxInstance, tag string, allowed bool) error {
	d, err := naiveDatagramOutbound(i, tag)
	if err != nil {
		return err
	}
	d.SetDatagramAllowed(allowed)
	return nil
}

// ResetNaiveDatagram tells the naive outbound tag of a new network, as the app reckons networks: what the last one
// decided is forgotten, the app's allowance too, and new UDP sockets go over UDP over TCP until the app allows
// datagrams again.
func ResetNaiveDatagram(i *BoxInstance, tag string) error {
	d, err := naiveDatagramOutbound(i, tag)
	if err != nil {
		return err
	}
	d.DatagramNetworkChanged()
	return nil
}

// RaycontrolProbeDatagram measures the channel as RaycontrolProbeTunnel does, at the probe name, but sends the echo
// as HTTP/3 datagrams of the naive outbound tag (naive-udp-v1 §9.4): the path UDP takes once the app allows it. The
// HTTP part of the probe goes through the same outbound over TCP, not the instance's default (a group's selector may
// point at another profile, or another node, whose session the echo would not find).
func RaycontrolProbeDatagram(i *BoxInstance, tag string, requestJSON string) (result string, err error) {
	defer device.DeferPanicToError("raycontrol.ProbeDatagram", func(err_ error) { err = err_ })
	d, outbound, err := naiveDatagramDialer(i, tag)
	if err != nil {
		return "", err
	}
	r, key, err := parseProbeRequest(requestJSON)
	if err != nil {
		return "", err
	}
	if r.Tunnel == "" {
		return "", errors.New("raycontrol probe: no probe name for the tunnel")
	}
	// as through any tunnel: the server would see the node's own address
	r.Link = false
	r.Report = nil
	e := &rayprobe.Endpoint{
		Address: net.JoinHostPort(r.Tunnel, "443"), Pin: r.Pin, ServerName: r.Tunnel,
		DeviceID: r.DeviceID, DeviceKey: key,
		Dial: datagramProbeDial(d, outbound),
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()
	raw, err := json.Marshal(r.run(ctx, e))
	return string(raw), err
}

// datagramProbeDial dials UDP as one datagram request bound to the address, and TCP through the outbound itself.
func datagramProbeDial(d naiveDatagram, tcp N.Dialer) func(ctx context.Context, network, address string) (net.Conn, error) {
	return func(ctx context.Context, network, address string) (net.Conn, error) {
		destination := M.ParseSocksaddr(address)
		if !destination.IsValid() || destination.Port == 0 {
			return nil, E.New("probe: bad address ", address)
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
			return nil, E.New("probe: network ", network)
		}
	}
}

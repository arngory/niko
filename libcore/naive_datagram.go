package libcore

import (
	"context"
	"encoding/json"
	"errors"
	"net"
	"time"

	"libcore/device"
	"libcore/rayprobe"

	"github.com/sagernet/sing-box/boxapi"
	"github.com/sagernet/sing/common/bufio"
	E "github.com/sagernet/sing/common/exceptions"
	M "github.com/sagernet/sing/common/metadata"
)

// naiveDatagram is what the core's naive outbound offers for UDP as HTTP/3 datagrams (raycontrol naive-udp-v1,
// the udp_datagram option of the core fork).
type naiveDatagram interface {
	SetDatagramAllowed(allowed bool)
	DatagramState() string
	DatagramProbe(ctx context.Context) (net.PacketConn, error)
}

func naiveDatagramOutbound(i *BoxInstance, tag string) (naiveDatagram, error) {
	if i == nil || i.Box == nil {
		return nil, errors.New("no instance")
	}
	outbound, ok := i.Outbound().Outbound(tag)
	if !ok {
		return nil, E.New("no outbound ", tag)
	}
	d, ok := outbound.(naiveDatagram)
	if !ok {
		return nil, E.New("outbound ", tag, " carries no UDP datagrams")
	}
	return d, nil
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
// or sends them over UDP over TCP: the app allows it once its probe has passed (naive-udp-v1 §9.4). A network
// change forgets it in the core.
func SetNaiveDatagramAllowed(i *BoxInstance, tag string, allowed bool) error {
	d, err := naiveDatagramOutbound(i, tag)
	if err != nil {
		return err
	}
	d.SetDatagramAllowed(allowed)
	return nil
}

// RaycontrolProbeDatagram measures the channel as RaycontrolProbeTunnel does, through the instance at the probe
// name, but sends the echo as HTTP/3 datagrams of the naive outbound tag (naive-udp-v1 §9.4): the path UDP takes
// once the app allows it. The HTTP part of the probe goes through the instance as before.
func RaycontrolProbeDatagram(i *BoxInstance, tag string, requestJSON string) (result string, err error) {
	defer device.DeferPanicToError("raycontrol.ProbeDatagram", func(err_ error) { err = err_ })
	d, err := naiveDatagramOutbound(i, tag)
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
		Dial: datagramProbeDial(i, d),
	}
	ctx, cancel := context.WithTimeout(context.Background(), 2*time.Minute)
	defer cancel()
	raw, err := json.Marshal(r.run(ctx, e))
	return string(raw), err
}

// datagramProbeDial dials UDP as one datagram request bound to the address, and TCP through the instance.
func datagramProbeDial(i *BoxInstance, d naiveDatagram) func(ctx context.Context, network, address string) (net.Conn, error) {
	return func(ctx context.Context, network, address string) (net.Conn, error) {
		switch network {
		case "udp", "udp4", "udp6":
			destination := M.ParseSocksaddr(address)
			if !destination.IsValid() || destination.Port == 0 {
				return nil, E.New("probe: bad address ", address)
			}
			packetConn, err := d.DatagramProbe(ctx)
			if err != nil {
				return nil, err
			}
			return bufio.NewBindPacketConn(packetConn, destination), nil
		default:
			return boxapi.DialContext(ctx, i.Box, nil, network, address)
		}
	}
}

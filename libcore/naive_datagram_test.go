package libcore

import (
	"context"
	"errors"
	"net"
	"testing"
	"time"

	M "github.com/sagernet/sing/common/metadata"
)

// fakeNaiveDatagram hands out a plain UDP socket for the probe's datagram request.
type fakeNaiveDatagram struct {
	allowed bool
	opened  int
	err     error
}

func (f *fakeNaiveDatagram) SetDatagramAllowed(allowed bool) { f.allowed = allowed }
func (f *fakeNaiveDatagram) DatagramState() string           { return "probing" }

func (f *fakeNaiveDatagram) DatagramProbe(ctx context.Context) (net.PacketConn, error) {
	if f.err != nil {
		return nil, f.err
	}
	f.opened++
	conn, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		return nil, err
	}
	return socksaddrPacketConn{conn}, nil
}

// socksaddrPacketConn takes the destination as the core's datagram socket does: a Socksaddr (a name too).
type socksaddrPacketConn struct{ net.PacketConn }

func (c socksaddrPacketConn) WriteTo(p []byte, addr net.Addr) (int, error) {
	return c.PacketConn.WriteTo(p, M.SocksaddrFromNet(addr).UDPAddr())
}

func TestNaiveDatagramProbeDial(t *testing.T) {
	echo, err := net.ListenPacket("udp", "127.0.0.1:0")
	if err != nil {
		t.Fatal(err)
	}
	defer echo.Close()
	go func() {
		b := make([]byte, 2048)
		for {
			n, from, err := echo.ReadFrom(b)
			if err != nil {
				return
			}
			echo.WriteTo(b[:n], from)
		}
	}()

	d := &fakeNaiveDatagram{}
	dial := datagramProbeDial(nil, d)
	conn, err := dial(context.Background(), "udp", echo.LocalAddr().String())
	if err != nil {
		t.Fatal(err)
	}
	defer conn.Close()
	if d.opened != 1 {
		t.Fatalf("the echo did not go through the outbound's datagram request: %d opened", d.opened)
	}
	conn.SetDeadline(time.Now().Add(5 * time.Second))
	if _, err = conn.Write([]byte("ping")); err != nil {
		t.Fatal(err)
	}
	b := make([]byte, 16)
	n, err := conn.Read(b)
	if err != nil || string(b[:n]) != "ping" {
		t.Fatalf("echo %q %v", b[:n], err)
	}

	if _, err = dial(context.Background(), "udp", "not an address"); err == nil {
		t.Error("a bad address dialled")
	}
	d.err = errors.New("unsupported")
	if _, err = dial(context.Background(), "udp", echo.LocalAddr().String()); err == nil {
		t.Error("a refused datagram request dialled")
	}
}

func TestNaiveDatagramNoInstance(t *testing.T) {
	if state := NaiveDatagramState(nil, "proxy"); state != "" {
		t.Errorf("state without an instance: %q", state)
	}
	if err := SetNaiveDatagramAllowed(nil, "proxy", true); err == nil {
		t.Error("allowed without an instance")
	}
	if _, err := RaycontrolProbeDatagram(nil, "proxy", "{}"); err == nil {
		t.Error("probed without an instance")
	}
}

package libcore

import (
	"errors"
	"fmt"
	"io"
	"log"
	"net"
	"os"
	"syscall"
	"time"
)

// The protect socket: the plugins and the DNS tunnels' own sockets (masterdns' sockprotect) hand a socket to the app
// before they connect it, so that VpnService.protect keeps it out of the VPN. The exchange on the Unix socket: the
// client sends one message carrying the descriptor (SCM_RIGHTS, a byte of payload), the server answers one byte, 1
// protected and 0 not.

// a whole exchange: what does not finish in it is dropped
const protectTimeout = 5 * time.Second

// serveProtect listens on the Unix socket at path (made anew) and calls protect with each descriptor sent to it.
// Close stops it. A socket that cannot be made is logged and nothing listens.
func serveProtect(path string, protect func(fd int) error) io.Closer {
	_ = os.Remove(path)
	l, err := net.ListenUnix("unix", &net.UnixAddr{Name: path, Net: "unix"})
	if err != nil {
		log.Println("protect socket:", err)
		return io.NopCloser(nil)
	}
	// the plugins' processes reach it (inside the app's own directory, which no other app can open)
	_ = os.Chmod(path, 0o777)
	go func() {
		for {
			c, err := l.AcceptUnix()
			if err != nil {
				if !errors.Is(err, net.ErrClosed) {
					log.Println("protect socket:", err)
				}
				return
			}
			go protectOne(c, protect)
		}
	}()
	return l
}

func protectOne(c *net.UnixConn, protect func(fd int) error) {
	defer c.Close()
	_ = c.SetDeadline(time.Now().Add(protectTimeout))
	fd, err := receiveFd(c)
	if err != nil {
		log.Println("protect socket:", err)
		return
	}
	defer syscall.Close(fd)
	answer := byte(1)
	if err = protect(fd); err != nil {
		log.Println("protect socket: protect:", err)
		answer = 0
	}
	_, _ = c.Write([]byte{answer})
}

// receiveFd reads the one message of an exchange and the one descriptor it carries; any other descriptors it
// carries are closed.
func receiveFd(c *net.UnixConn) (int, error) {
	oob := make([]byte, syscall.CmsgSpace(4*4))
	_, oobn, _, _, err := c.ReadMsgUnix(make([]byte, 1), oob)
	if err != nil {
		return -1, err
	}
	messages, err := syscall.ParseSocketControlMessage(oob[:oobn])
	if err != nil {
		return -1, err
	}
	var fds []int
	for i := range messages {
		got, err := syscall.ParseUnixRights(&messages[i])
		if err == nil {
			fds = append(fds, got...)
		}
	}
	if len(fds) != 1 {
		for _, fd := range fds {
			syscall.Close(fd)
		}
		return -1, fmt.Errorf("want one descriptor, got %d", len(fds))
	}
	return fds[0], nil
}

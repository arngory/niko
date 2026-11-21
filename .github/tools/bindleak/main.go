// bindleak: an HTTP GET from a socket bound to an interface by name (SO_BINDTODEVICE), as any app can bind one to
// the VPN's tun0 while the VPN leaves the app out (keqdroid's finding, e2e.sh: an app outside the VPN). With "-" for
// the interface, a plain socket, for comparison. Prints the answer's first 300 bytes, or why there is none.
//
//	bindleak IFACE|- HOST:PORT PATH
package main

import (
	"fmt"
	"io"
	"net"
	"os"
	"syscall"
	"time"
)

func main() {
	if len(os.Args) != 4 {
		fmt.Fprintln(os.Stderr, "usage: bindleak IFACE|- HOST:PORT PATH")
		os.Exit(2)
	}
	iface, address, path := os.Args[1], os.Args[2], os.Args[3]
	dialer := net.Dialer{Timeout: 10 * time.Second}
	if iface != "-" {
		dialer.Control = func(_, _ string, c syscall.RawConn) error {
			var bindErr error
			if err := c.Control(func(fd uintptr) {
				bindErr = syscall.SetsockoptString(int(fd), syscall.SOL_SOCKET, syscall.SO_BINDTODEVICE, iface)
			}); err != nil {
				return err
			}
			return bindErr
		}
	}
	conn, err := dialer.Dial("tcp4", address)
	if err != nil {
		fmt.Println("dial:", err)
		os.Exit(1)
	}
	defer conn.Close()
	conn.SetDeadline(time.Now().Add(20 * time.Second))
	if _, err = fmt.Fprintf(conn, "GET %s HTTP/1.0\r\n\r\n", path); err != nil {
		fmt.Println("write:", err)
		os.Exit(1)
	}
	answer, err := io.ReadAll(io.LimitReader(conn, 4096))
	if len(answer) > 300 {
		answer = answer[:300]
	}
	fmt.Print(string(answer))
	if err != nil && len(answer) == 0 {
		fmt.Println("read:", err)
		os.Exit(1)
	}
}

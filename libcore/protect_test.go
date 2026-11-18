package libcore

import (
	"errors"
	"io"
	"net"
	"os"
	"path/filepath"
	"sync/atomic"
	"syscall"
	"testing"
	"time"
)

// the client's side of the exchange as masterdns' sockprotect and the plugins speak it
func sendForProtect(t *testing.T, path string, fds ...int) (byte, error) {
	t.Helper()
	c, err := net.DialTimeout("unix", path, time.Second)
	if err != nil {
		return 0, err
	}
	defer c.Close()
	c.SetDeadline(time.Now().Add(3 * time.Second))
	if _, _, err = c.(*net.UnixConn).WriteMsgUnix([]byte{1}, syscall.UnixRights(fds...), nil); err != nil {
		return 0, err
	}
	var answer [1]byte
	_, err = io.ReadFull(c, answer[:])
	return answer[0], err
}

func TestServeProtect(t *testing.T) {
	path := filepath.Join(t.TempDir(), "protect_path")
	got := make(chan int, 4)
	var fail atomic.Bool
	closer := serveProtect(path, func(fd int) error {
		var st syscall.Stat_t
		if err := syscall.Fstat(fd, &st); err != nil {
			t.Errorf("the descriptor handed over is not open: %v", err)
		}
		got <- fd
		if fail.Load() {
			return errors.New("refused")
		}
		return nil
	})
	defer closer.Close()
	if info, err := os.Stat(path); err != nil || info.Mode()&os.ModeSocket == 0 {
		t.Fatalf("no socket: %v", err)
	}
	socket, _ := syscall.Socket(syscall.AF_INET, syscall.SOCK_DGRAM, 0)
	defer syscall.Close(socket)
	if answer, err := sendForProtect(t, path, socket); err != nil || answer != 1 {
		t.Fatalf("protected: %d %v", answer, err)
	}
	<-got
	// protect fails: 0
	fail.Store(true)
	if answer, err := sendForProtect(t, path, socket); err != nil || answer != 0 {
		t.Fatalf("refused: %d %v", answer, err)
	}
	<-got
	// two descriptors at once: not one, no answer and no protect
	if answer, err := sendForProtect(t, path, socket, socket); err == nil {
		t.Fatalf("two descriptors answered %d", answer)
	}
	select {
	case fd := <-got:
		t.Fatalf("protect called with %d for two descriptors", fd)
	case <-time.After(100 * time.Millisecond):
	}
	// closed: no one listens
	closer.Close()
	if _, err := sendForProtect(t, path, socket); err == nil {
		t.Fatal("answered after Close")
	}
}

// a socket that cannot be made: nothing listens, nothing crashes
func TestServeProtectNoSocket(t *testing.T) {
	closer := serveProtect(filepath.Join(t.TempDir(), "missing", "dir", "protect_path"), func(int) error { return nil })
	if err := closer.Close(); err != nil {
		t.Fatal(err)
	}
}

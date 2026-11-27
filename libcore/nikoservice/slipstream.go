package nikoservice

import (
	"context"
	"fmt"
	"net"
	"os"
	"os/exec"
	"path/filepath"
	"runtime"
	"sync"
	"time"

	"libcore/nikoconf"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// slipstreamManager manages bundled external `slipstream-client` child processes for Variant A
// (Stage 4: arngory/slipstream picoquic binary placed beside niko-daemon in the protected install dir).
type slipstreamManager struct {
	mu       sync.Mutex
	procs    []*exec.Cmd
	findPath func() (string, error)
}

func defaultSlipstreamBinaryPath() (string, error) {
	exe, err := os.Executable()
	if err != nil {
		return "", err
	}
	name := "slipstream-client"
	if runtime.GOOS == "windows" {
		name = "slipstream-client.exe"
	}
	candidate := filepath.Join(filepath.Dir(exe), name)
	info, err := os.Stat(candidate)
	if err != nil || info.IsDir() {
		return "", fmt.Errorf("%s not found beside niko-daemon (%s)", name, candidate)
	}
	return candidate, nil
}

func (m *slipstreamManager) StopAll() {
	m.mu.Lock()
	procs := m.procs
	m.procs = nil
	m.mu.Unlock()
	for _, cmd := range procs {
		if cmd.Process != nil {
			_ = cmd.Process.Kill()
			_, _ = cmd.Process.Wait()
		}
	}
}

func (m *slipstreamManager) Start(ctx context.Context, launches []nikoconf.SlipstreamLaunch) error {
	m.StopAll()
	if len(launches) == 0 {
		return nil
	}
	finder := m.findPath
	if finder == nil {
		finder = defaultSlipstreamBinaryPath
	}
	binPath, err := finder()
	if err != nil {
		return status.Errorf(codes.FailedPrecondition, "slipstream binary unavailable: %v", err)
	}

	var started []*exec.Cmd
	cleanup := func() {
		for _, c := range started {
			if c.Process != nil {
				_ = c.Process.Kill()
				_, _ = c.Process.Wait()
			}
		}
	}

	for _, l := range launches {
		cmd := exec.Command(binPath, l.Args...)
		cmd.Env = append([]string{}, os.Environ()...)
		for k, v := range l.Env {
			cmd.Env = append(cmd.Env, k+"="+v)
		}
		if err := cmd.Start(); err != nil {
			cleanup()
			return status.Errorf(codes.Internal, "start slipstream-client: %v", err)
		}
		started = append(started, cmd)
		// Wait briefly for the local TCP listener (127.0.0.1:<LocalPort>) to come up.
		if l.LocalPort > 0 {
			waitTCPReady(ctx, fmt.Sprintf("127.0.0.1:%d", l.LocalPort), 2*time.Second)
		}
	}

	m.mu.Lock()
	m.procs = started
	m.mu.Unlock()
	return nil
}

func waitTCPReady(ctx context.Context, addr string, maxWait time.Duration) {
	deadline := time.Now().Add(maxWait)
	for time.Now().Before(deadline) {
		if ctx.Err() != nil {
			return
		}
		conn, err := net.DialTimeout("tcp", addr, 100*time.Millisecond)
		if err == nil {
			_ = conn.Close()
			return
		}
		time.Sleep(50 * time.Millisecond)
	}
}

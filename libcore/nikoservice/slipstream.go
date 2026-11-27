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

// slipstreamProc tracks a single running slipstream-client child process with background reaping (H4).
type slipstreamProc struct {
	cmd  *exec.Cmd
	done chan struct{}
}

func (p *slipstreamProc) stop() {
	if p == nil || p.cmd == nil || p.cmd.Process == nil {
		return
	}
	_ = p.cmd.Process.Kill()
	select {
	case <-p.done:
	case <-time.After(2 * time.Second):
	}
}

// slipstreamBatch holds newly spawned processes before they replace the currently active batch.
type slipstreamBatch struct {
	procs []*slipstreamProc
}

func (b *slipstreamBatch) abort() {
	if b == nil {
		return
	}
	for _, p := range b.procs {
		p.stop()
	}
	b.procs = nil
}

// slipstreamManager manages bundled external `slipstream-client` child processes for Variant A
// (arngory/slipstream picoquic binary placed beside niko-daemon in the protected install dir).
type slipstreamManager struct {
	mu       sync.Mutex
	procs    []*slipstreamProc
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
	for _, p := range procs {
		p.stop()
	}
}

// Prepare starts all required slipstream-client helper processes without killing any currently active
// batch. Call Commit after core startup succeeds, or Abort if startup fails so an already-running core
// never loses its live tunnel on a failed reconnect (H4).
func (m *slipstreamManager) Prepare(ctx context.Context, launches []nikoconf.SlipstreamLaunch) (*slipstreamBatch, error) {
	if len(launches) == 0 {
		return &slipstreamBatch{}, nil
	}
	finder := m.findPath
	if finder == nil {
		finder = defaultSlipstreamBinaryPath
	}
	binPath, err := finder()
	if err != nil {
		return nil, status.Errorf(codes.FailedPrecondition, "slipstream binary unavailable: %v", err)
	}

	batch := &slipstreamBatch{}
	for _, l := range launches {
		cmd := exec.Command(binPath, l.Args...)
		setSlipstreamSysProcAttr(cmd)
		cmd.Env = append([]string{}, os.Environ()...)
		for k, v := range l.Env {
			cmd.Env = append(cmd.Env, k+"="+v)
		}
		if err := cmd.Start(); err != nil {
			batch.abort()
			return nil, status.Errorf(codes.Internal, "start slipstream-client: %v", err)
		}
		proc := &slipstreamProc{cmd: cmd, done: make(chan struct{})}
		go func() {
			_ = cmd.Wait()
			close(proc.done)
		}()
		batch.procs = append(batch.procs, proc)
		if l.LocalPort > 0 {
			if err := waitTCPReady(ctx, fmt.Sprintf("127.0.0.1:%d", l.LocalPort), proc.done, 3*time.Second); err != nil {
				batch.abort()
				return nil, status.Errorf(codes.Internal, "slipstream-client failed to listen on 127.0.0.1:%d: %v", l.LocalPort, err)
			}
		}
	}
	return batch, nil
}

// Commit swaps the prepared batch in as the active set and stops any previous helpers.
func (m *slipstreamManager) Commit(batch *slipstreamBatch) {
	var next []*slipstreamProc
	if batch != nil {
		next = batch.procs
		batch.procs = nil
	}
	m.mu.Lock()
	old := m.procs
	m.procs = next
	m.mu.Unlock()
	for _, p := range old {
		p.stop()
	}
}

// Abort tears down an uncommitted batch without touching currently active helpers.
func (m *slipstreamManager) Abort(batch *slipstreamBatch) {
	batch.abort()
}

// Start prepares and immediately commits launches (convenience wrapper).
func (m *slipstreamManager) Start(ctx context.Context, launches []nikoconf.SlipstreamLaunch) error {
	batch, err := m.Prepare(ctx, launches)
	if err != nil {
		return err
	}
	m.Commit(batch)
	return nil
}

func waitTCPReady(ctx context.Context, addr string, exited <-chan struct{}, maxWait time.Duration) error {
	deadline := time.Now().Add(maxWait)
	for time.Now().Before(deadline) {
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-exited:
			return fmt.Errorf("process exited before binding %s", addr)
		default:
		}
		conn, err := net.DialTimeout("tcp", addr, 100*time.Millisecond)
		if err == nil {
			_ = conn.Close()
			return nil
		}
		select {
		case <-ctx.Done():
			return ctx.Err()
		case <-exited:
			return fmt.Errorf("process exited before binding %s", addr)
		case <-time.After(50 * time.Millisecond):
		}
	}
	return fmt.Errorf("timed out waiting for %s", addr)
}

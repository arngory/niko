//go:build linux

package nikoservice

import (
	"os/exec"
	"syscall"
)

func setSlipstreamSysProcAttr(cmd *exec.Cmd) {
	cmd.SysProcAttr = &syscall.SysProcAttr{
		Pdeathsig: syscall.SIGTERM,
	}
}

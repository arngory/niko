//go:build !linux

package nikoservice

import "os/exec"

func setSlipstreamSysProcAttr(_ *exec.Cmd) {}

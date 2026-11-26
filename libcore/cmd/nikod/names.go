package main

import (
	"libcore/nikoservice"

	"github.com/sagernet/sing-box/experimental/boxdd"
)

// Build-time overrides passed via -ldflags -X:
//   - manifestSHA256: hex SHA-256 of resources\files.sha256 for safe Program Files installation;
//   - development: "true" enables --listen 127.0.0.1:<port> (never set in release builds).
var (
	manifestSHA256 string
	development    string
)

var nikoNames = boxdd.Names{
	Service:            "niko-daemon",
	DisplayName:        "Niko",
	Description:        "Niko's privileged service",
	Product:            "Niko",
	UpdateProductName:  "Niko",
	PipePath:           `\\.\pipe\ProtectedPrefix\Administrators\niko`,
	WorkerPipePrefix:   `\\.\pipe\niko-worker.`,
	WindowsWorkingDir:  boxdd.ProgramDataDir("Niko"),
	LinuxWorkingDir:    "/var/lib/niko-daemon",
	ApplicationExe:     "niko.exe",
	DaemonExe:          "niko-daemon.exe",
	PolkitActionPrefix: "io.niko.daemon",
}

func daemonOptions() boxdd.Options {
	return boxdd.Options{
		Services:              []boxdd.Service{nikoservice.Service()},
		Names:                 nikoNames,
		WorkerTransport:       boxdd.WorkerTransportUnix,
		InstallRoot:           boxdd.InstallProgramFiles,
		InstallManifestSHA256: manifestSHA256,
		DevelopmentListen:     development == "true",
	}
}

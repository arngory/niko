package main

import (
	"os"
	"os/exec"
	"strings"
	"testing"

	"github.com/sagernet/sing-box/experimental/boxdd"
)

func TestMain(m *testing.M) {
	if os.Getenv("NIKO_TEST_RUN_MAIN") == "1" {
		// Re-exec mode: strip test binary flags after "--" and run main() with the exact
		// build tags compiled into this test binary (no slow inner `go build`).
		for i, arg := range os.Args {
			if arg == "--" {
				os.Args = append([]string{os.Args[0]}, os.Args[i+1:]...)
				break
			}
		}
		main()
		return
	}
	os.Exit(m.Run())
}

func TestNikoNamesValid(t *testing.T) {
	if err := nikoNames.Validate(); err != nil {
		t.Fatalf("nikoNames.Validate(): %v", err)
	}
	opts := daemonOptions()
	if opts.DevelopmentListen {
		t.Fatal("DevelopmentListen must be false by default")
	}
	if opts.WorkerTransport != boxdd.WorkerTransportUnix {
		t.Fatalf("WorkerTransport = %d, want WorkerTransportUnix", opts.WorkerTransport)
	}
	if opts.InstallRoot != boxdd.InstallProgramFiles {
		t.Fatalf("InstallRoot = %d, want InstallProgramFiles", opts.InstallRoot)
	}
}

func TestNikodBinaryVersionAndNoDevListen(t *testing.T) {
	runMain := func(args ...string) ([]byte, error) {
		cmd := exec.Command(os.Args[0], append([]string{"--"}, args...)...)
		cmd.Env = append(os.Environ(), "NIKO_TEST_RUN_MAIN=1")
		return cmd.CombinedOutput()
	}

	versionOut, err := runMain("version")
	if err != nil {
		t.Fatalf("niko-daemon version: %v\n%s", err, string(versionOut))
	}
	if !strings.Contains(string(versionOut), "niko-daemon version") {
		t.Fatalf("version output = %q, want 'niko-daemon version'", string(versionOut))
	}

	// Without -X main.development=true, --listen must be rejected.
	listenOut, listenErr := runMain("run", "--working-directory", t.TempDir(), "--listen", "127.0.0.1:2080")
	if listenErr == nil {
		t.Fatalf("expected --listen to fail without development build flag, output: %s", string(listenOut))
	}
	if !strings.Contains(string(listenOut), "no development mode") {
		t.Fatalf("unexpected --listen error output: %s", string(listenOut))
	}
}

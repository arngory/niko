//go:build linux && with_naive_outbound && with_purego

package main

import (
	"fmt"
	"os"
	"path/filepath"

	"github.com/sagernet/cronet-go"
	"github.com/sagernet/sing-box/log"
	"golang.org/x/sys/unix"
)

// refuseCronetLoad poisons cronet-go's sync.Once loader with an impossible path so a later
// NewNaiveClient call cannot fall back to findLibrary() / LD_LIBRARY_PATH after a security refusal.
func refuseCronetLoad(reason string) {
	log.Warn("refusing to load libcronet.so: ", reason)
	_ = cronet.LoadLibrary("/dev/null/niko-cronet-refused")
}

// preloadNativeLibraries loads libcronet.so from beside the nikod binary when running the daemon
// (`niko-daemon run ...`). It deliberately skips `service *` and other CLI subcommands so a
// root-elevated `service install` never dlopens an unverified libcronet.so from a user directory
// before verifying files.sha256.
func preloadNativeLibraries() {
	if len(os.Args) < 2 || os.Args[1] != "run" {
		return
	}
	exe, err := os.Executable()
	if err != nil {
		return
	}
	if resolved, err := filepath.EvalSymlinks(exe); err == nil {
		exe = resolved
	}
	libPath := filepath.Join(filepath.Dir(exe), "libcronet.so")
	fd, err := unix.Open(libPath, unix.O_RDONLY|unix.O_NOFOLLOW|unix.O_NONBLOCK|unix.O_CLOEXEC, 0)
	if err != nil {
		if err != unix.ENOENT || os.Geteuid() == 0 {
			refuseCronetLoad(fmt.Sprintf("open failed: %v", err))
		}
		return
	}
	defer unix.Close(fd)

	var st unix.Stat_t
	if err := unix.Fstat(fd, &st); err != nil {
		refuseCronetLoad(fmt.Sprintf("fstat failed: %v", err))
		return
	}
	if st.Mode&unix.S_IFMT != unix.S_IFREG {
		refuseCronetLoad("not a regular file")
		return
	}
	if os.Geteuid() == 0 {
		if st.Uid != 0 || st.Mode&0o022 != 0 {
			refuseCronetLoad("as root, must be root-owned and not group/world-writable")
			return
		}
	}
	// Load directly through /proc/self/fd/<fd> to prevent path TOCTOU after validation.
	if err := cronet.LoadLibrary(fmt.Sprintf("/proc/self/fd/%d", fd)); err != nil {
		log.Warn("load libcronet.so: ", err)
	}
}

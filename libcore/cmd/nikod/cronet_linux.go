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
		if err != unix.ENOENT {
			log.Warn("open libcronet.so: ", err)
		}
		return
	}
	defer unix.Close(fd)

	var st unix.Stat_t
	if err := unix.Fstat(fd, &st); err != nil {
		log.Warn("stat libcronet.so: ", err)
		return
	}
	if st.Mode&unix.S_IFMT != unix.S_IFREG {
		log.Warn("refusing to load non-regular libcronet.so")
		return
	}
	if os.Geteuid() == 0 {
		if st.Uid != 0 || st.Mode&0o022 != 0 {
			log.Warn("refusing to load libcronet.so as root: must be root-owned and not group/world-writable")
			return
		}
	}
	// Load directly through /proc/self/fd/<fd> to prevent path TOCTOU after validation.
	if err := cronet.LoadLibrary(fmt.Sprintf("/proc/self/fd/%d", fd)); err != nil {
		log.Warn("load libcronet.so: ", err)
	}
}

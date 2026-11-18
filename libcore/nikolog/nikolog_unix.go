//go:build unix

package nikolog

import (
	"os"

	"golang.org/x/sys/unix"
)

// an advisory lock between the app's processes (both append to the one file)
func lock(f *os.File) { _ = unix.Flock(int(f.Fd()), unix.LOCK_EX) }

func unlock(f *os.File) { _ = unix.Flock(int(f.Fd()), unix.LOCK_UN) }

func redirectStderr(f *os.File) { _ = unix.Dup3(int(f.Fd()), int(os.Stderr.Fd()), 0) }

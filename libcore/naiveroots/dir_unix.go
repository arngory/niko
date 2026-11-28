//go:build unix

package naiveroots

import (
	"fmt"
	"os"
	"syscall"
)

// verifyPrivateDir checks via Lstat that dir is a real directory (not a symlink),
// owned by the current effective UID, with no group or other permissions.
func verifyPrivateDir(dir string) error {
	fi, err := os.Lstat(dir)
	if err != nil {
		return err
	}
	if fi.Mode()&os.ModeSymlink != 0 || !fi.IsDir() {
		return fmt.Errorf("naive roots dir %q is not a real directory", dir)
	}
	if fi.Mode().Perm()&0o077 != 0 {
		return fmt.Errorf("naive roots dir %q has insecure permissions %04o", dir, fi.Mode().Perm())
	}
	st, ok := fi.Sys().(*syscall.Stat_t)
	if !ok {
		return fmt.Errorf("naive roots dir %q: missing stat info", dir)
	}
	if int(st.Uid) != os.Geteuid() {
		return fmt.Errorf("naive roots dir %q owned by uid %d, want %d", dir, st.Uid, os.Geteuid())
	}
	return nil
}

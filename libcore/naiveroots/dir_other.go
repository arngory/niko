//go:build !unix

package naiveroots

import (
	"fmt"
	"os"
)

func verifyPrivateDir(dir string) error {
	fi, err := os.Lstat(dir)
	if err != nil {
		return err
	}
	if fi.Mode()&os.ModeSymlink != 0 || !fi.IsDir() {
		return fmt.Errorf("naive roots dir %q is not a real directory", dir)
	}
	return nil
}

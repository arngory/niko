//go:build !unix

package nikolog

import "os"

// one process writes the log here: no lock between processes, stderr left as it is
func lock(*os.File) {}

func unlock(*os.File) {}

func redirectStderr(*os.File) {}

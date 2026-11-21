package nikostore

import (
	"errors"
	"os"
	"path/filepath"
	"runtime"
	"sort"
	"strconv"
	"strings"
	"time"
)

// The store's files: written whole to a temporary file beside the old one, synced, then renamed over it (as boxdd
// writes its own through atomicfile), so a crash leaves the old file or the new one, never half of either. Only the
// daemon reads them: the directories 0700, the files 0600 (on Windows the daemon's directory's DACL, which they
// inherit).

// crashBeforeRename, set by a test, is called with the temporary file once it is written and synced; true stops the
// write there, the temporary file left as a crash would leave it.
var crashBeforeRename func(temporary string) bool

var errCrashed = errors.New("stopped before the rename (a test's crash)")

const temporarySuffix = ".tmp"

// writeFile replaces path with data atomically.
func writeFile(path string, data []byte) error {
	dir := filepath.Dir(path)
	// CreateTemp makes it 0600
	file, err := os.CreateTemp(dir, "."+filepath.Base(path)+".*"+temporarySuffix)
	if err != nil {
		return err
	}
	temporary := file.Name()
	kept := false
	defer func() {
		if !kept {
			file.Close()
			os.Remove(temporary)
		}
	}()
	if _, err = file.Write(data); err != nil {
		return err
	}
	if err = file.Sync(); err != nil {
		return err
	}
	if err = file.Close(); err != nil {
		return err
	}
	if crashBeforeRename != nil && crashBeforeRename(temporary) {
		kept = true
		return errCrashed
	}
	if err = os.Rename(temporary, path); err != nil {
		return err
	}
	kept = true
	return syncDir(dir)
}

// syncDir makes the rename itself durable where the system lets a directory be synced.
func syncDir(dir string) error {
	if runtime.GOOS == "windows" {
		return nil
	}
	d, err := os.Open(dir)
	if err != nil {
		return err
	}
	defer d.Close()
	return d.Sync()
}

// removeTemporaries removes what writes a crash cut short left in dir.
func removeTemporaries(dir string) {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return
	}
	for _, entry := range entries {
		name := entry.Name()
		if !entry.IsDir() && strings.HasPrefix(name, ".") && strings.HasSuffix(name, temporarySuffix) {
			os.Remove(filepath.Join(dir, name))
		}
	}
}

// privateDir makes dir, its missing parents too, for the daemon alone; one that was there is narrowed to it.
func privateDir(dir string) error {
	if err := os.MkdirAll(dir, 0o700); err != nil {
		return err
	}
	if runtime.GOOS == "windows" {
		return nil
	}
	info, err := os.Stat(dir)
	if err != nil {
		return err
	}
	if info.Mode().Perm()&0o077 != 0 {
		return os.Chmod(dir, 0o700)
	}
	return nil
}

// privateFile narrows a file that is there to the daemon alone (one copied in by hand, say).
func privateFile(path string) error {
	if runtime.GOOS == "windows" {
		return nil
	}
	info, err := os.Stat(path)
	if err != nil {
		return err
	}
	if info.Mode().Perm()&0o077 != 0 {
		return os.Chmod(path, 0o600)
	}
	return nil
}

// backup copies the store's file as it is, of its schema, into backup/<schema>-<date>/ before a migration, and keeps
// the last keptBackups copies.
func backup(dir string, schema int, data []byte, now time.Time) error {
	root := filepath.Join(dir, backupDir)
	if err := privateDir(root); err != nil {
		return err
	}
	base := strconv.Itoa(schema) + "-" + now.UTC().Format("20060102-150405")
	name := base
	for i := 2; ; i++ {
		if _, err := os.Stat(filepath.Join(root, name)); errors.Is(err, os.ErrNotExist) {
			break
		}
		name = base + "." + strconv.Itoa(i)
	}
	target := filepath.Join(root, name)
	if err := os.Mkdir(target, 0o700); err != nil {
		return err
	}
	if err := writeFile(filepath.Join(target, storeFile), data); err != nil {
		return err
	}
	return pruneBackups(root)
}

// pruneBackups removes all but the newest keptBackups copies, by the date in their names.
func pruneBackups(root string) error {
	entries, err := os.ReadDir(root)
	if err != nil {
		return err
	}
	var names []string
	for _, entry := range entries {
		if entry.IsDir() && strings.Contains(entry.Name(), "-") {
			names = append(names, entry.Name())
		}
	}
	date := func(name string) string { _, after, _ := strings.Cut(name, "-"); return after }
	sort.Slice(names, func(i, j int) bool {
		if date(names[i]) != date(names[j]) {
			return date(names[i]) < date(names[j])
		}
		return names[i] < names[j]
	})
	for len(names) > keptBackups {
		if err = os.RemoveAll(filepath.Join(root, names[0])); err != nil {
			return err
		}
		names = names[1:]
	}
	return nil
}

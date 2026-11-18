// Package nikolog is the log file of the app's Go side: one file both of the app's processes append to (the UI's
// and the service's `:bg`), cut to its last part when the service's process starts, with the process' stderr pointed
// at it so that what the Go runtime prints there (a panic's trace) lands in the log too.
package nikolog

import (
	"bytes"
	"io"
	"os"
	"sync"
	"sync/atomic"
)

var (
	mu   sync.Mutex
	file *os.File

	off atomic.Bool
)

// Writer is the log as an io.Writer, for log.SetOutput and the core's own lines. Before Open, and while Disable(true)
// holds, it takes every line and keeps none.
var Writer io.Writer = writer{}

// Disable turns the log off (the user's level "none") or back on, in this process.
func Disable(disabled bool) { off.Store(disabled) }

// Disabled is whether the log is off in this process.
func Disabled() bool { return off.Load() }

// Open opens the log at path for appending, made where missing. With cut (the service's process at its start), a file
// over maxSize bytes first keeps only its last maxSize bytes, from the first whole line in them. With stderr the
// process' standard error goes to the file too. A second Open in the process keeps the first file.
func Open(path string, maxSize int64, cut, stderr bool) error {
	mu.Lock()
	defer mu.Unlock()
	if file != nil {
		return nil
	}
	f, err := os.OpenFile(path, os.O_RDWR|os.O_APPEND|os.O_CREATE, 0o644)
	if err != nil {
		return err
	}
	if cut && maxSize > 0 {
		// the other process may be writing: the cut holds the same lock as each line
		lock(f)
		err = keepTail(f, maxSize)
		unlock(f)
		if err != nil {
			f.Close()
			return err
		}
	}
	if stderr {
		redirectStderr(f)
	}
	file = f
	return nil
}

// keepTail cuts f to its last maxSize bytes, starting at the first line that begins in them.
func keepTail(f *os.File, maxSize int64) error {
	info, err := f.Stat()
	if err != nil || info.Size() <= maxSize {
		return err
	}
	// the byte before the part kept too: a part that starts a line keeps it
	buf := make([]byte, maxSize+1)
	if _, err = f.ReadAt(buf, info.Size()-maxSize-1); err != nil && err != io.EOF {
		return err
	}
	tail := buf[1:]
	if buf[0] != '\n' {
		if i := bytes.IndexByte(tail, '\n'); i >= 0 {
			tail = tail[i+1:]
		}
	}
	if err = f.Truncate(0); err != nil {
		return err
	}
	// O_APPEND: after the truncation the write lands at the start
	_, err = f.Write(tail)
	return err
}

// Truncate empties the log (the app's "clear log").
func Truncate() {
	mu.Lock()
	defer mu.Unlock()
	if file != nil {
		lock(file)
		_ = file.Truncate(0)
		unlock(file)
	}
}

type writer struct{}

// Write appends p as one write under the file's lock, so the two processes' lines do not interleave. The log never
// fails its caller: what cannot be written is dropped.
func (writer) Write(p []byte) (int, error) {
	if off.Load() {
		return len(p), nil
	}
	mu.Lock()
	f := file
	mu.Unlock()
	if f != nil {
		lock(f)
		_, _ = f.Write(p)
		unlock(f)
	}
	return len(p), nil
}

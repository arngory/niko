package nikolog

import (
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
)

// reset drops the file of a test before, as a new process starts with none
func reset(t *testing.T) {
	t.Helper()
	mu.Lock()
	if file != nil {
		file.Close()
		file = nil
	}
	mu.Unlock()
	off.Store(false)
	t.Cleanup(reset2)
}

func reset2() {
	mu.Lock()
	if file != nil {
		file.Close()
		file = nil
	}
	mu.Unlock()
	off.Store(false)
}

func TestNothingBeforeOpen(t *testing.T) {
	reset(t)
	if n, err := Writer.Write([]byte("line\n")); n != 5 || err != nil {
		t.Fatalf("%d %v", n, err)
	}
}

func TestCutKeepsWholeLines(t *testing.T) {
	reset(t)
	path := filepath.Join(t.TempDir(), "neko.log")
	var b strings.Builder
	for i := 0; i < 100; i++ {
		b.WriteString("line number ")
		b.WriteString(strings.Repeat("x", i%7))
		b.WriteString("\n")
	}
	os.WriteFile(path, []byte(b.String()), 0o644)
	if err := Open(path, 200, true, false); err != nil {
		t.Fatal(err)
	}
	got, _ := os.ReadFile(path)
	if len(got) > 200 || len(got) == 0 || !strings.HasPrefix(string(got), "line number") || !strings.HasSuffix(b.String(), string(got)) {
		t.Fatalf("cut to %d bytes: %q", len(got), got)
	}
	// written after the cut: appended
	Writer.Write([]byte("after\n"))
	got, _ = os.ReadFile(path)
	if !strings.HasSuffix(string(got), "\nafter\n") {
		t.Fatalf("after the cut: %q", got)
	}
}

// a part that starts on a line's first byte keeps that line
func TestCutAtALine(t *testing.T) {
	reset(t)
	path := filepath.Join(t.TempDir(), "neko.log")
	os.WriteFile(path, []byte("aaaa\nbbbb\ncccc\n"), 0o644)
	if err := Open(path, 10, true, false); err != nil {
		t.Fatal(err)
	}
	if got, _ := os.ReadFile(path); string(got) != "bbbb\ncccc\n" {
		t.Fatalf("%q", got)
	}
}

func TestNoCutKeepsAll(t *testing.T) {
	reset(t)
	path := filepath.Join(t.TempDir(), "neko.log")
	old := strings.Repeat("old line\n", 100)
	os.WriteFile(path, []byte(old), 0o644)
	// the UI's process: it appends, the service's cuts
	if err := Open(path, 100, false, false); err != nil {
		t.Fatal(err)
	}
	Writer.Write([]byte("new\n"))
	if got, _ := os.ReadFile(path); string(got) != old+"new\n" {
		t.Fatalf("%d bytes", len(got))
	}
	// a second Open keeps the first file
	if err := Open(filepath.Join(t.TempDir(), "other.log"), 100, true, false); err != nil || file.Name() != path {
		t.Fatalf("second open: %v %s", err, file.Name())
	}
}

func TestDisableAndTruncate(t *testing.T) {
	reset(t)
	path := filepath.Join(t.TempDir(), "neko.log")
	if err := Open(path, 1<<20, true, false); err != nil {
		t.Fatal(err)
	}
	Writer.Write([]byte("kept\n"))
	Disable(true)
	if !Disabled() {
		t.Fatal("not disabled")
	}
	Writer.Write([]byte("dropped\n"))
	Disable(false)
	if got, _ := os.ReadFile(path); string(got) != "kept\n" {
		t.Fatalf("%q", got)
	}
	Truncate()
	Writer.Write([]byte("fresh\n"))
	if got, _ := os.ReadFile(path); string(got) != "fresh\n" {
		t.Fatalf("after truncate: %q", got)
	}
}

// lines written at once from many goroutines come out whole
func TestLinesWhole(t *testing.T) {
	reset(t)
	path := filepath.Join(t.TempDir(), "neko.log")
	if err := Open(path, 1<<20, false, false); err != nil {
		t.Fatal(err)
	}
	var wg sync.WaitGroup
	line := strings.Repeat("y", 300) + "\n"
	for i := 0; i < 50; i++ {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for j := 0; j < 20; j++ {
				Writer.Write([]byte(line))
			}
		}()
	}
	wg.Wait()
	got, _ := os.ReadFile(path)
	for _, l := range strings.Split(strings.TrimSuffix(string(got), "\n"), "\n") {
		if l+"\n" != line {
			t.Fatalf("a broken line: %q", l)
		}
	}
	if len(got) != 1000*len(line) {
		t.Fatalf("%d bytes, want %d", len(got), 1000*len(line))
	}
}

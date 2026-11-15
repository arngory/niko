package libcore

import (
	"bytes"
	"compress/gzip"
	"os"
	"path/filepath"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"
)

// TestMemoryStats: one line the e2e can take apart, every field a number in KB but GOGC's and the limit's words.
func TestMemoryStats(t *testing.T) {
	line := MemoryStats()
	t.Log(line)
	fields := strings.Fields(line)
	if len(fields) < 3 || fields[0] != "go-mem" || (fields[1] != "ui" && fields[1] != "bg") {
		t.Fatalf("not a go-mem line: %q", line)
	}
	seen := map[string]string{}
	for _, f := range fields[2:] {
		key, value, ok := strings.Cut(f, "=")
		if !ok {
			t.Fatalf("field %q of %q is not key=value", f, line)
		}
		seen[key] = value
	}
	for _, key := range []string{"sys", "heap_inuse", "heap_idle", "heap_released", "stack", "other", "gc", "next_gc", "goroutines"} {
		if _, err := strconv.ParseUint(seen[key], 10, 64); err != nil {
			t.Fatalf("%s=%q in %q", key, seen[key], line)
		}
	}
	if seen["sys"] == "0" {
		t.Fatalf("no memory from the system: %q", line)
	}
	if seen["limit"] != "none" {
		t.Fatalf("a test process has no soft limit, got limit=%s", seen["limit"])
	}
}

// TestWriteMemoryProfiles: nothing without the e2e's file, the heap's profile (gzip, pprof) and the goroutines with it
func TestWriteMemoryProfiles(t *testing.T) {
	dir := t.TempDir()
	saved := memoryProfileDir
	memoryProfileDir = dir
	t.Cleanup(func() { memoryProfileDir = saved })

	writeMemoryProfiles()
	if entries, _ := os.ReadDir(dir); len(entries) != 0 {
		t.Fatalf("profiles without the file: %v", entries)
	}

	if err := os.WriteFile(filepath.Join(dir, "memprofile"), nil, 0o600); err != nil {
		t.Fatal(err)
	}
	writeMemoryProfiles()
	heap, err := os.ReadFile(filepath.Join(dir, "heap-ui.pb.gz"))
	if err != nil {
		t.Fatal(err)
	}
	if _, err := gzip.NewReader(bytes.NewReader(heap)); err != nil {
		t.Fatalf("the heap's profile is no gzip: %v", err)
	}
	goroutines, err := os.ReadFile(filepath.Join(dir, "goroutines-ui.txt"))
	if err != nil {
		t.Fatal(err)
	}
	if !strings.Contains(string(goroutines), "TestWriteMemoryProfiles") {
		t.Fatalf("the goroutines do not name this test:\n%s", goroutines)
	}
}

// TestReleaseMemory: what a sync.Pool holds (where gVisor's packet chunks go) is gone after releaseMemory, which one
// collection alone leaves in the pool's victim cache
func TestReleaseMemory(t *testing.T) {
	var pool sync.Pool
	var freed atomic.Bool
	chunk := new([64 << 10]byte)
	runtime.SetFinalizer(chunk, func(*[64 << 10]byte) { freed.Store(true) })
	pool.Put(chunk)
	chunk = nil
	releaseMemory()
	for i := 0; i < 100 && !freed.Load(); i++ {
		time.Sleep(10 * time.Millisecond)
	}
	if !freed.Load() {
		t.Fatal("the pooled chunk is still there after releaseMemory")
	}
}

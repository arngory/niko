package libcore

import (
	"strconv"
	"strings"
	"testing"
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

package libcore

import (
	"fmt"
	"math"
	"runtime"
	"runtime/metrics"
)

// MemoryStats is the Go side of this process's memory in one line of the log, in KB: what the runtime holds from the
// system (sys), its heap in use, idle, and of the idle what it gave back (released), the goroutines' stacks, the
// runtime's own (other), the collections so far and the heap the next one waits for, the goroutines, GOGC and the
// soft limit. Go's part only: Chromium's (naive, PartitionAlloc), Java's and the library's own pages are not in it
// (dumpsys meminfo, /proc/self/smaps). The e2e reads it from the log (ci.yml, job e2e; ForceGc writes it).
//
// It stops the world for the time of runtime.ReadMemStats (well under a millisecond): not for a loop.
func MemoryStats() string {
	var m runtime.MemStats
	runtime.ReadMemStats(&m)
	samples := []metrics.Sample{{Name: "/gc/gogc:percent"}, {Name: "/gc/gomemlimit:bytes"}}
	metrics.Read(samples)
	gogc, limit := "?", "?"
	if samples[0].Value.Kind() == metrics.KindUint64 {
		gogc = fmt.Sprint(samples[0].Value.Uint64())
	}
	if samples[1].Value.Kind() == metrics.KindUint64 {
		if v := samples[1].Value.Uint64(); v >= math.MaxInt64 {
			limit = "none"
		} else {
			limit = fmt.Sprint(v >> 10)
		}
	}
	process := "ui"
	if isBgProcess {
		process = "bg"
	}
	return fmt.Sprintf("go-mem %s sys=%d heap_inuse=%d heap_idle=%d heap_released=%d stack=%d other=%d gc=%d next_gc=%d goroutines=%d gogc=%s limit=%s",
		process, m.Sys>>10, m.HeapInuse>>10, m.HeapIdle>>10, m.HeapReleased>>10, m.StackSys>>10,
		(m.MSpanSys+m.MCacheSys+m.BuckHashSys+m.GCSys+m.OtherSys)>>10, m.NumGC, m.NextGC>>10, runtime.NumGoroutine(),
		gogc, limit)
}

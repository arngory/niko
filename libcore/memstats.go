package libcore

import (
	"bytes"
	"fmt"
	"log"
	"math"
	"os"
	"path/filepath"
	"runtime"
	"runtime/debug"
	"runtime/metrics"
	"runtime/pprof"
	"sync/atomic"
	"time"
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

// memoryProfileDir is the working directory InitCore makes (no_backup): where the e2e asks for the profiles.
var memoryProfileDir string

// writeMemoryProfiles writes, when the file "memprofile" is in memoryProfileDir, the Go heap's profile (pprof: what is
// live as of the last collection, by where it was allocated) and the goroutines' stacks there, as heap-PROC.pb.gz and
// goroutines-PROC.txt: what keeps the heap the go-mem line counts. The e2e makes that file (as root); the app never
// does. Function names and sizes, no data the connections carry. ForceGc calls it after its collection, before its
// line, so the files are there by the time the line is.
func writeMemoryProfiles() {
	if memoryProfileDir == "" {
		return
	}
	if _, err := os.Stat(filepath.Join(memoryProfileDir, "memprofile")); err != nil {
		return
	}
	process := "ui"
	if isBgProcess {
		process = "bg"
	}
	for _, p := range []struct {
		name, file string
		debug      int
	}{
		{"heap", "heap-" + process + ".pb.gz", 0},
		{"goroutine", "goroutines-" + process + ".txt", 1},
	} {
		var out bytes.Buffer
		if err := pprof.Lookup(p.name).WriteTo(&out, p.debug); err != nil {
			log.Println("memory profile:", p.name, err)
			continue
		}
		if err := writeFileAtomic(filepath.Join(memoryProfileDir, p.file), out.Bytes()); err != nil {
			log.Println("memory profile:", p.name, err)
		}
	}
}

// releaseMemory gives the system back what the Go heap does not use: two collections, the first moving what the
// sync.Pools hold into their victim caches, the second (FreeOSMemory's) dropping those and returning the pages. The
// pools are where a stopped TUN stack's packets go (gVisor's chunks, sing's buffers): under an upload through a slow
// outbound its TCP queues take up to 8 MiB a connection, and FreeOSMemory alone kept them, 19.6 MB of gVisor's
// chunks live in the e2e's heap profile after naive's upload with the VPN down.
func releaseMemory() {
	runtime.GC()
	debug.FreeOSMemory()
}

var releasePending atomic.Bool

// releaseMemorySoon runs releaseMemory a moment after a box closed, once for a run of closes (a URL test closes a box
// for each profile it tests).
func releaseMemorySoon() {
	if releasePending.CompareAndSwap(false, true) {
		time.AfterFunc(3*time.Second, func() {
			releasePending.Store(false)
			releaseMemory()
		})
	}
}

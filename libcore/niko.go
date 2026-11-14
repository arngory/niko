package libcore

import (
	"fmt"
	"libcore/device"
	"os"
	"path/filepath"
	"runtime/debug"
	"strings"
	_ "unsafe"

	"log"

	"github.com/matsuridayo/libneko/neko_common"
	"github.com/matsuridayo/libneko/neko_log"
	"github.com/sagernet/sing-box/experimental/cachefile"
	"github.com/sagernet/sing-box/nekoutils"
	"github.com/sagernet/sing-box/option"
	"golang.org/x/sys/unix"
)

//go:linkname resourcePaths github.com/sagernet/sing-box/constant.resourcePaths
var resourcePaths []string

func NekoLogPrintln(s string) {
	log.Println(s)
}

func NekoLogClear() {
	neko_log.LogWriter.Truncate()
}

func ForceGc() {
	go debug.FreeOSMemory()
}

func InitCore(process, cachePath, internalAssets, externalAssets string,
	maxLogSizeKb int32, logEnable bool,
	if1 NikoInterface, if2 BoxPlatformInterface, if3 LocalDNSTransport,
) {
	defer device.DeferPanicToError("InitCore", func(err error) { log.Println(err) })
	isBgProcess = strings.HasSuffix(process, ":bg")

	neko_common.RunMode = neko_common.RunMode_NekoBoxForAndroid
	intfNiko = if1
	intfBox = if2
	useProcfs = intfBox.UseProcFS()
	gLocalDNSTransport = newPlatformTransport(if3, "", option.LocalDNSServerOptions{})

	// Working dir
	tmp := filepath.Join(cachePath, "../no_backup")
	os.MkdirAll(tmp, 0755)
	os.Chdir(tmp)
	// the file of roots naive's Chromium reads besides Chrome's list: named to it here, before any engine
	// (naive_roots.go)
	initNaiveRoots(tmp, isBgProcess)

	// sing-box fs
	resourcePaths = append(resourcePaths, externalAssets)
	externalAssetsPath = externalAssets
	internalAssetsPath = internalAssets

	// Set up log: 512 KB unless set (the log is cut to its last part at the service's start, and 50 KB, the
	// old default, kept less than one Go crash trace)
	if maxLogSizeKb <= 0 {
		maxLogSizeKb = 512
	} else if maxLogSizeKb < 50 {
		maxLogSizeKb = 50
	}
	neko_log.LogWriterDisable = !logEnable
	neko_log.TruncateOnStart = isBgProcess
	neko_log.SetupLog(int(maxLogSizeKb)*1024, filepath.Join(cachePath, "neko.log"))
	// every line of the log without secrets (redact.go)
	log.SetOutput(redactingWriter{neko_log.LogWriter})
	setCrashOutput(cachePath, isBgProcess)

	// nekoutils
	nekoutils.Selector_OnProxySelected = intfNiko.Selector_OnProxySelected

	// Set up some component
	go func() {
		defer device.DeferPanicToError("InitCore-go", func(err error) { log.Println(err) })
		device.GoDebug(process)

		// the roots chains are verified against: the list of the setting, with the ca.pem imported in the app
		// (AssetsActivity) into the route assets, which live in internal storage since 1.26.0
		applyRootStore()

		// bg
		if isBgProcess {
			extractAssets()
		}
	}()
}

// CrashFile is where a Go crash of the process goes (debug.SetCrashOutput), besides stderr (neko.log): the log is cut
// to its last part at the next start, which loses the head of a long trace, the panic's message with it.
func CrashFile(cachePath string, bg bool) string {
	name := "go-crash.log"
	if bg {
		name = "go-crash-bg.log"
	}
	return filepath.Join(cachePath, name)
}

// setCrashOutput appends this process's crashes to its crash file; one past 2 MB moves to .old first (the last crash
// is what matters, at its whole length).
func setCrashOutput(cachePath string, bg bool) {
	path := CrashFile(cachePath, bg)
	if info, err := os.Stat(path); err == nil && info.Size() > 2<<20 {
		_ = os.Rename(path, path+".old")
	}
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_APPEND|os.O_CREATE, 0o600)
	if err != nil {
		log.Println("crash output:", err)
		return
	}
	defer f.Close()
	if err := debug.SetCrashOutput(f, debug.CrashOptions{}); err != nil {
		log.Println("crash output:", err)
	}
}

func sendFdToProtect(fd int, path string) error {
	socketFd, err := unix.Socket(unix.AF_UNIX, unix.SOCK_STREAM, 0)
	if err != nil {
		return fmt.Errorf("failed to create unix socket: %w", err)
	}
	defer unix.Close(socketFd)

	var timeout unix.Timeval
	timeout.Usec = 100 * 1000

	_ = unix.SetsockoptTimeval(socketFd, unix.SOL_SOCKET, unix.SO_RCVTIMEO, &timeout)
	_ = unix.SetsockoptTimeval(socketFd, unix.SOL_SOCKET, unix.SO_SNDTIMEO, &timeout)

	err = unix.Connect(socketFd, &unix.SockaddrUnix{Name: path})
	if err != nil {
		return fmt.Errorf("failed to connect: %w", err)
	}

	err = unix.Sendmsg(socketFd, nil, unix.UnixRights(fd), nil, 0)
	if err != nil {
		return fmt.Errorf("failed to send: %w", err)
	}

	dummy := []byte{1}
	n, err := unix.Read(socketFd, dummy)
	if err != nil {
		return fmt.Errorf("failed to receive: %w", err)
	}
	if n != 1 {
		return fmt.Errorf("socket closed unexpectedly")
	}
	return nil
}

// SetLogEnable turns the log writer on or off in this process (initCore set it from the log level); a test run
// turns it on for its window and back after. The flag is a plain bool of libneko read by the writer without a lock:
// a word-sized store, at worst a line more or less around the switch.
func SetLogEnable(enable bool) {
	neko_log.LogWriterDisable = !enable
}

// LogMarker writes one line to the app's log: a test run finds its own part of the log by it, whatever the file
// was trimmed to meanwhile.
func LogMarker(text string) {
	log.Println(text)
}

// ClearFakeIPCache removes FakeDNS's addresses and the names they stand for from the core's cache file at path:
// kept for a restart of the core under a running VPN, they are a record of the names asked. No core may have the
// file open (the service calls it when the VPN stops, or before a fresh start).
func ClearFakeIPCache(path string) (err error) {
	// a panic would cross gomobile and end the process, at every start: an error instead
	defer device.DeferPanicToError("ClearFakeIPCache", func(e error) { err = e })
	return cachefile.ClearFakeIP(path)
}

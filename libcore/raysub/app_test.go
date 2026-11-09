package raysub

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"sync/atomic"
	"testing"
	"time"
)

// a release file as a node holds it (app-update-v1): Range, If-Range by its ETag, the sha256 in quotes
type appFile struct {
	name    string
	content []byte
	// what the node saw: the requests, the Range of the last
	requests  atomic.Int32
	lastRange atomic.Value
}

func (f *appFile) etag() string {
	sum := sha256.Sum256(f.content)
	return `"` + hex.EncodeToString(sum[:]) + `"`
}

func (f *appFile) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	f.requests.Add(1)
	f.lastRange.Store(r.Header.Get("Range"))
	if r.URL.Path != "/app/v1/niko/v1.38.0/"+f.name {
		http.Error(w, "not found", http.StatusNotFound)
		return
	}
	w.Header().Set("ETag", f.etag())
	http.ServeContent(w, r, f.name, time.Time{}, bytes.NewReader(f.content))
}

func randomBytes(n int) []byte {
	b := make([]byte, n)
	rand.Read(b)
	return b
}

func appNode(t *testing.T, handler http.Handler) (*node, string) {
	n := newNode(t)
	n.app = handler
	return n, filepath.Join(t.TempDir(), "file")
}

func TestNekoAppFileWhole(t *testing.T) {
	f := &appFile{name: "Niko-1.38.0-arm64-v8a.apk", content: randomBytes(300 << 10)}
	n, out := appNode(t, f)
	if err := AppFile(context.Background(), n.request, "niko", "v1.38.0", f.name, out, f.etag()); err != nil {
		t.Fatal(err)
	}
	got, _ := os.ReadFile(out)
	if !bytes.Equal(got, f.content) {
		t.Fatalf("%d bytes, want the file's %d", len(got), len(f.content))
	}
}

func TestNekoAppFileResumes(t *testing.T) {
	f := &appFile{name: "Niko-1.38.0-arm64-v8a.apk", content: randomBytes(300 << 10)}
	n, out := appNode(t, f)
	os.WriteFile(out, f.content[:100<<10], 0o600)
	if err := AppFile(context.Background(), n.request, "niko", "v1.38.0", f.name, out, f.etag()); err != nil {
		t.Fatal(err)
	}
	if r := f.lastRange.Load(); r != "bytes=102400-" {
		t.Fatalf("Range %q, want the rest", r)
	}
	got, _ := os.ReadFile(out)
	if !bytes.Equal(got, f.content) {
		t.Fatal("not the file after the rest")
	}
	// a part of another file (If-Range fails): the whole comes, and out is the file
	os.WriteFile(out, randomBytes(100<<10), 0o600)
	if err := AppFile(context.Background(), n.request, "niko", "v1.38.0", f.name, out, `"`+strings.Repeat("0", 64)+`"`); err != nil {
		t.Fatal(err)
	}
	got, _ = os.ReadFile(out)
	if !bytes.Equal(got, f.content) {
		t.Fatal("another file's part kept")
	}
	// more than the file (416): out starts over and gets it whole
	os.WriteFile(out, randomBytes(400<<10), 0o600)
	if err := AppFile(context.Background(), n.request, "niko", "v1.38.0", f.name, out, f.etag()); err != nil {
		t.Fatal(err)
	}
	got, _ = os.ReadFile(out)
	if !bytes.Equal(got, f.content) {
		t.Fatal("not the file after 416")
	}
	// no etag: a part cannot be told from another file's, the whole
	os.WriteFile(out, f.content[:10], 0o600)
	if err := AppFile(context.Background(), n.request, "niko", "v1.38.0", f.name, out, ""); err != nil {
		t.Fatal(err)
	}
	if r := f.lastRange.Load(); r != "" {
		t.Fatalf("Range %q without an etag", r)
	}
	got, _ = os.ReadFile(out)
	if !bytes.Equal(got, f.content) {
		t.Fatal("not the file without an etag")
	}
}

func TestNekoAppFileBusyThenServed(t *testing.T) {
	f := &appFile{name: "SHA256SUMS", content: []byte("sums\n")}
	var busy atomic.Int32
	n, out := appNode(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if busy.Add(1) == 1 {
			w.Header().Set("Retry-After", "1")
			http.Error(w, "busy", http.StatusTooManyRequests)
			return
		}
		f.ServeHTTP(w, r)
	}))
	started := time.Now()
	if err := AppFile(context.Background(), n.request, "niko", "v1.38.0", f.name, out, ""); err != nil {
		t.Fatal(err)
	}
	if time.Since(started) < time.Second {
		t.Fatal("Retry-After not waited")
	}
	// always busy: three tries, then the error
	n.app = http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Retry-After", "1")
		http.Error(w, "busy", http.StatusTooManyRequests)
	})
	var status busyError
	if err := AppFile(context.Background(), n.request, "niko", "v1.38.0", f.name, out, ""); !errors.As(err, &status) {
		t.Fatalf("%v, want 429 after the tries", err)
	}
}

func TestNekoAppFileNotHeld(t *testing.T) {
	f := &appFile{name: "SHA256SUMS", content: []byte("sums\n")}
	n, out := appNode(t, f)
	if err := AppFile(context.Background(), n.request, "niko", "v1.38.0", "Niko-1.38.0-x86.apk", out, ""); !errors.Is(err, ErrAppNotHeld) {
		t.Fatalf("%v, want ErrAppNotHeld", err)
	}
	// the node answered: one request, no knock on the other carriers
	if got := f.requests.Load(); got != 1 {
		t.Fatalf("%d requests, want 1", got)
	}
	// another device's key: the node's 404 too
	other := n.request
	other.SPA.DeviceKey = strings.Repeat("22", 32)
	if err := AppFile(context.Background(), other, "niko", "v1.38.0", f.name, out, ""); !errors.Is(err, ErrAppNotHeld) {
		t.Fatalf("%v, want ErrAppNotHeld", err)
	}
}

func TestNekoAppFileCutShort(t *testing.T) {
	content := randomBytes(200 << 10)
	sum := sha256.Sum256(content)
	etag := `"` + hex.EncodeToString(sum[:]) + `"`
	var cut atomic.Bool
	n, out := appNode(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("ETag", etag)
		if cut.CompareAndSwap(false, true) {
			// the length of the whole, and half of it: the connection ends there
			w.Header().Set("Content-Length", "204800")
			w.WriteHeader(http.StatusOK)
			w.Write(content[:100<<10])
			panic(http.ErrAbortHandler)
		}
		http.ServeContent(w, r, "f", time.Time{}, bytes.NewReader(content))
	}))
	// the next carrier goes on from the half: the whole in the end
	if err := AppFile(context.Background(), n.request, "niko", "v1.38.0", "f.apk", out, etag); err != nil {
		t.Fatal(err)
	}
	got, _ := os.ReadFile(out)
	if !bytes.Equal(got, content) {
		t.Fatalf("%d bytes, want the whole", len(got))
	}
}

func TestNekoAppFileNames(t *testing.T) {
	n, out := appNode(t, http.NotFoundHandler())
	for _, c := range [][3]string{
		{"other", "v1.38.0", "SHA256SUMS"}, {"niko", "1.38.0", "SHA256SUMS"}, {"niko", "v1.38.0/..", "x"},
		{"niko", "v1.38.0", "../x"}, {"niko", "v1.38.0", ".."}, {"niko", "v1.38.0", "a/b"}, {"niko", "v1.38.0", ""},
	} {
		if err := AppFile(context.Background(), n.request, c[0], c[1], c[2], out, ""); err == nil || errors.Is(err, ErrAppNotHeld) {
			t.Fatalf("%q asked: %v", c, err)
		}
	}
}

func TestNekoAppFileCutOverAndOver(t *testing.T) {
	// a node that ends every answer after 20 KB (as one ends a download at 15 min): more cuts than carriers, each
	// bringing more, the whole in the end
	content := randomBytes(300 << 10)
	sum := sha256.Sum256(content)
	etag := `"` + hex.EncodeToString(sum[:]) + `"`
	var cuts atomic.Int32
	n, out := appNode(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("ETag", etag)
		start := int64(0)
		if spec, ok := strings.CutPrefix(r.Header.Get("Range"), "bytes="); ok && r.Header.Get("If-Range") == etag {
			start, _ = strconv.ParseInt(strings.TrimSuffix(spec, "-"), 10, 64)
		}
		rest := content[start:]
		if start > 0 {
			w.Header().Set("Content-Range", "bytes "+strconv.FormatInt(start, 10)+"-"+strconv.Itoa(len(content)-1)+"/"+strconv.Itoa(len(content)))
		}
		w.Header().Set("Content-Length", strconv.Itoa(len(rest)))
		if start > 0 {
			w.WriteHeader(http.StatusPartialContent)
		} else {
			w.WriteHeader(http.StatusOK)
		}
		if len(rest) > 20<<10 {
			w.Write(rest[:20<<10])
			cuts.Add(1)
			panic(http.ErrAbortHandler)
		}
		w.Write(rest)
	}))
	if err := AppFile(context.Background(), n.request, "niko", "v1.38.0", "f.apk", out, etag); err != nil {
		t.Fatal(err)
	}
	got, _ := os.ReadFile(out)
	if !bytes.Equal(got, content) {
		t.Fatalf("%d bytes, want the whole", len(got))
	}
	if c := cuts.Load(); c < 10 {
		t.Fatalf("%d cuts, want more than the carriers", c)
	}
	// without an etag a part is not gone on from: no endless start over (the carriers bound it)
	os.Remove(out)
	cuts.Store(0)
	if err := AppFile(context.Background(), n.request, "niko", "v1.38.0", "f.apk", out, ""); err == nil {
		t.Fatal("whole without an etag from a node that cuts every answer")
	}
	if c := cuts.Load(); c > 5 {
		t.Fatalf("%d tries without an etag, want the carriers' few", c)
	}
}

// A node is trusted for nothing: a file larger than a release's file can be is dropped, not written on.
func TestNekoAppFileTooLarge(t *testing.T) {
	if appMaxSize("https://n/app/v1/niko/v1.0.0/Niko-1.0.0-x86.apk") != 256<<20 || appMaxSize("https://n/app/v1/niko/v1.0.0/SHA256SUMS") != 64<<10 {
		t.Fatal("limits")
	}
}

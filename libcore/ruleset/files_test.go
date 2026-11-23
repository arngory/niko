package ruleset

import (
	"bytes"
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"testing"
	"time"

	"libcore/nikoconf"

	"github.com/sagernet/sing-box/common/srs"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json/badoption"
)

// SRS is a binary rule-set of one rule, for tests.
func SRS(t testing.TB, rule option.DefaultHeadlessRule) []byte {
	t.Helper()
	var buf bytes.Buffer
	set := option.PlainRuleSet{Rules: []option.HeadlessRule{{Type: C.RuleTypeDefault, DefaultOptions: rule}}}
	if err := srs.Write(&buf, set, C.RuleSetVersionCurrent); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

// A list served over https: downloaded, checked, kept with its kinds; what a build asks of it (Path, Kinds, Stat);
// fetched again only once it is older than the age asked; a failed download keeps the file there was, and its error
// names no address (a list's may hold a token).
func TestDownload(t *testing.T) {
	domains := SRS(t, option.DefaultHeadlessRule{DomainSuffix: badoption.Listable[string]{"example.org"}})
	var answer []byte
	status, hits := http.StatusOK, 0
	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits++
		w.WriteHeader(status)
		w.Write(answer)
	}))
	defer server.Close()
	client := &Client{Transport: server.Client().Transport}
	files := Files{Dir: filepath.Join(t.TempDir(), "rulesets")}
	item := "srs:" + server.URL + "/geosite-x.srs?token=fake-token"

	answer = domains
	kinds, err := files.Download(context.Background(), client, item)
	if err != nil || kinds != int(HasDomain) {
		t.Fatalf("%d %v", kinds, err)
	}
	path := files.Path(item)
	if path != filepath.Join(files.Dir, nikoconf.RuleSetFileName(item)) {
		t.Fatalf("path %q", path)
	}
	if got, _ := os.ReadFile(path); !bytes.Equal(got, domains) {
		t.Fatal("not the list")
	}
	if k, ok := files.Kinds(item, false); !ok || k != int(HasDomain) {
		t.Fatalf("kinds %d %v", k, ok)
	}
	if info := files.Stat(item); info.Size != int64(len(domains)) || !info.KindsKnown {
		t.Fatalf("%+v", info)
	}
	// a kinds file older than its file is not about it: checked again with check, unknown without
	os.Chtimes(kindsFile(path), time.Now().Add(-time.Hour), time.Now().Add(-time.Hour))
	if _, ok := files.Kinds(item, false); ok {
		t.Fatal("a stale kinds file taken")
	}
	if k, ok := files.Kinds(item, true); !ok || k != int(HasDomain) {
		t.Fatalf("checked again: %d %v", k, ok)
	}

	// fresh: not fetched; older than the age: fetched
	now := time.Now()
	if results := files.Update(context.Background(), client, []string{item}, time.Hour, now); len(results) != 0 || hits != 1 {
		t.Fatalf("%v, %d fetches", results, hits)
	}
	if results := files.Update(context.Background(), client, []string{item, item}, time.Hour, now.Add(2*time.Hour)); len(results) != 1 || results[item] != nil || hits != 2 {
		t.Fatalf("%v, %d fetches", results, hits)
	}

	// failures: the file stays
	for name, serve := range map[string]func(){
		"a status":      func() { status, answer = http.StatusNotFound, []byte("no") },
		"not a list":    func() { status, answer = http.StatusOK, []byte("<html>a captive portal</html>") },
		"a broken list": func() { status, answer = http.StatusOK, append([]byte("SRS\x01"), bytes.Repeat([]byte{1}, 64)...) },
	} {
		serve()
		_, err := files.Download(context.Background(), client, item)
		if err == nil {
			t.Fatalf("%s: downloaded", name)
		}
		if strings.Contains(err.Error(), "fake-token") || strings.Contains(err.Error(), server.URL) {
			t.Fatalf("%s: the error names the address: %v", name, err)
		}
		if got, _ := os.ReadFile(path); !bytes.Equal(got, domains) {
			t.Fatalf("%s: the file replaced", name)
		}
	}
	// no server: no address in the error either
	server.Close()
	if _, err = files.Download(context.Background(), client, item); err == nil || strings.Contains(err.Error(), "fake-token") {
		t.Fatalf("no server: %v", err)
	}
	// only https
	if _, err = files.Download(context.Background(), client, "srs:http://lists.example.com/x.srs"); !errors.Is(err, ErrNotHTTPS) {
		t.Fatalf("http: %v", err)
	}
	if results := files.Update(context.Background(), client, []string{"srs:http://lists.example.com/x.srs"}, 0, now); !errors.Is(results["srs:http://lists.example.com/x.srs"], ErrNotHTTPS) {
		t.Fatalf("%v", results)
	}
	if entries, _ := os.ReadDir(files.Dir); len(entries) != 2 {
		t.Fatalf("left behind: %v", entries)
	}
}

// What a hostile server may do bounds the download: a body said to be larger than a list may be is not read, one
// larger without saying so is cut, a server that goes quiet is left, a redirect to http is not followed.
func TestDownloadBounds(t *testing.T) {
	quiet := make(chan struct{})
	server := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/declared.srs":
			w.Header().Set("Content-Length", strconv.Itoa(MaxSize+1))
			w.Write([]byte("SRS"))
		case "/endless.srs":
			chunk := bytes.Repeat([]byte("S"), 1<<20)
			for i := 0; i <= MaxSize>>20+1; i++ {
				if _, err := w.Write(chunk); err != nil {
					return
				}
			}
		case "/quiet.srs":
			w.Write([]byte("SRS"))
			w.(http.Flusher).Flush()
			select {
			case <-quiet:
			case <-r.Context().Done():
			}
		case "/redirect.srs":
			http.Redirect(w, r, "http://lists.example.com/x.srs", http.StatusFound)
		}
	}))
	defer server.Close()
	defer close(quiet)
	client := &Client{Transport: server.Client().Transport, Idle: 300 * time.Millisecond}
	files := Files{Dir: t.TempDir()}
	for _, name := range []string{"declared", "endless", "quiet", "redirect"} {
		item := "srs:" + server.URL + "/" + name + ".srs"
		start := time.Now()
		if _, err := files.Download(context.Background(), client, item); err == nil {
			t.Fatalf("%s: downloaded", name)
		} else {
			t.Logf("%s: %v", name, err)
		}
		if time.Since(start) > 20*time.Second {
			t.Fatalf("%s: took %v", name, time.Since(start))
		}
		if files.Path(item) != "" {
			t.Fatalf("%s: a file kept", name)
		}
	}
	if entries, _ := os.ReadDir(files.Dir); len(entries) != 0 {
		t.Fatalf("left behind: %v", entries)
	}
}

// LooksValid is RuleSetFiles.looksValid.
func TestLooksValid(t *testing.T) {
	for head, want := range map[string]bool{"SRS\x01": true, "SRS": false, "SR": false, " \n{": true, "[": false, "": false} {
		format := "binary"
		if !strings.HasPrefix(head, "S") {
			format = "source"
		}
		if LooksValid([]byte(head), format) != want {
			t.Errorf("%q %s", head, format)
		}
	}
}

// RemoveUnused removes the files no rule names, and downloads left half done an hour ago.
func TestRemoveUnused(t *testing.T) {
	dir := t.TempDir()
	files := Files{Dir: dir}
	kept, gone := "srs:https://a.example/kept.srs", "srs:https://a.example/gone.srs"
	for _, name := range []string{nikoconf.RuleSetFileName(kept), nikoconf.RuleSetFileName(kept) + ".kinds",
		nikoconf.RuleSetFileName(gone), nikoconf.RuleSetFileName(gone) + ".kinds", "old.tmp", "new.tmp"} {
		os.WriteFile(filepath.Join(dir, name), []byte("x"), 0o600)
	}
	old := time.Now().Add(-2 * time.Hour)
	os.Chtimes(filepath.Join(dir, "old.tmp"), old, old)
	files.RemoveUnused([]string{kept}, time.Now())
	entries, _ := os.ReadDir(dir)
	var names []string
	for _, e := range entries {
		names = append(names, e.Name())
	}
	if len(names) != 3 || files.Path(kept) == "" || files.Path(gone) != "" {
		t.Fatalf("%v", names)
	}
}

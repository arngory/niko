package ruleset

import (
	"bytes"
	"context"
	"errors"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"
	"unicode"

	"libcore/nikoconf"
)

// Files are the downloaded rule-sets in a directory, as RuleSetFiles keeps them: each item's file named by its URL
// (nikoconf.RuleSetFileName), and beside it "<file>.kinds", what Check found in it; a kinds file older than its file
// is not about it. They are what a build sees (nikoconf.RuleSets).
type Files struct {
	Dir string
}

// The fetch's bounds, RuleSetFiles.download's: a server that answers and then goes quiet holds no update.
const (
	FetchTimeout = 120 * time.Second
	IdleTimeout  = 30 * time.Second
)

// Client fetches rule-sets.
type Client struct {
	// Transport is the HTTP transport; nil: http.DefaultTransport's like, direct, the system's certificates
	Transport http.RoundTripper
	// UserAgent is sent where set
	UserAgent string
	// Timeout and Idle: zero for FetchTimeout and IdleTimeout
	Timeout, Idle time.Duration
}

func (f Files) file(item string) string { return filepath.Join(f.Dir, nikoconf.RuleSetFileName(item)) }

func kindsFile(file string) string { return file + ".kinds" }

// Path is the item's file where it is here and not empty, "" otherwise (RuleSetFiles.available but fetchable).
func (f Files) Path(item string) string {
	file := f.file(item)
	if info, err := os.Stat(file); err == nil && info.Mode().IsRegular() && info.Size() > 0 {
		return file
	}
	return ""
}

// Kinds is RuleSetFiles.kinds: what the kinds file says, where it is not older than the file; else, with check, the
// file checked now and its kinds kept.
func (f Files) Kinds(item string, check bool) (int, bool) {
	file := f.file(item)
	if kinds, ok := readKinds(file); ok {
		return kinds, true
	}
	if !check || !nikoconf.RuleSetFetchable(item) || f.Path(item) == "" {
		return 0, false
	}
	kinds, err := Check(file, nikoconf.RuleSetFormat(item))
	if err != nil {
		// not loadable: the next update replaces it
		return 0, false
	}
	_ = writeFile(kindsFile(file), []byte(strconv.Itoa(int(kinds))))
	return int(kinds), true
}

func readKinds(file string) (int, bool) {
	info, err := os.Stat(file)
	if err != nil {
		return 0, false
	}
	kinds := kindsFile(file)
	kindsInfo, err := os.Stat(kinds)
	if err != nil || kindsInfo.ModTime().Before(info.ModTime()) || kindsInfo.Size() > 16 {
		return 0, false
	}
	data, err := os.ReadFile(kinds)
	if err != nil {
		return 0, false
	}
	n, err := strconv.Atoi(strings.TrimSpace(string(data)))
	return n, err == nil
}

// Info is what is known of an item's file.
type Info struct {
	// Size and Modified: the file's; zero where it is not here
	Size     int64
	Modified time.Time
	// Kinds: what it holds (HasDomain, HasIP), where its check said; KindsKnown false otherwise
	Kinds      int
	KindsKnown bool
}

// Stat is what is known of the item's file, without reading it.
func (f Files) Stat(item string) Info {
	file := f.Path(item)
	if file == "" {
		return Info{}
	}
	var info Info
	if fi, err := os.Stat(file); err == nil {
		info.Size, info.Modified = fi.Size(), fi.ModTime()
	}
	info.Kinds, info.KindsKnown = readKinds(file)
	return info
}

// The download's errors, by what went wrong; none names the address (a list's URL may hold a token).
var (
	// ErrNotHTTPS: only https lists are fetched (a list in the clear could be any list, one sending all traffic
	// around the proxy)
	ErrNotHTTPS = errors.New("only https lists are fetched")
	// ErrNotRuleSet: what came is not a rule-set the core loads
	ErrNotRuleSet = errors.New("not a rule-set")
)

// FetchError is a download that did not get an answer to keep: the server's status, or the connection's failure.
type FetchError struct {
	Message string
}

func (e *FetchError) Error() string { return e.Message }

// Download fetches an item's rule-set as RuleSetFiles.download does: a GET of its URL, a 200 alone taken, a body of
// MaxSize at most (refused before a byte is saved where the server says it is larger), at most Timeout in all and
// Idle without a byte; a file of 5 bytes at least that begins as its format does and that the core loads (Check)
// replaces the item's, then its kinds are kept. A failed download leaves the file there was. The kinds.
func (f Files) Download(ctx context.Context, c *Client, item string) (int, error) {
	if !nikoconf.IsRuleSetItem(item) || !nikoconf.RuleSetFetchable(item) {
		return 0, ErrNotHTTPS
	}
	address := nikoconf.RuleSetURL(item)
	u, err := url.Parse(address)
	if err != nil || u.Scheme != "https" || u.Host == "" {
		return 0, errors.New("not an address")
	}
	timeout, idle := c.Timeout, c.Idle
	if timeout == 0 {
		timeout = FetchTimeout
	}
	if idle == 0 {
		idle = IdleTimeout
	}
	ctx, cancel := context.WithTimeout(ctx, timeout)
	defer cancel()
	// the idle bound runs from the start: the dial and the wait for the answer count as waits without progress
	ctx, stop := context.WithCancel(ctx)
	defer stop()
	timer := time.AfterFunc(idle, stop)
	defer timer.Stop()
	request, err := http.NewRequestWithContext(ctx, http.MethodGet, u.String(), nil)
	if err != nil {
		return 0, errors.New("not an address")
	}
	if c.UserAgent != "" {
		request.Header.Set("User-Agent", c.UserAgent)
	}
	transport := c.Transport
	if transport == nil {
		transport = &http.Transport{Proxy: nil, ForceAttemptHTTP2: true, TLSHandshakeTimeout: 30 * time.Second}
	}
	client := &http.Client{Transport: transport, CheckRedirect: func(next *http.Request, via []*http.Request) error {
		// a redirect stays on https: the list is what the user named, not what a plain address gives
		if next.URL.Scheme != "https" || len(via) >= 10 {
			return errors.New("a redirect away from https")
		}
		return nil
	}}
	response, err := client.Do(request)
	if err != nil {
		return 0, &FetchError{Message: fetchFailure(ctx, err)}
	}
	defer response.Body.Close()
	if response.StatusCode != http.StatusOK {
		return 0, &FetchError{Message: "HTTP " + strconv.Itoa(response.StatusCode)}
	}
	if response.ContentLength > MaxSize {
		return 0, fmt.Errorf("%w: %d bytes", ErrNotRuleSet, response.ContentLength)
	}
	if err = os.MkdirAll(f.Dir, 0o700); err != nil {
		return 0, err
	}
	file := f.file(item)
	// each download writes its own: two of the same list at once do not mix
	tmp, err := os.CreateTemp(f.Dir, filepath.Base(file)+".*.tmp")
	if err != nil {
		return 0, err
	}
	defer os.Remove(tmp.Name())
	n, err := copyIdle(tmp, response.Body, timer, idle)
	if closeErr := tmp.Close(); err == nil {
		err = closeErr
	}
	if err != nil {
		return 0, &FetchError{Message: fetchFailure(ctx, err)}
	}
	if n > MaxSize {
		return 0, fmt.Errorf("%w: over %d bytes", ErrNotRuleSet, MaxSize)
	}
	format := nikoconf.RuleSetFormat(item)
	head := make([]byte, 16)
	if in, err := os.Open(tmp.Name()); err == nil {
		read, _ := io.ReadFull(in, head)
		head = head[:read]
		in.Close()
	}
	if n < 5 || !LooksValid(head, format) {
		return 0, fmt.Errorf("%w (%d bytes)", ErrNotRuleSet, n)
	}
	// read as the core reads it: a file it can not load (a newer format, say) would fail every start
	kinds, err := Check(tmp.Name(), format)
	if err != nil {
		return 0, fmt.Errorf("%w: %v", ErrNotRuleSet, err)
	}
	if err = os.Rename(tmp.Name(), file); err != nil {
		return 0, err
	}
	// after the file: a kinds file older than it is not taken
	_ = writeFile(kindsFile(file), []byte(strconv.Itoa(int(kinds))))
	return int(kinds), nil
}

// copyIdle copies at most MaxSize+1 bytes, each read putting the idle bound off.
func copyIdle(w io.Writer, r io.Reader, timer *time.Timer, idle time.Duration) (int64, error) {
	buffer := make([]byte, 32<<10)
	var total int64
	limited := io.LimitReader(r, MaxSize+1)
	for {
		n, err := limited.Read(buffer)
		if n > 0 {
			timer.Reset(idle)
			if _, werr := w.Write(buffer[:n]); werr != nil {
				return total, werr
			}
			total += int64(n)
		}
		if errors.Is(err, io.EOF) {
			return total, nil
		}
		if err != nil {
			return total, err
		}
	}
}

// fetchFailure is why a fetch failed, without its address: net/http's errors name the URL.
func fetchFailure(ctx context.Context, err error) string {
	if errors.Is(ctx.Err(), context.DeadlineExceeded) {
		return "no answer in time"
	}
	if ctx.Err() != nil {
		return "the server went quiet, or the download was stopped"
	}
	var ue *url.Error
	if errors.As(err, &ue) {
		err = ue.Err
	}
	return strings.ToValidUTF8(err.Error(), "�")
}

// LooksValid is RuleSetFiles.looksValid: a file begins as a rule-set of its format, the magic "SRS" of a binary one,
// a JSON object.
func LooksValid(head []byte, format string) bool {
	if format == "binary" {
		return len(head) >= 4 && bytes.HasPrefix(head, []byte("SRS"))
	}
	for _, b := range head {
		// Kotlin's isWhitespace of the byte as a char
		if unicode.IsSpace(rune(b)) || b == 0x1c || b == 0x1d || b == 0x1e || b == 0x1f {
			continue
		}
		return b == '{'
	}
	return false
}

// Update downloads the items whose files are missing or older than maxAge (RuleSetFiles.update), between files
// stopping once ctx is done. What each got: its error, nil for one downloaded; an item fresh enough is not there.
func (f Files) Update(ctx context.Context, c *Client, items []string, maxAge time.Duration, now time.Time) map[string]error {
	results := map[string]error{}
	seen := map[string]bool{}
	for _, item := range items {
		if !nikoconf.IsRuleSetItem(item) || seen[item] {
			continue
		}
		seen[item] = true
		if ctx.Err() != nil {
			break
		}
		if !nikoconf.RuleSetFetchable(item) {
			results[item] = ErrNotHTTPS
			continue
		}
		if info := f.Stat(item); info.Size > 0 && now.Sub(info.Modified) < maxAge {
			continue
		}
		_, err := f.Download(ctx, c, item)
		results[item] = err
	}
	return results
}

// RemoveUnused removes the files no item of used names (a rule turned off keeps its), and downloads left half done
// an hour ago (RuleSetFiles.removeUnused).
func (f Files) RemoveUnused(used []string, now time.Time) {
	names := map[string]bool{}
	for _, item := range used {
		names[nikoconf.RuleSetFileName(item)] = true
	}
	entries, err := os.ReadDir(f.Dir)
	if err != nil {
		return
	}
	for _, e := range entries {
		name := e.Name()
		if !e.Type().IsRegular() {
			continue
		}
		unused := !names[strings.TrimSuffix(name, ".kinds")]
		if strings.HasSuffix(name, ".tmp") {
			info, err := e.Info()
			unused = err == nil && info.ModTime().Before(now.Add(-time.Hour))
		}
		if unused {
			_ = os.Remove(filepath.Join(f.Dir, name))
		}
	}
}

// writeFile writes a small file for the daemon alone, through a temporary beside it.
func writeFile(path string, data []byte) error {
	tmp, err := os.CreateTemp(filepath.Dir(path), filepath.Base(path)+".*.tmp")
	if err != nil {
		return err
	}
	defer os.Remove(tmp.Name())
	if _, err = tmp.Write(data); err == nil {
		err = tmp.Close()
	} else {
		tmp.Close()
	}
	if err != nil {
		return err
	}
	return os.Rename(tmp.Name(), path)
}

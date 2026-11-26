// Package redact scrubs profile secrets, URL credentials/tokens, hex keys, and registered
// raycontrol node addresses from core logs and debug exports (BG-10).
// Kept in a subpackage free of gomobile bindings so both Android (libcore) and desktop (nikod) share it.
package redact

import (
	"io"
	"regexp"
	"sort"
	"strings"
	"sync/atomic"
)

var (
	// "key": "value" in JSON, the core's config and the app's dumps
	redactJSON = regexp.MustCompile(`(?i)"(password|uuid|private_key|pre_shared_key|psk|secret|token|auth_str|auth|` +
		`obfs_password|encryption_key|device_key|device_key_ref|seed|public_key_seed|private_key_hex|key_hex|` +
		`server_password|users_password|credentials?|port_key|short_id|dns_probe_key|probe_key|key)"(\s*:\s*)"[^"]*"`)
	// user:password@ in URLs and share links
	redactUserinfo = regexp.MustCompile(`([a-z][a-z0-9+.\-]*://)[^/@\s"']+@`)
	// secret query parameters of share links (niko-ygg carries the device's mesh seed after ';')
	redactQuery = regexp.MustCompile(`(?i)([?&](?:password|obfs-password|pbk|sid|pqv|auth|token|key|niko-dnskey|` +
		`niko-ygg|encryption|psk|seed)=)[^&\s"'#]*`)
	// URLs, for the token-like segments of their paths (subscription links of panels: /sub/<token>)
	redactURL = regexp.MustCompile(`[a-zA-Z][a-zA-Z0-9+.\-]*://[^\s"'<>]+`)
	// a path segment that looks like a credential: long, and mixing letters with digits
	tokenSegment = regexp.MustCompile(`^[A-Za-z0-9_\-=.~%]{16,}$`)
	longWord     = regexp.MustCompile(`^[A-Za-z0-9_]+$`)
	// hex of 16 digits and more: Yggdrasil's keys (the endpoint's own in full, ironwood's lines with the first 8 bytes
	// of a peer's), the device's and the nodes'; a run of digits alone (a time, a count) stays
	redactHex = regexp.MustCompile(`\b[0-9a-fA-F]{16,}\b`)
	// literal strings registered (node hosts), as one case-insensitive alternation, longest first
	redactLiterals atomic.Pointer[regexp.Regexp]
)

// SetLiterals sets the literal strings left out of the log from now on (node hosts), one per line.
func SetLiterals(list string) {
	SetLiteralsSlice(strings.Split(list, "\n"))
}

// SetLiteralsSlice sets the literal strings left out of the log from now on (deduplicated, case-insensitive).
func SetLiteralsSlice(items []string) {
	seen := make(map[string]struct{}, len(items))
	var out []string
	for _, raw := range items {
		s := strings.TrimSpace(raw)
		if len(s) < 3 {
			continue
		}
		key := strings.ToLower(s)
		if _, dup := seen[key]; dup {
			continue
		}
		seen[key] = struct{}{}
		out = append(out, s)
	}
	if len(out) == 0 {
		redactLiterals.Store(nil)
		return
	}
	// longest first: the alternation takes the first that matches, so a host that contains another goes whole
	sort.SliceStable(out, func(i, j int) bool { return len(out[i]) > len(out[j]) })
	quoted := make([]string, len(out))
	for i, s := range out {
		quoted[i] = regexp.QuoteMeta(s)
	}
	// matched on the text's own bytes: no case-folded copy whose offsets could differ (non-ASCII, bad UTF-8)
	redactLiterals.Store(regexp.MustCompile(`(?i)(?:` + strings.Join(quoted, "|") + `)`))
}

// Log returns text without secrets and the registered literals, for logs and debug exports.
func Log(text string) string {
	text = redactJSON.ReplaceAllString(text, `"$1"$2"<redacted>"`)
	// paths first: the userinfo placeholder would end the URL match early
	text = redactURL.ReplaceAllStringFunc(text, redactURLPath)
	text = redactUserinfo.ReplaceAllString(text, `$1<redacted>@`)
	text = redactQuery.ReplaceAllString(text, `$1<redacted>`)
	text = redactHex.ReplaceAllStringFunc(text, func(s string) string {
		if strings.IndexAny(s, "abcdefABCDEF") < 0 {
			return s
		}
		return "<key>"
	})
	if re := redactLiterals.Load(); re != nil {
		text = re.ReplaceAllLiteralString(text, "<node>")
	}
	return text
}

// redactURLPath replaces the token-like segments of a URL's path (a Go url.Error quotes the whole request URL).
func redactURLPath(u string) string {
	scheme := strings.Index(u, "://")
	slash := strings.IndexByte(u[scheme+3:], '/')
	if slash < 0 {
		return u
	}
	slash += scheme + 3
	path, rest := u[slash:], ""
	if i := strings.IndexAny(path, "?#"); i >= 0 {
		path, rest = path[:i], path[i:]
	}
	segments := strings.Split(path, "/")
	changed := false
	for i, segment := range segments {
		// letters with digits from 16 chars on, or one unbroken word of 24 and more (a token without digits);
		// names like geosite-category-ads-all.srs have separators and stay
		mixed := strings.ContainsAny(segment, "0123456789") &&
			strings.IndexFunc(segment, func(r rune) bool { return r >= 'A' && r <= 'z' && (r <= 'Z' || r >= 'a') }) >= 0
		if tokenSegment.MatchString(segment) && (mixed || len(segment) >= 24 && longWord.MatchString(segment)) {
			segments[i] = "<redacted>"
			changed = true
		}
	}
	if !changed {
		return u
	}
	return u[:slash] + strings.Join(segments, "/") + rest
}

// NewWriter wraps w so every written chunk passes through Log.
func NewWriter(w io.Writer) io.Writer {
	return redactingWriter{w: w}
}

type redactingWriter struct{ w io.Writer }

func (r redactingWriter) Write(p []byte) (int, error) {
	if _, err := r.w.Write([]byte(Log(string(p)))); err != nil {
		return 0, err
	}
	return len(p), nil
}

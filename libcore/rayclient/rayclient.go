// Package rayclient is the X-Raycontrol-Client header every request to a raycontrol node carries (raycontrol
// docs/spec/client-min-v1.md §1): which client and which version asks, so a node can tell an old one to update. A
// node before client-min-v1 does not read it.
package rayclient

import (
	"net/http"
	"regexp"
	"sync/atomic"
)

// Header is the header's name; the remote API (Connect) carries it as the metadata x-raycontrol-client.
const Header = "X-Raycontrol-Client"

// what the node reads (§1): a product, a semantic version without +build, then optionally " (platform; ABI)", which
// the node neither parses nor keeps; printable ASCII, 128 bytes at most
var grammar = regexp.MustCompile(`^[a-z][a-z0-9-]{0,15}/(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)` +
	`(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?( \([ -'*-~]*\))?$`)

var value atomic.Pointer[string]

// Set sets what the header says from now on. A value the node would not read sends no header (as a node takes an
// unreadable one: like none) and is refused.
func Set(v string) bool {
	if len(v) > 128 || !grammar.MatchString(v) {
		value.Store(nil)
		return false
	}
	value.Store(&v)
	return true
}

// Value is what the header says, "" while unset.
func Value() string {
	if v := value.Load(); v != nil {
		return *v
	}
	return ""
}

// Apply puts the header on a request to a node or one of its relays; nothing while unset.
func Apply(h http.Header) {
	if v := value.Load(); v != nil {
		h.Set(Header, *v)
	}
}

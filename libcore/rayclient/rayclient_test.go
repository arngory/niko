package rayclient

import (
	"net/http"
	"strings"
	"testing"
)

func TestSet(t *testing.T) {
	t.Cleanup(func() { value.Store(nil) })
	good := []string{
		"niko/1.59.0 (android; arm64-v8a)",
		"niko/2.0.0 (windows; amd64)",
		"niko/1.59.0-pre.20261120.1 (linux; amd64)",
		"niko/1.59.0",
		"niko/1.59.0-rc.1",
	}
	for _, v := range good {
		if !Set(v) || Value() != v {
			t.Errorf("Set(%q) refused", v)
		}
	}
	bad := []string{
		"",
		"Niko/1.59.0",                     // the product is lower case
		"niko/1.59",                       // three numbers
		"niko/01.59.0",                    // no leading zero
		"niko/1.59.0+build.1",             // no build metadata
		"niko/1.59.0 android",             // the platform in parentheses
		"niko/1.59.0 (android; arm64-v8a", // closed
		"niko/1.59.0 (андроид; arm64)",    // ASCII only
		"niko/1.59.0 (a(b); c)",           // no parentheses inside
		"niko/1.59.0 (" + strings.Repeat("x", 120) + ")", // 128 bytes at most
	}
	for _, v := range bad {
		if Set(v) || Value() != "" {
			t.Errorf("Set(%q) taken", v)
		}
	}
}

func TestApply(t *testing.T) {
	t.Cleanup(func() { value.Store(nil) })
	h := http.Header{}
	Apply(h)
	if _, ok := h[Header]; ok {
		t.Fatal("a header while unset")
	}
	Set("niko/1.59.0 (android; x86_64)")
	Apply(h)
	if h.Get(Header) != "niko/1.59.0 (android; x86_64)" {
		t.Fatalf("header %q", h.Get(Header))
	}
}

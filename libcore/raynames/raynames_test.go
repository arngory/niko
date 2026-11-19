package raynames

import (
	"context"
	"errors"
	"net/netip"
	"testing"
)

func stub(answer []string, err error) Lookup {
	return func(context.Context, string) ([]netip.Addr, error) {
		var out []netip.Addr
		for _, a := range answer {
			out = append(out, netip.MustParseAddr(a))
		}
		return out, err
	}
}

func addresses(targets []Target) []string {
	var out []string
	for _, t := range targets {
		out = append(out, t.Address)
	}
	return out
}

func equal(a, b []string) bool {
	if len(a) != len(b) {
		return false
	}
	for i := range a {
		if a[i] != b[i] {
			return false
		}
	}
	return true
}

func TestTargets(t *testing.T) {
	ctx := context.Background()
	known := []string{"203.0.113.7", "2001:db8::7"}
	cases := []struct {
		name    string
		host    string
		known   []string
		lookup  Lookup
		want    []string
		sni     string
		warning bool
	}{
		{"the node's address in the answer", "n3kq7p.example.com", known, stub([]string{"198.51.100.1", "2001:db8::7"}, nil), []string{"2001:db8::7"}, "n3kq7p.example.com", false},
		{"a forged answer", "n3kq7p.example.com", known, stub([]string{"198.51.100.1"}, nil), known, "n3kq7p.example.com", true},
		{"no answer", "n3kq7p.example.com", known, stub(nil, nil), known, "n3kq7p.example.com", true},
		{"a failed lookup", "n3kq7p.example.com", known, stub(nil, errors.New("SERVFAIL")), known, "n3kq7p.example.com", true},
		{"an address", "203.0.113.7", known, nil, []string{"203.0.113.7"}, "", false},
		{"a node without addresses", "node.example.com", nil, nil, []string{"node.example.com"}, "", false},
	}
	for _, c := range cases {
		targets, warning := Targets(ctx, c.lookup, c.host, c.known)
		if !equal(addresses(targets), c.want) || targets[0].ServerName != c.sni || (warning != "") != c.warning {
			t.Errorf("%s: %v %q, warning %q", c.name, targets, targets[0].ServerName, warning)
		}
	}
}

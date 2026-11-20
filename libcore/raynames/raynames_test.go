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
		targets, warning := Targets(ctx, c.lookup, c.host, c.known, false)
		if !equal(addresses(targets), c.want) || targets[0].ServerName != c.sni || (warning != "") != c.warning {
			t.Errorf("%s: %v %q, warning %q", c.name, targets, targets[0].ServerName, warning)
		}
	}
}

// TestTargetsV3: a node that takes SPA v3 and has a name alone is knocked at each address of its answer, named by
// it; without an answer the name is left, which the knock refuses (no v2 at a name, spa-v3 §6 p. 4). With signed
// addresses the answer is held to them as with v2.
func TestTargetsV3(t *testing.T) {
	ctx := context.Background()
	targets, warning := Targets(ctx, stub([]string{"198.51.100.1", "::ffff:198.51.100.2", "0.0.0.0"}, nil), "node.example.com", nil, true)
	if !equal(addresses(targets), []string{"198.51.100.1", "198.51.100.2"}) || targets[0].ServerName != "node.example.com" || warning != "" {
		t.Fatalf("v3, a name alone: %v %q", targets, warning)
	}
	// DNS answers: no v2 after v3 there
	if targets[0].Signed || targets[1].Signed {
		t.Fatal("a DNS answer signed")
	}
	targets, warning = Targets(ctx, stub(nil, errors.New("SERVFAIL")), "node.example.com", nil, true)
	if !equal(addresses(targets), []string{"node.example.com"}) || warning == "" {
		t.Fatalf("v3, no answer: %v %q", targets, warning)
	}
	targets, _ = Targets(ctx, stub([]string{"198.51.100.1"}, nil), "n3kq7p.example.com", []string{"203.0.113.7"}, true)
	if !equal(addresses(targets), []string{"203.0.113.7"}) || !targets[0].Signed {
		t.Fatalf("v3 with signed addresses: %v", targets)
	}
	targets, _ = Targets(ctx, nil, "203.0.113.7", nil, true)
	if !equal(addresses(targets), []string{"203.0.113.7"}) || !targets[0].Signed {
		t.Fatalf("v3, an address: %v", targets)
	}
}

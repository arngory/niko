package nikoservice

import (
	"context"
	"strings"
	"testing"
	"time"

	"libcore/dnsprobe"
	"libcore/nikoray"
)

func TestDNSResolverCacheProbesAndCachesPerNetwork(t *testing.T) {
	probeCalls := 0
	cache := newDNSResolverCache(func(ctx context.Context, resolver, zone string, key []byte, large bool) dnsprobe.Result {
		probeCalls++
		if resolver == "tcp://195.208.5.1" && zone == "spare.example.com" {
			return dnsprobe.Result{OK: true, Large: true, EDNS: 1232, RTTMs: 42}
		}
		return dnsprobe.Result{OK: false, Error: "timeout"}
	})
	netKey := "net-wifi-1"
	cache.networkKey = func() string { return netKey }
	cache.systemDNSFn = func() []string { return []string{"192.0.2.53"} }

	node := &nikoray.Node{
		SPA: nikoray.SPAParams{NodeID: "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"},
		Probe: &nikoray.ProbeInfo{
			DNSKey: "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef",
		},
		Hints: &nikoray.Hints{DNS: "tcp"},
	}

	now := time.Unix(1_700_000_000, 0)
	pick, ok := cache.ProbeNodeDnstt(context.Background(), node, "main.example.com", []string{"spare.example.com"}, now)
	if !ok {
		t.Fatal("expected probe to find working spare zone resolver")
	}
	if pick.Resolver != "tcp://195.208.5.1" || pick.Kind != "tcp" || pick.Zone != "spare.example.com" || !pick.Large {
		t.Fatalf("unexpected pick: %+v", pick)
	}
	if summary := cache.SummaryForNode(node.ID(), now); summary != "tcp" {
		t.Fatalf("SummaryForNode = %q, want tcp", summary)
	}

	// Second call within TTL hits cache without issuing new probes.
	before := probeCalls
	if _, ok := cache.ProbeNodeDnstt(context.Background(), node, "main.example.com", []string{"spare.example.com"}, now.Add(time.Hour)); !ok {
		t.Fatal("expected cached pick")
	}
	if probeCalls != before {
		t.Fatalf("expected cache hit, got %d extra probes", probeCalls-before)
	}

	// Switching network invalidates the cached pick for the new network.
	netKey = "net-lte-2"
	if _, ok := cache.Get("dnstt", node.ID(), now.Add(time.Hour)); ok {
		t.Fatal("expected cache miss on different network")
	}
}

func TestMasterdnsAutoResolversExcludesYandexUntilFallback(t *testing.T) {
	primary := masterdnsAutoResolvers([]string{"198.51.100.53"}, "ru", false)
	if !strings.Contains(primary, "198.51.100.53") || !strings.Contains(primary, "195.208.5.1") {
		t.Fatalf("missing primary resolvers: %q", primary)
	}
	if strings.Contains(primary, "77.88.8.8") {
		t.Fatalf("yandex should not be included without fallback: %q", primary)
	}
	fallback := masterdnsAutoResolvers([]string{"198.51.100.53"}, "ru", true)
	if !strings.Contains(fallback, "77.88.8.8") {
		t.Fatalf("yandex should join on fallback: %q", fallback)
	}
}

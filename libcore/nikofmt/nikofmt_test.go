package nikofmt

import (
	"go/build"
	"testing"
)

// A node leaves a DNS tunnel's resolver to the client (niko-dns=dnstt;;zone;key): the app takes its setting, which the
// golden fixtures cannot hold (the JVM test has no settings store), so the setting is checked here.
func TestResolverLeftToTheClient(t *testing.T) {
	const key = "1897511c59aa74b806c0a6f8b14bedb1bfed381bbde6001615d838ddc4255bab"
	for _, tc := range []struct {
		link     string
		settings Settings
		want     string
	}{
		{"vless://u@inner:1?niko-dns=dnstt%3B%3Bt.example.com%3B" + key + "&type=raw", DefaultSettings, "auto"},
		{"vless://u@inner:1?niko-dns=dnstt%3B%20%3Bt.example.com%3B" + key + "&type=raw",
			Settings{DnsttResolver: "https://doh.example.net/dns-query"}, "https://doh.example.net/dns-query"},
		{"vless://u@inner:1?niko-dns=masterdns%3B%3Bm.example.com%3B5&niko-dnskey=k&type=raw",
			Settings{MasterDnsResolvers: "198.51.100.53\n203.0.113.53"}, "198.51.100.53\n203.0.113.53"},
		{"vless://u@inner:1?niko-dns=masterdns%3B%20%2C%20%3Bm.example.com%3B5&niko-dnskey=k&type=raw",
			DefaultSettings, "auto"},
		{"vless://u@inner:1?niko-dns=slipstream%3B%3Bs.example.com%3B11&niko-slip=v1%3Bpin%3Balpn%3Bexample.com" +
			"&type=raw", Settings{MasterDnsResolvers: "198.51.100.53"}, "198.51.100.53"},
	} {
		bean, err := ParseLink(tc.link, tc.settings)
		if err != nil {
			t.Fatalf("%s: %v", tc.link, err)
		}
		if got := *bean.base().DnsTunnelResolver; got != tc.want {
			t.Errorf("%s: resolver %q, want %q", tc.link, got, tc.want)
		}
	}
}

// The package is for the desktop daemon: the root package libcore is gomobile's, and nikod imports none of it.
func TestNoRootPackage(t *testing.T) {
	pkg, err := build.ImportDir(".", 0)
	if err != nil {
		t.Fatal(err)
	}
	for _, imports := range [][]string{pkg.Imports, pkg.TestImports} {
		for _, path := range imports {
			if path == "libcore" {
				t.Errorf("imports the root package libcore")
			}
		}
	}
}

// Links of kinds this package does not read yet are refused as such, not taken for another kind.
func TestUnsupported(t *testing.T) {
	for _, link := range []string{"vmess://e30=", "trojan://pw@example.com:443", "sn://naive?abc", "NAIVE+https://h"} {
		if _, err := ParseLink(link, DefaultSettings); err != ErrUnsupported {
			t.Errorf("%s: %v, want ErrUnsupported", link, err)
		}
	}
}

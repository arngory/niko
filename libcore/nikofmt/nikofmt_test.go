package nikofmt

import (
	"go/build"
	"testing"
)

// A node leaves a DNS tunnel's resolver to the client (niko-dns=dnstt;;zone;key): the app takes its setting, which the
// golden fixtures cannot hold (the JVM test has no settings store), so the setting is checked here.
func TestResolverLeftToTheClient(t *testing.T) {
	// a stand-in for the node's public key: any 64 hex digits
	const key = "abababababababababababababababababababababababababababababababab"
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

// Links of kinds the app does not import are refused as such, not taken for another kind; so is sn:// so far.
func TestUnsupported(t *testing.T) {
	for _, link := range []string{"NAIVE+https://h", "ssh://root@example.com:22", "wireguard://key@example.com:51820",
		"juicity://uuid:pw@example.com:443", "Trojan://pw@example.com:443", " ss://YWVz@example.com:1",
		"sn://naive?abc"} {
		if _, err := ParseLink(link, DefaultSettings); err != ErrUnsupported {
			t.Errorf("%s: %v, want ErrUnsupported", link, err)
		}
	}
}

// The links parseProxies takes for a subscription's are told apart from those it drops.
func TestSubscriptionLinks(t *testing.T) {
	for _, link := range []string{"clash://install-config?url=https%3A%2F%2Fexample.com%2Fs", "sn://subscription?eNpj",
		"https://example.com/sub/token", "http://[::1", "https://example.com:0/"} {
		if _, err := ParseLink(link, DefaultSettings); err != ErrSubscriptionLink {
			t.Errorf("%s: %v, want ErrSubscriptionLink", link, err)
		}
	}
	// a line terminator is no '.' of the app's regex: not an http link at all
	if _, err := ParseLink("https://example.com/ ", DefaultSettings); err != ErrUnsupported {
		t.Errorf("a line separator: %v, want ErrUnsupported", err)
	}
}

// android.util.Base64.decode, as layoutlib's copy of it decodes (the app's Util.b64Decode and NGUtil.decode stand on
// it): anything outside the alphabet is skipped, padding only where it may stand, a lone last character refused.
func TestAndroidBase64(t *testing.T) {
	for _, tc := range []struct {
		in      string
		urlSafe bool
		want    string
		ok      bool
	}{
		{"aGVsbG8=", false, "hello", true},
		{"aGVsbG8", false, "hello", true},
		{"aGVs bG8\n", false, "hello", true},
		{"a.G-V_s!béG8", false, "hello", true}, // '-' and '_' are not the standard alphabet's: skipped
		{"aGVsbG8-_w==", false, "", false},     // so this ends in a lone 'w' before its padding
		{"aGVsbG8-_w==", true, "hello>\xff", true},
		{"aGVsbG8==", false, "", false}, // a second '=' where one ends it
		{"aGVsbA==", false, "hell", true},
		{"aGVsbA= =", false, "hell", true},
		{"aGVsbA==x", false, "", false},
		{"aGVsb", false, "", false},
		{"=aGVs", false, "", false},
		{"", false, "", true},
	} {
		got, err := androidBase64Decode(tc.in, tc.urlSafe)
		if (err == nil) != tc.ok || tc.ok && string(got) != tc.want {
			t.Errorf("%q (url-safe %v): %q, %v; want %q, ok %v", tc.in, tc.urlSafe, got, err, tc.want, tc.ok)
		}
	}
}

// Gson().fromJson of v2rayN's JSON: lenient inside the object, strict after it.
func TestGsonObject(t *testing.T) {
	for _, tc := range []struct {
		in   string
		want string // the value of "a", "<nil>" for null
		ok   bool
	}{
		{`{"a":"x"}`, "x", true},
		{")]}'\n{a:x}", "x", true},
		{`{a=>1.50,b:[1,,{c:[]}],}`, "", false}, // a trailing ',' asks for a name
		{`{a=>1.50,b:[1,,{c:[]}]}`, "1.50", true},
		{`{'a':TRUE;}`, "", false},
		{`{'a':TRUE;b:2}`, "true", true},
		{`{a:nulls}`, "nulls", true},
		{`{a:null}`, "<nil>", true},
		{`{a:"A\'\/"}`, "A'/", true},
		{`{a:"\x"}`, "", false},
		{`{a:{}}`, "", false}, // an object is no string
		{`{a:x} `, "x", true},
		{`{a:x} // a comment`, "", false},
		{`/* first */ {a:x}`, "x", true},
		{`{a:x}{}`, "", false},
		{`[]`, "", false},
		{` `, "", false},
	} {
		var a *string
		err := gsonReadObject(tc.in, func(name string) **string {
			if name == "a" {
				return &a
			}
			return nil
		})
		got := "<nil>"
		if a != nil {
			got = *a
		}
		if (err == nil) != tc.ok || tc.ok && got != tc.want {
			t.Errorf("%s: %q, %v; want %q, ok %v", tc.in, got, err, tc.want, tc.ok)
		}
	}
}

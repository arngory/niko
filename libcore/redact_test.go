package libcore

import (
	"strings"
	"testing"
)

func TestRedactLog(t *testing.T) {
	SetLogRedactions("node.example.org\n198.51.100.7")
	defer SetLogRedactions("")
	in := `{"type":"vless","server":"node.example.org","uuid":"11111111-2222","password": "hunter2","private_key":"abc",` +
		`"secret":"s3cr3t","encryption_key":"k"} dial tcp 198.51.100.7:443 vless://uuid@Node.Example.org:443?pbk=PUB&sid=01&sni=x ` +
		`hysteria2://pw@1.2.3.4:443/?obfs-password=OB&niko-ygg=tls%3A%2F%2Fa%3Bseed#n`
	out := RedactLog(in)
	for _, leak := range []string{"11111111-2222", "hunter2", `"abc"`, "s3cr3t", `"k"`, "node.example.org", "Node.Example.org",
		"198.51.100.7", "uuid@", "pw@", "PUB", "OB&", "seed"} {
		if strings.Contains(out, leak) {
			t.Fatalf("%q left in %s", leak, out)
		}
	}
	// what diagnosis needs stays
	for _, kept := range []string{`"type":"vless"`, "sni=x", "1.2.3.4:443", "dial tcp"} {
		if !strings.Contains(out, kept) {
			t.Fatalf("%q lost in %s", kept, out)
		}
	}
}

// TestRedactLiteralsNonASCII: text whose case folding changes byte lengths (Ⱥ grows, K and İ shrink, bad UTF-8)
// before a node host: the host goes, nothing panics, the rest stays as it was.
func TestRedactLiteralsNonASCII(t *testing.T) {
	SetLogRedactions("node.example.org\nexample.org")
	defer SetLogRedactions("")
	for _, prefix := range []string{"ȺȺȺȺȺȺȺȺ ", "KKK ", "İİİİ ", "\xff\xfe\xfd ", ""} {
		in := prefix + "dial NODE.Example.org:443 and example.org"
		out := RedactLog(in)
		if want := prefix + "dial <node>:443 and <node>"; out != want {
			t.Fatalf("%q -> %q, want %q", in, out, want)
		}
	}
	SetLogRedactions("")
	if out := RedactLog("node.example.org"); out != "node.example.org" {
		t.Fatalf("cleared list still redacts: %q", out)
	}
}

// TestRedactURLPathTokens: a subscription token in a URL path (an url.Error quotes the request URL) goes; ordinary
// paths stay.
func TestRedactURLPathTokens(t *testing.T) {
	for in, want := range map[string]string{
		`Get "https://panel.example/sub/aB3dE5fG7hJ9kL1mN": through the local proxy: EOF`: `Get "https://panel.example/sub/<redacted>": through the local proxy: EOF`,
		`https://h.example/api/v1/client/subscribe?token=x`:                               `https://h.example/api/v1/client/subscribe?token=<redacted>`,
		`https://github.com/SagerNet/sing-geosite/releases/download/20261101/geosite.db`:  `https://github.com/SagerNet/sing-geosite/releases/download/20261101/geosite.db`,
		`https://raw.githubusercontent.com/x/y/rule-set/geosite-category-ads-all.srs`:     `https://raw.githubusercontent.com/x/y/rule-set/geosite-category-ads-all.srs`,
		`https://n.example/dns-query/0f3a9c2b7d1e4f6a8b0c`:                                `https://n.example/dns-query/<redacted>`,
	} {
		if out := RedactLog(in); out != want {
			t.Errorf("%q -> %q, want %q", in, out, want)
		}
	}
}

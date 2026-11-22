package nikosub

import (
	"context"
	"encoding/base64"
	"errors"
	"math/big"
	"net"
	"strings"
	"testing"

	"libcore/nikofmt"
)

// SnakeYAML 1.33's implicit types, as its jar resolves and SafeConstructor builds them (the class and toString):
// what a Clash config's scalar is to the app.
func TestYAMLScalars(t *testing.T) {
	for value, want := range map[string]string{
		"443": "Integer 443", "0x1BB": "Integer 443", "0777": "Integer 511", "1_000": "Integer 1000",
		"1:30": "Integer 90", "1e3": "Double 1000.0", "1.5": "Double 1.5", ".5": "Double 0.5", "yes": "Boolean true",
		"No": "Boolean false", "on": "Boolean true", "~": "null", "null": "null", "": "null",
		"0b101": "Integer 5", "+12": "Integer 12", "-0": "Integer 0", "012345678": "String 012345678",
		"1.0e+3": "Double 1000.0", "1E3": "Double 1000.0", ".inf": "Double Infinity", ".NaN": "Double NaN",
		"1:30.5": "Double 90.5", "0_": "String 0_", "100 Mbps": "String 100 Mbps", "3000000000": "Long 3000000000",
		"99999999999999999999": "BigInteger 99999999999999999999", "1.0e7": "Double 1.0E7",
		"0.0001": "Double 1.0E-4", "123456789.5": "Double 1.234567895E8", "-.5": "Double -0.5", "y": "String y",
		"n": "String n", "TRUE": "Boolean true", "0o17": "String 0o17", "1.": "Double 1.0", "_1": "String _1",
		"+.5": "Double 0.5", "0x_1F": "Integer 31", "1__2": "Integer 12",
	} {
		m, err := loadClashYAML("k: " + value)
		if err != nil {
			t.Errorf("%q: %v", value, err)
			continue
		}
		v := m.get("k")
		var got string
		switch x := v.(type) {
		case nil:
			got = "null"
		case string:
			got = "String " + x
		case bool:
			got = "Boolean " + javaToString(x)
		case float64:
			got = "Double " + javaToString(x)
		case *big.Int:
			switch {
			case x.IsInt64() && x.Int64() == int64(int32(x.Int64())):
				got = "Integer " + x.String()
			case x.IsInt64():
				got = "Long " + x.String()
			default:
				got = "BigInteger " + x.String()
			}
		default:
			got = javaToString(x)
		}
		if got != want {
			t.Errorf("%q: got %s, want %s", value, got, want)
		}
	}
}

// Mappings as SafeConstructor builds them: a duplicate's last value where the first was, merge keys, local tags.
func TestYAMLMappings(t *testing.T) {
	for doc, want := range map[string]string{
		"a: 1\na: 2\nb: 3": "{a=2, b=3}",
		"x: &d {p: 1, q: 2}\ny:\n  r: 0\n  <<: *d\n  p: 9": "{x={p=1, q=2}, y={r=0, p=9, q=2}}",
		"y:\n  <<: [{a: 1}, {a: 2, b: 3}]\n  c: 4":         "{y={a=1, b=3, c=4}}",
		"k: !str 123":         "{k=123}",
		"k: !!str 123":        "{k=123}",
		"k: 'yes'":            "{k=yes}",
		"k: !!int '12'":       "{k=12}",
		"k: [1, 2]":           "{k=[1, 2]}",
		"k: {a: b}":           "{k={a=b}}",
		"a: 1\nb: 2\na: 3":    "{b=2, a=3}",
		"x: &a [1]\ny: *a":    "{x=[1], y=[1]}",
		"k: \"<<\"":           "{k=<<}",
		"k: 2024-01-01\nl: 1": "{k=" + javaToString(mustTime(t, "2024-01-01")) + ", l=1}",
		"proxies:\n  - {name: a, port: 443, password: !str 123}\n": "{proxies=[{name=a, port=443, password=123}]}",
	} {
		m, err := loadClashYAML(doc)
		if err != nil {
			t.Errorf("%q: %v", doc, err)
			continue
		}
		if got := javaToString(m); got != want {
			t.Errorf("%q: got %s, want %s", doc, got, want)
		}
	}
	// a YAMLException: the app reads the text otherwise
	refused := []string{"--- a\n--- b", "proxies:\n  - !!java.net.URL [\"http://example.com\"]\n",
		"proxies: !!java.util.Date 2024-01-01\n", "- a\n- b", "", "k: *nothing", "a: [unclosed"}
	// fifty-one aliases of a collection
	var many strings.Builder
	many.WriteString("x: &x [1]\ny: [")
	for i := 0; i < 51; i++ {
		many.WriteString("*x, ")
	}
	many.WriteString("]")
	refused = append(refused, many.String())
	// fifty-two nested collections
	refused = append(refused, "k: "+strings.Repeat("[", 52)+strings.Repeat("]", 52))
	for _, doc := range refused {
		if _, err := loadClashYAML(doc); !errors.Is(err, errYAML) {
			t.Errorf("%q: %v", doc, err)
		}
	}
	if _, err := loadClashYAML("k: " + strings.Repeat("[", 50) + strings.Repeat("]", 50)); err != nil {
		t.Errorf("fifty nested: %v", err)
	}
	// what SnakeYAML throws besides a YAMLException fails the update
	var fatal *fatalYAML
	if _, err := loadClashYAML("k: !!int abc"); !errors.As(err, &fatal) {
		t.Errorf("!!int abc: %v", err)
	}
}

func mustTime(t *testing.T, s string) any {
	v, err := constructTimestamp(s)
	if err != nil {
		t.Fatal(err)
	}
	return v
}

// ini4j's Ini as the app reads a .conf with it: sections of one name are one, an option's last value, escapes and
// continued lines; what it refuses.
func TestINI(t *testing.T) {
	f, err := parseINI("[Interface]\nAddress = 10.0.0.2/32\nAddress = fd00::2/128, 10.1.1.1/32\nPrivateKey = aaa=\n" +
		"Jc = 4 ; comment\nMTU =\n# c\n; c2\n[Peer]\nPublicKey = p1=\nEndpoint = 1.2.3.4:51820\n[Peer]\n" +
		"PublicKey = p2=\nEndpoint = [2001:db8::1]:51820\nPresharedKey = psk\\\\x\n  [ Other ]  \nA = a\\\n  b\nB:C=D\n" +
		"E=\\u0041\\t\\x\\\\")
	if err != nil {
		t.Fatal(err)
	}
	iface, peer, other := f.sections["Interface"], f.sections["Peer"], f.sections["Other"]
	if strings.Join(iface.keys, ",") != "Address,PrivateKey,Jc,MTU" || len(iface.values["Address"]) != 2 {
		t.Errorf("interface: %+v", iface)
	}
	if v, _ := iface.get("Jc"); v != "4 ; comment" {
		t.Errorf("Jc: %q", v)
	}
	if v, ok := iface.get("MTU"); !ok || v != "" {
		t.Errorf("MTU: %q %v", v, ok)
	}
	if v, _ := peer.get("Endpoint"); v != "[2001:db8::1]:51820" || len(peer.values["Endpoint"]) != 2 {
		t.Errorf("peer: %+v", peer)
	}
	if v, _ := peer.get("PresharedKey"); v != `psk\x` {
		t.Errorf("psk: %q", v)
	}
	if a, _ := other.get("A"); a != "ab" {
		t.Errorf("A: %q", a)
	}
	if b, _ := other.get("B"); b != "C=D" {
		t.Errorf("B: %q", b)
	}
	if e, _ := other.get("E"); e != "A\tx\\" {
		t.Errorf("E: %q", e)
	}
	for _, bad := range []string{"\ufeff[I]\nA=1", "A=1\n[I]\nB=2", "[I]\n=1", "[I] ;c\nA=1", "[I]\nfoo"} {
		if _, err := parseINI(bad); err == nil {
			t.Errorf("%q: read", bad)
		}
	}
}

// SubscriptionHeadersTest's vectors.
func TestHeaders(t *testing.T) {
	if SpareAfterAnswer("https://a.example/s", "https://b.example/s", false) != "https://b.example/s" ||
		SpareAfterAnswer("https://a.example/s", "", false) != "" ||
		SpareAfterAnswer("https://a.example/s", "", true) != "https://a.example/s" {
		t.Error("spare")
	}
	if DecodeHeader(" My VPN ") != "My VPN" || DecodeHeader("base64:0JzQvtC5IFZQTg==") != "Мой VPN" ||
		DecodeHeader("") != "" || DecodeHeader("base64:@@@") != "" || DecodeHeader("BASE64:TmlrbyE") != "Niko!" {
		t.Error("text headers")
	}
	if m, ok := UpdateIntervalMinutes("12"); !ok || m != 720 {
		t.Error("interval")
	}
	for _, bad := range []string{"0", "soon", "", "721"} {
		if _, ok := UpdateIntervalMinutes(bad); ok {
			t.Errorf("interval %q", bad)
		}
	}
	usage, ok := ParseUserInfo("upload=100; download=900; total=10000; expire=2000000000")
	if !ok || usage.Used != 1000 || usage.Remaining() != 9000 || usage.Expire != 2000000000 ||
		usage.RunningOut(1000000000, 3, 5) || !usage.RunningOut(2000000000-3600, 3, 5) {
		t.Errorf("usage %+v", usage)
	}
	if u, _ := ParseUserInfo("upload=0;download=9600;total=10000"); !u.RunningOut(0, 3, 5) {
		t.Error("almost used")
	}
	if u, _ := ParseUserInfo("reupload=7; download=5"); u.Used != 5 {
		t.Error("reupload")
	}
	if _, ok := ParseUserInfo("upload=0; download=0; total=0; expire=0"); ok {
		t.Error("zeros")
	}
	a := DeviceID("secret", "https://a.example/sub/1")
	if len(a) != 32 || a != DeviceID("secret", "https://A.example/sub/other") ||
		a == DeviceID("secret", "https://b.example/sub/1") || a == DeviceID("other", "https://a.example/sub/1") {
		t.Error("device id")
	}
	headers := func(pairs ...string) func(string) string {
		return func(name string) string {
			for i := 0; i+1 < len(pairs); i += 2 {
				if pairs[i] == name {
					return pairs[i+1]
				}
			}
			return ""
		}
	}
	if RefusalOf(headers("x-hwid-max-devices-reached", " TRUE ")) != LimitReached ||
		RefusalOf(headers("x-hwid-limit", "true")) != LimitReached ||
		RefusalOf(headers("x-hwid-not-supported", "true", "x-hwid-active", "true")) != IDRequired ||
		RefusalOf(headers("x-hwid-active", "true", "x-hwid-limit", "false")) != NoRefusal ||
		refusalOfError("HTTP 404 Not Found [x-hwid-not-supported]: ") != IDRequired ||
		refusalOfError("HTTP 404 Not Found [x-hwid-max-devices-reached] [x-hwid-limit]: x") != LimitReached ||
		refusalOfError("HTTP 404 Not Found: x-hwid-not-supported in the body") != NoRefusal {
		t.Error("refusals")
	}
	current := "https://panel.example/sub/abc?x=1"
	none := func(string) string { return "" }
	if m := MoveOf(current, headers("new-url", " https://new.example/s/abc "), ""); m.To != "https://new.example/s/abc" || m.Spare != "" {
		t.Errorf("by header %+v", m)
	}
	if m := MoveOf("https://panel.example:8443/sub/abc?x=1", headers("new-domain", "Mirror.Example."), ""); m.To != "https://mirror.example:8443/sub/abc?x=1" {
		t.Errorf("new-domain %+v", m)
	}
	if m := MoveOf(current, headers("new-url", "https://new.example/s", "new-domain", "other.example"), ""); m.To != "https://new.example/s" {
		t.Errorf("new-url wins %+v", m)
	}
	body := "#new-url: https://body.example/s\n#fallback-url: https://spare.example/s\nvless://x@1.2.3.4:443#n\n"
	if m := MoveOf(current, none, body); m.To != "https://body.example/s" || m.Spare != "https://spare.example/s" {
		t.Errorf("body %+v", m)
	}
	if m := MoveOf(current, none, base64.StdEncoding.EncodeToString([]byte(body))); m.Spare != "https://spare.example/s" {
		t.Errorf("base64 body %+v", m)
	}
	if m := MoveOf(current, headers("new-url", "https://h.example/s"), body); m.To != "https://h.example/s" {
		t.Errorf("header over body %+v", m)
	}
	if m := MoveOf(current, headers("new-url", current), ""); m.To != "" {
		t.Errorf("same %+v", m)
	}
	https := "https://panel.example/s"
	if SubscriptionAddress(https, "http://new.example/s") != "" ||
		SubscriptionAddress("http://panel.example/s", "https://new.example/s") != "https://new.example/s" ||
		SubscriptionAddress("http://panel.example/s", "http://new.example/s") != "http://new.example/s" {
		t.Error("schemes")
	}
	for _, bad := range []string{"content://x/y", "javascript:alert(1)", "https://user:pass@new.example/s", "/sub/abc",
		"https:///s", "https://new.example/" + strings.Repeat("a", 2100), "not a url"} {
		if SubscriptionAddress(https, bad) != "" {
			t.Errorf("address %q", bad)
		}
	}
	for _, bad := range []string{"new.example/evil", "new.example:80", "https://new.example", "localhost", "a b.example"} {
		if withDomain(https, bad) != "" {
			t.Errorf("domain %q", bad)
		}
	}
	if withDomain(https, "xn--80ak6aa92e.example") != "https://xn--80ak6aa92e.example/s" {
		t.Error("punycode domain")
	}
	if name, ok := decodeFilename("attachment; filename*=UTF-8''%D0%9C%D0%BE%D1%8F+sub.yaml"); !ok || name != "Моя sub.yaml" {
		t.Errorf("filename %q", name)
	}
	if _, ok := decodeFilename("attachment; filename*=UTF-8''%zz"); ok {
		t.Error("a bad escape decoded")
	}
}

// SubscriptionLinksTest's vectors.
func TestDeepLinks(t *testing.T) {
	url := "https://example.com/sub/abc?token=1&x=2"
	encoded := "https%3A%2F%2Fexample.com%2Fsub%2Fabc%3Ftoken%3D1%26x%3D2"
	for link, want := range map[string]DeepLink{
		"sing-box://import-remote-profile?url=" + encoded + "#My%20VPN": {url, "My VPN"},
		"clashmeta://install-config?url=" + encoded + "&name=My%20VPN":  {url, "My VPN"},
		"v2rayng://install-sub?url=" + encoded:                          {url, ""},
		"hiddify://import/" + url + "#My%20VPN":                         {url, "My VPN"},
		"hiddify://import/" + encoded:                                   {url, ""},
		"sn://subscription?url=" + encoded + "&name=n":                  {url, "n"},
		"clash://install-config?url=" + encoded:                         {url, ""},
	} {
		if got, ok := ParseDeepLink(link); !ok || got != want {
			t.Errorf("%s: %+v %v", link, got, ok)
		}
	}
	for _, bad := range []string{"sn://subscription?eJxLz..", "sing-box://import-remote-profile?url=file%3A%2F%2F%2Fsdcard%2Fa",
		"vless://uuid@host:443", "not a link"} {
		if got, ok := ParseDeepLink(bad); ok {
			t.Errorf("%s: %+v", bad, got)
		}
	}
}

type fakeResolver map[string][]string

func (f fakeResolver) LookupIPAddr(_ context.Context, host string) ([]net.IPAddr, error) {
	var out []net.IPAddr
	for _, a := range f[host] {
		out = append(out, net.IPAddr{IP: net.ParseIP(a)})
	}
	if out == nil {
		return nil, errors.New("no such host")
	}
	return out, nil
}

// forceResolve: the first address in the app's order (IPv6 first, IPv4 first where IPv6 is preferred), the name kept
// as the SNI of a TLS profile without one; naive and addresses left alone, a name that does not resolve too.
func TestResolve(t *testing.T) {
	beans := func() []nikofmt.Bean {
		var out []nikofmt.Bean
		for _, link := range []string{
			"trojan://p@t.example.com:443#t",
			"socks5://s.example.com:1080#s",
			"naive+https://u:p@n.example.com:443#n",
			"socks5://192.0.2.1:1080#ip",
			"socks5://gone.example.com:1080#gone",
		} {
			b, err := nikofmt.ParseLink(link, nikofmt.DefaultSettings)
			if err != nil {
				t.Fatal(err)
			}
			out = append(out, b)
		}
		return out
	}
	r := fakeResolver{"t.example.com": {"192.0.2.10", "2001:db8::10"}, "s.example.com": {"192.0.2.11"},
		"n.example.com": {"192.0.2.12"}}
	for _, ipv6First := range []bool{false, true} {
		list := beans()
		Resolve(context.Background(), r, list, ipv6First)
		want := "2001:db8::10"
		if ipv6First {
			want = "192.0.2.10"
		}
		trojan := list[0].(*nikofmt.TrojanBean)
		if *trojan.ServerAddress != want || *trojan.Sni != "t.example.com" {
			t.Errorf("ipv6First %v: %s %s", ipv6First, *trojan.ServerAddress, *trojan.Sni)
		}
		if *nikofmt.Base(list[1]).ServerAddress != "192.0.2.11" || *nikofmt.Base(list[2]).ServerAddress != "n.example.com" ||
			*nikofmt.Base(list[3]).ServerAddress != "192.0.2.1" || *nikofmt.Base(list[4]).ServerAddress != "gone.example.com" {
			t.Errorf("ipv6First %v: others", ipv6First)
		}
	}
}

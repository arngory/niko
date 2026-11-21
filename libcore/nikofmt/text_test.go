package nikofmt

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"
)

// goldenBeans are the golden links the app makes a profile of, with that profile's JSON, in their files' order.
func goldenBeans(t *testing.T) (links []string, beans []string) {
	t.Helper()
	txts, err := filepath.Glob(filepath.Join(goldenLinks, "*.txt"))
	if err != nil || len(txts) == 0 {
		t.Fatalf("no links in %s: %v", goldenLinks, err)
	}
	sort.Strings(txts)
	for _, txt := range txts {
		raw, err := os.ReadFile(txt)
		if err != nil {
			t.Fatal(err)
		}
		want, err := os.ReadFile(strings.TrimSuffix(txt, ".txt") + ".json")
		if err != nil {
			t.Fatal(err)
		}
		if strings.TrimSpace(string(want)) == "null" {
			continue
		}
		links = append(links, ktTrim(javaUTF8(raw)))
		beans = append(beans, string(want))
	}
	return links, beans
}

// The golden links in one text, a line each, with blank lines and spaces around: every profile, in order, the same
// as each link alone gives.
func TestParseTextGolden(t *testing.T) {
	links, want := goldenBeans(t)
	text := "\r\n  " + strings.Join(links, " \r\n\n\t") + "\n"
	beans, err := ParseText(text, DefaultSettings)
	if err != nil {
		t.Fatal(err)
	}
	if len(beans) != len(want) {
		t.Fatalf("%d profiles of %d links", len(beans), len(want))
	}
	for i, bean := range beans {
		got, err := json.Marshal(bean)
		if err != nil {
			t.Fatal(err)
		}
		if diff := jsonDifferences(t, []byte(want[i]), got); diff != "" {
			t.Errorf("%s\n%s", links[i], diff)
		}
	}
}

func TestParseText(t *testing.T) {
	const ss = "ss://YWVzLTI1Ni1nY206cGFzcw@192.0.2.1:8388#one"
	const socks = "socks5://198.51.100.2:1080#two"
	// two links on a line: the words give two, the line none
	beans, err := ParseText(ss+" "+socks, DefaultSettings)
	if err != nil || len(beans) != 2 || TypeName(beans[0]) != "ss" || TypeName(beans[1]) != "socks" {
		t.Fatalf("two on a line: %v %v", beans, err)
	}
	// a name with a space, unencoded: the line reads it whole, the words cut it; one each, the line's taken
	beans, err = ParseText("socks5://198.51.100.2:1080#my server", DefaultSettings)
	if err != nil || len(beans) != 1 || DisplayName(beans[0]) != "my server" {
		t.Fatalf("a name with a space: %v %v", beans, err)
	}
	// nothing the import reads
	beans, err = ParseText("hello\nwireguard://x\n\n", DefaultSettings)
	if err != nil || len(beans) != 0 {
		t.Fatalf("nothing: %v %v", beans, err)
	}
	// a subscription's link anywhere: the subscription, no profile
	for _, link := range []string{
		"sn://subscription?url=https%3A%2F%2Fsub.example%2Fs&name=x",
		"clash://install-config?url=https%3A%2F%2Fsub.example%2Fs",
		"https://sub.example/s/0123456789abcdef",
	} {
		beans, err = ParseText(ss+"\n"+link+"\n"+socks, DefaultSettings)
		var found *SubscriptionFound
		if !errors.As(err, &found) || found.Link != link || !errors.Is(err, ErrSubscriptionLink) || beans != nil {
			t.Errorf("%s: %v %v", link, beans, err)
		}
		if err != nil && strings.Contains(err.Error(), "sub.example") {
			t.Errorf("the error carries the link: %v", err)
		}
	}
}

// Each golden profile's JSON read back as its type: the same bean, and the type's name the one its link has.
func TestDecodeBean(t *testing.T) {
	links, want := goldenBeans(t)
	for i, link := range links {
		bean, err := ParseLink(link, DefaultSettings)
		if err != nil {
			t.Fatal(err)
		}
		typ := TypeName(bean)
		if typ == "" {
			t.Fatalf("%s: no type name for %T", link, bean)
		}
		if strings.HasPrefix(link, "sn://") && !strings.HasPrefix(link, "sn://"+typ) {
			t.Errorf("%s: type %s", link, typ)
		}
		again, err := DecodeBean(typ, []byte(want[i]))
		if err != nil {
			t.Fatalf("%s: %v", link, err)
		}
		got, _ := json.Marshal(again)
		if diff := jsonDifferences(t, []byte(want[i]), got); diff != "" {
			t.Errorf("%s\n%s", link, diff)
		}
	}
	for _, c := range []struct{ typ, json string }{
		{"socks", `{"serverAddress": "192.0.2.1", "nosuchfield": 1}`},
		{"socks", `{"serverPort": "1080"}`},
		{"socks", `{"serverPort": 1080} {}`},
		{"socks", `[]`},
		{"socks", ``},
		{"neko", `{}`},
		{"vless", `{}`},
	} {
		if _, err := DecodeBean(c.typ, []byte(c.json)); err == nil {
			t.Errorf("%s %s: no error", c.typ, c.json)
		}
	}
	// null fields and a blank address: the defaults
	bean, err := DecodeBean("naive", []byte(`{"serverAddress": " ", "password": null}`))
	if err != nil {
		t.Fatal(err)
	}
	naive := bean.(*NaiveBean)
	if *naive.ServerAddress != "127.0.0.1" || *naive.ServerPort != 443 || *naive.Password != "" {
		t.Fatalf("%+v", naive)
	}
}

func TestDisplay(t *testing.T) {
	for _, c := range []struct {
		bean          Bean
		name, address string
	}{
		{&SOCKSBean{AbstractBean: AbstractBean{ServerAddress: ptr("192.0.2.1"), ServerPort: ptr(1080)}}, "192.0.2.1:1080", ""},
		{&SOCKSBean{AbstractBean: AbstractBean{ServerAddress: ptr("2001:db8::1"), ServerPort: ptr(1080), Name: ptr(" ")}}, "[2001:db8::1]:1080", ""},
		{&SOCKSBean{AbstractBean: AbstractBean{ServerAddress: ptr("[2001:db8::1]"), ServerPort: ptr(443), Name: ptr("home")}}, "home", "[2001:db8::1]:443"},
		{&SOCKSBean{AbstractBean: AbstractBean{ServerAddress: ptr("host.example")}}, "host.example:null", ""},
		{&HysteriaBean{ServerPorts: ptr("443,8443-8450"), AbstractBean: AbstractBean{ServerAddress: ptr("hy.example"), ServerPort: ptr(1080)}}, "hy.example:443,8443-8450", ""},
	} {
		address := c.address
		if address == "" {
			address = c.name
		}
		if got := DisplayName(c.bean); got != c.name {
			t.Errorf("name %q, want %q", got, c.name)
		}
		if got := DisplayAddress(c.bean); got != address {
			t.Errorf("address %q, want %q", got, address)
		}
	}
	for _, typ := range typeMap {
		bean, err := NewBean(typ)
		if typ == "neko" {
			if !errors.Is(err, ErrUnsupported) {
				t.Errorf("neko: %v", err)
			}
			continue
		}
		if err != nil || TypeName(bean) != typ {
			t.Errorf("%s: %T %v", typ, bean, err)
		}
	}
}

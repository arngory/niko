package nikosub

import (
	"errors"
	"net/url"
	"strconv"
	"strings"
	"testing"
	"time"

	"libcore/nikofmt"
)

// A provider's body is anyone's: what makes the JVM overflow its stack fails the phone's update (a Throwable caught),
// in Go it would end the daemon. Each of these is refused, and quickly.
func TestHostileBodies(t *testing.T) {
	var laughs strings.Builder
	laughs.WriteString("proxies: []\nl0: &l0 {k: v}\n")
	for i := 1; i <= 12; i++ {
		laughs.WriteString("l" + itoa(i) + ": &l" + itoa(i) + " {<<: [")
		for j := 0; j < 10; j++ {
			if j > 0 {
				laughs.WriteString(", ")
			}
			laughs.WriteString("*l" + itoa(i-1))
		}
		laughs.WriteString("]}\n")
	}
	for name, body := range map[string]string{
		"a mapping merged into itself": "proxies: []\nx: &a\n  k: v\n  <<: [*a]\n",
		"merged into itself, one away": "proxies: []\nx: &a\n  y: &b\n    <<: *a\n  <<: *b\n",
		"merges that multiply":         laughs.String(),
		"arrays without end":           strings.Repeat("[", 1<<20),
		"objects without end":          strings.Repeat(`{"a":`, 1<<18),
	} {
		start := time.Now()
		_, err := Parse(body, "", nikofmt.Settings{})
		if took := time.Since(start); took > 5*time.Second {
			t.Errorf("%s: %v to refuse", name, took)
		}
		if strings.HasPrefix(name, "a mapping") || strings.HasPrefix(name, "merge") {
			if err == nil {
				t.Errorf("%s: parsed", name)
			}
		}
	}
	// the merges SnakeYAML takes still go through
	m, err := loadClashYAML("base: &b {port: 1}\nx:\n  <<: [*b]\n  host: h\n")
	if err != nil {
		t.Fatal(err)
	}
	x, _ := m.get("x").(*yamlMap)
	if x == nil || !x.has("port") || !x.has("host") {
		t.Errorf("a plain merge: %#v", m.get("x"))
	}
}

func itoa(i int) string {
	return string(rune('0'+i/10)) + string(rune('0'+i%10))
}

// Many profiles of one name are named as the app's loop names them, in linear time.
func TestPrepareNamesMany(t *testing.T) {
	text := strings.Repeat("socks5://192.0.2.1:1080#a\n", 20000) + "socks5://192.0.2.1:1080#a (3)\nsocks5://192.0.2.1:1080#a (1)\n"
	beans, err := nikofmt.ParseText(text, nikofmt.Settings{})
	if err != nil {
		t.Fatal(err)
	}
	// the app's loop as it is, on a small list, against the fast one
	slow := func(names []string) []string {
		taken := map[string]bool{}
		var out []string
		for _, n := range names {
			index := 0
			name := n
			for taken[name] {
				index++
				name = strings.ReplaceAll(name, " ("+itoa2(index-1)+")", "") + " (" + itoa2(index) + ")"
			}
			taken[name] = true
			out = append(out, name)
		}
		return out
	}
	small := []string{"a", "a", "a (1)", "a", "a (2)", "b", "a", "b", "a (1)"}
	var smallText strings.Builder
	for _, n := range small {
		smallText.WriteString("socks5://192.0.2.1:1080#" + strings.ReplaceAll(n, " ", "%20") + "\n")
	}
	smallBeans, err := nikofmt.ParseText(smallText.String(), nikofmt.Settings{})
	if err != nil {
		t.Fatal(err)
	}
	var got []string
	for _, b := range Prepare(smallBeans) {
		got = append(got, nikofmt.DisplayName(b))
	}
	if want := slow(small); strings.Join(got, "|") != strings.Join(want, "|") {
		t.Fatalf("names %q, the app's loop %q", got, want)
	}
	start := time.Now()
	out := Prepare(beans)
	if took := time.Since(start); took > 3*time.Second {
		t.Fatalf("%v for %d profiles", took, len(out))
	}
	if n := nikofmt.DisplayName(out[len(out)-3]); n != "a (19999)" {
		t.Fatalf("the last of one name: %q", n)
	}
}

func itoa2(i int) string { return strconv.Itoa(i) }

// An answer that failed says why, not where: the address's path is the subscription's token.
func TestFetchErrorHidesTheAddress(t *testing.T) {
	err := &FetchError{Err: &url.Error{Op: "Get", URL: "https://example.com/s/fake-token", Err: errors.New("refused")}}
	if strings.Contains(err.Error(), "fake-token") || !strings.Contains(err.Error(), "refused") {
		t.Fatal(err)
	}
}

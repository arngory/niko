package nikoconf

import (
	"bytes"
	"encoding/json"
	"errors"
	"math/big"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"

	"libcore/nikofmt"
)

// The golden fixtures from this package's directory (docs/design/desktop-stage3.md §5): the links, and the outbounds
// the app built of their beans (app/src/test/java/io/niko/golden/OutboundsGoldenTest.kt writes them).
const (
	goldenLinks     = "../../shared/golden/links"
	goldenOutbounds = "../../shared/golden/outbounds"
)

// Each golden link, read as nikofmt's golden tests read it, must give the app's outbound, compared by meaning
// (numbers as numbers, keys in any order); a link with no outbound must need a plugin. A link the parser drops has
// no profile, so no outbound.
func TestGoldenOutbounds(t *testing.T) {
	txts, err := filepath.Glob(filepath.Join(goldenLinks, "*.txt"))
	if err != nil || len(txts) == 0 {
		t.Fatalf("no links in %s: %v", goldenLinks, err)
	}
	sort.Strings(txts)
	expected := map[string]bool{}
	for _, txt := range txts {
		name := strings.TrimSuffix(filepath.Base(txt), ".txt")
		raw, err := os.ReadFile(txt)
		if err != nil {
			t.Fatal(err)
		}
		// as Kotlin's readText().trim()
		bean, err := nikofmt.ParseLink(nikofmt.KtTrim(nikofmt.DecodeUTF8(raw)), nikofmt.DefaultSettings)
		if err != nil {
			continue // dropped: no profile
		}
		expected[name+".json"] = true
		t.Run(name, func(t *testing.T) {
			want, readErr := os.ReadFile(filepath.Join(goldenOutbounds, name+".json"))
			got, err := Outbound(bean, false)
			if readErr != nil {
				if !os.IsNotExist(readErr) {
					t.Fatal(readErr)
				}
				if !errors.Is(err, ErrNeedsPlugin) {
					t.Fatalf("no fixture, so the app needs a plugin for it; got %v, %v", got, err)
				}
				return
			}
			if err != nil {
				t.Fatalf("Outbound: %v\nthe app's: %s", err, want)
			}
			checkTypes(t, "", got)
			gotJSON, err := json.MarshalIndent(got, "", "  ")
			if err != nil {
				t.Fatal(err)
			}
			if diff := jsonDifferences(t, want, gotJSON); diff != "" {
				t.Errorf("%s\nGo's outbound:\n%s", diff, gotJSON)
			}
		})
	}
	jsons, _ := filepath.Glob(filepath.Join(goldenOutbounds, "*.json"))
	for _, j := range jsons {
		if name := filepath.Base(j); !expected[name] {
			t.Errorf("%s: no profile of a golden link for it", name)
		}
	}
}

// checkTypes: the values Outbound documents, nothing else (an int or a map[string]string would compare equal as JSON).
func checkTypes(t *testing.T, path string, v any) {
	t.Helper()
	switch v := v.(type) {
	case map[string]any:
		for k, e := range v {
			checkTypes(t, path+"."+k, e)
		}
	case []any:
		for _, e := range v {
			checkTypes(t, path+"[]", e)
		}
	case string, bool, int64, float64:
	default:
		t.Errorf("%s: a %T", path, v)
	}
}

// "Allow insecure" of the settings makes a TLS outbound insecure where its profile is not.
func TestGlobalInsecure(t *testing.T) {
	bean, err := nikofmt.ParseLink("trojan://pass@example.com:443?sni=a.example.com#t", nikofmt.DefaultSettings)
	if err != nil {
		t.Fatal(err)
	}
	for _, global := range []bool{false, true} {
		out, err := Outbound(bean, global)
		if err != nil {
			t.Fatal(err)
		}
		tls, _ := out["tls"].(map[string]any)
		if tls["insecure"] != global || tls["server_name"] != "a.example.com" {
			t.Errorf("globalInsecure %v: tls %v", global, tls)
		}
	}
}

// jsonDifferences is "" for two JSON texts of the same meaning, else what differs: the members of two objects, a
// level deep (a nested object's members as "tls.alpn"), or both values.
func jsonDifferences(t *testing.T, want, got []byte) string {
	t.Helper()
	w, g := decodeJSON(t, want), decodeJSON(t, got)
	if reflect.DeepEqual(w, g) {
		return ""
	}
	var out []string
	differences("", w, g, &out)
	sort.Strings(out)
	return strings.Join(out, "\n")
}

func differences(path string, w, g any, out *[]string) {
	wo, ok1 := w.(map[string]any)
	gotObject, ok2 := g.(map[string]any)
	if !ok1 || !ok2 {
		wv, _ := json.Marshal(w)
		gv, _ := json.Marshal(g)
		*out = append(*out, strings.TrimPrefix(path, ".")+": want "+string(wv)+", got "+string(gv))
		return
	}
	keys := map[string]bool{}
	for k := range wo {
		keys[k] = true
	}
	for k := range gotObject {
		keys[k] = true
	}
	for k := range keys {
		wv, inWant := wo[k]
		gv, inGot := gotObject[k]
		switch {
		case !inGot:
			*out = append(*out, strings.TrimPrefix(path+"."+k, ".")+": missing")
		case !inWant:
			*out = append(*out, strings.TrimPrefix(path+"."+k, ".")+": not in the app's")
		case !reflect.DeepEqual(wv, gv):
			differences(path+"."+k, wv, gv, out)
		}
	}
}

// decodeJSON: numbers in one form for each value (1, 1.0 and 1e0 alike), so they compare as numbers.
func decodeJSON(t *testing.T, data []byte) any {
	t.Helper()
	d := json.NewDecoder(bytes.NewReader(data))
	d.UseNumber()
	var v any
	if err := d.Decode(&v); err != nil {
		t.Fatalf("%v: %s", err, data)
	}
	return canonicalNumbers(t, v)
}

func canonicalNumbers(t *testing.T, v any) any {
	switch v := v.(type) {
	case map[string]any:
		for k, e := range v {
			v[k] = canonicalNumbers(t, e)
		}
	case []any:
		for i, e := range v {
			v[i] = canonicalNumbers(t, e)
		}
	case json.Number:
		r, ok := new(big.Rat).SetString(v.String())
		if !ok {
			t.Fatalf("not a number: %s", v)
		}
		return json.Number(r.RatString())
	}
	return v
}

// Xray's extra JSON as the core's XHTTP options: names in snake case, ranges as "min-max", a value of the wrong type
// or a name the core does not know left out, read as Android's org.json reads it.
func TestXhttpExtraOptions(t *testing.T) {
	got, err := xhttpExtraOptions(`{"xPaddingBytes":{"from":100,"to":1000},"scMaxConcurrentPosts":"100-200",
		"noGRPCHeader":true,"scMaxEachPostBytes":1000000,"uplinkHTTPMethod":'PUT',"downloadSettings":{},
		"headers":{"A":"b","N":null},"xmux":{"maxConcurrency":[16,32],"hKeepAlivePeriod":30.0,"cMaxReuseTimes":"8"}}`)
	if err != nil {
		t.Fatal(err)
	}
	want := object{"x_padding_bytes": "100-1000", "no_grpc_header": true, "sc_max_each_post_bytes": "1000000",
		"uplink_http_method": "PUT", "headers": object{"A": "b", "N": nil},
		"xmux": object{"max_concurrency": []any{int64(16), int64(32)}, "h_keep_alive_period": int64(30),
			"c_max_reuse_times": "8"}}
	if !reflect.DeepEqual(got, want) {
		t.Errorf("got %#v\nwant %#v", got, want)
	}
	for _, bad := range []string{`[]`, `{"xmux":{"maxConcurrency":[16,]}}`, `{"a":NaN}`, `{1:2}`} {
		if _, err := xhttpExtraOptions(bad); err == nil {
			t.Errorf("%s: no error", bad)
		}
	}
}

// org.json's literals: hexadecimal and octal integers, Double.valueOf's grammar, anything else a string.
func TestOrgJSONLiterals(t *testing.T) {
	o, err := orgJSONObject(`{a:0x10,b:010,c:08,d:1e2,e:2147483648,f:1.5f,g:-0x5,h:NULL;i=>"A\x"} not read`)
	if err != nil {
		t.Fatal(err)
	}
	want := map[string]any{"a": int32(16), "b": int32(8), "c": 8.0, "d": 100.0, "e": int64(2147483648), "f": 1.5,
		"g": "-0x5", "h": orgNull{}, "i": "Ax"}
	if !reflect.DeepEqual(o.values, want) || strings.Join(o.keys, "") != "abcdefghi" {
		t.Errorf("got %v %#v", o.keys, o.values)
	}
}

// AmneziaWG's lines as the endpoint's fields and the peer's keepalive; a wrong one is an error.
func TestAmneziaWG(t *testing.T) {
	awg, err := parseAmneziaWG("# awg\nJc = 4\nH1 = 5 - 10\nS1 = \nPersistent_Keepalive = 20-30\nRandomTrailers = on")
	if err != nil {
		t.Fatal(err)
	}
	if want := (object{"jc": int64(4), "h1": "5-10", "random_trailers": true}); !reflect.DeepEqual(awg.fields, want) ||
		awg.keepalive == nil || *awg.keepalive != "20-30" {
		t.Errorf("got %v, keepalive %v", awg.fields, awg.keepalive)
	}
	for _, bad := range []string{"Foo = 1", "Jc = -1", "H1 = 10-5", "= 1", "DisableCookies = maybe"} {
		if _, err := parseAmneziaWG(bad); err == nil {
			t.Errorf("%s: no error", bad)
		}
	}
	if got := genReserved("[1, 2, 300]"); got != "AQIs" {
		t.Errorf("genReserved: %s", got)
	}
}

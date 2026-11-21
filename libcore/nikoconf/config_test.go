package nikoconf

import (
	"bytes"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"

	"libcore/nikofmt"
)

// goldenConfigs: the configs the app built of hand-written inputs (app/src/test/java/io/niko/golden/
// ConfigGoldenTest.kt writes them): input.json, and android.json with warnings.json where the build told any, or
// error.txt where it threw.
const goldenConfigs = "../../shared/golden/config"

// Each case, built here from the same input, must give the app's config by meaning (numbers as numbers, keys in any
// order, lists in theirs), the same warnings, and an error where the app threw, with its message. A computer's case
// (an input with its platform) is Go's alone: TestDesktopSnapshots.
func TestGoldenConfigs(t *testing.T) {
	entries, err := os.ReadDir(goldenConfigs)
	if err != nil {
		t.Fatal(err)
	}
	cases := 0
	for _, e := range entries {
		if !e.IsDir() {
			continue
		}
		cases++
		dir := filepath.Join(goldenConfigs, e.Name())
		t.Run(e.Name(), func(t *testing.T) {
			raw, err := os.ReadFile(filepath.Join(dir, "input.json"))
			if err != nil {
				t.Fatalf("a case without its input: %v", err)
			}
			if desktopPlatform(t, raw) != "" {
				t.Skip("a computer's case")
			}
			want, wantErr := readOptional(t, filepath.Join(dir, "android.json")), readOptional(t, filepath.Join(dir,
				"error.txt"))
			if (want == nil) == (wantErr == nil) {
				t.Fatalf("a case has either android.json or error.txt: android.json %v, error.txt %v", want != nil,
					wantErr != nil)
			}
			in := goldenInput(t, raw)
			result, err := Build(in)

			var wantWarnings []string
			if w := readOptional(t, filepath.Join(dir, "warnings.json")); w != nil {
				if err := json.Unmarshal(w, &wantWarnings); err != nil {
					t.Fatal(err)
				}
			}
			if !reflect.DeepEqual(result.Warnings, wantWarnings) && len(result.Warnings)+len(wantWarnings) > 0 {
				t.Errorf("warnings %q, the app's %q", result.Warnings, wantWarnings)
			}

			if wantErr != nil {
				if err == nil {
					t.Fatalf("no error, the app's: %s\nGo's config:\n%s", wantErr, result.Config)
				}
				if got, want := err.Error(), strings.TrimSpace(string(wantErr)); got != want {
					t.Errorf("error %q, the app's %q", got, want)
				}
				return
			}
			if err != nil {
				t.Fatalf("Build: %v", err)
			}
			if diff := configDifferences(t, want, result.Config); diff != "" {
				t.Errorf("%s\nGo's config:\n%s", diff, result.Config)
			}
		})
	}
	if cases == 0 {
		t.Fatalf("no cases in %s", goldenConfigs)
	}
}

func readOptional(t *testing.T, path string) []byte {
	t.Helper()
	data, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return nil
	} else if err != nil {
		t.Fatal(err)
	}
	return data
}

// goldenInput is the input of an input.json as ConfigGoldenTest makes it: each profile's bean of its golden link,
// parsed as the links' tests parse it, with the patch over the bean's JSON (the app's field names, objects member by
// member) read back as Gson reads it, without the bean's defaults.
func goldenInput(t *testing.T, raw []byte) Input {
	t.Helper()
	var in Input
	if err := json.Unmarshal(raw, &in); err != nil {
		t.Fatal(err)
	}
	var links struct {
		Profiles []struct {
			Link  string         `json:"link"`
			Patch map[string]any `json:"patch"`
		} `json:"profiles"`
	}
	if err := json.Unmarshal(raw, &links); err != nil {
		t.Fatal(err)
	}
	for i, p := range links.Profiles {
		text, err := os.ReadFile(filepath.Join(goldenLinks, p.Link+".txt"))
		if err != nil {
			t.Fatal(err)
		}
		bean, err := nikofmt.ParseLink(nikofmt.KtTrim(nikofmt.DecodeUTF8(text)), nikofmt.DefaultSettings)
		if err != nil {
			t.Fatalf("%s: the app makes no profile of it: %v", p.Link, err)
		}
		if p.Patch != nil {
			data, err := json.Marshal(bean)
			if err != nil {
				t.Fatal(err)
			}
			var m map[string]any
			if err := json.Unmarshal(data, &m); err != nil {
				t.Fatal(err)
			}
			if data, err = json.Marshal(overlay(m, p.Patch)); err != nil {
				t.Fatal(err)
			}
			if bean, err = nikofmt.NewBean(nikofmt.TypeName(bean)); err != nil {
				t.Fatal(err)
			}
			d := json.NewDecoder(bytes.NewReader(data))
			d.DisallowUnknownFields()
			if err := d.Decode(bean); err != nil {
				t.Fatalf("%s: the patch: %v", p.Link, err)
			}
		}
		in.Profiles[i].Bean = bean
		in.Profiles[i].Type = nikofmt.TypeName(bean)
	}
	return in
}

// overlay puts over's members over base's, an object into an object member by member.
func overlay(base, over map[string]any) map[string]any {
	for k, v := range over {
		b, ok1 := base[k].(map[string]any)
		o, ok2 := v.(map[string]any)
		if ok1 && ok2 {
			base[k] = overlay(b, o)
		} else {
			base[k] = v
		}
	}
	return base
}

// configDifferences is "" for two JSON texts of the same meaning, else the paths where they differ (into lists of
// the same length too), the first 20.
func configDifferences(t *testing.T, want, got []byte) string {
	t.Helper()
	w, g := decodeJSON(t, want), decodeJSON(t, got)
	if reflect.DeepEqual(w, g) {
		return ""
	}
	var out []string
	deepDifferences("", w, g, &out)
	if len(out) > 20 {
		out = append(out[:20], "…")
	}
	return strings.Join(out, "\n")
}

func deepDifferences(path string, w, g any, out *[]string) {
	switch wv := w.(type) {
	case map[string]any:
		if gv, ok := g.(map[string]any); ok {
			var keys []string
			for k := range wv {
				keys = append(keys, k)
			}
			for k := range gv {
				if _, ok := wv[k]; !ok {
					keys = append(keys, k)
				}
			}
			sort.Strings(keys)
			for _, k := range keys {
				we, inWant := wv[k]
				ge, inGot := gv[k]
				switch {
				case !inGot:
					*out = append(*out, path+"."+k+": missing")
				case !inWant:
					*out = append(*out, fmt.Sprintf("%s.%s: not in the app's: %s", path, k, marshal(ge)))
				case !reflect.DeepEqual(we, ge):
					deepDifferences(path+"."+k, we, ge, out)
				}
			}
			return
		}
	case []any:
		if gv, ok := g.([]any); ok && len(gv) == len(wv) {
			for i := range wv {
				if !reflect.DeepEqual(wv[i], gv[i]) {
					deepDifferences(fmt.Sprintf("%s[%d]", path, i), wv[i], gv[i], out)
				}
			}
			return
		}
	}
	*out = append(*out, fmt.Sprintf("%s: want %s, got %s", path, marshal(w), marshal(g)))
}

func marshal(v any) string {
	data, _ := json.Marshal(v)
	return string(data)
}

// The JVM's orders, as java.util.HashSet (Kotlin's toHashSet) and HashMap gave them for these keys.
func TestJavaHashOrder(t *testing.T) {
	hosts := []string{}
	for i := 0; i < 40; i++ {
		hosts = append(hosts, fmt.Sprintf("full:host%d.example.com", i))
	}
	hosts = append(hosts, "full:host3.example.com", "full:dns.google", "full:Ünïcode.example😀")
	order := []int{4, 5, 6, 20, 31, 8, 21, 10, 11, 7, 9, 32, 23, 12, 22, 33, 25, 14, 15, 34, 36, 13, 17, 24, 28, 35,
		39, -1, 30, 38, 27, 16, 26, 37, 3, -2, 2, 0, 29, 18, 19, 1}
	var want []string
	for _, i := range order {
		switch i {
		case -1:
			want = append(want, "full:dns.google")
		case -2:
			want = append(want, "full:Ünïcode.example😀")
		default:
			want = append(want, fmt.Sprintf("full:host%d.example.com", i))
		}
	}
	if got := javaHashSetOrder(hosts, javaStringHash); !reflect.DeepEqual(got, want) {
		t.Errorf("strings: %q", got)
	}
	if got := javaHashSetOrder([]string{"full:inner", "full:dns.google"}, javaStringHash); !reflect.DeepEqual(got,
		[]string{"full:inner", "full:dns.google"}) {
		t.Errorf("two strings: %q", got)
	}

	uids := []javaInt{{value: 10123}, {null: true}, {value: 70000}, {value: 1000}, {null: true}, {value: 65536},
		{value: 1016}, {value: 2000000}}
	wantUIDs := []javaInt{{null: true}, {value: 70000}, {value: 65536}, {value: 1000}, {value: 1016}, {value: 10123},
		{value: 2000000}}
	if got := javaHashSetOrder(uids, javaIntegerHash); !reflect.DeepEqual(got, wantUIDs) {
		t.Errorf("uids: %v", got)
	}

	var m javaLongMap
	for _, id := range []int64{1, 17, 2, 33, 100, 5000000000, -1, 3, 16, 48, 0, 12, 13, 14, 15, 29, 30} {
		m.put(id, fmt.Sprintf("t%d", id))
	}
	wantValues := []string{"t-1", "t0", "t1", "t33", "t2", "t3", "t100", "t5000000000", "t12", "t13", "t14", "t15",
		"t16", "t48", "t17", "t29", "t30"}
	if got := m.values(); !reflect.DeepEqual(got, wantValues) {
		t.Errorf("map values: %q", got)
	}
}

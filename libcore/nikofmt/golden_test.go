package nikofmt

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strings"
	"testing"
)

// goldenLinks is shared/golden/links from this package's directory: the links and the beans the app's own parsers
// made of them (app/src/test/java/io/niko/golden/LinksGoldenTest.kt writes the .json; docs/design/desktop-stage3.md
// §5). A new link is added as <name>.txt and gets its .json from that test.
const goldenLinks = "../../shared/golden/links"

// Each link, read here, must give the JSON the app gave: the same bean field for field, or null where the app drops
// the link. Compared by meaning (numbers as numbers, keys in any order), as the Kotlin side compares.
func TestGoldenLinks(t *testing.T) {
	txts, err := filepath.Glob(filepath.Join(goldenLinks, "*.txt"))
	if err != nil || len(txts) == 0 {
		t.Fatalf("no links in %s: %v", goldenLinks, err)
	}
	sort.Strings(txts)
	for _, txt := range txts {
		name := strings.TrimSuffix(filepath.Base(txt), ".txt")
		t.Run(name, func(t *testing.T) {
			raw, err := os.ReadFile(txt)
			if err != nil {
				t.Fatal(err)
			}
			// as Kotlin's readText().trim() (a Windows checkout may add a CR)
			link := ktTrim(javaUTF8(raw))
			want, err := os.ReadFile(strings.TrimSuffix(txt, ".txt") + ".json")
			if err != nil {
				t.Fatalf("no fixture: run the app's io.niko.golden tests to write it (%v)", err)
			}
			var got []byte
			bean, err := ParseLink(link, DefaultSettings)
			if err != nil {
				t.Logf("dropped: %v", err)
				got = []byte("null")
			} else if got, err = json.MarshalIndent(bean, "", "  "); err != nil {
				t.Fatal(err)
			}
			if diff := jsonDifferences(t, want, got); diff != "" {
				t.Errorf("%s\n%s\nGo's bean:\n%s", link, diff, got)
			}
		})
	}
}

// jsonDifferences is "" for two JSON texts of the same meaning, else what differs: the keys of two objects, or both
// values.
func jsonDifferences(t *testing.T, want, got []byte) string {
	var w, g any
	if err := json.Unmarshal(want, &w); err != nil {
		t.Fatalf("the fixture: %v", err)
	}
	if err := json.Unmarshal(got, &g); err != nil {
		t.Fatalf("Go's JSON: %v", err)
	}
	if reflect.DeepEqual(w, g) {
		return ""
	}
	wo, ok1 := w.(map[string]any)
	gotObject, ok2 := g.(map[string]any)
	if !ok1 || !ok2 {
		return "want " + string(want) + ", got " + string(got)
	}
	keys := map[string]bool{}
	for k := range wo {
		keys[k] = true
	}
	for k := range gotObject {
		keys[k] = true
	}
	var out []string
	for k := range keys {
		if !reflect.DeepEqual(wo[k], gotObject[k]) {
			wv, _ := json.Marshal(wo[k])
			gv, _ := json.Marshal(gotObject[k])
			out = append(out, k+": want "+string(wv)+", got "+string(gv))
		}
	}
	sort.Strings(out)
	return strings.Join(out, "\n")
}

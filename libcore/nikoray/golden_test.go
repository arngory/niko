package nikoray

import (
	"encoding/json"
	"os"
	"path/filepath"
	"reflect"
	"sort"
	"strconv"
	"strings"
	"testing"

	"libcore/nikofmt"
)

// goldenRaycontrol is shared/golden/raycontrol from this package's directory: what the app's import makes of the
// invites and device packages, and of the payloads of a node's subscription
// (app/src/test/java/io/niko/golden/RaycontrolGoldenTest.kt writes the outputs; docs/design/desktop-stage3.md §5).
const goldenRaycontrol = "../../shared/golden/raycontrol"

func strs(list []string) []any {
	out := []any{}
	for _, s := range list {
		out = append(out, s)
	}
	return out
}

func services(list []Service) []any {
	out := []any{}
	for _, s := range list {
		out = append(out, s)
	}
	return out
}

// readPackage is what RaycontrolGoldenTest.readPackage writes of a text.
func readPackage(text string) map[string]any {
	if IsInvite(text) {
		i, err := ParseInvite(text)
		if err != nil {
			return map[string]any{"error": err.Error()}
		}
		o := map[string]any{"host": i.Host, "spa": i.SPA, "invite_id": i.InviteID, "invite_key": i.InviteKey,
			"services": services(i.Services), "api": i.API, "expires": i.Expires, "pin_required": i.PinRequired,
			"addrs": strs(i.Addrs)}
		if i.DNSName != "" {
			o["dns_name"] = i.DNSName
		}
		return map[string]any{"invite": o}
	}
	r, err := ParseDevice(text)
	if err != nil {
		return map[string]any{"error": err.Error()}
	}
	o := map[string]any{"host": r.Host, "spa": r.SPA, "device_id": r.DeviceID, "device_key": r.DeviceKey,
		"services": services(r.Services), "addrs": strs(r.Addrs)}
	if r.Subscription != nil {
		o["sub"] = r.Subscription
	}
	if r.API != nil {
		o["api"] = r.API
	}
	if r.Role != "" {
		o["role"] = r.Role
	}
	if r.DNSName != "" {
		o["dns_name"] = r.DNSName
	}
	return map[string]any{"device": o}
}

// meaning is v as JSON read back: numbers as float64, keys in any order.
func meaning(t *testing.T, v any) any {
	data, err := json.Marshal(v)
	if err != nil {
		t.Fatal(err)
	}
	var out any
	if err = json.Unmarshal(data, &out); err != nil {
		t.Fatal(err)
	}
	return out
}

func readMeaning(t *testing.T, path string) any {
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("no fixture: run the app's io.niko.golden tests to write it (%v)", err)
	}
	var out any
	if err = json.Unmarshal(data, &out); err != nil {
		t.Fatalf("%s: %v", path, err)
	}
	return out
}

// differences are the paths where two JSON values differ, the first few.
func differences(want, got any, path string, out *[]string) {
	if len(*out) >= 8 || reflect.DeepEqual(want, got) {
		return
	}
	w, ok1 := want.(map[string]any)
	g, ok2 := got.(map[string]any)
	if ok1 && ok2 {
		keys := map[string]bool{}
		for k := range w {
			keys[k] = true
		}
		for k := range g {
			keys[k] = true
		}
		var sorted []string
		for k := range keys {
			sorted = append(sorted, k)
		}
		sort.Strings(sorted)
		for _, k := range sorted {
			differences(w[k], g[k], path+"."+k, out)
		}
		return
	}
	wa, ok1 := want.([]any)
	ga, ok2 := got.([]any)
	if ok1 && ok2 && len(wa) == len(ga) {
		for i := range wa {
			differences(wa[i], ga[i], path+"["+itoa(i)+"]", out)
		}
		return
	}
	wj, _ := json.Marshal(want)
	gj, _ := json.Marshal(got)
	*out = append(*out, path+": want "+string(wj)+", got "+string(gj))
}

func itoa(i int) string { return strconv.Itoa(i) }

func TestGoldenInvites(t *testing.T) {
	txts, err := filepath.Glob(filepath.Join(goldenRaycontrol, "invites", "*.txt"))
	if err != nil || len(txts) == 0 {
		t.Fatalf("no invites: %v", err)
	}
	for _, txt := range txts {
		name := strings.TrimSuffix(filepath.Base(txt), ".txt")
		t.Run(name, func(t *testing.T) {
			raw, err := os.ReadFile(txt)
			if err != nil {
				t.Fatal(err)
			}
			got := meaning(t, readPackage(nikofmt.KtTrim(string(raw))))
			want := readMeaning(t, strings.TrimSuffix(txt, ".txt")+".json")
			var diff []string
			differences(want, got, "", &diff)
			if len(diff) > 0 {
				t.Error(strings.Join(diff, "\n"))
			}
		})
	}
}

// payloadResult is what RaycontrolGoldenTest.payload writes of a case.
func payloadResult(t *testing.T, dir string) map[string]any {
	payload, err := os.ReadFile(filepath.Join(dir, "payload.json"))
	if err != nil {
		t.Fatal(err)
	}
	var node struct {
		Services []struct {
			ID   int    `json:"id"`
			Kind string `json:"kind"`
		} `json:"services"`
		Now int64 `json:"now"`
	}
	data, err := os.ReadFile(filepath.Join(dir, "node.json"))
	if err != nil {
		t.Fatal(err)
	}
	if err = json.Unmarshal(data, &node); err != nil {
		t.Fatal(err)
	}
	kinds := map[int]string{}
	for _, s := range node.Services {
		kinds[s.ID] = s.Kind
	}
	parsed, err := Parse(payload, kinds, node.Now, nikofmt.DefaultSettings)
	if err != nil {
		t.Fatal(err)
	}
	rows := []any{}
	for _, r := range parsed.Rows {
		rows = append(rows, map[string]any{"key": r.Key, "type": nikofmt.TypeName(r.Bean), "bean": r.Bean,
			"links": r.Links})
	}
	next, err := NextRefresh(payload, node.Now)
	if err != nil {
		t.Fatal(err)
	}
	out := map[string]any{"rows": rows, "skipped": parsed.Skipped, "addrs": strs(parsed.Addrs),
		"spa_versions": parsed.SPAVersions, "next_refresh": next}
	optional := func(name, value string) {
		if value != "" {
			out[name] = value
		}
	}
	optional("doh", parsed.DoH)
	optional("doh_pins", parsed.DoHPins)
	optional("name", parsed.Name)
	optional("note", parsed.Note)
	optional("dns_name", parsed.DNSName)
	if parsed.Carriers != nil {
		out["carriers"] = strs(parsed.Carriers)
	}
	if parsed.Traffic != nil {
		out["traffic"] = parsed.Traffic
	}
	if parsed.Client != nil {
		out["client"] = parsed.Client
	}
	return out
}

func TestGoldenPayloads(t *testing.T) {
	cases, err := filepath.Glob(filepath.Join(goldenRaycontrol, "payloads", "*", "payload.json"))
	if err != nil || len(cases) == 0 {
		t.Fatalf("no payloads: %v", err)
	}
	for _, path := range cases {
		dir := filepath.Dir(path)
		t.Run(filepath.Base(dir), func(t *testing.T) {
			got := meaning(t, payloadResult(t, dir))
			want := readMeaning(t, filepath.Join(dir, "result.json"))
			var diff []string
			differences(want, got, "", &diff)
			if len(diff) > 0 {
				t.Error(strings.Join(diff, "\n"))
			}
		})
	}
}

package nikosub

import (
	"encoding/json"
	"errors"
	"os"
	"path/filepath"
	"reflect"
	"regexp"
	"sort"
	"strings"
	"testing"

	"libcore/nikofmt"
)

// goldenSubscriptions is shared/golden/subscriptions from this package's directory: a provider's answer and the group
// it updates, and what the app's own update made of them (app/src/test/java/io/niko/golden/SubscriptionsGoldenTest.kt
// writes result.json or error.txt; docs/design/desktop-stage3.md §5). A new case is a directory with its body.* and
// gets its result from that test.
const goldenSubscriptions = "../../shared/golden/subscriptions"

type goldenExisting struct {
	ID    int64           `json:"id"`
	Order int64           `json:"order"`
	Link  string          `json:"link"`
	Patch json.RawMessage `json:"patch"`
}

type goldenGroup struct {
	Name         *string         `json:"name"`
	Subscription json.RawMessage `json:"subscription"`
}

// existing is a profile of exists.json: its link's bean with the patch's fields over it.
func existing(t *testing.T, e goldenExisting) Existing {
	bean, err := nikofmt.ParseLink(e.Link, nikofmt.DefaultSettings)
	if err != nil {
		t.Fatalf("%s: %v", e.Link, err)
	}
	if len(e.Patch) > 0 {
		var fields, patch map[string]json.RawMessage
		data, _ := json.Marshal(bean)
		if err = json.Unmarshal(data, &fields); err != nil {
			t.Fatal(err)
		}
		if err = json.Unmarshal(e.Patch, &patch); err != nil {
			t.Fatal(err)
		}
		for k, v := range patch {
			fields[k] = v
		}
		data, _ = json.Marshal(fields)
		if bean, err = nikofmt.DecodeBean(nikofmt.TypeName(bean), data); err != nil {
			t.Fatal(err)
		}
	}
	return Existing{ID: e.ID, Order: e.Order, Bean: bean}
}

func readJSON(t *testing.T, path string, v any) bool {
	data, err := os.ReadFile(path)
	if errors.Is(err, os.ErrNotExist) {
		return false
	}
	if err != nil {
		t.Fatal(err)
	}
	if err = json.Unmarshal(data, v); err != nil {
		t.Fatalf("%s: %v", path, err)
	}
	return true
}

// runCase is the app's update of a case: result.json's JSON, or error.txt's text.
func runCase(t *testing.T, dir string) (any, string) {
	bodies, _ := filepath.Glob(filepath.Join(dir, "body.*"))
	if len(bodies) != 1 {
		t.Fatalf("%s: one body.* file, not %v", dir, bodies)
	}
	raw, err := os.ReadFile(bodies[0])
	if err != nil {
		t.Fatal(err)
	}
	headers := map[string]string{}
	var given map[string]string
	if readJSON(t, filepath.Join(dir, "headers.json"), &given) {
		for k, v := range given {
			headers[strings.ToLower(k)] = v
		}
	}
	name := "Subscription #1"
	sub := NewSubscription("")
	var group goldenGroup
	if readJSON(t, filepath.Join(dir, "group.json"), &group) {
		if group.Name != nil {
			name = *group.Name
		}
		if len(group.Subscription) > 0 {
			if err = json.Unmarshal(group.Subscription, &sub); err != nil {
				t.Fatal(err)
			}
		}
	}
	var list []goldenExisting
	readJSON(t, filepath.Join(dir, "exists.json"), &list)
	var exists []Existing
	for _, e := range list {
		exists = append(exists, existing(t, e))
	}
	before := make([]json.RawMessage, len(exists))
	for i, e := range exists {
		before[i], _ = json.Marshal(e.Bean)
	}

	parsed, err := Parse(nikofmt.DecodeUTF8(raw), "", nikofmt.DefaultSettings)
	if errors.Is(err, ErrNoProfiles) {
		return nil, "no profiles"
	}
	if err != nil {
		t.Logf("the update fails: %v", err)
		return nil, "failed"
	}
	if name, err = ApplyAnswer(name, &sub, func(h string) string { return headers[strings.ToLower(h)] }); err != nil {
		t.Logf("the update fails: %v", err)
		return nil, "failed"
	}
	plan, err := Merge(exists, Prepare(parsed), sub.Deduplication)
	if err != nil {
		t.Logf("the update fails: %v", err)
		return nil, "failed"
	}
	type profile struct {
		ID    *int64          `json:"id,omitempty"`
		Order int64           `json:"order"`
		State string          `json:"state"`
		Type  string          `json:"type"`
		Bean  json.RawMessage `json:"bean"`
	}
	profiles := []profile{}
	for _, p := range plan.Profiles {
		row := profile{Order: p.Order, State: p.State, Type: nikofmt.TypeName(p.Bean)}
		if p.ID != 0 {
			id := p.ID
			row.ID = &id
		}
		row.Bean, _ = json.Marshal(p.Bean)
		if p.State == Kept {
			for i, e := range exists {
				if e.ID == p.ID {
					row.Bean = before[i]
				}
			}
		}
		profiles = append(profiles, row)
	}
	nonNil := func(s []string) []string {
		if s == nil {
			return []string{}
		}
		return s
	}
	updated := [][2]string{}
	updated = append(updated, plan.Updated...)
	deleted := []int64{}
	deleted = append(deleted, plan.Deleted...)
	result := map[string]any{
		"name": name,
		"subscription": map[string]any{"subscriptionUserinfo": sub.UserInfo, "announce": sub.Announce,
			"autoUpdate": sub.AutoUpdate, "autoUpdateDelay": sub.AutoUpdateDelay},
		"profiles":     profiles,
		"deleted":      deleted,
		"changed":      plan.Changed,
		"added":        nonNil(plan.Added),
		"updated":      updated,
		"deletedNames": nonNil(plan.DeletedNames),
		"duplicate":    nonNil(plan.Duplicate),
	}
	data, _ := json.Marshal(result)
	var v any
	_ = json.Unmarshal(data, &v)
	return v, ""
}

// customName is a custom config's name without one: "Custom " and the hash of its Kryo, which has its text.
var customName = regexp.MustCompile(`^Custom -?[0-9]+$`)

// checkCustomNames: each nameless custom config of the app's result is named in its message as Go names that bean, its
// text as the JVM wrote it (the hash of the app's ConfigBean.displayName, done here).
func checkCustomNames(t *testing.T, want any) {
	result, _ := want.(map[string]any)
	profiles, _ := result["profiles"].([]any)
	added, _ := result["added"].([]any)
	for _, p := range profiles {
		row, _ := p.(map[string]any)
		if row["type"] != "config" || row["state"] != Added {
			continue
		}
		data, _ := json.Marshal(row["bean"])
		bean, err := nikofmt.DecodeBean("config", data)
		if err != nil {
			t.Fatal(err)
		}
		if name := nikofmt.Base(bean).Name; name != nil && *name != "" {
			continue
		}
		found := false
		for _, a := range added {
			found = found || a == nikofmt.DisplayName(bean)
		}
		if !found {
			t.Errorf("%s: not among the app's added %v", nikofmt.DisplayName(bean), added)
		}
	}
}

// normalize reads a custom config's text as its JSON, so that two configs of one meaning are equal: the JVM's
// JSON-java orders an object's names by its hash, the platform's org.json and Go keep them. So the hash in a nameless
// custom config's name differs too (checkCustomNames holds it to the app's for the app's text): it is left out.
func normalize(v any) any {
	switch v := v.(type) {
	case string:
		if customName.MatchString(v) {
			return "Custom …"
		}
		return v
	case map[string]any:
		out := map[string]any{}
		for k, e := range v {
			if s, ok := e.(string); ok && k == "config" {
				var parsed any
				if json.Unmarshal([]byte(s), &parsed) == nil {
					out[k] = parsed
					continue
				}
			}
			out[k] = normalize(e)
		}
		return out
	case []any:
		out := make([]any, len(v))
		for i, e := range v {
			out[i] = normalize(e)
		}
		return out
	}
	return v
}

// Each case, updated here, must give what the app's update gave: the same group, the same profiles in the same
// states with the same beans, the same message; or the same failure.
func TestGoldenSubscriptions(t *testing.T) {
	entries, err := os.ReadDir(goldenSubscriptions)
	if err != nil || len(entries) == 0 {
		t.Fatalf("no cases in %s: %v", goldenSubscriptions, err)
	}
	var names []string
	for _, e := range entries {
		if e.IsDir() {
			names = append(names, e.Name())
		}
	}
	sort.Strings(names)
	for _, name := range names {
		t.Run(name, func(t *testing.T) {
			dir := filepath.Join(goldenSubscriptions, name)
			got, failure := runCase(t, dir)
			if text, err := os.ReadFile(filepath.Join(dir, "error.txt")); err == nil {
				if want := strings.TrimSpace(string(text)); failure != want {
					gotJSON, _ := json.MarshalIndent(got, "", "  ")
					t.Fatalf("want the app's %q, got %q %s", want, failure, gotJSON)
				}
				return
			}
			var want any
			if !readJSON(t, filepath.Join(dir, "result.json"), &want) {
				t.Fatal("no result.json nor error.txt: run the app's io.niko.golden tests to write it")
			}
			if failure != "" {
				t.Fatalf("the app's update gives a result, Go's %q", failure)
			}
			checkCustomNames(t, want)
			if w, g := normalize(want), normalize(got); !reflect.DeepEqual(w, g) {
				wantJSON, _ := json.MarshalIndent(w, "", "  ")
				gotJSON, _ := json.MarshalIndent(g, "", "  ")
				t.Errorf("%s", lineDiff(string(wantJSON), string(gotJSON)))
			}
		})
	}
}

// lineDiff is the lines of two texts where they first differ, with some around.
func lineDiff(want, got string) string {
	w, g := strings.Split(want, "\n"), strings.Split(got, "\n")
	for i := 0; i < len(w) || i < len(g); i++ {
		if i >= len(w) || i >= len(g) || w[i] != g[i] {
			from := max(0, i-6)
			pick := func(lines []string) string {
				return strings.Join(lines[min(from, len(lines)):min(i+8, len(lines))], "\n")
			}
			return "want:\n" + pick(w) + "\ngot:\n" + pick(g)
		}
	}
	return "equal texts of unequal values"
}

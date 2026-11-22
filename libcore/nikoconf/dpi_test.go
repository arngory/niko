package nikoconf

import (
	"encoding/json"
	"os"
	"reflect"
	"testing"
)

// shared/golden/dpi/byedpi.json, as DpiGoldenTest reads it: the presets and ByeDPI strings with their strategies or
// why each is refused.
const goldenDpi = "../../shared/golden/dpi/byedpi.json"

func TestGoldenDpi(t *testing.T) {
	data, err := os.ReadFile(goldenDpi)
	if err != nil {
		t.Fatal(err)
	}
	var golden struct {
		Presets map[string]any `json:"presets"`
		Cases   []map[string]any
	}
	if err := json.Unmarshal(data, &golden); err != nil {
		t.Fatal(err)
	}
	// through JSON, as the file has it
	asJSON := func(v any) any {
		data, err := json.Marshal(v)
		if err != nil {
			t.Fatal(err)
		}
		var out any
		if err := json.Unmarshal(data, &out); err != nil {
			t.Fatal(err)
		}
		return out
	}
	if len(golden.Presets) != len(DpiPresets) {
		t.Errorf("%d presets, the file's %d", len(DpiPresets), len(golden.Presets))
	}
	for _, preset := range DpiPresets {
		if got := asJSON(preset.Strategy.config()); !reflect.DeepEqual(got, golden.Presets[preset.ID]) {
			t.Errorf("preset %s: %v, the file's %v", preset.ID, got, golden.Presets[preset.ID])
		}
	}
	if len(golden.Cases) == 0 {
		t.Fatal("no cases")
	}
	for _, c := range golden.Cases {
		in := c["in"].(string)
		want := map[string]any{}
		for k, v := range c {
			if k != "in" {
				want[k] = v
			}
		}
		got := map[string]any{}
		strategy, err := ParseByeDpi(in)
		if err != nil {
			e := err.(*ByeDpiError)
			got["error"] = e.Kind
			if e.Flag != "" {
				got["flag"] = e.Flag
			}
			if e.Value != "" {
				got["value"] = e.Value
			}
		} else {
			got["out"] = asJSON(strategy.config())
		}
		if !reflect.DeepEqual(got, want) {
			t.Errorf("%q: %v, the file's %v", in, got, want)
		}
	}
}

func TestDpiSetting(t *testing.T) {
	s := DefaultSettings()
	if s.DpiDirect != DpiOff || s.DpiDirectPreset != DpiDefaultPreset || s.dpiStrategy() != nil {
		t.Errorf("defaults: %d %q %v", s.DpiDirect, s.DpiDirectPreset, s.dpiStrategy())
	}
	s.DpiDirect = DpiPreset
	if got := s.dpiStrategy(); got == nil || got.TLSRec[0] != "sni+1" {
		t.Errorf("the default preset: %v", got)
	}
	s.DpiDirectPreset = "gone"
	if got := s.dpiStrategy(); got != nil {
		t.Errorf("an unknown preset: %v", got)
	}
	s.DpiDirect, s.DpiDirectCustom = DpiCustom, "-f -1"
	if got := s.dpiStrategy(); got != nil {
		t.Errorf("a string refused: %v", got)
	}
}

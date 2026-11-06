package libcore

import (
	"bytes"
	"os"
	"path/filepath"
	"testing"

	"github.com/sagernet/sing-box/common/srs"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json/badoption"
)

func writeRuleSet(t *testing.T, rules ...option.DefaultHeadlessRule) string {
	t.Helper()
	var buf bytes.Buffer
	set := option.PlainRuleSet{}
	for _, r := range rules {
		set.Rules = append(set.Rules, option.HeadlessRule{Type: C.RuleTypeDefault, DefaultOptions: r})
	}
	if err := srs.Write(&buf, set, C.RuleSetVersionCurrent); err != nil {
		t.Fatal(err)
	}
	path := filepath.Join(t.TempDir(), "set.srs")
	if err := os.WriteFile(path, buf.Bytes(), 0o600); err != nil {
		t.Fatal(err)
	}
	return path
}

func TestCheckRuleSet(t *testing.T) {
	domains := writeRuleSet(t, option.DefaultHeadlessRule{DomainSuffix: badoption.Listable[string]{"example.org"}})
	if kinds, err := CheckRuleSet(domains, C.RuleSetFormatBinary); err != nil || kinds != RuleSetHasDomain {
		t.Fatalf("domains: %d %v", kinds, err)
	}
	ips := writeRuleSet(t, option.DefaultHeadlessRule{IPCIDR: badoption.Listable[string]{"10.0.0.0/8"}})
	if kinds, err := CheckRuleSet(ips, C.RuleSetFormatBinary); err != nil || kinds != RuleSetHasIP {
		t.Fatalf("ips: %d %v", kinds, err)
	}
	// a newer format than this core reads, as a list built by a later sing-box: refused, not loaded at a start
	newer, _ := os.ReadFile(domains)
	newer[3] = C.RuleSetVersionCurrent + 1
	path := filepath.Join(t.TempDir(), "newer.srs")
	os.WriteFile(path, newer, 0o600)
	if _, err := CheckRuleSet(path, C.RuleSetFormatBinary); err == nil {
		t.Fatal("a rule-set of a newer version taken")
	}
	// JSON: a rule-set, and one the core would refuse
	good := filepath.Join(t.TempDir(), "good.json")
	os.WriteFile(good, []byte(`{"version":2,"rules":[{"domain_suffix":["example.org"],"ip_cidr":["10.0.0.0/8"]}]}`), 0o600)
	if kinds, err := CheckRuleSet(good, C.RuleSetFormatSource); err != nil || kinds != RuleSetHasDomain|RuleSetHasIP {
		t.Fatalf("json: %d %v", kinds, err)
	}
	bad := filepath.Join(t.TempDir(), "bad.json")
	os.WriteFile(bad, []byte(`{"version":2,"rules":[{"ip_cidr":["not an address"]}]}`), 0o600)
	if _, err := CheckRuleSet(bad, C.RuleSetFormatSource); err == nil {
		t.Fatal("a rule-set the core would refuse taken")
	}
	if _, err := CheckRuleSet(domains, "yaml"); err == nil {
		t.Fatal("an unknown format taken")
	}
}

package ruleset

import (
	"bytes"
	"compress/zlib"
	"os"
	"path/filepath"
	"strings"
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

func TestCheck(t *testing.T) {
	domains := writeRuleSet(t, option.DefaultHeadlessRule{DomainSuffix: badoption.Listable[string]{"example.org"}})
	if kinds, err := Check(domains, C.RuleSetFormatBinary); err != nil || kinds != HasDomain {
		t.Fatalf("domains: %d %v", kinds, err)
	}
	ips := writeRuleSet(t, option.DefaultHeadlessRule{IPCIDR: badoption.Listable[string]{"10.0.0.0/8"}})
	if kinds, err := Check(ips, C.RuleSetFormatBinary); err != nil || kinds != HasIP {
		t.Fatalf("ips: %d %v", kinds, err)
	}
	// a newer format than this core reads, as a list built by a later sing-box: refused, not loaded at a start
	newer, _ := os.ReadFile(domains)
	newer[3] = C.RuleSetVersionCurrent + 1
	path := filepath.Join(t.TempDir(), "newer.srs")
	os.WriteFile(path, newer, 0o600)
	if _, err := Check(path, C.RuleSetFormatBinary); err == nil {
		t.Fatal("a rule-set of a newer version taken")
	}
	// JSON: a rule-set, and one the core would refuse
	good := filepath.Join(t.TempDir(), "good.json")
	os.WriteFile(good, []byte(`{"version":2,"rules":[{"domain_suffix":["example.org"],"ip_cidr":["10.0.0.0/8"]}]}`), 0o600)
	if kinds, err := Check(good, C.RuleSetFormatSource); err != nil || kinds != HasDomain|HasIP {
		t.Fatalf("json: %d %v", kinds, err)
	}
	bad := filepath.Join(t.TempDir(), "bad.json")
	os.WriteFile(bad, []byte(`{"version":2,"rules":[{"ip_cidr":["not an address"]}]}`), 0o600)
	if _, err := Check(bad, C.RuleSetFormatSource); err == nil {
		t.Fatal("a rule-set the core would refuse taken")
	}
	if _, err := Check(domains, "yaml"); err == nil {
		t.Fatal("an unknown format taken")
	}
}

// A binary rule-set whose stream unpacks to more than maxInflated is refused before the core's reader takes it
// whole; a file over MaxSize unread.
func TestCheckBounds(t *testing.T) {
	var packed bytes.Buffer
	packed.Write([]byte{'S', 'R', 'S', C.RuleSetVersionCurrent})
	w, _ := zlib.NewWriterLevel(&packed, zlib.BestCompression)
	zeros := make([]byte, 1<<20)
	for i := 0; i <= maxInflated>>20; i++ {
		w.Write(zeros)
	}
	w.Close()
	if packed.Len() > MaxSize {
		t.Fatalf("the bomb itself is %d bytes", packed.Len())
	}
	bomb := filepath.Join(t.TempDir(), "bomb.srs")
	os.WriteFile(bomb, packed.Bytes(), 0o600)
	if _, err := Check(bomb, C.RuleSetFormatBinary); err == nil || !strings.Contains(err.Error(), "unpacking") {
		t.Fatalf("a bomb: %v", err)
	}
	large := filepath.Join(t.TempDir(), "large.json")
	os.WriteFile(large, bytes.Repeat([]byte(" "), MaxSize+1), 0o600)
	if _, err := Check(large, C.RuleSetFormatSource); err == nil || !strings.Contains(err.Error(), "bytes") {
		t.Fatalf("over the size: %v", err)
	}
}

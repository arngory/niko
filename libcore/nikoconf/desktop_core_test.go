//go:build with_gvisor

// The core's option parser on the desktop snapshots: built with the core fork's checkout, as desktop.yml's tests
// have it (its TAGS, with_gvisor among them); ci.yml's golden job has none, and leaves the check out.

package nikoconf

import (
	"context"
	"testing"

	"github.com/sagernet/sing-box/include"
	O "github.com/sagernet/sing-box/option"
)

func init() { coreReads = coreReadsConfig }

// coreReadsConfig: the core takes the config, its options parsed as the daemon parses a config it starts (boxdd's context,
// include.Context: every protocol of the build's tags registered, a stub for each one left out). The parser refuses
// a field it does not know, so a misspelt one fails here.
func coreReadsConfig(t *testing.T, config []byte) {
	t.Helper()
	var options O.Options
	if err := options.UnmarshalJSONContext(include.Context(context.Background()), config); err != nil {
		t.Errorf("the core does not read it: %v", err)
	}
}

// The core's parser refuses what it does not know, or coreReads checks nothing.
func TestCoreReadsRefuses(t *testing.T) {
	var options O.Options
	bad := `{"inbounds":[{"type":"mixed","tag":"mixed-in","set_system_proxi":true}]}`
	if options.UnmarshalJSONContext(include.Context(context.Background()), []byte(bad)) == nil {
		t.Fatal("an unknown field read")
	}
}

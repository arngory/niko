package nikofmt

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// Every golden link's bean written as the app's Kryo and read back by kryo.go (which the sn:// fixtures hold to the
// app's) is the bean it was: the writers have the readers' layout, field for field. Equality leaves the name out.
func TestKryoRoundTrip(t *testing.T) {
	txts, err := filepath.Glob(filepath.Join(goldenLinks, "*.txt"))
	if err != nil || len(txts) == 0 {
		t.Fatalf("no links in %s: %v", goldenLinks, err)
	}
	tried := 0
	for _, txt := range txts {
		raw, err := os.ReadFile(txt)
		if err != nil {
			t.Fatal(err)
		}
		bean, err := ParseLink(ktTrim(javaUTF8(raw)), DefaultSettings)
		if err != nil {
			continue
		}
		name := strings.TrimSuffix(filepath.Base(txt), ".txt")
		back, err := KryoClone(bean)
		if err != nil {
			t.Errorf("%s: %v", name, err)
			continue
		}
		want, _ := json.Marshal(withoutUnwritten(bean))
		got, _ := json.Marshal(back)
		if diff := jsonDifferences(t, want, got); diff != "" {
			t.Errorf("%s: %s", name, diff)
		}
		if same, err := KryoEqual(bean, back); err != nil || !same {
			t.Errorf("%s: not equal to its clone (%v)", name, err)
		}
		renamed, _ := KryoClone(bean)
		renamed.base().Name = ptr("another name")
		if same, _ := KryoEqual(bean, renamed); !same {
			t.Errorf("%s: the name counts in equals", name)
		}
		port := *bean.base().ServerPort + 1
		renamed.base().ServerPort = &port
		if same, _ := KryoEqual(bean, renamed); same {
			t.Errorf("%s: another port is equal", name)
		}
		tried++
	}
	if tried < 50 {
		t.Fatalf("only %d beans", tried)
	}
	// a null the app unboxes throws
	if _, err := KryoBytes(&ShadowsocksBean{AbstractBean: AbstractBean{ServerAddress: ptr("a"), ServerPort: ptr(1)}}); err == nil {
		t.Error("a null sUoT written")
	}
	if _, err := KryoBytes(&VMessBean{}); err == nil {
		t.Error("a null port written")
	}
}

// withoutUnwritten is a copy of the bean without what its Kryo does not hold: a V2Ray transport's host and path
// where the transport has none (tcp, quic; gRPC keeps its path alone), which the app's store loses too: read back,
// they are the defaults, empty.
func withoutUnwritten(b Bean) Bean {
	switch b := b.(type) {
	case *VMessBean:
		c := *b
		strip(&c.StandardV2RayBean)
		return &c
	case *TrojanBean:
		c := *b
		strip(&c.StandardV2RayBean)
		return &c
	case *HttpBean:
		c := *b
		strip(&c.StandardV2RayBean)
		return &c
	}
	return b
}

func strip(v *StandardV2RayBean) {
	switch str(v.Type) {
	case "ws", "http", "httpupgrade", "xhttp":
	case "grpc":
		v.Host = ptr("")
	default:
		v.Host, v.Path = ptr(""), ptr("")
	}
}

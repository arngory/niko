package nikofmt

import (
	"bytes"
	"compress/zlib"
	"encoding/base64"
	"encoding/json"
	"io"
	"os"
	"path/filepath"
	"sort"
	"strings"
	"testing"
)

// Each golden bean, written here, must give the app's <name>.share (LinksGoldenTest writes it): the same link for
// the kinds with links of their own; for sn:// the same type and the same Kryo bytes, as the zlib of the JVM and of
// Go differ. A link the app drops has no .share.
func TestGoldenShares(t *testing.T) {
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
			bean, err := ParseLink(ktTrim(javaUTF8(raw)), DefaultSettings)
			share, readErr := os.ReadFile(strings.TrimSuffix(txt, ".txt") + ".share")
			if err != nil {
				if readErr == nil {
					t.Fatalf("a link dropped here has a .share: %v", err)
				}
				return
			}
			if readErr != nil {
				t.Fatalf("no .share: run the app's io.niko.golden tests to write it (%v)", readErr)
			}
			want := strings.TrimSpace(string(share))
			got, err := ShareLink(bean)
			if err != nil {
				t.Fatalf("ShareLink: %v\nthe app's: %s", err, want)
			}
			if !strings.HasPrefix(want, "sn://") {
				if got != want {
					t.Errorf("\n got: %s\nwant: %s", got, want)
				}
				return
			}
			gotType, gotData := universalPayload(t, got)
			wantType, wantData := universalPayload(t, want)
			if gotType != wantType || !bytes.Equal(gotData, wantData) {
				t.Errorf("sn:// type %q, want %q; Kryo bytes\n got: %x\nwant: %x", gotType, wantType, gotData, wantData)
			}
			// and it reads back as the same bean
			again, err := ParseLink(got, DefaultSettings)
			if err != nil {
				t.Fatalf("Go's own sn:// does not read back: %v", err)
			}
			a, _ := json.Marshal(bean)
			b, _ := json.Marshal(again)
			if !bytes.Equal(a, b) {
				t.Errorf("read back\n got: %s\nwant: %s", b, a)
			}
		})
	}
}

// universalPayload: an sn:// link's type and its inflated Kryo bytes.
func universalPayload(t *testing.T, link string) (string, []byte) {
	t.Helper()
	rest := strings.TrimPrefix(link, "sn://")
	typ, data, ok := strings.Cut(rest, "?")
	if !ok {
		t.Fatalf("not an sn:// link: %s", link)
	}
	compressed, err := base64.RawURLEncoding.DecodeString(strings.TrimRight(data, "="))
	if err != nil {
		t.Fatalf("%s: %v", link, err)
	}
	r, err := zlib.NewReader(bytes.NewReader(compressed))
	if err != nil {
		t.Fatalf("%s: %v", link, err)
	}
	inflated, err := io.ReadAll(r)
	if err != nil {
		t.Fatalf("%s: %v", link, err)
	}
	return typ, inflated
}

// The Kryo writer against the reader: strings of each length class, chars of one, two and three bytes, a varint
// past one byte.
func TestKryoStringsRoundTrip(t *testing.T) {
	for _, s := range []*string{nil, ptr(""), ptr("a"), ptr("ab"), ptr(strings.Repeat("x", 32)), ptr(strings.Repeat("x", 33)),
		ptr("é"), ptr("日本語"), ptr("emoji 🙂"), ptr(strings.Repeat("я", 200))} {
		o := &kryoOutput{}
		o.writeString(s)
		got, err := newKryoInput(o.b).readString()
		if err != nil {
			t.Fatalf("%v: %v", s, err)
		}
		if (got == nil) != (s == nil) || (s != nil && *got != *s) {
			t.Fatalf("wrote %q, read %q", str(s), str(got))
		}
	}
}

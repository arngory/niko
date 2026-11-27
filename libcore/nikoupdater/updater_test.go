package nikoupdater

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"golang.org/x/crypto/blake2b"
)

type testMinisignKey struct {
	id   [8]byte
	pub  ed25519.PublicKey
	priv ed25519.PrivateKey
}

func generateTestKey(t *testing.T) testMinisignKey {
	t.Helper()
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	var id [8]byte
	if _, err := rand.Read(id[:]); err != nil {
		t.Fatal(err)
	}
	return testMinisignKey{id: id, pub: pub, priv: priv}
}

func (k testMinisignKey) publicKeyText() string {
	raw := make([]byte, 0, 2+8+ed25519.PublicKeySize)
	raw = append(raw, 'E', 'd')
	raw = append(raw, k.id[:]...)
	raw = append(raw, k.pub...)
	return "untrusted comment: test minisign key\n" + base64.StdEncoding.EncodeToString(raw) + "\n"
}

func (k testMinisignKey) sign(data []byte, trustedComment string) string {
	sum := blake2b.Sum512(data)
	sig := ed25519.Sign(k.priv, sum[:])
	raw := make([]byte, 0, 2+8+ed25519.SignatureSize)
	raw = append(raw, 'E', 'D')
	raw = append(raw, k.id[:]...)
	raw = append(raw, sig...)
	global := ed25519.Sign(k.priv, append(append([]byte(nil), sig...), trustedComment...))
	return fmt.Sprintf("untrusted comment: signature from test key\n%s\ntrusted comment: %s\n%s\n",
		base64.StdEncoding.EncodeToString(raw),
		trustedComment,
		base64.StdEncoding.EncodeToString(global),
	)
}

func shaHex(b []byte) string {
	s := sha256.Sum256(b)
	return hex.EncodeToString(s[:])
}

func TestCheckAndDownloadGitHubVerifiedAndTamperRejections(t *testing.T) {
	ctx := context.Background()
	key := generateTestKey(t)
	otherKey := generateTestKey(t)

	tag := "v0.2.0"
	assetName := "niko-0.2.0-linux-amd64.tar.gz"
	staleAsset := "niko-0.1.0-linux-amd64.tar.gz"
	assetBody := []byte("verified-niko-desktop-tarball-payload")
	assetHash := shaHex(assetBody)
	// Include a stale 0.1.0 line to verify exact versioned asset selection never picks it!
	sumsBody := []byte(fmt.Sprintf("%s  %s\n%s  %s\n", shaHex([]byte("old")), staleAsset, assetHash, assetName))
	validSig := key.sign(sumsBody, TrustedComment(tag))

	serveSums := sumsBody
	serveSig := validSig
	serveAsset := assetBody

	var server *httptest.Server
	server = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/repos/arngory/niko-desktop/releases/latest", "/repos/arngory/niko-desktop/releases/tags/" + tag:
			rel := ghRelease{
				TagName: tag,
				HTMLURL: server.URL + "/releases/" + tag,
				Body:    "Release 0.2.0 notes",
				Assets: []ghAsset{
					{Name: SumsFile, BrowserDownloadURL: server.URL + "/dl/" + SumsFile},
					{Name: SigFile, BrowserDownloadURL: server.URL + "/dl/" + SigFile},
					{Name: assetName, BrowserDownloadURL: server.URL + "/dl/" + assetName},
				},
			}
			_ = json.NewEncoder(w).Encode(rel)
		case "/dl/" + SumsFile:
			_, _ = w.Write(serveSums)
		case "/dl/" + SigFile:
			_, _ = w.Write([]byte(serveSig))
		case "/dl/" + assetName:
			_, _ = w.Write(serveAsset)
		default:
			http.NotFound(w, r)
		}
	}))
	defer server.Close()

	cfg := Config{
		PublicKey: key.publicKeyText(),
		APIBase:   server.URL,
		GOOS:      "linux",
		GOARCH:    "amd64",
		AllowHTTP: true,
	}

	// 1. Valid signed release check & download succeeds and picks exact 0.2.0 asset (never stale 0.1.0).
	info, err := CheckGitHub(ctx, cfg, "0.1.0", "")
	if err != nil {
		t.Fatalf("CheckGitHub valid: %v", err)
	}
	if !info.UpdateAvailable || !info.Verified || info.Tag != tag || info.AssetName != assetName || info.AssetSHA256 != assetHash {
		t.Fatalf("unexpected UpdateInfo: %+v", info)
	}
	upToDate, err := CheckGitHub(ctx, cfg, "0.2.0", "")
	if err != nil || upToDate.UpdateAvailable {
		t.Fatalf("expected up-to-date for 0.2.0, got (%+v, %v)", upToDate, err)
	}

	dest := t.TempDir()
	// Plant an old leftover archive to confirm pruning after successful download.
	_ = os.WriteFile(filepath.Join(dest, "niko-0.0.9-linux-amd64.tar.gz"), []byte("stale"), 0o600)
	dl, err := DownloadGitHub(ctx, cfg, tag, "", dest)
	if err != nil {
		t.Fatalf("DownloadGitHub valid: %v", err)
	}
	if !dl.Verified || dl.SHA256 != assetHash || dl.Size != int64(len(assetBody)) {
		t.Fatalf("unexpected Downloaded: %+v", dl)
	}
	gotBytes, err := os.ReadFile(dl.FilePath)
	if err != nil || string(gotBytes) != string(assetBody) {
		t.Fatalf("downloaded file mismatch: (%q, %v)", gotBytes, err)
	}
	if entries, _ := os.ReadDir(dest); len(entries) != 1 || entries[0].Name() != assetName {
		t.Fatalf("expected stale archive pruned, found %v", entries)
	}

	// 2. Android trusted comment ("niko v0.2.0") must be rejected by desktop ("niko-desktop v0.2.0")!
	serveSig = key.sign(sumsBody, "niko "+tag)
	if _, err := CheckGitHub(ctx, cfg, "0.1.0", ""); !errors.Is(err, ErrOtherRelease) {
		t.Fatalf("Android trusted comment = %v, want ErrOtherRelease", err)
	}

	// 3. Tampered SHA256SUMS rejected!
	serveSig = validSig
	serveSums = []byte(fmt.Sprintf("%s  %s\n", shaHex([]byte("evil")), assetName))
	if _, err := CheckGitHub(ctx, cfg, "0.1.0", ""); !errors.Is(err, ErrBadSignature) {
		t.Fatalf("tampered SHA256SUMS = %v, want ErrBadSignature", err)
	}
	serveSums = sumsBody

	// 4. Wrong minisign signing key rejected!
	serveSig = otherKey.sign(sumsBody, TrustedComment(tag))
	if _, err := CheckGitHub(ctx, cfg, "0.1.0", ""); !errors.Is(err, ErrBadSignature) {
		t.Fatalf("wrong key = %v, want ErrBadSignature", err)
	}

	// 5. Replayed signature for another tag rejected!
	serveSig = key.sign(sumsBody, TrustedComment("v0.1.9"))
	if _, err := CheckGitHub(ctx, cfg, "0.1.0", ""); !errors.Is(err, ErrOtherRelease) {
		t.Fatalf("wrong trusted comment = %v, want ErrOtherRelease", err)
	}
	serveSig = validSig

	// 6. Tampered binary payload rejected and .part file removed!
	destBad := t.TempDir()
	serveAsset = []byte("tampered-binary-payload")
	if _, err := DownloadGitHub(ctx, cfg, tag, "", destBad); !errors.Is(err, ErrBadHash) {
		t.Fatalf("tampered asset = %v, want ErrBadHash", err)
	}
	if entries, _ := os.ReadDir(destBad); len(entries) != 0 {
		t.Fatalf("expected tampered .part file cleaned up, found %v", entries)
	}
}

func TestDownloadFromMirrorVerifiedAndTamperRejected(t *testing.T) {
	ctx := context.Background()
	key := generateTestKey(t)
	tag := "v0.3.0"
	assetName := "niko-0.3.0-windows-amd64.zip"
	assetBody := []byte("windows-zip-bytes")
	assetHash := shaHex(assetBody)
	sumsBody := []byte(fmt.Sprintf("%s  %s\n", assetHash, assetName))
	sigText := key.sign(sumsBody, TrustedComment(tag))

	files := map[string][]byte{
		SumsFile:  sumsBody,
		SigFile:   []byte(sigText),
		assetName: assetBody,
	}
	fetch := func(_ context.Context, name, outPath, _ string) error {
		b, ok := files[name]
		if !ok {
			return os.ErrNotExist
		}
		return os.WriteFile(outPath, b, 0o600)
	}
	cfg := Config{PublicKey: key.publicKeyText(), GOOS: "windows", GOARCH: "amd64"}
	dest := t.TempDir()
	dl, err := DownloadFromMirror(ctx, cfg, tag, "", dest, fetch)
	if err != nil {
		t.Fatalf("DownloadFromMirror valid: %v", err)
	}
	if dl.Source != "mirror" || dl.FilePath != filepath.Join(dest, assetName) {
		t.Fatalf("unexpected mirror result: %+v", dl)
	}

	// Tampered asset from mirror must be rejected.
	files[assetName] = []byte("corrupted-mirror-zip")
	if _, err := DownloadFromMirror(ctx, cfg, tag, "", t.TempDir(), fetch); !errors.Is(err, ErrBadHash) {
		t.Fatalf("tampered mirror asset = %v, want ErrBadHash", err)
	}
}

func TestCompareVersionsSemver11(t *testing.T) {
	cases := []struct {
		a, b string
		want int
	}{
		{"0.1.0", "0.2.0", -1},
		{"v0.2.0", "0.2.0", 0},
		{"0.2.0-rc.1", "0.2.0", -1},
		{"0.2.0-rc.9", "0.2.0-rc.10", -1},
		{"0.2.0-alpha.1", "0.2.0-alpha.beta", -1},
		{"0.2.0-alpha", "0.2.0-alpha.1", -1},
		{"1.0.0", "0.9.9", 1},
	}
	for _, tc := range cases {
		if got := CompareVersions(tc.a, tc.b); got != tc.want {
			t.Errorf("CompareVersions(%q, %q) = %d, want %d", tc.a, tc.b, got, tc.want)
		}
	}
}

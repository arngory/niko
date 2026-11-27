package nikoservice

import (
	"bytes"
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"libcore/nikofmt"
	"libcore/nikopb"
	"libcore/nikoray"
	"libcore/nikoupdater"

	"golang.org/x/crypto/blake2b"
	"google.golang.org/grpc/codes"
)

func testSignMinisig(t *testing.T, id [8]byte, priv ed25519.PrivateKey, data []byte, comment string) string {
	t.Helper()
	sum := blake2b.Sum512(data)
	sig := ed25519.Sign(priv, sum[:])
	raw := make([]byte, 0, 2+8+ed25519.SignatureSize)
	raw = append(raw, 'E', 'D')
	raw = append(raw, id[:]...)
	raw = append(raw, sig...)
	global := ed25519.Sign(priv, append(append([]byte(nil), sig...), comment...))
	return fmt.Sprintf("untrusted comment: test\n%s\ntrusted comment: %s\n%s\n",
		base64.StdEncoding.EncodeToString(raw),
		comment,
		base64.StdEncoding.EncodeToString(global),
	)
}

func TestUpdaterRPCsStreamingDowngradeAndMirrorPayload(t *testing.T) {
	ctx := context.Background()
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	if err != nil {
		t.Fatal(err)
	}
	var keyID [8]byte
	_, _ = rand.Read(keyID[:])
	pubRaw := append(append([]byte{'E', 'd'}, keyID[:]...), pub...)
	pubText := "untrusted comment: test\n" + base64.StdEncoding.EncodeToString(pubRaw) + "\n"

	tag := "v0.2.5"
	asset := "niko-0.2.5-linux-amd64.tar.gz"
	payload := bytes.Repeat([]byte("niko-desktop-release-bytes-"), 20000) // ~540 KiB (>2 chunks)
	sum := sha256.Sum256(payload)
	sumHex := hex.EncodeToString(sum[:])
	sumsText := fmt.Sprintf("%s  %s\n", sumHex, asset)
	sigText := testSignMinisig(t, keyID, priv, []byte(sumsText), nikoupdater.TrustedComment(tag))

	tamperSig := false
	failGitHub := false
	var ts *httptest.Server
	ts = httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if failGitHub {
			http.Error(w, "unreachable", http.StatusBadGateway)
			return
		}
		switch r.URL.Path {
		case "/repos/arngory/niko-desktop/releases/latest", "/repos/arngory/niko-desktop/releases/tags/" + tag:
			_ = json.NewEncoder(w).Encode(map[string]any{
				"tag_name": tag,
				"html_url": ts.URL + "/releases/" + tag,
				"body":     "New features",
				"assets": []map[string]string{
					{"name": nikoupdater.SumsFile, "browser_download_url": ts.URL + "/dl/SHA256SUMS"},
					{"name": nikoupdater.SigFile, "browser_download_url": ts.URL + "/dl/SHA256SUMS.minisig"},
					{"name": asset, "browser_download_url": ts.URL + "/dl/" + asset},
				},
			})
		case "/dl/SHA256SUMS":
			_, _ = w.Write([]byte(sumsText))
		case "/dl/SHA256SUMS.minisig":
			if tamperSig {
				_, _ = w.Write([]byte(testSignMinisig(t, keyID, priv, []byte("wrong"), nikoupdater.TrustedComment(tag))))
			} else {
				_, _ = w.Write([]byte(sigText))
			}
		case "/dl/" + asset:
			_, _ = w.Write(payload)
		default:
			http.NotFound(w, r)
		}
	}))
	defer ts.Close()

	storeDir := t.TempDir()
	srv := storeServer(storeDir)
	srv.updaterCfg = &nikoupdater.Config{
		PublicKey: pubText,
		APIBase:   ts.URL,
		GOOS:      "linux",
		GOARCH:    "amd64",
		AllowHTTP: true,
		Transport: ts.Client().Transport,
	}
	client := serve(t, srv)

	// 1. CheckUpdate succeeds & verifies signature.
	info, err := client.CheckUpdate(ctx, &nikopb.CheckUpdateRequest{CurrentVersion: "0.1.0"})
	if err != nil {
		t.Fatalf("CheckUpdate: %v", err)
	}
	if !info.UpdateAvailable || !info.Verified || info.Tag != tag || info.AssetSha256 != sumHex {
		t.Fatalf("unexpected UpdateInfo: %+v", info)
	}

	// 2. Downgrade / same-version download must be rejected with FailedPrecondition!
	badStream, err := client.DownloadUpdate(ctx, &nikopb.DownloadUpdateRequest{Tag: tag, CurrentVersion: "0.2.5"})
	if err == nil {
		_, err = badStream.Recv()
	}
	if code(err) != codes.FailedPrecondition {
		t.Fatalf("same-version DownloadUpdate = %v, want FailedPrecondition", err)
	}

	// 3. Valid DownloadUpdate streams verified chunks and cleans up daemon's staging file.
	dlStream, err := client.DownloadUpdate(ctx, &nikopb.DownloadUpdateRequest{Tag: tag, CurrentVersion: "0.1.0"})
	if err != nil {
		t.Fatalf("DownloadUpdate: %v", err)
	}
	var got bytes.Buffer
	var final *nikopb.DownloadUpdateEvent
	for {
		ev, err := dlStream.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatalf("DownloadUpdate.Recv: %v", err)
		}
		got.Write(ev.Chunk)
		if ev.Done {
			final = ev
		}
	}
	if final == nil || !final.Verified || final.Sha256 != sumHex || !bytes.Equal(got.Bytes(), payload) {
		t.Fatalf("streamed update mismatch: final=%+v gotLen=%d wantLen=%d", final, got.Len(), len(payload))
	}
	// Staged file inside daemon store must have been removed after streaming.
	u, _ := srv.users.user(ctx)
	if entries, _ := os.ReadDir(filepath.Join(u.store.Dir(), "updates")); len(entries) != 0 {
		t.Fatalf("expected daemon staging dir cleaned after stream, found %v", entries)
	}

	// 4. Tampered signature fails with PermissionDenied and NEVER falls back to mirror.
	tamperSig = true
	if _, err := client.CheckUpdate(ctx, &nikopb.CheckUpdateRequest{CurrentVersion: "0.1.0"}); code(err) != codes.PermissionDenied {
		t.Fatalf("tampered CheckUpdate = %v, want PermissionDenied", err)
	}
	tamperSig = false

	// 5. Real app-update-v1 payload parsed via nikoray.Parse feeds CheckUpdate mirror fallback when GitHub is down!
	node := importSampleNode(t, srv, u)
	rawSub := []byte(`{
		"issued": 1700000000,
		"expires": 1900000000,
		"profiles": [],
		"app": {
			"niko-desktop": [
				{"tag": "v0.2.5", "prerelease": false},
				{"tag": "v0.3.0-rc.1", "prerelease": true},
				{"tag": "v0.9.9-bad", "prerelease": false}
			]
		}
	}`)
	parsed, err := nikoray.Parse(rawSub, node.Kinds(), 1750000000, nikofmt.DefaultSettings)
	if err != nil {
		t.Fatalf("nikoray.Parse real app-update-v1: %v", err)
	}
	if len(parsed.AppReleases) != 2 || parsed.AppReleases[0].Tag != "v0.2.5" || parsed.AppReleases[1].Tag != "v0.3.0-rc.1" {
		t.Fatalf("unexpected AppReleases from real payload: %+v", parsed.AppReleases)
	}
	_, _ = u.store.UpdateNode(node.ID(), func(n *nikoray.Node) bool {
		n.AppReleases = parsed.AppReleases
		return true
	})

	failGitHub = true
	mirrorInfo, err := client.CheckUpdate(ctx, &nikopb.CheckUpdateRequest{CurrentVersion: "0.1.0"})
	if err != nil {
		t.Fatalf("mirror CheckUpdate fallback: %v", err)
	}
	if mirrorInfo.Source != "mirror" || mirrorInfo.Tag != "v0.2.5" || mirrorInfo.Verified {
		t.Fatalf("unexpected mirror fallback UpdateInfo (must skip prerelease and mark Verified=false): %+v", mirrorInfo)
	}
}

func TestPresetsChinaIranAdsAndAdsPrependOrder(t *testing.T) {
	ctx := context.Background()
	srv := storeServer(t.TempDir())
	client := serve(t, srv)

	// China preset adds 2 rules (proxy non-CN, direct CN) with srs: rule-sets.
	cn, err := client.ApplyPreset(ctx, &nikopb.PresetRequest{Preset: nikopb.PresetRequest_CHINA})
	if err != nil || len(cn.Rules) != 2 || len(cn.RuleSets) != 3 {
		t.Fatalf("ApplyPreset(CHINA) = (%+v, %v)", cn, err)
	}
	// Iran preset adds 1 direct rule with 2 srs: rule-sets.
	ir, err := client.ApplyPreset(ctx, &nikopb.PresetRequest{Preset: nikopb.PresetRequest_IRAN})
	if err != nil || len(ir.Rules) != 1 || len(ir.RuleSets) != 2 {
		t.Fatalf("ApplyPreset(IRAN) = (%+v, %v)", ir, err)
	}
	// Ads preset adds 1 block rule (-2) PREPENDED at order 1 ahead of regional rules!
	ads, err := client.ApplyPreset(ctx, &nikopb.PresetRequest{Preset: nikopb.PresetRequest_ADS})
	if err != nil || len(ads.Rules) != 1 || ads.Rules[0].Outbound != -2 || ads.Rules[0].Order != 1 ||
		!strings.Contains(ads.Rules[0].Domains, "category-ads-all") {
		t.Fatalf("ApplyPreset(ADS) = (%+v, %v)", ads, err)
	}
	// Re-applying Ads replaces cleanly at order 1 without duplicating.
	if _, err := client.ApplyPreset(ctx, &nikopb.PresetRequest{Preset: nikopb.PresetRequest_ADS}); err != nil {
		t.Fatal(err)
	}
	rules, err := client.ListRules(ctx, &nikopb.Empty{})
	if err != nil || len(rules.Rules) != 4 || rules.Rules[0].Outbound != -2 {
		t.Fatalf("expected 4 total rules with Ads first (order 1), got %+v (err=%v)", rules.Rules, err)
	}
}

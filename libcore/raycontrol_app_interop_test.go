package libcore

import (
	"bytes"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"os"
	"path/filepath"
	"regexp"
	"strings"
	"testing"

	"libcore/knock"
	"libcore/raysub"
)

// TestRaycontrolAppMirror is app-update-v1 against a rayd whose mirror took a release from a source of the test
// (raycontrol-interop.yml: a GitHub of its own on the runner, the release RAYCONTROL_APP_TAG signed by the test's key
// RAYCONTROL_APP_KEY): the subscription names it, its files come the app's way (RaycontrolAppFile: SPA, the pin,
// RC1), the signature holds and is for "niko <tag>", the APK's hash is SHA256SUMS's; a download cut short goes on from
// its part; a name not in the release is the node's 404.
func TestRaycontrolAppMirror(t *testing.T) {
	tag := os.Getenv("RAYCONTROL_APP_TAG")
	if tag == "" {
		t.Skip("RAYCONTROL_APP_TAG not set (raycontrol-interop.yml sets it where the node has app-update-v1)")
	}
	publicKey := os.Getenv("RAYCONTROL_APP_KEY")
	node := os.Getenv("RAYCONTROL_ADDRESS")
	raw, err := os.ReadFile(os.Getenv("RAYCONTROL_BUNDLE"))
	if err != nil {
		t.Fatal(err)
	}
	body, err := base64.RawURLEncoding.DecodeString(strings.TrimPrefix(strings.TrimSpace(string(raw)), "rcdev1:"))
	if err != nil {
		t.Fatal("package: ", err)
	}
	var b struct {
		DeviceID  string             `json:"device_id"`
		DeviceKey string             `json:"device_key"`
		SPA       knock.SPAv2Options `json:"spa"`
		Sub       struct {
			Port int    `json:"port"`
			Pin  string `json:"pin"`
		} `json:"sub"`
		NodeKeys struct {
			Ed25519 string `json:"ed25519"`
			MLDSA65 string `json:"mldsa65"`
		} `json:"node_keys"`
	}
	if err = json.Unmarshal(body, &b); err != nil || b.Sub.Port == 0 {
		t.Fatalf("package: %v", err)
	}
	b.SPA.DeviceKey = b.DeviceKey
	request, _ := json.Marshal(raysub.Request{
		Host: node, Port: b.Sub.Port, Pin: b.Sub.Pin, DeviceID: b.DeviceID, NodeID: b.SPA.NodeID,
		Ed25519: b.NodeKeys.Ed25519, MLDSA65: b.NodeKeys.MLDSA65, SPA: b.SPA,
	})

	// the subscription names the release, its flag as its tag's
	appJSON, err := RaycontrolFetchSubscription(string(request))
	if err != nil {
		t.Fatalf("subscription: %v", err)
	}
	var sub struct {
		Payload struct {
			App struct {
				Niko []struct {
					Tag        string `json:"tag"`
					Prerelease bool   `json:"prerelease"`
				} `json:"niko"`
			} `json:"app"`
		} `json:"payload"`
	}
	if err = json.Unmarshal([]byte(appJSON), &sub); err != nil {
		t.Fatal(err)
	}
	named := false
	for _, r := range sub.Payload.App.Niko {
		if r.Tag == tag && r.Prerelease == strings.Contains(tag, "-") {
			named = true
		}
	}
	if !named {
		t.Fatalf("the subscription's app.niko %+v, want %s", sub.Payload.App.Niko, tag)
	}

	dir := t.TempDir()
	file := func(name, etag string) string {
		out := filepath.Join(dir, name)
		if err := RaycontrolAppFile(string(request), tag, name, out, etag); err != nil {
			t.Fatalf("%s: %v", name, err)
		}
		return out
	}
	sums, _ := os.ReadFile(file("SHA256SUMS", ""))
	signature, _ := os.ReadFile(file("SHA256SUMS.minisig", ""))
	trusted, err := MinisignVerify(publicKey, string(signature), sums)
	if err != nil {
		t.Fatalf("the signature: %v", err)
	}
	if trusted != "niko "+tag {
		t.Fatalf("signed for %q, want niko %s", trusted, tag)
	}
	// the APK of the app's first ABI, as Updates.apkFor takes it
	apk := regexp.MustCompile(`(?m)^([0-9a-f]{64}) [ *](Niko-[0-9A-Za-z.-]+-arm64-v8a\.apk)$`).FindSubmatch(sums)
	if apk == nil {
		t.Fatalf("no arm64-v8a APK in SHA256SUMS:\n%s", sums)
	}
	name, hash := string(apk[2]), string(apk[1])
	etag := `"` + hash + `"`
	check := func(what, path string) {
		t.Helper()
		content, _ := os.ReadFile(path)
		sum := sha256.Sum256(content)
		if hex.EncodeToString(sum[:]) != hash {
			t.Fatalf("%s: %d bytes, not the signed %s", what, len(content), name)
		}
	}
	path := file(name, etag)
	check("whole", path)
	t.Logf("%s of %s: %s signed for %q, %s whole", node, tag, "SHA256SUMS", trusted, name)

	// cut to its first half: the rest only (Range, If-Range by the hash), the file whole again
	whole, _ := os.ReadFile(path)
	if err = os.WriteFile(path, whole[:len(whole)/2], 0o600); err != nil {
		t.Fatal(err)
	}
	file(name, etag)
	check("gone on from its half", path)
	// a part of another file under the same name: If-Range does not hold, the node sends it whole
	if err = os.WriteFile(path, bytes.Repeat([]byte{0}, len(whole)/3), 0o600); err != nil {
		t.Fatal(err)
	}
	file(name, `"`+strings.Repeat("0", 64)+`"`)
	check("another file's part", path)

	// not in the release: the node's 404, not asked again elsewhere
	err = RaycontrolAppFile(string(request), tag, "Niko-0.0.0-mips.apk", filepath.Join(dir, "x"), "")
	if err == nil || !strings.Contains(err.Error(), "does not hold") {
		t.Fatalf("a file not in the release: %v, want the node's 404", err)
	}
	if err = RaycontrolAppFile(string(request), "v0.0.1", "SHA256SUMS", filepath.Join(dir, "y"), ""); err == nil ||
		!strings.Contains(err.Error(), "does not hold") {
		t.Fatalf("a release the node does not hold: %v, want its 404", err)
	}
}

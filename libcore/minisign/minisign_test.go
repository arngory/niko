package minisign

import (
	"bytes"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"os"
	"strings"
	"testing"

	"golang.org/x/crypto/blake2b"
)

// raycontrolKey is the public key of raycontrol's releases (its install/bootstrap.sh, key ID A9C7802E23B87A01); the
// testdata are its v0.13.0 release's SHA256SUMS and their signature, made by minisign itself
const raycontrolKey = "RWQBergjLoDHqeme5vyIuIRXJLjTj1EF3jR1nlWBVJO3rWxgy9SZj4bD"

func testdata(t *testing.T, name string) []byte {
	t.Helper()
	b, err := os.ReadFile("testdata/" + name)
	if err != nil {
		t.Fatal(err)
	}
	return b
}

// sign is what minisign writes for data: alg "ED" over its BLAKE2b-512, "Ed" over the data itself
func sign(priv ed25519.PrivateKey, id [8]byte, alg string, data []byte, trusted string) string {
	message := data
	if alg == "ED" {
		sum := blake2b.Sum512(data)
		message = sum[:]
	}
	return sigFile(priv, id, alg, ed25519.Sign(priv, message), trusted)
}

// sigFile is a signature file of signature under the label alg
func sigFile(priv ed25519.PrivateKey, id [8]byte, alg string, signature []byte, trusted string) string {
	raw := append(append([]byte(alg), id[:]...), signature...)
	global := ed25519.Sign(priv, append(bytes.Clone(signature), trusted...))
	return untrustedPrefix + "signature from minisign secret key\n" + base64.StdEncoding.EncodeToString(raw) + "\n" +
		trustedPrefix + trusted + "\n" + base64.StdEncoding.EncodeToString(global) + "\n"
}

func publicKeyText(pub ed25519.PublicKey, id [8]byte) string {
	return untrustedPrefix + "minisign public key\n" +
		base64.StdEncoding.EncodeToString(append(append([]byte("Ed"), id[:]...), pub...)) + "\n"
}

func TestMinisignItself(t *testing.T) {
	key, err := ParsePublicKey(raycontrolKey)
	if err != nil {
		t.Fatal(err)
	}
	data := testdata(t, "raycontrol-v0.13.0.SHA256SUMS")
	sig := string(testdata(t, "raycontrol-v0.13.0.SHA256SUMS.minisig"))
	trusted, err := Verify(key, sig, data)
	if err != nil || trusted != "raycontrol v0.13.0" {
		t.Fatalf("%q %v", trusted, err)
	}
	// with Windows' line ends
	if trusted, err := Verify(key, strings.ReplaceAll(sig, "\n", "\r\n"), data); err != nil || trusted != "raycontrol v0.13.0" {
		t.Fatalf("CRLF: %q %v", trusted, err)
	}
	changed := bytes.Clone(data)
	changed[0] ^= 1
	if _, err := Verify(key, sig, changed); !errors.Is(err, ErrSignature) {
		t.Fatalf("a changed file: %v", err)
	}
	// the trusted comment is what it signs for: another version under the same signature is refused
	if _, err := Verify(key, strings.Replace(sig, "raycontrol v0.13.0", "raycontrol v0.14.0", 1), data); !errors.Is(err, ErrTrustedComment) {
		t.Fatalf("another trusted comment: %v", err)
	}
	// the untrusted comment is not
	if _, err := Verify(key, strings.Replace(sig, "signature from minisign secret key", "anything", 1), data); err != nil {
		t.Fatalf("another untrusted comment: %v", err)
	}
}

func TestBothAlgorithms(t *testing.T) {
	pub, priv, _ := ed25519.GenerateKey(rand.Reader)
	id := [8]byte{1, 2, 3, 4, 5, 6, 7, 8}
	key, err := ParsePublicKey(publicKeyText(pub, id))
	if err != nil {
		t.Fatal(err)
	}
	data := []byte("Niko-1.36.0-arm64-v8a.apk\n")
	for _, alg := range []string{"ED", "Ed"} {
		if trusted, err := Verify(key, sign(priv, id, alg, data, "niko v1.36.0"), data); err != nil || trusted != "niko v1.36.0" {
			t.Fatalf("%s: %q %v", alg, trusted, err)
		}
	}
	// a trusted comment with spaces at its end, signed so
	if trusted, err := Verify(key, sign(priv, id, "ED", data, "niko v1.36.0  "), data); err != nil || trusted != "niko v1.36.0  " {
		t.Fatalf("spaces: %q %v", trusted, err)
	}
}

func TestRefusals(t *testing.T) {
	pub, priv, _ := ed25519.GenerateKey(rand.Reader)
	otherPub, otherPriv, _ := ed25519.GenerateKey(rand.Reader)
	id := [8]byte{8, 7, 6, 5, 4, 3, 2, 1}
	key, _ := ParsePublicKey(publicKeyText(pub, id))
	data := []byte("data")
	good := sign(priv, id, "ED", data, "niko v1.36.0")

	if _, err := Verify(key, sign(priv, [8]byte{9}, "ED", data, "t"), data); !errors.Is(err, ErrOtherKey) {
		t.Fatalf("another key ID: %v", err)
	}
	if _, err := Verify(key, sign(otherPriv, id, "ED", data, "t"), data); !errors.Is(err, ErrSignature) {
		t.Fatalf("another key under the same ID: %v", err)
	}
	if _, err := Verify(key, sign(priv, id, "Ex", data, "t"), data); !errors.Is(err, ErrAlgorithm) {
		t.Fatalf("an unknown algorithm: %v", err)
	}
	// the label says what was signed: a signature over the data itself labeled prehashed does not hold, nor one over
	// its hash labeled pure
	sum := blake2b.Sum512(data)
	if _, err := Verify(key, sigFile(priv, id, "ED", ed25519.Sign(priv, data), "t"), data); !errors.Is(err, ErrSignature) {
		t.Fatalf("a pure signature labeled prehashed: %v", err)
	}
	if _, err := Verify(key, sigFile(priv, id, "Ed", ed25519.Sign(priv, sum[:]), "t"), data); !errors.Is(err, ErrSignature) {
		t.Fatalf("a prehashed signature labeled pure: %v", err)
	}
	l := strings.Split(good, "\n")
	for name, sig := range map[string]string{
		"three lines":                 strings.Join(l[:3], "\n"),
		"no untrusted comment":        strings.Join(append([]string{"comment"}, l[1:]...), "\n"),
		"no trusted comment":          strings.Join([]string{l[0], l[1], "comment: x", l[3]}, "\n"),
		"a signature not base64":      strings.Join([]string{l[0], "!!", l[2], l[3]}, "\n"),
		"a short signature":           strings.Join([]string{l[0], base64.StdEncoding.EncodeToString([]byte("EDshort")), l[2], l[3]}, "\n"),
		"a global signature cut":      strings.Join([]string{l[0], l[1], l[2], l[3][:20]}, "\n"),
		"something after":             good + "more\n",
		"an empty global signature":   strings.Join([]string{l[0], l[1], l[2], ""}, "\n"),
		"another key's global":        strings.Join([]string{l[0], l[1], l[2], base64.StdEncoding.EncodeToString(ed25519.Sign(otherPriv, []byte("x")))}, "\n"),
		"the global over nothing too": strings.Join([]string{l[0], l[1], trustedPrefix, l[3]}, "\n"),
	} {
		if _, err := Verify(key, sig, data); err == nil {
			t.Fatalf("%s: taken", name)
		}
	}
	for name, text := range map[string]string{
		"two keys":       publicKeyText(pub, id) + publicKeyText(otherPub, id),
		"not base64":     "!!",
		"too short":      base64.StdEncoding.EncodeToString([]byte("Ed1234567")),
		"another scheme": base64.StdEncoding.EncodeToString(append(append([]byte("Ex"), id[:]...), pub...)),
		"empty":          "",
	} {
		if _, err := ParsePublicKey(text); !errors.Is(err, ErrNotPublicKey) {
			t.Fatalf("a public key %s: %v", name, err)
		}
	}
	if _, err := Verify(PublicKey{}, good, data); err == nil {
		t.Fatal("no key")
	}
}

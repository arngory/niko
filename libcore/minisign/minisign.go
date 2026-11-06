// Package minisign checks minisign signatures (https://jedisct1.github.io/minisign/): Ed25519 over the file (the
// algorithm "Ed") or over its BLAKE2b-512 (the algorithm "ED", minisign's default), and over the signature with its
// trusted comment. The app's updates come as SHA256SUMS signed so (release.yml).
package minisign

import (
	"bytes"
	"crypto/ed25519"
	"encoding/base64"
	"errors"
	"strings"

	"golang.org/x/crypto/blake2b"
)

const (
	untrustedPrefix = "untrusted comment: "
	trustedPrefix   = "trusted comment: "
)

var (
	ErrNotPublicKey   = errors.New("minisign: not a public key")
	ErrNotSignature   = errors.New("minisign: not a signature file")
	ErrOtherKey       = errors.New("minisign: signed with another key")
	ErrAlgorithm      = errors.New("minisign: unknown signature algorithm")
	ErrSignature      = errors.New("minisign: the signature does not hold")
	ErrTrustedComment = errors.New("minisign: the trusted comment does not hold")
)

// PublicKey is a minisign public key: its ID and its Ed25519 key.
type PublicKey struct {
	ID  [8]byte
	Key ed25519.PublicKey
}

// lines is text split into lines, without a carriage return at their ends
func lines(text string) []string {
	out := strings.Split(text, "\n")
	for i, line := range out {
		out[i] = strings.TrimSuffix(line, "\r")
	}
	return out
}

// ParsePublicKey takes a .pub file's text, or its key line alone.
func ParsePublicKey(text string) (PublicKey, error) {
	key := ""
	for _, line := range lines(text) {
		line = strings.TrimSpace(line)
		if line == "" || strings.HasPrefix(line, untrustedPrefix) {
			continue
		}
		if key != "" {
			return PublicKey{}, ErrNotPublicKey
		}
		key = line
	}
	raw, err := base64.StdEncoding.DecodeString(key)
	if err != nil || len(raw) != 2+8+ed25519.PublicKeySize || string(raw[:2]) != "Ed" {
		return PublicKey{}, ErrNotPublicKey
	}
	var out PublicKey
	copy(out.ID[:], raw[2:10])
	out.Key = ed25519.PublicKey(bytes.Clone(raw[10:]))
	return out, nil
}

// Verify is the trusted comment of sig (a .minisig file's text) over data under key, or an error when the signature
// or its trusted comment does not hold.
func Verify(key PublicKey, sig string, data []byte) (string, error) {
	l := lines(sig)
	// the untrusted comment, the signature, the trusted comment, the global signature; nothing after but a newline
	if len(l) < 4 || !strings.HasPrefix(l[0], untrustedPrefix) || !strings.HasPrefix(l[2], trustedPrefix) {
		return "", ErrNotSignature
	}
	for _, rest := range l[4:] {
		if strings.TrimSpace(rest) != "" {
			return "", ErrNotSignature
		}
	}
	raw, err := base64.StdEncoding.DecodeString(strings.TrimSpace(l[1]))
	if err != nil || len(raw) != 2+8+ed25519.SignatureSize {
		return "", ErrNotSignature
	}
	if !bytes.Equal(raw[2:10], key.ID[:]) {
		return "", ErrOtherKey
	}
	signature := raw[10:]
	message := data
	switch string(raw[:2]) {
	case "ED":
		sum := blake2b.Sum512(data)
		message = sum[:]
	case "Ed":
	default:
		return "", ErrAlgorithm
	}
	if len(key.Key) != ed25519.PublicKeySize || !ed25519.Verify(key.Key, message, signature) {
		return "", ErrSignature
	}
	// signed as written after its prefix, to the end of its line
	trusted := l[2][len(trustedPrefix):]
	global, err := base64.StdEncoding.DecodeString(strings.TrimSpace(l[3]))
	if err != nil || len(global) != ed25519.SignatureSize ||
		!ed25519.Verify(key.Key, append(bytes.Clone(signature), trusted...), global) {
		return "", ErrTrustedComment
	}
	return trusted, nil
}

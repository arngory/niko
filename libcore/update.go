package libcore

import "libcore/minisign"

// MinisignVerify is the trusted comment of signature (a .minisig file's text) over data under publicKey (a .pub
// file's text, or its key line), or an error when the signature or its trusted comment does not hold: the app's
// updates, SHA256SUMS signed by release.yml.
func MinisignVerify(publicKey string, signature string, data []byte) (string, error) {
	key, err := minisign.ParsePublicKey(publicKey)
	if err != nil {
		return "", err
	}
	return minisign.Verify(key, signature, data)
}

package nikofmt

import (
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"strings"
	"unicode"
	"unicode/utf16"
)

// pinItems are a pin list's items: split at commas, line ends, spaces and tabs, each trimmed, the empty ones left out.
func pinItems(s string) []string {
	var out []string
	for _, part := range strings.FieldsFunc(s, func(r rune) bool { return strings.ContainsRune(",\n \t", r) }) {
		if part = ktTrim(part); part != "" {
			out = append(out, part)
		}
	}
	return out
}

// pinHexDigits: each char (UTF-16) a digit (Kotlin's isDigit, any script's) or a to f in either case.
func pinHexDigits(s string) bool {
	for _, c := range utf16.Encode([]rune(s)) {
		r := rune(c)
		if utf16.IsSurrogate(r) || !unicode.IsDigit(r) && !(r >= 'a' && r <= 'f' || r >= 'A' && r <= 'F') {
			return false
		}
	}
	return true
}

// ParseCertificatePins is CertificatePinning.kt parseCertificatePins: SHA-256 hashes of the server's public key, as base64 or hex (colons
// allowed), one per line or comma separated; base64 for sing-box.
func ParseCertificatePins(text string) ([]string, error) {
	pins := []string{}
	for _, pin := range pinItems(text) {
		h := strings.ReplaceAll(pin, ":", "")
		var raw []byte
		if len(utf16.Encode([]rune(h))) == 64 && pinHexDigits(h) {
			// okio's decodeHex: another script's digit passes the check above, then fails here
			for _, r := range h {
				if r > unicode.MaxASCII {
					return nil, fmt.Errorf("Unexpected hex digit: %c", r)
				}
			}
			raw, _ = hex.DecodeString(h)
		} else {
			raw = okioDecodeBase64(pin)
		}
		if len(raw) != 32 {
			return nil, errors.New("not a SHA-256 hash: " + pin)
		}
		pins = append(pins, base64.StdEncoding.EncodeToString(raw))
	}
	return pins, nil
}

// okioDecodeBase64 is okio's decodeBase64: standard or URL-safe letters, padding and whitespace at the end left out,
// whitespace inside skipped; nil where okio gives null.
func okioDecodeBase64(s string) []byte {
	chars := utf16.Encode([]rune(s))
	limit := len(chars)
	for limit > 0 {
		if c := chars[limit-1]; c != '=' && c != '\n' && c != '\r' && c != ' ' && c != '\t' {
			break
		}
		limit--
	}
	var out []byte
	word, count := 0, 0
	for _, c := range chars[:limit] {
		var bits int
		switch {
		case c >= 'A' && c <= 'Z':
			bits = int(c) - 65
		case c >= 'a' && c <= 'z':
			bits = int(c) - 71
		case c >= '0' && c <= '9':
			bits = int(c) + 4
		case c == '+' || c == '-':
			bits = 62
		case c == '/' || c == '_':
			bits = 63
		case c == '\n' || c == '\r' || c == ' ' || c == '\t':
			continue
		default:
			return nil
		}
		word = word<<6 | bits
		if count++; count%4 == 0 {
			out = append(out, byte(word>>16), byte(word>>8), byte(word))
		}
	}
	switch count % 4 {
	case 1:
		return nil // 6 bits are a truncated byte
	case 2:
		word <<= 12
		out = append(out, byte(word>>16))
	case 3:
		word <<= 6
		out = append(out, byte(word>>16), byte(word>>8))
	}
	return out
}

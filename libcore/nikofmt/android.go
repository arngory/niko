package nikofmt

import (
	"errors"
	"strings"
)

// What the app's parsers get from Android itself and from its own small helpers on top of it (utils/Util.kt,
// utils/NGUtil.kt): android.util.Base64's decoder, which is not Go's.

var errBase64 = errors.New("bad base-64")

// androidBase64Decode is android.util.Base64.decode(str, flags) of a string (its bytes in UTF-8): the standard
// alphabet, or with urlSafe "-" and "_" for "+" and "/". Any other byte is skipped, a whitespace or not, anywhere;
// '=' ends the data only where padding may stand (after two or three characters of a quantum, then nothing but
// skipped bytes), and padding may be left out. A quantum of one character is an error.
func androidBase64Decode(s string, urlSafe bool) ([]byte, error) {
	var out []byte
	state, value := 0, 0
	for i := 0; i < len(s); i++ {
		d := androidBase64Value(s[i], urlSafe)
		switch state {
		case 0, 1:
			if d >= 0 {
				value = value<<6 | d
				state++
			} else if d != base64Skip {
				return nil, errBase64
			}
		case 2:
			if d >= 0 {
				value = value<<6 | d
				state++
			} else if d == base64Equals {
				out = append(out, byte(value>>4))
				state = 4
			} else if d != base64Skip {
				return nil, errBase64
			}
		case 3:
			if d >= 0 {
				value = value<<6 | d
				out = append(out, byte(value>>16), byte(value>>8), byte(value))
				state, value = 0, 0
			} else if d == base64Equals {
				out = append(out, byte(value>>10), byte(value>>2))
				state = 5
			} else if d != base64Skip {
				return nil, errBase64
			}
		case 4:
			if d == base64Equals {
				state++
			} else if d != base64Skip {
				return nil, errBase64
			}
		case 5:
			if d != base64Skip {
				return nil, errBase64
			}
		}
	}
	switch state {
	case 1, 4:
		return nil, errBase64
	case 2:
		out = append(out, byte(value>>4))
	case 3:
		out = append(out, byte(value>>10), byte(value>>2))
	}
	return out, nil
}

const (
	base64Skip   = -1
	base64Equals = -2
)

func androidBase64Value(c byte, urlSafe bool) int {
	switch {
	case c >= 'A' && c <= 'Z':
		return int(c - 'A')
	case c >= 'a' && c <= 'z':
		return int(c-'a') + 26
	case c >= '0' && c <= '9':
		return int(c-'0') + 52
	case c == '=':
		return base64Equals
	case c == '+' && !urlSafe, c == '-' && urlSafe:
		return 62
	case c == '/' && !urlSafe, c == '_' && urlSafe:
		return 63
	}
	return base64Skip
}

// b64Decode is Util.b64Decode: "-" and "_" made "+" and "/", then Android's decoder (DEFAULT and NO_WRAP decode
// alike); its error is the IllegalStateException that drops the link.
func b64Decode(s string) ([]byte, error) {
	b, err := androidBase64Decode(strings.NewReplacer("-", "+", "_", "/").Replace(s), false)
	if err != nil {
		return nil, errors.New("cannot decode base64")
	}
	return b, nil
}

// decodeBase64UrlSafe is ktx decodeBase64UrlSafe: b64Decode as UTF-8 text.
func decodeBase64UrlSafe(s string) (string, error) {
	b, err := b64Decode(s)
	if err != nil {
		return "", err
	}
	return javaUTF8(b), nil
}

// ngDecode is NGUtil.decode: the standard alphabet, else the URL-safe one (each skipping the other's two
// characters), again without trailing '=' if there were some; "" when none decodes.
func ngDecode(s string) string {
	if v, ok := ngTryDecodeBase64(s); ok {
		return v
	}
	if strings.HasSuffix(s, "=") {
		if v, ok := ngTryDecodeBase64(strings.TrimRight(s, "=")); ok {
			return v
		}
	}
	return ""
}

func ngTryDecodeBase64(s string) (string, bool) {
	if b, err := androidBase64Decode(s, false); err == nil {
		return javaUTF8(b), true
	}
	if b, err := androidBase64Decode(s, true); err == nil {
		return javaUTF8(b), true
	}
	return "", false
}

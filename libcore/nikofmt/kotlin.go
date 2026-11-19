package nikofmt

import (
	"strings"
	"unicode"
	"unicode/utf16"
	"unicode/utf8"
)

// What the app's parsers get from Kotlin and the JVM, where Go's own functions differ. Kotlin strings are UTF-16;
// these work on runes, which differs only for characters outside the BMP (noted where it matters).

// ktIsWhitespace is Kotlin's Char.isWhitespace on the JVM: Character.isWhitespace or isSpaceChar, so the separators
// (Zs, Zl, Zp, the no-break spaces among them), \t \n \v \f \r and the information separators \x1c-\x1f. Go's
// unicode.IsSpace takes U+0085 and leaves \x1c-\x1f out.
func ktIsWhitespace(r rune) bool {
	switch r {
	case '\t', '\n', '\v', '\f', '\r', 0x1c, 0x1d, 0x1e, 0x1f:
		return true
	}
	return unicode.In(r, unicode.Zs, unicode.Zl, unicode.Zp)
}

// ktTrim is Kotlin's String.trim().
func ktTrim(s string) string { return strings.TrimFunc(s, ktIsWhitespace) }

// ktIsBlank is Kotlin's String.isBlank() (and JavaUtil.isNullOrBlank of a string that is there): empty or whitespace.
func ktIsBlank(s string) bool {
	for _, r := range s {
		if !ktIsWhitespace(r) {
			return false
		}
	}
	return true
}

// ktLowercase is Kotlin's String.lowercase(), Java's toLowerCase(Locale.ROOT). Go maps U+0130 (İ) to a plain i, Java
// to i and a combining dot: a name with it passes the app's ASCII checks after Go's mapping, never after Java's.
// Java's final sigma (Σ at the end of a word to ς) is not done: nothing here keeps Greek past an ASCII check.
func ktLowercase(s string) string {
	if !strings.ContainsRune(s, 'İ') {
		return strings.ToLower(s)
	}
	return strings.ToLower(strings.ReplaceAll(s, "İ", "i̇"))
}

// javaDigit is Character.digit(ch, radix) for radix 10 or 16: ASCII digits and letters, the other decimal digits of
// Unicode (Nd: Arabic-Indic, fullwidth, ...) and, for 16, the fullwidth letters A-F. Only BMP characters: one outside
// it is two chars in Java, neither a digit.
func javaDigit(r rune, radix int) int {
	v := -1
	switch {
	case r >= '0' && r <= '9':
		v = int(r - '0')
	case r >= 'a' && r <= 'z':
		v = int(r-'a') + 10
	case r >= 'A' && r <= 'Z':
		v = int(r-'A') + 10
	case r >= 0xff41 && r <= 0xff5a: // fullwidth a-z
		v = int(r-0xff41) + 10
	case r >= 0xff21 && r <= 0xff3a: // fullwidth A-Z
		v = int(r-0xff21) + 10
	case r > 0x7f && r <= 0xffff && unicode.IsDigit(r):
		// the digits of a script come in runs of ten from its zero
		zero := r
		for unicode.IsDigit(zero - 1) {
			zero--
		}
		v = int(r-zero) % 10
	}
	if v >= radix {
		return -1
	}
	return v
}

// javaParseInt is Integer.parseInt(s, radix), which Kotlin's String.toInt() is, and Kotlin's toIntOrNull() (the same
// rules): an optional sign, digits as javaDigit takes them, the range of a Java int.
func javaParseInt(s string, radix int) (int32, bool) {
	rs := []rune(s)
	if len(rs) == 0 {
		return 0, false
	}
	negative := false
	limit := int64(-(1<<31 - 1))
	i := 0
	if rs[0] < '0' {
		switch rs[0] {
		case '-':
			negative = true
			limit = -(1 << 31)
		case '+':
		default:
			return 0, false
		}
		if len(rs) == 1 {
			return 0, false
		}
		i = 1
	}
	var result int64
	for ; i < len(rs); i++ {
		d := javaDigit(rs[i], radix)
		if d < 0 {
			return 0, false
		}
		result = result*int64(radix) - int64(d)
		if result < limit {
			return 0, false
		}
	}
	if negative {
		return int32(result), true
	}
	return int32(-result), true
}

// javaUTF8 decodes bytes as new String(bytes, UTF_8) does (OkHttp's percent decoding, URLDecoder): each maximal
// subpart of an ill-formed sequence becomes one U+FFFD, but for an encoded surrogate (ED A0-BF ..), which Java takes
// whole as one. Go's []rune or strings.ToValidUTF8 count them otherwise.
func javaUTF8(b []byte) string {
	if utf8.Valid(b) {
		return string(b)
	}
	var sb strings.Builder
	for len(b) > 0 {
		r, n := utf8.DecodeRune(b)
		if r != utf8.RuneError || n > 1 {
			sb.Write(b[:n])
			b = b[n:]
			continue
		}
		sb.WriteRune(utf8.RuneError)
		b = b[maximalSubpart(b):]
	}
	return sb.String()
}

// maximalSubpart is the length of the ill-formed sequence at the start of b that one U+FFFD stands for: a lead byte
// and the continuation bytes that could still have completed it.
func maximalSubpart(b []byte) int {
	lo, hi := byte(0x80), byte(0xbf)
	need := 0
	switch c := b[0]; {
	case c >= 0xc2 && c <= 0xdf:
		need = 1
	case c == 0xe0:
		need, lo = 2, 0xa0
	case c >= 0xe1 && c <= 0xef:
		// ED's second byte is 80-9F in UTF-8; Java decodes ED A0-BF .. to a surrogate and refuses the three
		need = 2
	case c == 0xf0:
		need, lo = 3, 0x90
	case c >= 0xf1 && c <= 0xf3:
		need = 3
	case c == 0xf4:
		need, hi = 3, 0x8f
	default:
		return 1
	}
	n := 1
	for i := 1; i <= need && i < len(b); i++ {
		if b[i] < lo || b[i] > hi {
			break
		}
		n++
		lo, hi = 0x80, 0xbf
	}
	return n
}

// javaURLDecode is java.net.URLDecoder.decode(s, "UTF-8"), and ok false where it throws: '+' is a space, a run of
// %XY escapes is UTF-8, an escape that is not one fails the whole string. XY goes through Integer.parseInt(.., 16),
// so "%+f" is 0x0f and "%-0" a NUL (Java's own quirk); a negative value fails. Positions are Java's chars (UTF-16).
func javaURLDecode(s string) (string, bool) {
	u := utf16.Encode([]rune(s))
	n := len(u)
	var out []uint16
	changed := false
	for i := 0; i < n; {
		c := u[i]
		switch c {
		case '+':
			out = append(out, ' ')
			i++
			changed = true
		case '%':
			var bytes []byte
			for i+2 < n && c == '%' {
				v, ok := javaParseInt(string(utf16.Decode(u[i+1:i+3])), 16)
				if !ok || v < 0 {
					return s, false
				}
				bytes = append(bytes, byte(v))
				i += 3
				if i < n {
					c = u[i]
				}
			}
			if i < n && c == '%' {
				return s, false // an incomplete trailing escape
			}
			out = append(out, utf16.Encode([]rune(javaUTF8(bytes)))...)
			changed = true
		default:
			out = append(out, c)
			i++
		}
	}
	if !changed {
		return s, true
	}
	return string(utf16.Decode(out)), true
}

package nikofmt

import (
	"errors"
	"strings"
	"unicode/utf16"
)

// gsonReadObject reads text as Gson 2.11's Gson().fromJson(text, SomeClass) reads it into a class of String fields
// (v2rayN's VmessQRCode): the object leniently (comments, unquoted or single-quoted names and values, '=' or "=>"
// for ':', ';' for ','), a leading ")]}'\n" skipped, then nothing but whitespace after it, as Gson checks the end
// strictly again. field gives the field of a name, nil for one the class has not (its value is skipped); a String
// field takes a string, a number's text, true or false, or null. An error is where Gson throws.
func gsonReadObject(text string, field func(name string) **string) error {
	r := &gsonReader{s: []rune(text)}
	if _, err := r.nextNonWhitespace(); err != nil {
		return err // an empty document: Gson gives null, and the caller's field access throws
	}
	r.pos--
	if strings.HasPrefix(string(r.s[r.pos:min(r.pos+5, len(r.s))]), ")]}'\n") {
		r.pos += 5
	}
	c, err := r.nextNonWhitespace()
	if err != nil {
		return err
	}
	if c != '{' {
		return errGson // Expected BEGIN_OBJECT
	}
	if err := r.object(field); err != nil {
		return err
	}
	for ; r.pos < len(r.s); r.pos++ {
		switch r.s[r.pos] {
		case ' ', '\t', '\n', '\r':
		default:
			return errGson // JSON document was not fully consumed (or a comment: strict again)
		}
	}
	return nil
}

var (
	errGson    = errors.New("malformed JSON")
	errGsonEnd = errors.New("malformed JSON: end of input")
)

type gsonReader struct {
	s   []rune // Gson reads chars; runes differ only outside the BMP, which no token here starts with
	pos int
}

// nextNonWhitespace is JsonReader.nextNonWhitespace, lenient: the next character after whitespace and comments
// (/* */, // and # to the end of the line); an error at the end.
func (r *gsonReader) nextNonWhitespace() (rune, error) {
	for r.pos < len(r.s) {
		c := r.s[r.pos]
		r.pos++
		switch c {
		case ' ', '\t', '\n', '\r':
			continue
		case '/':
			if r.pos == len(r.s) {
				return c, nil
			}
			switch r.s[r.pos] {
			case '*':
				end := strings.Index(string(r.s[r.pos+1:]), "*/")
				if end < 0 {
					return 0, errGson // unterminated comment
				}
				r.pos += 1 + len([]rune(string(r.s[r.pos+1:])[:end])) + 2
				continue
			case '/':
				r.pos++
				r.skipToEndOfLine()
				continue
			}
			return c, nil
		case '#':
			r.skipToEndOfLine()
			continue
		}
		return c, nil
	}
	return 0, errGsonEnd
}

func (r *gsonReader) skipToEndOfLine() {
	for r.pos < len(r.s) {
		c := r.s[r.pos]
		r.pos++
		if c == '\n' || c == '\r' {
			return
		}
	}
}

// gsonLiteral is JsonReader.isLiteral: a character an unquoted name or value may hold.
func gsonLiteral(c rune) bool {
	return !strings.ContainsRune("/\\;#={}[]:, \t\f\r\n", c)
}

// unquoted is nextUnquotedValue: the literal characters from here.
func (r *gsonReader) unquoted() string {
	start := r.pos
	for r.pos < len(r.s) && gsonLiteral(r.s[r.pos]) {
		r.pos++
	}
	return string(r.s[start:r.pos])
}

// quoted is nextQuotedValue after its opening quote: up to the same quote, with JSON's escapes and \' and an
// escaped line end; raw control characters are taken.
func (r *gsonReader) quoted(quote rune) (string, error) {
	var out []uint16
	for r.pos < len(r.s) {
		c := r.s[r.pos]
		r.pos++
		switch c {
		case quote:
			return string(utf16.Decode(out)), nil
		case '\\':
			if r.pos == len(r.s) {
				return "", errGson
			}
			e := r.s[r.pos]
			r.pos++
			switch e {
			case 'u':
				if r.pos+4 > len(r.s) {
					return "", errGson
				}
				v := 0
				for _, h := range r.s[r.pos : r.pos+4] {
					if !isHexRune(h) {
						return "", errGson // malformed Unicode escape
					}
					v = v<<4 | hexValue(byte(h))
				}
				r.pos += 4
				out = append(out, uint16(v))
			case 't':
				out = append(out, '\t')
			case 'b':
				out = append(out, '\b')
			case 'n':
				out = append(out, '\n')
			case 'r':
				out = append(out, '\r')
			case 'f':
				out = append(out, '\f')
			case '\n', '\'', '"', '\\', '/':
				out = append(out, uint16(e))
			default:
				return "", errGson // invalid escape sequence
			}
		default:
			out = utf16.AppendRune(out, c)
		}
	}
	return "", errGson // unterminated string
}

// object reads an object's members after its '{': a String field's value into it, any other skipped.
func (r *gsonReader) object(field func(string) **string) error {
	return r.members(func(name string) error {
		if f := field(name); f != nil {
			var err error
			*f, err = r.stringValue()
			return err
		}
		return r.skipValue()
	})
}

// members reads an object's members after its '{', lenient as JsonReader is: each name, then value reads its value.
func (r *gsonReader) members(value func(name string) error) error {
	for first := true; ; first = false {
		if !first {
			c, err := r.nextNonWhitespace()
			if err != nil {
				return err
			}
			switch c {
			case '}':
				return nil
			case ';', ',':
			default:
				return errGson // unterminated object
			}
		}
		c, err := r.nextNonWhitespace()
		if err != nil {
			return err
		}
		var name string
		switch c {
		case '"', '\'':
			if name, err = r.quoted(c); err != nil {
				return err
			}
		case '}':
			if first {
				return nil
			}
			return errGson // expected a name
		default:
			r.pos--
			if !gsonLiteral(c) {
				return errGson // expected a name
			}
			name = r.unquoted()
		}
		switch c, err = r.nextNonWhitespace(); {
		case err != nil:
			return err
		case c == '=':
			if r.pos < len(r.s) && r.s[r.pos] == '>' {
				r.pos++
			}
		case c != ':':
			return errGson // expected ':'
		}
		if err = value(name); err != nil {
			return err
		}
	}
}

// stringValue is Gson's String adapter on the next value: a quoted or unquoted string, a number's text, "true" or
// "false" for a keyword in any case, nil for null; an object or an array is an error.
func (r *gsonReader) stringValue() (*string, error) {
	c, err := r.nextNonWhitespace()
	if err != nil {
		return nil, err
	}
	switch c {
	case '"', '\'':
		s, err := r.quoted(c)
		return &s, err
	case '{', '[', ']', ';', ',':
		return nil, errGson // not a string, or an unexpected value
	}
	r.pos--
	for _, keyword := range []string{"true", "false", "null"} {
		if r.keyword(keyword) {
			if keyword == "null" {
				return nil, nil
			}
			return &keyword, nil
		}
	}
	if !gsonLiteral(c) {
		return nil, errGson // expected a value
	}
	s := r.unquoted()
	return &s, nil
}

// keyword is JsonReader.peekKeyword: the word here, each letter in either case, not followed by a literal character;
// taken if so.
func (r *gsonReader) keyword(word string) bool {
	n := len(word)
	if r.pos+n > len(r.s) {
		return false
	}
	for i := 0; i < n; i++ {
		if c := r.s[r.pos+i]; c != rune(word[i]) && c != rune(word[i]-'a'+'A') {
			return false
		}
	}
	if r.pos+n < len(r.s) && gsonLiteral(r.s[r.pos+n]) {
		return false
	}
	r.pos += n
	return true
}

// skipValue is JsonReader.skipValue on an object member's value.
func (r *gsonReader) skipValue() error {
	c, err := r.nextNonWhitespace()
	if err != nil {
		return err
	}
	switch c {
	case '{':
		return r.object(func(string) **string { return nil })
	case '[':
		return r.skipArray()
	case '"', '\'':
		_, err = r.quoted(c)
		return err
	case ']', ';', ',':
		return errGson // unexpected value
	}
	r.pos--
	if !gsonLiteral(c) {
		return errGson // expected a value
	}
	r.unquoted()
	return nil
}

// skipArray skips an array after its '[', lenient: an empty element (",,", ",]") is a null.
func (r *gsonReader) skipArray() error {
	for first := true; ; first = false {
		if !first {
			c, err := r.nextNonWhitespace()
			if err != nil {
				return err
			}
			switch c {
			case ']':
				return nil
			case ';', ',':
			default:
				return errGson // unterminated array
			}
		}
		c, err := r.nextNonWhitespace()
		if err != nil {
			return err
		}
		switch c {
		case ']':
			if first {
				return nil
			}
			r.pos-- // ",]": a null, then the end
		case ';', ',':
			r.pos-- // a null
		case '{':
			err = r.object(func(string) **string { return nil })
		case '[':
			err = r.skipArray()
		case '"', '\'':
			_, err = r.quoted(c)
		default:
			r.pos--
			if !gsonLiteral(c) {
				return errGson
			}
			r.unquoted()
		}
		if err != nil {
			return err
		}
	}
}

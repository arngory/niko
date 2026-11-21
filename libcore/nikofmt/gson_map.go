package nikofmt

import (
	"strconv"
	"strings"
)

// GsonMap reads text as the app's Gson for sing-box options reads it into a Map (SingBoxOptions.gsonSingbox,
// fromJson(text, Map.class)): what a custom outbound's JSON becomes (CustomSingBoxOption.getBasicMap). Lenient as
// gsonReadObject, comments after the value too (the reader stays lenient for Gson's end check). Values are
// map[string]any, []any, string, bool, nil for null, and numbers as ToNumberPolicy.LONG_OR_DOUBLE makes them:
// int64 where Long.parseLong takes the text, else float64 (±Inf past a double's range, as Double.valueOf gives).
//
// The map is nil for a text of only whitespace and comments, or a null (after which Gson checks nothing). A
// duplicate name of the map's own is an error, unless the first value was null; a nested object keeps its last
// value, as ObjectTypeAdapter does. The map may also be an array of [name, value] pairs, as Gson's map adapter
// reads one; only string names here (Gson would take a number for a name, and write it back as a string).
func GsonMap(text string) (map[string]any, error) {
	r := &gsonReader{s: []rune(text)}
	if _, err := r.nextNonWhitespace(); err != nil {
		if err == errGsonEnd {
			return nil, nil
		}
		return nil, err
	}
	r.pos--
	if strings.HasPrefix(string(r.s[r.pos:min(r.pos+5, len(r.s))]), ")]}'\n") {
		r.pos += 5
	}
	c, err := r.nextNonWhitespace()
	if err == errGsonEnd {
		return nil, nil
	} else if err != nil {
		return nil, err
	}
	m := map[string]any{}
	put := func(name string, v any) error {
		if old, ok := m[name]; ok && old != nil {
			return errGson // duplicate key
		}
		m[name] = v
		return nil
	}
	switch c {
	case '{':
		err = r.members(func(name string) error {
			v, err := r.value()
			if err != nil {
				return err
			}
			return put(name, v)
		})
	case '[':
		var pairs []any
		if pairs, err = r.arrayValue(); err != nil {
			return nil, err
		}
		for _, p := range pairs {
			pair, ok := p.([]any)
			if !ok || len(pair) != 2 {
				return nil, errGson // each entry is an array of two
			}
			name, ok := pair[0].(string)
			if !ok {
				return nil, errGson
			}
			if err = put(name, pair[1]); err != nil {
				return nil, err
			}
		}
	default:
		r.pos--
		if r.keyword("null") {
			return nil, nil
		}
		return nil, errGson // Expected BEGIN_OBJECT
	}
	if err != nil {
		return nil, err
	}
	// JSON document was not fully consumed: anything but whitespace and comments
	if _, err := r.nextNonWhitespace(); err != errGsonEnd {
		if err == nil {
			err = errGson
		}
		return nil, err
	}
	return m, nil
}

// value is ObjectTypeAdapter's read of the next value, numbers as LONG_OR_DOUBLE.
func (r *gsonReader) value() (any, error) {
	c, err := r.nextNonWhitespace()
	if err != nil {
		return nil, err
	}
	switch c {
	case '{':
		m := map[string]any{}
		err := r.members(func(name string) error {
			v, err := r.value()
			m[name] = v
			return err
		})
		return m, err
	case '[':
		return r.arrayValue()
	case '"', '\'':
		return r.quoted(c)
	case ']', ';', ',':
		return nil, errGson // unexpected value
	}
	r.pos--
	switch {
	case r.keyword("true"):
		return true, nil
	case r.keyword("false"):
		return false, nil
	case r.keyword("null"):
		return nil, nil
	}
	if n, ok := r.number(); ok {
		return n, nil
	}
	if !gsonLiteral(c) {
		return nil, errGson // expected a value
	}
	return r.unquoted(), nil
}

// arrayValue reads an array after its '[', lenient: an empty element (",,", ",]") is a null, as skipArray takes it.
func (r *gsonReader) arrayValue() ([]any, error) {
	list := []any{}
	for first := true; ; first = false {
		if !first {
			c, err := r.nextNonWhitespace()
			if err != nil {
				return nil, err
			}
			switch c {
			case ']':
				return list, nil
			case ';', ',':
			default:
				return nil, errGson // unterminated array
			}
		}
		c, err := r.nextNonWhitespace()
		if err != nil {
			return nil, err
		}
		r.pos--
		switch c {
		case ']':
			if first {
				r.pos++
				return list, nil
			}
			list = append(list, nil) // ",]": a null, then the end
			continue
		case ';', ',':
			list = append(list, nil)
			continue
		}
		v, err := r.value()
		if err != nil {
			return nil, err
		}
		list = append(list, v)
	}
}

// number is JsonReader.peekNumber, then LONG_OR_DOUBLE: a JSON number here (no leading zero, which could be octal)
// not followed by a literal character, taken if so. A whole number is an int64 where it fits a long ("-0" too,
// as Long.parseLong reads it), any other a float64. Not a number: ok false, and the caller takes an unquoted string.
func (r *gsonReader) number() (any, bool) {
	const (
		none = iota
		sign
		digit
		decimal
		fraction
		exponent
		exponentSign
		exponentDigit
	)
	last := none
	leadingZero := false
	i := r.pos
scan:
	for ; i < len(r.s); i++ {
		c := r.s[i]
		switch {
		case c == '-' && last == none:
			last = sign
		case (c == '-' || c == '+') && last == exponent:
			last = exponentSign
		case (c == 'e' || c == 'E') && (last == digit || last == fraction):
			last = exponent
		case c == '.' && last == digit:
			last = decimal
		case c >= '0' && c <= '9':
			switch last {
			case none, sign:
				last = digit
				leadingZero = c == '0'
			case digit:
				if leadingZero {
					return nil, false
				}
			case decimal:
				last = fraction
			case exponent, exponentSign:
				last = exponentDigit
			}
		case !gsonLiteral(c):
			break scan
		default:
			return nil, false
		}
	}
	text := string(r.s[r.pos:i])
	switch last {
	case digit:
		if n, err := strconv.ParseInt(text, 10, 64); err == nil {
			r.pos = i
			return n, true
		}
	case fraction, exponentDigit:
	default:
		return nil, false
	}
	f, _ := strconv.ParseFloat(text, 64) // out of range: ±Inf or 0, as Double.valueOf
	r.pos = i
	return f, true
}

package nikoconf

import (
	"errors"
	"math"
	"regexp"
	"strconv"
	"strings"
	"unicode/utf16"

	"libcore/nikofmt"
)

// Android's org.json (the platform's, not JSON-java), which the app's XHTTP code reads an extra JSON with: its
// JSONTokener's lenient grammar and its types. Values are *orgObject, []any (JSONArray: nil for Java's null, which
// a missing element is), string, bool, int32 (Integer), int64 (Long), float64 (Double) and orgNull (JSONObject.NULL).

// orgObject is a JSONObject: its names in the order first put (a LinkedHashMap; a name again replaces the value).
type orgObject struct {
	keys   []string
	values map[string]any
}

func (o *orgObject) put(name string, v any) {
	if _, ok := o.values[name]; !ok {
		o.keys = append(o.keys, name)
	}
	o.values[name] = v
}

// orgNull is JSONObject.NULL.
type orgNull struct{}

var errOrgJSON = errors.New("malformed JSON")

// orgJSONObject is new JSONObject(text): the first value of the text, which must be an object; what follows it is
// not looked at. A leading BOM is skipped.
func orgJSONObject(text string) (*orgObject, error) {
	t := &orgTokener{s: []rune(strings.TrimPrefix(text, "\ufeff"))}
	v, err := t.nextValue()
	if err != nil {
		return nil, err
	}
	o, ok := v.(*orgObject)
	if !ok {
		return nil, errors.New("not a JSON object")
	}
	return o, nil
}

// orgTokener is JSONTokener. It reads chars; runes differ only outside the BMP, which no token starts with.
type orgTokener struct {
	s   []rune
	pos int
}

// nextClean is nextCleanInternal: the next character after whitespace and comments (/* */, // and # to the line's
// end), -1 at the end.
func (t *orgTokener) nextClean() (rune, error) {
	for t.pos < len(t.s) {
		c := t.s[t.pos]
		t.pos++
		switch c {
		case '\t', ' ', '\n', '\r':
			continue
		case '/':
			if t.pos == len(t.s) {
				return c, nil
			}
			switch t.s[t.pos] {
			case '*':
				t.pos++
				end := strings.Index(string(t.s[t.pos:]), "*/")
				if end < 0 {
					return 0, errOrgJSON // unterminated comment
				}
				t.pos += len([]rune(string(t.s[t.pos:])[:end])) + 2
				continue
			case '/':
				t.pos++
				t.skipToEndOfLine()
				continue
			}
			return c, nil
		case '#':
			t.skipToEndOfLine()
			continue
		}
		return c, nil
	}
	return -1, nil
}

func (t *orgTokener) skipToEndOfLine() {
	for ; t.pos < len(t.s); t.pos++ {
		if c := t.s[t.pos]; c == '\r' || c == '\n' {
			t.pos++
			return
		}
	}
}

func (t *orgTokener) nextValue() (any, error) {
	c, err := t.nextClean()
	if err != nil {
		return nil, err
	}
	switch c {
	case -1:
		return nil, errOrgJSON // end of input
	case '{':
		return t.readObject()
	case '[':
		return t.readArray()
	case '\'', '"':
		return t.nextString(c)
	}
	t.pos--
	return t.readLiteral()
}

// nextString reads up to the quote: JSON's escapes, and any other escaped character as itself.
func (t *orgTokener) nextString(quote rune) (string, error) {
	var out []uint16
	for t.pos < len(t.s) {
		c := t.s[t.pos]
		t.pos++
		if c == quote {
			return string(utf16.Decode(out)), nil
		}
		if c != '\\' {
			out = utf16.AppendRune(out, c)
			continue
		}
		if t.pos == len(t.s) {
			return "", errOrgJSON // unterminated escape sequence
		}
		e := t.s[t.pos]
		t.pos++
		switch e {
		case 'u':
			if t.pos+4 > len(t.s) {
				return "", errOrgJSON
			}
			// Integer.parseInt(hex, 16): a sign is taken too, "-001" being (char) -1
			v, ok := nikofmt.JavaParseLong(string(t.s[t.pos:t.pos+4]), 16)
			if !ok {
				return "", errOrgJSON // invalid escape sequence
			}
			t.pos += 4
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
		default:
			out = utf16.AppendRune(out, e)
		}
	}
	return "", errOrgJSON // unterminated string
}

// readLiteral: null, true or false in any case, an Integer or Long (0x hexadecimal, a leading 0 octal), a Double,
// else the unquoted string.
func (t *orgTokener) readLiteral() (any, error) {
	start := t.pos
	for t.pos < len(t.s) && !strings.ContainsRune("{}[]/\\:,=;# \t\f\r\n", t.s[t.pos]) {
		t.pos++
	}
	literal := string(t.s[start:t.pos])
	switch {
	case literal == "":
		return nil, errOrgJSON // expected literal value
	case strings.EqualFold(literal, "null"):
		return orgNull{}, nil
	case strings.EqualFold(literal, "true"):
		return true, nil
	case strings.EqualFold(literal, "false"):
		return false, nil
	}
	if !strings.Contains(literal, ".") {
		number, base := literal, 10
		if strings.HasPrefix(number, "0x") || strings.HasPrefix(number, "0X") {
			number, base = number[2:], 16
		} else if strings.HasPrefix(number, "0") && len(number) > 1 {
			number, base = number[1:], 8
		}
		if n, ok := nikofmt.JavaParseLong(number, base); ok {
			if n == int64(int32(n)) {
				return int32(n), nil
			}
			return n, nil
		}
	}
	if f, ok := javaDoubleValueOf(literal); ok {
		return f, nil
	}
	return literal, nil
}

func (t *orgTokener) readObject() (*orgObject, error) {
	o := &orgObject{values: map[string]any{}}
	first, err := t.nextClean()
	if err != nil {
		return nil, err
	}
	if first == '}' {
		return o, nil
	} else if first != -1 {
		t.pos--
	}
	for {
		v, err := t.nextValue()
		if err != nil {
			return nil, err
		}
		name, ok := v.(string)
		if !ok {
			return nil, errOrgJSON // names must be strings
		}
		separator, err := t.nextClean()
		if err != nil {
			return nil, err
		}
		if separator != ':' && separator != '=' {
			return nil, errOrgJSON // expected ':'
		}
		// "=>", and ":>" as well: the '>' is skipped after either
		if t.pos < len(t.s) && t.s[t.pos] == '>' {
			t.pos++
		}
		if v, err = t.nextValue(); err != nil {
			return nil, err
		}
		// JSONObject.put refuses NaN and the infinities
		if f, ok := v.(float64); ok && (math.IsNaN(f) || math.IsInf(f, 0)) {
			return nil, errors.New("forbidden numeric value")
		}
		o.put(name, v)
		switch c, err := t.nextClean(); {
		case err != nil:
			return nil, err
		case c == '}':
			return o, nil
		case c != ';' && c != ',':
			return nil, errOrgJSON // unterminated object
		}
	}
}

// readArray: an element left out (",,", ",]", a leading ",") is Java's null, nil here.
func (t *orgTokener) readArray() ([]any, error) {
	list := []any{}
	trailingSeparator := false
	for {
		c, err := t.nextClean()
		if err != nil {
			return nil, err
		}
		switch c {
		case -1:
			return nil, errOrgJSON // unterminated array
		case ']':
			if trailingSeparator {
				list = append(list, nil)
			}
			return list, nil
		case ',', ';':
			list = append(list, nil)
			trailingSeparator = true
			continue
		}
		t.pos--
		v, err := t.nextValue()
		if err != nil {
			return nil, err
		}
		list = append(list, v)
		switch c, err = t.nextClean(); {
		case err != nil:
			return nil, err
		case c == ']':
			return list, nil
		case c == ',' || c == ';':
			trailingSeparator = true
		default:
			return nil, errOrgJSON // unterminated array
		}
	}
}

// javaDouble is the grammar of Double.valueOf, after its trim: a sign, then NaN, Infinity, or decimal or
// hexadecimal digits with a type suffix (f, F, d or D) that changes nothing.
var javaDouble = regexp.MustCompile(`^[+-]?(?:NaN|Infinity|(?:(?:[0-9]+\.?[0-9]*|\.[0-9]+)(?:[eE][+-]?[0-9]+)?|0[xX](?:[0-9a-fA-F]+\.?[0-9a-fA-F]*|\.[0-9a-fA-F]+)[pP][+-]?[0-9]+)[fFdD]?)$`)

// javaDoubleValueOf is Double.valueOf(s), ok false where it throws. The characters up to ' ' around it are trimmed,
// as String.trim does.
func javaDoubleValueOf(s string) (float64, bool) {
	s = strings.TrimFunc(s, func(r rune) bool { return r <= ' ' })
	if !javaDouble.MatchString(s) {
		return 0, false
	}
	switch {
	case strings.HasSuffix(s, "NaN"):
		return math.NaN(), true
	case strings.HasSuffix(s, "Infinity"):
		if s[0] == '-' {
			return math.Inf(-1), true
		}
		return math.Inf(1), true
	}
	f, err := strconv.ParseFloat(strings.TrimRight(s, "fFdD"), 64)
	if err != nil && !errors.Is(err, strconv.ErrRange) {
		return 0, false
	}
	return f, true // past a double's range: ±Inf or 0, as Java rounds
}

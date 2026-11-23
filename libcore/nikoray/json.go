package nikoray

import (
	"bytes"
	"encoding/json"
	"errors"
	"math"
	"math/big"
	"strconv"
	"strings"
)

// JSON as the app's Gson reads it, for what a node sends (an invite, a device package, a subscription's payload):
// values decoded with their numbers' text kept, and the getters of Gson's JsonPrimitive over them (a number's text as
// a string, a string's digits as a number, a boolean of "true"), so that the same value is judged the same. The text
// itself is read strictly (encoding/json): what Gson's lenient reader would also take (comments, unquoted names) is
// refused here, the safe side.

// maxDepth bounds how deep a node's JSON may nest: a payload is a few levels deep.
const maxDepth = 64

var errDepth = errors.New("nested too deep")

// decodeObject is a JSON object, its numbers as json.Number; an error for anything else.
func decodeObject(data []byte) (map[string]any, error) {
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.UseNumber()
	var v any
	if err := decoder.Decode(&v); err != nil {
		return nil, err
	}
	if decoder.More() {
		return nil, errors.New("more after the value")
	}
	if depth(v, 0) > maxDepth {
		return nil, errDepth
	}
	o, ok := v.(map[string]any)
	if !ok {
		return nil, errors.New("not an object")
	}
	return o, nil
}

// depth is how deep v nests, stopping once over maxDepth.
func depth(v any, at int) int {
	if at > maxDepth {
		return at
	}
	deepest := at
	switch v := v.(type) {
	case map[string]any:
		for _, e := range v {
			deepest = max(deepest, depth(e, at+1))
		}
	case []any:
		for _, e := range v {
			deepest = max(deepest, depth(e, at+1))
		}
	}
	return deepest
}

// isPrimitive: a string, a number or a boolean (Gson's JsonPrimitive), not null.
func isPrimitive(v any) bool {
	switch v.(type) {
	case string, json.Number, bool:
		return true
	}
	return false
}

// asString is JsonPrimitive.getAsString: a number's text, "true" or "false"; ok false for anything else.
func asString(v any) (string, bool) {
	switch v := v.(type) {
	case string:
		return v, true
	case json.Number:
		return string(v), true
	case bool:
		return strconv.FormatBool(v), true
	}
	return "", false
}

// asLong is JsonElement.getAsLong: a number's long value (its text as a Long, else as a BigDecimal cut to a long), a
// string's text parsed as a Long, an array of one element its element's; ok false where Gson throws.
func asLong(v any) (int64, bool) {
	switch v := v.(type) {
	case json.Number:
		return numberLong(string(v))
	case string:
		return javaParseLong(v)
	case []any:
		if len(v) == 1 {
			return asLong(v[0])
		}
	}
	return 0, false
}

// asInt is JsonElement.getAsInt, as asLong within an Int.
func asInt(v any) (int32, bool) {
	switch v := v.(type) {
	case json.Number:
		if n, err := strconv.ParseInt(string(v), 10, 32); err == nil {
			return int32(n), true
		}
		if n, err := strconv.ParseInt(string(v), 10, 64); err == nil {
			return int32(n), true // Gson: Integer.parseInt fails, Long.parseLong takes it, cut to an int
		}
		l, ok := bigDecimalLong(string(v))
		return int32(l), ok
	case string:
		n, ok := javaParseLong(v)
		if !ok || n < math.MinInt32 || n > math.MaxInt32 {
			return 0, false
		}
		return int32(n), true
	case []any:
		if len(v) == 1 {
			return asInt(v[0])
		}
	}
	return 0, false
}

// asBool is JsonElement.getAsBoolean: a boolean, or a primitive's text parsed as Boolean.parseBoolean (true only for
// "true", any case); ok false where Gson throws.
func asBool(v any) (bool, bool) {
	switch v := v.(type) {
	case bool:
		return v, true
	case string:
		return strings.EqualFold(v, "true"), true
	case json.Number:
		return false, true
	case []any:
		if len(v) == 1 {
			return asBool(v[0])
		}
	}
	return false, false
}

// numberLong is LazilyParsedNumber.longValue: Long.parseLong of the text, else BigDecimal's longValue.
func numberLong(text string) (int64, bool) {
	if n, err := strconv.ParseInt(text, 10, 64); err == nil {
		return n, true
	}
	return bigDecimalLong(text)
}

// bigDecimalLong is new BigDecimal(text).longValue(): the integer part, its low 64 bits.
func bigDecimalLong(text string) (int64, bool) {
	r, ok := new(big.Rat).SetString(text)
	if !ok {
		return 0, false
	}
	i := new(big.Int).Quo(r.Num(), r.Denom())
	// the low 64 bits, two's complement, as BigInteger.longValue
	mod := new(big.Int).And(i, new(big.Int).SetUint64(math.MaxUint64))
	return int64(mod.Uint64()), true
}

// javaParseLong is Long.parseLong(s): an optional sign and ASCII digits within a Long.
func javaParseLong(s string) (int64, bool) {
	if s == "" {
		return 0, false
	}
	for i, c := range s {
		if (c < '0' || c > '9') && !(i == 0 && (c == '-' || c == '+') && len(s) > 1) {
			return 0, false
		}
	}
	n, err := strconv.ParseInt(s, 10, 64)
	return n, err == nil
}

// field getters of an object as the app's own extensions read them (Rcdev1.kt string, long, hex)

// str is JsonObject.string: a primitive's text, else "missing <name>".
func str(o map[string]any, name string) (string, error) {
	v, ok := o[name]
	if !ok || !isPrimitive(v) {
		return "", errors.New("missing " + name)
	}
	s, _ := asString(v)
	return s, nil
}

// long is JsonObject.long: a number's long value, else "missing <name>".
func long(o map[string]any, name string) (int64, error) {
	if n, ok := o[name].(json.Number); ok {
		if l, ok := numberLong(string(n)); ok {
			return l, nil
		}
	}
	return 0, errors.New("missing " + name)
}

// hexField is JsonObject.hex: a string of bytes*2 hex digits, in lower case.
func hexField(o map[string]any, name string, bytes int) (string, error) {
	s, err := str(o, name)
	if err != nil {
		return "", err
	}
	s = strings.ToLower(s)
	if len(s) != bytes*2 || !isHex(s) {
		return "", errors.New(name + " must be " + strconv.Itoa(bytes*2) + " hex digits")
	}
	return s, nil
}

func isHex(s string) bool {
	if s == "" {
		return false
	}
	for _, c := range s {
		if (c < '0' || c > '9') && (c < 'a' || c > 'f') {
			return false
		}
	}
	return true
}

// object is o[name] as an object, nil where it is none.
func object(o map[string]any, name string) map[string]any {
	v, _ := o[name].(map[string]any)
	return v
}

// array is o[name] as an array, nil where it is none.
func array(o map[string]any, name string) ([]any, bool) {
	v, ok := o[name].([]any)
	return v, ok
}

// primitiveString is o[name]?.takeIf { it.isJsonPrimitive }?.asString.
func primitiveString(o map[string]any, name string) (string, bool) {
	v, ok := o[name]
	if !ok || !isPrimitive(v) {
		return "", false
	}
	return asString(v)
}

// stringValue is (o[name] as? JsonPrimitive)?.takeIf { it.isString }?.asString.
func stringValue(o map[string]any, name string) (string, bool) {
	s, ok := o[name].(string)
	return s, ok
}

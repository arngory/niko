package nikosub

import (
	"bytes"
	"errors"
	"math"
	"math/big"
	"strconv"
	"strings"

	"libcore/nikofmt"
)

// A subscription's JSON as the app reads it (RawUpdater.parseJSON): Android's org.json (nikofmt.OrgNextValue), its
// getters' coercions (ktx/Formats.kt getStr, getIntNya, getBool and JSONObject's opt*), Hysteria 1's and Trojan-Go's
// own JSON, a Shadowsocks server, SIP008, a sing-box config's outbounds as custom configs.

// errJSON is any exception the app's JSON branch meets: it then reads the text as base64 or as links.
var errJSON = errors.New("not a subscription's JSON")

const (
	hysteriaAuthString = 1
	hysteriaAuthBase64 = 2
	hysteriaFakeTCP    = 1
	hysteriaWeChat     = 2
)

// orgHas is JSONObject.has: the name is there, null or not.
func orgHas(o *nikofmt.OrgObject, name string) bool {
	_, ok := o.Values[name]
	return ok
}

// getStr is ktx getStr: a string that is not blank, else null.
func getStr(o *nikofmt.OrgObject, name string) *string {
	if s, ok := o.Values[name].(string); ok && !nikofmt.KtIsBlank(s) {
		return &s
	}
	return nil
}

// orgToInteger is the platform's JSON.toInteger: an Integer, a Number's intValue, a String as (int) parseDouble.
func orgToInteger(v any) (int, bool) {
	switch v := v.(type) {
	case int32:
		return int(v), true
	case int64:
		return int(int32(v)), true
	case float64:
		return javaDoubleToInt(v), true
	case string:
		if f, ok := nikofmt.JavaDoubleValueOf(v); ok {
			return javaDoubleToInt(f), true
		}
	}
	return 0, false
}

// javaDoubleToInt is Java's (int) of a double: towards zero, NaN 0, saturated at Integer's range.
func javaDoubleToInt(f float64) int {
	switch {
	case math.IsNaN(f):
		return 0
	case f >= math.MaxInt32:
		return math.MaxInt32
	case f <= math.MinInt32:
		return math.MinInt32
	}
	return int(f)
}

// getIntNya is ktx getIntNya: getInt, null where it throws (no such name, no number).
func getIntNya(o *nikofmt.OrgObject, name string) *int {
	if n, ok := orgToInteger(o.Values[name]); ok {
		return &n
	}
	return nil
}

// orgToBoolean is JSON.toBoolean: a Boolean, or "true" and "false" in any case.
func orgToBoolean(v any) (bool, bool) {
	switch v := v.(type) {
	case bool:
		return v, true
	case string:
		switch {
		case strings.EqualFold(v, "true"):
			return true, true
		case strings.EqualFold(v, "false"):
			return false, true
		}
	}
	return false, false
}

// getBool is ktx getBool: getBoolean, null where it throws.
func getBool(o *nikofmt.OrgObject, name string) *bool {
	if b, ok := orgToBoolean(o.Values[name]); ok {
		return &b
	}
	return nil
}

// optString is JSONObject.optString(name, fallback): the value's String.valueOf (JSONObject.NULL is "null"), the
// fallback where there is none.
func optString(o *nikofmt.OrgObject, name, fallback string) string {
	v, ok := o.Values[name]
	if !ok {
		return fallback
	}
	return javaToString(v)
}

func optInt(o *nikofmt.OrgObject, name string, fallback int) int {
	if n, ok := orgToInteger(o.Values[name]); ok {
		return n
	}
	return fallback
}

func optBoolean(o *nikofmt.OrgObject, name string, fallback bool) bool {
	if b, ok := orgToBoolean(o.Values[name]); ok {
		return b
	}
	return fallback
}

// optJSONArray: the value, where it is an array.
func optJSONArray(o *nikofmt.OrgObject, name string) ([]any, bool) {
	a, ok := o.Values[name].([]any)
	return a, ok
}

// filterObjects is ktx JSONArray.filterIsInstance<JSONObject>: get(i) of each, which throws on a null element.
func filterObjects(list []any) ([]*nikofmt.OrgObject, error) {
	var out []*nikofmt.OrgObject
	for _, e := range list {
		if e == nil {
			return nil, errJSON
		}
		if o, ok := e.(*nikofmt.OrgObject); ok {
			out = append(out, o)
		}
	}
	return out, nil
}

// parseJSONValue is parseJSON: the beans of a JSON value. An object that is none of the kinds gives none (and the
// app takes that for a subscription of no profiles); errJSON where the app's JSON branch throws.
func parseJSONValue(v any) ([]nikofmt.Bean, error) {
	var proxies []nikofmt.Bean
	switch v := v.(type) {
	case *nikofmt.OrgObject:
		switch {
		case orgHas(v, "server") && (orgHas(v, "up") || orgHas(v, "up_mbps")):
			return []nikofmt.Bean{hysteria1JSON(v)}, nil
		case orgHas(v, "method"):
			return []nikofmt.Bean{shadowsocksJSON(v)}, nil
		case orgHas(v, "remote_addr"):
			b, err := trojanGoJSON(v)
			if err != nil {
				return nil, err
			}
			return []nikofmt.Bean{b}, nil
		}
		if servers, ok := optJSONArray(v, "servers"); ok {
			// SIP008 online configuration: {"version": 1, "servers": [...]}
			objects, err := filterObjects(servers)
			if err != nil {
				return nil, err
			}
			out := []nikofmt.Bean{}
			for _, s := range objects {
				if orgHas(s, "server") && orgHas(s, "method") {
					b := shadowsocksJSON(s)
					b.InitializeDefaultValues()
					out = append(out, b)
				}
			}
			return out, nil
		}
		switch {
		case orgHas(v, "outbounds"):
			outbounds, ok := v.Values["outbounds"].([]any)
			if !ok {
				return nil, errJSON // getJSONArray throws
			}
			objects, err := filterObjects(outbounds)
			if err != nil {
				return nil, err
			}
			out := []nikofmt.Bean{}
			for _, o := range objects {
				switch ty := getStr(o, "type"); {
				case ty == nil, *ty == "dns", *ty == "block", *ty == "direct", *ty == "selector", *ty == "urltest":
					continue
				}
				b := &nikofmt.ConfigBean{}
				b.InitializeDefaultValues()
				b.Type = ptrTo(1)
				b.Config = ptrTo(toStringPretty(o))
				b.Name = getStr(o, "tag")
				out = append(out, b)
			}
			return out, nil
		case orgHas(v, "server") && orgHas(v, "server_port"):
			b := &nikofmt.ConfigBean{}
			b.InitializeDefaultValues()
			b.Type = ptrTo(1)
			b.Config = ptrTo(toStringPretty(v))
			return []nikofmt.Bean{b}, nil
		}
	case []any:
		for _, e := range v {
			switch e := e.(type) {
			case *nikofmt.OrgObject, []any:
				more, err := parseJSONValue(e)
				if err != nil {
					return nil, err
				}
				proxies = append(proxies, more...)
			case string:
				// isJsonObjectValid: a string is one where it reads as an object, and parseJSON of a string throws
				if _, err := nikofmt.OrgJSONObject(e); err == nil {
					return nil, errJSON
				}
			default:
				// a number, a boolean, JSONObject.NULL: isJsonObjectValid casts it to a String and throws; a null
				// element: JSONArray.get throws
				return nil, errJSON
			}
		}
	default:
		return nil, errJSON // json as JSONArray
	}
	for _, p := range proxies {
		p.InitializeDefaultValues()
	}
	if proxies == nil {
		proxies = []nikofmt.Bean{}
	}
	return proxies, nil
}

func ptrTo[T any](v T) *T { return &v }

// substringBeforeLast and substringAfterLast are Kotlin's, the whole string where the delimiter is missing.
func substringBeforeLast(s, delimiter string) string {
	if i := strings.LastIndex(s, delimiter); i >= 0 {
		return s[:i]
	}
	return s
}

func substringAfterLast(s, delimiter string) string {
	if i := strings.LastIndex(s, delimiter); i >= 0 {
		return s[i+len(delimiter):]
	}
	return s
}

// hysteria1JSON is parseHysteria1Json: Hysteria 1's client config. The app gives it no defaults: its Kryo then
// throws on the nulls left (the update fails, as KryoClone says).
func hysteria1JSON(o *nikofmt.OrgObject) *nikofmt.HysteriaBean {
	b := &nikofmt.HysteriaBean{ProtocolVersion: ptrTo(1)}
	server := optString(o, "server", "")
	b.ServerAddress = ptrTo(substringBeforeLast(server, ":"))
	b.ServerPorts = ptrTo(substringAfterLast(server, ":"))
	b.UploadMbps = getIntNya(o, "up_mbps")
	b.DownloadMbps = getIntNya(o, "down_mbps")
	b.Obfuscation = getStr(o, "obfs")
	if auth := getStr(o, "auth"); auth != nil {
		b.AuthPayloadType, b.AuthPayload = ptrTo(hysteriaAuthBase64), auth
	}
	if auth := getStr(o, "auth_str"); auth != nil {
		b.AuthPayloadType, b.AuthPayload = ptrTo(hysteriaAuthString), auth
	}
	if protocol := getStr(o, "protocol"); protocol != nil {
		switch *protocol {
		case "faketcp":
			b.Protocol = ptrTo(hysteriaFakeTCP)
		case "wechat-video":
			b.Protocol = ptrTo(hysteriaWeChat)
		}
	}
	b.Sni = getStr(o, "server_name")
	b.Alpn = getStr(o, "alpn")
	b.AllowInsecure = getBool(o, "insecure")
	b.StreamReceiveWindow = getIntNya(o, "recv_window_conn")
	b.ConnectionReceiveWindow = getIntNya(o, "recv_window")
	b.DisableMtuDiscovery = getBool(o, "disable_mtu_discovery")
	return b
}

// shadowsocksJSON is JSONObject.parseShadowsocks: a server of a Shadowsocks config or of SIP008.
func shadowsocksJSON(o *nikofmt.OrgObject) *nikofmt.ShadowsocksBean {
	b := &nikofmt.ShadowsocksBean{}
	b.ServerAddress = getStr(o, "server")
	b.ServerPort = getIntNya(o, "server_port")
	b.Password = getStr(o, "password")
	b.Method = getStr(o, "method")
	b.Name = ptrTo(optString(o, "remarks", ""))
	if plugin := getStr(o, "plugin"); plugin != nil && !nikofmt.KtIsBlank(*plugin) {
		b.Plugin = ptrTo(*plugin + ";" + optString(o, "plugin_opts", ""))
	}
	return b
}

// trojanGoJSON is JSONObject.parseTrojanGo, its quirks kept: "ssl", "websocket" and "shadowsocks" are read only
// where they are arrays, and then their keys are the top level's (Kotlin's receiver of optString is the object).
func trojanGoJSON(o *nikofmt.OrgObject) (*nikofmt.TrojanGoBean, error) {
	b := &nikofmt.TrojanGoBean{}
	b.InitializeDefaultValues()
	b.ServerAddress = ptrTo(optString(o, "remote_addr", *b.ServerAddress))
	b.ServerPort = ptrTo(optInt(o, "remote_port", *b.ServerPort))
	password, ok := o.Values["password"]
	if !ok {
		return nil, errJSON // get throws
	}
	if s, ok := password.(string); ok {
		b.Password = &s
	}
	if _, ok := optJSONArray(o, "ssl"); ok {
		b.Sni = ptrTo(optString(o, "sni", *b.Sni))
	}
	if _, ok := optJSONArray(o, "websocket"); ok && optBoolean(o, "enabled", false) {
		b.Type = ptrTo("ws")
		b.Host = ptrTo(optString(o, "host", *b.Host))
		b.Path = ptrTo(optString(o, "path", *b.Path))
	}
	if _, ok := optJSONArray(o, "shadowsocks"); ok && optBoolean(o, "enabled", false) {
		b.Encryption = ptrTo("ss;" + optString(o, "method", "") + ":" + optString(o, "password", ""))
	}
	return b, nil
}

// orgString is the platform's JSONObject.toString(): compact, its names in their order, '/' escaped, a number as
// numberToString writes it.
func orgString(v any) string {
	var b bytes.Buffer
	orgWrite(&b, v)
	return b.String()
}

func orgWrite(b *bytes.Buffer, v any) {
	switch v := v.(type) {
	case *nikofmt.OrgObject:
		b.WriteByte('{')
		for i, k := range v.Keys {
			if i > 0 {
				b.WriteByte(',')
			}
			orgQuote(b, k)
			b.WriteByte(':')
			orgWrite(b, v.Values[k])
		}
		b.WriteByte('}')
	case []any:
		b.WriteByte('[')
		for i, e := range v {
			if i > 0 {
				b.WriteByte(',')
			}
			orgWrite(b, e)
		}
		b.WriteByte(']')
	case nil, nikofmt.OrgNull:
		b.WriteString("null")
	case string:
		orgQuote(b, v)
	case bool:
		b.WriteString(strconv.FormatBool(v))
	case int32:
		b.WriteString(strconv.FormatInt(int64(v), 10))
	case int64:
		b.WriteString(strconv.FormatInt(v, 10))
	case float64:
		b.WriteString(orgNumberString(v))
	case *big.Int:
		b.WriteString(v.String())
	default:
		orgQuote(b, javaToString(v))
	}
}

// orgNumberString is JSONObject.numberToString of a Double: an integral one as its Long, -0 as "-0".
func orgNumberString(f float64) string {
	if f == 0 && math.Signbit(f) {
		return "-0"
	}
	if f >= -9.223372036854775808e18 && f < 9.223372036854775808e18 && f == math.Trunc(f) {
		return strconv.FormatInt(int64(f), 10)
	}
	return nikofmt.JavaDoubleString(f)
}

// orgQuote is JSONStringer's string: '"', '\' and '/' escaped, the short escapes, other controls as \u00xx.
func orgQuote(b *bytes.Buffer, s string) {
	b.WriteByte('"')
	for _, c := range s {
		switch c {
		case '"', '\\', '/':
			b.WriteByte('\\')
			b.WriteRune(c)
		case '\t':
			b.WriteString(`\t`)
		case '\b':
			b.WriteString(`\b`)
		case '\n':
			b.WriteString(`\n`)
		case '\r':
			b.WriteString(`\r`)
		case '\f':
			b.WriteString(`\f`)
		default:
			if c <= 0x1f {
				b.WriteString(`\u00`)
				b.WriteByte("0123456789abcdef"[c>>4])
				b.WriteByte("0123456789abcdef"[c&0xf])
			} else {
				b.WriteRune(c)
			}
		}
	}
	b.WriteByte('"')
}

// toStringPretty is ktx JSONObject.toStringPretty: the object's toString read by Gson and written by the app's Gson
// (pretty, two spaces, no HTML escaping), numbers as the text had them.
func toStringPretty(o *nikofmt.OrgObject) string {
	var b bytes.Buffer
	gsonPretty(&b, o, 0)
	return b.String()
}

func gsonPretty(b *bytes.Buffer, v any, indent int) {
	newline := func(level int) {
		b.WriteByte('\n')
		b.WriteString(strings.Repeat("  ", level))
	}
	switch v := v.(type) {
	case *nikofmt.OrgObject:
		if len(v.Keys) == 0 {
			b.WriteString("{}")
			return
		}
		b.WriteByte('{')
		for i, k := range v.Keys {
			if i > 0 {
				b.WriteByte(',')
			}
			newline(indent + 1)
			gsonQuote(b, k)
			b.WriteString(": ")
			gsonPretty(b, v.Values[k], indent+1)
		}
		newline(indent)
		b.WriteByte('}')
	case []any:
		if len(v) == 0 {
			b.WriteString("[]")
			return
		}
		b.WriteByte('[')
		for i, e := range v {
			if i > 0 {
				b.WriteByte(',')
			}
			newline(indent + 1)
			gsonPretty(b, e, indent+1)
		}
		newline(indent)
		b.WriteByte(']')
	case string:
		gsonQuote(b, v)
	default:
		// null, booleans and numbers: the text org.json wrote, which Gson keeps
		b.WriteString(orgString(v))
	}
}

// gsonQuote is Gson's JsonWriter.string without HTML escaping: '"' and '\', the short escapes, other controls and
// U+2028, U+2029 as \u.
func gsonQuote(b *bytes.Buffer, s string) {
	b.WriteByte('"')
	for _, c := range s {
		switch {
		case c == '"' || c == '\\':
			b.WriteByte('\\')
			b.WriteRune(c)
		case c == '\t':
			b.WriteString(`\t`)
		case c == '\b':
			b.WriteString(`\b`)
		case c == '\n':
			b.WriteString(`\n`)
		case c == '\r':
			b.WriteString(`\r`)
		case c == '\f':
			b.WriteString(`\f`)
		case c < 0x20 || c == 0x2028 || c == 0x2029:
			b.WriteString(`\u`)
			b.WriteString(strconv.FormatInt(int64(c)|0x10000, 16)[1:])
		default:
			b.WriteRune(c)
		}
	}
	b.WriteByte('"')
}

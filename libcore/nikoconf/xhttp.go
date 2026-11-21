package nikoconf

import (
	"fmt"
	"math"
	"regexp"
	"strconv"

	"libcore/nikofmt"
)

// XHTTP's transport options (fmt/v2ray/XHTTP.kt): the core's XHTTP client comes from sing-box-lx, and Xray's
// "extra" JSON of a profile is turned into its options.

// xhttpModes are the modes the core's XHTTP client takes; anything else would fail the config.
var xhttpModes = setOf("auto", "packet-up", "stream-up", "stream-one")

// xhttpOptionKeys are the core's XHTTP transport options (sing-box-lx option/v2ray_xhttp.go).
var xhttpOptionKeys = setOf(
	"headers", "x_padding_bytes", "xmux", "no_grpc_header", "session_placement", "session_key",
	"seq_placement", "seq_key", "session_table", "session_length", "uplink_data_placement",
	"uplink_data_key", "uplink_chunk_size", "uplink_http_method", "x_padding_obfs_mode",
	"x_padding_key", "x_padding_header", "x_padding_placement", "x_padding_method",
	"sc_max_each_post_bytes", "sc_min_posts_interval_ms", "sc_max_concurrent_posts",
	"server_max_header_bytes", "no_sse_header", "sc_max_buffered_posts", "sc_stream_up_server_secs",
)

var xmuxOptionKeys = setOf("max_concurrency", "max_connections", "c_max_reuse_times", "h_max_request_times",
	"h_max_reusable_secs", "h_keep_alive_period")

// xhttpRangeKeys are options the core takes as "min-max" strings, which Xray may write as {"from": a, "to": b}.
var xhttpRangeKeys = setOf("x_padding_bytes", "sc_max_each_post_bytes", "sc_min_posts_interval_ms",
	"sc_stream_up_server_secs", "uplink_chunk_size", "session_length")

// numbers and booleans in the core; Xray wrote some of them as ranges ("scMaxConcurrentPosts": "100-200")
var (
	xhttpIntKeys  = setOf("sc_max_concurrent_posts", "server_max_header_bytes", "sc_max_buffered_posts")
	xhttpBoolKeys = setOf("no_grpc_header", "x_padding_obfs_mode", "no_sse_header")
)

func setOf(keys ...string) map[string]bool {
	set := make(map[string]bool, len(keys))
	for _, k := range keys {
		set[k] = true
	}
	return set
}

var (
	snakeLowerUpper = regexp.MustCompile(`([a-z0-9])([A-Z])`)
	snakeUpperRun   = regexp.MustCompile(`([A-Z]+)([A-Z][a-z])`)
)

// xhttpSnakeCase: xPaddingBytes -> x_padding_bytes, noGRPCHeader -> no_grpc_header, uplinkHTTPMethod ->
// uplink_http_method.
func xhttpSnakeCase(key string) string {
	key = snakeLowerUpper.ReplaceAllString(key, "${1}_${2}")
	key = snakeUpperRun.ReplaceAllString(key, "${1}_${2}")
	return nikofmt.KtLowercase(key)
}

// xhttpOptions is buildXhttpOptions: the extra JSON's options, then host, path and mode. A downloadSettings in the
// extra is left out (the app warns in its log only: downloads then go to the main server).
func xhttpOptions(bean nikofmt.Bean, b *nikofmt.StandardV2RayBean) (object, error) {
	options, err := xhttpExtraOptions(str(b.XhttpExtra))
	if err != nil {
		return nil, fmt.Errorf("XHTTP extra (%s): %w", nikofmt.DisplayName(bean), err)
	}
	if !blank(b.Host) {
		options["host"] = *b.Host
	}
	options["path"] = pathOrRoot(b.Path)
	if b.XhttpMode != nil && xhttpModes[*b.XhttpMode] {
		options["mode"] = *b.XhttpMode
	}
	return options, nil
}

// xhttpExtraOptions is Xray's XHTTP "extra" JSON (camelCase) as the core's transport options, read as Android's
// org.json reads it. Keys the core does not know (downloadSettings, ...) are left out, as is a value of the wrong
// type: sing-box would refuse either, and the whole config with it.
func xhttpExtraOptions(extra string) (object, error) {
	options := object{}
	if nikofmt.KtIsBlank(extra) {
		return options, nil
	}
	json, err := orgJSONObject(extra)
	if err != nil {
		return nil, err
	}
	for _, rawKey := range json.keys {
		key := xhttpSnakeCase(rawKey)
		if !xhttpOptionKeys[key] {
			continue
		}
		value := json.values[rawKey]
		var option any
		switch o, isObject := value.(*orgObject); {
		case key == "xmux":
			if !isObject {
				continue
			}
			if option, err = xmuxOptions(o); err != nil {
				return nil, err
			}
		case key == "headers":
			if !isObject {
				continue
			}
			if option, err = orgValue(o); err != nil {
				return nil, err
			}
		case xhttpIntKeys[key]:
			n, ok := integralValue(value)
			if !ok {
				continue
			}
			option = n
		case xhttpBoolKeys[key]:
			v, ok := value.(bool)
			if !ok {
				continue
			}
			option = v
		case xhttpRangeKeys[key] && isObject:
			from, ok := integralValue(o.values["from"])
			if !ok {
				continue
			}
			to, ok := integralValue(o.values["to"])
			if !ok {
				continue
			}
			option = strconv.FormatInt(from, 10) + "-" + strconv.FormatInt(to, 10)
		case xhttpRangeKeys[key] && isOrgNumber(value):
			n, ok := integralValue(value)
			if !ok {
				continue
			}
			option = strconv.FormatInt(n, 10)
		default:
			s, ok := value.(string)
			if !ok {
				continue
			}
			option = s
		}
		options[key] = option
	}
	return options, nil
}

// xmuxOptions: ranges as "min-max", a number or [min, max]; h_keep_alive_period a number.
func xmuxOptions(xmux *orgObject) (object, error) {
	options := object{}
	for _, rawKey := range xmux.keys {
		key := xhttpSnakeCase(rawKey)
		value := xmux.values[rawKey]
		_, isString := value.(string)
		_, isArray := value.([]any)
		switch {
		case !xmuxOptionKeys[key]:
		case key == "h_keep_alive_period":
			if n, ok := integralValue(value); ok {
				options[key] = n
			}
		case isString || isArray:
			v, err := orgValue(value)
			if err != nil {
				return nil, err
			}
			options[key] = v
		default:
			if n, ok := integralValue(value); ok {
				options[key] = n
			}
		}
	}
	return options, nil
}

func isOrgNumber(v any) bool {
	switch v.(type) {
	case int32, int64, float64:
		return true
	}
	return false
}

// integralValue: an Integer or Long, or a Double without a fraction, as a Long (Kotlin's toLong saturates).
func integralValue(v any) (int64, bool) {
	switch v := v.(type) {
	case int32:
		return int64(v), true
	case int64:
		return v, true
	case float64:
		if math.Mod(v, 1) != 0 { // NaN and the infinities too
			return 0, false
		}
		switch {
		case v >= math.MaxInt64:
			return math.MaxInt64, true
		case v <= math.MinInt64:
			return math.MinInt64, true
		}
		return int64(v), true
	}
	return 0, false
}

// orgValue is jsonValue: org.json's values as the outbound's. JSONObject.NULL is nil, which Gson then leaves out of
// an object (Android's NULL is an anonymous class, which Gson writes as null); an array's missing element (Java's
// null, from "[1,]") is an error, as JSONArray.get throws for it.
func orgValue(v any) (any, error) {
	switch v := v.(type) {
	case *orgObject:
		o := object{}
		for _, k := range v.keys {
			e, err := orgValue(v.values[k])
			if err != nil {
				return nil, err
			}
			o[k] = e
		}
		return o, nil
	case []any:
		list := make([]any, len(v))
		for i, e := range v {
			if e == nil {
				return nil, fmt.Errorf("value at %d is null", i)
			}
			var err error
			if list[i], err = orgValue(e); err != nil {
				return nil, err
			}
		}
		return list, nil
	case orgNull:
		return nil, nil
	case int32:
		return int64(v), nil
	}
	return v, nil
}

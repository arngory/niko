// Package nikoconf is the app's config build (app/src/main/java/io/niko/fmt/ConfigBuilder.kt) in Go, for the desktop
// daemon (docs/design/desktop-stage3.md §5): the same core config from the same profiles, which the golden fixtures
// the app writes (shared/golden) hold both sides to. This file: the outbounds of a profile, as the app's builders
// make them (Outbound); the rest of the build comes later.
//
// The builders are ported as the app has them, their quirks included (each says so where it is). The beans are
// libcore/nikofmt's, filled with the app's defaults by its parsers.
package nikoconf

import (
	"errors"
	"fmt"
	"math"
	"strconv"
	"strings"

	"libcore/nikofmt"
)

// ErrNeedsPlugin is a profile the core has no outbound for, which the app runs a plugin for
// (ProxyEntity.needExternal): Hysteria over faketcp or wechat-video.
var ErrNeedsPlugin = errors.New("the profile needs a plugin, the core has no outbound for it")

// object is a JSON object of the outbound, as SingBoxOption.asMap gives it.
type object = map[string]any

// Outbound is the core's outbound of a profile as the app builds it (ConfigBuilder.kt buildSingBoxOutbound: each
// type's builder), its JSON object as SingBoxOption.asMap reads it back from Gson: a field the builder sets is there
// even where empty, false or 0, one it leaves null is not. A custom outbound (ConfigBean) is its config's JSON, and
// WireGuard's is an endpoint. Not yet what the build adds (the tag, mux, the profile's custom JSON, Niko's own
// parameters: knocking, pins, fragments, DNS tunnels, raycontrol's). globalInsecure is the settings' "allow
// insecure", which only the builders that take it in the app look at (not AnyTLS's, nor naive's).
//
// Values are what encoding/json decodes into an any, but for numbers: int64 for whole ones (Kotlin's Int and Long,
// which Gson reads back as Long), float64 for the others, which only a custom config has.
func Outbound(b nikofmt.Bean, globalInsecure bool) (map[string]any, error) {
	if needsPlugin(b) {
		return nil, ErrNeedsPlugin
	}
	var out object
	var err error
	switch b := b.(type) {
	case *nikofmt.ConfigBean:
		out, err = customOutbound(b)
	// ShadowTLS, which the app builds before the V2Ray family its bean belongs to, has no bean here: no link or sn://
	// type makes one
	case *nikofmt.HttpBean:
		out = httpOutbound(b, globalInsecure)
	case *nikofmt.VMessBean:
		out, err = vmessOutbound(b, globalInsecure)
	case *nikofmt.TrojanBean:
		out, err = trojanOutbound(b, globalInsecure)
	case *nikofmt.HysteriaBean:
		out, err = hysteriaOutbound(b, globalInsecure)
	case *nikofmt.TuicBean:
		out, err = tuicOutbound(b, globalInsecure)
	case *nikofmt.SOCKSBean:
		out = socksOutbound(b)
	case *nikofmt.ShadowsocksBean:
		out = shadowsocksOutbound(b)
	case *nikofmt.WireGuardBean:
		out, err = wireGuardEndpoint(b)
	case *nikofmt.SSHBean:
		out = sshOutbound(b)
	case *nikofmt.AnyTLSBean:
		out = anyTLSOutbound(b)
	case *nikofmt.NaiveBean:
		out = naiveOutbound(b)
	case *nikofmt.TrojanGoBean:
		out, err = trojanGoOutbound(b, globalInsecure)
	case *nikofmt.MieruBean:
		out = mieruOutbound(b)
	default:
		return nil, fmt.Errorf("no outbound for a %T", b)
	}
	if err != nil {
		return nil, err
	}
	if err = asMap(out); err != nil {
		return nil, err
	}
	return out, nil
}

// needsPlugin is ProxyEntity.needExternal: a Hysteria profile the core cannot take (HysteriaBean.canUseSingBox).
// The app's neko type, a plugin's own profile, has no bean here.
func needsPlugin(b nikofmt.Bean) bool {
	h, ok := b.(*nikofmt.HysteriaBean)
	return ok && num(h.Protocol) != hysteriaProtocolUDP
}

// asMap is what Gson's round trip of asMap does to the object: a null member left out (serializeNulls is off; a null
// in an array stays), and an error for NaN or an infinity, which Gson refuses to write.
func asMap(o object) error {
	for k, v := range o {
		if v == nil {
			delete(o, k)
		} else if err := asMapValue(v); err != nil {
			return err
		}
	}
	return nil
}

func asMapValue(v any) error {
	switch v := v.(type) {
	case object:
		return asMap(v)
	case []any:
		for _, e := range v {
			if err := asMapValue(e); err != nil {
				return err
			}
		}
	case float64:
		if math.IsNaN(v) || math.IsInf(v, 0) {
			return fmt.Errorf("%v is not a valid double value as per JSON specification", v)
		}
	}
	return nil
}

// customOutbound is CustomSingBoxOption(bean.config): the config's JSON as the object itself, read by the app's
// lenient Gson; an empty one, or null, is an empty object.
func customOutbound(b *nikofmt.ConfigBean) (object, error) {
	m, err := nikofmt.GsonMap(str(b.Config))
	if err != nil {
		return nil, fmt.Errorf("%s: custom config: %w", nikofmt.DisplayName(b), err)
	}
	if m == nil {
		m = object{}
	}
	return m, nil
}

// The setters of a field a builder takes from the bean: Gson leaves it out where the bean's is null.

func putString(o object, key string, s *string) {
	if s != nil {
		o[key] = *s
	}
}

func putInt(o object, key string, n *int) {
	if n != nil {
		o[key] = int64(*n)
	}
}

func putBool(o object, key string, v *bool) {
	if v != nil {
		o[key] = *v
	}
}

// str, num and flag read a bean's field where Kotlin takes it as not null: null is "", 0 and false (the parsers'
// defaults leave none null).

func str(s *string) string {
	if s == nil {
		return ""
	}
	return *s
}

func num(n *int) int {
	if n == nil {
		return 0
	}
	return *n
}

func flag(b *bool) bool { return b != nil && *b }

// blank is Kotlin's isNullOrBlank, and !isNotBlank of a field the parsers' defaults leave not null.
func blank(s *string) bool { return s == nil || nikofmt.KtIsBlank(*s) }

// intString is an Integer in a Kotlin string template: "null" for null.
func intString(n *int) string {
	if n == nil {
		return "null"
	}
	return strconv.Itoa(*n)
}

// listByLineOrComma is utils/KotlinUtil.kt String.listByLineOrComma: split at ',' and '\n', each trimmed, the empty
// ones left out.
func listByLineOrComma(s string) []any {
	list := []any{}
	for _, line := range strings.Split(s, "\n") {
		for _, item := range strings.Split(line, ",") {
			if item = nikofmt.KtTrim(item); item != "" {
				list = append(list, item)
			}
		}
	}
	return list
}

func strings2any(list []string) []any {
	out := make([]any, len(list))
	for i, s := range list {
		out[i] = s
	}
	return out
}

// substringBefore and substringAfter are Kotlin's: the part before or after the first delimiter, all of s without
// one; substringAfterOr gives missing then.

func substringBefore(s, delimiter string) string {
	before, _, _ := strings.Cut(s, delimiter)
	return before
}

func substringAfter(s, delimiter string) string { return substringAfterOr(s, delimiter, s) }

func substringAfterOr(s, delimiter, missing string) string {
	if _, after, ok := strings.Cut(s, delimiter); ok {
		return after
	}
	return missing
}

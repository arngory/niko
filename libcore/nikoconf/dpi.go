package nikoconf

import (
	"regexp"
	"strconv"
	"strings"
)

// «Обход DPI для прямых соединений» (DpiDirect.kt, docs/design/dpi.md §5.2): the core's tcp_desync on the direct
// rules that have their dpiBypass flag, a ready-made strategy or the user's ByeDPI string. The same as the app's,
// held by shared/golden/dpi/byedpi.json.

// The setting's values (Settings.DpiDirect).
const (
	DpiOff    = 0
	DpiPreset = 1
	DpiCustom = 2

	DpiDefaultPreset = "tlsrec_sni"

	dpiMaxCuts   = 64
	dpiMaxOffset = 65535
)

// DpiCut is a segment cut: at a core's marker, the part that ends there sent by Mode.
type DpiCut struct {
	At   string
	Mode string
}

// DpiStrategy is a tcp_desync strategy; OOBByte "" the core's "a".
type DpiStrategy struct {
	Split   []DpiCut
	TLSRec  []string
	OOBByte string
}

// config is the strategy as the route rule's tcp_desync has it.
func (s *DpiStrategy) config() object {
	o := object{}
	if len(s.Split) > 0 {
		split := make([]any, 0, len(s.Split))
		for _, cut := range s.Split {
			split = append(split, object{"at": cut.At, "mode": cut.Mode})
		}
		o["split"] = split
	}
	if len(s.TLSRec) > 0 {
		rec := make([]any, 0, len(s.TLSRec))
		for _, at := range s.TLSRec {
			rec = append(rec, at)
		}
		o["tlsrec"] = rec
	}
	if s.OOBByte != "" {
		o["oob_byte"] = s.OOBByte
	}
	return o
}

// DpiPreset is a ready-made strategy.
type DpiPresetStrategy struct {
	ID       string
	Strategy DpiStrategy
}

// DpiPresets are the app's (DpiDirect.presets), in its order.
var DpiPresets = []DpiPresetStrategy{
	{DpiDefaultPreset, DpiStrategy{TLSRec: []string{"sni+1"}}},
	{"split_sni", DpiStrategy{Split: []DpiCut{{"1", "split"}, {"midsld", "split"}}}},
	{"disoob_sld", DpiStrategy{Split: []DpiCut{{"midsld", "disoob"}}}},
	{"disorder_1", DpiStrategy{Split: []DpiCut{{"1", "disorder"}}}},
}

// dpiStrategy is the setting's strategy; nil: off, an unknown preset, an own string that does not parse.
func (s *Settings) dpiStrategy() *DpiStrategy {
	switch s.DpiDirect {
	case DpiPreset:
		for i := range DpiPresets {
			if DpiPresets[i].ID == s.DpiDirectPreset {
				return &DpiPresets[i].Strategy
			}
		}
	case DpiCustom:
		if strategy, err := ParseByeDpi(s.DpiDirectCustom); err == nil {
			return strategy
		}
	}
	return nil
}

// ByeDpiError is why a ByeDPI string is not taken: Kind is DpiDirect.ErrorKind's code, Flag the flag as typed, Value
// what was wrong.
type ByeDpiError struct {
	Kind  string
	Flag  string
	Value string
}

func (e *ByeDpiError) Error() string {
	parts := []string{"byedpi: " + e.Kind}
	if e.Flag != "" {
		parts = append(parts, e.Flag)
	}
	if e.Value != "" {
		parts = append(parts, strconv.Quote(e.Value))
	}
	return strings.Join(parts, " ")
}

var byeDpiLong = map[string]byte{
	"split": 's', "disorder": 'd', "oob": 'o', "disoob": 'q', "tlsrec": 'r', "oob-data": 'e',
	"fake": 'f', "ttl": 't', "fake-sni": 'n', "fake-tls-mod": 'Q', "fake-offset": 'O', "fake-data": 'l',
	"md5sig": 'S', "auto": 'A', "auto-mode": 'L', "timeout": 'T', "cache-ttl": 'u', "cache-dump": 'y',
}

var byeDpiModes = map[byte]string{'s': "split", 'd': "disorder", 'o': "oob", 'q': "disoob"}

func isZapret(token string) bool {
	if token == "nfqws" || token == "tpws" || token == "--new" {
		return true
	}
	for _, prefix := range []string{"--dpi-desync", "--wf-", "--filter-", "--hostlist", "--qnum", "--split-pos", "--orig-",
		"--dup"} {
		if strings.HasPrefix(token, prefix) {
			return true
		}
	}
	return false
}

func isLetter(c byte) bool { return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' }

// ParseByeDpi is DpiDirect.parseByeDpi: a ByeDPI command line, its subset (-s, -d, -o, -q, -r with ByeDPI's
// positions, -e), as the core's strategy.
func ParseByeDpi(text string) (*DpiStrategy, error) {
	tokens := strings.Fields(text)
	if len(tokens) > 0 {
		first := tokens[0][strings.LastIndexByte(tokens[0], '/')+1:]
		if first == "ciadpi" || first == "byedpi" {
			tokens = tokens[1:]
		}
	}
	if len(tokens) == 0 {
		return nil, &ByeDpiError{Kind: "empty"}
	}
	for _, token := range tokens {
		if isZapret(token) {
			return nil, &ByeDpiError{Kind: "nfqws"}
		}
	}
	strategy := &DpiStrategy{}
	for i := 0; i < len(tokens); {
		token := tokens[i]
		i++
		var flag string
		var option byte
		var value string
		hasValue := false
		switch {
		case strings.HasPrefix(token, "--") && len(token) > 2:
			flag = token
			if eq := strings.IndexByte(token, '='); eq >= 0 {
				flag, value, hasValue = token[:eq], token[eq+1:], true
			}
			var ok bool
			if option, ok = byeDpiLong[flag[2:]]; !ok {
				if strings.HasPrefix(flag, "--fake") {
					return nil, &ByeDpiError{Kind: "fake", Flag: flag}
				}
				return nil, &ByeDpiError{Kind: "unsupported", Flag: flag}
			}
		case len(token) >= 2 && token[0] == '-' && isLetter(token[1]):
			flag, option = token[:2], token[1]
			if len(token) > 2 {
				value, hasValue = token[2:], true
			}
		default:
			return nil, &ByeDpiError{Kind: "unexpected", Value: token}
		}
		switch {
		case strings.IndexByte("ftnQOlS", option) >= 0:
			return nil, &ByeDpiError{Kind: "fake", Flag: flag}
		case strings.IndexByte("ALTuy", option) >= 0:
			return nil, &ByeDpiError{Kind: "auto", Flag: flag}
		case strings.IndexByte("sdoqre", option) < 0:
			return nil, &ByeDpiError{Kind: "unsupported", Flag: flag}
		}
		// getopt takes the next word as the value whatever it is, "-1" too
		if !hasValue {
			if i >= len(tokens) {
				return nil, &ByeDpiError{Kind: "no_value", Flag: flag}
			}
			value = tokens[i]
			i++
		}
		switch option {
		case 'e':
			if len(value) != 1 || value[0] < '!' || value[0] > '~' {
				return nil, &ByeDpiError{Kind: "bad_oob_data", Flag: flag, Value: value}
			}
			strategy.OOBByte = value
		case 'r':
			at, ok := byeDpiPositions(value, true)
			if !ok {
				return nil, &ByeDpiError{Kind: "bad_position", Flag: flag, Value: value}
			}
			strategy.TLSRec = append(strategy.TLSRec, at...)
		default:
			at, ok := byeDpiPositions(value, false)
			if !ok {
				return nil, &ByeDpiError{Kind: "bad_position", Flag: flag, Value: value}
			}
			for _, a := range at {
				strategy.Split = append(strategy.Split, DpiCut{At: a, Mode: byeDpiModes[option]})
			}
		}
		if len(strategy.Split)+len(strategy.TLSRec) > dpiMaxCuts {
			return nil, &ByeDpiError{Kind: "too_many", Flag: flag, Value: value}
		}
	}
	if len(strategy.Split) == 0 && len(strategy.TLSRec) == 0 {
		return nil, &ByeDpiError{Kind: "nothing"}
	}
	return strategy, nil
}

var byeDpiPosition = regexp.MustCompile(`^(-?\d+)(?::(\d+)(?::(\d+))?)?(?:\+([shn])([emrs])?)?$`)

// byeDpiNumber is a decimal number as strtol takes it, but no octal ("010") and no hex.
func byeDpiNumber(text string) (int, bool) {
	digits := strings.TrimPrefix(text, "-")
	if len(digits) > 1 && digits[0] == '0' {
		return 0, false
	}
	n, err := strconv.Atoi(text)
	return n, err == nil
}

// byeDpiPositions is ByeDPI's position as the core's markers, one for each repeat; record: a -r position, whose plain
// offset ≥ 0 counts after the record's header.
func byeDpiPositions(text string, record bool) ([]string, bool) {
	m := byeDpiPosition.FindStringSubmatch(text)
	if m == nil {
		return nil, false
	}
	offsetText, repeatsText, skipText, baseText, endText := m[1], m[2], m[3], m[4], m[5]
	offset, ok := byeDpiNumber(offsetText)
	if !ok {
		return nil, false
	}
	repeats, skip := 1, 0
	if repeatsText != "" {
		if repeats, ok = byeDpiNumber(repeatsText); !ok {
			return nil, false
		}
	}
	if skipText != "" {
		if skip, ok = byeDpiNumber(skipText); !ok {
			return nil, false
		}
	}
	if repeats < 1 || repeats > dpiMaxCuts {
		return nil, false
	}
	base := ""
	switch baseText {
	case "s":
		base = "sni"
	case "h":
		base = "host"
	}
	switch endText {
	case "e":
		if base != "" {
			base = "endhost"
		} else if offset >= 0 {
			return nil, false
		}
	case "m":
		if base == "" {
			return nil, false
		}
		base = "midhost"
	case "r":
		return nil, false
	}
	flagged := baseText == "s" || baseText == "h" || endText != ""
	shift := 0
	if record && !flagged && offset >= 0 {
		shift = 5
	}
	var out []string
	for k := 0; k < repeats; k++ {
		at := offset + shift + skip*k
		switch {
		case at > dpiMaxOffset || at < -dpiMaxOffset:
			return nil, false
		case base == "" && offset < 0 && at >= 0:
			// from the end, repeated past it: ByeDPI's cut falls at the end, which is none
		case base == "":
			out = append(out, strconv.Itoa(at))
		case at > 0:
			out = append(out, base+"+"+strconv.Itoa(at))
		case at < 0:
			out = append(out, base+strconv.Itoa(at))
		default:
			out = append(out, base)
		}
	}
	return out, true
}

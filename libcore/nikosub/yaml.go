package nikosub

import (
	"bytes"
	"encoding/base64"
	"errors"
	"fmt"
	"io"
	"math"
	"math/big"
	"regexp"
	"strconv"
	"strings"
	"time"

	"libcore/nikofmt"

	"go.yaml.in/yaml/v3"
)

// A Clash config as the app loads it (group/RawUpdater.kt loadClashYaml): SnakeYAML 1.33 with a SafeConstructor and
// its default LoaderOptions, the local tag "!str" a string. yaml.v3 parses; what SnakeYAML makes of the nodes is done
// here: YAML 1.1's implicit types by SnakeYAML's own patterns (yes and off are booleans, 0777 is octal, 1:30 is 90),
// merge keys (<<), a duplicate key's last value, at most 50 aliases of collections and 50 nested collections.
//
// Values are what SafeConstructor builds: *yamlMap (a LinkedHashMap), []any, string, bool, *big.Int (Integer, Long
// or BigInteger, which all print their digits), float64 (Double), time.Time (a timestamp's Date), []byte (binary)
// and nil.

// yamlMap is a mapping in its keys' order.
type yamlMap struct {
	keys   []any
	values []any
}

// get is Map.get of a string key.
func (m *yamlMap) get(key string) any {
	for i, k := range m.keys {
		if s, ok := k.(string); ok && s == key {
			return m.values[i]
		}
	}
	return nil
}

func (m *yamlMap) has(key string) bool {
	for _, k := range m.keys {
		if s, ok := k.(string); ok && s == key {
			return true
		}
	}
	return false
}

// errYAML is SnakeYAML's YAMLException: the app takes the text for something else than a Clash config.
var errYAML = errors.New("not a Clash config")

// fatalYAML is what SnakeYAML throws besides a YAMLException (a NumberFormatException of an explicit !!int or
// !!float): the app's update fails on it.
type fatalYAML struct{ err error }

func (e *fatalYAML) Error() string { return "the Clash config: " + e.err.Error() }

const (
	maxAliasesForCollections = 50
	nestingDepthLimit        = 50
	// a resolver's limit on a plain scalar's length (Resolver: 1024 by default, 10 for booleans, nulls and the merge
	// key, 50 for timestamps)
	resolveLimit = 1024
)

// SnakeYAML 1.33's Resolver patterns, as its jar has them.
var (
	yamlBool      = regexp.MustCompile(`^(?:yes|Yes|YES|no|No|NO|true|True|TRUE|false|False|FALSE|on|On|ON|off|Off|OFF)$`)
	yamlFloat     = regexp.MustCompile(`^([-+]?(?:[0-9][0-9_]*)\.[0-9_]*(?:[eE][-+]?[0-9]+)?|[-+]?(?:[0-9][0-9_]*)(?:[eE][-+]?[0-9]+)|[-+]?\.[0-9_]+(?:[eE][-+]?[0-9]+)?|[-+]?[0-9][0-9_]*(?::[0-5]?[0-9])+\.[0-9_]*|[-+]?\.(?:inf|Inf|INF)|\.(?:nan|NaN|NAN))$`)
	yamlInt       = regexp.MustCompile(`^(?:[-+]?0b_*[0-1][0-1_]*|[-+]?0_*[0-7][0-7_]*|[-+]?(?:0|[1-9][0-9_]*)|[-+]?0x_*[0-9a-fA-F][0-9a-fA-F_]*|[-+]?[1-9][0-9_]*(?::[0-5]?[0-9])+)$`)
	yamlMerge     = regexp.MustCompile(`^(?:<<)$`)
	yamlNull      = regexp.MustCompile(`^(?:~|null|Null|NULL| )$`)
	yamlTimestamp = regexp.MustCompile(`^(?:[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9]|[0-9][0-9][0-9][0-9]-[0-9][0-9]?-[0-9][0-9]?(?:[Tt]|[ \t]+)[0-9][0-9]?:[0-9][0-9]:[0-9][0-9](?:\.[0-9]*)?(?:[ \t]*(?:Z|[-+][0-9][0-9]?(?::[0-9][0-9])?))?)$`)
)

type yamlTag int

const (
	tagStr yamlTag = iota
	tagBool
	tagInt
	tagFloat
	tagMerge
	tagNull
	tagTimestamp
)

// resolve is Resolver.resolve of a plain scalar: the resolvers of its first character in their order, each within its
// limit, then the empty one; a string where none matches.
func resolve(value string) yamlTag {
	type resolver struct {
		tag   yamlTag
		re    *regexp.Regexp
		first string
		limit int
	}
	resolvers := []resolver{
		{tagBool, yamlBool, "yYnNtTfFoO", 10},
		{tagInt, yamlInt, "-+0123456789", resolveLimit},
		{tagFloat, yamlFloat, "-+0123456789.", resolveLimit},
		{tagMerge, yamlMerge, "<", 10},
		{tagNull, yamlNull, "~nN\x00", 10},
		{tagTimestamp, yamlTimestamp, "0123456789", 50},
	}
	first := "\x00"
	if value != "" {
		first = value[:1]
	}
	length := javaLength(value)
	for _, r := range resolvers {
		if strings.Contains(r.first, first) && length <= r.limit && r.re.MatchString(value) {
			return r.tag
		}
	}
	if value == "" {
		return tagNull
	}
	return tagStr
}

// javaLength is the string's length in Java's chars, for the resolvers' limits.
func javaLength(s string) int {
	n := 0
	for _, r := range s {
		if r >= 0x10000 {
			n += 2
		} else {
			n++
		}
	}
	return n
}

// loadClashYAML is loadClashYaml: the text's one document, which must be a mapping. errYAML for what SnakeYAML
// refuses with a YAMLException; *fatalYAML for what it throws otherwise.
func loadClashYAML(text string) (*yamlMap, error) {
	decoder := yaml.NewDecoder(strings.NewReader(text))
	var doc yaml.Node
	if err := decoder.Decode(&doc); err != nil {
		if errors.Is(err, io.EOF) {
			return nil, errYAML // an empty stream loads as null
		}
		return nil, errYAML
	}
	var second yaml.Node
	if err := decoder.Decode(&second); !errors.Is(err, io.EOF) {
		return nil, errYAML // expected a single document in the stream
	}
	c := &yamlComposer{composing: map[*yaml.Node]bool{}, built: map[*yaml.Node]any{}}
	root := &doc
	if root.Kind == yaml.DocumentNode {
		if len(root.Content) == 0 {
			return nil, errYAML
		}
		root = root.Content[0]
	}
	v, err := c.construct(root)
	if err != nil {
		return nil, err
	}
	m, ok := v.(*yamlMap)
	if !ok {
		return nil, errYAML
	}
	return m, nil
}

type yamlComposer struct {
	depth   int
	aliases int
	// composing: the collections being built, for a recursive alias; built: what an anchor gave, shared by its
	// aliases as SnakeYAML's constructed objects are
	composing map[*yaml.Node]bool
	built     map[*yaml.Node]any
}

func (c *yamlComposer) construct(n *yaml.Node) (any, error) {
	if n.Kind == yaml.AliasNode {
		target := n.Alias
		if target == nil {
			return nil, errYAML
		}
		if target.Kind != yaml.ScalarNode {
			c.aliases++
			if c.aliases > maxAliasesForCollections {
				return nil, errYAML
			}
		}
		if c.composing[target] {
			return nil, errYAML // a recursive alias
		}
		if v, ok := c.built[target]; ok {
			return v, nil
		}
		return c.construct(target)
	}
	switch n.Kind {
	case yaml.ScalarNode:
		v, err := c.scalar(n)
		if err == nil && n.Anchor != "" {
			c.built[n] = v
		}
		return v, err
	case yaml.SequenceNode, yaml.MappingNode:
		if c.depth > nestingDepthLimit {
			return nil, errYAML
		}
		c.depth++
		c.composing[n] = true
		var v any
		var err error
		if n.Kind == yaml.SequenceNode {
			v, err = c.sequence(n)
		} else {
			v, err = c.mapping(n)
		}
		c.composing[n] = false
		c.depth--
		if err == nil && n.Anchor != "" {
			c.built[n] = v
		}
		return v, err
	}
	return nil, errYAML
}

// explicitTag is a node's tag as written, "" for none.
func explicitTag(n *yaml.Node) string {
	if n.Style&yaml.TaggedStyle == 0 {
		return ""
	}
	return n.Tag
}

func (c *yamlComposer) sequence(n *yaml.Node) (any, error) {
	if tag := explicitTag(n); tag != "" && tag != "!!seq" {
		return nil, errYAML // !!set, !!omap and the like are not read here
	}
	list := []any{}
	for _, item := range n.Content {
		v, err := c.construct(item)
		if err != nil {
			return nil, err
		}
		list = append(list, v)
	}
	return list, nil
}

// tuple is a key and value node of a mapping.
type tuple struct{ key, value *yaml.Node }

// isMergeKey: a plain << (or one tagged !!merge).
func isMergeKey(n *yaml.Node) bool {
	if n.Kind != yaml.ScalarNode {
		return false
	}
	if tag := explicitTag(n); tag != "" {
		return tag == "!!merge"
	}
	return n.Style&(yaml.SingleQuotedStyle|yaml.DoubleQuotedStyle|yaml.LiteralStyle|yaml.FoldedStyle) == 0 &&
		resolve(n.Value) == tagMerge
}

// keyOf is a key's identity as Java's equals of the constructed keys sees it.
func (c *yamlComposer) keyOf(n *yaml.Node) (string, any, error) {
	k, err := c.construct(n)
	if err != nil {
		return "", nil, err
	}
	return fmt.Sprintf("%T:%s", k, javaToString(k)), k, nil
}

func (c *yamlComposer) mapping(n *yaml.Node) (any, error) {
	if tag := explicitTag(n); tag != "" && tag != "!!map" {
		return nil, errYAML
	}
	resolveAlias := func(x *yaml.Node) *yaml.Node {
		for x != nil && x.Kind == yaml.AliasNode {
			x = x.Alias
		}
		return x
	}
	var tuples []tuple
	merged := false
	for i := 0; i+1 < len(n.Content); i += 2 {
		tuples = append(tuples, tuple{n.Content[i], n.Content[i+1]})
		merged = merged || isMergeKey(n.Content[i])
	}
	// processDuplicateKeys: of the same key, the last tuple stays, where it is
	seen := map[string]int{}
	drop := map[int]bool{}
	for i, t := range tuples {
		if isMergeKey(t.key) {
			continue
		}
		id, _, err := c.keyOf(t.key)
		if err != nil {
			return nil, err
		}
		if prev, ok := seen[id]; ok {
			drop[prev] = true
		}
		seen[id] = i
	}
	var kept []tuple
	for i, t := range tuples {
		if !drop[i] {
			kept = append(kept, t)
		}
	}
	tuples = kept
	if merged {
		// mergeNode: a merged mapping's keys where the << is, the node's own keys over them, the first merged one
		// over later ones
		var values []tuple
		index := map[string]int{}
		merging := map[*yaml.Node]bool{}
		var merge func(ts []tuple, preferred bool) error
		merge = func(ts []tuple, preferred bool) error {
			for _, t := range ts {
				if isMergeKey(t.key) {
					value := resolveAlias(t.value)
					if t.value.Kind == yaml.AliasNode && value != nil && value.Kind != yaml.ScalarNode {
						c.aliases++
						if c.aliases > maxAliasesForCollections {
							return errYAML
						}
					}
					var subs []*yaml.Node
					switch {
					case value == nil:
						return errYAML
					case value.Kind == yaml.MappingNode:
						subs = []*yaml.Node{value}
					case value.Kind == yaml.SequenceNode:
						for _, s := range value.Content {
							// an alias in the list counts as any other: SnakeYAML's parser counts every alias event
							if s.Kind == yaml.AliasNode {
								c.aliases++
								if c.aliases > maxAliasesForCollections {
									return errYAML
								}
							}
							s = resolveAlias(s)
							if s == nil || s.Kind != yaml.MappingNode {
								return errYAML
							}
							subs = append(subs, s)
						}
					default:
						return errYAML
					}
					for _, sub := range subs {
						// a mapping whose merge leads back to itself, or merges nested past the depth limit: Java
						// overflows its stack and the update fails, Go would take the daemon down
						if merging[sub] || c.depth > nestingDepthLimit {
							return errYAML
						}
						var inner []tuple
						for i := 0; i+1 < len(sub.Content); i += 2 {
							inner = append(inner, tuple{sub.Content[i], sub.Content[i+1]})
						}
						merging[sub] = true
						c.depth++
						err := merge(inner, false)
						c.depth--
						merging[sub] = false
						if err != nil {
							return err
						}
					}
					continue
				}
				id, _, err := c.keyOf(t.key)
				if err != nil {
					return err
				}
				if at, ok := index[id]; !ok {
					values = append(values, t)
					index[id] = len(values) - 1
				} else if preferred {
					values[at] = t
				}
			}
			return nil
		}
		if err := merge(tuples, true); err != nil {
			return nil, err
		}
		tuples = values
	}
	m := &yamlMap{}
	at := map[string]int{}
	for _, t := range tuples {
		id, k, err := c.keyOf(t.key)
		if err != nil {
			return nil, err
		}
		v, err := c.construct(t.value)
		if err != nil {
			return nil, err
		}
		if i, ok := at[id]; ok {
			m.values[i] = v
			continue
		}
		at[id] = len(m.keys)
		m.keys = append(m.keys, k)
		m.values = append(m.values, v)
	}
	return m, nil
}

func (c *yamlComposer) scalar(n *yaml.Node) (any, error) {
	tag := explicitTag(n)
	switch tag {
	case "":
		if n.Style&(yaml.SingleQuotedStyle|yaml.DoubleQuotedStyle|yaml.LiteralStyle|yaml.FoldedStyle) != 0 {
			return n.Value, nil
		}
		switch resolve(n.Value) {
		case tagBool:
			return constructBool(n.Value), nil
		case tagInt:
			return constructInt(n.Value)
		case tagFloat:
			return constructFloat(n.Value)
		case tagNull:
			return nil, nil
		case tagTimestamp:
			return constructTimestamp(n.Value)
		case tagMerge:
			return nil, errYAML // a merge key's value is no scalar of its own
		}
		return n.Value, nil
	case "!!str", "!str", "str", "tag:yaml.org,2002:str":
		return n.Value, nil
	case "!!bool":
		return constructBool(n.Value), nil
	case "!!int":
		return constructInt(n.Value)
	case "!!float":
		return constructFloat(n.Value)
	case "!!null":
		return nil, nil
	case "!!timestamp":
		return constructTimestamp(n.Value)
	case "!!binary":
		data, err := base64.StdEncoding.DecodeString(strings.Map(func(r rune) rune {
			if r == ' ' || r == '\n' || r == '\r' || r == '\t' {
				return -1
			}
			return r
		}, n.Value))
		if err != nil {
			return nil, errYAML
		}
		return data, nil
	}
	// a tag SafeConstructor has no constructor for (a global one among them: no class is made of a subscription)
	return nil, errYAML
}

// constructBool is ConstructYamlBool: BOOL_VALUES of the lowercased value, null for another.
func constructBool(value string) any {
	switch strings.ToLower(value) {
	case "yes", "true", "on":
		return true
	case "no", "false", "off":
		return false
	}
	return nil
}

// constructInt is ConstructYamlInt: '_' dropped, a sign, then 0b, 0x, a leading 0 octal, a:b sexagesimal (summed in
// an int, overflowing as Java's does), else decimal; Integer, Long or BigInteger by its size.
func constructInt(value string) (any, error) {
	value = strings.ReplaceAll(value, "_", "")
	if value == "" {
		return nil, errYAML // found empty value
	}
	sign := ""
	switch value[0] {
	case '-':
		sign, value = "-", value[1:]
	case '+':
		value = value[1:]
	}
	base := 10
	switch {
	case value == "0":
		return big.NewInt(0), nil
	case strings.HasPrefix(value, "0b"):
		value, base = value[2:], 2
	case strings.HasPrefix(value, "0x"):
		value, base = value[2:], 16
	case strings.HasPrefix(value, "0"):
		value, base = value[1:], 8
	case strings.Contains(value, ":"):
		digits := strings.Split(value, ":")
		bes, val := int32(1), int32(0)
		for i := len(digits) - 1; i >= 0; i-- {
			d, ok := nikofmt.JavaParseLong(digits[i], 10)
			if !ok {
				return nil, &fatalYAML{errors.New("not a number: " + value)}
			}
			val = int32(int64(val) + d*int64(bes))
			bes *= 60
		}
		return big.NewInt(int64(val) * signOf(sign)), nil
	}
	n, ok := new(big.Int).SetString(sign+value, base)
	if !ok {
		return nil, &fatalYAML{errors.New("not a number: " + value)}
	}
	return n, nil
}

func signOf(sign string) int64 {
	if sign == "-" {
		return -1
	}
	return 1
}

// constructFloat is ConstructYamlFloat: '_' dropped, ±.inf, .nan, a:b sexagesimal, else Double.valueOf.
func constructFloat(value string) (any, error) {
	value = strings.ReplaceAll(value, "_", "")
	if value == "" {
		return nil, errYAML // found empty value
	}
	lower := strings.ToLower(value)
	sign := 1.0
	if lower != "" && (lower[0] == '-' || lower[0] == '+') {
		if lower[0] == '-' {
			sign = -1
		}
		lower = lower[1:]
	}
	switch {
	case lower == ".inf":
		return math.Inf(int(sign)), nil
	case lower == ".nan":
		return math.NaN(), nil
	case strings.Contains(lower, ":"):
		digits := strings.Split(lower, ":")
		bes, val := 1, 0.0
		for i := len(digits) - 1; i >= 0; i-- {
			d, ok := nikofmt.JavaDoubleValueOf(digits[i])
			if !ok {
				return nil, &fatalYAML{errors.New("not a number: " + value)}
			}
			val += d * float64(bes)
			bes *= 60
		}
		return sign * val, nil
	}
	f, ok := nikofmt.JavaDoubleValueOf(value)
	if !ok {
		return nil, &fatalYAML{errors.New("not a number: " + value)}
	}
	return f, nil
}

// constructTimestamp is ConstructYamlTimestamp: a date at midnight UTC, or a time with its zone, UTC without one.
func constructTimestamp(value string) (any, error) {
	re := regexp.MustCompile(`^([0-9][0-9][0-9][0-9])-([0-9][0-9]?)-([0-9][0-9]?)(?:(?:[Tt]|[ \t]+)([0-9][0-9]?):([0-9][0-9]):([0-9][0-9])(?:\.([0-9]*))?(?:[ \t]*(Z|([-+][0-9][0-9]?)(?::([0-9][0-9]))?))?)?$`)
	m := re.FindStringSubmatch(value)
	if m == nil {
		return nil, &fatalYAML{errors.New("not a timestamp: " + value)}
	}
	atoi := func(s string) int {
		n, _ := strconv.Atoi(s)
		return n
	}
	t := time.Date(atoi(m[1]), time.Month(atoi(m[2])), atoi(m[3]), atoi(m[4]), atoi(m[5]), atoi(m[6]), 0, time.UTC)
	if m[7] != "" {
		fraction := (m[7] + "000")[:3]
		t = t.Add(time.Duration(atoi(fraction)) * time.Millisecond)
	}
	if m[9] != "" {
		offset := atoi(m[9])*3600 + signInt(m[9])*atoi(m[10])*60
		t = t.Add(-time.Duration(offset) * time.Second)
	}
	return t, nil
}

func signInt(s string) int {
	if strings.HasPrefix(s, "-") {
		return -1
	}
	return 1
}

// javaToString is the value's toString(), as Kotlin's toString() of it: a map as AbstractMap writes it ({a=1, b=2}),
// a list as AbstractCollection does ([1, 2]), null as "null".
func javaToString(v any) string {
	switch v := v.(type) {
	case nil:
		return "null"
	case string:
		return v
	case bool:
		return strconv.FormatBool(v)
	case *big.Int:
		return v.String()
	case int32:
		return strconv.FormatInt(int64(v), 10)
	case int64:
		return strconv.FormatInt(v, 10)
	case float64:
		return nikofmt.JavaDoubleString(v)
	case time.Time:
		// Date.toString in the process's zone, as the JVM's default one
		return v.Local().Format("Mon Jan 02 15:04:05 MST 2006")
	case []byte:
		return "[B@0"
	case []any:
		var b bytes.Buffer
		b.WriteByte('[')
		for i, e := range v {
			if i > 0 {
				b.WriteString(", ")
			}
			b.WriteString(javaToString(e))
		}
		b.WriteByte(']')
		return b.String()
	case *yamlMap:
		var b bytes.Buffer
		b.WriteByte('{')
		for i, k := range v.keys {
			if i > 0 {
				b.WriteString(", ")
			}
			b.WriteString(javaToString(k))
			b.WriteByte('=')
			b.WriteString(javaToString(v.values[i]))
		}
		b.WriteByte('}')
		return b.String()
	case *nikofmt.OrgObject:
		return orgString(v)
	case nikofmt.OrgNull:
		return "null"
	}
	return fmt.Sprint(v)
}

// Package ruleset is the rule-sets the app downloads and the core reads from a file (RuleSetFiles.kt): their check
// (Check, libcore.CheckRuleSet for the app), and for the desktop daemon their files under its data directory, their
// download and their update (files.go). What a rule names is nikoconf's (rulesets.go): the items, their URLs and
// file names.
package ruleset

import (
	"bytes"
	"compress/zlib"
	"context"
	"io"
	"os"

	"github.com/sagernet/sing-box/common/srs"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/route/rule"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json"
)

// What a rule-set holds, as Check reports it.
const (
	HasDomain int32 = 1 // domains (a DNS rule can use it)
	HasIP     int32 = 2 // IP CIDRs
)

const (
	// MaxSize is what a rule-set file may weigh at most (RuleSetFiles.MAX_SIZE)
	MaxSize = 32 << 20
	// maxInflated is what a binary one's rules may weigh unpacked: zlib makes a small file of a huge stream, which
	// the core would read whole into memory at each start
	maxInflated = 256 << 20
)

// Check reads a rule-set file as the core loads a local one ("binary" .srs, "source" JSON) and builds its rules,
// which kinds of items it holds (HasDomain, HasIP), or why the core would not load it: a download is checked before
// it replaces a file, since a file the core can not read fails every start (a newer format of the list, say). A file
// over MaxSize, or a binary one whose rules unpack to more than maxInflated, is refused unread.
func Check(path string, format string) (int32, error) {
	info, err := os.Stat(path)
	if err != nil {
		return 0, err
	}
	if info.Size() > MaxSize {
		return 0, E.New("a rule-set of ", info.Size(), " bytes")
	}
	var ruleSet option.PlainRuleSetCompat
	switch format {
	case C.RuleSetFormatSource:
		content, err := os.ReadFile(path)
		if err != nil {
			return 0, err
		}
		if ruleSet, err = json.UnmarshalExtended[option.PlainRuleSetCompat](content); err != nil {
			return 0, err
		}
	case C.RuleSetFormatBinary:
		content, err := os.ReadFile(path)
		if err != nil {
			return 0, err
		}
		if err = boundedInflate(content); err != nil {
			return 0, err
		}
		if ruleSet, err = srs.Read(bytes.NewReader(content), false); err != nil {
			return 0, err
		}
	default:
		return 0, E.New("unknown rule-set format: ", format)
	}
	plain, err := ruleSet.Upgrade()
	if err != nil {
		return 0, err
	}
	if len(plain.Rules) == 0 {
		return 0, E.New("empty rule-set")
	}
	var kinds int32
	for i, options := range plain.Rules {
		if _, err = rule.NewHeadlessRule(context.Background(), options); err != nil {
			return 0, E.Cause(err, "rules[", i, "]")
		}
		kinds |= headlessRuleKinds(options, 0)
	}
	return kinds, nil
}

// boundedInflate: a binary rule-set's stream (after its magic and version) unpacks to maxInflated at most. What it
// is otherwise is srs.Read's to say.
func boundedInflate(content []byte) error {
	if len(content) < 4 {
		return nil
	}
	inflater, err := zlib.NewReader(bytes.NewReader(content[4:]))
	if err != nil {
		return nil
	}
	defer inflater.Close()
	n, _ := io.Copy(io.Discard, io.LimitReader(inflater, maxInflated+1))
	if n > maxInflated {
		return E.New("a rule-set unpacking to over ", maxInflated>>20, " MiB")
	}
	return nil
}

// headlessRuleKinds: what a rule holds; a logical rule whatever its parts hold, as deep as the core lets them nest.
func headlessRuleKinds(options option.HeadlessRule, depth int) int32 {
	if options.Type != C.RuleTypeDefault && options.Type != "" {
		var kinds int32
		if depth > 64 {
			return kinds
		}
		for _, part := range options.LogicalOptions.Rules {
			kinds |= headlessRuleKinds(part, depth+1)
		}
		return kinds
	}
	rule := options.DefaultOptions
	var kinds int32
	if rule.DomainMatcher != nil || rule.AdGuardDomainMatcher != nil || len(rule.Domain) > 0 || len(rule.DomainSuffix) > 0 ||
		len(rule.DomainKeyword) > 0 || len(rule.DomainRegex) > 0 || len(rule.AdGuardDomain) > 0 {
		kinds |= HasDomain
	}
	if rule.IPSet != nil || len(rule.IPCIDR) > 0 {
		kinds |= HasIP
	}
	return kinds
}

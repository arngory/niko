package libcore

import (
	"context"
	"os"

	"github.com/sagernet/sing-box/common/srs"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing-box/route/rule"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json"
)

// What a rule-set holds, as CheckRuleSet reports it.
const (
	RuleSetHasDomain int32 = 1 // domains (a DNS rule can use it)
	RuleSetHasIP     int32 = 2 // IP CIDRs
)

// CheckRuleSet reads a rule-set file as the core loads a local one ("binary" .srs, "source" JSON) and builds its
// rules, which kinds of items it holds (RuleSetHasDomain, RuleSetHasIP), or why the core would not load it: the app
// checks a download before it replaces a file, since a file the core can not read fails every start (a newer format
// of the list, say).
func CheckRuleSet(path string, format string) (int32, error) {
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
		file, err := os.Open(path)
		if err != nil {
			return 0, err
		}
		defer file.Close()
		if ruleSet, err = srs.Read(file, false); err != nil {
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
		kinds |= headlessRuleKinds(options)
	}
	return kinds, nil
}

func headlessRuleKinds(options option.HeadlessRule) int32 {
	if options.Type != C.RuleTypeDefault && options.Type != "" {
		// a logical rule: whatever its parts hold
		var kinds int32
		for _, part := range options.LogicalOptions.Rules {
			kinds |= headlessRuleKinds(part)
		}
		return kinds
	}
	rule := options.DefaultOptions
	var kinds int32
	if rule.DomainMatcher != nil || rule.AdGuardDomainMatcher != nil || len(rule.Domain) > 0 || len(rule.DomainSuffix) > 0 ||
		len(rule.DomainKeyword) > 0 || len(rule.DomainRegex) > 0 || len(rule.AdGuardDomain) > 0 {
		kinds |= RuleSetHasDomain
	}
	if rule.IPSet != nil || len(rule.IPCIDR) > 0 {
		kinds |= RuleSetHasIP
	}
	return kinds
}

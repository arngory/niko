package libcore

import "libcore/ruleset"

// What a rule-set holds, as CheckRuleSet reports it.
const (
	RuleSetHasDomain int32 = ruleset.HasDomain // domains (a DNS rule can use it)
	RuleSetHasIP     int32 = ruleset.HasIP     // IP CIDRs
)

// CheckRuleSet reads a rule-set file as the core loads a local one ("binary" .srs, "source" JSON) and builds its
// rules, which kinds of items it holds (RuleSetHasDomain, RuleSetHasIP), or why the core would not load it: the app
// checks a download before it replaces a file, since a file the core can not read fails every start (a newer format
// of the list, say). The check is libcore/ruleset's, which the desktop daemon's downloads take too.
func CheckRuleSet(path string, format string) (int32, error) {
	return ruleset.Check(path, format)
}

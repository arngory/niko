package nikoconf

import (
	"crypto/sha256"
	"encoding/hex"
	"strings"
)

// The rule-sets the app downloads (RuleSetFiles.kt): "srs:https://…/geosite-x.srs" in a rule's lists. The build
// takes those that are here as local rule-sets, by their files, and leaves the others out (a rule they leave without
// any address is left out whole); a DNS rule takes one by what it holds. The files are the daemon's (libcore/ruleset
// downloads and checks them); the build only asks what is there.

// What a rule-set holds, as libcore's CheckRuleSet says (RuleSetFiles.HAS_DOMAIN, HAS_IP).
const (
	RuleSetHasDomain = 1
	RuleSetHasIP     = 2
)

// RuleSets are the downloaded rule-sets a build sees (RuleSetFiles' directory).
type RuleSets interface {
	// Path is the file of an item that is downloaded (not empty), "" where none is
	Path(item string) string
	// Kinds is what the item's file holds: what its check at the download found, or, with check, what checking it
	// now finds for a file without one; ok false where it cannot be told. Without check the file is not read (an
	// export, as RuleSetFiles.kinds(check = false))
	Kinds(item string, check bool) (kinds int, ok bool)
}

// GoldenRuleSetDir is where the golden fixtures' rule-sets are: ConfigGoldenTest writes the app's paths with it.
const GoldenRuleSetDir = "/golden/rule-sets"

// FixedRuleSets are the golden test's: the items of Files downloaded under Dir, each with what it holds.
type FixedRuleSets struct {
	Dir   string
	Files map[string]int
}

func (f FixedRuleSets) Path(item string) string {
	if _, ok := f.Files[item]; !ok {
		return ""
	}
	// a fixture's path is the same on every system
	return strings.TrimSuffix(f.Dir, "/") + "/" + RuleSetFileName(item)
}

func (f FixedRuleSets) Kinds(item string, _ bool) (int, bool) {
	kinds, ok := f.Files[item]
	return kinds, ok
}

// IsRuleSetItem: an item of a rule's lists that names a rule-set the app downloads.
func IsRuleSetItem(item string) bool { return isRuleSetFile(item) }

// RuleSetURL is the address of an item's rule-set (RuleSetFiles.url).
func RuleSetURL(item string) string { return ruleSetURL(item) }

// RuleSetFetchable: only https is fetched (RuleSetFiles.fetchable).
func RuleSetFetchable(item string) bool { return ruleSetFetchable(item) }

// RuleSetFormat is the item's format: "source" for JSON, "binary" (.srs) otherwise (RuleSetFiles.format).
func RuleSetFormat(item string) string { return ruleSetFormat(ruleSetURL(item)) }

// RuleSetFileName is the name of an item's file (RuleSetFiles.file): its URL's SHA-256, the first 16 bytes in hex,
// so the same URL keeps its file, and the format's extension.
func RuleSetFileName(item string) string {
	digest := sha256.Sum256([]byte(ruleSetURL(item)))
	name := hex.EncodeToString(digest[:16])
	if RuleSetFormat(item) == "source" {
		return name + ".json"
	}
	return name + ".srs"
}

// RuleSetItems are the downloadable items of rules' lists (RuleSetFiles.itemsOf): those of each rule's domains and
// IPs, each once, in their order.
func RuleSetItems(rules []Rule) []string {
	var lists []string
	for _, r := range rules {
		lists = append(lists, r.Domains, r.IP)
	}
	var items []string
	for _, list := range lists {
		for _, item := range listOf(list) {
			if isRuleSetFile(item) {
				items = append(items, item)
			}
		}
	}
	return distinctStrings(items)
}

// noRuleSets: none is downloaded.
type noRuleSets struct{}

func (noRuleSets) Path(string) string             { return "" }
func (noRuleSets) Kinds(string, bool) (int, bool) { return 0, false }

// ruleSetAvailable is RuleSetFiles.available: fetchable, and its file here.
func (b *builder) ruleSetAvailable(item string) bool {
	return ruleSetFetchable(item) && b.ruleSetFiles.Path(item) != ""
}

// usableInDNS is RuleSetFiles.usableInDns: whether a DNS rule can use the rule-set, one with domains (an IP-only one
// would take every name). By what the file holds, by its name when that is not known (an export of one not
// downloaded here).
func (b *builder) usableInDNS(item string) bool {
	if !ruleSetFetchable(item) || !b.forExport && !b.ruleSetAvailable(item) {
		return false
	}
	kinds, ok := b.ruleSetFiles.Kinds(item, !b.forExport)
	if !ok {
		return !isIPRuleSetName(item)
	}
	return kinds&RuleSetHasDomain != 0
}

// isGeoItem: a list of the app's geo assets (geosite.db, geoip.db), which a computer has not; geoip:private is the
// core's own.
func isGeoItem(item string) bool {
	return strings.HasPrefix(item, "geosite:") || strings.HasPrefix(item, "geoip:") && item != "geoip:private"
}

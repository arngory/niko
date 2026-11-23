package nikostore

import (
	"encoding/json"
	"fmt"
	"sort"
	"strings"
	"unicode/utf8"

	"libcore/nikoconf"
	"libcore/nikofmt"
)

// The route rules (RuleEntity, D-RULES-01), schema 3: "rules" of store.json, each with the app's field names (as
// nikoconf.Rule reads them), "next_rule" the id to give next. They are in the order the build takes them, by their
// userOrder (then id); a new one goes at the end (ProfileManager.createRule: the largest order and one).

const (
	// maxRules is how many rules a store keeps
	maxRules = 4096
	// maxRuleText is what one of a rule's lists, or its own JSON, may be: a pasted list of domains is the longest
	maxRuleText = 1 << 20
	// maxRulePrograms is how many programs (or apps) one rule may name
	maxRulePrograms = 1024
)

// decodeRules reads store.json's rules: each id once, the next id past them.
func decodeRules(rules []nikoconf.Rule, next int64) ([]nikoconf.Rule, int64, error) {
	seen := map[int64]bool{}
	out := make([]nikoconf.Rule, 0, len(rules))
	for _, r := range rules {
		if r.ID <= 0 || seen[r.ID] {
			return nil, 0, fmt.Errorf("rule %d: not a rule's id, or twice", r.ID)
		}
		seen[r.ID] = true
		next = max(next, r.ID+1)
		out = append(out, r)
	}
	sortRules(out)
	return out, max(next, 1), nil
}

func sortRules(rules []nikoconf.Rule) {
	sort.SliceStable(rules, func(i, j int) bool {
		if rules[i].UserOrder != rules[j].UserOrder {
			return rules[i].UserOrder < rules[j].UserOrder
		}
		return rules[i].ID < rules[j].ID
	})
}

func copyRule(r nikoconf.Rule) nikoconf.Rule {
	r.Packages = append([]string(nil), r.Packages...)
	r.Programs = append([]string(nil), r.Programs...)
	return r
}

// Rules are the route rules in their order.
func (s *Store) Rules() []nikoconf.Rule {
	s.mu.Lock()
	defer s.mu.Unlock()
	out := make([]nikoconf.Rule, 0, len(s.state.rules))
	for _, r := range s.state.rules {
		out = append(out, copyRule(r))
	}
	return out
}

// checkRule: what a rule may be, as the app's editor makes them. The outbound is the proxy (0), bypass (-1), block
// (-2) or a profile of the store (or the one it had, a profile deleted since: the build tells of it); the network
// none, tcp or udp; the rule's own JSON none or an object, which the build
// merges over the rule. The lists are text as the user wrote them, each bounded.
func (st *state) checkRule(r *nikoconf.Rule, had int64) error {
	if len(r.Name) > maxName {
		return fmt.Errorf("%w: a name over %d bytes", ErrInvalid, maxName)
	}
	for _, text := range []string{r.Name, r.Config, r.Domains, r.IP, r.Port, r.SourcePort, r.Network, r.Source,
		r.Protocol} {
		if len(text) > maxRuleText {
			return fmt.Errorf("%w: a rule's list over %d bytes", ErrInvalid, maxRuleText)
		}
		if !utf8.ValidString(text) {
			return fmt.Errorf("%w: a rule's text that is not UTF-8", ErrInvalid)
		}
	}
	switch r.Network {
	case "", "tcp", "udp":
	default:
		return fmt.Errorf("%w: the network %q is none of tcp, udp", ErrInvalid, r.Network)
	}
	switch {
	case r.Outbound >= -2 && r.Outbound <= 0:
	case r.Outbound > 0 && (st.profiles[r.Outbound] != nil || r.Outbound == had):
	default:
		return fmt.Errorf("%w: the outbound %d is no profile", ErrInvalid, r.Outbound)
	}
	if strings.TrimSpace(r.Config) != "" {
		var object map[string]json.RawMessage
		if err := json.Unmarshal([]byte(r.Config), &object); err != nil || object == nil {
			return fmt.Errorf("%w: the rule's JSON is not an object", ErrInvalid)
		}
		// as the build reads it (nikoconf's mergeJSON, the app's lenient Gson), which refuses what encoding/json takes,
		// a key twice among them: saved, it would fail every Connect. encoding/json first, for its depth bound
		if m, err := nikofmt.GsonMap(r.Config); err != nil || m == nil {
			return fmt.Errorf("%w: the rule's JSON is not one the build reads", ErrInvalid)
		}
	}
	for _, list := range [][]string{r.Programs, r.Packages} {
		if len(list) > maxRulePrograms {
			return fmt.Errorf("%w: over %d programs in a rule", ErrInvalid, maxRulePrograms)
		}
		for _, p := range list {
			if len(p) > maxProgram || strings.ContainsAny(p, "\r\n") || !utf8.ValidString(p) {
				return fmt.Errorf("%w: a program's name of more than a line", ErrInvalid)
			}
		}
	}
	return nil
}

// SaveRule saves a rule: a new one (ID 0) at the end of the rules, or the one of that ID with these fields where it
// is. A rule's apps (Packages, the phone's) are not the window's to give: a new rule has none, a changed one keeps
// its own. The rule as saved.
func (s *Store) SaveRule(r nikoconf.Rule) (nikoconf.Rule, error) {
	r = copyRule(r)
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	had := int64(0)
	if i := st.rule(r.ID); r.ID != 0 && i >= 0 {
		had = st.rules[i].Outbound
	}
	if err := st.checkRule(&r, had); err != nil {
		return nikoconf.Rule{}, err
	}
	if r.ID == 0 {
		if len(st.rules) >= maxRules {
			return nikoconf.Rule{}, fmt.Errorf("%w: over %d rules", ErrRefused, maxRules)
		}
		r.ID, r.UserOrder, r.Packages = st.nextRule, st.nextRuleOrder(), nil
		st.nextRule++
		st.rules = append(st.rules, r)
	} else {
		i := st.rule(r.ID)
		if i < 0 {
			return nikoconf.Rule{}, ErrNotFound
		}
		r.UserOrder, r.Packages = st.rules[i].UserOrder, st.rules[i].Packages
		st.rules[i] = r
	}
	if err := s.commit(st, Change{Kind: KindRules, IDs: []int64{r.ID}}); err != nil {
		return nikoconf.Rule{}, err
	}
	return copyRule(r), nil
}

func (st *state) rule(id int64) int {
	for i := range st.rules {
		if st.rules[i].ID == id {
			return i
		}
	}
	return -1
}

// nextRuleOrder is RuleEntity.Dao.nextOrder: the largest order and one, 1 for the first.
func (st *state) nextRuleOrder() int64 {
	var last int64
	for _, r := range st.rules {
		last = max(last, r.UserOrder)
	}
	return last + 1
}

// DeleteRules deletes the rules of those ids that are there. The ids deleted.
func (s *Store) DeleteRules(ids []int64) ([]int64, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	gone := map[int64]bool{}
	for _, id := range ids {
		gone[id] = true
	}
	var kept []nikoconf.Rule
	var deleted []int64
	for _, r := range st.rules {
		if gone[r.ID] {
			deleted = append(deleted, r.ID)
		} else {
			kept = append(kept, r)
		}
	}
	if len(deleted) == 0 {
		return nil, nil
	}
	st.rules = kept
	return deleted, s.commit(st, Change{Kind: KindRules, IDs: deleted})
}

// MoveRules puts rules, in the order given, before another rule (0: at the end); the rules are then numbered again
// from 1, in their new order.
func (s *Store) MoveRules(ids []int64, before int64) error {
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	if len(ids) == 0 {
		return fmt.Errorf("%w: no rule to move", ErrInvalid)
	}
	// each rule once at most: no more ids than the store keeps, looked up in one map (not a scan per id under the lock)
	if len(ids) > maxRules {
		return fmt.Errorf("%w: over %d rules to move", ErrInvalid, maxRules)
	}
	index := make(map[int64]int, len(st.rules))
	for i, r := range st.rules {
		index[r.ID] = i
	}
	moving := map[int64]bool{}
	var moved []nikoconf.Rule
	for _, id := range ids {
		i, ok := index[id]
		if !ok {
			return ErrNotFound
		}
		if !moving[id] {
			moving[id] = true
			moved = append(moved, st.rules[i])
		}
	}
	if _, ok := index[before]; before != 0 && (!ok || moving[before]) {
		return fmt.Errorf("%w: rule %d is not one to put the others before", ErrInvalid, before)
	}
	var placed []nikoconf.Rule
	for _, r := range st.rules {
		if moving[r.ID] {
			continue
		}
		if r.ID == before {
			placed = append(placed, moved...)
		}
		placed = append(placed, r)
	}
	if before == 0 {
		placed = append(placed, moved...)
	}
	var changed []int64
	for n := range placed {
		if order := int64(n + 1); placed[n].UserOrder != order {
			placed[n].UserOrder = order
			changed = append(changed, placed[n].ID)
		}
	}
	if len(changed) == 0 {
		return nil
	}
	st.rules = placed
	sort.Slice(changed, func(i, j int) bool { return changed[i] < changed[j] })
	return s.commit(st, Change{Kind: KindRules, IDs: changed})
}

// ApplyRussiaPreset is RoutePresets.addRussia: the Russian preset's rules (nikoconf.RussiaPreset) replace its rules
// there are, at the end, in one write. The rules added.
func (s *Store) ApplyRussiaPreset(blockedName, directName string) ([]nikoconf.Rule, error) {
	if len(blockedName) > maxName || len(directName) > maxName {
		return nil, fmt.Errorf("%w: a name over %d bytes", ErrInvalid, maxName)
	}
	s.mu.Lock()
	defer s.mu.Unlock()
	st := s.state.clone()
	var kept []nikoconf.Rule
	var changed []int64
	for _, r := range st.rules {
		if nikoconf.IsRussia(&r) {
			changed = append(changed, r.ID)
		} else {
			kept = append(kept, r)
		}
	}
	st.rules = kept
	preset := nikoconf.RussiaPreset(blockedName, directName)
	if len(st.rules)+len(preset) > maxRules {
		return nil, fmt.Errorf("%w: over %d rules", ErrRefused, maxRules)
	}
	for i := range preset {
		preset[i].ID, preset[i].UserOrder = st.nextRule, st.nextRuleOrder()
		st.nextRule++
		st.rules = append(st.rules, preset[i])
		changed = append(changed, preset[i].ID)
	}
	if err := s.commit(st, Change{Kind: KindRules, IDs: changed}); err != nil {
		return nil, err
	}
	return preset, nil
}

// RuleSetItems are the rule-sets the rules name that the daemon downloads (nikoconf.RuleSetItems): of the enabled
// rules alone, or of all (a rule turned off keeps its files).
func (s *Store) RuleSetItems(enabledOnly bool) []string {
	var rules []nikoconf.Rule
	for _, r := range s.Rules() {
		if r.Enabled || !enabledOnly {
			rules = append(rules, r)
		}
	}
	return nikoconf.RuleSetItems(rules)
}

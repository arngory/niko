package nikoservice

import (
	"context"
	"errors"
	"net/url"
	"path"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"sync"
	"time"

	"libcore/nikoconf"
	"libcore/nikopb"
	"libcore/nikostore"
	"libcore/ruleset"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

// The route rules (slice 10, D-RULES-01, -05, BG-09): kept in the store (nikostore's rules.go), built into Connect's
// config as the app builds them; the rule-sets they name ("srs:") downloaded by the daemon itself into rulesets/
// beside store.json (libcore/ruleset), on demand (UpdateRuleSets) and, while the core runs, as the app's
// RuleSetFiles.updateSoon does: those missing or older than the interval, every interval. The daemon's requests go
// directly (a bypass of the TUN comes with the nodes' Control, slice 11).

const (
	// ruleSetMargin: a list is stale by the next round, less this, as the app's (what a round fetched is older than
	// the interval by the next one)
	ruleSetMargin = 10 * time.Minute
	// ruleSetRetry: a list whose download failed is tried again on its own after this
	ruleSetRetry = 15 * time.Minute
	// maxRuleSetRequest is how many lists one request may name
	maxRuleSetRequest = 1024
	// maxErrorText is what an error shown to the user may be
	maxErrorText = 512
)

// ruleSetUpdates are a user's rule-set downloads: one update at a time, the lists it fetches, each list's last error
// and when the timed round last tried it.
type ruleSetUpdates struct {
	mu       sync.Mutex
	running  bool
	fetching map[string]bool
	errors   map[string]string
	tried    map[string]time.Time
}

func newRuleSetUpdates() *ruleSetUpdates {
	return &ruleSetUpdates{fetching: map[string]bool{}, errors: map[string]string{}, tried: map[string]time.Time{}}
}

// ruleSetsDir is where a store's rule-sets are: rulesets/ beside store.json (desktop-stage3.md §4).
func ruleSetsDir(store *nikostore.Store) string { return filepath.Join(store.Dir(), "rulesets") }

func ruleSetFiles(u *user) ruleset.Files { return ruleset.Files{Dir: ruleSetsDir(u.store)} }

func ruleProto(r nikoconf.Rule) *nikopb.Rule {
	return &nikopb.Rule{Id: r.ID, Name: r.Name, Order: r.UserOrder, Enabled: r.Enabled, Domains: r.Domains, Ip: r.IP,
		Port: r.Port, SourcePort: r.SourcePort, Network: r.Network, Source: r.Source, Protocol: r.Protocol,
		Outbound: r.Outbound, Programs: append([]string(nil), r.Programs...), DpiBypass: r.DpiBypass,
		Config: r.Config, Packages: int32(len(r.Packages))}
}

func ruleFrom(p *nikopb.Rule) nikoconf.Rule {
	return nikoconf.Rule{ID: p.Id, Name: p.Name, Enabled: p.Enabled, Domains: p.Domains, IP: p.Ip, Port: p.Port,
		SourcePort: p.SourcePort, Network: p.Network, Source: p.Source, Protocol: p.Protocol, Outbound: p.Outbound,
		Programs: append([]string(nil), p.Programs...), DpiBypass: p.DpiBypass, Config: p.Config}
}

func (s *server) ListRules(ctx context.Context, _ *nikopb.Empty) (*nikopb.RuleList, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	list := &nikopb.RuleList{}
	for _, r := range u.store.Rules() {
		list.Rules = append(list.Rules, ruleProto(r))
	}
	return list, nil
}

func (s *server) SaveRule(ctx context.Context, request *nikopb.Rule) (*nikopb.Rule, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	saved, err := u.store.SaveRule(ruleFrom(request))
	if err != nil {
		return nil, storeError(err)
	}
	return ruleProto(saved), nil
}

func (s *server) DeleteRules(ctx context.Context, request *nikopb.RuleIds) (*nikopb.Empty, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	if _, err = u.store.DeleteRules(request.Ids); err != nil {
		return nil, storeError(err)
	}
	return &nikopb.Empty{}, nil
}

func (s *server) MoveRules(ctx context.Context, request *nikopb.MoveRulesRequest) (*nikopb.Empty, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	if err = u.store.MoveRules(request.Ids, request.Before); err != nil {
		return nil, storeError(err)
	}
	return &nikopb.Empty{}, nil
}

// The app's English names of the Russian preset's rules (route_preset_ru_blocked, route_preset_ru_direct).
const (
	russiaBlockedName = "Blocked in Russia: through the proxy"
	russiaDirectName  = "Russian services: directly"
)

func (s *server) ApplyPreset(ctx context.Context, request *nikopb.PresetRequest) (*nikopb.PresetResult, error) {
	if request.Preset != nikopb.PresetRequest_RUSSIA {
		return nil, status.Error(codes.InvalidArgument, "no such preset")
	}
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	blocked, direct := request.BlockedName, request.DirectName
	if strings.TrimSpace(blocked) == "" {
		blocked = russiaBlockedName
	}
	if strings.TrimSpace(direct) == "" {
		direct = russiaDirectName
	}
	added, err := u.store.ApplyRussiaPreset(blocked, direct)
	if err != nil {
		return nil, storeError(err)
	}
	result := &nikopb.PresetResult{RuleSets: nikoconf.RuleSetItems(added)}
	for _, r := range added {
		result.Rules = append(result.Rules, ruleProto(r))
	}
	// the preset's lists change what ListRuleSets shows
	u.feed.publish(&nikopb.Change{Kind: nikopb.Change_RULE_SETS})
	return result, nil
}

// updateHours is the rule-sets' update interval of the user's settings, the app's 24 h where they cannot be read.
func updateHours(u *user) int {
	hours := nikoconf.DefaultSettings().RuleSetUpdateHours
	if settings, err := u.store.Settings(); err == nil {
		hours = settings.RuleSetUpdateHours
	}
	return max(1, min(hours, 24*30))
}

func (s *server) ListRuleSets(ctx context.Context, _ *nikopb.Empty) (*nikopb.RuleSetList, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	files := ruleSetFiles(u)
	enabled := map[string]bool{}
	for _, item := range u.store.RuleSetItems(true) {
		enabled[item] = true
	}
	list := &nikopb.RuleSetList{UpdateHours: int32(updateHours(u))}
	u.ruleSets.mu.Lock()
	defer u.ruleSets.mu.Unlock()
	for _, item := range u.store.RuleSetItems(false) {
		info := files.Stat(item)
		set := &nikopb.RuleSet{Item: item, Url: nikoconf.RuleSetURL(item), Fetchable: nikoconf.RuleSetFetchable(item),
			Enabled: enabled[item], Downloaded: info.Size > 0, Size: info.Size, Error: u.ruleSets.errors[item],
			Updating: u.ruleSets.fetching[item]}
		if info.Size > 0 {
			set.Updated = info.Modified.Unix()
		}
		if info.KindsKnown {
			set.Kinds = int32(info.Kinds)
		}
		list.RuleSets = append(list.RuleSets, set)
	}
	return list, nil
}

// ruleSetError is a download's error as its result's code, its text valid UTF-8 and short (a server's answer may be
// neither); no error names a list's address.
func ruleSetError(err error) error {
	text := errorText(err.Error())
	var fetch *ruleset.FetchError
	switch {
	case errors.As(err, &fetch):
		return status.Error(codes.Unavailable, text)
	case errors.Is(err, ruleset.ErrNotHTTPS), errors.Is(err, ruleset.ErrNotRuleSet):
		return status.Error(codes.InvalidArgument, text)
	case errors.Is(err, context.Canceled):
		return status.Error(codes.Canceled, "the daemon stops")
	}
	return status.Error(codes.Internal, text)
}

// listName is a list as a line names it: its host and file, not its query (an address may hold a token).
func listName(item string) string {
	u, err := url.Parse(nikoconf.RuleSetURL(item))
	if err != nil {
		return "a list"
	}
	return errorText(u.Host + path.Clean("/"+u.Path))
}

// errorText is an error's text for the user: valid UTF-8, cut at a rune's start.
func errorText(text string) string {
	text = strings.ToValidUTF8(text, "�")
	if len(text) <= maxErrorText {
		return text
	}
	cut := maxErrorText
	for cut > 0 && !isRuneStart(text[cut]) {
		cut--
	}
	return text[:cut] + "…"
}

func isRuneStart(b byte) bool { return b&0xC0 != 0x80 }

func (s *server) UpdateRuleSets(request *nikopb.RuleSetRequest, stream grpc.ServerStreamingServer[nikopb.TaskProgress]) error {
	u, err := s.users.user(stream.Context())
	if err != nil {
		return err
	}
	if len(request.Items) > maxRuleSetRequest {
		return status.Errorf(codes.InvalidArgument, "over %d rule-sets in a request", maxRuleSetRequest)
	}
	items := request.Items
	if len(items) == 0 {
		items = u.store.RuleSetItems(true)
	} else {
		// only lists the rules name: the daemon fetches no address the store does not hold
		named := map[string]bool{}
		for _, item := range u.store.RuleSetItems(false) {
			named[item] = true
		}
		for _, item := range items {
			if !named[item] {
				return status.Error(codes.InvalidArgument, "a rule-set no rule names")
			}
		}
	}
	maxAge := time.Duration(updateHours(u))*time.Hour - ruleSetMargin
	if request.Force {
		maxAge = 0
	}
	if !u.ruleSets.start() {
		return status.Error(codes.Aborted, "an update of the rule-sets runs already")
	}
	task := "rule-sets-" + strconv.FormatUint(s.users.tasks.Add(1), 10)
	return runTask(stream, func(progress func(*nikopb.TaskProgress)) *nikopb.TaskProgress {
		return s.ruleSetTask(u, task, items, maxAge, progress)
	})
}

// start marks an update running; false where one runs already.
func (r *ruleSetUpdates) start() bool {
	r.mu.Lock()
	defer r.mu.Unlock()
	if r.running {
		return false
	}
	r.running = true
	return true
}

// ruleSetTask downloads lists (ruleset.Files.Update, one at a time) to the end, the update marked running by its
// caller, and tells its result to the user's watchers; each list's start goes to progress, which may be nil.
func (s *server) ruleSetTask(u *user, task string, items []string, maxAge time.Duration, progress func(*nikopb.TaskProgress)) *nikopb.TaskProgress {
	defer func() {
		u.ruleSets.mu.Lock()
		u.ruleSets.running = false
		u.ruleSets.mu.Unlock()
		u.feed.publish(&nikopb.Change{Kind: nikopb.Change_RULE_SETS})
	}()
	result := &nikopb.TaskProgress{TaskId: task, Done: true}
	files := ruleSetFiles(u)
	client := &ruleset.Client{Transport: s.transport, UserAgent: "Niko/" + Version}
	got, failed := 0, 0
	now := time.Now
	if s.now != nil {
		now = s.now
	}
	for _, item := range items {
		ctx := s.users.context()
		if ctx.Err() != nil {
			break
		}
		u.ruleSets.mu.Lock()
		u.ruleSets.fetching[item] = true
		u.ruleSets.mu.Unlock()
		u.feed.publish(&nikopb.Change{Kind: nikopb.Change_RULE_SETS})
		if progress != nil {
			progress(&nikopb.TaskProgress{TaskId: task, Line: "fetching " + listName(item)})
		}
		results := files.Update(ctx, client, []string{item}, maxAge, now())
		err, tried := results[item]
		u.ruleSets.mu.Lock()
		delete(u.ruleSets.fetching, item)
		switch {
		case !tried:
			// fresh enough
		case err != nil:
			st := status.Convert(ruleSetError(err))
			u.ruleSets.errors[item] = st.Message()
			if result.Code == 0 {
				result.Code, result.Error = int32(st.Code()), st.Message()
			}
			failed++
		default:
			delete(u.ruleSets.errors, item)
			got++
		}
		u.ruleSets.mu.Unlock()
	}
	files.RemoveUnused(u.store.RuleSetItems(false), now())
	result.Line = "rule-sets: " + strconv.Itoa(got) + " fetched"
	if failed > 0 {
		result.Line += ", " + strconv.Itoa(failed) + " failed"
	}
	u.feed.publish(&nikopb.Change{Kind: nikopb.Change_TASK, Task: proto.Clone(result).(*nikopb.TaskProgress)})
	return result
}

// dueRuleSets are the enabled rules' lists missing or stale by the user's interval, but those the timed round tried
// within ruleSetRetry; each marked tried now.
func (s *server) dueRuleSets(u *user, now time.Time) []string {
	files := ruleSetFiles(u)
	maxAge := time.Duration(updateHours(u))*time.Hour - ruleSetMargin
	var due []string
	u.ruleSets.mu.Lock()
	defer u.ruleSets.mu.Unlock()
	for _, item := range u.store.RuleSetItems(true) {
		if !nikoconf.RuleSetFetchable(item) {
			continue
		}
		if info := files.Stat(item); info.Size > 0 && now.Sub(info.Modified) < maxAge {
			continue
		}
		if tried, ok := u.ruleSets.tried[item]; ok && now.Sub(tried) < ruleSetRetry {
			continue
		}
		u.ruleSets.tried[item] = now
		due = append(due, item)
	}
	sort.Strings(due)
	return due
}

// updateRuleSetsSoon fetches the user's due lists in a task of its own, unless an update runs already.
func (s *server) updateRuleSetsSoon(u *user, now time.Time) {
	// marked running before the due lists are marked tried: a list is not held back by ruleSetRetry for an update
	// that never ran (one the window started is on)
	if !u.ruleSets.start() {
		return
	}
	due := s.dueRuleSets(u, now)
	if len(due) == 0 {
		u.ruleSets.mu.Lock()
		u.ruleSets.running = false
		u.ruleSets.mu.Unlock()
		return
	}
	task := "rule-sets-" + strconv.FormatUint(s.users.tasks.Add(1), 10)
	go s.ruleSetTask(u, task, due, time.Duration(updateHours(u))*time.Hour-ruleSetMargin, nil)
}

// autoRuleSets is the timed update of the open stores' rule-sets while the core runs (RuleSetFiles.updateSoon).
func (s *server) autoRuleSets(now time.Time) {
	if !s.connected() {
		return
	}
	for _, u := range s.users.opened() {
		s.updateRuleSetsSoon(u, now)
	}
}

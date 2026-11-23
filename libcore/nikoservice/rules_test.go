package nikoservice

import (
	"bytes"
	"context"
	"errors"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"testing"
	"time"

	"libcore/nikoconf"
	"libcore/nikopb"

	"github.com/sagernet/sing-box/common/srs"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/boxdd"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json/badoption"

	"google.golang.org/grpc/codes"
)

// lists is a server of rule-sets on a local HTTPS server: the answer of each path, which the test changes.
type lists struct {
	mu      sync.Mutex
	answers map[string][]byte
	hits    map[string]int
}

func (l *lists) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	l.mu.Lock()
	body, ok := l.answers[r.URL.Path]
	l.hits[r.URL.Path]++
	l.mu.Unlock()
	if !ok {
		http.NotFound(w, r)
		return
	}
	_, _ = w.Write(body)
}

func (l *lists) set(path string, body []byte) {
	l.mu.Lock()
	defer l.mu.Unlock()
	l.answers[path] = body
}

// onlyHost carries requests to one host alone: the test reaches no other.
type onlyHost struct {
	host string
	next http.RoundTripper
}

func (o onlyHost) RoundTrip(r *http.Request) (*http.Response, error) {
	if r.URL.Host != o.host {
		return nil, errors.New("not the test's server")
	}
	return o.next.RoundTrip(r)
}

func ruleSetOf(t *testing.T, rule option.DefaultHeadlessRule) []byte {
	t.Helper()
	var buf bytes.Buffer
	set := option.PlainRuleSet{Rules: []option.HeadlessRule{{Type: C.RuleTypeDefault, DefaultOptions: rule}}}
	if err := srs.Write(&buf, set, C.RuleSetVersionCurrent); err != nil {
		t.Fatal(err)
	}
	return buf.Bytes()
}

func runRuleSets(t *testing.T, client nikopb.NikoServiceClient, request *nikopb.RuleSetRequest) *nikopb.TaskProgress {
	t.Helper()
	stream, err := client.UpdateRuleSets(context.Background(), request)
	if err != nil {
		t.Fatal(err)
	}
	var last *nikopb.TaskProgress
	for {
		p, err := stream.Recv()
		if err == io.EOF {
			break
		}
		if err != nil {
			t.Fatal(err)
		}
		last = p
	}
	if last == nil || !last.Done {
		t.Fatalf("no result: %v", last)
	}
	return last
}

func ruleSetStatus(t *testing.T, client nikopb.NikoServiceClient, item string) *nikopb.RuleSet {
	t.Helper()
	list, err := client.ListRuleSets(context.Background(), &nikopb.Empty{})
	if err != nil {
		t.Fatal(err)
	}
	for _, set := range list.RuleSets {
		if set.Item == item {
			return set
		}
	}
	t.Fatalf("no rule-set %s: %v", item, list)
	return nil
}

// The rules through NikoService: saved, moved, deleted, each in Watch; the Russian preset; a rule-set downloaded
// from a local server, checked as the core reads it (CheckRuleSet), its status; a failed download kept apart from the
// file there was; Connect's config with the rules and the list's file, the preset's direct rule waiting for its
// lists.
func TestRulesAndRuleSets(t *testing.T) {
	l := &lists{answers: map[string][]byte{}, hits: map[string]int{}}
	web := httptest.NewTLSServer(l)
	defer web.Close()
	host, _ := url.Parse(web.URL)
	var started []string
	s := storeServer(t.TempDir())
	s.transport = onlyHost{host: host.Host, next: web.Client().Transport}
	s.start = func(_ context.Context, config string, _ *boxdd.SystemProxy) error {
		started = append(started, config)
		return nil
	}
	client := serve(t, s)
	ctx := context.Background()
	watch := changes(t, client)
	if c := next(t, watch); !c.Reset_ {
		t.Fatalf("%v", c)
	}
	if ids := runImport(t, client, socksLink, 0).Ids; len(ids) != 1 {
		t.Fatalf("imported %v", ids)
	}
	for c := next(t, watch); c.Kind != nikopb.Change_TASK; c = next(t, watch) {
	}

	item := "srs:" + web.URL + "/geosite-example.srs?token=fake-token"
	mine, err := client.SaveRule(ctx, &nikopb.Rule{Name: "listed sites directly", Enabled: true,
		Domains: item + "\nfull:direct.example", Outbound: -1, DpiBypass: true})
	if err != nil || mine.Id != 1 || mine.Order != 1 {
		t.Fatalf("%v %v", mine, err)
	}
	expectChange(t, watch, 3, nikopb.Change_RULES, 1)
	via, err := client.SaveRule(ctx, &nikopb.Rule{Name: "via the profile", Enabled: true, Domains: "via.example",
		Outbound: 1, Programs: []string{"browser.exe"}})
	if err != nil || via.Id != 2 {
		t.Fatalf("%v %v", via, err)
	}
	expectChange(t, watch, 4, nikopb.Change_RULES, 2)
	if _, err = client.SaveRule(ctx, &nikopb.Rule{Name: "bad", Network: "icmp"}); code(err) != codes.InvalidArgument {
		t.Fatalf("a bad network: %v", err)
	}
	preset, err := client.ApplyPreset(ctx, &nikopb.PresetRequest{Preset: nikopb.PresetRequest_RUSSIA})
	if err != nil || len(preset.Rules) != 2 || preset.Rules[0].Name != russiaBlockedName || len(preset.RuleSets) != 4 {
		t.Fatalf("%v %v", preset, err)
	}
	expectChange(t, watch, 5, nikopb.Change_RULES, 3, 4)
	expectChange(t, watch, 6, nikopb.Change_RULE_SETS)
	if _, err = client.MoveRules(ctx, &nikopb.MoveRulesRequest{Ids: []int64{2}, Before: 1}); err != nil {
		t.Fatal(err)
	}
	expectChange(t, watch, 7, nikopb.Change_RULES, 1, 2)
	rules, err := client.ListRules(ctx, &nikopb.Empty{})
	if err != nil || len(rules.Rules) != 4 || rules.Rules[0].Id != 2 || rules.Rules[1].Id != 1 || !rules.Rules[1].DpiBypass {
		t.Fatalf("%v %v", rules, err)
	}

	// not here yet; an address no rule names is not fetched
	if set := ruleSetStatus(t, client, item); set.Downloaded || !set.Enabled || !set.Fetchable || set.Updated != 0 {
		t.Fatalf("%v", set)
	}
	stream, err := client.UpdateRuleSets(ctx, &nikopb.RuleSetRequest{Items: []string{"srs:" + web.URL + "/other.srs"}})
	if err == nil {
		_, err = stream.Recv()
	}
	if code(err) != codes.InvalidArgument {
		t.Fatalf("a list no rule names: %v", err)
	}

	// downloaded and checked: its kinds, its time
	l.set("/geosite-example.srs", ruleSetOf(t, option.DefaultHeadlessRule{DomainSuffix: badoption.Listable[string]{"example.org"}}))
	if result := runRuleSets(t, client, &nikopb.RuleSetRequest{Items: []string{item}}); result.Code != 0 ||
		!strings.Contains(result.Line, "1 fetched") {
		t.Fatalf("%v", result)
	}
	set := ruleSetStatus(t, client, item)
	if !set.Downloaded || set.Kinds != 1 || set.Updated == 0 || set.Error != "" || set.Size == 0 {
		t.Fatalf("%v", set)
	}
	// fresh: not fetched again unless forced
	runRuleSets(t, client, &nikopb.RuleSetRequest{Items: []string{item}})
	if l.hits["/geosite-example.srs"] != 1 {
		t.Fatalf("fetched %d times", l.hits["/geosite-example.srs"])
	}

	// what the core would not load is refused (CheckRuleSet), the file there was kept; the error names no address
	file := filepath.Join(ruleSetsDir(s.users.opened()[0].store), nikoconf.RuleSetFileName(item))
	before, _ := os.ReadFile(file)
	l.set("/geosite-example.srs", append([]byte("SRS\x01"), bytes.Repeat([]byte{0xff}, 64)...))
	result := runRuleSets(t, client, &nikopb.RuleSetRequest{Items: []string{item}, Force: true})
	if codes.Code(result.Code) != codes.InvalidArgument || strings.Contains(result.Error, "fake-token") {
		t.Fatalf("%v", result)
	}
	if after, _ := os.ReadFile(file); !bytes.Equal(after, before) {
		t.Fatal("the file replaced")
	}
	if set = ruleSetStatus(t, client, item); !set.Downloaded || set.Error == "" {
		t.Fatalf("%v", set)
	}
	// a server that does not answer with the list: UNAVAILABLE
	l.mu.Lock()
	delete(l.answers, "/geosite-example.srs")
	l.mu.Unlock()
	if result = runRuleSets(t, client, &nikopb.RuleSetRequest{Items: []string{item}, Force: true}); codes.Code(result.Code) != codes.Unavailable ||
		strings.Contains(result.Error, "fake-token") || strings.Contains(result.Line, "fake-token") {
		t.Fatalf("%v", result)
	}

	// Connect: the rules in their order, the list by its file, the preset's direct rule waiting for its blocked lists
	if _, err = client.Select(ctx, &nikopb.ProfileId{Id: 1}); err != nil {
		t.Fatal(err)
	}
	if _, err = client.Connect(ctx, &nikopb.ConnectRequest{ProfileId: 1}); err != nil {
		t.Fatal(err)
	}
	// the lists Connect fetches behind it (none here: the preset's are not this server's, ours is gone) end with the
	// test, which removes their directory
	u := s.users.opened()[0]
	deadline := time.Now().Add(20 * time.Second)
	for {
		u.ruleSets.mu.Lock()
		running := u.ruleSets.running
		u.ruleSets.mu.Unlock()
		if !running {
			break
		}
		if time.Now().After(deadline) {
			t.Fatal("the update after Connect did not end")
		}
		time.Sleep(10 * time.Millisecond)
	}
	config := started[len(started)-1]
	coreReads(t, config)
	built := parseConfig(t, config)
	var listed, viaRule, blocked map[string]any
	for _, r := range built.Route["rules"].([]any) {
		r := r.(map[string]any)
		if d, _ := r["domain"].([]any); len(d) > 0 && d[0] == "direct.example" {
			listed = r
		}
		if d, _ := r["domain_suffix"].([]any); len(d) > 0 && d[0] == "via.example" {
			viaRule = r
		}
		if r["outbound"] == "bypass" && r["domain_suffix"] != nil && strings.Contains(config, "xn--p1ai") {
			blocked = r
		}
	}
	if listed == nil || listed["outbound"] != "bypass" || listed["rule_set"].([]any)[0] != item || listed["tcp_desync"] != nil {
		t.Fatalf("the listed rule: %v", listed)
	}
	if viaRule == nil || viaRule["outbound"] != "proxy" || viaRule["process_name"] == nil {
		t.Fatalf("the program's rule: %v", viaRule)
	}
	if blocked != nil || strings.Contains(config, "xn--p1ai") {
		t.Fatal("the preset's direct rule did not wait for its lists")
	}
	var local map[string]any
	for _, rs := range built.Route["rule_set"].([]any) {
		if rs := rs.(map[string]any); rs["tag"] == item {
			local = rs
		}
	}
	if local == nil || local["type"] != "local" || local["path"] != file {
		t.Fatalf("the rule-set: %v", local)
	}
}

// errorText keeps what the user sees valid UTF-8 and short, cut at a rune's start.
func TestErrorText(t *testing.T) {
	long := strings.Repeat("ж", maxErrorText)
	got := errorText("\xff" + long)
	if !strings.HasPrefix(got, "�") || len(got) > maxErrorText+len("…") || !strings.HasSuffix(got, "…") {
		t.Fatalf("%q", got)
	}
	for _, r := range strings.TrimSuffix(got, "…") {
		if r == '�' && got[0] != 0xef {
			t.Fatalf("cut inside a rune: %q", got)
		}
	}
	if listName("srs:https://lists.example.com/a/../b.srs?token=fake-token") != "lists.example.com/b.srs" {
		t.Fatal(listName("srs:https://lists.example.com/a/../b.srs?token=fake-token"))
	}
}

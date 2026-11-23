package nikoservice

import (
	"context"
	"encoding/base64"
	"io"
	"net/http"
	"net/http/httptest"
	"os"
	"strings"
	"sync"
	"testing"
	"time"

	"libcore/nikopb"

	"google.golang.org/grpc/codes"
)

// provider is a subscription's provider on a local HTTP server: its answer, which the test changes, and what it was
// asked with.
type provider struct {
	mu      sync.Mutex
	status  int
	body    string
	headers map[string]string
	agents  []string
	// hold, when set, holds each answer until it is closed
	hold chan struct{}
}

func (p *provider) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	p.mu.Lock()
	p.agents = append(p.agents, r.UserAgent())
	status, body, hold := p.status, p.body, p.hold
	for k, v := range p.headers {
		w.Header().Set(k, v)
	}
	p.mu.Unlock()
	if hold != nil {
		<-hold
	}
	w.WriteHeader(status)
	_, _ = io.WriteString(w, body)
}

func (p *provider) answer(status int, body string, headers map[string]string) {
	p.mu.Lock()
	defer p.mu.Unlock()
	p.status, p.body, p.headers = status, body, headers
}

func links(list ...string) string {
	return base64.StdEncoding.EncodeToString([]byte(strings.Join(list, "\n")))
}

func runRefresh(t *testing.T, client nikopb.NikoServiceClient, group int64) *nikopb.TaskProgress {
	t.Helper()
	stream, err := client.Refresh(context.Background(), &nikopb.RefreshRequest{GroupId: group})
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

// until reads Watch up to a task's result, the kinds of what came before it.
func until(t *testing.T, ch <-chan *nikopb.Change) (*nikopb.TaskProgress, []nikopb.Change_Kind) {
	t.Helper()
	var kinds []nikopb.Change_Kind
	for {
		c := next(t, ch)
		if c.Kind == nikopb.Change_TASK {
			return c.Task, kinds
		}
		kinds = append(kinds, c.Kind)
	}
}

func subscriptionGroup(t *testing.T, client nikopb.NikoServiceClient, id int64) *nikopb.Group {
	t.Helper()
	groups, err := client.ListGroups(context.Background(), &nikopb.Empty{})
	if err != nil {
		t.Fatal(err)
	}
	for _, g := range groups.Groups {
		if g.Id == id {
			return g
		}
	}
	t.Fatalf("no group %d: %v", id, groups)
	return nil
}

func profileNames(t *testing.T, client nikopb.NikoServiceClient, group int64) string {
	t.Helper()
	list, err := client.ListProfiles(context.Background(), &nikopb.ListProfilesRequest{GroupId: group})
	if err != nil {
		t.Fatal(err)
	}
	var out []string
	for _, p := range list.Profiles {
		out = append(out, p.Name+"@"+p.Address)
	}
	return strings.Join(out, " ")
}

// A subscription end to end through NikoService against a provider on a local HTTP server: the import of its link
// makes the group and fetches it with the app's User-Agent; the provider's name, interval, traffic and expiry come
// with the answer; an update on demand merges the new answer into the group as the app does; a failing provider, a
// panel's refusal, an update while one runs; the timed update by the interval, one that waits for a connection; the
// group's settings; all of it again in a daemon started anew.
func TestSubscriptions(t *testing.T) {
	p := &provider{}
	web := httptest.NewServer(p)
	defer web.Close()
	link := web.URL + "/sub/fake-token"
	p.answer(http.StatusOK, links("socks5://192.0.2.1:1080#one", "socks5://192.0.2.2:1080#two",
		"socks5://192.0.2.3:1080#three"), map[string]string{
		"Subscription-Userinfo":   "upload=1000; download=2000; total=1073741824; expire=1893456000",
		"Profile-Title":           "base64:" + base64.StdEncoding.EncodeToString([]byte("Провайдер")),
		"profile-update-interval": "6",
		"announce":                "news",
	})
	root := t.TempDir()
	clock := time.Unix(1790000000, 0)
	var clockMu sync.Mutex
	now := func() time.Time {
		clockMu.Lock()
		defer clockMu.Unlock()
		return clock
	}
	s := &server{users: newUsers(root, developmentCaller, nil), now: now}
	client := serve(t, s)
	ctx := context.Background()
	watch := changes(t, client)
	next(t, watch) // the reset

	// recognized as a subscription, a node's invite as a node's
	if preview, err := client.Recognize(ctx, &nikopb.ImportInput{Text: link}); err != nil ||
		preview.Kind != nikopb.ImportPreview_SUBSCRIPTION || preview.Subscription != link {
		t.Fatalf("%v %v", preview, err)
	}
	deep := "clash://install-config?url=" + strings.NewReplacer(":", "%3A", "/", "%2F").Replace(link) + "&name=Mine"
	if preview, err := client.Recognize(ctx, &nikopb.ImportInput{Text: deep}); err != nil ||
		preview.Kind != nikopb.ImportPreview_SUBSCRIPTION {
		t.Fatalf("a deep link: %v %v", preview, err)
	}
	invite, _ := os.ReadFile("../../shared/golden/raycontrol/invites/rcinv2-v4.txt")
	if preview, err := client.Recognize(ctx, &nikopb.ImportInput{Text: string(invite)}); err != nil ||
		preview.Kind != nikopb.ImportPreview_NODE || !preview.Node.Invite || preview.Node.Host != "203.0.113.7" {
		t.Fatalf("an invite: %v %v", preview, err)
	}
	if _, err := client.Recognize(ctx, &nikopb.ImportInput{Text: "RCINV2:AAAA"}); code(err) != codes.InvalidArgument {
		t.Fatalf("a broken invite: %v", err)
	}

	// the import: the group, then its first update
	result := runImport(t, client, link, 0)
	if result.Code != 0 || result.GroupId == 0 || len(result.Ids) != 3 || !strings.Contains(result.Line, "3 added") {
		t.Fatalf("the import: %v", result)
	}
	if task, kinds := until(t, watch); task.TaskId != result.TaskId || len(kinds) < 3 {
		t.Fatalf("Watch: %v %v", task, kinds)
	}
	id := result.GroupId
	g := subscriptionGroup(t, client, id)
	sub := g.Subscription
	if g.Type != nikopb.Group_SUBSCRIPTION || g.Name != "Провайдер" || g.Profiles != 3 || sub.Link != link ||
		!sub.AutoUpdate || sub.AutoUpdateDelay != 360 || sub.Used != 3000 || sub.Total != 1073741824 ||
		sub.Expire != 1893456000 || sub.Announce != "news" || sub.LastUpdated != 1790000000 || sub.Updating {
		t.Fatalf("the group: %v", g)
	}
	if p.agents[0] != "NekoBox/Android/"+Version+" (Niko; Prefer ClashMeta Format)" {
		t.Fatalf("asked with %q", p.agents[0])
	}
	if names := profileNames(t, client, id); names != "one@192.0.2.1:1080 two@192.0.2.2:1080 three@192.0.2.3:1080" {
		t.Fatal(names)
	}
	before, _ := client.ListProfiles(ctx, &nikopb.ListProfilesRequest{GroupId: id})
	// its profiles are its subscription's: no import into it
	if r := runImport(t, client, socksLink, id); r.Code != int32(codes.FailedPrecondition) {
		t.Fatalf("an import into it: %v", r)
	}
	until(t, watch)

	// an update on demand: one moved, one changed (it keeps its number, as in the app), one gone, one new
	p.answer(http.StatusOK, links("socks5://192.0.2.4:1080#four", "socks5://192.0.2.1:1080#one",
		"socks5://192.0.2.2:1081#two"), map[string]string{"profile-title": "ignored now"})
	result = runRefresh(t, client, id)
	if result.Code != 0 || len(result.Ids) != 1 || !strings.Contains(result.Line, "1 added, 1 changed, 1 deleted") {
		t.Fatalf("the update: %v", result)
	}
	until(t, watch)
	if names := profileNames(t, client, id); names != "four@192.0.2.4:1080 one@192.0.2.1:1080 two@192.0.2.2:1081" {
		t.Fatal(names)
	}
	after, _ := client.ListProfiles(ctx, &nikopb.ListProfilesRequest{GroupId: id})
	if after.Profiles[1].Id != before.Profiles[0].Id || after.Profiles[2].Id != before.Profiles[1].Id {
		t.Fatalf("the ids: %v, before %v", after, before)
	}
	if g = subscriptionGroup(t, client, id); g.Name != "Провайдер" || g.Subscription.Used != 0 {
		t.Fatalf("after: %v", g)
	}

	// what fails, and leaves the group as it was
	for name, c := range map[string]struct {
		status  int
		headers map[string]string
		body    string
		want    codes.Code
	}{
		"the provider fails":   {http.StatusInternalServerError, nil, "down", codes.Unavailable},
		"a panel's refusal":    {http.StatusOK, map[string]string{"x-hwid-max-devices-reached": "true"}, "", codes.PermissionDenied},
		"a refusal by status":  {http.StatusNotFound, map[string]string{"x-hwid-not-supported": "true"}, "", codes.PermissionDenied},
		"no profiles":          {http.StatusOK, nil, "hello", codes.InvalidArgument},
		"another subscription": {http.StatusOK, nil, "clash://install-config?url=https%3A%2F%2Fx.example%2Fs", codes.InvalidArgument},
	} {
		p.answer(c.status, c.body, c.headers)
		if r := runRefresh(t, client, id); r.Code != int32(c.want) || r.Done != true {
			t.Errorf("%s: %v, want %v", name, r, c.want)
		}
		until(t, watch)
	}
	if names := profileNames(t, client, id); names != "four@192.0.2.4:1080 one@192.0.2.1:1080 two@192.0.2.2:1081" {
		t.Fatal(names)
	}
	// no such group, a basic one
	if r := runRefresh(t, client, 99); r.Code != int32(codes.NotFound) {
		t.Errorf("no group: %v", r)
	}
	until(t, watch)
	if r := runRefresh(t, client, 1); r.Code != int32(codes.FailedPrecondition) {
		t.Errorf("a basic group: %v", r)
	}
	until(t, watch)

	// one update of a group at a time: the second is aborted, and the group says it updates meanwhile
	hold := make(chan struct{})
	p.mu.Lock()
	p.hold = hold
	p.mu.Unlock()
	p.answer(http.StatusOK, links("socks5://192.0.2.4:1080#four"), nil)
	first := make(chan *nikopb.TaskProgress, 1)
	go func() { first <- runRefresh(t, client, id) }()
	deadline := time.Now().Add(10 * time.Second)
	for !subscriptionGroup(t, client, id).Subscription.Updating {
		if time.Now().After(deadline) {
			t.Fatal("never updating")
		}
		time.Sleep(10 * time.Millisecond)
	}
	if r := runRefresh(t, client, id); r.Code != int32(codes.Aborted) {
		t.Errorf("a second update: %v", r)
	}
	close(hold)
	if r := <-first; r.Code != 0 {
		t.Fatalf("the first update: %v", r)
	}
	p.mu.Lock()
	p.hold = nil
	p.mu.Unlock()
	until(t, watch)
	until(t, watch)

	// the timed update: not before its interval (6 hours from the provider), then by itself
	p.answer(http.StatusOK, links("socks5://192.0.2.4:1080#four", "socks5://192.0.2.5:1080#five"), nil)
	asked := len(p.agents)
	s.autoUpdates(now().Add(5 * time.Hour))
	time.Sleep(100 * time.Millisecond)
	if len(p.agents) != asked {
		t.Fatal("updated before its interval")
	}
	s.autoUpdates(now().Add(6 * time.Hour))
	if task, _ := until(t, watch); task.Code != 0 || len(task.Ids) != 1 || !strings.HasPrefix(task.TaskId, "update-") {
		t.Fatalf("the timed update: %v", task)
	}
	// one that waits for a connection, without one: not updated
	g, err := client.SaveGroup(ctx, &nikopb.Group{Id: id, Type: nikopb.Group_SUBSCRIPTION, Subscription: &nikopb.Subscription{
		Link: link, AutoUpdate: true, AutoUpdateDelay: 60, UpdateWhenConnectedOnly: true, Deduplication: true}})
	if err != nil || g.Name != "Провайдер" || g.Subscription.AutoUpdateDelay != 60 || !g.Subscription.Deduplication ||
		g.Subscription.LastUpdated == 0 {
		t.Fatalf("the settings: %v %v", g, err)
	}
	asked = len(p.agents)
	s.autoUpdates(now().Add(48 * time.Hour))
	time.Sleep(100 * time.Millisecond)
	if len(p.agents) != asked {
		t.Fatal("updated without a connection")
	}
	// settings refused
	for name, sub := range map[string]*nikopb.Subscription{
		"no link":       {Link: "ftp://x.example/s"},
		"too often":     {Link: link, AutoUpdateDelay: 5},
		"no link given": nil,
	} {
		if _, err = client.SaveGroup(ctx, &nikopb.Group{Id: id, Type: nikopb.Group_SUBSCRIPTION, Subscription: sub}); code(err) != codes.InvalidArgument {
			t.Errorf("%s: %v", name, err)
		}
	}
	if _, err = client.SaveGroup(ctx, &nikopb.Group{Id: 1, Type: nikopb.Group_SUBSCRIPTION, Subscription: &nikopb.Subscription{Link: link}}); code(err) != codes.FailedPrecondition {
		t.Errorf("a basic group as a subscription's: %v", err)
	}
	if _, err = client.SaveGroup(ctx, &nikopb.Group{Id: id, Name: "basic now"}); code(err) != codes.FailedPrecondition {
		t.Errorf("a subscription's group as a basic one: %v", err)
	}

	// a deep link's name; Refresh of every subscription
	r := runImport(t, client, deep, 0)
	if r.Code != 0 || subscriptionGroup(t, client, r.GroupId).Name != "Mine" {
		t.Fatalf("a deep link's import: %v", r)
	}
	until(t, watch)
	if r = runRefresh(t, client, 0); r.Code != 0 || len(strings.Split(r.Line, "\n")) != 2 {
		t.Fatalf("every subscription: %v", r)
	}

	// a daemon started anew: the store's groups, open at the start for the timed updates
	again := &server{users: newUsers(root, developmentCaller, nil), now: now}
	again.users.openAll()
	if len(again.users.opened()) != 1 {
		t.Fatal("the store not opened at the start")
	}
	if g = subscriptionGroup(t, serve(t, again), id); g.Subscription.Link != link || g.Profiles != 2 {
		t.Fatalf("again: %v", g)
	}
}

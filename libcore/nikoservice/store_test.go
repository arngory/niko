package nikoservice

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"io"
	"net"
	"os"
	"path/filepath"
	"reflect"
	"strings"
	"testing"
	"time"

	"libcore/nikofmt"
	"libcore/nikopb"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/credentials"
	"google.golang.org/grpc/credentials/insecure"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/peer"
	"google.golang.org/grpc/status"
	"google.golang.org/grpc/test/bufconn"
)

const (
	naiveLink = "naive+https://niko:examplepass0000000000000@203.0.113.7:443?sni=naive.example.com#naive"
	socksLink = "socks5://user:fake-pass@198.51.100.40:1080#SOCKS5"
	subLink   = "https://sub.example/s/0123456789abcdef0123"
)

// serve is NikoService on an in-memory connection, its client, and the server.
func serve(t *testing.T, s *server) nikopb.NikoServiceClient {
	t.Helper()
	listener := bufconn.Listen(1 << 20)
	g := grpc.NewServer()
	nikopb.RegisterNikoServiceServer(g, s)
	go g.Serve(listener)
	t.Cleanup(g.Stop)
	conn, err := grpc.NewClient("passthrough:///nikod", grpc.WithTransportCredentials(insecure.NewCredentials()),
		grpc.WithContextDialer(func(ctx context.Context, _ string) (net.Conn, error) { return listener.DialContext(ctx) }))
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { conn.Close() })
	return nikopb.NewNikoServiceClient(conn)
}

func storeServer(root string) *server {
	return &server{users: newUsers(root, developmentCaller, nil)}
}

func code(err error) codes.Code { return status.Code(err) }

// changes reads a Watch stream into a channel.
func changes(t *testing.T, client nikopb.NikoServiceClient) <-chan *nikopb.Change {
	t.Helper()
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)
	stream, err := client.Watch(ctx, &nikopb.WatchRequest{})
	if err != nil {
		t.Fatal(err)
	}
	out := make(chan *nikopb.Change, 64)
	go func() {
		defer close(out)
		for {
			c, err := stream.Recv()
			if err != nil {
				return
			}
			out <- c
		}
	}()
	return out
}

func next(t *testing.T, ch <-chan *nikopb.Change) *nikopb.Change {
	t.Helper()
	select {
	case c, ok := <-ch:
		if !ok {
			t.Fatal("Watch ended")
		}
		return c
	case <-time.After(10 * time.Second):
		t.Fatal("no change from Watch")
	}
	return nil
}

func expectChange(t *testing.T, ch <-chan *nikopb.Change, revision uint64, kind nikopb.Change_Kind, ids ...int64) *nikopb.Change {
	t.Helper()
	c := next(t, ch)
	if c.Revision != revision || c.Kind != kind || c.Reset_ || !reflect.DeepEqual(c.Ids, ids) {
		t.Fatalf("change %v, want revision %d %v %v", c, revision, kind, ids)
	}
	return c
}

func runImport(t *testing.T, client nikopb.NikoServiceClient, text string, group int64) *nikopb.TaskProgress {
	t.Helper()
	stream, err := client.Import(context.Background(), &nikopb.ImportRequest{Input: &nikopb.ImportInput{Text: text}, GroupId: group})
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

// An imported link is seen through Watch, then read, saved, moved and deleted; what the store holds is the parser's
// bean, and it is there again for a daemon started anew.
func TestImportWatch(t *testing.T) {
	root := t.TempDir()
	client := serve(t, storeServer(root))
	ctx := context.Background()
	watch := changes(t, client)
	if first := next(t, watch); !first.Reset_ || first.Revision != 0 {
		t.Fatalf("the first change: %v", first)
	}

	// recognized, nothing written
	preview, err := client.Recognize(ctx, &nikopb.ImportInput{Text: naiveLink + "\n" + socksLink})
	if err != nil || preview.Kind != nikopb.ImportPreview_PROFILES || len(preview.Profiles) != 2 {
		t.Fatalf("%v %v", preview, err)
	}
	if p := preview.Profiles[1]; p.Type != "socks" || p.Name != "SOCKS5" || p.Address != "198.51.100.40:1080" || p.Id != 0 || p.BeanJson != "" {
		t.Fatalf("a recognized row: %v", p)
	}
	if list, _ := client.ListProfiles(ctx, &nikopb.ListProfilesRequest{}); len(list.Profiles) != 0 {
		t.Fatalf("written: %v", list)
	}
	// from a file's content as well
	if preview, err = client.Recognize(ctx, &nikopb.ImportInput{File: []byte(socksLink + "\r\n"), FileName: "links.txt"}); err != nil ||
		len(preview.Profiles) != 1 {
		t.Fatalf("a file: %v %v", preview, err)
	}

	// imported: the profiles, then the task's result
	result := runImport(t, client, naiveLink+"\n"+socksLink, 0)
	if result.Code != 0 || !reflect.DeepEqual(result.Ids, []int64{1, 2}) || result.TaskId == "" {
		t.Fatalf("the result: %v", result)
	}
	expectChange(t, watch, 1, nikopb.Change_PROFILES, 1, 2)
	if task := expectChange(t, watch, 2, nikopb.Change_TASK); task.Task.TaskId != result.TaskId || !reflect.DeepEqual(task.Task.Ids, result.Ids) {
		t.Fatalf("the task in Watch: %v", task)
	}

	list, err := client.ListProfiles(ctx, &nikopb.ListProfilesRequest{})
	if err != nil || len(list.Profiles) != 2 || list.Profiles[0].Name != "naive" || list.Profiles[0].Type != "naive" ||
		list.Profiles[0].GroupId != 1 || list.Profiles[1].Order != 2 || list.Profiles[0].BeanJson != "" {
		t.Fatalf("%v %v", list, err)
	}
	p, err := client.GetProfile(ctx, &nikopb.ProfileId{Id: 2})
	if err != nil {
		t.Fatal(err)
	}
	bean, _ := nikofmt.ParseLink(socksLink, nikofmt.DefaultSettings)
	want, _ := json.Marshal(bean)
	if !sameJSON(t, []byte(p.BeanJson), want) {
		t.Fatalf("the bean: %s, want %s", p.BeanJson, want)
	}

	// a group of the user's, the profile edited, moved into it, the other deleted
	groups, err := client.ListGroups(ctx, &nikopb.Empty{})
	if err != nil || len(groups.Groups) != 1 || !groups.Groups[0].Ungrouped || groups.Groups[0].Profiles != 2 {
		t.Fatalf("%v %v", groups, err)
	}
	work, err := client.SaveGroup(ctx, &nikopb.Group{Name: "work"})
	if err != nil || work.Id != 2 {
		t.Fatalf("%v %v", work, err)
	}
	expectChange(t, watch, 3, nikopb.Change_GROUPS, 2)
	edited := strings.Replace(p.BeanJson, `"name":"SOCKS5"`, `"name":"edited"`, 1)
	saved, err := client.SaveProfile(ctx, &nikopb.Profile{Id: 2, Type: "socks", BeanJson: edited})
	if err != nil || saved.Name != "edited" || !strings.Contains(saved.BeanJson, `"edited"`) {
		t.Fatalf("%v %v", saved, err)
	}
	expectChange(t, watch, 4, nikopb.Change_PROFILES, 2)
	if _, err = client.MoveProfiles(ctx, &nikopb.MoveRequest{Ids: []int64{2}, GroupId: work.Id}); err != nil {
		t.Fatal(err)
	}
	expectChange(t, watch, 5, nikopb.Change_PROFILES, 2)
	if _, err = client.DeleteProfiles(ctx, &nikopb.ProfileIds{Ids: []int64{1, 7}}); err != nil {
		t.Fatal(err)
	}
	expectChange(t, watch, 6, nikopb.Change_PROFILES, 1)
	if list, _ = client.ListProfiles(ctx, &nikopb.ListProfilesRequest{GroupId: work.Id}); len(list.Profiles) != 1 || list.Profiles[0].Id != 2 {
		t.Fatalf("work: %v", list)
	}

	// what is refused, and how
	for name, err := range map[string]error{
		"no profile":                   second(client.GetProfile(ctx, &nikopb.ProfileId{Id: 1})),
		"no group's list":              second(client.ListProfiles(ctx, &nikopb.ListProfilesRequest{GroupId: 9})),
		"a bean of another type":       second(client.SaveProfile(ctx, &nikopb.Profile{Id: 2, Type: "naive", BeanJson: edited})),
		"not a bean":                   second(client.SaveProfile(ctx, &nikopb.Profile{Type: "socks", BeanJson: `{"port": 1}`})),
		"a subscription's group":       second(client.SaveGroup(ctx, &nikopb.Group{Name: "s", Type: nikopb.Group_SUBSCRIPTION})),
		"the ungrouped group":          second(client.DeleteGroup(ctx, &nikopb.GroupId{Id: 1})),
		"a move into no group":         second(client.MoveProfiles(ctx, &nikopb.MoveRequest{Ids: []int64{2}, GroupId: 9})),
		"an import into no group":      taskError(runImport(t, client, socksLink, 9)),
		"an import of nothing":         taskError(runImport(t, client, "hello", 0)),
		"an import of a node's invite": taskError(runImport(t, client, "the invite: rcinv1:eyJ0ZXN0IjoxfQ", 0)),
	} {
		want := map[string]codes.Code{
			"no profile": codes.NotFound, "no group's list": codes.NotFound, "a bean of another type": codes.InvalidArgument,
			"not a bean": codes.InvalidArgument, "a subscription's group": codes.InvalidArgument,
			"the ungrouped group": codes.FailedPrecondition, "a move into no group": codes.NotFound,
			"an import into no group": codes.NotFound,
			"an import of nothing":    codes.InvalidArgument, "an import of a node's invite": codes.Unimplemented,
		}[name]
		if code(err) != want {
			t.Errorf("%s: %v, want %v", name, err, want)
		}
	}
	// the failed tasks' results in Watch too, nothing else changed
	for revision := uint64(7); revision <= 9; revision++ {
		if c := expectChange(t, watch, revision, nikopb.Change_TASK); c.Task.Code == 0 || len(c.Task.Ids) != 0 {
			t.Fatalf("a failed task: %v", c)
		}
	}
	// an input over its limit (the daemon's gRPC limit is the same: here, past it)
	if _, err = (&server{}).Recognize(ctx, &nikopb.ImportInput{Text: strings.Repeat("x", maxInput+1)}); code(err) != codes.InvalidArgument {
		t.Errorf("an input over its limit: %v", err)
	}
	// more profiles than one import adds: refused already in the preview, not first by the import
	over := strings.Repeat(socksLink+"\n", maxImport+1)
	if _, err = (&server{}).Recognize(ctx, &nikopb.ImportInput{Text: over}); code(err) != codes.InvalidArgument {
		t.Errorf("a preview over the import's limit: %v", err)
	}
	// a subscription's link recognized as one
	preview, err = client.Recognize(ctx, &nikopb.ImportInput{Text: socksLink + "\n" + subLink})
	if err != nil || preview.Kind != nikopb.ImportPreview_SUBSCRIPTION || preview.Subscription != subLink || len(preview.Profiles) != 0 {
		t.Fatalf("%v %v", preview, err)
	}
	if preview, err = client.Recognize(ctx, &nikopb.ImportInput{Text: "hello"}); err != nil || preview.Kind != nikopb.ImportPreview_NOTHING {
		t.Fatalf("%v %v", preview, err)
	}

	// the group deleted with its profile
	if _, err = client.DeleteGroup(ctx, &nikopb.GroupId{Id: work.Id}); err != nil {
		t.Fatal(err)
	}
	expectChange(t, watch, 10, nikopb.Change_GROUPS, work.Id)
	expectChange(t, watch, 11, nikopb.Change_PROFILES, 2)

	// a daemon started again: the store as it was, the revisions from 0
	result = runImport(t, client, socksLink, 0)
	again := serve(t, storeServer(root))
	if first := next(t, changes(t, again)); !first.Reset_ || first.Revision != 0 {
		t.Fatalf("the first change again: %v", first)
	}
	if list, err = again.ListProfiles(ctx, &nikopb.ListProfilesRequest{}); err != nil || len(list.Profiles) != 1 ||
		list.Profiles[0].Id != result.Ids[0] || list.Profiles[0].Name != "SOCKS5" {
		t.Fatalf("again: %v %v", list, err)
	}
	// in development mode's user's directory, beside boxdd's own files of that user
	digest := sha256.Sum256([]byte("local"))
	if _, err = os.Stat(filepath.Join(root, "users", hex.EncodeToString(digest[:]), "niko", "store.json")); err != nil {
		t.Fatal(err)
	}
}

func second[T any](_ T, err error) error { return err }

func taskError(p *nikopb.TaskProgress) error {
	if p.Code == 0 {
		return nil
	}
	return status.Error(codes.Code(p.Code), p.Error)
}

func sameJSON(t *testing.T, a, b []byte) bool {
	t.Helper()
	var x, y any
	if err := json.Unmarshal(a, &x); err != nil {
		t.Fatal(err)
	}
	if err := json.Unmarshal(b, &y); err != nil {
		t.Fatal(err)
	}
	return reflect.DeepEqual(x, y)
}

// Each user has a store of its own; a caller the daemon does not name has none.
func TestUsers(t *testing.T) {
	root := t.TempDir()
	byMetadata := func(ctx context.Context) (string, error) {
		md, _ := metadata.FromIncomingContext(ctx)
		if who := md.Get("who"); len(who) == 1 {
			return who[0], nil
		}
		return developmentCaller(ctx)
	}
	client := serve(t, &server{users: newUsers(root, byMetadata, nil)})
	as := func(who string) context.Context {
		return metadata.AppendToOutgoingContext(context.Background(), "who", who)
	}
	stream, err := client.Import(as("alice"), &nikopb.ImportRequest{Input: &nikopb.ImportInput{Text: socksLink}})
	if err != nil {
		t.Fatal(err)
	}
	if p, err := stream.Recv(); err != nil || p.Code != 0 {
		t.Fatalf("%v %v", p, err)
	}
	if list, _ := client.ListProfiles(as("alice"), &nikopb.ListProfilesRequest{}); len(list.Profiles) != 1 {
		t.Fatalf("alice: %v", list)
	}
	if list, _ := client.ListProfiles(as("bob"), &nikopb.ListProfilesRequest{}); len(list.Profiles) != 0 {
		t.Fatalf("bob sees: %v", list)
	}
	entries, _ := os.ReadDir(filepath.Join(root, "users"))
	if len(entries) != 2 {
		t.Fatalf("users: %v", entries)
	}

	// with transport credentials, a caller the daemon verified but does not name to the service yet
	verified := peer.NewContext(context.Background(), &peer.Peer{AuthInfo: verifiedPeer{}})
	if _, err = developmentCaller(verified); code(err) != codes.Unimplemented {
		t.Fatalf("a named caller: %v", err)
	}
	loopback := &net.TCPAddr{IP: net.IPv4(127, 0, 0, 1), Port: 50000}
	if id, err := developmentCaller(peer.NewContext(context.Background(), &peer.Peer{Addr: loopback})); err != nil || id != "local" {
		t.Fatalf("development mode's: %q %v", id, err)
	}
	// without credentials but from another machine, or from nowhere known: no one's store
	for _, addr := range []net.Addr{&net.TCPAddr{IP: net.ParseIP("192.0.2.7"), Port: 50000}, nil} {
		if _, err = developmentCaller(peer.NewContext(context.Background(), &peer.Peer{Addr: addr})); code(err) != codes.Unimplemented {
			t.Fatalf("a caller from %v: %v", addr, err)
		}
	}
	if _, err = (&server{}).ListGroups(context.Background(), &nikopb.Empty{}); code(err) != codes.Unimplemented {
		t.Fatalf("no store: %v", err)
	}
}

// A watcher that fell behind gets a reset with the revision now instead of what it missed, then the changes again;
// Watch ends with the daemon.
func TestWatchGap(t *testing.T) {
	f := newFeed()
	w, revision := f.subscribe()
	if revision != 0 {
		t.Fatal(revision)
	}
	for i := 0; i < watchBuffer+5; i++ {
		f.publish(&nikopb.Change{Kind: nikopb.Change_PROFILES, Ids: []int64{int64(i)}})
	}
	if c := f.next(w, <-w.changes); !c.Reset_ || c.Revision != watchBuffer+5 {
		t.Fatalf("after the gap: %v", c)
	}
	if len(w.changes) != 0 {
		t.Fatalf("%d left", len(w.changes))
	}
	f.publish(&nikopb.Change{Kind: nikopb.Change_GROUPS})
	if c := f.next(w, <-w.changes); c.Reset_ || c.Revision != watchBuffer+6 || c.Kind != nikopb.Change_GROUPS {
		t.Fatalf("after the reset: %v", c)
	}
	f.unsubscribe(w)
	f.publish(&nikopb.Change{Kind: nikopb.Change_GROUPS})
	if len(w.changes) != 0 {
		t.Fatal("published to a watcher gone")
	}

	done := make(chan struct{})
	client := serve(t, &server{users: newUsers(t.TempDir(), developmentCaller, done)})
	stream, err := client.Watch(context.Background(), &nikopb.WatchRequest{})
	if err != nil {
		t.Fatal(err)
	}
	if c, err := stream.Recv(); err != nil || !c.Reset_ {
		t.Fatalf("%v %v", c, err)
	}
	close(done)
	if _, err = stream.Recv(); code(err) != codes.Unavailable {
		t.Fatalf("the daemon stopped: %v", err)
	}
}

// verifiedPeer is the transport credentials' account of a caller, as boxdd's peerAuthInfo is.
type verifiedPeer struct{ credentials.CommonAuthInfo }

func (verifiedPeer) AuthType() string { return "local-process" }

// Select and GetSelected: the choice said in Watch, kept by the store, gone with its profile.
func TestSelect(t *testing.T) {
	client := serve(t, storeServer(t.TempDir()))
	ctx := context.Background()
	watch := changes(t, client)
	next(t, watch)
	if got, err := client.GetSelected(ctx, &nikopb.Empty{}); err != nil || got.Id != 0 {
		t.Fatalf("a new store: %v %v", got, err)
	}
	runImport(t, client, naiveLink+"\n"+socksLink, 0)
	expectChange(t, watch, 1, nikopb.Change_PROFILES, 1, 2)
	expectChange(t, watch, 2, nikopb.Change_TASK)

	if _, err := client.Select(ctx, &nikopb.ProfileId{Id: 2}); err != nil {
		t.Fatal(err)
	}
	expectChange(t, watch, 3, nikopb.Change_SELECTED, 2)
	if got, err := client.GetSelected(ctx, &nikopb.Empty{}); err != nil || got.Id != 2 {
		t.Fatalf("selected: %v %v", got, err)
	}
	if _, err := client.Select(ctx, &nikopb.ProfileId{Id: 7}); code(err) != codes.NotFound {
		t.Fatalf("no such profile: %v", err)
	}

	if _, err := client.DeleteProfiles(ctx, &nikopb.ProfileIds{Ids: []int64{2}}); err != nil {
		t.Fatal(err)
	}
	expectChange(t, watch, 4, nikopb.Change_PROFILES, 2)
	expectChange(t, watch, 5, nikopb.Change_SELECTED)
	if got, err := client.GetSelected(ctx, &nikopb.Empty{}); err != nil || got.Id != 0 {
		t.Fatalf("after the deletion: %v %v", got, err)
	}
}

func TestShareProfile(t *testing.T) {
	client := serve(t, storeServer(t.TempDir()))
	ctx := context.Background()
	runImport(t, client, naiveLink+"\n"+socksLink, 0)
	for id, want := range map[int64]string{
		1: "naive+https://niko:examplepass0000000000000@203.0.113.7/?sni=naive.example.com#naive",
		2: "socks5://user:fake-pass@198.51.100.40:1080/#SOCKS5",
	} {
		got, err := client.ShareProfile(ctx, &nikopb.ProfileId{Id: id})
		if err != nil || got.Link != want {
			t.Fatalf("profile %d: %v %v, want %s", id, got, err, want)
		}
	}
	if _, err := client.ShareProfile(ctx, &nikopb.ProfileId{Id: 7}); code(err) != codes.NotFound {
		t.Fatalf("no such profile: %v", err)
	}
}

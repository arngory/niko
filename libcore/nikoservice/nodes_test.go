package nikoservice

import (
	"context"
	"errors"
	"io"
	"os"
	"path/filepath"
	"strings"
	"sync"
	"syscall"
	"testing"
	"time"

	"libcore/nikopb"
	"libcore/rayapi"
	"libcore/raysub"

	"github.com/sagernet/sing-box/experimental/boxdd"
	"github.com/sagernet/sing-box/protocol/knock"

	"google.golang.org/grpc/codes"
)

const goldenRaycontrol = "../../shared/golden/raycontrol"

func golden(t *testing.T, path string) string {
	t.Helper()
	data, err := os.ReadFile(filepath.Join(goldenRaycontrol, path))
	if err != nil {
		t.Fatal(err)
	}
	return strings.TrimSpace(string(data))
}

// fakeNode is a node as rayapi and raysub reach it: the registration's answers in turn, the subscription's payload
// or error, and what was asked of it.
type fakeNode struct {
	mu        sync.Mutex
	answers   []error
	bundle    string
	role      string
	identity  []rayapi.Identity
	invites   []rayapi.Invite
	payload   string
	fetchErr  error
	requests  []raysub.Request
	clockSkew int64
}

func (f *fakeNode) register(_ context.Context, invite rayapi.Invite, _ func(string, string, syscall.RawConn) error) (*rayapi.Registered, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.invites = append(f.invites, invite)
	f.identity = append(f.identity, *invite.Identity)
	if len(f.answers) > 0 {
		err := f.answers[0]
		f.answers = f.answers[1:]
		if err != nil {
			return nil, err
		}
	}
	return &rayapi.Registered{Bundle: f.bundle, DeviceID: "3333333333333333", Role: f.role, Identity: *invite.Identity}, nil
}

func (f *fakeNode) fetch(_ context.Context, request raysub.Request) (*raysub.Result, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.requests = append(f.requests, request)
	if f.fetchErr != nil {
		return nil, f.fetchErr
	}
	payload, err := raysub.PayloadOf([]byte(f.payload))
	if err != nil {
		return nil, err
	}
	return &raysub.Result{Payload: payload, ClockSkew: f.clockSkew}, nil
}

func nodeServer(t *testing.T, node *fakeNode) (*server, nikopb.NikoServiceClient) {
	t.Helper()
	s := storeServer(t.TempDir())
	s.registerAt, s.fetch = node.register, node.fetch
	made := 0
	s.newIdentity = func() (rayapi.Identity, error) {
		made++
		return rayapi.Identity{Cert: "cert", Key: strings.Repeat("K", 8) + string(rune('a'+made)), Pin: "pin"}, nil
	}
	at := time.Unix(1_800_000_000, 0)
	s.now = func() time.Time { return at }
	return s, serve(t, s)
}

func importWith(t *testing.T, client nikopb.NikoServiceClient, text, pin string) *nikopb.TaskProgress {
	t.Helper()
	stream, err := client.Import(context.Background(), &nikopb.ImportRequest{Input: &nikopb.ImportInput{Text: text}, Pin: pin})
	if err != nil {
		t.Fatal(err)
	}
	var last *nikopb.TaskProgress
	for {
		p, err := stream.Recv()
		if err == io.EOF {
			return last
		}
		if err != nil {
			t.Fatal(err)
		}
		last = p
	}
}

// An invite with a PIN: asked for before any try, refused for one not of 6 digits, asked again after a wrong one
// with the tries left, then registered with the same client key every try; the node's package kept, its
// subscription fetched into a group of the node's (its name, its rows, the node in ListNodes with what its card
// shows); updated again by Refresh of the node; its rows built with the node's knock, the device key by reference
// alone; removed with its group.
func TestNodeByInvite(t *testing.T) {
	node := &fakeNode{bundle: golden(t, "invites/rcdev1-user.txt"), role: "user",
		payload: golden(t, "payloads/every-kind/payload.json"), clockSkew: 3}
	left := 2
	node.answers = []error{&rayapi.Error{Code: "permission_denied", PinLeft: &left}, nil}
	s, client := nodeServer(t, node)
	ctx := context.Background()
	invite := golden(t, "invites/rcinv1-pin.txt")

	preview, err := client.Recognize(ctx, &nikopb.ImportInput{Text: invite})
	if err != nil || preview.Kind != nikopb.ImportPreview_NODE || !preview.Node.Invite || !preview.Node.PinRequired ||
		preview.Node.Host != "node.example.com" || preview.Node.Replaces {
		t.Fatalf("%v %v", preview, err)
	}
	if r := importWith(t, client, invite, ""); r.Pin == nil || r.Pin.Left != -1 || r.Pin.Wrong || len(node.invites) != 0 {
		t.Fatalf("no PIN: %v", r)
	}
	if r := importWith(t, client, invite, "12345"); codes.Code(r.Code) != codes.InvalidArgument || len(node.invites) != 0 {
		t.Fatalf("a PIN of 5 digits: %v", r)
	}
	if r := importWith(t, client, invite, "111111"); r.Pin == nil || r.Pin.Left != 2 || !r.Pin.Wrong {
		t.Fatalf("a wrong PIN: %v", r)
	}
	r := importWith(t, client, invite, "222222")
	if r.Code != 0 || r.NodeId != strings.Repeat("11", 16) || r.GroupId == 0 || len(r.Ids) != 6 ||
		!strings.Contains(r.Line, "registered at 203.0.113.7 as user") {
		t.Fatalf("the import: %v", r)
	}
	if len(node.identity) != 2 || node.identity[0] != node.identity[1] || node.invites[1].PinCode != "222222" ||
		node.invites[0].InviteID != strings.Repeat("77", 8) || node.invites[0].SPA.DeviceKey != strings.Repeat("88", 32) {
		t.Fatalf("the tries: %+v", node.invites)
	}
	// the key kept for the invite is forgotten once the node is
	if len(s.users.opened()[0].store.Nodes()) != 1 {
		t.Fatal("no node")
	}
	request := node.requests[0]
	if request.DeviceID != strings.Repeat("33", 8) || request.SPA.DeviceKey != strings.Repeat("44", 32) || request.Port != 8443 {
		t.Fatalf("the subscription's request: %+v", request)
	}

	nodes, err := client.ListNodes(ctx, &nikopb.Empty{})
	if err != nil || len(nodes.Nodes) != 1 {
		t.Fatalf("%v %v", nodes, err)
	}
	n := nodes.Nodes[0]
	if n.Name != "Home node one" || n.Note == "" || n.GroupId != r.GroupId || n.Profiles != 6 || !n.Subscription ||
		n.Updated != 1_800_000_000 || n.Expires != 1_800_000_000+3*86400-3 || n.NextUpdate <= n.Updated ||
		n.Traffic == nil || !n.Traffic.NearLimit || n.MinVersion != "1.60.0" || n.ClockSkew != 3 || n.Refused {
		t.Fatalf("the node: %v", n)
	}
	groups, _ := client.ListGroups(ctx, &nikopb.Empty{})
	if g := groups.Groups[len(groups.Groups)-1]; g.NodeId != n.Id || g.Name != "Home node one" {
		t.Fatalf("the group: %v", g)
	}
	rows, _ := client.ListProfiles(ctx, &nikopb.ListProfilesRequest{GroupId: r.GroupId})
	if len(rows.Profiles) != 6 || rows.Profiles[0].NodeId != n.Id || rows.Profiles[0].Name != "REALITY" {
		t.Fatalf("the rows: %v", rows.Profiles)
	}

	// Refresh of the node: the same rows
	stream, err := client.Refresh(ctx, &nikopb.RefreshRequest{NodeId: n.Id})
	if err != nil {
		t.Fatal(err)
	}
	var last *nikopb.TaskProgress
	for p, err := stream.Recv(); err == nil; p, err = stream.Recv() {
		last = p
	}
	if last == nil || last.Code != 0 || len(last.Ids) != 0 || last.GroupId != r.GroupId {
		t.Fatalf("the refresh: %v", last)
	}

	// connected through a row: the knock with SPA, the key by its reference in the core and nowhere in the config
	var config string
	s.start = func(_ context.Context, c string, _ *boxdd.SystemProxy) error {
		config = c
		return nil
	}
	if _, err = client.Connect(ctx, &nikopb.ConnectRequest{ProfileId: rows.Profiles[0].Id}); err != nil {
		t.Fatal(err)
	}
	ref := "raycontrol:" + n.Id + ":" + strings.Repeat("33", 8)
	if !strings.Contains(config, `"type": "knock"`) || !strings.Contains(config, ref) ||
		strings.Contains(config, strings.Repeat("44", 32)) {
		t.Fatalf("the config: %s", config)
	}
	if key, err := knock.DeviceKey(ref); err != nil || len(key) != 32 {
		t.Fatalf("the core's key: %v", err)
	}

	// the node refuses the device: no update planned, a new invite needed
	node.fetchErr = raysub.ErrNotFound
	stream, _ = client.Refresh(ctx, &nikopb.RefreshRequest{GroupId: r.GroupId})
	for p, err := stream.Recv(); err == nil; p, err = stream.Recv() {
		last = p
	}
	if codes.Code(last.Code) != codes.PermissionDenied {
		t.Fatalf("a refusal: %v", last)
	}
	if nodes, _ = client.ListNodes(ctx, &nikopb.Empty{}); !nodes.Nodes[0].Refused || nodes.Nodes[0].Failed != 1 ||
		nodes.Nodes[0].Error == "" {
		t.Fatalf("refused: %v", nodes.Nodes[0])
	}

	// removed: its group, rows and key go
	if _, err = client.DeleteNode(ctx, &nikopb.NodeId{Id: n.Id}); err != nil {
		t.Fatal(err)
	}
	if groups, _ = client.ListGroups(ctx, &nikopb.Empty{}); len(groups.Groups) != 1 {
		t.Fatalf("%v", groups)
	}
	if _, err = knock.DeviceKey(ref); err == nil {
		t.Fatal("the key is still in the core")
	}
}

// A device package is kept as it is, a bot's refused; an invite past its time, refused by the node or for a bot
// fails with its code, and never with the invite's key in its message.
func TestNodeImportRefusals(t *testing.T) {
	node := &fakeNode{bundle: golden(t, "invites/rcdev1-user.txt"), payload: golden(t, "payloads/used-up/payload.json")}
	_, client := nodeServer(t, node)
	r := importWith(t, client, golden(t, "invites/rcdev1-admin-names.txt"), "")
	if r.Code != 0 || len(node.invites) != 0 || len(node.requests) != 1 || !strings.Contains(r.Line, "requires Niko 9.0.0") {
		t.Fatalf("a device package: %v", r)
	}
	// names-v1: by the node's name, its addresses to fall back to
	if q := node.requests[0]; q.Host != "node.example.com" || len(q.Addrs) != 2 {
		t.Fatalf("%+v", q)
	}
	if r = importWith(t, client, golden(t, "invites/rcdev1-bot-role.txt"), ""); codes.Code(r.Code) != codes.FailedPrecondition {
		t.Fatalf("a bot's: %v", r)
	}

	invite := golden(t, "invites/rcinv1.txt")
	for name, c := range map[string]struct {
		err  error
		want codes.Code
	}{
		"refused":          {&rayapi.Error{Code: "not_found"}, codes.NotFound},
		"an integration's": {&rayapi.Error{Code: "failed_precondition", InviteRole: "billing"}, codes.FailedPrecondition},
		"lost":             {rayapi.ErrAnswerLost, codes.Unavailable},
		"no answer":        {rayapi.ErrNoAnswer, codes.Unavailable},
		"other":            {errors.New("dial: refused"), codes.Unavailable},
	} {
		node.answers = []error{c.err}
		r = importWith(t, client, invite, "")
		if codes.Code(r.Code) != c.want || strings.Contains(r.Error, strings.Repeat("88", 32)) || strings.Contains(r.Error, "rcinv1:") {
			t.Errorf("%s: %v", name, r)
		}
	}
	// the key kept across those tries
	for _, id := range node.identity[1:] {
		if id != node.identity[0] {
			t.Fatalf("a new key: %+v", node.identity)
		}
	}
	expired := &fakeNode{}
	s, client := nodeServer(t, expired)
	s.now = func() time.Time { return time.Unix(1_900_000_000, 0) }
	if r = importWith(t, client, invite, ""); codes.Code(r.Code) != codes.FailedPrecondition || len(expired.invites) != 0 {
		t.Fatalf("expired: %v", r)
	}
}

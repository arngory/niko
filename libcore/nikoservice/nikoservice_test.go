package nikoservice

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"os"
	"path/filepath"
	"testing"

	"libcore/nikopb"
	"libcore/nikostore"

	box "github.com/sagernet/sing-box"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/daemon"
	"github.com/sagernet/sing-box/experimental/boxdd"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/metadata"
	"google.golang.org/grpc/status"
)

// a core with the route tester's usual parts: DNS answered from fixed hosts, a rule by domain and one by address
func startCore(t *testing.T) context.Context {
	t.Helper()
	ctx, cancel := context.WithCancel(include.Context(context.Background()))
	t.Cleanup(cancel)
	var options option.Options
	err := options.UnmarshalJSONContext(ctx, []byte(`{
		"log": {"level": "error"},
		"dns": {"servers": [{"type": "hosts", "tag": "dns-hosts", "path": [], "predefined": {"www.example.com": ["203.0.113.7"]}}]},
		"outbounds": [{"type": "direct", "tag": "proxy"}, {"type": "direct", "tag": "bypass"}],
		"route": {
			"rules": [
				{"domain_suffix": ["example.com"], "outbound": "bypass"},
				{"ip_cidr": ["192.0.2.0/24"], "action": "reject"}
			],
			"final": "proxy"
		}
	}`))
	if err != nil {
		t.Fatal(err)
	}
	instance, err := box.New(box.Options{Context: ctx, Options: options})
	if err != nil {
		t.Fatal(err)
	}
	if err = instance.Start(); err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { instance.Close() })
	return ctx
}

func TestRouteTest(t *testing.T) {
	core := startCore(t)
	s := &server{core: func() (context.Context, func()) { return core, func() {} }}
	ask := func(request *nikopb.RouteTestRequest) *nikopb.RouteTestResult {
		t.Helper()
		answer, err := s.RouteTest(context.Background(), request)
		if err != nil {
			t.Fatalf("%v: %v", request, err)
		}
		return answer
	}
	// a domain through the local proxy: the rule by its suffix, the outbound it names
	if r := ask(&nikopb.RouteTestRequest{Host: "www.example.com", Port: 443, Inbound: "mixed-in"}); r.Index != 0 ||
		r.Outbound != "bypass" || r.Rule == "" || len(r.Steps) != 1 || r.Steps[0].Index != 0 {
		t.Fatalf("by domain: %+v", r)
	}
	// through the VPN with DNS allowed: the app's query first, then it connects to the address it got; with no sniff
	// the rules see the address alone, and the domain's rule does not take it
	if r := ask(&nikopb.RouteTestRequest{Host: "www.example.com", Port: 443, Resolve: true}); len(r.Resolved) == 0 ||
		r.Resolved[0] != "203.0.113.7" || r.Destination != "203.0.113.7:443" || r.Index != -1 || r.Outbound != "proxy" {
		t.Fatalf("resolved: %+v", r)
	}
	// an address of the rejected range, UDP
	if r := ask(&nikopb.RouteTestRequest{Host: "192.0.2.1", Port: 53, Network: "udp"}); r.Index != 1 || r.Action != "reject" {
		t.Fatalf("rejected: %+v", r)
	}
	// none matched: the final outbound
	if r := ask(&nikopb.RouteTestRequest{Host: "198.51.100.1", Port: 80}); r.Index != -1 || r.Outbound != "proxy" {
		t.Fatalf("final: %+v", r)
	}
	// a query the tester refuses
	for _, request := range []*nikopb.RouteTestRequest{
		{Host: "", Port: 443},
		{Host: "www.example.com", Port: 443, Network: "sctp"},
		{Host: "www.example.com", Port: 70000},
	} {
		if _, err := s.RouteTest(context.Background(), request); status.Code(err) != codes.InvalidArgument {
			t.Errorf("%v: %v", request, err)
		}
	}
}

func TestRouteTestNoCore(t *testing.T) {
	s := &server{core: func() (context.Context, func()) { return nil, nil }}
	if _, err := s.RouteTest(context.Background(), &nikopb.RouteTestRequest{Host: "www.example.com", Port: 443}); status.Code(err) != codes.FailedPrecondition {
		t.Fatalf("no core: %v", err)
	}
}

// A core stopped during the test: its answer refused, not given as the core's; the hold let go. A caller gone: its own
// cancellation.
func TestRouteTestStopped(t *testing.T) {
	core, stop := context.WithCancel(startCore(t))
	released := 0
	s := &server{core: func() (context.Context, func()) { return core, func() { released++ } }}
	stop()
	if _, err := s.RouteTest(context.Background(), &nikopb.RouteTestRequest{Host: "www.example.com", Port: 443}); status.Code(err) != codes.Aborted {
		t.Fatalf("a stopped core: %v", err)
	}
	if released != 1 {
		t.Fatalf("released %d times", released)
	}
	alive := startCore(t)
	s = &server{core: func() (context.Context, func()) { return alive, func() {} }}
	gone, cancel := context.WithCancel(context.Background())
	cancel()
	if _, err := s.RouteTest(gone, &nikopb.RouteTestRequest{Host: "www.example.com", Port: 443}); status.Code(err) != codes.Canceled {
		t.Fatalf("a caller gone: %v", err)
	}
}

// The store's schema to a caller the store serves; 0 to one it refuses and from a daemon without a store, as the
// window would hear from each of the store's calls.
func TestGetInfo(t *testing.T) {
	served := metadata.NewIncomingContext(context.Background(), metadata.Pairs("who", "alice"))
	refused := context.Background()
	byMetadata := func(ctx context.Context) (string, error) {
		md, _ := metadata.FromIncomingContext(ctx)
		if who := md.Get("who"); len(who) == 1 {
			return who[0], nil
		}
		return "", status.Error(codes.Unauthenticated, "no caller")
	}
	withStore := &server{users: newUsers(t.TempDir(), byMetadata, nil, nil)}
	for _, c := range []struct {
		name   string
		s      *server
		ctx    context.Context
		schema int32
	}{
		{"served", withStore, served, int32(nikostore.Schema())},
		{"refused", withStore, refused, 0},
		{"no store", &server{}, served, 0},
	} {
		info, err := c.s.GetInfo(c.ctx, &nikopb.GetInfoRequest{})
		if err != nil || info.NikoVersion != Version || info.CoreVersion != C.Version || info.Api != API ||
			info.StoreSchema != c.schema {
			t.Errorf("%s: %+v: %v", c.name, info, err)
		}
	}
}

type fakeHost struct {
	ctx    context.Context
	root   string
	caller func(context.Context) (boxdd.Caller, error)
}

func (h *fakeHost) Context() context.Context                       { return h.ctx }
func (h *fakeHost) StartedService() *daemon.StartedService         { return nil }
func (h *fakeHost) Logger() log.ContextLogger                      { return log.StdLogger() }
func (h *fakeHost) StartService(context.Context, string, *boxdd.SystemProxy) error {
	return nil
}
func (h *fakeHost) Caller(ctx context.Context) (boxdd.Caller, error) { return h.caller(ctx) }
func (h *fakeHost) WorkingDirectory() string                         { return h.root }
func (h *fakeHost) UserMode() bool                                   { return false }
func (h *fakeHost) CanStartService(context.Context) error            { return nil }
func (h *fakeHost) UserDirectory(userID string) string {
	if userID == "" {
		return ""
	}
	digest := sha256.Sum256([]byte(userID))
	return filepath.Join(h.root, "users", hex.EncodeToString(digest[:]))
}

// Service().New uses Host.WorkingDirectory(), Host.Caller(), and Host.UserDirectory() rather than
// os.Getwd() or development-only heuristics, and matches openAll's layout.
func TestServiceHostHooks(t *testing.T) {
	ctx, cancel := context.WithCancel(context.Background())
	t.Cleanup(cancel)

	root := t.TempDir()
	other := t.TempDir()
	origWD, err := os.Getwd()
	if err != nil {
		t.Fatal(err)
	}
	t.Cleanup(func() { _ = os.Chdir(origWD) })
	if err := os.Chdir(other); err != nil {
		t.Fatal(err)
	}

	host := &fakeHost{
		ctx:  ctx,
		root: root,
		caller: func(callCtx context.Context) (boxdd.Caller, error) {
			md, _ := metadata.FromIncomingContext(callCtx)
			if who := md.Get("who"); len(who) == 1 {
				return boxdd.Caller{UserID: who[0]}, nil
			}
			return boxdd.Caller{}, status.Error(codes.Unauthenticated, "missing caller")
		},
	}

	raw, err := Service().New(host)
	if err != nil {
		t.Fatalf("Service().New: %v", err)
	}
	srv := raw.(*server)
	client := serve(t, srv)

	// Unauthenticated caller gets UNAUTHENTICATED and StoreSchema == 0.
	if info, err := client.GetInfo(context.Background(), &nikopb.GetInfoRequest{}); err != nil || info.StoreSchema != 0 {
		t.Fatalf("unauthenticated GetInfo = (%+v, %v)", info, err)
	}
	if _, err := client.ListGroups(context.Background(), &nikopb.Empty{}); status.Code(err) != codes.Unauthenticated {
		t.Fatalf("unauthenticated ListGroups = %v, want Unauthenticated", err)
	}

	// Authenticated caller (Windows SID style) writes inside host.WorkingDirectory(), not os.Getwd().
	sid := "S-1-5-21-3623811015-3361044348-30300820-1013"
	sidCtx := metadata.AppendToOutgoingContext(context.Background(), "who", sid)
	if info, err := client.GetInfo(sidCtx, &nikopb.GetInfoRequest{}); err != nil || info.StoreSchema != int32(nikostore.Schema()) {
		t.Fatalf("authenticated GetInfo = (%+v, %v)", info, err)
	}
	if _, err := client.SaveGroup(sidCtx, &nikopb.Group{Name: "work"}); err != nil {
		t.Fatalf("SaveGroup: %v", err)
	}
	wantStore := filepath.Join(host.UserDirectory(sid), "niko", "store.json")
	if _, err := os.Stat(wantStore); err != nil {
		t.Fatalf("expected store at %s: %v", wantStore, err)
	}
	if _, err := os.Stat(filepath.Join(other, "users")); !os.IsNotExist(err) {
		t.Fatalf("store was unexpectedly created under os.Getwd() (%s)", other)
	}

	// Verify openAll discovers the exact directory created via Host.UserDirectory.
	freshUsers := newUsers(root, nil, host.UserDirectory, nil)
	freshUsers.openAll()
	if len(freshUsers.opened()) != 1 {
		t.Fatalf("openAll opened %d stores, want 1", len(freshUsers.opened()))
	}
}

// The policies the daemon applies: the info to any caller it verified, the core's routing to its owner, outside the
// daemon's lifecycle lock; Connect any verified caller's, its start checking the owner under that lock.
func TestServicePolicy(t *testing.T) {
	service := Service()
	if service.Desc.ServiceName != "niko.v1.NikoService" || service.Policy != boxdd.PolicyCaller ||
		service.Methods["RouteTest"] != boxdd.PolicyOwnerUnlocked ||
		service.Methods["TestConnection"] != boxdd.PolicyOwnerUnlocked ||
		service.Methods["ExitAddress"] != boxdd.PolicyOwnerUnlocked ||
		service.Methods["TestProfiles"] != boxdd.PolicyOwnerUnlocked ||
		service.Methods["RunNodeTest"] != boxdd.PolicyOwnerUnlocked ||
		service.Methods["NodeAdminCall"] != boxdd.PolicyOwnerUnlocked ||
		service.Methods["CheckUpdate"] != boxdd.PolicyOwnerUnlocked ||
		service.Methods["DownloadUpdate"] != boxdd.PolicyOwnerUnlocked ||
		len(service.Methods) != 8 {
		t.Fatalf("%+v", service)
	}
}

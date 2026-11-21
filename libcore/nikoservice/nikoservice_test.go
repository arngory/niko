package nikoservice

import (
	"context"
	"net"
	"testing"

	"libcore/nikopb"
	"libcore/nikostore"

	box "github.com/sagernet/sing-box"
	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/boxdd"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/peer"
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
	served := peer.NewContext(context.Background(), &peer.Peer{Addr: &net.TCPAddr{IP: net.IPv4(127, 0, 0, 1), Port: 50000}})
	refused := peer.NewContext(context.Background(), &peer.Peer{Addr: &net.TCPAddr{IP: net.ParseIP("192.0.2.7"), Port: 50000}})
	withStore := &server{users: newUsers(t.TempDir(), developmentCaller, nil)}
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

// The policies the daemon applies: the info to any caller it verified, the core's routing to its owner, outside the
// daemon's lifecycle lock.
func TestServicePolicy(t *testing.T) {
	service := Service()
	if service.Desc.ServiceName != "niko.v1.NikoService" || service.Policy != boxdd.PolicyCaller ||
		service.Methods["RouteTest"] != boxdd.PolicyOwnerUnlocked {
		t.Fatalf("%+v", service)
	}
}

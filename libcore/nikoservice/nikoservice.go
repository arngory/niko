// Package nikoservice is NikoService, Niko's own service in its desktop daemon nikod, beside the core's daemon's
// (docs/design/desktop-stage3.md): the daemon's info and the route tester (slice 1); each user's store of groups and
// profiles, its changes through Watch, the import of share links (slice 4, store.go); the connection to a stored
// profile, its config built here for this computer, in proxy or VPN mode (slices 7 and 8, connect.go), with the
// computer's settings (slice 13's part for a computer, settings.go); subscriptions, updated on demand and by their
// interval (slice 9, subscriptions.go); the route rules, the Russian preset and the rule-sets the daemon downloads
// (slice 10, rules.go); raycontrol nodes by invite, their subscriptions and their rows in Connect (slice 11,
// nodes.go).
package nikoservice

import (
	"context"
	"errors"
	"net/http"
	"sync"
	"syscall"
	"time"

	"libcore/nikopb"
	"libcore/nikostore"
	"libcore/rayapi"
	"libcore/raysub"
	"libcore/routetest"

	C "github.com/sagernet/sing-box/constant"
	"github.com/sagernet/sing-box/experimental/boxdd"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// Version is Niko's version, set by the build (-X libcore/nikoservice.Version=...).
var Version = "dev"

// API is the version of NikoService: what the window may ask of this daemon. 2: the store, Watch and the import; 3:
// Select and GetSelected; 4: ShareProfile; 5: Connect; 6: Connect's mode, the config a computer's; 7: the computer's
// settings (GetSettings, UpdateSettings), Connect with them; 8: the programs through Niko (programs_mode, programs); 9:
// subscriptions (Group.subscription, Refresh, their import); 10: the route rules, the Russian preset and the
// rule-sets (ListRules…UpdateRuleSets), their settings; 11: raycontrol nodes (their import by invite with its PIN,
// ListNodes, DeleteNode, Refresh of a node, their profiles in Connect); 12: per-user store served to any verified
// caller via Host.Caller and Host.UserDirectory (not development mode only).
const API = 12

// Service is NikoService as the daemon serves it: GetInfo for any caller the daemon verified, RouteTest for its
// owner only (it reads the owner's core, as the daemon's own calls on the running core do). RouteTest is checked as
// the owner's under the daemon's lifecycle lock and runs outside it: its lookups take up to the tester's 20 s, which a
// stop, a start or the service's shutdown must not wait for. It holds the running core (daemon.Instance.Hold): a stop
// cancels the core's context, the test ends at once, and only then is the core closed; an answer from a core stopped
// meanwhile is refused (ABORTED), not given as the core's. The store's calls are any verified caller's, on its own
// user's store (store.go); so is Connect, whose start (Host.StartService) checks the daemon's owner itself, as
// DesktopService.StartService does, under the lifecycle lock that PolicyOwner would hold already.
func Service() boxdd.Service {
	return boxdd.Service{
		Desc: &nikopb.NikoService_ServiceDesc,
		New: func(host boxdd.Host) (any, error) {
			root := host.WorkingDirectory()
			caller := func(ctx context.Context) (string, error) {
				c, err := host.Caller(ctx)
				if err != nil {
					return "", err
				}
				return c.UserID, nil
			}
			users := newUsers(root, caller, host.UserDirectory, host.Context().Done())
			setRaycontrolClient()
			s := &server{users: users, start: host.StartService, registerAt: rayapi.Register, fetch: raysub.Fetch,
				newIdentity: rayapi.NewIdentity, core: func() (context.Context, func()) {
					instance := host.StartedService().RunningInstance()
					if instance == nil {
						return nil, nil
					}
					release, held := instance.Hold()
					if !held {
						return nil, nil
					}
					return instance.Context(), release
				}}
			users.onNodesChanged = s.syncLogRedactions
			// the subscriptions' timed updates, without the window, and the rule-sets' while the core runs
			go s.scheduleUpdates()
			return s, nil
		},
		Policy:  boxdd.PolicyCaller,
		Methods: map[string]boxdd.MethodPolicy{"RouteTest": boxdd.PolicyOwnerUnlocked},
	}
}

type server struct {
	nikopb.UnimplementedNikoServiceServer
	// core holds the running core: its context and the release of the hold; nil when none runs
	core func() (context.Context, func())
	// users are the users' stores
	users *users
	// start starts the core with a config for the caller, as DesktopService.StartService, and the system proxy the
	// daemon sets for its owner while the core runs (nil: none); nil: no daemon
	start func(ctx context.Context, config string, systemProxy *boxdd.SystemProxy) error
	// transport carries the subscriptions' and rule-sets' requests in tests; nil uses s.bypassTransport().
	transport           http.RoundTripper
	bypassTransportOnce sync.Once
	cachedBypassRT      http.RoundTripper
	// now is the clock of the subscriptions' updates; nil: time.Now
	now func() time.Time
	// registerAt, fetch and newIdentity are the raycontrol node's registration, subscription and client keys
	// (rayapi.Register, raysub.Fetch, rayapi.NewIdentity; a test's own)
	registerAt  func(ctx context.Context, invite rayapi.Invite, control func(network, address string, conn syscall.RawConn) error) (*rayapi.Registered, error)
	fetch       func(ctx context.Context, request raysub.Request) (*raysub.Result, error)
	newIdentity func() (rayapi.Identity, error)
}

// GetInfo: the store's schema to a caller the store serves; to one it refuses (store.go's caller), 0, as from a daemon
// without one, so the window does not offer what each call of the store would refuse.
func (s *server) GetInfo(ctx context.Context, _ *nikopb.GetInfoRequest) (*nikopb.Info, error) {
	info := &nikopb.Info{NikoVersion: Version, CoreVersion: C.Version, Api: API}
	if s.users.serves(ctx) {
		info.StoreSchema = int32(nikostore.Schema())
	}
	return info, nil
}

func (s *server) RouteTest(ctx context.Context, request *nikopb.RouteTestRequest) (*nikopb.RouteTestResult, error) {
	if request.Port > 65535 {
		return nil, status.Error(codes.InvalidArgument, "port over 65535")
	}
	core, release := s.core()
	if core == nil {
		return nil, status.Error(codes.FailedPrecondition, "the core does not run")
	}
	defer release()
	// the core's services from its context, the caller's cancellation from the request's
	coreCtx, cancel := context.WithCancel(core)
	defer cancel()
	stop := context.AfterFunc(ctx, cancel)
	defer stop()
	result, err := routetest.Run(coreCtx, routetest.Query{
		Network: request.Network,
		Host:    request.Host,
		Port:    uint16(request.Port),
		Inbound: request.Inbound,
		Resolve: request.Resolve,
	})
	// what a stopped core or a gone caller left is no answer: the lookups failed for that, the rules were closing
	if core.Err() != nil {
		return nil, status.Error(codes.Aborted, "the core stopped during the test")
	}
	if ctx.Err() != nil {
		return nil, status.FromContextError(ctx.Err()).Err()
	}
	if errors.Is(err, routetest.ErrTimeLimit) {
		return nil, status.Error(codes.DeadlineExceeded, err.Error())
	}
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, err.Error())
	}
	return resultProto(result), nil
}

func resultProto(r *routetest.Result) *nikopb.RouteTestResult {
	answer := &nikopb.RouteTestResult{
		Index:       int32(r.Index),
		Rule:        r.Rule,
		Action:      r.Action,
		Outbound:    r.Outbound,
		Error:       r.Error,
		Destination: r.Destination,
		FakeIp:      r.FakeIP,
		Resolved:    r.Resolved,
		Notes:       r.Notes,
	}
	for _, step := range r.Steps {
		answer.Steps = append(answer.Steps, &nikopb.RouteTestStep{
			Index:    int32(step.Index),
			Rule:     step.Rule,
			Action:   step.Action,
			Resolved: step.Resolved,
			Note:     step.Note,
		})
	}
	return answer
}

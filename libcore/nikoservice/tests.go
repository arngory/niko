package nikoservice

import (
	"context"
	"net"
	"net/http"
	"strings"
	"sync"
	"time"

	"libcore/exitaddress"
	"libcore/nikoconf"
	"libcore/nikopb"
	"libcore/urltest"

	box "github.com/sagernet/sing-box"
	"github.com/sagernet/sing-box/boxapi"
	"github.com/sagernet/sing-box/include"
	"github.com/sagernet/sing-box/option"
	"github.com/sagernet/sing/common/json"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

const (
	defaultTestTimeoutMs = 5000
	defaultExitTimeoutMs = 10000
	maxTestTimeoutMs     = 30000
	maxProfilesPerBatch  = 256
	testWorkers          = 4
)

func clampTimeout(ms, def int32) time.Duration {
	if ms <= 0 {
		ms = def
	}
	if ms > maxTestTimeoutMs {
		ms = maxTestTimeoutMs
	}
	return time.Duration(ms) * time.Millisecond
}

func validateTestURL(raw string) (string, error) {
	u := strings.TrimSpace(raw)
	if u == "" {
		return urltest.DefaultURL, nil
	}
	lower := strings.ToLower(u)
	if !strings.HasPrefix(lower, "http://") && !strings.HasPrefix(lower, "https://") {
		return "", status.Error(codes.InvalidArgument, "test URL must be http:// or https://")
	}
	if len(u) > 2048 {
		return "", status.Error(codes.InvalidArgument, "test URL is too long")
	}
	return u, nil
}

// TestConnection measures HTTP round-trip latency through the running core's default outbound (D-MAIN-18).
func (s *server) TestConnection(ctx context.Context, req *nikopb.TestConnectionRequest) (*nikopb.TestConnectionResult, error) {
	testURL, err := validateTestURL(req.GetUrl())
	if err != nil {
		return nil, err
	}
	timeout := clampTimeout(req.GetTimeoutMs(), defaultTestTimeoutMs)

	var dial func(ctx context.Context, network, address string) (net.Conn, error)
	var coreCtx context.Context
	var release func()
	if s.coreDial != nil {
		dial = s.coreDial
		coreCtx = ctx
		release = func() {}
	} else {
		if s.coreBox == nil {
			return nil, status.Error(codes.FailedPrecondition, "the core does not run")
		}
		var b *box.Box
		coreCtx, b, release = s.coreBox()
		if coreCtx == nil || b == nil {
			if release != nil {
				release()
			}
			return nil, status.Error(codes.FailedPrecondition, "the core does not run")
		}
		dial = func(dCtx context.Context, network, address string) (net.Conn, error) {
			return boxapi.DialContext(dCtx, b, nil, network, address)
		}
	}
	defer release()

	runCtx, cancel := context.WithCancel(coreCtx)
	defer cancel()
	stop := context.AfterFunc(ctx, cancel)
	defer stop()

	client := &http.Client{Transport: &http.Transport{
		DialContext:           dial,
		TLSHandshakeTimeout:   timeout,
		ResponseHeaderTimeout: timeout,
		ForceAttemptHTTP2:     true,
	}}
	ms, testErr := urltest.Measure(runCtx, client, testURL, timeout)
	if coreCtx.Err() != nil && ctx.Err() == nil {
		return nil, status.Error(codes.Aborted, "the core stopped during the test")
	}
	if ctx.Err() != nil {
		return nil, status.FromContextError(ctx.Err()).Err()
	}
	if testErr != nil {
		return &nikopb.TestConnectionResult{Error: testErr.Error()}, nil
	}
	return &nikopb.TestConnectionResult{LatencyMs: ms}, nil
}

// ExitAddress queries an HTTPS lookup provider through the running core's default outbound (D-MAIN-03/14).
func (s *server) ExitAddress(ctx context.Context, req *nikopb.ExitAddressRequest) (*nikopb.ExitAddressResult, error) {
	provider := strings.TrimSpace(req.GetProvider())
	if provider == "" {
		provider = exitaddress.DefaultProvider
	}
	if _, ok := exitaddress.Providers[provider]; !ok {
		return nil, status.Errorf(codes.InvalidArgument, "unknown exit lookup provider %q", provider)
	}
	timeout := clampTimeout(req.GetTimeoutMs(), defaultExitTimeoutMs)

	var dial func(ctx context.Context, network, address string) (net.Conn, error)
	var coreCtx context.Context
	var release func()
	if s.coreDial != nil {
		dial = s.coreDial
		coreCtx = ctx
		release = func() {}
	} else {
		if s.coreBox == nil {
			return nil, status.Error(codes.FailedPrecondition, "the core does not run")
		}
		var b *box.Box
		coreCtx, b, release = s.coreBox()
		if coreCtx == nil || b == nil {
			if release != nil {
				release()
			}
			return nil, status.Error(codes.FailedPrecondition, "the core does not run")
		}
		dial = func(dCtx context.Context, network, address string) (net.Conn, error) {
			return boxapi.DialContext(dCtx, b, nil, network, address)
		}
	}
	defer release()

	runCtx, cancel := context.WithCancel(coreCtx)
	defer cancel()
	stop := context.AfterFunc(ctx, cancel)
	defer stop()

	ans, err := exitaddress.Lookup(runCtx, dial, provider, timeout)
	if coreCtx.Err() != nil && ctx.Err() == nil {
		return nil, status.Error(codes.Aborted, "the core stopped during the lookup")
	}
	if ctx.Err() != nil {
		return nil, status.FromContextError(ctx.Err()).Err()
	}
	if err != nil {
		return nil, status.Error(codes.Unavailable, err.Error())
	}
	return &nikopb.ExitAddressResult{
		Provider:    ans.Provider,
		Ip:          ans.IP,
		CountryCode: ans.CountryCode,
		Country:     ans.Country,
		City:        ans.City,
	}, nil
}

// TestProfiles tests a batch of stored profiles (or a group's profiles) using isolated in-memory test cores
// and streams each profile's latency result as it completes (D-MAIN-08/12).
func (s *server) TestProfiles(req *nikopb.TestProfilesRequest, stream grpc.ServerStreamingServer[nikopb.ProfileTestResult]) error {
	ctx := stream.Context()
	if s.canStart != nil {
		if err := s.canStart(ctx); err != nil {
			return err
		}
	}
	u, err := s.users.user(ctx)
	if err != nil {
		return err
	}
	testURL, err := validateTestURL(req.GetUrl())
	if err != nil {
		return err
	}
	timeout := clampTimeout(req.GetTimeoutMs(), defaultTestTimeoutMs)

	ids := req.GetProfileIds()
	if len(ids) == 0 {
		rows, err := u.store.Profiles(req.GetGroupId())
		if err != nil {
			return storeError(err)
		}
		ids = make([]int64, 0, len(rows))
		for _, r := range rows {
			ids = append(ids, r.ID)
		}
		// Large subscriptions may contain >256 profiles; cap the group batch cleanly instead of failing.
		if len(ids) > maxProfilesPerBatch {
			ids = ids[:maxProfilesPerBatch]
		}
	} else if len(ids) > maxProfilesPerBatch {
		return status.Errorf(codes.InvalidArgument, "at most %d profiles per batch", maxProfilesPerBatch)
	}
	if len(ids) == 0 {
		return nil
	}

	s.testSlotsOnce.Do(func() {
		if s.testSlots == nil {
			s.testSlots = make(chan struct{}, testWorkers)
		}
	})

	jobs := make(chan int64, len(ids))
	for _, id := range ids {
		jobs <- id
	}
	close(jobs)

	results := make(chan *nikopb.ProfileTestResult, len(ids))
	workers := min(testWorkers, len(ids))
	var wg sync.WaitGroup
	for range workers {
		wg.Add(1)
		go func() {
			defer wg.Done()
			for id := range jobs {
				select {
				case s.testSlots <- struct{}{}:
				case <-ctx.Done():
					return
				}
				var ms int32
				var testErr error
				func() {
					defer func() { <-s.testSlots }()
					if ctx.Err() != nil {
						testErr = ctx.Err()
						return
					}
					if s.testProfile != nil {
						ms, testErr = s.testProfile(ctx, u, id, testURL, timeout)
					} else {
						ms, testErr = s.measureStoredProfile(ctx, u, id, testURL, timeout)
					}
				}()
				if ctx.Err() != nil {
					return
				}
				res := &nikopb.ProfileTestResult{ProfileId: id}
				if testErr != nil {
					res.Error = testErr.Error()
				} else {
					res.LatencyMs = ms
				}
				select {
				case results <- res:
				case <-ctx.Done():
					return
				}
			}
		}()
	}
	go func() {
		wg.Wait()
		close(results)
	}()

	for res := range results {
		if err := stream.Send(res); err != nil {
			return err
		}
	}
	if ctx.Err() != nil {
		return status.FromContextError(ctx.Err()).Err()
	}
	return nil
}

func (s *server) measureStoredProfile(ctx context.Context, u *user, id int64, testURL string, timeout time.Duration) (int32, error) {
	settings, err := u.store.Settings()
	if err != nil {
		return 0, err
	}
	settings.ServiceMode = nikoconf.ModeProxy
	in, err := connectInput(u.store, id, connectSettings(settings))
	if err != nil {
		return 0, err
	}
	in.ForTest = true
	// Preserve the daemon's real privilege mode so privileged daemons keep auto_detect_interface
	// and bypass an active VPN TUN instead of looping test probes back through the running proxy.
	in.UserMode = s.userMode
	built, err := nikoconf.Build(in)
	if err != nil {
		return 0, err
	}
	boxCtx := include.Context(ctx)
	opts, err := json.UnmarshalExtendedContext[option.Options](boxCtx, built.Config)
	if err != nil {
		return 0, err
	}
	b, err := box.New(box.Options{Context: boxCtx, Options: opts})
	if err != nil {
		return 0, err
	}
	if err := b.Start(); err != nil {
		_ = b.Close()
		return 0, err
	}
	defer b.Close()

	client := &http.Client{Transport: &http.Transport{
		DialContext: func(dCtx context.Context, network, address string) (net.Conn, error) {
			return boxapi.DialContext(dCtx, b, nil, network, address)
		},
		TLSHandshakeTimeout:   timeout,
		ResponseHeaderTimeout: timeout,
		ForceAttemptHTTP2:     true,
	}}
	return urltest.Measure(ctx, client, testURL, timeout)
}

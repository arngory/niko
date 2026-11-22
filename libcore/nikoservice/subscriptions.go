package nikoservice

import (
	"context"
	"errors"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"libcore/nikofmt"
	"libcore/nikopb"
	"libcore/nikostore"
	"libcore/nikosub"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

// Subscriptions (slice 9): a subscription's group saved with its subscription (SaveGroup), made by the import of a
// subscription's link, updated as the app updates it (nikosub.Run, then one write of the store) on demand (Refresh)
// and on its own by its interval, as bg/SubscriptionUpdater.kt does. An update is a task: its result in Watch, as an
// import's; the group's change too, when it starts (updating) and when it is written.

// minAutoUpdate is WorkManager's least period, which the app's timed update runs at: an interval under it waits for it.
const minAutoUpdate = 15 * time.Minute

// updates are a user's subscriptions' updates: those running (one at a time a group, as GroupUpdater.updating), and
// when each was last tried on its own, so that a failing one is tried again only after its interval.
type updates struct {
	mu      sync.Mutex
	running map[int64]bool
	tried   map[int64]time.Time
}

func newUpdates() *updates {
	return &updates{running: map[int64]bool{}, tried: map[int64]time.Time{}}
}

// start marks a group's update running; false where one runs already.
func (u *updates) start(id int64) bool {
	u.mu.Lock()
	defer u.mu.Unlock()
	if u.running[id] {
		return false
	}
	u.running[id] = true
	return true
}

func (u *updates) done(id int64) {
	u.mu.Lock()
	defer u.mu.Unlock()
	delete(u.running, id)
}

func (u *updates) isRunning(id int64) bool {
	u.mu.Lock()
	defer u.mu.Unlock()
	return u.running[id]
}

// subscriptionProto is a group's subscription as NikoService gives it: the settings, what its updates said.
func subscriptionProto(sub *nikosub.Subscription, updating bool) *nikopb.Subscription {
	if sub == nil {
		return nil
	}
	p := &nikopb.Subscription{Link: sub.Link, Deduplication: sub.Deduplication, ForceResolve: sub.ForceResolve,
		UpdateWhenConnectedOnly: sub.UpdateWhenConnectedOnly, CustomUserAgent: sub.CustomUserAgent,
		AutoUpdate: sub.AutoUpdate, AutoUpdateDelay: int32(sub.AutoUpdateDelay), LastUpdated: sub.LastUpdated,
		Announce: sub.Announce, Updating: updating}
	if usage, ok := sub.Usage(); ok {
		p.Used, p.Total, p.Expire = usage.Used, usage.Total, usage.Expire
	}
	return p
}

// saveSubscriptionGroup is SaveGroup of a subscription's group.
func (s *server) saveSubscriptionGroup(ctx context.Context, request *nikopb.Group) (*nikopb.Group, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	settings := nikostore.SubscriptionSettings{}
	if sub := request.Subscription; sub != nil {
		settings = nikostore.SubscriptionSettings{Link: sub.Link, Deduplication: sub.Deduplication,
			ForceResolve: sub.ForceResolve, UpdateWhenConnectedOnly: sub.UpdateWhenConnectedOnly,
			CustomUserAgent: sub.CustomUserAgent, AutoUpdate: sub.AutoUpdate, AutoUpdateDelay: int(sub.AutoUpdateDelay)}
	}
	saved, err := u.store.SaveSubscriptionGroup(request.Id, request.Name, settings)
	if err != nil {
		return nil, storeError(err)
	}
	return s.groupProto(u, saved), nil
}

// connected: the core runs (the app's serviceState.connected), for a subscription that updates only then.
func (s *server) connected() bool {
	if s.core == nil {
		return false
	}
	ctx, release := s.core()
	if ctx == nil {
		return false
	}
	release()
	return true
}

// subscriptionOptions are an update's: the app's User-Agent of this Niko, the direct connection of the daemon (the
// app goes through its local proxy; a bypass of the TUN comes with the nodes' Control, slice 11), the computer's
// IPv6 setting for forceResolve.
func (s *server) subscriptionOptions(u *user) nikosub.Options {
	o := nikosub.Options{Client: &nikosub.Client{UserAgent: nikosub.UserAgent(Version), Transport: s.transport},
		Settings: nikofmt.DefaultSettings, Now: s.now}
	if settings, err := u.store.Settings(); err == nil {
		o.IPv6First = settings.IPv6Mode >= 2
	}
	return o
}

// subscriptionError is an update's error as its result's code.
func subscriptionError(err error) error {
	var refused *nikosub.RefusedError
	var fetch *nikosub.FetchError
	var found *nikofmt.SubscriptionFound
	switch {
	case errors.As(err, &refused):
		return status.Error(codes.PermissionDenied, refused.Error())
	case errors.As(err, &fetch):
		return status.Error(codes.Unavailable, fetch.Error())
	case errors.Is(err, nikosub.ErrNoProfiles):
		return status.Error(codes.InvalidArgument, err.Error())
	case errors.As(err, &found):
		return status.Error(codes.InvalidArgument, "the subscription's answer is another subscription's link")
	case errors.Is(err, context.Canceled):
		return status.Error(codes.Canceled, "the daemon stops")
	}
	if st, ok := status.FromError(err); ok {
		return st.Err()
	}
	return status.Error(codes.InvalidArgument, "the subscription's answer: "+err.Error())
}

// updateGroup runs one group's update to its end: its result's line, the profiles it added, or its error.
func (s *server) updateGroup(ctx context.Context, u *user, id int64) (string, []int64, error) {
	if !u.updates.start(id) {
		return "", nil, status.Error(codes.Aborted, "an update of this group runs already")
	}
	defer func() {
		u.updates.done(id)
		u.feed.publish(&nikopb.Change{Kind: nikopb.Change_GROUPS, Ids: []int64{id}})
	}()
	group, exists, err := u.store.SubscriptionOf(id)
	if err != nil {
		return "", nil, storeError(err)
	}
	u.feed.publish(&nikopb.Change{Kind: nikopb.Change_GROUPS, Ids: []int64{id}})
	update, err := nikosub.Run(ctx, group.Name, *group.Subscription, exists, s.subscriptionOptions(u))
	if err != nil {
		return "", nil, subscriptionError(err)
	}
	applied, err := u.store.ApplyUpdate(id, update)
	if err != nil {
		return "", nil, storeError(err)
	}
	plan := update.Plan
	line := update.Name + ": "
	if plan.Changed == 0 && len(plan.Duplicate) == 0 {
		line += "no difference"
	} else {
		line += strconv.Itoa(len(plan.Added)) + " added, " + strconv.Itoa(len(plan.Updated)) + " changed, " +
			strconv.Itoa(len(plan.Deleted)) + " deleted"
		if len(plan.Duplicate) > 0 {
			line += ", " + strconv.Itoa(len(plan.Duplicate)) + " duplicates"
		}
	}
	return line, applied.Added, nil
}

// subscriptionTask runs updates of groups as one task to its end and tells its result to the user's watchers; each
// group's start goes to progress, which may be nil.
func (s *server) subscriptionTask(u *user, task string, ids []int64, progress func(*nikopb.TaskProgress)) *nikopb.TaskProgress {
	result := &nikopb.TaskProgress{TaskId: task, Done: true}
	var lines []string
	for _, id := range ids {
		if progress != nil {
			progress(&nikopb.TaskProgress{TaskId: task, Line: "updating group " + strconv.FormatInt(id, 10), GroupId: id})
		}
		line, added, err := s.updateGroup(s.users.context(), u, id)
		if len(ids) == 1 {
			result.GroupId = id
		}
		if err != nil {
			st := status.Convert(err)
			// the first failure is the result's; the others' lines still tell
			if result.Code == 0 {
				result.Code, result.Error = int32(st.Code()), st.Message()
			}
			lines = append(lines, "group "+strconv.FormatInt(id, 10)+": "+st.Message())
			continue
		}
		result.Ids = append(result.Ids, added...)
		lines = append(lines, line)
	}
	result.Line = strings.Join(lines, "\n")
	u.feed.publish(&nikopb.Change{Kind: nikopb.Change_TASK, Task: proto.Clone(result).(*nikopb.TaskProgress)})
	return result
}

func (s *server) Refresh(request *nikopb.RefreshRequest, stream grpc.ServerStreamingServer[nikopb.TaskProgress]) error {
	u, err := s.users.user(stream.Context())
	if err != nil {
		return err
	}
	ids := []int64{request.GroupId}
	if request.GroupId == 0 {
		ids = nil
		for _, g := range u.store.Subscriptions() {
			ids = append(ids, g.ID)
		}
	}
	task := "refresh-" + strconv.FormatUint(s.users.tasks.Add(1), 10)
	return runTask(stream, func(progress func(*nikopb.TaskProgress)) *nikopb.TaskProgress {
		return s.subscriptionTask(u, task, ids, progress)
	})
}

// runTask runs a task that goes on without the stream, sending its progress while the stream lasts, then its result.
func runTask(stream grpc.ServerStreamingServer[nikopb.TaskProgress], run func(func(*nikopb.TaskProgress)) *nikopb.TaskProgress) error {
	steps := make(chan *nikopb.TaskProgress, 16)
	result := make(chan *nikopb.TaskProgress, 1)
	go func() {
		result <- run(func(p *nikopb.TaskProgress) {
			select {
			case steps <- p:
			default: // a stream that does not read loses steps, not the task
			}
		})
	}()
	for {
		select {
		case p := <-steps:
			if err := stream.Send(p); err != nil {
				return err
			}
		case p := <-result:
			return stream.Send(p)
		case <-stream.Context().Done():
			return status.FromContextError(stream.Context().Err()).Err()
		}
	}
}

// rayToken is a raycontrol package in a text, as RayImport.matches finds one: an invite (rcinv1, RCINV2, RCINV3) or a
// device package (rcdev1).
var rayToken = regexp.MustCompile(`(?:rcinv1|rcdev1):[A-Za-z0-9_-]|(?:RCINV2|RCINV3):\S`)

// subscriptionOfLink is the subscription a link found in an input names: an http(s) address as it is (the app's
// parseProxies wraps it into clash://install-config), another client's "add subscription" link read as the app reads
// it. ok false for sn://subscription of a whole group, which is not read here.
func subscriptionOfLink(link string) (nikosub.DeepLink, bool) {
	lower := strings.ToLower(link)
	if strings.HasPrefix(lower, "http://") || strings.HasPrefix(lower, "https://") {
		return nikosub.DeepLink{URL: link}, true
	}
	return nikosub.ParseDeepLink(link)
}

// importSubscription makes a group of a subscription and updates it, as MainActivity.importSubscription does: the
// link's name, else the app's default, which the first update replaces by the provider's. The group stays when its
// first update fails.
func (s *server) importSubscription(u *user, task string, link nikosub.DeepLink, progress func(*nikopb.TaskProgress)) *nikopb.TaskProgress {
	g, err := u.store.SaveSubscriptionGroup(0, link.Name, nikostore.SubscriptionSettings{Link: link.URL})
	if err != nil {
		result := &nikopb.TaskProgress{TaskId: task, Done: true}
		st := status.Convert(storeError(err))
		result.Code, result.Error = int32(st.Code()), st.Message()
		u.feed.publish(&nikopb.Change{Kind: nikopb.Change_TASK, Task: proto.Clone(result).(*nikopb.TaskProgress)})
		return result
	}
	return s.subscriptionTask(u, task, []int64{g.ID}, progress)
}

// autoUpdates is the timed update of every open store's subscriptions (SubscriptionUpdater.UpdateTask): each that
// says so once its interval has passed since its last update (or the last try), a subscription that waits for a
// connection only while the core runs.
func (s *server) autoUpdates(now time.Time) {
	for _, u := range s.users.opened() {
		var due []int64
		for _, g := range u.store.Subscriptions() {
			sub := g.Subscription
			if sub == nil || !sub.AutoUpdate || sub.Link == "" {
				continue
			}
			if sub.UpdateWhenConnectedOnly && !s.connected() {
				continue
			}
			period := max(time.Duration(sub.AutoUpdateDelay)*time.Minute, minAutoUpdate)
			last := time.Unix(sub.LastUpdated, 0)
			u.updates.mu.Lock()
			if tried := u.updates.tried[g.ID]; tried.After(last) {
				last = tried
			}
			if now.Sub(last) >= period && !u.updates.running[g.ID] {
				u.updates.tried[g.ID] = now
				due = append(due, g.ID)
			}
			u.updates.mu.Unlock()
		}
		if len(due) > 0 {
			task := "update-" + strconv.FormatUint(s.users.tasks.Add(1), 10)
			go s.subscriptionTask(u, task, due, nil)
		}
	}
}

// scheduleUpdates looks at the subscriptions every minute while the daemon runs, the stores on the disk opened first:
// the timed updates go on without the window.
func (s *server) scheduleUpdates() {
	s.users.openAll()
	ticker := time.NewTicker(time.Minute)
	defer ticker.Stop()
	for {
		select {
		case <-s.users.done:
			return
		case now := <-ticker.C:
			s.autoUpdates(now)
		}
	}
}

// openAll opens the store of each user directory that has one.
func (u *users) openAll() {
	dirs, _ := filepath.Glob(filepath.Join(u.root, "users", "*", "niko"))
	for _, dir := range dirs {
		if _, err := os.Stat(filepath.Join(dir, "store.json")); err == nil {
			_, _ = u.openDir(dir)
		}
	}
}

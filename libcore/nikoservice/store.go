package nikoservice

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"net"
	"path/filepath"
	"strconv"
	"sync"
	"sync/atomic"

	"libcore/nikofmt"
	"libcore/nikopb"
	"libcore/nikostore"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/peer"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

// The store's calls: each on the store of the user who called (docs/design/desktop-stage3.md §4, decision 5), at
// <the daemon's working directory>/users/<sha256 of the user's id>/niko, beside boxdd's own files of that user.

// users are the daemon's users' stores, each opened at its first call and kept.
type users struct {
	// root is the daemon's working directory
	root string
	// caller is who called: the user whose store it is
	caller func(context.Context) (string, error)
	// sealer: nil, the platform's
	sealer nikostore.Sealer
	// done ends the watchers with the daemon; nil: never
	done <-chan struct{}

	mu    sync.Mutex
	open  map[string]*user
	tasks atomic.Uint64
}

type user struct {
	store *nikostore.Store
	feed  *feed
}

func newUsers(root string, caller func(context.Context) (string, error), done <-chan struct{}) *users {
	return &users{root: root, caller: caller, done: done, open: map[string]*user{}}
}

// developmentCaller is the caller as far as NikoService can know it now. The daemon verifies each caller (on Windows
// the installed window, on Linux a local process by its credentials) but does not name it to an added service: a hook
// in the fork is due for that (§6, the Host's caller and the user's directory). Until then only development mode's
// caller is known: TCP on 127.0.0.1 without transport credentials, which boxdd itself takes for the user "local"
// (platformFallbackPeerIdentity); with credentials, the store is refused rather than shared between users.
func developmentCaller(ctx context.Context) (string, error) {
	if p, ok := peer.FromContext(ctx); ok && p.AuthInfo == nil && onThisMachine(p.Addr) {
		return "local", nil
	}
	return "", status.Error(codes.Unimplemented,
		"the daemon does not name its caller to NikoService yet: the store works in development mode only")
}

// onThisMachine: a caller on this machine, TCP from a loopback address (development mode listens on 127.0.0.1 alone,
// but a store of secrets does not lean on that), or a test's connection within the process (bufconn).
func onThisMachine(addr net.Addr) bool {
	if tcp, ok := addr.(*net.TCPAddr); ok {
		return tcp.IP.IsLoopback()
	}
	return addr != nil && addr.Network() == "bufconn"
}

// serves: whether the caller has a store here, without opening it.
func (u *users) serves(ctx context.Context) bool {
	if u == nil {
		return false
	}
	_, err := u.caller(ctx)
	return err == nil
}

// user is the caller's store, opened if it is not yet.
func (u *users) user(ctx context.Context) (*user, error) {
	if u == nil {
		return nil, status.Error(codes.Unimplemented, "no store")
	}
	id, err := u.caller(ctx)
	if err != nil {
		return nil, err
	}
	u.mu.Lock()
	defer u.mu.Unlock()
	if opened := u.open[id]; opened != nil {
		return opened, nil
	}
	digest := sha256.Sum256([]byte(id))
	f := newFeed()
	store, err := nikostore.Open(filepath.Join(u.root, "users", hex.EncodeToString(digest[:]), "niko"), nikostore.Options{
		Sealer: u.sealer,
		OnChange: func(c nikostore.Change) {
			f.publish(&nikopb.Change{Kind: changeKind(c.Kind), Ids: c.IDs})
		},
	})
	if errors.Is(err, nikostore.ErrNewerSchema) {
		return nil, status.Error(codes.FailedPrecondition, err.Error())
	}
	if err != nil {
		return nil, status.Error(codes.Internal, "the store: "+err.Error())
	}
	opened := &user{store: store, feed: f}
	u.open[id] = opened
	return opened, nil
}

func changeKind(kind nikostore.Kind) nikopb.Change_Kind {
	switch kind {
	case nikostore.KindProfiles:
		return nikopb.Change_PROFILES
	case nikostore.KindGroups:
		return nikopb.Change_GROUPS
	}
	return nikopb.Change_KIND_UNSPECIFIED
}

// storeError is a store's error as its gRPC status.
func storeError(err error) error {
	switch {
	case errors.Is(err, nikostore.ErrNotFound):
		return status.Error(codes.NotFound, err.Error())
	case errors.Is(err, nikostore.ErrInvalid):
		return status.Error(codes.InvalidArgument, err.Error())
	case errors.Is(err, nikostore.ErrRefused):
		return status.Error(codes.FailedPrecondition, err.Error())
	case errors.Is(err, nikostore.ErrUnsupported):
		return status.Error(codes.Unimplemented, err.Error())
	}
	return status.Error(codes.Internal, err.Error())
}

func profileProto(p nikostore.Profile) *nikopb.Profile {
	return &nikopb.Profile{Id: p.ID, GroupId: p.Group, Order: p.Order, Type: p.Type, Name: p.Name, Address: p.Address,
		BeanJson: string(p.Bean)}
}

func groupProto(g nikostore.Group) *nikopb.Group {
	kind := nikopb.Group_BASIC
	if g.Type == nikostore.GroupSubscription {
		kind = nikopb.Group_SUBSCRIPTION
	}
	return &nikopb.Group{Id: g.ID, Order: g.Order, Name: g.Name, Ungrouped: g.Ungrouped, Type: kind,
		Profiles: int32(g.Profiles)}
}

func (s *server) Watch(_ *nikopb.WatchRequest, stream grpc.ServerStreamingServer[nikopb.Change]) error {
	u, err := s.users.user(stream.Context())
	if err != nil {
		return err
	}
	w, revision := u.feed.subscribe()
	defer u.feed.unsubscribe(w)
	if err = stream.Send(&nikopb.Change{Revision: revision, Reset_: true}); err != nil {
		return err
	}
	for {
		select {
		case <-stream.Context().Done():
			return status.FromContextError(stream.Context().Err()).Err()
		case <-s.users.done:
			return status.Error(codes.Unavailable, "the daemon stops")
		case change := <-w.changes:
			if err = stream.Send(u.feed.next(w, change)); err != nil {
				return err
			}
		}
	}
}

func (s *server) ListGroups(ctx context.Context, _ *nikopb.Empty) (*nikopb.GroupList, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	list := &nikopb.GroupList{}
	for _, g := range u.store.Groups() {
		list.Groups = append(list.Groups, groupProto(g))
	}
	return list, nil
}

func (s *server) ListProfiles(ctx context.Context, request *nikopb.ListProfilesRequest) (*nikopb.ProfileList, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	rows, err := u.store.Profiles(request.GroupId)
	if err != nil {
		return nil, storeError(err)
	}
	list := &nikopb.ProfileList{}
	for _, row := range rows {
		list.Profiles = append(list.Profiles, profileProto(row))
	}
	return list, nil
}

func (s *server) GetProfile(ctx context.Context, request *nikopb.ProfileId) (*nikopb.Profile, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	p, err := u.store.Profile(request.Id)
	if err != nil {
		return nil, storeError(err)
	}
	return profileProto(p), nil
}

func (s *server) SaveProfile(ctx context.Context, request *nikopb.Profile) (*nikopb.Profile, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	saved, err := u.store.SaveProfile(nikostore.Profile{ID: request.Id, Group: request.GroupId, Type: request.Type,
		Bean: []byte(request.BeanJson)})
	if err != nil {
		return nil, storeError(err)
	}
	return profileProto(saved), nil
}

func (s *server) DeleteProfiles(ctx context.Context, request *nikopb.ProfileIds) (*nikopb.Empty, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	if _, err = u.store.DeleteProfiles(request.Ids); err != nil {
		return nil, storeError(err)
	}
	return &nikopb.Empty{}, nil
}

func (s *server) MoveProfiles(ctx context.Context, request *nikopb.MoveRequest) (*nikopb.Empty, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	if err = u.store.MoveProfiles(request.Ids, request.GroupId, request.Before); err != nil {
		return nil, storeError(err)
	}
	return &nikopb.Empty{}, nil
}

func (s *server) SaveGroup(ctx context.Context, request *nikopb.Group) (*nikopb.Group, error) {
	if request.Type != nikopb.Group_BASIC {
		return nil, status.Error(codes.Unimplemented, "a subscription's group comes with subscriptions")
	}
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	saved, err := u.store.SaveGroup(nikostore.Group{ID: request.Id, Name: request.Name})
	if err != nil {
		return nil, storeError(err)
	}
	return groupProto(saved), nil
}

func (s *server) DeleteGroup(ctx context.Context, request *nikopb.GroupId) (*nikopb.Empty, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	if err = u.store.DeleteGroup(request.Id); err != nil {
		return nil, storeError(err)
	}
	return &nikopb.Empty{}, nil
}

// The import (D-ADD-03, -04, -09): what the app's import reads of a text (nikofmt.ParseText), its parsers' settings
// the app's defaults until the desktop has settings of its own.

const (
	// maxInput is the most an input may be: the gRPC message's own limit
	maxInput = 4 << 20
	// maxImport is the most profiles one import adds
	maxImport = 10000
)

// errOverImport: an input of more profiles than one import adds, refused by Recognize as by Import
var errOverImport = status.Error(codes.InvalidArgument, "over "+strconv.Itoa(maxImport)+" profiles in one import")

// inputText is the text of an input: its text, else its file's content.
func inputText(input *nikopb.ImportInput) (string, error) {
	if input == nil {
		return "", nil
	}
	if len(input.Text)+len(input.File) > maxInput {
		return "", status.Error(codes.InvalidArgument, "the input is over "+strconv.Itoa(maxInput>>20)+" MiB")
	}
	if input.Text != "" {
		return input.Text, nil
	}
	return nikofmt.DecodeUTF8(input.File), nil
}

func (s *server) Recognize(_ context.Context, input *nikopb.ImportInput) (*nikopb.ImportPreview, error) {
	text, err := inputText(input)
	if err != nil {
		return nil, err
	}
	beans, err := nikofmt.ParseText(text, nikofmt.DefaultSettings)
	var found *nikofmt.SubscriptionFound
	if errors.As(err, &found) {
		return &nikopb.ImportPreview{Kind: nikopb.ImportPreview_SUBSCRIPTION, Subscription: found.Link}, nil
	}
	if err != nil {
		return nil, status.Error(codes.Internal, err.Error())
	}
	if len(beans) > maxImport {
		return nil, errOverImport
	}
	preview := &nikopb.ImportPreview{}
	for _, bean := range beans {
		preview.Kind = nikopb.ImportPreview_PROFILES
		preview.Profiles = append(preview.Profiles, &nikopb.Profile{Type: nikofmt.TypeName(bean),
			Name: nikofmt.DisplayName(bean), Address: nikofmt.DisplayAddress(bean)})
	}
	return preview, nil
}

func (s *server) Import(request *nikopb.ImportRequest, stream grpc.ServerStreamingServer[nikopb.TaskProgress]) error {
	u, err := s.users.user(stream.Context())
	if err != nil {
		return err
	}
	text, err := inputText(request.Input)
	if err != nil {
		return err
	}
	task := "import-" + strconv.FormatUint(s.users.tasks.Add(1), 10)
	// the task goes on without the stream: its result comes in Watch too
	result := make(chan *nikopb.TaskProgress, 1)
	go func() { result <- importText(u, task, text, request.GroupId) }()
	select {
	case progress := <-result:
		return stream.Send(progress)
	case <-stream.Context().Done():
		return status.FromContextError(stream.Context().Err()).Err()
	}
}

// importText runs an import to its end and tells its result to the user's watchers.
func importText(u *user, task, text string, group int64) *nikopb.TaskProgress {
	result := &nikopb.TaskProgress{TaskId: task, Done: true}
	fail := func(err error) {
		st := status.Convert(err)
		result.Code, result.Error = int32(st.Code()), st.Message()
	}
	beans, err := nikofmt.ParseText(text, nikofmt.DefaultSettings)
	var found *nikofmt.SubscriptionFound
	switch {
	case errors.As(err, &found):
		fail(status.Error(codes.Unimplemented, "a subscription's link: importing subscriptions comes later"))
	case err != nil:
		fail(status.Error(codes.Internal, err.Error()))
	case len(beans) == 0:
		fail(status.Error(codes.InvalidArgument, "no link this daemon reads"))
	case len(beans) > maxImport:
		fail(errOverImport)
	default:
		added, err := u.store.AddProfiles(group, beans)
		if err != nil {
			fail(storeError(err))
			break
		}
		for _, p := range added {
			result.Ids = append(result.Ids, p.ID)
		}
		result.Line = "profiles added: " + strconv.Itoa(len(added))
	}
	u.feed.publish(&nikopb.Change{Kind: nikopb.Change_TASK, Task: proto.Clone(result).(*nikopb.TaskProgress)})
	return result
}

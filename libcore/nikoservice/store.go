package nikoservice

import (
	"archive/zip"
	"bytes"
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io"
	"path/filepath"
	"strconv"
	"strings"
	"sync"
	"sync/atomic"

	"libcore/nikoconf"
	"libcore/nikofmt"
	"libcore/nikopb"
	"libcore/nikoray"
	"libcore/nikostore"
	"libcore/nikosub"

	"google.golang.org/grpc"
	"google.golang.org/grpc/codes"
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
	// userDir maps a verified caller's userID to its daemon user directory; nil falls back to <root>/users/<sha256(id)>
	userDir func(string) string
	// sealer: nil, the platform's
	sealer nikostore.Sealer
	// done ends the watchers with the daemon; nil: never
	done <-chan struct{}
	// ctx ends with done, cancel ends it
	ctx    context.Context
	cancel context.CancelFunc

	// onNodesChanged is called whenever a store opens or its raycontrol nodes change (BG-10 log redaction).
	onNodesChanged func()

	mu sync.Mutex
	// open are the stores opened, by their directory
	open  map[string]*user
	tasks atomic.Uint64
}

type user struct {
	store *nikostore.Store
	feed  *feed
	// updates are its subscriptions' (subscriptions.go)
	updates *updates
	// ruleSets are its rule-sets' downloads (rules.go)
	ruleSets *ruleSetUpdates
	// nodes are its raycontrol nodes' updates (nodes.go)
	nodes *nodeUpdates
}

func newUsers(root string, caller func(context.Context) (string, error), userDir func(string) string, done <-chan struct{}) *users {
	u := &users{root: root, caller: caller, userDir: userDir, done: done, open: map[string]*user{}}
	u.ctx, u.cancel = context.WithCancel(context.Background())
	if done != nil {
		go func() {
			<-done
			u.cancel()
		}()
	}
	return u
}

// context is the daemon's lifetime as a context: what a task runs in, which goes on without the call that asked.
func (u *users) context() context.Context { return u.ctx }

// opened are the stores open now.
func (u *users) opened() []*user {
	u.mu.Lock()
	defer u.mu.Unlock()
	var out []*user
	for _, opened := range u.open {
		out = append(out, opened)
	}
	return out
}

// serves: whether the caller has a store here, without opening it.
func (u *users) serves(ctx context.Context) bool {
	if u == nil || u.caller == nil {
		return false
	}
	id, err := u.caller(ctx)
	return err == nil && u.dirFor(id) != ""
}

func (u *users) dirFor(id string) string {
	if id == "" {
		return ""
	}
	if u.userDir != nil {
		return u.userDir(id)
	}
	digest := sha256.Sum256([]byte(id))
	return filepath.Join(u.root, "users", hex.EncodeToString(digest[:]))
}

// user is the caller's store, opened if it is not yet.
func (u *users) user(ctx context.Context) (*user, error) {
	if u == nil || u.caller == nil {
		return nil, status.Error(codes.Unimplemented, "no store")
	}
	id, err := u.caller(ctx)
	if err != nil {
		if status.Code(err) == codes.Unknown {
			return nil, status.Error(codes.Unauthenticated, err.Error())
		}
		return nil, err
	}
	dir := u.dirFor(id)
	if dir == "" {
		return nil, status.Error(codes.Unauthenticated, "empty caller identity")
	}
	return u.openDir(filepath.Join(dir, "niko"))
}

// userByID opens the store for a verified ownerUserID (e.g. boxdd restore).
func (u *users) userByID(id string) (*user, error) {
	if u == nil || id == "" {
		return nil, status.Error(codes.Unauthenticated, "empty user identity")
	}
	dir := u.dirFor(id)
	if dir == "" {
		return nil, status.Error(codes.Unauthenticated, "empty user directory")
	}
	return u.openDir(filepath.Join(dir, "niko"))
}

// openDir is the store of a user's directory, opened if it is not yet.
func (u *users) openDir(dir string) (*user, error) {
	u.mu.Lock()
	if opened := u.open[dir]; opened != nil {
		u.mu.Unlock()
		return opened, nil
	}
	f := newFeed()
	store, err := nikostore.Open(dir, nikostore.Options{
		Sealer: u.sealer,
		OnChange: func(c nikostore.Change) {
			f.publish(&nikopb.Change{Kind: changeKind(c.Kind), Ids: c.IDs})
		},
	})
	if errors.Is(err, nikostore.ErrNewerSchema) || errors.Is(err, nikostore.ErrSealerMismatch) {
		u.mu.Unlock()
		return nil, status.Error(codes.FailedPrecondition, err.Error())
	}
	if err != nil {
		u.mu.Unlock()
		return nil, status.Error(codes.Internal, "the store: "+err.Error())
	}
	opened := &user{store: store, feed: f, updates: newUpdates(), ruleSets: newRuleSetUpdates(), nodes: newNodeUpdates()}
	u.open[dir] = opened
	notify := u.onNodesChanged
	u.mu.Unlock()
	if notify != nil {
		notify()
	}
	return opened, nil
}

func changeKind(kind nikostore.Kind) nikopb.Change_Kind {
	switch kind {
	case nikostore.KindProfiles:
		return nikopb.Change_PROFILES
	case nikostore.KindGroups:
		return nikopb.Change_GROUPS
	case nikostore.KindSelected:
		return nikopb.Change_SELECTED
	case nikostore.KindSettings:
		return nikopb.Change_SETTINGS
	case nikostore.KindRules:
		return nikopb.Change_RULES
	case nikostore.KindNodes:
		return nikopb.Change_NODES
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
		BeanJson: string(p.Bean), Kind: p.Kind, Transport: p.Transport}
}

func (s *server) groupProto(u *user, g nikostore.Group) *nikopb.Group {
	kind := nikopb.Group_BASIC
	if g.Type == nikostore.GroupSubscription {
		kind = nikopb.Group_SUBSCRIPTION
	}
	return &nikopb.Group{Id: g.ID, Order: g.Order, Name: g.Name, Ungrouped: g.Ungrouped, Type: kind,
		Profiles: int32(g.Profiles), Subscription: subscriptionProto(g.Subscription, u.updates.isRunning(g.ID)),
		NodeId: g.Node, IsSelector: g.IsSelector, AutoSelect: g.AutoSelect,
		FrontProxy: g.FrontProxy, LandingProxy: g.LandingProxy, SortOrder: int32(g.SortOrder)}
}

// withNode is a profile as NikoService gives it, the node whose row it is named: the node of its group.
func withNode(u *user, p *nikopb.Profile) *nikopb.Profile {
	p.NodeId = nodeOfGroup(u, p.GroupId)
	return p
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
		list.Groups = append(list.Groups, s.groupProto(u, g))
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
	nodes := map[int64]string{}
	for _, g := range u.store.Groups() {
		nodes[g.ID] = g.Node
	}
	list := &nikopb.ProfileList{}
	for _, row := range rows {
		p := profileProto(row)
		p.NodeId = nodes[p.GroupId]
		list.Profiles = append(list.Profiles, p)
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
	return withNode(u, profileProto(p)), nil
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
	return withNode(u, profileProto(saved)), nil
}

// CheckProfile validates a profile's type and bean_json via DecodeBean + nikoconf dry-run without saving it.
// Loads the caller's existing profiles and raycontrol nodes when available so chain hops and dnsauth profiles validate accurately.
func (s *server) CheckProfile(ctx context.Context, request *nikopb.Profile) (*nikopb.CheckProfileResult, error) {
	bean, err := nikofmt.DecodeBean(request.Type, []byte(request.BeanJson))
	if err != nil {
		return &nikopb.CheckProfileResult{Ok: false, Error: err.Error()}, nil
	}
	kind, transport := nikofmt.Kind(bean)
	const syntheticID int64 = -1
	in := nikoconf.Input{
		Profile:  syntheticID,
		Profiles: []nikoconf.Profile{{ID: syntheticID, Group: 1, Type: request.Type, Bean: bean}},
		Groups:   []nikoconf.Group{{ID: 1}},
		Settings: nikoconf.DesktopSettings(),
		Platform: connectPlatform(),
		ForTest:  true,
		UserMode: s.userMode,
	}
	if s.users != nil {
		if u, uErr := s.users.user(ctx); uErr == nil {
			for _, kept := range u.store.Nodes() {
				if !kept.Unreadable {
					in.Nodes = append(in.Nodes, *kept.Node)
				}
			}
			if rows, rErr := u.store.Profiles(0); rErr == nil {
				for _, row := range rows {
					if full, pErr := u.store.Profile(row.ID); pErr == nil {
						if bp, bErr := buildProfile(full); bErr == nil {
							in.Profiles = append(in.Profiles, bp)
						}
					}
				}
			}
		}
	}
	res, err := nikoconf.Build(in)
	if err != nil {
		return &nikopb.CheckProfileResult{
			Ok:        false,
			Error:     err.Error(),
			Warnings:  res.Warnings,
			Kind:      kind,
			Transport: transport,
		}, nil
	}
	return &nikopb.CheckProfileResult{
		Ok:        true,
		Warnings:  res.Warnings,
		Kind:      kind,
		Transport: transport,
	}, nil
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

func (s *server) Select(ctx context.Context, request *nikopb.ProfileId) (*nikopb.Empty, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	if err = u.store.Select(request.Id); err != nil {
		return nil, storeError(err)
	}
	return &nikopb.Empty{}, nil
}

func (s *server) GetSelected(ctx context.Context, _ *nikopb.Empty) (*nikopb.ProfileId, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	return &nikopb.ProfileId{Id: u.store.Selected()}, nil
}

func (s *server) ShareProfile(ctx context.Context, request *nikopb.ProfileId) (*nikopb.ShareLink, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	p, err := u.store.ProfileToShare(request.Id)
	if err != nil {
		return nil, storeError(err)
	}
	bean, err := nikofmt.DecodeBean(p.Type, p.Bean)
	if err != nil {
		return nil, status.Error(codes.Internal, "the profile's bean: "+err.Error())
	}
	link, err := nikofmt.ShareLink(bean)
	if err != nil {
		return nil, status.Error(codes.Unimplemented, "no link for this profile: "+err.Error())
	}
	return &nikopb.ShareLink{Link: link}, nil
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
	switch request.Type {
	case nikopb.Group_SUBSCRIPTION:
		return s.saveSubscriptionGroup(ctx, request)
	case nikopb.Group_BASIC:
	default:
		return nil, status.Error(codes.InvalidArgument, "no such type of group")
	}
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	saved, err := u.store.SaveGroup(nikostore.Group{
		ID: request.Id, Name: request.Name,
		IsSelector: request.IsSelector, AutoSelect: request.AutoSelect,
		FrontProxy: request.FrontProxy, LandingProxy: request.LandingProxy,
		SortOrder: int(request.SortOrder),
	})
	if err != nil {
		return nil, storeError(err)
	}
	return s.groupProto(u, saved), nil
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
// the app's defaults until the desktop has settings of its own; a subscription's link imports the subscription
// (subscriptions.go).

const (
	// maxInput is the most an input may be: the gRPC message's own limit
	maxInput = 4 << 20
	// maxImport is the most profiles one import adds
	maxImport = 10000
)

// errOverImport: an input of more profiles than one import adds, refused by Recognize as by Import
var errOverImport = status.Error(codes.InvalidArgument, "over "+strconv.Itoa(maxImport)+" profiles in one import")

// inputText is the text of an input: its text, else its file's content (expanding bounded .zip archives).
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
	if len(input.File) >= 4 && bytes.Equal(input.File[:4], []byte{'P', 'K', 3, 4}) ||
		strings.HasSuffix(strings.ToLower(strings.TrimSpace(input.FileName)), ".zip") {
		return extractZipText(input.File)
	}
	return nikofmt.DecodeUTF8(input.File), nil
}

const maxZipEntries = 32

func extractZipText(raw []byte) (string, error) {
	zr, err := zip.NewReader(bytes.NewReader(raw), int64(len(raw)))
	if err != nil {
		return "", status.Error(codes.InvalidArgument, "invalid zip archive: "+err.Error())
	}
	if len(zr.File) > maxZipEntries {
		return "", status.Error(codes.InvalidArgument, "zip archive has too many files")
	}
	var parts []string
	var total int64
	for _, f := range zr.File {
		if f.FileInfo().IsDir() {
			continue
		}
		lower := strings.ToLower(filepath.Base(f.Name))
		if strings.HasPrefix(lower, ".") || strings.Contains(f.Name, "__MACOSX") {
			continue
		}
		rc, err := f.Open()
		if err != nil {
			return "", status.Error(codes.InvalidArgument, "read zip entry "+f.Name+": "+err.Error())
		}
		remaining := int64(maxInput) - total
		if remaining <= 0 {
			rc.Close()
			return "", status.Error(codes.InvalidArgument, "uncompressed zip content exceeds 4 MiB")
		}
		data, err := io.ReadAll(io.LimitReader(rc, remaining+1))
		rc.Close()
		if err != nil {
			return "", status.Error(codes.InvalidArgument, "read zip entry "+f.Name+": "+err.Error())
		}
		total += int64(len(data))
		if total > maxInput {
			return "", status.Error(codes.InvalidArgument, "uncompressed zip content exceeds 4 MiB")
		}
		if text := strings.TrimSpace(nikofmt.DecodeUTF8(data)); text != "" {
			parts = append(parts, text)
		}
	}
	return strings.Join(parts, "\n"), nil
}

func (s *server) Recognize(ctx context.Context, input *nikopb.ImportInput) (*nikopb.ImportPreview, error) {
	text, err := inputText(input)
	if err != nil {
		return nil, err
	}
	if nikoray.Matches(text) {
		node, err := s.nodePreview(ctx, text)
		if err != nil {
			return nil, err
		}
		return &nikopb.ImportPreview{Kind: nikopb.ImportPreview_NODE, Node: node}, nil
	}
	if link := nikofmt.KtTrim(text); !strings.ContainsAny(link, "\n\r") {
		if _, ok := nikosub.ParseDeepLink(link); ok {
			return &nikopb.ImportPreview{Kind: nikopb.ImportPreview_SUBSCRIPTION, Subscription: link}, nil
		}
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
	return runTask(stream, func(progress func(*nikopb.TaskProgress)) *nikopb.TaskProgress {
		return s.importText(u, task, text, request.GroupId, request.Pin, progress)
	})
}

// importText runs an import to its end and tells its result to the user's watchers; a subscription's link is the
// import of that subscription (importSubscription).
func (s *server) importText(u *user, task, text string, group int64, pin string, progress func(*nikopb.TaskProgress)) *nikopb.TaskProgress {
	result := &nikopb.TaskProgress{TaskId: task, Done: true}
	fail := func(err error) {
		st := status.Convert(err)
		result.Code, result.Error = int32(st.Code()), st.Message()
	}
	// a raycontrol node's invite or device package (nodes.go)
	if nikoray.Matches(text) {
		return s.importNode(u, task, text, pin, progress)
	}
	if link := nikofmt.KtTrim(text); !strings.ContainsAny(link, "\n\r") {
		if deep, ok := nikosub.ParseDeepLink(link); ok {
			return s.importSubscription(u, task, deep, progress)
		}
	}
	beans, err := nikofmt.ParseText(text, nikofmt.DefaultSettings)
	var found *nikofmt.SubscriptionFound
	switch {
	case errors.As(err, &found):
		if link, ok := subscriptionOfLink(found.Link); ok {
			return s.importSubscription(u, task, link, progress)
		}
		fail(status.Error(codes.Unimplemented, "a whole group shared as sn://subscription: not read by this daemon"))
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

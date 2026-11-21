package nikoservice

import (
	"context"
	"crypto/ed25519"
	"encoding/hex"
	"errors"
	"net"
	"time"

	"libcore/nikoconf"
	"libcore/nikofmt"
	"libcore/nikopb"
	"libcore/nikostore"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// Connect (BG-01, D-MAIN-04): the config of a stored profile built here as the app builds it (nikoconf.Build), and
// the core started with it by the daemon (boxdd's Host.StartService), as DesktopService.StartService starts a config
// the window gives: the same owner's check, the config kept for the daemon's next start.

func (s *server) Connect(ctx context.Context, request *nikopb.ConnectRequest) (*nikopb.Empty, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	in, err := connectInput(u.store, request.ProfileId)
	if err != nil {
		return nil, err
	}
	// no rules yet, so no warnings either
	result, err := nikoconf.Build(in)
	if errors.Is(err, nikoconf.ErrNeedsPlugin) {
		return nil, status.Error(codes.Unimplemented, err.Error())
	}
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, "the profile's config: "+err.Error())
	}
	if s.start == nil {
		return nil, status.Error(codes.Unimplemented, "no daemon to start the core")
	}
	if err = s.start(ctx, string(result.Config)); err != nil {
		return nil, err
	}
	return &nikopb.Empty{}, nil
}

// connectSettings are the build's settings until the desktop has its own (slice 13): the app's defaults, in proxy mode
// (the desktop's TUN comes with slice 8). The local proxy is then the app's, 127.0.0.1:2080, and with no password of
// the user's it asks for no login, as the app's in proxy mode.
func connectSettings() nikoconf.Settings {
	settings := nikoconf.DefaultSettings()
	settings.ServiceMode = nikoconf.ModeProxy
	return settings
}

// connectInput is the build's input for a profile (0: the selected one): the profile, its group and the profiles the
// build takes from it.
func connectInput(store *nikostore.Store, id int64) (nikoconf.Input, error) {
	if id == 0 {
		if id = store.Selected(); id == 0 {
			return nikoconf.Input{}, status.Error(codes.FailedPrecondition, "no profile is selected")
		}
	}
	stored, err := store.Profile(id)
	if err != nil {
		return nikoconf.Input{}, storeError(err)
	}
	profile, err := buildProfile(stored)
	if err != nil {
		return nikoconf.Input{}, err
	}
	in := nikoconf.Input{Profile: id, Profiles: []nikoconf.Profile{profile}, Settings: connectSettings(),
		Device: desktopDevice{}}
	var group *nikoconf.Group
	for _, g := range store.Groups() {
		if g.ID == profile.Group {
			group = buildGroup(g)
			in.Groups = append(in.Groups, *group)
		}
	}
	if group == nil {
		return in, nil
	}
	// a selector's or urltest's members, and the group's front and landing hops
	var more []int64
	if group.IsSelector || group.AutoSelect {
		rows, err := store.Profiles(group.ID)
		if err != nil {
			return nikoconf.Input{}, storeError(err)
		}
		for _, row := range rows {
			more = append(more, row.ID)
		}
	}
	more = append(more, group.FrontProxy, group.LandingProxy)
	taken := map[int64]bool{id: true}
	for _, other := range more {
		if other <= 0 || taken[other] {
			continue
		}
		taken[other] = true
		stored, err := store.Profile(other)
		if errors.Is(err, nikostore.ErrNotFound) {
			// a hop that is no more: the app's build leaves it out
			continue
		}
		if err != nil {
			return nikoconf.Input{}, storeError(err)
		}
		p, err := buildProfile(stored)
		if err != nil {
			return nikoconf.Input{}, err
		}
		in.Profiles = append(in.Profiles, p)
	}
	return in, nil
}

// buildProfile is a stored profile as the build reads it.
func buildProfile(p nikostore.Profile) (nikoconf.Profile, error) {
	bean, err := nikofmt.DecodeBean(p.Type, p.Bean)
	if err != nil {
		return nikoconf.Profile{}, status.Error(codes.Internal, "the profile's bean: "+err.Error())
	}
	return nikoconf.Profile{ID: p.ID, Group: p.Group, Order: p.Order, Type: p.Type, Bean: bean}, nil
}

// buildGroup is a stored group as the build reads it (ProxyGroup): a plain group, its profiles in their order, no
// selector and no hops, as the store has no such settings yet.
func buildGroup(g nikostore.Group) *nikoconf.Group {
	group := &nikoconf.Group{ID: g.ID, UserOrder: g.Order, Ungrouped: g.Ungrouped, Type: g.Type, FrontProxy: -1,
		LandingProxy: -1}
	if g.Name != "" {
		group.Name = &g.Name
	}
	return group
}

// desktopDevice is what the build asks of the device (nikoconf.Device), as this computer answers it: free ports from
// the system and its clock; no app's uid, no Private DNS and no protect server, which are the phone's.
type desktopDevice struct{}

// Port is a free local port, as the app's mkPort: one the system gives, let go at once; 0 where it gives none.
func (desktopDevice) Port() int {
	listener, err := net.Listen("tcp", "127.0.0.1:0")
	if err != nil {
		return 0
	}
	defer listener.Close()
	return listener.Addr().(*net.TCPAddr).Port
}

func (desktopDevice) UID(string) (int, bool)               { return 0, false }
func (desktopDevice) StrictPrivateDNSHost() (string, bool) { return "", false }
func (desktopDevice) ProtectPathReady() bool               { return false }

// YggdrasilKey is a one-time node key, as libcore's YggdrasilGenerateKey: 128 hex digits.
func (desktopDevice) YggdrasilKey() string {
	_, key, err := ed25519.GenerateKey(nil)
	if err != nil {
		return ""
	}
	return hex.EncodeToString(key)
}

// YggKeyRunning: no mesh node of a device key runs here yet.
func (desktopDevice) YggKeyRunning(string) bool { return false }
func (desktopDevice) Now() int64                { return time.Now().Unix() }

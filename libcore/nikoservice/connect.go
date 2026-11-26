package nikoservice

import (
	"context"
	"crypto/ed25519"
	"crypto/rand"
	"encoding/hex"
	"errors"
	"net"
	"runtime"
	"time"

	"libcore/nikoconf"
	"libcore/nikofmt"
	"libcore/nikopb"
	"libcore/nikostore"
	"libcore/ruleset"

	"github.com/sagernet/sing-box/experimental/boxdd"

	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
)

// Connect (BG-01, D-MAIN-04): the config of a stored profile built here as the app builds it (nikoconf.Build), for
// this computer with its settings (settings.go) and in the mode asked, else theirs (D-NAV-02), and the core started
// with it by the daemon (boxdd's Host.StartService), as DesktopService.StartService starts a config the window gives:
// the same owner's check, the config kept for the daemon's next start.

func (s *server) Connect(ctx context.Context, request *nikopb.ConnectRequest) (*nikopb.Empty, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	settings, err := u.store.Settings()
	if err != nil {
		return nil, storeError(err)
	}
	if request.Mode != nil {
		switch *request.Mode {
		case nikopb.ConnectRequest_PROXY:
			settings.ServiceMode = nikoconf.ModeProxy
		case nikopb.ConnectRequest_VPN:
			settings.ServiceMode = nikoconf.ModeVPN
		default:
			return nil, status.Errorf(codes.InvalidArgument, "no mode %d", *request.Mode)
		}
	}
	if s.userMode && settings.ServiceMode == nikoconf.ModeVPN {
		return nil, status.Error(codes.FailedPrecondition, "SERVICE_REQUIRED: VPN mode requires the Niko system service")
	}
	in, err := connectInput(u.store, request.ProfileId, connectSettings(settings))
	if err != nil {
		return nil, err
	}
	in.UserMode = s.userMode
	result, err := nikoconf.Build(in)
	if errors.Is(err, nikoconf.ErrNeedsService) {
		return nil, status.Error(codes.FailedPrecondition, "SERVICE_REQUIRED: "+err.Error())
	}
	if errors.Is(err, nikoconf.ErrNeedsPlugin) {
		return nil, status.Error(codes.Unimplemented, err.Error())
	}
	if err != nil {
		return nil, status.Error(codes.InvalidArgument, "the profile's config: "+err.Error())
	}
	if s.start == nil {
		return nil, status.Error(codes.Unimplemented, "no daemon to start the core")
	}
	if s.canStart != nil {
		if err = s.canStart(ctx); err != nil {
			return nil, err
		}
	}
	// the nodes' device keys into the core by their references, which the config names
	s.deviceKeys(in.Nodes)
	if err = s.start(ctx, string(result.Config), systemProxy(result.SystemProxy)); err != nil {
		return nil, err
	}
	// the enabled rules' lists missing or stale fetched now (the config left out those not here: the core takes them
	// when it starts again)
	s.updateRuleSetsSoon(u, time.Now())
	return &nikopb.Empty{}, nil
}

// systemProxy is the build's system proxy as the daemon sets it for its owner (the fork's Host.StartService): the
// local proxy of proxy mode, for the user who connected, not for SYSTEM or root the service runs as.
func systemProxy(p *nikoconf.SystemProxy) *boxdd.SystemProxy {
	if p == nil {
		return nil
	}
	return &boxdd.SystemProxy{Server: p.Server, Port: uint16(p.Port)}
}

// connectSettings are the build's settings of the computer's (nikostore's settings.json over nikoconf.DesktopSettings):
// in proxy mode by default the local proxy 127.0.0.1:2080 with no login and not the system's proxy, as the app's
// in proxy mode; VPN mode is the TUN of a computer's (nikoconf's desktop.go), which the core opens only with the
// daemon's privileges. The app's own login to the local proxy, which the build adds beside the user's (in VPN mode
// with a password of the user's), is a new one each time: nothing of the computer's uses it, and it is never "".
func connectSettings(settings nikoconf.Settings) nikoconf.Settings {
	settings.Secrets.LocalProxyPassword = rand.Text()
	return settings
}

// connectPlatform is the computer the config is built for: nikod is built for Windows and Linux.
func connectPlatform() string {
	if runtime.GOOS == "windows" {
		return nikoconf.PlatformWindows
	}
	return nikoconf.PlatformLinux
}

// connectInput is the build's input for a profile (0: the selected one) with these settings: the profile, its group
// and the profiles the build takes from it.
func connectInput(store *nikostore.Store, id int64, settings nikoconf.Settings) (nikoconf.Input, error) {
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
	in := nikoconf.Input{Profile: id, Profiles: []nikoconf.Profile{profile}, Settings: settings,
		Platform: connectPlatform(), Device: desktopDevice{}, Rules: store.Rules(),
		RuleSets: ruleset.Files{Dir: ruleSetsDir(store)}}
	// the raycontrol nodes, their rows by the profiles' ids; a row of one sealed elsewhere has no key to knock with
	for _, kept := range store.Nodes() {
		if !kept.Unreadable {
			in.Nodes = append(in.Nodes, *kept.Node)
		} else if kept.Node.GroupID == stored.Group {
			return nikoconf.Input{}, status.Error(codes.FailedPrecondition,
				"the profile's raycontrol node was sealed on another machine: import a new invite")
		}
	}
	var group *nikoconf.Group
	for _, g := range store.Groups() {
		if g.ID == profile.Group {
			group = buildGroup(g)
			in.Groups = append(in.Groups, *group)
		}
	}
	// a selector's or urltest's members, and the group's front and landing hops
	var more []int64
	if group != nil && (group.IsSelector || group.AutoSelect) {
		rows, err := store.Profiles(group.ID)
		if err != nil {
			return nikoconf.Input{}, storeError(err)
		}
		for _, row := range rows {
			more = append(more, row.ID)
		}
	}
	if group != nil {
		more = append(more, group.FrontProxy, group.LandingProxy)
	}
	// the profiles the enabled rules send their traffic to
	for _, r := range in.Rules {
		if r.Enabled && r.Outbound > 0 {
			more = append(more, r.Outbound)
		}
	}
	taken := map[int64]bool{id: true}
	for _, other := range more {
		if other <= 0 || taken[other] {
			continue
		}
		taken[other] = true
		stored, err := store.Profile(other)
		if errors.Is(err, nikostore.ErrNotFound) {
			// a hop or a rule's profile that is no more: the app's build leaves it out (a rule's with a warning)
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

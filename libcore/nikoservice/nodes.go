package nikoservice

import (
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"math/rand/v2"
	"net"
	"net/netip"
	"runtime"
	"strconv"
	"strings"
	"sync"
	"syscall"
	"time"

	"libcore/nikofmt"
	"libcore/nikopb"
	"libcore/nikoray"
	"libcore/nikostore"
	"libcore/rayapi"
	"libcore/rayclient"
	"libcore/raynames"
	"libcore/raysub"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/protocol/knock"
	M "github.com/sagernet/sing/common/metadata"
	"github.com/sagernet/sing/service"

	mDNS "github.com/miekg/dns"
	"google.golang.org/grpc/codes"
	"google.golang.org/grpc/status"
	"google.golang.org/protobuf/proto"
)

// raycontrol nodes (slice 11), the static part: an invite registers this computer with its node (rayapi.Register,
// the client key kept per invite before the first try, as RayApi.register), a device package is kept as it is; the
// node's subscription is fetched and verified (raysub.Fetch) into a group of its own (nikostore.ApplyNodeRows), on
// demand (Refresh) and on its own when the next update is due (RaySubscription's schedule: the next rotation, or
// three quarters of the subscription's life; a failure tried again after 15 minutes doubling to 6 hours). The
// daemon's own requests to a node go around the TUN while the core runs (bypass). The device keys go into the core
// by reference when it starts (Connect), never into the config; no call of NikoService gives a key.

const (
	// noAnswerRetry is the first retry after a fetch the node did not answer; it doubles to maxRetry
	noAnswerRetry = 15 * 60
	maxRetry      = 6 * 3600
	// clockOff is the skew a node's card warns of: SPA v2 fails at 30 s
	clockOff = 30
)

// nodeUpdates are a user's nodes' updates: those running, one at a time a node, their last errors, and when each
// was last tried on its own.
type nodeUpdates struct {
	mu      sync.Mutex
	running map[string]bool
	errors  map[string]string
	tried   map[string]time.Time
}

func newNodeUpdates() *nodeUpdates {
	return &nodeUpdates{running: map[string]bool{}, errors: map[string]string{}, tried: map[string]time.Time{}}
}

// minAutoRetry is the least time between two timed updates of a node: one that fails before its fetch (and so plans
// no retry of its own) is not tried every minute.
const minAutoRetry = 5 * time.Minute

// due marks a node's timed update tried now; false where one runs, or was tried within minAutoRetry.
func (n *nodeUpdates) due(id string, now time.Time) bool {
	n.mu.Lock()
	defer n.mu.Unlock()
	if n.running[id] || now.Sub(n.tried[id]) < minAutoRetry {
		return false
	}
	n.tried[id] = now
	return true
}

func (n *nodeUpdates) start(id string) bool {
	n.mu.Lock()
	defer n.mu.Unlock()
	if n.running[id] {
		return false
	}
	n.running[id] = true
	return true
}

func (n *nodeUpdates) done(id string, err error) {
	n.mu.Lock()
	defer n.mu.Unlock()
	delete(n.running, id)
	if err != nil {
		n.errors[id] = status.Convert(err).Message()
	} else {
		delete(n.errors, id)
	}
}

func (n *nodeUpdates) state(id string) (bool, string) {
	n.mu.Lock()
	defer n.mu.Unlock()
	return n.running[id], n.errors[id]
}

// setRaycontrolClient is the X-Raycontrol-Client header of this daemon's requests to nodes (client-min-v1 §1):
// niko/<version> (<system>; <arch>); none for a build without a version a node reads ("dev").
func setRaycontrolClient() {
	rayclient.Set("niko/" + Version + " (" + runtime.GOOS + "; " + runtime.GOARCH + ")")
}

// bypass is what the daemon's own requests to a node go by: each dial and each lookup asks currentBypass then, so
// that a core started (or stopped) during a registration or fetch of 90 s is taken into account by the next dial,
// as the app's protect() is applied to each socket.
func (s *server) bypass() (func(network, address string, conn syscall.RawConn) error, raynames.Lookup) {
	control := func(network, address string, conn syscall.RawConn) error {
		if current, _ := s.currentBypass(); current != nil {
			return current(network, address, conn)
		}
		return nil
	}
	lookup := func(ctx context.Context, name string) ([]netip.Addr, error) {
		if _, current := s.currentBypass(); current != nil {
			return current(ctx, name)
		}
		return net.DefaultResolver.LookupNetIP(ctx, "ip", name)
	}
	return control, lookup
}

// currentBypass is the bypass while the core runs: a socket bound to the interface the core's own connections take
// (its network manager's, as route.auto_detect_interface binds them), so that they go around the TUN; and the
// node's name looked up by the core's direct DNS, which is the system's under the TUN, not its FakeIP. Nils without
// a running core: the system's own route and resolver.
func (s *server) currentBypass() (func(network, address string, conn syscall.RawConn) error, raynames.Lookup) {
	if s.core == nil {
		return nil, nil
	}
	ctx, release := s.core()
	if ctx == nil {
		return nil, nil
	}
	defer release()
	var control func(network, address string, conn syscall.RawConn) error
	if manager := service.FromContext[adapter.NetworkManager](ctx); manager != nil {
		if fn := manager.AutoDetectInterfaceFunc(); fn != nil {
			control = fn
		}
	}
	var lookup raynames.Lookup
	if transports := service.FromContext[adapter.DNSTransportManager](ctx); transports != nil {
		if transport, ok := transports.Transport("dns-direct"); ok {
			lookup = func(ctx context.Context, name string) ([]netip.Addr, error) {
				return lookupWith(ctx, name, transport.Exchange)
			}
		}
	}
	return control, lookup
}

// lookupWith asks A and AAAA of name at once and takes what came by ctx's end (libcore's own, raynames_box.go).
func lookupWith(ctx context.Context, name string, exchange func(context.Context, *mDNS.Msg) (*mDNS.Msg, error)) ([]netip.Addr, error) {
	type answer struct {
		response *mDNS.Msg
		err      error
	}
	qtypes := []uint16{mDNS.TypeA, mDNS.TypeAAAA}
	answers := make(chan answer, len(qtypes))
	for _, qtype := range qtypes {
		go func() {
			request := new(mDNS.Msg)
			request.SetQuestion(mDNS.Fqdn(name), qtype)
			response, err := exchange(ctx, request)
			answers <- answer{response, err}
		}()
	}
	var addresses []netip.Addr
	var errs []error
	for range qtypes {
		select {
		case a := <-answers:
			if a.err != nil {
				errs = append(errs, a.err)
				continue
			}
			for _, record := range a.response.Answer {
				switch record := record.(type) {
				case *mDNS.A:
					addresses = append(addresses, M.AddrFromIP(record.A).Unmap())
				case *mDNS.AAAA:
					addresses = append(addresses, M.AddrFromIP(record.AAAA))
				}
			}
		case <-ctx.Done():
			if len(addresses) > 0 {
				return addresses, nil
			}
			return nil, ctx.Err()
		}
	}
	if len(addresses) == 0 && len(errs) > 0 {
		return nil, errors.Join(errs...)
	}
	return addresses, nil
}

func (s *server) clock() time.Time {
	if s.now != nil {
		return s.now()
	}
	return time.Now()
}

// spaOptions are the knock's options of an SPA's parameters and a key, as libcore's requests take them.
func spaOptions(spa map[string]any) (knock.SPAv2Options, error) {
	var options knock.SPAv2Options
	data, err := json.Marshal(spa)
	if err == nil {
		err = json.Unmarshal(data, &options)
	}
	return options, err
}

// inviteSPA is the SPA of an invite: its node's parameters and the invite's one-time key.
func inviteSPA(invite nikoray.Invite) map[string]any {
	p := invite.SPA
	o := map[string]any{"node_id": p.NodeID, "epoch_len_s": p.EpochLenS, "slot_len_s": p.SlotLenS,
		"port_key": p.PortKey, "port_base": p.PortBase, "port_range": p.PortRange, "len_min": p.LenMin,
		"len_max": p.LenMax, "device_key": invite.InviteKey}
	if p.V3() {
		o["versions"] = p.Versions
	}
	return o
}

// nodePreview is Recognize's NODE: what the invite or the package names, and whether it replaces a node kept
// (RayImport.replaceQuestion).
func (s *server) nodePreview(ctx context.Context, text string) (*nikopb.NodePreview, error) {
	preview := &nikopb.NodePreview{}
	var nodeID, deviceID string
	if nikoray.IsInvite(text) {
		invite, err := nikoray.ParseInvite(text)
		if err != nil {
			return nil, status.Error(codes.InvalidArgument, err.Error())
		}
		preview.Invite, preview.Host, preview.DnsName = true, invite.Host, invite.DNSName
		preview.PinRequired, preview.Expires = invite.PinRequired, invite.Expires
		nodeID = invite.SPA.NodeID
	} else {
		r, err := nikoray.ParseDevice(text)
		if err != nil {
			return nil, status.Error(codes.InvalidArgument, err.Error())
		}
		preview.Host, preview.DnsName = r.Host, r.DNSName
		nodeID, deviceID = r.SPA.NodeID, r.DeviceID
	}
	// a store only where the caller has one: Recognize needs none
	if s.users.serves(ctx) {
		if u, err := s.users.user(ctx); err == nil {
			for _, kept := range u.store.Nodes() {
				if kept.Node.ID() != nodeID {
					continue
				}
				sameHost := strings.EqualFold(kept.Node.Host, preview.Host)
				if !(sameHost && deviceID == kept.Node.DeviceID) {
					preview.Replaces = true
					if !sameHost {
						preview.KnownHost = kept.Node.Host
					}
				}
			}
		}
	}
	return preview, nil
}

// failTask sets a task's result to an error's code and message.
func failTask(result *nikopb.TaskProgress, err error) {
	st := status.Convert(err)
	result.Code, result.Error = int32(st.Code()), st.Message()
}

// importNode is the import of an invite or a device package (RayImport.import): the node kept, then its first
// subscription fetched.
func (s *server) importNode(u *user, task, text, pin string, progress func(*nikopb.TaskProgress)) *nikopb.TaskProgress {
	result := &nikopb.TaskProgress{TaskId: task, Done: true}
	defer func() {
		u.feed.publish(&nikopb.Change{Kind: nikopb.Change_TASK, Task: proto.Clone(result).(*nikopb.TaskProgress)})
	}()
	var node *nikoray.Node
	if nikoray.IsInvite(text) {
		registered, line, err := s.register(u, task, text, pin, result, progress)
		if err != nil {
			failTask(result, err)
			return result
		}
		if registered == nil {
			return result // the PIN is asked for
		}
		node, result.Line = registered, line
	} else {
		r, err := nikoray.ParseDevice(text)
		if err != nil {
			failTask(result, status.Error(codes.InvalidArgument, err.Error()))
			return result
		}
		// a bot's package (integrations-v1) is not kept: it would replace this device's own at the node
		if node, err = u.store.ImportNode(r, nil, ""); err != nil {
			failTask(result, nodeStoreError(err))
			return result
		}
		s.syncLogRedactions()
		result.Line = "node " + node.Host + " imported: " + strconv.Itoa(len(node.Services)) + " services"
	}
	result.NodeId = node.ID()
	if node.Subscription == nil {
		return result
	}
	if progress != nil {
		progress(&nikopb.TaskProgress{TaskId: task, Line: "fetching the subscription of " + node.Host, NodeId: node.ID()})
	}
	line, group, added, err := s.updateNode(s.users.context(), u, node.ID())
	result.GroupId, result.Ids = group, added
	if err != nil {
		// the node is kept: the error is the subscription's, tried again by the schedule
		failTask(result, err)
		result.Line += "\n" + result.Error
		return result
	}
	result.Line += "\n" + line
	return result
}

// nodeStoreError is a store's error of a node as its status.
func nodeStoreError(err error) error {
	var role *nikoray.IntegrationRoleError
	switch {
	case errors.As(err, &role):
		return status.Error(codes.FailedPrecondition, "the package is for an integration ("+role.Role+"), not for an app: it is not kept")
	case errors.Is(err, nikoray.ErrNoSubscription):
		return status.Error(codes.FailedPrecondition, "the package has no subscription, and the one kept for its node has: it is not kept")
	}
	return storeError(err)
}

// validPin: an invite's PIN is 6 digits.
func validPin(pin string) bool {
	if len(pin) != 6 {
		return false
	}
	for _, c := range pin {
		if c < '0' || c > '9' {
			return false
		}
	}
	return true
}

// register registers this computer with an invite (RayApi.register): the node kept with its client key, the line
// for the user; nil and no error where the PIN is asked for (result.pin).
func (s *server) register(u *user, task, text, pin string, result *nikopb.TaskProgress,
	progress func(*nikopb.TaskProgress)) (*nikoray.Node, string, error) {
	invite, err := nikoray.ParseInvite(text)
	if err != nil {
		return nil, "", status.Error(codes.InvalidArgument, err.Error())
	}
	now := s.clock().Unix()
	// the node checks the expiry too; this only saves a pointless try
	if invite.Expires >= 1 && invite.Expires < now-120 {
		return nil, "", status.Error(codes.FailedPrecondition, "the invite for "+invite.Host+" expired: ask for a new one")
	}
	pin = strings.TrimSpace(pin)
	if pin != "" && !validPin(pin) {
		return nil, "", status.Error(codes.InvalidArgument, "the PIN is 6 digits")
	}
	if invite.PinRequired && pin == "" {
		result.Pin = &nikopb.PinNeeded{Host: invite.Host, Left: -1}
		failTask(result, status.Error(codes.FailedPrecondition, "the invite needs its PIN"))
		return nil, "", nil
	}
	// the client key is made and kept before the first try: a retry after an answer that was lost sends the same,
	// and the node answers it the same package within its window, instead of "the invite is used"
	identity, err := u.store.PendingIdentity(invite.InviteID, now, func() (nikoray.Identity, error) {
		made, err := s.newIdentity()
		return nikoray.Identity{Cert: made.Cert, Key: made.Key, Pin: made.Pin}, err
	})
	if err != nil {
		return nil, "", storeError(err)
	}
	spa, err := spaOptions(inviteSPA(invite))
	if err != nil {
		return nil, "", status.Error(codes.InvalidArgument, "the invite's SPA: "+err.Error())
	}
	request := rayapi.Invite{Host: invite.Host, SPA: spa, InviteID: invite.InviteID, PinCode: pin,
		Identity: &rayapi.Identity{Cert: identity.Cert, Key: identity.Key, Pin: identity.Pin}}
	// RCINV3: by the node's name, its host the address to fall back to (names-v1 §5)
	if invite.DNSName != "" {
		request.Host, request.Addrs = invite.DNSName, invite.Addrs
	}
	request.API.Port, request.API.Pin = invite.API.Port, invite.API.Pin
	control, lookup := s.bypass()
	request.Lookup = lookup
	if progress != nil {
		progress(&nikopb.TaskProgress{TaskId: task, Line: "registering at " + invite.Host})
	}
	ctx, cancel := context.WithTimeout(s.users.context(), 90*time.Second)
	defer cancel()
	registered, err := s.registerAt(ctx, request, control)
	if err != nil {
		var apiErr *rayapi.Error
		if errors.As(err, &apiErr) && apiErr.PinLeft != nil {
			// the node asks for the PIN (none sent, or a wrong one): it tells how many tries are left
			if *apiErr.PinLeft <= 0 {
				return nil, "", status.Error(codes.PermissionDenied,
					"the invite was withdrawn after wrong PINs: ask for a new one")
			}
			result.Pin = &nikopb.PinNeeded{Host: invite.Host, Left: int32(*apiErr.PinLeft), Wrong: pin != ""}
			failTask(result, status.Error(codes.FailedPrecondition, "the invite needs its PIN"))
			return nil, "", nil
		}
		return nil, "", registerError(err)
	}
	role, err := nikoray.AppRole(registered.Role)
	if err != nil {
		// the invite is used: its key is of no further use
		_ = u.store.ForgetPending(invite.InviteID)
		return nil, "", nodeStoreError(err)
	}
	r, err := nikoray.ParseDevice(registered.Bundle)
	if err != nil {
		return nil, "", status.Error(codes.Internal, "the node's package: "+err.Error())
	}
	// the channel is pinned to the invite's node; its package may name a new address (a moved node): that is kept
	if r.SPA.NodeID != invite.SPA.NodeID {
		_ = u.store.ForgetPending(invite.InviteID)
		return nil, "", status.Error(codes.FailedPrecondition, "the node answered with a package of another node; "+
			"the invite is used: ask the administrator to revoke this device")
	}
	kept := &nikoray.Identity{Cert: registered.Identity.Cert, Key: registered.Identity.Key,
		Pin: registered.Identity.Pin, CreatedAt: now}
	// kept, the pending key forgotten in the same write: until then a retry sends the same key and gets the same
	// package
	node, err := u.store.ImportNode(r, kept, invite.InviteID)
	if errors.Is(err, nikoray.ErrNotForApp) || errors.Is(err, nikoray.ErrNoSubscription) {
		_ = u.store.ForgetPending(invite.InviteID)
	}
	if err != nil {
		return nil, "", nodeStoreError(err)
	}
	s.syncLogRedactions()
	return node, "registered at " + node.Host + " as " + role, nil
}

// registerError is a failed registration as its status (RayImport.failure); the invite's key never in it.
func registerError(err error) error {
	var apiErr *rayapi.Error
	switch {
	case errors.As(err, &apiErr) && apiErr.InviteRole != "":
		return status.Error(codes.FailedPrecondition, "the invite is for an integration ("+apiErr.InviteRole+
			"), not for an app: it is still unused")
	case errors.As(err, &apiErr) && apiErr.Code == "not_found":
		// used, expired, revoked, a wrong key, or the clock off by more than 2 minutes
		return status.Error(codes.NotFound, "the node refused the invite: it is used, expired or revoked, or this "+
			"computer's clock is off by more than 2 minutes")
	case errors.As(err, &apiErr):
		return status.Error(codes.FailedPrecondition, "the node refused the registration: "+apiErr.Code)
	case errors.Is(err, rayapi.ErrAnswerLost):
		return status.Error(codes.Unavailable, "the registration reached the node but its answer was lost: import "+
			"the invite again within 15 minutes, the node answers the same")
	case errors.Is(err, rayapi.ErrNoAnswer):
		return status.Error(codes.Unavailable, "the node did not answer; a clock off by more than 30 seconds is a "+
			"likely cause")
	case errors.Is(err, context.Canceled):
		return status.Error(codes.Canceled, "the daemon stops")
	}
	return status.Error(codes.Unavailable, "the registration failed: "+err.Error())
}

// refused: the node itself refused this device or proved to hold other keys (RaySubscription.refused); a retry
// cannot fix it, a new invite is needed. A pin mismatch is not one: any TLS middlebox causes it.
func refused(err error) bool {
	if errors.Is(err, raysub.ErrNotFound) {
		return true
	}
	m := err.Error()
	return strings.Contains(m, "signature does not verify") || strings.Contains(m, "signed for another node")
}

// retryAfter is RaySubscription.retryAfter: after a fetch the node did not answer, 15 minutes doubling to 6 hours
// for each failure in a row; 6 hours after another.
func retryAfter(err error, failures int) int64 {
	if errors.Is(err, raysub.ErrNoAnswer) {
		return min(int64(noAnswerRetry)<<min(max(failures-1, 0), 5), maxRetry)
	}
	return maxRetry
}

// jitter is a moment within d after now: each device of a node at its own (Jitter.upTo).
func jitter(d int64) int64 {
	if d <= 0 {
		return 0
	}
	return rand.Int64N(d)
}

// fetchError is a failed fetch as its status.
func fetchError(err error) error {
	switch {
	case errors.Is(err, raysub.ErrNotFound):
		return status.Error(codes.PermissionDenied, "the node does not know this device (revoked, disabled or "+
			"another key): import a new invite")
	case refused(err):
		return status.Error(codes.PermissionDenied, "the node's subscription is not signed by its keys: import a "+
			"new invite")
	case errors.Is(err, raysub.ErrNoAnswer):
		return status.Error(codes.Unavailable, "the node did not answer; a clock off by more than 30 seconds is a "+
			"likely cause")
	case errors.Is(err, context.Canceled):
		return status.Error(codes.Canceled, "the daemon stops")
	}
	return status.Error(codes.Unavailable, err.Error())
}

// updateNode fetches a node's subscription and stores it (RaySubscription.refresh): the line for the user, the group
// and the profiles added. A failure plans a retry, a refusal stops the schedule.
func (s *server) updateNode(ctx context.Context, u *user, id string) (string, int64, []int64, error) {
	if !u.nodes.start(id) {
		return "", 0, nil, status.Error(codes.Aborted, "an update of this node runs already")
	}
	u.feed.publish(&nikopb.Change{Kind: nikopb.Change_NODES})
	line, group, added, err := s.updateNodeLocked(ctx, u, id)
	u.nodes.done(id, err)
	u.feed.publish(&nikopb.Change{Kind: nikopb.Change_NODES})
	return line, group, added, err
}

func (s *server) updateNodeLocked(ctx context.Context, u *user, id string) (string, int64, []int64, error) {
	node, err := u.store.Node(id)
	if err != nil {
		return "", 0, nil, storeError(err)
	}
	info := node.Subscription
	if info == nil {
		return "", node.GroupID, nil, status.Error(codes.FailedPrecondition, "the node's registration has no subscription")
	}
	spa, err := spaOptions(node.SPAJSON())
	if err != nil {
		return "", node.GroupID, nil, status.Error(codes.Internal, "the node's SPA: "+err.Error())
	}
	control, lookup := s.bypass()
	lanHeader := ""
	if node.Lan != nil || node.LanShow {
		if node.LanShow {
			lanHeader = "on"
		} else {
			lanHeader = "off"
		}
	}
	request := raysub.Request{Host: node.Host, Port: info.Port, Pin: info.Pin, DeviceID: node.DeviceID,
		NodeID: node.ID(), Ed25519: info.Ed25519, MLDSA65: info.MLDSA65, SPA: spa, LastSeq: node.SubSeq,
		LAN: lanHeader, Control: control, Lookup: lookup}
	// names-v1 §5 p. 7: by the node's name with its addresses to fall back to, or by its host
	if name := node.ReachName(); name != "" {
		request.Host, request.Addrs = name, node.KnownAddrs()
	}
	if s.fetch == nil {
		return "", node.GroupID, nil, status.Error(codes.Unavailable, "no subscription fetcher")
	}
	fetchCtx, cancel := context.WithTimeout(ctx, 90*time.Second)
	defer cancel()
	fetched, err := s.fetch(fetchCtx, request)
	now := s.clock().Unix()
	// the registration this fetch is of: a node deleted and added again, or registered anew, meanwhile is another
	// device of it, and this fetch's answer is not that device's
	device := node.DeviceID
	if err != nil {
		_, _ = u.store.UpdateNode(id, func(n *nikoray.Node) bool {
			if n.DeviceID != device {
				return false
			}
			n.FailedFetches++
			if refused(err) {
				n.RefreshAt = nikoray.Stopped
			} else {
				delay := retryAfter(err, n.FailedFetches)
				n.RefreshAt = now + delay - delay/10 + jitter(delay/5)
			}
			return true
		})
		return "", node.GroupID, nil, fetchError(err)
	}
	payload, err := json.Marshal(fetched.Payload)
	if err != nil {
		return "", node.GroupID, nil, status.Error(codes.Internal, err.Error())
	}
	skew := fetched.ClockSkew
	nodeNow := now + skew
	// a subscription the node signed but this daemon cannot take: the next try as late as a failed fetch's last, not
	// every minAutoRetry (a full knock, TLS and a fetch of up to 4 MiB each time)
	unusable := func(failure error) (string, int64, []int64, error) {
		_, _ = u.store.UpdateNode(id, func(n *nikoray.Node) bool {
			if n.DeviceID != device {
				return false
			}
			n.RefreshAt = now + maxRetry - maxRetry/10 + jitter(maxRetry/5)
			return true
		})
		return "", node.GroupID, nil, failure
	}
	parsed, err := nikoray.Parse(payload, node.Kinds(), nodeNow, nikofmt.DefaultSettings)
	if err != nil {
		return unusable(status.Error(codes.InvalidArgument, err.Error()))
	}
	if len(parsed.Rows) > maxImport {
		return unusable(errOverImport)
	}
	next, err := nikoray.NextRefresh(payload, nodeNow)
	if err != nil {
		return unusable(status.Error(codes.InvalidArgument, "the subscription's times: "+err.Error()))
	}
	// a moment every device of the node shares (a rotation's from): each fetches within 15 minutes after it
	planned := max(now+60, next-skew)
	refreshAt := planned
	if planned > now+120 {
		refreshAt += jitter(15 * 60)
	}
	subExpires := fetched.Payload.Expires - skew
	seq := fetched.Payload.Seq
	record := func(n *nikoray.Node) {
		n.RefreshAt, n.ClockSkew, n.FailedFetches = refreshAt, skew, 0
		n.SubUpdatedAt, n.SubExpires = now, subExpires
		n.Traffic, n.Note, n.Client = parsed.Traffic, parsed.Note, parsed.Client
	}
	// the user's traffic used up, or this version refused (client-min-v1 §4): no profiles until then, the rows and
	// the selection stay as they are; only what the card shows and the next fetch change
	if parsed.Traffic != nil && parsed.Traffic.Exhausted || parsed.Client != nil && parsed.Client.Refused {
		_, err = u.store.UpdateNode(id, func(n *nikoray.Node) bool {
			if seq < n.SubSeq || n.DeviceID != device {
				return false
			}
			n.SubSeq = seq
			record(n)
			return true
		})
		if err != nil {
			return "", node.GroupID, nil, storeError(err)
		}
		if parsed.Client != nil && parsed.Client.Refused {
			return node.DisplayName() + ": the node requires Niko " + parsed.Client.Min["niko"] + " or later", node.GroupID, nil, nil
		}
		return node.DisplayName() + ": the traffic is used up", node.GroupID, nil, nil
	}
	links := map[string]nikoray.Links{}
	for _, row := range parsed.Rows {
		links[row.Key] = row.Links
	}
	applied, err := u.store.ApplyNodeRows(id, device, seq, parsed.Rows, func(n *nikoray.Node) {
		record(n)
		n.Links, n.DoH, n.DoHPins, n.Name = links, parsed.DoH, parsed.DoHPins, parsed.Name
		n.SPACarriers, n.SPAVersions = parsed.Carriers, parsed.SPAVersions
		// names-v1: the subscription's name and addresses; a node rolled back before v0.34.0 sends none, and the
		// package's are kept (§8). Record any previous DNS name in PastNames for host matching & log redaction.
		if parsed.DNSName != "" {
			if n.DNSName != "" && !strings.EqualFold(n.DNSName, parsed.DNSName) {
				n.RecordPastName(n.DNSName)
			}
			n.DNSName = parsed.DNSName
		}
		n.SubAddrs = parsed.Addrs
		n.Relays = parsed.Relays
		n.Probe = parsed.Probe
		n.Hints = parsed.Hints
		n.Lan = parsed.Lan
		n.AppReleases = parsed.AppReleases
	})
	if err != nil {
		return "", node.GroupID, nil, storeError(err)
	}
	if applied.Stale {
		return node.DisplayName() + ": nothing taken: a newer subscription is kept already, or the node was registered anew", node.GroupID, nil, nil
	}
	s.syncLogRedactions()
	line := nikoray.GroupName(node.Host, parsed.Name) + ": " + strconv.Itoa(len(parsed.Rows)) + " profiles"
	if parsed.Skipped > 0 {
		line += ", " + strconv.Itoa(parsed.Skipped) + " skipped"
	}
	if skew > clockOff || skew < -clockOff {
		line += fmt.Sprintf("; this computer's clock is off by %d s from the node's: set it right, SPA fails past 30 s", skew)
	}
	return line, applied.Group, applied.Added, nil
}

// nodeTask runs a node's update as a task to its end and tells its result to the user's watchers.
func (s *server) nodeTask(u *user, task, id string, progress func(*nikopb.TaskProgress)) *nikopb.TaskProgress {
	result := &nikopb.TaskProgress{TaskId: task, Done: true, NodeId: id}
	if progress != nil {
		progress(&nikopb.TaskProgress{TaskId: task, Line: "updating node " + id, NodeId: id})
	}
	line, group, added, err := s.updateNode(s.users.context(), u, id)
	result.Line, result.GroupId, result.Ids = line, group, added
	if err != nil {
		failTask(result, err)
	}
	u.feed.publish(&nikopb.Change{Kind: nikopb.Change_TASK, Task: proto.Clone(result).(*nikopb.TaskProgress)})
	return result
}

// nodeOfGroup is the node whose group a group is, "" for none.
func nodeOfGroup(u *user, group int64) string {
	for _, g := range u.store.Groups() {
		if g.ID == group {
			return g.Node
		}
	}
	return ""
}

// autoNodes is the timed update of every open store's nodes (RaySubscription.RefreshTask): each with a subscription
// whose next update is due, or was never planned; none the node refused.
func (s *server) autoNodes(now time.Time) {
	for _, u := range s.users.opened() {
		for _, kept := range u.store.Nodes() {
			n := kept.Node
			if kept.Unreadable {
				continue
			}
			// Promote any scheduled next credentials whose `from` timestamp has passed (applyDue).
			if changed, err := u.store.ApplyDueLinks(n.ID(), now.Unix()+n.ClockSkew); err == nil && len(changed) > 0 {
				s.markWatchdogStale(n.ID(), changed)
			}
			// Rotate admin client certificate every 90 days (api-v1.md §6, rotateIfDue).
			if n.API != nil && n.Identity != nil && now.Unix()-n.Identity.CreatedAt >= rotateIdentityAfterSeconds {
				go s.rotateNodeIdentityIfDue(s.users.context(), u, n.ID())
			}
			if n.Subscription == nil || n.RefreshAt == nikoray.Stopped || n.RefreshAt > now.Unix() {
				continue
			}
			if !u.nodes.due(n.ID(), now) {
				continue
			}
			task := "node-" + strconv.FormatUint(s.users.tasks.Add(1), 10)
			go s.nodeTask(u, task, n.ID(), nil)
		}
	}
}

// nodeProto is a node as ListNodes gives it: never its keys.
func nodeProto(u *user, kept nikostore.StoredNode, profiles map[int64]int) *nikopb.Node {
	n := kept.Node
	updating, lastError := u.nodes.state(n.ID())
	p := &nikopb.Node{Id: n.ID(), Host: n.Host, Name: n.DisplayName(), Note: n.Note, GroupId: n.GroupID,
		Profiles: int32(profiles[n.GroupID]), DnsName: n.DNSName, Admin: n.API != nil && n.Identity != nil,
		Subscription: n.Subscription != nil, Updated: n.SubUpdatedAt, Expires: n.SubExpires,
		Refused: n.RefreshAt == nikoray.Stopped, Failed: int32(n.FailedFetches), Error: lastError,
		ClockSkew: n.ClockSkew, Updating: updating, Unreadable: kept.Unreadable,
		HasProbe: n.Probe != nil, HasLan: n.Lan != nil, LanShow: n.LanShow, Relays: int32(len(n.Relays)),
		SwitchedFrom: n.SwitchedFrom, SwitchedTo: n.SwitchedTo, SwitchedAt: n.SwitchedAt}
	if n.RefreshAt > 0 {
		p.NextUpdate = n.RefreshAt
	}
	if t := n.Traffic; t != nil {
		p.Traffic = &nikopb.NodeTraffic{Used: t.Used, Limit: t.Limit, Since: t.Since, Reset_: t.Reset,
			Exhausted: t.Exhausted, NearLimit: t.NearLimit()}
	}
	if c := n.Client; c != nil {
		p.MinVersion, p.RefusedVersion = c.Min["niko"], c.Refused
	}
	return p
}

func (s *server) ListNodes(ctx context.Context, _ *nikopb.Empty) (*nikopb.NodeList, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	profiles := map[int64]int{}
	for _, g := range u.store.Groups() {
		profiles[g.ID] = g.Profiles
	}
	list := &nikopb.NodeList{}
	now := s.clock()
	netKey := desktopNetworkFingerprint()
	var liveNaive string
	if s.coreBox != nil {
		if _, boxInst, release := s.coreBox(); boxInst != nil {
			liveNaive = liveNaiveDatagramState(boxInst)
			if release != nil {
				release()
			}
		}
	}
	for _, kept := range u.store.Nodes() {
		np := nodeProto(u, kept, profiles)
		if s.dnsResolvers != nil {
			np.DnsResolver = s.dnsResolvers.SummaryForNode(kept.Node.ID(), now)
		}
		if liveNaive != "" && liveNaive != "off" {
			np.NaiveUdpState = liveNaive
		} else if s.datagrams != nil {
			if ok, found := s.datagrams.Lookup(netKey, kept.Node.ID(), now.Unix()); found {
				if ok {
					np.NaiveUdpState = "quic"
				} else {
					np.NaiveUdpState = "tcp_fallback"
				}
			}
		}
		list.Nodes = append(list.Nodes, np)
	}
	return list, nil
}

func (s *server) DeleteNode(ctx context.Context, request *nikopb.NodeId) (*nikopb.Empty, error) {
	u, err := s.users.user(ctx)
	if err != nil {
		return nil, err
	}
	if err = u.store.DeleteNode(request.Id); err != nil {
		return nil, storeError(err)
	}
	// its key out of the core: a core that runs with it knocks no more once it starts again
	s.retainDeviceKeys()
	s.syncLogRedactions()
	return &nikopb.Empty{}, nil
}

// deviceKeys hands the core the device keys of a user's nodes by their references (RayNodes.prepareDeviceKey): the
// knock and the DNS tunnels' dnsauth take them so, and the keys never enter a config. The keys of nodes no store has
// any more go (retainDeviceKeys).
func (s *server) deviceKeys(nodes []nikoray.Node) {
	for _, n := range nodes {
		if n.DeviceKey != "" {
			knock.SetDeviceKey(n.KeyRef(), n.DeviceKey)
		}
	}
	s.retainDeviceKeys()
}

// loadAllDeviceKeys populates knock's in-memory device keys for all currently opened user stores (H3).
func (s *server) loadAllDeviceKeys() {
	for _, u := range s.users.opened() {
		for _, kept := range u.store.Nodes() {
			if !kept.Unreadable && kept.Node.DeviceKey != "" {
				knock.SetDeviceKey(kept.Node.KeyRef(), kept.Node.DeviceKey)
			}
		}
	}
	s.retainDeviceKeys()
}

// retainDeviceKeys drops from the core the device keys of nodes no open store keeps.
func (s *server) retainDeviceKeys() {
	var refs []string
	for _, u := range s.users.opened() {
		for _, kept := range u.store.Nodes() {
			refs = append(refs, kept.Node.KeyRef())
		}
	}
	knock.RetainDeviceKeys(refs)
}

// Package knock implements a port knocking outbound.
//
// The outbound is used as the `detour` of a proxy outbound. Before dialing the
// proxy server it sends a sequence of TCP SYNs / UDP datagrams to the same host,
// so a knockd-style firewall opens the real port for this client. A "spa" step
// sends one authenticated, non-replayable datagram instead (spa.go).
//
// A knock is repeated when the session breaks: after a default network change
// (new client address), when a dial fails on a cached knock, and once per
// silence episode when a connection gets no reply for `silence` after sending.
package knock

import (
	"context"
	"encoding/hex"
	"net"
	"strings"
	"sync"
	"sync/atomic"
	"time"

	"github.com/sagernet/sing-box/adapter"
	"github.com/sagernet/sing-box/adapter/outbound"
	"github.com/sagernet/sing-box/common/dialer"
	"github.com/sagernet/sing-box/log"
	"github.com/sagernet/sing-box/option"
	E "github.com/sagernet/sing/common/exceptions"
	"github.com/sagernet/sing/common/json/badoption"
	"github.com/sagernet/sing/common/logger"
	M "github.com/sagernet/sing/common/metadata"
	N "github.com/sagernet/sing/common/network"
	"github.com/sagernet/sing/service"
)

const TypeKnock = "knock"

const (
	maxSteps        = 32
	defaultInterval = 200 * time.Millisecond
	defaultTTL      = 10 * time.Second
	maxPayload      = 1024
	// lower bound between forced knocks to the same host
	minReknockGap = 2 * time.Second
	// A TCP knock is abandoned before the kernel retransmits its SYN (initial
	// RTO is 1s): knockd counts any TCP packet, and a late SYN to the previous
	// port resets the sequence. The floor leaves time for protect() + connect().
	minTCPAttempt = 100 * time.Millisecond
	maxTCPAttempt = 800 * time.Millisecond
	// time for the server to run its start command before the real connection
	minSettle = 150 * time.Millisecond
)

// what the reference `knock` client sends to a UDP port
var defaultUDPPayload = []byte{0}

type Step struct {
	Port    uint16 `json:"port"`
	Network string `json:"network,omitempty"`
	Payload string `json:"payload,omitempty"`
}

type Options struct {
	option.DialerOptions
	Sequence []Step             `json:"sequence"`
	Interval badoption.Duration `json:"interval,omitempty"`
	TTL      badoption.Duration `json:"ttl,omitempty"`
	// Silence re-knocks once when a connection gets no reply this long after
	// sending. Zero disables it.
	Silence badoption.Duration `json:"silence,omitempty"`
	// SPAv2 replaces the sequence with raycontrol SPA v2 (spa2.go).
	SPAv2 *SPAv2Options `json:"spa_v2,omitempty"`
}

func Register(registry *outbound.Registry) {
	outbound.Register[Options](registry, TypeKnock, New)
}

type step struct {
	port    uint16
	udp     bool
	payload []byte
	// a Niko SPA v1 key: the datagram is built for each knock (spa.go)
	spaKey []byte
}

// target is the knock state of one server address. The lock only guards the
// fields; it is never held while knocking.
type target struct {
	access     sync.Mutex
	lastKnock  time.Time
	generation uint64
	inflight   chan struct{}
	// SPA v2: the access is renewed while the host is in use
	lastUse time.Time
	active  int
	renew   *time.Timer
}

var (
	_ adapter.Outbound                = (*Outbound)(nil)
	_ adapter.InterfaceUpdateListener = (*Outbound)(nil)
)

type Outbound struct {
	outbound.Adapter
	ctx            context.Context
	logger         logger.ContextLogger
	dialer         N.Dialer
	hasDetour      bool
	domainResolver *option.DomainResolveOptions
	steps          []step
	spa2           *spa2Params
	interval       time.Duration
	ttl            time.Duration
	silence        time.Duration

	// bumped on network changes, knocks of an older generation are stale
	generation atomic.Uint64

	access  sync.Mutex
	targets map[string]*target
}

func New(ctx context.Context, router adapter.Router, logger log.ContextLogger, tag string, options Options) (adapter.Outbound, error) {
	var (
		steps []step
		spa2  *spa2Params
		err   error
	)
	if options.SPAv2 != nil {
		if len(options.Sequence) > 0 {
			return nil, E.New("knock: spa_v2 and sequence are exclusive")
		}
		spa2, err = parseSPAv2(*options.SPAv2)
	} else {
		steps, err = parseSequence(options.Sequence)
	}
	if err != nil {
		return nil, err
	}
	interval := time.Duration(options.Interval)
	if interval <= 0 {
		interval = defaultInterval
	}
	ttl := time.Duration(options.TTL)
	if ttl <= 0 {
		ttl = defaultTTL
	}
	if spa2 != nil {
		// the server's window; renewals (spa2.go) keep it open while in use
		ttl = spa2.access() * 9 / 10
	}
	silence := time.Duration(options.Silence)
	if silence < 0 {
		silence = 0
	}
	// Without a detour the server address is resolved here, so that the knocks
	// and the real connection go to the same IP.
	outboundDialer, err := dialer.New(ctx, options.DialerOptions, false)
	if err != nil {
		return nil, err
	}
	return &Outbound{
		Adapter:        outbound.NewAdapterWithDialerOptions(TypeKnock, tag, []string{N.NetworkTCP, N.NetworkUDP}, options.DialerOptions),
		ctx:            ctx,
		logger:         logger,
		dialer:         outboundDialer,
		hasDetour:      options.Detour != "",
		domainResolver: options.DomainResolver,
		steps:          steps,
		spa2:           spa2,
		interval:       interval,
		ttl:            ttl,
		silence:        silence,
		targets:        make(map[string]*target),
	}, nil
}

func parseSequence(sequence []Step) ([]step, error) {
	if len(sequence) == 0 {
		return nil, E.New("knock: empty sequence")
	}
	if len(sequence) > maxSteps {
		return nil, E.New("knock: too many steps: ", len(sequence))
	}
	steps := make([]step, 0, len(sequence))
	for i, s := range sequence {
		if s.Port == 0 {
			return nil, E.New("knock: step ", i+1, ": missing port")
		}
		var parsed step
		parsed.port = s.Port
		switch strings.ToLower(s.Network) {
		case "", N.NetworkTCP:
		case N.NetworkUDP:
			parsed.udp = true
		case "spa":
			parsed.udp = true
			key, err := hex.DecodeString(s.Payload)
			if err != nil || len(key) != spaKeySize {
				return nil, E.New("knock: step ", i+1, ": spa needs a 32-byte hex key")
			}
			parsed.spaKey = key
			steps = append(steps, parsed)
			continue
		default:
			return nil, E.New("knock: step ", i+1, ": unknown network: ", s.Network)
		}
		if s.Payload != "" {
			if !parsed.udp {
				return nil, E.New("knock: step ", i+1, ": payload is only supported for udp")
			}
			payload, err := hex.DecodeString(s.Payload)
			if err != nil {
				return nil, E.Cause(err, "knock: step ", i+1, ": decode payload")
			}
			if len(payload) > maxPayload {
				return nil, E.New("knock: step ", i+1, ": payload too large")
			}
			parsed.payload = payload
		}
		steps = append(steps, parsed)
	}
	return steps, nil
}

func (o *Outbound) DialContext(ctx context.Context, network string, destination M.Socksaddr) (net.Conn, error) {
	destination, err := o.resolve(ctx, destination)
	if err != nil {
		return nil, err
	}
	knocked, err := o.knock(ctx, destination, false)
	if err != nil {
		return nil, err
	}
	conn, err := o.dialer.DialContext(ctx, network, destination)
	if err != nil && knocked && o.spa2 != nil {
		// the slot (and port) may have changed right at the boundary: one more packet
		o.logger.DebugContext(ctx, "dial after SPA failed, sending it once more: ", err)
		if o.sendSequence(ctx, destination) == nil {
			conn, err = o.dialer.DialContext(ctx, network, destination)
		}
	}
	if err != nil && !knocked {
		// the cached knock may be stale (window closed, server restarted): knock and retry once
		o.logger.DebugContext(ctx, "dial after cached knock failed, knocking again: ", err)
		if _, knockErr := o.knock(ctx, destination, true); knockErr != nil {
			return nil, err
		}
		conn, err = o.dialer.DialContext(ctx, network, destination)
	}
	if err != nil {
		// the port may need a moment more, knock again on the next attempt
		o.forget(destination)
		return nil, err
	}
	if o.spa2 != nil {
		conn = o.trackConn(conn, destination)
	}
	if N.NetworkName(network) == N.NetworkUDP {
		// e.g. WireGuard with one peer dials its UDP socket
		return o.watchConn(conn, destination), nil
	}
	// A broken TCP session errors out and the reconnect knocks; established
	// TCP survives a closed window via conntrack, and silence after a write is
	// normal there (uploads), so TCP connections are returned unwrapped.
	return conn, nil
}

func (o *Outbound) ListenPacket(ctx context.Context, destination M.Socksaddr) (net.PacketConn, error) {
	destination, err := o.resolve(ctx, destination)
	if err != nil {
		return nil, err
	}
	// e.g. multi-peer WireGuard listens on 0.0.0.0, there is nothing to knock at
	if !knockable(destination) {
		return o.dialer.ListenPacket(ctx, destination)
	}
	_, err = o.knock(ctx, destination, false)
	if err != nil {
		return nil, err
	}
	packetConn, err := o.dialer.ListenPacket(ctx, destination)
	if err != nil {
		return nil, err
	}
	if o.spa2 != nil {
		packetConn = o.trackPacketConn(packetConn, destination)
	}
	if o.silence <= 0 {
		return packetConn, nil
	}
	return &silentPacketConn{PacketConn: packetConn, watchdog: o.newWatchdog(destination)}, nil
}

// InterfaceUpdated is called when the default network changes. The server
// only opened the port for the old client address, so every knock is stale.
// It must not block: sing-box calls it from ResetNetwork.
func (o *Outbound) InterfaceUpdated(ctx context.Context) {
	o.generation.Add(1)
	if o.spa2 != nil {
		// SPA v2: hosts in use get a packet from the new address at once
		go o.renewInUse()
	}
}

func knockable(destination M.Socksaddr) bool {
	if destination.Port == 0 {
		return false
	}
	return destination.IsFqdn() || destination.Addr.IsValid() && !destination.Addr.IsUnspecified()
}

func (o *Outbound) resolve(ctx context.Context, destination M.Socksaddr) (M.Socksaddr, error) {
	if !destination.IsFqdn() || o.hasDetour {
		return destination, nil
	}
	queryOptions, err := dialer.NewDNSQueryOptions(o.ctx, o.domainResolver, false)
	if err != nil {
		return M.Socksaddr{}, err
	}
	addresses, err := service.FromContext[adapter.DNSRouter](o.ctx).Lookup(ctx, destination.Fqdn, queryOptions)
	if err != nil {
		return M.Socksaddr{}, err
	}
	if len(addresses) == 0 {
		return M.Socksaddr{}, E.New("knock: no address for ", destination.Fqdn)
	}
	return M.SocksaddrFrom(addresses[0], destination.Port), nil
}

func (o *Outbound) target(host string) *target {
	o.access.Lock()
	defer o.access.Unlock()
	t, loaded := o.targets[host]
	if !loaded {
		t = &target{}
		o.targets[host] = t
	}
	return t
}

func (o *Outbound) forget(destination M.Socksaddr) {
	t := o.target(destination.AddrString())
	t.access.Lock()
	t.lastKnock = time.Time{}
	t.access.Unlock()
}

// cached reports whether a knock to the host is still considered valid.
func (o *Outbound) cached(destination M.Socksaddr) bool {
	t := o.target(destination.AddrString())
	t.access.Lock()
	defer t.access.Unlock()
	return !t.lastKnock.IsZero() && t.generation == o.generation.Load() && time.Since(t.lastKnock) < o.ttl
}

// knock sends the sequence to the host unless a knock is still valid (with
// force: unless it was sent just now) and reports whether it sent one.
// Concurrent callers share one knock in flight.
func (o *Outbound) knock(ctx context.Context, destination M.Socksaddr, force bool) (bool, error) {
	t := o.target(destination.AddrString())
	for {
		t.access.Lock()
		generation := o.generation.Load()
		if !t.lastKnock.IsZero() && t.generation == generation {
			since := time.Since(t.lastKnock)
			if since < o.ttl && !force || since < minReknockGap {
				t.access.Unlock()
				return false, nil
			}
		}
		if t.inflight == nil {
			done := make(chan struct{})
			t.inflight = done
			t.access.Unlock()
			err := o.sendSequence(ctx, destination)
			t.access.Lock()
			t.inflight = nil
			if err == nil {
				// a network change during the knock leaves it stale
				t.lastKnock = time.Now()
				t.generation = generation
				if o.spa2 != nil {
					o.scheduleRenewal(t, destination)
				}
			}
			t.access.Unlock()
			close(done)
			return err == nil, err
		}
		inflight := t.inflight
		t.access.Unlock()
		select {
		case <-inflight:
			// re-check: the knock may have failed or become stale
			force = false
		case <-ctx.Done():
			return false, ctx.Err()
		}
	}
}

// sendSequence knocks the steps strictly in order: a step starts only after
// the previous one is over and at least `interval` after it started.
func (o *Outbound) sendSequence(ctx context.Context, destination M.Socksaddr) error {
	if o.spa2 != nil {
		return o.sendSPA2(ctx, destination)
	}
	o.logger.InfoContext(ctx, "knocking ", destination.AddrString(), " (", len(o.steps), " steps)")
	for i, s := range o.steps {
		started := time.Now()
		o.send(ctx, M.Socksaddr{Addr: destination.Addr, Fqdn: destination.Fqdn, Port: s.port}, s)
		if i == len(o.steps)-1 {
			break
		}
		err := sleep(ctx, o.interval-time.Since(started))
		if err != nil {
			return err
		}
	}
	// give the firewall time to apply the rule before the real connection
	return sleep(ctx, max(o.interval, minSettle))
}

func (o *Outbound) send(ctx context.Context, destination M.Socksaddr, s step) {
	if s.udp {
		packetConn, err := o.dialer.ListenPacket(ctx, destination)
		if err != nil {
			o.logger.DebugContext(ctx, "knock udp ", destination, ": ", err)
			return
		}
		defer packetConn.Close()
		var addr net.Addr = destination
		if destination.IsIP() {
			addr = destination.UDPAddr()
		}
		payload := s.payload
		if s.spaKey != nil {
			payload = spaPacket(s.spaKey)
		} else if len(payload) == 0 {
			payload = defaultUDPPayload
		}
		_, err = packetConn.WriteTo(payload, addr)
		if err != nil {
			o.logger.DebugContext(ctx, "knock udp ", destination, ": ", err)
		}
		return
	}
	// Like `knock`: one SYN, no waiting for the handshake. A dropped port
	// never answers, so the attempt is cut before the SYN is retransmitted.
	dialCtx, cancel := context.WithTimeout(ctx, tcpAttemptTimeout(o.interval))
	defer cancel()
	conn, err := o.dialer.DialContext(dialCtx, N.NetworkTCP, destination)
	if err == nil {
		// an open knock port; knockd needs `tcpflags = syn` to ignore our FIN
		conn.Close()
	}
}

func tcpAttemptTimeout(interval time.Duration) time.Duration {
	return min(max(interval, minTCPAttempt), maxTCPAttempt)
}

func sleep(ctx context.Context, duration time.Duration) error {
	if duration <= 0 {
		return ctx.Err()
	}
	timer := time.NewTimer(duration)
	defer timer.Stop()
	select {
	case <-timer.C:
		return nil
	case <-ctx.Done():
		return ctx.Err()
	}
}

// watchdog re-knocks a UDP session once when data was sent but nothing came
// back for `silence`, then checks after another `silence` whether the server
// answers again. It re-arms only after a reply, so a dead server is knocked
// once per session. Long-lived UDP transports (WireGuard, QUIC) keep one
// socket and never dial again, so this is their only way back.
type watchdog struct {
	outbound    *Outbound
	destination M.Socksaddr
	lastRead    atomic.Int64
	pending     atomic.Bool
	knocked     atomic.Bool
	closed      atomic.Bool
}

func (o *Outbound) newWatchdog(destination M.Socksaddr) *watchdog {
	w := &watchdog{outbound: o, destination: destination}
	w.lastRead.Store(time.Now().UnixNano())
	return w
}

func (w *watchdog) onRead() {
	w.lastRead.Store(time.Now().UnixNano())
	if w.knocked.CompareAndSwap(true, false) {
		w.outbound.logger.Info("server ", w.destination.AddrString(), " answers again after re-knock")
	}
}

// isWireGuardKeepalive matches an empty WireGuard transport message: the
// peer does not answer it, so it must not arm the watchdog. Bytes 1-3 may
// carry "reserved" values.
func isWireGuardKeepalive(p []byte) bool {
	return len(p) == 32 && p[0] == 4
}

func (w *watchdog) onWrite(p []byte) {
	if isWireGuardKeepalive(p) || w.knocked.Load() || !w.pending.CompareAndSwap(false, true) {
		return
	}
	sentAt := time.Now().UnixNano()
	time.AfterFunc(w.outbound.silence, func() {
		w.pending.Store(false)
		if w.closed.Load() || w.lastRead.Load() >= sentAt || !w.knocked.CompareAndSwap(false, true) {
			return
		}
		o := w.outbound
		ctx, cancel := context.WithTimeout(o.ctx, o.interval*time.Duration(len(o.steps)+2)+5*time.Second)
		defer cancel()
		knocked, err := o.knock(ctx, w.destination, true)
		if err != nil || !knocked {
			// nothing sent (another session knocked just now): stay armed
			w.knocked.Store(false)
			return
		}
		o.logger.Warn("no reply from ", w.destination.AddrString(), " for ", o.silence, ", knocked again")
		knockedAt := time.Now().UnixNano()
		time.AfterFunc(o.silence, func() {
			if !w.closed.Load() && w.lastRead.Load() < knockedAt {
				o.logger.Warn("still no reply from ", w.destination.AddrString(), " after re-knock")
			}
		})
	})
}

func (o *Outbound) watchConn(conn net.Conn, destination M.Socksaddr) net.Conn {
	if o.silence <= 0 {
		return conn
	}
	return &silentConn{Conn: conn, watchdog: o.newWatchdog(destination)}
}

type silentConn struct {
	net.Conn
	*watchdog
}

func (c *silentConn) Read(p []byte) (int, error) {
	n, err := c.Conn.Read(p)
	if n > 0 {
		c.onRead()
	}
	return n, err
}

func (c *silentConn) Write(p []byte) (int, error) {
	c.onWrite(p)
	return c.Conn.Write(p)
}

func (c *silentConn) Close() error {
	c.closed.Store(true)
	return c.Conn.Close()
}

type silentPacketConn struct {
	net.PacketConn
	*watchdog
}

func (c *silentPacketConn) ReadFrom(p []byte) (int, net.Addr, error) {
	n, addr, err := c.PacketConn.ReadFrom(p)
	if err == nil {
		c.onRead()
	}
	return n, addr, err
}

func (c *silentPacketConn) WriteTo(p []byte, addr net.Addr) (int, error) {
	c.onWrite(p)
	return c.PacketConn.WriteTo(p, addr)
}

func (c *silentPacketConn) Close() error {
	c.closed.Store(true)
	return c.PacketConn.Close()
}

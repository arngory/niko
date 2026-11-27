package nikoservice

import (
	"context"
	"net"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"time"

	box "github.com/sagernet/sing-box"
)

// Yggdrasil public peer selection (BG-08 / RayYggPeers.kt parity) and live naive-udp-v1 state helper (D-NODE-09).

const maxPublicYggPeers = 3

// YggPeerCheck is the status of one Yggdrasil public peer candidate.
type YggPeerCheck struct {
	URI       string `json:"uri"`
	Up        bool   `json:"up"`
	LatencyMs int64  `json:"latency_ms,omitempty"`
	Error     string `json:"error,omitempty"`
}

// ValidYggPeerURI checks scheme (tls/quic/tcp/ws/wss), host, port 1..65535, and optional 64-hex node key.
func ValidYggPeerURI(raw string) bool {
	u, err := url.Parse(strings.TrimSpace(raw))
	if err != nil || u.Host == "" {
		return false
	}
	switch strings.ToLower(u.Scheme) {
	case "tls", "quic", "tcp", "ws", "wss":
	default:
		return false
	}
	portStr := u.Port()
	port, err := strconv.Atoi(portStr)
	if err != nil || port < 1 || port > 65535 {
		return false
	}
	if key := u.Query().Get("key"); key != "" {
		if len(key) != 64 {
			return false
		}
		for _, ch := range key {
			if !((ch >= '0' && ch <= '9') || (ch >= 'a' && ch <= 'f') || (ch >= 'A' && ch <= 'F')) {
				return false
			}
		}
	}
	return true
}

func isEncryptedYggPeer(raw string) bool {
	u, err := url.Parse(strings.TrimSpace(raw))
	if err != nil {
		return false
	}
	switch strings.ToLower(u.Scheme) {
	case "tls", "quic", "wss", "sockstls":
		return true
	default:
		return false
	}
}

// PickYggPeers picks up to 3 healthy Yggdrasil public peers, preferring encrypted transports first
// (tls/quic/wss/sockstls) and then lowest latency (matches RayYggPeers.pick).
func PickYggPeers(checks []YggPeerCheck) []string {
	var up []YggPeerCheck
	for _, c := range checks {
		if c.Up && ValidYggPeerURI(c.URI) {
			up = append(up, c)
		}
	}
	sort.SliceStable(up, func(i, j int) bool {
		encI, encJ := isEncryptedYggPeer(up[i].URI), isEncryptedYggPeer(up[j].URI)
		if encI != encJ {
			return encI
		}
		return up[i].LatencyMs < up[j].LatencyMs
	})
	if len(up) > maxPublicYggPeers {
		up = up[:maxPublicYggPeers]
	}
	out := make([]string, len(up))
	for i, c := range up {
		out[i] = c.URI
	}
	return out
}

type naiveDatagramOutbound interface {
	SetDatagramAllowed(allowed bool)
	DatagramState() string
	DatagramProbe(ctx context.Context) (net.PacketConn, error)
}

func findNaiveDatagramOutbound(b *box.Box) naiveDatagramOutbound {
	if b == nil {
		return nil
	}
	outbound, ok := b.Outbound().Outbound("proxy")
	if !ok {
		return nil
	}
	if d, ok := outbound.(naiveDatagramOutbound); ok {
		return d
	}
	return nil
}

// liveNaiveDatagramState queries the running core's default "proxy" outbound for live naive-udp-v1 status
// ("allowed", "probing", "refused", "unsupported", "off", or "" if not naive).
func liveNaiveDatagramState(b *box.Box) string {
	if d := findNaiveDatagramOutbound(b); d != nil {
		return d.DatagramState()
	}
	return ""
}

// probeNaiveDatagramSoon wires naive-udp-v1 on desktop (H5): checks per-network datagram cache and
// either applies the cached verdict via SetDatagramAllowed or runs a short DatagramProbe to unlock
// HTTP/3 datagrams when the outbound is in "probing" state.
func (s *server) probeNaiveDatagramSoon(nodeID string) {
	if s.coreBox == nil {
		return
	}
	cacheKey := nodeID
	if cacheKey == "" {
		cacheKey = "manual"
	}
	go func() {
		ctx, boxInst, release := s.coreBox()
		if boxInst == nil {
			return
		}
		defer func() {
			if release != nil {
				release()
			}
		}()
		d := findNaiveDatagramOutbound(boxInst)
		if d == nil {
			return
		}
		state := d.DatagramState()
		if state != "probing" && state != "allowed" {
			return
		}
		netKey := desktopNetworkFingerprint()
		nowUnix := s.clock().Unix()
		s.dynMu.Lock()
		if s.datagrams == nil {
			s.datagrams = &datagramCache{}
		}
		cache := s.datagrams
		s.dynMu.Unlock()

		if allowed, found := cache.Lookup(netKey, cacheKey, nowUnix); found {
			d.SetDatagramAllowed(allowed)
			return
		}
		probeCtx, cancel := context.WithTimeout(ctx, 4*time.Second)
		defer cancel()
		pc, err := d.DatagramProbe(probeCtx)
		allowed := err == nil && pc != nil
		if pc != nil {
			_ = pc.Close()
		}
		d.SetDatagramAllowed(allowed)
		cache.Remember(netKey, cacheKey, allowed, s.clock().Unix())
	}()
}

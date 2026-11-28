package nikoservice

import (
	"context"
	"crypto/ed25519"
	"encoding/hex"
	"net"
	"net/url"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	"libcore/naiveprobe"
	"libcore/nikoray"
	"libcore/rayprobe"

	box "github.com/sagernet/sing-box"
	N "github.com/sagernet/sing/common/network"
	yggAddress "github.com/yggdrasil-network/yggdrasil-go/src/address"
)

// yggdrasilAddressFromHex computes the device's 200::/7 Yggdrasil IPv6 address from its
// 128-hex-digit Ed25519 private key, or "" if invalid.
func yggdrasilAddressFromHex(privateKeyHex string) string {
	key, err := hex.DecodeString(strings.TrimSpace(privateKeyHex))
	if err != nil || len(key) != ed25519.PrivateKeySize {
		return ""
	}
	pub, ok := ed25519.PrivateKey(key).Public().(ed25519.PublicKey)
	if !ok {
		return ""
	}
	addr := yggAddress.AddrForKey(pub)
	if addr == nil {
		return ""
	}
	return net.IP(addr[:]).String()
}

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

func findNaiveDatagramOutboundByTag(b *box.Box, tag string) (naiveprobe.DatagramOutbound, N.Dialer) {
	if b == nil || tag == "" {
		return nil, nil
	}
	outbound, ok := b.Outbound().Outbound(tag)
	if !ok {
		return nil, nil
	}
	if d, ok := outbound.(naiveprobe.DatagramOutbound); ok {
		return d, outbound
	}
	return nil, nil
}

// normalizeNaiveDatagramState maps sing-box protocol/naive DatagramState() values to the canonical
// desktop UI token vocabulary ("quic", "probing", "tcp_fallback", "unsupported", "off").
func normalizeNaiveDatagramState(raw string) string {
	switch raw {
	case "allowed":
		return "quic"
	case "refused":
		return "tcp_fallback"
	case "probing", "unsupported", "off":
		return raw
	default:
		return raw
	}
}

// liveNaiveDatagramState queries the running core's default "proxy" outbound for live naive-udp-v1 status,
// normalized to canonical desktop tokens.
func liveNaiveDatagramState(b *box.Box) string {
	if d, _ := findNaiveDatagramOutboundByTag(b, "proxy"); d != nil {
		return normalizeNaiveDatagramState(d.DatagramState())
	}
	return ""
}

func ownerNodeAndKey(profileID int64, nodes []nikoray.Node) (*nikoray.Node, string) {
	if profileID <= 0 {
		return nil, ""
	}
	for i := range nodes {
		if key := nodes[i].OwnerOf(profileID); key != "" {
			return &nodes[i], key
		}
	}
	return nil, ""
}

// probeNaiveDatagramSoon implements naive-udp-v1 §9.4 on desktop: iterates every naive outbound with
// udp_datagram enabled (proxy, selector/urltest members, chain hops), persists verdicts per network
// and service key (nodeID#svc or profile:<id>), resets on network change, and executes the real
// 30x1100B rayprobe.Echo over HTTP/3 datagrams when the node exposes a probe tunnel.
func (s *server) probeNaiveDatagramSoon(u *user, nodes []nikoray.Node, tags map[string]int64) {
	if s.coreBox == nil {
		return
	}
	targets := make(map[string]int64, len(tags))
	for tag, pid := range tags {
		targets[tag] = pid
	}
	if len(targets) == 0 {
		// Still inspect default "proxy" in case a custom config enabled udp_datagram.
		targets["proxy"] = 0
	}
	var cachePath string
	if u != nil && u.store != nil {
		cachePath = filepath.Join(u.store.Dir(), "datagrams.json")
	}
	settleDelay := 1500 * time.Millisecond
	if s.datagramSettleDelay != nil {
		settleDelay = *s.datagramSettleDelay
	}
	nodesCopy := append([]nikoray.Node(nil), nodes...)
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

		netKey := desktopNetworkFingerprint()
		s.dynMu.Lock()
		if s.datagrams == nil {
			s.datagrams = &datagramCache{}
		}
		cache := s.datagrams
		networkChanged := s.lastDatagramNet != "" && s.lastDatagramNet != netKey
		s.lastDatagramNet = netKey
		s.dynMu.Unlock()
		if cachePath != "" {
			cache.Load(cachePath)
		}

		// Deterministic iteration order.
		tagList := make([]string, 0, len(targets))
		for tag := range targets {
			tagList = append(tagList, tag)
		}
		sort.Strings(tagList)

		for _, tag := range tagList {
			if ctx.Err() != nil {
				return
			}
			d, tcpDialer := findNaiveDatagramOutboundByTag(boxInst, tag)
			if d == nil {
				continue
			}
			if networkChanged {
				d.DatagramNetworkChanged()
			}
			state := d.DatagramState()
			if state != "probing" && state != "allowed" {
				continue
			}

			profileID := targets[tag]
			node, rowKey := ownerNodeAndKey(profileID, nodesCopy)
			cacheKey := ""
			if node != nil && rowKey != "" {
				cacheKey = node.ID() + "#" + rowKey
			} else if profileID > 0 {
				cacheKey = "profile:" + strconv.FormatInt(profileID, 10)
			} else {
				cacheKey = "tag:" + tag
			}

			nowUnix := s.clock().Unix()
			if allowed, found := cache.Lookup(netKey, cacheKey, nowUnix); found {
				d.SetDatagramAllowed(allowed)
				continue
			}

			if settleDelay > 0 {
				select {
				case <-time.After(settleDelay):
				case <-ctx.Done():
					return
				}
			}

			// Raycontrol node with probe-v1 UDP echo support: run the full 30x1100B naive-udp-v1 §9.4 check.
			if node != nil && node.Probe != nil && node.Probe.UDP && node.Probe.Tunnel != "" && node.Subscription != nil {
				keyBytes, decErr := hex.DecodeString(node.DeviceKey)
				if decErr == nil && len(keyBytes) == 32 {
					ep := rayprobe.Endpoint{
						Address:    net.JoinHostPort(node.Probe.Tunnel, "443"),
						Pin:        node.Subscription.Pin,
						ServerName: node.Probe.Tunnel,
						DeviceID:   node.DeviceID,
						DeviceKey:  keyBytes,
						Now:        s.clock,
					}
					var verdict DatagramVerdict
					for attempt := 0; attempt < 2; attempt++ {
						probeCtx, cancel := context.WithTimeout(ctx, 25*time.Second)
						res := naiveprobe.Echo(probeCtx, d, tcpDialer, ep, DatagramEchoCount, DatagramEchoSize, 20*time.Millisecond)
						cancel()
						if ctx.Err() != nil {
							return
						}
						verdict = EvaluateDatagramEcho(res.Status, res.Sent, res.Received)
						if verdict == DatagramAgain && res.Status == 429 && res.RetryAfter > 0 && res.RetryAfter <= 10 && attempt == 0 {
							select {
							case <-time.After(time.Duration(res.RetryAfter) * time.Second):
								continue
							case <-ctx.Done():
								return
							}
						}
						break
					}
					switch verdict {
					case DatagramPassed:
						d.SetDatagramAllowed(true)
						cache.Remember(netKey, cacheKey, true, s.clock().Unix())
					case DatagramFailed:
						d.SetDatagramAllowed(false)
						cache.Remember(netKey, cacheKey, false, s.clock().Unix())
					}
					continue
				}
			}

			// Manual naive profile (or node without a valid probe device key): wait for an actual
			// connect-udp HTTP/3 response (200 + niko-udp: 1) before allowing or caching datagrams.
			type readyProber interface {
				DatagramProbeReady(ctx context.Context) error
			}
			if rp, ok := d.(readyProber); ok {
				probeCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
				err := rp.DatagramProbeReady(probeCtx)
				timedOut := probeCtx.Err() != nil
				cancel()
				if ctx.Err() != nil {
					return
				}
				allowed := err == nil
				d.SetDatagramAllowed(allowed)
				if !timedOut {
					cache.Remember(netKey, cacheKey, allowed, s.clock().Unix())
				}
			} else {
				// Fallback for test doubles that do not implement DatagramProbeReady: do not persist.
				probeCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
				pc, err := d.DatagramProbe(probeCtx)
				cancel()
				if pc != nil {
					_ = pc.Close()
				}
				d.SetDatagramAllowed(err == nil && pc != nil)
			}
		}
	}()
}

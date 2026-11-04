//go:build with_yggdrasil

package libcore

import (
	"crypto/ed25519"
	"encoding/hex"
	"encoding/json"
	"errors"
	"net"

	"github.com/sagernet/sing-box/adapter/endpoint"
	"github.com/sagernet/sing-box/protocol/yggdrasil"

	"github.com/yggdrasil-network/yggdrasil-go/src/address"
	"github.com/yggdrasil-network/yggdrasil-go/src/core"
)

// the Yggdrasil endpoint (experimental), only in builds with with_yggdrasil
func registerYggdrasil(registry *endpoint.Registry) {
	yggdrasil.RegisterEndpoint(registry)
}

// YggdrasilGenerateKey returns a new node key as yggdrasil-go stores it, 128 hex digits.
func YggdrasilGenerateKey() (string, error) {
	_, privateKey, err := ed25519.GenerateKey(nil)
	if err != nil {
		return "", err
	}
	return hex.EncodeToString(privateKey), nil
}

// YggdrasilAddress is the node's 200::/7 address for a key, "" for an invalid one.
func YggdrasilAddress(privateKeyHex string) string {
	key, err := hex.DecodeString(privateKeyHex)
	if err != nil || len(key) != ed25519.PrivateKeySize {
		return ""
	}
	nodeAddress := address.AddrForKey(ed25519.PrivateKey(key).Public().(ed25519.PublicKey))
	if nodeAddress == nil {
		return ""
	}
	return net.IP(nodeAddress[:]).String()
}

// yggPeer is one peer link of a node, for YggdrasilPeers.
type yggPeer struct {
	URI       string `json:"uri"`
	Up        bool   `json:"up"`
	LatencyMs int64  `json:"latency_ms,omitempty"`
	Error     string `json:"error,omitempty"`
}

// YggdrasilPeers is the state of the peer links of i's Yggdrasil endpoint tag, as JSON: [{"uri", "up",
// "latency_ms", "error"}], configured peers only. The app checks a node's public peers with a node of its own (a
// throwaway key) dialling them as the profile would, and takes the ones that came up.
func YggdrasilPeers(i *BoxInstance, tag string) (string, error) {
	if i == nil || i.Box == nil {
		return "", errors.New("no instance")
	}
	ep, ok := i.Box.Endpoint().Get(tag)
	if !ok {
		return "", errors.New("no endpoint " + tag)
	}
	node, ok := ep.(interface{ Peers() []core.PeerInfo })
	if !ok {
		return "", errors.New(tag + " is not a Yggdrasil endpoint")
	}
	out := []yggPeer{}
	for _, p := range node.Peers() {
		if p.Inbound {
			continue
		}
		peer := yggPeer{URI: p.URI, Up: p.Up}
		if p.Up {
			peer.LatencyMs = max(p.Latency.Milliseconds(), 1)
		} else if p.LastError != nil {
			peer.Error = p.LastError.Error()
		}
		out = append(out, peer)
	}
	raw, err := json.Marshal(out)
	return string(raw), err
}

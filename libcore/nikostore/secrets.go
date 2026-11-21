package nikostore

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"strings"
)

// Sealer wraps a profile's secret fields for the disk (docs/design/desktop-stage3.md §4, «Секреты», decision 6): on
// Windows DPAPI in the daemon's own context, which is SYSTEM's for the service; elsewhere none, and the directory's
// permissions alone keep the file (0700 and 0600, root's for the service).
type Sealer interface {
	// Name labels what it sealed on the disk ("dpapi"); "" for none: the secrets stay in the bean
	Name() string
	Seal(plain []byte) ([]byte, error)
	Open(sealed []byte) ([]byte, error)
}

// DefaultSealer is the platform's: DPAPI on Windows, none elsewhere.
func DefaultSealer() Sealer { return platformSealer() }

// NoSealer seals nothing.
type NoSealer struct{}

func (NoSealer) Name() string                       { return "" }
func (NoSealer) Seal(plain []byte) ([]byte, error)  { return plain, nil }
func (NoSealer) Open(sealed []byte) ([]byte, error) { return sealed, nil }

// secretFields are the beans' fields the disk holds sealed: whatever authenticates to a server or opens its traffic
// (passwords, UUIDs, tokens, Hysteria's auth and obfuscation, Trojan-Go's Shadowsocks layer, a REALITY short ID, the
// WireGuard and SSH keys, a DNS tunnel's shared key and zone key, the Yggdrasil seed, a knock's payloads) and what the
// user wrote whole (a custom config or outbound, TUIC's custom JSON, a naive's extra headers, a Shadowsocks plugin's
// options), which may carry any of those. The JSON names of nikofmt's beans, the app's field names.
var secretFields = map[string]bool{
	"password":             true,
	"uuid":                 true,
	"token":                true,
	"authPayload":          true,
	"obfuscation":          true,
	"encryption":           true,
	"realityShortId":       true,
	"privateKey":           true,
	"privateKeyPassphrase": true,
	"peerPreSharedKey":     true,
	"dnsTunnelKey":         true,
	"dnsTunnelSeal":        true,
	"yggKey":               true,
	"knockSequence":        true,
	"customOutboundJson":   true,
	"customConfigJson":     true,
	"customJSON":           true,
	"config":               true,
	"extraHeaders":         true,
	"plugin":               true,
}

// errSealed is a profile whose secrets this store cannot open: sealed on another machine, by another account, or by
// a sealer this build has not.
var errSealed = errors.New("sealed by another machine or account")

// sealBean splits a bean's JSON for the disk: the bean without its secret fields, and those fields sealed as
// "<sealer>:<base64>"; with no sealer, or no secret field set, the bean as it is and nothing sealed. A field left
// empty is no secret and stays.
func sealBean(sealer Sealer, bean json.RawMessage) (json.RawMessage, string, error) {
	if sealer.Name() == "" {
		return bean, "", nil
	}
	var fields map[string]json.RawMessage
	if err := json.Unmarshal(bean, &fields); err != nil {
		return nil, "", err
	}
	secrets := map[string]json.RawMessage{}
	for name, value := range fields {
		if secretFields[name] && string(value) != `""` && string(value) != "null" {
			secrets[name] = value
			delete(fields, name)
		}
	}
	if len(secrets) == 0 {
		return bean, "", nil
	}
	plain, err := json.Marshal(secrets)
	if err != nil {
		return nil, "", err
	}
	sealed, err := sealer.Seal(plain)
	if err != nil {
		return nil, "", err
	}
	public, err := json.Marshal(fields)
	if err != nil {
		return nil, "", err
	}
	return public, sealer.Name() + ":" + base64.StdEncoding.EncodeToString(sealed), nil
}

// openBean is the whole bean of what the disk holds: its secret fields opened and put back. errSealed where this
// sealer cannot open them.
func openBean(sealer Sealer, public json.RawMessage, sealed string) (json.RawMessage, error) {
	if sealed == "" {
		return public, nil
	}
	name, data, found := strings.Cut(sealed, ":")
	if !found || name == "" || name != sealer.Name() {
		return nil, errSealed
	}
	raw, err := base64.StdEncoding.DecodeString(data)
	if err != nil {
		return nil, errSealed
	}
	plain, err := sealer.Open(raw)
	if err != nil {
		return nil, errSealed
	}
	var secrets, fields map[string]json.RawMessage
	if err = json.Unmarshal(plain, &secrets); err != nil {
		return nil, errSealed
	}
	if err = json.Unmarshal(public, &fields); err != nil {
		return nil, err
	}
	for name, value := range secrets {
		fields[name] = value
	}
	return json.Marshal(fields)
}

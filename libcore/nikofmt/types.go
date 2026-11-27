package nikofmt

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"math"
	"strconv"
	"strings"
)

// What a profile is beside its bean, for the daemon's store (libcore/nikostore): the type's name as the app names it,
// the bean read back from its JSON, the name and address a list shows.

// Kind returns the specific human-facing protocol kind (e.g. "vless-reality", "vless-xhttp",
// "hysteria2", "naive-quic", "dnstt", "masterdns", "slipstream", "ygg", "awg") and its primary
// carrier transport ("tcp", "udp", or "dns") for UI badges and raycontrol diagnostics (M3 / Stage 1).
func Kind(b Bean) (kind string, transport string) {
	if b == nil {
		return "", "tcp"
	}
	base := b.base()
	if base.DnsTunnelDomain != nil && !ktIsBlank(*base.DnsTunnelDomain) {
		engine := ""
		if base.DnsTunnel != nil {
			engine = strings.ToLower(ktTrim(*base.DnsTunnel))
		}
		switch engine {
		case "masterdnsvpn", "masterdns":
			return "masterdns", "dns"
		case "slipstream":
			return "slipstream", "dns"
		default:
			return "dnstt", "dns"
		}
	}
	if base.YggPeer != nil && !ktIsBlank(*base.YggPeer) {
		return "ygg", "tcp"
	}
	switch v := b.(type) {
	case *NaiveBean:
		if v.Proto != nil && strings.EqualFold(ktTrim(*v.Proto), "quic") {
			return "naive-quic", "udp"
		}
		return "naive", "tcp"
	case *HysteriaBean:
		if v.ProtocolVersion != nil && *v.ProtocolVersion == 1 {
			return "hysteria", "udp"
		}
		return "hysteria2", "udp"
	case *VMessBean:
		tr := "tcp"
		if v.Type != nil && strings.EqualFold(ktTrim(*v.Type), "quic") {
			tr = "udp"
		}
		if v.IsVLESS() {
			if v.Type != nil && strings.EqualFold(ktTrim(*v.Type), "xhttp") {
				return "vless-xhttp", tr
			}
			if v.RealityPubKey != nil && !ktIsBlank(*v.RealityPubKey) {
				return "vless-reality", tr
			}
			if v.Security != nil && strings.EqualFold(ktTrim(*v.Security), "tls") {
				return "vless-tls", tr
			}
			return "vless", tr
		}
		return "vmess", tr
	case *TuicBean:
		return "tuic", "udp"
	case *WireGuardBean:
		if v.AmneziaWG != nil && !ktIsBlank(*v.AmneziaWG) {
			return "awg", "udp"
		}
		return "wg", "udp"
	case *MieruBean:
		if v.Protocol != nil && strings.EqualFold(ktTrim(*v.Protocol), "udp") {
			return "mieru", "udp"
		}
		return "mieru", "tcp"
	case *TrojanBean:
		return "trojan", "tcp"
	case *TrojanGoBean:
		return "trojan-go", "tcp"
	case *ShadowsocksBean:
		return "ss", "tcp"
	case *AnyTLSBean:
		return "anytls", "tcp"
	case *SSHBean:
		return "ssh", "tcp"
	case *SOCKSBean:
		return "socks", "tcp"
	case *HttpBean:
		return "http", "tcp"
	case *ConfigBean:
		return "config", "tcp"
	case *ShadowTLSBean:
		return "shadowtls", "tcp"
	case *ChainBean:
		return "chain", "tcp"
	default:
		return TypeName(b), "tcp"
	}
}

// TypeName is the bean's type as fmt/TypeMap.kt names it, and an sn:// link carries it: "vmess" for VLESS too, "http"
// for HTTPS.
func TypeName(b Bean) string {
	switch b.(type) {
	case *SOCKSBean:
		return "socks"
	case *HttpBean:
		return "http"
	case *ShadowsocksBean:
		return "ss"
	case *VMessBean:
		return "vmess"
	case *TrojanBean:
		return "trojan"
	case *TrojanGoBean:
		return "trojan-go"
	case *MieruBean:
		return "mieru"
	case *NaiveBean:
		return "naive"
	case *HysteriaBean:
		return "hysteria"
	case *SSHBean:
		return "ssh"
	case *WireGuardBean:
		return "wg"
	case *TuicBean:
		return "tuic"
	case *AnyTLSBean:
		return "anytls"
	case *ConfigBean:
		return "config"
	case *ShadowTLSBean:
		return "shadowtls"
	case *ChainBean:
		return "chain"
	}
	return ""
}

// NewBean is an empty bean of a type TypeMap names, for a profile's JSON to be read into. ErrUnsupported for neko (a
// plugin's profile, which comes with the plugins) and a name TypeMap has not.
func NewBean(typ string) (Bean, error) {
	switch typ {
	case "socks":
		return &SOCKSBean{}, nil
	case "http":
		return &HttpBean{}, nil
	case "ss":
		return &ShadowsocksBean{}, nil
	case "vmess":
		return &VMessBean{}, nil
	case "trojan":
		return &TrojanBean{}, nil
	case "trojan-go":
		return &TrojanGoBean{}, nil
	case "mieru":
		return &MieruBean{}, nil
	case "naive":
		return &NaiveBean{}, nil
	case "hysteria":
		return &HysteriaBean{}, nil
	case "ssh":
		return &SSHBean{}, nil
	case "wg":
		return &WireGuardBean{}, nil
	case "tuic":
		return &TuicBean{}, nil
	case "anytls":
		return &AnyTLSBean{}, nil
	case "config":
		return &ConfigBean{}, nil
	case "shadowtls":
		return &ShadowTLSBean{}, nil
	case "chain":
		return &ChainBean{}, nil
	}
	return nil, ErrUnsupported
}

// DecodeBean reads a bean's JSON, as the app's Gson writes it and the golden fixtures hold it, into the bean of the
// type it is said to be, then applies the bean's defaults. Strict where Gson is not: a field the bean has not, a value
// of another type or anything after the object is an error, so a form's typo is not saved as a profile without it.
// Field names match without regard to case, as encoding/json matches them.
func DecodeBean(typ string, data []byte) (Bean, error) {
	bean, err := NewBean(typ)
	if err != nil {
		return nil, err
	}
	if trimmed := bytes.TrimSpace(data); len(trimmed) == 0 || trimmed[0] != '{' {
		return nil, errors.New("a bean is a JSON object")
	}
	decoder := json.NewDecoder(bytes.NewReader(data))
	decoder.DisallowUnknownFields()
	if err = decoder.Decode(bean); err != nil {
		return nil, err
	}
	if _, err = decoder.Token(); err != io.EOF {
		return nil, errors.New("more after the bean's object")
	}
	bean.InitializeDefaultValues()
	return bean, nil
}

// DisplayName is AbstractBean.displayName: the profile's name, or its address where the name is blank; a custom
// config's is "Custom " and its hashCode (ConfigBean.displayName: Arrays.hashCode of its Kryo without the name).
func DisplayName(b Bean) string {
	if name := b.base().Name; name != nil && !ktIsBlank(*name) {
		return *name
	}
	if _, ok := b.(*ConfigBean); ok {
		_, data, err := kryoSerialize(b, true)
		if err != nil {
			return "Custom" // the app's hashCode throws
		}
		h := int32(1)
		for _, c := range data {
			h = 31*h + int32(int8(c))
		}
		if h < 0 && h != math.MinInt32 {
			h = -h // Math.abs, which leaves Integer.MIN_VALUE as it is
		}
		return "Custom " + strconv.Itoa(int(h))
	}
	return DisplayAddress(b)
}

// DisplayAddress is AbstractBean.displayAddress, wrapIPV6Host(serverAddress) + ":" + serverPort; Hysteria's is its
// ports (HysteriaBean.displayAddress), a custom config's none (InternalBean). A field left null reads "null", as Java's string concatenation writes it.
func DisplayAddress(b Bean) string {
	if _, ok := b.(*ConfigBean); ok {
		return "" // InternalBean.displayAddress
	}
	base := b.base()
	address := "null"
	if base.ServerAddress != nil {
		address = wrapIPv6Host(*base.ServerAddress)
	}
	if h, ok := b.(*HysteriaBean); ok {
		return address + ":" + javaString(h.ServerPorts)
	}
	port := "null"
	if base.ServerPort != nil {
		port = strconv.Itoa(*base.ServerPort)
	}
	return address + ":" + port
}

func javaString(s *string) string {
	if s == nil {
		return "null"
	}
	return *s
}

// wrapIPv6Host is ktx wrapIPV6Host: an IPv6 address in brackets, anything else as it is.
func wrapIPv6Host(s string) string {
	if unwrapped := unwrapIPv6Host(s); isIPv6Address(unwrapped) {
		return "[" + unwrapped + "]"
	}
	return s
}

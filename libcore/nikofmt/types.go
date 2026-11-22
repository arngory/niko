package nikofmt

import (
	"bytes"
	"encoding/json"
	"errors"
	"io"
	"math"
	"strconv"
)

// What a profile is beside its bean, for the daemon's store (libcore/nikostore): the type's name as the app names it,
// the bean read back from its JSON, the name and address a list shows.

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

package nikofmt

// The other profile beans, as beans.go has them: the Java field names, Gson's order, initializeDefaultValues ported
// as it is.

// SOCKSBean is fmt/socks/SOCKSBean.java.
type SOCKSBean struct {
	Protocol *int    `json:"protocol,omitempty"` // SOCKS4 0, SOCKS4A 1, SOCKS5 2
	SUoT     *bool   `json:"sUoT,omitempty"`
	Username *string `json:"username,omitempty"`
	Password *string `json:"password,omitempty"`
	AbstractBean
}

// SOCKSBean's protocols.
const (
	ProtocolSOCKS4  = 0
	ProtocolSOCKS4A = 1
	ProtocolSOCKS5  = 2
)

// InitializeDefaultValues is SOCKSBean.initializeDefaultValues.
func (b *SOCKSBean) InitializeDefaultValues() {
	b.AbstractBean.InitializeDefaultValues()
	setDefault(&b.Protocol, ProtocolSOCKS5)
	setDefault(&b.Username, "")
	setDefault(&b.Password, "")
	setDefault(&b.SUoT, false)
}

// ShadowsocksBean is fmt/shadowsocks/ShadowsocksBean.java.
type ShadowsocksBean struct {
	Method   *string `json:"method,omitempty"`
	Password *string `json:"password,omitempty"`
	Plugin   *string `json:"plugin,omitempty"` // "<name>;<options>"
	SUoT     *bool   `json:"sUoT,omitempty"`
	AbstractBean
}

// InitializeDefaultValues is ShadowsocksBean.initializeDefaultValues.
func (b *ShadowsocksBean) InitializeDefaultValues() {
	b.AbstractBean.InitializeDefaultValues()
	setBlankDefault(&b.Method, "aes-256-gcm")
	setDefault(&b.Password, "")
	setDefault(&b.Plugin, "")
	setDefault(&b.SUoT, false)
}

// TrojanGoBean is fmt/trojan_go/TrojanGoBean.java.
type TrojanGoBean struct {
	Password      *string `json:"password,omitempty"`
	Sni           *string `json:"sni,omitempty"`
	Type          *string `json:"type,omitempty"` // "original" or "ws"
	Host          *string `json:"host,omitempty"`
	Path          *string `json:"path,omitempty"`
	Encryption    *string `json:"encryption,omitempty"` // "none" or "ss;<method>:<password>"
	Plugin        *string `json:"plugin,omitempty"`
	AllowInsecure *bool   `json:"allowInsecure,omitempty"`
	Mux           *bool   `json:"mux,omitempty"`
	AbstractBean
}

// InitializeDefaultValues is TrojanGoBean.initializeDefaultValues.
func (b *TrojanGoBean) InitializeDefaultValues() {
	b.AbstractBean.InitializeDefaultValues()
	setDefault(&b.Mux, false)
	setDefault(&b.Password, "")
	setDefault(&b.Sni, "")
	setBlankDefault(&b.Type, "original")
	setDefault(&b.Host, "")
	setDefault(&b.Path, "")
	setBlankDefault(&b.Encryption, "none")
	setDefault(&b.Plugin, "")
	setDefault(&b.AllowInsecure, false)
}

// MieruBean is fmt/mieru/MieruBean.java.
type MieruBean struct {
	Protocol       *string `json:"protocol,omitempty"` // "TCP" or "UDP"
	Username       *string `json:"username,omitempty"`
	Password       *string `json:"password,omitempty"`
	Mtu            *int    `json:"mtu,omitempty"`
	Multiplexing   *string `json:"multiplexing,omitempty"`
	HandshakeMode  *string `json:"handshakeMode,omitempty"`
	TrafficPattern *string `json:"trafficPattern,omitempty"`
	AbstractBean
}

// InitializeDefaultValues is MieruBean.initializeDefaultValues.
func (b *MieruBean) InitializeDefaultValues() {
	b.AbstractBean.InitializeDefaultValues()
	setDefault(&b.Protocol, "TCP")
	setDefault(&b.Username, "")
	setDefault(&b.Password, "")
	setDefault(&b.Mtu, 1400)
	setDefault(&b.Multiplexing, "")
	setDefault(&b.HandshakeMode, "")
	setDefault(&b.TrafficPattern, "")
}

// TuicBean is fmt/tuic/TuicBean.java.
type TuicBean struct {
	Token                *string `json:"token,omitempty"`
	CaText               *string `json:"caText,omitempty"`
	UdpRelayMode         *string `json:"udpRelayMode,omitempty"`
	CongestionController *string `json:"congestionController,omitempty"`
	Alpn                 *string `json:"alpn,omitempty"`
	DisableSNI           *bool   `json:"disableSNI,omitempty"`
	ReduceRTT            *bool   `json:"reduceRTT,omitempty"`
	Mtu                  *int    `json:"mtu,omitempty"`
	Sni                  *string `json:"sni,omitempty"`
	FastConnect          *bool   `json:"fastConnect,omitempty"`
	AllowInsecure        *bool   `json:"allowInsecure,omitempty"`
	CustomJSON           *string `json:"customJSON,omitempty"`
	ProtocolVersion      *int    `json:"protocolVersion,omitempty"`
	Uuid                 *string `json:"uuid,omitempty"`
	AbstractBean
}

// InitializeDefaultValues is TuicBean.initializeDefaultValues.
func (b *TuicBean) InitializeDefaultValues() {
	b.AbstractBean.InitializeDefaultValues()
	setDefault(&b.Token, "")
	setDefault(&b.CaText, "")
	setDefault(&b.UdpRelayMode, "native")
	setDefault(&b.CongestionController, "cubic")
	setDefault(&b.Alpn, "")
	setDefault(&b.DisableSNI, false)
	setDefault(&b.ReduceRTT, false)
	setDefault(&b.Mtu, 1400)
	setDefault(&b.Sni, "")
	setDefault(&b.FastConnect, false)
	setDefault(&b.AllowInsecure, false)
	setDefault(&b.CustomJSON, "")
	setDefault(&b.ProtocolVersion, 5)
	setDefault(&b.Uuid, "")
}

// AnyTLSBean is proxy/anytls/AnyTLSBean.java.
type AnyTLSBean struct {
	Password        *string `json:"password,omitempty"`
	Sni             *string `json:"sni,omitempty"`
	Alpn            *string `json:"alpn,omitempty"`
	Certificates    *string `json:"certificates,omitempty"`
	UtlsFingerprint *string `json:"utlsFingerprint,omitempty"`
	AllowInsecure   *bool   `json:"allowInsecure,omitempty"`
	EchConfig       *string `json:"echConfig,omitempty"`
	AbstractBean
}

// InitializeDefaultValues is AnyTLSBean.initializeDefaultValues.
func (b *AnyTLSBean) InitializeDefaultValues() {
	b.AbstractBean.InitializeDefaultValues()
	setDefault(&b.Password, "")
	setDefault(&b.Sni, "")
	setDefault(&b.Alpn, "")
	setDefault(&b.Certificates, "")
	setDefault(&b.UtlsFingerprint, "")
	setDefault(&b.AllowInsecure, false)
	setDefault(&b.EchConfig, "")
}

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

// SSHBean is fmt/ssh/SSHBean.java (no share link of its own: sn:// only).
type SSHBean struct {
	Username             *string `json:"username,omitempty"`
	AuthType             *int    `json:"authType,omitempty"` // none 0, password 1, private key 2
	Password             *string `json:"password,omitempty"`
	PrivateKey           *string `json:"privateKey,omitempty"`
	PrivateKeyPassphrase *string `json:"privateKeyPassphrase,omitempty"`
	PublicKey            *string `json:"publicKey,omitempty"`
	AbstractBean
}

// SSHBean's authentications.
const (
	SSHAuthNone       = 0
	SSHAuthPassword   = 1
	SSHAuthPrivateKey = 2
)

// InitializeDefaultValues is SSHBean.initializeDefaultValues: port 22 before the superclass's 1080.
func (b *SSHBean) InitializeDefaultValues() {
	setDefault(&b.ServerPort, 22)
	b.AbstractBean.InitializeDefaultValues()
	setDefault(&b.Username, "root")
	setDefault(&b.AuthType, SSHAuthPassword)
	setDefault(&b.Password, "")
	setDefault(&b.PrivateKey, "")
	setDefault(&b.PrivateKeyPassphrase, "")
	setDefault(&b.PublicKey, "")
}

// WireGuardBean is fmt/wireguard/WireGuardBean.java, AmneziaWG's parameters with it (sn:// only here; a .conf is a
// subscription's).
type WireGuardBean struct {
	LocalAddress        *string `json:"localAddress,omitempty"`
	PrivateKey          *string `json:"privateKey,omitempty"`
	PeerPublicKey       *string `json:"peerPublicKey,omitempty"`
	PeerPreSharedKey    *string `json:"peerPreSharedKey,omitempty"`
	Mtu                 *int    `json:"mtu,omitempty"`
	Reserved            *string `json:"reserved,omitempty"`
	PersistentKeepalive *int    `json:"persistentKeepalive,omitempty"`
	AmneziaWG           *string `json:"amneziaWG,omitempty"`
	AbstractBean
}

// InitializeDefaultValues is WireGuardBean.initializeDefaultValues.
func (b *WireGuardBean) InitializeDefaultValues() {
	b.AbstractBean.InitializeDefaultValues()
	setDefault(&b.LocalAddress, "")
	setDefault(&b.PrivateKey, "")
	setDefault(&b.PeerPublicKey, "")
	setDefault(&b.PeerPreSharedKey, "")
	setDefault(&b.Mtu, 1420)
	setDefault(&b.Reserved, "")
	setDefault(&b.PersistentKeepalive, 0)
	setDefault(&b.AmneziaWG, "")
}

// ConfigBean is proxy/config/ConfigBean.java: a sing-box config (type 0) or outbound (type 1) of the user's own.
type ConfigBean struct {
	Type   *int    `json:"type,omitempty"`
	Config *string `json:"config,omitempty"`
	AbstractBean
}

// InitializeDefaultValues is ConfigBean.initializeDefaultValues.
func (b *ConfigBean) InitializeDefaultValues() {
	b.AbstractBean.InitializeDefaultValues()
	setDefault(&b.Type, 0)
	setDefault(&b.Config, "")
}

// ShadowTLSBean is proxy/shadowtls/ShadowTLSBean.java (Stage 5 parity).
type ShadowTLSBean struct {
	Version         *int    `json:"version,omitempty"` // 1, 2, or 3
	Password        *string `json:"password,omitempty"`
	Sni             *string `json:"sni,omitempty"`
	Alpn            *string `json:"alpn,omitempty"`
	UtlsFingerprint *string `json:"utlsFingerprint,omitempty"`
	AllowInsecure   *bool   `json:"allowInsecure,omitempty"`
	AbstractBean
}

func (b *ShadowTLSBean) InitializeDefaultValues() {
	setDefault(&b.ServerPort, 443)
	b.AbstractBean.InitializeDefaultValues()
	setDefault(&b.Version, 3)
	setDefault(&b.Password, "")
	setDefault(&b.Sni, "")
	setDefault(&b.Alpn, "")
	setDefault(&b.UtlsFingerprint, "chrome")
	setDefault(&b.AllowInsecure, false)
}

// ChainBean is fmt/internal/ChainBean.java: ordered list of profile IDs chained together (D-PROFILE-04).
type ChainBean struct {
	Proxies []int64 `json:"proxies,omitempty"`
	AbstractBean
}

func (b *ChainBean) InitializeDefaultValues() {
	b.AbstractBean.InitializeDefaultValues()
}

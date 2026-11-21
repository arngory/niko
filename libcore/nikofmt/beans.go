package nikofmt

import "strings"

// The app's profile beans (app/src/main/java/io/niko/fmt/**/…Bean.java and proxy/**/…Bean.java: naive, Hysteria and
// the V2Ray family here, the others in beans_more.go) as Go structs that marshal to the JSON the app's Gson writes
// of them (JavaUtil.gson): the Java field names; a boxed field Java leaves null is a nil pointer, left out as Gson
// leaves null fields out. Fields are in Gson's order, the class's own before its superclass's.
// initializeDefaultValues is ported as it is, null checks and blank checks where Java has each.

// Bean is a parsed profile: one of the …Bean types of this package, always a pointer.
type Bean interface {
	// InitializeDefaultValues is the bean's initializeDefaultValues().
	InitializeDefaultValues()
	base() *AbstractBean
}

// AbstractBean is fmt/AbstractBean.java: the server and what every profile has (port knocking, certificate pins, DNS
// tunnels, UDP desync, Yggdrasil). finalAddress and finalPort are transient there, so not here.
type AbstractBean struct {
	ServerAddress *string `json:"serverAddress,omitempty"`
	ServerPort    *int    `json:"serverPort,omitempty"`
	Name          *string `json:"name,omitempty"`

	CustomOutboundJson     *string `json:"customOutboundJson,omitempty"`
	CustomConfigJson       *string `json:"customConfigJson,omitempty"`
	KnockSequence          *string `json:"knockSequence,omitempty"`
	KnockInterval          *int    `json:"knockInterval,omitempty"`
	KnockTtl               *int    `json:"knockTtl,omitempty"`
	KnockSilence           *int    `json:"knockSilence,omitempty"`
	TlsFragment            *int    `json:"tlsFragment,omitempty"`
	CertPins               *string `json:"certPins,omitempty"`
	DnsTunnel              *string `json:"dnsTunnel,omitempty"`
	DnsTunnelResolver      *string `json:"dnsTunnelResolver,omitempty"`
	DnsTunnelDomain        *string `json:"dnsTunnelDomain,omitempty"`
	DnsTunnelKey           *string `json:"dnsTunnelKey,omitempty"`
	DnsTunnelMethod        *int    `json:"dnsTunnelMethod,omitempty"`
	DnsTunnelFixedUpstream *bool   `json:"dnsTunnelFixedUpstream,omitempty"`
	DnsTunnelAuth          *string `json:"dnsTunnelAuth,omitempty"`
	DnsTunnelSeal          *string `json:"dnsTunnelSeal,omitempty"`
	DnsTunnelSpare         *string `json:"dnsTunnelSpare,omitempty"`
	DnsTunnelTls           *string `json:"dnsTunnelTls,omitempty"`
	UdpPrelude             *int    `json:"udpPrelude,omitempty"`
	UdpPreludeSni          *string `json:"udpPreludeSni,omitempty"`
	UdpPreludeTtl          *int    `json:"udpPreludeTtl,omitempty"`
	UdpReorderInitial      *bool   `json:"udpReorderInitial,omitempty"`
	YggPeer                *string `json:"yggPeer,omitempty"`
	YggKey                 *string `json:"yggKey,omitempty"`
	YggVia                 *string `json:"yggVia,omitempty"`
}

func (b *AbstractBean) base() *AbstractBean { return b }

// InitializeDefaultValues is AbstractBean.initializeDefaultValues.
func (b *AbstractBean) InitializeDefaultValues() {
	if b.ServerAddress == nil || ktIsBlank(*b.ServerAddress) {
		b.ServerAddress = ptr("127.0.0.1")
	} else if strings.HasPrefix(*b.ServerAddress, "[") && strings.HasSuffix(*b.ServerAddress, "]") {
		b.ServerAddress = ptr(unwrapIPv6Host(*b.ServerAddress))
	}
	setDefault(&b.ServerPort, 1080)
	setDefault(&b.Name, "")
	setDefault(&b.CustomOutboundJson, "")
	setDefault(&b.CustomConfigJson, "")
	setDefault(&b.KnockSequence, "")
	setDefault(&b.KnockInterval, 200)
	setDefault(&b.KnockTtl, 10)
	setDefault(&b.KnockSilence, 15)
	setDefault(&b.TlsFragment, 0)
	setDefault(&b.CertPins, "")
	setDefault(&b.DnsTunnel, "")
	setDefault(&b.DnsTunnelResolver, "")
	setDefault(&b.DnsTunnelDomain, "")
	setDefault(&b.DnsTunnelKey, "")
	setDefault(&b.DnsTunnelMethod, 2)
	setDefault(&b.DnsTunnelFixedUpstream, false)
	setDefault(&b.DnsTunnelAuth, "")
	setDefault(&b.DnsTunnelSeal, "")
	setDefault(&b.DnsTunnelSpare, "")
	setDefault(&b.DnsTunnelTls, "")
	setDefault(&b.UdpPrelude, 0)
	setDefault(&b.UdpPreludeSni, "")
	setDefault(&b.UdpPreludeTtl, 0)
	setDefault(&b.YggPeer, "")
	setDefault(&b.YggKey, "")
	setDefault(&b.YggVia, "")
	setDefault(&b.UdpReorderInitial, false)
}

// unwrapIPv6Host is ktx unwrapIPV6Host: "[...]" without its brackets, again while there are some.
func unwrapIPv6Host(s string) string {
	for strings.HasPrefix(s, "[") && strings.HasSuffix(s, "]") && len(s) >= 2 {
		s = s[1 : len(s)-1]
	}
	return s
}

// NaiveBean is fmt/naive/NaiveBean.java.
type NaiveBean struct {
	Proto               *string `json:"proto,omitempty"`
	Username            *string `json:"username,omitempty"`
	Password            *string `json:"password,omitempty"`
	ExtraHeaders        *string `json:"extraHeaders,omitempty"`
	Sni                 *string `json:"sni,omitempty"`
	Certificates        *string `json:"certificates,omitempty"`
	InsecureConcurrency *int    `json:"insecureConcurrency,omitempty"`
	SUoT                *bool   `json:"sUoT,omitempty"`
	NikoIcmp            *bool   `json:"nikoIcmp,omitempty"`
	NikoAck             *bool   `json:"nikoAck,omitempty"`
	NikoUdp             *bool   `json:"nikoUdp,omitempty"`
	AbstractBean
}

// InitializeDefaultValues is NaiveBean.initializeDefaultValues: port 443 before the superclass's 1080.
func (b *NaiveBean) InitializeDefaultValues() {
	setDefault(&b.ServerPort, 443)
	b.AbstractBean.InitializeDefaultValues()
	setDefault(&b.Proto, "https")
	setDefault(&b.Username, "")
	setDefault(&b.Password, "")
	setDefault(&b.ExtraHeaders, "")
	setDefault(&b.Certificates, "")
	setDefault(&b.Sni, "")
	setDefault(&b.InsecureConcurrency, 0)
	setDefault(&b.SUoT, false)
	setDefault(&b.NikoIcmp, false)
	setDefault(&b.NikoAck, false)
	setDefault(&b.NikoUdp, false)
}

// HysteriaBean is fmt/hysteria/HysteriaBean.java (Hysteria2 links: protocolVersion 2).
type HysteriaBean struct {
	ProtocolVersion         *int    `json:"protocolVersion,omitempty"`
	ServerPorts             *string `json:"serverPorts,omitempty"`
	AuthPayload             *string `json:"authPayload,omitempty"`
	Obfuscation             *string `json:"obfuscation,omitempty"`
	Sni                     *string `json:"sni,omitempty"`
	CaText                  *string `json:"caText,omitempty"`
	UploadMbps              *int    `json:"uploadMbps,omitempty"`
	DownloadMbps            *int    `json:"downloadMbps,omitempty"`
	AllowInsecure           *bool   `json:"allowInsecure,omitempty"`
	StreamReceiveWindow     *int    `json:"streamReceiveWindow,omitempty"`
	ConnectionReceiveWindow *int    `json:"connectionReceiveWindow,omitempty"`
	DisableMtuDiscovery     *bool   `json:"disableMtuDiscovery,omitempty"`
	HopInterval             *int    `json:"hopInterval,omitempty"`
	HopIntervalMax          *int    `json:"hopIntervalMax,omitempty"`
	NikoAck                 *bool   `json:"nikoAck,omitempty"`
	Alpn                    *string `json:"alpn,omitempty"`
	AuthPayloadType         *int    `json:"authPayloadType,omitempty"`
	Protocol                *int    `json:"protocol,omitempty"`
	AbstractBean
}

// InitializeDefaultValues is HysteriaBean.initializeDefaultValues. serverPort is not one of its fields (serverPorts
// is): a parsed link leaves it to the superclass's 1080, as the app does.
func (b *HysteriaBean) InitializeDefaultValues() {
	b.AbstractBean.InitializeDefaultValues()
	setDefault(&b.ProtocolVersion, 2)
	setDefault(&b.AuthPayloadType, 0)
	setDefault(&b.AuthPayload, "")
	setDefault(&b.Protocol, 0)
	setDefault(&b.Obfuscation, "")
	setDefault(&b.Sni, "")
	setDefault(&b.Alpn, "")
	setDefault(&b.CaText, "")
	setDefault(&b.AllowInsecure, false)
	if *b.ProtocolVersion == 1 {
		setDefault(&b.UploadMbps, 10)
		setDefault(&b.DownloadMbps, 50)
	} else {
		setDefault(&b.UploadMbps, 0)
		setDefault(&b.DownloadMbps, 0)
	}
	setDefault(&b.StreamReceiveWindow, 0)
	setDefault(&b.ConnectionReceiveWindow, 0)
	setDefault(&b.DisableMtuDiscovery, false)
	setDefault(&b.HopInterval, 10)
	setDefault(&b.HopIntervalMax, 0)
	setDefault(&b.NikoAck, false)
	setDefault(&b.ServerPorts, "443")
}

// StandardV2RayBean is fmt/v2ray/StandardV2RayBean.java, what VMess, VLESS, Trojan and HTTP share: the transport
// and TLS.
type StandardV2RayBean struct {
	Uuid                 *string `json:"uuid,omitempty"`
	Encryption           *string `json:"encryption,omitempty"` // VMess security, VLESS flow
	Type                 *string `json:"type,omitempty"`
	Host                 *string `json:"host,omitempty"`
	Path                 *string `json:"path,omitempty"`
	Security             *string `json:"security,omitempty"`
	Sni                  *string `json:"sni,omitempty"`
	Alpn                 *string `json:"alpn,omitempty"`
	UtlsFingerprint      *string `json:"utlsFingerprint,omitempty"`
	AllowInsecure        *bool   `json:"allowInsecure,omitempty"`
	RealityPubKey        *string `json:"realityPubKey,omitempty"`
	RealityShortId       *string `json:"realityShortId,omitempty"`
	RealityKeyShare      *string `json:"realityKeyShare,omitempty"`
	VlessEncryption      *string `json:"vlessEncryption,omitempty"`
	RealityMldsa65Verify *string `json:"realityMldsa65Verify,omitempty"`
	WsMaxEarlyData       *int    `json:"wsMaxEarlyData,omitempty"`
	EarlyDataHeaderName  *string `json:"earlyDataHeaderName,omitempty"`
	XhttpMode            *string `json:"xhttpMode,omitempty"`
	XhttpExtra           *string `json:"xhttpExtra,omitempty"`
	Certificates         *string `json:"certificates,omitempty"`
	EnableECH            *bool   `json:"enableECH,omitempty"`
	EchConfig            *string `json:"echConfig,omitempty"`
	EnableMux            *bool   `json:"enableMux,omitempty"`
	MuxPadding           *bool   `json:"muxPadding,omitempty"`
	MuxType              *int    `json:"muxType,omitempty"`
	MuxConcurrency       *int    `json:"muxConcurrency,omitempty"`
	PacketEncoding       *int    `json:"packetEncoding,omitempty"` // 1 packetaddr, 2 xudp
	AbstractBean
}

// initializeDefaultValues is StandardV2RayBean.initializeDefaultValues; a Trojan bean's security defaults to "tls".
// Most strings there are checked for blank, not null, so a blank one from a link becomes the default too.
func (b *StandardV2RayBean) initializeDefaultValues(trojan bool) {
	b.AbstractBean.InitializeDefaultValues()
	setBlankDefault(&b.Uuid, "")
	if b.Type == nil || ktIsBlank(*b.Type) {
		b.Type = ptr("tcp")
	} else if *b.Type == "h2" {
		b.Type = ptr("http")
	}
	// type.toLowerCase() in the default locale: Locale.ROOT's here (a Turkish one would make "I" dotless)
	b.Type = ptr(ktLowercase(*b.Type))
	setBlankDefault(&b.Host, "")
	setBlankDefault(&b.Path, "")
	if trojan {
		setBlankDefault(&b.Security, "tls")
	} else {
		setBlankDefault(&b.Security, "none")
	}
	setBlankDefault(&b.Sni, "")
	setBlankDefault(&b.Alpn, "")
	setBlankDefault(&b.Certificates, "")
	setBlankDefault(&b.EarlyDataHeaderName, "")
	setBlankDefault(&b.UtlsFingerprint, "")
	setDefault(&b.WsMaxEarlyData, 0)
	setDefault(&b.AllowInsecure, false)
	setDefault(&b.PacketEncoding, 0)
	setDefault(&b.RealityPubKey, "")
	setDefault(&b.RealityShortId, "")
	setDefault(&b.RealityKeyShare, "")
	setDefault(&b.VlessEncryption, "")
	setDefault(&b.RealityMldsa65Verify, "")
	setDefault(&b.EnableECH, false)
	setBlankDefault(&b.EchConfig, "")
	setDefault(&b.EnableMux, false)
	setDefault(&b.MuxPadding, false)
	setDefault(&b.MuxType, 0)
	setDefault(&b.MuxConcurrency, 1)
	setBlankDefault(&b.XhttpMode, "auto")
	setDefault(&b.XhttpExtra, "")
}

// VMessBean is fmt/v2ray/VMessBean.java: VMess, and VLESS where alterId is -1.
type VMessBean struct {
	AlterId *int `json:"alterId,omitempty"`
	StandardV2RayBean
}

// IsVLESS is StandardV2RayBean.isVLESS.
func (b *VMessBean) IsVLESS() bool { return b.AlterId != nil && *b.AlterId == -1 }

// InitializeDefaultValues is VMessBean.initializeDefaultValues over StandardV2RayBean's.
func (b *VMessBean) InitializeDefaultValues() {
	b.StandardV2RayBean.initializeDefaultValues(false)
	setDefault(&b.AlterId, 0)
	if *b.AlterId == -1 {
		setBlankDefault(&b.Encryption, "")
	} else {
		setBlankDefault(&b.Encryption, "auto")
	}
}

// TrojanBean is fmt/trojan/TrojanBean.java.
type TrojanBean struct {
	Password *string `json:"password,omitempty"`
	StandardV2RayBean
}

// InitializeDefaultValues is TrojanBean.initializeDefaultValues.
func (b *TrojanBean) InitializeDefaultValues() {
	b.StandardV2RayBean.initializeDefaultValues(true)
	if b.Security == nil || *b.Security == "" {
		b.Security = ptr("tls")
	}
	setDefault(&b.Password, "")
}

// HttpBean is fmt/http/HttpBean.java: an HTTP proxy, HTTPS where security is "tls".
type HttpBean struct {
	Username *string `json:"username,omitempty"`
	Password *string `json:"password,omitempty"`
	StandardV2RayBean
}

// InitializeDefaultValues is HttpBean.initializeDefaultValues.
func (b *HttpBean) InitializeDefaultValues() {
	b.StandardV2RayBean.initializeDefaultValues(false)
	setDefault(&b.Username, "")
	setDefault(&b.Password, "")
}

func ptr[T any](v T) *T { return &v }

// setDefault is Java's "if (field == null) field = value".
func setDefault[T any](field **T, value T) {
	if *field == nil {
		*field = &value
	}
}

// setBlankDefault is Java's "if (JavaUtil.isNullOrBlank(field)) field = value".
func setBlankDefault(field **string, value string) {
	if *field == nil || ktIsBlank(**field) {
		*field = &value
	}
}

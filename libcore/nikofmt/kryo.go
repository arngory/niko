package nikofmt

import (
	"errors"
	"strconv"
	"strings"
	"unicode/utf16"
)

// kryoInput reads what the app's beans write with Kryo 5's ByteBufferOutput (fmt/AbstractBean.java and each bean's
// serialize), as ByteBufferInput reads it through fmt/BoundedKryoInput.java: ints of four bytes, little-endian (Kryo
// 5 writes them so, not big-endian as Kryo 4 did); a boolean is a byte that is 1; a string is ASCII up to a byte
// with its high bit set, or a varint length (0 null, 1 "", else chars + 1) and the chars as Java's modified UTF-8
// (one to three bytes a UTF-16 char). Running out of data is an error, as KryoConverters.strict makes it for a link.
type kryoInput struct {
	b   []byte
	pos int
	// Kryo's char buffer, reused from string to string: a malformed UTF-8 lead byte leaves its char as the buffer
	// had it, as Kryo does
	chars []uint16
}

var errKryo = errors.New("corrupt data: buffer underflow")

func newKryoInput(b []byte) *kryoInput { return &kryoInput{b: b, chars: make([]uint16, 32)} }

func (in *kryoInput) left() int { return len(in.b) - in.pos }

func (in *kryoInput) readInt() (int, error) {
	if in.left() < 4 {
		return 0, errKryo
	}
	b := in.b[in.pos:]
	in.pos += 4
	return int(int32(uint32(b[0]) | uint32(b[1])<<8 | uint32(b[2])<<16 | uint32(b[3])<<24)), nil
}

func (in *kryoInput) readBoolean() (bool, error) {
	if in.left() < 1 {
		return false, errKryo
	}
	in.pos++
	return in.b[in.pos-1] == 1, nil
}

func (in *kryoInput) readByte() (byte, error) {
	if in.left() < 1 {
		return 0, errKryo
	}
	in.pos++
	return in.b[in.pos-1], nil
}

// readVarIntFlag is Input.readVarIntFlag(true): six bits in the first byte (the seventh says another follows, the
// eighth is the flag), seven in each of up to four more.
func (in *kryoInput) readVarIntFlag() (int32, error) {
	b, err := in.readByte()
	if err != nil {
		return 0, err
	}
	result := uint32(b & 0x3f)
	if b&0x40 != 0 {
		for _, shift := range []uint{6, 13, 20, 27} {
			if b, err = in.readByte(); err != nil {
				return 0, err
			}
			result |= uint32(b&0x7f) << shift
			if b&0x80 == 0 {
				break
			}
		}
	}
	return int32(result), nil
}

func (in *kryoInput) readString() (*string, error) {
	if in.left() < 1 {
		return nil, errKryo
	}
	if in.b[in.pos]&0x80 == 0 {
		return in.readASCII()
	}
	count, err := in.readVarIntFlag()
	if err != nil {
		return nil, err
	}
	switch {
	case count == 0:
		return nil, nil
	case count == 1:
		return ptr(""), nil
	case count < 0:
		// a char count past Java's int: an allocation or a String that fails
		return nil, errors.New("corrupt data: a negative string length")
	case int(count-1) > in.left():
		return nil, errors.New("corrupt data: a string longer than the data") // BoundedKryoInput
	}
	n := int(count - 1)
	if len(in.chars) < n {
		in.chars = make([]uint16, n)
	}
	for i := 0; i < n; i++ {
		b, err := in.readByte()
		if err != nil {
			return nil, err
		}
		switch b >> 4 {
		case 0, 1, 2, 3, 4, 5, 6, 7:
			in.chars[i] = uint16(b)
		case 12, 13:
			b2, err := in.readByte()
			if err != nil {
				return nil, err
			}
			in.chars[i] = uint16(b&0x1f)<<6 | uint16(b2&0x3f)
		case 14:
			if in.left() < 2 {
				return nil, errKryo
			}
			b2, b3 := in.b[in.pos], in.b[in.pos+1]
			in.pos += 2
			in.chars[i] = uint16(b&0x0f)<<12 | uint16(b2&0x3f)<<6 | uint16(b3&0x3f)
		}
	}
	s := string(utf16.Decode(in.chars[:n]))
	return &s, nil
}

// readASCII is ByteBufferInput.readAsciiString: bytes up to one with its high bit set, which is the last char.
func (in *kryoInput) readASCII() (*string, error) {
	n := 0
	for {
		b, err := in.readByte()
		if err != nil {
			return nil, err
		}
		if n == len(in.chars) {
			grown := make([]uint16, n*2)
			copy(grown, in.chars)
			in.chars = grown
		}
		if b&0x80 != 0 {
			in.chars[n] = uint16(b & 0x7f)
			s := string(utf16.Decode(in.chars[:n+1]))
			return &s, nil
		}
		in.chars[n] = uint16(b)
		n++
	}
}

// kryoReader reads a bean's fields in order, keeping the first error: each read after it gives nothing.
type kryoReader struct {
	in  *kryoInput
	err error
}

func (r *kryoReader) int() *int {
	if r.err != nil {
		return nil
	}
	v, err := r.in.readInt()
	r.err = err
	return &v
}

func (r *kryoReader) intValue() int {
	if v := r.int(); v != nil {
		return *v
	}
	return 0
}

func (r *kryoReader) bool() *bool {
	if r.err != nil {
		return nil
	}
	v, err := r.in.readBoolean()
	r.err = err
	return &v
}

func (r *kryoReader) string() *string {
	if r.err != nil {
		return nil
	}
	v, err := r.in.readString()
	r.err = err
	return v
}

// abstractBean is AbstractBean.deserialize: the server's address and port.
func (r *kryoReader) abstractBean(b *AbstractBean) {
	b.ServerAddress = r.string()
	b.ServerPort = r.int()
}

// abstractExtra is the rest of AbstractBean.deserializeFromBuffer, after the bean's own fields: its extraVersion,
// then what each version added.
func (r *kryoReader) abstractExtra(b *AbstractBean) {
	extraVersion := r.intValue()
	b.Name = r.string()
	b.CustomOutboundJson = r.string()
	b.CustomConfigJson = r.string()
	if extraVersion >= 2 {
		b.KnockSequence = r.string()
		b.KnockInterval = r.int()
		b.KnockTtl = r.int()
		b.TlsFragment = r.int()
	}
	if extraVersion >= 3 {
		b.KnockSilence = r.int()
	}
	if extraVersion >= 4 {
		b.CertPins = r.string()
	}
	if extraVersion >= 5 {
		b.DnsTunnel = r.string()
		b.DnsTunnelResolver = r.string()
		b.DnsTunnelDomain = r.string()
		b.DnsTunnelKey = r.string()
		b.DnsTunnelMethod = r.int()
		b.DnsTunnelFixedUpstream = r.bool()
	}
	if extraVersion >= 6 {
		b.UdpPrelude = r.int()
		b.UdpPreludeSni = r.string()
		b.UdpPreludeTtl = r.int()
		b.UdpReorderInitial = r.bool()
	}
	if extraVersion >= 7 {
		b.YggPeer = r.string()
		b.YggKey = r.string()
	}
	if extraVersion >= 8 {
		b.YggVia = r.string()
	}
	if extraVersion >= 9 {
		b.DnsTunnelAuth = r.string()
	}
	if extraVersion >= 10 {
		b.DnsTunnelSeal = r.string()
	}
	if extraVersion >= 11 {
		b.DnsTunnelSpare = r.string()
	}
	if extraVersion >= 12 {
		b.DnsTunnelTls = r.string()
	}
}

var errKryoNull = errors.New("corrupt data: a null where the bean needs a value")

func (r *kryoReader) socks(b *SOCKSBean) {
	version := r.intValue()
	r.abstractBean(&b.AbstractBean)
	if version >= 1 {
		b.Protocol = r.int()
	}
	b.Username = r.string()
	b.Password = r.string()
	if version >= 2 {
		b.SUoT = r.bool()
	}
}

func (r *kryoReader) shadowsocks(b *ShadowsocksBean) {
	r.intValue() // version
	r.abstractBean(&b.AbstractBean)
	b.Method = r.string()
	b.Password = r.string()
	b.Plugin = r.string()
	b.SUoT = r.bool()
}

// standardV2Ray is StandardV2RayBean.deserialize; alterId is read for a VMessBean only.
func (r *kryoReader) standardV2Ray(b *StandardV2RayBean, alterID **int) {
	version := r.intValue()
	r.abstractBean(&b.AbstractBean)
	b.Uuid = r.string()
	b.Encryption = r.string()
	if alterID != nil {
		*alterID = r.int()
	}
	b.Type = r.string()
	if r.err != nil {
		return
	}
	if b.Type == nil {
		r.err = errKryoNull // switch on a null String
		return
	}
	if version < 5 && (*b.Type == "xhttp" || *b.Type == "splithttp") {
		// links stored the type as is before version 5, but no transport fields for it
		b.Type = ptr("xhttp")
	} else {
		switch *b.Type {
		case "ws":
			b.Host = r.string()
			b.Path = r.string()
			b.WsMaxEarlyData = r.int()
			b.EarlyDataHeaderName = r.string()
		case "http", "httpupgrade":
			b.Host = r.string()
			b.Path = r.string()
		case "xhttp":
			b.Host = r.string()
			b.Path = r.string()
			b.XhttpMode = r.string()
			b.XhttpExtra = r.string()
		case "grpc":
			b.Path = r.string()
			if version < 4 {
				r.string()
				r.string()
			}
		}
	}
	b.Security = r.string()
	if equals(b.Security, "tls") {
		b.Sni = r.string()
		b.Alpn = r.string()
		b.Certificates = r.string()
		b.AllowInsecure = r.bool()
		b.UtlsFingerprint = r.string()
		b.RealityPubKey = r.string()
		b.RealityShortId = r.string()
	}
	switch {
	case version >= 1:
		b.EnableECH = r.bool()
		if version >= 3 {
			b.EchConfig = r.string()
		} else if b.EnableECH != nil && *b.EnableECH {
			r.bool()
			r.bool()
			b.EchConfig = r.string()
		}
	case version == 0 && r.err == nil:
		// an old version 0 may hold enableECH or not: a packetEncoding of 1 or 2 right here says it does not
		position := r.in.pos
		tmpEnableECH := r.bool()
		tmpPacketEncoding := r.intValue()
		r.in.pos = position
		if r.err == nil && tmpPacketEncoding != 1 && tmpPacketEncoding != 2 {
			b.EnableECH = tmpEnableECH
			if *b.EnableECH {
				r.bool()
				r.bool()
				b.EchConfig = r.string()
			}
		}
	}
	b.PacketEncoding = r.int()
	if version >= 2 {
		b.EnableMux = r.bool()
		b.MuxPadding = r.bool()
		b.MuxType = r.int()
		b.MuxConcurrency = r.int()
	}
	if version >= 6 {
		b.RealityKeyShare = r.string()
	}
	if version >= 7 {
		b.VlessEncryption = r.string()
	}
	if version >= 8 {
		b.RealityMldsa65Verify = r.string()
	}
}

func (r *kryoReader) http(b *HttpBean) {
	r.intValue() // version
	r.standardV2Ray(&b.StandardV2RayBean, nil)
	b.Username = r.string()
	b.Password = r.string()
}

func (r *kryoReader) trojan(b *TrojanBean) {
	version := r.intValue()
	if version >= 2 {
		r.standardV2Ray(&b.StandardV2RayBean, nil)
		b.Password = r.string()
		return
	}
	b.ServerAddress = r.string()
	b.ServerPort = r.int()
	b.Password = r.string()
	b.Security = r.string()
	b.Sni = r.string()
	b.Alpn = r.string()
	if version == 1 {
		b.AllowInsecure = r.bool()
	}
}

func (r *kryoReader) trojanGo(b *TrojanGoBean) {
	version := r.intValue()
	r.abstractBean(&b.AbstractBean)
	b.Password = r.string()
	b.Sni = r.string()
	b.Type = r.string()
	if r.err == nil && b.Type == nil {
		r.err = errKryoNull
	}
	if equals(b.Type, "ws") {
		b.Host = r.string()
		b.Path = r.string()
	}
	b.Encryption = r.string()
	b.Plugin = r.string()
	if version >= 1 {
		b.AllowInsecure = r.bool()
	}
	if version >= 2 {
		b.Mux = r.bool()
	}
}

func (r *kryoReader) mieru(b *MieruBean) {
	version := r.intValue()
	r.abstractBean(&b.AbstractBean)
	b.Protocol = r.string()
	if r.err == nil && b.Protocol == nil {
		r.err = errKryoNull // protocol.equals(…) on null
	}
	b.Username = r.string()
	b.Password = r.string()
	if equals(b.Protocol, "UDP") {
		b.Mtu = r.int()
	}
	if version >= 1 {
		b.Multiplexing = r.string()
		b.HandshakeMode = r.string()
		b.TrafficPattern = r.string()
	}
}

func (r *kryoReader) naive(b *NaiveBean) {
	version := r.intValue()
	r.abstractBean(&b.AbstractBean)
	b.Proto = r.string()
	b.Username = r.string()
	b.Password = r.string()
	b.ExtraHeaders = r.string()
	if version >= 2 {
		b.Certificates = r.string()
		b.Sni = r.string()
	}
	if version >= 1 {
		b.InsecureConcurrency = r.int()
	}
	if version >= 3 {
		b.SUoT = r.bool()
	}
	if version >= 4 {
		b.NikoIcmp = r.bool()
	}
	if version >= 5 {
		b.NikoAck = r.bool()
	}
	if version >= 6 {
		b.NikoUdp = r.bool()
	}
}

func (r *kryoReader) hysteria(b *HysteriaBean) {
	version := r.intValue()
	r.abstractBean(&b.AbstractBean)
	if version >= 7 {
		b.ProtocolVersion = r.int()
	} else {
		b.ProtocolVersion = ptr(1)
	}
	b.AuthPayloadType = r.int()
	b.AuthPayload = r.string()
	if version >= 3 {
		b.Protocol = r.int()
	}
	b.Obfuscation = r.string()
	b.Sni = r.string()
	if version >= 2 {
		b.Alpn = r.string()
	}
	b.UploadMbps = r.int()
	b.DownloadMbps = r.int()
	b.AllowInsecure = r.bool()
	if version >= 1 {
		b.CaText = r.string()
		b.StreamReceiveWindow = r.int()
		b.ConnectionReceiveWindow = r.int()
		if version != 4 {
			b.DisableMtuDiscovery = r.bool()
		}
	}
	if version >= 5 {
		b.HopInterval = r.int()
	}
	if version >= 6 {
		b.ServerPorts = r.string()
	} else if r.err == nil {
		// old update to new: the ports were in the address
		if b.ServerAddress == nil {
			r.err = errKryoNull // isMultiPort(null)
		} else if address := *b.ServerAddress; isMultiPort(address) {
			colon := strings.LastIndexByte(address, ':')
			b.ServerPorts = ptr(address[colon+1:])
			b.ServerAddress = ptr(address[:colon])
		} else {
			b.ServerPorts = ptr(strconv.Itoa(*b.ServerPort))
		}
	}
	if version >= 8 {
		b.HopIntervalMax = r.int()
	}
	if version >= 9 {
		b.NikoAck = r.bool()
	}
}

func (r *kryoReader) ssh(b *SSHBean) {
	r.intValue() // version
	r.abstractBean(&b.AbstractBean)
	b.Username = r.string()
	b.AuthType = r.int()
	if b.AuthType != nil {
		switch *b.AuthType {
		case SSHAuthPassword:
			b.Password = r.string()
		case SSHAuthPrivateKey:
			b.PrivateKey = r.string()
			b.PrivateKeyPassphrase = r.string()
		}
	}
	b.PublicKey = r.string()
}

func (r *kryoReader) wireGuard(b *WireGuardBean) {
	version := r.intValue()
	r.abstractBean(&b.AbstractBean)
	b.LocalAddress = r.string()
	b.PrivateKey = r.string()
	b.PeerPublicKey = r.string()
	b.PeerPreSharedKey = r.string()
	b.Mtu = r.int()
	b.Reserved = r.string()
	if version >= 3 {
		b.PersistentKeepalive = r.int()
	}
	if version >= 4 {
		b.AmneziaWG = r.string()
	}
}

func (r *kryoReader) tuic(b *TuicBean) {
	version := r.intValue()
	r.abstractBean(&b.AbstractBean)
	b.Token = r.string()
	b.CaText = r.string()
	b.UdpRelayMode = r.string()
	b.CongestionController = r.string()
	b.Alpn = r.string()
	b.DisableSNI = r.bool()
	b.ReduceRTT = r.bool()
	b.Mtu = r.int()
	b.Sni = r.string()
	if version >= 1 {
		b.FastConnect = r.bool()
		b.AllowInsecure = r.bool()
	}
	if version >= 2 {
		b.CustomJSON = r.string()
		b.ProtocolVersion = r.int()
		b.Uuid = r.string()
	} else {
		b.ProtocolVersion = ptr(4)
	}
}

func (r *kryoReader) anyTLS(b *AnyTLSBean) {
	r.intValue() // version
	r.abstractBean(&b.AbstractBean)
	b.Password = r.string()
	b.Sni = r.string()
	b.Alpn = r.string()
	b.Certificates = r.string()
	b.UtlsFingerprint = r.string()
	b.AllowInsecure = r.bool()
	b.EchConfig = r.string()
}

func (r *kryoReader) config(b *ConfigBean) {
	r.intValue() // version
	r.abstractBean(&b.AbstractBean)
	b.Type = r.int()
	b.Config = r.string()
}

// kryoBean is KryoConverters' deserializer of a type of sn:// (fmt/TypeMap.kt): the bean, read through the data
// with its extra fields, its defaults applied. ErrUnsupported for a type of the app this package has no bean for
// (neko, a plugin's profile).
func kryoBean(typ string, data []byte) (Bean, error) {
	if len(data) == 0 {
		return nil, errors.New("no data: a null profile") // KryoConverters gives null, requireBean throws
	}
	r := &kryoReader{in: newKryoInput(data)}
	var bean Bean
	switch typ {
	case "socks":
		b := &SOCKSBean{}
		r.socks(b)
		bean = b
	case "http":
		b := &HttpBean{}
		r.http(b)
		bean = b
	case "ss":
		b := &ShadowsocksBean{}
		r.shadowsocks(b)
		bean = b
	case "vmess":
		b := &VMessBean{}
		r.standardV2Ray(&b.StandardV2RayBean, &b.AlterId)
		bean = b
	case "trojan":
		b := &TrojanBean{}
		r.trojan(b)
		bean = b
	case "trojan-go":
		b := &TrojanGoBean{}
		r.trojanGo(b)
		bean = b
	case "mieru":
		b := &MieruBean{}
		r.mieru(b)
		bean = b
	case "naive":
		b := &NaiveBean{}
		r.naive(b)
		bean = b
	case "hysteria":
		b := &HysteriaBean{}
		r.hysteria(b)
		bean = b
	case "ssh":
		b := &SSHBean{}
		r.ssh(b)
		bean = b
	case "wg":
		b := &WireGuardBean{}
		r.wireGuard(b)
		bean = b
	case "tuic":
		b := &TuicBean{}
		r.tuic(b)
		bean = b
	case "anytls":
		b := &AnyTLSBean{}
		r.anyTLS(b)
		bean = b
	case "config":
		b := &ConfigBean{}
		r.config(b)
		bean = b
	case "neko":
		return nil, ErrUnsupported
	default:
		return nil, errors.New("type " + typ + " not found")
	}
	r.abstractExtra(bean.base())
	if r.err != nil {
		return nil, r.err
	}
	bean.InitializeDefaultValues()
	return bean, nil
}

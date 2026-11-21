package nikofmt

import (
	"bytes"
	"compress/zlib"
	"encoding/base64"
	"errors"
	"unicode/utf16"
)

// kryoOutput writes as Kryo 5's ByteBufferOutput writes what kryoInput reads (kryo.go): ints of four bytes,
// little-endian; a boolean a byte; a string null as 0x80, "" as 0x81, ASCII of 2 to 32 chars as its bytes with the
// last one's high bit set, else its char count + 1 as a varint flagged in the first byte's high bit and its chars
// in Java's modified UTF-8 (Output.writeString).
type kryoOutput struct {
	b   []byte
	err error
}

var errKryoNullInt = errors.New("a null where the bean writes a number (the app's unboxing throws)")

func (o *kryoOutput) writeInt(v int) {
	u := uint32(int32(v))
	o.b = append(o.b, byte(u), byte(u>>8), byte(u>>16), byte(u>>24))
}

// writeInteger is writeInt of an Integer the app unboxes as it is: a null throws.
func (o *kryoOutput) writeInteger(v *int) {
	if v == nil {
		if o.err == nil {
			o.err = errKryoNullInt
		}
		return
	}
	o.writeInt(*v)
}

// writeIntOr is writeInt(v != null ? v : orElse).
func (o *kryoOutput) writeIntOr(v *int, orElse int) {
	if v == nil {
		o.writeInt(orElse)
		return
	}
	o.writeInt(*v)
}

func (o *kryoOutput) writeBoolean(v bool) {
	if v {
		o.b = append(o.b, 1)
	} else {
		o.b = append(o.b, 0)
	}
}

// writeVarIntFlag is Output.writeVarIntFlag(true, v, true): six bits and the flag in the first byte, the seventh
// saying another follows, then seven bits a byte.
func (o *kryoOutput) writeVarIntFlag(v uint32) {
	first := byte(v&0x3f) | 0x80
	if v>>6 == 0 {
		o.b = append(o.b, first)
		return
	}
	o.b = append(o.b, first|0x40)
	v >>= 6
	for v>>7 != 0 {
		o.b = append(o.b, byte(v&0x7f)|0x80)
		v >>= 7
	}
	o.b = append(o.b, byte(v))
}

func (o *kryoOutput) writeString(s *string) {
	if s == nil {
		o.b = append(o.b, 0x80)
		return
	}
	chars := utf16.Encode([]rune(*s))
	switch {
	case len(chars) == 0:
		o.b = append(o.b, 0x81)
		return
	case len(chars) > 1 && len(chars) <= 32 && kryoASCII(chars):
		for _, c := range chars {
			o.b = append(o.b, byte(c))
		}
		o.b[len(o.b)-1] |= 0x80
		return
	}
	o.writeVarIntFlag(uint32(len(chars) + 1))
	for _, c := range chars {
		switch {
		case c <= 0x7f:
			o.b = append(o.b, byte(c))
		case c > 0x7ff:
			o.b = append(o.b, byte(0xe0|c>>12&0x0f), byte(0x80|c>>6&0x3f), byte(0x80|c&0x3f))
		default:
			o.b = append(o.b, byte(0xc0|c>>6&0x1f), byte(0x80|c&0x3f))
		}
	}
}

func kryoASCII(chars []uint16) bool {
	for _, c := range chars {
		if c > 127 {
			return false
		}
	}
	return true
}

func (o *kryoOutput) abstractBean(b *AbstractBean) {
	o.writeString(b.ServerAddress)
	o.writeInteger(b.ServerPort)
}

// abstractExtra is the rest of AbstractBean.serializeToBuffer after the bean's own fields: extraVersion 12, the
// name, then each field with the app's value for a null.
func (o *kryoOutput) abstractExtra(b *AbstractBean) {
	orEmptyPtr := func(s *string) *string {
		if s == nil {
			return ptr("")
		}
		return s
	}
	o.writeInt(12)
	o.writeString(b.Name)
	o.writeString(b.CustomOutboundJson)
	o.writeString(b.CustomConfigJson)
	o.writeString(b.KnockSequence)
	o.writeIntOr(b.KnockInterval, 200)
	o.writeIntOr(b.KnockTtl, 10)
	o.writeIntOr(b.TlsFragment, 0)
	o.writeIntOr(b.KnockSilence, 15)
	o.writeString(orEmptyPtr(b.CertPins))
	o.writeString(orEmptyPtr(b.DnsTunnel))
	o.writeString(orEmptyPtr(b.DnsTunnelResolver))
	o.writeString(orEmptyPtr(b.DnsTunnelDomain))
	o.writeString(orEmptyPtr(b.DnsTunnelKey))
	o.writeIntOr(b.DnsTunnelMethod, 2)
	o.writeBoolean(flag(b.DnsTunnelFixedUpstream))
	o.writeIntOr(b.UdpPrelude, 0)
	o.writeString(orEmptyPtr(b.UdpPreludeSni))
	o.writeIntOr(b.UdpPreludeTtl, 0)
	o.writeBoolean(flag(b.UdpReorderInitial))
	o.writeString(orEmptyPtr(b.YggPeer))
	o.writeString(orEmptyPtr(b.YggKey))
	o.writeString(orEmptyPtr(b.YggVia))
	o.writeString(orEmptyPtr(b.DnsTunnelAuth))
	o.writeString(orEmptyPtr(b.DnsTunnelSeal))
	o.writeString(orEmptyPtr(b.DnsTunnelSpare))
	o.writeString(orEmptyPtr(b.DnsTunnelTls))
}

func (o *kryoOutput) mieru(b *MieruBean) {
	o.writeInt(1)
	o.abstractBean(&b.AbstractBean)
	if b.Protocol == nil {
		o.err = errKryoNull // protocol.equals(…) on null
		return
	}
	o.writeString(b.Protocol)
	o.writeString(b.Username)
	o.writeString(b.Password)
	if *b.Protocol == "UDP" {
		o.writeInteger(b.Mtu)
	}
	o.writeString(b.Multiplexing)
	o.writeString(b.HandshakeMode)
	o.writeString(b.TrafficPattern)
}

func (o *kryoOutput) ssh(b *SSHBean) {
	o.writeInt(0)
	o.abstractBean(&b.AbstractBean)
	o.writeString(b.Username)
	o.writeInteger(b.AuthType)
	switch num(b.AuthType) {
	case SSHAuthPassword:
		o.writeString(b.Password)
	case SSHAuthPrivateKey:
		o.writeString(b.PrivateKey)
		o.writeString(b.PrivateKeyPassphrase)
	}
	o.writeString(b.PublicKey)
}

func (o *kryoOutput) wireGuard(b *WireGuardBean) {
	o.writeInt(4)
	o.abstractBean(&b.AbstractBean)
	o.writeString(b.LocalAddress)
	o.writeString(b.PrivateKey)
	o.writeString(b.PeerPublicKey)
	o.writeString(b.PeerPreSharedKey)
	o.writeInteger(b.Mtu)
	o.writeString(b.Reserved)
	o.writeIntOr(b.PersistentKeepalive, 0)
	if b.AmneziaWG == nil {
		o.writeString(ptr(""))
	} else {
		o.writeString(b.AmneziaWG)
	}
}

func (o *kryoOutput) config(b *ConfigBean) {
	o.writeInt(0)
	o.abstractBean(&b.AbstractBean)
	o.writeInteger(b.Type)
	o.writeString(b.Config)
}

// kryoBytes is KryoConverters.serialize of a bean ShareLink gives an sn:// link: its type's name in TypeMap and
// its Kryo bytes. Only the kinds with no link of their own are written here.
func kryoBytes(b Bean) (string, []byte, error) {
	o := &kryoOutput{}
	var typ string
	switch b := b.(type) {
	case *MieruBean:
		typ = "mieru"
		o.mieru(b)
	case *SSHBean:
		typ = "ssh"
		o.ssh(b)
	case *WireGuardBean:
		typ = "wg"
		o.wireGuard(b)
	case *ConfigBean:
		typ = "config"
		o.config(b)
	default:
		return "", nil, errNoUniversalLink
	}
	if o.err != nil {
		return "", nil, o.err
	}
	o.abstractExtra(b.base())
	return typ, o.b, o.err
}

// UniversalLink is AbstractBean.toUniversalLink: sn://<type>?<the Kryo bytes, zlib at level 9, base64 URL-safe
// without padding>. Go's zlib is not the JVM's, so the bytes differ from the app's for the same bean; the app reads
// either (an inflater takes any deflate stream).
func UniversalLink(b Bean) (string, error) {
	typ, data, err := kryoBytes(b)
	if err != nil {
		return "", err
	}
	var z bytes.Buffer
	w, err := zlib.NewWriterLevel(&z, 9)
	if err != nil {
		return "", err
	}
	if _, err = w.Write(data); err != nil {
		return "", err
	}
	if err = w.Close(); err != nil {
		return "", err
	}
	return "sn://" + typ + "?" + base64.RawURLEncoding.EncodeToString(z.Bytes()), nil
}

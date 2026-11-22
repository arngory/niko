package nikofmt

import (
	"bytes"
	"errors"
)

// The Kryo of every bean, as KryoConverters.serialize writes it: what the app stores of a profile (Room keeps the
// bytes, not the bean), so what a profile is after the store, and what AbstractBean.equals compares (the bytes
// without the name). The layouts are the latest versions of the beans' serialize(), which kryo.go reads.

// writeBool is writeBoolean of a Boolean the app unboxes as it is: a null throws.
func (o *kryoOutput) writeBool(v *bool) {
	if v == nil {
		if o.err == nil {
			o.err = errKryoNullInt
		}
		return
	}
	o.writeBoolean(*v)
}

func (o *kryoOutput) socks(b *SOCKSBean) {
	o.writeInt(2)
	o.abstractBean(&b.AbstractBean)
	o.writeInteger(b.Protocol)
	o.writeString(b.Username)
	o.writeString(b.Password)
	o.writeBool(b.SUoT)
}

func (o *kryoOutput) shadowsocks(b *ShadowsocksBean) {
	o.writeInt(2)
	o.abstractBean(&b.AbstractBean)
	o.writeString(b.Method)
	o.writeString(b.Password)
	o.writeString(b.Plugin)
	o.writeBool(b.SUoT)
}

// standardV2Ray is StandardV2RayBean.serialize, version 8; alterID is written for a VMessBean only.
func (o *kryoOutput) standardV2Ray(b *StandardV2RayBean, vmess bool, alterID *int) {
	o.writeInt(8)
	o.abstractBean(&b.AbstractBean)
	o.writeString(b.Uuid)
	o.writeString(b.Encryption)
	if vmess {
		o.writeInteger(alterID)
	}
	o.writeString(b.Type)
	if b.Type == nil {
		if o.err == nil {
			o.err = errKryoNull // switch on a null String
		}
		return
	}
	switch *b.Type {
	case "ws":
		o.writeString(b.Host)
		o.writeString(b.Path)
		o.writeInteger(b.WsMaxEarlyData)
		o.writeString(b.EarlyDataHeaderName)
	case "http", "httpupgrade":
		o.writeString(b.Host)
		o.writeString(b.Path)
	case "xhttp":
		o.writeString(b.Host)
		o.writeString(b.Path)
		o.writeString(b.XhttpMode)
		o.writeString(b.XhttpExtra)
	case "grpc":
		o.writeString(b.Path)
	}
	o.writeString(b.Security)
	if equals(b.Security, "tls") {
		o.writeString(b.Sni)
		o.writeString(b.Alpn)
		o.writeString(b.Certificates)
		o.writeBool(b.AllowInsecure)
		o.writeString(b.UtlsFingerprint)
		o.writeString(b.RealityPubKey)
		o.writeString(b.RealityShortId)
	}
	o.writeBool(b.EnableECH)
	o.writeString(b.EchConfig)
	o.writeInteger(b.PacketEncoding)
	o.writeBool(b.EnableMux)
	o.writeBool(b.MuxPadding)
	o.writeInteger(b.MuxType)
	o.writeInteger(b.MuxConcurrency)
	o.writeString(orEmptyString(b.RealityKeyShare))
	o.writeString(orEmptyString(b.VlessEncryption))
	o.writeString(orEmptyString(b.RealityMldsa65Verify))
}

func orEmptyString(s *string) *string {
	if s == nil {
		return ptr("")
	}
	return s
}

func (o *kryoOutput) http(b *HttpBean) {
	o.writeInt(0)
	o.standardV2Ray(&b.StandardV2RayBean, false, nil)
	o.writeString(b.Username)
	o.writeString(b.Password)
}

func (o *kryoOutput) trojan(b *TrojanBean) {
	o.writeInt(2)
	o.standardV2Ray(&b.StandardV2RayBean, false, nil)
	o.writeString(b.Password)
}

func (o *kryoOutput) trojanGo(b *TrojanGoBean) {
	o.writeInt(2)
	o.abstractBean(&b.AbstractBean)
	o.writeString(b.Password)
	o.writeString(b.Sni)
	o.writeString(b.Type)
	if b.Type == nil {
		if o.err == nil {
			o.err = errKryoNull // switch on a null String
		}
		return
	}
	if *b.Type == "ws" {
		o.writeString(b.Host)
		o.writeString(b.Path)
	}
	o.writeString(b.Encryption)
	o.writeString(b.Plugin)
	o.writeBool(b.AllowInsecure)
	o.writeBool(b.Mux)
}

func (o *kryoOutput) naive(b *NaiveBean) {
	o.writeInt(6)
	o.abstractBean(&b.AbstractBean)
	o.writeString(b.Proto)
	o.writeString(b.Username)
	o.writeString(b.Password)
	o.writeString(b.ExtraHeaders)
	o.writeString(b.Certificates)
	o.writeString(b.Sni)
	o.writeInteger(b.InsecureConcurrency)
	o.writeBool(b.SUoT)
	o.writeBoolean(flag(b.NikoIcmp))
	o.writeBoolean(flag(b.NikoAck))
	o.writeBoolean(flag(b.NikoUdp))
}

func (o *kryoOutput) hysteria(b *HysteriaBean) {
	o.writeInt(9)
	o.abstractBean(&b.AbstractBean)
	o.writeInteger(b.ProtocolVersion)
	o.writeInteger(b.AuthPayloadType)
	o.writeString(b.AuthPayload)
	o.writeInteger(b.Protocol)
	o.writeString(b.Obfuscation)
	o.writeString(b.Sni)
	o.writeString(b.Alpn)
	o.writeInteger(b.UploadMbps)
	o.writeInteger(b.DownloadMbps)
	o.writeBool(b.AllowInsecure)
	o.writeString(b.CaText)
	o.writeInteger(b.StreamReceiveWindow)
	o.writeInteger(b.ConnectionReceiveWindow)
	o.writeBool(b.DisableMtuDiscovery)
	o.writeInteger(b.HopInterval)
	o.writeString(b.ServerPorts)
	o.writeIntOr(b.HopIntervalMax, 0)
	o.writeBoolean(flag(b.NikoAck))
}

func (o *kryoOutput) tuic(b *TuicBean) {
	o.writeInt(2)
	o.abstractBean(&b.AbstractBean)
	o.writeString(b.Token)
	o.writeString(b.CaText)
	o.writeString(b.UdpRelayMode)
	o.writeString(b.CongestionController)
	o.writeString(b.Alpn)
	o.writeBool(b.DisableSNI)
	o.writeBool(b.ReduceRTT)
	o.writeInteger(b.Mtu)
	o.writeString(b.Sni)
	o.writeBool(b.FastConnect)
	o.writeBool(b.AllowInsecure)
	o.writeString(b.CustomJSON)
	o.writeInteger(b.ProtocolVersion)
	o.writeString(b.Uuid)
}

func (o *kryoOutput) anyTLS(b *AnyTLSBean) {
	o.writeInt(0)
	o.abstractBean(&b.AbstractBean)
	o.writeString(b.Password)
	o.writeString(b.Sni)
	o.writeString(b.Alpn)
	o.writeString(b.Certificates)
	o.writeString(b.UtlsFingerprint)
	o.writeBool(b.AllowInsecure)
	o.writeString(b.EchConfig)
}

// kryoSerialize is KryoConverters.serialize of any bean: its type's name in TypeMap and its bytes; withoutName is
// AbstractBean.equals' serializeWithoutName. An error where the app's serialize throws (a null it unboxes).
func kryoSerialize(b Bean, withoutName bool) (string, []byte, error) {
	o := &kryoOutput{}
	var typ string
	switch b := b.(type) {
	case *SOCKSBean:
		typ = "socks"
		o.socks(b)
	case *HttpBean:
		typ = "http"
		o.http(b)
	case *ShadowsocksBean:
		typ = "ss"
		o.shadowsocks(b)
	case *VMessBean:
		typ = "vmess"
		o.standardV2Ray(&b.StandardV2RayBean, true, b.AlterId)
	case *TrojanBean:
		typ = "trojan"
		o.trojan(b)
	case *TrojanGoBean:
		typ = "trojan-go"
		o.trojanGo(b)
	case *MieruBean:
		typ = "mieru"
		o.mieru(b)
	case *NaiveBean:
		typ = "naive"
		o.naive(b)
	case *HysteriaBean:
		typ = "hysteria"
		o.hysteria(b)
	case *SSHBean:
		typ = "ssh"
		o.ssh(b)
	case *WireGuardBean:
		typ = "wg"
		o.wireGuard(b)
	case *TuicBean:
		typ = "tuic"
		o.tuic(b)
	case *AnyTLSBean:
		typ = "anytls"
		o.anyTLS(b)
	case *ConfigBean:
		typ = "config"
		o.config(b)
	default:
		return "", nil, errors.New("no Kryo for this kind of profile")
	}
	if o.err != nil {
		return "", nil, o.err
	}
	if withoutName {
		o.abstractExtraWithoutName(b.base())
	} else {
		o.abstractExtra(b.base())
	}
	return typ, o.b, o.err
}

// abstractExtraWithoutName is abstractExtra as equals writes it: the name left out.
func (o *kryoOutput) abstractExtraWithoutName(b *AbstractBean) {
	named := *b
	named.Name = nil
	start := len(o.b)
	o.abstractExtra(&named)
	// abstractExtra wrote the version, then the null name's one byte: take that byte out
	o.b = append(o.b[:start+4], o.b[start+5:]...)
}

// KryoBytes is KryoConverters.serialize of a bean: what the app stores of it. An error where the app's serialize
// throws on a null it unboxes.
func KryoBytes(b Bean) ([]byte, error) {
	_, data, err := kryoSerialize(b, false)
	return data, err
}

// KryoClone is the bean as the app has it back from its store: serialized, then read with its defaults
// (KryoConverters.deserialize, which bean.clone() is too). An error where the app's serialize throws.
func KryoClone(b Bean) (Bean, error) {
	typ, data, err := kryoSerialize(b, false)
	if err != nil {
		return nil, err
	}
	return kryoBean(typ, data)
}

// KryoEqual is AbstractBean.equals: the same class and the same bytes without the name. An error where the app's
// serialize throws.
func KryoEqual(a, b Bean) (bool, error) {
	typeA, dataA, err := kryoSerialize(a, true)
	if err != nil {
		return false, err
	}
	typeB, dataB, err := kryoSerialize(b, true)
	if err != nil {
		return false, err
	}
	if typeA != typeB {
		return false, nil
	}
	// VMess and VLESS are one class, as HttpBean is not a VMessBean: the type names say the class
	return bytes.Equal(dataA, dataB), nil
}

package nikoconf

import (
	"strings"

	"libcore/nikofmt"
)

// The V2Ray family (fmt/v2ray/V2RayFmt.kt): HTTP, VMess, VLESS and Trojan, their TLS and transports.

func httpOutbound(b *nikofmt.HttpBean, globalInsecure bool) object {
	o := object{"type": "http"}
	putString(o, "server", b.ServerAddress)
	putInt(o, "server_port", b.ServerPort)
	putString(o, "username", b.Username)
	putString(o, "password", b.Password)
	if tls := v2rayTLS(&b.StandardV2RayBean, globalInsecure); tls != nil {
		o["tls"] = tls
	}
	return o
}

func vmessOutbound(b *nikofmt.VMessBean, globalInsecure bool) (object, error) {
	s := &b.StandardV2RayBean
	o := object{}
	putString(o, "server", b.ServerAddress)
	putInt(o, "server_port", b.ServerPort)
	putString(o, "uuid", b.Uuid)
	if b.IsVLESS() {
		o["type"] = "vless"
		// VLESS keeps its flow in the bean's encryption
		if e := str(b.Encryption); !nikofmt.KtIsBlank(e) && e != "auto" {
			o["flow"] = e
		}
		if !blank(b.VlessEncryption) {
			o["encryption"] = *b.VlessEncryption
		}
	} else {
		o["type"] = "vmess"
		putInt(o, "alter_id", b.AlterId)
		if blank(b.Encryption) {
			o["security"] = "auto"
		} else {
			o["security"] = *b.Encryption
		}
	}
	putPacketEncoding(o, s)
	if tls := v2rayTLS(s, globalInsecure); tls != nil {
		o["tls"] = tls
	}
	transport, err := v2rayTransport(b, s)
	if err != nil {
		return nil, err
	}
	if transport != nil {
		o["transport"] = transport
	}
	return o, nil
}

// putPacketEncoding: "" too, for 0; a value it does not know leaves it null.
func putPacketEncoding(o object, s *nikofmt.StandardV2RayBean) {
	if s.PacketEncoding == nil {
		return
	}
	switch *s.PacketEncoding {
	case 0:
		o["packet_encoding"] = ""
	case 1:
		o["packet_encoding"] = "packetaddr"
	case 2:
		o["packet_encoding"] = "xudp"
	}
}

func trojanOutbound(b *nikofmt.TrojanBean, globalInsecure bool) (object, error) {
	o := object{"type": "trojan"}
	putString(o, "server", b.ServerAddress)
	putInt(o, "server_port", b.ServerPort)
	putString(o, "password", b.Password)
	if tls := v2rayTLS(&b.StandardV2RayBean, globalInsecure); tls != nil {
		o["tls"] = tls
	}
	transport, err := v2rayTransport(b, &b.StandardV2RayBean)
	if err != nil {
		return nil, err
	}
	if transport != nil {
		o["transport"] = transport
	}
	return o, nil
}

// v2rayTLS is buildSingBoxOutboundTLS: nil where security is not "tls". Reality without a fingerprint takes
// Chrome's, as the core's Reality needs uTLS.
func v2rayTLS(b *nikofmt.StandardV2RayBean, globalInsecure bool) object {
	if str(b.Security) != "tls" {
		return nil
	}
	tls := object{"enabled": true, "insecure": flag(b.AllowInsecure) || globalInsecure}
	if !blank(b.Sni) {
		tls["server_name"] = *b.Sni
	}
	if !blank(b.Alpn) {
		tls["alpn"] = listByLineOrComma(*b.Alpn)
	}
	if !blank(b.Certificates) {
		tls["certificate"] = *b.Certificates
	}
	fp := str(b.UtlsFingerprint)
	if !blank(b.RealityPubKey) {
		reality := object{"enabled": true, "public_key": *b.RealityPubKey}
		putString(reality, "short_id", b.RealityShortId)
		if !blank(b.RealityKeyShare) {
			reality["key_share"] = *b.RealityKeyShare
		}
		if !blank(b.RealityMldsa65Verify) {
			reality["mldsa65_verify"] = nikofmt.KtTrim(*b.RealityMldsa65Verify)
		}
		tls["reality"] = reality
		if nikofmt.KtIsBlank(fp) {
			fp = "chrome"
		}
	}
	if !nikofmt.KtIsBlank(fp) {
		tls["utls"] = object{"enabled": true, "fingerprint": fp}
	}
	if flag(b.EnableECH) {
		ech := object{"enabled": true}
		if !blank(b.EchConfig) {
			// a PEM's lines, its BEGIN and END among them, as the core takes them
			ech["config"] = strings2any(nikofmt.KtLines(*b.EchConfig))
		}
		tls["ech"] = ech
	}
	return tls
}

// v2rayTransport is buildSingBoxOutboundStreamSettings: nil for tcp and a type it does not know.
func v2rayTransport(bean nikofmt.Bean, b *nikofmt.StandardV2RayBean) (object, error) {
	switch str(b.Type) {
	case "ws":
		t := object{"type": "ws"}
		headers := object{}
		if !blank(b.Host) {
			headers["Host"] = *b.Host
		}
		t["headers"] = headers
		path := str(b.Path)
		if strings.Contains(path, "?ed=") {
			// v2rayN's early data in the path
			t["path"] = substringBefore(path, "?ed=")
			ed, ok := nikofmt.KtToIntOrNull(substringAfter(path, "?ed="))
			if !ok {
				ed = 2048
			}
			t["max_early_data"] = int64(ed)
			t["early_data_header_name"] = "Sec-WebSocket-Protocol"
		} else if !nikofmt.KtIsBlank(path) {
			t["path"] = path
		} else {
			t["path"] = "/"
		}
		if num(b.WsMaxEarlyData) > 0 {
			t["max_early_data"] = int64(*b.WsMaxEarlyData)
		}
		if !blank(b.EarlyDataHeaderName) {
			t["early_data_header_name"] = *b.EarlyDataHeaderName
		}
		return t, nil
	case "http":
		t := object{"type": "http"}
		if str(b.Security) != "tls" {
			t["method"] = "GET" // v2ray's tcp header
		}
		if !blank(b.Host) {
			// split as it is: no trim, the empty ones kept
			t["host"] = strings2any(strings.Split(*b.Host, ","))
		}
		t["path"] = pathOrRoot(b.Path)
		return t, nil
	case "quic":
		return object{"type": "quic"}, nil
	case "grpc":
		t := object{"type": "grpc"}
		putString(t, "service_name", b.Path)
		return t, nil
	case "httpupgrade":
		t := object{"type": "httpupgrade"}
		putString(t, "host", b.Host)
		putString(t, "path", b.Path)
		return t, nil
	case "xhttp":
		options, err := xhttpOptions(bean, b)
		if err != nil {
			return nil, err
		}
		options["type"] = "xhttp"
		return options, nil
	}
	return nil, nil
}

// pathOrRoot is "path.takeIf { it.isNotBlank() } ?: \"/\"".
func pathOrRoot(path *string) string {
	if blank(path) {
		return "/"
	}
	return *path
}

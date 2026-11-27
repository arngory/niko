package nikoconf

import (
	"errors"
	"fmt"
	"strings"

	"libcore/nikofmt"
)

// The other builders: Hysteria, TUIC, SOCKS, Shadowsocks, SSH, AnyTLS, naive, Trojan-Go and mieru, each as its
// …Fmt.kt has it.

// HysteriaBean's protocols and authentications (Hysteria 1).
const (
	hysteriaProtocolUDP = 0
	hysteriaAuthString  = 1
	hysteriaAuthBase64  = 2
)

// hysteriaOutbound is fmt/hysteria/HysteriaFmt.kt buildSingBoxOutboundHysteriaBean: Hysteria 1 or 2 by the bean's
// version. One port is server_port; anything else in serverPorts is a hopping list.
func hysteriaOutbound(b *nikofmt.HysteriaBean, globalInsecure bool) (object, error) {
	o := object{}
	putString(o, "server", b.ServerAddress)
	if port, ok := nikofmt.KtToIntOrNull(str(b.ServerPorts)); ok {
		o["server_port"] = int64(port)
	} else {
		o["server_ports"] = hopPortsToSingboxList(str(b.ServerPorts))
	}
	o["hop_interval"] = intString(b.HopInterval) + "s"
	putInt(o, "up_mbps", b.UploadMbps)
	putInt(o, "down_mbps", b.DownloadMbps)
	tls := object{"enabled": true, "insecure": flag(b.AllowInsecure) || globalInsecure}
	if !blank(b.Sni) {
		tls["server_name"] = *b.Sni
	}
	if !blank(b.CaText) {
		tls["certificate"] = *b.CaText
	}
	o["tls"] = tls
	switch num(b.ProtocolVersion) {
	case 1:
		o["type"] = "hysteria"
		putString(o, "obfs", b.Obfuscation) // "" too
		putBool(o, "disable_mtu_discovery", b.DisableMtuDiscovery)
		switch num(b.AuthPayloadType) {
		case hysteriaAuthBase64:
			putString(o, "auth", b.AuthPayload)
		case hysteriaAuthString:
			putString(o, "auth_str", b.AuthPayload)
		}
		if n := num(b.StreamReceiveWindow); n > 0 {
			o["recv_window_conn"] = int64(n)
		}
		if n := num(b.ConnectionReceiveWindow); n > 0 {
			o["recv_window"] = int64(n)
		}
		if !blank(b.Alpn) {
			tls["alpn"] = listByLineOrComma(*b.Alpn)
		}
	case 2:
		o["type"] = "hysteria2"
		// a random interval each hop makes the hopping pattern less regular
		if num(b.HopIntervalMax) > num(b.HopInterval) {
			o["hop_interval_max"] = intString(b.HopIntervalMax) + "s"
		}
		if !blank(b.Obfuscation) {
			o["obfs"] = object{"type": "salamander", "password": *b.Obfuscation}
		}
		putString(o, "password", b.AuthPayload)
		tls["alpn"] = []any{"h3"} // the profile's alpn is Hysteria 1's only
	default:
		return nil, fmt.Errorf("error_version %s", intString(b.ProtocolVersion))
	}
	return o, nil
}

// hopPortsToSingboxList: "443,5000-6000" -> ["443:443", "5000:6000"], as the core takes ranges only. What is neither
// a number nor one ':' (after '-' became ':') is left out.
func hopPortsToSingboxList(s string) []any {
	list := []any{}
	for _, p := range strings.Split(s, ",") {
		r := strings.ReplaceAll(nikofmt.KtTrim(p), "-", ":")
		if _, ok := nikofmt.KtToIntOrNull(r); ok {
			list = append(list, r+":"+r)
		} else if strings.Count(r, ":") == 1 {
			list = append(list, r)
		}
	}
	return list
}

// tuicOutbound is fmt/tuic/TuicFmt.kt buildSingBoxOutboundTuicBean: TUIC 5; its udp_relay_mode only for quic, native
// being the core's default.
func tuicOutbound(b *nikofmt.TuicBean, globalInsecure bool) (object, error) {
	if b.ProtocolVersion != nil && *b.ProtocolVersion == 4 {
		return nil, errors.New("TUIC v4 is no longer supported")
	}
	o := object{"type": "tuic"}
	putString(o, "server", b.ServerAddress)
	putInt(o, "server_port", b.ServerPort)
	putString(o, "uuid", b.Uuid)
	putString(o, "password", b.Token)
	putString(o, "congestion_control", b.CongestionController)
	if str(b.UdpRelayMode) == "quic" {
		o["udp_relay_mode"] = "quic"
	}
	putBool(o, "zero_rtt_handshake", b.ReduceRTT)
	tls := object{"enabled": true, "insecure": flag(b.AllowInsecure) || globalInsecure}
	if !blank(b.Sni) {
		tls["server_name"] = *b.Sni
	}
	if !blank(b.Alpn) {
		tls["alpn"] = listByLineOrComma(*b.Alpn)
	}
	if !blank(b.CaText) {
		tls["certificate"] = *b.CaText
	}
	putBool(tls, "disable_sni", b.DisableSNI)
	o["tls"] = tls
	return o, nil
}

// socksOutbound is fmt/socks/SOCKSFmt.kt buildSingBoxOutboundSocksBean.
func socksOutbound(b *nikofmt.SOCKSBean) object {
	o := object{"type": "socks"}
	putString(o, "server", b.ServerAddress)
	putInt(o, "server_port", b.ServerPort)
	putString(o, "username", b.Username)
	putString(o, "password", b.Password)
	// SOCKSBean.protocolVersionName
	switch num(b.Protocol) {
	case nikofmt.ProtocolSOCKS4:
		o["version"] = "4"
	case nikofmt.ProtocolSOCKS4A:
		o["version"] = "4a"
	default:
		o["version"] = "5"
	}
	return o
}

// shadowsocksOutbound is fmt/shadowsocks/ShadowsocksFmt.kt buildSingBoxOutboundShadowsocksBean: the plugin
// "<name>;<options>" split at its first ';'. A plugin without one gets its name for options too (Kotlin's
// substringAfter gives the whole string then): the app's quirk, kept.
func shadowsocksOutbound(b *nikofmt.ShadowsocksBean) object {
	o := object{"type": "shadowsocks"}
	putString(o, "server", b.ServerAddress)
	putInt(o, "server_port", b.ServerPort)
	putString(o, "password", b.Password)
	putString(o, "method", b.Method)
	if !blank(b.Plugin) {
		if plugin := substringBefore(*b.Plugin, ";"); plugin != "none" {
			o["plugin"] = plugin
			o["plugin_opts"] = substringAfter(*b.Plugin, ";")
		}
	}
	return o
}

// sshOutbound is fmt/ssh/SSHFmt.kt buildSingBoxOutboundSSHBean: a private key, else the password (for no
// authentication too, then "").
func sshOutbound(b *nikofmt.SSHBean) object {
	o := object{"type": "ssh"}
	putString(o, "server", b.ServerAddress)
	putInt(o, "server_port", b.ServerPort)
	putString(o, "user", b.Username)
	if !blank(b.PublicKey) {
		o["host_key"] = listByLineOrComma(*b.PublicKey)
	}
	if num(b.AuthType) == nikofmt.SSHAuthPrivateKey {
		putString(o, "private_key", b.PrivateKey)
		putString(o, "private_key_passphrase", b.PrivateKeyPassphrase)
	} else {
		putString(o, "password", b.Password)
	}
	return o
}

// anyTLSOutbound is proxy/anytls/AnyTLSFmt.kt buildSingBoxOutboundAnyTLSBean. The app's builder takes no "allow
// insecure" of the settings: insecure is there only where the profile allows it. Its ECH config is one string.
func anyTLSOutbound(b *nikofmt.AnyTLSBean) object {
	o := object{"type": "anytls"}
	putString(o, "server", b.ServerAddress)
	putInt(o, "server_port", b.ServerPort)
	putString(o, "password", b.Password)
	tls := object{"enabled": true}
	if !blank(b.Sni) {
		tls["server_name"] = *b.Sni
	}
	if flag(b.AllowInsecure) {
		tls["insecure"] = true
	}
	if !blank(b.Alpn) {
		tls["alpn"] = listByLineOrComma(*b.Alpn)
	}
	if !blank(b.Certificates) {
		tls["certificate"] = *b.Certificates
	}
	if !blank(b.UtlsFingerprint) {
		tls["utls"] = object{"enabled": true, "fingerprint": *b.UtlsFingerprint}
	}
	if !blank(b.EchConfig) {
		tls["ech"] = object{"enabled": true, "config": []any{*b.EchConfig}}
	}
	o["tls"] = tls
	return o
}

// shadowTLSOutbound builds a sing-box shadowtls outbound (Stage 5 parity).
func shadowTLSOutbound(b *nikofmt.ShadowTLSBean, globalInsecure bool) object {
	ver := num(b.Version)
	if ver < 1 || ver > 3 {
		ver = 3
	}
	o := object{
		"type":    "shadowtls",
		"version": int64(ver),
	}
	putString(o, "server", b.ServerAddress)
	putInt(o, "server_port", b.ServerPort)
	if ver >= 2 && !blank(b.Password) {
		o["password"] = *b.Password
	}
	tls := object{"enabled": true, "insecure": flag(b.AllowInsecure) || globalInsecure}
	if !blank(b.Sni) {
		tls["server_name"] = *b.Sni
	}
	if !blank(b.Alpn) {
		tls["alpn"] = listByLineOrComma(*b.Alpn)
	}
	if !blank(b.UtlsFingerprint) {
		tls["utls"] = object{"enabled": true, "fingerprint": *b.UtlsFingerprint}
	}
	o["tls"] = tls
	return o
}

// naiveOutbound is fmt/naive/NaiveFmt.kt buildSingBoxOutboundNaiveBean: the core's naive outbound (cronet), whose
// TLS is Chromium's: no "insecure", the certificate always checked (against the profile's roots where it has some).
func naiveOutbound(b *nikofmt.NaiveBean) object {
	o := object{"type": "naive"}
	putString(o, "server", b.ServerAddress)
	putInt(o, "server_port", b.ServerPort)
	if !blank(b.Username) {
		o["username"] = *b.Username
	}
	if !blank(b.Password) {
		o["password"] = *b.Password
	}
	if n := num(b.InsecureConcurrency); n > 0 {
		o["insecure_concurrency"] = int64(n)
	}
	if str(b.Proto) == "quic" {
		o["quic"] = true
	}
	if headers := naiveHeaders(str(b.ExtraHeaders)); len(headers) > 0 {
		o["extra_headers"] = headers
	}
	tls := object{"enabled": true}
	// the name naive shows: the SNI, else a server name (an address gets none of its own)
	if !blank(b.Sni) {
		tls["server_name"] = *b.Sni
	} else if b.ServerAddress != nil && !nikofmt.IsIPAddress(*b.ServerAddress) {
		tls["server_name"] = *b.ServerAddress
	}
	if !blank(b.Certificates) {
		tls["certificate"] = []any{*b.Certificates}
	}
	o["tls"] = tls
	return o
}

// naiveHeaders: the "Name: value" lines of a naive profile's extra headers, a line without a name left out, a name
// again taking the later value.
func naiveHeaders(text string) object {
	headers := object{}
	for _, line := range nikofmt.KtLines(text) {
		if name := nikofmt.KtTrim(substringBefore(line, ":")); name != "" && strings.Contains(line, ":") {
			headers[name] = nikofmt.KtTrim(substringAfter(line, ":"))
		}
	}
	return headers
}

// trojanGoOutbound is fmt/trojan_go/TrojanGoFmt.kt buildSingBoxOutboundTrojanGoBean: the core's Trojan with Trojan-Go's
// own layers in it (the Shadowsocks AEAD inside TLS, smux); TLS to the SNI or else the server, WebSocket for ws.
func trojanGoOutbound(b *nikofmt.TrojanGoBean, globalInsecure bool) (object, error) {
	o := object{"type": "trojan"}
	ss, err := trojanGoShadowsocks(str(b.Encryption))
	if err != nil {
		return nil, err
	}
	if ss != nil {
		o["trojan_go_shadowsocks"] = ss
	}
	if flag(b.Mux) {
		o["trojan_go_mux"] = true
	}
	putString(o, "server", b.ServerAddress)
	putInt(o, "server_port", b.ServerPort)
	putString(o, "password", b.Password)
	tls := object{"enabled": true, "insecure": flag(b.AllowInsecure) || globalInsecure}
	if !blank(b.Sni) {
		tls["server_name"] = *b.Sni
	} else {
		putString(tls, "server_name", b.ServerAddress)
	}
	o["tls"] = tls
	if str(b.Type) == "ws" {
		headers := object{}
		if !blank(b.Host) {
			headers["Host"] = *b.Host
		} else if !blank(b.Sni) {
			headers["Host"] = *b.Sni
		}
		o["transport"] = object{"type": "ws", "path": pathOrRoot(b.Path), "headers": headers}
	}
	return o, nil
}

// trojanGoShadowsocks is TrojanGoBean.shadowsocksLayer: Trojan-Go's "ss;METHOD:PASSWORD" as the core's option, nil
// for none; anything else is an error.
func trojanGoShadowsocks(encryption string) (object, error) {
	spec := nikofmt.KtTrim(encryption)
	if spec == "" || spec == "none" {
		return nil, nil
	}
	if !strings.HasPrefix(spec, "ss;") {
		return nil, fmt.Errorf("Trojan-Go encryption %s: only ss;method:password", spec)
	}
	methodAndPassword := strings.TrimPrefix(spec, "ss;")
	method := substringBefore(methodAndPassword, ":")
	password := substringAfterOr(methodAndPassword, ":", "")
	if password == "" {
		return nil, errors.New("Trojan-Go encryption: no Shadowsocks password")
	}
	if method == "" {
		method = "aes-128-gcm"
	}
	return object{"method": method, "password": password}, nil
}

// mieruOutbound is fmt/mieru/MieruFmt.kt buildSingBoxOutboundMieruBean: the core's mieru (its own client library, no
// plugin). The MTU only for UDP, within mieru's 1280-1500.
func mieruOutbound(b *nikofmt.MieruBean) object {
	o := object{"type": "mieru"}
	putString(o, "server", b.ServerAddress)
	putInt(o, "server_port", b.ServerPort)
	udp := strings.EqualFold(str(b.Protocol), "UDP")
	if udp {
		o["transport"] = "udp"
	} else {
		o["transport"] = "tcp"
	}
	putString(o, "username", b.Username)
	putString(o, "password", b.Password)
	if mtu := num(b.Mtu); udp && b.Mtu != nil && mtu >= 1280 && mtu <= 1500 {
		o["mtu"] = int64(mtu)
	}
	if !blank(b.Multiplexing) {
		o["multiplexing"] = *b.Multiplexing
	}
	if !blank(b.HandshakeMode) {
		o["handshake_mode"] = *b.HandshakeMode
	}
	if !blank(b.TrafficPattern) {
		o["traffic_pattern"] = *b.TrafficPattern
	}
	return o
}

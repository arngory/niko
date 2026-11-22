package nikosub

import (
	"errors"
	"strings"

	"libcore/nikofmt"
)

// A Clash (mihomo) config's proxies as the app reads them (RawUpdater.parseRaw's "proxies:" branch): socks5, http,
// ss with obfs or v2ray-plugin, vmess, vless and trojan with their transports and REALITY, anytls, hysteria,
// hysteria2 and tuic; another type is skipped, and so is an entry the app's code throws on (a field of the wrong type,
// a missing server), one at a time. Kotlin's casts are kept: "as String" throws on anything else, "as? Boolean"
// takes a YAML boolean alone, toString() is Java's of the value YAML gave (a port of 443.0 is no Int).

// errNoProxies: "proxies:" is in the text and it is a mapping, but its proxies are no list: the app's error
// (no_proxies_found_in_file), which fails the update.
var errNoProxies = errors.New("no proxies found in the file")

// errSkip is an exception within one proxy: the app logs it and goes on to the next.
var errSkip = errors.New("skip")

// toStr is ?.toString(): nil for null.
func toStr(v any) *string {
	if v == nil {
		return nil
	}
	s := javaToString(v)
	return &s
}

// asString is "as String": the string, errSkip for anything else (null too).
func asString(v any) (string, error) {
	if s, ok := v.(string); ok {
		return s, nil
	}
	return "", errSkip
}

// asStringOrNull is "as? String".
func asStringOrNull(v any) *string {
	if s, ok := v.(string); ok {
		return &s
	}
	return nil
}

// isTrue is "as? Boolean == true".
func isTrue(v any) bool {
	b, ok := v.(bool)
	return ok && b
}

// stringIsTrue is "toString() == \"true\"" (a null's toString is "null").
func stringIsTrue(v any) bool { return javaToString(v) == "true" }

// joinList is "(v as? List<Any>)?.joinToString(sep)".
func joinList(v any, sep string) *string {
	list, ok := v.([]any)
	if !ok {
		return nil
	}
	parts := make([]string, len(list))
	for i, e := range list {
		parts[i] = javaToString(e)
	}
	s := strings.Join(parts, sep)
	return &s
}

// toInt is "toString().toInt()": errSkip where Kotlin throws.
func toInt(v any) (int, error) {
	n, ok := nikofmt.KtToIntOrNull(javaToString(v))
	if !ok {
		return 0, errSkip
	}
	return n, nil
}

// toIntOrNull is "?.toString()?.toIntOrNull()".
func toIntOrNull(v any) *int {
	if v == nil {
		return nil
	}
	if n, ok := nikofmt.KtToIntOrNull(javaToString(v)); ok {
		return &n
	}
	return nil
}

// optionKey is "opt.key.replace(\"_\", \"-\")" of a map whose keys Kotlin takes for strings: errSkip for another key.
func optionKey(k any) (string, error) {
	s, ok := k.(string)
	if !ok {
		return "", errSkip
	}
	return strings.ReplaceAll(s, "_", "-"), nil
}

// clashCipher is RawUpdater.clashCipher.
func clashCipher(cipher string) string {
	if cipher == "dummy" {
		return "none"
	}
	return cipher
}

// parseClash is the "proxies:" branch: errYAML where the app goes on to read the text otherwise, errNoProxies and a
// *fatalYAML where its update fails.
func parseClash(text string) ([]nikofmt.Bean, error) {
	config, err := loadClashYAML(text)
	if err != nil {
		return nil, err
	}
	globalFingerprint := ""
	if v := config.get("global-client-fingerprint"); v != nil {
		globalFingerprint = javaToString(v)
	}
	list, ok := config.get("proxies").([]any)
	if !ok {
		return nil, errNoProxies
	}
	proxies := []nikofmt.Bean{}
	for _, item := range list {
		proxy, ok := item.(*yamlMap)
		if !ok {
			continue
		}
		bean, err := clashProxy(proxy)
		if err != nil || bean == nil {
			continue // one broken entry must not drop the whole subscription
		}
		proxies = append(proxies, bean)
	}
	// "Fix ent": the defaults, then a TLS V2Ray profile's SNI from its host, a REALITY one's fingerprint
	for _, p := range proxies {
		p.InitializeDefaultValues()
		var v *nikofmt.StandardV2RayBean
		switch b := p.(type) {
		case *nikofmt.VMessBean:
			v = &b.StandardV2RayBean
		case *nikofmt.TrojanBean:
			v = &b.StandardV2RayBean
		case *nikofmt.HttpBean:
			v = &b.StandardV2RayBean
		}
		if v == nil {
			continue
		}
		if equalsStr(v.Security, "tls") && blank(v.Sni) && !blank(v.Host) && !nikofmt.IsIPAddress(*v.Host) {
			v.Sni = ptrTo(*v.Host)
		}
		if !blank(v.RealityPubKey) && blank(v.UtlsFingerprint) {
			v.UtlsFingerprint = ptrTo(globalFingerprint)
			if blank(v.UtlsFingerprint) {
				v.UtlsFingerprint = ptrTo("chrome")
			}
		}
	}
	return proxies, nil
}

// blank is Kotlin's isNullOrBlank.
func blank(s *string) bool { return s == nil || nikofmt.KtIsBlank(*s) }

func equalsStr(s *string, v string) bool { return s != nil && *s == v }

// clashProxy is one entry: its bean, nil for a type the app does not read or an entry it skips ("continue"), errSkip
// where its code throws.
func clashProxy(proxy *yamlMap) (nikofmt.Bean, error) {
	typ, err := asString(proxy.get("type"))
	if err != nil {
		return nil, err
	}
	switch typ {
	case "socks5":
		b := &nikofmt.SOCKSBean{}
		server, err := asString(proxy.get("server"))
		if err != nil {
			return nil, err
		}
		b.ServerAddress = &server
		port, err := toInt(proxy.get("port"))
		if err != nil {
			return nil, err
		}
		b.ServerPort = &port
		b.Username = toStr(proxy.get("username"))
		b.Password = toStr(proxy.get("password"))
		b.Name = toStr(proxy.get("name"))
		return b, nil
	case "http":
		b := &nikofmt.HttpBean{}
		server, err := asString(proxy.get("server"))
		if err != nil {
			return nil, err
		}
		b.ServerAddress = &server
		port, err := toInt(proxy.get("port"))
		if err != nil {
			return nil, err
		}
		b.ServerPort = &port
		b.Username = toStr(proxy.get("username"))
		b.Password = toStr(proxy.get("password"))
		if stringIsTrue(proxy.get("tls")) {
			b.Security = ptrTo("tls")
		} else {
			b.Security = ptrTo("")
		}
		b.Sni = toStr(proxy.get("sni"))
		b.Name = toStr(proxy.get("name"))
		b.AllowInsecure = ptrTo(stringIsTrue(proxy.get("skip-cert-verify")))
		return b, nil
	case "ss":
		var plugin []string
		if proxy.has("plugin") {
			opts, ok := proxy.get("plugin-opts").(*yamlMap)
			if !ok {
				return nil, errSkip // as Map<String, Any?> of a null or another value
			}
			value := func(key string) string {
				if v := opts.get(key); v != nil {
					return javaToString(v)
				}
				return ""
			}
			switch p := proxy.get("plugin"); {
			case p == "obfs":
				plugin = append(plugin, "obfs-local", "obfs="+value("mode"), "obfs-host="+value("host"))
			case p == "v2ray-plugin":
				plugin = append(plugin, "v2ray-plugin", "mode="+value("mode"))
				if value("tls") == "true" {
					plugin = append(plugin, "tls")
				}
				plugin = append(plugin, "host="+value("host"), "path="+value("path"))
				if value("mux") == "true" {
					plugin = append(plugin, "mux=8")
				}
			}
		}
		b := &nikofmt.ShadowsocksBean{}
		server, err := asString(proxy.get("server"))
		if err != nil {
			return nil, err
		}
		b.ServerAddress = &server
		port, err := toInt(proxy.get("port"))
		if err != nil {
			return nil, err
		}
		b.ServerPort = &port
		b.Password = toStr(proxy.get("password"))
		cipher, err := asString(proxy.get("cipher"))
		if err != nil {
			return nil, err
		}
		b.Method = ptrTo(clashCipher(cipher))
		b.Plugin = ptrTo(strings.Join(plugin, ";"))
		b.Name = toStr(proxy.get("name"))
		return b, nil
	case "vmess", "vless", "trojan":
		return clashV2Ray(typ, proxy)
	case "anytls":
		b := &nikofmt.AnyTLSBean{}
		for i, k := range proxy.keys {
			v := proxy.values[i]
			if v == nil {
				continue
			}
			key, err := optionKey(k)
			if err != nil {
				return nil, err
			}
			switch key {
			case "name":
				b.Name = toStr(v)
			case "server":
				s, err := asString(v)
				if err != nil {
					return nil, err
				}
				b.ServerAddress = &s
			case "port":
				n, err := toInt(v)
				if err != nil {
					return nil, err
				}
				b.ServerPort = &n
			case "password":
				b.Password = toStr(v)
			case "client-fingerprint":
				s, err := asString(v)
				if err != nil {
					return nil, err
				}
				b.UtlsFingerprint = &s
			case "sni":
				b.Sni = toStr(v)
			case "skip-cert-verify":
				b.AllowInsecure = ptrTo(stringIsTrue(v))
			case "alpn":
				b.Alpn = joinList(v, "\n")
			}
		}
		return b, nil
	case "hysteria", "hysteria2":
		return clashHysteria(typ == "hysteria2", proxy)
	case "tuic":
		return clashTuic(proxy)
	}
	return nil, nil
}

func clashHysteria(v2 bool, proxy *yamlMap) (nikofmt.Bean, error) {
	b := &nikofmt.HysteriaBean{ProtocolVersion: ptrTo(1)}
	speedDefault := 100
	if v2 {
		b.ProtocolVersion = ptrTo(2)
		speedDefault = 0
	}
	hopPorts := ""
	for i, k := range proxy.keys {
		v := proxy.values[i]
		if v == nil {
			continue
		}
		key, err := optionKey(k)
		if err != nil {
			return nil, err
		}
		speed := func() *int {
			if n, ok := nikofmt.KtToIntOrNull(substringBefore(javaToString(v), " ")); ok {
				return &n
			}
			return ptrTo(speedDefault)
		}
		switch key {
		case "name":
			b.Name = toStr(v)
		case "server":
			s, err := asString(v)
			if err != nil {
				return nil, err
			}
			b.ServerAddress = &s
		case "port":
			b.ServerPorts = toStr(v)
		case "ports":
			hopPorts = javaToString(v)
		case "sni":
			b.Sni = toStr(v)
		case "skip-cert-verify":
			b.AllowInsecure = ptrTo(stringIsTrue(v))
		case "up":
			b.UploadMbps = speed()
		case "down":
			b.DownloadMbps = speed()
		}
		if v2 {
			switch key {
			case "obfs-password":
				b.Obfuscation = toStr(v)
			case "password":
				b.AuthPayload = toStr(v)
			}
			continue
		}
		switch key {
		case "obfs":
			b.Obfuscation = toStr(v)
		case "auth-str":
			b.AuthPayloadType = ptrTo(hysteriaAuthString)
			b.AuthPayload = toStr(v)
		case "recv-window-conn":
			b.ConnectionReceiveWindow = ptrTo(intOrZero(v))
		case "recv-window":
			b.StreamReceiveWindow = ptrTo(intOrZero(v))
		case "disable-mtu-discovery":
			s := javaToString(v)
			b.DisableMtuDiscovery = ptrTo(s == "true" || s == "1")
		case "alpn":
			if alpn := joinList(v, "\n"); alpn != nil {
				b.Alpn = alpn
			} else {
				b.Alpn = ptrTo("h3")
			}
		}
	}
	if !nikofmt.KtIsBlank(hopPorts) {
		b.ServerPorts = &hopPorts
	}
	return b, nil
}

func intOrZero(v any) int {
	if n, ok := nikofmt.KtToIntOrNull(javaToString(v)); ok {
		return n
	}
	return 0
}

// substringBefore is Kotlin's: the whole string where the delimiter is missing.
func substringBefore(s, delimiter string) string {
	if i := strings.Index(s, delimiter); i >= 0 {
		return s[:i]
	}
	return s
}

func clashTuic(proxy *yamlMap) (nikofmt.Bean, error) {
	b := &nikofmt.TuicBean{}
	ip := ""
	for i, k := range proxy.keys {
		v := proxy.values[i]
		if v == nil {
			continue
		}
		key, err := optionKey(k)
		if err != nil {
			return nil, err
		}
		switch key {
		case "name":
			b.Name = toStr(v)
		case "server":
			b.ServerAddress = toStr(v)
		case "ip":
			ip = javaToString(v)
		case "port":
			n, err := toInt(v)
			if err != nil {
				return nil, err
			}
			b.ServerPort = &n
		case "token":
			b.ProtocolVersion = ptrTo(4)
			b.Token = toStr(v)
		case "uuid":
			b.Uuid = toStr(v)
		case "password":
			b.Token = toStr(v)
		case "skip-cert-verify":
			b.AllowInsecure = ptrTo(stringIsTrue(v))
		case "disable-sni":
			b.DisableSNI = ptrTo(stringIsTrue(v))
		case "reduce-rtt":
			b.ReduceRTT = ptrTo(stringIsTrue(v))
		case "sni":
			b.Sni = toStr(v)
		case "alpn":
			b.Alpn = joinList(v, "\n")
		case "congestion-controller":
			b.CongestionController = toStr(v)
		case "udp-relay-mode":
			b.UdpRelayMode = toStr(v)
		}
	}
	if !nikofmt.KtIsBlank(ip) {
		b.ServerAddress = &ip
		if blank(b.Sni) && !blank(b.ServerAddress) && !nikofmt.IsIPAddress(*b.ServerAddress) {
			b.Sni = ptrTo(*b.ServerAddress)
		}
	}
	return b, nil
}

// clashV2Ray is the vmess, vless and trojan branch: each option in the entry's order; no server or port skips the
// entry without a word ("continue").
func clashV2Ray(typ string, proxy *yamlMap) (nikofmt.Bean, error) {
	var bean nikofmt.Bean
	var v *nikofmt.StandardV2RayBean
	var vmess *nikofmt.VMessBean
	var trojan *nikofmt.TrojanBean
	switch typ {
	case "vmess":
		vmess = &nikofmt.VMessBean{}
		bean, v = vmess, &vmess.StandardV2RayBean
	case "vless":
		vmess = &nikofmt.VMessBean{AlterId: ptrTo(-1)} // make it VLESS
		vmess.PacketEncoding = ptrTo(2)                // clash meta default XUDP
		bean, v = vmess, &vmess.StandardV2RayBean
	default:
		trojan = &nikofmt.TrojanBean{}
		trojan.Security = ptrTo("tls")
		bean, v = trojan, &trojan.StandardV2RayBean
	}
	server := toStr(proxy.get("server"))
	if server == nil {
		return nil, nil
	}
	v.ServerAddress = server
	port := toIntOrNull(proxy.get("port"))
	if port == nil {
		return nil, nil
	}
	v.ServerPort = port
	isVLESS := func() bool { return vmess != nil && vmess.IsVLESS() }
	options := func(value any, each func(key string, value any) error) error {
		m, ok := value.(*yamlMap)
		if !ok {
			return nil
		}
		for i, k := range m.keys {
			key, _ := k.(string)
			if _, ok := k.(string); !ok {
				key = "\x00" // a key no branch names
			}
			if err := each(key, m.values[i]); err != nil {
				return err
			}
		}
		return nil
	}
	for i, k := range proxy.keys {
		key, _ := k.(string)
		value := proxy.values[i]
		var err error
		switch key {
		case "name":
			v.Name = toStr(value)
		case "password":
			if trojan != nil {
				trojan.Password = toStr(value)
			}
		case "uuid":
			if vmess != nil {
				v.Uuid = toStr(value)
			}
		case "alterId":
			if vmess != nil && !isVLESS() {
				vmess.AlterId = toIntOrNull(value)
			}
		case "cipher":
			if vmess != nil && !isVLESS() {
				v.Encryption = asStringOrNull(value)
			}
		case "flow":
			if vmess != nil && isVLESS() {
				if s := asStringOrNull(value); s != nil && strings.Contains(*s, "xtls-rprx-vision") {
					v.Encryption = ptrTo("xtls-rprx-vision")
				}
			}
		case "encryption":
			// mihomo's VLESS post-quantum encryption, "none" = off
			if vmess != nil && isVLESS() {
				if s := asStringOrNull(value); s != nil {
					if t := nikofmt.KtTrim(*s); t != "" && t != "none" {
						v.VlessEncryption = &t
					}
				}
			}
		case "packet-encoding":
			if vmess != nil {
				switch s := asStringOrNull(value); {
				case s != nil && *s == "packetaddr":
					v.PacketEncoding = ptrTo(1)
				case s != nil && *s == "xudp":
					v.PacketEncoding = ptrTo(2)
				default:
					v.PacketEncoding = ptrTo(0)
				}
			}
		case "tls":
			if vmess != nil {
				if isTrue(value) {
					v.Security = ptrTo("tls")
				} else {
					v.Security = ptrTo("")
				}
			}
		case "servername", "sni":
			v.Sni = toStr(value)
		case "alpn":
			v.Alpn = joinList(value, "\n")
		case "skip-cert-verify":
			v.AllowInsecure = ptrTo(isTrue(value))
		case "client-fingerprint":
			s, e := asString(value)
			if e != nil {
				return nil, e
			}
			v.UtlsFingerprint = &s
		case "reality-opts":
			err = options(value, func(key string, value any) error {
				v.Security = ptrTo("tls")
				switch key {
				case "public-key":
					v.RealityPubKey = toStr(value)
				case "short-id":
					v.RealityShortId = toStr(value)
				}
				return nil
			})
		case "network":
			switch value {
			case "h2", "http":
				v.Type = ptrTo("http")
			case "ws", "grpc":
				v.Type = ptrTo(value.(string))
			}
		case "ws-opts":
			err = options(value, func(key string, value any) error {
				switch key {
				case "headers":
					if headers, ok := value.(*yamlMap); ok {
						for i, hk := range headers.keys {
							if nikofmt.KtLowercase(javaToString(hk)) == "host" {
								v.Host = toStr(headers.values[i])
							}
						}
					}
				case "path":
					v.Path = toStr(value)
				case "max-early-data":
					v.WsMaxEarlyData = toIntOrNull(value)
				case "early-data-header-name":
					v.EarlyDataHeaderName = toStr(value)
				case "v2ray-http-upgrade":
					if isTrue(value) {
						v.Type = ptrTo("httpupgrade")
					}
				}
				return nil
			})
		case "h2-opts":
			err = options(value, func(key string, value any) error {
				switch key {
				case "host":
					v.Host = joinList(value, "\n")
				case "path":
					v.Path = toStr(value)
				}
				return nil
			})
		case "http-opts":
			err = options(value, func(key string, value any) error {
				switch key {
				case "path":
					v.Path = joinList(value, "\n")
				case "headers":
					if headers, ok := value.(*yamlMap); ok {
						for i, hk := range headers.keys {
							if nikofmt.KtLowercase(javaToString(hk)) == "host" {
								// a List<Any> by the cast: anything else throws where it is joined
								host := joinList(headers.values[i], "\n")
								if host == nil {
									return errSkip
								}
								v.Host = host
							}
						}
					}
				}
				return nil
			})
		case "grpc-opts":
			err = options(value, func(key string, value any) error {
				if key == "grpc-service-name" {
					v.Path = toStr(value)
				}
				return nil
			})
		case "smux":
			err = options(value, func(key string, value any) error {
				switch key {
				case "enabled":
					v.EnableMux = ptrTo(stringIsTrue(value))
				case "max-streams":
					n, err := toInt(value)
					if err != nil {
						return err
					}
					v.MuxConcurrency = &n
				case "padding":
					v.MuxPadding = ptrTo(stringIsTrue(value))
				}
				return nil
			})
		case "ech-opts":
			err = options(value, func(key string, value any) error {
				if key == "enable" {
					v.EnableECH = ptrTo(stringIsTrue(value))
				}
				return nil
			})
		}
		if err != nil {
			return nil, err
		}
	}
	return bean, nil
}

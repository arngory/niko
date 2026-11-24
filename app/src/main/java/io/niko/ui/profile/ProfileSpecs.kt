package io.niko.ui.profile

import io.niko.R
import io.niko.database.ProxyEntity
import io.niko.fmt.AbstractBean
import io.niko.fmt.http.HttpBean
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.internal.ChainBean
import io.niko.fmt.mieru.MieruBean
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.shadowsocks.ShadowsocksBean
import io.niko.fmt.socks.SOCKSBean
import io.niko.fmt.ssh.SSHBean
import io.niko.fmt.trojan.TrojanBean
import io.niko.fmt.trojan_go.TrojanGoBean
import io.niko.fmt.tuic.TuicBean
import io.niko.fmt.v2ray.StandardV2RayBean
import io.niko.fmt.v2ray.VMessBean
import io.niko.fmt.wireguard.WireGuardBean
import io.niko.proxy.anytls.AnyTLSBean
import io.niko.proxy.config.ConfigBean
import io.niko.proxy.shadowtls.ShadowTLSBean

/**
 * Each type's editor, field by field as its old screen had them (the old res/xml preference screens and the show/hide of
 * *SettingsActivity): the same bean fields, read and written the same way. The new screen's pattern (P-Profile) puts
 * the name and the server first, the rest in sections, a few rarely changed ones on a page of their own.
 */
object ProfileSpecs {

    /** The editor of [bean]'s type. */
    fun of(bean: AbstractBean): ProfileSpec = when (bean) {
        is HttpBean -> standard("HTTP") { HttpBean() }
        is TrojanBean -> standard("Trojan") { TrojanBean() }
        is ShadowTLSBean -> shadowTls()
        is VMessBean -> if (bean.isVLESS) standard("VLESS") { VMessBean().apply { alterId = -1 } } else standard("VMess") { VMessBean() }
        is SOCKSBean -> socks()
        is ShadowsocksBean -> shadowsocks()
        is TrojanGoBean -> trojanGo()
        is MieruBean -> mieru()
        is NaiveBean -> naive()
        is HysteriaBean -> hysteria()
        is SSHBean -> ssh()
        is WireGuardBean -> wireGuard()
        is TuicBean -> tuic()
        is AnyTLSBean -> anyTls()
        is ConfigBean -> config()
        is ChainBean -> chain()
        else -> throw IllegalArgumentException("no editor for ${bean.javaClass.simpleName}")
    }

    /** A new profile of a ProxyEntity type ([vless] of TYPE_VMESS), as the old screens' createEntity made it. */
    fun newBean(type: Int, vless: Boolean = false): AbstractBean = when (type) {
        ProxyEntity.TYPE_SOCKS -> SOCKSBean()
        ProxyEntity.TYPE_HTTP -> HttpBean()
        ProxyEntity.TYPE_SS -> ShadowsocksBean()
        ProxyEntity.TYPE_VMESS -> VMessBean().apply { if (vless) alterId = -1 }
        ProxyEntity.TYPE_TROJAN -> TrojanBean()
        ProxyEntity.TYPE_TROJAN_GO -> TrojanGoBean()
        ProxyEntity.TYPE_MIERU -> MieruBean()
        ProxyEntity.TYPE_NAIVE -> NaiveBean()
        ProxyEntity.TYPE_HYSTERIA -> HysteriaBean()
        ProxyEntity.TYPE_SSH -> SSHBean()
        ProxyEntity.TYPE_WG -> WireGuardBean()
        ProxyEntity.TYPE_TUIC -> TuicBean()
        ProxyEntity.TYPE_SHADOWTLS -> ShadowTLSBean()
        ProxyEntity.TYPE_ANYTLS -> AnyTLSBean()
        ProxyEntity.TYPE_CONFIG -> ConfigBean()
        ProxyEntity.TYPE_CHAIN -> ChainBean()
        else -> throw IllegalArgumentException("no editor for type $type")
    }

    // the menus' values as the old arrays had them (values/arrays.xml)
    private val V2RAY_NETWORKS = listOf("tcp", "ws", "http", "quic", "grpc", "httpupgrade", "xhttp")
    private val VMESS_ENCRYPTIONS = listOf("chacha20-poly1305", "aes-128-gcm", "auto", "none", "zero")
    private val XHTTP_MODES = listOf("auto", "packet-up", "stream-up", "stream-one")
    private val UTLS_FINGERPRINTS = listOf("chrome", "firefox", "edge", "safari", "360", "qq", "ios", "android", "random", "randomized")
    private val SS_METHODS = listOf(
        "2022-blake3-aes-128-gcm", "2022-blake3-aes-256-gcm", "2022-blake3-chacha20-poly1305", "none", "aes-128-gcm",
        "aes-192-gcm", "aes-256-gcm", "chacha20-ietf-poly1305", "xchacha20-ietf-poly1305", "aes-128-ctr", "aes-192-ctr",
        "aes-256-ctr", "aes-128-cfb", "aes-192-cfb", "aes-256-cfb", "rc4-md5", "chacha20-ietf", "xchacha20",
    )
    private val SS_PLUGINS = listOf("obfs-local", "v2ray-plugin")
    internal val TROJAN_GO_NETWORKS = listOf("none", "ws")
    internal val TROJAN_GO_METHODS = listOf("AES-128-GCM", "AES-256-GCM", "CHACHA20-IETF-POLY1305")
    internal val MIERU_MULTIPLEXING = listOf("default", "off", "low", "middle", "high")
    internal val MIERU_HANDSHAKE = listOf("default", "standard", "no_wait")

    private fun raw(values: List<String>) = values.map { it to Txt(it) }

    private val utls get() = listOf("" to Txt(R.string.pe_default)) + raw(UTLS_FINGERPRINTS)

    private fun on(form: ProfileForm, key: String): Txt = Txt(if (form.flag(key)) R.string.pe_on else R.string.pe_off)

    /** Name, then the server and its port side by side: every type's top. */
    private fun <B : AbstractBean> SpecBuilder<B>.server(nameOnly: Boolean = false) {
        text("name", Txt(R.string.pe_name), { name }, { name = it })
        if (nameOnly) return
        pair {
            text("serverAddress", Txt(R.string.pe_server), { serverAddress }, { serverAddress = it }, mono = true)
            int("serverPort", Txt(R.string.pe_port), { serverPort }, { serverPort = it }, input = Input.PORT)
        }
    }

    // StandardV2RaySettingsActivity: HTTP, VMess, VLESS and Trojan, told apart by the bean's class and VLESS's alterId

    private fun <B : StandardV2RayBean> standard(title: String, create: () -> B) = SpecBuilder(Txt(title), create).apply {
        val http = title == "HTTP"
        val vmess = title == "VMess"
        val vless = title == "VLESS"
        val trojan = title == "Trojan"
        section(null) {
            server()
            if (http) {
                text("username", Txt(R.string.pe_user), { (this as HttpBean).username }, { (this as HttpBean).username = it }, mono = true)
                text("password", Txt(R.string.pe_password), { (this as HttpBean).password }, { (this as HttpBean).password = it }, input = Input.SECRET, mono = true)
            } else if (trojan) {
                // the old «uuid» binding wrote Trojan's password
                text("uuid", Txt(R.string.pe_password), { (this as TrojanBean).password }, { (this as TrojanBean).password = it }, input = Input.SECRET, mono = true)
            } else {
                text("uuid", Txt(R.string.pe_uuid), { uuid }, { uuid = it }, input = Input.SECRET, mono = true)
            }
            if (vmess) {
                int("alterId", Txt("Alter ID"), { (this as VMessBean).alterId }, { (this as VMessBean).alterId = it })
                choice("encryption", Txt(R.string.encryption), raw(VMESS_ENCRYPTIONS), { encryption }, { encryption = it })
            }
            if (vless) {
                // VMess's «encryption» is VLESS's XTLS flow
                choice("encryption", Txt("Flow"), listOf("" to Txt(R.string.pe_none), "xtls-rprx-vision" to Txt("xtls-rprx-vision")), { encryption }, { encryption = it })
                text("vlessEncryption", Txt(R.string.vless_encryption), { vlessEncryption }, { vlessEncryption = it }, mono = true, help = Txt(R.string.vless_encryption_help))
            }
            if (vmess || vless) {
                intChoice("packetEncoding", Txt(R.string.packet_encoding), listOf(0 to Txt("none"), 1 to Txt("packet"), 2 to Txt("xudp")), { packetEncoding }, { packetEncoding = it })
            }
        }
        val network: (ProfileForm) -> String = { it.text("type") }
        section(Txt(R.string.pe_transport), visible = { !http }) {
            choice("type", Txt(R.string.network), raw(V2RAY_NETWORKS), { type }, { type = it }, visible = { !http })
            text(
                "host", Txt(R.string.http_host), { host }, { host = it }, mono = true,
                labelOf = { f -> Txt(if (network(f) == "ws") R.string.ws_host else if (network(f) == "httpupgrade") R.string.http_upgrade_host else R.string.http_host) },
                visible = { f -> !http && network(f) in setOf("http", "ws", "httpupgrade", "xhttp") },
            )
            text(
                "path", Txt(R.string.http_path), { path }, { path = it }, mono = true,
                labelOf = { f ->
                    Txt(
                        when (network(f)) {
                            "ws" -> R.string.ws_path
                            "grpc" -> R.string.grpc_service_name
                            "httpupgrade" -> R.string.http_upgrade_path
                            else -> R.string.http_path
                        }
                    )
                },
                visible = { f -> !http && network(f) in setOf("http", "ws", "grpc", "httpupgrade", "xhttp") },
            )
            // the WebSocket's and XHTTP's own (serverWsCategory, serverXhttpCategory)
            int("wsMaxEarlyData", Txt(R.string.ws_max_early_data), { wsMaxEarlyData }, { wsMaxEarlyData = it }, visible = { f -> !http && network(f) == "ws" })
            text("earlyDataHeaderName", Txt(R.string.early_data_header_name), { earlyDataHeaderName }, { earlyDataHeaderName = it }, mono = true, visible = { f -> !http && network(f) == "ws" })
            choice("xhttpMode", Txt(R.string.pe_xhttp_mode), raw(XHTTP_MODES), { xhttpMode }, { xhttpMode = it }, visible = { f -> !http && network(f) == "xhttp" })
            text("xhttpExtra", Txt(R.string.xhttp_extra), { xhttpExtra }, { xhttpExtra = it }, mono = true, lines = true, help = Txt(R.string.xhttp_extra_help), visible = { f -> !http && network(f) == "xhttp" })
        }
        val tls: (ProfileForm) -> Boolean = { "tls" in it.text("security") }
        section(Txt("TLS")) {
            choice("security", Txt(R.string.security), listOf("none" to Txt(R.string.pe_none), "tls" to Txt("TLS")), { security }, { security = it })
            securityFields(visible = tls)
        }
        section(Txt("REALITY"), visible = tls) {
            pair(equal = true) {
                text("realityPubKey", Txt(R.string.pe_public_key), { realityPubKey }, { realityPubKey = it }, mono = true)
                text("realityShortId", Txt("Short ID"), { realityShortId }, { realityShortId = it }, mono = true)
            }
            choice(
                "realityKeyShare", Txt(R.string.reality_key_share),
                listOf("" to Txt(R.string.reality_key_share_auto), "hybrid" to Txt(R.string.reality_key_share_hybrid), "classical" to Txt(R.string.reality_key_share_classical)),
                { realityKeyShare }, { realityKeyShare = it }, sheet = true,
            )
            text("realityMldsa65Verify", Txt(R.string.reality_mldsa65), { realityMldsa65Verify }, { realityMldsa65Verify = it }, mono = true, help = Txt(R.string.reality_mldsa65_summary))
        }
        section(Txt("ECH"), visible = tls) {
            flag("enableECH", Txt(R.string.enable), { enableECH }, { enableECH = it })
            text("echConfig", Txt(R.string.ech_config), { echConfig }, { echConfig = it }, mono = true, lines = true)
        }
        section(Txt(R.string.mux_preference), page = "mux", summary = { on(it, "enableMux") }) {
            flag("enableMux", Txt(R.string.enable_mux), { enableMux }, { enableMux = it }, sub = Txt(R.string.mux_sum))
            intChoice("muxType", Txt(R.string.mux_type), listOf(0 to Txt("h2mux"), 1 to Txt("smux"), 2 to Txt("yamux")), { muxType }, { muxType = it })
            int("muxConcurrency", Txt(R.string.mux_concurrency), { muxConcurrency }, { muxConcurrency = it })
            flag("muxPadding", Txt(R.string.padding), { muxPadding }, { muxPadding = it })
        }
    }.build()

    /** SNI, ALPN, certificates, «insecure» and the uTLS fingerprint: TLS of the V2Ray family, ShadowTLS's, AnyTLS's. */
    private fun <B : AbstractBean> SpecBuilder<B>.securityFields(
        visible: (ProfileForm) -> Boolean = { true },
        sni: B.() -> String?,
        setSni: B.(String) -> Unit,
        alpn: B.() -> String?,
        setAlpn: B.(String) -> Unit,
        certificates: B.() -> String?,
        setCertificates: B.(String) -> Unit,
        insecure: B.() -> Boolean?,
        setInsecure: B.(Boolean) -> Unit,
        fingerprint: (B.() -> String?)?,
        setFingerprint: B.(String) -> Unit,
        insecureSub: Boolean = true,
    ) {
        text("sni", Txt(R.string.pe_sni), sni, setSni, mono = true, visible = visible)
        if (fingerprint != null) choice("utlsFingerprint", Txt(R.string.utls_fingerprint), utls, fingerprint, setFingerprint, visible = visible)
        text("alpn", Txt("ALPN"), alpn, setAlpn, mono = true, visible = visible)
        text("certificates", Txt(R.string.certificates), certificates, setCertificates, mono = true, lines = true, visible = visible)
        flag("allowInsecure", Txt(R.string.allow_insecure), insecure, setInsecure, sub = if (insecureSub) Txt(R.string.allow_insecure_sum) else null, visible = visible)
    }

    private fun <B : StandardV2RayBean> SpecBuilder<B>.securityFields(visible: (ProfileForm) -> Boolean = { true }, insecureSub: Boolean = true) = securityFields(
        visible, { sni }, { sni = it }, { alpn }, { alpn = it }, { certificates }, { certificates = it },
        { allowInsecure }, { allowInsecure = it }, { utlsFingerprint }, { utlsFingerprint = it }, insecureSub,
    )

    private fun shadowTls() = SpecBuilder(Txt("ShadowTLS")) { ShadowTLSBean() }.apply {
        section(null) {
            server()
            intChoice("version", Txt(R.string.protocol_version), listOf(2 to Txt("2"), 3 to Txt("3")), { version }, { version = it })
            text("password", Txt(R.string.pe_password), { password }, { password = it }, input = Input.SECRET, mono = true)
        }
        section(Txt("TLS")) { securityFields() }
    }.build()

    private fun anyTls() = SpecBuilder(Txt("AnyTLS")) { AnyTLSBean() }.apply {
        section(null) {
            server()
            text("password", Txt(R.string.pe_password), { password }, { password = it }, input = Input.SECRET, mono = true)
        }
        section(Txt("TLS")) {
            securityFields(
                sni = { sni }, setSni = { sni = it }, alpn = { alpn }, setAlpn = { alpn = it },
                certificates = { certificates }, setCertificates = { certificates = it },
                insecure = { allowInsecure }, setInsecure = { allowInsecure = it },
                fingerprint = { utlsFingerprint }, setFingerprint = { utlsFingerprint = it }, insecureSub = false,
            )
        }
    }.build()

    private fun socks() = SpecBuilder(Txt("SOCKS")) { SOCKSBean() }.apply {
        section(null) {
            server()
            intChoice(
                "protocol", Txt(R.string.app_version),
                listOf(SOCKSBean.PROTOCOL_SOCKS4 to Txt("SOCKS4"), SOCKSBean.PROTOCOL_SOCKS4A to Txt("SOCKS4A"), SOCKSBean.PROTOCOL_SOCKS5 to Txt("SOCKS5")),
                { protocol }, { protocol = it },
            )
            text("username", Txt(R.string.pe_user), { username }, { username = it }, mono = true)
            // SOCKS4 has no password
            text("password", Txt(R.string.pe_password), { password }, { password = it }, input = Input.SECRET, mono = true,
                visible = { it.text("protocol") == SOCKSBean.PROTOCOL_SOCKS5.toString() })
        }
        section(Txt("sing-box")) {
            flag("sUoT", Txt("UDP over TCP"), { sUoT }, { sUoT = it })
        }
    }.build()

    private fun shadowsocks() = SpecBuilder(Txt("Shadowsocks")) { ShadowsocksBean() }.apply {
        section(null) {
            server()
            choice("method", Txt(R.string.enc_method), raw(SS_METHODS), { method }, { method = it })
            text("password", Txt(R.string.pe_password), { password }, { password = it }, input = Input.SECRET, mono = true)
        }
        section(null) {
            // the plugin is one string, "name;options": its two halves as the old screen split it
            step(load = { b, v ->
                v.text["pluginName"] = b.plugin.orEmpty().substringBefore(";")
                v.text["pluginConfig"] = b.plugin.orEmpty().substringAfter(";")
            })
            choice("pluginName", Txt(R.string.plugin), listOf("" to Txt(R.string.pe_none)) + raw(SS_PLUGINS))
            text("pluginConfig", Txt(R.string.pe_plugin_options), mono = true, visible = { it.text("pluginName").isNotBlank() })
            step(save = { b, f ->
                val name = f.text("pluginName")
                b.plugin = if (name.isNotBlank()) "$name;${f.text("pluginConfig")}" else ""
            })
        }
        section(Txt("sing-box")) {
            flag("sUoT", Txt("UDP over TCP"), { sUoT }, { sUoT = it })
        }
    }.build()

    private fun trojanGo() = SpecBuilder(Txt("Trojan-Go")) { TrojanGoBean() }.apply {
        section(null) {
            server()
            text("password", Txt(R.string.pe_password), { password }, { password = it }, input = Input.SECRET, mono = true)
            text("sni", Txt(R.string.pe_sni), { sni }, { sni = it }, mono = true)
            flag("allowInsecure", Txt(R.string.allow_insecure), { allowInsecure }, { allowInsecure = it }, sub = Txt(R.string.allow_insecure_sum))
            flag("mux", Txt(R.string.enable_mux), { mux }, { mux = it }, sub = Txt(R.string.trojan_go_mux_sum))
            // a type the menu has not (the bean's default "original") shows and is saved as its first, «NONE»
            choice("type", Txt(R.string.network), listOf("none" to Txt(R.string.pe_none), "ws" to Txt("WebSocket")),
                { type.takeIf { it in TROJAN_GO_NETWORKS } ?: TROJAN_GO_NETWORKS[0] }, { type = it })
            // "ss;method:password" in one string, in three fields
            step(load = { b, v ->
                val encryption = b.encryption.orEmpty()
                if (encryption.startsWith("ss;")) {
                    v.text["encryption"] = "ss"
                    v.text["method"] = encryption.substringAfter(";").substringBefore(":")
                    v.text["password1"] = encryption.substringAfter(":")
                } else {
                    v.text["encryption"] = encryption
                }
                // a method the menu has not shows and is saved as its first
                if (v.text["method"] !in TROJAN_GO_METHODS) v.text["method"] = TROJAN_GO_METHODS[0]
            })
            choice("encryption", Txt(R.string.encryption), listOf("none" to Txt(R.string.pe_none), "ss" to Txt("Shadowsocks")))
        }
        section(Txt("WebSocket"), visible = { it.text("type") == "ws" }) {
            text("host", Txt(R.string.ws_host), { host }, { host = it }, mono = true)
            text("path", Txt(R.string.ws_path), { path }, { path = it }, mono = true)
        }
        section(Txt("Shadowsocks"), visible = { it.text("encryption") == "ss" }) {
            choice("method", Txt(R.string.enc_method), raw(TROJAN_GO_METHODS), sheet = true)
            text("password1", Txt(R.string.pe_password), input = Input.SECRET, mono = true)
            step(save = { b, f ->
                b.encryption = when (val security = f.text("encryption")) {
                    "ss" -> "ss;" + f.text("method") + ":" + f.text("password1")
                    else -> security
                }
            })
        }
    }.build()

    private fun mieru() = SpecBuilder(Txt("Mieru")) { MieruBean() }.apply {
        // a link's MULTIPLEXING_HIGH or HANDSHAKE_NO_WAIT as the menu's "high" or "no_wait"; none: mieru's default
        fun menuValue(kept: String?, prefix: String, values: List<String>) =
            kept.orEmpty().trim().removePrefix(prefix).lowercase().takeIf { it in values } ?: "default"
        section(null) {
            server()
            choice("protocol", Txt(R.string.protocol), raw(listOf("TCP", "UDP")), { protocol }, { protocol = it })
            text("username", Txt(R.string.pe_user), { username }, { username = it }, mono = true)
            text("password", Txt(R.string.pe_password), { password }, { password = it }, input = Input.SECRET, mono = true)
            int("mtu", Txt("MTU"), { mtu }, { mtu = it }, visible = { it.text("protocol") == "UDP" })
            choice(
                "multiplexing", Txt(R.string.mieru_multiplexing),
                listOf(
                    "default" to Txt(R.string.mieru_default), "off" to Txt(R.string.mieru_multiplexing_off), "low" to Txt(R.string.mieru_multiplexing_low),
                    "middle" to Txt(R.string.mieru_multiplexing_middle), "high" to Txt(R.string.mieru_multiplexing_high),
                ),
                { menuValue(multiplexing, "MULTIPLEXING_", MIERU_MULTIPLEXING) }, { multiplexing = it.takeIf { v -> v != "default" }.orEmpty() },
            )
            choice(
                "handshakeMode", Txt(R.string.mieru_handshake),
                listOf("default" to Txt(R.string.mieru_default), "standard" to Txt(R.string.mieru_handshake_standard), "no_wait" to Txt(R.string.mieru_handshake_no_wait)),
                { menuValue(handshakeMode, "HANDSHAKE_", MIERU_HANDSHAKE) }, { handshakeMode = it.takeIf { v -> v != "default" }.orEmpty() },
                sheet = true,
            )
            text("trafficPattern", Txt(R.string.mieru_traffic_pattern), { trafficPattern }, { trafficPattern = it.trim() }, mono = true, help = Txt(R.string.mieru_traffic_pattern_sum))
        }
    }.build()

    private fun naive() = SpecBuilder(Txt("naive")) { NaiveBean() }.apply {
        section(null) {
            server()
            text("username", Txt(R.string.pe_user), { username }, { username = it }, mono = true)
            text("password", Txt(R.string.pe_password), { password }, { password = it }, input = Input.SECRET, mono = true)
            choice("proto", Txt(R.string.protocol), listOf("https" to Txt("HTTPS"), "quic" to Txt("QUIC")), { proto }, { proto = it })
        }
        section(Txt("TLS")) {
            text("sni", Txt(R.string.pe_sni), { sni }, { sni = it }, mono = true)
            text("certificates", Txt(R.string.certificates), { certificates }, { certificates = it }, mono = true, lines = true)
        }
        section(Txt(R.string.pe_advanced)) {
            text("extraHeaders", Txt(R.string.extra_headers), { extraHeaders }, { extraHeaders = it.replace("\r\n", "\n") }, mono = true, lines = true)
            int("insecureConcurrency", Txt(R.string.naive_insecure_concurrency), { insecureConcurrency }, { insecureConcurrency = it }, help = Txt(R.string.naive_insecure_concurrency_summary))
            flag("sUoT", Txt(R.string.naive_uot), { sUoT }, { sUoT = it }, sub = Txt(R.string.naive_uot_summary))
        }
    }.build()

    private fun hysteria() = SpecBuilder(Txt("Hysteria")) { HysteriaBean() }.apply {
        val v1: (ProfileForm) -> Boolean = { (it.text("protocolVersion").toIntOrNull() ?: 1) != 2 }
        section(null) {
            text("name", Txt(R.string.pe_name), { name }, { name = it })
            intChoice("protocolVersion", Txt(R.string.protocol_version), listOf(1 to Txt("1"), 2 to Txt("2")), { protocolVersion }, { protocolVersion = it }, default = 2)
            pair {
                text("serverAddress", Txt(R.string.pe_server), { serverAddress }, { serverAddress = it }, mono = true)
                text("serverPorts", Txt(R.string.pe_ports), { serverPorts }, { serverPorts = it }, mono = true)
            }
            text("obfuscation", Txt(R.string.hysteria_obfs), { obfuscation }, { obfuscation = it }, input = Input.SECRET, mono = true)
            intChoice(
                "authPayloadType", Txt(R.string.hysteria_auth_type),
                listOf(HysteriaBean.TYPE_NONE to Txt(R.string.pe_none), HysteriaBean.TYPE_STRING to Txt("STRING"), HysteriaBean.TYPE_BASE64 to Txt("BASE64")),
                { authPayloadType }, { authPayloadType = it }, visible = v1,
            )
            text(
                "authPayload", Txt(R.string.pe_password), { authPayload }, { authPayload = it }, input = Input.SECRET, mono = true,
                labelOf = { f -> Txt(if (v1(f)) R.string.hysteria_auth_payload else R.string.pe_password) },
                visible = { f -> !v1(f) || f.text("authPayloadType") != HysteriaBean.TYPE_NONE.toString() },
            )
            intChoice(
                "protocol", Txt(R.string.protocol),
                listOf(HysteriaBean.PROTOCOL_UDP to Txt("UDP"), HysteriaBean.PROTOCOL_FAKETCP to Txt("FakeTCP (Root Required)"), HysteriaBean.PROTOCOL_WECHAT_VIDEO to Txt("WeChat Video")),
                { protocol }, { protocol = it }, sheet = true, visible = v1,
            )
        }
        section(Txt("TLS")) {
            text("sni", Txt(R.string.pe_sni), { sni }, { sni = it }, mono = true)
            text("alpn", Txt("ALPN"), { alpn }, { alpn = it }, mono = true, visible = v1)
            text("caText", Txt(R.string.certificates), { caText }, { caText = it }, mono = true, lines = true)
            flag("allowInsecure", Txt(R.string.allow_insecure), { allowInsecure }, { allowInsecure = it }, sub = Txt(R.string.allow_insecure_sum))
        }
        section(Txt(R.string.pe_speed)) {
            pair(equal = true) {
                int("uploadMbps", Txt(R.string.pe_upload), { uploadMbps }, { uploadMbps = it })
                int("downloadMbps", Txt(R.string.pe_download), { downloadMbps }, { downloadMbps = it })
            }
        }
        section(Txt("QUIC"), visible = v1) {
            // 0 shows as empty, as the old editor kept these two (stored only when above 0)
            text("streamReceiveWindow", Txt(R.string.hysteria_stream_receive_window), { streamReceiveWindow?.takeIf { it > 0 }?.toString() }, { streamReceiveWindow = it.toIntOrNull() ?: 0 }, input = Input.NUMBER, mono = true)
            text("connectionReceiveWindow", Txt(R.string.hysteria_connection_receive_window), { connectionReceiveWindow?.takeIf { it > 0 }?.toString() }, { connectionReceiveWindow = it.toIntOrNull() ?: 0 }, input = Input.NUMBER, mono = true)
            flag("disableMtuDiscovery", Txt(R.string.hysteria_disable_mtu_discovery), { disableMtuDiscovery }, { disableMtuDiscovery = it })
        }
        section(Txt(R.string.pe_hopping)) {
            pair(equal = true) {
                int("hopInterval", Txt(R.string.pe_hop_interval), { hopInterval }, { hopInterval = it }, default = 10)
                int("hopIntervalMax", Txt(R.string.pe_hop_interval_max), { hopIntervalMax }, { hopIntervalMax = it })
            }
        }
    }.build()

    private fun ssh() = SpecBuilder(Txt("SSH")) { SSHBean() }.apply {
        val auth: (ProfileForm) -> Int = { it.text("authType").toIntOrNull() ?: 0 }
        section(null) {
            server()
            text("username", Txt(R.string.pe_user), { username }, { username = it }, mono = true)
        }
        section(Txt(R.string.pe_auth)) {
            intChoice(
                "authType", Txt(R.string.hysteria_auth_type),
                listOf(SSHBean.AUTH_TYPE_NONE to Txt(R.string.pe_none), SSHBean.AUTH_TYPE_PASSWORD to Txt(R.string.pe_password), SSHBean.AUTH_TYPE_PRIVATE_KEY to Txt(R.string.pe_ssh_key)),
                { authType }, { authType = it },
            )
            // the password or the key of the type chosen: the other one stays as it was
            text("password", Txt(R.string.pe_password), { password }, { if (authType == SSHBean.AUTH_TYPE_PASSWORD) password = it },
                input = Input.SECRET, mono = true, visible = { auth(it) == SSHBean.AUTH_TYPE_PASSWORD })
            text("privateKey", Txt(R.string.ssh_private_key), { privateKey }, { if (authType == SSHBean.AUTH_TYPE_PRIVATE_KEY) privateKey = it },
                mono = true, lines = true, visible = { auth(it) == SSHBean.AUTH_TYPE_PRIVATE_KEY })
            text("privateKeyPassphrase", Txt(R.string.ssh_private_key_passphrase), { privateKeyPassphrase }, { if (authType == SSHBean.AUTH_TYPE_PRIVATE_KEY) privateKeyPassphrase = it },
                input = Input.SECRET, mono = true, visible = { auth(it) == SSHBean.AUTH_TYPE_PRIVATE_KEY })
            text("publicKey", Txt(R.string.pe_ssh_host_key), { publicKey }, { publicKey = it }, mono = true, lines = true)
        }
    }.build()

    private fun wireGuard() = SpecBuilder(Txt("WireGuard")) { WireGuardBean() }.apply {
        section(null) {
            server()
            text("localAddress", Txt(R.string.wireguard_local_address), { localAddress }, { localAddress = it }, mono = true, lines = true)
            text("privateKey", Txt(R.string.ssh_private_key), { privateKey }, { privateKey = it }, input = Input.SECRET, mono = true)
            text("peerPublicKey", Txt(R.string.wireguard_public_key), { peerPublicKey }, { peerPublicKey = it }, mono = true)
            text("peerPreSharedKey", Txt(R.string.wireguard_psk), { peerPreSharedKey }, { peerPreSharedKey = it }, input = Input.SECRET, mono = true)
            pair(equal = true) {
                text("reserved", Txt("Reserved"), { reserved }, { reserved = it }, mono = true)
                int("mtu", Txt("MTU"), { mtu }, { mtu = it })
            }
            int("persistentKeepalive", Txt(R.string.wireguard_persistent_keepalive), { persistentKeepalive }, { persistentKeepalive = it })
        }
        section(Txt("AmneziaWG")) {
            text("amneziaWG", Txt(R.string.amneziawg_params), { amneziaWG }, { amneziaWG = it }, mono = true, lines = true, help = Txt(R.string.amneziawg_help))
        }
    }.build()

    private fun tuic() = SpecBuilder(Txt("TUIC")) { TuicBean() }.apply {
        section(null) {
            server()
            text("uuid", Txt(R.string.pe_uuid), { uuid }, { uuid = it }, mono = true)
            text("token", Txt(R.string.pe_password), { token }, { token = it }, input = Input.SECRET, mono = true)
            choice("udpRelayMode", Txt(R.string.tuic_udp_relay_mode), raw(listOf("native", "quic")), { udpRelayMode }, { udpRelayMode = it })
            choice("congestionController", Txt(R.string.tuic_congestion_controller), raw(listOf("cubic", "new_reno", "bbr")), { congestionController }, { congestionController = it })
        }
        section(Txt("TLS")) {
            flag("disableSNI", Txt(R.string.tuic_disable_sni), { disableSNI }, { disableSNI = it })
            // «Отключить SNI» locks the name
            text("sni", Txt(R.string.pe_sni), { sni }, { sni = it }, mono = true, enabled = { !it.flag("disableSNI") })
            text("alpn", Txt("ALPN"), { alpn }, { alpn = it }, mono = true)
            text("caText", Txt(R.string.certificates), { caText }, { caText = it }, mono = true, lines = true)
            flag("reduceRTT", Txt(R.string.tuic_reduce_rtt), { reduceRTT }, { reduceRTT = it })
            flag("allowInsecure", Txt(R.string.allow_insecure), { allowInsecure }, { allowInsecure = it })
        }
    }.build()

    private fun config() = SpecBuilder(Txt(R.string.pe_config)) { ConfigBean() }.apply {
        section(null) {
            server(nameOnly = true)
            // type 1: the JSON is an outbound; 0: the whole config
            flag("isOutboundOnly", Txt(R.string.is_outbound_only), { type == 1 }, { type = if (it) 1 else 0 })
            text("config", Txt(R.string.custom_config), { config }, { config = it }, visible = { false })
            json("config", Txt(R.string.custom_config))
        }
    }.build()

    private fun chain() = SpecBuilder(Txt(R.string.pe_chain)) { ChainBean() }.apply {
        tweaks = false
        section(null) {
            server(nameOnly = true)
        }
        section(Txt(R.string.pe_chain_links)) {
            chain()
            step(save = { b, f ->
                b.proxies = f.chain
                b.initializeDefaultValues()
            })
        }
    }.build()
}

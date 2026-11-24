package io.niko.ui.profile

import io.niko.R
import io.niko.database.ProxyEntity
import io.niko.fmt.AbstractBean
import io.niko.fmt.KryoConverters
import io.niko.fmt.http.HttpBean
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.internal.ChainBean
import io.niko.fmt.mieru.MieruBean
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.parseCertificatePins
import io.niko.fmt.parseKnockSequence
import io.niko.fmt.shadowsocks.ShadowsocksBean
import io.niko.fmt.socks.SOCKSBean
import io.niko.fmt.ssh.SSHBean
import io.niko.fmt.trojan.TrojanBean
import io.niko.fmt.trojan_go.TrojanGoBean
import io.niko.fmt.tuic.TuicBean
import io.niko.fmt.v2ray.StandardV2RayBean
import io.niko.fmt.v2ray.VMessBean
import io.niko.fmt.wireguard.WireGuardBean
import io.niko.ktx.applyDefaultValues
import io.niko.proxy.anytls.AnyTLSBean
import io.niko.proxy.config.ConfigBean
import io.niko.proxy.shadowtls.ShadowTLSBean
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The profile editor's form: for every type a bean with every field set (none at its default) goes into the form and
 * back unchanged, byte for byte as it is stored, and the form carries each field into a new bean; what the old
 * editors changed on the way (a Trojan-Go type the menu has not, a link's Mieru values, naive's line ends) they still
 * change; the connection tweaks are refused, and stored, as the old dialog did.
 */
class ProfileFormTest {

    private val pin = "47DEQpj8HBSa+/TImW+5JCeuQeRkm5NMpJWZG3hSuFU="
    private val dnsttKey = "0123456789abcdef".repeat(4)

    /** Every field shared by the types set away from its default, the tweaks and the custom JSON with them. */
    private fun <B : AbstractBean> B.common(): B = apply {
        name = "Амстердам"
        serverAddress = "ams.example"
        serverPort = 8443
        customOutboundJson = "{\"tag\": \"out\"}"
        customConfigJson = "{\"log\": {}}"
        knockSequence = "7000, 8000/udp"
        knockInterval = 300
        knockTtl = 20
        knockSilence = 30
        tlsFragment = 2
        certPins = pin
        dnsTunnel = "dnstt"
        dnsTunnelResolver = "https://dns.example/dns-query"
        dnsTunnelDomain = "t.example.com"
        dnsTunnelKey = dnsttKey
        dnsTunnelMethod = 3
        dnsTunnelFixedUpstream = true
        dnsTunnelAuth = "v1;node;svc"
        dnsTunnelSeal = "seal"
        dnsTunnelSpare = "t2.example.com"
        udpPrelude = 2
        udpPreludeSni = "allowed.example.ru"
        udpPreludeTtl = 5
        udpReorderInitial = true
    }

    private fun <B : StandardV2RayBean> B.v2ray(): B = apply {
        uuid = "a3482e88-686a-4a58-8126-99c9df64b7bf"
        type = "xhttp"
        host = "cdn.example"
        path = "/x"
        wsMaxEarlyData = 2048
        earlyDataHeaderName = "Sec-WebSocket-Protocol"
        xhttpMode = "stream-one"
        xhttpExtra = "{\"xPaddingBytes\": \"100-1000\"}"
        security = "tls"
        sni = "www.example.nl"
        alpn = "h2"
        certificates = "-----BEGIN CERTIFICATE-----"
        allowInsecure = true
        utlsFingerprint = "firefox"
        realityPubKey = "pbk"
        realityShortId = "0123abcd"
        realityKeyShare = "hybrid"
        realityMldsa65Verify = "pqv"
        enableECH = true
        echConfig = "ech"
        enableMux = true
        muxPadding = true
        muxType = 2
        muxConcurrency = 8
        packetEncoding = 2
    }

    /** Each type's bean, all of its fields set. */
    private fun everyType(): List<AbstractBean> = listOf(
        SOCKSBean().applyDefaultValues().common().apply {
            protocol = SOCKSBean.PROTOCOL_SOCKS4A; username = "u"; password = "p"; sUoT = true
        },
        HttpBean().applyDefaultValues().common().v2ray().apply { username = "u"; password = "p" },
        ShadowsocksBean().applyDefaultValues().common().apply {
            method = "2022-blake3-aes-128-gcm"; password = "p"; plugin = "v2ray-plugin;mode=websocket;host=cdn.example"; sUoT = true
        },
        VMessBean().applyDefaultValues().common().v2ray().apply { alterId = 4; encryption = "aes-128-gcm" },
        VMessBean().apply { alterId = -1 }.applyDefaultValues().common().v2ray().apply {
            encryption = "xtls-rprx-vision"; vlessEncryption = "mlkem768x25519plus.native.0rtt.key"
        },
        TrojanBean().applyDefaultValues().common().v2ray().apply { password = "p"; security = "none" },
        TrojanGoBean().applyDefaultValues().common().apply {
            password = "p"; sni = "sni.example"; allowInsecure = true; mux = true; type = "ws"; host = "ws.example"; path = "/ws"
            encryption = "ss;AES-256-GCM:secret"
        },
        MieruBean().applyDefaultValues().common().apply {
            protocol = "UDP"; username = "u"; password = "p"; mtu = 1300; multiplexing = "high"; handshakeMode = "no_wait"; trafficPattern = "CAESAg=="
        },
        NaiveBean().applyDefaultValues().common().apply {
            username = "maria"; password = "p"; proto = "quic"; extraHeaders = "X-A: 1\nX-B: 2"; sni = "sni.example"
            certificates = "cert"; insecureConcurrency = 2; sUoT = true
        },
        HysteriaBean().apply { protocolVersion = 1 }.applyDefaultValues().common().apply {
            serverPorts = "443,20000-30000"; obfuscation = "obfs"; authPayloadType = HysteriaBean.TYPE_BASE64; authPayload = "cGF5"
            protocol = HysteriaBean.PROTOCOL_WECHAT_VIDEO; sni = "sni.example"; alpn = "hysteria"; caText = "ca"; allowInsecure = true
            uploadMbps = 20; downloadMbps = 100; streamReceiveWindow = 8388608; connectionReceiveWindow = 20971520
            disableMtuDiscovery = true; hopInterval = 30; hopIntervalMax = 60
        },
        SSHBean().applyDefaultValues().common().apply {
            username = "admin"; authType = SSHBean.AUTH_TYPE_PRIVATE_KEY; password = "p"; privateKey = "-----BEGIN OPENSSH PRIVATE KEY-----"
            privateKeyPassphrase = "phrase"; publicKey = "ssh-ed25519 AAAA"
        },
        WireGuardBean().applyDefaultValues().common().apply {
            localAddress = "10.0.0.2/32"; privateKey = "priv"; peerPublicKey = "pub"; peerPreSharedKey = "psk"; mtu = 1280
            reserved = "1,2,3"; persistentKeepalive = 25; amneziaWG = "Jc = 4\nJmin = 40"
        },
        TuicBean().applyDefaultValues().common().apply {
            uuid = "a3482e88-686a-4a58-8126-99c9df64b7bf"; token = "t"; alpn = "h3"; caText = "ca"; udpRelayMode = "quic"
            congestionController = "bbr"; disableSNI = true; sni = "sni.example"; reduceRTT = true; allowInsecure = true
        },
        ShadowTLSBean().applyDefaultValues().common().apply {
            version = 2; password = "p"; sni = "sni.example"; alpn = "h2"; certificates = "c"; allowInsecure = true; utlsFingerprint = "safari"
        },
        AnyTLSBean().applyDefaultValues().common().apply {
            password = "p"; sni = "sni.example"; alpn = "h2"; certificates = "c"; allowInsecure = true; utlsFingerprint = "ios"
        },
        ConfigBean().applyDefaultValues().common().apply { type = 1; config = "{\"type\": \"direct\"}" },
        ChainBean().applyDefaultValues().common().apply { proxies = mutableListOf(3L, 1L, 2L) },
    )

    private fun bytes(bean: AbstractBean) = KryoConverters.serialize(bean)

    private val ProfileSpec.name get() = title.raw ?: "#${title.res}"

    @Test
    fun everyTypeHasItsEditor() {
        val types = listOf(
            ProxyEntity.TYPE_SOCKS, ProxyEntity.TYPE_HTTP, ProxyEntity.TYPE_SS, ProxyEntity.TYPE_VMESS, ProxyEntity.TYPE_TROJAN,
            ProxyEntity.TYPE_TROJAN_GO, ProxyEntity.TYPE_MIERU, ProxyEntity.TYPE_NAIVE, ProxyEntity.TYPE_HYSTERIA,
            ProxyEntity.TYPE_SSH, ProxyEntity.TYPE_WG, ProxyEntity.TYPE_TUIC, ProxyEntity.TYPE_SHADOWTLS,
            ProxyEntity.TYPE_ANYTLS, ProxyEntity.TYPE_CONFIG, ProxyEntity.TYPE_CHAIN,
        )
        val titles = types.map { ProfileSpecs.of(ProfileSpecs.newBean(it).applyDefaultValues()).name } +
            ProfileSpecs.of(ProfileSpecs.newBean(ProxyEntity.TYPE_VMESS, vless = true).applyDefaultValues()).name
        assertEquals(
            listOf("SOCKS", "HTTP", "Shadowsocks", "VMess", "Trojan", "Trojan-Go", "Mieru", "naive", "Hysteria", "SSH", "WireGuard", "TUIC", "ShadowTLS", "AnyTLS", "#${R.string.pe_config}", "#${R.string.pe_chain}", "VLESS"),
            titles,
        )
    }

    /** An existing profile opened and saved as it is: the bean stored byte for byte as before. */
    @Test
    fun savedUnchangedIsStoredAsItWas() {
        for (bean in everyType()) {
            val before = bytes(bean)
            val spec = ProfileSpecs.of(bean)
            val form = spec.load(bean)
            spec.save(form, bean, form)
            assertArrayEquals(spec.name, before, bytes(bean))
            assertEquals(spec.name, "v1;node;svc", bean.dnsTunnelAuth)
        }
    }

    /** The form carries every field: written into a new bean of the type and read again, it is the same form. */
    @Test
    fun theFormCarriesEveryField() {
        for (bean in everyType()) {
            val spec = ProfileSpecs.of(bean)
            val form = spec.load(bean)
            val defaults = spec.load(spec.create().applyDefaultValues())
            for ((key, value) in form.text) assertNotEquals("${spec.name}: $key is its default", defaults.text(key), value)
            for ((key, value) in form.flags) assertNotEquals("${spec.name}: $key is its default", defaults.flag(key), value)
            assertNotEquals(spec.name, defaults.tweaks, form.tweaks)
            val made = spec.create()
            spec.save(form, made, form)
            made.applyDefaultValues()
            // SSH writes the secret of the kind chosen (a key here) and leaves the password as it was
            val expected = if (bean is SSHBean) form.withText("password", "") else form
            assertEquals(spec.name, expected, spec.load(made))
            assertEquals(spec.name, bean.javaClass, made.javaClass)
        }
    }

    /** A new profile is the old createEntity's: VLESS a VMess bean with alterId -1 that stays VLESS when saved. */
    @Test
    fun aNewVlessStaysVless() {
        val spec = ProfileSpecs.of(ProfileSpecs.newBean(ProxyEntity.TYPE_VMESS, vless = true).applyDefaultValues())
        val form = spec.load(ProfileSpecs.newBean(ProxyEntity.TYPE_VMESS, vless = true).applyDefaultValues())
        val made = spec.create() as VMessBean
        spec.save(form.withText("uuid", "id"), made, form)
        made.applyDefaultValues()
        assertTrue(made.isVLESS)
        assertEquals("id", made.uuid)
    }

    @Test
    fun aTrojanGoTypeTheMenuHasNotIsSavedAsNone() {
        val bean = TrojanGoBean().applyDefaultValues().apply { encryption = "ss;aes-128-gcm:secret" }
        assertEquals("original", bean.type)
        val spec = ProfileSpecs.of(bean)
        val form = spec.load(bean)
        spec.save(form, bean, form)
        assertEquals("none", bean.type)
        // the old menu's method upper-cased: a lower-case one became its first
        assertEquals("ss;AES-128-GCM:secret", bean.encryption)
    }

    @Test
    fun aLinksMieruValuesBecomeTheMenus() {
        val bean = MieruBean().applyDefaultValues().apply { multiplexing = "MULTIPLEXING_HIGH"; handshakeMode = "unknown"; trafficPattern = " p " }
        val spec = ProfileSpecs.of(bean)
        val form = spec.load(bean)
        assertEquals("high", form.text("multiplexing"))
        assertEquals("default", form.text("handshakeMode"))
        spec.save(form, bean, form)
        assertEquals("high", bean.multiplexing)
        assertEquals("", bean.handshakeMode)
        assertEquals("p", bean.trafficPattern)
    }

    @Test
    fun naiveHeadersLoseTheirCarriageReturns() {
        val bean = NaiveBean().applyDefaultValues()
        val spec = ProfileSpecs.of(bean)
        val form = spec.load(bean)
        spec.save(form.withText("extraHeaders", "A: 1\r\nB: 2"), bean, form)
        assertEquals("A: 1\nB: 2", bean.extraHeaders)
    }

    @Test
    fun shadowsocksPluginIsOneString() {
        val bean = ShadowsocksBean().applyDefaultValues().apply { plugin = "obfs-local;obfs=http" }
        val spec = ProfileSpecs.of(bean)
        val form = spec.load(bean)
        assertEquals("obfs-local", form.text("pluginName"))
        assertEquals("obfs=http", form.text("pluginConfig"))
        spec.save(form.withText("pluginName", ""), bean, form)
        assertEquals("", bean.plugin)
    }

    /** SSH keeps the secret of the other kind as it was: only the chosen kind's is written. */
    @Test
    fun sshWritesTheChosenKindsSecretOnly() {
        val bean = SSHBean().applyDefaultValues().apply { authType = SSHBean.AUTH_TYPE_PASSWORD; password = "old"; privateKey = "key" }
        val spec = ProfileSpecs.of(bean)
        val form = spec.load(bean)
        spec.save(form.withText("password", "new").withText("privateKey", "other"), bean, form)
        assertEquals("new", bean.password)
        assertEquals("key", bean.privateKey)
    }

    @Test
    fun aNumberNotTypedIsTheOldDefault() {
        val bean = HysteriaBean().applyDefaultValues()
        val spec = ProfileSpecs.of(bean)
        val form = spec.load(bean)
        spec.save(form.withText("hopInterval", "").withText("serverPort", "x").withText("protocolVersion", ""), bean, form)
        assertEquals(10, bean.hopInterval)
        assertEquals(2, bean.protocolVersion)
    }

    // the tweaks, as the old dialog («Настройки подключения») refused and stored them

    private fun tweaked(change: Tweaks.() -> Tweaks): Pair<AbstractBean, Tweaks> {
        val bean = SOCKSBean().applyDefaultValues().common()
        return bean to Tweaks.of(bean).change()
    }

    @Test
    fun theOldDialogsErrorsInItsOrder() {
        val (_, bad) = tweaked { copy(knockSequence = "70000", certPins = "nope", udpPreludeSni = "a b") }
        val knock = runCatching { parseKnockSequence("70000") }.exceptionOrNull()!!.message
        assertEquals(FieldError(KNOCK_SEQUENCE, R.string.port_knocking_invalid, knock), bad.check())
        val pins = runCatching { parseCertificatePins("nope") }.exceptionOrNull()!!.message
        assertEquals(FieldError(CERT_PINS, arg = pins), bad.copy(knockSequence = "").check())
        assertEquals(FieldError(PRELUDE_SNI, R.string.udp_prelude_sni_invalid), bad.copy(knockSequence = "", certPins = "").check())
        val tunnel = bad.copy(knockSequence = "", certPins = "", udpPreludeSni = "", dnsDomain = "t.example.com", dnsKey = "short")
        assertEquals(FieldError(DNS_DOMAIN, arg = "DNS tunnel: the public key must be 64 hex digits"), tunnel.check())
        assertNull(tweaked { this }.second.check())
        assertEquals(ProfilePage.TUNNEL, ProfilePage.of(DNS_DOMAIN))
        assertEquals(ProfilePage.TWEAKS, ProfilePage.of(KNOCK_SEQUENCE))
    }

    @Test
    fun changedTweaksAreTakenAsTheDialogsOk() {
        val (bean, changed) = tweaked {
            copy(knockInterval = "5", knockTtl = "", knockSilence = "99999", udpPrelude = "20", udpPreludeTtl = "300", dnsMethod = "9", knockSequence = " 7000 ", certPins = " $pin ")
        }
        changed.storedAgainst(Tweaks.of(bean)).applyTo(bean)
        assertEquals(10, bean.knockInterval)
        assertEquals(10, bean.knockTtl)
        assertEquals(3600, bean.knockSilence)
        assertEquals(8, bean.udpPrelude)
        assertEquals(255, bean.udpPreludeTtl)
        assertEquals(5, bean.dnsTunnelMethod)
        assertEquals("7000", bean.knockSequence)
        assertEquals(pin, bean.certPins)
        // the same tunnel: what the link signed for it stays
        assertEquals("v1;node;svc", bean.dnsTunnelAuth)
        assertEquals("seal", bean.dnsTunnelSeal)
    }

    @Test
    fun anotherTunnelDropsWhatTheLinkSignedForTheOld() {
        val (bean, changed) = tweaked { copy(dnsResolver = "udp://9.9.9.9") }
        changed.storedAgainst(Tweaks.of(bean)).applyTo(bean)
        assertEquals("", bean.dnsTunnelAuth)
        // a resolver is not the service: its seal and spare zones stay
        assertEquals("seal", bean.dnsTunnelSeal)
        val (other, off) = tweaked { copy(dnsDomain = "") }
        off.storedAgainst(Tweaks.of(other)).applyTo(other)
        assertEquals("", other.dnsTunnel)
        assertEquals("", other.dnsTunnelSeal)
        assertEquals("", other.dnsTunnelSpare)
    }

    @Test
    fun aSlipstreamTunnelIsKeptAsItIs() {
        val bean = SOCKSBean().applyDefaultValues().apply {
            dnsTunnel = "slipstream"; dnsTunnelDomain = "s.example.com"; dnsTunnelKey = "k"; dnsTunnelTls = "h3;sni.example"; dnsTunnelAuth = "v1;n;s"
        }
        val tweaks = Tweaks.of(bean)
        assertTrue(tweaks.slipstream)
        // its fields are not checked (it has no editor here) and stay, whatever else changes
        assertNull(tweaks.copy(knockInterval = "400").check())
        tweaks.copy(knockInterval = "400").storedAgainst(tweaks).applyTo(bean)
        assertEquals("slipstream", bean.dnsTunnel)
        assertEquals("v1;n;s", bean.dnsTunnelAuth)
        assertEquals(400, bean.knockInterval)
    }

    @Test
    fun linkKeysTellTheSameProfileTwiceApart() {
        assertEquals(listOf("1#1", "2#1", "1#2"), linkKeys(listOf(1L, 2L, 1L)))
    }
}

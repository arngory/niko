package io.niko.ui.profile

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.fmt.AbstractBean
import io.niko.fmt.http.HttpBean
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.mieru.MieruBean
import io.niko.fmt.ssh.SSHBean
import io.niko.fmt.trojan_go.TrojanGoBean
import io.niko.fmt.tuic.TuicBean
import io.niko.proxy.anytls.AnyTLSBean
import io.niko.proxy.config.ConfigBean
import io.niko.proxy.shadowtls.ShadowTLSBean
import io.niko.fmt.internal.ChainBean
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.shadowsocks.ShadowsocksBean
import io.niko.fmt.socks.SOCKSBean
import io.niko.fmt.trojan.TrojanBean
import io.niko.fmt.v2ray.VMessBean
import io.niko.fmt.wireguard.WireGuardBean
import io.niko.ktx.applyDefaultValues
import io.niko.ui.compose.NikoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The profile editor (P-Profile) as images, light and dark, in Russian: a VLESS with REALITY (the canvas's desktop
 * editor's data), Hysteria2, naive (the phone artboard's), Shadowsocks, WireGuard, SOCKS, a new profile untouched,
 * «Тонкая настройка» and its DNS tunnel, a refused save, a chain; a long form also scrolled to its middle and end.
 */
@RunWith(Parameterized::class)
class ProfileSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")
    }

    @get:Rule
    val paparazzi = Paparazzi(
        // the canvas's artboard: 412 by 915, in Russian
        deviceConfig = DeviceConfig.PIXEL_5.copy(
        screenWidth = 412 * 3,
        screenHeight = 915 * 3,
        xdpi = 480,
        ydpi = 480,
        density = Density.XXHIGH,
        locale = "ru",
        nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT,
        ),
    )

    private val none = object : ProfileActions {
        override fun back() {}
        override fun save() {}
        override fun delete() {}
        override fun move(groupId: Long) {}
        override fun shortcut() {}
        override fun editJson(target: JsonTarget) {}
        override fun pickLink(replace: Int) {}
    }

    /** [bean]'s editor on [page], scrolled down [screens] of the artboard's height (the end of a long form). */
    private fun shot(
        bean: AbstractBean,
        screens: Float = 0f,
        page: String = ProfilePage.MAIN,
        info: ProfileInfo = ProfileInfo(existing = true),
        name: String? = null,
        form: (ProfileForm) -> ProfileForm = { it },
    ) {
        val spec = ProfileSpecs.of(bean)
        val f = form(spec.load(bean))
        paparazzi.snapshot(name) {
            NikoTheme(dark = mode == "dark") { ProfileScreen(spec, f, info, page, {}, {}, none, (screens * 760 * 3).toInt()) }
        }
    }

    private fun vless() = VMessBean().apply { alterId = -1 }.applyDefaultValues().apply {
        name = "Нидерланды"
        serverAddress = "nl.provider.net"
        serverPort = 443
        uuid = "a3482e88-686a-4a58-8126-99c9df64b7bf"
        encryption = "xtls-rprx-vision"
        vlessEncryption = "none"
        security = "tls"
        sni = "www.example.nl"
        utlsFingerprint = "chrome"
        realityPubKey = "Z84J2IelR9ch3k8VtlVhhs5ycBUlXA7wHBWcBrjqnAw"
        realityShortId = "6ba85179"
    }

    @Test
    fun vlessReality() = shot(vless())

    @Test
    fun vlessRealityMiddle() = shot(vless(), 1f)

    @Test
    fun vlessRealityEnd() = shot(vless(), 3f)

    private fun hysteria() = HysteriaBean().applyDefaultValues().apply {
        name = "Хельсинки"
        serverAddress = "hel.provider.net"
        serverPorts = "443"
        authPayload = "secret"
        sni = "hel.provider.net"
        uploadMbps = 50
        downloadMbps = 200
    }

    @Test
    fun hysteria2() = shot(hysteria())

    @Test
    fun hysteria2End() = shot(hysteria(), 3f)

    @Test
    fun hysteria1() = shot(hysteria().apply { protocolVersion = 1; authPayloadType = HysteriaBean.TYPE_STRING }, 0.35f)

    private fun naiveBean() = NaiveBean().applyDefaultValues().apply {
        name = "Амстердам"
        serverAddress = "ams.example"
        serverPort = 443
        username = "maria"
        password = "secret1234"
    }

    @Test
    fun naive() = shot(naiveBean())

    @Test
    fun naiveEnd() = shot(naiveBean(), 3f)

    private fun ss() = ShadowsocksBean().applyDefaultValues().apply {
        name = "Франкфурт"
        serverAddress = "fra.provider.net"
        serverPort = 8388
        method = "2022-blake3-aes-256-gcm"
        password = "secret"
        plugin = "v2ray-plugin;mode=websocket;host=cdn.example"
    }

    @Test
    fun shadowsocks() = shot(ss())

    @Test
    fun shadowsocksEnd() = shot(ss(), 3f)

    private fun wg() = WireGuardBean().applyDefaultValues().apply {
        name = "Дом"
        serverAddress = "203.0.113.7"
        serverPort = 51820
        localAddress = "10.8.0.2/32"
        privateKey = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
        peerPublicKey = "xTIBA5rboUvnH4htodjb6e697QjLERt1NAB4mZqp8Dg="
        persistentKeepalive = 25
    }

    @Test
    fun wireGuard() = shot(wg())

    @Test
    fun wireGuardEnd() = shot(wg(), 3f)

    @Test
    fun socks() = shot(
        SOCKSBean().applyDefaultValues().apply {
            name = "Офис"
            serverAddress = "10.0.0.1"
            serverPort = 1080
            username = "user"
            password = "pass"
        },
    )

    @Test
    fun newProfile() = shot(TrojanBean().applyDefaultValues(), info = ProfileInfo(existing = false))

    @Test
    fun newProfileEnd() = shot(TrojanBean().applyDefaultValues(), 3f, info = ProfileInfo(existing = false))

    @Test
    fun tweaks() = shot(vless(), page = ProfilePage.TWEAKS) {
        it.copy(tweaks = it.tweaks.copy(tlsFragment = 1, knockSequence = "7000, 8000/udp", knockSilence = "0"))
    }

    @Test
    fun tweaksEnd() = shot(vless(), 3f, page = ProfilePage.TWEAKS)

    @Test
    fun tunnel() = shot(vless(), page = ProfilePage.TUNNEL) {
        it.copy(tweaks = it.tweaks.copy(dnsEngine = "masterdnsvpn", dnsResolver = "77.88.8.8\n77.88.8.1", dnsDomain = "t.example.com", dnsKey = "key"))
    }

    @Test
    fun quic() = shot(vless(), page = ProfilePage.QUIC)

    @Test
    fun refused() = shot(
        vless(), page = ProfilePage.TWEAKS,
        info = ProfileInfo(existing = true, error = FieldError(KNOCK_SEQUENCE, io.niko.R.string.port_knocking_invalid, "invalid port: 70000")),
    ) { it.copy(tweaks = it.tweaks.copy(knockSequence = "70000")) }

    @Test
    fun mux() = shot(vless(), page = "mux")

    @Test
    fun chain() = shot(
        ChainBean().applyDefaultValues().apply { name = "Через Германию"; proxies = mutableListOf(1L, 2L) },
        info = ProfileInfo(existing = true, links = mapOf(1L to ChainLink("Германия", "Trojan"), 2L to ChainLink("Нидерланды", "VLESS"))),
    )

    // the other types' editors, each at its top and its end

    @Test
    fun others() {
        for (bean in listOf(
            HttpBean(), VMessBean(), TrojanGoBean(), MieruBean(), SSHBean(), TuicBean(), ShadowTLSBean(), AnyTLSBean(), ConfigBean(),
        )) {
            bean.applyDefaultValues()
            bean.name = "Профиль"
            val title = ProfileSpecs.of(bean).title.raw ?: "config"
            shot(bean, name = title)
            shot(bean, 3f, name = "$title end")
        }
    }
}

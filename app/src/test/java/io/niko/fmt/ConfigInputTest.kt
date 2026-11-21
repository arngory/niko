package io.niko.fmt

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.Key
import io.niko.database.ProxyEntity
import io.niko.database.ProxyGroup
import io.niko.database.RuleEntity
import io.niko.fmt.shadowsocks.ShadowsocksBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The build on a [ConfigInput] alone, in the JVM: no database, no settings store, no raycontrol, no phone. What the
 * golden configs of desktop-stage3.md (section 5) take as their input.
 */
class ConfigInputTest {

    /** ports from 20000 on, the clock stopped, the warnings kept */
    private class Device : ConfigDevice {
        private var next = 20000
        val warnings = mutableListOf<ConfigWarning>()
        override fun port() = next++
        override fun uid(packageName: String): Int? = null
        override fun strictPrivateDnsHost(): String? = null
        override fun pluginBundled(id: String) = false
        override fun pluginExternal(id: String) = false
        override fun protectPathReady() = false
        override fun yggdrasilKey() = "a one-time key"
        override fun yggKeyRunning(seed: String) = false
        override fun now() = 1_800_000_000L
        override fun warn(warning: ConfigWarning) {
            warnings += warning
        }
    }

    private fun ss(id: Long, name: String, group: Long = 1, order: Long = id) =
        ProxyEntity(id = id, groupId = group, userOrder = order).putBean(ShadowsocksBean().apply {
            serverAddress = "198.51.100.$id"
            serverPort = 8388
            method = "2022-blake3-aes-128-gcm"
            password = "fake-pass"
            this.name = name
            initializeDefaultValues()
        })

    private fun build(
        proxy: ProxyEntity,
        settings: ConfigSettings = ConfigSettings(),
        profiles: List<ProxyEntity> = listOf(proxy),
        groups: List<ProxyGroup> = listOf(ProxyGroup(id = 1)),
        rules: List<RuleEntity> = emptyList(),
        device: Device = Device(),
    ): JsonObject {
        val input = ConfigInput(proxy, settings, ConfigStore.Of(profiles, groups, rules), ConfigRay.None, device)
        return JsonParser.parseString(buildConfig(input, notes = false).config).asJsonObject
    }

    private fun JsonObject.tagged(list: String, tag: String) =
        getAsJsonArray(list).map { it.asJsonObject }.firstOrNull { it.get("tag")?.asString == tag }

    @Test
    fun profileAlone() {
        val config = build(ss(1, "a"))
        val proxy = config.tagged("outbounds", TAG_PROXY)!!
        assertEquals("shadowsocks", proxy.get("type").asString)
        assertEquals("198.51.100.1", proxy.get("server").asString)
        assertEquals(TAG_PROXY, config.getAsJsonObject("route").get("final").asString)
        // the VPN mode of the defaults: a TUN, no local proxy without a login set
        val tun = config.tagged("inbounds", "tun-in")!!
        assertEquals(9000, tun.get("mtu").asInt)
        assertNull(config.tagged("inbounds", TAG_MIXED))
        assertNull(config.get("experimental")?.asJsonObject?.get("clash_api"))
    }

    @Test
    fun sameInputSameConfig() {
        val proxy = ss(1, "a")
        assertEquals(build(proxy), build(proxy))
    }

    @Test
    fun settingsAreTheInput() {
        val settings = ConfigSettings(
            serviceMode = Key.MODE_PROXY,
            mixedPort = 3080,
            mixedUsername = "me",
            remoteDns = "tls://1.1.1.1",
            secrets = ConfigSecrets(localProxyPassword = "app-pass", mixedPassword = "my-pass"),
        )
        val config = build(ss(1, "a"), settings)
        assertNull(config.tagged("inbounds", "tun-in"))
        val mixed = config.tagged("inbounds", TAG_MIXED)!!
        assertEquals(3080, mixed.get("listen_port").asInt)
        val users = mixed.getAsJsonArray("users").map { it.asJsonObject.get("username").asString }
        assertEquals(listOf(LOCAL_PROXY_USER, "me"), users)
        val remote = config.getAsJsonObject("dns").tagged("servers", "dns-remote")!!
        assertEquals("tls", remote.get("type").asString)
        assertEquals("1.1.1.1", remote.get("server").asString)
    }

    @Test
    fun groupFromTheStore() {
        val a = ss(1, "a")
        val b = ss(2, "b")
        val config = build(a, profiles = listOf(b, a), groups = listOf(ProxyGroup(id = 1, isSelector = true)))
        val selector = config.tagged("outbounds", TAG_PROXY)!!
        assertEquals("selector", selector.get("type").asString)
        assertEquals(listOf("a", "b"), selector.getAsJsonArray("outbounds").map { it.asString })
        assertEquals("a", selector.get("default").asString)
    }

    @Test
    fun rulesFromTheStoreWarn() {
        val device = Device()
        val rules = listOf(
            RuleEntity(id = 1, name = "apps", enabled = true, packages = setOf("org.example"), outbound = -1L),
            RuleEntity(id = 2, name = "gone", enabled = true, domains = "example.com", outbound = 99L, userOrder = 1),
            RuleEntity(id = 3, name = "off", enabled = false, domains = "example.org", outbound = -1L),
        )
        val config = build(ss(1, "a"), ConfigSettings(serviceMode = Key.MODE_PROXY), rules = rules, device = device)
        assertEquals(
            listOf(ConfigWarning.RuleNeedsVpn("apps"), ConfigWarning.NoSuchOutbound("gone")),
            device.warnings,
        )
        val routed = config.getAsJsonObject("route").getAsJsonArray("rules").map { it.toString() }
        assertFalse(routed.any { "example.org" in it })
    }

    @Test
    fun secretsStayOutOfLogs() {
        val settings = ConfigSettings(secrets = ConfigSecrets(clashApi = "s1", localProxyPassword = "s2", yggdrasilKey = "s3"))
        assertTrue(listOf("s1", "s2", "s3").none { it in settings.toString() })
    }
}

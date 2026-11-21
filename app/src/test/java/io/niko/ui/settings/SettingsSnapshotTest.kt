package io.niko.ui.settings

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import io.niko.Key
import io.niko.ui.compose.NikoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Настройки (SettingsScreen): the list as P-Settings has it and the pages of its rows, light and dark, Russian. The
 * values are the canvas's: dark theme, VPN, FakeIP, gVisor and 9000, the inbound on 127.0.0.1:2080, Yggdrasil off.
 * Goldens as NikoSnapshotTest's.
 */
@RunWith(Parameterized::class)
class SettingsSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            locale = "ru",
            nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT,
        ),
    )

    private val values = MemorySettingValues(
        flags = mapOf(
            Key.ENABLE_FAKEDNS to true, Key.ENABLE_DNS_ROUTING to true, Key.RAYCONTROL_AUTO_SWITCH to true,
            Key.NETWORK_CHANGE_RESET_CONNECTIONS to true, Key.PROFILE_TRAFFIC_STATISTICS to true, Key.SHOW_DIRECT_SPEED to true,
            Key.YGGDRASIL_TLS_CHROME to true,
        ),
        texts = mapOf(
            Key.NIGHT_THEME to "1", Key.SERVICE_MODE to "vpn", Key.TUN_IMPLEMENTATION to "0", Key.MTU to "9000",
            Key.MIXED_PORT to "2080", Key.REMOTE_DNS to "https://dns.google/dns-query", Key.DIRECT_DNS to "local",
            SettingKeys.STRATEGY_REMOTE to "auto", SettingKeys.STRATEGY_DIRECT to "auto", SettingKeys.STRATEGY_SERVER to "auto",
            Key.RULE_SET_UPDATE_HOURS to "24", Key.RULES_PROVIDER to "0", Key.IPV6_MODE to "0", Key.ROOT_STORE to "chrome",
            Key.CONNECTION_TEST_URL to "http://cp.cloudflare.com/", SettingKeys.CONNECTION_TEST_CONCURRENT to "5",
            Key.APP_TLS_VERSION to "1.2", Key.TLS_FRAGMENT_BYPASS to "0", Key.TRAFFIC_SNIFFING to "1", Key.LOG_LEVEL to "0",
            Key.SPEED_INTERVAL to "1000", Key.LOG_BUF_SIZE to "0", Key.RAYCONTROL_DNSTT_RESOLVER to "auto",
            Key.RAYCONTROL_MASTERDNS_RESOLVERS to "auto",
        ),
    )

    private val actions = object : SettingsActions {
        override fun back() {}
        override fun open(page: SettingsPage) {}
        override fun choose(choice: Choice) {}
        override fun newPassword() {}
        override fun customConfig() {}
        override fun backup() {}
        override fun about() {}
        override fun copy(text: String) {}
    }

    private fun shoot(page: SettingsPage, sheet: Choice? = null) = paparazzi.snapshot {
        NikoTheme(mode == "dark") { SettingsScreen(page, values, SettingsInfo("1.66.0", 0, null), actions, sheet) }
    }

    @Test
    fun root() = shoot(SettingsPage.ROOT)

    @Test
    fun dns() = shoot(SettingsPage.DNS)

    @Test
    fun check() = shoot(SettingsPage.CHECK)

    @Test
    fun experiments() = shoot(SettingsPage.EXPERIMENTS)

    @Test
    fun theme() = shoot(
        SettingsPage.ROOT,
        Choice("Тема", Key.NIGHT_THEME, listOf("0" to "Как в системе", "2" to "Светлая", "1" to "Тёмная", "3" to "По экономии заряда")),
    )
}

package io.niko.ui.home

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import io.niko.bg.BaseService
import io.niko.ui.compose.NikoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The main screen (ui/home) as images, light and dark, against the canvas's P-Simple, P-Advanced and P-ModeSheet: a
 * fixed clock and no ticking, so that the time online stays 00:42:17. Goldens as NikoSnapshotTest's (CONTRIBUTING).
 */
@RunWith(Parameterized::class)
class HomeSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")

        private const val NOW = 1_800_000_000_000L
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT),
    )

    private val none = object : HomeActions {
        override fun toggle() {}
        override fun view(view: HomeView) {}
        override fun mode(vpn: Boolean) {}
        override fun profiles() {}
        override fun configs(open: Boolean) {}
        override fun subscriptions() {}
        override fun route() {}
        override fun apps() {}
        override fun settings() {}
        override fun nodes() {}
        override fun log() {}
        override fun help() {}
    }

    private val connected = HomeState(
        service = BaseService.State.Connected,
        connectedSince = NOW - (42 * 60 + 17) * 1000L,
        upRate = 312 * 1024L,
        downRate = 4_400_000L,
        profile = HomeProfile(
            name = "Hysteria2", type = "Hysteria2", group = "arngory", usage = "until 20 Dec · 38 / 100 GB",
            pingMs = 38, unreachable = false,
        ),
        route = HomeRoute.Russia,
        apps = HomeApps(perApp = true, bypass = true, count = 3),
        profiles = 6,
        subscriptions = 1,
    )

    private val noConfigs = object : ConfigsActions {
        override fun select(id: Long) {}
        override fun test(groupId: Long) {}
        override fun stopTest() {}
        override fun edit(id: Long) {}
        override fun add() {}
    }

    private val noSubs = object : SubsActions {
        override fun open(row: SubRow) {}
        override fun update(row: SubRow) {}
        override fun updateAll() {}
        override fun add() {}
        override fun action(row: SubRow, action: SubAction) {}
    }

    private fun utc(pattern: String) = java.text.SimpleDateFormat(pattern, java.util.Locale.ENGLISH).apply {
        timeZone = java.util.TimeZone.getTimeZone("UTC")
    }

    private val format = io.niko.ui.node.NodeFormat(
        day = { utc("d MMM").format(java.util.Date(it * 1000)) },
        time = { utc("HH:mm").format(java.util.Date(it * 1000)) },
        bytes = { "%.0f GB".format(java.util.Locale.ENGLISH, it / 1e9) },
    )

    // P-Subs: a node with its limit, a provider's subscription fetched, one that has ended, the user's own group
    private val day = 86_400L
    private val subs = listOf(
        SubRow(
            1, "arngory", SubKind.NODE, profiles = 3, expire = NOW / 1000 + 20 * day, updatedAt = NOW / 1000 - 3600,
            used = 38_000_000_000, total = 100_000_000_000, reset = NOW / 1000 + 12 * day, canUpdate = true, nodeId = "n1",
        ),
        SubRow(
            2, "provider.net", SubKind.SUBSCRIPTION, profiles = 2, expire = NOW / 1000 + 31 * day, updatedAt = NOW / 1000 - 600,
            used = 12_000_000_000, total = 50_000_000_000, canUpdate = true,
        ),
        SubRow(3, "old.example", SubKind.SUBSCRIPTION, profiles = 5, expire = NOW / 1000 - 13 * day, updatedAt = NOW / 1000 - 15 * day, canUpdate = true),
        SubRow(4, "Свои", SubKind.OWN, profiles = 4),
    )

    // P-Configs: a node's group and a subscription's, one profile chosen, one tested and failed, one not tested
    private val groups = listOf(
        ConfigGroup(
            1, "arngory", subscription = true,
            listOf(
                ConfigRow(11, "naive", listOf("HTTPS"), 38, unreachable = false, selected = false),
                ConfigRow(12, "Hysteria2", listOf("QUIC"), 41, unreachable = false, selected = true),
                ConfigRow(13, "dnstt", listOf("VLESS", "DNS"), null, unreachable = false, selected = false),
            ),
        ),
        ConfigGroup(
            2, "provider.net", subscription = true,
            listOf(
                ConfigRow(21, "Netherlands · Amsterdam", listOf("VLESS", "REALITY"), 63, unreachable = false, selected = false),
                ConfigRow(22, "Finland · Helsinki", listOf("Shadowsocks"), null, unreachable = true, selected = false),
            ),
        ),
    )

    private fun shot(state: HomeState, view: HomeView, sheet: Boolean = false, configs: Boolean = false, onSubs: Boolean = false) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") {
            HomeScreen(
                state, view, none, clock = { NOW }, ticking = false, sheet = sheet,
                configs = groups, testing = setOf(2L), configsActions = noConfigs, configsOpen = configs,
                subs = subs, subsActions = noSubs, format = format, subsOpen = onSubs,
                // the test's results over the rows read: Helsinki answered meanwhile
                results = mapOf(22L to ConfigsTest.Result(pingMs = 88, unreachable = false)),
            )
        }
    }

    @Test
    fun simpleConnected() = shot(connected, HomeView.Simple)

    @Test
    fun advancedConnected() = shot(connected, HomeView.Advanced)

    @Test
    fun modeSheet() = shot(connected, HomeView.Simple, sheet = true)

    /** P-Configs over the main screen: the second group's test running, its stop in place of the test button. */
    @Test
    fun configsSheet() = shot(connected, HomeView.Advanced, configs = true)

    /** P-Subs: the sheet on its Subscriptions tab, opened from the Advanced view's tab. */
    @Test
    fun subsSheet() = shot(connected.copy(subscriptions = 4), HomeView.Advanced, onSubs = true)

    @Test
    fun simpleFailed() = shot(
        connected.copy(service = BaseService.State.Stopped, connectedSince = 0, upRate = 0, downRate = 0, error = "dial tcp 192.0.2.10:443: i/o timeout"),
        HomeView.Simple,
    )

    @Test
    fun simpleEmpty() = shot(HomeState(), HomeView.Simple)

    /** Configs there, none chosen: the sheet asks to choose one, not to add the first. */
    @Test
    fun simpleNoneChosen() = shot(HomeState(profiles = 20, subscriptions = 1), HomeView.Simple)
}

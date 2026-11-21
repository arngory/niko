package io.niko.ui.node

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import io.niko.raycontrol.RayTraffic
import io.niko.ui.compose.NikoTheme
import io.niko.ui.home.ConfigRow
import io.niko.ui.home.ConfigsTest
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** The node's card (P-Node) as images, light and dark: a fixed clock, day and size formats. Goldens as NikoSnapshotTest's. */
@RunWith(Parameterized::class)
class NodeSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")

        private const val NOW = 1_800_000_000L // unix s
        private const val DAY = 86_400L
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT),
    )

    private val none = object : NodeActions {
        override fun back() {}
        override fun update() {}
        override fun more() {}
        override fun test(groupId: Long) {}
        override fun stopTest() {}
        override fun select(id: Long) {}
        override fun channel() {}
        override fun lan() {}
        override fun why() {}
        override fun admin() {}
        override fun appUpdate() {}
    }

    private fun utc(pattern: String) = SimpleDateFormat(pattern, Locale.ENGLISH).apply { timeZone = TimeZone.getTimeZone("UTC") }

    private val format = NodeFormat(
        day = { utc("d MMM yyyy").format(Date(it * 1000)) },
        time = { utc("d MMM, HH:mm").format(Date(it * 1000)) },
        bytes = { "%.1f GB".format(Locale.ENGLISH, it / 1e9) },
    )

    private val ways = listOf(
        ConfigRow(11, "Helsinki · REALITY", listOf("VLESS", "REALITY", "TCP"), pingMs = 42, unreachable = false, selected = true),
        ConfigRow(12, "Helsinki · Hysteria2", listOf("Hysteria2"), pingMs = 37, unreachable = false, selected = false),
        ConfigRow(13, "Helsinki · naive", listOf("Naive", "HTTPS"), pingMs = null, unreachable = true, selected = false),
        ConfigRow(14, "Helsinki · dnstt", listOf("DNS"), pingMs = null, unreachable = false, selected = false),
    )

    private val card = NodeCardState(
        nodeId = "n1",
        name = "Helsinki",
        note = "⁨Family node, ask before sharing⁩",
        warning = null,
        traffic = RayTraffic(used = 38_000_000_000, limit = 100_000_000_000, since = NOW - 10 * DAY, reset = NOW + 20 * DAY, exhausted = false),
        expires = NOW + 29 * DAY,
        updated = NOW - 3_600,
        relayPath = true,
        clockSkew = null,
        dnstt = "net",
        groupId = 2,
        profiles = ways,
        admin = true,
        lan = true,
    )

    private fun shot(state: NodeCardState?, testing: Set<Long> = emptySet(), results: Map<Long, ConfigsTest.Result> = emptyMap()) =
        paparazzi.snapshot {
            NikoTheme(dark = mode == "dark") {
                NodeScreen(state, testing, results, format, NOW, none)
            }
        }

    @Test
    fun node() = shot(card)

    /** What keeps it from working above all; its ways under test, the stop in place of the test, a result come meanwhile. */
    @Test
    fun nodeWarned() = shot(
        card.copy(
            warning = NodeWarning.ClientMin("1.70.0", NOW + 5 * DAY),
            traffic = card.traffic!!.copy(used = 100_000_000_000, exhausted = true),
            expires = NOW - DAY,
            relayPath = false,
            clockSkew = -95,
            dnstt = "none",
            admin = false,
            lan = false,
        ),
        testing = setOf(2L),
        results = mapOf(14L to ConfigsTest.Result(pingMs = 310, unreachable = false)),
    )

    @Test
    fun nodeEmpty() = shot(card.copy(note = null, traffic = null, expires = 0, updated = 0, relayPath = null, dnstt = null, profiles = emptyList(), admin = false, lan = false))
}

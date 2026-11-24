package io.niko.ui.pick

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.ui.compose.NikoTheme
import io.niko.ui.home.ConfigGroup
import io.niko.ui.home.ConfigRow
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The profile picker as images, light and dark, on the canvas's data (P-Pick), and where its list opens. */
@RunWith(Parameterized::class)
class PickSnapshotTest(private val mode: String) {

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

    private fun row(id: Long, name: String, vararg tags: String, ping: Int? = null, dead: Boolean = false) =
        ConfigRow(id, name, tags.toList(), ping, dead, selected = false)

    private val groups = listOf(
        ConfigGroup(1, "arngory", subscription = false, listOf(
            row(11, "naive", "HTTPS", "UDP", ping = 38), row(12, "Hysteria2", "QUIC", ping = 41), row(13, "dnstt", "DNS"),
        )),
        ConfigGroup(2, "provider.net", subscription = true, listOf(
            row(21, "Нидерланды · Амстердам", "VLESS", "REALITY", ping = 63),
            row(22, "Германия · Франкфурт", "Trojan", "WS", ping = 70),
            row(23, "Финляндия · Хельсинки", "SS", dead = true),
        )),
        ConfigGroup(3, null, subscription = false, listOf(row(31, "Свой сервер", "WireGuard", ping = 55))),
    )

    private fun shot(state: PickState) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") { PickScreen(state, {}, {}, {}) {} }
    }

    @Test
    fun pick() = shot(PickState(groups, purpose = "Выход правила"))

    /** A chain's link replaced: the list knows which one. */
    @Test
    fun current() = shot(PickState(groups, current = 12, purpose = "Звено цепочки"))

    @Test
    fun search() = shot(PickState(groups, purpose = "Выход правила", searching = true, query = "фран"))

    @Test
    fun searchNone() = shot(PickState(groups, searching = true, query = "zzz"))

    @Test
    fun none() = shot(PickState(emptyList(), purpose = "Front proxy группы"))

    @Test
    fun loading() = shot(PickState())

    @Test
    fun opensOnTheCurrentProfile() {
        // a head, the rows and a gap for each group
        assertEquals(0, PickState(groups).startIndex)
        assertEquals(1, PickState(groups, current = 11).startIndex)
        assertEquals(3, PickState(groups, current = 13).startIndex)
        assertEquals(5 + 1 + 2, PickState(groups, current = 23).startIndex)
        assertEquals(10 + 1, PickState(groups, current = 31).startIndex)
        assertEquals(0, PickState(groups, current = 99).startIndex)
        assertEquals(listOf(22L), PickState(groups, query = "фран").shown!!.flatMap { g -> g.profiles.map { it.id } })
    }
}

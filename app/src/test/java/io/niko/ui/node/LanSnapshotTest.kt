package io.niko.ui.node

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.ui.compose.NikoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * A node's local network as images, light and dark, on the canvas's data (P-Lan): the devices and the trusted Wi-Fi,
 * the name hidden without the location, the network not offered, the ports' sheet with an error, the ways to know a
 * Wi-Fi; and what the trusted section says in each case.
 */
@RunWith(Parameterized::class)
class LanSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")
    }

    @get:Rule
    val paparazzi = Paparazzi(
        // the canvas's artboard, as tall as its P-Lan: 412 by 1000, in Russian
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 412 * 3,
            screenHeight = 1000 * 3,
            xdpi = 480,
            ydpi = 480,
            density = Density.XXHIGH,
            locale = "ru",
            nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT,
        ),
    )

    private val none = object : LanActions {
        override fun back() {}
        override fun show(on: Boolean) {}
        override fun portsEdit(text: String?) {}
        override fun portsSave() {}
        override fun direct(on: Boolean) {}
        override fun byOpen(open: Boolean) {}
        override fun by(ssid: Boolean) {}
        override fun forget(key: String) {}
        override fun allow() {}
        override fun trust() {}
    }

    private val shown = LanState(
        node = "arngory",
        show = true,
        ports = "22, 8080",
        notes = listOf("Через ноду: до 4 Мбит/с на все ваши устройства вместе."),
        devices = listOf(LanDevice("Ноутбук", "laptop.arngory.niko"), LanDevice("Планшет", "200:b07e:1c2d:3e4f:5a6b:7c8d:9e0f:0c3d")),
        direct = true,
        trusted = listOf(LanWifi("Home-5G", "k1", current = true), LanWifi("Дача", "k2")),
        wifi = LanWifiState(LanWifiNote.THIS_TRUSTED),
    )

    private fun shot(state: LanState) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") { LanScreen(state, none) }
    }

    @Test
    fun lan() = shot(shown)

    /** By name, without the location: the name is not seen, «Разрешить» asks. */
    @Test
    fun location() = shot(
        shown.copy(bySsid = true, trusted = listOf(LanWifi("Дача", "k2")), wifi = LanWifiState(LanWifiNote.LOCATION, canAllow = true)),
    )

    @Test
    fun trustThis() = shot(shown.copy(trusted = emptyList(), wifi = LanWifiState(canTrust = true), wifiName = "Office-Guest"))

    @Test
    fun notOffered() = shot(
        LanState(node = "arngory", notes = listOf("Нода не даёт вам локальной сети: её включает администратор для вашего пользователя.")),
    )

    @Test
    fun ports() = shot(shown.copy(portsEdit = "22, http", portsError = "Это не список портов: 22, http"))

    @Test
    fun by() = shot(shown.copy(byOpen = true))

    @Test
    fun wifiCases() {
        fun of(bySsid: Boolean, inWifi: Boolean = true, hasSsid: Boolean = true, trusted: Boolean = false, location: Boolean = true, background: Boolean = true) =
            LanWifiState.of(bySsid, inWifi, hasSsid, trusted, location, background)
        // by the router and DNS the location is never asked
        assertEquals(LanWifiState(canTrust = true), of(false, hasSsid = false, location = false))
        assertEquals(LanWifiState(LanWifiNote.THIS_TRUSTED), of(false, trusted = true))
        assertEquals(LanWifiState(LanWifiNote.NOT_WIFI), of(false, inWifi = false))
        // by name: the permission, then «all the time», then location itself
        assertEquals(LanWifiState(LanWifiNote.LOCATION, canAllow = true), of(true, hasSsid = false, location = false))
        assertEquals(LanWifiState(LanWifiNote.LOCATION_BACKGROUND, canAllow = true, canTrust = true), of(true, background = false))
        assertEquals(LanWifiState(LanWifiNote.LOCATION_OFF), of(true, hasSsid = false))
        assertEquals(LanWifiState(canTrust = true), of(true))
        assertEquals(LanWifiState(LanWifiNote.THIS_TRUSTED), of(true, trusted = true))
        assertEquals(LanWifiState(LanWifiNote.NOT_WIFI), of(true, inWifi = false))
    }
}

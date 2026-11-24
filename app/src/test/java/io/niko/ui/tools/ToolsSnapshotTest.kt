package io.niko.ui.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoTheme
import io.niko.ui.compose.NikoType
import io.niko.ui.home.Text
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The log's tools as images, light and dark, on the canvas's data: Тип NAT (P-NAT) before, during and after a check
 * and with its error; the sing-box panel (P-Panel) with its ⋮ and its address sheet, the web panel a stand-in.
 */
@RunWith(Parameterized::class)
class ToolsSnapshotTest(private val mode: String) {

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

    private fun nat(state: NatState) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") { NatScreen(state, {}, {}, {}) }
    }

    private val found = NatResult(
        type = "Port Restricted cone NAT",
        mapping = NatBehavior.ENDPOINT,
        filtering = NatBehavior.ADDRESS_AND_PORT,
        address = "203.0.113.7:40112",
    )

    @Test
    fun nat() = nat(NatState())

    @Test
    fun natRunning() = nat(NatState(running = true))

    @Test
    fun natResult() = nat(NatState(result = found))

    @Test
    fun natNotes() = nat(
        NatState(result = found.copy(type = "Symmetric NAT", notes = listOf("Fake fullcone (no endpoint IP change) detected!!"))),
    )

    @Test
    fun natError() = nat(NatState(error = "lookup stun.example.org: no such host"))

    private val none = object : PanelActions {
        override fun back() {}
        override fun menu(open: Boolean) {}
        override fun edit(text: String?) {}
        override fun save() {}
        override fun copySecret() {}
    }

    private fun panel(state: PanelState) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") {
            PanelScreen(state, none) {
                Box(Modifier.fillMaxSize().padding(horizontal = NikoSpace.s5).background(Niko.colors.surface1).padding(NikoSpace.s4)) {
                    Text("yacd", NikoType.label, Niko.colors.ink2)
                }
            }
        }
    }

    private val url = "http://127.0.0.1:9090/ui"

    @Test
    fun panel() = panel(PanelState(url))

    @Test
    fun panelMenu() = panel(PanelState(url, menu = true))

    @Test
    fun panelAddress() = panel(PanelState(url, editing = url))
}

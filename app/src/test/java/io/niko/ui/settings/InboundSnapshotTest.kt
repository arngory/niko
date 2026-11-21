package io.niko.ui.settings

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.ui.compose.NikoTheme
import io.niko.ui.probe.ListProbeActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Настройки › Вход both ways, light and dark (docs/research/list-screens-probe.md): by hand (variant А) and from
 * shared/screens/set_inbound.json (variant В). The canvas's artboard: 412 × 915, Russian; at 3x, as the canvas is
 * exported for comparison. InboundVariantsTest holds the two pictures equal. Goldens as NikoSnapshotTest's.
 */
@RunWith(Parameterized::class)
class InboundSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")
    }

    @get:Rule
    val paparazzi = Paparazzi(
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

    @Test
    fun hand() = paparazzi.snapshot {
        NikoTheme(mode == "dark") { InboundScreen(ListProbeActivity.probeValues(), onBack = {}, onNewPassword = {}) }
    }

    @Test
    fun spec() = paparazzi.snapshot {
        NikoTheme(mode == "dark") { ListScreen(ListScreens.setInbound, ListProbeActivity.probeValues(), onBack = {}, onAction = {}) }
    }
}

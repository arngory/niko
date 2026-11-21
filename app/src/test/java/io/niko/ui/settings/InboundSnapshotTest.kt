package io.niko.ui.settings

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.Key
import io.niko.ui.compose.NikoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * Настройки › Вход (InboundScreen), light and dark. The canvas's artboard: 412 × 915, Russian; at 3x, as the canvas
 * is exported for comparison (docs/research/list-screens-probe.md). Goldens as NikoSnapshotTest's.
 */
@RunWith(Parameterized::class)
class InboundSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")

        /** The canvas's values: user niko, port 2080, a password of twelve, every switch off. */
        fun canvasValues() = MemorySettingValues(
            texts = mapOf(Key.MIXED_USERNAME to "niko", Key.MIXED_PORT to "2080", Key.MIXED_PASSWORD to "000000000000"),
        )
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
        NikoTheme(mode == "dark") { InboundScreen(canvasValues(), onBack = {}, onNewPassword = {}) }
    }
}

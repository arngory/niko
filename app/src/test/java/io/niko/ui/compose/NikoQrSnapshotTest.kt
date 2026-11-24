package io.niko.ui.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The QR dialog's card as images, light and dark, over the scrim: a profile's link and its name, a name too long for
 * its two lines. The code is dark on white in both themes.
 */
@RunWith(Parameterized::class)
class NikoQrSnapshotTest(private val mode: String) {

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

    private fun shot(text: String, caption: String) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") {
            Box(Modifier.fillMaxSize().background(Niko.colors.surface0)) {
                Box(Modifier.fillMaxSize().background(Niko.colors.scrim).padding(NikoSpace.s6), contentAlignment = Alignment.Center) {
                    NikoQrCard(qrMatrix(text), caption)
                }
            }
        }
    }

    @Test
    fun code() = shot("socks://node.example.org:1080#%D0%A4%D1%80%D0%B0%D0%BD%D0%BA%D1%84%D1%83%D1%80%D1%82", "Франкфурт")

    @Test
    fun longName() = shot(
        "sn://subscription?url=https%3A%2F%2Fnode.example.org%2Fsub&name=test",
        "Подписка с очень длинным названием, которое не помещается и в две строки под кодом, сколько ни старайся",
    )
}

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
 * The Niko snackbar's bar as images, light and dark, at a screen's bottom as its host lays it: a message alone, one
 * with its action (a removal's undo), a message of two lines.
 */
@RunWith(Parameterized::class)
class NikoSnackbarSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 412 * 3,
            screenHeight = 480 * 3,
            xdpi = 480,
            ydpi = 480,
            density = Density.XXHIGH,
            locale = "ru",
            nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT,
        ),
    )

    private fun shot(text: String, action: String? = null) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") {
            Box(
                Modifier.fillMaxSize().background(Niko.colors.surface0).padding(horizontal = NikoSpace.s4, vertical = NikoSpace.s3),
                contentAlignment = Alignment.BottomCenter,
            ) {
                NikoSnackbarBar(text, action) {}
            }
        }
    }

    @Test
    fun message() = shot("Скопировано в буфер обмена")

    @Test
    fun action() = shot("Удалено: 2", "Отменить")

    @Test
    fun twoLines() = shot("Строгий Частный DNS (dns.example.org) отправляет запросы имён мимо VPN")
}

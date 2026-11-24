package io.niko.ui.profile

import androidx.compose.ui.text.input.TextFieldValue
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.ui.compose.NikoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The JSON editor as images, light and dark, on the canvas's data (P-Json): a text, its error in place, the question on back. */
@RunWith(Parameterized::class)
class JsonSnapshotTest(private val mode: String) {

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

    private val none = object : JsonActions {
        override fun back() {}
        override fun value(value: TextFieldValue) {}
        override fun undo() {}
        override fun redo() {}
        override fun format() {}
        override fun key(text: String) {}
        override fun save() {}
        override fun leave(save: Boolean?) {}
    }

    private val text = """
        {
          "type": "vless",
          "tag": "proxy",
          "server": "203.0.113.7"
          "server_port": 443,
          "tls": {
            "enabled": true,
            "utls": { "enabled": true }
          }
        }
    """.trimIndent()

    private fun shot(state: JsonState) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") { JsonScreen("Настройки конфигурации", state, none) }
    }

    @Test
    fun json() = shot(JsonState(TextFieldValue(text), canUndo = true))

    @Test
    fun error() = shot(JsonState(TextFieldValue(text), canUndo = true, error = JsonError(5, "ожидалась запятая")))

    @Test
    fun empty() = shot(JsonState())

    @Test
    fun leaving() = shot(JsonState(TextFieldValue(text), canUndo = true, canRedo = true, leaving = true))
}

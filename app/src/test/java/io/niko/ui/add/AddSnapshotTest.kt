package io.niko.ui.add

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.database.ProxyEntity
import io.niko.ui.compose.NikoTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The add screen as images, light and dark (P-Scan, P-ScanDenied): the viewfinder with the torch off and on, the
 * camera refused, an import running, the sheet of profiles made by hand. The camera's picture is left empty.
 */
@RunWith(Parameterized::class)
class AddSnapshotTest(private val mode: String) {

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

    private val none = object : AddActions {
        override fun back() {}
        override fun torch() {}
        override fun allow() {}
        override fun clipboard() {}
        override fun qrFile() {}
        override fun file() {}
        override fun manual(open: Boolean) {}
        override fun create(kind: AddManual) {}
    }

    private fun shot(state: AddState) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") { AddScreen(state, none) {} }
    }

    @Test
    fun scan() = shot(AddState(camera = AddCamera.ON))

    @Test
    fun torch() = shot(AddState(camera = AddCamera.ON, torch = true))

    @Test
    fun waiting() = shot(AddState())

    @Test
    fun denied() = shot(AddState(camera = AddCamera.DENIED))

    @Test
    fun manual() = shot(AddState(camera = AddCamera.ON, manual = true))

    /** The old «+ › вручную» had these seventeen, in this order; VLESS is VMess's editor told apart. */
    @Test
    fun manualKinds() {
        assertEquals(17, AddManual.entries.size)
        assertEquals(AddManual.SOCKS, AddManual.entries.first())
        assertEquals(AddManual.CHAIN, AddManual.entries.last())
        assertEquals(ProxyEntity.TYPE_VMESS, AddManual.VLESS.type)
        assertEquals(listOf(AddManual.VLESS), AddManual.entries.filter { it.vless })
        assertEquals(AddManual.entries.size - 1, AddManual.entries.map { it.type }.distinct().size)
    }
}

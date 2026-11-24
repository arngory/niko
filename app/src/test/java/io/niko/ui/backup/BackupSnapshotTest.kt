package io.niko.ui.backup

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.ui.compose.NikoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Резервная копия as images, light and dark: the page with a part unticked, a file's restore sheet, the restore running. */
@RunWith(Parameterized::class)
class BackupSnapshotTest(private val mode: String) {

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

    private val none = object : BackupActions {
        override fun back() {}
        override fun part(part: BackupPart, on: Boolean) {}
        override fun save() {}
        override fun share() {}
        override fun pick() {}
        override fun restorePart(part: BackupPart, on: Boolean) {}
        override fun restore() {}
        override fun cancelRestore() {}
        override fun reset() {}
    }

    private fun shot(state: BackupState) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") { BackupScreen(state, none) }
    }

    @Test
    fun backup() = shot(BackupState(parts = setOf(BackupPart.PROFILES, BackupPart.RULES)))

    /** A file with profiles and settings, the settings unticked. */
    @Test
    fun restore() = shot(BackupState(restore = BackupRestore(setOf(BackupPart.PROFILES, BackupPart.SETTINGS), setOf(BackupPart.PROFILES))))

    @Test
    fun restoring() = shot(BackupState(restore = BackupRestore(setOf(BackupPart.PROFILES)), restoring = true))
}

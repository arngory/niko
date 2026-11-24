package io.niko.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The sheets over the configs' sheet (ui/home/ConfigsMenus) as images, light and dark: a group's menu, a profile's
 * sheet, its ways to share, a failed test's error. Goldens as NikoSnapshotTest's (CONTRIBUTING).
 */
@RunWith(Parameterized::class)
class ConfigsMenusSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT),
    )

    private val row = ConfigRow(
        21, "Netherlands · Amsterdam", listOf("VLESS", "REALITY"), null, unreachable = true, selected = false,
        error = "dial tcp 192.0.2.10:443: connect: connection refused | handshake: context deadline exceeded",
        share = true, standardLink = true,
    )

    private fun shot(content: @Composable () -> Unit) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") {
            Box(Modifier.fillMaxSize().background(Niko.colors.surface0)) { content() }
        }
    }

    /** D-PROFILE-06: both tests, what they left to clear, what to delete (in danger), the traffic counters. */
    @Test
    fun groupMenu() = shot { GroupMenu("provider.net", testing = false, onPick = {}) {} }

    /** D-PROFILE-07, 08: a profile with a place to move to on both sides, not the one the service runs. */
    @Test
    fun profileMenu() = shot {
        ProfileMenu(row.name, ConfigsLoader.profileMenu(row, up = true, down = true), onPick = {}) {}
    }

    /** D-PROFILE-07: a kind with a standard link, so all six ways. */
    @Test
    fun shareMenu() = shot { ShareMenu(row.name, ConfigsLoader.shareMenu(row), onPick = {}) {} }

    /** What the failed test said, in full. */
    @Test
    fun errorSheet() = shot { ErrorSheet(row.name, row.error!!) {} }
}

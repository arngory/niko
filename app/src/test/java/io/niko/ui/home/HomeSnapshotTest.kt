package io.niko.ui.home

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import io.niko.bg.BaseService
import io.niko.ui.compose.NikoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The main screen (ui/home) as images, light and dark, against the canvas's P-Simple, P-Advanced and P-ModeSheet: a
 * fixed clock and no ticking, so that the time online stays 00:42:17. Goldens as NikoSnapshotTest's (CONTRIBUTING).
 */
@RunWith(Parameterized::class)
class HomeSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")

        private const val NOW = 1_800_000_000_000L
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT),
    )

    private val none = object : HomeActions {
        override fun toggle() {}
        override fun view(view: HomeView) {}
        override fun mode(vpn: Boolean) {}
        override fun profiles() {}
        override fun subscriptions() {}
        override fun route() {}
        override fun apps() {}
        override fun settings() {}
        override fun nodes() {}
        override fun log() {}
        override fun help() {}
    }

    private val connected = HomeState(
        service = BaseService.State.Connected,
        connectedSince = NOW - (42 * 60 + 17) * 1000L,
        upRate = 312 * 1024L,
        downRate = 4_400_000L,
        profile = HomeProfile(
            name = "Hysteria2", type = "Hysteria2", group = "arngory", usage = "until 20 Dec · 38 / 100 GB",
            pingMs = 38, unreachable = false,
        ),
        route = HomeRoute.Russia,
        apps = HomeApps(perApp = true, bypass = true, count = 3),
        profiles = 6,
        subscriptions = 1,
    )

    private fun shot(state: HomeState, view: HomeView, sheet: Boolean = false) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") {
            HomeScreen(state, view, none, clock = { NOW }, ticking = false, sheet = sheet)
        }
    }

    @Test
    fun simpleConnected() = shot(connected, HomeView.Simple)

    @Test
    fun advancedConnected() = shot(connected, HomeView.Advanced)

    @Test
    fun modeSheet() = shot(connected, HomeView.Simple, sheet = true)

    @Test
    fun simpleFailed() = shot(
        connected.copy(service = BaseService.State.Stopped, connectedSince = 0, upRate = 0, downRate = 0, error = "dial tcp 192.0.2.10:443: i/o timeout"),
        HomeView.Simple,
    )

    @Test
    fun simpleEmpty() = shot(HomeState(), HomeView.Simple)
}

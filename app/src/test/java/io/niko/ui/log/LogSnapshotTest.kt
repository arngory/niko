package io.niko.ui.log

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import io.niko.ui.compose.NikoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The log (P-Log) as images, light and dark, on the canvas's lines. Goldens as NikoSnapshotTest's. */
@RunWith(Parameterized::class)
class LogSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT),
    )

    private val none = object : LogActions {
        override fun back() {}
        override fun refresh() {}
        override fun share() {}
        override fun clear() {}
        override fun statsHour() {}
        override fun nat() {}
        override fun dashboard() {}
    }

    private val log = LogState.read(
        """
        +0300 2026-11-20 14:02:11 INFO[0001] router: started
        +0300 2026-11-20 14:02:11 INFO[0002] inbound/tun[tun-in]: started at tun0
        +0300 2026-11-20 14:02:12 INFO[0003] outbound/naive[naive-out]: connected
        +0300 2026-11-20 14:02:14 WARN[0004] outbound/hysteria2[hy2-out]: no answer, 3 s
        +0300 2026-11-20 14:02:17 ERROR[0004] outbound/hysteria2[hy2-out]: dial timeout
        2026/11/20 14:02:17 [Info] [Watchdog] naive stays
        +0300 2026-11-20 14:03:40 DEBUG[0005] dns: exchanged ya.ru A
        +0300 2026-11-20 14:03:41 INFO[0006] router: ru.rostel → direct
        +0300 2026-11-20 14:05:02 INFO[0000] rule-set: ru updated
        """.trimIndent().toByteArray(),
        crashes = 1,
    )

    private fun shot(state: LogState?, filter: LogFilter = LogFilter.All, dashboard: Boolean = false) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") {
            LogScreen(state, filter, {}, none, dashboard)
        }
    }

    @Test
    fun log() = shot(log)

    @Test
    fun errors() = shot(log, LogFilter.Errors, dashboard = true)

    @Test
    fun empty() = shot(LogState(emptyList(), crashes = 0))
}

package io.niko.ui.probe

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import io.niko.Key
import io.niko.ui.compose.NikoTheme
import io.niko.ui.settings.InboundScreen
import io.niko.ui.settings.ListScreen
import io.niko.ui.settings.ListScreens
import io.niko.ui.settings.MemorySettingValues
import io.niko.ui.settings.SettingValues

/**
 * The list-screen probe (docs/research/list-screens-probe.md): Настройки › Вход made by hand (variant А,
 * [ListProbeHandActivity]) and drawn from shared/screens/set_inbound.json (variant В, [ListProbeSpecActivity]), over
 * the same values held in memory. Only adb's shell starts them (DUMP; .github/scripts/list-probe.sh). With the extra
 * "frames" the screen is made anew that many times, one a frame, and the time from the start of its composition to
 * the end of its drawing goes to the log (tag NikoListProbe) as the median and the 90th percentile.
 */
abstract class ListProbeActivity : ComponentActivity() {

    protected abstract val variant: String

    @Composable
    protected abstract fun Screen(values: SettingValues)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val frames = intent.getIntExtra("frames", 0).coerceIn(0, 1000)
        val values = probeValues()
        setContent {
            NikoTheme {
                var generation by remember { mutableIntStateOf(0) }
                val started = remember { LongArray(1) }
                val took = remember { LongArray(frames) }
                Box(
                    Modifier.drawWithContent {
                        drawContent()
                        if (generation in 1..frames) took[generation - 1] = System.nanoTime() - started[0]
                    },
                ) {
                    key(generation) {
                        // a probe's own clock: composition is where the made-anew screen starts
                        started[0] = System.nanoTime()
                        Screen(values)
                    }
                }
                if (frames > 0) LaunchedEffect(Unit) {
                    repeat(frames) {
                        withFrameNanos { }
                        generation++
                    }
                    withFrameNanos { }
                    withFrameNanos { }
                    val sorted = took.sorted()
                    // INFO is loggable by default: list-probe.sh reads it from a release build
                    if (Log.isLoggable(TAG, Log.INFO)) {
                        Log.i(
                            TAG,
                            "variant=$variant frames=$frames median_us=${sorted[frames / 2] / 1000} " +
                                "p90_us=${sorted[frames * 9 / 10] / 1000}",
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val TAG = "NikoListProbe"

        /** The canvas's values: user niko, port 2080, a password of twelve, every switch off. */
        fun probeValues() = MemorySettingValues(
            texts = mapOf(Key.MIXED_USERNAME to "niko", Key.MIXED_PORT to "2080", Key.MIXED_PASSWORD to "000000000000"),
        )
    }
}

/** Variant А: the screen made by hand. */
class ListProbeHandActivity : ListProbeActivity() {
    override val variant = "hand"

    @Composable
    override fun Screen(values: SettingValues) = InboundScreen(values, onBack = ::finish, onNewPassword = {})
}

/** Variant В: the screen drawn from its description. */
class ListProbeSpecActivity : ListProbeActivity() {
    override val variant = "spec"

    @Composable
    override fun Screen(values: SettingValues) = ListScreen(ListScreens.setInbound, values, onBack = ::finish, onAction = {})
}

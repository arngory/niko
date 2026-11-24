package io.niko.ui.log

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.R
import io.niko.database.DataStore
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ktx.setField
import io.niko.ktx.snackbar
import io.niko.raycontrol.RayTestRun
import io.niko.ui.MainActivity
import io.niko.ui.tools.NatActivity
import io.niko.ui.compose.NikoTheme
import io.niko.utils.SendLog
import libcore.Libcore

/**
 * Журнал и инструменты (P-Log, D-LOG-01…03, D-TOOLS-01…02) on Compose: neko.log's last 50 KB, read off the main thread when the screen opens and on
 * refresh, shown from its end as the old screen did.
 */
class LogFragment : Fragment() {

    private val state = mutableStateOf<LogState?>(null)
    private val filter = mutableStateOf(LogFilter.All)

    private val actions = object : LogActions {
        override fun back() {
            (requireActivity() as MainActivity).displayFragmentWithId(R.id.nav_home)
        }

        override fun refresh() = read()

        override fun share() {
            val context = requireContext()
            runOnDefaultDispatcher { SendLog.sendLog(context, "Niko") }
        }

        override fun clear() {
            runOnDefaultDispatcher {
                try {
                    Libcore.nekoLogClear()
                    Runtime.getRuntime().exec("/system/bin/logcat -c")
                } catch (e: Exception) {
                    onMainDispatcher { if (isAdded) snackbar(e.readableMessage).show() }
                    return@runOnDefaultDispatcher
                }
                read()
            }
        }

        override fun statsHour() = askStatsHour()

        override fun nat() = startActivity(Intent(requireContext(), NatActivity::class.java))

        override fun dashboard() {
            (requireActivity() as MainActivity).displayFragmentWithId(R.id.nav_traffic)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NikoTheme {
                    val shown by state
                    val level by filter
                    val scroll = rememberScrollState()
                    // the newest lines at the bottom: each read and each filter from the end, once it is laid out
                    LaunchedEffect(shown, level) {
                        if (shown != null) {
                            withFrameNanos {}
                            scroll.scrollTo(scroll.maxValue)
                        }
                    }
                    LogScreen(shown, level, { filter.value = it }, actions, DataStore.enableClashAPI, scroll)
                }
            }
        }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        read()
    }

    private fun read() {
        runOnDefaultDispatcher {
            val read = LogState.read(SendLog.getNekoLog(50 * 1024), SendLog.coreCrashes())
            onMainDispatcher { state.value = read }
        }
    }

    /** "Statistics for the last hour" under a run id, the node's rayctl stats -id taking the same (Stats). */
    private fun askStatsHour() {
        val input = EditText(requireContext()).apply {
            setText(RayTestRun.defaultId())
            setSingleLine()
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stats_hour)
            .setMessage(R.string.stats_hour_help)
            .setField(input)
            .setPositiveButton(R.string.stats_hour_share) { _, _ ->
                val run = input.text.toString().trim()
                if (!RayTestRun.validId(run)) {
                    snackbar(R.string.raycontrol_test_run_bad_id).show()
                    return@setPositiveButton
                }
                val context = requireContext().applicationContext
                runOnDefaultDispatcher {
                    runCatching { SendLog.sendStats(context, run) }.onFailure { e ->
                        onMainDispatcher { if (isAdded) snackbar(e.readableMessage).show() }
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

package io.niko.ui

import android.annotation.SuppressLint
import android.os.Bundle
import android.text.SpannableString
import android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
import android.text.style.ForegroundColorSpan
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import androidx.core.view.doOnLayout
import io.niko.R
import io.niko.databinding.LayoutLogcatBinding
import io.niko.ktx.*
import io.niko.widget.ListListener
import libcore.Libcore
import io.niko.utils.SendLog

class LogcatFragment : ToolbarFragment(R.layout.layout_logcat),
    Toolbar.OnMenuItemClickListener {

    lateinit var binding: LayoutLogcatBinding

    @SuppressLint("RestrictedApi", "WrongConstant")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        toolbar.setTitle(R.string.menu_log)

        toolbar.inflateMenu(R.menu.logcat_menu)
        toolbar.setOnMenuItemClickListener(this)

        binding = LayoutLogcatBinding.bind(view)

        binding.textview.breakStrategy = 0 // simple

        ViewCompat.setOnApplyWindowInsetsListener(binding.root, ListListener)

        reloadSession()
    }

    private fun colorFor(tone: LogTone): Int = requireContext().getColor(
        when (tone) {
            LogTone.Danger -> R.color.niko_danger
            LogTone.Quiet -> R.color.niko_ink_2
            LogTone.Plain -> R.color.niko_ink
        }
    )

    private fun reloadSession() {
        val span = SpannableString(
            String(SendLog.getNekoLog(50 * 1024))
        )
        var offset = 0
        val colors = LogTone.entries.associateWith { colorFor(it) }
        for (line in span.lines()) {
            span.setSpan(
                ForegroundColorSpan(colors.getValue(LogTone.of(line))), offset, offset + line.length,
                SPAN_EXCLUSIVE_EXCLUSIVE,
            )
            offset += line.length + 1
        }
        binding.textview.text = span
        binding.textview.clearFocus()
        // 等 textview 完成最终 layout 再滚动到底部
        binding.textview.doOnLayout {
            binding.scroolview.scrollTo(0, binding.textview.height)
        }
    }

    /** "Statistics for the last hour" under a run id, the node's rayctl stats -id taking the same (Stats). */
    private fun statsHour() {
        val input = android.widget.EditText(requireContext()).apply {
            setText(io.niko.raycontrol.RayTestRun.defaultId())
            setSingleLine()
        }
        com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.stats_hour)
            .setMessage(R.string.stats_hour_help)
            .setField(input)
            .setPositiveButton(R.string.stats_hour_share) { _, _ ->
                val run = input.text.toString().trim()
                if (!io.niko.raycontrol.RayTestRun.validId(run)) {
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

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_clear_logcat -> {
                runOnDefaultDispatcher {
                    try {
                        Libcore.nekoLogClear()
                        Runtime.getRuntime().exec("/system/bin/logcat -c")
                    } catch (e: Exception) {
                        onMainDispatcher {
                            snackbar(e.readableMessage).show()
                        }
                        return@runOnDefaultDispatcher
                    }
                    onMainDispatcher {
                        binding.textview.text = ""
                    }
                }

            }

            R.id.action_send_logcat -> {
                val context = requireContext()
                runOnDefaultDispatcher {
                    SendLog.sendLog(context, "Niko")
                }
            }

            R.id.action_refresh -> {
                reloadSession()
            }

            R.id.action_stats_hour -> statsHour()
        }
        return true
    }

}

/**
 * A log line's colour, as the desktop's log has it (log_screen.dart): an error, a fatal one or a panic in danger, a
 * debug or trace line ink-2, the rest ink. The core's lines carry their level as "ERROR[0012]", libcore's as " [Error]".
 */
enum class LogTone {
    Danger, Quiet, Plain;

    companion object {
        private val danger = listOf("ERROR[", "FATAL[", "PANIC[", " [Error]", " [Fatal]", " [Panic]")
        private val quiet = listOf("DEBUG[", "TRACE[", " [Debug]", " [Trace]")

        fun of(line: String): LogTone = when {
            danger.any { line.contains(it) } -> Danger
            quiet.any { line.contains(it) } -> Quiet
            else -> Plain
        }
    }
}

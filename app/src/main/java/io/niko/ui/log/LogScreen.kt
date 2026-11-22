package io.niko.ui.log

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoRadius
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Section
import io.niko.ui.home.Text
import io.niko.ui.home.Trail
import io.niko.ui.settings.IconButton
import io.niko.ui.settings.ListPage
import io.niko.ui.settings.Segmented

/** What P-Log asks of its fragment. */
interface LogActions {
    fun back()
    fun refresh()
    /** The debug export: the log, the core's crashes, raycontrol's state, the secrets struck out (D-LOG-03). */
    fun share()
    fun clear()
    fun statsHour()
    /** The NAT's behaviour through STUN (D-LOG-03, D-TOOLS-01). */
    fun nat()
    /** The sing-box panel over the Clash API, while the setting has it on (D-TOOLS-02). */
    fun dashboard()
}

/**
 * Журнал (P-Log): the top bar's refresh, export and clear, «Все / Ошибки», the log's last part in Geist Mono (the clock
 * in ink-2, an error in danger), then the core's crash reports (in the export) and the statistics for the last hour;
 * below, the tools that were a screen of the side menu: the NAT's behaviour and, with the Clash API on ([dashboard]),
 * the sing-box panel. [state] null: not read yet.
 */
@Composable
fun LogScreen(
    state: LogState?,
    filter: LogFilter,
    onFilter: (LogFilter) -> Unit,
    actions: LogActions,
    dashboard: Boolean = false,
    scroll: ScrollState = rememberScrollState(),
) {
    val c = Niko.colors
    ListPage(
        stringResource(R.string.log_title), stringResource(R.string.screen_back), actions::back,
        actions = {
            IconButton(R.drawable.ic_l_refresh_cw, stringResource(R.string.group_update), onClick = actions::refresh)
            IconButton(R.drawable.ic_l_share_2, stringResource(R.string.logcat), onClick = actions::share)
            IconButton(R.drawable.ic_l_trash_2, stringResource(R.string.clear_logcat), onClick = actions::clear)
        },
        scroll = scroll,
    ) {
        Segmented(
            null,
            listOf(LogFilter.All to stringResource(R.string.log_all), LogFilter.Errors to stringResource(R.string.log_errors)),
            filter, block = true, name = stringResource(R.string.log_level), onPick = onFilter,
        )
        LogBlock(state?.shown(filter), filter)
        ListGroup {
            val crashes = state?.crashes ?: 0
            ListRow(
                stringResource(R.string.log_crashes),
                trail = { if (crashes > 0) Trail(crashes.toString()) else Text("0", NikoType.bodySm, c.ink2, maxLines = 1) },
                onClick = if (crashes > 0) actions::share else null,
            )
            ListRow(stringResource(R.string.stats_hour), divider = true, trail = { Trail(null) }, onClick = actions::statsHour)
        }
        Section(stringResource(R.string.log_tools)) {
            ListRow(stringResource(R.string.stun_test), trail = { Trail(null) }, onClick = actions::nat)
            if (dashboard) {
                ListRow(stringResource(R.string.menu_dashboard), divider = true, trail = { Trail(null) }, onClick = actions::dashboard)
            }
        }
    }
}

/** The log in t-code on surface-1, a line under another 6 apart; its text may be selected and copied. */
@Composable
private fun LogBlock(lines: List<LogLine>?, filter: LogFilter) {
    val c = Niko.colors
    val style = NikoType.code.copy(fontSize = 12.sp, lineHeight = 18.sp)
    SelectionContainer {
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(NikoRadius.md)).background(c.surface1).padding(NikoSpace.s4),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            when {
                lines == null -> {}
                lines.isEmpty() -> Text(
                    stringResource(if (filter == LogFilter.Errors) R.string.log_no_errors else R.string.log_empty),
                    NikoType.bodySm, c.ink2,
                )
                else -> for (line in lines) {
                    val tone = when (line.tone) {
                        LogTone.Danger -> c.danger
                        LogTone.Quiet -> c.ink2
                        LogTone.Plain -> c.ink
                    }
                    BasicText(
                        buildAnnotatedString {
                            if (line.time != null) withStyle(SpanStyle(color = c.ink2)) { append(line.time); append(' ') }
                            withStyle(SpanStyle(color = tone)) { append(line.text) }
                        },
                        style = style,
                    )
                }
            }
        }
    }
}

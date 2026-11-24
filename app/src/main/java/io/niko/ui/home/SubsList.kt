package io.niko.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoStroke
import io.niko.ui.compose.NikoType
import io.niko.ui.node.NodeFormat

/** What the Subscriptions tab asks of the activity. */
interface SubsActions {
    /** a node's card; another group's settings (the ungrouped has none) */
    fun open(row: SubRow)
    /** the group fetched anew: a provider's subscription, or the node's */
    fun update(row: SubRow)
    /** every subscription and node fetched anew, after a question */
    fun updateAll()
    /** a new group: its settings, empty */
    fun add()
    fun action(row: SubRow, action: SubAction)
    /** the group changes places with [other], next to it in the list (D-SUB-10) */
    fun move(row: SubRow, other: SubRow)
}

/** The header's buttons on the Subscriptions tab: update all, add (P-Subs). */
@Composable
internal fun SubsButtons(actions: SubsActions) {
    PlainIconButton(R.drawable.ic_l_refresh_cw, stringResource(R.string.subs_update_all), onClick = actions::updateAll)
    TonalIconButton(R.drawable.ic_l_plus, stringResource(R.string.home_add), actions::add)
}

/**
 * P-Subs (D-SUB-01…07): the groups in the user's order, each with when it ends and was fetched, its traffic of the
 * limit, its update and its menu; [rows] null: not read yet. [now] unix s, for what has ended.
 */
@Composable
internal fun SubsList(
    rows: List<SubRow>?,
    format: NodeFormat,
    now: Long,
    actions: SubsActions,
    modifier: Modifier = Modifier,
    onMenu: (SubRow) -> Unit,
) {
    val c = Niko.colors
    when {
        rows == null -> Unit
        rows.isEmpty() -> Text(stringResource(R.string.subs_none), NikoType.bodySm, c.ink2, Modifier.padding(NikoSpace.s5))
        else -> LazyColumn(modifier.fillMaxWidth()) {
            itemsIndexed(rows, key = { _, row -> row.id }) { i, row ->
                SubRowView(row, format, now, divider = i > 0, actions) { onMenu(row) }
            }
        }
    }
}

/** m-row: the name (a node's tagged), its lines and its traffic; on the right its update and its menu. */
@Composable
private fun SubRowView(row: SubRow, format: NodeFormat, now: Long, divider: Boolean, actions: SubsActions, onMenu: () -> Unit) {
    val c = Niko.colors
    val line = c.line
    val source = remember { MutableInteractionSource() }
    val opens = row.kind != SubKind.UNGROUPED
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .drawBehind {
                val side = NikoSpace.s5.toPx()
                if (divider) drawRect(line, Offset(side, 0f), androidx.compose.ui.geometry.Size(size.width - side, NikoStroke.hairline.toPx()))
            }
            .background(if (opens && pressed(source)) c.surface2 else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(source, indication = null, enabled = opens) { actions.open(row) }
            .padding(start = NikoSpace.s5, end = NikoSpace.s2, top = NikoSpace.s3, bottom = NikoSpace.s3),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
    ) {
        Column(Modifier.weight(1f).padding(top = NikoSpace.s1), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
                Text(row.name, NikoType.body.copy(fontWeight = NikoType.headline.fontWeight), c.ink, Modifier.weight(1f, fill = false), maxLines = 1)
                if (row.kind == SubKind.NODE) Tag(stringResource(R.string.subs_node_tag))
                if (row.admin) Tag(stringResource(R.string.subs_admin_tag))
            }
            val (text, danger) = subLine(row, format, now)
            Text(text, NikoType.bodySm, if (danger) c.danger else c.ink2, maxLines = 1)
            row.announce?.let { Text(it, NikoType.bodySm, c.ink2, maxLines = 2) }
            if (row.total > 0) SubQuota(row, format, Modifier.padding(top = NikoSpace.s2))
        }
        val name = row.name
        if (row.canUpdate) PlainIconButton(
            R.drawable.ic_l_refresh_cw, stringResource(R.string.subs_update, name), enabled = !row.updating,
        ) { actions.update(row) }
        PlainIconButton(R.drawable.ic_l_ellipsis_vertical, stringResource(R.string.subs_menu, name), onClick = onMenu)
    }
}

/**
 * The row's second line: until when, or since when it has ended (in danger), when it was fetched; the own groups their
 * count; one being fetched says so.
 */
@Composable
private fun subLine(row: SubRow, format: NodeFormat, now: Long): Pair<String, Boolean> {
    if (row.updating) return stringResource(R.string.subs_updating) to false
    val parts = mutableListOf<String>()
    val expired = row.expired(now)
    if (row.expire > 0) parts += stringResource(if (expired) R.string.subs_expired else R.string.subs_until, format.day(row.expire))
    when (row.kind) {
        SubKind.SUBSCRIPTION, SubKind.NODE -> parts += when {
            row.updatedAt > 0 -> stringResource(R.string.subs_updated, format.time(row.updatedAt))
            row.kind == SubKind.SUBSCRIPTION -> stringResource(R.string.group_status_empty_subscription)
            else -> pluralStringResource(R.plurals.subs_configs, row.profiles.toInt(), row.profiles.toInt())
        }
        SubKind.OWN, SubKind.UNGROUPED -> parts += pluralStringResource(R.plurals.subs_configs, row.profiles.toInt(), row.profiles.toInt())
    }
    return parts.joinToString(" · ") to (expired || row.exhausted)
}

/** n-quota: used of the limit and when it resets, the bar; used up or over: in danger. */
@Composable
private fun SubQuota(row: SubRow, format: NodeFormat, modifier: Modifier = Modifier) {
    val c = Niko.colors
    val fraction = (row.used.toFloat() / row.total).coerceIn(0f, 1f)
    val tone = if (row.exhausted || row.used >= row.total) c.danger else c.ink
    val label = stringResource(R.string.node_traffic)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
            Text("${format.bytes(row.used)} / ${format.bytes(row.total)}", NikoType.code, tone, maxLines = 1)
            if (row.reset > 0) Text(stringResource(R.string.node_quota_reset, format.day(row.reset)), NikoType.bodySm, c.ink2, maxLines = 1)
        }
        Box(
            Modifier.fillMaxWidth().height(6.dp).clip(CircleShape).background(c.surface2)
                .semantics {
                    contentDescription = label
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                },
        ) {
            Box(Modifier.fillMaxWidth(fraction).height(6.dp).clip(CircleShape).background(tone))
        }
    }
}

/**
 * m-sheet of a group's menu: its name, then a row for each action; clear and delete in danger. [up] and [down]: it can
 * change places with the group above or below.
 */
@Composable
internal fun SubMenu(row: SubRow, up: Boolean = false, down: Boolean = false, onPick: (SubAction) -> Unit, onDismiss: () -> Unit) {
    val c = Niko.colors
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { }.navigationBarsPadding()) {
            Text(row.name, NikoType.headline, c.ink, Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s2), maxLines = 1)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = NikoSpace.s6)) {
                SubsLoader.menu(row, up, down).forEachIndexed { i, action ->
                    val (icon, title) = menuItem(action)
                    val danger = action == SubAction.CLEAR || action == SubAction.DELETE
                    ListRow(
                        title,
                        divider = i > 0,
                        side = NikoSpace.s5,
                        lead = { Icon(icon, if (danger) c.danger else c.ink2) },
                        onClick = { onPick(action) },
                    )
                }
            }
        }
    }
}

@Composable
private fun menuItem(action: SubAction): Pair<Int, String> = when (action) {
    SubAction.SETTINGS -> R.drawable.ic_l_settings to stringResource(R.string.subs_settings)
    SubAction.ADMIN -> R.drawable.ic_l_server_cog to stringResource(R.string.raycontrol_admin)
    SubAction.LAN -> R.drawable.ic_l_network to stringResource(R.string.raycontrol_lan)
    SubAction.QR -> R.drawable.ic_l_share_2 to stringResource(R.string.subs_qr)
    SubAction.COPY_LINK -> R.drawable.ic_l_link to stringResource(R.string.subs_copy_link)
    SubAction.ANOTHER_DEVICE -> R.drawable.ic_l_monitor_smartphone to stringResource(R.string.subs_another_device)
    SubAction.EXPORT_CLIPBOARD -> R.drawable.ic_l_upload to stringResource(R.string.action_export_clipboard)
    SubAction.EXPORT_FILE -> R.drawable.ic_l_file_up to stringResource(R.string.action_export_file)
    SubAction.MOVE_UP -> R.drawable.ic_l_arrow_up to stringResource(R.string.pe_move_up)
    SubAction.MOVE_DOWN -> R.drawable.ic_l_arrow_down to stringResource(R.string.pe_move_down)
    SubAction.CLEAR -> R.drawable.ic_l_x to stringResource(R.string.subs_clear)
    SubAction.DELETE -> R.drawable.ic_l_trash_2 to stringResource(R.string.subs_delete)
}

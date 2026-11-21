package io.niko.ui.node

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoRadius
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.ConfigRow
import io.niko.ui.home.ConfigsLoader
import io.niko.ui.home.ConfigsTest
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.PlainIconButton
import io.niko.ui.home.Radio
import io.niko.ui.home.Tag
import io.niko.ui.home.Text
import io.niko.ui.home.Trail

/** What the node card asks of its screen. */
interface NodeActions {
    fun back()
    /** the subscription anew from the node */
    fun update()
    /** the rest of the node's actions: the report, the last test run's findings, removing it */
    fun more()
    /** the node's profiles tested (ConfigsTest on its group), or the test stopped */
    fun test(groupId: Long)
    fun stopTest()
    /** the profile to use */
    fun select(id: Long)
    /** the channel check: the direct path, the DNS tunnels' resolvers, each profile measured here */
    fun channel()
    fun lan()
    /** the test run, "why it does not work" (D-WHY) */
    fun why()
    fun admin()
    /** this app below the node's minimum: the update (About, checking) */
    fun appUpdate()
}

/** How the card writes a day and a time (the device's locale; a fixed one in the snapshots). */
class NodeFormat(val day: (Long) -> String, val time: (Long) -> String, val bytes: (Long) -> String)

/**
 * P-Node: the node's name and note, what holds and keeps it from working (D-NODE-01…09), its ways with their pings and
 * test (D-NODE-10, 13), and the node's other screens as rows: the channel, the local network, the test run, the admin
 * panel. [state] null: not read yet.
 */
@Composable
fun NodeScreen(
    state: NodeCardState?,
    testing: Set<Long>,
    results: Map<Long, ConfigsTest.Result>,
    format: NodeFormat,
    now: Long,
    actions: NodeActions,
    /** the subscription is being fetched anew: the update button waits */
    updating: Boolean = false,
) {
    val c = Niko.colors
    Column(Modifier.fillMaxSize().background(c.surface0).statusBarsPadding()) {
        // m-topbar: back, the name over the note, update and more
        Row(
            Modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s2),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlainIconButton(R.drawable.ic_l_arrow_left, stringResource(R.string.node_back), background = c.surface0, onClick = actions::back)
            Column(Modifier.weight(1f).padding(horizontal = NikoSpace.s2)) {
                Text(state?.name.orEmpty(), NikoType.headline, c.ink, maxLines = 1)
                state?.note?.let { Text(it, NikoType.bodySm, c.ink2, maxLines = 1) }
            }
            PlainIconButton(
                R.drawable.ic_l_refresh_cw, stringResource(R.string.node_update), enabled = state != null && !updating,
                background = c.surface0, onClick = actions::update,
            )
            PlainIconButton(
                R.drawable.ic_l_ellipsis_vertical, stringResource(R.string.node_more), enabled = state != null,
                background = c.surface0, onClick = actions::more,
            )
        }
        if (state == null) return@Column
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()
                .padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s6),
            verticalArrangement = Arrangement.spacedBy(NikoSpace.s6),
        ) {
            state.warning?.let { Warning(it, format, actions) }
            Facts(state, format, now)
            Ways(state, testing, results, actions)
            // the node's other screens; the channel check and the test run need its ways
            val ways = state.profiles.isNotEmpty()
            val screens = buildList {
                if (ways) add(R.string.node_channel to actions::channel)
                if (state.lan) add(R.string.node_lan to actions::lan)
                if (ways) add(R.string.node_why to actions::why)
                if (state.admin) add(R.string.node_admin to actions::admin)
            }
            if (screens.isNotEmpty()) ListGroup {
                screens.forEachIndexed { i, (title, open) ->
                    ListRow(stringResource(title), divider = i > 0, trail = { Trail(null) }, onClick = open)
                }
            }
        }
    }
}

/** m-banner m-banner--danger: what keeps the node from working, in words; this app below the node's minimum leads to the update. */
@Composable
private fun Warning(warning: NodeWarning, format: NodeFormat, actions: NodeActions) {
    val c = Niko.colors
    val text = when (warning) {
        NodeWarning.Refused -> stringResource(R.string.raycontrol_card_refused)
        is NodeWarning.Failing -> stringResource(R.string.raycontrol_card_failing, warning.times)
        is NodeWarning.ClientMin -> if (warning.from == 0L) stringResource(R.string.raycontrol_card_client_min, warning.min)
        else stringResource(R.string.raycontrol_card_client_min_from, warning.min, format.day(warning.from))
    }
    val tap = if (warning is NodeWarning.ClientMin) Modifier.clickable(role = Role.Button, onClick = actions::appUpdate) else Modifier
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(NikoRadius.md)).background(c.dangerSoft).then(tap).padding(NikoSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NikoSpace.s3),
    ) {
        io.niko.ui.home.Icon(R.drawable.ic_l_triangle_alert, c.danger)
        Text(text.replaceFirstChar { it.uppercase() }, NikoType.bodySm, c.danger)
    }
}

/** The node's facts: its traffic, its end, when it last came, its path here. */
@Composable
private fun Facts(state: NodeCardState, format: NodeFormat, now: Long) {
    val c = Niko.colors
    val rows = buildList<@Composable (Boolean) -> Unit> {
        state.traffic?.let { traffic -> add { divider -> Quota(traffic, format, divider) } }
        if (state.expires > 0) add { divider ->
            val past = state.expires <= now
            ListRow(stringResource(R.string.node_expires), divider = divider, trail = {
                Text(
                    if (past) stringResource(R.string.raycontrol_card_expired, format.day(state.expires)) else format.day(state.expires),
                    NikoType.bodySm, if (past) c.danger else c.ink, maxLines = 1,
                )
            })
        }
        add { divider ->
            ListRow(stringResource(R.string.node_updated), divider = divider, trail = {
                Text(
                    if (state.updated > 0) format.time(state.updated) else stringResource(R.string.node_updated_never),
                    NikoType.code, c.ink, maxLines = 1,
                )
            })
        }
        state.relayPath?.let { relay ->
            add { divider ->
                ListRow(stringResource(R.string.node_path), divider = divider, trail = {
                    Text(
                        stringResource(if (relay) R.string.raycontrol_card_path_relay else R.string.raycontrol_card_path_direct),
                        NikoType.bodySm, c.ink, maxLines = 1,
                    )
                })
            }
        }
    }
    ListGroup { rows.forEachIndexed { i, row -> row(i > 0) } }
}

/** n-quota: used of the limit, when it resets, and the bar; used up: in danger. */
@Composable
private fun Quota(traffic: io.niko.raycontrol.RayTraffic, format: NodeFormat, divider: Boolean) {
    val c = Niko.colors
    val fraction = if (traffic.limit > 0) (traffic.used.toFloat() / traffic.limit).coerceIn(0f, 1f) else 0f
    val tone = if (traffic.exhausted || traffic.nearLimit) c.danger else c.ink
    val label = stringResource(R.string.node_traffic)
    Column(
        Modifier.fillMaxWidth().padding(horizontal = NikoSpace.s4, vertical = NikoSpace.s3),
        verticalArrangement = Arrangement.spacedBy(NikoSpace.s2),
    ) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
            Text("${format.bytes(traffic.used)} / ${format.bytes(traffic.limit)}", NikoType.code, tone, maxLines = 1)
            if (!traffic.exhausted && traffic.reset > 0) {
                Text(stringResource(R.string.node_quota_reset, format.day(traffic.reset)), NikoType.bodySm, c.ink2, maxLines = 1)
            }
        }
        // used up: until when, or until the admin raises the limit (traffic-limit-v1)
        if (traffic.exhausted) Text(
            (if (traffic.reset > 0) stringResource(R.string.raycontrol_card_traffic_exhausted, format.day(traffic.reset))
            else stringResource(R.string.raycontrol_card_traffic_exhausted_admin)).replaceFirstChar { it.uppercase() },
            NikoType.bodySm, c.danger,
        )
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

/** The node's ways: its profiles with their tags and pings, the one in use chosen; the test from the section's head. */
@Composable
private fun Ways(state: NodeCardState, testing: Set<Long>, results: Map<Long, ConfigsTest.Result>, actions: NodeActions) {
    val c = Niko.colors
    val running = state.groupId in testing
    val rows = ConfigsLoader.withResults(listOf(io.niko.ui.home.ConfigGroup(state.groupId, null, true, state.profiles)), results)
        .single().profiles
    Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
        Row(Modifier.fillMaxWidth().padding(start = NikoSpace.s4), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.node_ways).uppercase(), NikoType.label, c.ink2, Modifier.weight(1f), maxLines = 1)
            if (state.profiles.isNotEmpty()) {
                if (running) PlainIconButton(R.drawable.ic_l_x, stringResource(R.string.node_stop_test), background = c.surface0, onClick = actions::stopTest)
                else PlainIconButton(R.drawable.ic_l_zap, stringResource(R.string.node_test_ways), background = c.surface0) { actions.test(state.groupId) }
            }
        }
        ListGroup {
            if (rows.isEmpty()) ListRow(stringResource(R.string.node_no_ways))
            rows.forEachIndexed { i, row -> WayRow(row, i > 0, actions) }
        }
    }
}

@Composable
private fun WayRow(row: ConfigRow, divider: Boolean, actions: NodeActions) {
    val c = Niko.colors
    val ping = when {
        row.unreachable -> stringResource(R.string.home_unreachable)
        row.pingMs != null -> "${row.pingMs} ms"
        else -> "—"
    }
    ListRow(
        row.name,
        titleBold = row.selected,
        divider = divider,
        lead = { Radio(row.selected) },
        sub = { row.tags.forEach { Tag(it) } },
        trail = { Text(ping, NikoType.code, if (row.unreachable) c.danger else c.ink2, maxLines = 1) },
        onClick = { actions.select(row.id) },
        role = Role.RadioButton,
    )
}

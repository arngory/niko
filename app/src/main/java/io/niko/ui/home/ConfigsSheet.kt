package io.niko.ui.home

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoRadius
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType

/** What the configs' sheet asks of the activity. */
interface ConfigsActions {
    /** the profile to use: the service, when running, takes it at once */
    fun select(id: Long)
    /** a URL test of the group's profiles (ConfigsTest), the results into the database */
    fun test(groupId: Long)
    /** the test running stopped, what it tested so far stored */
    fun stopTest()
    /** the profile's editor */
    fun edit(id: Long)
    /** adding a profile: the list where that is done today, until the Add screen (P-Add) */
    fun add()
    fun subscriptions()
}

/**
 * P-Configs: the profiles in their groups over the main screen, one chosen (a radio), the ping on the right, a group
 * tested at once from its head (D-MAIN-07, 10, 11; D-PROFILE-05). A tap chooses and closes the sheet, a long press opens
 * the profile's editor. [groups] null: not read yet.
 */
@Composable
internal fun ConfigsSheet(
    groups: List<ConfigGroup>?,
    profiles: Int,
    subscriptions: Int,
    testing: Set<Long>,
    results: Map<Long, ConfigsTest.Result>,
    actions: ConfigsActions,
    onClose: () -> Unit,
) {
    val c = Niko.colors
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    BackHandler {
        if (searching) {
            searching = false
            query = ""
        } else onClose()
    }
    Box(
        Modifier.fillMaxSize().background(c.scrim).statusBarsPadding()
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(
            Modifier.fillMaxHeight(0.92f)
                .clickable(remember { MutableInteractionSource() }, indication = null) { }
                .navigationBarsPadding(),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(start = NikoSpace.s5, end = NikoSpace.s3, bottom = NikoSpace.s2),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NikoSpace.s2),
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(NikoSpace.s6)) {
                    Tab(stringResource(R.string.home_profiles), profiles, active = true) { }
                    Tab(stringResource(R.string.home_subscriptions), subscriptions, active = false, onClick = actions::subscriptions)
                }
                PlainIconButton(R.drawable.ic_l_search, stringResource(R.string.home_search)) {
                    searching = !searching
                    if (!searching) query = ""
                }
                TonalIconButton(R.drawable.ic_l_plus, stringResource(R.string.home_add), actions::add)
            }
            if (searching) SearchField(query, { query = it })
            val shown = groups?.let { ConfigsLoader.filter(ConfigsLoader.withResults(it, results), query) }
            when {
                shown == null -> Unit
                shown.isEmpty() -> Text(
                    stringResource(if (query.isBlank()) R.string.home_no_profile else R.string.home_search_none),
                    NikoType.bodySm, c.ink2, Modifier.padding(NikoSpace.s5),
                )
                else -> LazyColumn(Modifier.fillMaxWidth().weight(1f)) {
                    for (group in shown) {
                        item(key = "g${group.id}") {
                            val running = group.id in testing
                            GroupHead(group, running) { if (running) actions.stopTest() else actions.test(group.id) }
                        }
                        items(group.profiles, key = { it.id }) { row ->
                            ConfigRowView(row, divider = row !== group.profiles.first(), actions, onClose)
                        }
                        item(key = "s${group.id}") { Spacer(Modifier.height(NikoSpace.s4)) }
                    }
                }
            }
        }
    }
}

/** m-section__head: the group's name in capitals, and its test button; while the group's test runs, its stop. */
@Composable
private fun GroupHead(group: ConfigGroup, testing: Boolean, onTest: () -> Unit) {
    val c = Niko.colors
    val name = group.name ?: stringResource(R.string.home_ungrouped)
    Row(
        Modifier.fillMaxWidth().padding(start = NikoSpace.s5, end = NikoSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name.uppercase(), NikoType.label, c.ink2, Modifier.weight(1f), maxLines = 1)
        if (testing) PlainIconButton(R.drawable.ic_l_x, stringResource(R.string.home_stop_test, name), onClick = onTest)
        else PlainIconButton(R.drawable.ic_l_zap, stringResource(R.string.home_test_group, name), onClick = onTest)
    }
}

/** m-row with a radio: the profile chosen in bold; the ping, or "no answer", on the right in mono. */
@Composable
private fun ConfigRowView(row: ConfigRow, divider: Boolean, actions: ConfigsActions, onClose: () -> Unit) {
    val unreachable = stringResource(R.string.home_unreachable)
    val ping = when {
        row.unreachable -> unreachable
        row.pingMs != null -> "${row.pingMs} ms"
        else -> "—"
    }
    ListRow(
        row.name,
        Modifier.semantics { selected = row.selected },
        titleBold = row.selected,
        divider = divider,
        side = NikoSpace.s5,
        lead = { Radio(row.selected) },
        sub = { row.tags.forEach { Tag(it) } },
        trail = { Text(ping, NikoType.code, if (row.unreachable) Niko.colors.danger else Niko.colors.ink2, maxLines = 1) },
        // chosen: back to the main screen, which shows it
        onClick = {
            actions.select(row.id)
            onClose()
        },
        onLongClick = { actions.edit(row.id) },
        role = Role.RadioButton,
    )
}

/** m-field: the search over the names and tags, on surface-2. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val c = Niko.colors
    Box(
        Modifier.fillMaxWidth().padding(horizontal = NikoSpace.s5, vertical = NikoSpace.s2).heightIn(min = 48.dp)
            .clip(RoundedCornerShape(NikoRadius.sm)).background(c.surface2).padding(horizontal = NikoSpace.s4),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) Text(stringResource(R.string.home_search_hint), NikoType.body, c.ink2, maxLines = 1)
        BasicTextField(
            query, onQuery, Modifier.fillMaxWidth(), singleLine = true,
            textStyle = NikoType.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink),
        )
    }
}

/** m-iconbtn (and m-iconbtn--muted): an icon alone, size-tap round, surface-2 pressed; dimmed when not enabled. */
@Composable
internal fun PlainIconButton(@DrawableRes icon: Int, label: String, enabled: Boolean = true, onClick: () -> Unit) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.size(NikoSize.tap).clip(CircleShape).background(if (pressed(source)) c.surface2 else c.surface1)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, if (enabled) c.ink2 else c.lineStrong, NikoSize.icon)
    }
}

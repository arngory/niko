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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import io.niko.ui.node.NodeFormat
import kotlinx.coroutines.launch

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
    /** adding a profile: the Add screen (P-Add) */
    fun add()
}

/**
 * P-Configs: the profiles in their groups over the main screen, one chosen (a radio), the ping on the right, a group
 * tested at once from its head (D-MAIN-07, 10, 11; D-PROFILE-05). A tap chooses and closes the sheet, a long press opens
 * the profile's sheet (edit, share, move, delete), a group's ⋮ its menu, a failed test's words its error; without
 * [menus] (a snapshot of the list alone) a long press opens the editor. The Configs tab tapped again scrolls to the
 * chosen profile, or to the top when it is in view. [groups] null: not read yet. Its second tab, P-Subs: the groups
 * themselves ([SubsList]), open first when [onSubs] ([asks] counts the times a tab was asked for: each turns an
 * open sheet to it). [testTotal] the profiles the running test takes: its group's
 * head counts those tested of them.
 */
@Composable
internal fun ConfigsSheet(
    groups: List<ConfigGroup>?,
    profiles: Int,
    subscriptions: Int,
    testing: Set<Long>,
    results: Map<Long, ConfigsTest.Result>,
    actions: ConfigsActions,
    subs: List<SubRow>? = null,
    subsActions: SubsActions? = null,
    format: NodeFormat? = null,
    now: Long = System.currentTimeMillis() / 1000,
    onSubs: Boolean = false,
    asks: Int = 0,
    menus: ConfigsMenuActions? = null,
    testTotal: Int = 0,
    onClose: () -> Unit,
) {
    val c = Niko.colors
    // without the tab's actions (a snapshot of the configs alone) the tab stays on the configs; asked for another tab
    // while open (a link imported), the sheet turns to it
    var subsTab by rememberSaveable(onSubs, asks) { mutableStateOf(onSubs && subsActions != null) }
    // the group whose menu is up, over the sheet
    var menuOf by rememberSaveable { mutableStateOf<Long?>(null) }
    // over the configs: a group's menu, a profile's sheet, its ways to share, its failed test's error
    var groupMenu by rememberSaveable { mutableStateOf<Long?>(null) }
    var profileMenu by rememberSaveable { mutableStateOf<Long?>(null) }
    var shareOf by rememberSaveable { mutableStateOf<Long?>(null) }
    var errorOf by rememberSaveable { mutableStateOf<Long?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    val current = groups?.let { ConfigsLoader.withResults(it, results) }
    val shown = current?.let { ConfigsLoader.filter(it, query) }
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
                    Tab(stringResource(R.string.home_profiles), profiles, active = !subsTab) {
                        if (subsTab) subsTab = false else {
                            // on it already: to the chosen profile, or to the top when it is in view (D-PROFILE-05)
                            val chosen = ConfigsLoader.selectedIndex(shown.orEmpty())
                            val inView = list.layoutInfo.visibleItemsInfo.any { it.index == chosen }
                            scope.launch { list.animateScrollToItem(if (chosen >= 0 && !inView) chosen else 0) }
                        }
                    }
                    Tab(stringResource(R.string.home_subscriptions), subscriptions, active = subsTab) {
                        if (subsActions != null) {
                            subsTab = true
                            searching = false
                            query = ""
                        }
                    }
                }
                if (subsTab && subsActions != null) SubsButtons(subsActions) else {
                    PlainIconButton(R.drawable.ic_l_search, stringResource(R.string.home_search)) {
                        searching = !searching
                        if (!searching) query = ""
                    }
                    TonalIconButton(R.drawable.ic_l_plus, stringResource(R.string.home_add), actions::add)
                }
            }
            if (subsTab && subsActions != null && format != null) {
                SubsList(subs, format, now, subsActions, Modifier.weight(1f)) { menuOf = it.id }
                return@Sheet
            }
            if (searching) SearchField(query, { query = it })
            when {
                shown == null -> Unit
                shown.isEmpty() -> Text(
                    stringResource(if (query.isBlank()) R.string.home_no_profile else R.string.home_search_none),
                    NikoType.bodySm, c.ink2, Modifier.padding(NikoSpace.s5),
                )
                else -> LazyColumn(Modifier.fillMaxWidth().weight(1f), list) {
                    for (group in shown) {
                        item(key = "g${group.id}") {
                            val running = group.id in testing
                            val progress = if (running) ConfigsLoader.testProgress(results, testTotal) else null
                            GroupHead(group, running, progress, onMenu = menus?.let { { groupMenu = group.id } }) {
                                if (running) actions.stopTest() else actions.test(group.id)
                            }
                        }
                        items(group.profiles, key = { it.id }) { row ->
                            ConfigRowView(
                                row, divider = row !== group.profiles.first(), actions, onClose, format?.bytes,
                                onMenu = menus?.let { { profileMenu = row.id } }, onError = { errorOf = row.id },
                            )
                        }
                        item(key = "s${group.id}") { Spacer(Modifier.height(NikoSpace.s4)) }
                    }
                }
            }
        }
        if (subsTab && subsActions != null) subs?.firstOrNull { it.id == menuOf }?.let { row ->
            val (above, below) = SubsLoader.neighbours(subs, row)
            SubMenu(row, up = above != null, down = below != null, onPick = { action ->
                when (action) {
                    // the menu stays, to move it further
                    SubAction.MOVE_UP -> above?.let { subsActions.move(row, it) }
                    SubAction.MOVE_DOWN -> below?.let { subsActions.move(row, it) }
                    else -> {
                        menuOf = null
                        subsActions.action(row, action)
                    }
                }
            }) { menuOf = null }
        }
        if (!subsTab && current != null) {
            fun row(id: Long?) = id?.let { current.firstNotNullOfOrNull { g -> g.profiles.firstOrNull { it.id == id } } }
            row(errorOf)?.let { row -> row.error?.let { ErrorSheet(row.name, it) { errorOf = null } } }
        }
        if (!subsTab && menus != null && current != null) {
            current.firstOrNull { it.id == groupMenu }?.let { group ->
                GroupMenu(group.name ?: stringResource(R.string.home_ungrouped), testing.isNotEmpty(), onPick = { action ->
                    groupMenu = null
                    menus.group(group.id, action)
                }) { groupMenu = null }
            }
            fun row(id: Long?) = id?.let { current.firstNotNullOfOrNull { g -> g.profiles.firstOrNull { it.id == id } } }
            row(profileMenu)?.let { row ->
                // moved among all the group's profiles: not while a search hides some of them
                val (above, below) = if (query.isBlank()) ConfigsLoader.neighbours(current, row.id) else null to null
                ProfileMenu(row.name, ConfigsLoader.profileMenu(row, above != null, below != null), onPick = { action ->
                    when (action) {
                        // the sheet stays, to move it further
                        ProfileAction.MOVE_UP -> above?.let { menus.move(row.id, it) }
                        ProfileAction.MOVE_DOWN -> below?.let { menus.move(row.id, it) }
                        else -> {
                            profileMenu = null
                            when (action) {
                                ProfileAction.EDIT -> actions.edit(row.id)
                                ProfileAction.SHARE -> shareOf = row.id
                                else -> menus.delete(row.id)
                            }
                        }
                    }
                }) { profileMenu = null }
            }
            row(shareOf)?.let { row ->
                ShareMenu(row.name, ConfigsLoader.shareMenu(row), onPick = { action ->
                    shareOf = null
                    menus.share(row.id, action)
                }) { shareOf = null }
            }
        }
    }
}

/**
 * m-section__head: the group's name in capitals, its test button (while the group's test runs, its stop and
 * [progress]: the profiles tested of all) and, with [onMenu], its menu.
 */
@Composable
private fun GroupHead(group: ConfigGroup, testing: Boolean, progress: Pair<Int, Int>?, onMenu: (() -> Unit)?, onTest: () -> Unit) {
    val c = Niko.colors
    val name = group.name ?: stringResource(R.string.home_ungrouped)
    Row(
        Modifier.fillMaxWidth().padding(start = NikoSpace.s5, end = NikoSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(name.uppercase(), NikoType.label, c.ink2, Modifier.weight(1f), maxLines = 1)
        if (progress != null) {
            Text(stringResource(R.string.home_test_progress, progress.first, progress.second), NikoType.code, c.ink2, maxLines = 1)
        }
        if (testing) PlainIconButton(R.drawable.ic_l_x, stringResource(R.string.home_stop_test, name), onClick = onTest)
        else PlainIconButton(R.drawable.ic_l_zap, stringResource(R.string.home_test_group, name), onClick = onTest)
        if (onMenu != null) PlainIconButton(R.drawable.ic_l_ellipsis_vertical, stringResource(R.string.subs_menu, name), onClick = onMenu)
    }
}

/**
 * m-row with a radio: the profile chosen in bold; the ping, or "no answer", on the right in mono: a failed test that
 * said why shows it on a tap ([onError]). After the tags, in mono: the address («Показывать адрес») and the traffic
 * counted, sizes by [bytes]. A long press: [onMenu], the editor without it.
 */
@Composable
private fun ConfigRowView(
    row: ConfigRow,
    divider: Boolean,
    actions: ConfigsActions,
    onClose: () -> Unit,
    bytes: ((Long) -> String)?,
    onMenu: (() -> Unit)?,
    onError: () -> Unit,
) {
    val detail = bytes?.let { ConfigsLoader.detail(row, it) }
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
        sub = {
            row.tags.forEach { Tag(it) }
            if (detail != null) Text(detail, NikoType.code, Niko.colors.ink2, Modifier.weight(1f, fill = false), maxLines = 1)
        },
        trail = {
            val why = stringResource(R.string.cfg_error_show)
            Text(
                ping, NikoType.code, if (row.unreachable) Niko.colors.danger else Niko.colors.ink2,
                if (row.unreachable && row.error != null) {
                    Modifier.clickable(onClickLabel = why, role = Role.Button, onClick = onError).padding(vertical = NikoSpace.s2)
                } else Modifier,
                maxLines = 1,
            )
        },
        // chosen: back to the main screen, which shows it
        onClick = {
            actions.select(row.id)
            onClose()
        },
        onLongClick = onMenu ?: { actions.edit(row.id) },
        role = Role.RadioButton,
    )
}

/** m-field: the search over the names, tags and addresses, on surface-2. */
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

/**
 * m-iconbtn (and m-iconbtn--muted): an icon alone, size-tap round, surface-2 pressed; dimmed when not enabled.
 * [background] the surface it sits on (the sheet's surface-1 by default, a screen's surface-0).
 */
@Composable
internal fun PlainIconButton(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean = true,
    background: androidx.compose.ui.graphics.Color = Niko.colors.surface1,
    onClick: () -> Unit,
) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.size(NikoSize.tap).clip(CircleShape).background(if (pressed(source)) c.surface2 else background)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, if (enabled) c.ink2 else c.lineStrong, NikoSize.icon)
    }
}

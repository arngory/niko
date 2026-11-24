package io.niko.ui.apps

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoRadius
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.Icon
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Radio
import io.niko.ui.home.Sheet
import io.niko.ui.home.Sub
import io.niko.ui.home.Text
import io.niko.ui.home.Trail
import io.niko.ui.home.pressed
import io.niko.ui.route.Checkbox
import io.niko.ui.route.LetterTile
import io.niko.ui.route.PrimaryButton
import io.niko.ui.settings.IconButton
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** What P-Apps asks of its activity. */
interface AppsActions {
    fun back()
    fun mode(mode: AppsMode)
    fun toggle(uid: Int)
    fun query(text: String)
    fun system(on: Boolean)
    fun invert()
    fun clear()
    /** the list from assets in place of the choice, after a question (M-RULES-03) */
    fun auto()
    fun export()
    fun import()
    /** the app's page in the system's settings, where the list of apps is allowed */
    fun openSettings()
}

/**
 * Приложения (P-Apps, M-RULES-01) or a rule's apps (M-RULES-02): the mode (not for a rule), the apps the choice holds,
 * then the others, a tick each; the search in the bar; the rest in ⋮ (M-RULES-03). [icon] an app's icon (a letter in
 * the tests); [menuOpen] the menu shown at once (the tests, the TV's Menu key).
 */
@Composable
fun AppsScreen(
    state: AppsState,
    actions: AppsActions,
    menuOpen: Boolean = false,
    modeOpen: Boolean = false,
    searchOpen: Boolean = state.query.isNotEmpty(),
    onMenu: (Boolean) -> Unit = {},
    icon: @Composable (AppRow) -> Unit = { LetterTile(it.name) },
) {
    val c = Niko.colors
    var search by remember { mutableStateOf(searchOpen) }
    var menu by remember { mutableStateOf(menuOpen) }
    var sheet by remember { mutableStateOf(modeOpen) }
    LaunchedEffect(menuOpen) { menu = menuOpen }
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize().background(c.surface0)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
            ) {
                IconButton(R.drawable.ic_l_arrow_left, stringResource(R.string.screen_back), onClick = actions::back)
                Text(
                    stringResource(if (state.forRule) R.string.ap_title_rule else R.string.ap_title), NikoType.headline, c.ink,
                    Modifier.weight(1f).padding(horizontal = NikoSpace.s2), maxLines = 1,
                )
                if (search) {
                    IconButton(R.drawable.ic_l_x, stringResource(R.string.ap_search_close)) {
                        search = false
                        actions.query("")
                    }
                } else {
                    IconButton(R.drawable.ic_l_search, stringResource(R.string.ap_search_open)) { search = true }
                }
                IconButton(R.drawable.ic_l_ellipsis_vertical, stringResource(R.string.ap_menu)) {
                    menu = true
                    onMenu(true)
                }
            }
            if (search) SearchField(state.query, actions::query)
            Box(Modifier.weight(1f)) {
                AppList(state, actions, list, { sheet = true }, icon)
                val (pinned, rest) = state.shown
                if (pinned.size + rest.size > 20) FastScroll(list, state)
            }
        }
        if (menu) Menu(state, actions) {
            menu = false
            onMenu(false)
        }
        if (sheet) ModeSheet(state.mode, { actions.mode(it); sheet = false }) { sheet = false }
    }
}

/** The list: the mode, then the chosen apps and the others in their groups, or why there are none. */
@Composable
private fun AppList(state: AppsState, actions: AppsActions, list: LazyListState, onMode: () -> Unit, icon: @Composable (AppRow) -> Unit) {
    val c = Niko.colors
    val (pinned, rest) = state.shown
    LazyColumn(
        Modifier.fillMaxSize(),
        state = list,
        contentPadding = PaddingValues(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s8),
    ) {
        if (!state.forRule) item(key = "mode") {
            ListGroup(Modifier.padding(bottom = NikoSpace.s6)) {
                ListRow(stringResource(R.string.ap_mode), trail = { Trail(modeName(state.mode)) }, onClick = onMode)
            }
        }
        val apps = state.apps
        when {
            apps == null -> item(key = "loading") { Note(stringResource(R.string.ap_loading)) }
            apps.isEmpty() -> item(key = "denied") {
                Column(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(NikoRadius.md)).background(c.surface1).padding(NikoSpace.s4),
                    verticalArrangement = Arrangement.spacedBy(NikoSpace.s4),
                ) {
                    Text(stringResource(R.string.app_list_permission_denied), NikoType.bodySm, c.ink)
                    PrimaryButton(stringResource(R.string.ap_open_settings), actions::openSettings)
                }
            }
            pinned.isEmpty() && rest.isEmpty() -> item(key = "none") { Note(stringResource(R.string.ap_none_found)) }
            else -> {
                if (pinned.isNotEmpty()) {
                    item(key = "head-chosen") { Head(stringResource(R.string.ap_chosen, state.count)) }
                    pinned.forEachIndexed { i, app ->
                        item(key = "p" + app.packageName) { AppRowView(app, state, actions, i, pinned.size, icon) }
                    }
                }
                if (rest.isNotEmpty()) {
                    item(key = "head-rest") {
                        Head(stringResource(if (pinned.isEmpty()) R.string.ap_all else R.string.ap_rest), top = pinned.isNotEmpty())
                    }
                    rest.forEachIndexed { i, app ->
                        item(key = "r" + app.packageName) { AppRowView(app, state, actions, i, rest.size, icon) }
                    }
                }
            }
        }
    }
}

/** m-overline over a group, space-6 after the group before it. */
@Composable
private fun Head(text: String, top: Boolean = false) {
    Text(
        text.uppercase(), NikoType.label, Niko.colors.ink2,
        Modifier.padding(start = NikoSpace.s4, end = NikoSpace.s4, top = if (top) NikoSpace.s6 else 0.dp, bottom = NikoSpace.s2),
    )
}

@Composable
private fun Note(text: String) = Text(text, NikoType.bodySm, Niko.colors.ink2, Modifier.padding(NikoSpace.s4))

/**
 * An app as m-row of its group (the group drawn row by row, the list being lazy): its icon, name and package, the tick
 * at its end; the whole row ticks it, a checkbox to a screen reader.
 */
@Composable
private fun AppRowView(app: AppRow, state: AppsState, actions: AppsActions, index: Int, size: Int, icon: @Composable (AppRow) -> Unit) {
    val c = Niko.colors
    val on = app.uid in state.chosen
    val source = remember { MutableInteractionSource() }
    val r = NikoRadius.md
    val shape = RoundedCornerShape(
        topStart = if (index == 0) r else 0.dp, topEnd = if (index == 0) r else 0.dp,
        bottomStart = if (index == size - 1) r else 0.dp, bottomEnd = if (index == size - 1) r else 0.dp,
    )
    Box(Modifier.clip(shape).background(c.surface1)) {
        ListRow(
            app.name,
            Modifier.background(if (pressed(source)) c.surface2 else Color.Transparent)
                .toggleable(on, source, indication = null, role = Role.Checkbox) { actions.toggle(app.uid) },
            sub = { Sub(app.packageName, mono = true) },
            divider = index > 0,
            lead = { icon(app) },
            trail = { Checkbox(on) },
        )
    }
}

/** m-field: the search over the names, packages and uids, on surface-2, the keyboard up when it opens. */
@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val c = Niko.colors
    val focus = remember { FocusRequester() }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = NikoSpace.s5, vertical = NikoSpace.s2).heightIn(min = 48.dp)
            .clip(RoundedCornerShape(NikoRadius.sm)).background(c.surface2).padding(horizontal = NikoSpace.s4),
        contentAlignment = Alignment.CenterStart,
    ) {
        if (query.isEmpty()) Text(stringResource(R.string.ap_search), NikoType.body, c.ink2, maxLines = 1)
        BasicTextField(
            query, onQuery, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
            textStyle = NikoType.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink),
        )
    }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
}

/**
 * The fast scroll the old list had: a thumb at the list's end that a drag moves through the whole list, the first
 * letter of the app under it shown beside it while dragged.
 */
@Composable
private fun FastScroll(list: LazyListState, state: AppsState) {
    val c = Niko.colors
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var y by remember { mutableFloatStateOf(0f) }
    val label = stringResource(R.string.ap_scroll)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val height = with(LocalDensity.current) { maxHeight.toPx() }
        val thumb = with(LocalDensity.current) { 48.dp.toPx() }
        val bubble = with(LocalDensity.current) { 56.dp.toPx() }
        // the list's place read through derivedStateOf: the thumb is drawn again when it moves, not on every frame
        val total by remember(list) { derivedStateOf { list.layoutInfo.totalItemsCount } }
        val first by remember(list) { derivedStateOf { list.firstVisibleItemIndex } }
        // where the thumb is: the drag's place while dragged, else the list's
        val fraction = if (dragging) (y / height).coerceIn(0f, 1f)
        else if (total <= 1) 0f else (first.toFloat() / (total - 1)).coerceIn(0f, 1f)
        val top = ((height - thumb) * fraction).roundToInt()
        Box(
            Modifier.align(Alignment.TopEnd).width(24.dp).fillMaxHeight()
                .semantics { contentDescription = label }
                .pointerInput(total, height) {
                    detectVerticalDragGestures(
                        onDragStart = { dragging = true; y = it.y },
                        onDragEnd = { dragging = false },
                        onDragCancel = { dragging = false },
                    ) { change, delta ->
                        change.consume()
                        y = (y + delta).coerceIn(0f, height)
                        val to = ((y / height) * (total - 1)).roundToInt().coerceIn(0, maxOf(total - 1, 0))
                        scope.launch { list.scrollToItem(to) }
                    }
                },
        ) {
            Box(
                Modifier.offset { IntOffset(0, top) }.align(Alignment.TopCenter).width(4.dp).height(48.dp)
                    .background(if (dragging) c.ink2 else c.lineStrong, CircleShape),
            )
        }
        if (dragging) {
            val letter = letterAt(state, first)
            if (letter != null) Box(
                Modifier.offset { IntOffset(0, (top + (thumb - bubble) / 2).roundToInt().coerceAtLeast(0)) }
                    .align(Alignment.TopEnd).padding(end = 32.dp).size(56.dp).background(c.ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(letter, NikoType.headline, c.onInk, maxLines = 1)
            }
        }
    }
}

/** The first letter of the app at [index] of the list (its heads and mode row counted), or of the next app. */
private fun letterAt(state: AppsState, index: Int): String? {
    val (pinned, rest) = state.shown
    val rows = buildList<AppRow?> {
        if (!state.forRule) add(null)
        if (pinned.isNotEmpty()) { add(null); addAll(pinned) }
        if (rest.isNotEmpty()) { add(null); addAll(rest) }
    }
    return rows.drop(index.coerceAtLeast(0)).firstOrNull { it != null }?.name?.trim()?.firstOrNull()?.uppercase()
}

@Composable
private fun modeName(mode: AppsMode) = stringResource(
    when (mode) {
        AppsMode.ALL -> R.string.ap_mode_all
        AppsMode.ONLY -> R.string.ap_mode_only
        AppsMode.EXCEPT -> R.string.ap_mode_except
    },
)

/** A sheet over the scrim: a press outside closes it. */
@Composable
private fun SheetOver(title: String, onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Niko.colors
    Box(
        Modifier.fillMaxSize().background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { }.navigationBarsPadding()) {
            Text(title, NikoType.headline, c.ink, Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s2))
            Column(Modifier.padding(bottom = NikoSpace.s6)) { content() }
        }
    }
}

/** Через Niko: all apps, only the chosen ones, all but them. */
@Composable
private fun ModeSheet(mode: AppsMode, onPick: (AppsMode) -> Unit, onDismiss: () -> Unit) {
    SheetOver(stringResource(R.string.ap_mode), onDismiss) {
        AppsMode.entries.forEachIndexed { i, m ->
            ListRow(
                modeName(m), divider = i > 0, side = NikoSpace.s5, role = Role.RadioButton,
                lead = { Radio(m == mode) }, onClick = { onPick(m) },
            )
        }
    }
}

/** ⋮: the system's apps, the choice inverted or cleared, chosen from the list in assets (not for a rule), the clipboard. */
@Composable
private fun Menu(state: AppsState, actions: AppsActions, onDismiss: () -> Unit) {
    val c = Niko.colors
    SheetOver(stringResource(R.string.ap_menu), onDismiss) {
        val source = remember { MutableInteractionSource() }
        ListRow(
            stringResource(R.string.ap_system),
            Modifier.background(if (pressed(source)) c.surface2 else Color.Transparent)
                .toggleable(state.system, source, indication = null, role = Role.Checkbox) { actions.system(it) },
            side = NikoSpace.s5,
            lead = { Checkbox(state.system) },
        )
        MenuRow(R.drawable.ic_l_arrow_left_right, stringResource(R.string.ap_invert)) { onDismiss(); actions.invert() }
        MenuRow(R.drawable.ic_l_x, stringResource(R.string.ap_clear)) { onDismiss(); actions.clear() }
        if (!state.forRule && state.mode != AppsMode.ALL) {
            MenuRow(R.drawable.ic_l_zap, stringResource(R.string.ap_auto)) { onDismiss(); actions.auto() }
        }
        MenuRow(R.drawable.ic_l_upload, stringResource(R.string.ap_export)) { onDismiss(); actions.export() }
        MenuRow(R.drawable.ic_l_download, stringResource(R.string.ap_import)) { onDismiss(); actions.import() }
    }
}

@Composable
private fun MenuRow(@DrawableRes icon: Int, text: String, onClick: () -> Unit) =
    ListRow(text, divider = true, side = NikoSpace.s5, lead = { Icon(icon, Niko.colors.ink2) }, onClick = onClick)

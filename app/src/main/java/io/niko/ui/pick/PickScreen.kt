package io.niko.ui.pick

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoRadius
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.ConfigGroup
import io.niko.ui.home.ConfigRow
import io.niko.ui.home.ConfigsLoader
import io.niko.ui.home.ListRow
import io.niko.ui.home.Tag
import io.niko.ui.home.Text
import io.niko.ui.settings.IconButton

/**
 * Выбор профиля (D-PICK-01): the profiles in their [groups] as the configs' sheet lists them (null: not read yet),
 * [current] the one the choice replaces (a chain's link), [purpose] what it is picked for, the search and its [query].
 */
data class PickState(
    val groups: List<ConfigGroup>? = null,
    val current: Long? = null,
    val purpose: String? = null,
    val searching: Boolean = false,
    val query: String = "",
) {
    /** The groups the list shows: those the search leaves. */
    val shown get() = groups?.let { ConfigsLoader.filter(it, query) }

    /** Where the list opens: the row of [current] among the items (a head, the rows and a gap for each group), or 0. */
    val startIndex: Int
        get() {
            var index = 0
            for (group in groups.orEmpty()) {
                val at = group.profiles.indexOfFirst { it.id == current }
                if (at >= 0) return index + 1 + at
                index += group.profiles.size + 2
            }
            return 0
        }
}

/** The page: what the profile is for under the title, the search, then the groups; a row picks and closes. */
@Composable
fun PickScreen(state: PickState, onBack: () -> Unit, onSearch: (Boolean) -> Unit, onQuery: (String) -> Unit, onPick: (Long) -> Unit) {
    val c = Niko.colors
    BackHandler(enabled = state.searching) { onSearch(false) }
    Column(Modifier.fillMaxSize().background(c.surface0).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
        ) {
            IconButton(R.drawable.ic_l_arrow_left, stringResource(R.string.screen_back), onClick = onBack)
            Column(Modifier.weight(1f).padding(horizontal = NikoSpace.s2)) {
                Text(stringResource(R.string.pick_title), NikoType.headline, c.ink, maxLines = 1)
                if (state.purpose != null) Text(state.purpose, NikoType.caption, c.ink2, maxLines = 1)
            }
            IconButton(R.drawable.ic_l_search, stringResource(R.string.home_search)) { onSearch(!state.searching) }
        }
        if (state.searching) {
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Box(
                Modifier.fillMaxWidth().padding(horizontal = NikoSpace.s5, vertical = NikoSpace.s2).heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(NikoRadius.sm)).background(c.surface2).padding(horizontal = NikoSpace.s4),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (state.query.isEmpty()) Text(stringResource(R.string.home_search_hint), NikoType.body, c.ink2, maxLines = 1)
                BasicTextField(
                    state.query, onQuery, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                    textStyle = NikoType.body.copy(color = c.ink), cursorBrush = SolidColor(c.ink),
                )
            }
        }
        val shown = state.shown
        when {
            shown == null -> Unit
            shown.isEmpty() -> Text(
                stringResource(if (state.query.isBlank()) R.string.pick_none else R.string.home_search_none),
                NikoType.bodySm, c.ink2, Modifier.padding(NikoSpace.s5),
            )
            else -> {
                // opened on the profile the choice replaces, as the old list scrolled to it
                val list = rememberLazyListState(initialFirstVisibleItemIndex = if (state.query.isBlank()) state.startIndex else 0)
                LazyColumn(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding(), state = list) {
                    for (group in shown) {
                        item(key = "g${group.id}") {
                            Text(
                                (group.name ?: stringResource(R.string.home_ungrouped)).uppercase(), NikoType.label, c.ink2,
                                Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s1),
                                maxLines = 1,
                            )
                        }
                        items(group.profiles, key = { it.id }) { row ->
                            PickRow(row, row.id == state.current, divider = row !== group.profiles.first()) { onPick(row.id) }
                        }
                        item(key = "s${group.id}") { Spacer(Modifier.height(NikoSpace.s4)) }
                    }
                }
            }
        }
    }
}

/** m-row without the choice's radio: the name (the current one in bold), its tags, the last ping in mono. */
@Composable
private fun PickRow(row: ConfigRow, current: Boolean, divider: Boolean, onClick: () -> Unit) {
    val c = Niko.colors
    val ping = when {
        row.unreachable -> stringResource(R.string.home_unreachable)
        row.pingMs != null -> "${row.pingMs} ms"
        else -> "—"
    }
    ListRow(
        row.name,
        Modifier.semantics { selected = current },
        titleBold = current,
        divider = divider,
        side = NikoSpace.s5,
        sub = { row.tags.forEach { Tag(it) } },
        trail = { Text(ping, NikoType.code, if (row.unreachable) c.danger else c.ink2, maxLines = 1) },
        onClick = onClick,
    )
}

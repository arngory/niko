package io.niko.ui.route

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.ListRow
import io.niko.ui.home.Section
import io.niko.ui.home.Sub
import io.niko.ui.home.Text
import io.niko.ui.settings.IconButton
import io.niko.ui.settings.ListPage

/** What P-Lists asks of its fragment. */
interface ListsActions {
    fun back()
    /** a .db or a root certificate .pem from a file */
    fun import()
    /** geoip.db and geosite.db, each from the latest release of the chosen source */
    fun updateAll()
    fun update(name: String)
    /** a swipe: an own file goes, with an undo */
    fun delete(name: String)
}

/**
 * Наборы правил (P-Lists, D-RULES-03, D-RULES-04): the geo bases with their versions and update, then the own files
 * (a .db, the root certificate), a swipe deleting one; the source and the interval are the settings'.
 */
@Composable
fun ListsScreen(state: ListsState, actions: ListsActions) {
    val c = Niko.colors
    ListPage(
        stringResource(R.string.rt_lists), stringResource(R.string.screen_back), actions::back,
        actions = {
            IconButton(R.drawable.ic_l_folder_plus, stringResource(R.string.action_import_file), onClick = actions::import)
            IconButton(R.drawable.ic_l_refresh_cw, stringResource(R.string.rt_update_all), onClick = actions::updateAll)
        },
    ) {
        Section(stringResource(R.string.rt_geo)) {
            state.rows.filter { it.kind == AssetKind.GEO }.forEachIndexed { i, row ->
                ListRow(
                    row.name,
                    divider = i > 0,
                    sub = { Sub(row.version, mono = true) },
                    trail = {
                        if (row.updating) {
                            Text(stringResource(R.string.rt_updating), NikoType.bodySm, c.ink2, maxLines = 1)
                        } else {
                            IconButton(
                                R.drawable.ic_l_refresh_cw, stringResource(R.string.rt_update_one, row.name),
                                muted = true, small = true,
                            ) { actions.update(row.name) }
                        }
                    },
                )
            }
        }
        Section(stringResource(R.string.rt_own)) {
            val own = state.rows.filter { it.kind != AssetKind.GEO }
            if (own.isEmpty()) {
                Text(stringResource(R.string.rt_own_none), NikoType.bodySm, c.ink2, Modifier.padding(NikoSpace.s4))
            }
            val delete = stringResource(R.string.rt_delete)
            own.forEachIndexed { i, row ->
                key(row.name) {
                    SwipeToDelete(delete, enabled = true, onDelete = { actions.delete(row.name) }) {
                        ListRow(
                            row.name,
                            divider = i > 0,
                            sub = { Sub(if (row.kind == AssetKind.CA) stringResource(R.string.rt_ca) else row.version, mono = row.kind != AssetKind.CA) },
                        )
                    }
                }
            }
        }
        Text(stringResource(R.string.rt_lists_source), NikoType.caption, c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
    }
}

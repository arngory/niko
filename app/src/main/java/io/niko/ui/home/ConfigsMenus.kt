package io.niko.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType

/** What the menus of the configs' sheet ask (a group's ⋮, a profile's sheet): the old list's actions. */
interface ConfigsMenuActions {
    /** a group's test, its results or traffic cleared, its failed or repeated profiles deleted after a question */
    fun group(groupId: Long, action: GroupAction)
    /** the profile's link or configuration, shown as a QR code, copied or written to a file */
    fun share(id: Long, action: ShareAction)
    /** the profile deleted at once, with an undo offered for a while */
    fun delete(id: Long)
    /** the profile changes places with [other], next to it in its group */
    fun move(id: Long, other: Long)
}

/** m-sheet over the configs' sheet: a title, then rows; the scrim or Back closes it. */
@Composable
private fun MenuSheet(title: String, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Niko.colors
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { }.navigationBarsPadding()) {
            Text(title, NikoType.headline, c.ink, Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s2), maxLines = 1)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = NikoSpace.s6), content = content)
        }
    }
}

@Composable
private fun MenuRow(icon: Int, title: String, divider: Boolean, danger: Boolean = false, onClick: () -> Unit) {
    val c = Niko.colors
    ListRow(title, divider = divider, side = NikoSpace.s5, lead = { Icon(icon, if (danger) c.danger else c.ink2) }, onClick = onClick)
}

/** A group's ⋮ (D-PROFILE-06): its tests (not while one runs), what they left cleared, what to delete. */
@Composable
internal fun GroupMenu(name: String, testing: Boolean, onPick: (GroupAction) -> Unit, onDismiss: () -> Unit) {
    MenuSheet(name, onDismiss) {
        ConfigsLoader.groupMenu(testing).forEachIndexed { i, action ->
            val (icon, title) = when (action) {
                GroupAction.TCP_PING -> R.drawable.ic_l_activity to stringResource(R.string.connection_test_tcp_ping)
                GroupAction.URL_TEST -> R.drawable.ic_l_zap to stringResource(R.string.connection_test_url_test)
                GroupAction.CLEAR_RESULTS -> R.drawable.ic_l_x to stringResource(R.string.connection_test_clear_results)
                GroupAction.DELETE_UNAVAILABLE -> R.drawable.ic_l_trash_2 to stringResource(R.string.connection_test_delete_unavailable)
                GroupAction.REMOVE_DUPLICATES -> R.drawable.ic_l_copy_minus to stringResource(R.string.remove_duplicate)
                GroupAction.CLEAR_TRAFFIC -> R.drawable.ic_l_rotate_ccw to stringResource(R.string.cfg_clear_traffic)
            }
            val danger = action == GroupAction.DELETE_UNAVAILABLE || action == GroupAction.REMOVE_DUPLICATES
            MenuRow(icon, title, divider = i > 0, danger = danger) { onPick(action) }
        }
    }
}

/** A profile's sheet (a long press on its row; D-PROFILE-07, 08): [actions] of ConfigsLoader.profileMenu. */
@Composable
internal fun ProfileMenu(name: String, actions: List<ProfileAction>, onPick: (ProfileAction) -> Unit, onDismiss: () -> Unit) {
    MenuSheet(name, onDismiss) {
        actions.forEachIndexed { i, action ->
            val (icon, title) = when (action) {
                ProfileAction.EDIT -> R.drawable.ic_l_pencil to stringResource(R.string.cfg_edit)
                ProfileAction.SHARE -> R.drawable.ic_l_share_2 to stringResource(R.string.share)
                ProfileAction.MOVE_UP -> R.drawable.ic_l_arrow_up to stringResource(R.string.pe_move_up)
                ProfileAction.MOVE_DOWN -> R.drawable.ic_l_arrow_down to stringResource(R.string.pe_move_down)
                ProfileAction.DELETE -> R.drawable.ic_l_trash_2 to stringResource(R.string.delete)
            }
            MenuRow(icon, title, divider = i > 0, danger = action == ProfileAction.DELETE) { onPick(action) }
        }
    }
}

/** «Поделиться» (D-PROFILE-07): [actions] of ConfigsLoader.shareMenu. */
@Composable
internal fun ShareMenu(name: String, actions: List<ShareAction>, onPick: (ShareAction) -> Unit, onDismiss: () -> Unit) {
    MenuSheet(name, onDismiss) {
        actions.forEachIndexed { i, action ->
            val (icon, title) = when (action) {
                ShareAction.QR -> R.drawable.ic_l_share_2 to stringResource(R.string.share_qr_nfc)
                ShareAction.QR_SN -> R.drawable.ic_l_share_2 to stringResource(R.string.cfg_share_qr_sn)
                ShareAction.LINK -> R.drawable.ic_l_link to stringResource(R.string.cfg_share_link)
                ShareAction.LINK_SN -> R.drawable.ic_l_link to stringResource(R.string.cfg_share_link_sn)
                ShareAction.CONFIG_CLIPBOARD -> R.drawable.ic_l_braces to stringResource(R.string.cfg_share_config)
                ShareAction.CONFIG_FILE -> R.drawable.ic_l_file_up to stringResource(R.string.cfg_share_config_file)
            }
            MenuRow(icon, title, divider = i > 0) { onPick(action) }
        }
    }
}

/** What a failed test said, in full, to select and copy (the old list's tap on a failed status). */
@Composable
internal fun ErrorSheet(name: String, error: String, onDismiss: () -> Unit) {
    val c = Niko.colors
    MenuSheet(name, onDismiss) {
        Text(
            stringResource(R.string.cfg_error), NikoType.label, c.danger,
            Modifier.padding(horizontal = NikoSpace.s5).padding(bottom = NikoSpace.s2), maxLines = 1,
        )
        SelectionContainer(Modifier.fillMaxWidth().padding(horizontal = NikoSpace.s5)) {
            Text(error, NikoType.code, c.ink)
        }
    }
}

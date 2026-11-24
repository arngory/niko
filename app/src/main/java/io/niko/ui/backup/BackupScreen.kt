package io.niko.ui.backup

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.Icon
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Section
import io.niko.ui.home.Sheet
import io.niko.ui.home.Sub
import io.niko.ui.home.Text
import io.niko.ui.route.CheckRow
import io.niko.ui.route.PrimaryButton
import io.niko.ui.settings.ListPage

/**
 * Резервная копия (D-SET-19, D-SET-32): [parts] the parts a backup holds; [restore] the file picked to restore, with
 * the parts it holds ([BackupRestore.available]) and those chosen; [restoring] the restore running (the app restarts
 * after it).
 */
data class BackupState(
    val parts: Set<BackupPart> = BackupPart.entries.toSet(),
    val restore: BackupRestore? = null,
    val restoring: Boolean = false,
)

/** A file to restore from: the parts it holds and those the user keeps ticked. */
data class BackupRestore(val available: Set<BackupPart>, val chosen: Set<BackupPart> = available)

/** What the backup page asks of its fragment. */
interface BackupActions {
    fun back()
    fun part(part: BackupPart, on: Boolean)
    /** the backup to a file the user picks */
    fun save()
    /** the backup through the system's share sheet */
    fun share()
    /** a file to restore from */
    fun pick()
    fun restorePart(part: BackupPart, on: Boolean)
    fun restore()
    fun cancelRestore()
    /** the default settings, after a question; nodes and profiles stay */
    fun reset()
}

@Composable
private fun partName(part: BackupPart) = stringResource(
    when (part) {
        BackupPart.PROFILES -> R.string.backup_groups_and_configurations
        BackupPart.RULES -> R.string.backup_rules
        BackupPart.SETTINGS -> R.string.backup_settings
    },
)

/**
 * The page: what to keep, then save, share or restore; the note on rules without profiles; the reset of the settings
 * apart. A file picked opens [RestoreSheet] over it.
 */
@Composable
fun BackupScreen(state: BackupState, actions: BackupActions) {
    val c = Niko.colors
    Box(Modifier.fillMaxSize()) {
        ListPage(stringResource(R.string.bk_title), stringResource(R.string.screen_back), actions::back) {
            Section(stringResource(R.string.bk_parts)) {
                BackupPart.entries.forEachIndexed { i, part ->
                    CheckRow(partName(part), part in state.parts, { actions.part(part, it) }, divider = i > 0)
                }
            }
            ListGroup {
                val none = state.parts.isEmpty()
                ListRow(
                    stringResource(R.string.bk_save), lead = { Icon(R.drawable.ic_l_download, c.ink2) },
                    onClick = if (none) null else actions::save,
                )
                ListRow(
                    stringResource(R.string.share), divider = true, lead = { Icon(R.drawable.ic_l_share_2, c.ink2) },
                    onClick = if (none) null else actions::share,
                )
                ListRow(
                    stringResource(R.string.bk_restore), divider = true, lead = { Icon(R.drawable.ic_l_upload, c.ink2) },
                    onClick = actions::pick,
                )
            }
            Text(stringResource(R.string.backup_summary), NikoType.bodySm, c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
            ListGroup {
                ListRow(
                    stringResource(R.string.bk_reset),
                    sub = { Sub(stringResource(R.string.bk_reset_sub)) },
                    lead = { Icon(R.drawable.ic_l_rotate_ccw, c.danger) },
                    onClick = actions::reset,
                )
            }
        }
        state.restore?.let { RestoreSheet(it, state.restoring, actions) }
    }
}

/** Восстановить: the file's parts, ticked; the existing data they replace; then the restore and the restart. */
@Composable
private fun RestoreSheet(restore: BackupRestore, restoring: Boolean, actions: BackupActions) {
    val c = Niko.colors
    BackHandler(enabled = !restoring) { actions.cancelRestore() }
    Box(
        Modifier.fillMaxSize().background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null) { if (!restoring) actions.cancelRestore() },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { }.navigationBarsPadding()) {
            Column(
                Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s6),
                verticalArrangement = Arrangement.spacedBy(NikoSpace.s4),
            ) {
                Text(stringResource(R.string.bk_restore_title), NikoType.headline, c.ink)
                Text(stringResource(R.string.backup_import_summary), NikoType.bodySm, c.ink2)
                ListGroup {
                    restore.available.sortedBy { it.ordinal }.forEachIndexed { i, part ->
                        CheckRow(partName(part), part in restore.chosen, { actions.restorePart(part, it) }, divider = i > 0)
                    }
                }
                PrimaryButton(
                    stringResource(if (restoring) R.string.backup_importing else R.string.bk_restore_go),
                    actions::restore, Modifier.fillMaxWidth(),
                    enabled = !restoring && restore.chosen.isNotEmpty(),
                )
            }
        }
    }
}

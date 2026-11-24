package io.niko.ui.tools

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.Icon
import io.niko.ui.home.ListRow
import io.niko.ui.home.Sheet
import io.niko.ui.home.Sub
import io.niko.ui.home.Text
import io.niko.ui.home.Trail
import io.niko.ui.route.PrimaryButton
import io.niko.ui.settings.Field
import io.niko.ui.settings.IconButton

/**
 * Панель sing-box (D-TOOLS-02): the panel's [url] as stored; [menu] its ⋮ open; [editing] the address being typed in
 * the sheet over it, null while none is.
 */
data class PanelState(val url: String, val menu: Boolean = false, val editing: String? = null)

/** What the panel's page asks of its fragment. */
interface PanelActions {
    fun back()
    fun menu(open: Boolean)
    /** the address sheet opened with the stored address, typed in, closed without saving */
    fun edit(text: String?)
    /** the typed address stored and loaded */
    fun save()
    fun copySecret()
}

/** The page: the bar, the web panel ([content]) under it, the ⋮ sheet and the address sheet over both. */
@Composable
fun PanelScreen(state: PanelState, actions: PanelActions, content: @Composable () -> Unit) {
    val c = Niko.colors
    Box(Modifier.fillMaxSize().background(c.surface0)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
            ) {
                IconButton(R.drawable.ic_l_arrow_left, stringResource(R.string.screen_back), onClick = actions::back)
                Text(stringResource(R.string.menu_dashboard), NikoType.headline, c.ink, Modifier.weight(1f).padding(horizontal = NikoSpace.s2), maxLines = 1)
                IconButton(R.drawable.ic_l_ellipsis_vertical, stringResource(R.string.more_options)) { actions.menu(true) }
            }
            Box(Modifier.weight(1f).fillMaxWidth().navigationBarsPadding()) { content() }
        }
        val editing = state.editing
        when {
            editing != null -> Over({ actions.edit(null) }) {
                Column(
                    Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s6),
                    verticalArrangement = Arrangement.spacedBy(NikoSpace.s4),
                ) {
                    Text(stringResource(R.string.panel_url), NikoType.headline, c.ink)
                    Field(stringResource(R.string.panel_url_label), editing, { actions.edit(it) }, mono = true, onGo = actions::save)
                    PrimaryButton(stringResource(R.string.save), actions::save, Modifier.fillMaxWidth(), enabled = editing.isNotBlank())
                }
            }
            state.menu -> Over({ actions.menu(false) }) {
                Text(
                    stringResource(R.string.menu_dashboard), NikoType.headline, c.ink,
                    Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s2),
                )
                Column(Modifier.padding(bottom = NikoSpace.s6)) {
                    ListRow(
                        stringResource(R.string.panel_url), side = NikoSpace.s5,
                        sub = { Sub(state.url, mono = true) },
                        lead = { Icon(R.drawable.ic_l_link, c.ink2) },
                        trail = { Trail(null) },
                        onClick = { actions.edit(state.url) },
                    )
                    ListRow(
                        stringResource(R.string.copy_clash_api_secret), side = NikoSpace.s5, divider = true,
                        sub = { Sub(stringResource(R.string.panel_secret_sub)) },
                        lead = { Icon(R.drawable.ic_l_copy, c.ink2) },
                        onClick = actions::copySecret,
                    )
                }
            }
        }
    }
}

/** A sheet over the scrim, over the keyboard: a press outside or back closes it. */
@Composable
private fun Over(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val c = Niko.colors
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { }.navigationBarsPadding().imePadding()) {
            content()
        }
    }
}

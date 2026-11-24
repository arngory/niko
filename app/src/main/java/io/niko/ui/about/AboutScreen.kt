package io.niko.ui.about

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoOpacity
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.Icon
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Section
import io.niko.ui.home.Sub
import io.niko.ui.home.Text
import io.niko.ui.home.Trail
import io.niko.ui.home.pressed
import io.niko.ui.settings.ListPage
import io.niko.ui.settings.SwitchRow

/** The update row: nothing asked yet, the check running, none newer, a newer one ([version]) to install, or why not. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object None : UpdateState
    data class Found(val version: String) : UpdateState
    data class Failed(val reason: String) : UpdateState
}

/** A plugin as the list shows it: its id and provider, its package, its version, whether its signer is trusted. */
class PluginRow(val id: String, val provider: String, val packageName: String, val version: String, val trusted: Boolean)

/**
 * P-About (D-SET-12): [version] the app's, [core] sing-box's; [update] the update row, [preview] the channel of
 * pre-releases; the phone's limits on the background (M-SET-02): [batteryOptimized] the system's optimization on,
 * [restricted] battery use "Restricted", [vendor] a vendor's manager of its own; [plugins] (M-SET-03).
 */
data class AboutState(
    val version: String,
    val core: String,
    val update: UpdateState = UpdateState.Idle,
    val preview: Boolean = false,
    val batteryOptimized: Boolean = false,
    val restricted: Boolean = false,
    val vendor: Boolean = false,
    val plugins: List<PluginRow> = emptyList(),
)

/** What P-About asks of its fragment. */
interface AboutActions {
    fun back()
    /** the releases' page */
    fun releases()
    /** the check, or the offer again of the update found */
    fun update()
    fun preview(on: Boolean)
    fun battery()
    fun restricted()
    fun vendor()
    fun plugin(row: PluginRow)
    fun licenses()
    /** Niko's own LICENSE: its copyright and the GPL-3.0 */
    fun license()
    fun source()
    fun upstream()
    fun donate()
}

/**
 * О программе (P-About, D-SET-12): the name and version, the update with its channel, the limits on the background
 * (M-SET-02), the plugins (M-SET-03), the project (D-SET-34): licenses, source, Niko's license, the project it forks,
 * the support of the project.
 */
@Composable
fun AboutScreen(state: AboutState, actions: AboutActions) {
    val c = Niko.colors
    ListPage(stringResource(R.string.ab_title), stringResource(R.string.screen_back), actions::back) {
        Column(
            Modifier.fillMaxWidth().clip(CircleShape).clickable(role = Role.Button, onClick = actions::releases)
                .padding(vertical = NikoSpace.s2),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(NikoSpace.s1),
        ) {
            Text("niko", NikoType.title.copy(textAlign = TextAlign.Center), c.ink, maxLines = 1)
            Text(state.version, NikoType.code.copy(textAlign = TextAlign.Center), c.ink2, maxLines = 1)
            Text("sing-box " + state.core, NikoType.code.copy(textAlign = TextAlign.Center), c.ink2, maxLines = 1)
        }
        ListGroup {
            UpdateRow(state.update, actions::update)
            SwitchRow(stringResource(R.string.ab_preview), state.preview, actions::preview, divider = true)
        }
        Section(stringResource(R.string.ab_background)) {
            ListRow(
                stringResource(R.string.ab_battery),
                trail = { Value(stringResource(if (state.batteryOptimized) R.string.ab_battery_on else R.string.ab_battery_off), state.batteryOptimized) },
                onClick = if (state.batteryOptimized) actions::battery else null,
            )
            ListRow(
                stringResource(R.string.ab_restricted), divider = true,
                trail = { Value(stringResource(if (state.restricted) R.string.ab_restricted_yes else R.string.ab_restricted_no), state.restricted) },
                onClick = if (state.restricted) actions::restricted else null,
            )
            if (state.vendor) {
                ListRow(stringResource(R.string.ab_vendor), divider = true, trail = { Trail(null) }, onClick = actions::vendor)
            }
        }
        if (state.plugins.isNotEmpty()) {
            Section(stringResource(R.string.ab_plugins)) {
                state.plugins.forEachIndexed { i, p ->
                    ListRow(
                        "${p.id} (${p.provider})", divider = i > 0,
                        sub = {
                            Sub(
                                "v${p.version} · " +
                                    stringResource(if (p.trusted) R.string.plugin_trusted else R.string.plugin_not_trusted),
                            )
                        },
                        trail = { Trail(null) },
                        onClick = { actions.plugin(p) },
                    )
                }
            }
        }
        ListGroup {
            ProjectRow(R.drawable.ic_l_scroll_text, stringResource(R.string.licenses), false, actions::licenses)
            ProjectRow(R.drawable.ic_l_link, stringResource(R.string.ab_source), true, actions::source)
            ProjectRow(R.drawable.ic_l_file_badge, stringResource(R.string.ab_license), true, actions::license)
            ProjectRow(R.drawable.ic_l_split, stringResource(R.string.ab_upstream), true, actions::upstream)
            ProjectRow(R.drawable.ic_l_coffee, stringResource(R.string.donate), true, actions::donate)
        }
    }
}

/** A row's value, and its chevron where a press does something ([pressed]: the limit is there to lift). */
@Composable
private fun Value(text: String, pressed: Boolean) =
    if (pressed) Trail(text) else Text(text, NikoType.bodySm, Niko.colors.ink2, maxLines = 1)

@Composable
private fun ProjectRow(@DrawableRes icon: Int, title: String, divider: Boolean, onClick: () -> Unit) =
    ListRow(title, divider = divider, titleLines = 2, lead = { Icon(icon, Niko.colors.ink2) }, trail = { Trail(null) }, onClick = onClick)

/** Обновление: what the check found, the button that checks or installs at its end. */
@Composable
private fun UpdateRow(update: UpdateState, onClick: () -> Unit) {
    val sub = when (update) {
        UpdateState.Idle -> null
        UpdateState.Checking -> stringResource(R.string.ab_update_checking)
        UpdateState.None -> stringResource(R.string.ab_update_none)
        is UpdateState.Found -> update.version
        is UpdateState.Failed -> update.reason
    }
    val button = stringResource(if (update is UpdateState.Found) R.string.ab_update_install else R.string.ab_update_check)
    ListRow(
        stringResource(R.string.ab_update),
        sub = sub?.let { { Sub(it, mono = update is UpdateState.Found) } },
        trail = { TonalButton(button, enabled = update != UpdateState.Checking, onClick = onClick) },
    )
}

/** m-btn m-btn--tonal: its text on surface-2, the width of its text; surface-3 pressed. */
@Composable
private fun TonalButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.height(NikoSize.control).clip(CircleShape).alpha(if (enabled) 1f else NikoOpacity.DISABLED)
            .background(if (pressed(source)) c.surface3 else c.surface2)
            .clickable(source, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = NikoSpace.s6),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, NikoType.body.copy(fontWeight = NikoType.headline.fontWeight), c.ink, maxLines = 1)
    }
}

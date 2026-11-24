package io.niko.ui.node

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoRadius
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.Icon
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Radio
import io.niko.ui.home.Section
import io.niko.ui.home.Sheet
import io.niko.ui.home.Sub
import io.niko.ui.home.Text
import io.niko.ui.home.Trail
import io.niko.ui.route.DangerBanner
import io.niko.ui.route.PrimaryButton
import io.niko.ui.settings.Field
import io.niko.ui.settings.IconButton
import io.niko.ui.settings.ListPage
import io.niko.ui.settings.SwitchRow

/** Another of the user's devices on the node: its name and the name (or address) it answers to. */
data class LanDevice(val name: String, val address: String)

/** A Wi-Fi the user trusts; [current] the one the phone is in now. */
data class LanWifi(val name: String, val key: String, val current: Boolean = false)

/** What stands between the phone and the Wi-Fi's name, or what the Wi-Fi is. */
enum class LanWifiNote { LOCATION, LOCATION_BACKGROUND, LOCATION_OFF, NOT_WIFI, THIS_TRUSTED }

/** What the trusted Wi-Fi section shows under its list: the [note], «Разрешить» with it, and the Wi-Fi to trust. */
data class LanWifiState(val note: LanWifiNote? = null, val canAllow: Boolean = false, val canTrust: Boolean = false) {
    companion object {
        /**
         * As the old dialog worked it out. The name is read with the location permission while the app is open, «all
         * the time» with it closed, and location on; [inWifi] the phone is in a Wi-Fi, [hasSsid] its name is known,
         * [trusted] it is among the trusted ones by the way they are known ([bySsid]).
         */
        fun of(bySsid: Boolean, inWifi: Boolean, hasSsid: Boolean, trusted: Boolean, location: Boolean, background: Boolean): LanWifiState {
            val missing = when {
                !bySsid -> null
                !location -> LanWifiNote.LOCATION
                !background -> LanWifiNote.LOCATION_BACKGROUND
                inWifi && !hasSsid -> LanWifiNote.LOCATION_OFF
                else -> null
            }
            return LanWifiState(
                note = missing ?: when {
                    !inWifi -> LanWifiNote.NOT_WIFI
                    trusted -> LanWifiNote.THIS_TRUSTED
                    else -> null
                },
                canAllow = missing == LanWifiNote.LOCATION || missing == LanWifiNote.LOCATION_BACKGROUND,
                canTrust = inWifi && !trusted && (!bySsid || hasSsid),
            )
        }
    }
}

/**
 * Локальная сеть узла (D-NODE-08, D-NODE-13, D-NODE-14): [show] this device shown to the user's others; [ports] the
 * ports they reach here; [notes] what the node says about the network (not offered, waiting, the relay's rate);
 * [devices] the others, null while this device is not shown; then the direct link in a trusted Wi-Fi. [portsEdit] the
 * ports being typed in their sheet with [portsError]; [byOpen] the sheet of the ways to know a Wi-Fi.
 */
data class LanState(
    val node: String = "",
    val show: Boolean = false,
    val ports: String = "",
    val notes: List<String> = emptyList(),
    val devices: List<LanDevice>? = null,
    val direct: Boolean = false,
    val bySsid: Boolean = false,
    val trusted: List<LanWifi> = emptyList(),
    val wifi: LanWifiState = LanWifiState(),
    val wifiName: String? = null,
    val portsEdit: String? = null,
    val portsError: String? = null,
    val byOpen: Boolean = false,
)

/** What the local network's page asks of its activity. */
interface LanActions {
    fun back()
    fun show(on: Boolean)
    /** the ports' sheet opened with the stored ones, typed in, closed (null) */
    fun portsEdit(text: String?)
    fun portsSave()
    fun direct(on: Boolean)
    fun byOpen(open: Boolean)
    fun by(ssid: Boolean)
    fun forget(key: String)
    /** the location permission asked, the step that is missing */
    fun allow()
    /** the Wi-Fi the phone is in, trusted */
    fun trust()
}

@Composable
private fun noteText(note: LanWifiNote) = stringResource(
    when (note) {
        LanWifiNote.LOCATION -> R.string.raycontrol_lan_location
        LanWifiNote.LOCATION_BACKGROUND -> R.string.raycontrol_lan_location_background
        LanWifiNote.LOCATION_OFF -> R.string.raycontrol_lan_location_off
        LanWifiNote.NOT_WIFI -> R.string.raycontrol_lan_not_wifi
        LanWifiNote.THIS_TRUSTED -> R.string.raycontrol_lan_this_trusted
    },
)

/** The page: this device and its ports, the other devices, the trusted Wi-Fi; the sheets over it. */
@Composable
fun LanScreen(state: LanState, actions: LanActions) {
    val c = Niko.colors
    Box(Modifier.fillMaxSize()) {
        ListPage(stringResource(R.string.raycontrol_lan), stringResource(R.string.screen_back), actions::back) {
            ListGroup {
                SwitchRow(stringResource(R.string.lan_show), state.show, actions::show, sub = stringResource(R.string.lan_show_sub))
                ListRow(
                    stringResource(R.string.lan_ports), divider = true,
                    sub = { Sub(stringResource(R.string.lan_ports_sub)) },
                    trail = { Trail(state.ports.ifBlank { stringResource(R.string.lan_ports_none) }, mono = state.ports.isNotBlank()) },
                    onClick = { actions.portsEdit(state.ports) },
                )
            }
            Text(
                (listOf(stringResource(R.string.raycontrol_lan_help)) + state.notes).joinToString("\n\n"),
                NikoType.bodySm, c.ink2, Modifier.padding(horizontal = NikoSpace.s4),
            )
            state.devices?.let { devices ->
                Section(stringResource(R.string.lan_devices, devices.size)) {
                    if (devices.isEmpty()) ListRow(stringResource(R.string.raycontrol_lan_none), titleLines = 2)
                    devices.forEachIndexed { i, device ->
                        ListRow(device.name, divider = i > 0, sub = { Sub(device.address, mono = true) })
                    }
                }
            }
            Section(stringResource(R.string.lan_wifi)) {
                SwitchRow(
                    stringResource(R.string.raycontrol_lan_direct), state.direct, actions::direct,
                    sub = stringResource(R.string.lan_direct_sub),
                )
                if (state.direct) {
                    ListRow(
                        stringResource(R.string.lan_by), divider = true,
                        trail = { Trail(stringResource(if (state.bySsid) R.string.lan_by_ssid else R.string.lan_by_fingerprint)) },
                        onClick = { actions.byOpen(true) },
                    )
                    if (state.trusted.isEmpty()) ListRow(stringResource(R.string.raycontrol_lan_trusted_none), divider = true)
                    for (wifi in state.trusted) {
                        ListRow(
                            wifi.name, divider = true,
                            sub = if (wifi.current) ({ Sub(stringResource(R.string.lan_this_wifi)) }) else null,
                            trail = {
                                IconButton(
                                    R.drawable.ic_l_x, stringResource(R.string.raycontrol_lan_forget, wifi.name),
                                    muted = true, small = true,
                                ) { actions.forget(wifi.key) }
                            },
                        )
                    }
                }
            }
            if (state.direct) {
                state.wifi.note?.let { note -> WifiNote(noteText(note), if (state.wifi.canAllow) actions::allow else null) }
                if (state.wifi.canTrust) ListGroup {
                    ListRow(
                        stringResource(R.string.raycontrol_lan_trust),
                        sub = state.wifiName?.let { { Sub(it) } },
                        lead = { Icon(R.drawable.ic_l_plus, c.ink2) },
                        onClick = actions::trust,
                    )
                }
            }
        }
        val editing = state.portsEdit
        when {
            editing != null -> Over({ actions.portsEdit(null) }) {
                Column(
                    Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s6),
                    verticalArrangement = Arrangement.spacedBy(NikoSpace.s4),
                ) {
                    Text(stringResource(R.string.lan_ports), NikoType.headline, c.ink)
                    Field(stringResource(R.string.raycontrol_lan_ports), editing, { actions.portsEdit(it) }, mono = true, onGo = actions::portsSave)
                    state.portsError?.let { DangerBanner(it) }
                    Text(stringResource(R.string.raycontrol_lan_ports_help), NikoType.bodySm, c.ink2)
                    PrimaryButton(stringResource(R.string.save), actions::portsSave, Modifier.fillMaxWidth())
                }
            }
            state.byOpen -> Over({ actions.byOpen(false) }) {
                Text(
                    stringResource(R.string.lan_by), NikoType.headline, c.ink,
                    Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s2),
                )
                Column(Modifier.padding(bottom = NikoSpace.s6)) {
                    ListRow(
                        stringResource(R.string.raycontrol_lan_direct_by_fingerprint), side = NikoSpace.s5, role = Role.RadioButton,
                        lead = { Radio(!state.bySsid) }, onClick = { actions.by(false) },
                    )
                    ListRow(
                        stringResource(R.string.raycontrol_lan_direct_by_ssid), side = NikoSpace.s5, divider = true, role = Role.RadioButton,
                        lead = { Radio(state.bySsid) }, onClick = { actions.by(true) },
                    )
                }
            }
        }
    }
}

/** m-banner: what the Wi-Fi's name needs, with «Разрешить» where a permission is the step. */
@Composable
private fun WifiNote(text: String, onAllow: (() -> Unit)?) {
    val c = Niko.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(NikoRadius.md)).background(c.surface1).padding(NikoSpace.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NikoSpace.s3),
    ) {
        Icon(R.drawable.ic_l_wifi, c.ink2)
        Text(text, NikoType.bodySm, c.ink, Modifier.weight(1f))
        if (onAllow != null) Text(
            stringResource(R.string.raycontrol_lan_allow), NikoType.bodySm.copy(fontWeight = NikoType.headline.fontWeight), c.ink,
            Modifier.clip(RoundedCornerShape(NikoRadius.sm)).clickable(role = Role.Button, onClick = onAllow).padding(NikoSpace.s2),
        )
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

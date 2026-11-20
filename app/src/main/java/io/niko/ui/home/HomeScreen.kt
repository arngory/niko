package io.niko.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.niko.R
import io.niko.bg.BaseService
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import kotlinx.coroutines.delay

/**
 * The main screen drawn from the canvas: Главная · Простой (P-Simple) and Главная · Расширенный (P-Advanced), the mode's
 * sheet (P-ModeSheet); D-MAIN, D-NAV. A function of [state] alone, so that Paparazzi draws it without the service;
 * [clock] gives the time online its now (a fixed one in the snapshots, where [ticking] is off); [sheet] opens it with
 * the mode's sheet up (a snapshot's).
 */
@Composable
fun HomeScreen(
    state: HomeState,
    view: HomeView,
    actions: HomeActions,
    clock: () -> Long = System::currentTimeMillis,
    ticking: Boolean = true,
    sheet: Boolean = false,
) {
    val c = Niko.colors
    var modeSheet by rememberSaveable { mutableStateOf(sheet) }
    var now by remember { mutableLongStateOf(clock()) }
    LaunchedEffect(state.service, ticking) {
        while (ticking && state.service == BaseService.State.Connected) {
            now = clock()
            delay(1000)
        }
    }
    Box(Modifier.fillMaxSize().background(c.surface0)) {
        Column(Modifier.fillMaxSize()) {
            TopBar(view, actions::view, Modifier.statusBarsPadding())
            when (view) {
                HomeView.Simple -> Simple(state, now, actions, Modifier.weight(1f)) { modeSheet = true }
                HomeView.Advanced -> Advanced(state, now, actions, Modifier.weight(1f)) { modeSheet = true }
            }
        }
        if (modeSheet) {
            BackHandler { modeSheet = false }
            ModeSheet(state.mode, onPick = { vpn ->
                modeSheet = false
                if (vpn != state.mode.vpn) actions.mode(vpn)
            }) { modeSheet = false }
        }
    }
}

/** The state in words and its tone (m-status, m-status--off, --connecting, --error). */
@Composable
private fun statusLabel(state: HomeState): Pair<String, androidx.compose.ui.graphics.Color> {
    val c = Niko.colors
    return when (state.service) {
        BaseService.State.Connected -> stringResource(R.string.home_state_connected) to c.ink
        BaseService.State.Connecting -> stringResource(R.string.home_state_connecting) to c.ink2
        BaseService.State.Stopping -> stringResource(R.string.home_state_stopping) to c.ink2
        else -> if (state.error != null) {
            stringResource(R.string.home_state_failed) to c.danger
        } else {
            stringResource(R.string.home_state_off) to c.ink2
        }
    }
}

private fun speeds(state: HomeState) = "↓ ${rateText(state.downRate)} · ↑ ${rateText(state.upRate)}"

private fun online(state: HomeState, now: Long): String? =
    if (state.service == BaseService.State.Connected && state.connectedSince > 0) elapsedText(now - state.connectedSince) else null

/** The connection tile's and row's value: the profile's name, and its kind when the name does not say it. */
private fun HomeProfile.method() = if (name.equals(type, ignoreCase = true) || name.isBlank()) type else "$name · $type"

@Composable
private fun modeValue(mode: HomeMode) =
    if (mode.vpn) stringResource(R.string.service_mode_vpn) else stringResource(R.string.home_mode_proxy)

@Composable
private fun routeValue(route: HomeRoute) = stringResource(
    when (route) {
        HomeRoute.Russia -> R.string.home_route_russia
        HomeRoute.Own -> R.string.home_route_own
        HomeRoute.None -> R.string.home_route_none
    }
)

@Composable
private fun appsValue(apps: HomeApps) = when {
    !apps.perApp || apps.count == 0 -> stringResource(R.string.home_apps_all)
    apps.bypass -> stringResource(R.string.home_apps_except, apps.count)
    else -> stringResource(R.string.home_apps_only, apps.count)
}

private fun ping(profile: HomeProfile?, unreachable: String): String? = when {
    profile == null -> null
    profile.pingMs != null -> "${profile.pingMs} ms"
    profile.unreachable -> unreachable
    else -> null
}

/** P-Simple: the big button, its state, the three tiles, the node in use on the sheet. */
@Composable
private fun Simple(state: HomeState, now: Long, actions: HomeActions, modifier: Modifier, onMode: () -> Unit) {
    val (label, tone) = statusLabel(state)
    Column(modifier.fillMaxWidth()) {
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            // a low window or a large font: the button of size-connect-md
            val size = if (maxHeight < 360.dp) NikoSize.connectMd else NikoSize.connect
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.weight(1f))
                ConnectButton(state.service, state.error != null, actions::toggle, size)
                Spacer(Modifier.height(NikoSpace.s8))
                val detail = when {
                    state.service == BaseService.State.Connected ->
                        listOfNotNull(online(state, now), speeds(state)).joinToString(" · ")
                    state.service.canStop -> null
                    else -> state.error
                }
                Status(label, detail, tone, modifier = Modifier.padding(horizontal = NikoSpace.s5))
                Spacer(Modifier.weight(1f))
            }
        }
        Column(
            Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s4),
            verticalArrangement = Arrangement.spacedBy(NikoSpace.s2),
        ) {
            Tile(
                R.drawable.ic_l_plug, stringResource(R.string.home_connection),
                state.profile?.method() ?: stringResource(R.string.home_choose_profile), actions::profiles,
                Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
                Tile(R.drawable.ic_l_shield, stringResource(R.string.home_mode), modeValue(state.mode), onMode, Modifier.weight(1f))
                Tile(R.drawable.ic_l_route, stringResource(R.string.home_route), routeValue(state.route), actions::route, Modifier.weight(1f))
            }
        }
        Sheet(Modifier.navigationBarsPadding()) { NodeRow(state.profile, actions, bottom = NikoSpace.s6) }
    }
}

/** The node or profile in use on the sheet (D-MAIN-10, 13): its name, its expiry and traffic, its ping. */
@Composable
private fun NodeRow(profile: HomeProfile?, actions: HomeActions, bottom: androidx.compose.ui.unit.Dp, advanced: Boolean = false) {
    // in the connection card the row comes after the state's, with the hairline above it
    if (profile == null) {
        ListRow(
            stringResource(R.string.home_no_profile), titleBold = true, bottom = bottom, divider = advanced,
            sub = { Sub(stringResource(R.string.home_no_profile_hint)) },
            trail = { Icon(R.drawable.ic_l_plus, Niko.colors.ink2) },
            onClick = actions::profiles,
        )
        return
    }
    val unreachable = stringResource(R.string.home_unreachable)
    val title = if (advanced) listOfNotNull(profile.group, profile.name).distinct().joinToString(" · ") else profile.group ?: profile.name
    ListRow(
        title, titleBold = true, bottom = bottom, divider = advanced,
        sub = {
            when {
                advanced -> Tag(profile.type)
                profile.usage != null -> Sub(profile.usage)
                else -> Sub(profile.method())
            }
        },
        trail = {
            ping(profile, unreachable)?.let {
                Text(it, NikoType.code, if (profile.unreachable && profile.pingMs == null) Niko.colors.danger else Niko.colors.ink2, maxLines = 1)
            }
            Icon(R.drawable.ic_l_chevrons_up_down, Niko.colors.ink2)
        },
        onClick = actions::profiles,
    )
}

/** P-Advanced: the connection card, the main settings and the rest as rows, the configs and subscriptions on the sheet. */
@Composable
private fun Advanced(state: HomeState, now: Long, actions: HomeActions, modifier: Modifier, onMode: () -> Unit) {
    val (label, tone) = statusLabel(state)
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s6),
            verticalArrangement = Arrangement.spacedBy(NikoSpace.s6),
        ) {
            ListGroup {
                ConnectionCard(state, now, label, tone, actions)
                NodeRow(state.profile, actions, bottom = NikoSpace.s3, advanced = true)
            }
            Section(stringResource(R.string.home_section_main)) {
                ListRow(stringResource(R.string.home_mode), trail = { Trail(modeValue(state.mode)) }, onClick = onMode)
                ListRow(stringResource(R.string.home_route), divider = true, trail = { Trail(routeValue(state.route)) }, onClick = actions::route)
                ListRow(stringResource(R.string.home_apps), divider = true, trail = { Trail(appsValue(state.apps)) }, onClick = actions::apps)
            }
            Section(stringResource(R.string.home_section_settings)) {
                ListRow(
                    stringResource(R.string.home_connection), sub = { Sub(stringResource(R.string.home_connection_hint)) },
                    trail = { Trail(null) }, onClick = actions::settings,
                )
                ListRow(
                    stringResource(R.string.home_routing), divider = true, sub = { Sub(stringResource(R.string.home_routing_hint)) },
                    trail = { Trail(null) }, onClick = actions::route,
                )
                ListRow(stringResource(R.string.home_nodes), divider = true, trail = { Trail(null) }, onClick = actions::nodes)
                ListRow(stringResource(R.string.home_log), divider = true, trail = { Trail(null) }, onClick = actions::log)
                ListRow(stringResource(R.string.home_help), divider = true, trail = { Trail(null) }, onClick = actions::help)
            }
        }
        Sheet(Modifier.navigationBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(end = NikoSpace.s3, bottom = NikoSpace.s6),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(Modifier.weight(1f).padding(horizontal = NikoSpace.s5), horizontalArrangement = Arrangement.spacedBy(NikoSpace.s6)) {
                    Tab(stringResource(R.string.home_profiles), state.profiles, active = true, onClick = actions::profiles)
                    Tab(stringResource(R.string.home_subscriptions), state.subscriptions, active = false, onClick = actions::subscriptions)
                }
                TonalIconButton(R.drawable.ic_l_plus, stringResource(R.string.home_add), actions::profiles)
            }
        }
    }
}

/** The connection card's first row (P-Advanced): the small button, the state and speeds, the time online. */
@Composable
private fun ConnectionCard(state: HomeState, now: Long, label: String, tone: androidx.compose.ui.graphics.Color, actions: HomeActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = NikoSpace.s4, vertical = NikoSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NikoSpace.s4),
    ) {
        ConnectButton(state.service, state.error != null, actions::toggle, NikoSize.connectSm)
        val detail = when {
            state.service == BaseService.State.Connected -> speeds(state)
            state.service.canStop -> null
            else -> state.error
        }
        Status(label, detail, tone, start = true, modifier = Modifier.weight(1f))
        online(state, now)?.let { Text(it, NikoType.code, Niko.colors.ink2, maxLines = 1) }
    }
}

/** P-ModeSheet: VPN or the proxy alone, over the scrim (D-NAV-02). */
@Composable
private fun ModeSheet(mode: HomeMode, onPick: (Boolean) -> Unit, onDismiss: () -> Unit) {
    val c = Niko.colors
    Box(
        Modifier.fillMaxSize().background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { }.navigationBarsPadding()) {
            Text(
                stringResource(R.string.home_mode), NikoType.headline, c.ink,
                Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s2),
            )
            Column(Modifier.selectableGroup().padding(bottom = NikoSpace.s6)) {
                for (vpn in listOf(true, false)) {
                    val selected = vpn == mode.vpn
                    ListRow(
                        if (vpn) stringResource(R.string.service_mode_vpn) else stringResource(R.string.home_mode_proxy),
                        Modifier.selectable(selected, role = Role.RadioButton) { onPick(vpn) },
                        titleBold = selected,
                        divider = !vpn,
                        side = NikoSpace.s5,
                        lead = { Radio(selected) },
                        sub = { Sub(if (vpn) stringResource(R.string.home_mode_vpn_hint) else "127.0.0.1:${mode.proxyPort}", mono = !vpn) },
                    )
                }
            }
        }
    }
}

package io.niko.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
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
    configs: List<ConfigGroup>? = null,
    testing: Set<Long> = emptySet(),
    results: Map<Long, ConfigsTest.Result> = emptyMap(),
    configsActions: ConfigsActions? = null,
    configsOpen: Boolean = false,
    subs: List<SubRow>? = null,
    subsActions: SubsActions? = null,
    format: io.niko.ui.node.NodeFormat? = null,
    subsOpen: Boolean = false,
    configsMenus: ConfigsMenuActions? = null,
) {
    val c = Niko.colors
    var modeSheet by rememberSaveable { mutableStateOf(sheet) }
    var cardMenu by rememberSaveable { mutableStateOf(false) }
    var configsSheet by rememberSaveable { mutableStateOf(configsOpen || subsOpen) }
    // the sheet opened on its Subscriptions tab (P-Subs)
    var onSubs by rememberSaveable { mutableStateOf(subsOpen) }
    // the activity reads the sheet's groups while it is open; the screen gone (another screen in its place) closes it
    DisposableEffect(configsSheet) {
        actions.configs(configsSheet)
        onDispose { if (configsSheet) actions.configs(false) }
    }
    // the configs' sheet from the node row and the Configs tab; without its actions (a snapshot of the screen alone) the
    // old list as before
    val openConfigs = {
        if (configsActions != null) {
            onSubs = false
            configsSheet = true
        } else actions.profiles()
    }
    val openSubs = {
        if (configsActions != null && subsActions != null) {
            onSubs = true
            configsSheet = true
        } else actions.subscriptions()
    }
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
                HomeView.Simple -> Simple(state, now, actions, openConfigs, Modifier.weight(1f)) { modeSheet = true }
                HomeView.Advanced -> Advanced(state, now, actions, openConfigs, openSubs, Modifier.weight(1f), onCardMenu = { cardMenu = true }) {
                    modeSheet = true
                }
            }
        }
        if (modeSheet) {
            BackHandler { modeSheet = false }
            ModeSheet(state.mode, onPick = { vpn ->
                modeSheet = false
                if (vpn != state.mode.vpn) actions.mode(vpn)
            }) { modeSheet = false }
        }
        if (cardMenu && state.service == BaseService.State.Connected) {
            BackHandler { cardMenu = false }
            CardMenu(onCheck = { cardMenu = false; actions.check() }, onExit = { cardMenu = false; actions.exitAddress() }) { cardMenu = false }
        }
        if (configsSheet && configsActions != null) {
            ConfigsSheet(
                configs, state.profiles, state.subscriptions, testing, results, configsActions,
                subs = subs, subsActions = subsActions, format = format, now = now / 1000, onSubs = onSubs,
                menus = configsMenus,
            ) { configsSheet = false }
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

/** The detail's speeds, "↓ 4.2 MB/s · ↑ 312 KB/s", the arrows drawn as icons (Status) and read by their names. */
@Composable
private fun speeds(state: HomeState, online: String? = null): AnnotatedString {
    val down = stringResource(R.string.home_rate_down)
    val up = stringResource(R.string.home_rate_up)
    return buildAnnotatedString {
        if (online != null) append("$online · ")
        appendInlineContent(RATE_DOWN, down)
        append(" ${rateText(state.downRate)} · ")
        appendInlineContent(RATE_UP, up)
        append(" ${rateText(state.upRate)}")
    }
}

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
private fun Simple(state: HomeState, now: Long, actions: HomeActions, onConfigs: () -> Unit, modifier: Modifier, onMode: () -> Unit) {
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
                    state.service == BaseService.State.Connected -> speeds(state, online(state, now))
                    state.service.canStop -> null
                    else -> state.error?.let(::AnnotatedString)
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
        Sheet(Modifier.navigationBarsPadding()) { NodeRow(state.profile, state.profiles, actions, onConfigs, bottom = NikoSpace.s6) }
    }
}

/** The node or profile in use on the sheet (D-MAIN-10, 13): its name, its expiry and traffic, its ping. */
@Composable
private fun NodeRow(profile: HomeProfile?, profiles: Int, actions: HomeActions, onConfigs: () -> Unit, bottom: androidx.compose.ui.unit.Dp, advanced: Boolean = false) {
    // in the connection card the row comes after the state's, with the hairline above it
    if (profile == null && profiles > 0) {
        // configs there, none chosen (none yet, or the chosen one deleted): the list to choose from
        ListRow(
            stringResource(R.string.home_choose_profile), titleBold = true, bottom = bottom, divider = advanced,
            sub = { Sub("${stringResource(R.string.home_profiles)} $profiles") },
            trail = { Icon(R.drawable.ic_l_chevrons_up_down, Niko.colors.ink2) },
            onClick = onConfigs,
        )
        return
    }
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
        onClick = onConfigs,
    )
}

/** P-Advanced: the connection card, the main settings and the rest as rows, the configs and subscriptions on the sheet. */
@Composable
private fun Advanced(
    state: HomeState,
    now: Long,
    actions: HomeActions,
    onConfigs: () -> Unit,
    onSubs: () -> Unit,
    modifier: Modifier,
    onCardMenu: () -> Unit = {},
    onMode: () -> Unit,
) {
    val (label, tone) = statusLabel(state)
    Column(modifier.fillMaxWidth()) {
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s6),
            verticalArrangement = Arrangement.spacedBy(NikoSpace.s6),
        ) {
            ListGroup {
                ConnectionCard(state, now, label, tone, actions, onCardMenu)
                NodeRow(state.profile, state.profiles, actions, onConfigs, bottom = NikoSpace.s3, advanced = true)
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
                    Tab(stringResource(R.string.home_profiles), state.profiles, active = true, onClick = onConfigs)
                    Tab(stringResource(R.string.home_subscriptions), state.subscriptions, active = false, onClick = onSubs)
                }
                TonalIconButton(R.drawable.ic_l_plus, stringResource(R.string.home_add), actions::profiles)
            }
        }
    }
}

/**
 * The connection card's first row (P-Advanced): the small button, the state and speeds, the time online. Connected,
 * the state and speeds are a button, as the old status bar was: a tap checks the connection (D-MAIN-18), its answer in
 * place of the time online for a while (a failure's reason under the state); a long press opens the card's menu with
 * the check and the exit address (D-MAIN-03, D-MAIN-14).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ConnectionCard(
    state: HomeState,
    now: Long,
    label: String,
    tone: androidx.compose.ui.graphics.Color,
    actions: HomeActions,
    onMenu: () -> Unit,
) {
    val c = Niko.colors
    val connected = state.service == BaseService.State.Connected
    val check = if (connected) state.check else null
    Row(
        Modifier.fillMaxWidth().padding(horizontal = NikoSpace.s4, vertical = NikoSpace.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NikoSpace.s4),
    ) {
        ConnectButton(state.service, state.error != null, actions::toggle, NikoSize.connectSm)
        val detail = when {
            check is HomeCheck.Failed -> AnnotatedString(check.reason)
            connected -> speeds(state)
            state.service.canStop -> null
            else -> state.error?.let(::AnnotatedString)
        }
        val checkLabel = stringResource(R.string.home_check)
        val menuLabel = stringResource(R.string.home_card_menu)
        Row(
            Modifier.weight(1f).then(
                if (connected) {
                    Modifier.combinedClickable(
                        onClickLabel = checkLabel, role = Role.Button, onLongClickLabel = menuLabel,
                        onLongClick = onMenu, onClick = actions::check,
                    )
                } else Modifier,
            ),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NikoSpace.s4),
        ) {
            Status(label, detail, tone, start = true, modifier = Modifier.weight(1f))
            when (check) {
                null -> online(state, now)?.let { Text(it, NikoType.code, c.ink2, maxLines = 1) }
                HomeCheck.Testing -> Text(stringResource(R.string.home_check_testing), NikoType.code, c.ink2, maxLines = 1)
                is HomeCheck.Done -> Text(stringResource(R.string.home_check_done, check.millis), NikoType.code, c.ink, maxLines = 1)
                is HomeCheck.Failed -> Text(stringResource(R.string.home_check_failed), NikoType.code, c.danger, maxLines = 1)
            }
        }
    }
}

/** The status card's menu (P-Advanced, its long press): the connection check and the exit address. */
@Composable
private fun CardMenu(onCheck: () -> Unit, onExit: () -> Unit, onDismiss: () -> Unit) {
    val c = Niko.colors
    Box(
        Modifier.fillMaxSize().background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { }.navigationBarsPadding()) {
            Text(
                stringResource(R.string.home_card_menu), NikoType.headline, c.ink,
                Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s2),
            )
            Column(Modifier.padding(bottom = NikoSpace.s6)) {
                ListRow(stringResource(R.string.home_check), side = NikoSpace.s5, lead = { Icon(R.drawable.ic_l_activity, c.ink2) }, onClick = onCheck)
                ListRow(
                    stringResource(R.string.exit_address), divider = true, side = NikoSpace.s5,
                    lead = { Icon(R.drawable.ic_l_globe, c.ink2) }, onClick = onExit,
                )
            }
        }
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

package io.niko.ui.admin

import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoOpacity
import io.niko.ui.compose.NikoRadius
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.Icon
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Radio
import io.niko.ui.home.Section
import io.niko.ui.home.Sheet
import io.niko.ui.home.Sub
import io.niko.ui.home.Tag
import io.niko.ui.home.Text
import io.niko.ui.home.TonalIconButton
import io.niko.ui.home.Trail
import io.niko.ui.home.pressed
import io.niko.ui.route.CheckRow
import io.niko.ui.route.DangerBanner
import io.niko.ui.route.PrimaryButton
import io.niko.ui.route.SectionWithButton
import io.niko.ui.settings.Field
import io.niko.ui.settings.FieldButton
import io.niko.ui.settings.IconButton
import io.niko.ui.settings.Segmented
import io.niko.ui.settings.Switch
import io.niko.ui.settings.SwitchRow

/**
 * The admin panel of a node (P-Admin, P-AdminUser, P-AdminInvite, P-AdminServices): the page on top of
 * [AdminState.pages], and over it what is asked ([AdminState.sheet]). Back closes the sheet, then the page.
 */
@Composable
fun AdminScreen(state: AdminState, actions: AdminActions) {
    BackHandler(enabled = state.sheet != null || state.pages.size > 1) {
        if (state.sheet != null) actions.dismiss() else actions.back()
    }
    Box(Modifier.fillMaxSize()) {
        // a page keeps its own scroll: another page starts at its top
        key(state.pages.size, state.page::class) {
            when (val page = state.page) {
                is AdminPage.Status -> StatusPage(state, page, actions)
                is AdminPage.Users -> UsersPage(state, page, actions)
                is AdminPage.User -> UserPage(state, page, actions)
                is AdminPage.NewUser -> NewUserPage(state, page, actions)
                is AdminPage.Invite -> InvitePage(state, page, actions)
                is AdminPage.Services -> ServicesPage(state, page, actions)
                is AdminPage.Service -> ServicePage(state, page, actions)
                is AdminPage.Network -> NetworkPage(state, page, actions)
                is AdminPage.Detail -> DetailPage(state, page, actions)
            }
        }
        state.sheet?.let { SheetView(it, actions) }
    }
}

/**
 * m-topbar with the way back, the title over its overline [sub], the page read again and the page's own [bar]
 * buttons; then m-body, the reason the page could not be read first. [refresh]: the page has something to read.
 */
@Composable
private fun Page(
    state: AdminState,
    title: String,
    actions: AdminActions,
    sub: String? = null,
    refresh: Boolean = true,
    bar: @Composable RowScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Niko.colors
    Column(Modifier.fillMaxSize().background(c.surface0).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
        ) {
            IconButton(R.drawable.ic_l_arrow_left, stringResource(R.string.screen_back), onClick = actions::back)
            Column(Modifier.weight(1f).padding(horizontal = NikoSpace.s2)) {
                Text(title, NikoType.headline, c.ink, maxLines = 1)
                if (sub != null) Text(sub.uppercase(), NikoType.label, c.ink2, maxLines = 1)
            }
            if (refresh) {
                IconButton(
                    R.drawable.ic_l_rotate_cw, stringResource(R.string.ad_refresh),
                    Modifier.alpha(if (state.busy) NikoOpacity.DISABLED else 1f), onClick = actions::refresh,
                )
            }
            bar()
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding().imePadding()
                .padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(NikoSpace.s6),
        ) {
            state.error?.let { DangerBanner(it) }
            content()
        }
    }
}

/** A line of the page outside its groups: a help text, "loading", "nothing yet". */
@Composable
private fun Note(text: String, danger: Boolean = false) {
    val c = Niko.colors
    Text(text, NikoType.bodySm, if (danger) c.danger else c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
}

/** What a page shows before its data: that it is being read, or nothing (the banner says why not). */
@Composable
private fun Waiting(state: AdminState) {
    if (state.busy) Note(stringResource(R.string.ad_loading))
}

/** A row's lines under its title, as many as there are. */
@Composable
private fun Lines(text: String, mono: Boolean = false) =
    Text(text, if (mono) NikoType.code else NikoType.bodySm, Niko.colors.ink2)

@Composable
private fun onOff(on: Boolean) = stringResource(if (on) R.string.ad_on else R.string.ad_off)

// ---- P-Admin

@Composable
private fun StatusPage(state: AdminState, page: AdminPage.Status, actions: AdminActions) {
    val c = Niko.colors
    Page(state, state.node, actions, sub = stringResource(R.string.ad_admin)) {
        val data = page.data
        if (data == null) {
            Waiting(state)
            return@Page
        }
        // what the node warns its admin of (D-ADMIN-22), before everything else
        if (data.warnings.isNotEmpty()) Warnings(data.warnings)
        if (data.hasName) {
            Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
                Field(
                    stringResource(R.string.ad_node_name), page.nameShown, actions::name,
                    button = save(page.nameChanged, actions::saveName), onGo = actions::saveName,
                )
                if (page.nameChanged) Note(stringResource(R.string.raycontrol_admin_node_name_help))
            }
        }
        if (data.hasNote) {
            Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
                Field(
                    stringResource(R.string.ad_note), page.noteShown, actions::note,
                    button = save(page.noteChanged, actions::saveNote), onGo = actions::saveNote,
                )
                when {
                    page.noteError != null -> Note(page.noteError, danger = true)
                    page.noteChanged -> Note(stringResource(R.string.raycontrol_admin_node_note_help))
                }
            }
        }
        ListGroup {
            ListRow(stringResource(R.string.ad_members), trail = { Count(data.users) })
            ListRow(stringResource(R.string.ad_devices), divider = true, trail = { Count(data.devices) })
            data.update?.let { update ->
                ListRow(
                    stringResource(R.string.ad_version), divider = true,
                    sub = { Sub(update.versions, mono = true) },
                    trail = {
                        if (update.offer != null) TonalButton(stringResource(R.string.ad_update), actions::update) else Trail(null)
                    },
                    onClick = { actions.open(AdminDest.UPDATE) },
                )
            }
        }
        ListGroup {
            NavRow(stringResource(R.string.ad_members), data.users.toString(), false) { actions.open(AdminDest.USERS) }
            NavRow(stringResource(R.string.ad_services), data.services.toString(), true) { actions.open(AdminDest.SERVICES) }
            NavRow(stringResource(R.string.raycontrol_admin_network), null, true) { actions.open(AdminDest.NETWORK) }
            NavRow(stringResource(R.string.ad_audit), null, true) { actions.open(AdminDest.AUDIT) }
            data.mirror?.let { NavRow(stringResource(R.string.ad_releases), onOff(it), true) { actions.open(AdminDest.MIRROR) } }
            data.summary?.let {
                NavRow(stringResource(R.string.raycontrol_admin_summary), onOff(it), true) { actions.open(AdminDest.SUMMARY) }
            }
            NavRow(stringResource(R.string.ad_ports_metrics), null, true) { actions.open(AdminDest.PORTS) }
            NavRow(
                stringResource(R.string.ad_ssh),
                if (data.access.isEmpty()) stringResource(R.string.ad_off) else data.access.size.toString(), true,
            ) { actions.open(AdminDest.ACCESS) }
        }
        Section(stringResource(R.string.ad_node)) {
            ListRow("${data.host} · ${data.version}", sub = { Sub(data.nodeId, mono = true) })
            ListRow(stringResource(R.string.raycontrol_admin_this_device), divider = true, sub = { Sub(data.device) })
            ListRow(
                stringResource(R.string.raycontrol_admin_spa_ports), divider = true,
                sub = if (data.spaPorts.isEmpty()) null else ({ Lines(data.spaPorts, mono = true) }),
                trail = if (data.spaPorts.isEmpty()) ({ Text("—", NikoType.code, c.ink2) }) else null,
            )
        }
    }
}

/** The field's button that sends what was typed; none while it is as the node has it. */
@Composable
private fun save(changed: Boolean, onClick: () -> Unit) =
    if (changed) FieldButton(R.drawable.ic_l_check, stringResource(R.string.ad_save), onClick) else null

/** Предупреждения узла: each as the node words it, in danger on danger-soft. */
@Composable
private fun Warnings(lines: List<String>) {
    val c = Niko.colors
    Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
        Text(stringResource(R.string.raycontrol_admin_warnings).uppercase(), NikoType.label, c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
        for (line in lines) DangerBanner(line)
    }
}

@Composable
private fun Count(value: Long) = Text(value.toString(), NikoType.code, Niko.colors.ink2, maxLines = 1)

/** A row that leads to a page: its value and the chevron. */
@Composable
private fun NavRow(title: String, value: String?, divider: Boolean, onClick: () -> Unit) =
    ListRow(title, divider = divider, trail = { Trail(value) }, onClick = onClick)

/** m-btn m-btn--tonal: its text on surface-2, the width of its text; surface-3 pressed. */
@Composable
private fun TonalButton(text: String, onClick: () -> Unit) {
    val c = Niko.colors
    val source = remember { MutableInteractionSource() }
    Box(
        Modifier.height(NikoSize.control).clip(CircleShape).background(if (pressed(source)) c.surface3 else c.surface2)
            .clickable(source, indication = null, role = Role.Button, onClick = onClick)
            .padding(horizontal = NikoSpace.s6),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, NikoType.body.copy(fontWeight = NikoType.headline.fontWeight), c.ink, maxLines = 1)
    }
}

// ---- the users

@Composable
private fun UsersPage(state: AdminState, page: AdminPage.Users, actions: AdminActions) {
    Page(state, stringResource(R.string.ad_members), actions, bar = {
        IconButton(R.drawable.ic_l_plus, stringResource(R.string.raycontrol_admin_user_new), onClick = actions::newUser)
    }) {
        val users = page.users
        if (users == null) {
            Waiting(state)
            return@Page
        }
        if (users.isNotEmpty()) {
            ListGroup {
                users.forEachIndexed { i, u ->
                    ListRow(
                        u.name, divider = i > 0,
                        sub = { Lines(u.facts) },
                        trail = { Trail(if (u.enabled) null else stringResource(R.string.raycontrol_admin_disabled)) },
                        onClick = { actions.user(u.name) },
                    )
                }
            }
        }
        // how a user gets the node, under the users
        Note(stringResource(R.string.raycontrol_admin_users_help))
    }
}

// ---- P-AdminUser

@Composable
private fun UserPage(state: AdminState, page: AdminPage.User, actions: AdminActions) {
    val c = Niko.colors
    Page(state, page.name, actions, bar = {
        IconButton(R.drawable.ic_l_ellipsis_vertical, stringResource(R.string.more_options), onClick = actions::userMenu)
    }) {
        val info = page.info
        if (info == null) {
            Waiting(state)
            return@Page
        }
        ListGroup {
            ListRow(info.facts, titleLines = 4)
            SwitchRow(stringResource(R.string.ad_access_on), info.enabled, actions::userEnabled, divider = true)
        }
        SectionWithButton(
            stringResource(R.string.ad_devices), R.drawable.ic_l_plus, stringResource(R.string.raycontrol_admin_invite), actions::invite,
        ) {
            val devices = page.devices
            when {
                devices == null -> ListRow(stringResource(if (state.busy) R.string.ad_loading else R.string.raycontrol_admin_empty))
                devices.isEmpty() -> ListRow(stringResource(R.string.raycontrol_admin_empty))
                else -> devices.forEachIndexed { i, d ->
                    ListRow(
                        d.title, divider = i > 0,
                        sub = { Lines(d.sub) },
                        trail = if (!d.revocable) null else ({
                            IconButton(R.drawable.ic_l_x, stringResource(R.string.ad_revoke) + " " + d.name, muted = true, small = true) {
                                actions.revoke(d)
                            }
                        }),
                    )
                }
            }
        }
        if (info.services.isEmpty()) {
            Note(stringResource(R.string.raycontrol_admin_user_no_services))
        } else {
            Section(stringResource(R.string.ad_services)) {
                info.services.forEachIndexed { i, s ->
                    SwitchRow(s.title, s.on, { actions.userService(s.id, it) }, divider = i > 0)
                }
            }
        }
        ListGroup {
            ListRow(stringResource(R.string.raycontrol_admin_links), trail = { Trail(null) }, onClick = actions::links)
            ListRow(
                stringResource(R.string.raycontrol_admin_renew), divider = true,
                trail = { Icon(R.drawable.ic_l_rotate_cw, c.ink2) }, onClick = actions::renew,
            )
        }
    }
}

@Composable
private fun NewUserPage(state: AdminState, page: AdminPage.NewUser, actions: AdminActions) {
    Page(state, stringResource(R.string.raycontrol_admin_user_new), actions, refresh = false) {
        Field(stringResource(R.string.raycontrol_admin_user_name), page.name, { actions.form(AdminField.USER_NAME, it) })
        Field(stringResource(R.string.raycontrol_admin_user_days), page.days, { actions.form(AdminField.USER_DAYS, it.filter(Char::isDigit)) })
        Field(stringResource(R.string.raycontrol_admin_user_gb), page.gb, { actions.form(AdminField.USER_GB, it.filter(Char::isDigit)) })
        Field(stringResource(R.string.raycontrol_admin_user_note), page.note, { actions.form(AdminField.USER_NOTE, it) })
        if (page.services.isEmpty()) {
            Note(stringResource(R.string.raycontrol_admin_user_no_services))
        } else {
            Section(stringResource(R.string.ad_services)) {
                page.services.forEachIndexed { i, s -> CheckRow(s.title, s.on, { actions.tick(s.id, it) }, divider = i > 0) }
            }
        }
        PrimaryButton(stringResource(R.string.raycontrol_admin_user_create), actions::createUser, Modifier.fillMaxWidth(), enabled = !state.busy)
    }
}

// ---- P-AdminInvite

@Composable
private fun InvitePage(state: AdminState, page: AdminPage.Invite, actions: AdminActions) {
    val c = Niko.colors
    Page(state, stringResource(R.string.ad_invite), actions, sub = page.user, refresh = false) {
        Field(stringResource(R.string.ad_device), page.device, { actions.form(AdminField.DEVICE, it) })
        Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
            Text(stringResource(R.string.ad_role).uppercase(), NikoType.label, c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
            Segmented(
                null,
                listOf("user" to stringResource(R.string.ad_role_user), "admin" to stringResource(R.string.ad_admin)),
                page.role, block = true, name = stringResource(R.string.ad_role), onPick = actions::role,
            )
            Note(stringResource(if (page.role == "admin") R.string.raycontrol_admin_role_admin else R.string.raycontrol_admin_role_user))
        }
        ListGroup {
            ListRow(
                stringResource(R.string.ad_ttl),
                trail = { Trail(stringArrayResource(R.array.raycontrol_admin_ttls).getOrNull(page.ttl)) }, onClick = actions::ttl,
            )
            SwitchRow(
                stringResource(R.string.ad_pin), page.pin, actions::pin, divider = true,
                sub = stringResource(R.string.ad_pin_sub),
            )
        }
        val result = page.result
        if (result == null) {
            Note(stringResource(if (page.created) R.string.raycontrol_admin_invite_help_created else R.string.raycontrol_admin_invite_help, page.user))
        } else {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(NikoSpace.s3)) {
                Qr(result.code)
                if (result.pin.isNotEmpty()) Text(result.pin.chunked(3).joinToString(" "), NikoType.title.copy(fontFamily = NikoType.code.fontFamily), c.ink, maxLines = 1)
                Row(horizontalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
                    TonalIconButton(R.drawable.ic_l_copy_minus, stringResource(R.string.action_copy), actions::copy)
                    TonalIconButton(R.drawable.ic_l_share_2, stringResource(R.string.share), actions::share)
                }
            }
            Note(result.message)
        }
        PrimaryButton(stringResource(R.string.raycontrol_admin_invite_create), actions::createInvite, Modifier.fillMaxWidth(), enabled = !state.busy)
    }
}

/** The invite as a QR code: dark modules on white in both themes, a quiet module around it. */
@Composable
private fun Qr(code: String) {
    val matrix = remember(code) {
        runCatching { MultiFormatWriter().encode(code, BarcodeFormat.QR_CODE, 0, 0, mapOf(EncodeHintType.MARGIN to 1)) }.getOrNull()
    }
    val label = stringResource(R.string.ad_invite)
    Canvas(Modifier.size(220.dp).clip(RoundedCornerShape(NikoRadius.sm)).background(Color.White).semantics { contentDescription = label }) {
        if (matrix == null) return@Canvas
        val cell = size.width / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            // a hair over the cell, so that no seam shows between neighbours
            if (matrix.get(x, y)) drawRect(Color.Black, Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

// ---- P-AdminServices

@Composable
private fun ServicesPage(state: AdminState, page: AdminPage.Services, actions: AdminActions) {
    Page(state, stringResource(R.string.ad_services), actions, bar = {
        IconButton(R.drawable.ic_l_plus, stringResource(R.string.raycontrol_admin_service_kind), onClick = actions::newService)
    }) {
        val services = page.services
        if (services == null) {
            Waiting(state)
            return@Page
        }
        if (services.isEmpty()) {
            Note(stringResource(R.string.raycontrol_admin_empty))
        } else {
            ListGroup {
                services.forEachIndexed { i, s ->
                    ListRow(
                        s.name, divider = i > 0,
                        sub = {
                            Tag(s.kind)
                            Sub(s.facts)
                        },
                        trail = { SwitchButton(s.name, s.enabled) { actions.serviceEnabled(s.name, it) } },
                        onClick = { actions.service(s.name) },
                    )
                }
            }
        }
        ListGroup {
            NavRow(stringResource(R.string.raycontrol_admin_network), null, false) { actions.open(AdminDest.NETWORK) }
            NavRow(stringResource(R.string.ad_audit), "200", true) { actions.open(AdminDest.AUDIT) }
        }
    }
}

/** m-switch pressed by itself, in a row that leads elsewhere. */
@Composable
private fun SwitchButton(label: String, on: Boolean, onChange: (Boolean) -> Unit) =
    Box(
        Modifier.clip(CircleShape).toggleable(on, remember { MutableInteractionSource() }, indication = null, role = Role.Switch, onValueChange = onChange)
            .semantics { contentDescription = label },
    ) { Switch(on) }

@Composable
private fun ServicePage(state: AdminState, page: AdminPage.Service, actions: AdminActions) {
    val c = Niko.colors
    Page(state, page.name, actions, sub = page.info?.row?.kind) {
        val info = page.info
        if (info == null) {
            Waiting(state)
            return@Page
        }
        ListGroup {
            SwitchRow(
                stringResource(R.string.ad_service_on), info.row.enabled, { actions.serviceEnabled(page.name, it) },
                sub = info.row.facts,
            )
        }
        if (info.options.isNotEmpty()) {
            Section(stringResource(R.string.raycontrol_admin_options)) {
                info.options.forEachIndexed { i, o ->
                    ListRow(o.title, divider = i > 0, titleLines = 2, sub = { Lines(o.value, mono = true) }, trail = { Trail(null) }, onClick = { actions.option(o.key) })
                }
            }
        }
        if (info.keys.isNotEmpty()) {
            Section(stringResource(R.string.ad_keys)) {
                info.keys.forEachIndexed { i, k ->
                    ListRow(
                        stringResource(
                            when (k) {
                                ServiceKey.REALITY -> R.string.raycontrol_admin_rotate
                                ServiceKey.SEAL -> R.string.raycontrol_admin_rotate_seal
                                ServiceKey.SERVICE -> R.string.raycontrol_admin_rotate_service
                            },
                        ),
                        divider = i > 0, lead = { Icon(R.drawable.ic_l_key_round, c.ink2) }, onClick = { actions.key(k) },
                    )
                }
            }
        }
        ListGroup {
            DangerRow(stringResource(R.string.delete), actions::deleteService)
        }
    }
}

/** A row that takes something away for good: its trash in danger. */
@Composable
private fun DangerRow(title: String, onClick: () -> Unit) =
    ListRow(title, lead = { Icon(R.drawable.ic_l_trash_2, Niko.colors.danger) }, onClick = onClick)

// ---- the network

@Composable
private fun NetworkPage(state: AdminState, page: AdminPage.Network, actions: AdminActions) {
    Page(state, stringResource(R.string.raycontrol_admin_network), actions) {
        val data = page.data
        if (data == null) {
            Waiting(state)
            return@Page
        }
        if (data.dns != null || data.zones != null) {
            ListGroup {
                data.dns?.let {
                    ListRow(stringResource(R.string.raycontrol_admin_dns), sub = { Lines(it) }, trail = { Trail(null) }, onClick = actions::dns)
                }
                data.zones?.let {
                    ListRow(
                        stringResource(R.string.raycontrol_admin_dns_check), divider = data.dns != null,
                        sub = { Lines(it) }, trail = { Trail(null) }, onClick = actions::zones,
                    )
                }
            }
        }
        if (data.relays.isNotEmpty()) {
            Section(stringResource(R.string.ad_relays)) {
                data.relays.forEachIndexed { i, r ->
                    ListRow(r.title, divider = i > 0, sub = r.sub?.let { { Lines(it) } }, trail = { Trail(null) }, onClick = { actions.relay(r.id) })
                }
            }
        }
        // AddRelay, RemoveRelay and a relay's bundle are root's on the node only (api-v1 §5)
        Note(stringResource(R.string.raycontrol_admin_relays_root))
    }
}

// ---- the pages of text and rows: the audit, the update, the releases, the summaries, the ports and metrics

@Composable
private fun DetailPage(state: AdminState, page: AdminPage.Detail, actions: AdminActions) {
    val c = Niko.colors
    Page(state, page.title, actions, refresh = page.kind != DetailKind.TEXT && page.kind != DetailKind.PORTS, bar = {
        if (page.share != null) {
            IconButton(R.drawable.ic_l_copy_minus, stringResource(R.string.action_copy), onClick = actions::copy)
            IconButton(R.drawable.ic_l_share_2, stringResource(R.string.share), onClick = actions::share)
        }
    }) {
        page.toggle?.let { ListGroup { SwitchRow(it.title, it.on, actions::toggle) } }
        if (page.text != null) {
            SelectionContainer {
                Text(page.text, if (page.mono) NikoType.code.copy(fontSize = NikoType.caption.fontSize, lineHeight = NikoType.caption.lineHeight) else NikoType.bodySm,
                    if (page.mono) c.ink else c.ink2, Modifier.padding(horizontal = if (page.mono) 0.dp else NikoSpace.s4))
            }
        }
        if (page.rows.isNotEmpty()) {
            ListGroup {
                page.rows.forEachIndexed { i, r ->
                    ListRow(
                        r.title, divider = i > 0, titleLines = 2,
                        sub = r.sub?.let { { Lines(it) } },
                        trail = if (r.action) ({ Trail(null) }) else null,
                        onClick = if (r.action) ({ actions.detail(r.id) }) else null,
                    )
                }
            }
        } else if (page.text == null && page.toggle == null) {
            if (state.busy) Note(stringResource(R.string.ad_loading)) else if (state.error == null) Note(stringResource(R.string.raycontrol_admin_empty))
        }
    }
}

// ---- what is asked over a page

@Composable
private fun SheetView(sheet: AdminSheet, actions: AdminActions) {
    val c = Niko.colors
    Box(
        Modifier.fillMaxSize().background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = actions::dismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { }.navigationBarsPadding().imePadding()) {
            Column(
                Modifier.verticalScroll(rememberScrollState()).padding(bottom = NikoSpace.s6),
                verticalArrangement = Arrangement.spacedBy(NikoSpace.s4),
            ) {
                when (sheet) {
                    is AdminSheet.Confirm -> {
                        SheetText(sheet.text, ink = true)
                        SheetButtons(stringResource(R.string.yes), actions::confirm, actions::dismiss)
                    }
                    is AdminSheet.Message -> {
                        SheetText(sheet.text, ink = true)
                        SheetButtons(stringResource(R.string.ad_ok), actions::dismiss, null)
                    }
                    is AdminSheet.Choice -> {
                        Text(sheet.title, NikoType.headline, c.ink, Modifier.padding(horizontal = NikoSpace.s5))
                        Column {
                            sheet.options.forEachIndexed { i, option ->
                                ListRow(
                                    option, divider = i > 0, side = NikoSpace.s5, titleLines = 4,
                                    role = if (sheet.selected != null) Role.RadioButton else Role.Button,
                                    lead = if (sheet.selected != null) ({ Radio(i == sheet.selected) }) else null,
                                    onClick = { actions.choose(i) },
                                )
                            }
                        }
                    }
                    is AdminSheet.Input -> {
                        Text(sheet.title, NikoType.headline, c.ink, Modifier.padding(horizontal = NikoSpace.s5))
                        if (sheet.help != null) SheetText(sheet.help)
                        if (sheet.error != null) SheetText("⚠ " + sheet.error, danger = true)
                        Column(Modifier.padding(horizontal = NikoSpace.s5), verticalArrangement = Arrangement.spacedBy(NikoSpace.s4)) {
                            sheet.fields.forEachIndexed { i, f ->
                                Field(
                                    f.label, f.value, { actions.input(i, it) }, input = f.input, multiLine = f.multiLine,
                                    onGo = if (f.multiLine || sheet.fields.size > 1) null else actions::submit,
                                )
                            }
                        }
                        SheetButtons(stringResource(R.string.ad_ok), actions::submit, actions::dismiss)
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetText(text: String, ink: Boolean = false, danger: Boolean = false) {
    val c = Niko.colors
    Text(text, if (ink) NikoType.body else NikoType.bodySm, if (danger) c.danger else if (ink) c.ink else c.ink2, Modifier.padding(horizontal = NikoSpace.s5))
}

/** The sheet's answer in ink, and the way out of it beside ([onCancel] null: there is only one answer). */
@Composable
private fun SheetButtons(yes: String, onYes: () -> Unit, onCancel: (() -> Unit)?) {
    val c = Niko.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = NikoSpace.s5), horizontalArrangement = Arrangement.spacedBy(NikoSpace.s3)) {
        if (onCancel != null) {
            val source = remember { MutableInteractionSource() }
            Box(
                Modifier.weight(1f).height(NikoSize.control).clip(CircleShape).background(if (pressed(source)) c.surface3 else c.surface2)
                    .clickable(source, indication = null, role = Role.Button, onClick = onCancel),
                contentAlignment = Alignment.Center,
            ) {
                Text(stringResource(R.string.ad_cancel), NikoType.body.copy(fontWeight = NikoType.headline.fontWeight, textAlign = TextAlign.Center), c.ink, maxLines = 1)
            }
        }
        PrimaryButton(yes, onYes, Modifier.weight(1f))
    }
}

package io.niko.ui.group

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.niko.GroupOrder
import io.niko.GroupType
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.ListRow
import io.niko.ui.home.Section
import io.niko.ui.home.Text
import io.niko.ui.settings.Choice
import io.niko.ui.settings.ChoiceSheet
import io.niko.ui.settings.Field
import io.niko.ui.settings.FieldButton
import io.niko.ui.settings.IconButton
import io.niko.ui.settings.Segmented
import io.niko.ui.settings.SwitchRow
import io.niko.ui.settings.ValueRow

/** What the group's screen asks beyond editing the form. */
interface GroupActions {
    /** leave: what was changed is kept (a new group made, unless nothing was typed) */
    fun back()
    fun share()
    fun delete()
    fun copyLink(link: String)
    /** a profile for the front (true) or landing proxy, picked in the old list */
    fun pickProxy(front: Boolean)
}

/** What the screen shows that is not the form: whether the group is there yet, its node, the proxies' names. */
class GroupInfo(
    val existing: Boolean,
    /** a raycontrol node's host: the group's type is the node's (basic), its rows come from the node */
    val nodeHost: String? = null,
    val frontName: String? = null,
    val landingName: String? = null,
)

/**
 * P-SubSettings (D-SUB-08, D-SUB-09, D-GROUP-01): the name, a subscription's link, its update and request, then the
 * group's own: duplicates, the choice of its config, its order, the front and landing proxies. Every change is in
 * [form] at once; it is stored when the screen is left (the activity does that).
 */
@Composable
fun GroupScreen(form: GroupForm, info: GroupInfo, onForm: (GroupForm) -> Unit, actions: GroupActions) {
    val c = Niko.colors
    var choosing by rememberSaveable { mutableStateOf<String?>(null) }
    BackHandler(onBack = actions::back)
    Box(Modifier.fillMaxSize().background(c.surface0)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s1),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
            ) {
                IconButton(R.drawable.ic_l_arrow_left, stringResource(R.string.gs_back), onClick = actions::back)
                val title = form.name.ifBlank { stringResource(if (info.existing) R.string.gs_group else R.string.gs_new) }
                Text(title, NikoType.headline, c.ink, Modifier.weight(1f).padding(horizontal = NikoSpace.s2), maxLines = 1)
                if (info.existing) {
                    IconButton(R.drawable.ic_l_share_2, stringResource(R.string.gs_share), onClick = actions::share)
                    IconButton(R.drawable.ic_l_trash_2, stringResource(R.string.gs_delete), onClick = actions::delete)
                }
            }
            Column(
                Modifier.weight(1f).verticalScroll(rememberScrollState()).navigationBarsPadding()
                    .padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s8),
                verticalArrangement = Arrangement.spacedBy(NikoSpace.s6),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s4)) {
                    Field(stringResource(R.string.gs_name), form.name, { onForm(form.copy(name = it)) })
                    if (info.nodeHost != null) {
                        Text(stringResource(R.string.raycontrol_group_locked, info.nodeHost), NikoType.bodySm, c.ink2)
                    } else {
                        Segmented(
                            stringResource(R.string.gs_kind),
                            listOf(GroupType.BASIC to stringResource(R.string.gs_kind_own), GroupType.SUBSCRIPTION to stringResource(R.string.gs_kind_subscription)),
                            form.type,
                        ) { onForm(form.copy(type = it)) }
                    }
                    if (form.subscription) {
                        Field(
                            stringResource(R.string.gs_link), form.link, { onForm(form.copy(link = it)) }, mono = true,
                            button = FieldButton(R.drawable.ic_l_link, stringResource(R.string.gs_copy_link)) { actions.copyLink(form.link) }
                                .takeIf { form.link.isNotBlank() },
                        )
                    }
                }
                if (form.subscription) {
                    Section(stringResource(R.string.gs_update)) {
                        SwitchRow(stringResource(R.string.gs_auto_update), form.autoUpdate, { onForm(form.copy(autoUpdate = it)) })
                        ValueRow(
                            stringResource(R.string.gs_interval), interval(form.autoUpdateDelay), divider = true,
                            onClick = { choosing = INTERVAL }, enabled = form.autoUpdate,
                        )
                        SwitchRow(
                            stringResource(R.string.gs_connected_only), form.whenConnectedOnly, { onForm(form.copy(whenConnectedOnly = it)) },
                            divider = true,
                        )
                    }
                    Section(stringResource(R.string.gs_request)) {
                        Column(Modifier.padding(NikoSpace.s4)) {
                            Field("User-Agent", form.userAgent, { onForm(form.copy(userAgent = it)) }, mono = true)
                            if (form.userAgent.isBlank()) Text(stringResource(R.string.gs_user_agent_default), NikoType.bodySm, c.ink2, Modifier.padding(top = NikoSpace.s2))
                        }
                        SwitchRow("HWID", form.sendHwid, { onForm(form.copy(sendHwid = it)) }, divider = true, sub = stringResource(R.string.gs_hwid_sub))
                        SwitchRow(
                            stringResource(R.string.gs_force_resolve), form.forceResolve, { onForm(form.copy(forceResolve = it)) },
                            divider = true,
                        )
                    }
                }
                Section(stringResource(R.string.gs_group)) {
                    var first = true
                    if (form.subscription) {
                        SwitchRow(stringResource(R.string.gs_dedup), form.deduplication, { onForm(form.copy(deduplication = it)) })
                        first = false
                    }
                    ListRow(
                        stringResource(R.string.gs_pick),
                        divider = !first,
                        trail = {
                            Segmented(
                                null,
                                listOf(false to stringResource(R.string.gs_pick_manual), true to stringResource(R.string.gs_pick_fastest)),
                                form.autoSelect,
                            ) { onForm(form.copy(autoSelect = it)) }
                        },
                    )
                    ValueRow(stringResource(R.string.gs_order), orderName(form.order), divider = true, onClick = { choosing = ORDER })
                    SwitchRow(stringResource(R.string.use_selector), form.selector, { onForm(form.copy(selector = it)) }, divider = true)
                    ValueRow("Front proxy", info.frontName ?: "—", divider = true, onClick = { choosing = FRONT })
                    ValueRow("Landing proxy", info.landingName ?: "—", divider = true, onClick = { choosing = LANDING })
                }
            }
        }
        when (choosing) {
            INTERVAL -> {
                val values = (GroupForm.DELAYS + form.autoUpdateDelay).distinct().sorted()
                val choice = Choice(stringResource(R.string.gs_interval), INTERVAL, values.map { it.toString() to interval(it) })
                ChoiceSheet(choice, form.autoUpdateDelay.toString(), { picked ->
                    choosing = null
                    onForm(form.copy(autoUpdateDelay = picked.toInt()))
                }) { choosing = null }
            }
            ORDER -> {
                val orders = listOf(GroupOrder.ORIGIN, GroupOrder.BY_NAME, GroupOrder.BY_DELAY)
                val choice = Choice(stringResource(R.string.gs_order), ORDER, orders.map { it.toString() to orderName(it) })
                ChoiceSheet(choice, form.order.toString(), { picked ->
                    choosing = null
                    onForm(form.copy(order = picked.toInt()))
                }) { choosing = null }
            }
            FRONT, LANDING -> {
                val front = choosing == FRONT
                val set = if (front) form.frontProxy >= 0 else form.landingProxy >= 0
                val choice = Choice(
                    if (front) "Front proxy" else "Landing proxy", choosing!!,
                    listOf(NONE to stringResource(R.string.gs_proxy_none), PICK to stringResource(R.string.gs_proxy_pick)),
                )
                ChoiceSheet(choice, if (set) PICK else NONE, { picked ->
                    choosing = null
                    when {
                        picked == PICK -> actions.pickProxy(front)
                        front -> onForm(form.copy(frontProxy = -1))
                        else -> onForm(form.copy(landingProxy = -1))
                    }
                }) { choosing = null }
            }
        }
    }
}

private const val INTERVAL = "interval"
private const val ORDER = "order"
private const val FRONT = "front"
private const val LANDING = "landing"
private const val NONE = "none"
private const val PICK = "pick"

/** An interval in minutes, in the largest whole unit: "12 h", "30 min", "1 d". */
@Composable
private fun interval(minutes: Int): String = when {
    minutes % 1440 == 0 -> pluralStringResource(R.plurals.gs_days, minutes / 1440, minutes / 1440)
    minutes % 60 == 0 -> pluralStringResource(R.plurals.gs_hours, minutes / 60, minutes / 60)
    else -> pluralStringResource(R.plurals.gs_minutes, minutes, minutes)
}

@Composable
private fun orderName(order: Int) = stringResource(
    when (order) {
        GroupOrder.BY_NAME -> R.string.gs_order_name
        GroupOrder.BY_DELAY -> R.string.gs_order_delay
        else -> R.string.gs_order_origin
    }
)

package io.niko.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.zIndex
import io.niko.R
import io.niko.fmt.DnsTunnel
import io.niko.fmt.parseCertificatePins
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.Icon
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Sheet
import io.niko.ui.home.Tag
import io.niko.ui.home.Text
import io.niko.ui.home.Trail
import io.niko.ui.settings.Choice
import io.niko.ui.settings.ChoiceSheet
import io.niko.ui.settings.IconButton
import io.niko.ui.settings.NarrowField
import io.niko.ui.settings.Segmented
import io.niko.ui.settings.ValueRow

/** The editor's pages beyond the type's own sections' ([Section.page]): the tweaks and the three of their rows. */
object ProfilePage {
    const val MAIN = "main"
    const val TWEAKS = "tweaks"
    const val PINS = "pins"
    const val TUNNEL = "tunnel"
    const val QUIC = "quic"

    /** The page back goes to from [page]. */
    fun parent(page: String) = when (page) {
        PINS, TUNNEL, QUIC -> TWEAKS
        else -> MAIN
    }

    /** The page the tweaks' field [key] is on. */
    fun of(key: String) = when (key) {
        KNOCK_SEQUENCE -> TWEAKS
        CERT_PINS -> PINS
        DNS_DOMAIN -> TUNNEL
        PRELUDE_SNI -> QUIC
        else -> MAIN
    }
}

/** m-fields-row: fields side by side, their controls on one line whatever their labels' lines. */
@Composable
private fun PairRow(content: @Composable RowScope.() -> Unit) =
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(NikoSpace.s3), verticalAlignment = Alignment.Bottom, content = content)

/** A chain's link as its row shows it: the profile's name and type. */
class ChainLink(val name: String, val type: String)

/**
 * What the editor shows that is not the form: whether the profile is there yet, the groups it may move to (none: no
 * «Переместить»), a chain's links' names, the error that stopped the last save.
 */
class ProfileInfo(
    val existing: Boolean,
    val groups: List<Pair<Long, String>> = emptyList(),
    val links: Map<Long, ChainLink> = emptyMap(),
    val error: FieldError? = null,
)

/** The JSON a row of the editor opens in ConfigEditActivity. */
enum class JsonTarget { OUTBOUND, CONFIG, CUSTOM }

/** What P-Profile asks beyond editing the form. */
interface ProfileActions {
    /** leave: unsaved changes are asked about */
    fun back()
    fun save()
    fun delete()
    fun move(groupId: Long)
    fun shortcut()
    fun editJson(target: JsonTarget)
    /** a profile to add to the chain, or (index ≥ 0) to put in place of that link (ProfileSelectActivity) */
    fun pickLink(replace: Int)
}

/**
 * Профиль (P-Profile, D-PROFILE-01…04, D-PROFILE-08, M-PROFILE-01): the type's fields from its [spec], «Подключение»
 * (a section's own page, «Тонкая настройка», the custom JSON), the shortcut; «Сохранить» at the bottom, the move and
 * the delete in ⋮. [page] the main page or one a row opened.
 */
@Composable
fun ProfileScreen(
    spec: ProfileSpec,
    form: ProfileForm,
    info: ProfileInfo,
    page: String,
    onPage: (String) -> Unit,
    onForm: (ProfileForm) -> Unit,
    actions: ProfileActions,
    initialScroll: Int = 0,
) {
    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    // the main page's place is kept while a page it opened is shown
    val mainScroll = rememberScrollState(initialScroll)
    val up = { onPage(ProfilePage.parent(page)) }
    @Composable
    fun Page(title: String, content: @Composable ColumnScope.() -> Unit) = Page(title, up, rememberScrollState(initialScroll), content)
    Box(Modifier.fillMaxSize()) {
        when (page) {
            ProfilePage.MAIN -> Main(spec, form, info, mainScroll, onPage, onForm, actions) { sheet = it }
            ProfilePage.TWEAKS -> Page(stringResource(R.string.pe_tweaks)) { TweaksPage(form, info, onPage, onForm) }
            ProfilePage.PINS -> Page(stringResource(R.string.cert_pins)) { PinsPage(form, info, onForm) }
            ProfilePage.TUNNEL -> Page(stringResource(R.string.pe_dns_tunnel)) { TunnelPage(form, info, onForm) }
            ProfilePage.QUIC -> Page(stringResource(R.string.udp_desync)) { QuicPage(form, info, onForm) }
            else -> {
                val section = spec.sections.firstOrNull { it.page == page }
                if (section == null) {
                    Main(spec, form, info, mainScroll, onPage, onForm, actions) { sheet = it }
                } else {
                    Page(section.title?.text().orEmpty()) {
                        Items(section.items, form, info, onForm, actions) { sheet = it }
                    }
                }
            }
        }
        when (val open = sheet) {
            null -> {}
            MENU -> MenuSheet(info, { sheet = null }, { sheet = MOVE }, actions)
            MOVE -> MoveSheet(info, { sheet = null }, actions)
            else -> {
                val item = spec.items.firstOrNull { it is ChoiceItem && it.key == open } as ChoiceItem?
                if (item != null) {
                    BackHandler { sheet = null }
                    val choice = Choice(item.title.text(), item.key, item.options.map { (v, t) -> v to t.text() })
                    ChoiceSheet(choice, form.text(item.key), { picked ->
                        sheet = null
                        onForm(form.withText(item.key, picked))
                    }) { sheet = null }
                }
            }
        }
    }
}

private const val MENU = "\u0000menu"
private const val MOVE = "\u0000move"

@Composable
private fun Main(
    spec: ProfileSpec,
    form: ProfileForm,
    info: ProfileInfo,
    scroll: ScrollState,
    onPage: (String) -> Unit,
    onForm: (ProfileForm) -> Unit,
    actions: ProfileActions,
    onSheet: (String) -> Unit,
) {
    val c = Niko.colors
    BackHandler(onBack = actions::back)
    Column(Modifier.fillMaxSize().background(c.surface0).statusBarsPadding()) {
        TopBar(spec.title.text(), actions::back) {
            if (info.existing) IconButton(R.drawable.ic_l_ellipsis_vertical, stringResource(R.string.pe_more)) { onSheet(MENU) }
        }
        Column(
            Modifier.weight(1f).verticalScroll(scroll)
                .padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(NikoSpace.s6),
        ) {
            for (section in spec.sections) {
                if (section.page != null || !section.visible(form)) continue
                val visible = section.items.filter { it.visible(form) }
                if (visible.isEmpty()) continue
                Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s3)) {
                    section.title?.let { Overline(it.text(), inset = visible.all { item -> item.isRow() || item is ChainItem }) }
                    Items(visible, form, info, onForm, actions, onSheet)
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s3)) {
                Overline(stringResource(R.string.pe_connection), inset = true)
                ListGroup {
                    var divider = false
                    for (section in spec.sections) {
                        val target = section.page ?: continue
                        if (!section.visible(form)) continue
                        ValueRow(section.title?.text().orEmpty(), section.summary?.invoke(form)?.text(), divider, { onPage(target) })
                        divider = true
                    }
                    if (spec.tweaks) {
                        ListRow(
                            stringResource(R.string.pe_tweaks),
                            divider = divider,
                            titleLines = 2,
                            sub = { Text(stringResource(R.string.pe_tweaks_sub), NikoType.bodySm, c.ink2, maxLines = 1) },
                            trail = { Trail(tweaksSummary(form.tweaks)) },
                            onClick = { onPage(ProfilePage.TWEAKS) },
                        )
                        divider = true
                    }
                    ValueRow(stringResource(R.string.pe_json_outbound), lines(form.outboundJson), divider, { actions.editJson(JsonTarget.OUTBOUND) })
                    ValueRow(stringResource(R.string.pe_json_config), lines(form.configJson), true, { actions.editJson(JsonTarget.CONFIG) })
                }
            }
            if (info.existing) {
                ListGroup {
                    ListRow(
                        stringResource(R.string.pe_shortcut),
                        titleLines = 2,
                        trail = { IconButton(R.drawable.ic_l_plus, stringResource(R.string.pe_shortcut_add), muted = true, small = true, onClick = actions::shortcut) },
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s3, bottom = NikoSpace.s6)) {
            PrimaryButton(stringResource(R.string.pe_save), actions::save, Modifier.fillMaxWidth())
        }
    }
}

/** m-topbar: the way back, [title], [content] (its icon buttons) at its end. */
@Composable
private fun TopBar(title: String, onBack: () -> Unit, content: @Composable () -> Unit = {}) {
    val c = Niko.colors
    Row(
        Modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
    ) {
        IconButton(R.drawable.ic_l_arrow_left, stringResource(R.string.screen_back), onClick = onBack)
        Text(title, NikoType.headline, c.ink, Modifier.weight(1f).padding(horizontal = NikoSpace.s2), maxLines = 1)
        content()
    }
}

/** A page a row opened: its top bar (back goes [up]), its parts space-6 apart. */
@Composable
private fun Page(title: String, up: () -> Unit, scroll: ScrollState = rememberScrollState(), content: @Composable ColumnScope.() -> Unit) {
    BackHandler(onBack = up)
    Column(Modifier.fillMaxSize().background(Niko.colors.surface0).statusBarsPadding()) {
        TopBar(title, up)
        Column(
            Modifier.weight(1f).verticalScroll(scroll).navigationBarsPadding()
                .padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(NikoSpace.s6),
            content = content,
        )
    }
}

/** m-overline: a section's name in capitals, [inset] as an m-list's rows are (over fields, at their edge). */
@Composable
private fun Overline(text: String, inset: Boolean = false) =
    Text(text.uppercase(), NikoType.label, Niko.colors.ink2, Modifier.padding(horizontal = if (inset) NikoSpace.s4 else 0.dp))

private fun Item.isRow() = this is SwitchItem || this is JsonItem || (this is ChoiceItem && sheet)

@Composable
private fun Help(text: String) = Text(text, NikoType.caption, Niko.colors.ink2)

/** "N строк" of a JSON, «нет» for none. */
@Composable
private fun lines(json: String) =
    if (json.isBlank()) stringResource(R.string.pe_none_lower) else stringResource(R.string.lines, json.split('\n').size)

@Composable
private fun errorText(error: FieldError?, key: String): String? {
    if (error == null || error.key != key) return null
    return if (error.res != 0) stringResource(error.res, error.arg.orEmpty()) else error.arg.orEmpty()
}

/** Items the way the pattern draws them: fields and segmented choices alone, rows together in an m-list. */
@Composable
private fun Items(
    items: List<Item>,
    form: ProfileForm,
    info: ProfileInfo,
    onForm: (ProfileForm) -> Unit,
    actions: ProfileActions,
    onSheet: (String) -> Unit,
) {
    val visible = items.filter { it.visible(form) }
    val runs = mutableListOf<MutableList<Item>>()
    for (item in visible) {
        val last = runs.lastOrNull()
        if (item.isRow() && last != null && last.first().isRow()) last += item
        else runs += mutableListOf(item)
    }
    Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s4)) {
        for (run in runs) {
            val first = run.first()
            when {
                first.isRow() -> ListGroup {
                    run.forEachIndexed { i, item -> RowItem(item, form, i > 0, actions, onForm, onSheet) }
                }
                first is ChainItem -> ChainRows(form, info, onForm, actions)
                else -> FieldItem(first, form, info, onForm)
            }
        }
    }
}

@Composable
private fun RowItem(item: Item, form: ProfileForm, divider: Boolean, actions: ProfileActions, onForm: (ProfileForm) -> Unit, onSheet: (String) -> Unit) {
    when (item) {
        is SwitchItem -> ToggleRow(item.title.text(), form.flag(item.key), { onForm(form.withFlag(item.key, it)) }, divider, item.sub?.text())
        is JsonItem -> ValueRow(item.title.text(), lines(form.text(item.key)), divider, { actions.editJson(JsonTarget.CUSTOM) })
        is ChoiceItem -> {
            val value = form.text(item.key)
            val name = item.options.firstOrNull { it.first == value }?.second?.text() ?: value
            ListRow(
                item.title.text(),
                divider = divider,
                titleLines = 2,
                // the value's name cut to a part of the row: the title stays readable beside a long one
                trail = {
                    Text(name, NikoType.bodySm, Niko.colors.ink2, Modifier.widthIn(max = 140.dp), maxLines = 1)
                    Icon(R.drawable.ic_l_chevron_right, Niko.colors.ink2)
                },
                onClick = { onSheet(item.key) },
            )
        }
        else -> {}
    }
}

@Composable
private fun FieldItem(item: Item, form: ProfileForm, info: ProfileInfo, onForm: (ProfileForm) -> Unit) {
    when (item) {
        is TextItem -> TextFieldItem(item, form, info, onForm, Modifier.fillMaxWidth())
        is PairItem -> PairRow {
            TextFieldItem(item.first, form, info, onForm, Modifier.weight(1f))
            TextFieldItem(item.second, form, info, onForm, if (item.equal) Modifier.weight(1f) else Modifier.width(NarrowField))
        }
        is ChoiceItem -> Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
            Segmented(item.title.text(), item.options.map { (v, t) -> v to t.text() }, form.text(item.key), block = true) {
                onForm(form.withText(item.key, it))
            }
            item.help?.let { Help(it.text()) }
        }
        else -> {}
    }
}

@Composable
private fun TextFieldItem(item: TextItem, form: ProfileForm, info: ProfileInfo, onForm: (ProfileForm) -> Unit, modifier: Modifier) {
    ProfileField(
        (item.labelOf?.invoke(form) ?: item.label).text(),
        form.text(item.key),
        { onForm(form.withText(item.key, it)) },
        modifier,
        input = item.input,
        mono = item.mono,
        lines = item.lines,
        placeholder = item.placeholder?.text(),
        help = item.help?.text(),
        error = errorText(info.error, item.key),
        enabled = item.enabled(form),
    )
}

/** Each link's key for its row: its id, and which of the same profile's links it is (one may come twice). */
internal fun linkKeys(chain: List<Long>): List<String> {
    val seen = mutableMapOf<Long, Int>()
    return chain.map { id -> "$id#" + seen.merge(id, 1, Int::plus) }
}

/** The links of a chain in their order (a drag of the grip moves one), then «Добавить профиль». */
@Composable
private fun ChainRows(form: ProfileForm, info: ProfileInfo, onForm: (ProfileForm) -> Unit, actions: ProfileActions) {
    val c = Niko.colors
    val current by rememberUpdatedState(form)
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val heights = remember { mutableStateMapOf<String, Int>() }
    val upName = stringResource(R.string.pe_move_up)
    val downName = stringResource(R.string.pe_move_down)
    fun move(from: Int, to: Int) {
        val list = current.chain.toMutableList()
        if (from !in list.indices || to !in list.indices) return
        list.add(to, list.removeAt(from))
        onForm(current.copy(chain = list))
    }
    val keys = linkKeys(form.chain)
    ListGroup {
        form.chain.forEachIndexed { i, id ->
            val rowKey = keys[i]
            key(rowKey) {
                val link = info.links[id]
                val name = link?.name ?: "#$id"
                val dragged = dragging == rowKey
                Box(
                    Modifier.zIndex(if (dragged) 1f else 0f).graphicsLayer { translationY = if (dragged) dragY else 0f }
                        .onSizeChanged { heights[rowKey] = it.height },
                ) {
                    val grip = Modifier.pointerInput(rowKey) {
                        detectVerticalDragGestures(
                            onDragStart = { dragging = rowKey; dragY = 0f },
                            onDragEnd = { dragging = null; dragY = 0f },
                            onDragCancel = { dragging = null; dragY = 0f },
                        ) { change, dy ->
                            change.consume()
                            dragY += dy
                            val list = linkKeys(current.chain)
                            val at = list.indexOf(rowKey)
                            if (at < 0) return@detectVerticalDragGestures
                            if (dragY > 0 && at < list.lastIndex) {
                                val next = heights[list[at + 1]] ?: return@detectVerticalDragGestures
                                if (dragY > next / 2f) {
                                    move(at, at + 1)
                                    dragY -= next
                                }
                            } else if (dragY < 0 && at > 0) {
                                val previous = heights[list[at - 1]] ?: return@detectVerticalDragGestures
                                if (-dragY > previous / 2f) {
                                    move(at, at - 1)
                                    dragY += previous
                                }
                            }
                        }
                    }.semantics {
                        customActions = listOfNotNull(
                            CustomAccessibilityAction(upName) { move(i, i - 1); true }.takeIf { i > 0 },
                            CustomAccessibilityAction(downName) { move(i, i + 1); true }.takeIf { i < form.chain.lastIndex },
                        )
                    }
                    ListRow(
                        name,
                        Modifier.background(if (dragged) c.surface2 else Color.Transparent),
                        divider = i > 0 && !dragged,
                        lead = {
                            val drag = stringResource(R.string.pe_drag, name)
                            Box(grip.size(NikoSize.icon, NikoSize.tap).semantics { contentDescription = drag }, contentAlignment = Alignment.Center) {
                                Icon(R.drawable.ic_l_menu, c.ink2)
                            }
                            Text((i + 1).toString(), NikoType.code, c.ink2, maxLines = 1)
                        },
                        sub = {
                            if (link != null) Tag(link.type)
                            if (i == form.chain.lastIndex) Text(stringResource(R.string.pe_chain_exit), NikoType.bodySm, c.ink2, maxLines = 1)
                        },
                        trail = {
                            IconButton(R.drawable.ic_l_x, stringResource(R.string.pe_chain_remove, name), muted = true, small = true) {
                                onForm(form.copy(chain = form.chain.filterIndexed { at, _ -> at != i }))
                            }
                        },
                        onClick = { actions.pickLink(i) },
                    )
                }
            }
        }
        ListRow(
            stringResource(R.string.add_profile),
            divider = form.chain.isNotEmpty(),
            lead = { Icon(R.drawable.ic_l_plus, c.ink2, NikoSize.icon) },
            onClick = { actions.pickLink(-1) },
        )
    }
}

/** The tweaks' row's value: what of them is on, «нет» for nothing. */
@Composable
private fun tweaksSummary(t: Tweaks): String? {
    val on = buildList {
        if (t.tlsFragment in 1..2) add("TLS")
        if (t.certPins.isNotBlank()) add(stringResource(R.string.pe_pins_short))
        if (t.dnsDomain.isNotBlank() && t.dnsEngine.isNotEmpty()) add("DNS")
        if ((t.udpPrelude.toIntOrNull() ?: 0) > 0 || t.udpReorderInitial) add("QUIC")
        if (t.knockSequence.isNotBlank()) add(stringResource(R.string.pe_knock_short))
    }
    return if (on.isEmpty()) null else on.joinToString(" · ")
}

/** «Тонкая настройка» (D-PROFILE-03): TLS fragmentation, the pins, the DNS tunnel, QUIC, port knocking. */
@Composable
private fun TweaksPage(form: ProfileForm, info: ProfileInfo, onPage: (String) -> Unit, onForm: (ProfileForm) -> Unit) {
    val t = form.tweaks
    val set = { changed: Tweaks -> onForm(form.copy(tweaks = changed)) }
    Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
        Segmented(
            stringResource(R.string.tls_fragment),
            listOf(0 to stringResource(R.string.pe_none), 1 to stringResource(R.string.pe_fragment_records), 2 to "TCP"),
            if (t.tlsFragment in 1..2) t.tlsFragment else 0, block = true,
        ) { set(t.copy(tlsFragment = it)) }
        Help(stringResource(R.string.tls_fragment_summary))
    }
    ListGroup {
        val pins = runCatching { parseCertificatePins(t.certPins).size }.getOrNull()
        ValueRow(
            stringResource(R.string.cert_pins),
            when {
                t.certPins.isBlank() -> stringResource(R.string.pe_none_lower)
                pins != null -> pluralStringResource(R.plurals.pe_pins, pins, pins)
                else -> stringResource(R.string.error_title)
            },
            false, { onPage(ProfilePage.PINS) },
        )
        val engine = when {
            t.slipstream -> "slipstream"
            t.dnsDomain.isBlank() -> stringResource(R.string.pe_tunnel_off)
            t.shownEngine == DnsTunnel.MASTERDNSVPN -> "MasterDnsVPN"
            else -> "dnstt"
        }
        ValueRow(stringResource(R.string.pe_dns_tunnel), engine, true, { onPage(ProfilePage.TUNNEL) })
        val quic = (t.udpPrelude.toIntOrNull() ?: 0) > 0 || t.udpReorderInitial
        ValueRow(stringResource(R.string.udp_desync), stringResource(if (quic) R.string.pe_on else R.string.pe_off), true, { onPage(ProfilePage.QUIC) })
    }
    Card(stringResource(R.string.port_knocking)) {
        ProfileField(
            stringResource(R.string.pe_knock_sequence), t.knockSequence, { set(t.copy(knockSequence = it)) }, Modifier.fillMaxWidth(),
            mono = true, placeholder = stringResource(R.string.pe_knock_placeholder), error = errorText(info.error, KNOCK_SEQUENCE),
        )
        PairRow {
            ProfileField(stringResource(R.string.port_knocking_interval), t.knockInterval, { set(t.copy(knockInterval = it)) }, Modifier.weight(1f), input = Input.NUMBER, mono = true)
            ProfileField(stringResource(R.string.port_knocking_ttl), t.knockTtl, { set(t.copy(knockTtl = it)) }, Modifier.weight(1f), input = Input.NUMBER, mono = true)
        }
        ProfileField(stringResource(R.string.port_knocking_silence), t.knockSilence, { set(t.copy(knockSilence = it)) }, Modifier.fillMaxWidth(), input = Input.NUMBER, mono = true)
        Help(stringResource(R.string.port_knocking_summary))
    }
}

@Composable
private fun PinsPage(form: ProfileForm, info: ProfileInfo, onForm: (ProfileForm) -> Unit) {
    val t = form.tweaks
    ProfileField(
        stringResource(R.string.cert_pins_hint), t.certPins, { onForm(form.copy(tweaks = t.copy(certPins = it))) }, Modifier.fillMaxWidth(),
        mono = true, lines = true, help = stringResource(R.string.cert_pins_summary), error = errorText(info.error, CERT_PINS),
    )
}

@Composable
private fun TunnelPage(form: ProfileForm, info: ProfileInfo, onForm: (ProfileForm) -> Unit) {
    val t = form.tweaks
    val set = { changed: Tweaks -> onForm(form.copy(tweaks = changed)) }
    // a slipstream tunnel comes from its link (raycontrol slipstream-v1) and has no editor here: shown, kept as it is
    val locked = t.slipstream
    if (locked) Text(stringResource(R.string.pe_tunnel_slipstream), NikoType.bodySm, Niko.colors.ink2)
    if (!locked) {
        Segmented(
            stringResource(R.string.pe_tunnel_engine), listOf(DnsTunnel.DNSTT to "dnstt", DnsTunnel.MASTERDNSVPN to "MasterDnsVPN"), t.shownEngine, block = true,
        ) { if (it != t.shownEngine) set(t.copy(dnsEngine = it)) }
    }
    ProfileField(
        stringResource(R.string.pe_tunnel_resolver), t.dnsResolver, { set(t.copy(dnsResolver = it)) }, Modifier.fillMaxWidth(),
        mono = true, lines = true, help = stringResource(R.string.dns_tunnel_resolver), enabled = !locked,
    )
    ProfileField(
        stringResource(R.string.dns_tunnel_domain), t.dnsDomain, { set(t.copy(dnsDomain = it)) }, Modifier.fillMaxWidth(),
        mono = true, error = errorText(info.error, DNS_DOMAIN), enabled = !locked,
    )
    ProfileField(
        stringResource(R.string.pe_tunnel_key), t.dnsKey, { set(t.copy(dnsKey = it)) }, Modifier.fillMaxWidth(),
        mono = true, help = stringResource(R.string.dns_tunnel_key), enabled = !locked,
    )
    // the MasterDnsVPN settings only show with that engine
    if (!locked && t.shownEngine == DnsTunnel.MASTERDNSVPN) {
        ProfileField(
            stringResource(R.string.pe_tunnel_method), t.dnsMethod, { set(t.copy(dnsMethod = it)) }, Modifier.fillMaxWidth(),
            input = Input.NUMBER, mono = true, help = stringResource(R.string.dns_tunnel_method),
        )
        ListGroup {
            ToggleRow(stringResource(R.string.dns_tunnel_fixed_upstream), t.dnsFixedUpstream, { set(t.copy(dnsFixedUpstream = it)) }, false)
        }
    }
    Help(stringResource(R.string.dns_tunnel_summary))
}

@Composable
private fun QuicPage(form: ProfileForm, info: ProfileInfo, onForm: (ProfileForm) -> Unit) {
    val t = form.tweaks
    val set = { changed: Tweaks -> onForm(form.copy(tweaks = changed)) }
    ProfileField(stringResource(R.string.udp_prelude), t.udpPrelude, { set(t.copy(udpPrelude = it)) }, Modifier.fillMaxWidth(), input = Input.NUMBER, mono = true)
    ProfileField(
        stringResource(R.string.udp_prelude_sni), t.udpPreludeSni, { set(t.copy(udpPreludeSni = it)) }, Modifier.fillMaxWidth(),
        mono = true, error = errorText(info.error, PRELUDE_SNI),
    )
    ProfileField(stringResource(R.string.udp_prelude_ttl), t.udpPreludeTtl, { set(t.copy(udpPreludeTtl = it)) }, Modifier.fillMaxWidth(), input = Input.NUMBER, mono = true)
    ListGroup {
        ToggleRow(stringResource(R.string.udp_reorder_initial), t.udpReorderInitial, { set(t.copy(udpReorderInitial = it)) }, false)
    }
    Help(stringResource(R.string.udp_desync_summary))
}

/** ⋮: move the profile to another of the user's groups (when there is one), delete it. */
@Composable
private fun MenuSheet(info: ProfileInfo, onDismiss: () -> Unit, onMove: () -> Unit, actions: ProfileActions) {
    SheetOver(onDismiss) {
        if (info.groups.isNotEmpty()) {
            ListRow(
                stringResource(R.string.pe_move), side = NikoSpace.s5,
                lead = { Icon(R.drawable.ic_l_folder, Niko.colors.ink2, NikoSize.icon) }, onClick = onMove,
            )
        }
        ListRow(
            stringResource(R.string.pe_delete), side = NikoSpace.s5, divider = info.groups.isNotEmpty(),
            lead = { Icon(R.drawable.ic_l_trash_2, Niko.colors.ink2, NikoSize.icon) },
            onClick = { onDismiss(); actions.delete() },
        )
    }
}

/** The groups the profile may move to; a press moves it there. */
@Composable
private fun MoveSheet(info: ProfileInfo, onDismiss: () -> Unit, actions: ProfileActions) {
    SheetOver(onDismiss, stringResource(R.string.pe_move)) {
        info.groups.forEachIndexed { i, (id, name) ->
            ListRow(
                name, side = NikoSpace.s5, divider = i > 0, titleLines = 2,
                lead = { Icon(R.drawable.ic_l_folder, Niko.colors.ink2, NikoSize.icon) },
                onClick = { onDismiss(); actions.move(id) },
            )
        }
    }
}

/** m-sheet over a scrim, [title] on top; a press outside it closes it. */
@Composable
private fun SheetOver(onDismiss: () -> Unit, title: String? = null, content: @Composable ColumnScope.() -> Unit) {
    val c = Niko.colors
    BackHandler(onBack = onDismiss)
    Box(
        Modifier.fillMaxSize().background(c.scrim).clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { }.navigationBarsPadding()) {
            if (title != null) Text(title, NikoType.headline, c.ink, Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s2))
            Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = NikoSpace.s6), content = content)
        }
    }
}

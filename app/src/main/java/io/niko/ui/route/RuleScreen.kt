package io.niko.ui.route

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoSize
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Section
import io.niko.ui.home.Sub
import io.niko.ui.home.Text
import io.niko.ui.settings.Field
import io.niko.ui.settings.IconButton
import io.niko.ui.settings.ListPage
import io.niko.ui.settings.Segmented
import io.niko.ui.settings.SwitchRow
import io.niko.ui.settings.ValueRow

/** The rule editor's pages: the rule, and the conditions' own (И ещё условия). */
enum class RulePage { MAIN, DOMAINS, ADDRESSES, PORTS }

/** What the editor shows that is not the form: whether the rule is there yet, its profile's name, its apps' labels. */
class RuleInfo(
    val existing: Boolean,
    val profileName: String? = null,
    /** package → label, for those known; an app not installed shows by its package */
    val labels: Map<String, String> = emptyMap(),
)

/** What P-Rule asks beyond editing the form. */
interface RuleActions {
    /** leave: unsaved changes are asked about */
    fun back()
    fun save()
    fun delete()
    /** the apps, in the app picker (AppsActivity) */
    fun pickApps()
    /** a profile to send it through (ProfileSelectActivity) */
    fun pickProfile()
    /** the rule's own JSON (ConfigEditActivity) */
    fun editJson()
}

/**
 * Правило (P-Rule, D-RULES-01, D-RULES-02): the name, the apps, the other conditions each on its page (domains;
 * addresses; ports, network and protocol; the rule's JSON), the action (through the node, direct, block, or a
 * profile), «Обход DPI» for a direct one; «Сохранить» at the bottom.
 */
@Composable
fun RuleScreen(
    form: RuleForm,
    info: RuleInfo,
    page: RulePage,
    onPage: (RulePage) -> Unit,
    onForm: (RuleForm) -> Unit,
    actions: RuleActions,
) {
    val back = stringResource(R.string.screen_back)
    when (page) {
        RulePage.MAIN -> Main(form, info, onPage, onForm, actions)
        RulePage.DOMAINS -> {
            BackHandler { onPage(RulePage.MAIN) }
            ListPage(stringResource(R.string.rt_domains), back, { onPage(RulePage.MAIN) }) {
                Help {
                    Field(stringResource(R.string.rt_domains), form.domains, { onForm(form.copy(domains = it)) }, mono = true, multiLine = true)
                    HelpText(R.string.route_domain_help)
                }
            }
        }
        RulePage.ADDRESSES -> {
            BackHandler { onPage(RulePage.MAIN) }
            ListPage(stringResource(R.string.rt_addresses), back, { onPage(RulePage.MAIN) }) {
                Help {
                    Field(stringResource(R.string.rt_dst_ip), form.ip, { onForm(form.copy(ip = it)) }, mono = true, multiLine = true)
                    HelpText(R.string.route_ip_help)
                }
                Field(stringResource(R.string.rt_src_ip), form.source, { onForm(form.copy(source = it)) }, mono = true, multiLine = true)
            }
        }
        RulePage.PORTS -> {
            BackHandler { onPage(RulePage.MAIN) }
            ListPage(stringResource(R.string.rt_ports), back, { onPage(RulePage.MAIN) }) {
                Field(stringResource(R.string.rt_dst_port), form.port, { onForm(form.copy(port = it)) }, mono = true)
                Field(stringResource(R.string.rt_src_port), form.sourcePort, { onForm(form.copy(sourcePort = it)) }, mono = true)
                Segmented(
                    stringResource(R.string.rt_network),
                    listOf("" to stringResource(R.string.rt_tcp_udp), "tcp" to "TCP", "udp" to "UDP"),
                    form.network, block = true,
                ) { onForm(form.copy(network = it)) }
                Field(stringResource(R.string.rt_protocol), form.protocol, { onForm(form.copy(protocol = it)) }, mono = true)
            }
        }
    }
}

@Composable
private fun Main(form: RuleForm, info: RuleInfo, onPage: (RulePage) -> Unit, onForm: (RuleForm) -> Unit, actions: RuleActions) {
    val c = Niko.colors
    BackHandler(onBack = actions::back)
    Column(Modifier.fillMaxSize().background(c.surface0).statusBarsPadding()) {
        Row(
            Modifier.fillMaxWidth().height(NikoSize.bar).padding(horizontal = NikoSpace.s1),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(NikoSpace.s1),
        ) {
            IconButton(R.drawable.ic_l_arrow_left, stringResource(R.string.screen_back), onClick = actions::back)
            val title = stringResource(if (info.existing) R.string.rt_rule else R.string.rt_new_rule)
            Text(title, NikoType.headline, c.ink, Modifier.weight(1f).padding(horizontal = NikoSpace.s2), maxLines = 1)
            if (info.existing) IconButton(R.drawable.ic_l_trash_2, stringResource(R.string.rt_delete_rule), onClick = actions::delete)
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState())
                .padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s2, bottom = NikoSpace.s8),
            verticalArrangement = Arrangement.spacedBy(NikoSpace.s6),
        ) {
            Field(stringResource(R.string.rt_name), form.name, { onForm(form.copy(name = it)) })
            SectionWithButton(stringResource(R.string.apps), R.drawable.ic_l_plus, stringResource(R.string.rt_pick_apps), actions::pickApps) {
                if (form.packages.isEmpty()) {
                    Text(stringResource(R.string.rt_any_app), NikoType.bodySm, c.ink2, Modifier.padding(NikoSpace.s4))
                }
                form.packages.forEachIndexed { i, pkg ->
                    val label = info.labels[pkg] ?: pkg
                    ListRow(
                        label,
                        divider = i > 0,
                        lead = { LetterTile(label) },
                        sub = { Sub(pkg, mono = true) },
                        trail = {
                            IconButton(R.drawable.ic_l_x, stringResource(R.string.rt_remove_app, label), muted = true, small = true) {
                                onForm(form.copy(packages = form.packages - pkg))
                            }
                        },
                    )
                }
            }
            Section(stringResource(R.string.rt_conditions)) {
                ValueRow(stringResource(R.string.rt_domains), clip(shortList(form.domains)), false, { onPage(RulePage.DOMAINS) })
                val addresses = listOfNotNull(shortList(form.ip), shortList(form.source)?.let { "src $it" }).joinToString(" · ")
                ValueRow(stringResource(R.string.rt_addresses), clip(addresses), true, { onPage(RulePage.ADDRESSES) })
                val ports = listOf(form.port.trim(), form.sourcePort.trim().takeIf { it.isNotEmpty() }?.let { "src $it" }.orEmpty(), form.network.uppercase(), form.protocol.trim())
                    .filter { it.isNotEmpty() }.joinToString(" · ")
                ValueRow(stringResource(R.string.rt_ports), clip(ports), true, { onPage(RulePage.PORTS) })
                val lines = form.config.takeIf { it.isNotBlank() }?.split('\n')?.size
                ValueRow(stringResource(R.string.rt_json), lines?.let { stringResource(R.string.lines, it) } ?: "—", true, actions::editJson)
            }
            Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
                val title = stringResource(R.string.rt_action)
                Text(title.uppercase(), NikoType.label, c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
                Segmented(
                    null,
                    listOf(
                        0L to stringResource(R.string.rt_out_proxy),
                        -1L to stringResource(R.string.rt_out_direct),
                        -2L to stringResource(R.string.rt_out_block),
                    ),
                    form.outbound, block = true, name = title,
                ) { onForm(form.copy(outbound = it)) }
            }
            ListGroup {
                val profile = if (form.out == RuleOut.PROFILE) info.profileName ?: stringResource(R.string.error_title) else "—"
                ValueRow(stringResource(R.string.rt_profile), clip(profile), false, actions::pickProfile)
                if (form.out == RuleOut.DIRECT) {
                    SwitchRow(
                        stringResource(R.string.route_dpi_bypass), form.dpiBypass, { onForm(form.copy(dpiBypass = it)) },
                        divider = true, sub = stringResource(R.string.route_dpi_bypass_summary),
                    )
                }
            }
        }
        Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(start = NikoSpace.s5, end = NikoSpace.s5, top = NikoSpace.s3, bottom = NikoSpace.s6)) {
            PrimaryButton(stringResource(R.string.rt_save), actions::save, Modifier.fillMaxWidth())
        }
    }
}

/** A condition's value in a row's trail: "—" for none, a long one cut. */
private fun clip(value: String?): String = when {
    value.isNullOrEmpty() -> "—"
    value.length > 24 -> value.take(23) + "…"
    else -> value
}

@Composable
private fun Help(content: @Composable () -> Unit) =
    Column(verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) { content() }

@Composable
private fun HelpText(text: Int) = Text(stringResource(text), NikoType.caption, Niko.colors.ink2)

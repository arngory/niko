package io.niko.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import io.niko.Key
import io.niko.R
import io.niko.ui.compose.Niko
import io.niko.ui.compose.NikoOpacity
import io.niko.ui.compose.NikoSpace
import io.niko.ui.compose.NikoType
import io.niko.ui.home.ListGroup
import io.niko.ui.home.ListRow
import io.niko.ui.home.Radio
import io.niko.ui.home.Section
import io.niko.ui.home.Sheet
import io.niko.ui.home.Text
import io.niko.ui.home.Trail

// Настройки (P-Settings, D-SET-03…33) by hand on the settings components (docs/ui/platforms.md, variant А): the list
// of the canvas, each row a setting, a choice or a page of its own. The pages the canvas has no artboard of are drawn
// as P-SetInbound is, of the same rows and fields.

/** The settings' pages: the list, and the pages its rows open. */
enum class SettingsPage { ROOT, INBOUND, DNS, TUN, YGGDRASIL, NODES, CHECK, ROOTS, EXPERIMENTS, LOG }

/** What the pages show that is not a setting of the store. */
class SettingsInfo(val version: String, val customConfigLines: Int, val yggdrasilAddress: String?)

/** A setting chosen from a few: its name, its key, the stored values with their names. */
class Choice(val title: String, val key: String, val options: List<Pair<String, String>>)

/** What the pages do beyond changing a value. */
interface SettingsActions {
    fun back()
    fun open(page: SettingsPage)
    fun choose(choice: Choice)
    fun newPassword()
    fun customConfig()
    fun backup()
    fun about()
    fun copy(text: String)
}

@Composable
fun SettingsScreen(
    page: SettingsPage,
    values: SettingValues,
    info: SettingsInfo,
    actions: SettingsActions,
    sheet: Choice? = null,
    onDismissSheet: () -> Unit = {},
) {
    Box(Modifier.fillMaxSize()) {
        when (page) {
            SettingsPage.ROOT -> Root(values, info, actions)
            SettingsPage.INBOUND -> InboundScreen(values, actions::back, actions::newPassword)
            SettingsPage.DNS -> Dns(values, actions)
            SettingsPage.TUN -> Tun(values, actions)
            SettingsPage.YGGDRASIL -> Yggdrasil(values, info, actions)
            SettingsPage.NODES -> Nodes(values, actions)
            SettingsPage.CHECK -> Check(values, actions)
            SettingsPage.ROOTS -> Roots(values, actions)
            SettingsPage.EXPERIMENTS -> Experiments(values, actions)
            SettingsPage.LOG -> Log(values, actions)
        }
        if (sheet != null) ChoiceSheet(sheet, values.text(sheet.key), { values.setChoice(sheet.key, it); onDismissSheet() }, onDismissSheet)
    }
}

/** m-row that opens something: the title, [value] and a chevron at its end; not [enabled], dimmed and not pressed. */
@Composable
internal fun ValueRow(title: String, value: String?, divider: Boolean, onClick: () -> Unit, mono: Boolean = false, enabled: Boolean = true) =
    ListRow(
        title,
        Modifier.alpha(if (enabled) 1f else NikoOpacity.DISABLED),
        divider = divider,
        titleLines = 2,
        trail = { Trail(value, mono = mono) },
        onClick = onClick.takeIf { enabled },
    )

/** The row of a [choice]: its stored value's name at the end; a press opens the choice. */
@Composable
private fun ChoiceRow(choice: Choice, values: SettingValues, actions: SettingsActions, divider: Boolean, enabled: Boolean = true) {
    val stored = values.text(choice.key)
    ValueRow(choice.title, choice.options.firstOrNull { it.first == stored }?.second ?: stored, divider, { actions.choose(choice) }, enabled = enabled)
}

@Composable
private fun Switch(values: SettingValues, key: String, title: Int, divider: Boolean, sub: String? = null, enabled: Boolean = true) =
    Switch(values, key, stringResource(title), divider, sub, enabled)

@Composable
private fun Switch(values: SettingValues, key: String, title: String, divider: Boolean, sub: String? = null, enabled: Boolean = true) =
    SwitchRow(title, values.flag(key), { values.setFlag(key, it) }, enabled, divider, sub)

@Composable
private fun TextField(
    values: SettingValues,
    key: String,
    label: Int,
    modifier: Modifier = Modifier,
    input: FieldInput = FieldInput.TEXT,
    help: Int? = null,
    multiLine: Boolean = false,
) = Column(modifier, verticalArrangement = Arrangement.spacedBy(NikoSpace.s2)) {
    Field(stringResource(label), values.text(key), { values.setText(key, it) }, input = input, mono = true, multiLine = multiLine)
    if (help != null) Text(stringResource(help), NikoType.caption, Niko.colors.ink2)
}

/** m-sheet of a [choice]: its name, then a radio row for each value; a pick closes it. */
@Composable
internal fun ChoiceSheet(choice: Choice, selected: String, onPick: (String) -> Unit, onDismiss: () -> Unit) {
    val c = Niko.colors
    Box(
        Modifier.fillMaxSize().background(c.scrim)
            .clickable(remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Sheet(Modifier.clickable(remember { MutableInteractionSource() }, indication = null) { }.navigationBarsPadding()) {
            Text(choice.title, NikoType.headline, c.ink, Modifier.padding(start = NikoSpace.s5, end = NikoSpace.s5, bottom = NikoSpace.s2))
            Column(Modifier.selectableGroup().verticalScroll(rememberScrollState()).padding(bottom = NikoSpace.s6)) {
                choice.options.forEachIndexed { i, (value, name) ->
                    val on = value == selected
                    ListRow(
                        name,
                        Modifier.selectable(on, role = Role.RadioButton) { onPick(value) },
                        titleBold = on,
                        divider = i > 0,
                        side = NikoSpace.s5,
                        titleLines = 2,
                        lead = { Radio(on) },
                    )
                }
            }
        }
    }
}

// the choices, their values as the store keeps them

@Composable
private fun themeChoice() = Choice(
    stringResource(R.string.set_theme), Key.NIGHT_THEME,
    listOf(
        "0" to stringResource(R.string.set_theme_system),
        "2" to stringResource(R.string.set_theme_light),
        "1" to stringResource(R.string.set_theme_dark),
        "3" to stringResource(R.string.set_theme_battery),
    ),
)

@Composable
private fun modeChoice() = Choice(
    stringResource(R.string.set_mode), Key.SERVICE_MODE,
    listOf(
        Key.MODE_VPN to stringResource(R.string.service_mode_vpn),
        Key.MODE_PROXY to stringResource(R.string.home_mode_proxy),
        "transproxy" to stringResource(R.string.set_mode_transproxy),
    ),
)

@Composable
private fun rootsChoices() = listOf(
    "chrome" to "Chrome",
    "mozilla" to "Mozilla",
    "system" to stringResource(R.string.set_roots_system),
)

@Composable
private fun strategyChoice(key: String, title: Int) = Choice(
    stringResource(title), key,
    listOf(
        "auto" to stringResource(R.string.set_strategy_auto),
        "prefer_ipv4" to stringResource(R.string.set_strategy_prefer_ipv4),
        "prefer_ipv6" to stringResource(R.string.set_strategy_prefer_ipv6),
        "ipv4_only" to stringResource(R.string.set_strategy_ipv4_only),
        "ipv6_only" to stringResource(R.string.set_strategy_ipv6_only),
    ),
)

@Composable
private fun Root(values: SettingValues, info: SettingsInfo, actions: SettingsActions) =
    ListPage(stringResource(R.string.settings), stringResource(R.string.screen_back), actions::back) {
        val on = stringResource(R.string.set_on)
        val off = stringResource(R.string.set_off)
        Section(stringResource(R.string.set_view)) {
            ChoiceRow(themeChoice(), values, actions, divider = false)
        }
        Section(stringResource(R.string.set_start)) {
            Switch(values, Key.PERSIST_ACROSS_REBOOT, R.string.set_auto_connect, divider = false, sub = stringResource(R.string.set_auto_connect_sub))
        }
        Section(stringResource(R.string.set_connection)) {
            ChoiceRow(modeChoice(), values, actions, divider = false)
            val dns = if (values.flag(Key.ENABLE_FAKEDNS)) "FakeIP" else dnsHost(values.text(Key.REMOTE_DNS))
            ValueRow("DNS", dns, divider = true, onClick = { actions.open(SettingsPage.DNS) })
            val tun = listOf("gVisor", "System", "Mixed").getOrNull(values.text(Key.TUN_IMPLEMENTATION).toIntOrNull() ?: 0) ?: "gVisor"
            ValueRow("TUN", "$tun · ${values.text(Key.MTU)}", divider = true, onClick = { actions.open(SettingsPage.TUN) })
            val host = if (values.flag(Key.ALLOW_ACCESS)) "0.0.0.0" else "127.0.0.1"
            ValueRow(stringResource(R.string.inbound_title), "$host:${values.text(Key.MIXED_PORT)}", divider = true, mono = true, onClick = { actions.open(SettingsPage.INBOUND) })
            ValueRow("Yggdrasil", if (values.flag(Key.YGGDRASIL_ENABLED)) on else off, divider = true, onClick = { actions.open(SettingsPage.YGGDRASIL) })
        }
        Section(stringResource(R.string.set_nodes)) {
            Switch(values, Key.RAYCONTROL_AUTO_SWITCH, R.string.set_auto_switch, divider = false)
            ValueRow(stringResource(R.string.set_brutal_resolvers), null, divider = true, onClick = { actions.open(SettingsPage.NODES) })
            Switch(values, Key.RAYCONTROL_REPORTS, R.string.set_reports, divider = true, sub = stringResource(R.string.set_reports_sub))
        }
        Section(stringResource(R.string.set_more)) {
            ValueRow(stringResource(R.string.set_check), null, divider = false, onClick = { actions.open(SettingsPage.CHECK) })
            val roots = rootsChoices().firstOrNull { it.first == values.text(Key.ROOT_STORE) }?.second
            ValueRow(stringResource(R.string.set_roots), roots, divider = true, onClick = { actions.open(SettingsPage.ROOTS) })
            ValueRow(stringResource(R.string.set_experiments), null, divider = true, onClick = { actions.open(SettingsPage.EXPERIMENTS) })
            val config = if (info.customConfigLines == 0) stringResource(R.string.set_not_set) else stringResource(R.string.lines, info.customConfigLines)
            ValueRow(stringResource(R.string.set_custom_config), config, divider = true, onClick = actions::customConfig)
            ValueRow(stringResource(R.string.set_log_view), null, divider = true, onClick = { actions.open(SettingsPage.LOG) })
            ValueRow(stringResource(R.string.set_backup), null, divider = true, onClick = actions::backup)
            ValueRow(stringResource(R.string.set_about), info.version, divider = true, onClick = actions::about)
        }
    }

/**
 * A DNS setting as the list shows it: its first server's host (`https://cloudflare-dns.com/dns-query` as
 * cloudflare-dns.com) and +1, +2 for the servers after it, a line each; blank lines and "#" comments not counted.
 */
internal fun dnsHost(servers: String): String {
    val lines = servers.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
    val first = lines.firstOrNull() ?: return servers
    val host = first.substringAfter("://").substringBefore('/').ifEmpty { first }
    return if (lines.size > 1) "$host +${lines.size - 1}" else host
}

@Composable
private fun Page(title: Int, actions: SettingsActions, content: @Composable ColumnScope.() -> Unit) =
    Page(stringResource(title), actions, content)

@Composable
private fun Page(title: String, actions: SettingsActions, content: @Composable ColumnScope.() -> Unit) =
    ListPage(title, stringResource(R.string.screen_back), actions::back, content)

@Composable
private fun Dns(values: SettingValues, actions: SettingsActions) = Page(R.string.set_dns_route, actions) {
    TextField(values, Key.REMOTE_DNS, R.string.set_remote_dns, help = R.string.remote_dns_help, multiLine = true)
    TextField(values, Key.DIRECT_DNS, R.string.set_direct_dns, help = R.string.direct_dns_help)
    ListGroup {
        Switch(values, Key.ENABLE_FAKEDNS, "FakeIP", divider = false)
        Switch(values, Key.ENABLE_DNS_ROUTING, R.string.set_dns_routing, divider = true)
        Switch(values, Key.DNS_OPTIMISTIC_CACHE, R.string.set_optimistic_cache, divider = true)
    }
    Section(stringResource(R.string.set_strategy)) {
        ChoiceRow(strategyChoice(SettingKeys.STRATEGY_REMOTE, R.string.set_strategy_remote), values, actions, divider = false)
        ChoiceRow(strategyChoice(SettingKeys.STRATEGY_DIRECT, R.string.set_strategy_direct), values, actions, divider = true)
        ChoiceRow(strategyChoice(SettingKeys.STRATEGY_SERVER, R.string.set_strategy_server), values, actions, divider = true)
    }
    Section(stringResource(R.string.set_route)) {
        Switch(values, Key.BYPASS_LAN, R.string.set_bypass_lan, divider = false)
        ChoiceRow(
            Choice(
                stringResource(R.string.set_rule_sets_update), Key.RULE_SET_UPDATE_HOURS,
                listOf(
                    "6" to stringResource(R.string.rule_set_update_6h),
                    "12" to stringResource(R.string.rule_set_update_12h),
                    "24" to stringResource(R.string.rule_set_update_1d),
                    "72" to stringResource(R.string.rule_set_update_3d),
                    "168" to stringResource(R.string.rule_set_update_7d),
                ),
            ),
            values, actions, divider = true,
        )
        ChoiceRow(
            Choice(
                stringResource(R.string.set_rules_provider), Key.RULES_PROVIDER,
                listOf(
                    "0" to stringResource(R.string.route_rules_official),
                    "1" to "Loyalsoldier",
                    "2" to "Chocolate4U",
                    "3" to "L11R Antizapret",
                ),
            ),
            values, actions, divider = true,
        )
    }
}

@Composable
private fun Tun(values: SettingValues, actions: SettingsActions) = Page("TUN", actions) {
    TextField(values, Key.MTU, R.string.mtu, input = FieldInput.PORT, modifier = Modifier.width(NarrowField))
    ListGroup {
        ChoiceRow(
            Choice(stringResource(R.string.set_tun_impl), Key.TUN_IMPLEMENTATION, listOf("0" to "gVisor", "1" to "System", "2" to "Mixed")),
            values, actions, divider = false,
        )
        ChoiceRow(
            Choice(
                "IPv6", Key.IPV6_MODE,
                listOf(
                    "0" to stringResource(R.string.set_no),
                    "1" to stringResource(R.string.set_yes),
                    "2" to stringResource(R.string.set_first),
                    "3" to stringResource(R.string.set_only),
                ),
            ),
            values, actions, divider = true,
        )
    }
}

@Composable
private fun Yggdrasil(values: SettingValues, info: SettingsInfo, actions: SettingsActions) = Page("Yggdrasil", actions) {
    val on = values.flag(Key.YGGDRASIL_ENABLED)
    ListGroup { Switch(values, Key.YGGDRASIL_ENABLED, R.string.set_ygg_enable, divider = false) }
    if (on) {
        TextField(values, Key.YGGDRASIL_PEERS, R.string.set_ygg_peers, help = R.string.yggdrasil_peers_help, multiLine = true)
        ListGroup {
            Switch(values, Key.YGGDRASIL_PEERS_VIA_PROXY, R.string.set_ygg_via_proxy, divider = false)
            Switch(values, Key.YGGDRASIL_TLS_CHROME, R.string.set_ygg_tls_chrome, divider = true)
            Switch(values, Key.YGGDRASIL_ALLOW_INBOUND, R.string.set_ygg_inbound, divider = true)
        }
        if (values.flag(Key.YGGDRASIL_ALLOW_INBOUND)) TextField(values, Key.YGGDRASIL_INBOUND_PORTS, R.string.set_ygg_ports, help = R.string.yggdrasil_inbound_ports_help)
        val address = info.yggdrasilAddress
        ListGroup {
            ValueRow(
                stringResource(R.string.set_ygg_address), address ?: stringResource(R.string.yggdrasil_not_built),
                divider = false, mono = address != null, enabled = address != null, onClick = { address?.let(actions::copy) },
            )
        }
    }
}

@Composable
private fun Nodes(values: SettingValues, actions: SettingsActions) = Page(R.string.set_brutal_resolvers, actions) {
    ListGroup { Switch(values, Key.RAYCONTROL_BRUTAL, R.string.set_brutal, divider = false, sub = "Hysteria2") }
    TextField(values, Key.RAYCONTROL_DNSTT_RESOLVER, R.string.set_dnstt_resolver, help = R.string.raycontrol_dnstt_resolver_help)
    TextField(values, Key.RAYCONTROL_MASTERDNS_RESOLVERS, R.string.set_masterdns_resolvers, help = R.string.raycontrol_masterdns_resolvers_help)
}

@Composable
private fun Check(values: SettingValues, actions: SettingsActions) = Page(R.string.set_check, actions) {
    FieldsRow {
        TextField(values, Key.CONNECTION_TEST_URL, R.string.set_test_url, modifier = Modifier.weight(1f))
        TextField(values, SettingKeys.CONNECTION_TEST_CONCURRENT, R.string.set_test_parallel, input = FieldInput.PORT, modifier = Modifier.width(NarrowField))
    }
    ListGroup {
        Switch(values, Key.NETWORK_CHANGE_RESET_CONNECTIONS, R.string.set_reset_network, divider = false)
        Switch(values, Key.WAKE_RESET_CONNECTIONS, R.string.set_reset_wake, divider = true)
        Switch(values, Key.ENABLE_CLASH_API, "Clash API", divider = true)
    }
    Section(stringResource(R.string.set_danger)) {
        Switch(values, Key.GLOBAL_ALLOW_INSECURE, R.string.set_insecure, divider = false)
        Switch(values, Key.ALLOW_INSECURE_ON_REQUEST, R.string.set_insecure_subs, divider = true)
        ChoiceRow(Choice(stringResource(R.string.set_subs_tls), Key.APP_TLS_VERSION, listOf("1.2" to "TLS 1.2", "1.3" to "TLS 1.3")), values, actions, divider = true)
    }
}

@Composable
private fun Roots(values: SettingValues, actions: SettingsActions) = Page(R.string.set_roots, actions) {
    val c = Niko.colors
    val stored = values.text(Key.ROOT_STORE)
    ListGroup(Modifier.selectableGroup()) {
        rootsChoices().forEachIndexed { i, (value, name) ->
            val on = value == stored
            ListRow(
                name,
                Modifier.selectable(on, role = Role.RadioButton) { values.setChoice(Key.ROOT_STORE, value) },
                titleBold = on,
                divider = i > 0,
                lead = { Radio(on) },
            )
        }
    }
    ListGroup {
        Switch(values, Key.NAIVE_QUIC_PHONE_ROOTS, R.string.set_naive_quic_phone_roots, divider = false, enabled = stored == "system")
    }
    Text(stringResource(R.string.root_store_naive_note), NikoType.caption, c.ink2, Modifier.padding(horizontal = NikoSpace.s4))
}

@Composable
private fun Experiments(values: SettingValues, actions: SettingsActions) = Page(R.string.set_experiments, actions) {
    ListGroup {
        Switch(values, Key.BYPASS_LAN_IN_CORE, R.string.set_bypass_lan_core, divider = false)
        Switch(values, Key.REFUSE_QUIC_OVER_STREAMS, R.string.set_refuse_quic, divider = true)
        Switch(values, Key.HONEST_CONNECT, R.string.set_honest_connect, divider = true)
        Switch(values, Key.ICMP_THROUGH_NODE, R.string.set_icmp_node, divider = true)
        Switch(values, Key.ICMP_NO_LOCAL_REPLY, R.string.set_icmp_no_local, divider = true)
        Switch(values, Key.RESOLVE_DESTINATION, R.string.set_resolve_destination, divider = true)
    }
    ListGroup {
        ChoiceRow(
            Choice(
                stringResource(R.string.set_tls_fragment), Key.TLS_FRAGMENT_BYPASS,
                listOf("0" to stringResource(R.string.set_no), "1" to stringResource(R.string.set_tls_fragment_records), "2" to "TCP"),
            ),
            values, actions, divider = false,
        )
        ChoiceRow(
            Choice(stringResource(R.string.set_sniffing), Key.TRAFFIC_SNIFFING, listOf("0" to stringResource(R.string.set_no), "1" to stringResource(R.string.set_sniffing_route))),
            values, actions, divider = true,
        )
    }
}

@Composable
private fun Log(values: SettingValues, actions: SettingsActions) = Page(R.string.set_log_view, actions) {
    val speed = values.text(Key.SPEED_INTERVAL) != "0"
    ListGroup {
        ChoiceRow(
            Choice(stringResource(R.string.set_log_level), Key.LOG_LEVEL, listOf("none", "warn", "info", "debug", "trace").mapIndexed { i, name -> "$i" to name }),
            values, actions, divider = false,
        )
        ChoiceRow(
            Choice(
                stringResource(R.string.set_speed_interval), Key.SPEED_INTERVAL,
                listOf("0" to stringResource(R.string.set_off), "500" to "0.5 s", "1000" to "1 s", "3000" to "3 s", "10000" to "10 s"),
            ),
            values, actions, divider = true,
        )
    }
    TextField(values, Key.LOG_BUF_SIZE, R.string.set_log_size, input = FieldInput.PORT, modifier = Modifier.width(NarrowField))
    ListGroup {
        Switch(values, Key.PROFILE_TRAFFIC_STATISTICS, R.string.set_traffic_stats, divider = false, enabled = speed)
        Switch(values, Key.SHOW_DIRECT_SPEED, R.string.set_direct_speed, divider = true)
        Switch(values, SettingKeys.SHOW_GROUP_IN_NOTIFICATION, R.string.set_group_in_notification, divider = true)
        Switch(values, Key.ALWAYS_SHOW_ADDRESS, R.string.set_show_address, divider = true)
        Switch(values, Key.SHOW_BOTTOM_BAR, R.string.set_bottom_bar, divider = true)
    }
}

package io.niko.ui.home

import io.niko.GroupOrder
import io.niko.GroupType
import io.niko.database.DataStore
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.v2ray.StandardV2RayBean
import io.niko.ktx.Logs
import io.niko.raycontrol.RayNodes
import java.text.Normalizer
import java.util.Locale

/** A profile as the configs' sheet lists it (P-Configs): its name, its kind in tags, its last ping. */
data class ConfigRow(
    val id: Long,
    val name: String,
    /** the kind first (VLESS, Hysteria2…), then what sets it apart: REALITY, the transport */
    val tags: List<String>,
    /** the last ping in ms, null when not measured or failed */
    val pingMs: Int?,
    /** the last test failed */
    val unreachable: Boolean,
    val selected: Boolean,
    /** what the failed test said, in full; null when it said nothing */
    val error: String? = null,
    /** shared as a link, a QR code or its configuration: not a chain, a plugin's profile or a node's row */
    val share: Boolean = false,
    /** its kind has a standard link, besides the SN one */
    val standardLink: Boolean = false,
    /** the running service uses it: not deleted meanwhile */
    val inUse: Boolean = false,
    /** its server, host:port: searched by; null when the profile has none of its own (a chain) */
    val address: String? = null,
    /** the address on the row too: «Показывать адрес» (D-SET-24) is on and the name does not say it already */
    val showAddress: Boolean = false,
    /** the traffic counted through it, bytes up and down */
    val tx: Long = 0,
    val rx: Long = 0,
)

/** What a group's menu offers (P-Configs ⋮, D-PROFILE-06), in its order. */
enum class GroupAction { TCP_PING, URL_TEST, CLEAR_RESULTS, DELETE_UNAVAILABLE, REMOVE_DUPLICATES, CLEAR_TRAFFIC }

/** What a profile's sheet offers (a long press on its row; D-PROFILE-07, 08), in its order. */
enum class ProfileAction { EDIT, SHARE, MOVE_UP, MOVE_DOWN, DELETE }

/** How a profile is shared (D-PROFILE-07): its standard link or the SN one, as a QR code or copied; its configuration. */
enum class ShareAction { QR, QR_SN, LINK, LINK_SN, CONFIG_CLIPBOARD, CONFIG_FILE }

/** A group of profiles: a node's, a subscription's, or the user's own; ungrouped ones have no name. */
data class ConfigGroup(
    val id: Long,
    /** null for the ungrouped */
    val name: String?,
    val subscription: Boolean,
    val profiles: List<ConfigRow>,
    /** in the user's own order (not sorted by name or delay): its profiles can be moved */
    val manual: Boolean = true,
)

/** What the configs' sheet reads from the database, off the main thread. */
object ConfigsLoader {

    fun load(): List<ConfigGroup> {
        val selected = DataStore.selectedProxy
        val running = if (DataStore.serviceState.started) DataStore.currentProfile else 0L
        val addresses = DataStore.alwaysShowAddress
        // a node's rows are this device's: not shared, another device takes an invite (salamander-device-v1 §6)
        val nodeGroups = RayNodes.list().map { it.groupId }.filter { it != 0L }.toSet()
        return SagerDatabase.groupDao.allGroups().mapNotNull { group ->
            // a profile whose bean does not read (an older build's, a broken import) is left out, not the whole list
            val rows = ordered(SagerDatabase.proxyDao.getByGroup(group.id), group.order).mapNotNull { proxy ->
                runCatching { row(proxy, selected, running, nodeRow = group.id in nodeGroups, addresses = addresses) }.onFailure { Logs.w(it) }.getOrNull()
            }
            if (rows.isEmpty()) return@mapNotNull null
            ConfigGroup(
                id = group.id,
                name = runCatching { group.takeUnless { it.ungrouped }?.displayName() }.getOrNull(),
                subscription = group.type == GroupType.SUBSCRIPTION,
                profiles = rows,
                manual = group.order == GroupOrder.ORIGIN,
            )
        }
    }

    /** The group's profiles in its order (P-SubSettings): as stored, by name, or by delay with the untested last. */
    fun ordered(profiles: List<ProxyEntity>, order: Int): List<ProxyEntity> = when (order) {
        GroupOrder.BY_NAME -> profiles.sortedBy { it.displayName() }
        GroupOrder.BY_DELAY -> profiles.sortedBy { if (it.status == 1) it.ping else 114514 }
        else -> profiles
    }

    /** The rows with the tests' results over what was read (ConfigsTest). */
    fun withResults(groups: List<ConfigGroup>, results: Map<Long, ConfigsTest.Result>): List<ConfigGroup> {
        if (results.isEmpty()) return groups
        return groups.map { group ->
            group.copy(profiles = group.profiles.map { row ->
                results[row.id]?.let { row.copy(pingMs = it.pingMs, unreachable = it.unreachable, error = it.error) } ?: row
            })
        }
    }

    /**
     * [running] the profile the service runs (0: none), [nodeRow] one of a raycontrol node's group, [addresses] the
     * setting «Показывать адрес».
     */
    fun row(proxy: ProxyEntity, selected: Long, running: Long = 0, nodeRow: Boolean = false, addresses: Boolean = false) = ConfigRow(
        id = proxy.id,
        name = proxy.displayName(),
        tags = tags(proxy),
        pingMs = proxy.ping.takeIf { proxy.status == 1 && it > 0 },
        unreachable = proxy.status > 1,
        selected = proxy.id == selected,
        error = error(proxy.status, proxy.error),
        share = shared(proxy, nodeRow),
        standardLink = proxy.haveStandardLink(),
        inUse = proxy.id == selected && proxy.id == running,
        address = address(proxy),
        // as the old list: a profile without a name is named by its address already
        showAddress = addresses && !proxy.requireBean().name.isNullOrBlank(),
        tx = proxy.tx,
        rx = proxy.rx,
    )

    /** The profile's server as host:port; none for a chain (it has no server of its own). */
    fun address(proxy: ProxyEntity): String? =
        if (proxy.type == ProxyEntity.TYPE_CHAIN) null else proxy.displayAddress().takeIf { it.isNotBlank() }

    /**
     * What the row says after its tags (the old list's second line): the address when it is to be shown, the traffic
     * up and down when any was counted, sizes by [bytes]; null: nothing.
     */
    fun detail(row: ConfigRow, bytes: (Long) -> String): String? = listOfNotNull(
        row.address?.takeIf { row.showAddress },
        "↑ ${bytes(row.tx)} ↓ ${bytes(row.rx)}".takeIf { row.tx + row.rx > 0 },
    ).joinToString(" · ").takeIf { it.isNotEmpty() }

    /** How far a group's test is, as its head says it: the profiles tested of [total]; null when it does not run. */
    fun testProgress(results: Map<Long, ConfigsTest.Result>, total: Int): Pair<Int, Int>? =
        if (total > 0) results.size.coerceAtMost(total) to total else null

    /** What a failed test said (status 2: in a few words, 3: the core's error), null when it passed or said nothing. */
    fun error(status: Int, error: String?): String? = error?.trim()?.takeIf { status > 1 && it.isNotEmpty() }

    /** As the old list: no sharing of a chain, a plugin's profile, or a node's row. */
    fun shared(proxy: ProxyEntity, nodeRow: Boolean) =
        !nodeRow && proxy.type != ProxyEntity.TYPE_CHAIN && proxy.nekoBean == null

    /** A group's menu; no second test while one runs. */
    fun groupMenu(testing: Boolean): List<GroupAction> =
        if (testing) GroupAction.entries - setOf(GroupAction.TCP_PING, GroupAction.URL_TEST) else GroupAction.entries

    /** A profile's sheet: [up] and [down] when it has where to move; the one the service runs is not deleted. */
    fun profileMenu(row: ConfigRow, up: Boolean, down: Boolean): List<ProfileAction> = buildList {
        add(ProfileAction.EDIT)
        if (row.share) add(ProfileAction.SHARE)
        if (up) add(ProfileAction.MOVE_UP)
        if (down) add(ProfileAction.MOVE_DOWN)
        if (!row.inUse) add(ProfileAction.DELETE)
    }

    /** The ways to share a profile: the standard link only for the kinds that have one. */
    fun shareMenu(row: ConfigRow): List<ShareAction> = when {
        !row.share -> emptyList()
        row.standardLink -> ShareAction.entries
        else -> ShareAction.entries - setOf(ShareAction.QR, ShareAction.LINK)
    }

    /**
     * The profiles next to [id] in its group, above and below, to change places with; none when the group is sorted
     * (its order is not the user's).
     */
    fun neighbours(groups: List<ConfigGroup>, id: Long): Pair<Long?, Long?> {
        val group = groups.firstOrNull { g -> g.profiles.any { it.id == id } } ?: return null to null
        if (!group.manual) return null to null
        val at = group.profiles.indexOfFirst { it.id == id }
        return group.profiles.getOrNull(at - 1)?.id to group.profiles.getOrNull(at + 1)?.id
    }

    /** Where the chosen profile's row is in the sheet's list (a head, the rows and a gap for each group); -1: none. */
    fun selectedIndex(groups: List<ConfigGroup>): Int {
        var index = 0
        for (group in groups) {
            val at = group.profiles.indexOfFirst { it.selected }
            if (at >= 0) return index + 1 + at
            index += group.profiles.size + 2
        }
        return -1
    }

    /** The rows without [ids] (deleted, their undo still offered); the groups left empty go. */
    fun without(groups: List<ConfigGroup>, ids: Set<Long>): List<ConfigGroup> {
        if (ids.isEmpty()) return groups
        return groups.mapNotNull { group ->
            group.profiles.filterNot { it.id in ids }.takeIf { it.isNotEmpty() }?.let { group.copy(profiles = it) }
        }
    }

    /**
     * The kind, unless the name already says it (a node's rows are named by their method: "naive", "Hysteria2"), then
     * what sets the profile apart: naive's HTTPS or QUIC, Hysteria's QUIC; for VMess, VLESS and Trojan REALITY over
     * TLS and the transport over TCP.
     */
    fun tags(proxy: ProxyEntity): List<String> {
        val kind = proxy.displayType()
        val extra = when (val bean = proxy.requireBean()) {
            is StandardV2RayBean -> {
                val security = bean.security.orEmpty().lowercase(Locale.ROOT)
                val transport = bean.type.orEmpty().lowercase(Locale.ROOT)
                listOfNotNull(
                    "REALITY".takeIf { security == "reality" },
                    transport.takeIf { it.isNotEmpty() && it != "tcp" }?.uppercase(Locale.ROOT),
                )
            }
            is NaiveBean -> listOfNotNull(bean.proto?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT))
            is HysteriaBean -> listOf("QUIC")
            else -> emptyList()
        }
        return if (plain(proxy.displayName()) == plain(kind)) extra else listOf(kind) + extra
    }

    // "Naïve" and "naive" alike: no marks, no case
    private fun plain(text: String) =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{M}"), "").lowercase(Locale.ROOT).trim()

    /** The rows that match [query] in their name, tags or server address, case aside; the groups left empty go. */
    fun filter(groups: List<ConfigGroup>, query: String): List<ConfigGroup> {
        val q = query.trim()
        if (q.isEmpty()) return groups
        return groups.mapNotNull { group ->
            val byGroup = group.name?.contains(q, ignoreCase = true) == true
            val rows = if (byGroup) group.profiles else group.profiles.filter { row ->
                row.name.contains(q, ignoreCase = true) || row.tags.any { it.contains(q, ignoreCase = true) } ||
                    row.address?.contains(q, ignoreCase = true) == true
            }
            rows.takeIf { it.isNotEmpty() }?.let { group.copy(profiles = it) }
        }
    }
}

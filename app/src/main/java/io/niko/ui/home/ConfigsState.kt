package io.niko.ui.home

import io.niko.GroupType
import io.niko.database.DataStore
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.v2ray.StandardV2RayBean
import io.niko.ktx.Logs
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
)

/** A group of profiles: a node's, a subscription's, or the user's own; ungrouped ones have no name. */
data class ConfigGroup(
    val id: Long,
    /** null for the ungrouped */
    val name: String?,
    val subscription: Boolean,
    val profiles: List<ConfigRow>,
)

/** What the configs' sheet reads from the database, off the main thread. */
object ConfigsLoader {

    fun load(): List<ConfigGroup> {
        val selected = DataStore.selectedProxy
        return SagerDatabase.groupDao.allGroups().mapNotNull { group ->
            // a profile whose bean does not read (an older build's, a broken import) is left out, not the whole list
            val rows = SagerDatabase.proxyDao.getByGroup(group.id).mapNotNull { proxy ->
                runCatching { row(proxy, selected) }.onFailure { Logs.w(it) }.getOrNull()
            }
            if (rows.isEmpty()) return@mapNotNull null
            ConfigGroup(
                id = group.id,
                name = runCatching { group.takeUnless { it.ungrouped }?.displayName() }.getOrNull(),
                subscription = group.type == GroupType.SUBSCRIPTION,
                profiles = rows,
            )
        }
    }

    /** The rows with the tests' results over what was read (ConfigsTest). */
    fun withResults(groups: List<ConfigGroup>, results: Map<Long, ConfigsTest.Result>): List<ConfigGroup> {
        if (results.isEmpty()) return groups
        return groups.map { group ->
            group.copy(profiles = group.profiles.map { row ->
                results[row.id]?.let { row.copy(pingMs = it.pingMs, unreachable = it.unreachable) } ?: row
            })
        }
    }

    fun row(proxy: ProxyEntity, selected: Long) = ConfigRow(
        id = proxy.id,
        name = proxy.displayName(),
        tags = tags(proxy),
        pingMs = proxy.ping.takeIf { proxy.status == 1 && it > 0 },
        unreachable = proxy.status > 1,
        selected = proxy.id == selected,
    )

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

    /** The rows that match [query] in their name or tags, case aside; the groups left empty go. */
    fun filter(groups: List<ConfigGroup>, query: String): List<ConfigGroup> {
        val q = query.trim()
        if (q.isEmpty()) return groups
        return groups.mapNotNull { group ->
            val byGroup = group.name?.contains(q, ignoreCase = true) == true
            val rows = if (byGroup) group.profiles else group.profiles.filter { row ->
                row.name.contains(q, ignoreCase = true) || row.tags.any { it.contains(q, ignoreCase = true) }
            }
            rows.takeIf { it.isNotEmpty() }?.let { group.copy(profiles = it) }
        }
    }
}

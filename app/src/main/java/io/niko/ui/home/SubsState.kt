package io.niko.ui.home

import io.niko.GroupType
import io.niko.database.ProxyGroup
import io.niko.database.SagerDatabase
import io.niko.group.GroupUpdater
import io.niko.group.parseSubscriptionUserinfo
import io.niko.ktx.Logs
import io.niko.raycontrol.RayNode
import io.niko.raycontrol.RayNodes

/** What a group is to the Subscriptions tab: a provider's subscription, a raycontrol node's rows, the user's own. */
enum class SubKind { SUBSCRIPTION, NODE, OWN, UNGROUPED }

/** What a group's menu offers (P-Subs ⋮), in its order. */
enum class SubAction {
    SETTINGS, ADMIN, LAN, QR, COPY_LINK, ANOTHER_DEVICE, EXPORT_CLIPBOARD, EXPORT_FILE, MOVE_UP, MOVE_DOWN, CLEAR, DELETE
}

/**
 * A group as the Subscriptions tab lists it (P-Subs, D-SUB-01…07): its name, when it ends and was fetched, its
 * traffic of the limit. Times are unix seconds, 0 not known.
 */
data class SubRow(
    val id: Long,
    val name: String,
    val kind: SubKind,
    val profiles: Long,
    val expire: Long = 0,
    val updatedAt: Long = 0,
    /** traffic used and the limit, 0: no limit known */
    val used: Long = 0,
    val total: Long = 0,
    /** the traffic's next reset */
    val reset: Long = 0,
    /** the node's word that the traffic is used up */
    val exhausted: Boolean = false,
    val updating: Boolean = false,
    /** an update to start: a provider's subscription, a node that serves one */
    val canUpdate: Boolean = false,
    /** the provider's message, plain text */
    val announce: String? = null,
    /** a node's group: the node, for its card, its admin panel and its local network */
    val nodeId: String? = null,
    val admin: Boolean = false,
    val lan: Boolean = false,
) {
    fun expired(now: Long) = expire in 1..now
}

/** What the Subscriptions tab reads from the database, off the main thread. */
object SubsLoader {

    fun load(): List<SubRow> {
        val nodes = RayNodes.list()
        return SagerDatabase.groupDao.allGroups().mapNotNull { group ->
            runCatching {
                val node = if (group.type == GroupType.BASIC) nodes.firstOrNull { it.groupId == group.id } else null
                row(group, SagerDatabase.proxyDao.countByGroup(group.id), node, group.id in GroupUpdater.updating)
            }.onFailure { Logs.w(it) }.getOrNull()
        }.filterNot { it.kind == SubKind.UNGROUPED && it.profiles == 0L } // nothing in it, nothing to do with it
    }

    fun row(group: ProxyGroup, profiles: Long, node: RayNode?, updating: Boolean): SubRow {
        val name = group.displayName()
        if (node != null) {
            val traffic = node.traffic
            return SubRow(
                id = group.id, name = name, kind = SubKind.NODE, profiles = profiles,
                expire = node.subExpires, updatedAt = node.subUpdatedAt,
                used = traffic?.used ?: 0, total = traffic?.limit ?: 0, reset = traffic?.reset ?: 0,
                exhausted = traffic?.exhausted == true,
                updating = updating, canUpdate = node.subscription != null,
                nodeId = node.spa.nodeId, admin = node.isAdmin, lan = node.lan != null || node.lanShow != null,
            )
        }
        val sub = group.subscription
        if (group.type != GroupType.SUBSCRIPTION || sub == null) {
            return SubRow(group.id, name, if (group.ungrouped) SubKind.UNGROUPED else SubKind.OWN, profiles)
        }
        // the traffic: SIP008 and Open Online Config give it in the bean, the others in Subscription-Userinfo
        val usage = parseSubscriptionUserinfo(sub.subscriptionUserinfo)
        val sipUsed = sub.bytesUsed ?: 0L
        val (used, total) = when {
            sipUsed > 0 -> sipUsed to ((sub.bytesRemaining ?: 0L).takeIf { it > 0 }?.let { it + sipUsed } ?: 0L)
            usage != null -> usage.used to usage.total
            else -> 0L to 0L
        }
        val expire = usage?.expire?.takeIf { it > 0 } ?: (sub.expiryDate ?: 0).toLong().coerceAtLeast(0)
        return SubRow(
            id = group.id, name = name, kind = SubKind.SUBSCRIPTION, profiles = profiles,
            expire = expire, updatedAt = (sub.lastUpdated ?: 0).toLong().coerceAtLeast(0),
            used = used, total = total, updating = updating, canUpdate = true,
            announce = sub.announce?.trim()?.takeIf { it.isNotEmpty() },
        )
    }

    /**
     * A group's menu: the ungrouped can only be exported or cleared; a node's adds its panel and its network, and in
     * place of the link and the export says how another device is added: its rows are this device's, the other takes
     * an invite of its own (salamander-device-v1 §6). [up] and [down]: it has a group to change places with (D-SUB-10).
     */
    fun menu(row: SubRow, up: Boolean = false, down: Boolean = false): List<SubAction> = buildList {
        val node = row.kind == SubKind.NODE
        if (row.kind != SubKind.UNGROUPED) add(SubAction.SETTINGS)
        if (row.admin) add(SubAction.ADMIN)
        if (row.lan) add(SubAction.LAN)
        if (node) {
            add(SubAction.ANOTHER_DEVICE)
        } else if (row.kind != SubKind.UNGROUPED) {
            add(SubAction.QR)
            add(SubAction.COPY_LINK)
        }
        if (row.profiles > 0) {
            if (!node) {
                add(SubAction.EXPORT_CLIPBOARD)
                add(SubAction.EXPORT_FILE)
            }
        }
        if (up) add(SubAction.MOVE_UP)
        if (down) add(SubAction.MOVE_DOWN)
        if (row.profiles > 0) add(SubAction.CLEAR)
        if (row.kind != SubKind.UNGROUPED) add(SubAction.DELETE)
    }

    /**
     * The groups next to [row] in the list, above and below, to change places with (D-SUB-10). As the old list's drag:
     * the ungrouped and a group being fetched stay where they are.
     */
    fun neighbours(rows: List<SubRow>, row: SubRow): Pair<SubRow?, SubRow?> {
        val at = rows.indexOfFirst { it.id == row.id }
        if (at < 0 || row.kind == SubKind.UNGROUPED || row.updating) return null to null
        return rows.getOrNull(at - 1) to rows.getOrNull(at + 1)
    }
}

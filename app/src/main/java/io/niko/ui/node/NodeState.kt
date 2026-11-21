package io.niko.ui.node

import io.niko.database.DataStore
import io.niko.database.SagerDatabase
import io.niko.ktx.Logs
import io.niko.raycontrol.RayChannel
import io.niko.raycontrol.RayClient
import io.niko.raycontrol.RayNode
import io.niko.raycontrol.RayNodeCard
import io.niko.raycontrol.RayNodes
import io.niko.raycontrol.RayTraffic
import io.niko.ui.home.ConfigRow
import io.niko.ui.home.ConfigsLoader
import kotlin.math.abs

/** Why the node card warns above everything (D-NODE-09, client-min-v1): the first that holds. */
sealed interface NodeWarning {
    /** the node refused this device: import its invite again */
    data object Refused : NodeWarning

    /** the subscription failed [times] in a row */
    data class Failing(val times: Int) : NodeWarning

    /** this app is below the node's minimum [min]; [from] unix s, 0 already refused */
    data class ClientMin(val min: String, val from: Long) : NodeWarning
}

/** What the node card (P-Node, D-NODE-01…10, 13) shows of one raycontrol node. */
data class NodeCardState(
    val nodeId: String,
    /** the name the admin gave, else the address */
    val name: String,
    /** the admin's note, null none */
    val note: String?,
    val warning: NodeWarning?,
    val traffic: RayTraffic?,
    /** the subscription's end, unix s, 0 none */
    val expires: Long,
    /** when the subscription last came, unix s, 0 never */
    val updated: Long,
    /** this network's path when the node has relays: through one (true), direct (false); null not known or no relays */
    val relayPath: Boolean?,
    /** this phone's clock against the node's, s, when off by more than the card warns of (RayNodeCard); null not */
    val clockSkew: Long?,
    /** the kind of resolver this network's dnstt tunnel takes (RayDnsResolvers), null none picked */
    val dnstt: String?,
    /** the node's group of profiles, for its test */
    val groupId: Long,
    val profiles: List<ConfigRow>,
    val admin: Boolean,
    /** the node offers its local network, or it was chosen here before (lan-v1) */
    val lan: Boolean,
)

/** Reads the node card from the store and the database, off the main thread. */
object NodeLoader {

    fun load(nodeId: String): NodeCardState? {
        val node = RayNodes.get(nodeId) ?: return null
        val now = System.currentTimeMillis() / 1000
        val selected = DataStore.selectedProxy
        val rows = node.profileIds.orEmpty().values.sorted().mapNotNull { id ->
            SagerDatabase.proxyDao.getById(id)?.let { proxy ->
                runCatching { ConfigsLoader.row(proxy, selected) }.onFailure { Logs.w(it) }.getOrNull()
            }
        }
        // the group's own order when the rows are in the database: the order the subscription gave
        val order = SagerDatabase.proxyDao.getIdsByGroup(node.groupId)
        val ordered = rows.sortedBy { order.indexOf(it.id).let { i -> if (i < 0) Int.MAX_VALUE else i } }
        val network = RayChannel.network()
        return NodeCardState(
            nodeId = nodeId,
            name = node.name ?: node.host,
            note = RayNodeCard.note(node),
            warning = warning(node, now),
            traffic = node.traffic,
            expires = node.subExpires,
            updated = node.subUpdatedAt,
            relayPath = network?.takeIf { node.relays.isNotEmpty() }?.let { RayChannel.relayPath(node.spa.nodeId, it, now) },
            clockSkew = node.clockSkew.takeIf { abs(it) > RayNodeCard.CLOCK_OFF },
            dnstt = network?.let { RayChannel.dnsPick(node.spa.nodeId, it, "dnstt")?.get("kind")?.asString },
            groupId = node.groupId,
            profiles = ordered,
            admin = node.isAdmin,
            lan = node.lan != null || node.lanShow != null,
        )
    }

    fun warning(node: RayNode, now: Long, version: String = RayClient.version): NodeWarning? = when {
        node.refreshAt == RayNodes.STOPPED -> NodeWarning.Refused
        else -> RayNodeCard.clientMin(node, now, version)?.let { NodeWarning.ClientMin(it.min, it.from) }
            ?: node.failedFetches.takeIf { it > 0 }?.let { NodeWarning.Failing(it) }
    }
}

package io.niko.raycontrol

import kotlin.math.abs

/**
 * The short technical facts about a raycontrol node (its card, ui/node): when its subscription came and until when it
 * holds, what keeps it from updating, the path and the dnstt resolver on this network, a clock off.
 */
object RayNodeCard {

    sealed class Fact {
        /** The node refused this device: import it again. */
        data object Refused : Fact()
        data class Failing(val times: Int) : Fact()
        data class Expires(val at: Long, val past: Boolean) : Fact()
        data class Path(val relay: Boolean) : Fact()
        data class Dnstt(val kind: String) : Fact()
        data class Clock(val skew: Long) : Fact()
        data class Traffic(val traffic: RayTraffic) : Fact()

        /** This app is below the node's minimum [min] (client-min-v1): from [from] (unix s), 0 already. */
        data class ClientMin(val min: String, val from: Long) : Fact()
    }

    // a skew the card mentions: SPA v2 fails at 30 s
    const val CLOCK_OFF = 30L

    /**
     * What the card says about [node], in its order; [relay] and [dnsttKind] are this network's (null: unknown),
     * [version] this app's.
     */
    fun facts(node: RayNode, now: Long, relay: Boolean?, dnsttKind: String?, version: String = RayClient.version): List<Fact> = buildList {
        when {
            node.refreshAt == RayNodes.STOPPED -> add(Fact.Refused)
            node.failedFetches > 0 -> add(Fact.Failing(node.failedFetches))
        }
        // the node's minimum version, once this app is below it: refused already, or from a date (the node's clock)
        clientMin(node, now, version)?.let { add(it) }
        // the user's traffic: what is used of the limit, or used up; after what keeps the subscription from coming
        node.traffic?.let { add(Fact.Traffic(it)) }
        if (node.subExpires > 0) add(Fact.Expires(node.subExpires, node.subExpires <= now))
        // the path only for a node with relays: without one it is always direct
        if (relay != null && node.relays.isNotEmpty()) add(Fact.Path(relay))
        dnsttKind?.let { add(Fact.Dnstt(it)) }
        if (abs(node.clockSkew) > CLOCK_OFF) add(Fact.Clock(node.clockSkew))
    }

    /** The node's minimum this [version] is below, null none: the card says it and a tap leads to the update. */
    fun clientMin(node: RayNode, now: Long, version: String = RayClient.version): Fact.ClientMin? {
        val client = node.client?.takeIf { RayClient.below(it, version) } ?: return null
        val from = client.from - node.clockSkew
        return Fact.ClientMin(client.niko ?: return null, if (client.refused || from <= now) 0 else from)
    }

    /**
     * The node's note as the card shows it: plain text (no links, no markup: a TextView without autoLink) in an
     * isolate, so a right-to-left note keeps its own order and leaves the lines around it theirs. It wraps.
     */
    fun note(node: RayNode) = node.note?.let { "\u2068$it\u2069" }
}

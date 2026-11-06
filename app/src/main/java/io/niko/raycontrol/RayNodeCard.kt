package io.niko.raycontrol

import android.content.Context
import io.niko.R
import java.text.DateFormat
import java.util.Date
import kotlin.math.abs

/**
 * The short technical lines on a raycontrol node's group card (GroupFragment): when its subscription came and until
 * when it holds, what keeps it from updating, the path and the dnstt resolver on this network, a clock off.
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
    }

    // a skew the card mentions: SPA v2 fails at 30 s
    private const val CLOCK_OFF = 30L

    /** What the card says about [node], in its order; [relay] and [dnsttKind] are this network's (null: unknown). */
    fun facts(node: RayNode, now: Long, relay: Boolean?, dnsttKind: String?): List<Fact> = buildList {
        when {
            node.refreshAt == RayNodes.STOPPED -> add(Fact.Refused)
            node.failedFetches > 0 -> add(Fact.Failing(node.failedFetches))
        }
        if (node.subExpires > 0) add(Fact.Expires(node.subExpires, node.subExpires <= now))
        // the path only for a node with relays: without one it is always direct
        if (relay != null && node.relays.isNotEmpty()) add(Fact.Path(relay))
        dnsttKind?.let { add(Fact.Dnstt(it)) }
        if (abs(node.clockSkew) > CLOCK_OFF) add(Fact.Clock(node.clockSkew))
    }

    /** The status line (profiles, last update) and the line of facts, empty when there are none. */
    fun lines(context: Context, node: RayNode, profiles: Long): Pair<String, String> {
        val now = System.currentTimeMillis() / 1000
        val format = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        val status = if (node.subUpdatedAt > 0) {
            context.getString(R.string.raycontrol_card_status, profiles, format.format(Date(node.subUpdatedAt * 1000)))
        } else {
            context.getString(R.string.raycontrol_card_status_never, profiles)
        }
        val network = RayChannel.network()
        val relay = network?.let { RayChannel.relayPath(node.spa.nodeId, it, now) }
        val dnstt = network?.let { RayChannel.dnsPick(node.spa.nodeId, it, "dnstt")?.get("kind")?.asString }
        val info = facts(node, now, relay, dnstt).joinToString(" · ") { fact ->
            when (fact) {
                Fact.Refused -> context.getString(R.string.raycontrol_card_refused)
                is Fact.Failing -> context.getString(R.string.raycontrol_card_failing, fact.times)
                is Fact.Expires -> context.getString(
                    if (fact.past) R.string.raycontrol_card_expired else R.string.raycontrol_card_expires,
                    format.format(Date(fact.at * 1000)),
                )
                is Fact.Path -> context.getString(if (fact.relay) R.string.raycontrol_card_path_relay else R.string.raycontrol_card_path_direct)
                is Fact.Dnstt -> context.getString(R.string.raycontrol_card_dnstt, kindName(context, fact.kind))
                is Fact.Clock -> context.getString(R.string.raycontrol_card_clock, fact.skew)
            }
        }
        return status to info
    }

    private fun kindName(context: Context, kind: String) = when (kind) {
        "net" -> context.getString(R.string.raycontrol_card_dns_net)
        RayDnsResolvers.NONE -> context.getString(R.string.raycontrol_card_dns_none)
        else -> kind.uppercase()
    }
}

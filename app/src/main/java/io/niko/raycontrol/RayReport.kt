package io.niko.raycontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.niko.database.DataStore

/**
 * The anonymous summary for a node (RAYCONTROL.md §14.3 with niko-r14-reply.md §2): what this device's own probes
 * saw on the current network, in coarse buckets, sent only with the user's consent (setting raycontrolReports, off
 * by default) and only about the current network, from it, at most once a day. No device or network identifiers,
 * no times, no exact figures; the node takes the ASN from the request's address and keeps counters only.
 *
 * Sending waits for the node's POST /probe/v1/report; until then the report is only built (and shown on request).
 */
object RayReport {

    // the observations of the last day
    private const val RECENT = 24 * 3600L
    private const val MAX_OBS = 16

    /** works / degraded / fails of a probe (degraded: TCP works but stalled, under 1 Mbit/s, or UDP loses > 20 %). */
    fun result(s: ChannelScore): String = when {
        !s.tcpOk -> "fails"
        s.stalledAt >= 0 || s.kbps < 1000 || s.udpOk && s.udpLossPct > 20 -> "degraded"
        else -> "works"
    }

    /** The stall threshold rounded down to a power of two: 8, 16, 32, 64 or 128 KiB; null without a stall. */
    fun stallKib(s: ChannelScore): Int? {
        if (s.stalledAt < 0) return null
        val kib = (s.stalledAt / 1024).toInt()
        return listOf(128, 64, 32, 16, 8).firstOrNull { kib >= it } ?: 8
    }

    /** The UDP loss bucket in %: 0-1, 1-5, 5-20, 20-50, 50-100, or none when UDP did not pass. */
    fun udpBucket(s: ChannelScore): String = when {
        !s.udpOk -> "none"
        s.udpLossPct <= 1 -> "0-1"
        s.udpLossPct <= 5 -> "1-5"
        s.udpLossPct <= 20 -> "5-20"
        s.udpLossPct <= 50 -> "20-50"
        else -> "50-100"
    }

    /** The download speed bucket through the tunnel, Mbit/s. */
    fun mbpsBucket(s: ChannelScore): String = when {
        s.kbps < 1000 -> "<1"
        s.kbps < 5000 -> "1-5"
        s.kbps < 20_000 -> "5-20"
        s.kbps < 100_000 -> "20-100"
        else -> "100+"
    }

    /** The report about [network] for [node] from the last day's probes there, or null when there is nothing. */
    fun build(node: RayNode, network: ChannelNetwork, now: Long = System.currentTimeMillis() / 1000): JsonObject? {
        val scores = RayChannel.scores(node.spa.nodeId, network)
        val bySvc = LinkedHashMap<Int, ChannelScore>()
        for ((key, score) in scores) {
            if (now - score.at > RECENT) continue
            val svc = key.substringBefore('#').toIntOrNull() ?: continue
            // one per service: the latest
            if ((bySvc[svc]?.at ?: 0) < score.at) bySvc[svc] = score
        }
        if (bySvc.isEmpty()) return null
        return JsonObject().apply {
            addProperty("v", 1)
            addProperty("net", network.kind)
            add("obs", JsonArray().apply {
                bySvc.entries.take(MAX_OBS).forEach { (svc, s) ->
                    add(JsonObject().apply {
                        addProperty("svc", svc)
                        addProperty("result", result(s))
                        stallKib(s)?.let { addProperty("tcp_stall_kib", it) }
                        addProperty("udp", udpBucket(s))
                        if (s.tcpOk) addProperty("mbps", mbpsBucket(s))
                    })
                }
            })
        }
    }

    /** Consent given (the setting is on). Sending itself waits for the node's endpoint. */
    val consented get() = DataStore.raycontrolReports
}

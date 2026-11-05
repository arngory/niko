package io.niko.raycontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.database.DataStore
import io.niko.ktx.Logs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import libcore.Libcore

/**
 * The anonymous summary for a node (RAYCONTROL.md §14.3 with niko-r14-reply.md §2): what this device's own probes
 * saw on the current network, in coarse buckets, sent only with the user's consent (setting raycontrolReports, off
 * by default) and only about the current network, from it, at most once a day. No device or network identifiers,
 * no times, no exact figures; the node takes the ASN from the request's address and keeps counters only.
 *
 * It goes to the node's POST /probe/v1/report (raycontrol v0.5.0, probe-v1.md §6.1) directly, after a probe or a
 * passing connection test on the network: the node takes one per device and network a UTC day.
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

    /** The bucket of the largest UDP echo that came back directly (probe-v1.md §6.1): <1200 means no QUIC there. */
    fun mtuBucket(mtu: Int): String = when {
        mtu < 1200 -> "<1200"
        mtu < 1350 -> "1200-1350"
        mtu < 1472 -> "1350-1472"
        else -> "1472+"
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
        // the node refuses a report with a service the user does not have (400): only the subscription's current ones
        val current = node.profileIds.orEmpty().keys.mapNotNull { it.substringBefore('#').toIntOrNull() }.toSet()
        val bySvc = LinkedHashMap<Int, ChannelScore>()
        for ((key, score) in scores) {
            if (now - score.at > RECENT) continue
            val svc = key.substringBefore('#').toIntOrNull()?.takeIf { it in current } ?: continue
            // one per service: the latest
            if ((bySvc[svc]?.at ?: 0) < score.at) bySvc[svc] = score
        }
        if (bySvc.isEmpty()) return null
        return JsonObject().apply {
            addProperty("v", 1)
            addProperty("net", network.kind)
            // only to a node that takes it (probe.report): an older one answers 400, which closes the day
            if ("udp_mtu" in node.probe?.report.orEmpty()) {
                RayChannel.directUdpMtu(node.spa.nodeId, network, now - RECENT)?.let { addProperty("udp_mtu", mtuBucket(it)) }
            }
            // the path this network reaches the node by (relay-v1 §9.3), to a node that declares it
            if ("path" in node.probe?.report.orEmpty()) {
                addProperty("path", if (RayChannel.relayPath(node.spa.nodeId, network, now)) "relay" else "direct")
            }
            add("obs", JsonArray().apply {
                bySvc.entries.take(MAX_OBS).forEach { (svc, s) ->
                    add(JsonObject().apply {
                        addProperty("svc", svc)
                        addProperty("result", result(s))
                        stallKib(s)?.let { addProperty("tcp_stall_kib", it) }
                        // not measured: no field (§14.3), not "none"
                        if (s.udpMeasured) addProperty("udp", udpBucket(s))
                        if (s.tcpOk) addProperty("mbps", mbpsBucket(s))
                        // the resolver kind a DNS tunnel went through in this probe (dnsprobe-v1), never its address;
                        // only for a tunnel that worked, to a node that declares it
                        val kind = node.services.firstOrNull { it.id == svc }?.kind
                        if ("dns" in node.probe?.report.orEmpty() && (kind == "dnstt" || kind == "masterdns") && s.tcpOk) {
                            s.dnsKind?.let { addProperty("dns", it) }
                        }
                    })
                }
            })
        }
    }

    /** Consent given (the setting is on). */
    val consented get() = DataStore.raycontrolReports

    /** What became of a summary. */
    enum class Outcome { SENT, ALREADY_SENT, NOT_TAKEN, REFUSED, FAILED }

    private const val DAY = 24 * 3600L

    // a summary that got no answer goes again after this
    private const val RETRY = 3600L

    private val sending = Mutex()

    // what the node answers to a summary: taken, a repeat, not taken, refused, not directly
    private val FINAL = setOf(200, 204, 400, 404, 409, 429)

    /**
     * Sends the summary about the network the device is on to [node], directly from it, when the user agreed and the
     * node answers probes: once a UTC day per network, also when the node refused it or takes none (404). Returns
     * what happened, or null when nothing was due (no consent, sent or answered today, nothing measured here).
     */
    suspend fun send(node: RayNode): Outcome? = sending.withLock {
        if (!consented || node.probe == null || node.subscription == null) return null
        val network = RayChannel.network() ?: return null
        val nodeId = node.spa.nodeId
        val now = System.currentTimeMillis() / 1000
        val today = now / DAY
        val state = RayChannel.reportState(nodeId, network)
        if (state?.get("day")?.asLong == today && (state.get("status")?.asInt ?: 0) != 0) return null
        if ((state?.get("retry_at")?.asLong ?: 0) > now) return null
        val report = build(node, network, now) ?: return null
        // a network that reaches the node through a relay reports to the relay (relay-v1 §9.3): the node would see the
        // relay's address there, and the relay passes the client's network on; none that takes reports: nothing due
        val through = if (report.get("path")?.asString == "relay") {
            node.relays.firstOrNull { it.probe?.report == true } ?: return null
        } else null
        // a short window: over UDP and then the TCP carrier, a node that does not answer costs ~10 s
        val request = (through?.let { RayRelays.probeRequest(node, it, RayNodes.deviceKeyHex(node)) } ?: RayBrutal.request(node)).apply {
            add("report", report)
            addProperty("window_ms", 5_000)
        }
        val result = try {
            JsonParser.parseString(withContext(Dispatchers.IO) { Libcore.raycontrolProbe(request.toString()) }).asJsonObject
        } catch (e: CancellationException) {
            // a stop or restart: nothing is recorded, the next run tries again (a repeat that went out gets 429)
            throw e
        } catch (e: Exception) {
            Logs.w("raycontrol ${node.host}: summary: ${e.message}")
            null
        }
        val status = result?.get("report_status")?.asInt ?: 0
        // the node's answers (probe-v1.md §6.1) end the day for this network; anything else (no answer, a front's
        // 502 while the node restarts) is tried again within the hour
        val final = status in FINAL
        RayChannel.recordReportState(nodeId, network, JsonObject().apply {
            addProperty("at", now)
            addProperty("day", today)
            addProperty("status", if (final) status else 0)
            if (!final) addProperty("retry_at", now + RETRY)
        })
        val outcome = when (status) {
            200, 204 -> Outcome.SENT
            429 -> Outcome.ALREADY_SENT
            404 -> Outcome.NOT_TAKEN
            400, 409 -> Outcome.REFUSED
            else -> Outcome.FAILED
        }
        Logs.i("raycontrol ${node.host}: summary of this network${through?.let { " (through relay ${it.id})" } ?: ""}: $outcome" +
            (if (status != 0) " ($status)" else result?.get("report_error")?.asString?.let { ": $it" } ?: ""))
        outcome
    }
}

package io.niko.raycontrol

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.database.DataStore
import io.niko.database.SagerDatabase
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.ktx.Logs
import kotlinx.coroutines.Job
import libcore.Libcore
import kotlin.math.abs
import kotlin.math.max

/** Brutal rates of a node's Hysteria2 profiles on one network (kbit/s, 0 = BBR for that direction). */
data class BrutalRates(val at: Long, val downKbps: Long, val upKbps: Long) {
    val any get() = downKbps > 0 || upKbps > 0

    fun toJson() = JsonObject().apply {
        addProperty("at", at)
        addProperty("down_kbps", downKbps)
        addProperty("up_kbps", upKbps)
    }

    companion object {
        fun fromJson(o: JsonObject) = BrutalRates(
            o.get("at").asLong, o.get("down_kbps")?.asLong ?: 0, o.get("up_kbps")?.asLong ?: 0,
        )
    }
}

/**
 * Brutal for a raycontrol node's Hysteria2 by measurement (setting raycontrolBrutal, off by default): the channel
 * is measured directly (probe-v1: down, up, the UDP echo), once a day per network; the profile then declares a
 * share of what got through, each direction apart (a mobile network gives a smaller share: its capacity moves).
 * Without a measurement for this network, or when UDP does not pass well, the core keeps BBR. While a rate is in
 * use, the node's view of this device's own Hysteria2 session (link `own`, niko-server v0.7.0; the address's sums
 * before) shows whether the path takes it: an ack rate under 0.9 twice in a row lowers the download rate.
 */
object RayBrutal {

    private const val ACK_MIN = 0.9
    private const val LOWER = 0.75
    private const val LINK_EVERY_EARLY = 5 * 60L
    private const val LINK_EVERY = 30 * 60L
    private const val EARLY = 15 * 60L

    // per watchdog run: when it started, the last link call, low ack rates in a row
    @Volatile
    private var runStart = 0L

    @Volatile
    private var lastLink = 0L

    @Volatile
    private var strikes = 0

    /** A run of the core began: its link watch starts over. */
    fun reset() {
        runStart = System.currentTimeMillis() / 1000
        lastLink = 0
        strikes = 0
    }

    /** The rates the config applies for [node] on this network, if measured and fresh. */
    fun ratesFor(node: RayNode): BrutalRates? {
        if (!DataStore.raycontrolBrutal) return null
        val network = RayChannel.network() ?: return null
        return RayChannel.brutalRates(node.spa.nodeId, network)?.takeIf { it.any }
    }

    /**
     * After a test that passed, from the watchdog: measures this network once a day and restarts the core with the
     * new rates, or watches the ack rate of the running ones. [caller] is the watchdog's job (its restart).
     */
    suspend fun afterTest(node: RayNode, key: String, caller: Job?) {
        if (!DataStore.raycontrolBrutal || node.probe == null || node.subscription == null) return
        val id = node.profileIds?.get(key) ?: return
        if (SagerDatabase.proxyDao.getById(id)?.requireBean() !is HysteriaBean) return
        val network = RayChannel.network() ?: return
        val now = System.currentTimeMillis() / 1000
        val known = RayChannel.brutalRates(node.spa.nodeId, network)
        if (known == null) {
            // measured once a day per network, also when it fails (that marks the network as BBR for the day)
            val rates = measure(node, network.mobile, now)
            RayChannel.recordBrutal(node.spa.nodeId, network, rates)
            Logs.i("raycontrol ${node.host}: Brutal on this network: down ${rates.downKbps} kbit/s, up ${rates.upKbps} kbit/s")
            if (rates.any) RayWatchdog.restart(caller)
            return
        }
        if (!known.any) return
        val every = if (now - runStart < EARLY) LINK_EVERY_EARLY else LINK_EVERY
        if (now - lastLink < every) return
        lastLink = now
        val ack = ackRate(node, key, known) ?: return
        if (ack >= ACK_MIN) {
            strikes = 0
            return
        }
        if (++strikes < 2) return
        strikes = 0
        val lower = known.copy(at = now, downKbps = (known.downKbps * LOWER).toLong().takeIf { it >= 1000 } ?: 0)
        RayChannel.recordBrutal(node.spa.nodeId, network, lower)
        Logs.i("raycontrol ${node.host}: ack rate $ack under $ACK_MIN: download rate ${known.downKbps} → ${lower.downKbps} kbit/s")
        RayWatchdog.restart(caller)
    }

    private fun request(node: RayNode) = JsonObject().apply {
        val sub = node.subscription!!
        addProperty("host", node.host)
        addProperty("port", sub.port)
        addProperty("pin", sub.pin)
        addProperty("device_id", node.deviceId)
        add("spa", node.spa.toJson().apply { addProperty("device_key", RayNodes.deviceKeyHex(node)) })
    }

    /** Direct down, up and echo; the share of what got through, or zeros (BBR) when UDP does not pass well. */
    private fun measure(node: RayNode, mobile: Boolean, now: Long): BrutalRates {
        val r = request(node).apply {
            addProperty("down_bytes", if (mobile) 4L shl 20 else 16L shl 20)
            addProperty("up_bytes", if (mobile) 2L shl 20 else 8L shl 20)
            if (node.probe?.udp == true) {
                addProperty("echo_count", 50)
                addProperty("echo_size", 1200)
            }
            addProperty("window_ms", 10_000)
        }
        val o = try {
            JsonParser.parseString(Libcore.raycontrolProbe(r.toString())).asJsonObject
        } catch (e: Exception) {
            Logs.w("raycontrol ${node.host}: channel measurement: ${e.message}")
            return BrutalRates(now, 0, 0)
        }
        val echo = o.getAsJsonObject("echo")
        val sent = echo?.get("sent")?.asInt ?: 0
        val lossPct = if (sent > 0) (echo?.get("lost")?.asInt ?: 0) * 100.0 / sent else 100.0
        // Brutal sends at its rate regardless of loss: where UDP is lossy or blocked it only adds to it
        if (echo?.get("ok")?.asBoolean != true || lossPct > 20) return BrutalRates(now, 0, 0)
        val share = if (mobile) 0.7 else 0.85
        fun rate(kbps: Long?) = ((kbps ?: 0) * share).toLong().takeIf { it >= 1000 } ?: 0
        val down = o.getAsJsonObject("down")?.takeIf { it.get("ok")?.asBoolean == true }?.get("kbps")?.asLong
        val up = o.getAsJsonObject("up")?.takeIf { it.get("ok")?.asBoolean == true }?.get("kbps")?.asLong
        return BrutalRates(now, rate(down), rate(up))
    }

    /**
     * The ack rate of this device's own session on the profile's service (link `own`), picked by the rate it
     * declared; the median over the address when the node has no `own` (niko-server before v0.7.0).
     */
    private fun ackRate(node: RayNode, key: String, rates: BrutalRates): Double? {
        val svc = key.substringBefore('#')
        val o = try {
            JsonParser.parseString(Libcore.raycontrolProbe(request(node).apply { addProperty("link", true) }.toString())).asJsonObject
        } catch (e: Exception) {
            Logs.w("raycontrol ${node.host}: link: ${e.message}")
            return null
        }
        val service = o.getAsJsonObject("link")?.getAsJsonObject("hysteria2")?.getAsJsonObject(svc) ?: return null
        val declared = rates.downKbps * 1000.0
        val own = service.getAsJsonArray("own")?.mapNotNull { (it as? JsonObject)?.getAsJsonObject("brutal") }
        if (own != null) {
            val mine = own.minByOrNull { abs((it.get("rate_bps")?.asDouble ?: 0.0) - declared) } ?: return null
            return mine.get("ack_rate")?.asDouble
        }
        return service.getAsJsonObject("brutal")?.getAsJsonObject("ack_rate")?.get("median")?.asDouble
    }

    /** Mbit/s for the core, at least 1, from kbit/s. */
    fun mbps(kbps: Long) = max(1L, kbps / 1000).toInt()
}

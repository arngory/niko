package io.niko.raycontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.database.DataStore
import io.niko.database.SagerDatabase
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.ktx.Logs
import kotlinx.coroutines.Job
import libcore.Libcore
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.max

/** Brutal rates of a node's Hysteria2 profiles on one network (kbit/s, 0 = BBR for that direction). */
data class BrutalRates(val at: Long, val downKbps: Long, val upKbps: Long) {
    val any get() = downKbps > 0 || upKbps > 0

    /** The same rates, whenever measured. */
    fun sameRates(other: BrutalRates?) = downKbps == (other?.downKbps ?: 0) && upKbps == (other?.upKbps ?: 0)

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
 * Without a measurement for this network, or when UDP does not pass well, the core keeps BBR. The config declares
 * the rates of the network it was built on: on another network, or when they change, the core restarts with the
 * right ones. While a rate is in use, the node's view of this device's own Hysteria2 session (link `own`,
 * niko-server v0.7.0; the address's median before) shows whether the path takes it: an ack rate under 0.9 twice in
 * a row lowers the download rate.
 */
object RayBrutal {

    private const val ACK_MIN = 0.9
    private const val LOWER = 0.75
    private const val LINK_EVERY_EARLY = 5 * 60L
    private const val LINK_EVERY = 30 * 60L
    private const val EARLY = 15 * 60L

    // a measurement that could not run (the node's probe slot busy, no answer): tried again after this long
    private const val RETRY = 3600L

    // node ID → the rates the running config declares for its Hysteria2 (null: BBR)
    private val applied = ConcurrentHashMap<String, Applied>()

    private data class Applied(val rates: BrutalRates?)

    private val retryAt = ConcurrentHashMap<String, Long>()

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

    /** The rates a config should declare for [node] on [network]: measured, fresh, not all zero; null is BBR. */
    fun ratesFor(node: RayNode, network: ChannelNetwork?): BrutalRates? {
        if (!DataStore.raycontrolBrutal || network == null) return null
        return RayChannel.brutalRates(node.spa.nodeId, network)?.takeIf { it.any }
    }

    /** The main config was built with [rates] (null: BBR) for [node]'s Hysteria2. */
    fun noteApplied(node: RayNode, rates: BrutalRates?) {
        applied[node.spa.nodeId] = Applied(rates)
    }

    /**
     * After a test that passed, from the watchdog: restarts the core when it declares other rates than this network
     * wants, measures this network once a day, and watches the ack rate of the declared one. [caller] is the
     * watchdog's job (its restart).
     */
    suspend fun afterTest(node: RayNode, key: String, caller: Job?) {
        val id = node.profileIds?.get(key) ?: return
        val bean = SagerDatabase.proxyDao.getById(id)?.requireBean() as? HysteriaBean ?: return
        // the config sets rates for Hysteria2 only
        if (bean.protocolVersion != 2) return
        val nodeId = node.spa.nodeId
        val network = RayChannel.network()
        val declared = applied[nodeId]
        val wanted = ratesFor(node, network)
        if (declared != null && !(declared.rates?.sameRates(wanted) ?: (wanted == null))) {
            Logs.i("raycontrol ${node.host}: the Brutal rates of the running core are not this network's, restarting")
            RayWatchdog.restart(caller)
            return
        }
        if (!DataStore.raycontrolBrutal || network == null || node.probe == null || node.subscription == null) return
        val now = System.currentTimeMillis() / 1000
        val known = RayChannel.brutalRates(nodeId, network)
        if (known == null) {
            if (now < (retryAt[network.key] ?: 0)) return
            val rates = measure(node, network.mobile, now) ?: run {
                retryAt[network.key] = now + RETRY
                return
            }
            RayChannel.recordBrutal(nodeId, network, rates)
            Logs.i("raycontrol ${node.host}: Brutal on this network: down ${rates.downKbps} kbit/s, up ${rates.upKbps} kbit/s")
            if (rates.any) RayWatchdog.restart(caller)
            return
        }
        val inUse = declared?.rates ?: return
        val every = if (now - runStart < EARLY) LINK_EVERY_EARLY else LINK_EVERY
        if (now - lastLink < every) return
        lastLink = now
        val ack = ackRate(node, key, inUse) ?: return
        if (ack >= ACK_MIN) {
            strikes = 0
            return
        }
        if (++strikes < 2) return
        strikes = 0
        // the measurement's time stays: the rate is measured again a day after it, not a day after lowering
        val lower = known.copy(downKbps = (inUse.downKbps * LOWER).toLong().takeIf { it >= 1000 } ?: 0)
        RayChannel.recordBrutal(nodeId, network, lower)
        Logs.i("raycontrol ${node.host}: ack rate $ack under $ACK_MIN: download rate ${inUse.downKbps} → ${lower.downKbps} kbit/s")
        RayWatchdog.restart(caller)
    }

    /** A direct probe request to [node]'s subscription endpoint (libcore RaycontrolProbe), steps to be added. */
    fun request(node: RayNode) = JsonObject().apply {
        val sub = node.subscription!!
        addProperty("host", node.host)
        addProperty("port", sub.port)
        addProperty("pin", sub.pin)
        addProperty("device_id", node.deviceId)
        add("spa", node.spa.toJson().apply { addProperty("device_key", RayNodes.deviceKeyHex(node)) })
    }

    /**
     * Direct down, up and echo: the share of what got through, zeros (BBR for the day) when UDP does not pass well,
     * null when the measurement could not run (the node's probe slot busy, the day's volume spent, no answer).
     */
    private fun measure(node: RayNode, mobile: Boolean, now: Long): BrutalRates? {
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
            return null
        }
        val down = o.getAsJsonObject("down")
        val up = o.getAsJsonObject("up")
        val echo = o.getAsJsonObject("echo")
        if (listOf(down, up, echo).any { it?.get("status")?.asInt?.let { s -> s == 429 || s == 404 } == true }) return null
        if (down?.get("ok")?.asBoolean != true) return null
        val sent = echo?.get("sent")?.asInt ?: 0
        val lossPct = if (sent > 0) (echo?.get("lost")?.asInt ?: 0) * 100.0 / sent else 100.0
        // Brutal sends at its rate regardless of loss: where UDP is lossy or blocked it only adds to it
        if (echo?.get("ok")?.asBoolean != true || lossPct > 20) return BrutalRates(now, 0, 0)
        val share = if (mobile) 0.7 else 0.85
        fun rate(kbps: Long?) = ((kbps ?: 0) * share).toLong().takeIf { it >= 1000 } ?: 0
        val upKbps = up?.takeIf { it.get("ok")?.asBoolean == true }?.get("kbps")?.asLong
        return BrutalRates(now, rate(down.get("kbps")?.asLong), rate(upKbps))
    }

    /**
     * The ack rate of this device's own session on the profile's service (link `own`), picked by the rate it
     * declared; the median over the address when the node has no `own` (niko-server before v0.7.0).
     */
    private fun ackRate(node: RayNode, key: String, declared: BrutalRates): Double? {
        val svc = key.substringBefore('#')
        val o = try {
            JsonParser.parseString(Libcore.raycontrolProbe(request(node).apply { addProperty("link", true) }.toString())).asJsonObject
        } catch (e: Exception) {
            Logs.w("raycontrol ${node.host}: link: ${e.message}")
            return null
        }
        val service = (o.get("link") as? JsonObject)?.let { it.get("hysteria2") as? JsonObject }?.let { it.get(svc) as? JsonObject }
            ?: return null
        val rateBps = declared.downKbps * 1000.0
        val own = (service.get("own") as? JsonArray)?.mapNotNull { (it as? JsonObject)?.get("brutal") as? JsonObject }
        if (own != null) {
            val mine = own.minByOrNull { abs((it.get("rate_bps")?.asDouble ?: 0.0) - rateBps) } ?: return null
            return mine.get("ack_rate")?.takeIf { it.isJsonPrimitive }?.asDouble
        }
        return ((service.get("brutal") as? JsonObject)?.get("ack_rate") as? JsonObject)?.get("median")?.takeIf { it.isJsonPrimitive }?.asDouble
    }

    /** Mbit/s for the core, at least 1, from kbit/s. */
    fun mbps(kbps: Long) = max(1L, kbps / 1000).toInt()
}

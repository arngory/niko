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

/**
 * Brutal rates of a node's Hysteria2 profiles on one network (kbit/s, 0 = BBR for that direction); [lowered]: how
 * often the ack rate lowered the download rate since the measurement.
 */
data class BrutalRates(val at: Long, val downKbps: Long, val upKbps: Long, val lowered: Int = 0) {
    val any get() = downKbps > 0 || upKbps > 0

    /** The same rates, whenever measured. */
    fun sameRates(other: BrutalRates?) = downKbps == (other?.downKbps ?: 0) && upKbps == (other?.upKbps ?: 0)

    /**
     * Close enough to [other] (null: BBR) that a restart of the core for the difference is not worth its broken
     * connections: within 20 % each direction, BBR only where both are BBR (P3).
     */
    fun closeTo(other: BrutalRates?): Boolean {
        fun close(a: Long, b: Long) = if (a == 0L || b == 0L) a == b else abs(a - b) <= max(a, b) / 5
        return close(downKbps, other?.downKbps ?: 0) && close(upKbps, other?.upKbps ?: 0)
    }

    fun toJson() = JsonObject().apply {
        addProperty("at", at)
        addProperty("down_kbps", downKbps)
        addProperty("up_kbps", upKbps)
        if (lowered > 0) addProperty("lowered", lowered)
    }

    companion object {
        fun fromJson(o: JsonObject) = BrutalRates(
            o.get("at").asLong, o.get("down_kbps")?.asLong ?: 0, o.get("up_kbps")?.asLong ?: 0, o.get("lowered")?.asInt ?: 0,
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

    /**
     * The rates a config should declare for [node] on [network]: measured, not all zero; null is BBR. A measurement
     * older than a day (up to a week) still counts until a new one replaces it (P3): the core keeps its rate instead
     * of a restart to BBR and another one after the measurement. Roaming: BBR, nothing is measured there.
     */
    fun ratesFor(node: RayNode, network: ChannelNetwork?): BrutalRates? {
        if (!DataStore.raycontrolBrutal || network == null || network.roaming) return null
        return RayChannel.brutalRates(node.spa.nodeId, network, RayChannel.BRUTAL_KEPT)?.takeIf { it.any }
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
        val now = System.currentTimeMillis() / 1000
        // a day old or a new network: measure first, then restart at most once for what it says (P3)
        val measuring = DataStore.raycontrolBrutal && network != null && !network.roaming && node.probe != null &&
                node.subscription != null && RayChannel.brutalRates(nodeId, network) == null
        if (measuring && now >= (retryAt[network!!.key] ?: 0)) {
            when (val m = measure(node, network, now)) {
                is Measurement.Done -> {
                    RayChannel.recordBrutal(nodeId, network, m.rates)
                    Logs.i("raycontrol ${node.host}: Brutal on this network: down ${m.rates.downKbps} kbit/s, up ${m.rates.upKbps} kbit/s")
                }
                // the node's Retry-After when longer than an hour (the day's volume spent)
                is Measurement.Later -> retryAt[network.key] = now + max(RETRY, m.retryAfter)
            }
        }
        val wanted = ratesFor(node, network)
        if (declared != null && !(declared.rates?.closeTo(wanted) ?: (wanted == null))) {
            Logs.i("raycontrol ${node.host}: the Brutal rates of the running core are not this network's, restarting")
            RayWatchdog.restart(caller)
            return
        }
        if (!DataStore.raycontrolBrutal || network == null) return
        val known = RayChannel.brutalRates(nodeId, network, RayChannel.BRUTAL_KEPT) ?: return
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
        // the measurement's time stays: the rate is measured again a day after it, not a day after lowering. A second
        // lowering since the measurement means the path does not hold a fixed rate: BBR for the rest of the day, not
        // a restart every ten minutes (P3)
        val lowered = known.lowered + 1
        val downKbps = if (lowered >= 2) 0 else (inUse.downKbps * LOWER).toLong().takeIf { it >= 1000 } ?: 0
        val lower = known.copy(downKbps = downKbps, lowered = lowered)
        RayChannel.recordBrutal(nodeId, network, lower)
        Logs.i("raycontrol ${node.host}: ack rate $ack under $ACK_MIN: download rate ${inUse.downKbps} → ${lower.downKbps} kbit/s")
        RayWatchdog.restart(caller)
    }

    /** What a measurement gave: rates (zeros: BBR for the day), or try again later. */
    private sealed class Measurement {
        class Done(val rates: BrutalRates) : Measurement()
        class Later(val retryAfter: Long) : Measurement()
    }

    /** A direct probe request to [node]'s subscription endpoint (libcore RaycontrolProbe), steps to be added. */
    fun request(node: RayNode) = JsonObject().apply {
        val sub = node.subscription!!
        addProperty("host", node.host)
        addProperty("port", sub.port)
        addProperty("pin", sub.pin)
        addProperty("device_id", node.deviceId)
        add("spa", node.spaJson().apply { addProperty("device_key", RayNodes.deviceKeyHex(node)) })
    }

    /**
     * Direct down, up and echo: the share of what got through, zeros (BBR for the day) when UDP does not pass well,
     * [Measurement.Later] when the measurement could not run (the node's probe slot busy, the day's volume spent, no
     * answer). The size follows the network's price: 6 MiB where it is metered (a phone's hotspot as well), 24 MiB
     * where not (P4).
     */
    private fun measure(node: RayNode, network: ChannelNetwork, now: Long): Measurement {
        val metered = network.metered
        val r = request(node).apply {
            addProperty("down_bytes", if (metered) 4L shl 20 else 16L shl 20)
            addProperty("up_bytes", if (metered) 2L shl 20 else 8L shl 20)
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
            return Measurement.Later(0)
        }
        val down = o.getAsJsonObject("down")
        val up = o.getAsJsonObject("up")
        val echo = o.getAsJsonObject("echo")
        val steps = listOfNotNull(down, up, echo)
        // a refused step (4xx) ended the measurement in the core: try again after the node's Retry-After
        if (steps.any { it.get("status")?.asInt?.let { s -> s in 400..499 } == true }) {
            return Measurement.Later(steps.maxOfOrNull { it.get("retry_after")?.asLong ?: 0 } ?: 0)
        }
        if (down?.get("ok")?.asBoolean != true) return Measurement.Later(0)
        val sent = echo?.get("sent")?.asInt ?: 0
        val lossPct = if (sent > 0) (echo?.get("lost")?.asInt ?: 0) * 100.0 / sent else 100.0
        // Brutal sends at its rate regardless of loss: where UDP is lossy or blocked it only adds to it
        if (echo?.get("ok")?.asBoolean != true || lossPct > 20) return Measurement.Done(BrutalRates(now, 0, 0))
        val share = if (network.mobile) 0.7 else 0.85
        fun rate(kbps: Long?) = ((kbps ?: 0) * share).toLong().takeIf { it >= 1000 } ?: 0
        val upKbps = up?.takeIf { it.get("ok")?.asBoolean == true }?.get("kbps")?.asLong
        return Measurement.Done(BrutalRates(now, rate(down.get("kbps")?.asLong), rate(upKbps)))
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

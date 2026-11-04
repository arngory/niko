package io.niko.raycontrol

import android.content.Context
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.Key
import io.niko.R
import io.niko.SagerNet
import io.niko.bg.proto.TestInstance
import io.niko.database.DataStore
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase
import io.niko.ktx.Logs
import io.niko.ktx.app
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.security.MessageDigest
import kotlin.math.ln
import libcore.Libcore

/**
 * A channel probe of one profile on one network: a TCP download through it (did it work, time to the first byte,
 * speed, after how many bytes it stalled) and UDP round trips through it (DNS queries: loss, RTT).
 */
data class ChannelScore(
    val at: Long,
    val tcpOk: Boolean,
    val firstByteMs: Long = 0,
    val kbps: Long = 0,
    val stalledAt: Long = -1,
    val tcpError: String? = null,
    val udpOk: Boolean = false,
    val udpLossPct: Double = 0.0,
    val udpRttMs: Double = 0.0,
    val udpError: String? = null,
    // UDP was probed at all (not: a TCP-only node service, a failover's record of a failed profile)
    val udpMeasured: Boolean = false,
) {

    /**
     * Higher is better, and any profile that works ranks above all that do not. Among working ones: speed counts
     * by its logarithm (8 Mbit/s against 4 matters as much as 2 against 1), a slow first byte costs, and working UDP
     * adds, less for losses.
     */
    fun rank(): Double {
        if (!tcpOk) return -1.0
        val speed = 10 * ln(1.0 + kbps / 1000.0)
        val wait = firstByteMs / 200.0
        val udp = if (udpOk) 5 - udpLossPct / 10 else 0.0
        return 100 + speed - wait + udp
    }

    fun toJson() = JsonObject().apply {
        addProperty("at", at)
        addProperty("tcp_ok", tcpOk)
        addProperty("first_byte_ms", firstByteMs)
        addProperty("kbps", kbps)
        addProperty("stalled_at", stalledAt)
        tcpError?.let { addProperty("tcp_error", it) }
        addProperty("udp_ok", udpOk)
        addProperty("udp_loss_pct", udpLossPct)
        addProperty("udp_rtt_ms", udpRttMs)
        udpError?.let { addProperty("udp_error", it) }
        addProperty("udp_measured", udpMeasured)
    }

    companion object {
        fun fromJson(o: JsonObject) = ChannelScore(
            at = o.get("at").asLong,
            tcpOk = o.get("tcp_ok").asBoolean,
            firstByteMs = o.get("first_byte_ms")?.asLong ?: 0,
            kbps = o.get("kbps")?.asLong ?: 0,
            stalledAt = o.get("stalled_at")?.asLong ?: -1,
            tcpError = o.get("tcp_error")?.asString,
            udpOk = o.get("udp_ok")?.asBoolean ?: false,
            udpLossPct = o.get("udp_loss_pct")?.asDouble ?: 0.0,
            udpRttMs = o.get("udp_rtt_ms")?.asDouble ?: 0.0,
            udpError = o.get("udp_error")?.asString,
            // records before 1.21.0: what they say about UDP
            udpMeasured = o.get("udp_measured")?.asBoolean ?: (o.get("udp_ok")?.asBoolean == true ||
                (o.get("udp_loss_pct")?.asDouble ?: 0.0) > 0 ||
                o.get("udp_error")?.asString.let { it != null && it != NO_ECHO }),
        )

        // a node service that answers no UDP echo
        private const val NO_ECHO = "no UDP echo at this node"

        /**
         * libcore RaycontrolProbeTunnel's result (probe-v1 through the profile's tunnel), or null when the node's one
         * probe slot was busy (429): not a result for the profile.
         */
        fun fromNodeProbe(at: Long, json: String): ChannelScore? {
            val o = JsonParser.parseString(json).asJsonObject
            val down = o.getAsJsonObject("down")
            val echo = o.getAsJsonObject("echo")
            if (down?.get("status")?.asInt == 429 || echo?.get("status")?.asInt == 429) return null
            val sent = echo?.get("sent")?.asInt ?: 0
            return ChannelScore(
                at = at,
                tcpOk = down?.get("ok")?.asBoolean ?: false,
                firstByteMs = down?.get("first_byte_ms")?.asLong ?: 0,
                kbps = down?.get("kbps")?.asLong ?: 0,
                stalledAt = down?.get("stalled_at")?.asLong ?: -1,
                tcpError = down?.get("error")?.asString,
                udpOk = echo?.get("ok")?.asBoolean ?: false,
                udpLossPct = if (sent > 0) (echo?.get("lost")?.asInt ?: 0) * 100.0 / sent else 0.0,
                udpRttMs = echo?.get("rtt_avg_ms")?.asDouble ?: 0.0,
                udpError = echo?.get("error")?.asString ?: if (echo == null) NO_ECHO else null,
                udpMeasured = echo != null,
            )
        }

        /** libcore ChannelProbe's result. */
        fun fromProbe(at: Long, json: String): ChannelScore {
            val o = JsonParser.parseString(json).asJsonObject
            val tcp = o.getAsJsonObject("tcp")
            val udp = o.getAsJsonObject("udp")
            return ChannelScore(
                at = at,
                tcpOk = tcp?.get("ok")?.asBoolean ?: false,
                firstByteMs = tcp?.get("first_byte_ms")?.asLong ?: 0,
                kbps = tcp?.get("kbps")?.asLong ?: 0,
                stalledAt = tcp?.get("stalled_at")?.asLong ?: -1,
                tcpError = tcp?.get("error")?.asString,
                udpOk = udp?.get("ok")?.asBoolean ?: false,
                udpLossPct = udp?.get("loss_pct")?.asDouble ?: 0.0,
                udpRttMs = udp?.get("rtt_avg_ms")?.asDouble ?: 0.0,
                udpError = udp?.get("error")?.asString,
                udpMeasured = udp != null,
            )
        }
    }
}

/**
 * The subscription's `hints` (probe-v1.md §6.2): what other devices' summaries say about the network the
 * subscription was asked from, with k reports or more. Only a starting order: this device's own results come first.
 */
data class RayHints(
    val at: Long,
    val order: List<Int> = emptyList(),
    val avoid: List<Int> = emptyList(),
    val udpBlocked: Boolean = false,
    val tcpStallKib: Int = 0,
    val devices: Long = 0,
    val days: Int = 0,
) {
    fun toJson() = JsonObject().apply {
        addProperty("at", at)
        add("order", JsonArray().apply { order.forEach { add(it) } })
        add("avoid", JsonArray().apply { avoid.forEach { add(it) } })
        addProperty("udp_blocked", udpBlocked)
        addProperty("tcp_stall_kib", tcpStallKib)
        addProperty("devices", devices)
        addProperty("days", days)
    }

    companion object {
        /** The payload's `hints` as the node sends it, or null when there are none. */
        fun fromJson(o: JsonObject, at: Long) = RayHints(
            at = o.get("at")?.asLong ?: at,
            order = (o.get("order") as? JsonArray)?.mapNotNull { runCatching { it.asInt }.getOrNull() }.orEmpty(),
            avoid = (o.get("avoid") as? JsonArray)?.mapNotNull { runCatching { it.asInt }.getOrNull() }.orEmpty(),
            udpBlocked = o.get("udp_blocked")?.asBoolean ?: false,
            tcpStallKib = o.get("tcp_stall_kib")?.asInt ?: 0,
            devices = o.get("devices")?.asLong ?: 0,
            days = o.get("days")?.asInt ?: 0,
        )
    }

    /**
     * [keys] (profile keys, svc first) in the hint's order: its `order` first, then the services it says nothing
     * about (where UDP is blocked, those over [overUdp] after the rest), the ones to avoid last.
     */
    fun sort(keys: List<String>, overUdp: (String) -> Boolean): List<String> = keys.sortedWith(compareBy({ key ->
        val svc = key.substringBefore('#').toIntOrNull()
        when {
            svc in avoid -> 3
            svc in order -> 0
            udpBlocked && overUdp(key) -> 2
            else -> 1
        }
    }, { key -> order.indexOf(key.substringBefore('#').toIntOrNull()) }))
}

/**
 * The network the device is on, as the probes know it: a hash, never the raw values; [kind] is cellular, wifi,
 * ethernet or other.
 */
data class ChannelNetwork(val key: String, val mobile: Boolean, val kind: String = if (mobile) "cellular" else "other")

/**
 * Measures the channel through the profiles of a raycontrol node and remembers the results per network (only on
 * the device), to switch to a working profile of the node when the running one stops working there.
 */
object RayChannel {

    // a download that passes the ~16-20 KB after which some filters freeze a connection; Cloudflare serves any size
    private const val TCP_URL = "https://speed.cloudflare.com/__down?bytes="
    private const val UDP_SERVER = "1.1.1.1:53"
    private const val MAX_NETWORKS = 16

    // a profile that failed on a network is not tried there again for this long: no back and forth
    private const val FAILED_HOLD = 10 * 60L

    // results older than this do not order the candidates any more
    private const val FRESH_WIFI = 7 * 24 * 3600L
    private const val FRESH_MOBILE = 24 * 3600L

    /** Probe options; [deep] is a check the user asked for: a longer window and more data, less on mobile. */
    fun options(deep: Boolean, mobile: Boolean): String = JsonObject().apply {
        val bytes = when {
            !deep -> 256 shl 10
            mobile -> 2 shl 20
            else -> 8 shl 20
        }
        addProperty("tcp_url", TCP_URL + bytes)
        addProperty("tcp_bytes", bytes)
        addProperty("udp_server", UDP_SERVER)
        addProperty("udp_count", if (deep) 30 else 10)
        addProperty("udp_interval_ms", 200)
        addProperty("window_ms", if (deep) 10_000 else 5_000)
    }.toString()

    /**
     * The probe's score, or null when it cannot run here: a Yggdrasil profile the running connection uses, or the
     * node's probe slot busy. A node that answers probes (probe-v1) is measured through the profile's tunnel at its
     * probe name: the tunnel as a whole, its exit included; other profiles through a download from Cloudflare.
     */
    suspend fun probe(profile: ProxyEntity, deep: Boolean, mobile: Boolean): ChannelScore? {
        val at = System.currentTimeMillis() / 1000
        val nodeRequest = RayNodes.ownerOf(profile.id)?.let { (node, key) ->
            node.probe?.takeIf { it.reachesThrough(key.substringBefore('#').toIntOrNull()) }?.let { probeRequest(node, it, deep, mobile) }
        }
        return try {
            if (nodeRequest != null) {
                ChannelScore.fromNodeProbe(at, TestInstance(profile, "", 0).doProbeNode(nodeRequest.toString()))
            } else {
                ChannelScore.fromProbe(at, TestInstance(profile, "", 0).doProbe(options(deep, mobile)))
            }
        } catch (e: CancellationException) {
            // cancelled, not failed: nothing to record
            throw e
        } catch (e: io.niko.fmt.YggInUseException) {
            // a second link with the device's key would disturb the running one: not a result
            null
        } catch (e: Exception) {
            Logs.w("raycontrol channel probe ${profile.displayName()}: ${e.message}")
            ChannelScore(at, tcpOk = false, tcpError = e.message ?: e.javaClass.simpleName)
        }
    }

    /** A probe-v1 request through a profile's tunnel: the subscription's endpoint and the device, then the steps. */
    private fun probeRequest(node: RayNode, info: RayProbeInfo, deep: Boolean, mobile: Boolean): JsonObject? {
        val sub = node.subscription ?: return null
        return JsonObject().apply {
            addProperty("host", node.host)
            addProperty("port", sub.port)
            addProperty("pin", sub.pin)
            addProperty("device_id", node.deviceId)
            add("spa", node.spa.toJson().apply { addProperty("device_key", RayNodes.deviceKeyHex(node)) })
            addProperty("tunnel", info.tunnel)
            addProperty("down_bytes", downBytes(deep, mobile))
            if (info.udp) {
                addProperty("echo_count", if (deep) 50 else 20)
                addProperty("echo_size", 1200)
            }
            addProperty("window_ms", if (deep) 10_000 else 5_000)
        }
    }

    private fun downBytes(deep: Boolean, mobile: Boolean): Long = when {
        !deep -> 256L shl 10
        mobile -> 2L shl 20
        else -> 8L shl 20
    }

    /** The network under the VPN: transport, operator or the LAN's gateway, DNS servers and domain, hashed. */
    fun network(): ChannelNetwork? {
        val net = SagerNet.underlyingNetwork ?: return null
        val caps = SagerNet.connectivity.getNetworkCapabilities(net) ?: return null
        val mobile = caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        val wifi = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
        val kind = when {
            mobile -> "cellular"
            wifi -> "wifi"
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            else -> "other"
        }
        val parts = mutableListOf<String>()
        when {
            mobile -> {
                parts += "cellular"
                // MCC and MNC: no permission needed
                parts += app.getSystemService<TelephonyManager>()?.networkOperator.orEmpty()
            }

            else -> {
                // the key as before the network kind was reported: what was measured on a network stays its own
                parts += if (wifi) "wifi" else "other"
                SagerNet.connectivity.getLinkProperties(net)?.let { lp ->
                    parts += lp.routes.filter { it.isDefaultRoute }.mapNotNull { it.gateway?.hostAddress }.sorted()
                    parts += lp.dnsServers.mapNotNull { it.hostAddress }.sorted()
                    parts += lp.domains.orEmpty()
                }
            }
        }
        val digest = MessageDigest.getInstance("SHA-256").digest(parts.joinToString("|").toByteArray())
        return ChannelNetwork(digest.take(8).joinToString("") { "%02x".format(it) }, mobile, kind)
    }

    private fun load(): JsonObject = try {
        DataStore.configurationStore.getString(Key.RAYCONTROL_CHANNEL, null)
            ?.let { JsonParser.parseString(it).asJsonObject } ?: JsonObject()
    } catch (e: Exception) {
        Logs.w("raycontrol: broken channel results: ${e.message}")
        JsonObject()
    }

    // the direct path to the node on a network (directPath): which SPA carrier opens its port, the UDP MTU
    private const val DIRECT = "_direct"

    // echo sizes for the path's MTU: Ethernet's and PPPoE's payloads down to QUIC's minimum, and one well below it
    private val MTU_SIZES = listOf(1472, 1452, 1400, 1350, 1280, 1200, 576)

    /** The direct path to [node] from this network, for the channel check: null when the node takes no probes. */
    data class DirectPath(val spaCarrier: String?, val udpMtu: Int, val udpProbed: Boolean)

    /**
     * Measures the direct path to [node] on this network (probe-v1 directly, after SPA over UDP and then TCP): which
     * carrier opened the port, and the largest UDP datagram that comes back (the node drops fragments, QUIC needs
     * 1200 bytes). Kept with the network's results for the debug export.
     */
    suspend fun directPath(node: RayNode, network: ChannelNetwork): DirectPath? {
        if (node.probe == null || node.subscription == null) return null
        val udp = node.probe.udp
        val request = RayBrutal.request(node).apply {
            addProperty("down_bytes", 64 * 1024)
            if (udp) add("mtu_sizes", JsonArray().apply { MTU_SIZES.forEach { add(it) } })
            addProperty("window_ms", 10_000)
        }
        val o = try {
            JsonParser.parseString(
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { Libcore.raycontrolProbe(request.toString()) }
            ).asJsonObject
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Logs.w("raycontrol ${node.host}: direct path: ${e.message}")
            return DirectPath(null, 0, udp)
        }
        val path = DirectPath(o.get("spa_carrier")?.asString?.takeIf { it.isNotEmpty() }, o.get("udp_mtu")?.asInt ?: 0, udp)
        RayNodesLock.withLock {
            val all = load()
            val nodeObject = all.getAsJsonObject(node.spa.nodeId) ?: JsonObject().also { all.add(node.spa.nodeId, it) }
            val onNetwork = nodeObject.getAsJsonObject(network.key) ?: JsonObject().also { nodeObject.add(network.key, it) }
            onNetwork.add(DIRECT, JsonObject().apply {
                addProperty("at", System.currentTimeMillis() / 1000)
                path.spaCarrier?.let { addProperty("spa", it) }
                if (udp) addProperty("udp_mtu", path.udpMtu)
                (o.get("mtu") as? JsonArray)?.let { add("mtu", it) }
            })
            DataStore.configurationStore.putString(Key.RAYCONTROL_CHANNEL, all.toString())
        }
        return path
    }

    /** Everything stored, by node and network (a hash), for the debug export (RayDiagnostics). */
    fun exportState(): JsonObject = load()

    /** The stored results of [nodeId] on [network], by profile key. */
    fun scores(nodeId: String, network: ChannelNetwork): Map<String, ChannelScore> {
        val byProfile = load().getAsJsonObject(nodeId)?.getAsJsonObject(network.key) ?: return emptyMap()
        return byProfile.entrySet().filter { !it.key.startsWith("_") }.mapNotNull { (key, value) ->
            runCatching { key to ChannelScore.fromJson(value.asJsonObject) }.getOrNull()
        }.toMap()
    }

    fun record(nodeId: String, network: ChannelNetwork, profileKey: String, score: ChannelScore) = RayNodesLock.withLock {
        val all = load()
        val node = all.getAsJsonObject(nodeId) ?: JsonObject().also { all.add(nodeId, it) }
        val onNetwork = node.getAsJsonObject(network.key) ?: JsonObject().also { node.add(network.key, it) }
        onNetwork.add(profileKey, score.toJson())
        // the networks of the node, the most recent kept
        if (node.size() > MAX_NETWORKS) {
            val latest = { o: JsonObject -> o.entrySet().maxOfOrNull { it.value.asJsonObject.get("at").asLong } ?: 0 }
            node.entrySet().sortedBy { latest(it.value.asJsonObject) }.take(node.size() - MAX_NETWORKS)
                .map { it.key }.forEach { node.remove(it) }
        }
        DataStore.configurationStore.putString(Key.RAYCONTROL_CHANNEL, all.toString())
    }

    /** One line for the user: [name]'s TCP and UDP results. */
    fun describe(context: Context, name: String, score: ChannelScore): String = buildString {
        append(
            when {
                score.tcpOk -> context.getString(
                    R.string.raycontrol_channel_tcp, name, "%.1f".format(score.kbps / 1000.0), score.firstByteMs.toInt()
                )

                score.stalledAt >= 0 -> context.getString(R.string.raycontrol_channel_stalled, name, (score.stalledAt / 1024).toInt())
                else -> context.getString(R.string.raycontrol_channel_tcp_failed, name, score.tcpError ?: "?")
            }
        )
        append("; ")
        append(
            if (score.udpOk) context.getString(R.string.raycontrol_channel_udp, "%.0f".format(score.udpLossPct), "%.0f".format(score.udpRttMs))
            else context.getString(R.string.raycontrol_channel_udp_failed)
        )
    }

    // the network's Brutal rates sit next to its profile results, under a key no profile has
    private const val BRUTAL = "_brutal"

    /** The Brutal rates measured for [nodeId] on [network], for a day after the measurement on any network. */
    fun brutalRates(nodeId: String, network: ChannelNetwork): BrutalRates? {
        val o = load().getAsJsonObject(nodeId)?.getAsJsonObject(network.key)?.getAsJsonObject(BRUTAL) ?: return null
        val rates = runCatching { BrutalRates.fromJson(o) }.getOrNull() ?: return null
        return rates.takeIf { System.currentTimeMillis() / 1000 - it.at < BRUTAL_FRESH }
    }

    private const val BRUTAL_FRESH = 24 * 3600L

    fun recordBrutal(nodeId: String, network: ChannelNetwork, rates: BrutalRates) = RayNodesLock.withLock {
        val all = load()
        val node = all.getAsJsonObject(nodeId) ?: JsonObject().also { all.add(nodeId, it) }
        val onNetwork = node.getAsJsonObject(network.key) ?: JsonObject().also { node.add(network.key, it) }
        onNetwork.add(BRUTAL, rates.toJson())
        DataStore.configurationStore.putString(Key.RAYCONTROL_CHANNEL, all.toString())
    }

    // node ID → the running core has the node's public peers (ConfigBuilder notes it)
    private val yggViaRunning = java.util.concurrent.ConcurrentHashMap<String, Boolean>()

    fun noteYggVia(nodeId: String, on: Boolean) {
        yggViaRunning[nodeId] = on
    }

    // a network where a Yggdrasil profile of the node goes through the node's public peers (R4-18), for a day
    private const val YGG_VIA = "_ygg_via"
    private const val YGG_VIA_HOLD = 24 * 3600L

    /** The node's Yggdrasil profiles take its public peers on [network]: the direct peer failed there today. */
    fun yggViaActive(nodeId: String, network: ChannelNetwork?): Boolean {
        network ?: return false
        val at = load().getAsJsonObject(nodeId)?.getAsJsonObject(network.key)?.getAsJsonObject(YGG_VIA)?.get("at")?.asLong ?: return false
        return System.currentTimeMillis() / 1000 - at < YGG_VIA_HOLD
    }

    /**
     * The public peers taken on [network] (the check's pick), or null: none checked (a record from before 1.23.0,
     * which took them all).
     */
    fun yggViaPeers(nodeId: String, network: ChannelNetwork?): List<String>? {
        network ?: return null
        val o = load().getAsJsonObject(nodeId)?.getAsJsonObject(network.key)?.getAsJsonObject(YGG_VIA) ?: return null
        return (o.get("peers") as? com.google.gson.JsonArray)?.mapNotNull { runCatching { it.asString }.getOrNull() }
    }

    /**
     * The running Yggdrasil profile [key] of [node] does not work: with public peers of the node and none in use yet
     * on this network, they are checked (RayYggPeers) and the fastest that answer are taken for a day (the caller
     * restarts the core): true. None answering: false, the public peers would not help here either. Back to the
     * direct peer alone at a start after that day.
     */
    suspend fun tryYggVia(node: RayNode, key: String): Boolean {
        val id = node.profileIds?.get(key) ?: return false
        val bean = SagerDatabase.proxyDao.getById(id)?.requireBean() ?: return false
        if (bean.yggPeer.isNullOrBlank() || bean.yggVia.isNullOrBlank()) return false
        val network = network() ?: return false
        val via = bean.yggVia.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (yggViaActive(node.spa.nodeId, network)) {
            val picked = yggViaPeers(node.spa.nodeId, network)
            // the network wants them since earlier today: a core built elsewhere runs without them. Unless the
            // subscription dropped every peer the check took: then the new list is checked
            if (picked == null || picked.any { it in via }) return yggViaRunning[node.spa.nodeId] != true
            Logs.i("raycontrol ${node.host}: the public Yggdrasil peers taken here are gone from the subscription, checking the new ones")
        }
        val checks = RayYggPeers.check(via)
        val picked = RayYggPeers.pick(checks)
        Logs.i("raycontrol ${node.host}: public Yggdrasil peers here: " + checks.joinToString("; ") {
            RayYggPeers.label(it.uri) + if (it.up) " ${it.latencyMs} ms" else " no link" + (it.error?.let { e -> " ($e)" } ?: "")
        })
        if (picked.isEmpty()) return false
        RayNodesLock.withLock {
            val all = load()
            val nodeObject = all.getAsJsonObject(node.spa.nodeId) ?: JsonObject().also { all.add(node.spa.nodeId, it) }
            val onNetwork = nodeObject.getAsJsonObject(network.key) ?: JsonObject().also { nodeObject.add(network.key, it) }
            onNetwork.add(YGG_VIA, JsonObject().apply {
                addProperty("at", System.currentTimeMillis() / 1000)
                add("peers", JsonArray().apply { picked.forEach { add(it) } })
            })
            DataStore.configurationStore.putString(Key.RAYCONTROL_CHANNEL, all.toString())
        }
        return true
    }

    // the subscription's hints for a network, next to the network's results
    private const val HINTS = "_hints"

    // the node counts them over a week; a subscription asked on the network since replaces them
    private const val HINTS_FRESH = 7 * 24 * 3600L

    /** The node's hints for [network], from a subscription asked there in the last week. */
    fun hints(nodeId: String, network: ChannelNetwork): RayHints? {
        val o = load().getAsJsonObject(nodeId)?.getAsJsonObject(network.key)?.getAsJsonObject(HINTS) ?: return null
        val hints = runCatching { RayHints.fromJson(o, 0) }.getOrNull() ?: return null
        return hints.takeIf { System.currentTimeMillis() / 1000 - it.at < HINTS_FRESH }
    }

    /** What the subscription asked on [network] said: its hints, or none (fewer than k reports, or none at all). */
    fun recordHints(nodeId: String, network: ChannelNetwork, hints: RayHints?) = RayNodesLock.withLock {
        val all = load()
        val node = all.getAsJsonObject(nodeId)
        if (hints == null) {
            if (node?.getAsJsonObject(network.key)?.remove(HINTS) != null) {
                DataStore.configurationStore.putString(Key.RAYCONTROL_CHANNEL, all.toString())
            }
            return@withLock
        }
        val onNode = node ?: JsonObject().also { all.add(nodeId, it) }
        val onNetwork = onNode.getAsJsonObject(network.key) ?: JsonObject().also { onNode.add(network.key, it) }
        onNetwork.add(HINTS, hints.toJson())
        DataStore.configurationStore.putString(Key.RAYCONTROL_CHANNEL, all.toString())
    }

    // the network's summary state (RayReport): the UTC day it was sent on, and the node's answer
    const val REPORT = "_report"

    fun reportState(nodeId: String, network: ChannelNetwork): JsonObject? =
        load().getAsJsonObject(nodeId)?.getAsJsonObject(network.key)?.getAsJsonObject(REPORT)

    fun recordReportState(nodeId: String, network: ChannelNetwork, state: JsonObject) = RayNodesLock.withLock {
        val all = load()
        val node = all.getAsJsonObject(nodeId) ?: JsonObject().also { all.add(nodeId, it) }
        val onNetwork = node.getAsJsonObject(network.key) ?: JsonObject().also { node.add(network.key, it) }
        onNetwork.add(REPORT, state)
        DataStore.configurationStore.putString(Key.RAYCONTROL_CHANNEL, all.toString())
    }

    /**
     * Whether [profile] goes over UDP (Hysteria, TUIC, WireGuard, NaiveProxy over QUIC, V2Ray's QUIC transport):
     * what blocked UDP stops.
     */
    fun overUdp(profile: ProxyEntity?): Boolean = when (val bean = profile?.requireBean()) {
        is io.niko.fmt.hysteria.HysteriaBean, is io.niko.fmt.tuic.TuicBean, is io.niko.fmt.wireguard.WireGuardBean -> true
        is io.niko.fmt.naive.NaiveBean -> bean.proto == "quic"
        is io.niko.fmt.v2ray.StandardV2RayBean -> bean.type == "quic"
        else -> false
    }

    /** Forgets the results of a node (it was removed). */
    fun forget(nodeId: String) = RayNodesLock.withLock {
        val all = load()
        if (all.remove(nodeId) != null) DataStore.configurationStore.putString(Key.RAYCONTROL_CHANNEL, all.toString())
    }

    /**
     * The running profile [failedKey] of [node] stopped working: probes the node's other profiles on this network
     * (the best known first) and returns the key of the best one that works, or null. Profiles that failed here in
     * the last [FAILED_HOLD] are not tried. On a network without this device's own results the node's hints set the
     * order (probe-v1.md §6.2), and the first profile that works there is taken.
     */
    suspend fun failover(node: RayNode, failedKey: String): String? {
        val network = network() ?: return null
        val nodeId = node.spa.nodeId
        val now = System.currentTimeMillis() / 1000
        record(nodeId, network, failedKey, ChannelScore(now, tcpOk = false, tcpError = "connection test failed"))
        val known = scores(nodeId, network)
        val fresh = if (network.mobile) FRESH_MOBILE else FRESH_WIFI
        val candidates = node.profileIds.orEmpty().keys
            .filter { it != failedKey }
            .filter { key -> known[key]?.let { it.tcpOk || now - it.at > FAILED_HOLD } ?: true }
            .sortedByDescending { key -> known[key]?.takeIf { now - it.at < fresh }?.rank() ?: 0.0 }
        // own results, fresh: they decide; otherwise the hints, if the node has them for this network
        val hints = hints(nodeId, network)?.takeIf { _ -> candidates.none { key -> known[key]?.let { now - it.at < fresh } == true } }
        val order = hints?.let { h ->
            val udp = candidates.associateWith { key -> overUdp(node.profileIds?.get(key)?.let { SagerDatabase.proxyDao.getById(it) }) }
            h.sort(candidates) { udp[it] == true }
        } ?: candidates
        if (hints != null) Logs.i("raycontrol ${node.host}: no own results on this network, the node's hints first: $order")
        var best: Pair<String, ChannelScore>? = null
        for (key in order) {
            // the watchdog run that asked was stopped (the core restarted, the service stopped): no more probes
            currentCoroutineContext().ensureActive()
            val id = node.profileIds?.get(key) ?: continue
            val profile = SagerDatabase.proxyDao.getById(id) ?: continue
            val score = probe(profile, deep = false, mobile = network.mobile) ?: continue
            record(nodeId, network, key, score)
            Logs.i("raycontrol ${node.host}: $key on this network: ${if (score.tcpOk) "works, ${score.kbps} kbit/s" else score.tcpError}")
            if (score.tcpOk && (best == null || score.rank() > best.second.rank())) best = key to score
            // the hints' order: the first that works, without waiting for the rest
            if (hints != null && best != null) break
        }
        return best?.first
    }
}

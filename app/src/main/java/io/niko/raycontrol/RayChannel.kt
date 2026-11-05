package io.niko.raycontrol

import android.content.Context
import android.net.NetworkCapabilities
import android.os.Build
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
    // the UDP MTU bucket of most reports from the network (raycontrol v0.8); "<1200": no QUIC there
    val udpMtu: String? = null,
    val devices: Long = 0,
    val days: Int = 0,
    // most reports from the network went through a relay (relay-v1 §9.3): a place to start, not a verdict
    val viaRelay: Boolean = false,
) {
    fun toJson() = JsonObject().apply {
        addProperty("at", at)
        add("order", JsonArray().apply { order.forEach { add(it) } })
        add("avoid", JsonArray().apply { avoid.forEach { add(it) } })
        addProperty("udp_blocked", udpBlocked)
        addProperty("tcp_stall_kib", tcpStallKib)
        udpMtu?.let { addProperty("udp_mtu", it) }
        addProperty("devices", devices)
        addProperty("days", days)
        if (viaRelay) addProperty("via_relay", true)
    }

    companion object {
        /** The payload's `hints` as the node sends it, or null when there are none. */
        fun fromJson(o: JsonObject, at: Long) = RayHints(
            at = o.get("at")?.asLong ?: at,
            order = (o.get("order") as? JsonArray)?.mapNotNull { runCatching { it.asInt }.getOrNull() }.orEmpty(),
            avoid = (o.get("avoid") as? JsonArray)?.mapNotNull { runCatching { it.asInt }.getOrNull() }.orEmpty(),
            udpBlocked = o.get("udp_blocked")?.asBoolean ?: false,
            tcpStallKib = o.get("tcp_stall_kib")?.asInt ?: 0,
            udpMtu = o.get("udp_mtu")?.takeIf { it.isJsonPrimitive }?.asString,
            devices = o.get("devices")?.asLong ?: 0,
            days = o.get("days")?.asInt ?: 0,
            viaRelay = o.get("via_relay")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: false,
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
            // UDP blocked, or a path MTU under QUIC's 1200 bytes
            (udpBlocked || udpMtu == "<1200") && overUdp(key) -> 2
            else -> 1
        }
    }, { key -> order.indexOf(key.substringBefore('#').toIntOrNull()) }))
}

/**
 * The network the device is on, as the probes know it: a hash, never the raw values; [kind] is cellular, wifi,
 * ethernet or other.
 */
data class ChannelNetwork(
    val key: String,
    val mobile: Boolean,
    val kind: String = if (mobile) "cellular" else "other",
    // paid by the byte (a phone's hotspot is Wi-Fi and metered), and roaming: the measurements' size follows these
    val metered: Boolean = mobile,
    val roaming: Boolean = false,
)

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
            add("spa", node.spaJson().apply { addProperty("device_key", RayNodes.deviceKeyHex(node)) })
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
        val metered = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
        val roaming = Build.VERSION.SDK_INT >= 28 && !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING)
        return ChannelNetwork(digest.take(8).joinToString("") { "%02x".format(it) }, mobile, kind, metered, roaming)
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

    /**
     * The UDP MTU measured directly to [nodeId] on [network] since [since] (unix s), or null: not measured then, or
     * no echo came back at all.
     */
    fun directUdpMtu(nodeId: String, network: ChannelNetwork, since: Long): Int? {
        val o = load().getAsJsonObject(nodeId)?.getAsJsonObject(network.key)?.getAsJsonObject(DIRECT) ?: return null
        if ((o.get("at")?.asLong ?: 0) < since) return null
        return o.get("udp_mtu")?.asInt?.takeIf { it > 0 }
    }

    // echo sizes for the path's MTU: Ethernet's and PPPoE's payloads down to QUIC's minimum, and one well below it
    private val MTU_SIZES = listOf(1472, 1452, 1400, 1350, 1280, 1200, 576)

    /**
     * The direct path to a node from this network, for the channel check. [udpMeasured] false: the echo could not
     * run ([udpStatus] 429: another probe of the device held the node's one slot), not a property of the network;
     * [error]: the measurement itself failed (no SPA sent, ...), the node's address left out.
     */
    data class DirectPath(
        val spaCarrier: String?, val udpMtu: Int, val udpProbed: Boolean,
        val udpMeasured: Boolean = false, val udpStatus: Int = 0, val udpRetryAfter: Int = 0, val error: String? = null,
    )

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
            return DirectPath(null, 0, udp, error = RayDiagnostics.redact(e.message ?: e.javaClass.simpleName, node.host))
        }
        val steps = (o.get("mtu") as? JsonArray)?.map { it.asJsonObject }.orEmpty()
        // PathMTU stops at the first size lost or not sent: a last step that was not sent (the session did not open:
        // 429, a failed request) leaves the sizes before it a lower bound only, not the network's MTU
        val last = steps.lastOrNull()
        val measured = last?.get("sent")?.asInt?.let { it > 0 } == true
        val path = DirectPath(
            spaCarrier = o.get("spa_carrier")?.asString?.takeIf { it.isNotEmpty() },
            udpMtu = if (measured) o.get("udp_mtu")?.asInt ?: 0 else 0,
            udpProbed = udp,
            udpMeasured = measured,
            udpStatus = last?.get("status")?.asInt ?: 0,
            udpRetryAfter = last?.get("retry_after")?.asInt ?: 0,
        )
        // what was measured: no answer at all is a result of the network too (it replaces an older one); a busy node
        // or a UDP session that did not open is not, and leaves the record as it was
        if (path.spaCarrier == null || !udp || measured) edit(node.spa.nodeId, network) { onNetwork ->
            onNetwork.add(DIRECT, JsonObject().apply {
                addProperty("at", System.currentTimeMillis() / 1000)
                addProperty("spa", path.spaCarrier ?: "none")
                if (path.spaCarrier != null && measured) {
                    addProperty("udp_mtu", path.udpMtu)
                    // sizes and counts only
                    add("mtu", JsonArray().apply {
                        steps.forEach { s ->
                            add(JsonObject().apply {
                                for (k in listOf("size", "sent", "received")) s.get(k)?.let { add(k, it) }
                            })
                        }
                    })
                }
            })
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

    fun record(nodeId: String, network: ChannelNetwork, profileKey: String, score: ChannelScore) =
        edit(nodeId, network) { it.add(profileKey, score.toJson()) }

    /**
     * Changes what is kept for [nodeId] on [network] (results, `_brutal`, `_direct`, `_ygg_via`, `_hints`, `_report`)
     * under the lock, and keeps the node's [MAX_NETWORKS] most recent networks, whatever part wrote last. Must not
     * be called under [RayNodesLock] already.
     */
    private fun edit(nodeId: String, network: ChannelNetwork, change: (JsonObject) -> Unit) = RayNodesLock.withLock {
        val all = load()
        val node = all.getAsJsonObject(nodeId) ?: JsonObject().also { all.add(nodeId, it) }
        val onNetwork = node.getAsJsonObject(network.key) ?: JsonObject().also { node.add(network.key, it) }
        change(onNetwork)
        if (onNetwork.size() == 0) node.remove(network.key)
        if (node.size() > MAX_NETWORKS) {
            node.entrySet().sortedBy { (it.value as? JsonObject)?.let(::latest) ?: 0 }.take(node.size() - MAX_NETWORKS)
                .map { it.key }.forEach { node.remove(it) }
        }
        if (node.size() == 0) all.remove(nodeId)
        DataStore.configurationStore.putString(Key.RAYCONTROL_CHANNEL, all.toString())
    }

    /** the newest time (unix s) of anything kept for a network; 0 when nothing has one */
    private fun latest(onNetwork: JsonObject): Long = onNetwork.entrySet().maxOfOrNull { entry ->
        (entry.value as? JsonObject)?.get("at")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() } ?: 0
    } ?: 0

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
    /**
     * The Brutal rates measured for [nodeId] on [network] within [maxAge] (s): a day is fresh, a measurement up to
     * [BRUTAL_KEPT] old stays in use until a new one replaces it.
     */
    fun brutalRates(nodeId: String, network: ChannelNetwork, maxAge: Long = BRUTAL_FRESH): BrutalRates? {
        val o = load().getAsJsonObject(nodeId)?.getAsJsonObject(network.key)?.getAsJsonObject(BRUTAL) ?: return null
        val rates = runCatching { BrutalRates.fromJson(o) }.getOrNull() ?: return null
        return rates.takeIf { System.currentTimeMillis() / 1000 - it.at < maxAge }
    }

    private const val BRUTAL_FRESH = 24 * 3600L
    const val BRUTAL_KEPT = 7 * 24 * 3600L

    fun recordBrutal(nodeId: String, network: ChannelNetwork, rates: BrutalRates) =
        edit(nodeId, network) { it.add(BRUTAL, rates.toJson()) }

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
        edit(node.spa.nodeId, network) { onNetwork ->
            onNetwork.add(YGG_VIA, JsonObject().apply {
                addProperty("at", System.currentTimeMillis() / 1000)
                add("peers", JsonArray().apply { picked.forEach { add(it) } })
            })
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
    fun recordHints(nodeId: String, network: ChannelNetwork, hints: RayHints?) {
        // nothing to drop: no write at all
        if (hints == null && load().getAsJsonObject(nodeId)?.getAsJsonObject(network.key)?.has(HINTS) != true) return
        edit(nodeId, network) { if (hints == null) it.remove(HINTS) else it.add(HINTS, hints.toJson()) }
    }

    // the network's summary state (RayReport): the UTC day it was sent on, and the node's answer
    const val REPORT = "_report"

    fun reportState(nodeId: String, network: ChannelNetwork): JsonObject? =
        load().getAsJsonObject(nodeId)?.getAsJsonObject(network.key)?.getAsJsonObject(REPORT)

    fun recordReportState(nodeId: String, network: ChannelNetwork, state: JsonObject) =
        edit(nodeId, network) { it.add(REPORT, state) }

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
        // relay-v1 §9: where the node is reached through its relay, the relay rows go first; elsewhere they are the
        // last resort
        val viaFirst = node.relays.isNotEmpty() &&
                (relayPath(nodeId, network) || hints(nodeId, network)?.viaRelay == true || detectRelay(node, network))
        val ordered = RayRelays.order(order, viaFirst)
        if (viaFirst) Logs.i("raycontrol ${node.host}: this network reaches the node through its relay: relay rows first")
        var best: Pair<String, ChannelScore>? = null
        for (key in ordered) {
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
        // the path that works here, for the order and the summary's path next time
        best?.let { (key, _) -> if (node.relays.isNotEmpty()) markRelayPath(nodeId, network, RayRelays.relayOfKey(key) != null) }
        return best?.first
    }

    // the path to the node on a network (relay-v1 §9): "relay" since when, while it holds
    private const val RELAY_PATH = "_relay_path"
    private const val RELAY_HOLD = 24 * 3600L

    /** The node is reached through its relay on [network] (found in the last day); direct is tried again after. */
    fun relayPath(nodeId: String, network: ChannelNetwork, now: Long = System.currentTimeMillis() / 1000): Boolean {
        val at = load().getAsJsonObject(nodeId)?.getAsJsonObject(network.key)?.get(RELAY_PATH)?.asLong ?: return false
        return now - at < RELAY_HOLD
    }

    fun markRelayPath(nodeId: String, network: ChannelNetwork, relay: Boolean) {
        val had = load().getAsJsonObject(nodeId)?.getAsJsonObject(network.key)?.has(RELAY_PATH) == true
        if (!relay && !had) return
        edit(nodeId, network) {
            if (relay) it.addProperty(RELAY_PATH, System.currentTimeMillis() / 1000) else it.remove(RELAY_PATH)
        }
    }

    /**
     * The whitelist detector (relay-v1 §5): the node does not answer directly and one of its relays does, at its own
     * probe endpoint. A small probe each; only for relays that measure (relays[].probe).
     */
    private suspend fun detectRelay(node: RayNode, network: ChannelNetwork): Boolean {
        val relays = node.relays.filter { it.probe != null }
        if (relays.isEmpty() || node.subscription == null) return false
        val key = RayNodes.deviceKeyHex(node)
        fun answered(request: JsonObject): Boolean = runCatching {
            val out = JsonParser.parseString(Libcore.raycontrolProbe(request.apply {
                addProperty("down_bytes", 16L shl 10)
                addProperty("window_ms", 5_000)
            }.toString())).asJsonObject
            out.getAsJsonObject("down")?.let { it.get("ok")?.asBoolean == true || (it.get("status")?.asInt ?: 0) != 0 } == true
        }.getOrDefault(false)
        if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { answered(RayBrutal.request(node)) }) return false
        for (relay in relays) {
            currentCoroutineContext().ensureActive()
            val request = RayRelays.probeRequest(node, relay, key) ?: continue
            if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { answered(request) }) {
                Logs.i("raycontrol ${node.host}: no answer directly, relay ${relay.id} answers: the relay path on this network")
                markRelayPath(node.spa.nodeId, network, true)
                return true
            }
        }
        return false
    }
}

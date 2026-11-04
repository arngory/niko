package io.niko.raycontrol

import android.content.Context
import android.net.NetworkCapabilities
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
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
        )

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
                udpError = echo?.get("error")?.asString ?: if (echo == null) "no UDP echo at this node" else null,
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
            )
        }
    }
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
     * The running Yggdrasil profile [key] of [node] does not work: with public peers of the node and none in use yet
     * on this network, they are taken for a day (the caller restarts the core) and true is returned. Back to the
     * direct peer alone at a start after that day.
     */
    fun tryYggVia(node: RayNode, key: String): Boolean {
        val id = node.profileIds?.get(key) ?: return false
        val bean = SagerDatabase.proxyDao.getById(id)?.requireBean() ?: return false
        if (bean.yggPeer.isNullOrBlank() || bean.yggVia.isNullOrBlank()) return false
        val network = network() ?: return false
        if (yggViaActive(node.spa.nodeId, network)) {
            // the network wants them since earlier today: a core built elsewhere runs without them
            return yggViaRunning[node.spa.nodeId] != true
        }
        RayNodesLock.withLock {
            val all = load()
            val nodeObject = all.getAsJsonObject(node.spa.nodeId) ?: JsonObject().also { all.add(node.spa.nodeId, it) }
            val onNetwork = nodeObject.getAsJsonObject(network.key) ?: JsonObject().also { nodeObject.add(network.key, it) }
            onNetwork.add(YGG_VIA, JsonObject().apply { addProperty("at", System.currentTimeMillis() / 1000) })
            DataStore.configurationStore.putString(Key.RAYCONTROL_CHANNEL, all.toString())
        }
        return true
    }

    /** Forgets the results of a node (it was removed). */
    fun forget(nodeId: String) = RayNodesLock.withLock {
        val all = load()
        if (all.remove(nodeId) != null) DataStore.configurationStore.putString(Key.RAYCONTROL_CHANNEL, all.toString())
    }

    /**
     * The running profile [failedKey] of [node] stopped working: probes the node's other profiles on this network
     * (the best known first) and returns the key of the best one that works, or null. Profiles that failed here in
     * the last [FAILED_HOLD] are not tried.
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
        var best: Pair<String, ChannelScore>? = null
        for (key in candidates) {
            // the watchdog run that asked was stopped (the core restarted, the service stopped): no more probes
            currentCoroutineContext().ensureActive()
            val id = node.profileIds?.get(key) ?: continue
            val profile = SagerDatabase.proxyDao.getById(id) ?: continue
            val score = probe(profile, deep = false, mobile = network.mobile) ?: continue
            record(nodeId, network, key, score)
            Logs.i("raycontrol ${node.host}: $key on this network: ${if (score.tcpOk) "works, ${score.kbps} kbit/s" else score.tcpError}")
            if (score.tcpOk && (best == null || score.rank() > best.second.rank())) best = key to score
        }
        return best?.first
    }
}

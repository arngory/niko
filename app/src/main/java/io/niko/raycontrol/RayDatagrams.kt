package io.niko.raycontrol

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.Key
import io.niko.database.DataStore
import io.niko.ktx.Logs
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import libcore.BoxInstance
import libcore.Libcore

/**
 * UDP of naive as HTTP/3 datagrams, per network (raycontrol naive-udp-v1 §9.4). The core sends the UDP of a naive
 * outbound with udp_datagram over UDP over TCP until the app allows datagrams on the network: on a new network UDP
 * goes over TCP at once, and a probe-v1 echo through a datagram request decides in the background whether new
 * sockets go as datagrams there. A network is the app's own reckoning ([RayChannel.network]: one Wi-Fi is told from
 * the next on the same interface), told to the core before anything is decided on it. The result is remembered per
 * network and node service, only on the device; the node's hints that UDP does not pass on the network, or passes
 * under QUIC's 1200 bytes, skip the probe.
 */
object RayDatagrams {

    // 30 echoes of 1100 bytes: past the 15-20 KB after which some filters freeze a flow (§10.4), each in one QUIC
    // datagram (the probe name's address in front stays under the 1194 bytes one carries over IPv6)
    internal const val ECHO_COUNT = 30
    internal const val ECHO_SIZE = 1100

    // passed: this many came back, over 24 KB with a quarter lost at most
    internal const val ECHO_PASS = 22
    private const val WINDOW_MS = 5_000

    // a pass holds as long as a channel result does; a failure a few hours, then the network is probed again
    internal const val PASS_WIFI = 7 * 24 * 3600L
    internal const val PASS_MOBILE = 24 * 3600L
    internal const val FAILED_HOLD = 6 * 3600L
    internal const val MAX_NETWORKS = 16

    // a new network's first packets go over TCP; the probe once it has settled (SPA, the subscription)
    private const val SETTLE_MS = 5_000L

    // the node's one probe slot busy, or the probe's session not started: tried again a few times; a failure counts
    // once it repeats (a Wi-Fi that drops and comes back as the same network loses one probe's echoes)
    private const val TRIES = 3
    private const val FAILS = 2
    private const val RETRY_MS = 60_000L
    private const val RETRY_MAX_MS = 10 * 60_000L

    /** What one probe said. */
    internal sealed class Verdict {
        object Passed : Verdict()
        object Failed : Verdict()

        /** No measure of the path: the node's slot busy ([retryAfter] s, 0 none given), or no echo sent at all. */
        class Again(val retryAfter: Int) : Verdict()
    }

    /**
     * The verdict of a probe's `echo` (libcore RaycontrolProbeDatagram): passed when [ECHO_PASS] came back; failed
     * when echoes went and fewer came back; again when the node was busy (429) or none went (the probe's session
     * over TCP did not start, or the request did not open: a refusal the core keeps on its own).
     */
    internal fun verdict(echo: JsonObject?): Verdict {
        if (echo == null) return Verdict.Again(0)
        val status = echo.get("status")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        if (status == 429) return Verdict.Again(echo.get("retry_after")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0)
        val sent = echo.get("sent")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        if (sent == 0) return Verdict.Again(0)
        val received = echo.get("received")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
        return if (received >= ECHO_PASS) Verdict.Passed else Verdict.Failed
    }

    /** The result remembered for [id] on [network] in [all], null when none or no longer fresh at [now] (unix s). */
    internal fun remembered(all: JsonObject, network: ChannelNetwork, id: String, now: Long): Boolean? {
        val o = (all.get(network.key) as? JsonObject)?.get(id) as? JsonObject ?: return null
        val at = o.get("at")?.takeIf { it.isJsonPrimitive }?.asLong ?: return null
        val ok = o.get("ok")?.takeIf { it.isJsonPrimitive }?.asBoolean ?: return null
        val hold = when {
            !ok -> FAILED_HOLD
            network.mobile -> PASS_MOBILE
            else -> PASS_WIFI
        }
        return ok.takeIf { now - at in 0 until hold }
    }

    /** [all] with [ok] for [id] on [network] at [now]; the [MAX_NETWORKS] networks heard of last are kept. */
    internal fun remember(all: JsonObject, network: ChannelNetwork, id: String, ok: Boolean, now: Long): JsonObject {
        val results = all.get(network.key) as? JsonObject ?: JsonObject().also { all.add(network.key, it) }
        results.add(id, JsonObject().apply {
            addProperty("at", now)
            addProperty("ok", ok)
        })
        val last = all.entrySet().associate { (key, value) ->
            key to ((value as? JsonObject)?.entrySet()
                ?.maxOfOrNull { (it.value as? JsonObject)?.get("at")?.takeIf { at -> at.isJsonPrimitive }?.asLong ?: 0L } ?: 0L)
        }
        last.entries.sortedByDescending { it.value }.drop(MAX_NETWORKS).forEach { all.remove(it.key) }
        return all
    }

    private fun load(): JsonObject = try {
        DataStore.configurationStore.getString(Key.RAYCONTROL_DATAGRAMS, null)
            ?.let { JsonParser.parseString(it).asJsonObject } ?: JsonObject()
    } catch (e: Exception) {
        Logs.w("raycontrol: broken datagram results: ${e.message}")
        JsonObject()
    }

    private class Running(val box: BoxInstance, val tags: Map<String, Long>) {
        var network: String? = null
        var job: Job? = null
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()
    private var running: Running? = null

    /** The core started with [tags] (ConfigBuildResult.naiveDatagramTags): decide on the network it runs on. */
    fun started(box: BoxInstance, tags: Map<String, Long>) {
        synchronized(lock) {
            running?.job?.cancel()
            running = if (tags.isEmpty()) null else Running(box, tags)
        }
        networkChanged()
    }

    /** The core stops: nothing more is allowed on it. */
    fun stopped() = synchronized(lock) {
        running?.job?.cancel()
        running = null
    }

    /** The default network changed, or may have: a network the core has not been decided on is decided anew. */
    fun networkChanged() {
        val network = RayChannel.network() ?: return
        synchronized(lock) {
            val r = running ?: return
            if (r.network == network.key) return
            r.network = network.key
            r.job?.cancel()
            r.job = scope.launch { decide(r, network) }
        }
    }

    // the decision still stands: the core it was for runs, on the network it was for, and [job] is still the one
    // deciding there (a network that went and came back under the same key has a job of its own). Under the lock, which
    // a network change holds to cancel the job and set the next one
    private fun stands(r: Running, network: ChannelNetwork, job: Job) =
        job.isActive && r.job === job && running === r && r.network == network.key

    private suspend fun decide(r: Running, network: ChannelNetwork) {
        val job = currentCoroutineContext().job
        // the last network's allowance goes at once: this one's UDP over TCP until decided
        for (tag in r.tags.keys) {
            runCatching { Libcore.resetNaiveDatagram(r.box, tag) }.onFailure { Logs.w("raycontrol datagrams $tag: ${it.message}") }
        }
        delay(SETTLE_MS)
        for ((tag, profileId) in r.tags) {
            currentCoroutineContext().ensureActive()
            val (node, key) = RayNodes.ownerOf(profileId) ?: continue
            val svc = key.substringBefore('#').toIntOrNull()
            val info = node.probe?.takeIf { it.udp && it.reachesThrough(svc) }
            if (info == null) {
                Logs.i("raycontrol datagrams $tag: the node probes no UDP through the service, UDP stays over TCP")
                continue
            }
            val hints = RayChannel.hints(node.spa.nodeId, network)
            if (hints != null && (hints.udpBlocked || hints.udpMtu == "<1200")) {
                Logs.i("raycontrol datagrams $tag: the node's hints say UDP does not pass on this network, UDP stays over TCP")
                continue
            }
            if (runCatching { Libcore.naiveDatagramState(r.box, tag) }.getOrNull() == "unsupported") continue
            val id = "${node.spa.nodeId}#$svc"
            val known = remembered(load(), network, id, System.currentTimeMillis() / 1000)
            val ok = known ?: probe(r, network, job, tag, node, info) ?: continue
            // the probe's call blocks and does not stop with the job: one whose network went, or whose core stopped,
            // while it ran measured a path that is no longer this one's, partly another network's or a closed core's.
            // Neither kept nor applied; the network's next decision probes it anew
            synchronized(lock) {
                if (!stands(r, network, job)) return
                if (known == null) {
                    val all = remember(load(), network, id, ok, System.currentTimeMillis() / 1000)
                    DataStore.configurationStore.putString(Key.RAYCONTROL_DATAGRAMS, all.toString())
                }
                if (ok) runCatching { Libcore.setNaiveDatagramAllowed(r.box, tag, true) }.onFailure { Logs.w("raycontrol datagrams $tag: ${it.message}") }
            }
            Logs.i("raycontrol datagrams $tag: " + if (ok) "UDP as HTTP/3 datagrams on this network" else "the probe did not pass, UDP stays over TCP on this network")
        }
    }

    /** The probe's verdict for [tag], or null when it could not measure the path in a few tries. */
    private suspend fun probe(r: Running, network: ChannelNetwork, job: Job, tag: String, node: RayNode, info: RayProbeInfo): Boolean? {
        val request = request(node, info) ?: return null
        var failed = 0
        for (attempt in 1..TRIES) {
            currentCoroutineContext().ensureActive()
            if (!synchronized(lock) { stands(r, network, job) }) return null
            val verdict = try {
                verdict(JsonParser.parseString(Libcore.raycontrolProbeDatagram(r.box, tag, request.toString())).asJsonObject.getAsJsonObject("echo"))
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.w("raycontrol datagrams $tag: probe: ${e.message}")
                Verdict.Again(0)
            }
            val retryAfter = when (verdict) {
                Verdict.Passed -> return true
                Verdict.Failed -> if (++failed >= FAILS) return false else 0
                is Verdict.Again -> verdict.retryAfter
            }
            if (attempt < TRIES) delay((retryAfter * 1000L).coerceIn(RETRY_MS, RETRY_MAX_MS))
        }
        return null
    }

    /** A probe-v1 request through the tunnel at the probe name, the echo only: the path datagrams take. */
    private fun request(node: RayNode, info: RayProbeInfo): JsonObject? {
        val sub = node.subscription ?: return null
        return JsonObject().apply {
            addProperty("host", node.host)
            addProperty("port", sub.port)
            addProperty("pin", sub.pin)
            addProperty("device_id", node.deviceId)
            add("spa", node.spaJson().apply { addProperty("device_key", RayNodes.deviceKeyHex(node)) })
            addProperty("tunnel", info.tunnel)
            addProperty("echo_count", ECHO_COUNT)
            addProperty("echo_size", ECHO_SIZE)
            addProperty("window_ms", WINDOW_MS)
        }
    }
}

package io.niko.raycontrol

import com.google.gson.JsonParser
import io.niko.SingBoxOptions.DNSServerOptions
import io.niko.database.DataStore
import io.niko.ktx.Logs
import io.niko.net.LocalResolverImpl
import io.niko.utils.JavaUtil.gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import libcore.Libcore

/** A public Yggdrasil peer as a check saw it: its link up with the latency, or not (the error, when one came). */
data class YggPeerCheck(val uri: String, val up: Boolean, val latencyMs: Long = 0, val error: String? = null)

/**
 * The node's public Yggdrasil peers (niko-ygg-via, R4-18), checked before the client takes them: a mesh node of the
 * client's own with a throwaway key (never the device's) links with all of them for a few seconds, dialling them as
 * the profile would (directly, with the direct DNS), and the core tells which links came up and how fast. The
 * profile then takes the fastest few: every public peer sees the device's address, and each link costs keepalives.
 */
object RayYggPeers {

    /** Public peers a profile takes at most. */
    const val TAKE = 3

    private const val TAG = "check"
    private const val WAIT_MS = 6000L

    /** The state of each of [peers] after up to a few seconds, in their order. */
    suspend fun check(peers: List<String>): List<YggPeerCheck> = withContext(Dispatchers.IO) {
        if (peers.isEmpty()) return@withContext emptyList()
        val box = Libcore.newSingBoxInstance(config(peers), LocalResolverImpl)
        var seen = emptyList<YggPeerCheck>()
        try {
            box.start()
            val until = System.currentTimeMillis() + WAIT_MS
            while (System.currentTimeMillis() < until) {
                delay(300)
                seen = parse(Libcore.yggdrasilPeers(box, TAG))
                // each one up or failed: nothing more to wait for
                if (peers.all { uri -> seen.any { sameEndpoint(it.uri, uri) && (it.up || it.error != null) } }) break
            }
        } catch (e: Exception) {
            Logs.w("raycontrol: Yggdrasil peer check: ${e.message}")
        } finally {
            runCatching { box.close() }
        }
        peers.map { uri ->
            seen.firstOrNull { sameEndpoint(it.uri, uri) }?.copy(uri = uri) ?: YggPeerCheck(uri, false, error = null)
        }
    }

    /** The peers to take: up, the fastest first, at most [TAKE]. */
    fun pick(checks: List<YggPeerCheck>): List<String> =
        checks.filter { it.up }.sortedBy { it.latencyMs }.take(TAKE).map { it.uri }

    /** One line for the user: the peer's scheme and host, and how the check went. */
    fun label(uri: String): String = runCatching {
        val u = java.net.URI(uri)
        "${u.scheme}://${u.host}:${u.port}"
    }.getOrDefault(uri)

    private fun parse(json: String): List<YggPeerCheck> = JsonParser.parseString(json).asJsonArray.map {
        val o = it.asJsonObject
        YggPeerCheck(
            uri = o.get("uri").asString,
            up = o.get("up")?.asBoolean ?: false,
            latencyMs = o.get("latency_ms")?.asLong ?: 0,
            error = o.get("error")?.asString,
        )
    }

    // the core may write a peer's URI back in its own form: scheme, host and port tell them apart
    private fun sameEndpoint(a: String, b: String): Boolean = runCatching {
        val x = java.net.URI(a)
        val y = java.net.URI(b)
        x.scheme.equals(y.scheme, true) && x.host.trim('[', ']').equals(y.host.trim('[', ']'), true) && x.port == y.port
    }.getOrDefault(a == b)

    /** A core with the checking node alone: no inbounds, the direct DNS of the profile's dials. */
    private fun config(peers: List<String>): String {
        val directDns = DataStore.directDns.split("\n").map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: "local"
        val servers = listOf(
            DNSServerOptions.fromAddress("local", "dns-local", null, null).asMap(),
            DNSServerOptions.fromAddress(directDns, "dns-direct", null, "dns-local").asMap(),
        )
        return gson.toJson(
            mapOf(
                "log" to mapOf("level" to "warn"),
                "dns" to mapOf("servers" to servers, "final" to "dns-direct"),
                "endpoints" to listOf(
                    mapOf(
                        "type" to "yggdrasil", "tag" to TAG,
                        // a throwaway node: the peers never learn the device's mesh key
                        "private_key" to Libcore.yggdrasilGenerateKey(),
                        "node_info_privacy" to true,
                        "peers" to peers,
                        "domain_resolver" to "dns-direct",
                    )
                ),
                "outbounds" to listOf(mapOf("type" to "direct", "tag" to "direct")),
                "route" to mapOf("final" to "direct", "default_domain_resolver" to "dns-direct"),
            )
        )
    }
}

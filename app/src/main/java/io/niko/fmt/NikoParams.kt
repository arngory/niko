package io.niko.fmt

import io.niko.database.DataStore
import io.niko.ktx.isIpAddress

/**
 * Niko parameters of a share link (raycontrol subscription-v1.md §4):
 * - niko-dns: `dnstt;<resolver>;<zone>;<pubkey hex>` or `masterdns;<resolvers, comma>;<zone>;<method>`,
 *   an empty resolver means the client's choice (Settings → raycontrol nodes);
 * - niko-dnskey: the MasterDnsVPN key;
 * - niko-pin: SHA-256 of the server key (SPKI), base64url;
 * - niko-ygg: `<peer>;<seed>`, a Yggdrasil profile: the VLESS server is the node's mesh address, reached through a
 *   mesh node of the device's own (the ed25519 seed, 64 hex digits) whose only peer is the node
 *   (`tls://<name>:443?key=<node key>&sni=<name>`);
 * - niko-ygg-via (repeated): the node's public peers, used only when the direct peer does not connect.
 * niko-node and niko-svc tie the profile to an imported node: its SPA follows the server address already.
 */
fun AbstractBean.applyNikoParams(param: (String) -> String?) = applyNikoParams(param) { listOfNotNull(param(it)) }

/** As [applyNikoParams], with [params] giving every value of a repeated parameter (niko-ygg-via). */
fun AbstractBean.applyNikoParams(param: (String) -> String?, params: (String) -> List<String>) {
    param("niko-dns")?.takeIf { it.isNotBlank() }?.let { spec ->
        val parts = spec.split(';')
        require(parts.size == 4) { "niko-dns: want engine;resolver;zone;key-or-method" }
        val (engine, resolver, zone, last) = parts
        when (engine) {
            DnsTunnel.DNSTT -> {
                dnsTunnel = DnsTunnel.DNSTT
                dnsTunnelResolver = resolver.ifBlank { DataStore.raycontrolDnsttResolver }
                dnsTunnelDomain = zone
                dnsTunnelKey = last
            }

            "masterdns", DnsTunnel.MASTERDNSVPN -> {
                dnsTunnel = DnsTunnel.MASTERDNSVPN
                dnsTunnelResolver = resolver.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    .joinToString("\n").ifBlank { DataStore.raycontrolMasterDnsResolvers }
                dnsTunnelDomain = zone
                dnsTunnelMethod = last.toIntOrNull() ?: 5
                // the node's MasterDnsVPN runs in TCP mode with the inner stage as its one upstream
                dnsTunnelFixedUpstream = true
                param("niko-dnskey")?.let { dnsTunnelKey = it.trim() }
            }

            else -> throw IllegalArgumentException("niko-dns: unknown engine $engine")
        }
    }
    param("niko-pin")?.takeIf { it.isNotBlank() }?.let { certPins = it.trim() }
    param("niko-ygg")?.takeIf { it.isNotBlank() }?.let { spec ->
        val (peer, seed) = parseYggParam(spec)
        yggPeer = peer
        yggKey = seed
        // the node's public peers (R4-18): a way through the public mesh when the direct peer does not connect.
        // A quic:// peer only by address: yggdrasil-go looks its name up with the system resolver, past the core's
        // DNS (on the network's DNS in the clear, or into a tunnel that is down right then)
        yggVia = params("niko-ygg-via").map { it.trim() }.filter { via ->
            val host = runCatching { java.net.URI(via).host }.getOrNull()?.trim('[', ']')
            YGG_VIA_SCHEMES.any { via.startsWith("$it://") } && host != null &&
                (!via.startsWith("quic://") || host.isIpAddress())
        }.distinct().joinToString("\n")
    }
}

/** The peer schemes yggdrasil-go dials (subscription-v1.md §4). */
private val YGG_VIA_SCHEMES = listOf("tls", "quic", "wss", "ws", "tcp")

/** `<peer>;<seed>` of niko-ygg, checked: a tls:// peer with the node's key, a 32-byte seed in hex. */
fun parseYggParam(spec: String): Pair<String, String> {
    val parts = spec.split(';')
    require(parts.size == 2) { "niko-ygg: want peer;seed" }
    val peer = parts[0].trim()
    val seed = parts[1].trim().lowercase()
    val uri = java.net.URI(peer)
    require(uri.scheme == "tls" && !uri.host.isNullOrBlank() && uri.port > 0) { "niko-ygg: the peer must be tls://host:port" }
    val key = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("key=") }?.removePrefix("key=")
    require(key != null && key.length == 64 && key.all { it in "0123456789abcdefABCDEF" }) { "niko-ygg: the peer needs the node's key" }
    require(seed.length == 64 && seed.all { it in "0123456789abcdef" }) { "niko-ygg: the seed must be 64 hex digits" }
    return peer to seed
}

/** The host a Yggdrasil profile's peer link dials: the node itself. */
fun yggPeerHost(peer: String): String? = runCatching { java.net.URI(peer).host }.getOrNull()

/** A latency test or probe of a Yggdrasil profile whose key the running connection uses: not run. */
class YggInUseException(name: String) :
    Exception("$name: its Yggdrasil key is in use by the running connection; a second link would disturb it")

/**
 * The device keys of the Yggdrasil profiles the service's core runs (any profile its config holds: the selected
 * one, a group's others, route rules, chains), as short hashes in the settings store, which every process of the
 * app reads. The service sets them when its core starts and clears them when it stops.
 */
fun setRunningYggKeys(keys: Set<String>) {
    io.niko.database.DataStore.configurationStore.putString(
        io.niko.Key.RAYCONTROL_YGG_RUNNING, keys.joinToString(",") { yggKeyHash(it) }
    )
}

/** Whether the service's core runs a mesh node with this device key. */
fun yggKeyRunning(seed: String): Boolean {
    if (!io.niko.database.DataStore.serviceState.started) return false
    val running = io.niko.database.DataStore.configurationStore.getString(io.niko.Key.RAYCONTROL_YGG_RUNNING, null)
    return !running.isNullOrBlank() && yggKeyHash(seed) in running.split(',')
}

private fun yggKeyHash(seed: String) = java.security.MessageDigest.getInstance("SHA-256")
    .digest(seed.toByteArray()).take(8).joinToString("") { "%02x".format(it) }

package io.niko.fmt

import io.niko.database.DataStore

/**
 * Niko parameters of a share link (raycontrol subscription-v1.md §4):
 * - niko-dns: `dnstt;<resolver>;<zone>;<pubkey hex>` or `masterdns;<resolvers, comma>;<zone>;<method>`,
 *   an empty resolver means the client's choice (Settings → raycontrol nodes);
 * - niko-dnskey: the MasterDnsVPN key;
 * - niko-pin: SHA-256 of the server key (SPKI), base64url;
 * - niko-ygg: `<peer>;<seed>`, a Yggdrasil profile: the VLESS server is the node's mesh address, reached through a
 *   mesh node of the device's own (the ed25519 seed, 64 hex digits) whose only peer is the node
 *   (`tls://<name>:443?key=<node key>&sni=<name>`).
 * niko-node and niko-svc tie the profile to an imported node: its SPA follows the server address already.
 */
fun AbstractBean.applyNikoParams(param: (String) -> String?) {
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
    }
}

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

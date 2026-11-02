package io.niko.fmt

import io.niko.database.DataStore

/**
 * Niko parameters of a share link (raycontrol subscription-v1.md §4):
 * - niko-dns: `dnstt;<resolver>;<zone>;<pubkey hex>` or `masterdns;<resolvers, comma>;<zone>;<method>`,
 *   an empty resolver means the client's choice (Settings → raycontrol nodes);
 * - niko-dnskey: the MasterDnsVPN key;
 * - niko-pin: SHA-256 of the server key (SPKI), base64url.
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
}

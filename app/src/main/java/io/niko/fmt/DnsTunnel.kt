package io.niko.fmt

/**
 * A DNS tunnel a profile dials through (experimental). The core runs the tunnel client and
 * the profile reaches its server through it:
 * - dnstt: the dnstt server forwards to its one upstream, so this profile's address is unused;
 * - masterdnsvpn: in SOCKS5 mode the server connects to this profile's server, in TCP mode
 *   (fixed upstream) to its one target.
 * Empty domain = off.
 */
/**
 * A subscription's update of this bean (fresh from its link) over [old], the stored row: a DNS tunnel the link gives
 * (niko-dns) is the node's, its fields as the link has them (a seal turned on, a rotated key, dnsauth), only the
 * resolver the user picked for the same engine staying; a tunnel the user set on a link without one stays whole.
 */
fun AbstractBean.keepDnsTunnelOf(old: AbstractBean) {
    if (DnsTunnel.of(this).enabled) {
        if (!old.dnsTunnelResolver.isNullOrBlank() && old.dnsTunnel == dnsTunnel) dnsTunnelResolver = old.dnsTunnelResolver
        return
    }
    dnsTunnel = old.dnsTunnel
    dnsTunnelResolver = old.dnsTunnelResolver
    dnsTunnelDomain = old.dnsTunnelDomain
    dnsTunnelKey = old.dnsTunnelKey
    dnsTunnelMethod = old.dnsTunnelMethod
    dnsTunnelFixedUpstream = old.dnsTunnelFixedUpstream
    dnsTunnelAuth = old.dnsTunnelAuth
    dnsTunnelSeal = old.dnsTunnelSeal
}

data class DnsTunnel(
    val engine: String,
    val resolver: String,
    val domain: String,
    val key: String,
    val method: Int = 2,
    val fixedUpstream: Boolean = false,
    /** raycontrol dnsauth-v1 of a dnstt tunnel: "v1;<node id>;<service id>", "" off */
    val auth: String = "",
    /** raycontrol dnsseal-v1 of a dnstt tunnel: the zone's key in base64url, "" off */
    val seal: String = "",
) {

    companion object {
        const val DNSTT = "dnstt"
        const val MASTERDNSVPN = "masterdnsvpn"
        private val DNSTT_KEY = Regex("^[0-9a-fA-F]{64}$")
        private val SEAL_KEY = Regex("^[A-Za-z0-9_-]{43}$")
        private val DNSTT_RESOLVER =
            Regex("^(https://\\S+|(tls|tcp|udp)://\\S+|[^\\s/:]+(:\\d+)?|\\[[0-9a-fA-F:]+](:\\d+)?)$")
        // 8.8.8.8, 1.1.1.1:5353, 192.168.1.0/30, [2001:db8::1]:53, 2001:db8::1 ([2001:db8::1] is taken bare: resolvers)
        private val MDV_RESOLVER =
            Regex("^([0-9.]+(/\\d+)?(:\\d+)?|\\[[0-9a-fA-F:]+](:\\d+)?|[0-9a-fA-F:]+(/\\d+)?)$")
        private val DOMAIN = Regex("^[A-Za-z0-9.-]+$")

        fun of(bean: AbstractBean) = DnsTunnel(
            engine = bean.dnsTunnel.orEmpty().ifEmpty { DNSTT },
            resolver = bean.dnsTunnelResolver.orEmpty(),
            domain = bean.dnsTunnelDomain.orEmpty(),
            key = bean.dnsTunnelKey.orEmpty(),
            method = bean.dnsTunnelMethod ?: 2,
            fixedUpstream = bean.dnsTunnelFixedUpstream == true,
            auth = bean.dnsTunnelAuth.orEmpty(),
            seal = bean.dnsTunnelSeal.orEmpty(),
        )
    }

    val enabled get() = domain.isNotBlank()

    /** dnstt and a masterdnsvpn server in TCP mode forward to one upstream, the profile's address is unused */
    val ignoresServerAddress get() = engine == DNSTT || fixedUpstream

    private val domains get() = domain.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    // MasterDnsVPN takes an IPv6 address in brackets only with a port (it skipped "[2001:db8::1]"): such a line goes
    // bare, a profile saved with one included
    private val resolvers
        get() = resolver.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { if (it.startsWith("[") && it.endsWith("]")) it.removeSurrounding("[", "]") else it }

    /** null when usable or off, otherwise what is wrong */
    fun validate(): String? {
        if (!enabled) return null
        if (domains.any { !DOMAIN.matches(it) }) return "DNS tunnel: the domain is a name, like t.example.com"
        return when (engine) {
            DNSTT -> when {
                domains.size != 1 -> "DNS tunnel: dnstt takes one domain"
                resolver.isBlank() -> "DNS tunnel: the resolver is missing"
                !DNSTT_RESOLVER.matches(resolver) -> "DNS tunnel: the resolver must be https://…, tls://host, tcp://host or udp://host"
                !DNSTT_KEY.matches(key) -> "DNS tunnel: the public key must be 64 hex digits"
                seal.isNotEmpty() && !SEAL_KEY.matches(seal) -> "DNS tunnel: the seal key must be 32 bytes in base64url"
                else -> null
            }

            MASTERDNSVPN -> when {
                resolvers.isEmpty() -> "DNS tunnel: no resolvers, one IP per line"
                resolvers.any { !MDV_RESOLVER.matches(it) } -> "DNS tunnel: MasterDnsVPN resolvers are IPs (or IP/prefix), with an optional :port"
                method !in 0..5 -> "DNS tunnel: the encryption method is 0-5"
                method != 0 && key.isBlank() -> "DNS tunnel: the key is missing"
                else -> null
            }

            else -> "DNS tunnel: unknown engine $engine"
        }
    }

    /** The node id and the service id of dnsauth-v1 (dnstt only), or null when it is off. */
    val authNode: Pair<String, Int>?
        get() = auth.split(';').takeIf { engine == DNSTT && it.size == 3 && it[0] == "v1" }?.let { (_, node, svc) ->
            svc.toIntOrNull()?.let { node to it }
        }

    /** The core outbound; [deviceKeyRef]: the reference of the node's device key (dnsauth-v1). */
    fun outboundOptions(protectPath: String?, deviceKeyRef: String? = null): Map<String, Any> = when (engine) {
        MASTERDNSVPN -> buildMap {
            put("type", MASTERDNSVPN)
            put("domains", domains)
            put("resolvers", resolvers)
            put("encryption_method", method)
            if (key.isNotBlank()) put("encryption_key", key)
            put("mode", if (fixedUpstream) "tcp" else "socks5")
            // its own sockets must stay outside the VPN
            if (protectPath != null) put("protect_path", protectPath)
        }

        else -> buildMap {
            put("type", DNSTT)
            put("domain", domain.trim())
            put("public_key", key.lowercase())
            put("resolver", resolver.trim())
            if (seal.isNotEmpty()) put("seal", mapOf("key" to seal))
            authNode?.let { (node, svc) ->
                if (deviceKeyRef != null) {
                    put("auth", mapOf("node_id" to node, "service_id" to svc, "device_key_ref" to deviceKeyRef))
                }
            }
        }
    }
}

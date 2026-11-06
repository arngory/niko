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
 * A subscription's update of this bean (fresh from its link) over [old], the stored row. A DNS tunnel the link gives
 * (niko-dns) is the profile itself, its server reached only through it: the link's fields win whole, as in a node's
 * own subscription (a seal turned on, a rotated key, dnsauth, the link's resolver or, left to the client, the
 * setting's as it is now, so a changed setting applies from the next update as its help says). An edit of such a
 * tunnel in the profile, turning it off included, lasts until the next update. A tunnel the user set on a link
 * without one stays whole.
 */
fun AbstractBean.keepDnsTunnelOf(old: AbstractBean) {
    if (DnsTunnel.of(this).enabled) return
    dnsTunnel = old.dnsTunnel
    dnsTunnelResolver = old.dnsTunnelResolver
    dnsTunnelDomain = old.dnsTunnelDomain
    dnsTunnelKey = old.dnsTunnelKey
    dnsTunnelMethod = old.dnsTunnelMethod
    dnsTunnelFixedUpstream = old.dnsTunnelFixedUpstream
    dnsTunnelAuth = old.dnsTunnelAuth
    dnsTunnelSeal = old.dnsTunnelSeal
    dnsTunnelSpare = old.dnsTunnelSpare
    dnsTunnelTls = old.dnsTunnelTls
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
    /**
     * raycontrol dnsseal-v1 of a dnstt tunnel: the zone's key in base64url; mdseal-v1 of a masterdns one: the node's
     * mode, [SEAL_OPTIONAL] or [SEAL_REQUIRED] (the key is [key]); "" off
     */
    val seal: String = "",
    /** raycontrol dnszones-v1: the spare zones in their order, comma separated, "" none (the main zone is [domain]) */
    val spare: String = "",
    /** slipstream's TLS toward its server, "<ALPN>;<SNI>" (the pin of its key is [key]); "" for the others */
    val tls: String = "",
) {

    companion object {
        const val DNSTT = "dnstt"
        const val MASTERDNSVPN = "masterdnsvpn"
        // raycontrol slipstream-v1: QUIC over DNS, the bundled slipstream client (a process of its own)
        const val SLIPSTREAM = "slipstream"
        // slipstream's name holds (240 - zone) / 1.6 bytes, less dnsauth's 8, and an Initial needs 129 (slipstream-v1 §3)
        const val SLIPSTREAM_MAX_ZONE = 20
        // a path each: the client's multipath adds them up
        const val MAX_SLIP_RESOLVERS = 4
        private val SLIP_PIN = Regex("^[A-Za-z0-9_-]{43}$")
        private val SLIP_ALPN = Regex("^[!-~]{1,255}$")
        // a name, its last label starting with a letter (an SNI is never an address)
        private val SLIP_SNI = Regex("^([A-Za-z0-9]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+[A-Za-z]([A-Za-z0-9-]{0,61}[A-Za-z0-9])?$")
        // a resolver the client reaches through the core: an IPv4 or bracketed IPv6 address, a port if not 53
        private val SLIP_RESOLVER = Regex("^([0-9]{1,3}(\\.[0-9]{1,3}){3}(:\\d{1,5})?|\\[[0-9a-fA-F:]+](:\\d{1,5})?|[0-9a-fA-F:]*:[0-9a-fA-F:]+)$")
        const val SEAL_OPTIONAL = "optional"
        const val SEAL_REQUIRED = "required"
        private val AUTH_VERSIONS = setOf("v1", "v2")

        /**
         * A row signed by dnsauth-v1 ("v1;node;svc", what a link's niko-dnsauth=v1 gives) signed by v2 instead, when
         * [v2] (the node's subscription announces dnsauth.v 2: its niko-server checks the hint); else as it is.
         */
        fun signWithHint(bean: AbstractBean, v2: Boolean) {
            val auth = bean.dnsTunnelAuth.orEmpty()
            if (v2 && auth.startsWith("v1;")) bean.dnsTunnelAuth = "v2;" + auth.removePrefix("v1;")
        }
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
            spare = bean.dnsTunnelSpare.orEmpty(),
            tls = bean.dnsTunnelTls.orEmpty(),
        )
    }

    val enabled get() = domain.isNotBlank()

    /**
     * dnstt, slipstream and a masterdnsvpn server in TCP mode forward to one upstream, the profile's address is
     * unused
     */
    val ignoresServerAddress get() = engine == DNSTT || engine == SLIPSTREAM || fixedUpstream

    /** slipstream's ALPN and SNI ([tls]) */
    val alpn get() = tls.substringBefore(';')
    val sni get() = tls.substringAfter(';', "")

    private val domains get() = domain.split(',', ' ', '\n').map { it.trim() }.filter { it.isNotEmpty() }

    /** The spare zones (dnszones-v1), in their order */
    val spareZones get() = spare.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /**
     * The zones the tunnel may go through on a network: the main one (the first of [domain]) and its spares, in their
     * order (dnszones-v1 §3); one zone goes into the config at a time.
     */
    val zones get() = listOfNotNull(domains.firstOrNull()) + spareZones

    /** This tunnel through [zone], one of its [zones] (the main one otherwise) */
    fun withZone(zone: String?): DnsTunnel =
        if (zone == null || zone == domains.firstOrNull() || zone !in spareZones) this else copy(domain = zone)
    // MasterDnsVPN takes an IPv6 address in brackets only with a port (it skipped "[2001:db8::1]"): such a line goes
    // bare, a profile saved with one included
    private val resolvers
        get() = resolver.lines().map { it.trim() }.filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { if (it.startsWith("[") && it.endsWith("]")) it.removeSurrounding("[", "]") else it }

    /** null when usable or off, otherwise what is wrong */
    fun validate(): String? {
        if (!enabled) return null
        if (domains.any { !DOMAIN.matches(it) }) return "DNS tunnel: the domain is a name, like t.example.com"
        if (spareZones.any { !DOMAIN.matches(it) }) return "DNS tunnel: a spare zone is a name, like t2.example.com"
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
                seal.isNotEmpty() && seal != SEAL_OPTIONAL && seal != SEAL_REQUIRED -> "DNS tunnel: unknown seal mode $seal"
                seal.isNotEmpty() && (method == 0 || key.isBlank()) -> "DNS tunnel: the seal needs the key"
                // a tag under a cipher that does not authenticate the query says nothing (dnsauth-masterdns §3)
                authNode != null && method !in 3..5 -> "DNS tunnel: dnsauth needs encryption method 3-5"
                else -> null
            }

            SLIPSTREAM -> when {
                domains.size != 1 -> "DNS tunnel: slipstream takes one zone"
                spareZones.isNotEmpty() -> "DNS tunnel: slipstream has no spare zones"
                domains.first().length > SLIPSTREAM_MAX_ZONE ->
                    "DNS tunnel: slipstream's zone is $SLIPSTREAM_MAX_ZONE characters at most (a query must hold a packet)"
                resolvers.isEmpty() -> "DNS tunnel: no resolvers, one IP per line"
                resolvers.size > MAX_SLIP_RESOLVERS -> "DNS tunnel: slipstream takes $MAX_SLIP_RESOLVERS resolvers at most"
                resolvers.any { !SLIP_RESOLVER.matches(it) } ->
                    "DNS tunnel: slipstream resolvers are IP addresses, with an optional :port"
                !SLIP_PIN.matches(key) -> "DNS tunnel: slipstream's pin is the SHA-256 of the server's key in base64url"
                !SLIP_ALPN.matches(alpn) -> "DNS tunnel: slipstream's ALPN is missing"
                !SLIP_SNI.matches(sni) -> "DNS tunnel: slipstream's server name is a name, like cdn.example.net"
                // the node's front takes dnsauth-v2 only (slipstream-v1 §2)
                authNode != null && authVersion != 2 -> "DNS tunnel: slipstream signs by dnsauth-v2 only"
                else -> null
            }

            else -> "DNS tunnel: unknown engine $engine"
        }
    }

    /** slipstream's resolvers as host and port (an IPv6 address bare, 53 when none is given) */
    val slipResolvers: List<Pair<String, Int>>
        get() = resolvers.map { line ->
            when {
                line.startsWith("[") -> line.substringAfter('[').substringBefore(']') to
                    (line.substringAfter("]:", "").toIntOrNull() ?: 53)
                line.count { it == ':' } > 1 -> line to 53
                ':' in line -> line.substringBefore(':') to (line.substringAfter(':').toIntOrNull() ?: 53)
                else -> line to 53
            }
        }

    /**
     * The slipstream client's arguments: it listens on 127.0.0.1:[localPort] (the hop dials it there), its queries go
     * to [resolvers] (the core's mapping ports, so they leave through the core's own sockets, outside the VPN), and
     * [clockOffset] is how far the node's clock is ahead of the device's, for dnsauth's epoch. dnsauth's key goes in
     * the environment (SLIPSTREAM_DNSAUTH_KEY), never among the arguments, which other processes of the app may read.
     */
    fun slipstreamArgs(localPort: Int, resolvers: List<String>, clockOffset: Long): List<String> = buildList {
        add("--quiet")
        add("--tcp-listen-host=127.0.0.1")
        add("--tcp-listen-port=$localPort")
        add("--domain=${domains.first()}")
        for (resolver in resolvers) add("--resolver=$resolver")
        add("--pin=$key")
        add("--alpn=$alpn")
        add("--sni=$sni")
        if (authNode != null) {
            add("--dnsauth")
            add("--clock-offset=$clockOffset")
        }
    }

    /**
     * The node id and the service id of dnsauth (dnstt, masterdns since raycontrol v0.14), or null when it is off:
     * "v1;…" signs by dnsauth-v1, "v2;…" by dnsauth-v2 (the hint, when the node's subscription announces it).
     */
    val authNode: Pair<String, Int>?
        get() = auth.split(';').takeIf { it.size == 3 && it[0] in AUTH_VERSIONS }?.let { (_, node, svc) ->
            svc.toIntOrNull()?.let { node to it }
        }

    /** dnsauth's version, 2 for "v2;…", else 1 (when [authNode] is on). */
    val authVersion: Int
        get() = if (auth.startsWith("v2;")) 2 else 1

    /**
     * The core outbound; [deviceKeyRef]: the reference of the node's device key (dnsauth-v1), [clockSkew]: how many
     * seconds the node's clock is ahead of the device's (the subscription's), the tag's epoch being the node's.
     */
    fun outboundOptions(protectPath: String?, deviceKeyRef: String? = null, clockSkew: Long = 0): Map<String, Any> {
        val authOptions = authNode?.takeIf { deviceKeyRef != null }?.let { (node, svc) ->
            buildMap {
                put("node_id", node)
                put("service_id", svc)
                put("device_key_ref", deviceKeyRef!!)
                if (clockSkew != 0L) put("clock_skew", clockSkew)
                // dnsauth-v2: hint || tag in the same 8 bytes (raycontrol docs/spec/dnsauth-v2.md); v1 sends none
                if (authVersion == 2) put("version", 2)
            }
        }
        return when (engine) {
            MASTERDNSVPN -> buildMap {
                put("type", MASTERDNSVPN)
                put("domains", domains)
                put("resolvers", resolvers)
                put("encryption_method", method)
                if (key.isNotBlank()) put("encryption_key", key)
                put("mode", if (fixedUpstream) "tcp" else "socks5")
                // its own sockets must stay outside the VPN
                if (protectPath != null) put("protect_path", protectPath)
                if (seal.isNotEmpty()) put("seal", seal)
                authOptions?.let { put("auth", it) }
            }

            else -> buildMap {
                put("type", DNSTT)
                put("domain", domain.trim())
                put("public_key", key.lowercase())
                put("resolver", resolver.trim())
                if (seal.isNotEmpty()) put("seal", mapOf("key" to seal))
                authOptions?.let { put("auth", it) }
            }
        }
    }
}

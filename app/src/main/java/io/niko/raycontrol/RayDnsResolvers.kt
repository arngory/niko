package io.niko.raycontrol

import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.SagerNet
import io.niko.ktx.Logs
import io.niko.ktx.app
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import libcore.Libcore
import java.util.Locale

/**
 * The resolver of a node's DNS tunnel (dnstt, masterdns) when its link leaves it to the client ("auto"): picked per
 * network with dnsprobe-v1 (raycontrol: a label in the tunnel's zone the node's front answers itself, MAC'd with the
 * subscription's dns_probe key) and kept for a day. Candidates: the network's own resolvers first, then public
 * resolvers of the country (raycontrol docs/13-dns-resolvers.md; no Google, Cloudflare or Quad9), the node's hint
 * (hints.dns, per tunnel kind) moving its kind forward. masterdns takes UDP resolvers only.
 */
object RayDnsResolvers {

    const val AUTO = "auto"

    /** A resolver to try and its kind for the summary: net, udp, doh or dot (never its address). */
    data class Candidate(val resolver: String, val kind: String)

    /** The tunnel kind as raycontrol names it (hints.dns, the services). */
    fun tunnelKind(engine: String) = if (engine == "masterdnsvpn") "masterdns" else engine

    // per country: national or in-country public resolvers over UDP 53, then DoT/DoH where those may pass
    private val COUNTRY = mapOf(
        "ru" to listOf(
            Candidate("195.208.5.1", "udp"), Candidate("195.208.4.1", "udp"), Candidate("62.76.76.62", "udp"),
            Candidate("77.88.8.8", "udp"), Candidate("77.88.8.1", "udp"),
            Candidate("tls://common.dot.dns.yandex.net", "dot"),
            Candidate("https://common.dot.dns.yandex.net/dns-query", "doh"),
        ),
        "cn" to listOf(Candidate("119.29.29.29", "udp"), Candidate("223.5.5.5", "udp"), Candidate("1.2.4.8", "udp")),
    )

    fun country(): String = runCatching {
        val tm = app.getSystemService<TelephonyManager>()
        tm?.networkCountryIso.orEmpty().ifEmpty { tm?.simCountryIso.orEmpty() }
    }.getOrDefault("").ifEmpty { Locale.getDefault().country }.lowercase()

    /** The network's own resolvers (UDP 53), and its strict Private DNS host (DoT) when set. */
    private fun networkCandidates(): List<Candidate> {
        val link = SagerNet.underlyingNetwork?.let { SagerNet.connectivity.getLinkProperties(it) } ?: return emptyList()
        val out = link.dnsServers.mapNotNull { it.hostAddress?.substringBefore('%') }
            .map { Candidate(if (':' in it) "[$it]" else it, "net") }.toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            link.privateDnsServerName?.takeIf { it.isNotBlank() }?.let { out += Candidate("tls://$it", "dot") }
        }
        return out
    }

    /** The candidates for [engine] on this network, in order; a hinted kind goes first after the network's own. */
    fun candidates(engine: String, hintKind: String?): List<Candidate> {
        var all = networkCandidates() + COUNTRY[country()].orEmpty()
        if (engine == "masterdnsvpn") all = all.filter { it.kind == "net" || it.kind == "udp" }
        if (hintKind != null) all = all.sortedBy { if (it.kind == "net") 0 else if (it.kind == hintKind) 1 else 2 }
        return all.distinctBy { it.resolver }
    }

    // what "auto" means until a resolver is picked for the network (no probe key: a node before raycontrol v0.11.1,
    // or not measured yet): the defaults of the releases before, so nothing changes there
    private const val DNSTT_DEFAULT = "https://common.dot.dns.yandex.net/dns-query"
    private const val MASTERDNS_DEFAULT = "77.88.8.8\n77.88.8.1"

    /** The resolver for the tunnel's config: the network's pick while it holds, else the defaults of before. */
    fun resolverFor(node: RayNode?, engine: String): String {
        val network = RayChannel.network()
        val picked = if (node != null && network != null) RayChannel.dnsPick(node.spa.nodeId, network, tunnelKind(engine)) else null
        val first = picked?.get("resolver")?.asString
            ?: return if (engine == "masterdnsvpn") MASTERDNS_DEFAULT else DNSTT_DEFAULT
        // dnstt takes one resolver, masterdns a list: the pick first, then the other UDP candidates of the network
        return when {
            engine == "masterdnsvpn" -> listOf(first).plus(candidates(engine, null).map { it.resolver })
                .distinct().take(4).joinToString("\n") { it.removePrefix("udp://") }
            first.startsWith("https://") || first.startsWith("tls://") || first.startsWith("udp://") -> first
            else -> "udp://$first"
        }
    }

    /** The kind of the resolver picked on [network] for the summary's obs dns, or null. */
    fun pickedKind(nodeId: String, network: ChannelNetwork, tunnelKind: String): String? =
        RayChannel.dnsPick(nodeId, network, tunnelKind)?.get("kind")?.asString

    /**
     * Probes the candidates for [engine] at [zone] and keeps the first that passes a large answer, else the first that
     * passes a small one (TC on the large: the tunnel goes, slower). Returns the pick, or null when none answered.
     */
    suspend fun detect(node: RayNode, network: ChannelNetwork, engine: String, zone: String, progress: (String) -> Unit = {}): Candidate? {
        val key = node.dnsProbeKey ?: return null
        val kind = tunnelKind(engine)
        val hint = RayChannel.hints(node.spa.nodeId, network)?.dns?.get(kind)
        var fallback: Candidate? = null
        for (c in candidates(engine, hint)) {
            currentCoroutineContext().ensureActive()
            val resolver = if (c.resolver.contains("://")) c.resolver else "udp://${c.resolver}"
            val small = probe(resolver, zone, key, large = false)
            progress("dns ${c.kind}: ${if (small?.get("ok")?.asBoolean == true) "answers" else small?.get("error")?.asString ?: "no answer"}")
            if (small?.get("ok")?.asBoolean != true) continue
            val large = probe(resolver, zone, key, large = true)
            if (large?.get("ok")?.asBoolean == true) {
                record(node, network, kind, c, tc = false)
                return c
            }
            if (fallback == null) fallback = c
        }
        fallback?.let { record(node, network, kind, it, tc = true) }
        return fallback
    }

    /**
     * Picks the resolver for each DNS tunnel kind of [node] whose rows leave it to the client, where no pick holds on
     * [network] ([force]: anew). The node's zone of each kind is its rows'.
     */
    suspend fun detectForNode(node: RayNode, network: ChannelNetwork, force: Boolean = false, progress: (String) -> Unit = {}) {
        if (node.dnsProbeKey == null) return
        val zones = LinkedHashMap<String, String>()
        for (id in node.profileIds.orEmpty().values) {
            val bean = io.niko.database.SagerDatabase.proxyDao.getById(id)?.requireBean() ?: continue
            val tunnel = io.niko.fmt.DnsTunnel.of(bean)
            if (!tunnel.enabled || tunnel.resolver.trim() != AUTO) continue
            val zone = tunnel.domain.split(',', ' ', '\n').map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: continue
            zones.putIfAbsent(tunnel.engine, zone)
        }
        for ((engine, zone) in zones) {
            if (!force && RayChannel.dnsPick(node.spa.nodeId, network, tunnelKind(engine)) != null) continue
            val pick = detect(node, network, engine, zone, progress)
            progress("dns ${tunnelKind(engine)}: ${pick?.kind ?: "no resolver answered"}")
        }
    }

    private suspend fun probe(resolver: String, zone: String, key: String, large: Boolean): JsonObject? = withContext(Dispatchers.IO) {
        runCatching {
            JsonParser.parseString(Libcore.raycontrolDnsProbe(JsonObject().apply {
                addProperty("resolver", resolver)
                addProperty("zone", zone)
                addProperty("key", key)
                addProperty("large", large)
                addProperty("timeout_ms", 4_000)
            }.toString())).asJsonObject
        }.getOrNull()
    }

    private fun record(node: RayNode, network: ChannelNetwork, kind: String, c: Candidate, tc: Boolean) {
        Logs.i("raycontrol ${node.host}: $kind on this network goes through a ${c.kind} resolver${if (tc) " (small answers only)" else ""}")
        RayChannel.recordDnsPick(node.spa.nodeId, network, kind, JsonObject().apply {
            addProperty("resolver", c.resolver)
            addProperty("kind", c.kind)
            addProperty("tc", tc)
            addProperty("at", System.currentTimeMillis() / 1000)
        })
    }
}

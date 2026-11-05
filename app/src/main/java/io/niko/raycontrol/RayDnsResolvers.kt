package io.niko.raycontrol

import android.os.Build
import android.telephony.TelephonyManager
import androidx.core.content.getSystemService
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.SagerNet
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase
import io.niko.fmt.AbstractBean
import io.niko.fmt.DnsTunnel
import io.niko.ktx.Logs
import io.niko.ktx.app
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libcore.Libcore
import java.util.Locale

/**
 * The resolver of a node's DNS tunnel (dnstt, masterdns) when its link leaves it to the client ("auto"): picked per
 * network with dnsprobe-v1 (raycontrol: a label in the tunnel's zone the node's front answers itself, MAC'd with the
 * subscription's dns_probe key) and kept for a day. Candidates: the network's own resolvers first, then public
 * resolvers of the country (raycontrol docs/13-dns-resolvers.md; no Google, Cloudflare or Quad9), the node's hint
 * (hints.dns, per tunnel kind) moving its kind forward. masterdns takes UDP resolvers only, and every one that
 * answered (it spreads its queries over the list). No resolver answering is kept too, an hour: a pick of kind
 * [NONE], without a resolver, which leaves the tunnel on the defaults of before.
 */
object RayDnsResolvers {

    const val AUTO = "auto"

    /** The kind of a pick where no candidate answered. */
    const val NONE = "none"

    // masterdns takes at most this many resolvers from the pick
    private const val MAX_RESOLVERS = 4

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

    /** The network's pick of a resolver for [engine] while it holds; not a pick of [NONE]. */
    private fun heldPick(node: RayNode?, engine: String): JsonObject? {
        val network = RayChannel.network() ?: return null
        if (node == null) return null
        return RayChannel.dnsPick(node.spa.nodeId, network, tunnelKind(engine))?.takeIf { it.has("resolver") }
    }

    /** The resolver for the tunnel's config: the network's pick while it holds, else the defaults of before. */
    fun resolverFor(node: RayNode?, engine: String): String {
        val picked = heldPick(node, engine)
        val first = picked?.get("resolver")?.asString
            ?: return if (engine == "masterdnsvpn") MASTERDNS_DEFAULT else DNSTT_DEFAULT
        // dnstt takes one resolver, masterdns a list: the candidates that answered the probe, the pick first
        return when {
            engine == "masterdnsvpn" -> (listOf(first) + picked.getAsJsonArray("all")?.map { it.asString }.orEmpty())
                .distinct().take(MAX_RESOLVERS).joinToString("\n") { it.removePrefix("udp://") }
            first.startsWith("https://") || first.startsWith("tls://") || first.startsWith("udp://") -> first
            else -> "udp://$first"
        }
    }

    /** The engine and zone of [bean]'s DNS tunnel when its resolver is left to the client, else null. */
    private fun autoTunnel(bean: AbstractBean): Pair<String, String>? {
        val tunnel = DnsTunnel.of(bean)
        if (!tunnel.enabled || tunnel.resolver.trim() != AUTO) return null
        val zone = tunnel.domain.split(',', ' ', '\n').map { it.trim() }.firstOrNull { it.isNotEmpty() } ?: return null
        return tunnel.engine to zone
    }

    /**
     * The kind of the resolver [profile]'s DNS tunnel goes through on this network when the resolver is the client's
     * to pick (what [resolverFor] gives its config now: the pick's kind, else the default's), or null: no such tunnel.
     * For the summary's obs dns, recorded with the probe.
     */
    fun usedKind(profile: ProxyEntity): String? {
        val (engine, _) = autoTunnel(profile.requireBean()) ?: return null
        return heldPick(RayNodes.ownerOf(profile.id)?.first, engine)?.get("kind")?.asString
            ?: if (engine == "masterdnsvpn") "udp" else "doh"
    }

    /**
     * Probes the candidates for [engine] at [zone] and keeps the first that passes a large answer, else the first that
     * passes a small one (TC on the large: the tunnel goes, slower); masterdns all that pass, the large ones if any,
     * at most [MAX_RESOLVERS]. Returns the pick, or null when none answered (kept as a pick of [NONE]).
     */
    suspend fun detect(node: RayNode, network: ChannelNetwork, engine: String, zone: String, progress: (String) -> Unit = {}): Candidate? {
        val key = node.dnsProbeKey ?: return null
        val kind = tunnelKind(engine)
        val hint = RayChannel.hints(node.spa.nodeId, network)?.dns?.get(kind)
        val list = engine == "masterdnsvpn"
        val full = mutableListOf<Candidate>()
        val smallOnly = mutableListOf<Candidate>()
        for (c in candidates(engine, hint)) {
            currentCoroutineContext().ensureActive()
            val resolver = if (c.resolver.contains("://")) c.resolver else "udp://${c.resolver}"
            val small = probe(resolver, zone, key, large = false)
            progress("dns ${c.kind}: ${if (small?.get("ok")?.asBoolean == true) "answers" else small?.get("error")?.asString ?: "no answer"}")
            if (small?.get("ok")?.asBoolean != true) continue
            val large = probe(resolver, zone, key, large = true)
            if (large?.get("ok")?.asBoolean == true) full += c else smallOnly += c
            if (full.size >= (if (list) MAX_RESOLVERS else 1)) break
        }
        // the network changed meanwhile: what was measured is neither network's
        if (RayChannel.network()?.key != network.key) return null
        val passed = full.ifEmpty { smallOnly }
        record(node, network, kind, passed, tc = full.isEmpty(), list)
        return passed.firstOrNull()
    }

    /**
     * Picks the resolver anew for each DNS tunnel kind of [node] whose rows leave it to the client. The node's zone of
     * each kind is its rows'.
     */
    suspend fun detectForNode(node: RayNode, network: ChannelNetwork, progress: (String) -> Unit = {}) {
        if (node.dnsProbeKey == null) return
        val zones = LinkedHashMap<String, String>()
        for (id in node.profileIds.orEmpty().values) {
            val bean = SagerDatabase.proxyDao.getById(id)?.requireBean() ?: continue
            val (engine, zone) = autoTunnel(bean) ?: continue
            zones.putIfAbsent(engine, zone)
        }
        for ((engine, zone) in zones) {
            val pick = detect(node, network, engine, zone, progress)
            progress("dns ${tunnelKind(engine)}: ${pick?.kind ?: "no resolver answered"}")
        }
    }

    // a sweep at a time per node and tunnel kind, detached: the failover that starts it does not wait for its probes
    private val sweeping = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val sweeps = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // a pick younger than this is not redone when its tunnel fails: a resolver that answered minutes ago is not why
    private const val RECHECK = 10 * 60L

    /**
     * The row [key] of [node] failed on [network]. When it is a DNS tunnel whose resolver is the client's, the resolver
     * is picked anew in the background (the held pick, or the default, may be what stopped), unless one was picked in
     * the last [RECHECK] or none answered in the last hour. The pick is for the row's next start.
     */
    fun recheckAfterFailure(node: RayNode, network: ChannelNetwork, key: String) {
        if (node.dnsProbeKey == null) return
        val bean = node.profileIds?.get(key)?.let { SagerDatabase.proxyDao.getById(it) }?.requireBean() ?: return
        val (engine, zone) = autoTunnel(bean) ?: return
        val kind = tunnelKind(engine)
        RayChannel.dnsPick(node.spa.nodeId, network, kind)?.let { held ->
            val age = System.currentTimeMillis() / 1000 - (held.get("at")?.asLong ?: 0)
            if (held.get("kind")?.asString == NONE || age < RECHECK) return
        }
        val sweep = "${node.spa.nodeId}/$kind"
        if (!sweeping.add(sweep)) return
        sweeps.launch {
            try {
                detect(node, network, engine, zone)
            } catch (e: Exception) {
                Logs.w("raycontrol ${node.host}: the $kind resolver: ${e.message}")
            } finally {
                sweeping.remove(sweep)
            }
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

    /** Keeps the pick: the first of [passed] (and for a list all of them), or [NONE] when it is empty. */
    private fun record(node: RayNode, network: ChannelNetwork, kind: String, passed: List<Candidate>, tc: Boolean, list: Boolean) {
        val c = passed.firstOrNull()
        if (c == null) Logs.w("raycontrol ${node.host}: no resolver on this network answers for $kind, the default stays")
        else Logs.i("raycontrol ${node.host}: $kind on this network goes through a ${c.kind} resolver${if (tc) " (small answers only)" else ""}")
        RayChannel.recordDnsPick(node.spa.nodeId, network, kind, JsonObject().apply {
            if (c == null) {
                addProperty("kind", NONE)
            } else {
                addProperty("resolver", c.resolver)
                addProperty("kind", c.kind)
                addProperty("tc", tc)
                if (list) add("all", com.google.gson.JsonArray().apply { passed.forEach { add(it.resolver) } })
            }
            addProperty("at", System.currentTimeMillis() / 1000)
        })
    }
}

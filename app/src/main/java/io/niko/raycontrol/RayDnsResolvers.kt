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
import kotlinx.coroutines.CancellationException
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
 * The resolver of a node's dnstt tunnel when its link leaves it to the client ("auto"): picked per network with
 * dnsprobe-v1 (raycontrol: a label in the tunnel's zone the node's front answers itself, MAC'd with the
 * subscription's dns_probe key) and kept for a day. Candidates: the network's own resolvers first, then public
 * resolvers of the country (raycontrol docs/13-dns-resolvers.md; no Google, Cloudflare or Quad9), the node's hint
 * (hints.dns) moving its kind forward. No resolver answering is kept too, an hour: a pick of kind [NONE], without a
 * resolver, which leaves the tunnel on the default of before. masterdns zones answer no probe (its answers look
 * otherwise): its "auto" is the network's own and the country's public resolvers, Yandex only as the fallback
 * ([masterdnsResolvers]).
 */
object RayDnsResolvers {

    const val AUTO = "auto"

    /** The kind of a pick where no candidate answered. */
    const val NONE = "none"

    /** A resolver to try and its kind for the summary: net, udp, doh or dot (never its address). */
    data class Candidate(val resolver: String, val kind: String)

    /** The tunnel kind as raycontrol names it (hints.dns, the services). */
    fun tunnelKind(engine: String) = if (engine == DnsTunnel.MASTERDNSVPN) "masterdns" else engine

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

    /**
     * The network's own resolvers (UDP 53), and its strict Private DNS host (DoT) when set. Not a link-local one (a
     * router's RDNSS fe80::1): the address means nothing without its interface, which the configs do not carry.
     */
    private fun networkCandidates(): List<Candidate> {
        val link = SagerNet.underlyingNetwork?.let { SagerNet.connectivity.getLinkProperties(it) } ?: return emptyList()
        val out = link.dnsServers.filterNot { it.isLinkLocalAddress }.mapNotNull { it.hostAddress?.substringBefore('%') }
            .map { Candidate(if (':' in it) "[$it]" else it, "net") }.toMutableList()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            link.privateDnsServerName?.takeIf { it.isNotBlank() }?.let { out += Candidate("tls://$it", "dot") }
        }
        return out
    }

    /** The candidates on this network, in order; a hinted kind goes first after the network's own. */
    fun candidates(hintKind: String?): List<Candidate> {
        var all = withTcp(networkCandidates() + COUNTRY[country()].orEmpty())
        if (hintKind != null) all = all.sortedBy { if (it.kind == "net") 0 else if (it.kind == hintKind) 1 else 2 }
        return all.distinctBy { it.resolver }
    }

    /**
     * Each resolver on UDP 53 (the network's own, the public ones) followed by itself on TCP 53, of kind "tcp": a
     * network that redirects UDP 53 to its own resolver may leave TCP 53 alone. The order of the resolvers stays.
     */
    fun withTcp(candidates: List<Candidate>): List<Candidate> = candidates.flatMap { c ->
        if (c.kind == "net" || c.kind == "udp") listOf(c, Candidate("tcp://${c.resolver}", TCP)) else listOf(c)
    }

    // what dnstt's "auto" means until a resolver is picked for the network (no probe key: a node before raycontrol
    // v0.11.1, or not measured yet): the default of the releases before
    private const val DNSTT_DEFAULT = "https://common.dot.dns.yandex.net/dns-query"
    private const val DNSTT = DnsTunnel.DNSTT
    private const val MASTERDNS = "masterdns"

    /** The kind of a resolver reached on TCP 53 (dnstt only). */
    const val TCP = "tcp"

    // Yandex's public resolvers: for masterdns the fallback only, after the network's own and the country's others
    private val YANDEX = listOf("77.88.8.8", "77.88.8.1")

    /**
     * masterdns's resolvers for "auto" from [candidates]: those over UDP (the network's own, the country's public
     * ones) but Yandex's, which join them where [fallback] (the tunnel failed on this network without them) or when
     * there are no others. The MasterDnsVPN client checks each at its start and spreads its queries over those that
     * answer, so a dead one costs nothing; it has no order of preference, hence Yandex out of the list, not last in it.
     * An IPv6 address goes bare: MasterDnsVPN takes [..] only with a port.
     */
    fun masterdnsResolvers(candidates: List<Candidate>, fallback: Boolean): List<Candidate> {
        val primary = candidates.filter { (it.kind == "net" || it.kind == "udp") && it.resolver !in YANDEX }
            .map { it.copy(resolver = it.resolver.removeSurrounding("[", "]")) }.distinctBy { it.resolver }
        return if (fallback || primary.isEmpty()) primary + YANDEX.map { Candidate(it, "udp") } else primary
    }

    /** Whether Yandex's resolvers joined masterdns's for [node] on this network (its tunnel failed without them). */
    private fun masterdnsFallback(node: RayNode?): Boolean {
        val network = RayChannel.network() ?: return false
        return node != null && RayChannel.dnsPick(node.spa.nodeId, network, MASTERDNS)?.get("fallback")?.asBoolean == true
    }

    private fun masterdnsFor(node: RayNode?) =
        masterdnsResolvers(networkCandidates() + COUNTRY[country()].orEmpty(), masterdnsFallback(node))

    /** The network's pick of a dnstt resolver for [node]'s tunnel while it holds; not a pick of [NONE]. */
    private fun heldPick(node: RayNode?): JsonObject? {
        val network = RayChannel.network() ?: return null
        if (node == null) return null
        return RayChannel.dnsPick(node.spa.nodeId, network, DNSTT)?.takeIf { it.has("resolver") }
    }

    /**
     * The resolver for the tunnel's config: dnstt the network's pick while it holds, else the default of before;
     * masterdns its list for this network ([masterdnsResolvers]).
     */
    fun resolverFor(node: RayNode?, engine: String): String {
        if (engine != DNSTT) return masterdnsFor(node).joinToString("\n") { it.resolver }
        val first = heldPick(node)?.get("resolver")?.asString ?: return DNSTT_DEFAULT
        // https://, tls://, tcp://, udp:// as picked; a bare address is UDP's
        return if ("://" in first) first else "udp://$first"
    }

    /**
     * The engine and zones of [bean]'s DNS tunnel when its resolver is left to the client, else null: the main zone
     * first, then its spares (dnszones-v1).
     */
    private fun autoTunnel(bean: AbstractBean): Pair<String, List<String>>? {
        val tunnel = DnsTunnel.of(bean)
        if (!tunnel.enabled || tunnel.resolver.trim() != AUTO) return null
        return tunnel.zones.takeIf { it.isNotEmpty() }?.let { tunnel.engine to it }
    }

    /** The zones of [bean]'s dnstt tunnel when its resolver is the client's to pick (and probe), else null. */
    private fun probedZones(bean: AbstractBean): List<String>? =
        autoTunnel(bean)?.takeIf { it.first == DNSTT }?.second

    /**
     * Whether [tunnel]'s zone follows this network's pick (dnszones-v1 §3): masterdns's always (its failures move it),
     * dnstt's only while its resolver is the client's (the pick was measured through the network's candidates; one set
     * by hand takes the main zone). [tunnel] as the profile has it: in a config its resolver is already replaced.
     */
    internal fun followsPick(tunnel: DnsTunnel) =
        tunnel.spareZones.isNotEmpty() && (tunnel.engine != DNSTT || tunnel.resolver.trim() == AUTO)

    /** The zone of the network's pick [picked] ("" none) for [tunnel] as the profile has it; null the main one. */
    internal fun zoneOf(tunnel: DnsTunnel, picked: String): String? =
        picked.takeIf { followsPick(tunnel) && it in tunnel.zones }

    /**
     * The zone [tunnel] (as the profile has it) goes through on this network (dnszones-v1 §3): dnstt the zone of the
     * network's pick, masterdns the one its failures moved to, while either holds; null, the main one, otherwise.
     */
    fun zoneFor(node: RayNode?, tunnel: DnsTunnel): String? =
        if (node == null || !followsPick(tunnel)) null else zoneOf(tunnel, pickedZone(node, tunnel))

    /**
     * The DNS tunnel a config of [node]'s row runs, from [original] as the profile has it: its zone ([zoneFor]), and a
     * resolver left to the client replaced by [resolver] ([resolverFor]).
     */
    fun forConfig(original: DnsTunnel, node: RayNode?, resolver: () -> String): DnsTunnel =
        configTunnel(original, if (node == null || !followsPick(original)) "" else pickedZone(node, original), resolver)

    /**
     * [forConfig] with the network's pick [picked]: the zone is decided on [original] first, since once replaced the
     * resolver no longer says whether it was the client's (dnstt's zone would always be the main one).
     */
    internal fun configTunnel(original: DnsTunnel, picked: String, resolver: () -> String): DnsTunnel {
        var tunnel = original
        if (original.spareZones.isNotEmpty()) tunnel = tunnel.withZone(zoneOf(original, picked) ?: original.zones.first())
        if (original.resolver.trim() == AUTO) tunnel = tunnel.copy(resolver = resolver())
        return tunnel
    }

    /** the zone of the network's pick for [tunnel], by its main zone, "" none (the main one) */
    private fun pickedZone(node: RayNode?, tunnel: DnsTunnel): String {
        val network = RayChannel.network() ?: return ""
        val main = tunnel.zones.firstOrNull() ?: return ""
        if (node == null) return ""
        return RayChannel.zonePick(node.spa.nodeId, network, kindOf(tunnel.engine), main).orEmpty()
    }

    private fun kindOf(engine: String) = if (engine == DNSTT) DNSTT else MASTERDNS

    /**
     * The number of the zone [profile]'s dnstt tunnel goes through on this network in its [DnsTunnel.zones], 0 the main
     * one, for the summary's obs zone (dnszones-v1 §3): only while a pick holds, and dnstt only.
     */
    fun usedZone(profile: ProxyEntity): Int? {
        val tunnel = DnsTunnel.of(profile.requireBean())
        if (!tunnel.enabled || tunnel.engine != DNSTT || tunnel.resolver.trim() != AUTO) return null
        val node = RayNodes.ownerOf(profile.id)?.first ?: return null
        heldPick(node) ?: return null
        val zone = zoneFor(node, tunnel) ?: return 0
        return tunnel.zones.indexOf(zone).takeIf { it >= 0 }
    }

    /**
     * The kind of the resolver [profile]'s DNS tunnel goes through on this network when the resolver is the client's
     * to pick (what [resolverFor] gives its config now: the pick's kind, else the default's), or null: no such tunnel.
     * For the summary's obs dns, recorded with the probe.
     */
    fun usedKind(profile: ProxyEntity): String? {
        val (engine, _) = autoTunnel(profile.requireBean()) ?: return null
        // masterdns: the kind of the first of its list (the network's own when it has any)
        if (engine != DNSTT) return masterdnsFor(RayNodes.ownerOf(profile.id)?.first).first().kind
        return heldPick(RayNodes.ownerOf(profile.id)?.first)?.get("kind")?.asString ?: "doh"
    }

    /**
     * Probes the candidates for [node]'s dnstt tunnel at its main zone, the first of [zones], and keeps the first that
     * passes a large answer, else the first that passes a small one (the large probe answered small, the resolver's
     * EDNS being under 1206, or lost: the tunnel goes, slower). When no candidate answers there, two at least, the zone
     * is what is cut: its spares in their order the same way (dnszones-v1 §3), one alone saying nothing of the zone.
     * Returns the pick, or null when none answered (kept as a pick of [NONE]).
     */
    suspend fun detect(node: RayNode, network: ChannelNetwork, zones: List<String>, progress: (String) -> Unit = {}): Candidate? {
        var key = node.dnsProbeKey ?: return null
        val hint = RayChannel.hints(node.spa.nodeId, network)?.dns?.get(DNSTT)
        val candidates = candidates(hint)
        // an answer not the node's may be a stale key (dnsprobe-v1 §6): the subscription anew once, and the same
        // candidate again with the key it brings; with the current key such an answer is the resolver's own
        var keyChecked = false
        for ((index, zone) in zones.withIndex()) {
            if (index > 0) {
                if (candidates.size < 2) break
                progress("dns $DNSTT: no resolver answers in the main zone here, the spare zone $index")
            }
            var full: Candidate? = null
            var smallOnly: Candidate? = null
            for (c in candidates) {
                currentCoroutineContext().ensureActive()
                val resolver = if (c.resolver.contains("://")) c.resolver else "udp://${c.resolver}"
                var small = probe(resolver, zone, key, large = false)
                if (small?.get("unclear")?.asBoolean == true && !keyChecked) {
                    keyChecked = true
                    freshKey(node)?.takeIf { it != key }?.let {
                        key = it
                        small = probe(resolver, zone, key, large = false)
                    }
                }
                progress("dns ${c.kind}: ${describe(small)}")
                if (small?.get("ok")?.asBoolean != true) continue
                val large = probe(resolver, zone, key, large = true)
                val passed = large?.get("ok")?.asBoolean == true && large.get("large")?.asBoolean == true
                progress("dns ${c.kind} large: ${if (large?.get("ok")?.asBoolean == true && !passed) "a small answer" else describe(large)}")
                if (passed) {
                    full = c
                    break
                }
                if (smallOnly == null) smallOnly = c
            }
            // the network changed meanwhile: what was measured is neither network's
            if (RayChannel.network()?.key != network.key) return null
            val pick = full ?: smallOnly ?: continue
            record(node, network, pick, smallOnly = full == null)
            // the zone it passed in, for this tunnel's service (by its main zone); the main one forgets a spare
            RayChannel.recordZonePick(node.spa.nodeId, network, DNSTT, zones.first(), zone)
            return pick
        }
        if (RayChannel.network()?.key != network.key) return null
        record(node, network, null, smallOnly = false)
        // no zone passed either: a spare picked before says nothing now, the tunnel and the summary take the main one
        RayChannel.recordZonePick(node.spa.nodeId, network, DNSTT, zones.first(), null)
        return null
    }

    /** What [detectForNode] picked: [kind] null when no resolver answered; [zone] the number of its zone, 0 the main. */
    data class NodePick(val kind: String?, val zone: Int = 0)

    /**
     * Picks the resolver anew for [node]'s dnstt tunnel when its rows leave it to the client (their zone); null when
     * none does (or the node gives no key to probe with).
     */
    suspend fun detectForNode(node: RayNode, network: ChannelNetwork, progress: (String) -> Unit = {}): NodePick? {
        if (node.dnsProbeKey == null) return null
        val zones = node.profileIds.orEmpty().values.firstNotNullOfOrNull { id ->
            SagerDatabase.proxyDao.getById(id)?.requireBean()?.let(::probedZones)
        } ?: return null
        val pick = detect(node, network, zones, progress)
        progress("dns $DNSTT: ${pick?.kind ?: "no resolver answered"}")
        val zone = if (pick == null) null else RayChannel.zonePick(node.spa.nodeId, network, DNSTT, zones.first())
        return NodePick(pick?.kind, zone?.let { zones.indexOf(it) }?.takeIf { it > 0 } ?: 0)
    }

    // a sweep at a time per node, detached: the failover or the start that asks for it does not wait for its probes
    private val sweeping = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()
    private val sweeps = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private fun sweepInBackground(node: RayNode, network: ChannelNetwork, zones: List<String>) {
        val sweep = node.spa.nodeId
        if (!sweeping.add(sweep)) return
        sweeps.launch {
            try {
                detect(node, network, zones)
            } catch (e: Exception) {
                Logs.w("raycontrol ${node.host}: the dnstt resolver: ${e.message}")
            } finally {
                sweeping.remove(sweep)
            }
        }
    }

    /**
     * A config that runs takes [tunnel]'s default resolver for dnstt because no pick holds on this network (the
     * default is Yandex's, the last of the candidates): the pick is made now in the background, and the core restarts
     * with it when it differs (RayWatchdog). Not within the hour after none answered.
     */
    fun pickSoon(node: RayNode?, tunnel: DnsTunnel) {
        if (node?.dnsProbeKey == null || tunnel.engine != DNSTT || tunnel.resolver.trim() != AUTO) return
        val network = RayChannel.network() ?: return
        if (RayChannel.dnsPick(node.spa.nodeId, network, DNSTT) != null) return
        val zones = tunnel.zones.takeIf { it.isNotEmpty() } ?: return
        sweepInBackground(node, network, zones)
    }

    // a pick younger than this is not redone when its tunnel fails: a resolver that answered minutes ago is not why
    private const val RECHECK = 10 * 60L

    /**
     * The row [key] of [node] failed on [network]. When it is a dnstt tunnel whose resolver is the client's, the
     * resolver is picked anew in the background (the held pick, or the default, may be what stopped), unless one was
     * picked in the last [RECHECK] or none answered in the last hour; a masterdns one takes Yandex's resolvers too on
     * this network for a day. Either is for the row's next start.
     */
    fun recheckAfterFailure(node: RayNode, network: ChannelNetwork, key: String) {
        val bean = node.profileIds?.get(key)?.let { SagerDatabase.proxyDao.getById(it) }?.requireBean() ?: return
        val tunnel = DnsTunnel.of(bean)
        if (tunnel.enabled && tunnel.engine != DNSTT) {
            nextMasterdnsZone(node, network, tunnel)
            return
        }
        val (engine, zones) = autoTunnel(bean) ?: return
        if (engine != DNSTT) return
        if (node.dnsProbeKey == null) return
        RayChannel.dnsPick(node.spa.nodeId, network, DNSTT)?.let { held ->
            val age = System.currentTimeMillis() / 1000 - (held.get("at")?.asLong ?: 0)
            if (held.get("kind")?.asString == NONE || age < RECHECK) return
        }
        sweepInBackground(node, network, zones)
    }

    /**
     * masterdns failed on [network] with the subscription fresh: first Yandex's resolvers join its list for a day (an
     * "auto" list), then, failing again with them, the next of its zones for a day (dnszones-v1 §3: masterdns zones
     * answer no probe, its own failure is the sign; never on one resolver's failure, its whole list having failed).
     */
    private fun nextMasterdnsZone(node: RayNode, network: ChannelNetwork, tunnel: DnsTunnel) {
        // the core runs another network's list (a restart for this one's is due): not this list's fault
        if (runningStale()) return
        val held = RayChannel.dnsPick(node.spa.nodeId, network, MASTERDNS)
        val now = System.currentTimeMillis() / 1000
        if (held == null && tunnel.resolver.trim() == AUTO) {
            Logs.i("raycontrol ${node.host}: masterdns fails on this network without Yandex's resolvers, they join for a day")
            RayChannel.recordDnsPick(node.spa.nodeId, network, MASTERDNS, JsonObject().apply {
                addProperty("kind", "udp")
                addProperty("fallback", true)
                addProperty("at", now)
            })
            return
        }
        // this service's zones only, by its main zone: another masterdns service of the node keeps its own
        val zones = tunnel.zones.takeIf { it.size > 1 } ?: return
        val current = zones.indexOf(pickedZone(node, tunnel).ifEmpty { zones.first() }).coerceAtLeast(0)
        if (current + 1 >= zones.size) return
        Logs.i("raycontrol ${node.host}: masterdns fails on this network in zone $current, the spare zone ${current + 1} for a day")
        RayChannel.recordZonePick(node.spa.nodeId, network, MASTERDNS, zones.first(), zones[current + 1])
        RayWatchdog.dnsChanged()
    }

    // what the running core's DNS tunnels left to the client took (ConfigBuilder, a config that runs): node id and
    // engine → their resolvers
    private val running = java.util.concurrent.ConcurrentHashMap<Pair<String, String>, String>()

    // what the running core's DNS tunnels with spare zones took: node id, engine and main zone → the zone of the pick
    // they were built with ("" none), as it was, so a pick of a zone the subscription no longer gives restarts nothing
    // again
    private val runningZones = java.util.concurrent.ConcurrentHashMap<Triple<String, String, String>, String>()

    /** A config that will run is built: what its DNS tunnels take is noted anew ([noteRunning]). */
    fun clearRunning() {
        running.clear()
        runningZones.clear()
    }

    fun noteRunning(node: RayNode?, engine: String, resolver: String) {
        running[(node?.spa?.nodeId ?: "") to engine] = resolver
    }

    /**
     * A config that will run takes [tunnel] (as the profile has it) through a zone of its own (dnszones-v1): the pick
     * it went by is noted, when its zone follows one ([followsPick]).
     */
    fun noteRunningZone(node: RayNode?, tunnel: DnsTunnel) {
        val main = tunnel.zones.firstOrNull() ?: return
        if (!followsPick(tunnel)) return
        runningZones[Triple(node?.spa?.nodeId ?: "", tunnel.engine, main)] = pickedZone(node, tunnel)
    }

    /**
     * Whether a DNS tunnel of the running core would take other resolvers, or another zone, on the network now: its
     * config was built on another (a network's own resolver, a pick, masterdns's list and zone are the network's), and
     * the core wants a restart.
     */
    fun runningStale(): Boolean = running.entries.any { (k, resolver) ->
        resolverFor(k.first.takeIf { it.isNotEmpty() }?.let { RayNodes.get(it) }, k.second) != resolver
    } || runningZones.entries.any { (k, zone) ->
        val node = k.first.takeIf { it.isNotEmpty() }?.let { RayNodes.get(it) }
        val network = RayChannel.network()
        val now = if (node == null || network == null) "" else
            RayChannel.zonePick(node.spa.nodeId, network, kindOf(k.second), k.third).orEmpty()
        now != zone
    }

    /** A probe's result for the progress: answered (the EDNS size the node saw), or why not; never an address. */
    private fun describe(r: JsonObject?): String = when {
        r == null -> "no answer"
        r.get("ok")?.asBoolean == true -> "answers" + (r.get("edns")?.asInt?.let { ", edns $it" } ?: "")
        else -> r.get("error")?.asString ?: "no answer"
    }

    /** The node's probe key after fetching its subscription anew, or null when the fetch failed. */
    private suspend fun freshKey(node: RayNode): String? = try {
        RaySubscription.refresh(node.spa.nodeId, reload = RaySubscription.Reload.LATER)
        RayNodes.get(node.spa.nodeId)?.dnsProbeKey
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Logs.w("raycontrol ${node.host}: the subscription for a fresh DNS probe key: ${e.message}")
        null
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

    /** Keeps the pick, or [NONE] when there is none. */
    private fun record(node: RayNode, network: ChannelNetwork, c: Candidate?, smallOnly: Boolean) {
        if (c == null) Logs.w("raycontrol ${node.host}: no resolver on this network answers for dnstt, the default stays")
        else Logs.i("raycontrol ${node.host}: dnstt on this network goes through a ${c.kind} resolver${if (smallOnly) " (small answers only)" else ""}")
        RayChannel.recordDnsPick(node.spa.nodeId, network, DNSTT, JsonObject().apply {
            if (c == null) {
                addProperty("kind", NONE)
            } else {
                addProperty("resolver", c.resolver)
                addProperty("kind", c.kind)
                addProperty("small_only", smallOnly)
            }
            addProperty("at", System.currentTimeMillis() / 1000)
        })
        // the running core may have taken another resolver for this tunnel: the watchdog compares and restarts
        RayWatchdog.dnsChanged()
    }
}

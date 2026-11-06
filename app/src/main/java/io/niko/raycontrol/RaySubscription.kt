package io.niko.raycontrol

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkerParameters
import androidx.work.multiprocess.RemoteWorkManager
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.GroupType
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.database.GroupManager
import io.niko.database.ProxyEntity
import io.niko.database.ProxyGroup
import io.niko.database.SagerDatabase
import io.niko.fmt.AbstractBean
import io.niko.ktx.Logs
import io.niko.ktx.app
import io.niko.ktx.applyDefaultValues
import io.niko.ktx.readableMessage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libcore.Libcore
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** What a subscription update did. */
data class RaySubscriptionResult(
    val profiles: Int,
    val skipped: Int,
    val clockSkew: Long,
    val spaOverTcp: Boolean,
    // the row the core runs with got other credentials
    val runningChanged: Boolean = false,
    // the core was asked to restart with the new rows
    val restarted: Boolean = false,
) {
    /** spa-v2.md §7: SPA v2 needs the clocks within 30 s */
    val clockWrong get() = abs(clockSkew) > 30
}

/**
 * The raycontrol subscription of a node (subscription-v1.md): the core opens the port with SPA v2, fetches over
 * TLS with X25519MLKEM768 only and the pinned key, checks both node signatures, the node, seq and expiry. The
 * profiles update the node's group in place (matched by service), and the next fetch is scheduled for the next
 * rotation or before the subscription expires.
 */
object RaySubscription {

    /** What to do when the row the core runs with changed. */
    enum class Reload {
        /** restart the core at once: the user asked */
        NOW,

        /** keep running connections; [RayWatchdog] restarts once the old credential fails (a rotation) */
        LATER,

        /** the watchdog fetched because the profile fails: restart at once, as the watchdog's own restart */
        WATCHDOG,
    }

    /** the core's error when the port stayed closed (raysub.ErrNoAnswer) */
    private const val NO_ANSWER = "the node did not answer"

    /**
     * The node itself refused this device or proved to hold other keys: a retry cannot fix it, the registration has
     * to be imported again (raysub.ErrNotFound and the signature checks, made inside the pinned TLS session). A pin
     * mismatch is not one: any TLS middlebox on the network path causes it, so it is retried like other failures.
     */
    private val REFUSED = listOf("does not know this device", "signature does not verify", "signed for another node")

    private fun refused(e: Throwable) = e.message?.let { m -> REFUSED.any { m.contains(it) } } == true

    /**
     * A failed fetch is tried again after this: when the port stayed closed, 15 min doubling to 6 h for each failure in
     * a row on the same network ([failures] counts this one); rarely otherwise. "No answer" is also DNS, the dial
     * and a clock off: every try is ~45 s under a wakelock, also with the VPN off.
     */
    fun retryAfter(e: Throwable, failures: Int): Long =
        if (e.message?.contains(NO_ANSWER) == true) minOf(15 * 60L shl (failures - 1).coerceIn(0, 5), MAX_RETRY) else MAX_RETRY

    private const val MAX_RETRY = 6 * 3600L

    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Fetches and stores the subscription; a failure plans a retry, a refusal stops the schedule. [reschedule] is
     * false only in the scheduled worker, which plans the next run itself.
     *
     * When the row the core runs with changed, [reload] says how the core restarts: at once ([Reload.NOW],
     * [Reload.WATCHDOG]), or ([Reload.LATER]: [RefreshTask], a rotation) only once the old credential stops working,
     * so the running connections stay (niko-r4-note.md §1).
     */
    suspend fun refresh(nodeId: String, reschedule: Boolean = true, reload: Reload = Reload.NOW): RaySubscriptionResult =
        locks.getOrPut(nodeId) { Mutex() }.withLock {
            try {
                refreshLocked(nodeId, reload)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val network = RayChannel.network()?.key
                RayNodes.update(nodeId) {
                    if (it.subscription == null) return@update it
                    val failures = if (it.failedNetwork == network) it.failedFetches + 1 else 1
                    val at = if (refused(e)) RayNodes.STOPPED else System.currentTimeMillis() / 1000 + retryAfter(e, failures)
                    it.copy(refreshAt = at, failedFetches = failures, failedNetwork = network)
                }
                throw e
            } finally {
                if (reschedule) schedule()
            }
        }

    /**
     * The subscription directly, then through the node's relays when the node does not answer (relay-v1 §2: a relay
     * forwards the subscription port; the pin and both signatures stay the node's). A refusal is the node's answer
     * and is not asked again elsewhere. Returns the answer and the relay it came through.
     */
    private fun fetchDirectOrViaRelay(node: RayNode, request: JsonObject): Pair<String, String?> {
        try {
            return Libcore.raycontrolFetchSubscription(request.toString()) to null
        } catch (e: Exception) {
            if (refused(e) || node.relays.isEmpty()) throw e
            for (relay in node.relays) {
                val via = request.deepCopy().apply {
                    addProperty("host", relay.host)
                    add("spa", relay.spaJson(node, RayNodes.deviceKeyHex(node)))
                }
                try {
                    return Libcore.raycontrolFetchSubscription(via.toString()).also {
                        Logs.i("raycontrol ${node.host}: subscription through relay ${relay.id} (directly: ${e.message})")
                    } to relay.id
                } catch (viaError: Exception) {
                    if (refused(viaError)) throw viaError
                    Logs.w("raycontrol ${node.host}: subscription through relay ${relay.id}: ${viaError.message}")
                }
            }
            throw e
        }
    }

    private suspend fun refreshLocked(nodeId: String, reload: Reload): RaySubscriptionResult {
        val node = RayNodes.get(nodeId) ?: throw Exception("raycontrol node $nodeId was removed")
        val info = node.subscription ?: throw Exception("raycontrol node ${node.host}: its registration has no subscription")
        val request = JsonObject().apply {
            addProperty("host", node.host)
            addProperty("port", info.port)
            addProperty("pin", info.pin)
            addProperty("device_id", node.deviceId)
            addProperty("node_id", node.spa.nodeId)
            addProperty("ed25519", info.ed25519)
            addProperty("mldsa65", info.mldsa65)
            addProperty("last_seq", node.subSeq)
            add("spa", node.spaJson().apply { addProperty("device_key", RayNodes.deviceKeyHex(node)) })
        }
        val (raw, viaRelay) = fetchDirectOrViaRelay(node, request)
        val result = JsonParser.parseString(raw).asJsonObject
        val payload = result.getAsJsonObject("payload")
        val now = System.currentTimeMillis() / 1000
        // rotation times are the node's: a device clock off by minutes would switch too early or too late
        val skew = result.get("clock_skew_s")?.asLong ?: 0
        val nodeNow = now + skew
        val parsed = parse(payload, node.services.associate { it.id to it.kind }, nodeNow)

        // the UI and the scheduled worker (`:bg`) may both get here: one writer across the processes
        val refreshAt = maxOf(now + 60, nextRefresh(payload, nodeNow) - skew)
        val stored = RayNodesLock.withLock {
            store(nodeId, payload.get("seq").asLong, refreshAt, skew, parsed.beans, parsed.links, parsed.doh)?.also {
                RayNodes.update(nodeId) { node ->
                    node.copy(
                        probe = parsed.probe, spaCarriers = parsed.carriers, failedFetches = 0, failedNetwork = null,
                        relays = parsed.relays, dnsProbeKey = parsed.dnsProbeKey, subUpdatedAt = now,
                        subExpires = payload.get("expires")?.takeIf { it.isJsonPrimitive }?.asLong?.minus(skew) ?: 0,
                    )
                }
            }
        }
        // the hints are for the network this subscription was asked from: directly, the node saw its address (through
        // a relay it saw the relay's)
        if (viaRelay == null) RayChannel.network()?.let { network ->
            RayChannel.recordHints(nodeId, network, parsed.hints?.let { RayHints.fromJson(it, now) })
        }
        return finish(stored, reload, parsed, skew, spaOverTcp = result.get("spa_over_tcp")?.asBoolean == true)
    }

    /** What a subscription's payload gives the node and its rows ([parse]), without the network or the store. */
    data class Parsed(
        val beans: LinkedHashMap<String, AbstractBean>,
        val links: HashMap<String, RayLinks>,
        val doh: String?,
        val skipped: Int,
        val relays: List<RayRelay>,
        val dnsProbeKey: String?,
        val probe: RayProbeInfo? = null,
        val carriers: List<String>? = null,
        val hints: JsonObject? = null,
    )

    /**
     * The rows of [payload] (profile key → bean and its credentials; DoH as the remote DNS; rows through the relays)
     * and what the node record takes from it; [kinds] are the node's service kinds by id, [nodeNow] the node's clock.
     */
    fun parse(payload: JsonObject, kinds: Map<Int, String>, nodeNow: Long): Parsed {
        val profiles = (payload.get("profiles") as? JsonArray ?: JsonArray()).map { it.asJsonObject }
        var skipped = 0
        val beans = LinkedHashMap<String, AbstractBean>()
        val links = HashMap<String, RayLinks>()
        var doh: String? = null
        // relays in front of the node (relay-v1 §4, a draft): a profile with `via` gets a row through each
        val relays = RayRelay.listFromJson(payload.get("relays") as? JsonArray)
        // dnsprobe-v1: the key that probes the resolvers of the node's DNS tunnels (only for a user with one)
        val dnsProbeKey = (payload.get("dns_probe") as? JsonObject)?.takeIf { it.get("v")?.asInt == 1 }
            ?.get("key")?.takeIf { it.isJsonPrimitive }?.asString
        profiles.zip(profileKeys(profiles)).forEach { (profile, key) ->
            try {
                val pair = linksOf(profile, nodeNow)
                // DoH is not a proxy: it becomes the remote DNS while a profile of the node runs
                val kind = profile.get("svc")?.takeIf { it.isJsonPrimitive }?.asInt?.let { kinds[it] }
                if (isDoh(pair.activeLink, kind)) {
                    if (doh == null) doh = dohUrl(pair.activeLink)
                    return@forEach
                }
                // the spec's schemes only, without sent JSON (RayRows): a row that breaks it is skipped
                val bean = RayRows.parse(pair.activeLink)
                if (pair.name.isNotBlank()) bean.name = pair.name
                bean.applyDefaultValues()
                beans[key] = bean
                links[key] = pair
                for (relay in viaOf(profile).mapNotNull { id -> relays.firstOrNull { it.id == id } }) {
                    val name = "${bean.name} · ${relay.id}"
                    val via = RayLinks(
                        name = name,
                        link = RayRelays.viaLink(pair.link, relay) ?: continue,
                        next = pair.next?.let { RayRelays.viaLink(it, relay) },
                        from = pair.from,
                        nextActive = pair.nextActive,
                    )
                    val viaBean = RayRows.parse(via.activeLink)
                    viaBean.name = name
                    viaBean.applyDefaultValues()
                    beans[RayRelays.viaKey(key, relay.id)] = viaBean
                    links[RayRelays.viaKey(key, relay.id)] = via
                }
            } catch (e: Exception) {
                Logs.w("raycontrol subscription: profile ${profile.get("name")}: ${e.message}")
                skipped++
            }
        }
        // probe v1 only: another version is unknown to this client
        val probe = (payload.get("probe") as? JsonObject)?.let { RayProbeInfo.fromJson(it) }?.takeIf { it.v == 1 }
        // the SPA carriers the node takes (v0.10.1); none = an older node, which gets the whole ladder
        val carriers = ((payload.get("spa") as? JsonObject)?.get("carriers") as? JsonArray)
            ?.mapNotNull { runCatching { it.asString }.getOrNull() }?.takeIf { it.isNotEmpty() }
        return Parsed(beans, links, doh, skipped, relays, dnsProbeKey, probe, carriers, payload.get("hints") as? JsonObject)
    }

    /** After the store: the group, the core's restart where the running row changed, the result for the caller. */
    private suspend fun finish(stored: Stored?, reload: Reload, parsed: Parsed, skew: Long, spaOverTcp: Boolean): RaySubscriptionResult {
        var restarted = false
        if (stored != null) {
            if (stored.groupCreated) GroupManager.iterator { groupAdd(stored.group) }
            GroupManager.postReload(stored.group.id)
            // a running row that is gone cannot wait: the core runs a profile that no longer exists
            when {
                !stored.runningChanged && !stored.runningGone -> {}
                // the watchdog's own restart: it keeps its backoff
                reload == Reload.WATCHDOG -> {
                    RayWatchdog.restart(currentCoroutineContext()[Job])
                    restarted = true
                }
                // a running row that is gone cannot wait: the core runs a profile that no longer exists
                reload == Reload.NOW || stored.runningGone -> {
                    SagerNet.reloadService(force = true)
                    restarted = true
                }

                else -> RayWatchdog.markStale()
            }
        }
        return RaySubscriptionResult(
            profiles = parsed.beans.size,
            skipped = parsed.skipped,
            clockSkew = skew,
            spaOverTcp = spaOverTcp,
            runningChanged = stored?.runningChanged == true,
            restarted = restarted,
        )
    }

    private class Stored(val group: ProxyGroup, val groupCreated: Boolean, val runningChanged: Boolean, val runningGone: Boolean)

    /**
     * Writes the profiles into the node's group (under [RayNodesLock]): a known key updates its row (the id, the
     * selection and the user's own tweaks stay), a new key adds a row, rows of keys gone are deleted. Rows the user
     * added to the group by hand stay.
     *
     * Crash safety: the rows and the node record are in two databases. The node record is written last inside the
     * rows' transaction and keeps the rows to delete as `stale:` keys until they are gone, so a crash at any point
     * leaves no row of the node unknown to it: the next refresh adds what is missing and deletes the stale rows.
     *
     * Returns null when another process already stored a newer subscription.
     */
    private fun store(
        nodeId: String, seq: Long, refreshAt: Long, skew: Long, beans: Map<String, AbstractBean>, links: Map<String, RayLinks>,
        doh: String?,
    ): Stored? {
        val node = RayNodes.get(nodeId) ?: throw Exception("raycontrol node $nodeId was removed")
        if (seq < node.subSeq) return null
        var group = SagerDatabase.groupDao.getById(node.groupId)
        val groupCreated = group == null
        if (group == null) {
            group = ProxyGroup(name = "raycontrol · ${node.host}", type = GroupType.BASIC).applyDefaultValues()
            group.userOrder = SagerDatabase.groupDao.nextOrder() ?: 1
            group.id = SagerDatabase.groupDao.createGroup(group)
        }
        // a 1.13.0 record (no ids at all; an empty map is a subscription without profiles): that version replaced
        // the whole group, so its rows are this node's, matched by name once
        val known = node.profileIds ?: if (groupCreated) emptyMap() else legacyIds(group.id, beans)
        val rows = SagerDatabase.proxyDao.getByGroup(group.id).associateBy { it.id }
        val ids = LinkedHashMap<String, Long>()
        val changed = HashSet<Long>()
        SagerDatabase.instance.runInTransaction {
            val toUpdate = ArrayList<ProxyEntity>()
            var order = 1L
            for ((key, bean) in beans) {
                val row = known[key]?.let { rows[it] }
                if (row == null) {
                    ids[key] = SagerDatabase.proxyDao.addProxy(ProxyEntity(groupId = group.id, userOrder = order).apply { putBean(bean) })
                } else {
                    val old = row.requireBean()
                    keepUserTweaks(old, bean)
                    val beanChanged = old != bean
                    if (beanChanged) changed += row.id
                    if (beanChanged || row.userOrder != order) {
                        row.putBean(bean)
                        row.userOrder = order
                        toUpdate.add(row)
                    }
                    ids[key] = row.id
                }
                order++
            }
            if (toUpdate.isNotEmpty()) SagerDatabase.proxyDao.updateProxy(toUpdate)
            val stale = known.values.filter { it !in ids.values && it in rows }.associateBy { "stale:$it" }
            RayNodes.update(nodeId) {
                it.copy(
                    subSeq = seq, groupId = group.id, profileIds = ids + stale, refreshAt = refreshAt,
                    links = links.filterKeys { k -> k in ids }, clockSkew = skew, doh = doh,
                )
            }
        }
        val gone = known.values.filter { it !in ids.values }.mapNotNull { rows[it] }
        if (gone.isNotEmpty()) {
            SagerDatabase.proxyDao.deleteProxy(gone)
            RayNodes.update(nodeId) { it.copy(profileIds = ids) }
            changed += gone.map { it.id }
        }
        val selected = DataStore.selectedProxy
        if (selected in gone.map { it.id }) {
            // the selected service left the subscription: keep a profile of this node selected
            DataStore.selectedProxy = ids.values.firstOrNull() ?: 0L
        }
        val running = DataStore.currentProfile
        val runningChanged = selected in changed || running in changed
        val runningGone = gone.any { it.id == running || it.id == selected }
        return Stored(group, groupCreated, runningChanged, runningGone)
    }

    private fun legacyIds(groupId: Long, beans: Map<String, AbstractBean>): Map<String, Long> {
        val rows = SagerDatabase.proxyDao.getByGroup(groupId)
        val byName = rows.groupBy { it.requireBean().name }
        val ids = HashMap<String, Long>()
        val used = HashSet<Long>()
        for ((key, bean) in beans) {
            byName[bean.name]?.firstOrNull { it.id !in used }?.let { ids[key] = it.id; used += it.id }
        }
        // the rest are stale rows of the node: keys nothing matches, so store() deletes them
        rows.filter { it.id !in used }.forEach { ids["stale:${it.id}"] = it.id }
        return ids
    }

    /**
     * The worker could not fetch at a rotation: switch the rows whose `next` is due by the node's clock to it from
     * what the last subscription carried. Returns whether the running row changed.
     */
    suspend fun applyDue(nodeId: String): Boolean {
        val node = RayNodes.get(nodeId) ?: return false
        val nodeNow = System.currentTimeMillis() / 1000 + node.clockSkew
        var running = false
        for ((key, l) in node.links) {
            if (l.next != null && !l.nextActive && nodeNow >= l.from) running = setActive(nodeId, key, true) || running
        }
        return running
    }

    /**
     * The running row of the node fails and the subscription could not be fetched: switch it to its other
     * credential, `link` ↔ `next` (niko-r4-note.md §1 p. 2). Only rows in a rotation have one. Returns whether the
     * row changed; the caller restarts the core.
     */
    suspend fun useAlternative(nodeId: String, key: String): Boolean {
        val node = RayNodes.get(nodeId) ?: return false
        val l = node.links[key] ?: return false
        if (l.next == null) return false
        // `next` is not accepted before `from` (REALITY keys): only from shortly before it, for a clock behind
        val nodeNow = System.currentTimeMillis() / 1000 + node.clockSkew
        if (!l.nextActive && nodeNow < l.from - 120) return false
        return setActive(nodeId, key, !l.nextActive)
    }

    /** Puts `next` ([nextActive]) or `link` into the key's row. Returns whether the running row changed. */
    private suspend fun setActive(nodeId: String, key: String, nextActive: Boolean): Boolean {
        val l = RayNodes.get(nodeId)?.links?.get(key) ?: return false
        val target = l.copy(nextActive = nextActive)
        val bean = try {
            RayRows.parse(target.activeLink)
        } catch (e: Exception) {
            null
        } ?: return false
        if (target.name.isNotBlank()) bean.name = target.name
        bean.applyDefaultValues()
        return locks.getOrPut(nodeId) { Mutex() }.withLock {
            RayNodesLock.withLock {
                val node = RayNodes.get(nodeId) ?: return@withLock false
                // a refresh meanwhile replaced the links: theirs win
                if (node.links[key] != l) return@withLock false
                val row = node.profileIds?.get(key)?.let { SagerDatabase.proxyDao.getById(it) } ?: return@withLock false
                SagerDatabase.instance.runInTransaction {
                    keepUserTweaks(row.requireBean(), bean)
                    row.putBean(bean)
                    SagerDatabase.proxyDao.updateProxy(row)
                    RayNodes.update(nodeId) { it.copy(links = it.links + (key to target)) }
                }
                row.id == DataStore.selectedProxy || row.id == DataStore.currentProfile
            }
        }.also { if (it) Logs.i("raycontrol: profile $key of $nodeId now uses ${if (nextActive) "next" else "link"}") }
    }

    /** settings the user set on a profile that the subscription link does not carry */
    private fun keepUserTweaks(old: AbstractBean, new: AbstractBean) {
        new.customOutboundJson = old.customOutboundJson
        new.customConfigJson = old.customConfigJson
        new.tlsFragment = old.tlsFragment
        new.udpPrelude = old.udpPrelude
        new.udpPreludeSni = old.udpPreludeSni
        new.udpPreludeTtl = old.udpPreludeTtl
        new.udpReorderInitial = old.udpReorderInitial
    }

    /** Stable keys of the profiles: the service id, `svc#n` for its n-th profile when a service has several. */
    fun profileKeys(profiles: List<JsonObject>): List<String> {
        val seen = HashMap<String, Int>()
        return profiles.map {
            val svc = it.get("svc")?.takeIf { s -> s.isJsonPrimitive }?.asString ?: "-"
            val n = seen.merge(svc, 1, Int::plus)!!
            if (n == 1) svc else "$svc#$n"
        }
    }

    /** A DoH profile of the subscription (subscription-v1.md §4): the service kind, or the RFC 8484 path. */
    fun isDoh(link: String, kind: String?): Boolean =
        kind == "doh" || (link.startsWith("https://") && link.toHttpUrlOrNull()?.encodedPath?.startsWith("/dns-query") == true)

    /** The DoH URL to query: the link without `niko-*` parameters and the name (niko-r3-note.md §2 p. 2). */
    fun dohUrl(link: String): String {
        val url = link.toHttpUrlOrNull() ?: throw IllegalArgumentException("DoH: not a URL")
        require(url.isHttps) { "DoH: not https" }
        return url.newBuilder().apply {
            url.queryParameterNames.filter { it.startsWith("niko-") }.forEach { removeAllQueryParameters(it) }
            fragment(null)
        }.build().toString()
    }

    /** rotation (R4): from `next.from` on (the node's clock), the next credential replaces the current one */
    fun currentLink(profile: JsonObject, nodeNow: Long) = linksOf(profile, nodeNow).activeLink

    /** The relays a profile also goes through (its `via`). */
    fun viaOf(profile: JsonObject): List<String> = (profile.get("via") as? JsonArray)
        ?.mapNotNull { runCatching { it.asString }.getOrNull()?.takeIf { id -> id.isNotBlank() } }?.distinct().orEmpty()

    fun linksOf(profile: JsonObject, nodeNow: Long): RayLinks {
        val next = profile.get("next") as? JsonObject
        val from = next?.get("from")?.asLong ?: 0
        return RayLinks(
            name = profile.get("name")?.takeIf { it.isJsonPrimitive }?.asString.orEmpty(),
            link = profile.get("link").asString,
            next = next?.get("link")?.asString,
            from = from,
            nextActive = next != null && nodeNow >= from,
        )
    }

    /**
     * When to fetch again: at the earliest rotation still ahead, or once three quarters of the subscription's life
     * have passed, so a renewed one arrives before it expires. Never sooner than a minute from now.
     */
    fun nextRefresh(payload: JsonObject, now: Long): Long {
        val issued = payload.get("issued")?.asLong ?: now
        val expires = payload.get("expires").asLong
        val renew = issued + (expires - issued) * 3 / 4
        val rotation = (payload.get("profiles") as? JsonArray ?: JsonArray()).mapNotNull {
            (it.asJsonObject.get("next") as? JsonObject)?.get("from")?.asLong?.takeIf { from -> from > now }
        }.minOrNull()
        return maxOf(now + 60, minOf(renew, rotation ?: Long.MAX_VALUE))
    }

    private const val WORK_NAME = "RaycontrolSubscription"

    /**
     * At start: nodes whose fetch was never planned (imported by 1.13.0, or before their first fetch) are due now.
     */
    fun startup() {
        val now = System.currentTimeMillis() / 1000
        val due = RayNodes.list().filter { it.subscription != null && it.refreshAt == 0L }
        due.forEach { n -> RayNodes.update(n.spa.nodeId) { if (it.refreshAt == 0L) it.copy(refreshAt = now) else it } }
        if (due.isNotEmpty()) schedule()
    }

    /** Plans the next scheduled fetch for the earliest node due; none due cancels it. */
    /**
     * The service saw another network ([network], its key): a fetch that failed on a different one is due within a
     * minute and its backoff starts over there. Called from the watchdog, which limits how often per network.
     */
    fun networkChanged(network: String?) {
        val now = System.currentTimeMillis() / 1000
        val due = RayNodes.list().filter {
            it.subscription != null && it.failedFetches > 0 && it.failedNetwork != network &&
                    it.refreshAt != RayNodes.STOPPED && it.refreshAt > now + 60
        }
        if (due.isEmpty()) return
        for (node in due) {
            RayNodes.update(node.spa.nodeId) {
                if (it.failedFetches == 0 || it.refreshAt == RayNodes.STOPPED) it else it.copy(refreshAt = now + 30 + (0L..30L).random())
            }
        }
        schedule()
    }

    fun schedule(fromWorker: Boolean = false) {
        val at = RayNodes.list().filter { it.subscription != null && it.refreshAt > 0 }.minOfOrNull { it.refreshAt }
        val manager = RemoteWorkManager.getInstance(app)
        if (at == null) {
            if (!fromWorker) manager.cancelUniqueWork(WORK_NAME)
            return
        }
        val delay = (at - System.currentTimeMillis() / 1000).coerceAtLeast(0)
        val request = OneTimeWorkRequest.Builder(RefreshTask::class.java)
            .setInitialDelay(delay, TimeUnit.SECONDS)
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .build()
        // REPLACE would cancel the worker that is calling this; it runs after it instead
        val policy = if (fromWorker) ExistingWorkPolicy.APPEND_OR_REPLACE else ExistingWorkPolicy.REPLACE
        manager.enqueueUniqueWork(WORK_NAME, policy, request)
    }

    class RefreshTask(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
        override suspend fun doWork(): Result {
            val now = System.currentTimeMillis() / 1000
            for (node in RayNodes.list()) {
                if (node.subscription == null || node.refreshAt <= 0 || node.refreshAt > now) continue
                try {
                    refresh(node.spa.nodeId, reschedule = false, reload = Reload.LATER)
                } catch (e: Exception) {
                    // refresh() planned the retry; a rotation due now still happens from the stored `next`
                    Logs.w("raycontrol subscription ${node.host}: ${e.message}")
                    if (applyDue(node.spa.nodeId)) RayWatchdog.markStale()
                }
            }
            // admin devices: the client key changes every 90 days (api-v1.md §6)
            for (node in RayNodes.list()) {
                if (!node.isAdmin) continue
                try {
                    RayApi.rotateIfDue(node)
                } catch (e: Exception) {
                    Logs.w("raycontrol ${node.host}: client key rotation: ${e.message}")
                }
            }
            schedule(fromWorker = true)
            return Result.success()
        }
    }

    /** One line for the user: what was imported, and a warning when the clocks disagree. */
    fun describe(context: Context, node: RayNode, r: RaySubscriptionResult) = buildString {
        append(context.getString(R.string.raycontrol_sub_updated, node.host, r.profiles))
        if (r.skipped > 0) append(" ").append(context.getString(R.string.raycontrol_sub_skipped, r.skipped))
        if (r.clockWrong) append(" ").append(context.getString(R.string.raycontrol_clock_wrong, r.clockSkew))
    }

    /**
     * A refresh the user started: the line to show, success or failure. A closed port gets the hint that a wrong
     * clock is a likely cause. [context] is the application context: the screen may be gone when this returns.
     */
    suspend fun refreshForUser(context: Context, node: RayNode): String = try {
        describe(context, node, refresh(node.spa.nodeId))
    } catch (e: Exception) {
        Logs.w(e)
        buildString {
            append(context.getString(R.string.raycontrol_sub_failed, e.readableMessage))
            if (e.message?.contains(NO_ANSWER) == true) append(" ").append(context.getString(R.string.raycontrol_clock_hint))
            if (refused(e)) append(" ").append(context.getString(R.string.raycontrol_sub_reimport))
        }
    }
}

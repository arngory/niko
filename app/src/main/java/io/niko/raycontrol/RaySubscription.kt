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
import io.niko.ktx.parseProxies
import io.niko.ktx.readableMessage
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libcore.Libcore
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/** What a subscription update did. */
data class RaySubscriptionResult(val profiles: Int, val skipped: Int, val clockSkew: Long, val spaOverTcp: Boolean) {
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

    /** the core's error when the port stayed closed (raysub.ErrNoAnswer) */
    private const val NO_ANSWER = "the node did not answer"

    /** a failed fetch is tried again after this: soon when the port stayed closed, rarely when the node said no */
    private fun retryAfter(e: Throwable) = if (e.message?.contains(NO_ANSWER) == true) 15 * 60L else 6 * 3600L

    private val locks = ConcurrentHashMap<String, Mutex>()

    /**
     * Fetches and stores the subscription; a failure plans a retry. [reschedule] is false only in the scheduled
     * worker, which plans the next run itself.
     */
    suspend fun refresh(nodeId: String, reschedule: Boolean = true): RaySubscriptionResult =
        locks.getOrPut(nodeId) { Mutex() }.withLock {
            try {
                refreshLocked(nodeId)
            } catch (e: Exception) {
                val now = System.currentTimeMillis() / 1000
                RayNodes.update(nodeId) { if (it.subscription == null) it else it.copy(refreshAt = now + retryAfter(e)) }
                throw e
            } finally {
                if (reschedule) schedule()
            }
        }

    private suspend fun refreshLocked(nodeId: String): RaySubscriptionResult {
        // read under the lock: another refresh or a re-import may have changed the node
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
            add("spa", node.spa.toJson().apply { addProperty("device_key", RayNodes.deviceKeyHex(node)) })
        }
        val result = JsonParser.parseString(Libcore.raycontrolFetchSubscription(request.toString())).asJsonObject
        val payload = result.getAsJsonObject("payload")
        val now = System.currentTimeMillis() / 1000
        val profiles = (payload.get("profiles") as? JsonArray ?: JsonArray()).map { it.asJsonObject }

        var skipped = 0
        val beans = LinkedHashMap<String, AbstractBean>()
        profiles.zip(profileKeys(profiles)).forEach { (profile, key) ->
            try {
                val bean = parseProxies(currentLink(profile, now)).firstOrNull()
                if (bean == null) {
                    skipped++
                } else {
                    profile.get("name")?.asString?.takeIf { it.isNotBlank() }?.let { bean.name = it }
                    beans[key] = bean
                }
            } catch (e: Exception) {
                Logs.w("raycontrol subscription: profile ${profile.get("name")}: ${e.message}")
                skipped++
            }
        }

        val group = SagerDatabase.groupDao.getById(node.groupId) ?: GroupManager.createGroup(
            ProxyGroup(name = "raycontrol · ${node.host}", type = GroupType.BASIC)
        )
        // 1.13.0 replaced the whole group and kept no ids: its rows are this node's, matched by name once
        val known = node.profileIds.ifEmpty {
            if (node.groupId == 0L) emptyMap() else legacyIds(group.id, beans)
        }
        val (ids, selectedChanged) = store(group.id, known, beans)
        GroupManager.postReload(group.id)

        RayNodes.update(nodeId) {
            it.copy(subSeq = payload.get("seq").asLong, groupId = group.id, profileIds = ids, refreshAt = nextRefresh(payload, now))
        }
        // the running connection uses the old credential of the selected profile
        if (selectedChanged && DataStore.serviceState.started) SagerNet.reloadService()
        return RaySubscriptionResult(
            profiles = beans.size,
            skipped = skipped,
            clockSkew = result.get("clock_skew_s")?.asLong ?: 0,
            spaOverTcp = result.get("spa_over_tcp")?.asBoolean == true,
        )
    }

    /**
     * Writes the profiles into the group: a known key updates its row (the id, the selection and the user's own
     * tweaks stay), a new key adds a row, rows of keys gone from the subscription are deleted. Returns the new
     * key → id map and whether the selected profile changed.
     */
    private fun store(groupId: Long, known: Map<String, Long>, beans: Map<String, AbstractBean>): Pair<Map<String, Long>, Boolean> {
        val selected = DataStore.selectedProxy
        val rows = SagerDatabase.proxyDao.getByGroup(groupId).associateBy { it.id }
        val ids = LinkedHashMap<String, Long>()
        val toUpdate = ArrayList<ProxyEntity>()
        var selectedChanged = false
        var order = 1L
        for ((key, bean) in beans) {
            val row = known[key]?.let { rows[it] }
            if (row == null) {
                bean.applyDefaultValues()
                val entity = ProxyEntity(groupId = groupId, userOrder = order).apply { putBean(bean) }
                ids[key] = SagerDatabase.proxyDao.addProxy(entity)
            } else {
                bean.applyDefaultValues()
                keepUserTweaks(row.requireBean(), bean)
                if (row.requireBean() != bean || row.userOrder != order) {
                    if (row.id == selected && row.requireBean() != bean) selectedChanged = true
                    row.putBean(bean)
                    row.userOrder = order
                    toUpdate.add(row)
                }
                ids[key] = row.id
            }
            order++
        }
        if (toUpdate.isNotEmpty()) SagerDatabase.proxyDao.updateProxy(toUpdate)
        // rows of this node only: profiles the user added to the group by hand stay
        val gone = known.values.filter { it !in ids.values }.mapNotNull { rows[it] }
        if (gone.isNotEmpty()) SagerDatabase.proxyDao.deleteProxy(gone)
        if (gone.any { it.id == selected }) {
            // the selected service left the subscription: keep a profile of this node selected
            DataStore.selectedProxy = ids.values.firstOrNull() ?: 0L
            selectedChanged = true
        }
        return ids to selectedChanged
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

    /** rotation (R4): from `next.from` on, the next credential replaces the current one */
    fun currentLink(profile: JsonObject, now: Long): String {
        val next = profile.get("next") as? JsonObject
        return if (next != null && now >= next.get("from").asLong) next.get("link").asString else profile.get("link").asString
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

    /** Plans the next scheduled fetch for the earliest node due; none due cancels it. */
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
                    refresh(node.spa.nodeId, reschedule = false)
                } catch (e: Exception) {
                    // refresh() planned the retry
                    Logs.w("raycontrol subscription ${node.host}: ${e.message}")
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
        }
    }
}

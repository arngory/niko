package io.niko.raycontrol

import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.ktx.Logs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Keeps a connection to a raycontrol node working across credential changes (raycontrol niko-r4-note.md §1). It
 * runs in the service process while the core runs a profile of a node subscription, and checks the running profile
 * with the connection test (through the core's main outbound):
 *
 * - after a scheduled rotation the core keeps the old credential, so running connections stay ([markStale]); once
 *   the test fails the core restarts with the new one;
 * - when the test fails twice and nothing is pending, the subscription is fetched at once (it does not depend on the
 *   profile: own port, SPA, pinned key) and the core restarts if the profile changed; if the fetch fails, the
 *   profile switches to its other credential (`link` ↔ `next`), if it has one.
 *
 * Tests run 20 s after the start, 15 s after a network change, every 10 min, and every 30 s for 3 min after a
 * rotation; fetches after failures back off from 5 min to 1 h, and start over on a new network. Without a network
 * nothing is tested: a failure there says nothing about the node's credentials.
 */
object RayWatchdog {

    private const val PERIOD = 10 * 60_000L
    private const val FIRST_BACKOFF = 5 * 60_000L

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var job: Job? = null
    private val wake = Channel<Long>(Channel.CONFLATED)

    // the running core holds an older credential than the profile row
    @Volatile
    private var stale = false

    @Volatile
    private var fastUntil = 0L

    @Volatile
    private var online = true

    @Volatile
    private var failures = 0

    @Volatile
    private var recoverAt = 0L

    @Volatile
    private var backoff = FIRST_BACKOFF

    /** The core connected: [probe] tests the running profile and returns whether it works. */
    @Synchronized
    fun start(probe: suspend () -> Boolean) {
        job?.cancel()
        // the core just started with the current rows
        stale = false
        failures = 0
        job = scope.launch { loop(probe) }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
    }

    /** A test in [delayMs]. */
    fun poke(delayMs: Long) {
        wake.trySend(delayMs)
    }

    /** The default network appeared or went away (the service's network listener). */
    fun network(available: Boolean) {
        if (available == online) return
        online = available
        if (available) networkChanged()
    }

    /** Another network: what failed on the old one says nothing here, test soon and fetch without waiting. */
    fun networkChanged() {
        failures = 0
        recoverAt = 0
        backoff = FIRST_BACKOFF
        poke(15_000L)
    }

    /** A rotation changed the running row without a restart: restart once the old credential fails. */
    fun markStale() {
        stale = true
        fastUntil = System.currentTimeMillis() + 3 * 60_000L
        poke(15_000L)
    }

    private suspend fun loop(probe: suspend () -> Boolean) {
        var wait = 20_000L
        while (true) {
            // a poke sets a new delay; the test runs when the delay passes without one
            val poked = withTimeoutOrNull(wait) { wake.receive() }
            wait = poked ?: try {
                step(probe)
            } catch (e: Exception) {
                Logs.w(e)
                PERIOD
            }
        }
    }

    private suspend fun step(probe: suspend () -> Boolean): Long {
        if (!online) return PERIOD
        // stale without an owner: the running row left the subscription; the core still runs it
        val owner = RayNodes.ownerOf(DataStore.currentProfile)
        if (owner == null && !stale) return PERIOD
        val now = System.currentTimeMillis()
        val ok = probe()
        // stopped meanwhile (a restart): this result belongs to the old core
        currentCoroutineContext().ensureActive()
        if (ok) {
            failures = 0
            backoff = FIRST_BACKOFF
            return if (now < fastUntil) 30_000L else PERIOD
        }
        failures++
        if (stale) {
            // the old credential stopped working: the row already holds the new one
            Logs.i("raycontrol: the running credential fails, restarting with the stored one")
            stale = false
            SagerNet.reloadService()
            return PERIOD
        }
        val (node, key) = owner ?: return PERIOD
        if (failures < 2) return 15_000L
        if (now < recoverAt) return minOf(PERIOD, recoverAt - now)
        recoverAt = now + backoff
        backoff = minOf(backoff * 2, 60 * 60_000L)
        recover(node, key)
        return 60_000L
    }

    private suspend fun recover(node: RayNode, key: String) {
        val nodeId = node.spa.nodeId
        Logs.i("raycontrol ${node.host}: the profile does not work, fetching the subscription")
        try {
            // restarts the core itself when the running row changed
            if (RaySubscription.refresh(nodeId).runningChanged) return
        } catch (e: Exception) {
            Logs.w("raycontrol ${node.host}: subscription: ${e.message}")
            if (RaySubscription.useAlternative(nodeId, key)) SagerNet.reloadService()
        }
    }
}

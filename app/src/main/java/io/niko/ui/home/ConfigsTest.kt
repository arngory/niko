package io.niko.ui.home

import io.niko.aidl.ISagerNetService
import io.niko.bg.proto.UrlTest
import io.niko.database.DataStore
import io.niko.database.GroupManager
import io.niko.database.ProfileManager
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase
import io.niko.ktx.Logs
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.plugin.PluginManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A group's URL test from the configs' sheet (D-MAIN-12), the old list's test without its dialog. Held by the app, not
 * the activity: a rotation or the sheet closed keeps it running and its results. Each result is shown as it comes
 * ([results], over the rows the sheet read); all are stored at the end in one transaction, as the old dialog did.
 */
object ConfigsTest {

    /** A profile's tested ping, or its failure. */
    data class Result(val pingMs: Int?, val unreachable: Boolean)

    /** the group under test (one test at a time, the old list's included: DataStore.runningTest) */
    val testing = MutableStateFlow<Set<Long>>(emptySet())

    /** the results of the test running or just stored, by profile, until the rows are read again (taken) */
    val results = MutableStateFlow<Map<Long, Result>>(emptyMap())

    /** counts the tests stored: the main screen takes their results and reads its rows again */
    val stored = MutableStateFlow(0)

    private var job: Job? = null
    private var service: ISagerNetService? = null

    /** Tests [groupId]'s profiles; false when a test runs already. The tests run in [service]'s process when it is up. */
    @Synchronized
    fun start(groupId: Long, service: ISagerNetService?): Boolean {
        if (DataStore.runningTest) return false
        DataStore.runningTest = true
        this.service = service
        results.value = emptyMap()
        testing.value = setOf(groupId)
        job = runOnDefaultDispatcher { run(groupId, service) }
        return true
    }

    /** Stops the test: the results so far are stored, the tests held on the service's Binder threads end there too. */
    @Synchronized
    fun stop() {
        job?.cancel() ?: return
        service?.let { runCatching { it.cancelTests() } }
    }

    /**
     * The results stored and read again (or the sheet closed): they go, so that they never stand over what another
     * test, an update or an edit writes later. Nothing while a test runs: its results are not stored yet.
     */
    fun forget() {
        if (testing.value.isEmpty()) results.value = emptyMap()
    }

    private suspend fun run(groupId: Long, service: ISagerNetService?) {
        val tested = ConcurrentLinkedQueue<ProxyEntity>()
        try {
            val profiles = ConcurrentLinkedQueue(SagerDatabase.proxyDao.getByGroup(groupId))
            val workers = DataStore.connectionTestConcurrent.let {
                if (service != null) minOf(it, UrlTest.inServiceAtOnce) else it
            }
            coroutineScope {
                repeat(workers) {
                    launch(Dispatchers.IO) {
                        val urlTest = UrlTest()
                        // the service's test is a blocking Binder call that does not suspend: the stop is seen here,
                        // between two tests, and after one (its result, come after the stop, is not shown)
                        while (isActive) {
                            val profile = profiles.poll() ?: break
                            try {
                                profile.ping = urlTest.doTest(profile, service)
                                profile.status = 1
                                profile.error = null
                            } catch (e: PluginManager.PluginNotFoundException) {
                                profile.status = 2
                                profile.error = e.readableMessage
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                profile.status = 3
                                profile.error = e.readableMessage
                            }
                            if (!isActive) break
                            tested += profile
                            val result = Result(profile.ping.takeIf { profile.status == 1 && it > 0 }, profile.status > 1)
                            results.update { it + (profile.id to result) }
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            // stopped: what was tested is stored below
        } catch (e: Exception) {
            Logs.w(e)
        } finally {
            withContext(NonCancellable) {
                try {
                    ProfileManager.updateTestResult(tested.toList())
                    GroupManager.postReload(groupId)
                } catch (e: Exception) {
                    Logs.w(e)
                }
                synchronized(this@ConfigsTest) {
                    job = null
                    this@ConfigsTest.service = null
                }
                DataStore.runningTest = false
                testing.value = emptySet()
                stored.update { it + 1 }
            }
        }
    }
}

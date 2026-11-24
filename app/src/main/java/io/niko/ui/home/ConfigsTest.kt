package io.niko.ui.home

import android.os.SystemClock
import io.niko.R
import io.niko.SagerNet
import io.niko.aidl.ISagerNetService
import io.niko.bg.proto.UrlTest
import io.niko.database.DataStore
import io.niko.database.GroupManager
import io.niko.database.ProfileManager
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase
import io.niko.ktx.Logs
import io.niko.ktx.app
import io.niko.ktx.isIpAddress
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
import okhttp3.internal.closeQuietly
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * A group's test from the configs' sheet (D-MAIN-12, D-PROFILE-06), the old list's URL test and TCP ping without their
 * dialog. Held by the app, not
 * the activity: a rotation or the sheet closed keeps it running and its results. Each result is shown as it comes
 * ([results], over the rows the sheet read); all are stored at the end in one transaction, as the old dialog did.
 */
object ConfigsTest {

    /** A profile's tested ping, or its failure and what it said. */
    data class Result(val pingMs: Int?, val unreachable: Boolean, val error: String? = null)

    /** What is measured: a request through the profile, or a TCP connection to its server from the device. */
    enum class Mode { URL, TCP }

    /** Why a TCP connection failed, by the socket's message. */
    enum class TcpFailure { TIMEOUT, REFUSED, UNREACHABLE, OTHER }

    fun tcpFailure(message: String) = when {
        !message.contains("failed:") -> TcpFailure.TIMEOUT
        message.contains("ECONNREFUSED") -> TcpFailure.REFUSED
        message.contains("ENETUNREACH") -> TcpFailure.UNREACHABLE
        else -> TcpFailure.OTHER
    }

    /** the group under test (one test at a time: DataStore.runningTest) */
    val testing = MutableStateFlow<Set<Long>>(emptySet())

    /** the results of the test running or just stored, by profile, until the rows are read again (taken) */
    val results = MutableStateFlow<Map<Long, Result>>(emptyMap())

    /** how many profiles the running test takes (0: none runs); those tested so far are in [results] */
    val total = MutableStateFlow(0)

    /** counts the tests stored: the main screen takes their results and reads its rows again */
    val stored = MutableStateFlow(0)

    private var job: Job? = null
    private var service: ISagerNetService? = null

    /**
     * Tests [groupId]'s profiles; false when a test runs already. The URL tests run in [service]'s process when it is
     * up; the TCP pings always here.
     */
    @Synchronized
    fun start(groupId: Long, service: ISagerNetService?, mode: Mode = Mode.URL): Boolean {
        if (DataStore.runningTest) return false
        DataStore.runningTest = true
        val inService = service.takeIf { mode == Mode.URL }
        this.service = inService
        results.value = emptyMap()
        testing.value = setOf(groupId)
        job = runOnDefaultDispatcher { run(groupId, inService, mode) }
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

    private suspend fun run(groupId: Long, service: ISagerNetService?, mode: Mode) {
        val tested = ConcurrentLinkedQueue<ProxyEntity>()
        try {
            val profiles = ConcurrentLinkedQueue(
                SagerDatabase.proxyDao.getByGroup(groupId).filter { mode == Mode.URL || it.requireBean().canTCPing() },
            )
            total.value = profiles.size
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
                            if (mode == Mode.TCP) {
                                if (!tcpPing(profile) { isActive }) break
                            } else try {
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
                            val result = Result(
                                profile.ping.takeIf { profile.status == 1 && it > 0 }, profile.status > 1,
                                ConfigsLoader.error(profile.status, profile.error),
                            )
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
                total.value = 0
                stored.update { it + 1 }
            }
        }
    }

    /**
     * The old list's TCP ping of [profile]: the time to connect to its server, off the tunnel (the underlying network).
     * False: stopped meanwhile, no result.
     */
    private fun tcpPing(profile: ProxyEntity, active: () -> Boolean): Boolean {
        profile.status = 0
        var address = profile.requireBean().serverAddress
        if (!address.isIpAddress()) {
            try {
                // no network known yet: the system's resolver
                (SagerNet.underlyingNetwork?.getAllByName(address) ?: InetAddress.getAllByName(address)).apply {
                    if (isNotEmpty()) {
                        address = this[0].hostAddress
                    }
                }
            } catch (ignored: UnknownHostException) {
            }
        }
        if (!active()) return false
        if (!address.isIpAddress()) {
            profile.status = 2
            profile.error = app.getString(R.string.connection_test_domain_not_found)
            return true
        }
        try {
            val socket = SagerNet.underlyingNetwork?.socketFactory?.createSocket() ?: Socket()
            try {
                socket.soTimeout = 3000
                socket.bind(InetSocketAddress(0))
                val start = SystemClock.elapsedRealtime()
                socket.connect(InetSocketAddress(address, profile.requireBean().serverPort), 3000)
                if (!active()) return false
                profile.status = 1
                profile.ping = (SystemClock.elapsedRealtime() - start).toInt()
                profile.error = null
            } finally {
                socket.closeQuietly()
            }
        } catch (e: Exception) {
            if (!active()) return false
            val message = e.readableMessage
            profile.status = 2
            when (tcpFailure(message)) {
                TcpFailure.TIMEOUT -> profile.error = app.getString(R.string.connection_test_timeout)
                TcpFailure.REFUSED -> profile.error = app.getString(R.string.connection_test_refused)
                TcpFailure.UNREACHABLE -> profile.error = app.getString(R.string.connection_test_unreachable)
                TcpFailure.OTHER -> {
                    profile.status = 3
                    profile.error = message
                }
            }
        }
        return true
    }
}

package io.niko.bg.proto

import io.niko.BuildConfig
import io.niko.bg.GuardedProcessPool
import io.niko.database.ProxyEntity
import io.niko.fmt.buildConfig
import io.niko.ktx.Logs
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ktx.runOnIoDispatcher
import io.niko.ktx.tryResume
import io.niko.ktx.tryResumeWithException
import kotlinx.coroutines.delay
import libcore.Libcore
import io.niko.net.LocalResolverImpl
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.Continuation

class TestInstance(profile: ProxyEntity, val link: String, private val timeout: Int) :
    BoxInstance(profile) {

    suspend fun doTest(): Int = withBox { Libcore.urlTest(it, link, timeout) }

    /** A channel probe through this profile (libcore probe.Options as JSON in, the result as JSON out). */
    suspend fun doProbe(options: String): String = withBox { Libcore.channelProbe(it, options) }

    /** A raycontrol node's channel probe (probe-v1) through this profile's tunnel, at the probe name. */
    suspend fun doProbeNode(request: String): String = withBox { Libcore.raycontrolProbeTunnel(it, request) }

    /**
     * [block] with this profile's core (its plugins too), which it reaches the net through (the default outbound,
     * HTTPClient.useInstance); the core closed after it: the app's update through a profile while the service is off.
     */
    suspend fun <T> through(block: (libcore.BoxInstance) -> T): T = withBox(io = true, block)

    // cancellable: the caller goes on at once; a test already running ends within its own limits and closes the box.
    // [io]: a long blocking transfer (an update's download) on the IO pool, not on one of Default's few threads
    private suspend fun <T> withBox(io: Boolean = false, test: (libcore.BoxInstance) -> T): T {
        return suspendCancellableCoroutine { cancellable ->
            // the app's tryResume (ignores a second resume), not CancellableContinuation's internal one
            val c: Continuation<T> = cancellable
            processes = GuardedProcessPool {
                Logs.w(it)
                c.tryResumeWithException(it)
            }
            val launchOn: (suspend kotlinx.coroutines.CoroutineScope.() -> Unit) -> Unit =
                if (io) { block -> runOnIoDispatcher(block) } else { block -> runOnDefaultDispatcher(block) }
            launchOn {
                use {
                    try {
                        init()
                        launch()
                        if (processes.processCount > 0) {
                            // wait for plugin start
                            delay(500)
                        }
                        c.tryResume(test(box))
                    } catch (e: Exception) {
                        c.tryResumeWithException(e)
                    }
                }
            }
        }
    }

    override fun buildConfig() {
        config = buildConfig(profile, true)
    }

    override suspend fun loadConfig() {
        // don't call destroyAllJsi here
        if (BuildConfig.DEBUG) Logs.d(config.config)
        box = Libcore.newSingBoxInstance(config.config, LocalResolverImpl)
    }

}

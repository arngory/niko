package io.niko.bg

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.*
import android.widget.Toast
import androidx.core.content.ContextCompat
import io.niko.Action
import io.niko.BootReceiver
import io.niko.R
import io.niko.RuleSetFiles
import io.niko.SagerNet
import io.niko.aidl.ISagerNetService
import io.niko.aidl.ISagerNetServiceCallback
import io.niko.bg.proto.ProxyInstance
import io.niko.database.DataStore
import io.niko.database.SagerDatabase
import io.niko.ktx.*
import io.niko.plugin.PluginManager
import io.niko.raycontrol.RayWatchdog
import io.niko.utils.DefaultNetworkListener
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import libcore.Libcore
import io.niko.Protocols
import io.niko.utils.Util
import java.net.UnknownHostException

class BaseService {

    enum class State(
        val canStop: Boolean = false,
        val started: Boolean = false,
        val connected: Boolean = false,
    ) {
        /**
         * Idle state is only used by UI and will never be returned by BaseService.
         */
        Idle, Connecting(true, true, false), Connected(true, true, true), Stopping, Stopped,
    }

    interface ExpectedException

    class Data internal constructor(private val service: Interface) {
        var state = State.Stopped
        var proxy: ProxyInstance? = null
        var notification: ServiceNotification? = null

        val receiver = broadcastReceiver { ctx, intent ->
            when (intent.action) {
                Intent.ACTION_SHUTDOWN -> service.persistStats()
                Action.RELOAD -> service.reload(intent.getBooleanExtra(Action.EXTRA_FORCE, false))
                // Action.SWITCH_WAKE_LOCK -> runOnDefaultDispatcher { service.switchWakeLock() }
                PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED -> {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        if (SagerNet.power.isDeviceIdleMode) {
                            proxy?.box?.sleep()
                        } else {
                            proxy?.box?.wake()
                            if (DataStore.wakeResetConnections) {
                                Libcore.resetAllConnections(true)
                            }
                        }
                    }
                }

                Action.RESET_UPSTREAM_CONNECTIONS -> runOnDefaultDispatcher {
                    Libcore.resetAllConnections(true)
                    runOnMainDispatcher {
                        Util.collapseStatusBar(ctx)
                        Toast.makeText(ctx, "Reset upstream connections done", Toast.LENGTH_SHORT)
                            .show()
                    }
                }

                else -> service.stopRunner()
            }
        }
        var closeReceiverRegistered = false

        // the next start is stopRunner's restart (a reload under the running VPN), not a fresh one
        var restarting = false

        val binder = Binder(this)
        var connectingJob: Job? = null

        fun changeState(s: State, msg: String? = null) {
            if (state == s && msg == null) return
            state = s
            DataStore.serviceState = s
            binder.stateChanged(s, msg)
        }
    }

    class Binder(private var data: Data? = null) : ISagerNetService.Stub(), CoroutineScope,
        AutoCloseable {
        private val callbacks = object : RemoteCallbackList<ISagerNetServiceCallback>() {
            override fun onCallbackDied(callback: ISagerNetServiceCallback?, cookie: Any?) {
                super.onCallbackDied(callback, cookie)
            }
        }

        val callbackIdMap = mutableMapOf<ISagerNetServiceCallback, Int>()

        override val coroutineContext = Dispatchers.Main.immediate + Job()

        override fun getState(): Int = (data?.state ?: State.Idle).ordinal
        override fun getProfileName(): String = data?.proxy?.displayProfileName ?: "Idle"

        override fun registerCallback(cb: ISagerNetServiceCallback, id: Int) {
            if (id == SagerConnection.CONNECTION_ID_RESTART_BG) {
                Runtime.getRuntime().exit(0)
                return
            }
            if (!callbackIdMap.contains(cb)) {
                callbacks.register(cb)
            }
            callbackIdMap[cb] = id
        }

        private val broadcastMutex = Mutex()

        suspend fun broadcast(work: (ISagerNetServiceCallback) -> Unit) {
            broadcastMutex.withLock {
                val count = callbacks.beginBroadcast()
                try {
                    repeat(count) {
                        try {
                            work(callbacks.getBroadcastItem(it))
                        } catch (_: RemoteException) {
                        } catch (_: Exception) {
                        }
                    }
                } finally {
                    callbacks.finishBroadcast()
                }
            }
        }

        override fun unregisterCallback(cb: ISagerNetServiceCallback) {
            callbackIdMap.remove(cb)
            callbacks.unregister(cb)
        }

        override fun urlTest(): Int {
            // a core not built yet has no box (lateinit): an exception Binder cannot carry would end this process
            val box = data?.proxy?.takeIf { it.isInitialized() }?.box ?: error("core not started")
            try {
                return Libcore.urlTest(
                    box, DataStore.connectionTestURL, 3000
                )
            } catch (e: Exception) {
                error(Protocols.genFriendlyMsg(e.readableMessage))
            }
        }

        fun stateChanged(s: State, msg: String?) = launch {
            val profileName = profileName
            broadcast { it.stateChanged(s.ordinal, profileName, msg) }
        }

        fun missingPlugin(pluginName: String) = launch {
            val profileName = profileName
            broadcast { it.missingPlugin(profileName, pluginName) }
        }

        override fun close() {
            callbacks.kill()
            cancel()
            data = null
        }
    }

    interface Interface {
        val data: Data
        val tag: String
        fun createNotification(profileName: String): ServiceNotification

        fun onBind(intent: Intent): IBinder? =
            if (intent.action == Action.SERVICE) data.binder else null

        fun reload(force: Boolean = false) {
            // the broadcast receiver runs this on the main thread: an exception here would end the process
            try {
                reloadOrRestart(force)
            } catch (e: Exception) {
                Logs.w(e)
                if (data.state.canStop) stopRunner(true)
            }
        }

        private fun reloadOrRestart(force: Boolean) {
            if (DataStore.selectedProxy == 0L) {
                stopRunner(false, (this as Context).getString(R.string.profile_empty))
            }
            if (!force && canReloadSelector()) {
                val proxy = data.proxy!!
                val ent = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
                val tag = proxy.config.profileTagMap[ent?.id] ?: ""
                if (tag.isNotBlank() && ent != null) {
                    // select from GUI
                    proxy.box.selectOutbound(tag)
                    // or select from webui
                    // => selector_OnProxySelected
                }
                // nothing restarted: a raycontrol watchdog mark must not stick to a later start
                RayWatchdog.reloadReceived(restarts = false)
                return
            }
            val s = data.state
            when {
                s == State.Stopped -> {
                    RayWatchdog.reloadReceived(restarts = false)
                    startRunner()
                }

                s.canStop -> {
                    RayWatchdog.reloadReceived(restarts = true)
                    stopRunner(true)
                }

                else -> {
                    Logs.w("Illegal state $s when invoking use")
                    RayWatchdog.reloadReceived(restarts = false)
                }
            }
        }

        fun canReloadSelector(): Boolean {
            // only a core that runs: one still starting has no config yet (lateinit), or a selector that is still
            // filling its outbounds, and a selection then races it (a Go map read and write at once ends the process)
            if (data.state != State.Connected) return false
            val proxy = data.proxy?.takeIf { it.isInitialized() } ?: return false
            if (proxy.config.selectorGroupId < 0) return false
            val ent = SagerDatabase.proxyDao.getById(DataStore.selectedProxy) ?: return false
            val tmpBox = ProxyInstance(ent)
            // a group that no longer builds (a key, a pin, a tunnel's settings): a restart, which reports it
            if (runCatching { tmpBox.buildConfigTmp() }.onFailure { Logs.w(it) }.isFailure) return false
            return tmpBox.lastSelectorGroupId == proxy.lastSelectorGroupId
        }

        suspend fun startProcesses() {
            // the core may download remote rule-sets while starting: keep the main thread free,
            // so a stop request can still reach close()
            val proxy = data.proxy!!
            withContext(Dispatchers.IO) { proxy.launch() }
        }

        /** the connection test through the running profile, for [RayWatchdog] */
        suspend fun probeRunning(): Boolean {
            val box = data.proxy?.takeIf { it.isInitialized() }?.box ?: return false
            return try {
                withContext(Dispatchers.IO) { Libcore.urlTest(box, DataStore.connectionTestURL, 5000) }
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Logs.d("raycontrol watchdog: ${e.readableMessage}")
                false
            }
        }

        fun startRunner() {
            this as Context
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(Intent(this, javaClass))
            else startService(Intent(this, javaClass))
        }

        fun killProcesses() {
            RayWatchdog.stop()
            RuleSetFiles.stop()
            data.proxy?.close()
            wakeLock?.apply {
                release()
                wakeLock = null
            }
            runOnDefaultDispatcher {
                DefaultNetworkListener.stop(this)
            }
        }

        fun stopRunner(restart: Boolean = false, msg: String? = null) {
            DataStore.baseService = null
            DataStore.vpnService = null

            if (data.state == State.Stopping) return
            data.notification?.destroy()
            data.notification = null
            this as Service

            data.changeState(State.Stopping)
            // a stop for good: a later start is the user's or the system's, not the watchdog's
            if (!restart) RayWatchdog.stop(final = true)

            runOnMainDispatcher {
                // Stop connecting first: cancel the core start (it runs on an IO thread and may wait
                // on a download), wait for the launch to return, and only then close, so close()
                // never overlaps launch().
                val connecting = data.connectingJob
                connecting?.cancel()
                data.proxy?.takeIf { it.isInitialized() }?.box?.cancelStart()
                connecting?.join()
                // we use a coroutineScope here to allow clean-up in parallel
                coroutineScope {
                    killProcesses()
                    val data = data
                    if (data.closeReceiverRegistered) {
                        unregisterReceiver(data.receiver)
                        data.closeReceiverRegistered = false
                    }
                    data.proxy = null
                }

                // the VPN stops: FakeDNS's names asked leave the core's cache file (kept for a restart only)
                if (!restart) withContext(Dispatchers.IO) { clearFakeIPCache() }
                // change the state
                data.changeState(State.Stopped, msg)
                data.restarting = restart
                // stop the service if nothing has bound to it
                if (restart) startRunner() else {
                    stopSelf()
                }
            }
        }

        /** FakeDNS's addresses and the names asked, out of the core's cache file (no core has it open). */
        fun clearFakeIPCache() {
            runCatching {
                Libcore.clearFakeIPCache(java.io.File(SagerNet.application.noBackupFilesDir, "cache.db").absolutePath)
            }.onFailure { Logs.w(it) }
        }

        fun persistStats() {
            // TODO NEW save app stats?
        }

        // networks
        var upstreamInterfaceName: String?

        suspend fun preInit() {
            RayWatchdog.beginRun()
            DefaultNetworkListener.start(this) {
                RayWatchdog.network(it != null)
                SagerNet.connectivity.getLinkProperties(it)?.also { link ->
                    SagerNet.underlyingNetwork = it
                    DataStore.vpnService?.updateUnderlyingNetwork()
                    //
                    val oldName = upstreamInterfaceName
                    if (oldName != link.interfaceName) {
                        upstreamInterfaceName = link.interfaceName
                    }
                    if (oldName != null && upstreamInterfaceName != null && oldName != upstreamInterfaceName) {
                        Logs.d("Network changed: $oldName -> $upstreamInterfaceName")
                        if (DataStore.networkChangeResetConnections) {
                            Libcore.resetAllConnections(true)
                        }
                        // a raycontrol node may be reachable differently here (SPA carrier, port)
                        RayWatchdog.networkChanged()
                    }
                }
            }
            // a cold :bg has no network yet: the first config would miss the network's Brutal rates and Yggdrasil
            // peers, and the watchdog would restart the core ~20 s later for them (P6). Wait briefly for it.
            if (SagerNet.underlyingNetwork == null && io.niko.raycontrol.RayNodes.list().isNotEmpty()) {
                kotlinx.coroutines.withTimeoutOrNull(2_000L) {
                    while (SagerNet.underlyingNetwork == null) kotlinx.coroutines.delay(50L)
                }
            }
        }

        var wakeLock: PowerManager.WakeLock?
        fun acquireWakeLock()

        suspend fun lateInit() {
            wakeLock?.apply {
                release()
                wakeLock = null
            }

            if (DataStore.acquireWakeLock) {
                acquireWakeLock()
                data.notification?.postNotificationWakeLockStatus(true)
            } else {
                data.notification?.postNotificationWakeLockStatus(false)
            }
        }

        fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
            DataStore.baseService = this

            val data = data
            if (data.state != State.Stopped) return Service.START_NOT_STICKY
            val profile = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)
            this as Context
            if (profile == null) { // gracefully shutdown: https://stackoverflow.com/q/47337857/2245107
                data.notification = createNotification("")
                stopRunner(false, getString(R.string.profile_empty))
                return Service.START_NOT_STICKY
            }

            val proxy = ProxyInstance(profile, this)
            data.proxy = proxy
            BootReceiver.enabled = DataStore.persistAcrossReboot
            if (!data.closeReceiverRegistered) {
                val filter = IntentFilter().apply {
                    addAction(Action.RELOAD)
                    addAction(Intent.ACTION_SHUTDOWN)
                    addAction(Action.CLOSE)
                    // addAction(Action.SWITCH_WAKE_LOCK)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        addAction(PowerManager.ACTION_DEVICE_IDLE_MODE_CHANGED)
                    }
                    addAction(Action.RESET_UPSTREAM_CONNECTIONS)
                }
                // every sender is this app (its UI process or its own notification actions)
                ContextCompat.registerReceiver(
                    this,
                    data.receiver,
                    filter,
                    "$packageName.SERVICE",
                    null,
                    ContextCompat.RECEIVER_NOT_EXPORTED
                )
                data.closeReceiverRegistered = true
            }

            data.changeState(State.Connecting)
            // a stop requested meanwhile ends the connect quietly: stopRunner reports the state
            fun failed(msg: String?) {
                if (data.state == State.Connecting) stopRunner(false, msg)
            }
            data.connectingJob = runOnMainDispatcher {
                try {
                    data.notification = createNotification(ServiceNotification.genTitle(profile))

                    Executable.killAll()    // clean up old processes
                    // a fresh start (a run that ended without a stop, the process killed): the last run's FakeDNS names
                    // are of a VPN that is gone; a restart under the running VPN keeps them for the system's addresses
                    if (!data.restarting) withContext(Dispatchers.IO) { clearFakeIPCache() }
                    data.restarting = false
                    preInit()
                    proxy.init()
                    DataStore.currentProfile = profile.id

                    proxy.processes = GuardedProcessPool {
                        Logs.w(it)
                        stopRunner(false, it.readableMessage)
                    }

                    startProcesses()
                    if (data.state != State.Connecting) return@runOnMainDispatcher
                    data.changeState(State.Connected)
                    RayWatchdog.start { probeRunning() }
                    // the rules' downloaded rule-sets: the stale ones through the proxy, once it has settled
                    RuleSetFiles.updateSoon()

                    lateInit()
                } catch (_: CancellationException) { // if the job was cancelled, it is canceller's responsibility to call stopRunner
                } catch (_: UnknownHostException) {
                    failed(getString(R.string.invalid_server))
                } catch (e: PluginManager.PluginNotFoundException) {
                    Toast.makeText(this@Interface, e.readableMessage, Toast.LENGTH_SHORT).show()
                    Logs.w(e)
                    data.binder.missingPlugin(e.plugin)
                    failed(null)
                } catch (exc: Throwable) {
                    if (exc.javaClass.name.endsWith("proxyerror")) {
                        // error from golang
                        Logs.w(exc.readableMessage)
                    } else {
                        Logs.w(exc)
                    }
                    failed("${getString(R.string.service_failed)}: ${exc.readableMessage}")
                } finally {
                    data.connectingJob = null
                }
            }
            return Service.START_NOT_STICKY
        }
    }

}
package io.niko.utils

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import io.niko.ktx.app
import io.niko.ktx.listenForPackageChanges
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.niko.plugin.Plugins
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The installed apps, for rules by app and the app lists. The core asks it from its own threads (NativeInterface)
 * while a change of the installed packages rebuilds it: each map is built whole and then put in place, never cleared
 * and refilled where a reader could see it half done (ThroneForAndroid found the race in NekoBox's code).
 */
object PackageCache {

    @Volatile
    lateinit var installedPackages: Map<String, PackageInfo>

    @Volatile
    lateinit var installedPluginPackages: Map<String, PackageInfo>

    @Volatile
    lateinit var installedApps: Map<String, ApplicationInfo>

    @Volatile
    lateinit var packageMap: Map<String, Int>

    @Volatile
    var uidMap: Map<Int, Set<String>> = emptyMap()
        private set
    val loaded = Mutex(true)
    var registerd = AtomicBoolean(false)

    private val reloads = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var pendingReload: Job? = null

    // called from init (suspend)
    fun register() {
        if (registerd.getAndSet(true)) return
        reload()
        app.listenForPackageChanges(false) {
            // the broadcast comes on the main thread and listing the packages is slow: off it, and one listing for
            // a burst of changes (an update of several apps)
            synchronized(this) {
                pendingReload?.cancel()
                pendingReload = reloads.launch {
                    delay(1000L)
                    reload()
                    labelMap.clear()
                }
            }
        }
        loaded.unlock()
    }

    @Synchronized
    @SuppressLint("InlinedApi")
    fun reload() {
        val rawPackageInfo = app.packageManager.getInstalledPackages(
            PackageManager.MATCH_UNINSTALLED_PACKAGES
                    or PackageManager.GET_PERMISSIONS
                    or PackageManager.GET_PROVIDERS
                    or PackageManager.GET_META_DATA
        )

        installedPackages = rawPackageInfo.filter {
            when (it.packageName) {
                "android" -> true
                else -> it.requestedPermissions?.contains(Manifest.permission.INTERNET) == true
            }
        }.associateBy { it.packageName }

        installedPluginPackages = rawPackageInfo.filter {
            Plugins.isExe(it)
        }.associateBy { it.packageName }

        val installed = app.packageManager.getInstalledApplications(PackageManager.GET_META_DATA)
        installedApps = installed.associateBy { it.packageName }
        packageMap = installed.associate { it.packageName to it.uid }
        val uids = HashMap<Int, HashSet<String>>()
        for (info in installed) {
            uids.getOrPut(info.uid) { HashSet() }.add(info.packageName)
        }
        uidMap = uids
    }

    /**
     * The packages of a uid, for the names the core shows (NativeInterface). An app of another user on the device (a
     * work profile, a second space, a clone) has the uid of its app ID in that user: the app's own, found by the app
     * ID when the uid itself is not listed. Rules by app take the main user's uids (packageMap) only.
     */
    fun packagesOf(uid: Int): Set<String>? = packagesOf(uidMap, uid)

    internal fun packagesOf(map: Map<Int, Set<String>>, uid: Int): Set<String>? =
        map[uid] ?: map[uid % PER_USER_RANGE]?.takeIf { uid >= PER_USER_RANGE }

    // android.os.UserHandle.PER_USER_RANGE
    private const val PER_USER_RANGE = 100000

    operator fun get(uid: Int) = packagesOf(uid)
    operator fun get(packageName: String) = packageMap[packageName]

    fun awaitLoadSync() {
        if (::packageMap.isInitialized) {
            return
        }
        if (!registerd.get()) {
            register()
            return
        }
        runBlocking {
            loaded.withLock {
                // just await
            }
        }
    }

    private val labelMap = ConcurrentHashMap<String, String>()
    fun loadLabel(packageName: String): String {
        var label = labelMap[packageName]
        if (label != null) return label
        val info = installedApps[packageName] ?: return packageName
        label = info.loadLabel(app.packageManager).toString()
        labelMap[packageName] = label
        return label
    }

}
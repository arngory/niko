package io.niko

import android.annotation.SuppressLint
import android.app.*
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.PowerManager
import android.os.StrictMode
import android.os.UserManager
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import go.Seq
import io.niko.bg.SagerConnection
import io.niko.database.DataStore
import io.niko.ktx.Logs
import io.niko.ktx.isOss
import io.niko.ktx.isPreview
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ui.MainActivity
import io.niko.utils.*
import kotlinx.coroutines.DEBUG_PROPERTY_NAME
import kotlinx.coroutines.DEBUG_PROPERTY_VALUE_ON
import libcore.Libcore
import io.niko.NativeInterface
import io.niko.net.LocalResolverImpl
import io.niko.utils.JavaUtil
import io.niko.utils.cleanWebview
import java.io.File
import androidx.work.Configuration as WorkConfiguration

class SagerNet : Application(),
    WorkConfiguration.Provider {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)

        application = this
    }

    private val nativeInterface = NativeInterface()

    /** Route rules (geoip.db, geosite.db) and an imported ca.pem. Internal storage: on Android 6-10 any app
     *  with storage access could replace files under getExternalFilesDir and redirect traffic or add a root. */
    val routeAssets: File by lazy { File(filesDir, "route") }

    /**
     * Rule databases of 1.25 and older lived under getExternalFilesDir: another provider's or the user's own would
     * be replaced by the bundled ones, and rules with their codes would stop the service. They move once, but only
     * where that folder was private to the app (Android 11+); a ca.pem there is never taken.
     */
    private fun migrateRouteAssets() {
        if (Build.VERSION.SDK_INT < 30) return
        val marker = File(routeAssets, ".migrated-1.26")
        if (marker.exists()) return
        val old = getExternalFilesDir(null) ?: return
        old.listFiles()?.filter { it.isFile && (it.name.endsWith(".db") || it.name.endsWith(".version.txt")) }
            ?.forEach { file ->
                val target = File(routeAssets, file.name)
                if (target.exists()) return@forEach
                // the main and :bg processes may both get here: copy aside, then rename
                val tmp = File(routeAssets, "${file.name}.$process.migrating")
                try {
                    file.copyTo(tmp, overwrite = true)
                    if (!target.exists()) tmp.renameTo(target)
                } catch (e: Exception) {
                    Logs.w(e)
                } finally {
                    tmp.delete()
                }
            }
        marker.createNewFile()
    }
    val process: String = JavaUtil.getProcessName()
    private val isMainProcess = process == BuildConfig.APPLICATION_ID
    val isBgProcess = process.endsWith(":bg")

    override fun onCreate() {
        super.onCreate()

        Thread.setDefaultUncaughtExceptionHandler(CrashHandler)

        if (isMainProcess || isBgProcess) {
            DataStore.migrateLegacySettings()
            if (isMainProcess) {
                // create the per-install tokens early; creation is atomic across processes
                DataStore.clashApiSecret
                DataStore.shortcutToken
            }
            routeAssets.mkdirs()
            migrateRouteAssets()
            Seq.setContext(this)
            Libcore.initCore(
                process,
                cacheDir.absolutePath + "/",
                filesDir.absolutePath + "/",
                routeAssets.absolutePath + "/",
                DataStore.logBufSize,
                DataStore.logLevel > 0,
                nativeInterface, nativeInterface, LocalResolverImpl
            )
            // client-min-v1 §1: this version on every request to a raycontrol node, so the node can say "update"
            val rayClient = io.niko.raycontrol.RayClient.header(this)
            if (!Libcore.setRaycontrolClient(rayClient)) Logs.w("raycontrol: the client header $rayClient is not valid")
            // the user's node addresses stay out of this process' log
            runOnDefaultDispatcher { io.niko.raycontrol.RayNodes.syncLogRedactions() }
            // SPA v3 at a DNS64 address: the network's NAT64 prefix, for the core and libcore's requests here.
            // It listens for the process's life, so the callback stays between the service's runs (the main process
            // kept one already): the cached network follows its callbacks, a request rests on the default network
            runOnDefaultDispatcher { DefaultNetworkListener.start(Nat64) { Nat64.sync(it) } }

            // fix multi process issue in Android 9+
            JavaUtil.handleWebviewDir(this)

            runOnDefaultDispatcher {
                PackageCache.register()
                cleanWebview()
            }
        }

        if (isMainProcess) {
            Theme.apply(this)
            Theme.applyNightTheme()
            runOnDefaultDispatcher {
                DefaultNetworkListener.start(this) {
                    underlyingNetwork = it
                }

                updateNotificationChannels()
                // raycontrol nodes whose subscription fetch was never planned (imported by 1.13.0)
                try {
                    io.niko.raycontrol.RaySubscription.startup()
                } catch (e: Exception) {
                    Logs.w(e)
                }
            }
        }

        if (BuildConfig.DEBUG) {
            System.setProperty(DEBUG_PROPERTY_NAME, DEBUG_PROPERTY_VALUE_ON)
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .detectLeakedRegistrationObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        updateNotificationChannels()
    }

    override val workManagerConfiguration: WorkConfiguration
        get() = WorkConfiguration.Builder()
            .setDefaultProcessName("${BuildConfig.APPLICATION_ID}:bg")
            .build()

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)

        Libcore.forceGc()
    }

    @SuppressLint("InlinedApi")
    companion object {

        lateinit var application: SagerNet

        val isTv by lazy {
            uiMode.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION
        }

        val configureIntent: (Context) -> PendingIntent by lazy {
            {
                PendingIntent.getActivity(
                    it,
                    0,
                    Intent(
                        application, MainActivity::class.java
                    ).setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT),
                    PendingIntent.FLAG_IMMUTABLE
                )
            }
        }
        val activity by lazy { application.getSystemService<ActivityManager>()!! }
        val clipboard by lazy { application.getSystemService<ClipboardManager>()!! }
        val connectivity by lazy { application.getSystemService<ConnectivityManager>()!! }
        val notification by lazy { application.getSystemService<NotificationManager>()!! }
        val user by lazy { application.getSystemService<UserManager>()!! }
        val uiMode by lazy { application.getSystemService<UiModeManager>()!! }
        val power by lazy { application.getSystemService<PowerManager>()!! }

        fun getClipboardText(): String {
            return clipboard.primaryClip?.takeIf { it.itemCount > 0 }
                ?.getItemAt(0)?.text?.toString() ?: ""
        }

        fun clearClipboard() {
            try {
                clipboard.clearPrimaryClip()
            } catch (e: RuntimeException) {
                Logs.w(e)
            }
        }

        /** [sensitive]: a secret (an invite): the system hides it in its clipboard preview (Android 13+). */
        fun trySetPrimaryClip(clip: String, sensitive: Boolean = false) = try {
            clipboard.setPrimaryClip(ClipData.newPlainText(null, clip).apply {
                // setExtras is API 24; the flag means something from 33 on
                if (sensitive && Build.VERSION.SDK_INT >= 33) description.extras = android.os.PersistableBundle().apply {
                    putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
                }
            })
            true
        } catch (e: RuntimeException) {
            Logs.w(e)
            false
        }

        fun updateNotificationChannels() {
            notification.createNotificationChannels(
                listOf(
                    NotificationChannel(
                        "service-vpn",
                        application.getText(R.string.service_vpn),
                        NotificationManager.IMPORTANCE_MIN
                    ),   // #1355
                    NotificationChannel(
                        "service-proxy",
                        application.getText(R.string.service_proxy),
                        NotificationManager.IMPORTANCE_LOW
                    ), NotificationChannel(
                        "service-subscription",
                        application.getText(R.string.service_subscription),
                        NotificationManager.IMPORTANCE_DEFAULT
                    ), NotificationChannel(
                        "connection-test",
                        application.getText(R.string.connection_test),
                        NotificationManager.IMPORTANCE_DEFAULT
                    )
                )
            )
        }

        fun startService() = ContextCompat.startForegroundService(
            application, Intent(application, SagerConnection.serviceClass)
        )

        /**
         * [force]: a new core even where a selector group would only switch its outbound (raycontrol: new
         * credentials, Brutal rates, Yggdrasil peers live in the config, not in the selector) (P5).
         */
        fun reloadService(force: Boolean = false) = application.sendBroadcast(
            Intent(Action.RELOAD).setPackage(application.packageName).putExtra(Action.EXTRA_FORCE, force)
        )

        fun stopService() =
            application.sendBroadcast(Intent(Action.CLOSE).setPackage(application.packageName))

        var underlyingNetwork: Network? = null

        var appVersionNameForDisplay = {
            var n = BuildConfig.VERSION_NAME
            if (isPreview) {
                n += " " + BuildConfig.PRE_VERSION_NAME
            } else if (!isOss) {
                n += " ${BuildConfig.FLAVOR}"
            }
            if (BuildConfig.DEBUG) {
                n += " DEBUG"
            }
            n
        }()
    }

}

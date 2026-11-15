package io.niko.utils

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import io.niko.ktx.launchCustomTab
import java.util.Locale

/**
 * What stops the app in the background besides Doze: Android's "Restricted" battery use (API 28+), which blocks
 * background starts whatever the battery optimization says, and the vendors' own autostart and background managers.
 * The watchdog of a raycontrol node and the subscription updates run in the background: either kills them. After
 * ThroneForAndroid's BatteryOptimizations and OemBackground.
 */
object BackgroundLimits {

    /** Battery use "Restricted" for this app: lifted on its App info page, under Battery. */
    fun restricted(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return false
        val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        return manager.isBackgroundRestricted
    }

    /** Opens the app's App info page. */
    fun openAppInfo(context: Context): Boolean = launch(
        context,
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
    )

    // the vendors' autostart screens; their component names change between ROM versions: best effort
    private val vendorScreens = mapOf(
        "xiaomi" to "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",
        "redmi" to "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",
        "poco" to "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",
        "huawei" to "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity",
        "honor" to "com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity",
        "oppo" to "com.coloros.safecenter/.permission.startup.StartupAppListActivity",
        "realme" to "com.coloros.safecenter/.permission.startup.StartupAppListActivity",
        "vivo" to "com.vivo.permissionmanager/.activity.BgStartUpManagerActivity",
        "samsung" to "com.samsung.android.lool/com.samsung.android.sm.battery.ui.BatteryActivity",
        "oneplus" to "com.oneplus.security/.chainlaunch.view.ChainLaunchAppListActivity",
        "asus" to "com.asus.mobilemanager/.entry.FunctionActivity",
    )

    /** The vendor's autostart screen for a manufacturer, null for one without its own background manager. */
    internal fun vendorScreen(manufacturer: String): String? = vendorScreens[manufacturer.lowercase(Locale.ROOT).trim()]

    private val manufacturer get() = Build.MANUFACTURER.orEmpty().lowercase(Locale.ROOT).trim()

    /** Whether this phone's vendor has a background manager of its own to look at. */
    fun hasVendorManager(): Boolean = manufacturer in vendorScreens

    /** The vendor's screen when it opens, else dontkillmyapp.com's page of the vendor (in a browser). */
    fun openVendorManager(context: Context) {
        val screen = vendorScreen(manufacturer)?.let(ComponentName::unflattenFromString)
        if (screen != null && launch(context, Intent().setComponent(screen))) return
        context.launchCustomTab("https://dontkillmyapp.com/$manufacturer")
    }

    private fun launch(context: Context, intent: Intent): Boolean {
        if (intent.resolveActivity(context.packageManager) == null) return false
        return try {
            if (context !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }
}

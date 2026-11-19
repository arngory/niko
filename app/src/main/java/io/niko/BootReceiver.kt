package io.niko

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import io.niko.bg.SubscriptionUpdater
import io.niko.database.DataStore
import io.niko.ktx.app
import io.niko.ktx.runOnDefaultDispatcher

class BootReceiver : BroadcastReceiver() {
    companion object {
        private val componentName by lazy { ComponentName(app, BootReceiver::class.java) }
        var enabled: Boolean
            get() = app.packageManager.getComponentEnabledSetting(componentName) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            set(value) = app.packageManager.setComponentEnabledSetting(
                componentName, if (value) PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                else PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP
            )
    }

    override fun onReceive(context: Context, intent: Intent) {
        // the receiver is exported; only the system can send these protected broadcasts
        when (intent.action) {
            // an update is handled by PackageReplacedReceiver, which is always enabled
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_LOCKED_BOOT_COMPLETED -> {}
            else -> return
        }
        runOnDefaultDispatcher {
            SubscriptionUpdater.reconfigureUpdater()
        }

        if (!DataStore.persistAcrossReboot) {   // sanity check
            enabled = false
            return
        }

        val doStart = when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED -> false // DataStore.directBootAware
            else -> SagerNet.user.isUserUnlocked
        } && DataStore.selectedProxy > 0

        if (doStart) SagerNet.startService()
    }
}

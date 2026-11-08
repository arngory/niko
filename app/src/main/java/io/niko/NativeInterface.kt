package io.niko

import android.content.Context
import android.net.ConnectivityManager
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Build.VERSION_CODES
import androidx.annotation.RequiresApi
import io.niko.SagerNet
import io.niko.bg.ServiceNotification
import io.niko.database.DataStore
import io.niko.database.SagerDatabase
import io.niko.ktx.Logs
import io.niko.ktx.app
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.utils.PackageCache
import io.niko.utils.DefaultNetworkListener
import kotlinx.coroutines.runBlocking
import libcore.BoxPlatformInterface
import libcore.InterfaceUpdateListener
import libcore.Libcore
import libcore.NikoInterface
import java.net.InetSocketAddress
import java.net.NetworkInterface
import java.net.SocketException

class NativeInterface : BoxPlatformInterface, NikoInterface {

    //  libbox interface

    override fun startDefaultInterfaceMonitor(id: Long, listener: InterfaceUpdateListener) {
        runBlocking {
            DefaultNetworkListener.start(id) { network ->
                val name = network?.let { SagerNet.connectivity.getLinkProperties(it)?.interfaceName }
                try {
                    val intf = name?.let { NetworkInterface.getByName(it) }
                    listener.updateDefaultInterface(name.orEmpty(), intf?.index ?: -1, intf?.mtu ?: 0)
                } catch (error: SocketException) {
                    Logs.w(error)
                    listener.updateDefaultInterface("", -1, 0)
                }
            }
        }
    }

    override fun closeDefaultInterfaceMonitor(id: Long) {
        runBlocking { DefaultNetworkListener.stop(id) }
    }

    override fun autoDetectInterfaceControl(fd: Int) {
        DataStore.vpnService?.protect(fd)
    }

    override fun openTun(singTunOptionsJson: String, tunPlatformOptionsJson: String): Long {
        if (DataStore.vpnService == null) {
            throw Exception("no VpnService")
        }
        return DataStore.vpnService!!.startVpn(singTunOptionsJson, tunPlatformOptionsJson).toLong()
    }

    override fun useProcFS(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override fun findConnectionOwner(
        ipProto: Int, srcIp: String, srcPort: Int, destIp: String, destPort: Int
    ): Int {
        return SagerNet.connectivity.getConnectionOwnerUid(
            ipProto, InetSocketAddress(srcIp, srcPort), InetSocketAddress(destIp, destPort)
        )
    }

    override fun packageNameByUid(uid: Int): String {
        PackageCache.awaitLoadSync()

        if (uid <= 1000L) {
            return "android"
        }

        val packageNames = PackageCache.uidMap[uid]
        if (!packageNames.isNullOrEmpty()) for (packageName in packageNames) {
            return packageName
        }

        error("unknown uid $uid")
    }

    override fun uidByPackageName(packageName: String): Int {
        PackageCache.awaitLoadSync()
        return PackageCache[packageName] ?: 0
    }

    // the SSID and BSSID for wifi_ssid / wifi_bssid rules. WifiManager.connectionInfo is deprecated for
    // NetworkCapabilities.transportInfo, which gives the SSID only to a callback with location info: the same
    // permission, so the old getter stays
    @Suppress("DEPRECATION")
    override fun wifiState(): String {
        val wifiManager =
            app.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                ?: return ""
        return try {
            val connectionInfo = wifiManager.connectionInfo
            "${connectionInfo.ssid},${connectionInfo.bssid}"
        } catch (e: SecurityException) {
            Logs.w(e)
            ""
        }
    }

    // nb4a interface

    override fun useOfficialAssets(): Boolean {
        return DataStore.rulesProvider == 0
    }

    override fun selector_OnProxySelected(selectorTag: String, tag: String) {
        if (selectorTag != "proxy") {
            Logs.d("other selector: $selectorTag")
            return
        }
        Libcore.resetAllConnections(true)
        DataStore.baseService?.apply {
            runOnDefaultDispatcher {
                // the core may have stopped or been replaced since: nothing to show then
                val config = data.proxy?.takeIf { it.isInitialized() }?.config ?: return@runOnDefaultDispatcher
                val id = config.profileTagMap
                    .filterValues { it == tag }.keys.firstOrNull() ?: -1
                val ent = SagerDatabase.proxyDao.getById(id) ?: return@runOnDefaultDispatcher
                // traffic & title
                data.proxy?.apply {
                    looper?.selectMain(id)
                    displayProfileName = ServiceNotification.genTitle(ent)
                    data.notification?.postNotificationTitle(displayProfileName)
                }
                // post binder
                data.binder.broadcast { b ->
                    b.cbSelectorUpdate(id)
                }
            }
        }
    }

}

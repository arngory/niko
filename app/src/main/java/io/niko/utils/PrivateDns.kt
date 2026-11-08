package io.niko.utils

import android.net.NetworkCapabilities
import android.os.Build
import io.niko.SagerNet

object PrivateDns {
    /**
     * The host of strict Private DNS (Settings → Network → Private DNS: a hostname), as the underlying network has
     * it: the setting in force. Null in the other modes, and before Android 9.
     */
    fun strictHost(): String? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        return runCatching {
            val cm = SagerNet.connectivity
            @Suppress("DEPRECATION")
            cm.allNetworks.asSequence().filter { network ->
                cm.getNetworkCapabilities(network)?.let {
                    !it.hasTransport(NetworkCapabilities.TRANSPORT_VPN) && it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                } == true
            }.mapNotNull { cm.getLinkProperties(it)?.privateDnsServerName?.trim()?.takeIf { name -> name.isNotEmpty() } }.firstOrNull()
        }.getOrNull()
    }
}

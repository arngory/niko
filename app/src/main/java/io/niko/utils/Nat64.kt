package io.niko.utils

import android.net.Network
import android.os.Build
import io.niko.SagerNet
import io.niko.ktx.Logs
import libcore.Libcore

/**
 * The NAT64 prefix of the network in use, handed to the core in each process (Libcore.setNAT64Prefix): a SPA v3
 * knock at a DNS64 address names the node's IPv4 it embeds (raycontrol spa-v3 §3). Android tells it from API 30.
 */
object Nat64 {

    @Volatile
    private var last: String? = null

    fun sync(network: Network?) {
        val prefix = if (Build.VERSION.SDK_INT >= 30 && network != null) {
            runCatching { SagerNet.connectivity.getLinkProperties(network)?.nat64Prefix?.toString() }.getOrNull()
        } else null
        val value = prefix.orEmpty()
        if (value == last) return
        last = value
        try {
            Libcore.setNAT64Prefix(value)
        } catch (e: Throwable) {
            Logs.w(e)
        }
    }
}

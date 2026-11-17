package io.niko.raycontrol

import android.Manifest
import android.content.pm.PackageManager
import android.net.wifi.WifiManager
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.database.DataStore
import io.niko.ktx.Logs
import io.niko.ktx.app
import io.niko.ktx.runOnDefaultDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import java.security.MessageDigest

/**
 * lan-v1 §3: the user's devices in one trusted Wi-Fi link to each other directly, by Yggdrasil's multicast with the
 * password the node gives them. The user keeps the trusted networks and picks how one is known (owner 2026-11-17):
 * - by its fingerprint: the hash of its gateway, DNS servers and domain (RayChannel.network), no permission at all,
 *   in the background too; a Wi-Fi of another place with the same router address and DNS is taken for it, where this
 *   device only announces itself (a node without the user's password never links);
 * - by its SSID: the location permission, and "all the time" for the background on Android 10+ (the service is no
 *   location service): without it the name is unknown there, and the network is not taken for a trusted one.
 * Never together with niko-ygg-via: a node's mesh through the public peers stays off the local network.
 */
object RayLanDirect {

    const val BY_FINGERPRINT = "fingerprint"
    const val BY_SSID = "ssid"

    /** The interfaces the beacons go out on and are heard on: Wi-Fi's. */
    const val INTERFACES = "^wlan[0-9]+$"

    /** A network the user trusts: the name shown, its fingerprint and, where it was known, its SSID. */
    data class Trusted(val name: String, val key: String, val ssid: String?) {
        fun toJson() = JsonObject().apply {
            addProperty("name", name)
            addProperty("key", key)
            ssid?.let { addProperty("ssid", it) }
        }

        companion object {
            fun fromJson(o: JsonObject): Trusted? {
                val key = o.get("key")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() } ?: return null
                val ssid = o.get("ssid")?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotEmpty() }
                val name = o.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.trim()?.take(64)?.ifEmpty { null }
                return Trusted(name ?: ssid ?: key.take(8), key, ssid)
            }
        }
    }

    /** The Wi-Fi under the VPN: its fingerprint and, with the permission, its SSID. */
    data class Current(val key: String, val ssid: String?)

    fun trusted(): List<Trusted> = parse(DataStore.lanTrustedWifi)

    /** The stored list; a broken one is none (the user adds the networks again). */
    fun parse(stored: String?): List<Trusted> = try {
        (stored?.takeIf { it.isNotBlank() }?.let { JsonParser.parseString(it) } as? JsonArray)
            ?.mapNotNull { (it as? JsonObject)?.let(Trusted::fromJson) }?.distinctBy { it.key }.orEmpty()
    } catch (e: Exception) {
        emptyList()
    }

    fun save(list: List<Trusted>) {
        DataStore.lanTrustedWifi = JsonArray().apply { list.distinctBy { it.key }.forEach { add(it.toJson()) } }.toString()
    }

    /** The network under the VPN when it is a Wi-Fi, else null. */
    fun current(): Current? {
        val network = RayChannel.network() ?: return null
        if (network.kind != "wifi") return null
        return Current(network.key, ssid())
    }

    /** The location permission the SSID needs (ACCESS_FINE_LOCATION; the background one apart, [backgroundGranted]). */
    fun locationGranted() =
        ContextCompat.checkSelfPermission(app, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The SSID read when the app is not on the screen: "all the time" on Android 10+. */
    fun backgroundGranted() = Build.VERSION.SDK_INT < 29 || ContextCompat.checkSelfPermission(
        app, Manifest.permission.ACCESS_BACKGROUND_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED

    /** The Wi-Fi's name, null without the permission or with location off ("<unknown ssid>"). */
    fun ssid(): String? = if (!locationGranted()) null else runCatching {
        @Suppress("DEPRECATION")
        app.getSystemService<WifiManager>()?.connectionInfo?.ssid
    }.getOrNull()?.removeSurrounding("\"")?.takeIf { it.isNotEmpty() && it != UNKNOWN_SSID }

    // WifiManager.UNKNOWN_SSID, a constant of API 30 that older systems answer just the same
    private const val UNKNOWN_SSID = "<unknown ssid>"

    /** Whether [current] is one of [list], the way [by] knows a network. */
    fun matches(current: Current?, list: List<Trusted>, by: String): Boolean {
        current ?: return false
        return list.any { if (by == BY_SSID) it.ssid != null && it.ssid == current.ssid else it.key == current.key }
    }

    /** [node]'s LAN goes out on the Wi-Fi now: the user's switch, a trusted network, not through the public peers. */
    fun on(node: RayNode, network: ChannelNetwork? = RayChannel.network()): Boolean =
        DataStore.lanDirect && matches(current(), trusted(), DataStore.lanDirectBy) &&
                !RayChannel.yggViaActive(node.spa.nodeId, network)

    /** The running config's mark of the direct link: a short hash of the password it took, so it is not stored. */
    fun mark(password: String): String =
        MessageDigest.getInstance("SHA-256").digest(password.toByteArray()).take(6).joinToString("") { "%02x".format(it) }

    private var lock: WifiManager.MulticastLock? = null
    private var switchAt: Job? = null

    /**
     * The config the service runs: [on] takes multicast on Wi-Fi, which drops the group's packets without a
     * MulticastLock; [nextSwitch] is the device clock's second the nodes' next password starts, at which the config
     * is built anew ([RayLanNet.reloadIfChanged]).
     */
    @Synchronized
    fun noteRunning(on: Boolean, nextSwitch: Long?) {
        if (on) {
            if (lock == null) lock = runCatching {
                app.getSystemService<WifiManager>()?.createMulticastLock("niko-lan")?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
            }.onFailure { Logs.w("multicast lock: ${it.message}") }.getOrNull()
        } else {
            release()
        }
        switchAt?.cancel()
        switchAt = null
        val now = System.currentTimeMillis() / 1000
        if (on && nextSwitch != null && nextSwitch > now) switchAt = runOnDefaultDispatcher {
            delay((nextSwitch - now + 1) * 1000)
            RayLanNet.reloadIfChanged(System.currentTimeMillis() / 1000)
        }
    }

    /** The service stops: no lock held, no switch waited for. */
    @Synchronized
    fun stop() {
        switchAt?.cancel()
        switchAt = null
        release()
    }

    private fun release() {
        lock?.let { runCatching { if (it.isHeld) it.release() } }
        lock = null
    }
}

package io.niko.raycontrol

import android.content.Context
import io.niko.BuildConfig
import io.niko.ktx.isPreview
import io.niko.utils.Updates
import io.niko.utils.compareVersions

/**
 * What this app tells a raycontrol node of itself (client-min-v1 §1, raycontrol docs/spec/client-min-v1.md): the
 * X-Raycontrol-Client header libcore sends on every request to the node and its relays (the subscription, the app's
 * mirror, the channel probe, the remote API), and how this version stands against the node's minimum (§3).
 */
object RayClient {

    const val PRODUCT = "niko"

    private val SEMVER = Regex("""^(0|[1-9]\d*)\.(0|[1-9]\d*)\.(0|[1-9]\d*)(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?$""")
    private val CORE = Regex("""^\d+\.\d+\.\d+""")
    private val PREVIEW = Regex("""^pre-(\d+\.\d+\.\d+)-(\d{8})-(\d+)$""")

    /** This build's version as the node compares it (semver 2.0). */
    val version: String by lazy { version(BuildConfig.VERSION_NAME, if (isPreview) BuildConfig.PRE_VERSION_NAME else "") }

    /**
     * [name] (VERSION_NAME) as semver; a preview's [pre] (PRE_VERSION_NAME, "pre-1.59.0-20261120-1") as
     * 1.59.0-pre.20261120.1: below its release (§9.2), a later preview of the day above the earlier one.
     */
    fun version(name: String, pre: String): String {
        PREVIEW.matchEntire(pre)?.let { m ->
            val (core, day, n) = m.destructured
            return "${number(core)}-pre.${day.toLong()}.${n.toLong()}"
        }
        val core = number(CORE.find(name)?.value ?: "0.0.0")
        return when {
            pre.isNotEmpty() -> "$core-pre"
            SEMVER.matches(name) -> name
            else -> core
        }
    }

    // "01.2.3" is not semver: numbers without leading zeros
    private fun number(core: String) = core.split('.').joinToString(".") { (it.toLongOrNull() ?: 0).toString() }

    /** The header's value: niko/1.59.0 (android; arm64-v8a), the ABI of the installed APK. */
    fun header(context: Context) = header(version, Updates.abis(context).firstOrNull())

    fun header(version: String, abi: String?) =
        "$PRODUCT/$version" + (abi?.takeIf { ABI.matches(it) }?.let { " (android; $it)" } ?: " (android)")

    private val ABI = Regex("[0-9A-Za-z_-]{1,32}")

    /** Whether this [version] is below the node's minimum for Niko in [client]. */
    fun below(client: RayClientMin?, version: String = this.version): Boolean {
        val min = client?.niko ?: return false
        return compareVersions(version, min) < 0
    }
}

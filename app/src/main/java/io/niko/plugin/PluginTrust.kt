package io.niko.plugin

import android.content.pm.PackageManager
import io.niko.R
import io.niko.SagerNet
import io.niko.bg.BaseService
import io.niko.database.DataStore
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Which plugin packages the user trusts, by their signer (S8). A plugin runs under Niko's UID: it reads the
 * profiles it serves and uses the VPN's protect, so any app with a provider of the plugin prefix could slip in its
 * own binary. A package is used only after the user confirmed its signer; a new signer under the same package
 * name (another build, or another app) is asked about again.
 */
object PluginTrust {

    class UntrustedException(val plugin: String, val packageName: String) : Exception(),
        BaseService.ExpectedException {
        override fun getLocalizedMessage() =
            SagerNet.application.getString(R.string.plugin_untrusted, plugin, packageName)
    }

    /** SHA-256 of the package's current signing certificates, hex with colons, sorted and joined by commas. */
    fun signer(packageName: String): String? = try {
        val pm = SagerNet.application.packageManager
        @Suppress("DEPRECATION")
        val signatures = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES).signingInfo?.apkContentsSigners
        } else {
            pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES).signatures
        }
        signatures?.takeIf { it.isNotEmpty() }?.map { digest(it.toByteArray()) }?.sorted()?.joinToString(",")
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    fun digest(der: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(der).joinToString(":") { "%02X".format(it) }

    private fun trusted(): JSONObject = try {
        JSONObject(DataStore.pluginTrust)
    } catch (_: Exception) {
        JSONObject()
    }

    fun isTrusted(packageName: String): Boolean {
        val signer = signer(packageName) ?: return false
        return trusted().optString(packageName) == signer
    }

    /** Throws when the package is not trusted with its current signer. */
    fun require(plugin: String, packageName: String) {
        if (!isTrusted(packageName)) throw UntrustedException(plugin, packageName)
    }

    fun trust(packageName: String) {
        val signer = signer(packageName) ?: return
        DataStore.pluginTrust = trusted().put(packageName, signer).toString()
    }

    fun revoke(packageName: String) {
        DataStore.pluginTrust = trusted().apply { remove(packageName) }.toString()
    }
}

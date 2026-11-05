package io.niko.plugin

import android.content.pm.ComponentInfo
import android.content.pm.ProviderInfo
import io.niko.R
import io.niko.SagerNet
import io.niko.bg.BaseService
import io.niko.ktx.Logs
import io.niko.plugin.Plugins
import java.io.File
import java.io.FileNotFoundException

object PluginManager {

    class PluginNotFoundException(val plugin: String) : FileNotFoundException(plugin),
        BaseService.ExpectedException {
        override fun getLocalizedMessage() =
            SagerNet.application.getString(R.string.plugin_unknown, plugin)
    }

    data class InitResult(
        val path: String,
        val info: ProviderInfo,
    )

    @Throws(Throwable::class)
    fun init(pluginId: String): InitResult? {
        if (pluginId.isEmpty()) return null
        var throwable: Throwable? = null

        try {
            val result = initNative(pluginId)
            if (result != null) return result
        } catch (t: Throwable) {
            throwable = t
            Logs.w(t)
        }

        throw throwable ?: PluginNotFoundException(pluginId)
    }

    private fun initNative(pluginId: String): InitResult? {
        // a binary shipped in the app goes first: nothing outside it to trust
        try {
            initNativeInternal(pluginId)?.let { return InitResult(it, ProviderInfo().apply { authority = Plugins.AUTHORITIES_PREFIX_NEKO_EXE }) }
        } catch (t: Throwable) {
            Logs.w("initNativeInternal failed", t)
        }
        val info = Plugins.getPlugin(pluginId) ?: return null

        // internal so
        if (info.applicationInfo == null) {
            try {
                initNativeInternal(pluginId)?.let { return InitResult(it, info) }
            } catch (t: Throwable) {
                Logs.w("initNativeInternal failed", t)
            }
            return null
        }

        // another app's binary runs as Niko: only from a package whose signer the user trusts (S8)
        PluginTrust.require(pluginId, info.packageName)
        try {
            initNativeFaster(info)?.let { return InitResult(it, info) }
        } catch (t: Throwable) {
            Logs.w("initNativeFaster failed", t)
        }

        Logs.w("Init native returns empty result")
        return null
    }

    private fun initNativeInternal(pluginId: String): String? {
        fun soIfExist(soName: String): String? {
            val f = File(SagerNet.application.applicationInfo.nativeLibraryDir, soName)
            if (f.canExecute()) {
                return f.absolutePath
            }
            return null
        }
        return when (pluginId) {
            "hysteria-plugin" -> soIfExist("libhysteria.so")
            "hysteria2-plugin" -> soIfExist("libhysteria2.so")
            else -> null
        }
    }

    private fun initNativeFaster(provider: ProviderInfo): String? {
        return provider.loadString(Plugins.METADATA_KEY_EXECUTABLE_PATH)
            ?.let { relativePath ->
                // inside the plugin's own library folder: "../" cannot point elsewhere
                val dir = File(provider.applicationInfo.nativeLibraryDir).canonicalFile
                dir.resolve(relativePath).canonicalFile.apply {
                    check(path.startsWith(dir.path + File.separator)) { "plugin binary outside its library folder" }
                    check(canExecute())
                }.absolutePath
            }
    }

    fun ComponentInfo.loadString(key: String) = when (val value = metaData.get(key)) {
        is String -> value
        is Int -> SagerNet.application.packageManager.getResourcesForApplication(applicationInfo)
            .getString(value)

        null -> null
        else -> error("meta-data $key has invalid type ${value.javaClass}")
    }
}

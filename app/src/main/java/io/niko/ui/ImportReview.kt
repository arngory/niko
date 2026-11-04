package io.niko.ui

import android.content.Context
import android.content.Intent
import com.google.gson.JsonParser
import io.niko.R
import io.niko.fmt.AbstractBean
import io.niko.fmt.overridesFullConfig
import io.niko.proxy.config.ConfigBean

/**
 * One check for every import path (link, QR code, clipboard, file): a profile whose JSON goes over the whole config
 * (`customConfigJson`, a full sing-box config) can open a proxy or the Clash API on Wi-Fi, change DNS and routing.
 * Such an import is shown with the top-level keys it sets and asked first.
 */
object ImportReview {

    const val ACTION_REVIEW = "io.niko.action.REVIEW_IMPORT"

    /** The top-level keys of the config the profile sets (merge markers `+` dropped), empty when it sets none. */
    fun keys(bean: AbstractBean): List<String> {
        val json = buildList {
            bean.customConfigJson?.takeIf { it.isNotBlank() }?.let(::add)
            if (bean is ConfigBean && bean.type == 0) bean.config?.takeIf { it.isNotBlank() }?.let(::add)
        }
        return json.flatMap { text ->
            try {
                JsonParser.parseString(text).asJsonObject.keySet().map { it.trim('+') }
            } catch (e: Exception) {
                listOf("?")
            }
        }.distinct().sorted()
    }

    /** The question to ask before importing [proxies], or null when none of them goes beyond its own outbound. */
    fun warning(context: Context, proxies: List<AbstractBean>): String? {
        val risky = proxies.filter { it.overridesFullConfig() }
        if (risky.isEmpty()) return null
        val names = proxies.take(5).joinToString { it.displayName() } + if (proxies.size > 5) ", …" else ""
        return context.getString(R.string.profile_import_message, names) + "\n\n" +
                context.getString(R.string.profile_import_full_config_warning) + "\n\n" +
                context.getString(R.string.profile_import_keys, risky.flatMap(::keys).distinct().sorted().joinToString())
    }

    private val pending = ArrayList<AbstractBean>()

    /** The scanner closes at once: it hands the profiles to [MainActivity], which asks ([take]). */
    fun handOff(context: Context, proxies: List<AbstractBean>) {
        synchronized(pending) { pending.addAll(proxies) }
        context.startActivity(
            Intent(context, MainActivity::class.java).setAction(ACTION_REVIEW).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun take(): List<AbstractBean> = synchronized(pending) { pending.toList().also { pending.clear() } }
}

package io.niko.ui

import io.niko.R
import io.niko.ktx.Logs
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ui.compose.NikoDialog
import org.json.JSONObject
import java.util.Locale

/** The lookup services the exit address is asked of (libcore exitProviders): their ids and the names shown. */
internal val EXIT_PROVIDERS = listOf(
    "cloudflare" to "Cloudflare (cloudflare.com)",
    "ipinfo" to "ipinfo.io",
    "ipwho" to "ipwho.is",
    "ipapi-co" to "ipapi.co",
)

/**
 * The exit address (after ThroneForAndroid's exit lookup; the owner's decision of 2026-11-15: only when asked, saying
 * first that the service learns the node's address): the user picks a lookup service, which the running core asks
 * through the profile, and the address and country it saw are shown. Nothing is sent before the user presses Ask.
 */
fun MainActivity.showExitAddress() {
    NikoDialog(this)
        .title(R.string.exit_address)
        .message(R.string.exit_address_message)
        .choices(EXIT_PROVIDERS.map { it.second })
        .ghost(R.string.dialog_cancel)
        .primary(R.string.exit_address_ask) { _, chosen ->
            val (id, name) = EXIT_PROVIDERS[chosen]
            runOnDefaultDispatcher {
                val text = try {
                    describeExitAddress(JSONObject(exitAddress(id)), name, ::getString)
                } catch (e: Exception) {
                    Logs.w(e)
                    getString(R.string.exit_address_failed, name, e.readableMessage)
                }
                onMainDispatcher {
                    if (isFinishing || isDestroyed) return@onMainDispatcher
                    NikoDialog(this@showExitAddress).title(R.string.exit_address)
                        .message(text, selectable = true)
                        .primary(R.string.dialog_ok)
                        .show()
                }
            }
        }
        .show()
}

/**
 * The service's answer as the user reads it: the address, the country (its name in the phone's language when the
 * service gave only the code) and the city, and which service answered.
 */
internal fun describeExitAddress(answer: JSONObject, providerName: String, string: (Int) -> String): String {
    val code = answer.optString("country_code")
    val country = answer.optString("country").ifEmpty {
        if (code.isNotEmpty()) Locale("", code).getDisplayCountry(Locale.getDefault()).takeIf { it != code }.orEmpty() else ""
    }
    val place = listOf(
        listOf(country, code.takeIf { it.isNotEmpty() && country.isNotEmpty() }?.let { "($it)" }.orEmpty())
            .filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { code },
        answer.optString("city"),
    ).filter { it.isNotEmpty() }.joinToString(", ")
    return listOf(answer.optString("ip"), place, string(R.string.exit_address_answered) + " " + providerName)
        .filter { it.isNotEmpty() }.joinToString("\n")
}

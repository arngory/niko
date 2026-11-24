package io.niko.ktx

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.fragment.app.Fragment
import io.niko.R
import io.niko.ui.compose.NikoDialog

fun Context.alert(text: String): NikoDialog =
    NikoDialog(this).title(R.string.error_title).message(text).primary(android.R.string.ok)

fun Fragment.alert(text: String) = requireContext().alert(text)

/**
 * The first of [start], then what [next] gives for it and so on, that [match] takes: at most [depth] steps, so a
 * chain that loops ends. For an Activity under a context's wrappers.
 */
internal inline fun <T : Any> firstInChain(start: T, depth: Int = 16, next: (T) -> T?, match: (T) -> Boolean): T? {
    var current: T? = start
    repeat(depth) {
        val at = current ?: return null
        if (match(at)) return at
        val following = next(at)
        if (following === at) return null
        current = following
    }
    return null
}

/** The Activity under the wrappers of a context: a dialog's context is a ContextThemeWrapper around it. */
fun Context.findActivity(): Activity? =
    firstInChain(this, next = { (it as? ContextWrapper)?.baseContext }, match = { it is Activity }) as Activity?

fun NikoDialog.tryToShow() {
    // the context may be a wrapper around the Activity: a cast to it failed for every dialog, and none was
    // shown (an error of NekoBox's, ThroneForAndroid ac1f62f)
    val activity = context.findActivity()
    if (activity == null || activity.isFinishing || activity.isDestroyed) {
        Logs.w("a dialog not shown: ${if (activity == null) "no activity under ${context.javaClass.name}" else "its activity is going"}")
        return
    }
    try {
        show()
    } catch (e: Exception) {
        Logs.e(e)
    }
}

package io.niko.ktx

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.widget.FrameLayout
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.R

fun Context.alert(text: String): AlertDialog {
    return MaterialAlertDialogBuilder(this).setTitle(R.string.error_title)
        .setMessage(text)
        .setPositiveButton(android.R.string.ok, null)
        .create()
}

fun Fragment.alert(text: String) = requireContext().alert(text)

/** A confirmation that deletes or resets (m-dialog's danger action): its positive button in danger, after show(). */
fun AlertDialog.danger(): AlertDialog = apply {
    getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(ContextCompat.getColor(context, R.color.niko_danger))
}

/** [view], a dialog's one field, inset as the dialog's title and words are (space-6), space-2 below them. */
fun MaterialAlertDialogBuilder.setField(view: View): MaterialAlertDialogBuilder = setView(FrameLayout(context).apply {
    val side = resources.getDimensionPixelSize(R.dimen.niko_space_6)
    setPadding(side, resources.getDimensionPixelSize(R.dimen.niko_space_2), side, 0)
    addView(view)
})

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

fun AlertDialog.tryToShow() {
    // the context of a dialog is never the Activity itself: a cast to it failed for every dialog, and none was
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

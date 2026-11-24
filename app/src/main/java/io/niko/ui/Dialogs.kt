package io.niko.ui

import android.content.Context
import io.niko.R
import io.niko.ktx.Logs
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnMainDispatcher
import io.niko.ui.compose.NikoDialog
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object Dialogs {
    fun logExceptionAndShow(context: Context, e: Exception, callback: Runnable) {
        Logs.e(e)
        runOnMainDispatcher {
            NikoDialog(context)
                .title(R.string.error_title)
                .message(e.readableMessage)
                .cancelable(false)
                .primary(R.string.dialog_ok) { _, _ -> callback.run() }
                .show()
        }
    }

    /**
     * Asks for the PIN of an invite to [host] (6 digits the administrator told apart from it): what the user typed,
     * or null when they cancel. [left]: the tries the invite has left, [wrong]: the last PIN was wrong. On the main
     * thread. The digits stay visible: a typo costs one of five tries.
     */
    suspend fun askPin(context: Context, host: String, left: Int?, wrong: Boolean): String? =
        suspendCancellableCoroutine { continuation ->
            var answered = false
            val answer = { pin: String? ->
                if (!answered) {
                    answered = true
                    continuation.resume(pin)
                }
            }
            val message = buildString {
                append(context.getString(R.string.raycontrol_invite_pin_message))
                if (wrong && left != null) append("\n\n").append(context.getString(R.string.raycontrol_invite_pin_wrong, left))
            }
            NikoDialog(context)
                .title(context.getString(R.string.raycontrol_invite_pin_title, host))
                .message(message)
                .field(hint = "000000", digits = 6)
                .ghost(R.string.dialog_cancel) { answer(null) }
                .primary(R.string.dialog_ok) { pin, _ -> answer(pin) }
                .onDismiss { answer(null) }
                .show()
        }

    fun message(context: Context, title: String, message: String) {
        runOnMainDispatcher {
            NikoDialog(context).title(title).message(message, selectable = true).show()
        }
    }
}

package io.niko.ui

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.R
import io.niko.ktx.Logs
import io.niko.ktx.dp2px
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnMainDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object Dialogs {
    fun logExceptionAndShow(context: Context, e: Exception, callback: Runnable) {
        Logs.e(e)
        runOnMainDispatcher {
            MaterialAlertDialogBuilder(context)
                .setTitle(R.string.error_title)
                .setMessage(e.readableMessage)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    callback.run()
                }
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
            val input = EditText(context).apply {
                inputType = InputType.TYPE_CLASS_NUMBER
                filters = arrayOf(InputFilter.LengthFilter(6))
                hint = "000000"
                isSingleLine = true
            }
            val frame = FrameLayout(context).apply {
                setPadding(dp2px(24), dp2px(8), dp2px(24), 0)
                addView(input)
            }
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
            MaterialAlertDialogBuilder(context)
                .setTitle(context.getString(R.string.raycontrol_invite_pin_title, host))
                .setMessage(message)
                .setView(frame)
                .setPositiveButton(android.R.string.ok) { _, _ -> answer(input.text.toString()) }
                .setNegativeButton(android.R.string.cancel) { _, _ -> answer(null) }
                .setOnDismissListener { answer(null) }
                .show()
        }

    fun message(context: Context, title: String, message: String) {
        runOnMainDispatcher {
            val dialog = MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setCancelable(true)
                .show()
            dialog.findViewById<TextView>(android.R.id.message)?.apply {
                setTextIsSelectable(true)
            }
        }
    }
}
package io.niko.raycontrol

import android.content.Context
import io.niko.R
import io.niko.ktx.Logs
import io.niko.ktx.readableMessage

/** Import of raycontrol packages from any text: the clipboard, a file, a QR code, a link. */
object RayImport {

    fun matches(text: String) = Rcinv1.matches(text) || Rcdev1.matches(text)

    /**
     * Imports the invite (`rcinv1:`, registers this device) or the device package (`rcdev1:`) found in [text], then
     * fetches the node's subscription. [show] gets each line for the user as it comes: the registration and the
     * fetch take seconds. [context] is the application context.
     */
    suspend fun import(context: Context, text: String, show: suspend (String) -> Unit) {
        val node = if (Rcinv1.matches(text)) {
            val invite = Rcinv1.parse(text)
            show(context.getString(R.string.raycontrol_invite_registering, invite.host))
            val (node, role) = try {
                RayApi.register(invite)
            } catch (e: Exception) {
                Logs.w(e)
                show(failure(context, e))
                return
            }
            show(context.getString(R.string.raycontrol_invite_registered, node.host, role))
            node
        } else {
            RayNodes.import(Rcdev1.parse(text)).also {
                show(context.getString(R.string.raycontrol_node_imported, it.host, it.services.size))
            }
        }
        if (node.subscription != null) show(RaySubscription.refreshForUser(context, node))
    }

    /** A failed registration for the user (api-v1.md §4, §7). */
    fun failure(context: Context, e: Throwable): String = when {
        // used, expired, revoked, a wrong key, or the clock off by more than 2 minutes
        RayApi.code(e) == "not_found" -> context.getString(R.string.raycontrol_invite_refused)
        e.message?.contains("did not answer") == true ->
            context.getString(R.string.raycontrol_invite_failed, e.readableMessage) + " " +
                context.getString(R.string.raycontrol_clock_hint)

        else -> context.getString(R.string.raycontrol_invite_failed, e.readableMessage)
    }
}

package io.niko.raycontrol

import android.content.Context
import io.niko.R
import io.niko.ktx.Logs
import io.niko.ktx.readableMessage

/** Import of raycontrol packages from any text: the clipboard, a file, a QR code, a link. */
object RayImport {

    fun matches(text: String) = Rcinv1.matches(text) || Rcdev1.matches(text)

    /**
     * The bare token in [text], e.g. to pass a scanned QR code on as a link: the first candidate that parses, so a
     * token wrapped over lines comes out whole.
     */
    fun token(text: String): String {
        val invite = Rcinv1.matches(text)
        val all = if (invite) Rcinv1.candidates(text) else Rcdev1.candidates(text)
        return all.firstOrNull { runCatching { if (invite) Rcinv1.parse(it) else Rcdev1.parse(it) }.isSuccess } ?: all.first()
    }

    /**
     * The question to ask before importing [text] when it replaces a stored node (same node ID): the device gets
     * another key and loses the current one and its admin access. A node with another address is called out (a
     * moved node is legitimate; a link from someone else is not). Null when it adds a node, or re-imports the same
     * device package. Throws IllegalArgumentException for a broken package.
     */
    fun replaceQuestion(context: Context, text: String): String? {
        val (nodeId, host, deviceId) = if (Rcinv1.matches(text)) {
            Rcinv1.parse(text).let { Triple(it.spa.nodeId, it.host, null) }
        } else {
            Rcdev1.parse(text).let { Triple(it.spa.nodeId, it.host, it.deviceId) }
        }
        val known = RayNodes.get(nodeId) ?: return null
        val sameHost = known.host.equals(host, ignoreCase = true)
        if (sameHost && deviceId == known.deviceId) return null
        return if (sameHost) context.getString(R.string.raycontrol_import_replace, host)
        else context.getString(R.string.raycontrol_import_moved, known.host, host)
    }

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
        // the request reached the node: the invite may be used by this very registration (rayapi.ErrAnswerLost)
        e.message?.contains("answer was lost") == true -> context.getString(R.string.raycontrol_invite_lost)
        e.message?.contains("did not answer") == true ->
            context.getString(R.string.raycontrol_invite_failed, e.readableMessage) + " " +
                context.getString(R.string.raycontrol_clock_hint)

        else -> context.getString(R.string.raycontrol_invite_failed, e.readableMessage)
    }
}

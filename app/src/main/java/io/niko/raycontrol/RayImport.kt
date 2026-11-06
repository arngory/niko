package io.niko.raycontrol

import android.content.Context
import io.niko.R
import io.niko.ktx.Logs
import io.niko.ktx.readableMessage

/** Import of raycontrol packages from any text: the clipboard, a file, a QR code, a link. */
object RayImport {

    fun matches(text: String) = isInvite(text) || Rcdev1.matches(text)

    /** An invite in [text]: rcinv1, or the short rcinv2 (api-v1.md §3a). */
    fun isInvite(text: String) = Rcinv2.matches(text) || Rcinv1.matches(text)

    /** The invite in [text], either format. Throws IllegalArgumentException with a readable reason. */
    fun parseInvite(text: String): RayInvite = if (Rcinv2.matches(text)) Rcinv2.parse(text) else Rcinv1.parse(text)

    /**
     * The bare token in [text], e.g. to pass a scanned QR code on as a link: the first candidate that parses, so a
     * token wrapped over lines comes out whole.
     */
    fun token(text: String): String {
        if (Rcinv2.matches(text)) return Rcinv2.token(text)
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
        val (nodeId, host, deviceId) = if (isInvite(text)) {
            parseInvite(text).let { Triple(it.spa.nodeId, it.host, null) }
        } else {
            Rcdev1.parse(text).let { Triple(it.spa.nodeId, it.host, it.deviceId) }
        }
        val known = RayNodes.get(nodeId) ?: return null
        val sameHost = known.host.equals(host, ignoreCase = true)
        if (sameHost && deviceId == known.deviceId) return null
        return if (sameHost) context.getString(R.string.raycontrol_import_replace, host)
        else context.getString(R.string.raycontrol_import_moved, known.host, host)
    }

    /** Asks the user for an invite's PIN: the host, the tries left (null: unknown), whether the last one was wrong. */
    fun interface PinAsker {
        suspend fun ask(host: String, left: Int?, wrong: Boolean): String?
    }

    /**
     * Imports the invite (`rcinv1:`, registers this device) or the device package (`rcdev1:`) found in [text], then
     * fetches the node's subscription. [show] gets each line for the user as it comes: the registration and the
     * fetch take seconds. [askPin] asks for the PIN of an invite that needs one (api-v1.md §3, §4): before the
     * registration when the invite says so, and again after a wrong one. [context] is the application context.
     */
    suspend fun import(context: Context, text: String, askPin: PinAsker, show: suspend (String) -> Unit) {
        val node = if (isInvite(text)) {
            val invite = parseInvite(text)
            // 6 digits, asked again until they are, or null when the user gives up
            suspend fun pin(left: Int?, wrong: Boolean): String? {
                while (true) {
                    val pin = askPin.ask(invite.host, left, wrong)?.trim() ?: return null
                    if (pin.length == 6 && pin.all { it in '0'..'9' }) return pin
                    show(context.getString(R.string.raycontrol_invite_pin_format))
                }
            }
            var pin: String? = null
            if (invite.pinRequired) {
                pin = pin(null, wrong = false) ?: return show(context.getString(R.string.raycontrol_invite_pin_cancelled))
            }
            var registered: Pair<RayNode, String>? = null
            while (registered == null) {
                show(context.getString(R.string.raycontrol_invite_registering, invite.host))
                try {
                    registered = RayApi.register(invite, pin)
                } catch (e: Exception) {
                    Logs.w(e)
                    // the node asks for the PIN (none sent, or a wrong one): it tells how many tries are left
                    val left = RayApi.pinLeft(e) ?: return show(failure(context, e))
                    if (left <= 0) return show(context.getString(R.string.raycontrol_invite_pin_withdrawn))
                    pin = pin(left, wrong = pin != null) ?: return show(context.getString(R.string.raycontrol_invite_pin_cancelled))
                }
            }
            val (node, role) = registered
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

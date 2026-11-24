package io.niko.ui.admin

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import io.niko.R
import io.niko.raycontrol.RayApi
import io.niko.raycontrol.RayNode
import io.niko.utils.isNewerVersion

// What the admin panel says of the node's answers (api-v1.md §5), apart from the screen: moved as it was from
// RayAdminActivity, with the lines its dialogs built, so that tests read them without a device.

/** A string with its arguments, as the activity's getString gives it: the parts below take one, and tests their own. */
internal class AdminTexts(private val get: (Int, Array<out Any>) -> String) {
    operator fun invoke(id: Int, vararg args: Any) = get(id, args)
}

/**
 * What a revocation did, from RevokeDeviceResponse (raycontrol v0.27.0): an unused invite withdrawn, a device revoked
 * already, or the device revoked and its user's credentials new, which cuts the user's other devices until they fetch
 * their subscription. Null from a node before v0.27.0, which answers with none of it and changes no credentials.
 */
internal fun revokeText(answer: JsonObject, user: String, name: String, t: AdminTexts): String? {
    if (answer.b("invite")) return t(R.string.raycontrol_admin_revoked_invite, user, name)
    val owner = answer.s("user").ifEmpty { return null }
    val device = answer.s("device").ifEmpty { name }
    val credentials = answer.l("credentials")
    return when {
        answer.b("already") -> t(R.string.raycontrol_admin_revoked_already, owner, device)
        credentials == 0L -> t(R.string.raycontrol_admin_revoked_no_credentials, owner, device)
        else -> t(R.string.raycontrol_admin_revoked_renewed, owner, device, credentials)
    }
}

/** What RenewUser did: how many of the user's credentials are new; none for a user who had none yet. */
internal fun renewText(answer: JsonObject, user: String, t: AdminTexts): String {
    val name = answer.s("name").ifEmpty { user }
    val credentials = answer.l("credentials")
    return if (credentials == 0L) t(R.string.raycontrol_admin_renewed_none, name) else t(R.string.raycontrol_admin_renewed, name, credentials)
}

/**
 * The node refused to disable or delete its last enabled ssh service unforced (raycontrol v0.27.0): the one
 * failed_precondition of those calls that a force answers. Another service's refusal is not asked about.
 */
internal fun lastSshRefused(kind: String, code: String?, force: Boolean) = !force && kind == "ssh" && code == "failed_precondition"

/**
 * A REALITY target as typed, HOST:PORT (a name or an address, an IPv6 one in brackets, a port 1 to 65535), trimmed;
 * null when it is not of that form. The form only: the node checks the target itself and says why it refuses one.
 */
internal fun realityTarget(text: String): String? {
    val value = text.trim()
    val host: String
    val port: String
    if (value.startsWith("[")) {
        val end = value.indexOf(']')
        if (end < 0 || value.getOrNull(end + 1) != ':') return null
        host = value.substring(1, end)
        port = value.substring(end + 2)
        if (':' !in host || !host.all { it in '0'..'9' || it.lowercaseChar() in 'a'..'f' || it == ':' || it == '.' }) return null
    } else {
        host = value.substringBeforeLast(':', "")
        port = value.substringAfterLast(':', "")
        // an IPv6 address goes in brackets, as Go's SplitHostPort on the node wants it
        if (host.isEmpty() || !host.all { it.isLetterOrDigit() || it == '.' || it == '-' || it == '_' }) return null
    }
    if (port.isEmpty() || port.length > 5 || !port.all { it in '0'..'9' } || port.toInt() !in 1..65535) return null
    return value
}

/** A node before v0.27.0 refuses the target as an option it does not have (provision's "has no option"). */
internal fun targetUnknown(message: String?) = message?.contains("no option \"target\"") == true

/** The node's own words in an error of its API: without the core's "raycontrol api: CODE: " in front. */
internal fun nodeMessage(message: String): String =
    Regex("^raycontrol api: [a-z_]+: ").find(message)?.let { message.substring(it.range.last + 1) }?.ifBlank { null } ?: message

/**
 * A port of the node, or a range of them (GetPorts' NodePort), in a line for a provider's firewall: the protocols and
 * the ports, the name, who reaches it and how it was set; for a rotating range, its port now and until when ([time]
 * of unix seconds).
 */
internal fun portLine(port: JsonObject, t: AdminTexts, time: (Long) -> String): String {
    val from = port.l("from")
    val to = port.l("to")
    val span = if (to > from) "$from-$to" else "$from"
    val access = when (val a = port.s("access")) {
        "everyone" -> t(R.string.raycontrol_admin_port_everyone)
        "spa" -> t(R.string.raycontrol_admin_port_spa)
        "admin" -> t(R.string.raycontrol_admin_port_admin)
        "acme" -> t(R.string.raycontrol_admin_port_acme)
        else -> a
    }
    val source = when (val s = port.s("source")) {
        "fixed" -> t(R.string.raycontrol_admin_port_fixed)
        "random" -> t(R.string.raycontrol_admin_port_random)
        "chosen" -> t(R.string.raycontrol_admin_port_chosen)
        "sshd" -> t(R.string.raycontrol_admin_port_sshd)
        else -> s
    }
    val protos = port.strs("protos").joinToString("+") { it.uppercase() }
    var line = "$protos $span ${port.s("name")}: " + listOf(access, source).filter { it.isNotEmpty() }.joinToString(", ")
    val current = port.l("current")
    if (current > 0) line += "; " + t(R.string.raycontrol_admin_port_current, current, time(port.l("currentUntil")))
    return line
}

/**
 * The newest patch release of the node's minor version (GetUpdateStatus latest_patch, raycontrol v0.27.0), which the
 * auto update takes; null without one, or when it is the latest release, which the summary names already.
 */
internal fun latestPatch(update: JsonObject): String? = update.s("latestPatch").takeIf {
    it.isNotEmpty() && it != update.s("latest") && isNewerVersion(it, update.s("installed"))
}

// protobuf JSON as the node answers: int64 as strings, defaults (false, 0, empty) left out
internal fun JsonObject.s(name: String): String = get(name)?.takeIf { it.isJsonPrimitive }?.asString.orEmpty()
internal fun JsonObject.l(name: String): Long = get(name)?.takeIf { it.isJsonPrimitive }?.asString?.toLongOrNull() ?: 0L
internal fun JsonObject.b(name: String): Boolean = get(name)?.takeIf { it.isJsonPrimitive }?.asBoolean == true
internal fun JsonObject.arr(name: String): List<JsonObject> = (get(name) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
internal fun JsonObject.strs(name: String): List<String> = (get(name) as? JsonArray)?.map { it.asString }.orEmpty()

// ---- the relays' updates (relay-update-remote-v1 §8, raycontrol v0.28.0)

/** The states a relay leaves by itself, each within its bound: while one is in them, the update is still going. */
private val RELAY_PENDING = setOf("waiting", "offered", "requested", "running")

/** A relay is still on its way to the node's version (GetUpdateStatusResponse.relays). */
internal fun relaysPending(update: JsonObject) = update.arr("relays").any { it.s("state") in RELAY_PENDING }

/** How many relays are in which state, in a line; null without relays (or from a node before v0.28.0). */
internal fun relaysSummary(update: JsonObject, t: AdminTexts): String? {
    val relays = update.arr("relays").takeIf { it.isNotEmpty() } ?: return null
    // by the state in words: one the cleaning leaves empty counts as unknown
    val counts = relays.groupingBy { relayState(it.s("state"), t) }.eachCount()
    return t(R.string.raycontrol_admin_relays_count, counts.entries.joinToString(", ") { (state, n) -> "$state $n" })
}

/** A relay's state in words; a state this build does not know (a newer node) as the node says it, in one line. */
internal fun relayState(state: String, t: AdminTexts): String = when (state) {
    "waiting" -> t(R.string.raycontrol_admin_relay_waiting)
    "offered" -> t(R.string.raycontrol_admin_relay_offered)
    "requested" -> t(R.string.raycontrol_admin_relay_requested)
    "running" -> t(R.string.raycontrol_admin_relay_running)
    "done" -> t(R.string.raycontrol_admin_relay_done)
    "rolled_back" -> t(R.string.raycontrol_admin_relay_rolled_back)
    "failed" -> t(R.string.raycontrol_admin_relay_failed)
    "current" -> t(R.string.raycontrol_admin_relay_current)
    "off" -> t(R.string.raycontrol_admin_relay_off)
    "unknown", "" -> t(R.string.raycontrol_admin_relay_unknown)
    // nothing left of it once cleaned: as good as no state
    else -> relayText(state, RELAY_WORD).ifEmpty { t(R.string.raycontrol_admin_relay_unknown) }
}

/**
 * Why a relay's update did not go (§3); a code this build does not know (a relay newer than its node) as it is, in one
 * line.
 */
internal fun relayReason(reason: String, t: AdminTexts): String = when (reason) {
    "policy" -> t(R.string.raycontrol_admin_relay_reason_policy)
    "stale" -> t(R.string.raycontrol_admin_relay_reason_stale)
    "version" -> t(R.string.raycontrol_admin_relay_reason_version)
    "busy" -> t(R.string.raycontrol_admin_relay_reason_busy)
    "unfinished" -> t(R.string.raycontrol_admin_relay_reason_unfinished)
    "bundle" -> t(R.string.raycontrol_admin_relay_reason_bundle)
    "ssh" -> t(R.string.raycontrol_admin_relay_reason_ssh)
    "download" -> t(R.string.raycontrol_admin_relay_reason_download)
    "signature" -> t(R.string.raycontrol_admin_relay_reason_signature)
    "place" -> t(R.string.raycontrol_admin_relay_reason_place)
    "install" -> t(R.string.raycontrol_admin_relay_reason_install)
    "check" -> t(R.string.raycontrol_admin_relay_reason_check)
    "interrupted" -> t(R.string.raycontrol_admin_relay_reason_interrupted)
    "open" -> t(R.string.raycontrol_admin_relay_reason_open)
    "not_taken" -> t(R.string.raycontrol_admin_relay_reason_not_taken)
    "no_word" -> t(R.string.raycontrol_admin_relay_reason_no_word)
    else -> relayText(reason, RELAY_WORD)
}

/** A word of the node's this build does not know (a state, a reason) is shown up to this many characters. */
private const val RELAY_WORD = 32

/**
 * A relay's text as the node passes it on ([raw]: its name and version, what it said of a failure, which v0.28.1 makes
 * the last line of rayctl's error on the relay, up to 200 bytes) in one line: the note's rules (control and format
 * characters go, line breaks become a space, more than [max] characters end in "…"), so a relay cannot add lines of
 * its own or turn the text around it.
 */
private fun relayText(raw: String, max: Int): String = RayNode.nodeNote(raw, max).orEmpty()

/**
 * A relay not heard for longer than this is marked so, whatever its state says: well above the 10 minutes the node
 * leaves between refreshes of a connected relay's seen_at (relay-update-remote-v1 §8.3), and above a few minutes of
 * the phone's clock against the node's, which the answer does not carry.
 */
private const val RELAY_QUIET_SECONDS = 30 * 60L

/** The node's warnings shown: up to this many, each up to [WARNING_MAX] characters. */
private const val MAX_WARNINGS = 16
private const val WARNING_MAX = 300

/**
 * The node's warnings to its admin (GetStatus `warnings`, raycontrol v0.35.1; today: Hysteria2 without a Brutal
 * ceiling), each its own line as the node words it. Cleaned as the note is (control and format characters go, line
 * breaks become a space, a long one ends in "…") and isolated, so a warning cannot add lines or turn the text around it;
 * one of nothing but such characters, or no string, is left out.
 */
internal fun warningLines(status: JsonObject): List<String> =
    (status.get("warnings") as? JsonArray)?.toList().orEmpty()
        .mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.asString }
        .mapNotNull { RayNode.nodeNote(it, WARNING_MAX)?.takeIf { line -> line.isNotEmpty() } }
        .take(MAX_WARNINGS)
        .map { "\u2068$it\u2069" }

/** A relay's name, version and target are shown up to this many characters; its error up to the node's 200 bytes. */
private const val RELAY_VERSION = 64
private const val RELAY_ERROR = 200

/**
 * A relay's lines: its name, version, state, the version it is offered and since when; why it failed (the reason in
 * words, the error as the node gives it); that the node has not heard it for a while; and what root on the relay can
 * do for one that is off or unknown. The relay's times are unix seconds by the node's clock, [now] the phone's (the
 * answer carries no time of the node's), which [RELAY_QUIET_SECONDS] leaves room for; [time] shows a time.
 */
internal fun relayLine(relay: JsonObject, t: AdminTexts, now: Long, time: (Long) -> String): String {
    // cleaned as the line shows it: a state of control characters alone is no state, and gets unknown's hint
    val state = relayText(relay.s("state"), RELAY_WORD)
    val version = relayText(relay.s("version"), RELAY_VERSION)
    val shown = version.ifEmpty { t(R.string.raycontrol_admin_relay_no_version) }
    var head = "${relayText(relay.s("name"), RELAY_VERSION)} · $shown · ${relayState(state, t)}"
    relayText(relay.s("target"), RELAY_VERSION).takeIf { it.isNotEmpty() && it != version }?.let { head += " → $it" }
    relay.l("changedAt").takeIf { it > 0 }?.let { head += " · " + t(R.string.raycontrol_admin_relay_since, time(it)) }
    val lines = mutableListOf(head)
    val reason = relayReason(relay.s("reason"), t).takeIf { it.isNotEmpty() }
    // the relay's own words, isolated from the line's direction
    val error = relayText(relay.s("error"), RELAY_ERROR).takeIf { it.isNotEmpty() }?.let { "\u2068$it\u2069" }
    listOfNotNull(reason, error).takeIf { it.isNotEmpty() }?.let { lines += it.joinToString(": ") }
    val seen = relay.l("seenAt")
    when {
        seen == 0L -> lines += t(R.string.raycontrol_admin_relay_never_heard)
        now - seen > RELAY_QUIET_SECONDS -> lines += t(R.string.raycontrol_admin_relay_last_heard, time(seen))
    }
    when (state) {
        "off" -> lines += t(R.string.raycontrol_admin_relay_hint_off)
        "unknown", "" -> lines += t(R.string.raycontrol_admin_relay_hint_unknown)
    }
    return lines.joinToString("\n")
}

// ---- the lines of the panel's rows, as the old screen's dialogs built them

/** rayapi.ErrAnswerLost: sent once, not again, and it may have taken effect. */
internal fun answerLost(e: Throwable) = e.message?.contains("answer was lost") == true

/** An error of the node's API in words: no rights, not known any more, the answer lost; else [other]. */
internal fun failureText(e: Throwable, t: AdminTexts, other: (Throwable) -> String): String = when {
    RayApi.code(e) == "permission_denied" -> t(R.string.raycontrol_admin_denied)
    RayApi.code(e) == "unauthenticated" -> t(R.string.raycontrol_admin_unauthenticated)
    answerLost(e) -> t(R.string.raycontrol_admin_lost)
    else -> other(e)
}

/** A note as clients show it: cleaned, in an isolate; "none" for none. */
internal fun noteShown(note: String, t: AdminTexts) =
    RayNode.nodeNote(note)?.let { "\u2068$it\u2069" } ?: t(R.string.raycontrol_admin_node_note_none)

/**
 * A note as typed, cleaned whole as clients show one (not cut: a note longer than the node takes comes back to be
 * shortened), and its length in characters.
 */
internal fun noteTyped(typed: String): Pair<String, Int> {
    val note = RayNode.nodeNote(typed, Int.MAX_VALUE).orEmpty()
    return note to note.codePointCount(0, note.length)
}

/** Addresses as typed, a line, a comma or a space apart. */
internal fun cidrs(text: String) = text.split('\n', ',', ' ').map { it.trim() }.filter { it.isNotEmpty() }

/** A version as typed, vX.Y.Z with or without the v; null when it is not one. */
internal fun updateVersion(text: String): String? =
    text.trim().let { if (it.startsWith("v")) it else "v$it" }.takeIf { Regex("v[0-9]+\\.[0-9]+\\.[0-9]+").matches(it) }

/** The release an update would take: the latest when it is newer than the node's, else null ("the latest", asked). */
internal fun newerRelease(update: JsonObject): String? =
    update.s("latest").takeIf { it.isNotEmpty() && isNewerVersion(it, update.s("installed")) }

/** The last update in a line; null before any (idle). */
internal fun updateState(status: JsonObject, t: AdminTexts, date: (Long) -> String): String? {
    val state = status.s("state").ifEmpty { "idle" }
    val release = status.s("release").ifEmpty { status.s("target") }
    val line = when (state) {
        "idle" -> return null
        "requested" -> t(R.string.raycontrol_admin_update_requested, release, status.s("by"))
        "running" -> t(R.string.raycontrol_admin_update_running, status.s("from"), release)
        "done" -> t(R.string.raycontrol_admin_update_done, status.s("from"), status.s("to"), date(status.l("endedAt")))
        "rolled_back" -> t(R.string.raycontrol_admin_update_rolled_back, release, status.s("from"))
        "failed" -> t(R.string.raycontrol_admin_update_failed, release)
        else -> state
    }
    val error = status.s("error").takeIf { it.isNotBlank() && state in setOf("rolled_back", "failed") }
    return if (error != null) "$line: $error" else line
}

/** The node's update in lines: installed and latest, the patch, by request or not, the auto update, held, relays. */
internal fun updateSummary(update: JsonObject, t: AdminTexts, date: (Long) -> String): String {
    val installed = update.s("installed")
    val latest = newerRelease(update)
    val parts = mutableListOf(
        if (latest != null) t(R.string.raycontrol_admin_update_available, installed, latest)
        else t(R.string.raycontrol_admin_update_current, installed),
        t(if (update.s("remote") == "on") R.string.raycontrol_admin_update_remote_on else R.string.raycontrol_admin_update_remote_off),
    )
    latestPatch(update)?.let { parts.add(1, t(R.string.raycontrol_admin_update_patch, installed, it)) }
    if (update.s("auto") == "patch") parts += t(R.string.raycontrol_admin_update_auto, update.s("window"), update.s("timezone"))
    update.getAsJsonObject("status")?.let { updateState(it, t, date) }?.let { parts += it }
    update.strs("held").takeIf { it.isNotEmpty() }?.let { parts += t(R.string.raycontrol_admin_update_held, it.joinToString(", ")) }
    relaysSummary(update, t)?.let { parts += it }
    return parts.joinToString("\n")
}

/** The node's own update is asked for or under way. */
internal fun nodeUpdating(update: JsonObject) = update.getAsJsonObject("status")?.s("state") in setOf("requested", "running")

/** The relays' lines (relay-update-remote-v1 §8), with what they are about first; null without relays. */
internal fun relaysText(update: JsonObject, t: AdminTexts, now: Long, time: (Long) -> String): String? {
    val relays = update.arr("relays").takeIf { it.isNotEmpty() } ?: return null
    return t(R.string.raycontrol_admin_relays_help) + "\n\n" + relays.joinToString("\n\n") { relayLine(it, t, now, time) }
}

/** The app's releases on the node (app-update-v1): off, or what it holds, when it checked and what went wrong. */
internal fun mirrorSummary(mirror: JsonObject, t: AdminTexts, date: (Long) -> String, size: (Long) -> String): String {
    if (!mirror.b("enabled")) return t(R.string.raycontrol_admin_mirror_off)
    val held = mirror.arr("releases").joinToString(", ") { r -> "${r.s("tag")} (${size(r.l("bytes"))})" }
        .ifEmpty { t(R.string.raycontrol_admin_mirror_empty) }
    val parts = mutableListOf(t(R.string.raycontrol_admin_mirror_on, held))
    parts += t(R.string.raycontrol_admin_mirror_checked, date(mirror.l("checked")))
    mirror.s("error").takeIf { it.isNotBlank() }?.let { parts += t(R.string.raycontrol_admin_mirror_error, it) }
    return parts.joinToString("\n")
}

/** Client summaries (probe-v1 §6): off, or the networks, the reports of small ones, the days. */
internal fun summarySummary(summary: JsonObject, t: AdminTexts): String {
    if (!summary.b("enabled")) return t(R.string.raycontrol_admin_summary_off)
    return t(R.string.raycontrol_admin_summary_on, summary.arr("networks").size, summary.l("otherReports"),
        summary.l("days").takeIf { it > 0 } ?: 7)
}

/** The summaries' networks, a line each; empty without any. */
internal fun summaryNetworks(summary: JsonObject) = summary.arr("networks").joinToString("\n") { n ->
    "AS${n.l("asn")} ${n.s("name")} ${n.s("country")}: ${n.l("reports")}"
}

/** The metrics' samples only: the HELP and TYPE lines double the size. */
internal fun metricSamples(text: String) =
    text.lines().filter { it.isNotBlank() && !it.startsWith("#") }.joinToString("\n").ifEmpty { text }

/** A user's row: the services, the expiry, the limit, the note. */
internal fun userFacts(user: JsonObject, t: AdminTexts, date: (Long) -> String, size: (Long) -> String, services: Boolean = true): String {
    val parts = mutableListOf<String>()
    if (services) parts += user.strs("services").joinToString(", ").ifEmpty { t(R.string.raycontrol_admin_no_services) }
    parts += user.l("expiresAt").let { if (it == 0L) t(R.string.raycontrol_admin_no_expiry) else t(R.string.raycontrol_admin_expires, date(it)) }
    parts += user.l("trafficLimitBytes").let { if (it == 0L) t(R.string.raycontrol_admin_no_limit) else t(R.string.raycontrol_admin_limit, size(it)) }
    user.s("note").takeIf { it.isNotBlank() }?.let { parts += it }
    return parts.joinToString(" · ")
}

/** The new user's request (CreateUser): the expiry from the days, the limit from the gigabytes; int64 as strings. */
internal fun newUserBody(name: String, days: String, gb: String, note: String, services: List<String>, now: Long) = JsonObject().apply {
    val expires = days.trim().toLongOrNull()?.takeIf { it > 0 }?.let { now + it * 86400 } ?: 0L
    val limit = gb.trim().toLongOrNull()?.takeIf { it > 0 }?.let { it * 1_000_000_000L } ?: 0L
    addProperty("name", name)
    addProperty("expiresAt", expires.toString())
    addProperty("trafficLimitBytes", limit.toString())
    addProperty("note", note.trim())
    add("services", JsonArray().apply { services.forEach { add(it) } })
}

/** The user's devices, then the invites not used yet (ListDevices). */
internal fun deviceRows(list: JsonObject, t: AdminTexts, date: (Long) -> String): List<DeviceRow> =
    list.arr("devices").map { d ->
        val revoked = d.l("revokedAt") != 0L
        val state = t(if (revoked) R.string.raycontrol_admin_revoked else R.string.raycontrol_admin_active)
        val via = if (d.b("registered")) " · " + t(R.string.raycontrol_admin_registered) else ""
        DeviceRow(d.s("id"), d.s("name"), "${d.s("name")} · ${d.s("role")}", "$state$via · ${date(d.l("createdAt"))}", revocable = !revoked)
    } + list.arr("invites").map { inv ->
        DeviceRow(inv.s("id"), inv.s("name"), "${inv.s("name")} · ${inv.s("role")}",
            t(R.string.raycontrol_admin_invite_pending, date(inv.l("expiresAt"))), invite = true)
    }

/**
 * A setting of a service that may change after its creation (SetServiceOption): its key, what it takes ([values] to
 * pick from, else a number, or text when [text]) and its value now.
 */
internal class ServiceOption(val key: String, val title: Int, val values: List<String>?, val current: String, val text: Boolean = false)

private fun settingsOf(service: JsonObject): JsonObject =
    runCatching { JsonParser.parseString(service.s("settingsJson")).asJsonObject }.getOrNull() ?: JsonObject()

/** A number of the service's settings (a key's grace: seal_prev_until, key_prev_until); 0 without it. */
internal fun settingLong(service: JsonObject, key: String): Long = runCatching { settingsOf(service).get(key)?.asLong }.getOrNull() ?: 0L

internal fun optionsOf(service: JsonObject): List<ServiceOption> {
    val settings = settingsOf(service)
    // the setting as kept (a list: its size, spare_zones's count), "—" where the node keeps its default
    fun now(key: String) = settings.get(key)?.let {
        when {
            it.isJsonArray -> it.asJsonArray.size().toString()
            it.isJsonPrimitive -> it.asString
            else -> null
        }
    } ?: "—"
    fun list(key: String) = settings.get(key)?.takeIf { it.isJsonArray }?.asJsonArray
        ?.mapNotNull { e -> e.takeIf { it.isJsonPrimitive }?.asString } ?: emptyList()
    val modes = listOf("off", "optional", "required")
    val onOff = listOf("true", "false")
    return when (service.s("kind")) {
        "dnstt", "masterdns" -> listOf(
            ServiceOption("auth", R.string.raycontrol_admin_option_auth, modes, now("auth")),
            ServiceOption("auth_hint", R.string.raycontrol_admin_option_auth_hint, modes, now("auth_hint")),
            ServiceOption("seal", R.string.raycontrol_admin_option_seal, modes, now("seal")),
            ServiceOption("spare_zones", R.string.raycontrol_admin_option_spare_zones, null, now("spare_zones")),
        ) + listOfNotNull(
            // a spare zone made the main one (dnszones-v1 §5): clients have it already; the main one leaves
            list("spare_zones").takeIf { it.isNotEmpty() }?.let { ServiceOption("zone", R.string.raycontrol_admin_option_zone, it, now("zone")) },
        )
        "slipstream" -> listOf(
            ServiceOption("max_connections", R.string.raycontrol_admin_option_max_connections, null, now("max_connections")),
            ServiceOption("device_connections", R.string.raycontrol_admin_option_device_connections, null, now("device_connections")),
        )
        "site" -> listOf(ServiceOption("answer", R.string.raycontrol_admin_option_answer, listOf("404", "403", "silent", "pages"), now("answer")))
        "reality" -> listOfNotNull(
            ServiceOption("rotate_sni", R.string.raycontrol_admin_option_rotate_sni, onOff, now("rotate_sni")),
            ServiceOption("open", R.string.raycontrol_admin_option_open, onOff, now("open")),
            // who answers those who are not clients (raycontrol v0.27.0), for a target of the service's own: one
            // that steals from the node's site has that site's name, which no foreign target has a certificate
            // for, and the node refuses it
            ServiceOption("target", R.string.raycontrol_admin_option_target, null, now("target"), text = true)
                .takeIf { settings.get("site")?.takeIf { it.isJsonPrimitive }?.asString.isNullOrEmpty() },
        )
        "hy2-masq", "hy2-obfs" -> listOf(
            ServiceOption("up_mbps", R.string.raycontrol_admin_option_up_mbps, null, now("up_mbps")),
            ServiceOption("down_mbps", R.string.raycontrol_admin_option_down_mbps, null, now("down_mbps")),
        )
        "yggdrasil" -> listOf(
            ServiceOption("peers", R.string.raycontrol_admin_option_peers, null, list("peers").joinToString(", ").ifEmpty { "—" }, text = true),
        )
        else -> emptyList()
    }
}

/** The keys a service of [kind] changes: REALITY's by plan or now, dnstt's seal key, masterdns's service key. */
internal fun keysOf(kind: String): List<ServiceKey> = listOfNotNull(
    ServiceKey.REALITY.takeIf { kind.contains("reality") },
    ServiceKey.SEAL.takeIf { kind == "dnstt" },
    ServiceKey.SERVICE.takeIf { kind == "masterdns" },
)

/** The DNS provider's row: none, or the provider, its zone, the records and what is wrong; null from a node without it. */
internal fun dnsSummary(dns: JsonObject?, t: AdminTexts): Pair<String, Boolean>? {
    if (dns == null) return t(R.string.raycontrol_admin_dns_none) to false
    val status = dns.getAsJsonObject("status") ?: return null
    val provider = status.s("provider")
    if (provider.isEmpty()) return t(R.string.raycontrol_admin_dns_none) to false
    val parts = mutableListOf("$provider · ${status.s("zone")}", t(R.string.raycontrol_admin_dns_records, status.arr("records").size))
    status.strs("problems").forEach { parts += "⚠ $it" }
    return parts.joinToString("\n") to true
}

/** The last periodic check of the tunnels' zones in a line. */
internal fun zonesSummary(last: JsonObject, t: AdminTexts, date: (Long) -> String): String {
    val checked = last.l("checkedAt")
    val states = last.arr("zones").groupingBy { it.s("state").ifEmpty { "?" } }.eachCount()
        .entries.joinToString(", ") { "${it.key} ${it.value}" }
    return if (checked == 0L) t(R.string.raycontrol_admin_dns_check_summary)
    else t(R.string.raycontrol_admin_dns_check_last, date(checked), states.ifEmpty { "—" })
}

/** What SyncDNS changed and what is wrong, a line each. */
internal fun dnsSyncText(status: JsonObject, t: AdminTexts) =
    (status.strs("changes") + status.strs("problems").map { "⚠ $it" }).ifEmpty { listOf(t(R.string.raycontrol_admin_dns_in_sync)) }.joinToString("\n")

/** The tunnels' zones as the node checked them now: the delegation, the front and backend, the resolvers. */
internal fun zonesText(answer: JsonObject, t: AdminTexts, date: (Long) -> String): String {
    val text = answer.arr("zones").joinToString("\n\n") { z ->
        val lines = mutableListOf("${z.s("zone")} (${z.s("service")}, ${z.s("kind")}): ${z.s("state")}")
        z.s("summary").takeIf { it.isNotBlank() }?.let { lines += it }
        z.s("error").takeIf { it.isNotBlank() }?.let { lines += "⚠ $it" }
        for (step in listOf("parent", "front", "backend")) {
            z.getAsJsonObject(step)?.let { st ->
                val fix = st.s("fix").takeIf { it.isNotBlank() }?.let { " → $it" }.orEmpty()
                lines += "  $step: ${st.s("state")} ${st.s("detail")}$fix"
            }
        }
        z.arr("resolvers").forEach { r ->
            val states = listOf("soa", "small", "large").mapNotNull { r.getAsJsonObject(it)?.s("state") }
            val worst = states.firstOrNull { it == "fail" } ?: states.firstOrNull { it == "warn" } ?: "ok"
            lines += "  ${r.s("resolver")} ${r.s("network")}: $worst"
        }
        lines.joinToString("\n")
    }.ifEmpty { t(R.string.raycontrol_admin_empty) }
    val head = t(R.string.raycontrol_admin_dns_checked, date(answer.l("checkedAt"))) +
        if (answer.b("limited")) "\n" + t(R.string.raycontrol_admin_dns_check_limited) else ""
    return head + "\n\n" + text
}

/** The user's links for other apps (ExportUser): what to share, and what to read; null without a link. */
internal fun linksText(profiles: List<JsonObject>, user: String, t: AdminTexts): Pair<String, String>? {
    val links = profiles.mapNotNull { p -> p.s("link").takeIf { it.isNotBlank() } }
    if (links.isEmpty()) return null
    return links.joinToString("\n") to t(R.string.raycontrol_admin_links_help, user) + "\n\n" +
        profiles.joinToString("\n\n") { p -> "${p.s("service")}:\n${p.s("link")}" }
}

/** The node has a name to set (raycontrol v0.15.0: GetStatus has it), a note (v0.21.0: left out while there is none). */
internal fun hasName(status: JsonObject) = status.has("name") || status.s("version").let { it.isNotEmpty() && isNewerVersion(it, "0.14.99") }
internal fun hasNote(status: JsonObject) = status.has("note") || status.s("version").let { it.isNotEmpty() && isNewerVersion(it, "0.20.99") }

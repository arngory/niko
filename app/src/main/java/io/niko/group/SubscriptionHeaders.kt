package io.niko.group

import okio.ByteString.Companion.decodeBase64
import java.net.URI
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** A text subscription header (profile-title, announce): plain or "base64:<data>". */
fun decodeSubscriptionHeader(value: String?): String {
    val text = value?.trim().orEmpty()
    if (!text.startsWith("base64:", ignoreCase = true)) return text
    return text.substring("base64:".length).trim().decodeBase64()?.utf8()?.trim().orEmpty()
}

/** profile-update-interval (hours) as minutes for the auto update, null when missing or odd. */
fun subscriptionUpdateIntervalMinutes(value: String?): Int? =
    value?.trim()?.toIntOrNull()?.takeIf { it in 1..24 * 30 }?.times(60)

/** Subscription-Userinfo: "upload=…; download=…; total=…; expire=…" (bytes, unix seconds). */
data class SubscriptionUsage(val used: Long, val total: Long, val expire: Long) {
    val remaining get() = total - used

    /** Ends within [days] or has less than [percent] % of the traffic left. */
    fun runningOut(nowSeconds: Long, days: Int = 3, percent: Int = 5): Boolean =
        (expire > 0 && expire - nowSeconds < days * 86400L) ||
                (total > 0 && remaining * 100 < total * percent)
}

fun parseSubscriptionUserinfo(header: String?): SubscriptionUsage? {
    if (header.isNullOrBlank()) return null
    fun value(name: String) = Regex("""(?:^|[;\s])$name\s*=\s*(\d+)""").find(header)
        ?.groupValues?.get(1)?.toLongOrNull() ?: 0L
    val usage = SubscriptionUsage(
        used = value("upload") + value("download"),
        total = value("total"),
        expire = value("expire"),
    )
    return usage.takeIf { it.used > 0 || it.total > 0 || it.expire > 0 }
}

/**
 * x-hwid for one provider: HMAC of the install secret and the subscription host, so providers
 * can not link the same device across subscriptions while each sees a stable id.
 */
fun subscriptionDeviceId(installSecret: String, subscriptionLink: String): String {
    val host = try {
        URI(subscriptionLink).host
    } catch (_: Exception) {
        null
    } ?: subscriptionLink
    val mac = Mac.getInstance("HmacSHA256")
    mac.init(SecretKeySpec(installSecret.toByteArray(), "HmacSHA256"))
    return mac.doFinal(host.lowercase().toByteArray()).take(16).joinToString("") { "%02x".format(it) }
}

/** Why a panel that limits devices (Remnawave, since 2.7.5) gave no subscription to this device. */
enum class HwidRefusal {
    /** x-hwid-max-devices-reached (x-hwid-limit for older clients): the user's devices are all taken */
    LIMIT_REACHED,

    /** x-hwid-not-supported: the limit is on and the device sent no id it takes (the panel answers 404) */
    ID_REQUIRED,
}

private fun isTrue(value: String?) = value?.trim().equals("true", ignoreCase = true)

/** The refusal an answer's headers carry, if any. */
fun hwidRefusal(header: (String) -> String?): HwidRefusal? = when {
    isTrue(header("x-hwid-max-devices-reached")) || isTrue(header("x-hwid-limit")) -> HwidRefusal.LIMIT_REACHED
    isTrue(header("x-hwid-not-supported")) -> HwidRefusal.ID_REQUIRED
    else -> null
}

/** The refusal a refused answer's error names: libcore puts each such header set to true in brackets after the status. */
fun hwidRefusal(error: String?): HwidRefusal? = when {
    error == null -> null
    "[x-hwid-max-devices-reached]" in error || "[x-hwid-limit]" in error -> HwidRefusal.LIMIT_REACHED
    "[x-hwid-not-supported]" in error -> HwidRefusal.ID_REQUIRED
    else -> null
}

/**
 * Where the provider moves a subscription and its spare address, the names Happ reads: new-url (the whole new
 * address), new-domain (the same address on another domain), fallback-url (tried when the address fails). From the
 * answer's headers, else from "#name: value" lines of its body (or of the body base64-decoded). Each is checked
 * ([subscriptionAddress]); [to] is null when the address stays, [spare] when there is none.
 */
class SubscriptionMove(val to: String?, val spare: String?)

private val MOVE_PARAMS = setOf("new-url", "new-domain", "fallback-url")

fun subscriptionMove(current: String, header: (String) -> String?, body: String): SubscriptionMove {
    val lines by lazy { bodyParams(body) }
    fun value(name: String) = header(name)?.trim().orEmpty().ifEmpty { lines[name].orEmpty() }
    val to = subscriptionAddress(current, value("new-url")) ?: withDomain(current, value("new-domain"))
    val spare = subscriptionAddress(current, value("fallback-url"))
    return SubscriptionMove(to?.takeIf { it != current }, spare?.takeIf { it != current })
}

/** The "#name: value" lines of a body for [MOVE_PARAMS], the first of each name; of the base64-decoded body when it has none. */
internal fun bodyParams(body: String): Map<String, String> {
    fun scan(text: String): Map<String, String> {
        val found = LinkedHashMap<String, String>()
        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (!line.startsWith("#")) continue
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val name = line.substring(1, colon).trim().lowercase()
            if (name in MOVE_PARAMS) found.putIfAbsent(name, line.substring(colon + 1).trim())
        }
        return found
    }
    scan(body).takeIf { it.isNotEmpty() }?.let { return it }
    val decoded = body.trim().takeIf { it.isNotEmpty() }?.decodeBase64()?.utf8() ?: return emptyMap()
    return scan(decoded)
}

/**
 * A provider's address for a subscription, or null: absolute, http(s) with a host and no user info, at most 2048
 * characters, and no step down from https to http (a panel on https cannot send the app to plain text).
 */
fun subscriptionAddress(current: String, candidate: String): String? {
    val text = candidate.trim()
    if (text.isEmpty() || text.length > 2048) return null
    val uri = try {
        URI(text)
    } catch (_: Exception) {
        return null
    }
    val scheme = uri.scheme?.lowercase() ?: return null
    if (scheme != "https" && scheme != "http") return null
    if (uri.host.isNullOrBlank() || uri.rawUserInfo != null) return null
    val currentScheme = try {
        URI(current.trim()).scheme?.lowercase()
    } catch (_: Exception) {
        null
    }
    if (scheme == "http" && currentScheme != "http") return null
    return text
}

private val DOMAIN = Regex("""^(?=.{1,253}$)([A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\.)+[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?$""")

/** The current address on another domain (new-domain): the scheme, port, path, query and fragment as they were. */
fun withDomain(current: String, domain: String): String? {
    val host = domain.trim().trimEnd('.')
    if (!DOMAIN.matches(host)) return null
    val uri = try {
        URI(current.trim())
    } catch (_: Exception) {
        return null
    }
    val scheme = uri.scheme ?: return null
    if (uri.host.isNullOrBlank()) return null
    val moved = buildString {
        append(scheme).append("://").append(host.lowercase())
        if (uri.port != -1) append(':').append(uri.port)
        append(uri.rawPath.orEmpty())
        uri.rawQuery?.let { append('?').append(it) }
        uri.rawFragment?.let { append('#').append(it) }
    }
    return subscriptionAddress(current, moved)
}

/**
 * The spare address to keep after an answer: the one the main address's answer names now, none when it names none (a
 * spare the provider dropped gets no more requests, nor the device's headers). An answer of the spare itself does
 * not name it: the one kept stays.
 */
fun spareAfterAnswer(kept: String?, named: String?, answeredBySpare: Boolean): String =
    if (answeredBySpare) kept.orEmpty() else named.orEmpty()

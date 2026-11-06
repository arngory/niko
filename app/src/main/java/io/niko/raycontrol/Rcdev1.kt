package io.niko.raycontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okio.ByteString.Companion.decodeBase64

/** SPA v2 parameters of a raycontrol node (raycontrol docs/spec/spa-v2.md §1). */
data class RaySpaParams(
    val nodeId: String,
    val epochLenS: Long,
    val slotLenS: Long,
    val portKey: String,
    val portBase: Int,
    val portRange: Int,
    val lenMin: Int,
    val lenMax: Int,
) {
    fun toJson() = JsonObject().apply {
        addProperty("node_id", nodeId)
        addProperty("epoch_len_s", epochLenS)
        addProperty("slot_len_s", slotLenS)
        addProperty("port_key", portKey)
        addProperty("port_base", portBase)
        addProperty("port_range", portRange)
        addProperty("len_min", lenMin)
        addProperty("len_max", lenMax)
    }

    companion object {
        fun fromJson(o: JsonObject) = RaySpaParams(
            nodeId = o.hex("node_id", 16),
            epochLenS = o.long("epoch_len_s"),
            slotLenS = o.long("slot_len_s"),
            portKey = o.hex("port_key", 32),
            portBase = o.long("port_base").toInt(),
            portRange = o.long("port_range").toInt(),
            lenMin = o.long("len_min").toInt(),
            lenMax = o.long("len_max").toInt(),
        ).also { it.validate() }
    }

    fun validate() {
        require(epochLenS > 0 && slotLenS > 0) { "epoch_len_s and slot_len_s must be positive" }
        require(portRange > 0 && portBase > 0 && portBase + portRange <= 65536) { "invalid SPA port range" }
        // nonce 24 + tag 16 + header 13
        require(lenMin >= 53 && lenMax >= lenMin && lenMax <= 512) { "invalid SPA packet length bounds" }
    }
}

/** A UDP service whose port rotates every slot (spa-v2.md §11): the schedule's key and range. */
data class RayPortSchedule(val portKey: String, val portBase: Int, val portRange: Int) {
    init {
        require(portRange > 0 && portBase > 0 && portBase + portRange <= 65536) { "invalid service port range" }
    }
}

data class RayService(val id: Int, val kind: String, val name: String, val ports: RayPortSchedule? = null) {
    fun toJson() = JsonObject().apply {
        addProperty("id", id)
        addProperty("kind", kind)
        addProperty("name", name)
        ports?.let {
            addProperty("port_key", it.portKey)
            addProperty("port_base", it.portBase)
            addProperty("port_range", it.portRange)
        }
    }

    companion object {
        /** `services[]` of a registration (spa-v2.md §8): `port_key`, `port_base`, `port_range` all or none. */
        fun fromJson(s: JsonObject): RayService {
            val ports = if (s.has("port_key") || s.has("port_base") || s.has("port_range")) {
                RayPortSchedule(s.hex("port_key", 32), s.long("port_base").toInt(), s.long("port_range").toInt())
            } else null
            return RayService(s.long("id").toInt(), s.string("kind"), s.string("name"), ports)
        }
    }
}

/** Subscription endpoint (spa-v2.md §8 `sub`) and the node's signing keys (`node_keys`). */
data class RaySubscriptionInfo(val port: Int, val pin: String, val ed25519: String, val mldsa65: String) {
    fun toJson() = JsonObject().apply {
        addProperty("port", port)
        addProperty("pin", pin)
        addProperty("ed25519", ed25519)
        addProperty("mldsa65", mldsa65)
    }

    companion object {
        fun fromJson(o: JsonObject) = RaySubscriptionInfo(o.long("port").toInt(), o.string("pin"), o.string("ed25519"), o.string("mldsa65"))

        private val BASE64URL = Regex("^[A-Za-z0-9_-]+$")

        /** From the registration: both `sub` and `node_keys`, or none (old nodes have neither). */
        fun fromRegistration(o: JsonObject): RaySubscriptionInfo? {
            val sub = o.get("sub") as? JsonObject ?: return null
            val keys = o.get("node_keys") as? JsonObject
                ?: throw IllegalArgumentException("rcdev1: sub without node_keys, the subscription could not be verified")
            val port = sub.long("port").toInt()
            require(port in 1..65535) { "rcdev1: invalid sub.port" }
            val pin = sub.string("pin")
            require(pin.length == 43 && BASE64URL.matches(pin)) { "rcdev1: sub.pin must be base64url SHA-256" }
            val ed25519 = keys.hex("ed25519", 32)
            val mldsa65 = keys.string("mldsa65")
            require(mldsa65.length == 2603 && BASE64URL.matches(mldsa65)) { "rcdev1: node_keys.mldsa65 must be a base64url ML-DSA-65 key" }
            return RaySubscriptionInfo(port, pin, ed25519, mldsa65)
        }
    }
}

/** The node's remote API (api-v1.md §1): only in packages of admin devices and in invites. */
data class RayApiInfo(val port: Int, val pin: String) {
    fun toJson() = JsonObject().apply {
        addProperty("port", port)
        addProperty("pin", pin)
    }

    companion object {
        private val BASE64URL = Regex("^[A-Za-z0-9_-]{43}$")

        fun fromJson(o: JsonObject): RayApiInfo {
            val port = o.long("port").toInt()
            require(port in 1..65535) { "api.port is invalid" }
            val pin = o.string("pin")
            require(BASE64URL.matches(pin)) { "api.pin must be base64url SHA-256" }
            return RayApiInfo(port, pin)
        }
    }
}

/** A device registration package `rcdev1:` (raycontrol docs/spec/spa-v2.md §8). It holds the device key. */
data class RayRegistration(
    val host: String,
    val spa: RaySpaParams,
    val deviceId: String,
    val deviceKey: String,
    val services: List<RayService>,
    val subscription: RaySubscriptionInfo? = null,
    val api: RayApiInfo? = null,
) {
    override fun toString() = "RayRegistration(host=$host, node=${spa.nodeId}, device=$deviceId)"
}

/**
 * A one-time invite `rcinv1:` (api-v1.md §3): the registration package format with the invite's ID and one-time SPA
 * key, the API endpoint and an expiry; the device registers itself with it and gets its own `rcdev1:`.
 */
data class RayInvite(
    val host: String,
    val spa: RaySpaParams,
    val inviteId: String,
    val inviteKey: String,
    val services: List<RayService>,
    val api: RayApiInfo,
    val expires: Long,
    // the invite needs a PIN too, told by another channel (api-v1.md §3, raycontrol v0.13)
    val pinRequired: Boolean = false,
) {
    override fun toString() = "RayInvite(host=$host, node=${spa.nodeId}, invite=$inviteId)"
}

object Rcinv1 {
    const val PREFIX = "rcinv1:"
    private val token = RayToken(PREFIX)

    fun matches(text: String) = token.matches(text)

    fun candidates(text: String) = token.candidates(text)

    /** Throws IllegalArgumentException with a readable reason. */
    fun parse(text: String): RayInvite = token.parse(text) { o -> fromJson(o, "rcinv1") }

    /** An invite's JSON (api-v1.md §3) to the invite, checked: rcinv1's, and rcinv2's as rcinv1's ([Rcinv2]). */
    internal fun fromJson(o: JsonObject, name: String): RayInvite {
        val r = Rcdev1.fields(o, name)
        val api = (o.get("api") as? JsonObject ?: throw IllegalArgumentException("$name: no api")).let {
            try {
                RayApiInfo.fromJson(it)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$name: ${e.message}")
            }
        }
        return RayInvite(
            r.host, r.spa, r.deviceId, r.deviceKey, r.services, api, o.long("expires"),
            pinRequired = (o.get("pin") as? com.google.gson.JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean == true,
        )
    }
}

/**
 * The short invite `RCINV2:` (api-v1.md §3a, raycontrol v0.13): rcinv1's invite in a fixed binary layout, base32 in
 * upper case for the QR code's alphanumeric mode (version 8-9 instead of 17). Read into the rcinv1 JSON and checked
 * as one, so the two never differ in what they take.
 */
object Rcinv2 {
    const val PREFIX = "RCINV2:"
    private val token = Regex("""(?<![A-Za-z0-9_-])RCINV2:[A-Za-z2-7]+""", RegexOption.IGNORE_CASE)
    private val label = Regex("^[A-Za-z0-9-]{1,63}$")
    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun matches(text: String) = token.containsMatchIn(text)

    /** The bare token in [text], e.g. to pass a scanned QR code on as a link. */
    fun token(text: String) = token.find(text)?.value ?: text.trim()

    /** Throws IllegalArgumentException with a readable reason. */
    fun parse(text: String): RayInvite {
        val found = token.find(text)?.value ?: throw IllegalArgumentException("rcinv2: no invite in the text")
        val b = java.nio.ByteBuffer.wrap(base32(found.substringAfter(':')))
        fun need(n: Int) = require(b.remaining() >= n) { "rcinv2: cut short" }
        fun bytes(n: Int) = ByteArray(n).also { need(n); b.get(it) }
        fun hex(n: Int) = bytes(n).joinToString("") { "%02x".format(it) }
        fun u16() = run { need(2); b.short.toInt() and 0xffff }
        fun u32() = run { need(4); b.int.toLong() and 0xffffffffL }
        need(2)
        require(b.get().toInt() == 2) { "rcinv2: unsupported version" }
        val flags = b.get().toInt() and 0xff
        require(flags and 0x07.inv() == 0) { "rcinv2: unknown flags" }
        val host = when ((flags shr 1) and 0x03) {
            0 -> bytes(4).joinToString(".") { (it.toInt() and 0xff).toString() }
            1 -> ipv6(bytes(16))
            2 -> {
                need(1)
                val name = String(bytes(b.get().toInt() and 0xff), Charsets.US_ASCII)
                require(name.length in 1..253 && name.split('.').all { label.matches(it) }) { "rcinv2: invalid host name" }
                name
            }
            else -> throw IllegalArgumentException("rcinv2: unknown kind of host")
        }
        val o = JsonObject().apply {
            addProperty("v", 1)
            addProperty("host", host)
            add("spa", JsonObject().apply {
                addProperty("node_id", hex(16))
                addProperty("epoch_len_s", u32())
                addProperty("slot_len_s", u32())
                addProperty("port_key", hex(32))
                addProperty("port_base", u16())
                addProperty("port_range", u16())
                addProperty("len_min", u16())
                addProperty("len_max", u16())
            })
            addProperty("device_id", hex(8))
            addProperty("device_key", hex(32))
            // one service, the API: kind and name are not sent
            add("services", com.google.gson.JsonArray().apply {
                add(JsonObject().apply { addProperty("id", u16()); addProperty("kind", "api"); addProperty("name", "api") })
            })
            val apiPort = u16()
            val apiPin = okio.ByteString.of(*bytes(32)).base64Url().trimEnd('=')
            add("api", JsonObject().apply { addProperty("port", apiPort); addProperty("pin", apiPin) })
            val expires = u32()
            require(expires != 0L && apiPort != 0) { "rcinv2: no expiry or no API port" }
            addProperty("expires", expires)
            if (flags and 0x01 != 0) addProperty("pin", true)
        }
        require(!b.hasRemaining()) { "rcinv2: bytes after its end" }
        return Rcinv1.fromJson(o, "rcinv2")
    }

    /** RFC 4648 base32 without padding, either case. */
    private fun base32(text: String): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        var buffer = 0
        var bits = 0
        for (c in text.uppercase()) {
            val v = ALPHABET.indexOf(c)
            require(v >= 0) { "rcinv2: not base32" }
            buffer = (buffer shl 5) or v
            bits += 5
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xff)
            }
        }
        // the rest must be the padding bits of the last character, zero
        require(bits < 5 && buffer and ((1 shl bits) - 1) == 0) { "rcinv2: not base32" }
        return out.toByteArray()
    }

    /** An IPv6 address in its short form (the longest run of zero groups as ::). */
    private fun ipv6(raw: ByteArray): String {
        val groups = (0 until 8).map { ((raw[2 * it].toInt() and 0xff) shl 8) or (raw[2 * it + 1].toInt() and 0xff) }
        var bestStart = -1
        var bestLen = 0
        var i = 0
        while (i < 8) {
            if (groups[i] != 0) {
                i++
                continue
            }
            var j = i
            while (j < 8 && groups[j] == 0) j++
            if (j - i > bestLen && j - i >= 2) {
                bestStart = i
                bestLen = j - i
            }
            i = j
        }
        fun part(from: Int, to: Int) = groups.subList(from, to).joinToString(":") { Integer.toHexString(it) }
        return if (bestStart < 0) part(0, 8) else part(0, bestStart) + "::" + part(bestStart + bestLen, 8)
    }
}

/** Finds and decodes a `<prefix><base64url JSON>` token in any text (rcdev1, rcinv1). */
class RayToken(private val prefix: String) {
    private val token = Regex("""(?<![A-Za-z0-9_-])${Regex.escape(prefix)}[A-Za-z0-9_-]+""")

    fun matches(text: String) = token.containsMatchIn(text)

    fun candidates(text: String): List<String> {
        val match = token.find(text) ?: return listOf(text.trim())
        val out = mutableListOf(match.value)
        val rest = text.substring(match.range.last + 1)
        if (rest.startsWith("\n") || rest.startsWith("\r\n")) {
            var joined = match.value
            for (line in rest.lines().drop(1).take(64)) {
                val part = line.trim()
                if (!CONTINUATION.matches(part)) break
                joined += part
                out += joined
            }
        }
        return out
    }

    fun <T> parse(text: String, read: (JsonObject) -> T): T {
        var first: IllegalArgumentException? = null
        for (candidate in candidates(text)) {
            try {
                return read(decode(candidate))
            } catch (e: IllegalArgumentException) {
                if (first == null) first = e
            }
        }
        throw first!!
    }

    private fun decode(text: String): JsonObject {
        val name = prefix.removeSuffix(":")
        val body = text.trim().removePrefix(prefix).trim()
        require(body.isNotEmpty() && body.all { it.isLetterOrDigit() || it == '-' || it == '_' }) { "$name: not base64url" }
        val json = body.decodeBase64()?.utf8() ?: throw IllegalArgumentException("$name: not base64url")
        return try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("$name: not JSON")
        }
    }

    companion object {
        private val CONTINUATION = Regex("""^[A-Za-z0-9_-]+$""")
    }
}

object Rcdev1 {
    const val PREFIX = "rcdev1:"
    private val token = RayToken(PREFIX)

    /** A package anywhere in the text, e.g. the whole output of `rayctl device add` (raycontrol niko-r4-note.md §4). */
    fun matches(text: String) = token.matches(text)

    /**
     * The package in [text]: the token that starts with `rcdev1:`. A terminal may have wrapped it, so the lines right
     * after it that are base64url only are tried as its continuation, shortest first.
     */
    fun candidates(text: String) = token.candidates(text)

    /** Throws IllegalArgumentException with a readable reason. */
    fun parse(text: String): RayRegistration = token.parse(text) { o ->
        fields(o, "rcdev1").copy(
            subscription = RaySubscriptionInfo.fromRegistration(o),
            api = (o.get("api") as? JsonObject)?.let {
                try {
                    RayApiInfo.fromJson(it)
                } catch (e: IllegalArgumentException) {
                    throw IllegalArgumentException("rcdev1: ${e.message}")
                }
            },
        )
    }

    /** The fields an rcdev1 package and an rcinv1 invite share (spa-v2.md §8). */
    internal fun fields(o: JsonObject, name: String): RayRegistration {
        require(o.long("v") == 1L) { "$name: unsupported version ${o.get("v")}" }
        val host = o.string("host")
        require(host.isNotBlank() && host.none { it.isWhitespace() || it == '/' }) { "$name: invalid host" }
        val spa = (o.get("spa") as? JsonObject ?: throw IllegalArgumentException("$name: no spa")).let {
            try {
                RaySpaParams.fromJson(it)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$name: ${e.message}")
            }
        }
        val services = (o.get("services") as? JsonArray)?.map {
            try {
                RayService.fromJson(it.asJsonObject)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("$name: services: ${e.message}")
            }
        }.orEmpty()
        return RayRegistration(
            host = host.trim().removePrefix("[").removeSuffix("]"),
            spa = spa,
            deviceId = o.hex("device_id", 8),
            deviceKey = o.hex("device_key", 32),
            services = services,
        )
    }
}

internal fun JsonObject.string(name: String): String =
    get(name)?.takeIf { it.isJsonPrimitive }?.asString ?: throw IllegalArgumentException("missing $name")

internal fun JsonObject.long(name: String): Long =
    get(name)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isNumber }?.asLong
        ?: throw IllegalArgumentException("missing $name")

private val HEX = Regex("^[0-9a-f]+$")

internal fun JsonObject.hex(name: String, bytes: Int): String {
    val value = string(name).lowercase()
    require(value.length == bytes * 2 && HEX.matches(value)) { "$name must be ${bytes * 2} hex digits" }
    return value
}

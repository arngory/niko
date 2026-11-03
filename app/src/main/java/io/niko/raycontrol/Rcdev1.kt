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
) {
    override fun toString() = "RayInvite(host=$host, node=${spa.nodeId}, invite=$inviteId)"
}

object Rcinv1 {
    const val PREFIX = "rcinv1:"
    private val token = RayToken(PREFIX)

    fun matches(text: String) = token.matches(text)

    /** Throws IllegalArgumentException with a readable reason. */
    fun parse(text: String): RayInvite = token.parse(text) { o ->
        val r = Rcdev1.fields(o, "rcinv1")
        val api = (o.get("api") as? JsonObject ?: throw IllegalArgumentException("rcinv1: no api")).let {
            try {
                RayApiInfo.fromJson(it)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("rcinv1: ${e.message}")
            }
        }
        RayInvite(r.host, r.spa, r.deviceId, r.deviceKey, r.services, api, o.long("expires"))
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

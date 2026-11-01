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

data class RayService(val id: Int, val kind: String, val name: String)

/** A device registration package `rcdev1:` (raycontrol docs/spec/spa-v2.md §8). It holds the device key. */
data class RayRegistration(
    val host: String,
    val spa: RaySpaParams,
    val deviceId: String,
    val deviceKey: String,
    val services: List<RayService>,
) {
    override fun toString() = "RayRegistration(host=$host, node=${spa.nodeId}, device=$deviceId)"
}

object Rcdev1 {
    const val PREFIX = "rcdev1:"

    fun matches(text: String) = text.trim().startsWith(PREFIX)

    /** Throws IllegalArgumentException with a readable reason. */
    fun parse(text: String): RayRegistration {
        val body = text.trim().removePrefix(PREFIX).trim()
        require(body.isNotEmpty() && body.all { it.isLetterOrDigit() || it == '-' || it == '_' }) {
            "rcdev1: not base64url"
        }
        val json = body.decodeBase64()?.utf8() ?: throw IllegalArgumentException("rcdev1: not base64url")
        val o = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            throw IllegalArgumentException("rcdev1: not JSON")
        }
        require(o.long("v") == 1L) { "rcdev1: unsupported version ${o.get("v")}" }
        val host = o.string("host")
        require(host.isNotBlank() && host.none { it.isWhitespace() || it == '/' }) { "rcdev1: invalid host" }
        val spa = (o.get("spa") as? JsonObject ?: throw IllegalArgumentException("rcdev1: no spa")).let {
            try {
                RaySpaParams.fromJson(it)
            } catch (e: IllegalArgumentException) {
                throw IllegalArgumentException("rcdev1: ${e.message}")
            }
        }
        val services = (o.get("services") as? JsonArray)?.map {
            val s = it.asJsonObject
            RayService(s.long("id").toInt(), s.string("kind"), s.string("name"))
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

package io.niko.raycontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A relay in front of a node (relay-v1 §4, a draft): its address and its own SPA, at which the device knocks with
 * K_relay (the core derives it from the device key, the node's node_id and the relay's id and node_id).
 */
data class RayRelay(
    val id: String, val host: String, val spa: RaySpaParams, val subPin: String? = null,
    // the relay's own probe-v1 endpoint (relay-v1 §9.2, R6-c); null: the relay measures nothing
    val probe: RayRelayProbe? = null,
) {

    fun toJson() = JsonObject().apply {
        addProperty("id", id)
        addProperty("host", host)
        add("spa", spa.toJson())
        subPin?.let { addProperty("sub_pin", it) }
        probe?.let { add("probe", it.toJson()) }
    }

    /** The relay's SPA for the core: its parameters, the device key, and the node behind it (K_relay, §3). */
    fun spaJson(node: RayNode, deviceKeyHex: String) = spa.toJson().apply {
        addProperty("device_key", deviceKeyHex)
        add("relay", JsonObject().apply {
            addProperty("exit_node_id", node.spa.nodeId)
            addProperty("id", id)
        })
    }

    companion object {
        fun fromJson(o: JsonObject): RayRelay? = runCatching {
            RayRelay(
                id = o.get("id").asString,
                host = o.get("host").asString,
                spa = RaySpaParams.fromJson(o.getAsJsonObject("spa")),
                subPin = o.get("sub_pin")?.takeIf { it.isJsonPrimitive }?.asString,
                probe = (o.get("probe") as? JsonObject)?.let { RayRelayProbe.fromJson(it) }?.takeIf { it.v == 1 },
            )
        }.getOrNull()?.takeIf { it.id.isNotBlank() && it.host.isNotBlank() }

        fun listFromJson(a: JsonArray?): List<RayRelay> = a?.mapNotNull { (it as? JsonObject)?.let(::fromJson) }.orEmpty()
    }
}

/**
 * relays[].probe (relay-v1 §9.2): the relay's probe endpoint, pinned. svc is its service id for a knock with an
 * explicit scope; the client knocks at a relay with an empty scope ("all allowed"), which opens it as well (§9.1).
 */
data class RayRelayProbe(
    val v: Int, val port: Int, val pin: String, val udp: Boolean = false, val report: Boolean = false, val svc: Int? = null,
) {
    fun toJson() = JsonObject().apply {
        addProperty("v", v)
        addProperty("port", port)
        addProperty("pin", pin)
        addProperty("udp", udp)
        addProperty("report", report)
        svc?.let { addProperty("svc", it) }
    }

    companion object {
        fun fromJson(o: JsonObject): RayRelayProbe? = runCatching {
            RayRelayProbe(
                v = o.get("v").asInt, port = o.get("port").asInt, pin = o.get("pin").asString,
                udp = o.get("udp")?.asBoolean ?: false, report = o.get("report")?.asBoolean ?: false,
                svc = o.get("svc")?.takeIf { it.isJsonPrimitive }?.asInt,
            )
        }.getOrNull()?.takeIf { it.port in 1..65535 && it.pin.isNotBlank() }
    }
}

object RayRelays {

    /** A probe-v1 request to the relay's own endpoint: its SPA with K_relay, RC1 on K_relay (§9.1). */
    fun probeRequest(node: RayNode, relay: RayRelay, deviceKeyHex: String): JsonObject? {
        val probe = relay.probe ?: return null
        return JsonObject().apply {
            addProperty("host", relay.host)
            addProperty("port", probe.port)
            addProperty("pin", probe.pin)
            addProperty("device_id", node.deviceId)
            add("spa", relay.spaJson(node, deviceKeyHex))
        }
    }

    /**
     * Rows in the order for a network: through a relay first where the node is reached that way (the detector or
     * the node's hints said so), last elsewhere; the order within each part stays.
     */
    fun order(keys: List<String>, viaFirst: Boolean): List<String> =
        keys.sortedBy { (relayOfKey(it) == null) == viaFirst }

    /**
     * The key of a profile row through a relay: the profile's own key and the relay's id. The part before the first
     * '#' stays the service id, so hints, probes and rates take the row as its service's.
     */
    fun viaKey(key: String, relayId: String) = "$key#via-$relayId"

    fun relayOfKey(key: String): String? = key.substringAfter("#via-", "").takeIf { it.isNotEmpty() }

    /**
     * The profile's link through the relay: the relay's host; UDP (Hysteria2) on 443 without port rotation, which
     * relay-v1 §4 leaves off. The credentials, the names and the pins stay the node's: the tunnel ends there.
     */
    fun viaLink(link: String, relay: RayRelay): String? {
        val scheme = link.substringBefore("://", "")
        if (scheme.isEmpty() || ("https://" + link.substringAfter("://")).toHttpUrlOrNull() == null) return null
        // the link as written, only its authority and the hopping parameter changed (a URL library would drop
        // the default port and add a path)
        val rest = link.substringAfter("://")
        val end = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }.let { if (it < 0) rest.length else it }
        val authority = rest.substring(0, end)
        val userInfo = authority.substringBeforeLast('@', "")
        val hostPort = authority.substringAfterLast('@')
        val port = if (hostPort.startsWith("[")) hostPort.substringAfter("]", "").removePrefix(":")
        else hostPort.substringAfter(':', "")
        val udp = scheme == "hysteria2" || scheme == "hy2"
        val host = relay.host.trim('[', ']').let { if (':' in it) "[$it]" else it }
        val newPort = if (udp) "443" else port
        var tail = rest.substring(end)
        // the node's port hopping is not at the relay
        val query = tail.substringAfter('?', "").substringBefore('#')
        if (query.isNotEmpty()) {
            val kept = query.split('&').filterNot { it.substringBefore('=') == "mport" }.joinToString("&")
            tail = tail.substringBefore('?') + (if (kept.isEmpty()) "" else "?$kept") +
                    tail.substringAfter('#', "").let { if (it.isEmpty() && '#' !in tail) "" else "#$it" }
        }
        return scheme + "://" + (if (userInfo.isEmpty()) "" else "$userInfo@") + host +
                (if (newPort.isEmpty()) "" else ":$newPort") + tail
    }
}

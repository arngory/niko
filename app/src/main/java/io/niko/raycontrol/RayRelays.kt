package io.niko.raycontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * A relay in front of a node (relay-v1 §4, a draft): its address and its own SPA, at which the device knocks with
 * K_relay (the core derives it from the device key, the node's node_id and the relay's id and node_id).
 */
data class RayRelay(val id: String, val host: String, val spa: RaySpaParams, val subPin: String? = null) {

    fun toJson() = JsonObject().apply {
        addProperty("id", id)
        addProperty("host", host)
        add("spa", spa.toJson())
        subPin?.let { addProperty("sub_pin", it) }
    }

    companion object {
        fun fromJson(o: JsonObject): RayRelay? = runCatching {
            RayRelay(
                id = o.get("id").asString,
                host = o.get("host").asString,
                spa = RaySpaParams.fromJson(o.getAsJsonObject("spa")),
                subPin = o.get("sub_pin")?.takeIf { it.isJsonPrimitive }?.asString,
            )
        }.getOrNull()?.takeIf { it.id.isNotBlank() && it.host.isNotBlank() }

        fun listFromJson(a: JsonArray?): List<RayRelay> = a?.mapNotNull { (it as? JsonObject)?.let(::fromJson) }.orEmpty()
    }
}

object RayRelays {

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

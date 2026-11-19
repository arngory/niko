package io.niko.raycontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import io.niko.ktx.isIpAddress
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * names-v1 (raycontrol docs/spec/names-v1.md): the client reaches a node by its name, as a browser reaches a site,
 * with the node's addresses as the fallback. The node's name (`dns_name`) and addresses (`addrs`) come in its device
 * package and its subscription, both signed; the links of the subscription name the service or the node with the
 * addresses as `niko-ip`. The core's knock resolves the name once per attempt and knocks only at one of them
 * (protocol/knock/names.go), libcore's own requests the same (raynames).
 */
object RayNames {

    private val label = Regex("[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?")

    /** A node's name: two labels or more of letters, digits and hyphens, in lower case; null for anything else. */
    fun name(raw: String?): String? {
        val name = raw?.trim()?.lowercase()?.removeSuffix(".") ?: return null
        if (name.length > 253 || name.isIpAddress()) return null
        val labels = name.split('.')
        return name.takeIf { labels.size >= 2 && labels.all { label.matches(it) } }
    }

    /** The addresses of [values] (IPv6 bare or in brackets), once each in their order; anything else is left out. */
    fun addrs(values: Collection<String>?): List<String> = values.orEmpty()
        .map { it.trim().removePrefix("[").removeSuffix("]").lowercase() }
        .filter { it.isNotEmpty() && it.isIpAddress() }
        .distinct()

    /** The addresses of a link's `niko-ip`: a comma-separated list. */
    fun addrs(param: String?): List<String> = addrs(param?.split(',').orEmpty())

    /** The `niko-ip` addresses of a subscription link (vless, hysteria2, naive, DoH), none for anything else. */
    fun ofLink(link: String?): List<String> {
        if (link.isNullOrBlank() || !link.contains("niko-ip=")) return emptyList()
        val url = runCatching { ("https://" + link.substringAfter("://")).toHttpUrl() }.getOrNull() ?: return emptyList()
        return url.queryParameterValues("niko-ip").flatMap { addrs(it) }.distinct()
    }

    /**
     * names-v1 §5 p. 7: [request] to [node] for libcore (raysub, rayapi, the probe) goes by the node's name with its
     * addresses to fall back to, or by its host as before v0.34.0.
     */
    fun reach(request: JsonObject, node: RayNode) {
        request.addProperty("host", node.reachName ?: node.host)
        node.reachName?.let { request.add("addrs", JsonArray().apply { node.knownAddrs.forEach { add(it) } }) }
    }

    /**
     * The node's name and addresses in [o] (an rcdev1 package, the subscription's payload). The name comes only with
     * addresses (§3): without them it is left out, as a node whose -host is a name sends neither.
     */
    fun of(o: JsonObject): Pair<String?, List<String>> {
        val addrs = addrs((o.get("addrs") as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.asString })
        val name = name((o.get("dns_name") as? JsonPrimitive)?.takeIf { it.isString }?.asString)
        return if (addrs.isEmpty()) null to emptyList() else name to addrs
    }
}

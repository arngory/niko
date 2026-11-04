package io.niko.raycontrol

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import io.niko.database.DataStore

/**
 * The raycontrol state for the debug export: the settings, each node without its keys, links or address, and what
 * the channel store holds per network (a hash of it, never its names): the profiles' probe results, Brutal's rates,
 * the public Yggdrasil peers taken, the summary's state, the node's hints. What the channel check and the watchdog
 * decided on, for reading a network's trouble afterwards.
 */
object RayDiagnostics {

    private val IPV4 = Regex("""\b\d{1,3}(\.\d{1,3}){3}\b""")
    private val IPV6 = Regex("""\[[0-9a-fA-F:.]+]|\b[0-9a-fA-F]{1,4}(:[0-9a-fA-F]{0,4}){2,7}\b""")

    /** Every field named *error, at any depth of [element], without [host] and IP addresses. */
    fun redactErrors(element: com.google.gson.JsonElement, host: String?) {
        when {
            element.isJsonObject -> element.asJsonObject.entrySet().forEach { (key, value) ->
                if (key.endsWith("error") && value.isJsonPrimitive) {
                    element.asJsonObject.addProperty(key, redact(value.asString, host))
                } else {
                    redactErrors(value, host)
                }
            }
            element.isJsonArray -> element.asJsonArray.forEach { redactErrors(it, host) }
        }
    }

    /** [text] without [host] and IP addresses. */
    fun redact(text: String, host: String?): String {
        var s = text
        if (!host.isNullOrBlank()) s = s.replace(host, "<node>", ignoreCase = true)
        return s.replace(IPV6, "<ip>").replace(IPV4, "<ip>")
    }

    fun dump(): String = buildString {
        appendLine("raycontrol:")
        appendLine(
            "  settings: auto_switch=${DataStore.raycontrolAutoSwitch} brutal=${DataStore.raycontrolBrutal} " +
                "reports=${DataStore.raycontrolReports}"
        )
        appendLine("  network now: ${RayChannel.network()?.let { "${it.key} (${it.kind})" } ?: "none"}")
        val nodes = runCatching { RayNodes.list() }.getOrDefault(emptyList())
        val labels = nodes.mapIndexed { i, node -> node.spa.nodeId to "node ${i + 1}" }.toMap()
        for (node in nodes) {
            appendLine("  ${labels[node.spa.nodeId]}:")
            appendLine("    services: " + node.services.joinToString { "${it.id} ${it.kind}" })
            appendLine("    profiles: " + node.profileIds.orEmpty().keys.sorted().joinToString())
            appendLine(
                "    subscription: seq ${node.subSeq}, refresh at ${node.refreshAt}, clock skew ${node.clockSkew} s, " +
                    "doh ${node.doh != null}, admin ${node.isAdmin}"
            )
            appendLine(
                "    probe: " + (node.probe?.let { "v${it.v} udp=${it.udp} tunnel_svc=${it.tunnelSvc}" } ?: "none")
            )
        }
        val channel = RayChannel.exportState()
        val out = JsonObject()
        for ((nodeId, value) in channel.entrySet()) {
            val host = nodes.firstOrNull { it.spa.nodeId == nodeId }?.host
            // an error may name the node's address ("dial tcp 198.51.100.7:443: ..."): left out, at any depth
            redactErrors(value, host)
            out.add(labels[nodeId] ?: "node ?", value)
        }
        appendLine("  channel by network:")
        GsonBuilder().setPrettyPrinting().create().toJson(out).lines().forEach { appendLine("    $it") }
    }
}

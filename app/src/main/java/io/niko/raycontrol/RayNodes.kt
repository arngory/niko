package io.niko.raycontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.Key
import io.niko.database.DataStore
import io.niko.ktx.Logs
import libcore.Libcore

/** An imported raycontrol node: SPA v2 parameters and this device's wrapped key. */
data class RayNode(
    val host: String,
    val spa: RaySpaParams,
    val deviceId: String,
    val deviceKeyWrapped: String,
    val services: List<RayService>,
) {
    /** the key reference handed to the core; the key itself never enters the config */
    val keyRef get() = "raycontrol:${spa.nodeId}:$deviceId"

    fun matchesHost(address: String?) =
        !address.isNullOrBlank() && address.trim().removePrefix("[").removeSuffix("]").equals(host, ignoreCase = true)

    fun toJson() = JsonObject().apply {
        addProperty("host", host)
        add("spa", spa.toJson())
        addProperty("device_id", deviceId)
        addProperty("device_key_wrapped", deviceKeyWrapped)
        add("services", JsonArray().apply {
            services.forEach {
                add(JsonObject().apply {
                    addProperty("id", it.id)
                    addProperty("kind", it.kind)
                    addProperty("name", it.name)
                })
            }
        })
    }

    companion object {
        fun fromJson(o: JsonObject) = RayNode(
            host = o.string("host"),
            spa = RaySpaParams.fromJson(o.getAsJsonObject("spa")),
            deviceId = o.string("device_id"),
            deviceKeyWrapped = o.string("device_key_wrapped"),
            services = o.getAsJsonArray("services")?.map {
                val s = it.asJsonObject
                RayService(s.long("id").toInt(), s.string("kind"), s.string("name"))
            }.orEmpty(),
        )
    }
}

/**
 * The knock outbound options for a node: SPA v2 with the device key passed by reference. Scope is empty, so the
 * node opens everything the device is allowed to use.
 */
fun RayNode.knockOptions(): Map<String, Any> = mapOf(
    "spa_v2" to mapOf(
        "node_id" to spa.nodeId,
        "epoch_len_s" to spa.epochLenS,
        "slot_len_s" to spa.slotLenS,
        "port_key" to spa.portKey,
        "port_base" to spa.portBase,
        "port_range" to spa.portRange,
        "len_min" to spa.lenMin,
        "len_max" to spa.lenMax,
        "device_key_ref" to keyRef,
    ),
    // a UDP session without replies sends a new packet once
    "silence" to "15s",
)

object RayNodes {

    fun list(): List<RayNode> {
        val raw = DataStore.configurationStore.getString(Key.RAYCONTROL_NODES, null)
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            JsonParser.parseString(raw).asJsonArray.mapNotNull {
                try {
                    RayNode.fromJson(it.asJsonObject)
                } catch (e: Exception) {
                    Logs.w("raycontrol: skipping a broken node entry: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            Logs.w("raycontrol: broken node list: ${e.message}")
            emptyList()
        }
    }

    private fun save(nodes: List<RayNode>) {
        DataStore.configurationStore.putString(
            Key.RAYCONTROL_NODES, JsonArray().apply { nodes.forEach { add(it.toJson()) } }.toString()
        )
    }

    /** Imports a registration; the same node is replaced (a new device key for it). */
    @Synchronized
    fun import(registration: RayRegistration): RayNode {
        val wrapped = DeviceKeyVault.wrap(hexToBytes(registration.deviceKey))
        val node = RayNode(registration.host, registration.spa, registration.deviceId, wrapped, registration.services)
        save(list().filter { it.spa.nodeId != node.spa.nodeId } + node)
        return node
    }

    @Synchronized
    fun remove(nodeId: String) {
        val nodes = list()
        nodes.filter { it.spa.nodeId == nodeId }.forEach { Libcore.spaSetDeviceKey(it.keyRef, "") }
        save(nodes.filter { it.spa.nodeId != nodeId })
    }

    fun forHost(address: String?): RayNode? = if (address.isNullOrBlank()) null else list().firstOrNull { it.matchesHost(address) }

    /** Hands the node's device key to the core and returns the knock outbound options. */
    fun prepareKnock(node: RayNode): Map<String, Any> {
        val key = try {
            DeviceKeyVault.unwrap(node.deviceKeyWrapped)
        } catch (e: Exception) {
            throw Exception("raycontrol node ${node.host}: the device key cannot be read (import the node again): ${e.message}")
        }
        Libcore.spaSetDeviceKey(node.keyRef, key.joinToString("") { "%02x".format(it) })
        key.fill(0)
        return node.knockOptions()
    }

    private fun hexToBytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

package io.niko.raycontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.Key
import io.niko.database.DataStore
import io.niko.ktx.Logs
import io.niko.ktx.app
import libcore.Libcore
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.locks.ReentrantLock

/** An imported raycontrol node: SPA v2 parameters and this device's wrapped key. */
data class RayNode(
    val host: String,
    val spa: RaySpaParams,
    val deviceId: String,
    val deviceKeyWrapped: String,
    val services: List<RayService>,
    val subscription: RaySubscriptionInfo? = null,
    // the last accepted subscription (seq never goes back) and the group its profiles live in
    val subSeq: Long = 0,
    val groupId: Long = 0,
    // profile key (svc, see RaySubscription.profileKeys) → its row, so an update keeps the rows and the selection;
    // null = a record from 1.13.0, which kept no ids: its group's rows are matched by name once
    val profileIds: Map<String, Long>? = emptyMap(),
    // when to fetch the subscription again (unix s): the next rotation or before expiry;
    // 0 = not planned yet (planned at start), STOPPED = the node refused this device, import it again
    val refreshAt: Long = 0,
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
        subscription?.let { add("sub", it.toJson()) }
        addProperty("sub_seq", subSeq)
        addProperty("group_id", groupId)
        profileIds?.let { ids -> add("profile_ids", JsonObject().apply { ids.forEach { (k, v) -> addProperty(k, v) } }) }
        addProperty("refresh_at", refreshAt)
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
            subscription = (o.get("sub") as? JsonObject)?.let { RaySubscriptionInfo.fromJson(it) },
            subSeq = o.get("sub_seq")?.asLong ?: 0,
            groupId = o.get("group_id")?.asLong ?: 0,
            profileIds = (o.get("profile_ids") as? JsonObject)?.entrySet()?.associate { it.key to it.value.asLong },
            refreshAt = o.get("refresh_at")?.asLong ?: 0,
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

/**
 * One writer at a time across the app's processes: the UI and the scheduled refresh (`:bg`) both change the node
 * list and the node's profile rows. Reentrant within a thread.
 */
object RayNodesLock {
    private val local = ReentrantLock()

    fun <T> withLock(block: () -> T): T {
        local.lock()
        try {
            if (local.holdCount > 1) return block()
            RandomAccessFile(File(app.filesDir, "raycontrol.lock"), "rw").channel.use { channel ->
                // FileLock is AutoCloseable only from API 24
                val lock = channel.lock()
                try {
                    return block()
                } finally {
                    lock.release()
                }
            }
        } finally {
            local.unlock()
        }
    }
}

object RayNodes {

    const val STOPPED = -1L

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
    fun import(registration: RayRegistration): RayNode = RayNodesLock.withLock {
        val wrapped = DeviceKeyVault.wrap(hexToBytes(registration.deviceKey))
        // a new registration of the same node keeps its profile group and rows
        val old = list().firstOrNull { it.spa.nodeId == registration.spa.nodeId }
        val node = RayNode(
            registration.host, registration.spa, registration.deviceId, wrapped, registration.services,
            registration.subscription, groupId = old?.groupId ?: 0, profileIds = if (old == null) emptyMap() else old.profileIds,
        )
        save(list().filter { it.spa.nodeId != node.spa.nodeId } + node)
        node
    }

    /**
     * Removes the stored node and drops its key from this process's core (latency tests ran here). The service's
     * core drops it on the reload that follows (buildConfig).
     */
    fun remove(nodeId: String) {
        val left = RayNodesLock.withLock {
            list().filter { it.spa.nodeId != nodeId }.also { save(it) }
        }
        Libcore.spaRetainDeviceKeys(left.joinToString("\n") { it.keyRef })
    }

    fun get(nodeId: String) = list().firstOrNull { it.spa.nodeId == nodeId }

    /**
     * Changes the stored node in place: [change] gets the current entry, so a re-import (a new key) in between is
     * kept. Returns the stored node, or null when it was removed meanwhile.
     */
    fun update(nodeId: String, change: (RayNode) -> RayNode): RayNode? = RayNodesLock.withLock {
        var updated: RayNode? = null
        val nodes = list().map {
            if (it.spa.nodeId == nodeId) change(it).also { n -> updated = n } else it
        }
        if (updated != null) save(nodes)
        updated
    }

    /** the device key as hex, for the core */
    fun deviceKeyHex(node: RayNode): String {
        val key = DeviceKeyVault.unwrap(node.deviceKeyWrapped)
        return key.joinToString("") { "%02x".format(it) }.also { key.fill(0) }
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

package io.niko.raycontrol

import io.niko.database.SagerDatabase
import io.niko.fmt.v2ray.StandardV2RayBean
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.hysteria.HysteriaBean
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

/**
 * The credentials of one subscription profile (subscription-v1 `link` and `next`): the row holds [link] or, from
 * [from] on (node time), [next]. [active] says which one the row holds now; the other is the fallback when the
 * running one stops working (raycontrol niko-r4-note.md §1).
 */
data class RayLinks(val name: String, val link: String, val next: String? = null, val from: Long = 0, val nextActive: Boolean = false) {
    val activeLink get() = if (nextActive && next != null) next else link

    fun toJson() = JsonObject().apply {
        addProperty("name", name)
        addProperty("link", link)
        next?.let { addProperty("next", it) }
        addProperty("from", from)
        addProperty("next_active", nextActive)
    }

    companion object {
        fun fromJson(o: JsonObject) = RayLinks(
            name = o.get("name")?.asString.orEmpty(),
            link = o.string("link"),
            next = o.get("next")?.takeIf { it.isJsonPrimitive }?.asString,
            from = o.get("from")?.asLong ?: 0,
            nextActive = o.get("next_active")?.asBoolean == true,
        )
    }
}

/**
 * The device's client certificate for the node's remote API (api-v1.md §1): the node knows the device by the key.
 * The key is wrapped by the Android Keystore like the device key.
 */
data class RayIdentity(val cert: String, val keyWrapped: String, val pin: String, val createdAt: Long) {
    fun toJson() = JsonObject().apply {
        addProperty("cert", cert)
        addProperty("key_wrapped", keyWrapped)
        addProperty("pin", pin)
        addProperty("created_at", createdAt)
    }

    companion object {
        fun fromJson(o: JsonObject) = RayIdentity(o.string("cert"), o.string("key_wrapped"), o.string("pin"), o.long("created_at"))
    }
}

/**
 * The subscription's `probe` (probe-v1.md §1): v1, the UDP echo, and the name inside the tunnels of tunnelSvc that
 * leads to the probe.
 */
data class RayProbeInfo(
    val v: Int, val udp: Boolean, val tunnel: String?, val tunnelSvc: List<Int>,
    // optional fields of the summary the node takes (probe-v1.md §6.1, raycontrol v0.8): sent only when listed
    val report: List<String> = emptyList(),
) {
    fun reachesThrough(svc: Int?) = svc != null && tunnel != null && svc in tunnelSvc

    fun toJson() = JsonObject().apply {
        addProperty("v", v)
        addProperty("udp", udp)
        tunnel?.let { addProperty("tunnel", it) }
        add("tunnel_svc", JsonArray().apply { tunnelSvc.forEach { add(it) } })
        if (report.isNotEmpty()) add("report", JsonArray().apply { report.forEach { add(it) } })
    }

    companion object {
        fun fromJson(o: JsonObject) = RayProbeInfo(
            v = o.get("v")?.asInt ?: 0,
            udp = o.get("udp")?.asBoolean ?: false,
            tunnel = o.get("tunnel")?.takeIf { it.isJsonPrimitive }?.asString,
            tunnelSvc = (o.get("tunnel_svc") as? JsonArray)?.mapNotNull { runCatching { it.asInt }.getOrNull() }.orEmpty(),
            report = (o.get("report") as? JsonArray)?.mapNotNull { runCatching { it.asString }.getOrNull() }.orEmpty(),
        )
    }
}

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
    // profile key → its current and next credential, for the switch at `from` and the fallback
    val links: Map<String, RayLinks> = emptyMap(),
    // node clock minus ours at the last fetch (s): rotation times are the node's
    val clockSkew: Long = 0,
    // the node's DoH from the subscription (https://<name>/dns-query/<token>, niko-* dropped): the remote DNS
    // while a profile of this node runs
    val doh: String? = null,
    // admin devices: the node's remote API and this device's client certificate for it
    val api: RayApiInfo? = null,
    val identity: RayIdentity? = null,
    // a rotation under way: the new key is kept before the node switches to it (api-v1.md §6)
    val identityNext: RayIdentity? = null,
    // the subscription's `probe`: the node answers channel probes (probe-v1.md), directly and through these tunnels
    val probe: RayProbeInfo? = null,
    // the subscription's spa.carriers (raycontrol v0.10.1): the SPA carriers the node takes, in the order to try
    // them; null = an older node (UDP, ClientHello, bare TCP)
    val spaCarriers: List<String>? = null,
    // fetches of the subscription failed in a row on that network (the backoff of the retries); 0 after a success
    val failedFetches: Int = 0,
    val failedNetwork: String? = null,
    // relays in front of the node (relay-v1 §4, a draft): profiles of the subscription's `via` go through them
    val relays: List<RayRelay> = emptyList(),
    // the subscription's dns_probe key (dnsprobe-v1): probes the resolvers of the node's DNS tunnels, base64url
    val dnsProbeKey: String? = null,
    // when the last subscription was taken, and until when it holds (this device's clock, unix s; 0 = unknown):
    // the node's card
    val subUpdatedAt: Long = 0,
    val subExpires: Long = 0,
    // the name the node's admin gave it (the subscription's `name`), null none: what its group and card show
    val name: String? = null,
) {
    /** What the node is called in the app: its admin's name, else "raycontrol · <host>" as before. */
    val displayName get() = groupName(host, name)

    /** The node in a line of text: its name and address, or the address alone. */
    val label get() = name?.let { "$it · $host" } ?: host

    /** The node's SPA parameters for the core, with the carriers it takes. */
    fun spaJson(): JsonObject = spa.toJson().apply {
        spaCarriers?.let { carriers -> add("carriers", JsonArray().apply { carriers.forEach { add(it) } }) }
    }

    /** an admin device of the node, registered through the API: the admin mode is available */
    val isAdmin get() = api != null && identity != null

    /** the key reference handed to the core; the key itself never enters the config */
    val keyRef get() = "raycontrol:${spa.nodeId}:$deviceId"

    fun matchesHost(address: String?) =
        !address.isNullOrBlank() && address.trim().removePrefix("[").removeSuffix("]").equals(host, ignoreCase = true)

    fun toJson() = JsonObject().apply {
        addProperty("host", host)
        add("spa", spa.toJson())
        addProperty("device_id", deviceId)
        addProperty("device_key_wrapped", deviceKeyWrapped)
        add("services", JsonArray().apply { services.forEach { add(it.toJson()) } })
        subscription?.let { add("sub", it.toJson()) }
        addProperty("sub_seq", subSeq)
        addProperty("group_id", groupId)
        profileIds?.let { ids -> add("profile_ids", JsonObject().apply { ids.forEach { (k, v) -> addProperty(k, v) } }) }
        addProperty("refresh_at", refreshAt)
        add("links", JsonObject().apply { links.forEach { (k, v) -> add(k, v.toJson()) } })
        addProperty("clock_skew", clockSkew)
        doh?.let { addProperty("doh", it) }
        api?.let { add("api", it.toJson()) }
        identity?.let { add("identity", it.toJson()) }
        identityNext?.let { add("identity_next", it.toJson()) }
        probe?.let { add("probe", it.toJson()) }
        spaCarriers?.let { carriers -> add("spa_carriers", JsonArray().apply { carriers.forEach { add(it) } }) }
        if (failedFetches > 0) addProperty("failed_fetches", failedFetches)
        failedNetwork?.let { addProperty("failed_network", it) }
        if (relays.isNotEmpty()) add("relays", JsonArray().apply { relays.forEach { add(it.toJson()) } })
        dnsProbeKey?.let { addProperty("dns_probe_key", it) }
        if (subUpdatedAt > 0) addProperty("sub_updated_at", subUpdatedAt)
        if (subExpires > 0) addProperty("sub_expires", subExpires)
        name?.let { addProperty("name", it) }
    }

    /** The relay with this address, for a profile row through it. */
    fun relayAt(address: String?): RayRelay? {
        val host = address?.trim()?.removePrefix("[")?.removeSuffix("]") ?: return null
        return relays.firstOrNull { it.host.trim('[', ']').equals(host, ignoreCase = true) }
    }

    companion object {
        fun fromJson(o: JsonObject) = RayNode(
            host = o.string("host"),
            spa = RaySpaParams.fromJson(o.getAsJsonObject("spa")),
            deviceId = o.string("device_id"),
            deviceKeyWrapped = o.string("device_key_wrapped"),
            services = o.getAsJsonArray("services")?.map { RayService.fromJson(it.asJsonObject) }.orEmpty(),
            subscription = (o.get("sub") as? JsonObject)?.let { RaySubscriptionInfo.fromJson(it) },
            subSeq = o.get("sub_seq")?.asLong ?: 0,
            groupId = o.get("group_id")?.asLong ?: 0,
            profileIds = (o.get("profile_ids") as? JsonObject)?.entrySet()?.associate { it.key to it.value.asLong },
            refreshAt = o.get("refresh_at")?.asLong ?: 0,
            links = (o.get("links") as? JsonObject)?.entrySet()?.associate { it.key to RayLinks.fromJson(it.value.asJsonObject) }.orEmpty(),
            clockSkew = o.get("clock_skew")?.asLong ?: 0,
            doh = o.get("doh")?.takeIf { it.isJsonPrimitive }?.asString,
            api = (o.get("api") as? JsonObject)?.let { RayApiInfo.fromJson(it) },
            identity = (o.get("identity") as? JsonObject)?.let { RayIdentity.fromJson(it) },
            identityNext = (o.get("identity_next") as? JsonObject)?.let { RayIdentity.fromJson(it) },
            probe = (o.get("probe") as? JsonObject)?.let { RayProbeInfo.fromJson(it) },
            spaCarriers = (o.get("spa_carriers") as? JsonArray)?.mapNotNull { runCatching { it.asString }.getOrNull() },
            failedFetches = o.get("failed_fetches")?.asInt ?: 0,
            failedNetwork = o.get("failed_network")?.takeIf { it.isJsonPrimitive }?.asString,
            relays = RayRelay.listFromJson(o.get("relays") as? JsonArray),
            dnsProbeKey = o.get("dns_probe_key")?.takeIf { it.isJsonPrimitive }?.asString,
            subUpdatedAt = o.get("sub_updated_at")?.asLong ?: 0,
            subExpires = o.get("sub_expires")?.asLong ?: 0,
            name = o.get("name")?.takeIf { it.isJsonPrimitive }?.asString?.let(::nodeName),
        )

        /** The group name of the node at [host] called [name] (null: none given). */
        fun groupName(host: String, name: String?) = name ?: "raycontrol · $host"

        // a name is shown as it is, its length in characters (code points) bounded
        private const val MAX_NAME = 48

        /**
         * [raw] as a name to show, or null: none, or nothing left. Control and format characters go (a right-to-left
         * override would turn the rest around, a line break would split it), whitespace runs become one space, at
         * most [MAX_NAME] characters stay.
         */
        fun nodeName(raw: String?): String? {
            if (raw == null) return null
            val out = StringBuilder()
            var space = false
            var count = 0
            var i = 0
            while (i < raw.length && count < MAX_NAME) {
                val cp = raw.codePointAt(i)
                i += Character.charCount(cp)
                when {
                    Character.isWhitespace(cp) || Character.isSpaceChar(cp) -> space = out.isNotEmpty()
                    Character.getType(cp).let {
                        it == Character.CONTROL.toInt() || it == Character.FORMAT.toInt() ||
                            it == Character.UNASSIGNED.toInt() || it == Character.PRIVATE_USE.toInt() ||
                            it == Character.SURROGATE.toInt()
                    } -> {}
                    else -> {
                        if (space) {
                            out.append(' ')
                            count++
                            space = false
                        }
                        if (count < MAX_NAME) {
                            out.appendCodePoint(cp)
                            count++
                        }
                    }
                }
            }
            return out.toString().trim().takeIf { it.isNotEmpty() }
        }
    }
}

/**
 * The knock outbound options for a node: SPA v2 with the device key passed by reference. Scope is empty, so the
 * node opens everything the device is allowed to use.
 */
fun RayNode.knockOptions(relay: RayRelay? = null): Map<String, Any> = mapOf(
    // through a relay: its SPA, with K_relay out of the same device key (relay-v1 §3)
    "spa_v2" to (relay?.spa ?: spa).let { p ->
        mapOf(
            "node_id" to p.nodeId,
            "epoch_len_s" to p.epochLenS,
            "slot_len_s" to p.slotLenS,
            "port_key" to p.portKey,
            "port_base" to p.portBase,
            "port_range" to p.portRange,
            "len_min" to p.lenMin,
            "len_max" to p.lenMax,
            "device_key_ref" to keyRef,
        )
    } + (if (relay != null) mapOf("relay" to mapOf("exit_node_id" to spa.nodeId, "id" to relay.id))
    else spaCarriers?.let { mapOf("carriers" to it) } ?: emptyMap()),
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

    /** The last parsed list and the text it came from: a config build asks for it once per hop. */
    @Volatile
    private var parsed: Pair<String, List<RayNode>>? = null

    fun list(): List<RayNode> {
        val raw = DataStore.configurationStore.getString(Key.RAYCONTROL_NODES, null)
        if (raw.isNullOrBlank()) return emptyList()
        parsed?.let { (text, nodes) -> if (text == raw) return nodes }
        return parse(raw).also { parsed = raw to it }
    }

    private fun parse(raw: String): List<RayNode> {
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
        syncLogRedactions(nodes)
    }

    /**
     * The nodes' addresses never reach the log or the debug export (libcore redact.go): set at each process' start
     * and whenever the nodes change.
     */
    fun syncLogRedactions(nodes: List<RayNode> = runCatching { list() }.getOrDefault(emptyList())) {
        val hosts = nodes.flatMap { node ->
            RayRows.nodeHosts(node.host, node.doh, node.links.values.flatMap { listOfNotNull(it.link, it.next) })
        }
        // subscription links carry their token in the path (panels: /sub/<token>)
        val paths = runCatching { SagerDatabase.groupDao.allGroups() }.getOrDefault(emptyList())
            .mapNotNull { it.subscription?.link }.flatMap(RayRows::subscriptionSecrets)
        runCatching { libcore.Libcore.setLogRedactions((hosts + paths).distinct().joinToString("\n")) }
    }

    /**
     * Imports a registration; the same node is replaced (a new device key for it). [identity] is the client
     * certificate a registration through the API made; a package of the same device keeps the one it had.
     */
    fun import(registration: RayRegistration, identity: RayIdentity? = null): RayNode = RayNodesLock.withLock {
        val wrapped = DeviceKeyVault.wrap(hexToBytes(registration.deviceKey))
        // a new registration of the same node keeps its profile group and rows
        val old = list().firstOrNull { it.spa.nodeId == registration.spa.nodeId }
        val node = RayNode(
            registration.host, registration.spa, registration.deviceId, wrapped, registration.services,
            registration.subscription, groupId = old?.groupId ?: 0, profileIds = if (old == null) emptyMap() else old.profileIds,
            api = registration.api,
            identity = identity ?: old?.identity?.takeIf { old.deviceId == registration.deviceId && registration.api != null },
            // an older package of the same node (same signing and TLS keys) must not let an older subscription in;
            // a node with new keys starts its seq anew
            subSeq = old?.subSeq?.takeIf {
                val a = old.subscription
                val b = registration.subscription
                a != null && b != null && a.pin == b.pin && a.ed25519 == b.ed25519 && a.mldsa65 == b.mldsa65
            } ?: 0,
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
            RayChannel.forget(nodeId)
            list().filter { it.spa.nodeId != nodeId }.also { save(it) }
        }
        retainCoreKeys(left)
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

    /** the node whose subscription row [profileId] is, with the row's key */
    fun ownerOf(profileId: Long): Pair<RayNode, String>? {
        if (profileId <= 0) return null
        for (node in list()) {
            val key = node.profileIds?.entries?.firstOrNull { it.value == profileId && !it.key.startsWith("stale:") }?.key
            if (key != null) return node to key
        }
        return null
    }

    fun forHost(address: String?): RayNode? = if (address.isNullOrBlank()) null else list().firstOrNull { it.matchesHost(address) }

    /** The node behind the relay at this address, and the relay (a profile row through a relay, relay-v1 §4). */
    fun forRelayHost(address: String?): Pair<RayNode, RayRelay>? {
        if (address.isNullOrBlank()) return null
        for (node in list()) node.relayAt(address)?.let { return node to it }
        return null
    }

    /**
     * The names the node serves (its host, the SNI and server names of its profiles, its DoH): a fake QUIC Initial
     * naming one of them would pin the flow to that backend of the node's front (niko-mux-note.md §2).
     */
    fun names(node: RayNode): Set<String> {
        val names = mutableSetOf(node.host.lowercase())
        node.doh?.let { runCatching { java.net.URI(it).host }.getOrNull() }?.let { names += it.lowercase() }
        for (id in node.profileIds.orEmpty().values) {
            val bean = SagerDatabase.proxyDao.getById(id)?.requireBean() ?: continue
            names += listOfNotNull(
                (bean as? HysteriaBean)?.sni, (bean as? StandardV2RayBean)?.sni, (bean as? NaiveBean)?.sni,
                bean.serverAddress, bean.yggPeer?.let { io.niko.fmt.yggPeerHost(it) },
            ).map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        }
        return names
    }

    /** Hands the node's device key to the core and returns the knock outbound options. */
    fun prepareKnock(node: RayNode, relay: RayRelay? = null): Map<String, Any> {
        prepareDeviceKey(node)
        return node.knockOptions(relay)
    }

    /**
     * Puts the node's device key into the core under [RayNode.keyRef]: SPA and the dnsauth-v1 tag of its DNS tunnels
     * take it by that reference, so the key itself never is in a config.
     */
    fun prepareDeviceKey(node: RayNode) {
        // the Keystore unwrap is slow and a build asks once per hop: once per key and process (a new import of the
        // node wraps its key anew, so a changed key is set again)
        if (keysInCore[node.keyRef] == node.deviceKeyWrapped) return
        val key = try {
            DeviceKeyVault.unwrap(node.deviceKeyWrapped)
        } catch (e: Exception) {
            throw Exception("raycontrol node ${node.host}: the device key cannot be read (import the node again): ${e.message}")
        }
        Libcore.spaSetDeviceKey(node.keyRef, key.joinToString("") { "%02x".format(it) })
        key.fill(0)
        keysInCore[node.keyRef] = node.deviceKeyWrapped
    }

    /** the device keys this process put into its core: reference to the wrapped key it came from */
    private val keysInCore = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** Drops from the core (and from [keysInCore]) the device keys of nodes no longer stored. */
    fun retainCoreKeys(nodes: List<RayNode> = list()) {
        val refs = nodes.map { it.keyRef }
        keysInCore.keys.retainAll(refs.toSet())
        Libcore.spaRetainDeviceKeys(refs.joinToString("\n"))
    }

    private fun hexToBytes(hex: String) = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
}

package io.niko.raycontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase

/**
 * A device of the user on the node's LAN (lan-v1 §1.2, raycontrol v0.23.0): its name for display, its DNS label
 * (the node computes it, §1.3) and its Yggdrasil addresses, the next one valid from [nextFrom] (node's clock).
 */
data class RayLanDevice(
    val id: String,
    val name: String,
    val label: String,
    val addr: String,
    val nextAddr: String? = null,
    val nextFrom: Long = 0,
) {
    /** Where the device is at [nodeNow]: its next address from its `from` on. */
    fun addressAt(nodeNow: Long) = nextAddr?.takeIf { nextFrom in 1..nodeNow } ?: addr

    fun toJson() = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        addProperty("label", label)
        addProperty("addr", addr)
        nextAddr?.let { next -> add("next", JsonObject().apply { addProperty("addr", next); addProperty("from", nextFrom) }) }
    }

    companion object {
        /** A device as the node lists it; null when its id or address is not one (the rest of the list stays). */
        fun fromJson(o: JsonObject): RayLanDevice? {
            val id = o.str("id")?.lowercase()?.takeIf { ID.matches(it) } ?: return null
            val addr = o.str("addr")?.let(::yggAddress) ?: return null
            val next = o.get("next") as? JsonObject
            val nextAddr = next?.str("addr")?.let(::yggAddress)
            return RayLanDevice(
                id = id,
                // shown only; the node's names are [a-z0-9._-], anything else is cut away
                name = o.str("name")?.take(64)?.filter { it.isLetterOrDigit() || it in "._- " }?.ifBlank { null } ?: id,
                // a label that is not one (the node computes them, §1.3) is replaced as the node would for an empty one
                label = o.str("label")?.takeIf { LABEL.matches(it) } ?: "d-${id.take(8)}",
                addr = addr,
                nextAddr = nextAddr,
                nextFrom = if (nextAddr != null) next?.num("from") ?: 0 else 0,
            )
        }
    }
}

/** An address the user's devices kept after a rotation: taken from [id] until [until] (this device's clock). */
data class RayLanPrevious(val id: String, val addr: String, val until: Long) {
    fun toJson() = JsonObject().apply {
        addProperty("id", id)
        addProperty("addr", addr)
        addProperty("until", until)
    }

    companion object {
        fun fromJson(o: JsonObject): RayLanPrevious? {
            val id = o.str("id") ?: return null
            val addr = o.str("addr")?.let(::yggAddress) ?: return null
            return RayLanPrevious(id, addr, o.num("until") ?: return null)
        }
    }
}

/**
 * The subscription's `lan` (lan-v1 §1.2): the Yggdrasil service the network runs on, the relay ceiling through the
 * node (Mbit/s per user, 0 = only directly in one Wi-Fi), the node's DNS label, and, once this device has shown
 * itself, the multicast password and the user's devices (this one among them).
 */
data class RayLan(
    val svc: Int,
    val relayMbps: Int,
    val label: String,
    val password: String? = null,
    val nextPassword: String? = null,
    val nextFrom: Long = 0,
    val devices: List<RayLanDevice> = emptyList(),
) {
    /** The node sent the password and the list: this device is shown to the others (§1.1, symmetry). */
    val shown get() = password != null

    /** The user's other devices: [deviceId] is this one's (X-Raycontrol-Device). */
    fun others(deviceId: String) = devices.filter { it.id != deviceId.lowercase() }

    fun toJson() = JsonObject().apply {
        addProperty("v", 1)
        addProperty("svc", svc)
        addProperty("relay", relayMbps)
        addProperty("label", label)
        password?.let { addProperty("password", it) }
        nextPassword?.let { next ->
            add("next", JsonObject().apply { addProperty("password", next); addProperty("from", nextFrom) })
        }
        if (devices.isNotEmpty()) add("devices", JsonArray().apply { devices.forEach { add(it.toJson()) } })
    }

    companion object {
        /** `lan` of version 1, else null: another version is unknown to this client and skipped whole (§1.2). */
        fun fromJson(o: JsonObject?): RayLan? {
            if (o == null || runCatching { o.get("v")?.asInt }.getOrNull() != 1) return null
            val svc = o.num("svc")?.toInt()?.takeIf { it > 0 } ?: return null
            // a node label that is no DNS label would give no names: the list is still a network, without names
            val label = o.str("label")?.takeIf { LABEL.matches(it) } ?: return null
            val next = o.get("next") as? JsonObject
            val nextPassword = next?.str("password")?.takeIf(::password)
            return RayLan(
                svc = svc,
                relayMbps = o.num("relay")?.toInt()?.coerceIn(0, 100_000) ?: DEFAULT_RELAY,
                label = label,
                password = o.str("password")?.takeIf(::password),
                nextPassword = nextPassword,
                nextFrom = if (nextPassword != null) next?.num("from") ?: 0 else 0,
                devices = (o.get("devices") as? JsonArray)?.asSequence()?.mapNotNull { (it as? JsonObject)?.let(RayLanDevice::fromJson) }
                    ?.distinctBy { it.id }?.take(MAX_DEVICES)?.toList().orEmpty(),
            )
        }

        // niko-server's default ceiling (lan-v1 §4.4)
        const val DEFAULT_RELAY = 20
        private const val MAX_DEVICES = 256

        // the multicast password: 43 base64url characters as the node makes it (§3), within yggdrasil-go's 64 bytes
        private fun password(s: String) = s.length in 16..64 && s.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }
}

/** What the user's devices are on the nodes' LANs (lan-v1): the names, the routes and the allowlist of a config. */
object RayLanNet {

    /** The zone of the names: `.internal` is for private use and never delegated (§1.3). */
    const val ZONE = "niko.internal"

    /** An address a rotated device kept: the others still take it this long after it changed (§5). */
    const val PREVIOUS_FOR = 2 * 86400L

    /** With the LAN shown, the subscription is asked at least this often: a revoked device leaves the list (§4.2). */
    const val REFRESH_AT_MOST = 6 * 3600L

    /** An `on` the node took no password for yet: asked again this soon (§5), for this long after the switch. */
    const val RETRY_ON = 60L
    const val RETRY_ON_FOR = 10 * 60L

    /**
     * The device's LAN on [node] as a config carries it: shown by the user, the node's list in hand, this device on
     * it. Null otherwise: nothing about the LAN goes into the config.
     */
    fun of(node: RayNode): RayLan? = node.lan?.takeIf { node.lanShow == true && it.shown && node.subscription != null }

    /**
     * The addresses this device takes connections from and routes to on [node]'s LAN: every listed other device's
     * current and next address, and the ones they had until two days ago ([previous], unexpired at [now]).
     */
    fun neighbours(node: RayNode, now: Long): List<String> {
        val lan = of(node) ?: return emptyList()
        val others = lan.others(node.deviceId)
        val ids = others.map { it.id }.toSet()
        return (others.flatMap { listOfNotNull(it.addr, it.nextAddr) } +
                node.lanPrevious.orEmpty().filter { it.id in ids && it.until > now }.map { it.addr }).distinct()
    }

    /**
     * The names of the user's other devices: `<device label>.<node label>.niko.internal` → the address at each
     * node's time. Two nodes with one label: the one with the larger node id takes `-<node_id[:4]>` (§1.3).
     */
    fun names(nodes: List<RayNode>, now: Long): Map<String, String> {
        val active = nodes.filter { of(it) != null }
        val names = LinkedHashMap<String, String>()
        for (node in active) {
            val lan = of(node)!!
            val clash = active.any { it !== node && of(it)!!.label == lan.label && it.spa.nodeId < node.spa.nodeId }
            val nodeLabel = if (clash) (lan.label.take(58) + "-" + node.spa.nodeId.take(4).lowercase()) else lan.label
            for (device in lan.others(node.deviceId)) {
                names.putIfAbsent("${device.label}.$nodeLabel.$ZONE", device.addressAt(now + node.clockSkew))
            }
        }
        return names
    }

    /** The node labels the names end in, for the DNS rule (after [names]' clash rule). */
    fun suffixes(names: Map<String, String>) = names.keys.map { it.substringAfter('.') }.distinct()

    /**
     * The addresses kept after this answer: the stored list's addresses of the devices still listed in [new] that it
     * no longer names, until two days after the switch (§5), with the earlier ones still unexpired. A device gone from the list (hidden, revoked)
     * takes its addresses with it.
     */
    fun previous(node: RayNode, new: RayLan?, now: Long): List<RayLanPrevious> {
        if (new == null) return emptyList()
        val listed = new.devices.associateBy { it.id }
        val kept = node.lanPrevious.orEmpty().filter { it.until > now && it.id in listed }
        val dropped = node.lan?.devices.orEmpty().filter { it.id in listed }.flatMap { old ->
            val current = listed.getValue(old.id).let { setOfNotNull(it.addr, it.nextAddr) }
            // two days after the switch the old address held until (its next's `from`, the node's clock), else from now
            val until = if (old.nextFrom > 0) old.nextFrom - node.clockSkew + PREVIOUS_FOR else now + PREVIOUS_FOR
            listOfNotNull(old.addr, old.nextAddr).filter { it !in current }.map { RayLanPrevious(old.id, it, until) }
        }
        return (dropped + kept).distinctBy { it.addr }
    }

    /**
     * The row of [node]'s subscription the LAN runs on: the Yggdrasil profile (niko-ygg) of the service [svc]
     * (`lan.svc`, §1.2: its profile must be up). Null without one: no network then.
     */
    fun row(node: RayNode, svc: Int? = of(node)?.svc): ProxyEntity? {
        if (svc == null) return null
        val ids = node.profileIds.orEmpty().filterKeys { it.substringBefore('#') == svc.toString() }.values
        return ids.asSequence().mapNotNull { SagerDatabase.proxyDao.getById(it) }
            .firstOrNull { !it.requireBean().yggPeer.isNullOrBlank() && !it.requireBean().yggKey.isNullOrBlank() }
    }

    /**
     * When to ask [node]'s subscription again for its LAN ([lan] the answer's): an `on` the node gave no password for
     * within a minute (it takes one switch a minute, §4.4), else at least every 6 h while shown; never sooner than
     * the plan otherwise.
     */
    fun refreshBy(node: RayNode, lan: RayLan?, now: Long): Long = when {
        node.lanShow != true || lan == null -> Long.MAX_VALUE
        !lan.shown && now - node.lanShowAt < RETRY_ON_FOR -> now + RETRY_ON
        // a device's next address from its `from` (the names change then), a kept address expiring: the fetch then
        // finds the config's LAN changed and restarts the core ([reloadIfChanged])
        else -> (lan.devices.mapNotNull { d -> d.nextFrom.takeIf { d.nextAddr != null && it > 0 }?.minus(node.clockSkew) } +
                node.lanPrevious.orEmpty().map { it.until }).filter { it > now }.fold(now + REFRESH_AT_MOST, ::minOf)
    }

    /**
     * What a config takes from the nodes' LANs at [now]: the neighbours, the names and the rows they run on. The
     * build that runs stores it ([noteRunning]); a different one now restarts the core ([reloadIfChanged]).
     */
    fun signature(nodes: List<RayNode>, now: Long): String = nodes.filter { of(it) != null }.joinToString("\n") { node ->
        val lan = of(node)!!
        val rows = node.profileIds.orEmpty().filterKeys { it.substringBefore('#') == lan.svc.toString() }.values.sorted()
        node.spa.nodeId + " " + lan.svc + " " + rows + " " + neighbours(node, now).sorted().joinToString(",")
    } + "\n" + names(nodes, now).entries.joinToString(",") { "${it.key}=${it.value}" }

    /** The running config's LAN: [signature] at its build, in the store both processes read. */
    fun noteRunning(nodes: List<RayNode>, now: Long) {
        io.niko.database.DataStore.lanRunning = signature(nodes, now)
    }

    /**
     * The neighbours, their names or the rows changed since the running config was built (a fetch, the user's
     * switch, a device's next address from its `from`, a kept address expired): a running core takes them at once.
     * The service ignores the reload when it is not running.
     */
    fun reloadIfChanged(now: Long) {
        val running = io.niko.database.DataStore.lanRunning.ifEmpty { signature(emptyList(), now) }
        if (signature(RayNodes.list(), now) != running) io.niko.SagerNet.reloadService(force = true)
    }

    /**
     * The user shows this device on [nodeId]'s LAN or hides it: the choice goes to the node with a new request at
     * once (§5). Hidden, the config drops the LAN right away, before the node's answer.
     */
    suspend fun setShow(nodeId: String, show: Boolean): RaySubscriptionResult? {
        val now = System.currentTimeMillis() / 1000
        RayNodesLock.withLock {
            RayNodes.update(nodeId) { it.copy(lanShow = show, lanShowAt = now) }
        }
        reloadIfChanged(now)
        return runCatching { RaySubscription.refresh(nodeId) }.getOrNull()
    }

    /**
     * The running core carries a LAN (set by the config build that runs): the VPN then takes 200::/7 and gives the
     * apps an IPv6 address, also with IPv6 off, so they ask for and reach the devices' addresses.
     */
    @Volatile
    var running = false
}

private val ID = Regex("^[0-9a-f]{16}$")

// a DNS label as the node computes them (§1.3): LDH, no "-" at either end, at most 63
private val LABEL = Regex("^[a-z0-9]([a-z0-9-]{0,61}[a-z0-9])?$")

/**
 * A Yggdrasil address (200::/7) in its canonical form (RFC 5952), null for anything else: parsed here, strictly, as
 * the core will (netip), so no address the core refuses ever reaches a config, and one address is one string.
 */
internal fun yggAddress(s: String): String? {
    val text = s.trim().lowercase()
    if (text.isEmpty() || text.length > 39 || !text.all { it in '0'..'9' || it in 'a'..'f' || it == ':' }) return null
    val halves = text.split("::")
    if (halves.size > 2) return null
    fun groups(part: String) = if (part.isEmpty()) emptyList() else part.split(':')
    val head = groups(halves[0])
    val tail = if (halves.size == 2) groups(halves[1]) else emptyList()
    if ((head + tail).any { it.isEmpty() || it.length > 4 }) return null
    val missing = 8 - head.size - tail.size
    if (if (halves.size == 2) missing < 1 else missing != 0) return null
    val words = (head + List(missing) { "0" } + tail).map { it.toInt(16) }
    if (words[0] !in 0x200..0x3ff) return null
    // the longest run of two or more zero words becomes "::", the first of equal runs
    var best = -1
    var bestLength = 1
    var i = 0
    while (i < 8) {
        if (words[i] != 0) {
            i++
            continue
        }
        var j = i
        while (j < 8 && words[j] == 0) j++
        if (j - i > bestLength) {
            best = i
            bestLength = j - i
        }
        i = j
    }
    if (best < 0) return words.joinToString(":") { it.toString(16) }
    return words.subList(0, best).joinToString(":") { it.toString(16) } + "::" +
            words.subList(best + bestLength, 8).joinToString(":") { it.toString(16) }
}

private fun JsonObject.str(name: String) = get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asString }.getOrNull() }

private fun JsonObject.num(name: String) = get(name)?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }

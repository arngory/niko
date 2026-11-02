package io.niko.raycontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.GroupType
import io.niko.database.DataStore
import io.niko.database.GroupManager
import io.niko.database.ProfileManager
import io.niko.database.ProxyGroup
import io.niko.database.SagerDatabase
import io.niko.ktx.Logs
import io.niko.ktx.parseProxies
import libcore.Libcore
import kotlin.math.abs

/** What a subscription update did. */
data class RaySubscriptionResult(val profiles: Int, val skipped: Int, val clockSkew: Long, val spaOverTcp: Boolean) {
    /** spa-v2.md §7: SPA v2 needs the clocks within 30 s */
    val clockWrong get() = abs(clockSkew) > 30
}

/**
 * The raycontrol subscription of a node (subscription-v1.md): the core opens the port with SPA v2, fetches over
 * TLS with X25519MLKEM768 only and the pinned key, checks both node signatures, the node, seq and expiry. The
 * profiles replace those of the node's group.
 */
object RaySubscription {

    suspend fun refresh(node: RayNode): RaySubscriptionResult {
        val info = node.subscription ?: throw Exception("raycontrol node ${node.host}: its registration has no subscription")
        val request = JsonObject().apply {
            addProperty("host", node.host)
            addProperty("port", info.port)
            addProperty("pin", info.pin)
            addProperty("device_id", node.deviceId)
            addProperty("node_id", node.spa.nodeId)
            addProperty("ed25519", info.ed25519)
            addProperty("mldsa65", info.mldsa65)
            addProperty("last_seq", node.subSeq)
            add("spa", node.spa.toJson().apply { addProperty("device_key", RayNodes.deviceKeyHex(node)) })
        }
        val result = JsonParser.parseString(Libcore.raycontrolFetchSubscription(request.toString())).asJsonObject
        val payload = result.getAsJsonObject("payload")
        val now = System.currentTimeMillis() / 1000

        var skipped = 0
        val beans = (payload.get("profiles") as? JsonArray).orEmpty().mapNotNull { element ->
            val profile = element.asJsonObject
            // rotation (R4): after `from` the next credential replaces the current one
            val next = profile.get("next") as? JsonObject
            val link = if (next != null && now >= next.get("from").asLong) next.get("link").asString else profile.get("link").asString
            try {
                parseProxies(link).firstOrNull()?.apply {
                    name = profile.get("name")?.asString?.takeIf { it.isNotBlank() } ?: name
                } ?: run { skipped++; null }
            } catch (e: Exception) {
                Logs.w("raycontrol subscription: profile ${profile.get("name")}: ${e.message}")
                skipped++
                null
            }
        }

        val group = SagerDatabase.groupDao.getById(node.groupId) ?: GroupManager.createGroup(
            ProxyGroup(name = "raycontrol · ${node.host}", type = GroupType.BASIC)
        )
        val selectedWasHere = SagerDatabase.proxyDao.getById(DataStore.selectedProxy)?.groupId == group.id
        SagerDatabase.proxyDao.deleteByGroup(group.id)
        val created = beans.map { ProfileManager.createProfile(group.id, it) }
        // the selected profile was replaced: select its successor so the connection keeps a profile
        if (selectedWasHere) DataStore.selectedProxy = created.firstOrNull()?.id ?: 0L
        GroupManager.postReload(group.id)

        RayNodes.update(node.copy(subSeq = payload.get("seq").asLong, groupId = group.id))
        return RaySubscriptionResult(
            profiles = beans.size,
            skipped = skipped,
            clockSkew = result.get("clock_skew_s")?.asLong ?: 0,
            spaOverTcp = result.get("spa_over_tcp")?.asBoolean == true,
        )
    }

    private fun JsonArray?.orEmpty(): JsonArray = this ?: JsonArray()

    /** One line for the user: what was imported, and a warning when the clocks disagree. */
    fun describe(context: android.content.Context, node: RayNode, r: RaySubscriptionResult) = buildString {
        append(context.getString(io.niko.R.string.raycontrol_sub_updated, node.host, r.profiles))
        if (r.skipped > 0) append(" ").append(context.getString(io.niko.R.string.raycontrol_sub_skipped, r.skipped))
        if (r.clockWrong) append(" ").append(context.getString(io.niko.R.string.raycontrol_clock_wrong, r.clockSkew))
    }
}

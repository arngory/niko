package io.niko.raycontrol

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.ktx.Logs
import libcore.Libcore
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

/**
 * The remote API of a raycontrol node (api-v1.md): registration by invite, and the node's NodeService for admin
 * devices. The core opens the port with SPA v2 and calls over TLS 1.3 with X25519MLKEM768 only, the node's key
 * pinned and this device's client certificate.
 */
object RayApi {
    const val NODE = "raycontrol.node.v1.NodeService/"
    const val DEVICE = "raycontrol.device.v1.DeviceService/"

    /** the client key changes after this, like the REALITY keys (api-v1.md §6) */
    const val ROTATE_AFTER_S = 90L * 86400

    /** a node refusal code from the core's error ("raycontrol api: not_found: ..."), or null */
    fun code(e: Throwable): String? =
        e.message?.substringAfter("raycontrol api: ", "")?.substringBefore(':')?.trim()?.takeIf { it.isNotEmpty() && it.all { c -> c.isLowerCase() || c == '_' } }

    private fun now() = System.currentTimeMillis() / 1000

    private fun spaJson(spa: RaySpaParams, keyHex: String) = spa.toJson().apply { addProperty("device_key", keyHex) }

    /** The endpoint for the core: the keys are unwrapped for the call only. */
    private fun endpoint(node: RayNode, identity: RayIdentity? = node.identity): String {
        val api = node.api ?: throw Exception("raycontrol node ${node.host}: this device has no access to the node's API")
        identity ?: throw Exception("raycontrol node ${node.host}: no client certificate, register through an invite")
        val key = DeviceKeyVault.unwrap(identity.keyWrapped)
        try {
            return JsonObject().apply {
                addProperty("host", node.host)
                addProperty("port", api.port)
                addProperty("pin", api.pin)
                add("identity", JsonObject().apply {
                    addProperty("cert", identity.cert)
                    addProperty("key", key.toByteString().base64())
                    addProperty("pin", identity.pin)
                })
                add("spa", spaJson(node.spa, RayNodes.deviceKeyHex(node)))
            }.toString()
        } finally {
            key.fill(0)
        }
    }

    /**
     * One NodeService or DeviceService call; [method] is "<package>.<Service>/<Method>". A rotation whose answer
     * was lost left the node on the pending key: on `unauthenticated` that key is tried and kept if it works.
     */
    fun call(node: RayNode, method: String, body: JsonObject = JsonObject()): JsonObject {
        val answer = try {
            Libcore.raycontrolApiCall(endpoint(node), method, body.toString())
        } catch (e: Exception) {
            val next = node.identityNext
            if (next == null || code(e) != "unauthenticated") throw e
            val answer = Libcore.raycontrolApiCall(endpoint(node, next), method, body.toString())
            RayNodes.update(node.spa.nodeId) { if (it.identityNext == next) it.copy(identity = next, identityNext = null) else it }
            Logs.i("raycontrol ${node.host}: the node had switched to the new client key")
            answer
        }
        return if (answer.isBlank()) JsonObject() else JsonParser.parseString(answer).asJsonObject
    }

    fun node(node: RayNode, name: String, body: JsonObject = JsonObject()) = call(node, NODE + name, body)

    /**
     * Registers this device with an invite (api-v1.md §4): a new client key, SPA with the invite's key, then
     * `Register`; the answer is this device's own rcdev1 package. Returns the node and the device's role.
     */
    fun register(invite: RayInvite): Pair<RayNode, String> {
        // the node checks the expiry too; this only saves a pointless try
        if (invite.expires in 1 until now() - 120) throw Exception("raycontrol invite for ${invite.host}: expired")
        val request = JsonObject().apply {
            addProperty("host", invite.host)
            add("spa", spaJson(invite.spa, invite.inviteKey))
            addProperty("device_id", invite.inviteId)
            add("api", invite.api.toJson())
        }
        val answer = JsonParser.parseString(Libcore.raycontrolRegister(request.toString())).asJsonObject
        val registration = Rcdev1.parse(answer.get("bundle").asString)
        val identity = answer.getAsJsonObject("identity")
        val key = identity.get("key").asString.decodeBase64()?.toByteArray()
            ?: throw Exception("raycontrol: the core returned no client key")
        val wrapped = try {
            DeviceKeyVault.wrap(key)
        } finally {
            key.fill(0)
        }
        val node = RayNodes.import(
            registration, RayIdentity(identity.get("cert").asString, wrapped, identity.get("pin").asString, now())
        )
        return node to (answer.get("role")?.asString ?: "user")
    }

    /**
     * Moves an admin device to a new client key once the current one is [ROTATE_AFTER_S] old (api-v1.md §6).
     * Returns whether it rotated.
     */
    fun rotateIfDue(node: RayNode): Boolean {
        val identity = node.identity ?: return false
        if (node.api == null || now() - identity.createdAt < ROTATE_AFTER_S) return false
        // the new key is stored before the node switches to it: a lost answer leaves it pending, not lost
        val next = node.identityNext ?: run {
            val made = JsonParser.parseString(Libcore.raycontrolNewIdentity()).asJsonObject
            val key = made.get("key").asString.decodeBase64()?.toByteArray()
                ?: throw Exception("raycontrol: the core returned no client key")
            val wrapped = try {
                DeviceKeyVault.wrap(key)
            } finally {
                key.fill(0)
            }
            RayIdentity(made.get("cert").asString, wrapped, made.get("pin").asString, now()).also { pending ->
                RayNodes.update(node.spa.nodeId) { it.copy(identityNext = pending) }
            }
        }
        val nextKey = DeviceKeyVault.unwrap(next.keyWrapped)
        val nextJson = try {
            JsonObject().apply {
                addProperty("cert", next.cert)
                addProperty("key", nextKey.toByteString().base64())
                addProperty("pin", next.pin)
            }.toString()
        } finally {
            nextKey.fill(0)
        }
        try {
            Libcore.raycontrolRotateCertificate(endpoint(node), nextJson)
        } catch (e: Exception) {
            if (code(e) != "unauthenticated") throw e
            // an earlier try switched the node already: the pending key works, call() keeps it
            call(RayNodes.get(node.spa.nodeId) ?: return false, DEVICE + "GetSelf")
            return true
        }
        RayNodes.update(node.spa.nodeId) { it.copy(identity = next, identityNext = null) }
        Logs.i("raycontrol ${node.host}: client key rotated")
        return true
    }
}

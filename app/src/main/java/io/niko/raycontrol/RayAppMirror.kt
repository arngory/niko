package io.niko.raycontrol

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import io.niko.utils.compareVersions
import io.niko.utils.isNewerVersion

/**
 * A release of the app a raycontrol node holds whole (app-update-v1: the subscription's app.niko): its files at
 * /app/v1/niko/<tag>/<name>, behind the subscription's TLS, SPA and RC1. The node is trusted for nothing: the app
 * checks the release's signature and each file's hash as GitHub's (Updates).
 */
data class RayAppRelease(val tag: String, val prerelease: Boolean) {

    fun toJson() = JsonObject().apply {
        addProperty("tag", tag)
        addProperty("prerelease", prerelease)
    }

    companion object {
        // release.yml's tags: vX.Y.Z, a pre-release with a suffix after a dash (GitHub's flag by the dash)
        private val TAG = Regex("^v\\d{1,6}\\.\\d{1,6}\\.\\d{1,6}(-[0-9A-Za-z][0-9A-Za-z.-]{0,31})?$")

        // the node holds a stable release and a newer pre-release at most
        const val MAX = 2

        private fun of(o: JsonObject?): RayAppRelease? {
            val tag = o?.get("tag")?.takeIf { it.isJsonPrimitive }?.asString ?: return null
            if (!TAG.matches(tag)) return null
            // the flag as the tag says: a node's word does not make a pre-release stable
            val prerelease = '-' in tag
            if (o.get("prerelease")?.takeIf { it.isJsonPrimitive }?.asBoolean != prerelease) return null
            return RayAppRelease(tag, prerelease)
        }

        /** The payload's app.niko: the releases the node holds, those that are versions and flagged as their tags. */
        fun listFromPayload(payload: JsonObject): List<RayAppRelease> =
            listFromJson((payload.get("app") as? JsonObject)?.get("niko") as? JsonArray)

        fun listFromJson(a: JsonArray?): List<RayAppRelease> =
            a?.mapNotNull { of(it as? JsonObject) }?.distinctBy { it.tag }?.take(MAX).orEmpty()
    }
}

object RayAppMirror {

    // node id and tag whose files a node failed to give whole and signed (the signature refused, or the node does not
    // hold them after all): not asked of it again while the app runs
    private val failed = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /** [nodeId]'s files of [tag] did not come whole and signed: it is not the update's source for them again. */
    fun failed(nodeId: String, tag: String) {
        failed += "$nodeId $tag"
    }

    // a node whose subscription this device takes (one that refused it serves it nothing either), and its releases
    // that did not fail
    private fun held(nodes: List<RayNode>) = nodes.filter { it.subscription != null && it.refreshAt != RayNodes.STOPPED }
        .map { node -> node to node.appReleases.filter { "${node.spa.nodeId} ${it.tag}" !in failed } }

    /**
     * The first of [nodes] that holds [tag]: the release GitHub named, downloaded from the node. Null: none. The check
     * is GitHub's; a node is a source of the files, not the word on what the newest release is.
     */
    fun holding(nodes: List<RayNode>, tag: String): RayNode? =
        held(nodes).firstOrNull { (_, releases) -> releases.any { it.tag == tag } }?.first

    /**
     * The newest release of the kind asked ([preview]: pre-releases, else stable ones) newer than [current] that one of
     * [nodes] holds, and a node that holds it: for when GitHub is not reached at all. Null: none.
     */
    fun newest(nodes: List<RayNode>, preview: Boolean, current: String): Pair<RayNode, RayAppRelease>? {
        val held = held(nodes)
        val release = held.flatMap { it.second }
            .filter { it.prerelease == preview && isNewerVersion(it.tag, current) }
            .maxWithOrNull { a, b -> compareVersions(a.tag, b.tag) } ?: return null
        return held.first { (_, releases) -> releases.any { it.tag == release.tag } }.first to release
    }
}

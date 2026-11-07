package io.niko.raycontrol

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RayAppMirrorTest {

    private val spa = RaySpaParams(
        nodeId = "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf", epochLenS = 600, slotLenS = 30,
        portKey = "11".repeat(32), portBase = 30000, portRange = 2000, lenMin = 96, lenMax = 160,
    )
    private val sub = RaySubscriptionInfo(8443, "pin", "ed", "ml")

    private fun node(host: String, vararg releases: RayAppRelease, subscription: RaySubscriptionInfo? = sub, refreshAt: Long = 0) =
        RayNode(
            // a node id of its own (RayAppMirror.failed is the app's, across the tests)
            host = host, spa = spa.copy(nodeId = "%032x".format(host.hashCode())), deviceId = "dev", deviceKeyWrapped = "w",
            services = emptyList(),
            subscription = subscription, refreshAt = refreshAt, appReleases = releases.toList(),
        )

    @Test
    fun theReleasesAsTheirTagsSay() {
        val payload = JsonParser.parseString(
            """{"app": {"niko": [
                {"tag": "v1.38.0", "prerelease": false},
                {"tag": "v1.39.0-rc.1", "prerelease": true},
                {"tag": "v1.38.0", "prerelease": false},
                {"tag": "v1.40.0", "prerelease": false}
            ], "other": [{"tag": "v9.0.0", "prerelease": false}]}}"""
        ).asJsonObject
        // two at most, each once; another app's are not this one's
        assertEquals(
            listOf(RayAppRelease("v1.38.0", false), RayAppRelease("v1.39.0-rc.1", true)),
            RayAppRelease.listFromPayload(payload),
        )
        // a flag the tag does not bear out, a tag that is no version, a path: left out
        val bad = JsonParser.parseString(
            """{"app": {"niko": [
                {"tag": "v1.39.0-rc.1", "prerelease": false},
                {"tag": "v1.38.0", "prerelease": true},
                {"tag": "v1.38.0/../x", "prerelease": false},
                {"tag": "1.38.0", "prerelease": false},
                {"tag": "v1.38.0"},
                "v1.38.0"
            ]}}"""
        ).asJsonObject
        assertEquals(emptyList<RayAppRelease>(), RayAppRelease.listFromPayload(bad))
        assertEquals(emptyList<RayAppRelease>(), RayAppRelease.listFromPayload(JsonParser.parseString("{}").asJsonObject))
    }

    @Test
    fun theRecordKeepsThem() {
        val n = node("203.0.113.2", RayAppRelease("v1.38.0", false))
        assertEquals(n.appReleases, RayNode.fromJson(n.toJson()).appReleases)
        assertEquals(emptyList<RayAppRelease>(), RayNode.fromJson(node("203.0.113.2").toJson()).appReleases)
    }

    @Test
    fun theNewestOfTheKindAskedNewerThanThisOne() {
        val a = node("a", RayAppRelease("v1.38.0", false), RayAppRelease("v1.39.0-beta.1", true))
        val b = node("b", RayAppRelease("v1.38.1", false))
        val c = node("c", RayAppRelease("v1.38.1", false))
        // the newest stable, from the first node that holds it
        assertEquals(b to RayAppRelease("v1.38.1", false), RayAppMirror.newest(listOf(a, b, c), false, "1.37.0"))
        // pre-releases only when asked
        assertEquals(a to RayAppRelease("v1.39.0-beta.1", true), RayAppMirror.newest(listOf(a, b, c), true, "1.37.0"))
        // nothing newer than this one: none
        assertNull(RayAppMirror.newest(listOf(a, b, c), false, "1.38.1"))
        // a pre-release of the version this one already is: older
        assertNull(RayAppMirror.newest(listOf(node("d", RayAppRelease("v1.38.0-rc.1", true))), true, "1.38.0"))
    }

    @Test
    fun notFromANodeThatTakesThisDeviceNoLonger() {
        val stopped = node("a", RayAppRelease("v1.39.0", false), refreshAt = RayNodes.STOPPED)
        val noSub = node("b", RayAppRelease("v1.39.0", false), subscription = null)
        val held = node("c", RayAppRelease("v1.38.0", false))
        assertEquals(held to RayAppRelease("v1.38.0", false), RayAppMirror.newest(listOf(stopped, noSub, held), false, "1.37.0"))
        assertNull(RayAppMirror.newest(listOf(stopped, noSub), false, "1.37.0"))
    }

    @Test
    fun aSourceForTheReleaseGitHubNamed() {
        val a = node("source-a", RayAppRelease("v1.38.0", false))
        val b = node("source-b", RayAppRelease("v1.38.1", false))
        assertEquals(b, RayAppMirror.holding(listOf(a, b), "v1.38.1"))
        assertNull(RayAppMirror.holding(listOf(a, b), "v1.39.0"))
        // its files refused (or not held after all): not that node's release again, another's still
        val c = node("source-c", RayAppRelease("v1.38.1", false))
        RayAppMirror.failed(b.spa.nodeId, "v1.38.1")
        assertEquals(c, RayAppMirror.holding(listOf(a, b, c), "v1.38.1"))
        assertNull(RayAppMirror.newest(listOf(a, b), false, "1.38.0"))
        assertEquals(c to RayAppRelease("v1.38.1", false), RayAppMirror.newest(listOf(a, b, c), false, "1.38.0"))
    }
}

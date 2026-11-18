package io.niko.raycontrol

import io.niko.raycontrol.RayNodeCard.Fact
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** client-min-v1 on the app's side: the header's value, the version against the node's minimum, the card's line. */
class RayClientTest {

    // the same grammar as libcore/rayclient (the node's, client-min-v1 §1)
    private val grammar = Regex(
        """^[a-z][a-z0-9-]{0,15}/(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)""" +
            """(-[0-9A-Za-z-]+(\.[0-9A-Za-z-]+)*)?( \([ -'*-~]*\))?$"""
    )

    @Test
    fun theVersionAsSemver() {
        assertEquals("1.59.0", RayClient.version("1.59.0", ""))
        assertEquals("1.59.0-rc.1", RayClient.version("1.59.0-rc.1", ""))
        // a preview below its release, a later one of the day above the earlier (§9.2)
        assertEquals("1.59.0-pre.20261120.1", RayClient.version("1.58.0", "pre-1.59.0-20261120-1"))
        assertEquals("1.59.0-pre.20261120.12", RayClient.version("1.58.0", "pre-1.59.0-20261120-012"))
        // what is not semver gives its numbers
        assertEquals("1.59.0", RayClient.version("1.59.0.1", ""))
        assertEquals("1.59.0", RayClient.version("01.59.0 beta", ""))
        assertEquals("1.59.0-pre", RayClient.version("1.59.0", "nightly"))
        assertEquals("0.0.0", RayClient.version("", ""))
    }

    @Test
    fun theHeader() {
        for (abi in listOf("arm64-v8a", "armeabi-v7a", "x86", "x86_64")) {
            val header = RayClient.header("1.59.0", abi)
            assertEquals("niko/1.59.0 (android; $abi)", header)
            assertTrue(header, grammar.matches(header))
        }
        assertTrue(grammar.matches(RayClient.header("1.59.0-pre.20261120.1", "arm64-v8a")))
        // an ABI that is not one is left out, not sent
        assertEquals("niko/1.59.0 (android)", RayClient.header("1.59.0", "arm64 (v8a)"))
        assertEquals("niko/1.59.0 (android)", RayClient.header("1.59.0", null))
    }

    @Test
    fun belowTheNodesMinimum() {
        val min = RayClientMin(mapOf("niko" to "1.59.0"), from = 0, refused = false)
        assertTrue(RayClient.below(min, "1.58.0"))
        assertTrue(RayClient.below(min, "1.59.0-pre.20261120.1"))
        assertFalse(RayClient.below(min, "1.59.0"))
        assertFalse(RayClient.below(min, "1.60.0-rc.1"))
        assertFalse(RayClient.below(min, "2.0.0"))
        // a minimum for another product, or none
        assertFalse(RayClient.below(RayClientMin(mapOf("rayctl" to "9.0.0"), 0, false), "1.58.0"))
        assertFalse(RayClient.below(null, "1.58.0"))
    }

    private val spa = RaySpaParams(
        nodeId = "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf", epochLenS = 600, slotLenS = 30,
        portKey = "11".repeat(32), portBase = 30000, portRange = 2000, lenMin = 96, lenMax = 160,
    )
    private val node = RayNode(
        host = "203.0.113.2", spa = spa, deviceId = "dev-1", deviceKeyWrapped = "w",
        services = listOf(RayService(3, "reality", "reality")),
    )

    @Test
    fun theCardSaysIt() {
        val now = 1_768_000_000L
        val ahead = node.copy(client = RayClientMin(mapOf("niko" to "1.59.0"), from = now + 86_400, refused = false))
        // from a date: the card says when; the node's clock ahead of this device by a minute
        assertEquals(Fact.ClientMin("1.59.0", now + 86_400), RayNodeCard.clientMin(ahead, now, "1.58.0"))
        assertEquals(Fact.ClientMin("1.59.0", now + 86_340), RayNodeCard.clientMin(ahead.copy(clockSkew = 60), now, "1.58.0"))
        // the date past, or refused: already
        assertEquals(Fact.ClientMin("1.59.0", 0), RayNodeCard.clientMin(ahead, now + 86_400, "1.58.0"))
        val refused = node.copy(client = RayClientMin(mapOf("niko" to "1.59.0"), from = 0, refused = true))
        assertEquals(Fact.ClientMin("1.59.0", 0), RayNodeCard.clientMin(refused, now, "1.58.0"))
        // at the minimum (updated since the answer came) or none: nothing
        assertNull(RayNodeCard.clientMin(refused, now, "1.59.0"))
        assertNull(RayNodeCard.clientMin(node, now, "1.58.0"))
        // after what keeps the subscription from coming, before the rest
        val facts = RayNodeCard.facts(refused.copy(failedFetches = 2, subExpires = now + 3_600), now, null, null, "1.58.0")
        assertEquals(listOf(Fact.Failing(2), Fact.ClientMin("1.59.0", 0), Fact.Expires(now + 3_600, past = false)), facts)
    }
}

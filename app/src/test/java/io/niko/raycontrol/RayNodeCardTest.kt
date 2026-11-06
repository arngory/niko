package io.niko.raycontrol

import io.niko.raycontrol.RayNodeCard.Fact
import org.junit.Assert.assertEquals
import org.junit.Test

class RayNodeCardTest {

    private val spa = RaySpaParams(
        nodeId = "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf", epochLenS = 600, slotLenS = 30,
        portKey = "11".repeat(32), portBase = 30000, portRange = 2000, lenMin = 96, lenMax = 160,
    )
    private val node = RayNode(
        host = "203.0.113.2", spa = spa, deviceId = "dev-1", deviceKeyWrapped = "w",
        services = listOf(RayService(3, "reality", "reality")), subUpdatedAt = 1_000, subExpires = 2_000,
    )

    @Test
    fun aHealthyNodeSaysWhenItExpires() {
        assertEquals(listOf(Fact.Expires(2_000, past = false)), RayNodeCard.facts(node, now = 1_500, relay = false, dnsttKind = null))
        // past it: expired, not valid till
        assertEquals(listOf(Fact.Expires(2_000, past = true)), RayNodeCard.facts(node, now = 2_000, relay = null, dnsttKind = null))
    }

    @Test
    fun whatKeepsItFromUpdatingComesFirst() {
        val refused = node.copy(refreshAt = RayNodes.STOPPED, failedFetches = 3)
        assertEquals(Fact.Refused, RayNodeCard.facts(refused, 1_500, null, null).first())
        assertEquals(Fact.Failing(3), RayNodeCard.facts(node.copy(failedFetches = 3), 1_500, null, null).first())
    }

    @Test
    fun thePathOnlyForANodeWithRelays() {
        val relay = RayRelay("r1", "198.51.100.20", spa.copy(nodeId = "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf"))
        // without relays it is always direct: not worth a word
        assertEquals(emptyList<Fact>(), RayNodeCard.facts(node.copy(subExpires = 0), 1_500, relay = true, dnsttKind = null))
        assertEquals(listOf(Fact.Path(relay = true)),
            RayNodeCard.facts(node.copy(subExpires = 0, relays = listOf(relay)), 1_500, relay = true, dnsttKind = null))
    }

    @Test
    fun theDnsttResolverAndAClockOff() {
        val facts = RayNodeCard.facts(node.copy(subExpires = 0, clockSkew = -45), 1_500, null, dnsttKind = "doh")
        assertEquals(listOf(Fact.Dnstt("doh"), Fact.Clock(-45)), facts)
        // within SPA v2's 30 s: nothing
        assertEquals(emptyList<Fact>(), RayNodeCard.facts(node.copy(subExpires = 0, clockSkew = 30), 1_500, null, null))
    }
}

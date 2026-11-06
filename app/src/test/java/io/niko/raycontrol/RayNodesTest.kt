package io.niko.raycontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RayNodesTest {

    private val spa = RaySpaParams(
        nodeId = "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf", epochLenS = 600, slotLenS = 30,
        portKey = "11".repeat(32), portBase = 30000, portRange = 2000, lenMin = 96, lenMax = 160,
    )
    private val relay = RayRelay("r1", "198.51.100.20", spa.copy(nodeId = "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf", portBase = 20000))
    private val node = RayNode(
        host = "203.0.113.2", spa = spa, deviceId = "dev-1", deviceKeyWrapped = "w",
        services = listOf(RayService(3, "reality", "reality")), spaCarriers = listOf("udp", "tls"),
        failedFetches = 2, failedNetwork = "wifi:abc", relays = listOf(relay), subUpdatedAt = 1_700_000_000,
        subExpires = 1_700_259_200,
    )

    @Test
    fun jsonRoundTrip() {
        // every field the store keeps comes back, relays and the fetch backoff included
        assertEquals(node, RayNode.fromJson(node.toJson()))
        val bare = node.copy(relays = emptyList(), failedFetches = 0, failedNetwork = null, spaCarriers = null)
        assertEquals(bare, RayNode.fromJson(bare.toJson()))
        assertFalse(bare.toJson().has("relays"))
    }

    @Test
    fun knockAtTheNodeAndAtARelay() {
        @Suppress("UNCHECKED_CAST")
        val direct = node.knockOptions()["spa_v2"] as Map<String, Any>
        assertEquals(spa.nodeId, direct["node_id"])
        assertEquals(30000, direct["port_base"])
        assertEquals(listOf("udp", "tls"), direct["carriers"])
        assertEquals(node.keyRef, direct["device_key_ref"])
        assertNull(direct["relay"])

        // at the relay: its SPA, the same key reference, K_relay named by the node and the relay
        @Suppress("UNCHECKED_CAST")
        val via = node.knockOptions(relay)["spa_v2"] as Map<String, Any>
        assertEquals(relay.spa.nodeId, via["node_id"])
        assertEquals(20000, via["port_base"])
        assertEquals(node.keyRef, via["device_key_ref"])
        assertEquals(mapOf("exit_node_id" to spa.nodeId, "id" to "r1"), via["relay"])
        // the node's carriers are its own, not the relay's
        assertNull(via["carriers"])
    }

    @Test
    fun relayAddresses() {
        assertEquals(relay, node.relayAt("198.51.100.20"))
        assertEquals(relay, node.relayAt(" [198.51.100.20] "))
        assertNull(node.relayAt("203.0.113.2"))
        assertNull(node.relayAt(null))
        assertTrue(node.matchesHost("[203.0.113.2]"))
        assertFalse(node.matchesHost("198.51.100.20"))
    }
}

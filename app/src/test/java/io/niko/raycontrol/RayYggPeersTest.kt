package io.niko.raycontrol

import org.junit.Assert.assertEquals
import org.junit.Test

/** Which public Yggdrasil peers a profile takes after the check (RayYggPeers). */
class RayYggPeersTest {

    @Test
    fun pick() {
        val checks = listOf(
            YggPeerCheck("tls://a.example:443", up = true, latencyMs = 80),
            YggPeerCheck("quic://203.0.113.9:443", up = false, error = "timeout"),
            YggPeerCheck("tls://b.example:443", up = true, latencyMs = 20),
            YggPeerCheck("wss://c.example:443", up = true, latencyMs = 50),
            YggPeerCheck("tcp://d.example:9000", up = true, latencyMs = 200),
        )
        // the fastest that came up, at most three
        assertEquals(listOf("tls://b.example:443", "wss://c.example:443", "tls://a.example:443"), RayYggPeers.pick(checks))
        // none up: none taken (the public peers would not help on this network)
        assertEquals(emptyList<String>(), RayYggPeers.pick(checks.map { it.copy(up = false) }))
    }

    @Test
    fun label() {
        assertEquals("tls://ygg.example.net:443", RayYggPeers.label("tls://ygg.example.net:443?key=" + "ab".repeat(32)))
        assertEquals("quic://[2001:db8::1]:8443", RayYggPeers.label("quic://[2001:db8::1]:8443"))
    }
}

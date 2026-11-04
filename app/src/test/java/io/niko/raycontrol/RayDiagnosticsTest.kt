package io.niko.raycontrol

import org.junit.Assert.assertEquals
import org.junit.Test

/** The debug export leaves the node's address out of probe errors (RayDiagnostics). */
class RayDiagnosticsTest {

    @Test
    fun redact() {
        assertEquals("dial tcp <ip>:443: i/o timeout", RayDiagnostics.redact("dial tcp 198.51.100.7:443: i/o timeout", null))
        assertEquals("dial tcp <ip>:443: refused", RayDiagnostics.redact("dial tcp [2001:db8::7]:443: refused", null))
        assertEquals("lookup <node>: no such host", RayDiagnostics.redact("lookup node.example.org: no such host", "node.example.org"))
        assertEquals("connection test failed", RayDiagnostics.redact("connection test failed", "node.example.org"))
    }

    @Test
    fun redactErrors() {
        val state = com.google.gson.JsonParser.parseString(
            """{"net1":{"3#a":{"tcp_ok":false,"tcp_error":"dial tcp 198.51.100.7:443: refused"},
               "_direct":{"spa":"udp","mtu":[{"size":576,"error":"Post \"https://node.example.org:8443/probe/v1/udp\": dial tcp 198.51.100.7:8443: timeout"}]},
               "_ygg_via":{"peers":["quic://203.0.113.9:443"]}}}"""
        )
        RayDiagnostics.redactErrors(state, "node.example.org")
        val text = state.toString()
        assertEquals(false, text.contains("198.51.100.7") || text.contains("node.example.org"))
        // a public peer is not an error: kept
        assertEquals(true, text.contains("203.0.113.9"))
    }
}

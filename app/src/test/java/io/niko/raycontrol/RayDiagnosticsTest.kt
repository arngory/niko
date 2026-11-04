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
}

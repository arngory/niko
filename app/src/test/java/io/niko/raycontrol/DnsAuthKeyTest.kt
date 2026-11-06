package io.niko.raycontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class DnsAuthKeyTest {

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** dnsauth-v1 §2's vector (raycontrol TestDerivedKeyVectors; the core's TestNekoDNSAuthVector takes the same) */
    @Test
    fun theSpecsVector() {
        val key = DnsAuthKey.derive(ByteArray(32) { it.toByte() }, "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf", 3)
        assertEquals("0b485b425e8b19e0ec7c0362d55fdc34338147f984b87ee005455c8f5fc68b1c", hex(key))
        // and the core's own second one
        assertEquals(
            "2f9bd5e6fc2ef3f56206998eee87f2d4b798a66c3c846ef34eff2f2647599bd5",
            hex(DnsAuthKey.derive(ByteArray(32) { 0x11 }, "33".repeat(16), 3)),
        )
    }

    @Test
    fun whatItRefuses() {
        for ((name, call) in listOf<Pair<String, () -> Unit>>(
            "a short key" to { DnsAuthKey.derive(ByteArray(31), "33".repeat(16), 3) },
            "a short node id" to { DnsAuthKey.derive(ByteArray(32), "33".repeat(15), 3) },
            "a node id not hex" to { DnsAuthKey.derive(ByteArray(32), "zz".repeat(16), 3) },
            "a service id past 16 bits" to { DnsAuthKey.derive(ByteArray(32), "33".repeat(16), 0x10000) },
        )) {
            try {
                call()
                fail(name)
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}

package io.niko.fmt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PortKnockingTest {

    @Test
    fun parsesSequence() {
        val steps = parseKnockSequence("7000, 8000/udp\n9000/TCP 1234/udp:68656C6C6F")
        assertEquals(
            listOf(
                KnockStep(7000, false, ""),
                KnockStep(8000, true, ""),
                KnockStep(9000, false, ""),
                KnockStep(1234, true, "68656c6c6f"),
            ), steps
        )
        assertTrue(parseKnockSequence("  ").isEmpty())
        val key = "AB".repeat(32)
        assertEquals(listOf(KnockStep(62201, true, key.lowercase(), spa = true)), parseKnockSequence("62201/spa:$key"))
        assertEquals(
            listOf(KnockStep(62201, true, key.lowercase(), spa = true, spaMasked = true)),
            parseKnockSequence("62201/SPA1M:$key")
        )
    }

    @Test
    fun rejectsInvalidSteps() {
        for (sequence in listOf("0", "70000", "abc", "1/icmp", "1:00", "1/udp:0", "1/udp:zz", "1/spa", "1/spa:abcd", "1/spa:" + "z".repeat(64))) {
            try {
                parseKnockSequence(sequence)
                throw AssertionError("accepted: $sequence")
            } catch (_: IllegalArgumentException) {
            }
        }
    }
}

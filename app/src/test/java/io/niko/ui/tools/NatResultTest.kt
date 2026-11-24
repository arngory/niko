package io.niko.ui.tools

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The core's STUN text read into the rows of Тип NAT. */
class NatResultTest {

    @Test
    fun fullAnswer() {
        val r = NatResult.parse(
            """
            NAT Type: Port restricted NAT
            External IP Family: 1
            External IP: 203.0.113.7
            External Port: 40112
            Mapping Behavior: EndpointIndependent
            Filtering Behavior: AddressAndPortDependent
            Normal NAT Type: Port Restricted cone NAT
            """.trimIndent(),
        )
        // the behaviour test's name wins over the older test's
        assertEquals("Port Restricted cone NAT", r.type)
        assertEquals(NatBehavior.ENDPOINT, r.mapping)
        assertEquals(NatBehavior.ADDRESS_AND_PORT, r.filtering)
        assertEquals("203.0.113.7:40112", r.address)
        assertTrue(r.notes.isEmpty())
        assertFalse(r.empty)
    }

    @Test
    fun undefinedBehaviourKeepsTheOlderName() {
        val r = NatResult.parse(
            "NAT Type: Symmetric NAT\nMapping Behavior: AddressDependent\nFiltering Behavior: AddressDependent\nNormal NAT Type: Undefined",
        )
        assertEquals("Symmetric NAT", r.type)
        assertEquals(NatBehavior.ADDRESS, r.mapping)
        assertNull(r.address)
    }

    @Test
    fun errorsAndUnknownLinesAreNotes() {
        val r = NatResult.parse(
            "Discover Error: i/o timeout\nFake fullcone (no endpoint IP change) detected!!\nBehaviorTest Error: no response\nMapping Behavior: Unknown",
        )
        assertEquals(
            listOf("Discover Error: i/o timeout", "Fake fullcone (no endpoint IP change) detected!!", "BehaviorTest Error: no response"),
            r.notes,
        )
        // a behaviour this version has no word for is shown as the core wrote it
        assertEquals(NatBehavior.OTHER, r.mapping)
        assertEquals("Unknown", r.mappingText)
    }

    @Test
    fun ipv6AddressInBrackets() {
        assertEquals("[2001:db8::7]:40112", NatResult.parse("External IP: 2001:db8::7\nExternal Port: 40112").address)
        assertEquals("203.0.113.7", NatResult.parse("External IP: 203.0.113.7").address)
    }

    @Test
    fun nothingLearned() {
        assertTrue(NatResult.parse("").empty)
        assertTrue(NatResult.parse("Discover Error: timeout").empty)
    }
}

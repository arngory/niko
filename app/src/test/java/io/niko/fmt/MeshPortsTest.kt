package io.niko.fmt

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MeshPortsTest {

    @Test
    fun parsesPortsAndRanges() {
        assertEquals(MeshPorts(listOf(22, 8080), listOf("8000:8010")), MeshPorts.parse("22, 8080 8000-8010;22"))
        assertEquals(MeshPorts(emptyList(), listOf("1:65535")), MeshPorts.parse("1:65535"))
    }

    @Test
    fun nothingValidMeansEveryPort() {
        // null: the mesh reaches every port but Niko's own
        for (text in listOf(null, "", " ", "0", "70000", "abc", "10-5", "1-70000", "-")) {
            assertNull(text, MeshPorts.parse(text))
        }
    }
}

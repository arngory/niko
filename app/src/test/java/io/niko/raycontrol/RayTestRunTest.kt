package io.niko.raycontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class RayTestRunTest {

    @Test
    fun runIds() {
        // UTC, whatever the device's zone
        assertEquals("r-20261105-1712", RayTestRun.defaultId(Instant.parse("2026-11-05T17:12:59Z")))
        assertEquals("2026-11-05T17:12:59.123Z", RayTestRun.utc(Instant.parse("2026-11-05T17:12:59.123456Z")))
        assertTrue(RayTestRun.validId("r-20261105-1"))
        assertTrue(RayTestRun.validId("vps_check.2"))
        // it goes into a file name: no separators, no spaces
        for (bad in listOf("", "a/b", "../x", "r 1", "x".repeat(65))) assertFalse(bad, RayTestRun.validId(bad))
    }
}

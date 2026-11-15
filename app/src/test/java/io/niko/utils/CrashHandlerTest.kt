package io.niko.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrashHandlerTest {

    // a crash restarts the app for its report, but not again within 30 s of the last such restart: a report that
    // crashes, or a start that does, would loop. A clock set back (the last restart "in the future") restarts
    @Test
    fun noRestartLoop() {
        val now = 1_800_000_000_000L
        assertTrue(CrashHandler.rebirthAllowed(now, 0L))
        assertTrue(CrashHandler.rebirthAllowed(now, now - 30_000L))
        assertFalse(CrashHandler.rebirthAllowed(now, now - 29_999L))
        assertFalse(CrashHandler.rebirthAllowed(now, now))
        assertTrue(CrashHandler.rebirthAllowed(now, now + 5_000L))
    }
}

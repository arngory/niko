package io.niko.ui.log

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LogStateTest {

    @Test
    fun levels() {
        // the core's lines (sing-box) and libcore's (Logs, through Go's log with its date)
        assertEquals(LogTone.Danger, LogTone.of("+0300 2026-11-20 10:00:00 ERROR[0012] outbound/naive: dial: refused"))
        assertEquals(LogTone.Danger, LogTone.of("+0300 2026-11-20 10:00:00 FATAL[0000] start service"))
        assertEquals(LogTone.Danger, LogTone.of("2026/11/20 10:00:00 [Error] [BaseService] start failed"))
        assertEquals(LogTone.Quiet, LogTone.of("+0300 2026-11-20 10:00:00 DEBUG[0003] router: match"))
        assertEquals(LogTone.Quiet, LogTone.of("2026/11/20 10:00:00 [Debug] [GroupUpdater] fetched"))
        assertEquals(LogTone.Plain, LogTone.of("+0300 2026-11-20 10:00:00 INFO[0001] inbound/tun: started"))
        assertEquals(LogTone.Plain, LogTone.of("+0300 2026-11-20 10:00:00 WARN[0001] dns: slow"))
        assertEquals(LogTone.Plain, LogTone.of("2026/11/20 10:00:00 [Warning] [Watchdog] no answer"))
        assertEquals(LogTone.Plain, LogTone.of("\tat io.niko.bg.BaseService.start(BaseService.kt:42)"))
    }

    @Test
    fun clock() {
        val core = LogLine.of("+0300 2026-11-20 10:00:07 ERROR[0012] outbound/naive: dial: refused")
        assertEquals("10:00:07", core.time)
        assertEquals("ERROR[0012] outbound/naive: dial: refused", core.text)
        assertEquals(LogTone.Danger, core.tone)
        val go = LogLine.of("2026/11/20 10:00:08 [Info] [Watchdog] up")
        assertEquals("10:00:08", go.time)
        assertEquals("[Info] [Watchdog] up", go.text)
        // a line of a stack: as it is, no clock
        val stack = LogLine.of("\tat io.niko.bg.BaseService.start(BaseService.kt:42)")
        assertNull(stack.time)
        assertEquals("\tat io.niko.bg.BaseService.start(BaseService.kt:42)", stack.text)
    }

    @Test
    fun errorsOnly() {
        val state = LogState.read(
            """
            +0300 2026-11-20 10:00:00 INFO[0001] inbound/tun: started

            +0300 2026-11-20 10:00:01 ERROR[0002] outbound/hysteria2: dial timeout
            2026/11/20 10:00:02 [Error] [BaseService] start failed
            """.trimIndent().toByteArray(),
            crashes = 1,
        )
        assertEquals(3, state.lines.size) // the blank line goes
        assertEquals(3, state.shown(LogFilter.All).size)
        assertEquals(listOf("10:00:01", "10:00:02"), state.shown(LogFilter.Errors).map { it.time })
        assertEquals(1, state.crashes)
    }
}

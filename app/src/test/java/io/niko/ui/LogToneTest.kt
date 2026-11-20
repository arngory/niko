package io.niko.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class LogToneTest {

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
}

package io.niko.raycontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The buckets of the anonymous summary (RAYCONTROL.md §14.3, niko-r14-reply.md §2). */
class RayReportTest {

    private fun score(tcpOk: Boolean = true, kbps: Long = 8000, stalledAt: Long = -1, udpOk: Boolean = true, loss: Double = 0.0) =
        ChannelScore(0, tcpOk = tcpOk, kbps = kbps, stalledAt = stalledAt, udpOk = udpOk, udpLossPct = loss, udpMeasured = true)

    @Test
    fun udpMeasured() {
        // a node service without UDP echo: not measured; one whose echo got no answer: measured, blocked
        val tcpOnly = ChannelScore.fromNodeProbe(0, """{"down":{"ok":true,"kbps":8000}}""")!!
        val blocked = ChannelScore.fromNodeProbe(0, """{"down":{"ok":true,"kbps":8000},"echo":{"ok":false,"sent":50,"lost":50}}""")!!
        assertEquals(false, tcpOnly.udpMeasured)
        assertEquals(true, blocked.udpMeasured)
        assertEquals("none", RayReport.udpBucket(blocked))
        // the stored form keeps it; records from before keep what they said
        assertEquals(false, ChannelScore.fromJson(tcpOnly.toJson()).udpMeasured)
        assertEquals(true, ChannelScore.fromJson(blocked.toJson()).udpMeasured)
        val old = blocked.toJson().apply { remove("udp_measured") }
        assertEquals(true, ChannelScore.fromJson(old).udpMeasured)
        val oldTcpOnly = tcpOnly.toJson().apply { remove("udp_measured") }
        assertEquals(false, ChannelScore.fromJson(oldTcpOnly).udpMeasured)
    }

    @Test
    fun result() {
        assertEquals("works", RayReport.result(score()))
        assertEquals("fails", RayReport.result(score(tcpOk = false)))
        assertEquals("degraded", RayReport.result(score(stalledAt = 16384)))
        assertEquals("degraded", RayReport.result(score(kbps = 500)))
        assertEquals("degraded", RayReport.result(score(loss = 30.0)))
        // no UDP at all is not a degraded TCP profile
        assertEquals("works", RayReport.result(score(udpOk = false)))
    }

    @Test
    fun stall() {
        assertNull(RayReport.stallKib(score()))
        assertEquals(16, RayReport.stallKib(score(stalledAt = 17 * 1024L)))
        assertEquals(8, RayReport.stallKib(score(stalledAt = 3 * 1024L)))
        assertEquals(128, RayReport.stallKib(score(stalledAt = 4L shl 20)))
    }

    @Test
    fun buckets() {
        assertEquals("none", RayReport.udpBucket(score(udpOk = false)))
        assertEquals("0-1", RayReport.udpBucket(score(loss = 0.5)))
        assertEquals("5-20", RayReport.udpBucket(score(loss = 12.0)))
        assertEquals("50-100", RayReport.udpBucket(score(loss = 80.0)))
        assertEquals("<1", RayReport.mbpsBucket(score(kbps = 900)))
        assertEquals("5-20", RayReport.mbpsBucket(score(kbps = 8000)))
        assertEquals("100+", RayReport.mbpsBucket(score(kbps = 250_000)))
    }
}

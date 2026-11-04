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
    fun hintsOrder() {
        val hints = RayHints(0, order = listOf(5, 3), avoid = listOf(4), udpBlocked = true)
        val keys = listOf("1#a", "3#b", "4#c", "5#d", "6#e")
        // order first, then the unknown (TCP before UDP where UDP is blocked), avoided last
        assertEquals(listOf("5#d", "3#b", "6#e", "1#a", "4#c"), hints.sort(keys) { it == "1#a" })
        // the stored form
        val stored = RayHints.fromJson(hints.toJson(), 99)
        assertEquals(hints, stored)
        // as the node sends it: no at, fields left out
        val sent = RayHints.fromJson(com.google.gson.JsonParser.parseString("""{"order":[3],"devices":12,"days":7}""").asJsonObject, 42)
        assertEquals(RayHints(42, order = listOf(3), devices = 12, days = 7), sent)
    }

    @Test
    fun mtuBucket() {
        assertEquals("<1200", RayReport.mtuBucket(576))
        assertEquals("1200-1350", RayReport.mtuBucket(1200))
        assertEquals("1200-1350", RayReport.mtuBucket(1280))
        assertEquals("1350-1472", RayReport.mtuBucket(1350))
        assertEquals("1350-1472", RayReport.mtuBucket(1452))
        assertEquals("1472+", RayReport.mtuBucket(1472))
        // a hint's MTU under QUIC's minimum puts the UDP profiles after the rest, as a blocked UDP does
        val hints = RayHints(0, udpMtu = "<1200")
        assertEquals(listOf("2#b", "1#a"), hints.sort(listOf("1#a", "2#b")) { it == "1#a" })
        val probe = RayProbeInfo.fromJson(com.google.gson.JsonParser.parseString(
            """{"v":1,"udp":true,"report":["udp_mtu"],"tunnel_svc":[3]}""").asJsonObject)
        assertEquals(listOf("udp_mtu"), probe.report)
        assertEquals(probe, RayProbeInfo.fromJson(probe.toJson()))
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

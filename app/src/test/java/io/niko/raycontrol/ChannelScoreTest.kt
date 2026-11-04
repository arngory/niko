package io.niko.raycontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelScoreTest {

    @Test
    fun fromProbe() {
        val s = ChannelScore.fromProbe(
            100,
            """{"tcp":{"ok":true,"connect_ms":40,"tls_ms":90,"first_byte_ms":180,"bytes":262144,"duration_ms":400,
               "kbps":5242,"stalled_at":-1},
               "udp":{"ok":true,"sent":10,"received":9,"reordered":0,"loss_pct":10,"rtt_min_ms":50,"rtt_avg_ms":61.5,
               "rtt_max_ms":80,"jitter_ms":4}}""",
        )
        assertTrue(s.tcpOk)
        assertEquals(180L, s.firstByteMs)
        assertEquals(5242L, s.kbps)
        assertEquals(-1L, s.stalledAt)
        assertTrue(s.udpOk)
        assertEquals(10.0, s.udpLossPct, 0.0)
        assertEquals(61.5, s.udpRttMs, 0.0)
        assertEquals(s, ChannelScore.fromJson(s.toJson()))
    }

    @Test
    fun stalledAndNoUdp() {
        // a protocol without UDP: the dial fails, no "udp.ok"
        val s = ChannelScore.fromProbe(
            100,
            """{"tcp":{"ok":false,"error":"stalled","first_byte_ms":200,"bytes":16384,"stalled_at":16384},
               "udp":{"ok":false,"error":"not supported"}}""",
        )
        assertFalse(s.tcpOk)
        assertEquals(16384L, s.stalledAt)
        assertEquals("stalled", s.tcpError)
        assertFalse(s.udpOk)
        assertEquals("not supported", s.udpError)
    }

    @Test
    fun rank() {
        val fast = ChannelScore(0, tcpOk = true, firstByteMs = 150, kbps = 20_000, udpOk = true)
        val slow = ChannelScore(0, tcpOk = true, firstByteMs = 150, kbps = 500, udpOk = true)
        val lossy = fast.copy(udpLossPct = 40.0)
        val noUdp = fast.copy(udpOk = false)
        val waits = fast.copy(firstByteMs = 3000)
        val broken = ChannelScore(0, tcpOk = false, stalledAt = 16384)
        // any working profile above any broken one, even a slow DNS tunnel
        val dnsTunnel = ChannelScore(0, tcpOk = true, firstByteMs = 2500, kbps = 30)
        assertTrue(dnsTunnel.rank() > broken.rank())
        assertTrue(fast.rank() > slow.rank())
        assertTrue(fast.rank() > lossy.rank())
        assertTrue(lossy.rank() > noUdp.rank())
        assertTrue(fast.rank() > waits.rank())
        assertTrue(slow.rank() > dnsTunnel.rank())
    }

    @Test
    fun fromNodeProbe() {
        val s = ChannelScore.fromNodeProbe(
            100,
            """{"down":{"ok":true,"first_byte_ms":120,"bytes":262145,"kbps":9000,"stalled_at":-1,
               "server":{"bytes":262144,"rtt_us":41000}},
               "echo":{"ok":true,"size":1200,"sent":20,"received":19,"lost":1,"lost_up":1,"lost_down":0,"rtt_avg_ms":44.5}}""",
        )!!
        assertTrue(s.tcpOk)
        assertEquals(9000L, s.kbps)
        assertTrue(s.udpOk)
        assertEquals(5.0, s.udpLossPct, 0.001)
        assertEquals(44.5, s.udpRttMs, 0.0)
        // the node's one probe slot was busy: no result
        assertEquals(null, ChannelScore.fromNodeProbe(100, """{"down":{"ok":false,"status":429,"error":"429 Too Many Requests"}}"""))
        // naive carries no UDP: TCP decides
        val naive = ChannelScore.fromNodeProbe(100, """{"down":{"ok":true,"kbps":3000,"stalled_at":-1},"echo":{"ok":false,"sent":20,"lost":20,"error":"no echo"}}""")!!
        assertTrue(naive.tcpOk)
        assertFalse(naive.udpOk)
    }

    @Test
    fun brutalRates() {
        val r = BrutalRates(100, 8500, 0)
        assertEquals(r, BrutalRates.fromJson(r.toJson()))
        assertTrue(r.any)
        assertFalse(BrutalRates(100, 0, 0).any)
        assertEquals(8, RayBrutal.mbps(8500))
        assertEquals(1, RayBrutal.mbps(400))
    }
}

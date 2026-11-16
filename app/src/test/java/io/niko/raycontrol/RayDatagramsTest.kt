package io.niko.raycontrol

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RayDatagramsTest {

    private fun echo(json: String) = JsonParser.parseString(json).asJsonObject

    @Test
    fun verdictByEchoesBack() {
        val pass = RayDatagrams.ECHO_PASS
        assertTrue(RayDatagrams.verdict(echo("""{"ok":true,"sent":30,"received":$pass,"lost":${30 - pass}}""")) is RayDatagrams.Verdict.Passed)
        assertTrue(RayDatagrams.verdict(echo("""{"ok":true,"sent":30,"received":${pass - 1}}""")) is RayDatagrams.Verdict.Failed)
        // a path that froze after the first few echoes (§10.4): failed, not passed on its first answers
        assertTrue(RayDatagrams.verdict(echo("""{"ok":true,"sent":30,"received":12}""")) is RayDatagrams.Verdict.Failed)
        assertTrue(RayDatagrams.verdict(echo("""{"ok":false,"sent":30,"received":0,"error":"no echo"}""")) is RayDatagrams.Verdict.Failed)
    }

    @Test
    fun verdictWithoutAMeasure() {
        // the node's one probe slot busy: again, after what it asks
        val busy = RayDatagrams.verdict(echo("""{"ok":false,"status":429,"retry_after":120,"sent":0}"""))
        assertTrue(busy is RayDatagrams.Verdict.Again)
        assertEquals(120, (busy as RayDatagrams.Verdict.Again).retryAfter)
        // no echo went (the probe's session over TCP did not start, the request did not open): not the path's verdict
        assertTrue(RayDatagrams.verdict(echo("""{"ok":false,"sent":0,"error":"session"}""")) is RayDatagrams.Verdict.Again)
        assertTrue(RayDatagrams.verdict(null) is RayDatagrams.Verdict.Again)
    }

    @Test
    fun probeFitsAQuicDatagramAndPassesTheFreeze() {
        // the probe name's address in front (type, length, name, port) and the echo stay within a QUIC datagram over
        // IPv6 (1194 bytes), and the echoes carry past the 15-20 KB after which some filters freeze a flow
        val address = 1 + 1 + "probe.raycontrol.invalid".length + 2
        assertTrue(address + RayDatagrams.ECHO_SIZE <= 1194)
        assertTrue(RayDatagrams.ECHO_PASS * RayDatagrams.ECHO_SIZE > 20 * 1024)
        assertTrue(RayDatagrams.ECHO_PASS <= RayDatagrams.ECHO_COUNT)
    }

    private val wifi = ChannelNetwork("aaaa", mobile = false, kind = "wifi")
    private val cellular = ChannelNetwork("bbbb", mobile = true)

    @Test
    fun rememberedWhileFresh() {
        val now = 1_800_000_000L
        val all = JsonObject()
        RayDatagrams.remember(all, wifi, "node#4", true, now)
        RayDatagrams.remember(all, cellular, "node#4", true, now)
        assertEquals(true, RayDatagrams.remembered(all, wifi, "node#4", now + RayDatagrams.PASS_WIFI - 1))
        assertNull(RayDatagrams.remembered(all, wifi, "node#4", now + RayDatagrams.PASS_WIFI))
        // a mobile network's pass holds a day, as its channel results do
        assertEquals(true, RayDatagrams.remembered(all, cellular, "node#4", now + RayDatagrams.PASS_MOBILE - 1))
        assertNull(RayDatagrams.remembered(all, cellular, "node#4", now + RayDatagrams.PASS_MOBILE))
        // another node, another network: nothing
        assertNull(RayDatagrams.remembered(all, wifi, "other#4", now))
        assertNull(RayDatagrams.remembered(all, ChannelNetwork("cccc", mobile = false), "node#4", now))
        // a clock set back: not taken
        assertNull(RayDatagrams.remembered(all, wifi, "node#4", now - 60))
    }

    @Test
    fun failureHoldsAFewHours() {
        val now = 1_800_000_000L
        val all = RayDatagrams.remember(JsonObject(), wifi, "node#4", false, now)
        assertEquals(false, RayDatagrams.remembered(all, wifi, "node#4", now + RayDatagrams.FAILED_HOLD - 1))
        assertNull(RayDatagrams.remembered(all, wifi, "node#4", now + RayDatagrams.FAILED_HOLD))
        // a later pass replaces it
        RayDatagrams.remember(all, wifi, "node#4", true, now + 10)
        assertEquals(true, RayDatagrams.remembered(all, wifi, "node#4", now + 20))
    }

    @Test
    fun keepsTheNetworksHeardOfLast() {
        val all = JsonObject()
        for (i in 0 until RayDatagrams.MAX_NETWORKS + 3) {
            RayDatagrams.remember(all, ChannelNetwork("net$i", mobile = false), "node#4", true, 1_000L + i)
        }
        assertEquals(RayDatagrams.MAX_NETWORKS, all.size())
        assertFalse(all.has("net0") || all.has("net1") || all.has("net2"))
        assertTrue(all.has("net${RayDatagrams.MAX_NETWORKS + 2}"))
        // broken entries do not break it
        all.addProperty("junk", 1)
        RayDatagrams.remember(all, wifi, "node#4", true, 5_000L)
        assertTrue(all.has(wifi.key))
        assertNull(RayDatagrams.remembered(all, ChannelNetwork("junk", mobile = false), "node#4", 5_000L))
    }
}

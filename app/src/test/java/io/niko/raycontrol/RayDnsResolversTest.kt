package io.niko.raycontrol

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RayDnsResolversTest {

    @Test
    fun hintsPerTunnelKind() {
        val h = RayHints.fromJson(
            JsonParser.parseString("""{"order":[7],"dns":{"dnstt":"doh","masterdns":"udp","x":1},"devices":12,"days":3}""").asJsonObject, 1
        )!!
        assertEquals(mapOf("dnstt" to "doh", "masterdns" to "udp", "x" to "1"), h.dns)
        assertEquals(h.dns, RayHints.fromJson(h.toJson(), 1)!!.dns)
        assertEquals("masterdns", RayDnsResolvers.tunnelKind("masterdnsvpn"))
        assertEquals("dnstt", RayDnsResolvers.tunnelKind("dnstt"))
    }

    @Test
    fun masterdnsTakesYandexOnlyAsTheFallback() {
        val c = listOf(
            RayDnsResolvers.Candidate("192.168.1.1", "net"), RayDnsResolvers.Candidate("tls://dns.example", "dot"),
            RayDnsResolvers.Candidate("195.208.5.1", "udp"), RayDnsResolvers.Candidate("77.88.8.8", "udp"),
            RayDnsResolvers.Candidate("62.76.76.62", "udp"), RayDnsResolvers.Candidate("77.88.8.1", "udp"),
            RayDnsResolvers.Candidate("https://common.dot.dns.yandex.net/dns-query", "doh"),
        )
        // UDP only, the network's first, Yandex's out
        assertEquals(listOf("192.168.1.1", "195.208.5.1", "62.76.76.62"),
            RayDnsResolvers.masterdnsResolvers(c, fallback = false).map { it.resolver })
        // the tunnel failed here without them: Yandex's join, last
        assertEquals(listOf("192.168.1.1", "195.208.5.1", "62.76.76.62", "77.88.8.8", "77.88.8.1"),
            RayDnsResolvers.masterdnsResolvers(c, fallback = true).map { it.resolver })
        // no others (another country, no network resolver known): Yandex's, as before
        assertEquals(listOf("77.88.8.8", "77.88.8.1"),
            RayDnsResolvers.masterdnsResolvers(listOf(RayDnsResolvers.Candidate("77.88.8.8", "udp")), fallback = false).map { it.resolver })
    }

    @Test
    fun scoreKeepsTheResolverKindItWentThrough() {
        assertEquals("doh", ChannelScore.fromJson(ChannelScore(1, tcpOk = true, dnsKind = "doh").toJson()).dnsKind)
        // not a DNS tunnel left to the client, and records before 1.33.0: no kind
        assertNull(ChannelScore.fromJson(ChannelScore(1, tcpOk = true).toJson()).dnsKind)
        assertNull(ChannelScore.fromJson(JsonParser.parseString("""{"at":1,"tcp_ok":true}""").asJsonObject).dnsKind)
    }
}

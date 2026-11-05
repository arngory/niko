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
    fun scoreKeepsTheResolverKindItWentThrough() {
        assertEquals("doh", ChannelScore.fromJson(ChannelScore(1, tcpOk = true, dnsKind = "doh").toJson()).dnsKind)
        // not a DNS tunnel left to the client, and records before 1.33.0: no kind
        assertNull(ChannelScore.fromJson(ChannelScore(1, tcpOk = true).toJson()).dnsKind)
        assertNull(ChannelScore.fromJson(JsonParser.parseString("""{"at":1,"tcp_ok":true}""").asJsonObject).dnsKind)
    }
}

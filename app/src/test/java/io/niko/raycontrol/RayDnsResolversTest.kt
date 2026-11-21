package io.niko.raycontrol

import com.google.gson.JsonParser
import io.niko.fmt.DnsTunnel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
    fun eachUdpResolverAlsoOnTcp() {
        val c = listOf(
            RayDnsResolvers.Candidate("192.168.1.1", "net"), RayDnsResolvers.Candidate("195.208.5.1", "udp"),
            RayDnsResolvers.Candidate("[2001:db8::53]", "net"), RayDnsResolvers.Candidate("tls://dns.example", "dot"),
        )
        assertEquals(
            listOf("192.168.1.1", "tcp://192.168.1.1", "195.208.5.1", "tcp://195.208.5.1", "[2001:db8::53]",
                "tcp://[2001:db8::53]", "tls://dns.example"),
            RayDnsResolvers.withTcp(c).map { it.resolver },
        )
        assertEquals(RayDnsResolvers.TCP, RayDnsResolvers.withTcp(c)[1].kind)
        // masterdns takes UDP only: the TCP ones are not its
        assertEquals(listOf("192.168.1.1", "195.208.5.1", "2001:db8::53"),
            RayDnsResolvers.masterdnsResolvers(RayDnsResolvers.withTcp(c), fallback = false).map { it.resolver })
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
        // an IPv6 resolver of the network goes bare (MasterDnsVPN takes [..] only with a port)
        assertEquals(listOf("2001:db8::53", "195.208.5.1"), RayDnsResolvers.masterdnsResolvers(
            listOf(RayDnsResolvers.Candidate("[2001:db8::53]", "net"), RayDnsResolvers.Candidate("195.208.5.1", "udp")),
            fallback = false,
        ).map { it.resolver })
        // no others (another country, no network resolver known): Yandex's, as before
        assertEquals(listOf("77.88.8.8", "77.88.8.1"),
            RayDnsResolvers.masterdnsResolvers(listOf(RayDnsResolvers.Candidate("77.88.8.8", "udp")), fallback = false).map { it.resolver })
    }

    @Test
    fun scoreKeepsTheResolverKindItWentThrough() {
        assertEquals("doh", ChannelScore.fromJson(ChannelScore(1, tcpOk = true, dnsKind = "doh").toJson()).dnsKind)
        // and the number of the zone (dnszones-v1), none for a record before it
        assertEquals(2, ChannelScore.fromJson(ChannelScore(1, tcpOk = true, dnsZone = 2).toJson()).dnsZone)
        assertEquals(null, ChannelScore.fromJson(ChannelScore(1, tcpOk = true).toJson()).dnsZone)
        // not a DNS tunnel left to the client, and records before 1.33.0: no kind
        assertNull(ChannelScore.fromJson(ChannelScore(1, tcpOk = true).toJson()).dnsKind)
        assertNull(ChannelScore.fromJson(JsonParser.parseString("""{"at":1,"tcp_ok":true}""").asJsonObject).dnsKind)
    }

    /**
     * The review: the config replaced a resolver left to the client before it asked for the zone, so a dnstt tunnel
     * never took the spare its pick passed in. The zone is decided on the tunnel as the profile has it.
     */
    @Test
    fun theZoneOfAConfigDecidedBeforeTheResolverIsReplaced() {
        val auto = DnsTunnel(DnsTunnel.DNSTT, "auto", "t.example.com", "key", spare = "s1.example.com,s2.example.com")
        var asked = 0
        val picked = { asked++; "https://dns.example/dns-query" }
        val running = RayDnsResolvers.configTunnel(auto, "s1.example.com", picked)
        assertEquals("s1.example.com", running.zones.first())
        assertEquals("https://dns.example/dns-query", running.resolver)
        assertEquals(1, asked)
        // no pick on this network, or a pick of a zone the link no longer gives: the main one
        assertEquals("t.example.com", RayDnsResolvers.configTunnel(auto, "", picked).zones.first())
        assertEquals("t.example.com", RayDnsResolvers.configTunnel(auto, "gone.example.com", picked).zones.first())
        // a resolver set by hand: the main zone (the pick was measured through the network's candidates), its own resolver
        val byHand = auto.copy(resolver = "udp://9.9.9.9:53")
        assertFalse(RayDnsResolvers.followsPick(byHand))
        val own = RayDnsResolvers.configTunnel(byHand, "s1.example.com") { error("not asked") }
        assertEquals("t.example.com", own.zones.first())
        assertEquals("udp://9.9.9.9:53", own.resolver)
        // masterdns follows the zone its failures moved to, whatever its resolvers
        val masterdns = DnsTunnel(DnsTunnel.MASTERDNSVPN, "1.1.1.1:53", "m.example.com", "k", 3, spare = "n.example.com")
        assertTrue(RayDnsResolvers.followsPick(masterdns))
        assertEquals("n.example.com", RayDnsResolvers.configTunnel(masterdns, "n.example.com") { error("not asked") }.zones.first())
        // no spare zones: nothing to follow
        assertFalse(RayDnsResolvers.followsPick(auto.copy(spare = "")))
        assertNull(RayDnsResolvers.zoneOf(auto.copy(spare = ""), "s1.example.com"))
    }

    @Test
    fun slipstreamLeavesOutResolversWithTooLittleEdns() {
        val list = listOf(
            RayDnsResolvers.Candidate("192.168.1.1", "net"), RayDnsResolvers.Candidate("10.0.0.0/30", "udp"),
            RayDnsResolvers.Candidate("195.208.5.1", "udp"), RayDnsResolvers.Candidate("2001:db8::53", "net"),
            RayDnsResolvers.Candidate("62.76.76.62", "udp"), RayDnsResolvers.Candidate("77.88.8.8", "udp"),
            RayDnsResolvers.Candidate("77.88.8.1", "udp"),
        )
        // as before with none measured: addresses only, four at most
        assertEquals(listOf("192.168.1.1", "195.208.5.1", "2001:db8::53", "62.76.76.62"),
            RayDnsResolvers.slipResolvers(list, emptySet()))
        // the network's resolver and one public one pass too little: the next ones take their places
        assertEquals(listOf("195.208.5.1", "62.76.76.62", "77.88.8.8", "77.88.8.1"),
            RayDnsResolvers.slipResolvers(list, setOf("192.168.1.1", "2001:db8::53")))
        // all of them too small: the list stays, a tunnel through them may still carry something
        val all = list.map { it.resolver }.toSet()
        assertEquals(listOf("192.168.1.1", "195.208.5.1", "2001:db8::53", "62.76.76.62"),
            RayDnsResolvers.slipResolvers(list, all))
        assertEquals(1206, RayDnsResolvers.SLIP_MIN_EDNS)
    }

    @Test
    fun slipstreamTakesYandexOnlyInPlaceOfSmallOnes() {
        // the candidates of a phone in ru: the network's, the country's (Yandex among them), DoT and DoH
        val candidates = listOf(
            RayDnsResolvers.Candidate("192.168.1.1", "net"), RayDnsResolvers.Candidate("195.208.5.1", "udp"),
            RayDnsResolvers.Candidate("195.208.4.1", "udp"), RayDnsResolvers.Candidate("62.76.76.62", "udp"),
            RayDnsResolvers.Candidate("77.88.8.8", "udp"), RayDnsResolvers.Candidate("77.88.8.1", "udp"),
            RayDnsResolvers.Candidate("tls://common.dot.dns.yandex.net", "dot"),
        )
        val before = listOf("192.168.1.1", "195.208.5.1", "195.208.4.1", "62.76.76.62")
        val yandex = setOf("77.88.8.8", "77.88.8.1")
        // nothing measured small, no masterdns fallback: Yandex stays out, as for masterdns
        assertEquals(before, RayDnsResolvers.slipList(candidates, fallback = false, small = emptySet(), whole = yandex))
        // all but Yandex's measured small, Yandex's whole: they take the places
        val small = before.toSet()
        assertEquals(yandex.toList(), RayDnsResolvers.slipList(candidates, fallback = false, small = small, whole = yandex))
        // Yandex's did not answer the probe (neither small nor whole): the list as it was, not two dead ones
        assertEquals(before, RayDnsResolvers.slipList(candidates, fallback = false, small = small, whole = emptySet()))
        // one small: the rest, then only the Yandex measured whole
        assertEquals(listOf("195.208.5.1", "195.208.4.1", "62.76.76.62", "77.88.8.1"),
            RayDnsResolvers.slipList(candidates, fallback = false, small = setOf("192.168.1.1"), whole = setOf("77.88.8.1")))
        // Yandex's small too: the list as it was, nothing measured whole to take its place
        assertEquals(before, RayDnsResolvers.slipList(candidates, fallback = false, small = small + yandex, whole = emptySet()))
        // masterdns's fallback: Yandex's in the list as before, measured or not
        assertEquals(listOf("192.168.1.1", "195.208.5.1", "195.208.4.1", "62.76.76.62"),
            RayDnsResolvers.slipList(candidates, fallback = true, small = emptySet(), whole = emptySet()))
    }
}

package io.niko.fmt

import io.niko.fmt.socks.SOCKSBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DnsTunnelTest {

    private val key = "a".repeat(64)

    private fun dnstt(resolver: String, domain: String, key: String) = DnsTunnel(DnsTunnel.DNSTT, resolver, domain, key)

    private fun mdv(resolver: String, domain: String = "v.example.com", key: String = "secret", method: Int = 2) =
        DnsTunnel(DnsTunnel.MASTERDNSVPN, resolver, domain, key, method)

    @Test
    fun dnsttValidation() {
        assertNull(dnstt("", "", "").validate()) // off
        assertNull(dnstt("https://dns.example/dns-query", "t.example.com", key).validate())
        assertNull(dnstt("udp://8.8.8.8", "t.example.com", key).validate())
        assertNull(dnstt("8.8.8.8:53", "t.example.com", key).validate())
        assertNotNull(dnstt("", "t.example.com", key).validate())
        assertNotNull(dnstt("ftp://x", "t.example.com", key).validate())
        assertNotNull(dnstt("udp://8.8.8.8", "t.example.com", "abc").validate())
        assertNotNull(dnstt("udp://8.8.8.8", "https://t.example.com", key).validate())
        assertNotNull(dnstt("udp://8.8.8.8", "a.example.com,b.example.com", key).validate())
    }

    @Test
    fun masterDnsVpn() {
        assertNull(mdv("8.8.8.8\n1.1.1.1:5353\n192.168.1.0/30\n[2001:db8::1]:53").validate())
        assertNotNull(mdv("").validate())
        assertNotNull(mdv("https://dns.example/dns-query").validate())
        assertNotNull(mdv("8.8.8.8", key = "").validate())
        assertNull(mdv("8.8.8.8", key = "", method = 0).validate())
        assertNotNull(mdv("8.8.8.8", method = 9).validate())

        val options = mdv("8.8.8.8\n# comment\n1.1.1.1", domain = "a.example.com, b.example.com")
            .outboundOptions("protect_path")
        assertEquals("masterdnsvpn", options["type"])
        assertEquals(listOf("a.example.com", "b.example.com"), options["domains"])
        assertEquals(listOf("8.8.8.8", "1.1.1.1"), options["resolvers"])
        assertEquals("socks5", options["mode"])
        assertEquals("protect_path", options["protect_path"])
        assertFalse(mdv("8.8.8.8").outboundOptions(null).containsKey("protect_path"))
        assertEquals("tcp", mdv("8.8.8.8").copy(fixedUpstream = true).outboundOptions(null)["mode"])
    }

    /** raycontrol dnsauth-v1: the link's niko-dnsauth with niko-node and niko-svc, an auth block by key reference. */
    @Test
    fun dnsAuth() {
        val node = "33".repeat(16)
        val params = mapOf(
            "niko-dns" to "dnstt;udp://8.8.8.8;t.example.com;$key", "niko-dnsauth" to "v1",
            "niko-node" to node, "niko-svc" to "3",
        )
        val bean = SOCKSBean().apply { initializeDefaultValues(); applyNikoParams({ params[it] }) }
        assertEquals("v1;$node;3", bean.dnsTunnelAuth)
        val tunnel = DnsTunnel.of(bean)
        assertEquals(node to 3, tunnel.authNode)
        val auth = tunnel.outboundOptions(null, "raycontrol:$node:dev")["auth"] as Map<*, *>
        assertEquals(mapOf("node_id" to node, "service_id" to 3, "device_key_ref" to "raycontrol:$node:dev"), auth)
        // no key reference, no auth block (the device key is never in the config)
        assertFalse(tunnel.outboundOptions(null).containsKey("auth"))
        // only dnstt signs
        assertNull(tunnel.copy(engine = DnsTunnel.MASTERDNSVPN).authNode)
        // another version, or masterdnsvpn: off
        val other = SOCKSBean().apply { initializeDefaultValues(); applyNikoParams({ (params + ("niko-dnsauth" to "v2"))[it] }) }
        assertEquals("", other.dnsTunnelAuth)
        assertNull(DnsTunnel.of(other).authNode)
        // without the node it can not be signed
        try {
            SOCKSBean().apply { initializeDefaultValues(); applyNikoParams({ (params - "niko-node")[it] }) }
            org.junit.Assert.fail("accepted without niko-node")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test
    fun beanRoundTrip() {
        val bean = SOCKSBean().apply {
            initializeDefaultValues()
            dnsTunnel = DnsTunnel.MASTERDNSVPN
            dnsTunnelResolver = "8.8.8.8"
            dnsTunnelDomain = "v.example.com"
            dnsTunnelKey = "secret"
            dnsTunnelMethod = 5
            dnsTunnelFixedUpstream = true
        }
        val copy = KryoConverters.deserialize(SOCKSBean(), KryoConverters.serialize(bean))
        assertEquals(DnsTunnel.of(bean), DnsTunnel.of(copy))
        assertEquals(5, DnsTunnel.of(copy).method)
        assertEquals("tcp", DnsTunnel.of(copy).outboundOptions(null)["mode"])
    }
}

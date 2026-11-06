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
        assertNull(dnstt("tcp://195.208.5.1", "t.example.com", key).validate())
        assertNotNull(dnstt("", "t.example.com", key).validate())
        assertNotNull(dnstt("ftp://x", "t.example.com", key).validate())
        assertNotNull(dnstt("udp://8.8.8.8", "t.example.com", "abc").validate())
        assertNotNull(dnstt("udp://8.8.8.8", "https://t.example.com", key).validate())
        assertNotNull(dnstt("udp://8.8.8.8", "a.example.com,b.example.com", key).validate())
    }

    @Test
    fun masterDnsVpn() {
        assertNull(mdv("8.8.8.8\n1.1.1.1:5353\n192.168.1.0/30\n[2001:db8::1]:53\n2001:db8::2").validate())
        // [..] without a port, which the core skipped, goes bare (a profile saved with one still starts)
        assertNull(mdv("[2001:db8::1]\n8.8.8.8").validate())
        assertEquals(listOf("2001:db8::1", "8.8.8.8", "[2001:db8::2]:53"),
            mdv("[2001:db8::1]\n8.8.8.8\n[2001:db8::2]:53").outboundOptions(null)["resolvers"])
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

    /** raycontrol dnsseal-v1: the link's niko-dnsseal=v1;<key> gives the outbound its seal; dnstt only. */
    @Test
    fun dnsSeal() {
        val seal = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
        val params = mapOf("niko-dns" to "dnstt;udp://8.8.8.8;t.example.com;$key", "niko-dnsseal" to "v1;$seal")
        val bean = SOCKSBean().apply { initializeDefaultValues(); applyNikoParams({ params[it] }) }
        assertEquals(seal, bean.dnsTunnelSeal)
        val tunnel = DnsTunnel.of(bean)
        assertNull(tunnel.validate())
        assertEquals(mapOf("key" to seal), tunnel.outboundOptions(null)["seal"])
        // kept through the profile's storage
        val copy = KryoConverters.deserialize(SOCKSBean(), KryoConverters.serialize(bean))
        assertEquals(seal, copy.dnsTunnelSeal)
        // another version: open, as an older client sends it
        val v2 = SOCKSBean().apply { initializeDefaultValues(); applyNikoParams({ (params + ("niko-dnsseal" to "v2;$seal"))[it] }) }
        assertEquals("", v2.dnsTunnelSeal)
        assertFalse(DnsTunnel.of(v2).outboundOptions(null).containsKey("seal"))
        // masterdns has no seal
        val mdns = SOCKSBean().apply {
            initializeDefaultValues()
            applyNikoParams({ (params + ("niko-dns" to "masterdns;8.8.8.8;v.example.com;5"))[it] })
        }
        assertEquals("", mdns.dnsTunnelSeal)
        // a key that is not 32 bytes is refused, at the link and at the check
        try {
            SOCKSBean().apply { initializeDefaultValues(); applyNikoParams({ (params + ("niko-dnsseal" to "v1;short"))[it] }) }
            org.junit.Assert.fail("accepted a short key")
        } catch (e: IllegalArgumentException) {
        }
        assertNotNull(tunnel.copy(seal = "short").validate())
    }

    /** A subscription's update: the link's tunnel wins (its seal, its key), the user's resolver stays. */
    @Test
    fun updateKeepsWhatIsTheUsers() {
        val seal = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
        val old = SOCKSBean().apply {
            initializeDefaultValues()
            applyNikoParams({ mapOf("niko-dns" to "dnstt;udp://8.8.8.8;t.example.com;$key")[it] })
            dnsTunnelResolver = "tcp://195.208.5.1"
        }
        // the node turned the seal on and rotated its key: the link's values, the user's resolver
        val other = "cd".repeat(32)
        val fresh = SOCKSBean().apply {
            initializeDefaultValues()
            applyNikoParams({ mapOf("niko-dns" to "dnstt;udp://8.8.8.8;t.example.com;$other", "niko-dnsseal" to "v1;$seal")[it] })
        }
        fresh.keepDnsTunnelOf(old)
        assertEquals(seal, fresh.dnsTunnelSeal)
        assertEquals(other, fresh.dnsTunnelKey)
        assertEquals("tcp://195.208.5.1", fresh.dnsTunnelResolver)
        // a link without a tunnel: the one the user set stays whole
        val plain = SOCKSBean().apply { initializeDefaultValues() }
        plain.keepDnsTunnelOf(old)
        assertEquals(DnsTunnel.of(old), DnsTunnel.of(plain))
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

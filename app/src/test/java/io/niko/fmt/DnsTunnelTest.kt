package io.niko.fmt

import io.niko.fmt.socks.SOCKSBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
        // the subscription's skew: the tag's epoch is the node's
        assertEquals(-95L, (tunnel.outboundOptions(null, "raycontrol:$node:dev", -95)["auth"] as Map<*, *>)["clock_skew"])
        // v1 sends no version; the subscription's dnsauth.v 2 makes the row v2, the core's auth version 2
        assertEquals(1, tunnel.authVersion)
        DnsTunnel.signWithHint(bean, false)
        assertEquals("v1;$node;3", bean.dnsTunnelAuth)
        DnsTunnel.signWithHint(bean, true)
        assertEquals("v2;$node;3", bean.dnsTunnelAuth)
        val v2 = DnsTunnel.of(bean)
        assertEquals(node to 3, v2.authNode)
        assertEquals(2, (v2.outboundOptions(null, "raycontrol:$node:dev")["auth"] as Map<*, *>)["version"])
        DnsTunnel.signWithHint(bean, true)
        assertEquals("v2;$node;3", bean.dnsTunnelAuth)
        // another version: off
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

    /**
     * raycontrol v0.14 for masterdns: mdseal-v1 by niko-mdseal=v1[;required] (the key is niko-dnskey), dnsauth by
     * niko-dnsauth=v1 as dnstt's, AES-GCM only.
     */
    @Test
    fun masterDnsSealAndAuth() {
        val node = "44".repeat(16)
        val params = mapOf(
            "niko-dns" to "masterdns;8.8.8.8;v.example.com;5", "niko-dnskey" to "0123456789abcdef0123456789abcdef",
            "niko-mdseal" to "v1", "niko-dnsauth" to "v1", "niko-node" to node, "niko-svc" to "8",
        )
        fun bean(params: Map<String, String>) = SOCKSBean().apply { initializeDefaultValues(); applyNikoParams({ params[it] }) }
        val optional = DnsTunnel.of(bean(params))
        assertNull(optional.validate())
        assertEquals(DnsTunnel.SEAL_OPTIONAL, optional.seal)
        val options = optional.outboundOptions("protect_path", "raycontrol:$node:dev", 30)
        assertEquals("optional", options["seal"])
        assertEquals(
            mapOf("node_id" to node, "service_id" to 8, "device_key_ref" to "raycontrol:$node:dev", "clock_skew" to 30L),
            options["auth"],
        )
        assertEquals(DnsTunnel.SEAL_REQUIRED, DnsTunnel.of(bean(params + ("niko-mdseal" to "v1;required"))).seal)
        // another version, or the dnstt seal's parameter: open
        assertEquals("", DnsTunnel.of(bean(params + ("niko-mdseal" to "v2"))).seal)
        assertEquals("", DnsTunnel.of(bean(params - "niko-mdseal" + ("niko-dnsseal" to "v1;" + "A".repeat(43)))).seal)
        assertFalse(DnsTunnel.of(bean(params - "niko-mdseal")).outboundOptions(null).containsKey("seal"))
        // a tag under a cipher that does not authenticate the query, a seal without a key: refused at the check
        assertNotNull(optional.copy(method = 2).validate())
        assertNotNull(optional.copy(key = "").validate())
        assertNotNull(optional.copy(seal = "always").validate())
        assertNull(optional.copy(auth = "", method = 2).validate())
        // kept through the profile's storage
        val stored = KryoConverters.deserialize(SOCKSBean(), KryoConverters.serialize(bean(params + ("niko-mdseal" to "v1;required"))))
        assertEquals(DnsTunnel.SEAL_REQUIRED, stored.dnsTunnelSeal)
        assertEquals("v1;$node;8", stored.dnsTunnelAuth)
    }

    /**
     * raycontrol dnszones-v1: niko-dns-spare gives the spare zones in their order, at most 4, a name that is not one or
     * the main zone again left out; one zone goes into the outbound at a time.
     */
    @Test
    fun spareZones() {
        val params = mapOf(
            "niko-dns" to "dnstt;auto;t.example.com;$key",
            "niko-dns-spare" to "T2.Example.com, t3.example.com,t.example.com,bad_name.example.com,t3.example.com,t4.example.com,t5.example.com,t6.example.com",
        )
        val bean = SOCKSBean().apply { initializeDefaultValues(); applyNikoParams({ params[it] }) }
        assertEquals("t2.example.com,t3.example.com,t4.example.com,t5.example.com", bean.dnsTunnelSpare)
        val tunnel = DnsTunnel.of(bean)
        assertEquals(listOf("t.example.com", "t2.example.com", "t3.example.com", "t4.example.com", "t5.example.com"), tunnel.zones)
        assertNull(tunnel.validate())
        // a zone of its own: the outbound goes through it alone; the main one or another's leaves the tunnel as it was
        assertEquals("t3.example.com", tunnel.withZone("t3.example.com").outboundOptions(null)["domain"])
        assertEquals("t.example.com", tunnel.withZone("t.example.com").outboundOptions(null)["domain"])
        assertEquals("t.example.com", tunnel.withZone("other.example.org").outboundOptions(null)["domain"])
        assertEquals("t.example.com", tunnel.withZone(null).outboundOptions(null)["domain"])
        // masterdns: its DOMAINS one zone too
        val mdns = DnsTunnel.of(SOCKSBean().apply {
            initializeDefaultValues()
            applyNikoParams({ (params + ("niko-dns" to "masterdns;8.8.8.8;v.example.com;5") + ("niko-dnskey" to "k"))[it] })
        })
        assertEquals(listOf("t2.example.com"), mdns.withZone("t2.example.com").outboundOptions(null)["domains"])
        assertEquals(listOf("v.example.com"), mdns.outboundOptions(null)["domains"])
        // none given: none; a bad one set by hand: refused at the check
        assertEquals("", SOCKSBean().apply { initializeDefaultValues(); applyNikoParams({ (params - "niko-dns-spare")[it] }) }.dnsTunnelSpare)
        assertNotNull(tunnel.copy(spare = "t2.example.com,https://x").validate())
        // kept through the profile's storage, and through a subscription's update that gives none
        val stored = KryoConverters.deserialize(SOCKSBean(), KryoConverters.serialize(bean))
        assertEquals(bean.dnsTunnelSpare, stored.dnsTunnelSpare)
        val updated = SOCKSBean().apply { initializeDefaultValues() }
        updated.keepDnsTunnelOf(bean)
        assertEquals(bean.dnsTunnelSpare, updated.dnsTunnelSpare)
    }

    /** A subscription's update: a tunnel the link gives wins whole; one the user set on a link without one stays. */
    @Test
    fun updateKeepsWhatIsTheUsers() {
        val seal = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8"
        val old = SOCKSBean().apply {
            initializeDefaultValues()
            applyNikoParams({ mapOf("niko-dns" to "dnstt;udp://8.8.8.8;t.example.com;$key")[it] })
            // edited in the profile, or the turned-off tunnel's emptied domain: until the next update
            dnsTunnelResolver = "tcp://195.208.5.1"
        }
        // the node turned the seal on, rotated its key and moved to another resolver: all as the link has it
        val other = "cd".repeat(32)
        val fresh = SOCKSBean().apply {
            initializeDefaultValues()
            applyNikoParams({ mapOf("niko-dns" to "dnstt;tls://dns.node.example;t.example.com;$other", "niko-dnsseal" to "v1;$seal")[it] })
        }
        fresh.keepDnsTunnelOf(old)
        assertEquals(seal, fresh.dnsTunnelSeal)
        assertEquals(other, fresh.dnsTunnelKey)
        assertEquals("tls://dns.node.example", fresh.dnsTunnelResolver)
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

class SlipstreamTunnelTest {

    private val node = "44".repeat(16)
    private val pin = "A".repeat(42) + "Q"
    private fun bean(extra: Map<String, String> = emptyMap(), drop: Set<String> = emptySet()): SOCKSBean {
        val params = mapOf(
            "niko-dns" to "slipstream;1.1.1.1,77.88.8.8:5353,[2001:db8::1]:53;s.example.com;9",
            "niko-slip" to "v1;$pin;k3x9q2p7m1;cdn.example.net",
            "niko-dnsauth" to "v1", "niko-node" to node, "niko-svc" to "9",
        ) - drop + extra
        return SOCKSBean().apply { initializeDefaultValues(); applyNikoParams({ params[it] }) }
    }

    /** raycontrol slipstream-v1 §4: niko-dns, niko-slip and niko-dnsauth into the row; dnsauth is v2 from the start */
    @Test
    fun theLink() {
        val bean = bean()
        assertEquals(DnsTunnel.SLIPSTREAM, bean.dnsTunnel)
        assertEquals("1.1.1.1\n77.88.8.8:5353\n[2001:db8::1]:53", bean.dnsTunnelResolver)
        assertEquals(pin, bean.dnsTunnelKey)
        assertEquals("k3x9q2p7m1;cdn.example.net", bean.dnsTunnelTls)
        assertEquals("v2;$node;9", bean.dnsTunnelAuth)
        val tunnel = DnsTunnel.of(bean)
        assertNull(tunnel.validate())
        assertEquals(2, tunnel.authVersion)
        assertTrue(tunnel.ignoresServerAddress)
        assertEquals(listOf("1.1.1.1" to 53, "77.88.8.8" to 5353, "2001:db8::1" to 53), tunnel.slipResolvers)
        // the service id from niko-dns's last field when niko-svc is not there
        assertEquals("v2;$node;9", bean(drop = setOf("niko-svc")).dnsTunnelAuth)
        // no niko-slip: not a slipstream link
        try {
            bean(drop = setOf("niko-slip"))
            fail("taken without niko-slip")
        } catch (_: IllegalArgumentException) {
        }
    }

    /** the client's arguments: no secret among them, dnsauth with the node's clock */
    @Test
    fun theClientsArguments() {
        val tunnel = DnsTunnel.of(bean())
        val args = tunnel.slipstreamArgs(7001, listOf("127.0.0.1:7100", "127.0.0.1:7101"), -95)
        assertEquals(
            listOf(
                "--quiet", "--tcp-listen-host=127.0.0.1", "--tcp-listen-port=7001", "--domain=s.example.com",
                "--resolver=127.0.0.1:7100", "--resolver=127.0.0.1:7101", "--pin=$pin", "--alpn=k3x9q2p7m1",
                "--sni=cdn.example.net", "--dnsauth", "--clock-offset=-95",
            ),
            args,
        )
        val open = DnsTunnel.of(bean(drop = setOf("niko-dnsauth")))
        assertNull(open.authNode)
        assertFalse(open.slipstreamArgs(7001, listOf("127.0.0.1:7100"), 0).any { it.startsWith("--dnsauth") })
    }

    /** what a slipstream row may not be */
    @Test
    fun whatItRefuses() {
        val good = DnsTunnel.of(bean())
        for ((name, tunnel) in listOf(
            "a zone past 20 characters" to good.copy(domain = "slipstream-zone.example.com"),
            "two zones" to good.copy(domain = "s.example.com,t.example.com"),
            "a spare zone" to good.copy(spare = "t.example.com"),
            "a range for a resolver" to good.copy(resolver = "10.0.0.0/30"),
            "a name for a resolver" to good.copy(resolver = "dns.example.com"),
            "five resolvers" to good.copy(resolver = (1..5).joinToString("\n") { "10.0.0.$it" }),
            "a short pin" to good.copy(key = "abc"),
            "no ALPN" to good.copy(tls = ";cdn.example.net"),
            "no SNI" to good.copy(tls = "k3x9q2p7m1;"),
            "an address for the SNI" to good.copy(tls = "k3x9q2p7m1;1.2.3"),
            "dnsauth-v1" to good.copy(auth = "v1;$node;9"),
        )) {
            assertNotNull(name, tunnel.validate())
        }
    }
}

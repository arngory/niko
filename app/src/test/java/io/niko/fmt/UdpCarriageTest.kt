package io.niko.fmt

import io.niko.database.ProxyEntity
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.v2ray.VMessBean
import io.niko.fmt.wireguard.WireGuardBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UdpCarriageTest {

    private fun entity(bean: AbstractBean) = ProxyEntity().putBean(bean.apply { initializeDefaultValues() })

    private fun vless(transport: String = "tcp") = VMessBean().apply { alterId = -1; type = transport }

    @Test
    fun datagramsOrAStream() {
        assertEquals(true, entity(HysteriaBean()).carriesUdpAsDatagrams())
        assertEquals(true, entity(WireGuardBean()).carriesUdpAsDatagrams())
        // REALITY and other VLESS over TCP: UDP inside the stream; the QUIC transport too (one QUIC stream a connection)
        assertEquals(false, entity(vless()).carriesUdpAsDatagrams())
        assertEquals(false, entity(vless("quic")).carriesUdpAsDatagrams())
        // naive: none, or inside the stream (UoT)
        assertEquals(false, entity(NaiveBean()).carriesUdpAsDatagrams())
        // a raycontrol Yggdrasil profile: VLESS over the mesh's TLS link
        assertEquals(false, entity(vless().apply { yggPeer = "tls://203.0.113.2:443?key=" + "ab".repeat(32) }).carriesUdpAsDatagrams())
        assertNull(ProxyEntity(type = ProxyEntity.TYPE_CONFIG).let { runCatching { it.carriesUdpAsDatagrams() }.getOrNull() })
    }

    @Test
    fun aChainWithAStreamHop() {
        assertTrue(udpRidesAStream(listOf(entity(vless()))))
        assertFalse(udpRidesAStream(listOf(entity(HysteriaBean()))))
        // Hysteria2 behind REALITY: the REALITY hop carries it in a stream
        assertTrue(udpRidesAStream(listOf(entity(vless()), entity(HysteriaBean()))))
        assertFalse(udpRidesAStream(emptyList()))
    }

    @Test
    fun aProxyRuleGetsItsQuicRefusalAhead() {
        val rule = io.niko.SingBoxOptions.Rule_DefaultOptions().apply {
            domain_suffix = listOf("youtube.com")
            outbound = "proxy"
        }
        val refusal = quicRefusalBefore(rule, sniffed = true)!!.asMap()
        assertEquals(listOf("youtube.com"), refusal["domain_suffix"])
        assertEquals(listOf("udp"), refusal["network"])
        assertEquals(listOf("quic"), refusal["protocol"])
        assertEquals("reject", refusal["action"])
        assertEquals(true, refusal["no_drop"])
        assertFalse(refusal.containsKey("outbound"))
        // without sniffing, UDP 443 is the sign
        assertEquals(listOf(443L), quicRefusalBefore(rule, sniffed = false)!!.asMap()["port"])
        // no copy where it would not mean this rule's QUIC
        assertNull(quicRefusalBefore(io.niko.SingBoxOptions.Rule_DefaultOptions().apply { invert = true; outbound = "proxy" }, true))
        assertNull(quicRefusalBefore(io.niko.SingBoxOptions.Rule_DefaultOptions().apply { network = listOf("tcp"); outbound = "proxy" }, true))
        assertNull(quicRefusalBefore(io.niko.SingBoxOptions.Rule_DefaultOptions().apply { protocol = listOf("tls"); outbound = "proxy" }, true))
        assertNull(quicRefusalBefore(io.niko.SingBoxOptions.Rule_DefaultOptions().apply { port = listOf(8443); outbound = "proxy" }, false))
        // a rule with JSON of its own: inverted, logical, with route options or another network, a copy could mean
        // anything (refuse all but QUIC, or a config the core does not start with): none, its QUIC goes as switched off
        for (json in listOf(
            """{"invert": true}""",
            """{"type": "logical", "mode": "or", "rules": [{"domain_suffix": ["ru"]}]}""",
            """{"udp_timeout": "5m"}""",
            """{"network": "tcp"}""",
        )) {
            val own = io.niko.SingBoxOptions.Rule_DefaultOptions().apply {
                domain_suffix = listOf("ru"); outbound = "proxy"; _hack_custom_config = json
            }
            assertNull(json, quicRefusalBefore(own, true))
        }
        // inverted or logical by the keys the app put in, as the core reads the rule
        assertNull(quicRefusalBefore(io.niko.SingBoxOptions.Rule_DefaultOptions().apply {
            domain_suffix = listOf("ru"); outbound = "proxy"; _hack_config_map["invert"] = true
        }, true))
        // the rule at the end: never a silent drop
        assertEquals(true, quicRefusal(true).asMap()["no_drop"])
    }
}

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
        // REALITY and other VLESS over TCP: UDP inside the stream; over a QUIC transport: datagrams of its own
        assertEquals(false, entity(vless()).carriesUdpAsDatagrams())
        assertEquals(true, entity(vless("quic")).carriesUdpAsDatagrams())
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
}

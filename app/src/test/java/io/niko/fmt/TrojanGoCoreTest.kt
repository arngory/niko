package io.niko.fmt

import io.niko.fmt.trojan_go.buildSingBoxOutboundTrojanGoBean
import io.niko.fmt.trojan_go.parseTrojanGo
import io.niko.SingBoxOptions.V2RayTransportOptions_WebsocketOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

// Trojan-Go profiles run on the core's Trojan: plain and over WebSocket, no plugin
class TrojanGoCoreTest {

    @Test
    fun webSocketOnTheCoresTrojan() {
        val bean = parseTrojanGo("trojan-go://pass%20word@example.com:443/?sni=cdn.example.com&type=ws&host=h.example.com&path=%2Fws#n")
        val out = buildSingBoxOutboundTrojanGoBean(bean, false)
        assertEquals("trojan", out.type)
        assertEquals("example.com", out.server)
        assertEquals(443, out.server_port)
        assertEquals("pass word", out.password)
        assertEquals("cdn.example.com", out.tls.server_name)
        val ws = out.transport as V2RayTransportOptions_WebsocketOptions
        assertEquals("/ws", ws.path)
        assertEquals("h.example.com", ws.headers["Host"])
    }

    @Test
    fun plainTakesTheServerAsSni() {
        val out = buildSingBoxOutboundTrojanGoBean(parseTrojanGo("trojan-go://p@example.com:443#n"), false)
        assertEquals("example.com", out.tls.server_name)
        assertNull(out.transport)
    }

    @Test
    fun shadowsocksLayerAndMuxOnTheCore() {
        val bean = parseTrojanGo("trojan-go://p@example.com:443/?encryption=ss%3Baes-256-gcm%3As%3Ae%3Acret#n")
        bean.mux = true
        val out = buildSingBoxOutboundTrojanGoBean(bean, false)
        assertEquals(mapOf("method" to "aes-256-gcm", "password" to "s:e:cret"), out._hack_config_map["trojan_go_shadowsocks"])
        assertEquals(true, out._hack_config_map["trojan_go_mux"])
        // none, and no mux: neither key
        val plain = buildSingBoxOutboundTrojanGoBean(parseTrojanGo("trojan-go://p@example.com:443/?encryption=none#n"), false)
        assertFalse(plain._hack_config_map.containsKey("trojan_go_shadowsocks") || plain._hack_config_map.containsKey("trojan_go_mux"))
    }

    @Test
    fun mieruLink() {
        val bean = io.niko.fmt.mieru.parseMieru(
            "mierus://alice:p%40ss@[2001:db8::1]?profile=home&mtu=1400&multiplexing=MULTIPLEXING_LOW&handshake-mode=HANDSHAKE_NO_WAIT&traffic-pattern=CCI%3D&port=3000-3010&protocol=UDP&port=2999&protocol=TCP"
        )
        assertEquals("2001:db8::1", bean.serverAddress)
        assertEquals(3000, bean.serverPort)
        assertEquals("alice", bean.username)
        assertEquals("p@ss", bean.password)
        assertEquals("UDP", bean.protocol)
        assertEquals("home", bean.name)
        val out = io.niko.fmt.mieru.buildSingBoxOutboundMieruBean(bean)._hack_config_map
        assertEquals("mieru", io.niko.fmt.mieru.buildSingBoxOutboundMieruBean(bean).type)
        assertEquals("udp", out["transport"])
        assertEquals(1400, out["mtu"])
        assertEquals("MULTIPLEXING_LOW", out["multiplexing"])
        assertEquals("HANDSHAKE_NO_WAIT", out["handshake_mode"])
        assertEquals("CCI=", out["traffic_pattern"])
    }
}

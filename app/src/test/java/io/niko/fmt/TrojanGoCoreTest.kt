package io.niko.fmt

import io.niko.fmt.trojan_go.buildSingBoxOutboundTrojanGoBean
import io.niko.fmt.trojan_go.needsPlugin
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
        assertFalse(bean.needsPlugin())
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
    fun shadowsocksLayerStillNeedsThePlugin() {
        val bean = parseTrojanGo("trojan-go://p@example.com:443/?encryption=ss%3Baes-128-gcm%3Asecret#n")
        assertTrue(bean.needsPlugin())
    }
}

package io.niko.fmt

import io.niko.fmt.naive.buildSingBoxOutboundNaiveBean
import io.niko.fmt.naive.naiveHeaders
import io.niko.fmt.naive.parseNaive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class NaiveCoreTest {

    @Test
    fun buildsTheCoreOutbound() {
        val bean = parseNaive("naive+quic://niko:secret@203.0.113.7:443?sni=naive.example&insecure-concurrency=2#n")
        val o = buildSingBoxOutboundNaiveBean(bean)
        assertEquals("naive", o.type)
        val m = o._hack_config_map
        assertEquals("203.0.113.7", m["server"])
        assertEquals(443, m["server_port"])
        assertEquals("niko", m["username"])
        assertEquals("secret", m["password"])
        assertEquals(true, m["quic"])
        assertEquals(2, m["insecure_concurrency"])
        @Suppress("UNCHECKED_CAST")
        val tls = m["tls"] as Map<String, Any>
        assertEquals(true, tls["enabled"])
        assertEquals("naive.example", tls["server_name"])
        // naive takes no insecure: never set
        assertFalse(tls.containsKey("insecure"))
    }

    @Test
    fun addressGetsNoServerName() {
        val o = buildSingBoxOutboundNaiveBean(parseNaive("naive+https://u:p@198.51.100.1:443"))
        @Suppress("UNCHECKED_CAST")
        val tls = o._hack_config_map["tls"] as Map<String, Any>
        assertFalse(tls.containsKey("server_name"))
        assertFalse(o._hack_config_map.containsKey("quic"))
    }

    @Test
    fun udpOverTcpOnlyWhereTheLinkSaysSo() {
        // a server without UoT (a raycontrol node so far) closes the tunnel to the magic name: off unless the link
        // says the server takes it
        assertFalse(parseNaive("naive+https://u:p@198.51.100.1:443#n").sUoT)
        assertEquals(true, parseNaive("naive+https://u:p@198.51.100.1:443?uot=2#n").sUoT)
        assertFalse(parseNaive("naive+https://u:p@198.51.100.1:443?uot=0#n").sUoT)
        // v1 or a bare flag: the core speaks v2 only
        assertFalse(parseNaive("naive+https://u:p@198.51.100.1:443?uot=1#n").sUoT)
        assertFalse(parseNaive("naive+https://u:p@198.51.100.1:443?uot=true#n").sUoT)
    }

    @Test
    fun pingThroughTheNodeOnlyWhereTheLinkSaysSo() {
        val bean = parseNaive("naive+https://u:p@198.51.100.1:443?uot=2&icmp=1#n")
        assertEquals(true, bean.nikoIcmp)
        // kept through the store (bean version 4)
        assertEquals(true, KryoConverters.deserialize(io.niko.fmt.naive.NaiveBean(), KryoConverters.serialize(bean)).nikoIcmp)
        for (link in listOf("naive+https://u:p@198.51.100.1:443#n", "naive+https://u:p@198.51.100.1:443?icmp=on#n")) {
            val other = parseNaive(link)
            assertFalse(link, other.nikoIcmp)
            assertFalse(link, KryoConverters.deserialize(io.niko.fmt.naive.NaiveBean(), KryoConverters.serialize(other)).nikoIcmp)
        }
    }

    @Test
    fun headers() {
        assertEquals(mapOf("X-A" to "1", "X-B" to "two: parts"), naiveHeaders("X-A: 1\nX-B: two: parts\n\nbad"))
    }
}

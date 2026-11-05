package io.niko.fmt

import io.niko.fmt.naive.buildSingBoxOutboundNaiveBean
import io.niko.fmt.naive.parseNaive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeTlsTest {

    @Test
    fun naiveTakesNoNodeTweaks() {
        // a raycontrol node's naive row: cronet refuses curve_preferences, and its own tls map must stay whole
        val o = buildSingBoxOutboundNaiveBean(parseNaive("naive+https://niko:pw@203.0.113.2:443?sni=naive.example"))
        val tls = o.asMap()["tls"] as Map<*, *>
        assertTrue(nodeTlsTweaks("naive", tls).isEmpty())
        val merged = mergeTls(o._hack_config_map["tls"] as Map<*, *>, mapOf("record_fragment" to true))
        assertEquals(true, merged["enabled"])
        assertEquals("naive.example", merged["server_name"])
        assertEquals(true, merged["record_fragment"])
    }

    @Test
    fun otherOutbounds() {
        assertEquals(mapOf("curve_preferences" to listOf("X25519MLKEM768")), nodeTlsTweaks("hysteria2", mapOf("enabled" to true)))
        assertEquals(
            mapOf("reality" to mapOf("key_share" to "hybrid")),
            nodeTlsTweaks("vless", mapOf("enabled" to true, "reality" to mapOf("enabled" to true)))
        )
        var replaced: Any? = null
        val tweaks = nodeTlsTweaks(
            "vless", mapOf("enabled" to true, "utls" to mapOf("enabled" to true, "fingerprint" to "firefox"))
        ) { replaced = it }
        assertEquals(mapOf("fingerprint" to "chrome"), tweaks["utls"])
        assertEquals("firefox", replaced)
        assertTrue(nodeTlsTweaks("vless", mapOf("enabled" to false)).isEmpty())
        // a typed outbound keeps no tls among its raw options: the tweaks alone
        assertEquals(mapOf("x" to 1), mergeTls(null, mapOf("x" to 1)))
    }
}

package io.niko.fmt

import io.niko.fmt.naive.buildSingBoxOutboundNaiveBean
import io.niko.fmt.naive.parseNaive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    // cert-pin-v1: naive (Chromium) checks the pins besides the chain; Go TLS takes them in its place
    @Test
    fun keyPins() {
        val pins = "8dWUSbcnFl3nMr8oMzgSK5liimFZGP7cZ9h4__z0fac,aKDy1GvP0Ev823jJBZeuwhvSD4gJN1ax7KUDa0YLvnM"
        val hashes = listOf("8dWUSbcnFl3nMr8oMzgSK5liimFZGP7cZ9h4//z0fac=", "aKDy1GvP0Ev823jJBZeuwhvSD4gJN1ax7KUDa0YLvnM=")
        val bean = parseNaive("naive+https://niko:pw@203.0.113.2:443?sni=naive.example&cert=ROOT&niko-pin=${pins.replace(",", "%2C")}")
        assertEquals(pins, bean.certPins)
        val o = buildSingBoxOutboundNaiveBean(bean)
        val own = o._hack_config_map["tls"] as Map<*, *>
        assertNull(pinRefusal("naive", false, own))
        val tls = mergeTls(own, pinTlsTweaks("naive", bean.certPins))
        assertEquals(hashes, tls["certificate_public_key_sha256"])
        // the profile's own roots stay: the chain is still checked, against them
        assertEquals(listOf("ROOT"), tls["certificate"])
        assertEquals("naive.example", tls["server_name"])

        // a naive profile at an address without a name: Chromium has nothing to pin the key to
        val bare = buildSingBoxOutboundNaiveBean(parseNaive("naive+https://niko:pw@203.0.113.2:443"))
        assertEquals("naive needs a server name (SNI)", pinRefusal("naive", false, bare._hack_config_map["tls"] as Map<*, *>))

        // Go TLS: the pin in place of the chain, a custom CA goes (sing-box takes one or the other)
        assertEquals(
            mapOf("certificate_public_key_sha256" to hashes, "certificate" to emptyList<String>()),
            pinTlsTweaks("hysteria2", pins),
        )
        assertNull(pinRefusal("hysteria2", false, mapOf("enabled" to true)))
        assertEquals("not supported with REALITY",
            pinRefusal("vless", false, mapOf("enabled" to true, "reality" to mapOf("enabled" to true))))
        assertEquals("the profile does not use TLS", pinRefusal("vless", false, mapOf("enabled" to false)))
        assertEquals("not supported for this profile type", pinRefusal("shadowsocks", false, mapOf("enabled" to true)))
        assertEquals("not supported for this profile type", pinRefusal("hysteria2", true, mapOf("enabled" to true)))

        // the node's DoH: the pins of its row, none without
        assertEquals(
            mapOf("enabled" to true, "server_name" to "d3x8.example.com", "curve_preferences" to listOf("X25519MLKEM768"),
                "certificate_public_key_sha256" to hashes),
            nodeDohTls("d3x8.example.com", pins),
        )
        assertFalse(nodeDohTls("d3x8.example.com", null).containsKey("certificate_public_key_sha256"))
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

package io.niko.raycontrol

import com.google.gson.JsonParser
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class Rcdev1Test {

    // spa-v2.md §8, with the vector parameters
    private val json = """
        {"v": 1, "host": "203.0.113.7",
         "spa": {"node_id": "33333333333333333333333333333333", "epoch_len_s": 86400, "slot_len_s": 600,
                 "port_key": "4444444444444444444444444444444444444444444444444444444444444444",
                 "port_base": 40000, "port_range": 4096, "len_min": 96, "len_max": 160},
         "device_id": "0123456789abcdef",
         "device_key": "1111111111111111111111111111111111111111111111111111111111111111",
         "services": [{"id": 1, "kind": "reality", "name": "reality"}, {"id": 2, "kind": "dnstt", "name": "dns"}]}
    """.trimIndent()

    private fun pack(text: String) = Rcdev1.PREFIX + text.encodeUtf8().base64Url().trimEnd('=')

    @Test
    fun parsesRegistration() {
        val registration = Rcdev1.parse("  " + pack(json) + "\n")
        assertEquals("203.0.113.7", registration.host)
        assertEquals("33333333333333333333333333333333", registration.spa.nodeId)
        assertEquals(40000, registration.spa.portBase)
        assertEquals(160, registration.spa.lenMax)
        assertEquals("0123456789abcdef", registration.deviceId)
        assertEquals("1".repeat(64), registration.deviceKey)
        assertEquals(listOf(RayService(1, "reality", "reality"), RayService(2, "dnstt", "dns")), registration.services)
        // the device key never shows up in a log line
        assertFalse(registration.toString().contains("1".repeat(64)))
        assertTrue(Rcdev1.matches(" rcdev1:abc"))
        assertFalse(Rcdev1.matches("vless://x"))
    }

    // `rayctl device add` without -o prints the package among other words; a terminal may wrap it
    @Test
    fun findsPackageInText() {
        val token = pack(json)
        val output = "Device phone of alice added.\nPackage: $token\nHand it over a trusted channel.\n"
        assertTrue(Rcdev1.matches(output))
        assertEquals("0123456789abcdef", Rcdev1.parse(output).deviceId)
        val wrapped = "Package:\n" + token.chunked(80).joinToString("\n") + "\nDone\n"
        assertEquals("203.0.113.7", Rcdev1.parse(wrapped).host)
        assertFalse(Rcdev1.matches("xrcdev1:abc"))
    }

    @Test
    fun rejectsBadPackages() {
        val bad = listOf(
            "rcdev1:",
            "rcdev1:!!!",
            pack("not json"),
            pack(json.replace("\"v\": 1", "\"v\": 2")),
            pack(json.replace("\"203.0.113.7\"", "\"\"")),
            pack(json.replace("33333333333333333333333333333333", "3333")),
            pack(json.replace("\"device_key\": \"1111", "\"device_key\": \"zz11")),
            pack(json.replace("\"port_base\": 40000", "\"port_base\": 64000")),
            pack(json.replace("\"len_max\": 160", "\"len_max\": 600")),
            pack(json.replace("\"slot_len_s\": 600", "\"slot_len_s\": 0")),
        )
        // no package text in the logs, not even a test one (niko-r4-note.md §5)
        for ((i, text) in bad.withIndex()) {
            try {
                Rcdev1.parse(text)
                fail("accepted bad package #$i")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    @Test
    fun nodeRoundTripAndKnockOptions() {
        val r = Rcdev1.parse(pack(json))
        val node = RayNode(r.host, r.spa, r.deviceId, "wrapped", r.services)
        assertEquals(node, RayNode.fromJson(JsonParser.parseString(node.toJson().toString()).asJsonObject))
        assertTrue(node.matchesHost("203.0.113.7"))
        assertTrue(RayNode("2001:db8::1", r.spa, r.deviceId, "w", emptyList()).matchesHost("[2001:DB8::1]"))
        assertFalse(node.matchesHost("203.0.113.8"))

        val spa = node.knockOptions()["spa_v2"] as Map<*, *>
        assertEquals("raycontrol:33333333333333333333333333333333:0123456789abcdef", spa["device_key_ref"])
        assertEquals(4096, spa["port_range"])
        // the key itself never enters the config
        assertFalse(spa.containsKey("device_key"))
        assertFalse(node.knockOptions().toString().contains("1".repeat(64)))
        assertFalse(spa.containsKey("carriers"))
        assertFalse(node.spaJson().has("carriers"))

        // the subscription's spa.carriers (v0.10.1) go with the SPA options to the core, and are stored
        val listed = node.copy(spaCarriers = listOf("udp", "tls"))
        assertEquals(listOf("udp", "tls"), (listed.knockOptions()["spa_v2"] as Map<*, *>)["carriers"])
        assertEquals("[\"udp\",\"tls\"]", listed.spaJson().get("carriers").toString())
        assertEquals(listed, RayNode.fromJson(JsonParser.parseString(listed.toJson().toString()).asJsonObject))
    }
}

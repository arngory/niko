package io.niko.raycontrol

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RayRelaysTest {

    private val relaySpa = RaySpaParams(
        nodeId = "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf", epochLenS = 600, slotLenS = 30,
        portKey = "00".repeat(32), portBase = 20000, portRange = 1000, lenMin = 96, lenMax = 160,
    )
    private val relay = RayRelay("r1", "198.51.100.20", relaySpa)

    @Test
    fun viaLinkSwapsTheHost() {
        // REALITY: the relay's host, the node's names, keys and port
        assertEquals(
            "vless://uuid@198.51.100.20:443?security=reality&sni=github.com&pbk=key#r",
            RayRelays.viaLink("vless://uuid@203.0.113.2:443?security=reality&sni=github.com&pbk=key#r", relay)
        )
        // Hysteria2: UDP 443 at the relay, no hopping
        assertEquals(
            "hysteria2://pw@198.51.100.20:443?obfs=salamander#h",
            RayRelays.viaLink("hysteria2://pw@203.0.113.2:8443?obfs=salamander&mport=20000-30000#h", relay)
        )
        assertNull(RayRelays.viaLink("not a link", relay))
    }

    @Test
    fun keys() {
        val key = RayRelays.viaKey("3#2", "r1")
        assertEquals("3#2#via-r1", key)
        // the service id stays in front: hints, probes and rates take the row as their service's
        assertEquals("3", key.substringBefore('#'))
        assertEquals("r1", RayRelays.relayOfKey(key))
        assertNull(RayRelays.relayOfKey("3#2"))
    }

    @Test
    fun relaysAndVia() {
        val payload = JsonParser.parseString(
            """{"relays":[{"id":"r1","host":"198.51.100.20","spa":${relaySpa.toJson()}},{"id":"","host":"x","spa":{}}],
               "profiles":[{"svc":3,"name":"reality","link":"vless://a@b:443","via":["r1","r1",""]}]}"""
        ).asJsonObject
        assertEquals(listOf(relay), RayRelay.listFromJson(payload.getAsJsonArray("relays")))
        assertEquals(listOf("r1"), RaySubscription.viaOf(payload.getAsJsonArray("profiles")[0].asJsonObject))
    }
}

class RayRelayProbeTest {

    @Test
    fun probeOfARelay() {
        val spa = RaySpaParams("b0".repeat(16), 600, 30, "00".repeat(32), 20000, 1000, 96, 160)
        val o = JsonParser.parseString(
            """{"id":"r1","host":"198.51.100.20","spa":${spa.toJson()},
               "probe":{"v":1,"port":8443,"pin":"pin","udp":true,"report":true,"svc":12},"future":1}"""
        ).asJsonObject
        val relay = RayRelay.fromJson(o)!!
        assertEquals(RayRelayProbe(1, 8443, "pin", udp = true, report = true, svc = 12), relay.probe)
        assertEquals(relay, RayRelay.fromJson(relay.toJson()))
        // another version or no pin: no probe, the relay still forwards
        o.getAsJsonObject("probe").addProperty("v", 2)
        assertNull(RayRelay.fromJson(o)!!.probe)
    }

    @Test
    fun orderForTheNetwork() {
        val keys = listOf("3", "3#via-r1", "4", "4#via-r1")
        assertEquals(listOf("3", "4", "3#via-r1", "4#via-r1"), RayRelays.order(keys, viaFirst = false))
        assertEquals(listOf("3#via-r1", "4#via-r1", "3", "4"), RayRelays.order(keys, viaFirst = true))
    }

    @Test
    fun viaRelayHint() {
        val h = RayHints.fromJson(JsonParser.parseString("""{"order":[3],"via_relay":true,"devices":5,"days":2}""").asJsonObject, 1)!!
        assertEquals(true, h.viaRelay)
        assertEquals(true, RayHints.fromJson(h.toJson(), 1)!!.viaRelay)
    }
}

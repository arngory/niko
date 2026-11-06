package io.niko.raycontrol

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.fmt.DnsTunnel
import io.niko.fmt.hysteria.HysteriaBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The subscription as the app reads it from a real node: RaycontrolFetchSubscription's JSON from raycontrol-interop
 * (raycontrol v0.11.1 with niko-server v0.8.3; the CI node's throwaway keys). The app's own parsing on it, so a field
 * the node sends and the app misses (as hints.via_relay once never reached it) shows here.
 */
class RaySubscriptionPayloadTest {

    private val result = JsonParser.parseString(
        requireNotNull(javaClass.getResource("/raycontrol/subscription-v0.11.1.json")).readText()
    ).asJsonObject
    private val payload = result.getAsJsonObject("payload")

    /**
     * The node leaves the DNS tunnels' resolver to the client (niko-dns=dnstt;;zone;key): the app's setting fills it
     * at import, auto by default, which a test has no store for. As if the setting were auto.
     */
    private fun withAuto(p: JsonObject) = p.deepCopy().apply {
        getAsJsonArray("profiles").forEach { e ->
            val o = e.asJsonObject
            o.addProperty("link", o.get("link").asString.replace(Regex("niko-dns=(dnstt|masterdns)%3B%3B"), "niko-dns=$1%3Bauto%3B"))
        }
    }

    @Test
    fun theNodeLeavesTheDnsResolverToTheClient() {
        val dns = payload.getAsJsonArray("profiles").map { it.asJsonObject.get("link").asString }.filter { "niko-dns=" in it }
        assertEquals(2, dns.size)
        assertTrue(dns.all { Regex("niko-dns=(dnstt|masterdns)%3B%3B").containsMatchIn(it) })
    }

    @Test
    fun everyRowAndWhatTheNodeRecordTakes() {
        val parsed = RaySubscription.parse(withAuto(payload), emptyMap(), payload.get("time").asLong)
        assertEquals(0, parsed.skipped)
        // six services; REALITY and both Hysteria2 through the relay r1 as well
        assertEquals(
            setOf("3", "4", "6", "7", "8", "9", "3#via-r1", "4#via-r1", "9#via-r1"),
            parsed.beans.keys,
        )
        // the relay with its own probe endpoint (R6-c), the node's probe with what its summary takes
        val relay = parsed.relays.single()
        assertEquals("r1", relay.id)
        assertEquals(1, requireNotNull(relay.probe).v)
        val probe = requireNotNull(parsed.probe)
        assertTrue(probe.report.containsAll(listOf("udp_mtu", "path", "dns")))
        assertEquals(listOf(3, 4, 6, 7, 8, 9), probe.tunnelSvc)
        assertEquals(listOf("udp", "tls", "tcp"), parsed.carriers)
        // dnsprobe-v1: the key for the dnstt user
        assertNotNull(parsed.dnsProbeKey)
    }

    @Test
    fun theDnsTunnelsAndTheRowsThroughTheRelay() {
        val parsed = RaySubscription.parse(withAuto(payload), emptyMap(), payload.get("time").asLong)
        val dnstt = DnsTunnel.of(parsed.beans.getValue("7"))
        assertEquals(DnsTunnel.DNSTT, dnstt.engine)
        assertEquals(RayDnsResolvers.AUTO, dnstt.resolver)
        // dnsauth-v1: the node's dnstt takes signed queries only
        assertNotNull(dnstt.authNode)
        val masterdns = DnsTunnel.of(parsed.beans.getValue("8"))
        assertEquals(DnsTunnel.MASTERDNSVPN, masterdns.engine)
        assertEquals(RayDnsResolvers.AUTO, masterdns.resolver)
        // through the relay: its address, Hysteria2 on UDP 443 there (a Hysteria bean keeps its ports in serverPorts)
        val relayHost = parsed.relays.single().host
        for (key in listOf("3#via-r1", "4#via-r1", "9#via-r1")) assertEquals(relayHost, parsed.beans.getValue(key).serverAddress)
        assertEquals("443", (parsed.beans.getValue("4#via-r1") as HysteriaBean).serverPorts)
    }

    /** dnsauth-v2: the subscription's dnsauth.v with 2 signs the node's DNS tunnels by the hint; without it, by v1. */
    @Test
    fun theHintWhenTheSubscriptionAnnouncesIt() {
        fun auths(p: JsonObject) = RaySubscription.parse(withAuto(p), emptyMap(), p.get("time").asLong).beans
            .filterKeys { it == "7" || it == "8" }.mapValues { (_, bean) -> DnsTunnel.of(bean) }
        val signed = auths(payload).filterValues { it.authNode != null }
        assertTrue("the fixture's dnstt row signs", "7" in signed)
        for ((key, tunnel) in signed) {
            assertEquals(key, 1, tunnel.authVersion)
            assertTrue(key, tunnel.auth.startsWith("v1;"))
        }
        val announced = payload.deepCopy().apply {
            add("dnsauth", JsonParser.parseString("""{"v":[1,2]}"""))
        }
        for ((key, tunnel) in auths(announced)) {
            if (key !in signed) {
                // a row that does not sign stays so
                assertEquals(key, "", tunnel.auth)
                continue
            }
            assertEquals(key, 2, tunnel.authVersion)
            assertEquals(key, signed.getValue(key).authNode, tunnel.authNode)
            assertEquals(key, 2, (tunnel.outboundOptions(null, "raycontrol:x:dev")["auth"] as Map<*, *>)["version"])
        }
        // v1 alone, or a field this client cannot read: v1
        for (field in listOf("""{"v":[1]}""", """{"v":"2"}""", """[2]""")) {
            val other = payload.deepCopy().apply { add("dnsauth", JsonParser.parseString(field)) }
            for ((key, tunnel) in auths(other)) assertEquals("$field $key", 1, tunnel.authVersion)
        }
    }
}

package io.niko.raycontrol

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.fmt.DnsTunnel
import io.niko.fmt.hysteria.HysteriaBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
        // a node before v0.21.0 has no note
        assertNull(parsed.note)
    }

    // client-min-v1 §4: a device below the node's minimum gets no profiles, `client.refused`, and still the app's
    // releases and the relays (the way to update); a device at it gets `client.min` for its information
    @Test
    fun theNodesMinimumVersion() {
        val time = payload.get("time").asLong
        assertNull(RaySubscription.parse(withAuto(payload), emptyMap(), time).client)
        val told = withAuto(payload).apply {
            add("client", JsonParser.parseString("""{"v": 1, "min": {"niko": "1.59.0"}, "from": 1768435200}"""))
        }
        val parsed = RaySubscription.parse(told, emptyMap(), time)
        assertEquals(RayClientMin(mapOf("niko" to "1.59.0"), 1_768_435_200, refused = false), parsed.client)
        assertTrue(parsed.beans.isNotEmpty())
        val refused = told.deepCopy().apply {
            add("profiles", com.google.gson.JsonArray())
            add("client", JsonParser.parseString("""{"v": 1, "min": {"niko": "1.59.0"}, "refused": true}"""))
            add("app", JsonParser.parseString("""{"niko": [{"tag": "v1.59.0", "prerelease": false}]}"""))
        }
        val gone = RaySubscription.parse(refused, emptyMap(), time)
        assertEquals(true, gone.client?.refused)
        assertTrue(gone.beans.isEmpty())
        assertEquals(parsed.relays, gone.relays)
        assertEquals(listOf(RayAppRelease("v1.59.0", prerelease = false)), gone.appReleases)
    }

    @Test
    fun theNodesNote() {
        val noted = withAuto(payload).apply { addProperty("note", "Лимит 100 ГБ\u202E\n😀") }
        assertEquals("Лимит 100 ГБ 😀", RaySubscription.parse(noted, emptyMap(), payload.get("time").asLong).note)
        noted.addProperty("note", 5)
        assertEquals("5", RaySubscription.parse(noted, emptyMap(), payload.get("time").asLong).note)
        noted.add("note", JsonObject())
        assertNull(RaySubscription.parse(noted, emptyMap(), payload.get("time").asLong).note)
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

    /**
     * relay-names-v1 §8 (raycontrol v0.35.0, internal/sub/conformance_test.go): a line through r1 by its name comes from
     * via_links, its next from next.via_links or else via_links as it is; a profile without one keeps the host swap;
     * an id not in via is never read; the relay's address is not taken for the node's.
     */
    @Test
    fun theRowsThroughTheRelayByItsName() {
        val relayHost = payload.getAsJsonArray("relays")[0].asJsonObject.get("host").asString
        fun byName(link: String, name: String) =
            link.replace("@203.0.113.2:", "@$name:").replace("#", "&niko-ip=$relayHost#")
        val named = withAuto(payload)
        val profiles = named.getAsJsonArray("profiles").map { it.asJsonObject }
        val reality = profiles.first { it.get("svc").asInt == 3 }
        val hy2m = profiles.first { it.get("svc").asInt == 9 }
        val realityVia = byName(reality.get("link").asString, "x7q.example.test")
        val hy2mVia = byName(hy2m.get("link").asString, "k2m.example.test")
        val hy2mNextVia = hy2mVia.replace("2e4110bb", "3f5221cc")
        reality.add("via_links", JsonObject().apply {
            addProperty("r1", realityVia)
            addProperty("r9", "vless://u@r9.example.test:443#x")
        })
        reality.add("next", JsonObject().apply {
            addProperty("link", reality.get("link").asString)
            addProperty("from", payload.get("time").asLong + 600)
        })
        hy2m.add("via_links", JsonObject().apply { addProperty("r1", hy2mVia) })
        hy2m.add("next", JsonObject().apply {
            addProperty("link", hy2m.get("link").asString.replace("2e4110bb", "3f5221cc"))
            addProperty("from", payload.get("time").asLong + 600)
            add("via_links", JsonObject().apply { addProperty("r1", hy2mNextVia) })
        })
        val parsed = RaySubscription.parse(named, emptyMap(), payload.get("time").asLong)
        val realityRow = parsed.links.getValue("3#via-r1")
        assertEquals(realityVia, realityRow.link)
        // no next.via_links: the line by name stays across the switch
        assertEquals(realityVia, realityRow.next)
        assertEquals("x7q.example.test", parsed.beans.getValue("3#via-r1").serverAddress)
        val hy2mRow = parsed.links.getValue("9#via-r1")
        assertEquals(hy2mVia, hy2mRow.link)
        assertEquals(hy2mNextVia, hy2mRow.next)
        assertEquals("k2m.example.test", parsed.beans.getValue("9#via-r1").serverAddress)
        // hy2o sent none: the relay's address for the host, as before
        assertEquals(relayHost, parsed.beans.getValue("4#via-r1").serverAddress)
        assertNull(parsed.links["3#via-r9"])
        assertTrue(relayHost !in parsed.addrs)
    }

    /** spa-v3 §5: the subscription's spa.versions, [2] without the field (a node before v0.35.0). */
    @Test
    fun theSpaVersions() {
        assertEquals(listOf(2), RaySubscription.parse(withAuto(payload), emptyMap(), payload.get("time").asLong).spaVersions)
        val v3 = withAuto(payload).apply {
            (get("spa") as? JsonObject ?: JsonObject().also { add("spa", it) })
                .add("versions", com.google.gson.JsonArray().apply { add(2); add(3) })
        }
        assertEquals(listOf(2, 3), RaySubscription.parse(v3, emptyMap(), payload.get("time").asLong).spaVersions)
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

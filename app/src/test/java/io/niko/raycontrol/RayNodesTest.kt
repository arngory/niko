package io.niko.raycontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RayNodesTest {

    private val spa = RaySpaParams(
        nodeId = "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf", epochLenS = 600, slotLenS = 30,
        portKey = "11".repeat(32), portBase = 30000, portRange = 2000, lenMin = 96, lenMax = 160,
    )
    private val relay = RayRelay("r1", "198.51.100.20", spa.copy(nodeId = "b0b1b2b3b4b5b6b7b8b9babbbcbdbebf", portBase = 20000))
    private val node = RayNode(
        host = "203.0.113.2", spa = spa, deviceId = "dev-1", deviceKeyWrapped = "w",
        services = listOf(RayService(3, "reality", "reality")), spaCarriers = listOf("udp", "tls"),
        failedFetches = 2, failedNetwork = "wifi:abc", relays = listOf(relay), subUpdatedAt = 1_700_000_000,
        subExpires = 1_700_259_200, note = "До 100 ГБ в месяц 🙂",
        traffic = RayTraffic(used = 12_884_901_888, limit = 53_687_091_200, since = 1_767_225_600, reset = 1_769_904_000, exhausted = false),
        pastNames = mapOf("n4k2.example.com" to 1_700_000_000, "203.0.113.2" to 1_700_000_000),
    )

    // a bot's package (integrations-v1) is not kept, by the role it names or, from a node before roles were named,
    // by the subscription it lacks where the node's kept package has one: it would replace this device's own
    @Test
    fun keepsOnlyAnAppsPackage() {
        val sub = RaySubscriptionInfo(443, "11".repeat(32), "22".repeat(32), "33".repeat(32))
        val app = RayRegistration(node.host, spa, "0123456789abcdef", "44".repeat(32), node.services, sub)
        RayNodes.checkForApp(app, null)
        RayNodes.checkForApp(app, node.copy(subscription = sub))
        RayNodes.checkForApp(app.copy(role = "admin"), node.copy(subscription = sub))
        for (role in listOf("billing", "support")) {
            val refused = runCatching { RayNodes.checkForApp(app.copy(role = role), null) }.exceptionOrNull()
            assertTrue(refused is RayApi.IntegrationRole && refused.role == role)
        }
        val bare = app.copy(subscription = null)
        RayNodes.checkForApp(bare, null)
        RayNodes.checkForApp(bare, node.copy(subscription = null))
        assertTrue(runCatching { RayNodes.checkForApp(bare, node.copy(subscription = sub)) }.exceptionOrNull() is RayApi.NoSubscription)
    }

    // a fake QUIC Initial must not name anything the node's front serves on UDP 443: the names of every line the
    // subscription carries, a rotation's next name with the current one, whichever of the two the profile holds
    @Test
    fun namesOfEveryLineCurrentAndNext() {
        val line = "naive+quic://niko:f3b1c0de-0000-4000-8000-000000000001@203.0.113.2:443?niko-node=00112233445566778899aabbccddeeff"
        val rotating = node.copy(links = mapOf(
            "5" to RayLinks("naive", "$line&sni=n4k2.example.com", next = "$line&sni=Q7R1.example.com", from = 1_700_100_000),
            "6" to RayLinks("hy2", "hysteria2://pw@203.0.113.2:443/?sni=masq.example.net&insecure=0"),
            "7" to RayLinks("broken", "naive+quic://"),
        ))
        val names = RayNodes.linkNames(rotating.links)
        assertTrue(names.containsAll(listOf("n4k2.example.com", "q7r1.example.com", "masq.example.net", "203.0.113.2")))
        // the same once the row has switched to its next line
        assertEquals(names, RayNodes.linkNames(rotating.links.mapValues { it.value.copy(nextActive = true) }))
        assertTrue(RayNodes.linkNames(node.links).isEmpty())
    }

    // a name the subscription dropped is still served by the front for a while: remembered from the last time a
    // subscription carried it, forgotten 180 days after, the newest 64 at most
    @Test
    fun namesRememberedPastTheSubscription() {
        val line = "naive+quic://niko:f3b1c0de-0000-4000-8000-000000000001@203.0.113.2:443?niko-node=00112233445566778899aabbccddeeff"
        val day = 24 * 3600L
        val t0 = 1_800_000_000L
        val first = RayNodes.rememberNames(emptyMap(), mapOf("5" to RayLinks("naive", "$line&sni=a.example.com")), t0)
        assertEquals(mapOf("a.example.com" to t0, "203.0.113.2" to t0), first)
        // the next subscription carries another name: the first stays, with the time it was last seen
        val second = RayNodes.rememberNames(first, mapOf("5" to RayLinks("naive", "$line&sni=b.example.com")), t0 + day)
        assertEquals(t0, second["a.example.com"])
        assertEquals(t0 + day, second["b.example.com"])
        assertEquals(t0 + day, second["203.0.113.2"])
        // 180 days after it was last seen it goes
        val later = RayNodes.rememberNames(second, emptyMap(), t0 + 181 * day)
        assertEquals(setOf("b.example.com", "203.0.113.2"), later.keys)
        assertTrue(RayNodes.rememberNames(later, emptyMap(), t0 + 182 * day).isEmpty())
        // a node whose name changes every hour: the current names and the 64 previous ones seen last
        var many = emptyMap<String, Long>()
        for (hour in 0 until 100) {
            many = RayNodes.rememberNames(many, mapOf("5" to RayLinks("naive", "$line&sni=h$hour.example.com")), t0 + hour * 3600L)
        }
        assertEquals(66, many.size)
        assertTrue("h99.example.com" in many && "203.0.113.2" in many && "h35.example.com" in many && "h34.example.com" !in many)
        // a node with more current names than the bound keeps every one of them and the name it dropped as well
        val rows = (0 until 40).associate { "$it" to RayLinks("naive", "$line&sni=r$it.example.com", next = "$line&sni=n$it.example.com") }
        val before = RayNodes.rememberNames(emptyMap(), rows + ("x" to RayLinks("naive", "$line&sni=old.example.com")), t0)
        val after = RayNodes.rememberNames(before, rows + ("x" to RayLinks("naive", "$line&sni=new.example.com")), t0 + day)
        assertTrue("old.example.com" in after && "new.example.com" in after && "r39.example.com" in after && "n0.example.com" in after)
    }

    @Test
    fun jsonRoundTrip() {
        // every field the store keeps comes back, relays and the fetch backoff included
        assertEquals(node, RayNode.fromJson(node.toJson()))
        val bare = node.copy(relays = emptyList(), failedFetches = 0, failedNetwork = null, spaCarriers = null, note = null, traffic = null)
        assertEquals(bare, RayNode.fromJson(bare.toJson()))
        assertFalse(bare.toJson().has("relays"))
        assertFalse(bare.toJson().has("note"))
        assertFalse(bare.toJson().has("client"))
        val ahead = node.copy(client = RayClientMin(mapOf("niko" to "1.59.0"), from = 1_768_435_200, refused = false))
        assertEquals(ahead, RayNode.fromJson(ahead.toJson()))
        val refused = node.copy(client = RayClientMin(mapOf("niko" to "1.59.0", "rayctl" to "0.32.0"), from = 0, refused = true))
        assertEquals(refused, RayNode.fromJson(refused.toJson()))
    }

    @Test
    fun knockAtTheNodeAndAtARelay() {
        @Suppress("UNCHECKED_CAST")
        val direct = node.knockOptions()["spa_v2"] as Map<String, Any>
        assertEquals(spa.nodeId, direct["node_id"])
        assertEquals(30000, direct["port_base"])
        assertEquals(listOf("udp", "tls"), direct["carriers"])
        assertEquals(node.keyRef, direct["device_key_ref"])
        assertNull(direct["relay"])

        // at the relay: its SPA, the same key reference, K_relay named by the node and the relay
        @Suppress("UNCHECKED_CAST")
        val via = node.knockOptions(relay)["spa_v2"] as Map<String, Any>
        assertEquals(relay.spa.nodeId, via["node_id"])
        assertEquals(20000, via["port_base"])
        assertEquals(node.keyRef, via["device_key_ref"])
        assertEquals(mapOf("exit_node_id" to spa.nodeId, "id" to "r1"), via["relay"])
        // the node's carriers are its own, not the relay's
        assertNull(via["carriers"])
        // no versions: v2, as before raycontrol v0.35.0
        assertNull(direct["versions"])
        assertNull(via["versions"])
    }

    /**
     * spa-v3 §5: the versions the node takes go to the knock and to libcore's requests; the last subscription's word
     * over the device package's, the relay's own for a relay.
     */
    @Test
    fun spaVersions() {
        val v3 = listOf(2, 3)
        val fromPackage = node.copy(spa = spa.copy(versions = v3))
        @Suppress("UNCHECKED_CAST")
        fun versions(n: RayNode, r: RayRelay? = null) = (n.knockOptions(r)["spa_v2"] as Map<String, Any>)["versions"]
        assertEquals(v3, versions(fromPackage))
        assertEquals("[2,3]", fromPackage.spaJson().get("versions").toString())
        // a subscription without the field (a node rolled back): v2 again
        val rolledBack = fromPackage.copy(spaVersions = listOf(2))
        assertNull(versions(rolledBack))
        assertNull(rolledBack.spaJson().get("versions"))
        assertEquals(node.copy(spaVersions = v3), RayNode.fromJson(node.copy(spaVersions = v3).toJson()))
        // a relay by its own word: none until it says so
        assertNull(versions(fromPackage, relay))
        val relayV3 = relay.copy(spa = relay.spa.copy(versions = v3))
        assertEquals(v3, versions(node, relayV3))
        assertEquals(relayV3, RayRelay.fromJson(relayV3.toJson()))
        assertEquals("[2,3]", relayV3.spaJson(node, "00".repeat(32)).get("versions").toString())
        // an unknown version is left out, 2 is always there
        assertEquals(listOf(2, 3), spaVersions(com.google.gson.JsonParser.parseString("""{"versions":[3,4,"x"]}""").asJsonObject))
        assertEquals(listOf(2), spaVersions(null))
    }

    @Test
    fun relayAddresses() {
        assertEquals(relay, node.relayAt("198.51.100.20"))
        assertEquals(relay, node.relayAt(" [198.51.100.20] "))
        assertNull(node.relayAt("203.0.113.2"))
        assertNull(node.relayAt(null))
        assertTrue(node.matchesHost("[203.0.113.2]"))
        assertFalse(node.matchesHost("198.51.100.20"))
    }

    @Test
    fun theNodesNameAsItIsShown() {
        assertEquals("Амстердам 2", RayNode.nodeName("  Амстердам \t 2  "))
        // a right-to-left override, a zero-width joiner, a line break and a control character go
        assertEquals("node evil", RayNode.nodeName("node\u202E \u200Devil\n\u0007"))
        assertEquals("a b", RayNode.nodeName("a\nb"))
        assertNull(RayNode.nodeName("\u202E\u0000 "))
        assertNull(RayNode.nodeName(null))
        // at most 48 characters, emoji counted as one each
        assertEquals("🙂".repeat(48), RayNode.nodeName("🙂".repeat(60)))
        assertEquals("x".repeat(48), RayNode.nodeName("x".repeat(100)))
        // the group: the name, or the address as before
        assertEquals("Амстердам", RayNode.groupName("203.0.113.2", "Амстердам"))
        assertEquals("raycontrol · 203.0.113.2", RayNode.groupName("203.0.113.2", null))
    }

    @Test
    fun theNodesNoteAsItIsShown() {
        assertNull(RayNode.nodeNote(null))
        assertNull(RayNode.nodeNote(" \u202E\u200D\n"))
        // one line: line breaks, tabs and the line separator become one space
        assertEquals("Лимит 100 ГБ. Правила: без торрентов", RayNode.nodeNote(" Лимит 100 ГБ.\n\tПравила:\u2028без торрентов "))
        // the joiners of emoji and of Persian stay; a family, a heart on fire, a keycap, a skin tone
        for (text in listOf("👨\u200D👩\u200D👧", "❤\uFE0F\u200D🔥", "1\uFE0F\u20E3", "👍🏽", "می\u200Cخواهم", "🇷🇺")) {
            assertEquals(text, RayNode.nodeNote(text))
        }
        // a joiner not between two characters goes: at the start, before a space, at the end, a second one
        assertEquals("a b", RayNode.nodeNote("\u200Da\u200D b\u200C"))
        assertEquals("a\u200Db", RayNode.nodeNote("a\u200D\u200Db"))
        // the flag of England: its tags up to U+E007F; tags without their end or without the flag go
        val england = "🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F"
        assertEquals("$england!", RayNode.nodeNote("$england!"))
        assertEquals("🏴x", RayNode.nodeNote("🏴\uDB40\uDC67\uDB40\uDC62x"))
        assertEquals("ax", RayNode.nodeNote("a\uDB40\uDC67\uDB40\uDC7Fx"))
        // bidi overrides and isolates, zero-width space, BOM, private use go: the card puts the note in its own isolate
        assertEquals("evil", RayNode.nodeNote("\u202Eev\u2066il\u200B\uFEFF\uE000"))
        // a mark only on a character, at most four on one
        assertEquals("a b\u0301", RayNode.nodeNote("\u0301a \u0301b\u0301"))
        assertEquals("e" + "\u0301".repeat(4) + "x", RayNode.nodeNote("e" + "\u0301".repeat(9) + "x"))
        assertEquals("Tiếng Việt", RayNode.nodeNote("Tiếng Việt"))
        // 200 characters whole; more end in "…", never cut without a sign
        assertEquals("x".repeat(200), RayNode.nodeNote("x".repeat(200)))
        assertEquals("x".repeat(200), RayNode.nodeNote("x".repeat(200) + " \u200B"))
        assertEquals("x".repeat(200) + "…", RayNode.nodeNote("x".repeat(201)))
        assertEquals("🙂".repeat(200) + "…", RayNode.nodeNote("🙂".repeat(250)))
        // a cut never leaves half a character: a flag without its tags or its other letter, a joined pair without
        // its second, a letter without its mark; a space before it goes too
        val englandFlag = "🏴\uDB40\uDC67\uDB40\uDC62\uDB40\uDC65\uDB40\uDC6E\uDB40\uDC67\uDB40\uDC7F"
        for (tail in listOf(englandFlag, "👨\u200D👩", "🇷🇺", "e\u0301", " y")) {
            assertEquals(tail, "x".repeat(199) + "…", RayNode.nodeNote("x".repeat(199) + tail + "z"))
        }
        assertEquals("x".repeat(198) + "…", RayNode.nodeNote("x".repeat(198) + "👨\u200D👩"))
        assertEquals("x".repeat(198) + "🇷🇺…", RayNode.nodeNote("x".repeat(198) + "🇷🇺🇺🇦"))
        // the whole note, uncut, for the admin's own check of its length
        assertEquals("x".repeat(250), RayNode.nodeNote("x".repeat(250), Int.MAX_VALUE))
        // emoji newer than the device's tables stay (U+1FAFF unassigned yet); other unassigned characters go
        assertEquals("a\uD83E\uDEFF", RayNode.nodeNote("a\uD83E\uDEFF"))
        assertEquals("ab", RayNode.nodeNote("a\u0378b"))
        assertEquals("a\uD83E\uDEFF", RayNode.nodeName("a\uD83E\uDEFF"))
        // the card: in an isolate, none without a note
        assertEquals("\u2068До 100 ГБ в месяц 🙂\u2069", RayNodeCard.note(node))
        assertNull(RayNodeCard.note(node.copy(note = null)))
    }

    // client-min-v1 §3: the node's minimum by product, from when, and the refusal of this device
    @Test
    fun theNodesMinimumVersion() {
        fun parse(json: String) = RayClientMin.fromJson(com.google.gson.JsonParser.parseString(json).asJsonObject)
        val min = parse("""{"v": 1, "min": {"niko": "1.59.0"}, "from": 1768435200}""")!!
        assertEquals("1.59.0", min.niko)
        assertEquals(1_768_435_200L, min.from)
        assertFalse(min.refused)
        assertEquals(min, RayClientMin.fromJson(min.toJson()))
        assertTrue(parse("""{"v": 1, "min": {"niko": "1.59.0"}, "refused": true}""")!!.refused)
        // another version is unknown; a product or version off the node's grammar and limits is left out
        assertNull(parse("""{"v": 2, "min": {"niko": "1.59.0"}}"""))
        assertNull(RayClientMin.fromJson(null))
        val odd = parse("""{"v": 1, "min": {"Niko": "1.0.0", "niko": "${"9".repeat(65)}", "x": 5, "ok": "1.0.0"}, "from": -1}""")!!
        assertEquals(mapOf("ok" to "1.0.0"), odd.min)
        assertNull(odd.niko)
        assertEquals(0L, odd.from)
        val many = (0 until 12).joinToString(",") { "\"p$it\": \"1.0.$it\"" }
        assertEquals(8, parse("""{"v": 1, "min": {$many}}""")!!.min.size)
    }

    @Test
    fun theUsersTraffic() {
        fun parse(json: String) = RayTraffic.fromJson(com.google.gson.JsonParser.parseString(json).asJsonObject)
        val traffic = parse("""{"v": 1, "used": 12884901888, "limit": 53687091200, "since": 1767225600, "reset": 1769904000}""")!!
        assertFalse(traffic.exhausted)
        assertFalse(traffic.nearLimit)
        assertEquals(traffic, RayTraffic.fromJson(traffic.toJson()))
        assertTrue(parse("""{"v": 1, "used": 49000000000, "limit": 50000000000, "since": 1, "reset": 0}""")!!.nearLimit)
        // used up is the node's word, and then there is nothing "near" about it
        val exhausted = parse("""{"v": 1, "used": 1, "limit": 50, "since": 1, "reset": 0, "exhausted": true}""")!!
        assertTrue(exhausted.exhausted)
        assertFalse(exhausted.nearLimit)
        assertEquals(exhausted, RayTraffic.fromJson(exhausted.toJson()))
        // another version is unknown; nonsense numbers read as none
        assertNull(parse("""{"v": 2, "used": 1, "limit": 2}"""))
        assertNull(RayTraffic.fromJson(null))
        assertEquals(0L, parse("""{"v": 1, "used": -5, "limit": "x"}""")!!.used)
        // the card: its fact, after a failing fetch, before the rest
        val facts = RayNodeCard.facts(node.copy(traffic = exhausted, relays = emptyList()), 1_700_000_000, null, null)
        assertEquals(listOf(RayNodeCard.Fact.Failing(2), RayNodeCard.Fact.Traffic(exhausted)), facts.take(2))
        assertEquals(RayNodeCard.Fact.Traffic(exhausted), RayNodeCard.facts(node.copy(traffic = exhausted, failedFetches = 0), 1_700_000_000, null, null).first())
    }
}

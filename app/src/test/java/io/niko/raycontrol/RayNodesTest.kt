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
    )

    @Test
    fun jsonRoundTrip() {
        // every field the store keeps comes back, relays and the fetch backoff included
        assertEquals(node, RayNode.fromJson(node.toJson()))
        val bare = node.copy(relays = emptyList(), failedFetches = 0, failedNetwork = null, spaCarriers = null, note = null, traffic = null)
        assertEquals(bare, RayNode.fromJson(bare.toJson()))
        assertFalse(bare.toJson().has("relays"))
        assertFalse(bare.toJson().has("note"))
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

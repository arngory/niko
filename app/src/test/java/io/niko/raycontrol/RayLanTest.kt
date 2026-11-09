package io.niko.raycontrol

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RayLanTest {

    private val spa = RaySpaParams(
        nodeId = "a0a1a2a3a4a5a6a7a8a9aaabacadaeaf", epochLenS = 600, slotLenS = 30,
        portKey = "11".repeat(32), portBase = 30000, portRange = 2000, lenMin = 96, lenMax = 160,
    )

    // lan-v1 §1.2 as raycontrol v0.23.0 sends it, this device (0123456789abcdef) and two others
    private val payload = """
        {"v": 1, "svc": 7, "relay": 20, "label": "ams",
         "password": "${"A".repeat(43)}", "next": {"password": "${"B".repeat(43)}", "from": 1767268800},
         "devices": [
           {"id": "0123456789abcdef", "name": "phone", "label": "phone", "addr": "201:1111::1"},
           {"id": "1111222233334444", "name": "work.laptop", "label": "work-laptop", "addr": "201:2222::2",
            "next": {"addr": "203:2222::3", "from": 1767268800}},
           {"id": "5555666677778888", "name": "nas", "label": "nas", "addr": "202:5555::5"}
         ]}
    """.trimIndent()

    private val lan = RayLan.fromJson(JsonParser.parseString(payload).asJsonObject)!!

    private fun node(
        lan: RayLan? = this.lan, show: Boolean? = true, nodeId: String = spa.nodeId, previous: List<RayLanPrevious>? = null,
    ) = RayNode(
        host = "203.0.113.2", spa = spa.copy(nodeId = nodeId), deviceId = "0123456789ABCDEF", deviceKeyWrapped = "w",
        services = emptyList(), subscription = RaySubscriptionInfo(port = 8443, pin = "p", ed25519 = "e", mldsa65 = "m"),
        lanShow = show, lanShowAt = 1_700_000_000, lan = lan, lanPrevious = previous,
    )

    @Test
    fun parse() {
        assertEquals(7, lan.svc)
        assertEquals(20, lan.relayMbps)
        assertEquals("ams", lan.label)
        assertTrue(lan.shown)
        assertEquals(1767268800, lan.nextFrom)
        assertEquals(listOf("phone", "work-laptop", "nas"), lan.devices.map { it.label })
        // this device is found by its X-Raycontrol-Device id, whatever its case
        assertEquals(listOf("1111222233334444", "5555666677778888"), lan.others("0123456789ABCDEF").map { it.id })
        // the round trip through the node store
        assertEquals(lan, RayLan.fromJson(lan.toJson()))
        assertEquals(node(previous = listOf(RayLanPrevious("1111222233334444", "201:2222::1", 5))), RayNode.fromJson(node(previous = listOf(RayLanPrevious("1111222233334444", "201:2222::1", 5))).toJson()))
    }

    @Test
    fun unknownOrBrokenSkipped() {
        fun of(json: String) = RayLan.fromJson(JsonParser.parseString(json).asJsonObject)
        // another version is skipped whole (§1.2)
        assertNull(of("""{"v": 2, "svc": 7, "label": "ams"}"""))
        assertNull(of("""{"v": 1, "label": "ams"}"""))
        // a node label that is no DNS label: no names to give
        assertNull(of("""{"v": 1, "svc": 7, "label": "Ams"}"""))
        assertNull(of("""{"v": 1, "svc": 7, "label": "xn--a-"}"""))
        // allowed but not shown: no password, no devices
        val hidden = of("""{"v": 1, "svc": 7, "relay": 0, "label": "ams"}""")!!
        assertFalse(hidden.shown)
        assertEquals(0, hidden.relayMbps)
        // a device with an address outside 200::/7, a bad id or a broken label: dropped or relabelled, the rest stays
        val mixed = of("""{"v": 1, "svc": 7, "label": "ams", "password": "${"A".repeat(43)}", "devices": [
            {"id": "1111222233334444", "name": "a", "label": "a", "addr": "2001:db8::1"},
            {"id": "xyz", "name": "b", "label": "b", "addr": "201::2"},
            {"id": "5555666677778888", "name": "c", "label": "-c-", "addr": "201::3"},
            {"id": "9999aaaabbbbcccc", "name": "d", "label": "d", "addr": "201::4/128"},
            {"id": "ddddeeeeffff0000", "name": "e", "label": "e", "addr": "[201::5]"}]}""")!!
        assertEquals(listOf("5555666677778888"), mixed.devices.map { it.id })
        assertEquals("d-55556666", mixed.devices.single().label)
        assertEquals("201::3", mixed.devices.single().addr)
    }

    @Test
    fun onlyWhenShownAndListed() {
        assertEquals(lan, RayLanNet.of(node()))
        // the user hid it, or never chose: nothing of the LAN in the config
        assertNull(RayLanNet.of(node(show = false)))
        assertNull(RayLanNet.of(node(show = null)))
        // allowed, not shown yet (no password): neither
        assertNull(RayLanNet.of(node(lan = lan.copy(password = null, devices = emptyList()))))
        assertTrue(RayLanNet.neighbours(node(show = false), 0).isEmpty())
    }

    @Test
    fun neighboursAndNames() {
        val now = 1_767_000_000L
        // the others' current and next addresses, never this device's own
        assertEquals(listOf("201:2222::2", "203:2222::3", "202:5555::5"), RayLanNet.neighbours(node(), now))
        // names answer where each device is at the node's time: the next address from its `from` on
        assertEquals(
            mapOf("work-laptop.ams.niko.internal" to "201:2222::2", "nas.ams.niko.internal" to "202:5555::5"),
            RayLanNet.names(listOf(node()), now),
        )
        assertEquals("203:2222::3", RayLanNet.names(listOf(node()), 1_767_268_800)["work-laptop.ams.niko.internal"])
        assertEquals(listOf("ams.niko.internal"), RayLanNet.suffixes(RayLanNet.names(listOf(node()), now)))
    }

    @Test
    fun twoNodesOneLabel() {
        val other = node(nodeId = "f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff")
        val names = RayLanNet.names(listOf(other, node()), 0)
        // the node with the larger id takes -<node_id[:4]> (§1.3); the other keeps its label
        assertTrue("nas.ams.niko.internal" in names)
        assertTrue("nas.ams-f0f1.niko.internal" in names)
        assertEquals(4, names.size)
    }

    @Test
    fun previousAddresses() {
        val now = 1_767_300_000L
        // the node rotated work-laptop: its old address is still taken two days on; nas left the list, its address
        // goes at once (hidden or revoked)
        val rotated = lan.copy(devices = listOf(
            lan.devices[0],
            lan.devices[1].copy(addr = "203:2222::3", nextAddr = null, nextFrom = 0),
        ))
        val previous = RayLanNet.previous(node(), rotated, now)
        assertEquals(listOf(RayLanPrevious("1111222233334444", "201:2222::2", now + RayLanNet.PREVIOUS_FOR)), previous)
        val after = node(lan = rotated, previous = previous)
        assertEquals(listOf("203:2222::3", "201:2222::2"), RayLanNet.neighbours(after, now))
        assertEquals(listOf("203:2222::3"), RayLanNet.neighbours(after, now + RayLanNet.PREVIOUS_FOR + 1))
        // the device hidden later: its kept address goes with it
        assertTrue(RayLanNet.previous(after, rotated.copy(devices = listOf(lan.devices[0])), now).isEmpty())
        // no `lan` any more (turned off, over the limit): nothing kept
        assertTrue(RayLanNet.previous(after, null, now).isEmpty())
    }

    @Test
    fun refreshBy() {
        val now = 1_700_000_030L
        // an `on` the node gave no password for: again in a minute, for ten minutes after the switch
        val waiting = lan.copy(password = null, devices = emptyList())
        assertEquals(now + RayLanNet.RETRY_ON, RayLanNet.refreshBy(node(lan = waiting), waiting, now))
        assertEquals(now + 11 * 60 + RayLanNet.REFRESH_AT_MOST, RayLanNet.refreshBy(node(lan = waiting), waiting, now + 11 * 60))
        // shown: at least every 6 h
        assertEquals(now + RayLanNet.REFRESH_AT_MOST, RayLanNet.refreshBy(node(), lan, now))
        // hidden or not offered: the plan alone
        assertEquals(Long.MAX_VALUE, RayLanNet.refreshBy(node(show = false), lan, now))
        assertEquals(Long.MAX_VALUE, RayLanNet.refreshBy(node(), null, now))
    }
}

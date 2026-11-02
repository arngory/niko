package io.niko.raycontrol

import com.google.gson.JsonParser
import io.niko.fmt.DnsTunnel
import io.niko.fmt.applyNikoParams
import io.niko.fmt.v2ray.VMessBean
import io.niko.fmt.v2ray.parseDuckSoft
import okhttp3.HttpUrl.Companion.toHttpUrl
import okio.ByteString.Companion.encodeUtf8
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RaySubscriptionTest {

    private val base = """"v": 1, "host": "203.0.113.7",
        "spa": {"node_id": "33333333333333333333333333333333", "epoch_len_s": 86400, "slot_len_s": 600,
                "port_key": "4444444444444444444444444444444444444444444444444444444444444444",
                "port_base": 40000, "port_range": 4096, "len_min": 96, "len_max": 160},
        "device_id": "0123456789abcdef",
        "device_key": "1111111111111111111111111111111111111111111111111111111111111111", "services": []"""
    private val pin = "A".repeat(43)
    private val mldsa = "B".repeat(2603)
    private val withSub = """{$base, "sub": {"port": 8443, "pin": "$pin"},
        "node_keys": {"ed25519": "${"cd".repeat(32)}", "mldsa65": "$mldsa"}}"""

    private fun pack(json: String) = Rcdev1.PREFIX + json.encodeUtf8().base64Url().trimEnd('=')

    @Test
    fun registrationWithSubscription() {
        val sub = Rcdev1.parse(pack(withSub)).subscription!!
        assertEquals(8443, sub.port)
        assertEquals(pin, sub.pin)
        assertEquals("cd".repeat(32), sub.ed25519)
        assertNull(Rcdev1.parse(pack("{$base}")).subscription)
        for (bad in listOf(
            withSub.replace(""""node_keys": {""", """"other": {"""),
            withSub.replace("\"port\": 8443", "\"port\": 0"),
            withSub.replace(pin, "short"),
            withSub.replace(mldsa, "B".repeat(10)),
        )) {
            try {
                Rcdev1.parse(pack(bad))
                fail("accepted: $bad")
            } catch (_: IllegalArgumentException) {
            }
        }
        // the node store keeps the subscription, seq and group
        val r = Rcdev1.parse(pack(withSub))
        val node = RayNode(r.host, r.spa, r.deviceId, "w", r.services, r.subscription, subSeq = 42, groupId = 7)
        assertEquals(node, RayNode.fromJson(JsonParser.parseString(node.toJson().toString()).asJsonObject))
    }

    // the DNS tunnel link raycontrol renders (internal/sub Profiles), with the resolvers given
    @Test
    fun dnsTunnelLinks() {
        val pub = "ab".repeat(32)
        val dnstt = VMessBean().apply { alterId = -1; initializeDefaultValues() }
        dnstt.parseDuckSoft("https://uuid@inner:1?type=raw&encryption=mlkem768x25519plus.native.0rtt.KEY&niko-node=33&niko-svc=3&niko-dns=dnstt%3Bhttps://dns.example/dns-query%3Bt.example.com%3B$pub".toHttpUrl())
        assertEquals("tcp", dnstt.type)
        assertEquals("mlkem768x25519plus.native.0rtt.KEY", dnstt.vlessEncryption)
        assertEquals(DnsTunnel.DNSTT, dnstt.dnsTunnel)
        assertEquals("https://dns.example/dns-query", dnstt.dnsTunnelResolver)
        assertEquals("t.example.com", dnstt.dnsTunnelDomain)
        assertEquals(pub, dnstt.dnsTunnelKey)
        assertNull(DnsTunnel.of(dnstt).validate())

        val mdns = VMessBean().apply { alterId = -1; initializeDefaultValues() }
        mdns.parseDuckSoft("https://uuid@inner:1?type=raw&niko-dns=masterdns%3B8.8.8.8,1.1.1.1%3Bv.example.com%3B5&niko-dnskey=0123456789abcdef0123456789abcdef".toHttpUrl())
        assertEquals(DnsTunnel.MASTERDNSVPN, mdns.dnsTunnel)
        assertEquals("8.8.8.8\n1.1.1.1", mdns.dnsTunnelResolver)
        assertEquals(5, mdns.dnsTunnelMethod)
        assertTrue(mdns.dnsTunnelFixedUpstream)
        assertEquals("0123456789abcdef0123456789abcdef", mdns.dnsTunnelKey)
        assertNull(DnsTunnel.of(mdns).validate())

        val pinned = VMessBean().apply { alterId = -1; initializeDefaultValues() }
        pinned.applyNikoParams { if (it == "niko-pin") pin else null }
        assertEquals(pin, pinned.certPins)
    }
}

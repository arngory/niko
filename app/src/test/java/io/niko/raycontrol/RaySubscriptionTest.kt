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
                fail("accepted a bad package")
            } catch (_: IllegalArgumentException) {
            }
        }
        // the node store keeps the subscription, seq and group
        val r = Rcdev1.parse(pack(withSub))
        val node = RayNode(r.host, r.spa, r.deviceId, "w", r.services, r.subscription, subSeq = 42, groupId = 7,
            profileIds = mapOf("3" to 11L, "3#2" to 12L), refreshAt = 1767268800, clockSkew = -42,
            links = mapOf("3" to RayLinks("dns", "vless://a", "vless://b", 1767268800, nextActive = true), "3#2" to RayLinks("x", "vless://c")))
        assertEquals(node, RayNode.fromJson(JsonParser.parseString(node.toJson().toString()).asJsonObject))
        // a 1.13.0 record has no profile_ids (matched by name once); an empty map stays empty
        val legacy = node.toJson().apply { remove("profile_ids") }
        assertNull(RayNode.fromJson(legacy).profileIds)
        val empty = node.copy(profileIds = emptyMap())
        assertEquals(emptyMap<String, Long>(), RayNode.fromJson(JsonParser.parseString(empty.toJson().toString()).asJsonObject).profileIds)
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

    private fun obj(json: String) = JsonParser.parseString(json).asJsonObject

    // rows are matched by service: a renamed profile keeps its row, a service with two profiles gets two keys
    @Test
    fun profileKeys() {
        val profiles = listOf(obj("""{"svc": 3}"""), obj("""{"svc": 5}"""), obj("""{"svc": 3}"""), obj("""{}"""))
        assertEquals(listOf("3", "5", "3#2", "-"), RaySubscription.profileKeys(profiles))
    }

    @Test
    fun rotation() {
        val p = obj("""{"name": "r", "link": "a", "next": {"link": "b", "from": 1000}}""")
        assertEquals("a", RaySubscription.currentLink(p, 999))
        assertEquals("b", RaySubscription.currentLink(p, 1000))
        // both kept: the fallback switches between them
        assertEquals(RayLinks("r", "a", "b", 1000, nextActive = false), RaySubscription.linksOf(p, 999))
        assertEquals("a", RaySubscription.linksOf(p, 1000).copy(nextActive = false).activeLink)
        assertEquals("a", RaySubscription.currentLink(obj("""{"link": "a"}"""), 5000))

        // the next fetch: the rotation ahead, else 3/4 of the life, never sooner than a minute
        val payload = obj("""{"issued": 0, "expires": 4000, "profiles": [
            {"link": "a", "next": {"link": "b", "from": 1000}}, {"link": "c", "next": {"link": "d", "from": 2500}}]}""")
        assertEquals(1000, RaySubscription.nextRefresh(payload, 100))
        assertEquals(2500, RaySubscription.nextRefresh(payload, 1500))
        assertEquals(3000, RaySubscription.nextRefresh(payload, 2600))
        assertEquals(3660, RaySubscription.nextRefresh(payload, 3600))
    }

    // R3: rotating UDP ports of a service (spa-v2.md §11), kept in the node
    @Test
    fun servicePortSchedules() {
        val services = """"services": [{"id": 4, "kind": "hy2-masq", "name": "hy2", "port_key": "${"55".repeat(32)}",
            "port_base": 50000, "port_range": 2000}, {"id": 1, "kind": "reality", "name": "reality"}]"""
        val r = Rcdev1.parse(pack("{" + base.replace(""""services": []""", services) + "}"))
        assertEquals(RayPortSchedule("55".repeat(32), 50000, 2000), r.services[0].ports)
        assertNull(r.services[1].ports)
        val node = RayNode(r.host, r.spa, r.deviceId, "w", r.services, doh = "https://d.example/dns-query/tok")
        assertEquals(node, RayNode.fromJson(JsonParser.parseString(node.toJson().toString()).asJsonObject))
        for (bad in listOf(
            services.replace(""""port_base": 50000, """, ""),
            services.replace("2000", "20000"),
            services.replace("55".repeat(32), "55"),
        )) {
            try {
                Rcdev1.parse(pack("{" + base.replace(""""services": []""", bad) + "}"))
                fail("accepted a bad port schedule")
            } catch (_: IllegalArgumentException) {
            }
        }
    }

    // R3 links (subscription-v1.md §4): DoH is not a proxy; hy2-obfs pins the key by niko-pin
    @Test
    fun r3Links() {
        val doh = "https://abc.node.example/dns-query/TOKEN?niko-node=33&niko-svc=5#doh"
        assertTrue(RaySubscription.isDoh(doh, null))
        assertTrue(RaySubscription.isDoh("https://x.example/q", "doh"))
        assertTrue(!RaySubscription.isDoh("naive+https://niko:pw@n.example:443?niko-svc=6#naive", "naive"))
        assertEquals("https://abc.node.example/dns-query/TOKEN", RaySubscription.dohUrl(doh))

        val spki = "q".repeat(43)
        val hy2 = io.niko.fmt.hysteria.parseHysteria2(
            "hysteria2://pw@203.0.113.7:47000/?sni=0011aabb.invalid&insecure=0&obfs=salamander&obfs-password=op" +
                "&niko-pin=$spki&pinSHA256=${"ab".repeat(32)}&niko-node=33&niko-svc=4#hy2-obfs"
        )
        assertEquals(spki, hy2.certPins)
        assertEquals("0011aabb.invalid", hy2.sni)
        assertEquals("op", hy2.obfuscation)
        assertTrue(!hy2.allowInsecure)
        // the pin is a SHA-256 of the key: it parses for sing-box
        assertEquals(1, io.niko.fmt.parseCertificatePins(hy2.certPins).size)
    }

    // R3: invites (api-v1.md §3) and the api field of admin packages
    @Test
    fun invitesAndApi() {
        val apiPin = "C".repeat(43)
        val invite = "{" + base.replace(""""services": []""", """"services": [{"id": 7, "kind": "api", "name": "api"}]""") +
            """, "api": {"port": 48213, "pin": "$apiPin"}, "expires": 1767312000}"""
        val text = "Invite for alice/phone:\n" + Rcinv1.PREFIX + invite.encodeUtf8().base64Url().trimEnd('=') + "\nValid for a day.\n"
        assertTrue(Rcinv1.matches(text))
        assertTrue(!Rcdev1.matches(text))
        assertTrue(RayImport.matches(text))
        val parsed = Rcinv1.parse(text)
        assertEquals("0123456789abcdef", parsed.inviteId)
        assertEquals("11".repeat(32), parsed.inviteKey)
        assertEquals(RayApiInfo(48213, apiPin), parsed.api)
        assertEquals(1767312000, parsed.expires)
        // the key stays out of logs
        assertTrue(!parsed.toString().contains("11".repeat(32)))
        for (bad in listOf(invite.replace(""", "api": {"port": 48213, "pin": "$apiPin"}""", ""),
            invite.replace(apiPin, "short"), invite.replace("48213", "0"), invite.replace(""", "expires": 1767312000""", ""))) {
            try {
                Rcinv1.parse(Rcinv1.PREFIX + bad.encodeUtf8().base64Url().trimEnd('='))
                fail("accepted a bad invite")
            } catch (_: IllegalArgumentException) {
            }
        }

        // an admin package: api kept in the node with the client certificate
        val admin = Rcdev1.parse(pack("{$base, \"api\": {\"port\": 48213, \"pin\": \"$apiPin\"}}"))
        assertEquals(RayApiInfo(48213, apiPin), admin.api)
        val identity = RayIdentity("Y2VydA==", "wrapped", "D".repeat(43), 1767225600)
        val node = RayNode(admin.host, admin.spa, admin.deviceId, "w", admin.services, api = admin.api, identity = identity,
            identityNext = identity.copy(pin = "E".repeat(43)))
        assertTrue(node.isAdmin)
        assertEquals(node, RayNode.fromJson(JsonParser.parseString(node.toJson().toString()).asJsonObject))
        assertTrue(!RayNode(admin.host, admin.spa, admin.deviceId, "w", admin.services).isAdmin)
        assertEquals("not_found", RayApi.code(Exception("raycontrol api: not_found")))
        assertEquals("unauthenticated", RayApi.code(Exception("go error: raycontrol api: unauthenticated: device revoked")))
        assertNull(RayApi.code(Exception("raycontrol api: the node did not answer: dial tcp")))
    }
}


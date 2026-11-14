package io.niko.raycontrol

import io.niko.fmt.KryoConverters
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.parseCertificatePins
import io.niko.fmt.v2ray.VMessBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class RayRowsTest {

    private val enc = "mlkem768x25519plus.native.0rtt.Xw7QmfF2Ty6s6LYhyF_2O5ilJSvSrA7Pi0yHrzP2nQ0"
    private val pqv = "A".repeat(2603)
    private val node = "niko-node=33333333333333333333333333333333"
    private val reality = "vless://uuid@203.0.113.7:443?type=raw&security=reality&flow=xtls-rprx-vision&encryption=$enc" +
            "&sni=a.example&fp=chrome&pbk=key&sid=ab&pqv=$pqv&$node&niko-svc=2"

    private fun refused(link: String, what: String) {
        try {
            RayRows.parse(link)
            fail("$what: accepted")
        } catch (e: IllegalArgumentException) {
        }
    }

    @Test
    fun specRows() {
        val r = RayRows.parse(reality) as VMessBean
        assertTrue(r.isVLESS)
        assertEquals(enc, r.vlessEncryption)
        assertEquals(pqv, r.realityMldsa65Verify)
        assertEquals("", r.customConfigJson)

        val dns = RayRows.parse("vless://uuid@inner:1?type=raw&encryption=$enc&$node&niko-svc=3&niko-dns=dnstt%3B1.1.1.1%3Bt.example%3B${"ab".repeat(32)}")
        assertTrue(dns is VMessBean)

        val hy2 = RayRows.parse("hysteria2://pw@203.0.113.7:443/?sni=x.invalid&insecure=0&obfs=salamander&obfs-password=op&niko-pin=${"B".repeat(43)}&$node&niko-svc=5") as HysteriaBean
        assertEquals(2, hy2.protocolVersion)

        val naive = RayRows.parse("naive+https://niko:pw@n.example:443?cert=X&$node&niko-svc=6") as NaiveBean
        assertEquals("", naive.certificates)
        assertEquals("", naive.extraHeaders)
        assertTrue(RayRows.parse("naive+quic://niko:pw@n.example:443?$node") is NaiveBean)
    }

    // cert-pin-v1 §10: the rows as a node writes them, the leaf on the key of vector 1, the next key of vector 2
    @Test
    fun sitePinRows() {
        val pin1 = "8dWUSbcnFl3nMr8oMzgSK5liimFZGP7cZ9h4__z0fac"
        val pin2 = "aKDy1GvP0Ev823jJBZeuwhvSD4gJN1ax7KUDa0YLvnM"
        val spec = "niko-node=00112233445566778899aabbccddeeff&niko-pin=$pin1%2C$pin2"
        // the pins as sing-box takes them: standard base64 of the vectors' SHA-256
        val hashes = listOf(
            "f1d59449b727165de732bf283338122b99628a615918fedc67d878fffcf47da7",
            "68a0f2d46bcfd04bfcdb78c90597aec21bd20f88093756b1eca5036b460bbe73",
        ).map { hex -> java.util.Base64.getEncoder().encodeToString(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()) }

        val naive = RayRows.parse(
            "naive+https://niko:f3b1c0de-0000-4000-8000-000000000001@203.0.113.7:443?$spec&niko-svc=5&sni=n4k2.example.com&uot=2#naive"
        ) as NaiveBean
        assertEquals("$pin1,$pin2", naive.certPins)
        assertEquals("n4k2.example.com", naive.sni)
        assertEquals(hashes, parseCertificatePins(naive.certPins))
        // the same over QUIC: the address and the certificate are the same
        val quic = RayRows.parse(
            "naive+quic://niko:f3b1c0de-0000-4000-8000-000000000001@203.0.113.7:443?$spec&niko-svc=5&sni=n4k2.example.com#naive-quic"
        ) as NaiveBean
        assertEquals("quic", quic.proto)
        assertEquals(naive.certPins, quic.certPins)
        // kept through the store
        assertEquals(naive.certPins, KryoConverters.deserialize(NaiveBean(), KryoConverters.serialize(naive)).certPins)

        val hy2 = RayRows.parse(
            "hysteria2://f3b1c0de-0000-4000-8000-000000000002@203.0.113.7:443/?niko-ack=1&$spec&niko-svc=6&sni=h7q9.example.com#hy2-masq"
        ) as HysteriaBean
        assertEquals(hashes, parseCertificatePins(hy2.certPins))

        // a list is a list: one pin, or three
        val one = RayRows.parse("naive+https://niko:pw@203.0.113.7:443?niko-pin=$pin2&sni=n4k2.example.com") as NaiveBean
        assertEquals(listOf(hashes[1]), parseCertificatePins(one.certPins))
        val three = RayRows.parse("naive+https://niko:pw@n.example:443?niko-pin=$pin2%2C$pin1%2C${"A".repeat(43)}") as NaiveBean
        assertEquals(3, parseCertificatePins(three.certPins).size)
        // no parameter: no pin, the check as before
        assertEquals("", (RayRows.parse("naive+https://niko:pw@n.example:443?$node") as NaiveBean).certPins)

        // a list that does not read is not dropped for a row without pins: no row
        refused("naive+https://niko:pw@n.example:443?niko-pin=$pin1%2Cshort", "naive: a short pin")
        refused("naive+https://niko:pw@n.example:443?niko-pin=${"A".repeat(44)}A", "naive: a long pin")
        refused("naive+https://niko:pw@n.example:443?niko-pin=%2C", "naive: an empty list")
        refused("hysteria2://pw@203.0.113.7:443/?sni=h.example&niko-pin=$pin1%2Cnot-a-pin", "hysteria2: a bad pin")
        // Chromium pins a key to a name: a naive row at an address needs its sni
        refused("naive+https://niko:pw@203.0.113.7:443?niko-pin=$pin1", "naive: a pin without a name")
    }

    @Test
    fun hostsForTheLog() {
        val ygg = "vless://uuid@[200:1::2]:8443?type=raw&encryption=$enc&$node&niko-ygg=" +
                "tls%3A%2F%2Fy.node.example%3A443%3Fkey%3Dab%26sni%3Dy.node.example%3B${"cd".repeat(32)}&niko-ygg-via=tls%3A%2F%2Fpublic.example%3A443"
        val naive = "naive+https://niko:pw@n.node.example:443?$node"
        val selfSteal = reality.replace("sni=a.example", "sni=r1.node.example")
        // REALITY's cover (a.example) is a public site: not the node's, it stays readable
        assertEquals(
            listOf("203.0.113.7", "200:1::2", "y.node.example", "n.node.example", "r1.node.example"),
            RayRows.nodeHosts("203.0.113.7", null, listOf(reality, ygg, naive, selfSteal))
        )
        assertEquals(listOf("203.0.113.7", "d.node.example"),
            RayRows.nodeHosts("203.0.113.7", "https://d.node.example/dns-query/t", listOf("vless://uuid@inner:1?type=raw&encryption=$enc")))
        // known only by its address: its REALITY names are taken as its own (a self-steal name must not show)
        assertEquals(listOf("203.0.113.7", "r1.mynode.net", "a.mynode.net"),
            RayRows.nodeHosts("203.0.113.7", null, listOf(reality.replace("sni=a.example", "sni=r1.mynode.net&tlsServerName=a.mynode.net"))))
        // tokens the generic URL rule misses: letters only, short custom ones, encoded characters as in the log
        assertEquals(listOf("qwhzkrtmbvplxnae"), RayRows.subscriptionSecrets("https://panel.example/sub/qwhzkrtmbvplxnae"))
        assertEquals(listOf("ab12cd34ef"), RayRows.subscriptionSecrets("https://panel.example/sub/ab12cd34ef"))
        assertEquals(listOf("aB3dE5fG7hJ9kL1mN"), RayRows.subscriptionSecrets("https://panel.example/sub/aB3dE5fG7hJ9kL1mN"))
        assertEquals(listOf("a%2Bb%3Dc%2Fd1234"), RayRows.subscriptionSecrets("https://panel.example/s/a%2Bb%3Dc%2Fd1234"))
        // plain words and file names stay readable
        assertEquals(emptyList<String>(), RayRows.subscriptionSecrets("https://p.example/api/v1/client/subscribe?token=x"))
        assertEquals(emptyList<String>(), RayRows.subscriptionSecrets("https://p.example/subscribe"))
        assertEquals(emptyList<String>(), RayRows.subscriptionSecrets("https://raw.example/user/repo/main/config.yaml"))
        assertEquals(emptyList<String>(), RayRows.subscriptionSecrets("https://p.example/sub/v2ray-json"))
        assertEquals(listOf("qwhzkrtmbvplxnae.txt"), RayRows.subscriptionSecrets("https://p.example/qwhzkrtmbvplxnae.txt"))
    }

    @Test
    fun outsideTheSpec() {
        refused("sn://AAAA", "a Kryo link with a full config")
        refused("vmess://eyJ2IjoyfQ", "VMess")
        refused("ss://YWVzLTI1Ni1nY206cHc@203.0.113.7:443", "Shadowsocks")
        refused("https://n.example/dns-query/t", "DoH is not a proxy row")
        refused(reality.replace("&pqv=$pqv", ""), "REALITY without pqv")
        refused(reality.replace("&pqv=$pqv", "").replace("security=reality", "security=tls"), "REALITY keys under security=tls")
        refused(reality.replace("&encryption=$enc", ""), "VLESS without encryption")
        refused(reality.replace("encryption=$enc", "encryption=none"), "VLESS with encryption=none")
        refused("vless://uuid@203.0.113.7:443?type=raw&security=tls&encryption=$enc&allowInsecure=1", "VLESS insecure")
        refused("vless://tcp:uuid-0@203.0.113.7:443?encryption=$enc", "the old VMess-style form")
        refused("hysteria2://pw@203.0.113.7:443/?sni=x&insecure=1", "Hysteria2 insecure")
    }
}

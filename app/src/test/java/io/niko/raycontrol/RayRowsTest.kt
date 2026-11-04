package io.niko.raycontrol

import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.naive.NaiveBean
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
        assertEquals(listOf("aB3dE5fG7hJ9kL1mN"), RayRows.subscriptionSecrets("https://panel.example/sub/aB3dE5fG7hJ9kL1mN"))
        assertEquals(listOf("qwhzkrtmbvplxnaeqwhzkrtm"), RayRows.subscriptionSecrets("https://panel.example/s/qwhzkrtmbvplxnaeqwhzkrtm"))
        assertEquals(emptyList<String>(), RayRows.subscriptionSecrets("https://p.example/api/v1/client/subscribe?token=x"))
        assertEquals(emptyList<String>(), RayRows.subscriptionSecrets("https://p.example/subscribe"))
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

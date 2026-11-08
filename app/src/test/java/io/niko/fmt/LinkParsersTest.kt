package io.niko.fmt

import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.hysteria.hopPortsToSingboxList
import io.niko.fmt.hysteria.parseHysteria2
import io.niko.fmt.v2ray.VMessBean
import io.niko.fmt.v2ray.echShareParameter
import io.niko.fmt.v2ray.parseDuckSoft
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LinkParsersTest {

    @Test
    fun hysteria2PortHoppingInAuthority() {
        val bean = parseHysteria2(
            "hy2://user:pass@example.com:443,5000-6000/?sni=a.example&obfs=salamander&obfs-password=secret&insecure=1#Name"
        )
        assertEquals("example.com", bean.serverAddress)
        assertEquals("443,5000-6000", bean.serverPorts)
        assertEquals("user:pass", bean.authPayload)
        assertEquals("a.example", bean.sni)
        assertEquals("secret", bean.obfuscation)
        assertTrue(bean.allowInsecure)
        assertEquals("Name", bean.name)
    }

    @Test
    fun hysteria2NikoAck() {
        // raycontrol from v0.21.0: the node answers right after its dial; kept through the store (bean version 9)
        val bean = parseHysteria2("hy2://pw@203.0.113.2:443/?sni=h2.test&niko-ack=1#node").apply { initializeDefaultValues() }
        assertTrue(bean.nikoAck)
        val stored = KryoConverters.deserialize(HysteriaBean(), KryoConverters.serialize(bean))
        assertTrue(stored.nikoAck)
        for (link in listOf("hy2://pw@203.0.113.2:443/?niko-ack=0", "hy2://pw@203.0.113.2:443/?niko-ack=true", "hy2://pw@203.0.113.2:443/")) {
            val other = parseHysteria2(link).apply { initializeDefaultValues() }
            assertFalse(link, other.nikoAck)
            assertFalse(link, KryoConverters.deserialize(HysteriaBean(), KryoConverters.serialize(other)).nikoAck)
        }
    }

    @Test
    fun hysteria2HoppingWithIpv6AndPlainPort() {
        val v6 = parseHysteria2("hysteria2://auth@[2001:db8::1]:20000-30000?sni=b.example")
        assertEquals("2001:db8::1", v6.serverAddress)
        assertEquals("20000-30000", v6.serverPorts)
        assertEquals("auth", v6.authPayload)

        val plain = parseHysteria2("hysteria2://auth@example.com:8443")
        assertEquals("8443", plain.serverPorts)

        val mport = parseHysteria2("hysteria2://auth@example.com:443?mport=1000-2000")
        assertEquals("1000-2000", mport.serverPorts)
    }

    @Test
    fun hopPortsBecomeRanges() {
        assertEquals(listOf("443:443", "5000:6000"), hopPortsToSingboxList("443,5000-6000"))
        assertEquals(listOf("443:443", "8443:8443"), hopPortsToSingboxList("443, 8443"))
        assertEquals(listOf("20000:30000"), hopPortsToSingboxList("20000:30000"))
    }

    @Test
    fun vlessInsecureAndEch() {
        val bean = VMessBean().apply { alterId = -1 }
        bean.parseDuckSoft(
            "https://uuid@example.com:443?security=tls&sni=a.example&insecure=1&ech=AEX+DQBBpQAgACCd_w".toHttpUrl()
        )
        assertTrue(bean.allowInsecure)
        assertTrue(bean.enableECH)
        assertEquals(
            "-----BEGIN ECH CONFIGS-----\nAEX+DQBBpQAgACCd/w==\n-----END ECH CONFIGS-----",
            bean.echConfig
        )
        assertEquals("AEX+DQBBpQAgACCd/w==", bean.echShareParameter())

        // "<domain>+<dns>" can not be expressed in sing-box options: no ECH rather than a broken one
        val dns = VMessBean().apply { alterId = -1 }
        dns.parseDuckSoft(
            "https://uuid@example.com:443?security=tls&ech=cloudflare-ech.com%2Bhttps://1.1.1.1/dns-query".toHttpUrl()
        )
        assertFalse(dns.enableECH == true)
        assertEquals(null, dns.echShareParameter())

        // REALITY conflicts with ECH
        val reality = VMessBean().apply { alterId = -1 }
        reality.parseDuckSoft(
            "https://uuid@example.com:443?security=reality&pbk=key&ech=AEX%2BDQBBpQAgACCd_w".toHttpUrl()
        )
        assertFalse(reality.enableECH == true)

        val plain = VMessBean().apply { alterId = -1 }
        plain.parseDuckSoft("https://uuid@example.com:443?security=tls".toHttpUrl())
        assertFalse(plain.allowInsecure == true)
        assertFalse(plain.enableECH == true)
    }
}

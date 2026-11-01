package io.niko.fmt

import io.niko.fmt.v2ray.VMessBean
import io.niko.fmt.v2ray.buildSingBoxOutboundStandardV2RayBean
import io.niko.fmt.v2ray.parseDuckSoft
import io.niko.fmt.v2ray.toUriVMessVLESSTrojan
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VlessEncryptionTest {

    private val spec = "mlkem768x25519plus.native.0rtt.Xw7QmfF2Ty6s6LYhyF_2O5ilJSvSrA7Pi0yHrzP2nQ0"

    private fun vless() = VMessBean().apply { alterId = -1; initializeDefaultValues() }

    @Test
    fun linkRoundTripAndOutbound() {
        val bean = vless()
        bean.parseDuckSoft("https://uuid@example.com:443?type=tcp&encryption=$spec&flow=xtls-rprx-vision".toHttpUrl())
        assertEquals(spec, bean.vlessEncryption)
        assertEquals("xtls-rprx-vision", bean.encryption)
        val outbound = buildSingBoxOutboundStandardV2RayBean(bean).asMap()
        assertEquals(spec, outbound["encryption"])
        assertEquals("xtls-rprx-vision", outbound["flow"])
        bean.name = "" // the fragment encoder needs Android
        assertTrue(bean.toUriVMessVLESSTrojan(false).contains("encryption=$spec"))

        val plain = vless()
        plain.parseDuckSoft("https://uuid@example.com:443?type=tcp&encryption=none".toHttpUrl())
        assertEquals("", plain.vlessEncryption)
        assertTrue(!buildSingBoxOutboundStandardV2RayBean(plain).asMap().containsKey("encryption"))
    }

    @Test
    fun realityMldsa65Link() {
        val pqv = "A".repeat(2603)
        val bean = vless()
        bean.parseDuckSoft("https://uuid@example.com:443?type=tcp&security=reality&pbk=key&sid=ab&pqv=$pqv".toHttpUrl())
        assertEquals(pqv, bean.realityMldsa65Verify)
        bean.name = ""
        assertTrue(bean.toUriVMessVLESSTrojan(false).contains("pqv=$pqv"))
        val copy = KryoConverters.deserialize(vless(), KryoConverters.serialize(bean))
        assertEquals(pqv, copy.realityMldsa65Verify)
    }
}

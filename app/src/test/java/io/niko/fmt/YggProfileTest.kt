package io.niko.fmt

import io.niko.fmt.v2ray.VMessBean
import io.niko.fmt.v2ray.parseDuckSoft
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** raycontrol's Yggdrasil profile (subscription-v1.md §4, niko-ygg-note.md). */
class YggProfileTest {

    private val nodeKey = "ab".repeat(32)
    private val seed = "0f".repeat(32)
    private val peer = "tls://node.example:443?key=$nodeKey&sni=node.example"

    private fun vless() = VMessBean().apply { alterId = -1; initializeDefaultValues() }

    @Test
    fun link() {
        val enc = "mlkem768x25519plus.native.0rtt.Xw7QmfF2Ty6s6LYhyF_2O5ilJSvSrA7Pi0yHrzP2nQ0"
        val ygg = java.net.URLEncoder.encode("$peer;$seed", "UTF-8")
        val bean = vless()
        bean.parseDuckSoft(
            "https://uuid@[200:1234:5678::1]:31337?type=raw&encryption=$enc&niko-node=n1&niko-svc=7&niko-ygg=$ygg".toHttpUrl()
        )
        assertEquals("200:1234:5678::1", bean.serverAddress)
        assertEquals(31337, bean.serverPort)
        assertEquals(peer, bean.yggPeer)
        assertEquals(seed, bean.yggKey)
        assertEquals("node.example", yggPeerHost(bean.yggPeer))
        // the fields survive the profile store
        val copy = KryoConverters.deserialize(vless(), KryoConverters.serialize(bean))
        assertEquals(peer, copy.yggPeer)
        assertEquals(seed, copy.yggKey)
    }

    @Test
    fun broken() {
        for (bad in listOf(
            "$peer",                                       // no seed
            "$peer;${seed.dropLast(2)}",                   // short seed
            "tcp://node.example:443?key=$nodeKey;$seed",   // not tls
            "tls://node.example:443?sni=node.example;$seed", // no node key
            "tls://node.example?key=$nodeKey;$seed",       // no port
        )) {
            assertThrows(bad, IllegalArgumentException::class.java) { parseYggParam(bad) }
        }
    }
}

package io.niko.golden

import io.niko.fmt.AbstractBean
import io.niko.fmt.http.parseHttp
import io.niko.fmt.hysteria.parseHysteria1
import io.niko.fmt.hysteria.parseHysteria2
import io.niko.fmt.mieru.parseMieru
import io.niko.fmt.naive.parseNaive
import io.niko.fmt.parseUniversal
import io.niko.fmt.shadowsocks.parseShadowsocks
import io.niko.fmt.socks.parseSOCKS
import io.niko.fmt.trojan.parseTrojan
import io.niko.fmt.trojan_go.parseTrojanGo
import io.niko.fmt.tuic.parseTuic
import io.niko.fmt.v2ray.parseV2Ray
import io.niko.proxy.anytls.parseAnytls
import java.io.File

/** What the golden tests share: the fixtures' directory and the app's parse of a link. */
object GoldenLinks {

    /** shared/golden/<name> above the test's directory */
    fun dir(name: String): File = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "shared/golden/$name") }
        .firstOrNull { it.isDirectory }
        ?: error("no shared/golden/$name above ${File("").absolutePath}")

    /** The bean of [link] with its defaults, or null where the app makes none of it (its parser throws). */
    fun parse(link: String): AbstractBean? = try {
        when {
            link.startsWith("clash://install-config?") || link.startsWith("sn://subscription?") -> {
                println("a subscription: $link")
                null
            }

            link.startsWith("sn://") -> parseUniversal(link)
            link.startsWith("socks://") || link.startsWith("socks4://") || link.startsWith("socks4a://") ||
                link.startsWith("socks5://") -> parseSOCKS(link)

            link.matches("(http|https)://.*".toRegex()) -> parseHttp(link)
            link.startsWith("vmess://") || link.startsWith("vless://") -> parseV2Ray(link)
            link.startsWith("trojan://") -> parseTrojan(link)
            link.startsWith("trojan-go://") -> parseTrojanGo(link)
            link.startsWith("mierus://") -> parseMieru(link)
            link.startsWith("ss://") -> parseShadowsocks(link)
            link.startsWith("naive+") -> parseNaive(link)
            link.startsWith("hysteria://") -> parseHysteria1(link)
            link.startsWith("hysteria2://") || link.startsWith("hy2://") -> parseHysteria2(link)
            link.startsWith("tuic://") -> parseTuic(link)
            link.startsWith("anytls://") -> parseAnytls(link)
            else -> throw AssertionError("$link: not a kind of link the app imports")
        }?.apply { initializeDefaultValues() }
    } catch (e: Exception) {
        // as parseProxies' runCatching (an Error, such as a class the JVM lacks, fails the test instead)
        println("dropped: $link: $e")
        null
    }
}

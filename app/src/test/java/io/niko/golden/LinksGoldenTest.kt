package io.niko.golden

import com.google.gson.JsonElement
import com.google.gson.JsonParser
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
import io.niko.utils.JavaUtil
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The golden fixtures of share links (shared/golden/links, docs/design/desktop-stage3.md §5): each `<name>.txt` holds
 * one link, `<name>.json` the bean the app's parser makes of it with its defaults applied, as the app's Gson writes it
 * (JavaUtil.gson), or `null` for a link the app drops. libcore/nikofmt reads the same files and must give the same
 * JSON (golden_test.go). Compared by meaning: numbers as numbers, the order of keys aside.
 *
 * A missing `.json` is written and the test fails: a new link takes its fixture from Kotlin, which CI's golden job
 * (ci.yml) uploads as the artifact golden-links to be committed. With NIKO_GOLDEN_UPDATE=1 in the environment every
 * `.json` is written anew and the test passes:
 *
 *     NIKO_GOLDEN_UPDATE=1 ./gradlew app:testOssReleaseUnitTest --tests 'io.niko.golden.*'
 *
 * A change of the parsers that changes a fixture updates it in the same change, and Go follows.
 *
 * The parser is the one parseProxies (ktx/Formats.kt) picks by the scheme, in its order, then
 * initializeDefaultValues, as there; a parser's exception drops the link, as its runCatching does. A subscription's
 * link (clash://install-config?, sn://subscription?, an http(s) URL that is no proxy) is no profile: null here, the
 * import takes it for a subscription. android.util.Base64 is layoutlib's (Paparazzi's), the platform's own code; what
 * the parsers log goes to the test's output (Logs without libcore's native library). A link the DataStore would fill
 * (a DNS tunnel's resolver left to the client) cannot be a fixture here: the JVM has no settings store; nor can a
 * NekoBean (sn://neko), whose JSONObject Gson writes by the JSON library's own fields. The sn:// links are the app's
 * own (toUniversalLink), but for a few in the layout of an older bean version (sn-*-v<N>) and the legacy form without
 * zlib (sn-legacy-*).
 */
class LinksGoldenTest {

    private val dir = generateSequence(File("").absoluteFile) { it.parentFile }
        .map { File(it, "shared/golden/links") }
        .firstOrNull { it.isDirectory }
        ?: error("no shared/golden/links above ${File("").absolutePath}")

    private val update = System.getenv("NIKO_GOLDEN_UPDATE") == "1"

    @Test
    fun links() {
        val links = dir.listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }
        assertTrue("no links in $dir", links.isNotEmpty())
        val problems = mutableListOf<String>()
        val recorded = mutableListOf<String>()
        for (txt in links) {
            val name = txt.name.removeSuffix(".txt")
            val link = txt.readText().trim()
            if (link.isEmpty() || '\n' in link) {
                problems += "$name.txt: one link per file"
                continue
            }
            val actual = JavaUtil.gson.toJson(parse(link))
            val json = File(dir, "$name.json")
            when {
                update -> json.writeText(actual + "\n")
                !json.exists() -> {
                    json.writeText(actual + "\n")
                    recorded += "$name.json"
                }

                else -> {
                    val expected = JsonParser.parseString(json.readText())
                    val got = JsonParser.parseString(actual)
                    if (expected != got) problems += "$name: ${differences(expected, got)}\n  the bean now: $actual"
                }
            }
        }
        val orphans = dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty()
            .filter { !File(dir, it.name.removeSuffix(".json") + ".txt").exists() }
        orphans.forEach { problems += "${it.name}: no ${it.name.removeSuffix(".json")}.txt" }
        if (recorded.isNotEmpty()) {
            problems += "written, check and commit: ${recorded.joinToString()} (in $dir)"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** The bean of [link] with its defaults, or null where the app makes none of it (its parser throws). */
    private fun parse(link: String): AbstractBean? = try {
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

    /** What differs between two JSON values: the keys of two objects, else both values. */
    private fun differences(expected: JsonElement, got: JsonElement): String {
        if (!expected.isJsonObject || !got.isJsonObject) return "expected $expected, got $got"
        val e = expected.asJsonObject
        val g = got.asJsonObject
        return (e.keySet() + g.keySet()).sorted().filter { e.get(it) != g.get(it) }
            .joinToString { "$it: expected ${e.get(it)}, got ${g.get(it)}" }
    }
}

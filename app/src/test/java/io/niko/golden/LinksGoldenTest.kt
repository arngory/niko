package io.niko.golden

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import io.niko.fmt.AbstractBean
import io.niko.fmt.hysteria.parseHysteria2
import io.niko.fmt.naive.parseNaive
import io.niko.fmt.v2ray.parseV2RayStandard
import io.niko.utils.JavaUtil
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.URISyntaxException

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
 * The parser is the one parseProxies (ktx/Formats.kt) picks by the scheme, then initializeDefaultValues, as there;
 * parseProxies itself logs through libcore's native library, which a JVM test has not. A vless:// link goes to
 * parseV2RayStandard: parseV2Ray's VMess forms before it never take one. A link the DataStore would fill (a DNS
 * tunnel's resolver left to the client) cannot be a fixture here: the JVM has no settings store.
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

    /** The bean of [link] with its defaults, or null where the app drops the link (its parser throws). */
    private fun parse(link: String): AbstractBean? = try {
        when {
            link.startsWith("vless://") -> parseV2RayStandard(link)
            link.startsWith("naive+") -> parseNaive(link)
            link.startsWith("hysteria2://") || link.startsWith("hy2://") -> parseHysteria2(link)
            else -> throw AssertionError("$link: not a kind of link the golden tests take yet")
        }.apply { initializeDefaultValues() }
    } catch (e: IllegalArgumentException) {
        // require(), toHttpUrl(), toInt()
        println("dropped: $link: $e")
        null
    } catch (e: IllegalStateException) {
        // error()
        println("dropped: $link: $e")
        null
    } catch (e: URISyntaxException) {
        // java.net.URI of niko-ygg's peer
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

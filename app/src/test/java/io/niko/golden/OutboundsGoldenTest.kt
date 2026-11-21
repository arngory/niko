package io.niko.golden

import com.google.gson.JsonParser
import io.niko.database.ProxyEntity
import io.niko.fmt.buildSingBoxOutbound
import io.niko.utils.JavaUtil
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The golden outbounds (shared/golden/outbounds, docs/design/desktop-stage3.md §5): for each golden link the app
 * makes a profile of (shared/golden/links/<name>.txt), `<name>.json` holds the core outbound the app builds for it
 * (buildSingBoxOutbound, with "allow insecure" off), as the config writes it. libcore/nikoconf builds the same from
 * the same bean (outbound_test.go). A profile that needs a plugin (Hysteria 1 over faketcp or wechat-video) has no
 * outbound of the core's: no file. Written and checked as LinksGoldenTest does, NIKO_GOLDEN_UPDATE=1 included.
 */
class OutboundsGoldenTest {

    private val links: File = GoldenLinks.dir("links")
    private val dir: File = File(links.parentFile, "outbounds")
    private val update = System.getenv("NIKO_GOLDEN_UPDATE") == "1"

    @Test
    fun outbounds() {
        dir.mkdirs()
        val txts = links.listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }
        assertTrue("no links in $links", txts.isNotEmpty())
        val problems = mutableListOf<String>()
        val recorded = mutableListOf<String>()
        val expectedNames = mutableSetOf<String>()
        for (txt in txts) {
            val name = txt.name.removeSuffix(".txt")
            val bean = GoldenLinks.parse(txt.readText().trim()) ?: continue
            if (ProxyEntity().putBean(bean).needExternal()) continue
            expectedNames += "$name.json"
            val actual = try {
                JavaUtil.gson.toJson(buildSingBoxOutbound(bean, false).asMap())
            } catch (e: Exception) {
                problems += "$name: the build throws: $e"
                continue
            }
            val json = File(dir, "$name.json")
            when {
                update -> json.writeText(actual + "\n")
                !json.exists() -> {
                    json.writeText(actual + "\n")
                    recorded += "$name.json"
                }

                JsonParser.parseString(json.readText()) != JsonParser.parseString(actual) ->
                    problems += "$name: expected ${json.readText().trim()}\n  got $actual"
            }
        }
        dir.listFiles { f -> f.name.endsWith(".json") }.orEmpty().filter { it.name !in expectedNames }
            .forEach { problems += "${it.name}: no profile of a golden link for it" }
        if (recorded.isNotEmpty()) problems += "written, check and commit: ${recorded.joinToString()} (in $dir)"
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

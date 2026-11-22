package io.niko.golden

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.fmt.DpiDirect
import io.niko.utils.JavaUtil
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * shared/golden/dpi/byedpi.json: the ready-made strategies of «Обход DPI для прямых соединений» and ByeDPI strings
 * with the strategy each gives (`out`, as the route rule's tcp_desync) or why it is refused (`error`, the kind's code,
 * with its `flag` and `value`). libcore/nikoconf's dpi_test.go reads the same file.
 */
class DpiGoldenTest {

    private val file = File(GoldenLinks.dir("dpi"), "byedpi.json")
    private val golden = JsonParser.parseString(file.readText()).asJsonObject
    private val gson = JavaUtil.gson

    private fun json(strategy: DpiDirect.Strategy) = gson.toJsonTree(strategy.toConfig())

    @Test
    fun presets() {
        val want = golden.getAsJsonObject("presets")
        assertEquals(want.keySet().toList(), DpiDirect.presets.map { it.id })
        for (preset in DpiDirect.presets) assertEquals(preset.id, want.get(preset.id), json(preset.strategy))
        assertTrue(DpiDirect.preset(DpiDirect.DEFAULT_PRESET) != null)
    }

    @Test
    fun byeDpi() {
        val problems = mutableListOf<String>()
        for (case in golden.getAsJsonArray("cases").map { it.asJsonObject }) {
            val input = case.get("in").asString
            val got = when (val parsed = DpiDirect.parseByeDpi(input)) {
                is DpiDirect.Parsed.Ok -> JsonObject().apply { add("out", json(parsed.strategy)) }
                is DpiDirect.Parsed.Error -> JsonObject().apply {
                    addProperty("error", parsed.kind.code)
                    if (parsed.flag.isNotEmpty()) addProperty("flag", parsed.flag)
                    if (parsed.value.isNotEmpty()) addProperty("value", parsed.value)
                }
            }
            val want = case.deepCopy().apply { remove("in") }
            if (got != want) problems += "\"$input\": want $want, got $got"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    @Test
    fun setting() {
        assertEquals(null, DpiDirect.strategy(DpiDirect.OFF, DpiDirect.DEFAULT_PRESET, "-s 1"))
        assertEquals(DpiDirect.preset("split_sni")!!.strategy, DpiDirect.strategy(DpiDirect.PRESET, "split_sni", ""))
        assertEquals(null, DpiDirect.strategy(DpiDirect.PRESET, "gone", ""))
        assertEquals(DpiDirect.Strategy(tlsrec = listOf("sni+1")), DpiDirect.strategy(DpiDirect.CUSTOM, "split_sni", "-r 1+s"))
        assertEquals(null, DpiDirect.strategy(DpiDirect.CUSTOM, "split_sni", "-f -1"))
        val needs = DpiDirect.presets.associate { it.id to (it.strategy.needsTtl to it.strategy.needsOob) }
        assertEquals(
            mapOf("tlsrec_sni" to (false to false), "split_sni" to (false to false), "disoob_sld" to (true to true), "disorder_1" to (true to false)),
            needs,
        )
    }
}

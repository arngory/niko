package io.niko.utils

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StatsReportTest {

    private val min = 60_000L
    private val from = 1_000_000_000_000L
    private val to = from + 60 * min

    private fun find(a: JSONArray, name: String, label: Pair<String, String>? = null): JSONObject? =
        (0 until a.length()).map { a.getJSONObject(it) }.firstOrNull {
            it.getString("name") == name && (label == null || it.getJSONObject("labels").optString(label.first) == label.second)
        }

    private fun sample(t: Long, epoch: Long, up: Long, rss: Long = 100) = StatsReport.Sample(
        t, mapOf("niko_traffic_bytes_total;dir=up" to (epoch to up)), mapOf("niko_process_rss_mb;process=bg" to rss),
    )

    @Test
    fun countersGrowFromTheSampleBeforeTheWindowAndCountResets() {
        val coreA = from - 30 * min
        val coreB = from + 20 * min
        val samples = listOf(
            sample(from - min, coreA, 1_000),       // the baseline
            sample(from + min, coreA, 1_500),       // +500
            sample(from + 10 * min, coreA, 3_000),  // +1500
            sample(from + 21 * min, coreB, 400),    // a new core inside the window: counts whole
            sample(from + 30 * min, coreB, 900),    // +500
            sample(to + min, coreB, 99_999),        // after the window
        )
        val up = find(StatsReport.sampleCounters(samples, from, to), "niko_traffic_bytes_total", "dir" to "up")!!
        assertEquals(2_900L, up.getLong("delta"))
        assertEquals(1, up.getInt("reset"))
        assertFalse(up.getBoolean("new"))
    }

    @Test
    fun aCounterWithoutABaselineIsNewAndItsOlderEpochDoesNotCount() {
        val core = from - 30 * min
        val samples = listOf(sample(from + min, core, 5_000), sample(from + 2 * min, core, 5_600))
        val up = find(StatsReport.sampleCounters(samples, from, to), "niko_traffic_bytes_total")!!
        assertEquals(600L, up.getLong("delta"))
        assertTrue(up.getBoolean("new"))
        assertEquals(0, up.getInt("reset"))
    }

    @Test
    fun gaugesTakeTheWindowsSamplesOnly() {
        val samples = listOf(
            sample(from - min, 1, 0, rss = 999),
            sample(from + min, 1, 0, rss = 120),
            sample(from + 2 * min, 1, 0, rss = 80),
            sample(from + 3 * min, 1, 0, rss = 100),
        )
        val rss = find(StatsReport.gauges(samples, from, to), "niko_process_rss_mb", "process" to "bg")!!
        assertEquals(100L, rss.getLong("end"))
        assertEquals(80L, rss.getLong("min"))
        assertEquals(120L, rss.getLong("max"))
    }

    @Test
    fun eventsAreCountedByNameAndLabelsInsideTheWindow() {
        val events = listOf(
            StatsReport.Event(from - min, "service_start", mapOf("mode" to "vpn"), null),
            StatsReport.Event(from + min, "service_start", mapOf("mode" to "vpn"), null),
            StatsReport.Event(from + 2 * min, "service_start", mapOf("mode" to "vpn"), null),
            StatsReport.Event(from + 3 * min, "service_start", mapOf("mode" to "proxy"), null),
            StatsReport.Event(from + 4 * min, "network_change", mapOf("to" to "wifi"), null),
        )
        val counters = StatsReport.eventCounters(events, from, to)
        assertEquals(2L, find(counters, "niko_service_start_total", "mode" to "vpn")!!.getLong("delta"))
        assertEquals(1L, find(counters, "niko_service_start_total", "mode" to "proxy")!!.getLong("delta"))
        assertEquals(1L, find(counters, "niko_network_change_total", "to" to "wifi")!!.getLong("delta"))
    }

    @Test
    fun timingsGiveTheMedianThe90thAndTheMost() {
        val events = (1..10).map {
            StatsReport.Event(from + it * min, "url_test", mapOf("profile" to "naive", "outcome" to "ok"), it * 100L)
        } + StatsReport.Event(from + 11 * min, "url_test", mapOf("profile" to "naive", "outcome" to "fail"), null)
        val t = find(StatsReport.values(events, from, to), "niko_url_test_ms", "outcome" to "ok")!!
        assertEquals(10, t.getInt("count"))
        assertEquals(500L, t.getLong("p50"))
        assertEquals(900L, t.getLong("p90"))
        assertEquals(1_000L, t.getLong("max"))
        assertNull(find(StatsReport.values(events, from, to), "niko_url_test_ms", "outcome" to "fail"))
    }

    @Test
    fun theWrapperIsTheNodesAndPartialWhenRecordsBeginInside() {
        val head = JSONObject().put("app", JSONObject().put("product", "niko"))
        val report = StatsReport.build("r-1", to, from, to, emptyList(), emptyList(), from + 5 * min, head)
        assertEquals(1, report.getInt("v"))
        assertEquals("niko-stats", report.getString("kind"))
        assertEquals("client", report.getString("side"))
        assertEquals("r-1", report.getString("run"))
        assertEquals(StatsReport.utc(from), report.getString("from"))
        assertTrue(report.getBoolean("partial"))
        assertTrue(report.getString("partial_why").startsWith("records begin at "))
        assertEquals("niko", report.getJSONObject("app").getString("product"))
        assertEquals(60, report.getJSONObject("coverage").getInt("window_minutes"))

        val whole = StatsReport.build("r-1", to, from, to, emptyList(), emptyList(), from - min, head)
        assertFalse(whole.getBoolean("partial"))
        assertFalse(whole.has("partial_why"))
    }

    @Test
    fun linesParseBackAndBadOnesAreSkipped() {
        val e = StatsReport.parseEvent("""{"t":5,"p":"bg","n":"watchdog","l":{"action":"switch"},"v":7}""")!!
        assertEquals("watchdog", e.name)
        assertEquals("switch", e.labels["action"])
        assertEquals(7L, e.value)
        assertNull(StatsReport.parseEvent("not json"))
        val s = StatsReport.parseSample("""{"t":9,"c":{"a;x=1":[3,4]},"g":{"b":5}}""")!!
        assertEquals(3L to 4L, s.counters["a;x=1"])
        assertEquals(5L, s.gauges["b"])
        assertEquals("a" to mapOf("x" to "1", "y" to "2"), StatsReport.metric("a;x=1,y=2"))
    }
}

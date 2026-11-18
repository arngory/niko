package io.niko.utils

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * The client's side of stats-v1 (raycontrol `docs/spec/stats-v1.md`): what the app recorded in a window, in the
 * wrapper the node's `rayctl stats` writes, under the same run ID. Built from [Stats]' two files: events (one line
 * each, from both processes) and the service's samples (one a minute while it runs, cumulative counters with the
 * epoch they count from). Labels come from fixed words only ([Stats.label]): no addresses, keys, domains or names.
 */
object StatsReport {

    class Event(val t: Long, val name: String, val labels: Map<String, String>, val value: Long?)

    /** [counters]: metric → (epoch, value since the epoch); [gauges]: metric → value. A metric is "name;k=v,k=v". */
    class Sample(val t: Long, val counters: Map<String, Pair<Long, Long>>, val gauges: Map<String, Long>)

    fun parseEvent(line: String): Event? = runCatching {
        val o = JSONObject(line)
        val labels = o.optJSONObject("l")?.let { l -> l.keys().asSequence().associateWith { l.getString(it) } }.orEmpty()
        Event(o.getLong("t"), o.getString("n"), labels, if (o.has("v")) o.getLong("v") else null)
    }.getOrNull()

    fun parseSample(line: String): Sample? = runCatching {
        val o = JSONObject(line)
        val c = o.optJSONObject("c")
        val g = o.optJSONObject("g")
        Sample(
            o.getLong("t"),
            c?.keys()?.asSequence()?.associateWith { c.getJSONArray(it).let { a -> a.getLong(0) to a.getLong(1) } }.orEmpty(),
            g?.keys()?.asSequence()?.associateWith { g.getLong(it) }.orEmpty(),
        )
    }.getOrNull()

    /**
     * What a full records file keeps: the lines of the last two hours ([since] on), and of those at most [maxBytes],
     * the newest. Half the file's bound, so the next trim is far off and a busy hour does not rewrite it every line.
     */
    fun trimmed(lines: List<String>, since: Long, maxBytes: Int): List<String> {
        val recent = lines.filter { l -> l.isNotBlank() && runCatching { JSONObject(l).getLong("t") >= since }.getOrDefault(false) }
        var size = 0
        val kept = ArrayDeque<String>()
        for (l in recent.asReversed()) {
            size += l.toByteArray().size + 1
            if (size > maxBytes) break
            kept.addFirst(l)
        }
        return kept.toList()
    }

    fun utc(ms: Long): String = Instant.ofEpochMilli(ms).truncatedTo(ChronoUnit.SECONDS).toString()

    /** "name;k=v,k=v" → name and labels */
    fun metric(key: String): Pair<String, Map<String, String>> {
        val name = key.substringBefore(';')
        val labels = key.substringAfter(';', "").split(',').filter { '=' in it }
            .associate { it.substringBefore('=') to it.substringAfter('=') }
        return name to labels
    }

    private fun labelsJson(labels: Map<String, String>) = JSONObject().apply { labels.toSortedMap().forEach { (k, v) -> put(k, v) } }

    /**
     * Each sampled counter over (from, to]: the growth between samples of one epoch; a new epoch (the core or the
     * process started again) or a value that went down is a reset, and the new epoch's value counts whole when it began
     * inside the window. new: no sample before the window to start from.
     */
    fun sampleCounters(samples: List<Sample>, from: Long, to: Long): JSONArray {
        val sorted = samples.filter { it.t <= to }.sortedBy { it.t }
        val inside = sorted.filter { it.t > from }
        val out = JSONArray()
        for (key in inside.flatMap { it.counters.keys }.toSortedSet()) {
            // the last sample before the window, however old: the sampler's minute stretches in deep sleep. A counter's
            // epoch is its run (the core's, the service's), so a stop in between is a reset, not growth
            var prev = sorted.lastOrNull { it.t <= from && key in it.counters }?.counters?.get(key)
            val isNew = prev == null
            var delta = 0L
            var resets = 0
            for (s in inside) {
                val cur = s.counters[key] ?: continue
                val last = prev
                if (last != null && cur.first == last.first && cur.second >= last.second) {
                    delta += cur.second - last.second
                } else {
                    if (last != null) resets++
                    if (cur.first >= from) delta += cur.second
                }
                prev = cur
            }
            val (name, labels) = metric(key)
            out.put(JSONObject().put("name", name).put("labels", labelsJson(labels)).put("delta", delta)
                .put("reset", resets).put("new", isNew))
        }
        return out
    }

    /** Each sampled gauge over (from, to]: its last value, the least and the most. */
    fun gauges(samples: List<Sample>, from: Long, to: Long): JSONArray {
        val inside = samples.filter { it.t in (from + 1)..to }.sortedBy { it.t }
        val out = JSONArray()
        for (key in inside.flatMap { it.gauges.keys }.toSortedSet()) {
            val values = inside.mapNotNull { it.gauges[key] }
            val (name, labels) = metric(key)
            out.put(JSONObject().put("name", name).put("labels", labelsJson(labels)).put("end", values.last())
                .put("min", values.min()).put("max", values.max()))
        }
        return out
    }

    /** The events in (from, to] as counters: how many of each name and labels, niko_<name>_total. */
    fun eventCounters(events: List<Event>, from: Long, to: Long): JSONArray {
        val counts = events.filter { it.t in (from + 1)..to }
            .groupingBy { it.name to it.labels.toSortedMap() }.eachCount()
        val out = JSONArray()
        counts.entries.sortedWith(compareBy({ it.key.first }, { it.key.second.toString() })).forEach { (key, count) ->
            out.put(JSONObject().put("name", "niko_${key.first}_total").put("labels", labelsJson(key.second))
                .put("delta", count.toLong()).put("reset", 0).put("new", false))
        }
        return out
    }

    /** The events in (from, to] that carry a value (a URL test's time), per name and labels: count, median, 90th, most. */
    fun values(events: List<Event>, from: Long, to: Long): JSONArray {
        val groups = events.filter { it.t in (from + 1)..to && it.value != null }
            .groupBy { it.name to it.labels.toSortedMap() }
        val out = JSONArray()
        groups.entries.sortedWith(compareBy({ it.key.first }, { it.key.second.toString() })).forEach { (key, list) ->
            val v = list.mapNotNull { it.value }.sorted()
            out.put(JSONObject().put("name", "niko_${key.first}_ms").put("labels", labelsJson(key.second))
                .put("count", v.size).put("p50", percentile(v, 50)).put("p90", percentile(v, 90)).put("max", v.last()))
        }
        return out
    }

    /** the nearest-rank percentile of [sorted], not empty */
    fun percentile(sorted: List<Long>, p: Int): Long {
        val rank = Math.ceil(p / 100.0 * sorted.size).toInt().coerceIn(1, sorted.size)
        return sorted[rank - 1]
    }

    /** Minutes of (from, to] with a sample: the service ran then. */
    fun serviceMinutes(samples: List<Sample>, from: Long, to: Long): Int =
        samples.filter { it.t in (from + 1)..to }.map { (it.t - from - 1) / 60_000L }.toSet().size

    /**
     * The whole report. [head]'s fields (app, state, settings) go in as they are; partial when the records begin
     * inside the window ([recordsSince]: the oldest line kept, or the moment the app first recorded).
     */
    fun build(
        run: String,
        generated: Long,
        from: Long,
        to: Long,
        events: List<Event>,
        samples: List<Sample>,
        recordsSince: Long?,
        head: JSONObject,
    ): JSONObject {
        val out = JSONObject()
            .put("v", 1)
            .put("kind", "niko-stats")
            .put("side", "client")
            .put("run", run)
            .put("generated", utc(generated))
            .put("from", utc(from))
            .put("to", utc(to))
        val begins = recordsSince != null && recordsSince > from
        out.put("partial", recordsSince == null || begins)
        when {
            recordsSince == null -> out.put("partial_why", "nothing recorded yet")
            begins -> out.put("partial_why", "records begin at ${utc(recordsSince)}")
        }
        head.keys().forEach { out.put(it, head.get(it)) }
        out.put(
            "coverage", JSONObject()
                .put("window_minutes", ((to - from) / 60_000L).toInt())
                .put("service_minutes", serviceMinutes(samples, from, to))
        )
        val counters = JSONArray()
        eventCounters(events, from, to).let { for (i in 0 until it.length()) counters.put(it.get(i)) }
        sampleCounters(samples, from, to).let { for (i in 0 until it.length()) counters.put(it.get(i)) }
        out.put("counters", counters)
        out.put("gauges", gauges(samples, from, to))
        out.put("timings", values(events, from, to))
        return out
    }
}

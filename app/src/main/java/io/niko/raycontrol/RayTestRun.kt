package io.niko.raycontrol

import com.google.gson.JsonObject
import io.niko.database.DataStore
import io.niko.database.SagerDatabase
import io.niko.ktx.app
import io.niko.utils.SendLog
import libcore.Libcore
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A test run against a node with a run id both sides log under (the node's admin runs `rayctl report -id` for the
 * same window): the direct path (which SPA carrier opens the port, the UDP MTU), the subscription, and each profile
 * in turn (handshake to the first byte, down, the UDP echo through the tunnel). Every step is a JSON line with the
 * id and a UTC time; the core runs at debug meanwhile. The file holds the steps and the app's log since the run
 * started, redacted as "Send log" is (node names and addresses, tokens and keys).
 */
object RayTestRun {

    private val day = DateTimeFormatter.ofPattern("yyyyMMdd").withZone(ZoneOffset.UTC)
    private val minute = DateTimeFormatter.ofPattern("HHmm").withZone(ZoneOffset.UTC)

    /** r-YYYYMMDD-HHMM in UTC: unique enough for one person's runs, and both sides read the time from it. */
    fun defaultId(now: Instant = Instant.now()) = "r-${day.format(now)}-${minute.format(now)}"

    /** A run id of letters, digits, dots, dashes and underscores, at most 64 (it goes into a file name). */
    fun validId(id: String) = id.length in 1..64 && id.all { it.isLetterOrDigit() || it in ".-_" }

    fun utc(at: Instant = Instant.now()): String = DateTimeFormatter.ISO_INSTANT.format(at.truncatedTo(java.time.temporal.ChronoUnit.MILLIS))

    /** Runs the steps, telling [progress] each one, and returns the redacted file to share. */
    suspend fun run(node: RayNode, id: String, progress: (String) -> Unit): File {
        require(validId(id)) { "run id: letters, digits, '.', '-', '_' only" }
        val steps = StringBuilder()
        fun step(name: String, fields: JsonObject.() -> Unit = {}) {
            val line = JsonObject().apply {
                addProperty("run", id)
                addProperty("t", utc())
                addProperty("step", name)
                fields()
            }
            steps.append(line).append('\n')
            progress(line.toString())
        }
        val logFrom = SendLog.getNekoLog(0).size
        val level = DataStore.logLevel
        // the test cores of this run log at debug; the running service keeps its level until its next start
        DataStore.logLevel = maxOf(level, 3)
        try {
            val network = RayChannel.network()
            step("start") {
                addProperty("app", io.niko.BuildConfig.VERSION_NAME)
                addProperty("node_id", node.spa.nodeId)
                addProperty("net", network?.kind ?: "none")
                addProperty("profiles", node.profileIds.orEmpty().size)
            }
            if (network == null) {
                step("end") { addProperty("error", "no network") }
            } else {
                RayChannel.directPath(node, network)?.let { p ->
                    step("direct") {
                        p.spaCarrier?.let { addProperty("spa_carrier", it) }
                        addProperty("udp_probed", p.udpProbed)
                        if (p.udpMeasured) addProperty("udp_mtu", p.udpMtu)
                        if (p.udpStatus != 0) addProperty("udp_status", p.udpStatus)
                        p.error?.let { addProperty("error", it) }
                    }
                }
                val sub = runCatching { RaySubscription.refresh(node.spa.nodeId, reload = RaySubscription.Reload.LATER) }
                step("subscription") {
                    sub.onSuccess {
                        addProperty("profiles", it.profiles)
                        addProperty("skipped", it.skipped)
                        addProperty("clock_skew_s", it.clockSkew)
                    }.onFailure { addProperty("error", it.message ?: it.javaClass.simpleName) }
                }
                val current = RayNodes.get(node.spa.nodeId) ?: node
                for ((key, rowId) in current.profileIds.orEmpty()) {
                    val profile = SagerDatabase.proxyDao.getById(rowId) ?: continue
                    step("profile_start") {
                        addProperty("key", key)
                        addProperty("name", profile.displayName())
                        addProperty("type", profile.displayType())
                        RayRelays.relayOfKey(key)?.let { addProperty("relay", it) }
                    }
                    val score = RayChannel.probe(profile, deep = true, mobile = network.mobile)
                    step("profile") {
                        addProperty("key", key)
                        if (score == null) {
                            addProperty("error", "the profile is in use")
                        } else {
                            addProperty("tcp_ok", score.tcpOk)
                            addProperty("first_byte_ms", score.firstByteMs)
                            addProperty("kbps", score.kbps)
                            if (score.stalledAt >= 0) addProperty("stalled_at", score.stalledAt)
                            score.tcpError?.let { addProperty("tcp_error", it) }
                            if (score.udpMeasured) {
                                addProperty("udp_ok", score.udpOk)
                                addProperty("udp_loss_pct", score.udpLossPct)
                                addProperty("udp_rtt_ms", score.udpRttMs)
                                score.udpError?.let { addProperty("udp_error", it) }
                            }
                            RayChannel.record(node.spa.nodeId, network, key, score)
                        }
                    }
                }
                step("end")
            }
        } finally {
            DataStore.logLevel = level
        }

        val log = SendLog.getNekoLog(0).let { all -> String(all, logFrom.coerceAtMost(all.size), (all.size - logFrom).coerceAtLeast(0)) }
        val report = buildString {
            append("# Niko test run ").append(id).append(", ").append(utc()).append('\n')
            append("# steps (JSON lines)\n").append(steps)
            append("# the app's log since the run started\n").append(log)
        }
        RayNodes.syncLogRedactions()
        val file = File(File(app.cacheDir, "log").also { it.mkdirs() }, "niko-$id.log")
        file.writeText(report.lines().chunked(2000).joinToString("\n") { chunk ->
            runCatching { Libcore.redactLog(chunk.joinToString("\n")) }.getOrDefault("<redaction failed>")
        })
        return file
    }
}

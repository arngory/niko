package io.niko.utils

import android.os.Build
import io.niko.BuildConfig
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicLong

/**
 * What the app does, kept for two hours for "Statistics for the last hour" ([StatsReport], stats-v1): events from
 * both processes (a start, a failure and its kind, another network, the watchdog's step, a URL test's time) and,
 * while the service runs, a sample a minute (the traffic through the proxy, the warnings and errors logged, the
 * service's memory). Only fixed words and numbers are written: no addresses, keys, domains or names.
 */
object Stats {

    private const val KEEP_MS = 2 * 60 * 60_000L
    private const val TRIM_AT = 256 * 1024L
    private val LABEL = Regex("[a-z0-9_.#-]{1,32}")

    private val dir by lazy { File(SagerNet.application.noBackupFilesDir, "stats").apply { mkdirs() } }
    val eventsFile: File get() = File(dir, "events.jsonl")
    val samplesFile: File get() = File(dir, "samples.jsonl")

    private val writer = Executors.newSingleThreadExecutor { Thread(it, "niko-stats").apply { isDaemon = true } }
    private val boot = System.currentTimeMillis()

    /** Warnings and errors this process logged (Logs). */
    val warnings = AtomicLong()
    val errors = AtomicLong()

    /** The traffic through the proxy since [trafficEpoch], the running core's start (TrafficLooper). */
    @Volatile
    var trafficEpoch = 0L

    @Volatile
    var trafficUp = 0L

    @Volatile
    var trafficDown = 0L

    /** [value] as a label: lower case letters, digits and "_.#-", at most 32, else "other". */
    fun label(value: String): String = value.lowercase(Locale.ROOT).takeIf { LABEL.matches(it) } ?: "other"

    /** A profile's kind in fixed words: never its name (a chain's or a custom config's type is the user's). */
    fun kind(profile: ProxyEntity?): String = when (profile?.type) {
        null -> "none"
        ProxyEntity.TYPE_SOCKS -> "socks"
        ProxyEntity.TYPE_HTTP -> "http"
        ProxyEntity.TYPE_SS -> "shadowsocks"
        ProxyEntity.TYPE_VMESS -> if (profile.vmessBean?.isVLESS == true) "vless" else "vmess"
        ProxyEntity.TYPE_TROJAN -> "trojan"
        ProxyEntity.TYPE_TROJAN_GO -> "trojan-go"
        ProxyEntity.TYPE_MIERU -> "mieru"
        ProxyEntity.TYPE_NAIVE -> "naive"
        ProxyEntity.TYPE_HYSTERIA -> "hysteria${profile.hysteriaBean?.protocolVersion ?: ""}"
        ProxyEntity.TYPE_SSH -> "ssh"
        ProxyEntity.TYPE_WG -> if (profile.wgBean?.amneziaWG.isNullOrBlank()) "wireguard" else "amneziawg"
        ProxyEntity.TYPE_TUIC -> "tuic"
        ProxyEntity.TYPE_SHADOWTLS -> "shadowtls"
        ProxyEntity.TYPE_ANYTLS -> "anytls"
        ProxyEntity.TYPE_CHAIN -> "chain"
        ProxyEntity.TYPE_NEKO -> "plugin"
        ProxyEntity.TYPE_CONFIG -> "config"
        else -> "other"
    }

    private val process get() = if (SagerNet.application.isBgProcess) "bg" else "ui"

    /** what carries [network]: wifi, cellular, ethernet, vpn or other */
    fun transport(network: android.net.Network?): String {
        val caps = network?.let { runCatching { SagerNet.connectivity.getNetworkCapabilities(it) }.getOrNull() } ?: return "other"
        return when {
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) -> "wifi"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR) -> "cellular"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET) -> "ethernet"
            caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_VPN) -> "vpn"
            else -> "other"
        }
    }

    /** Records [name] with [labels] (each a fixed word, [label]) and a number, if any (a time in ms). */
    fun event(name: String, labels: Map<String, String> = emptyMap(), value: Long? = null) {
        val line = JSONObject().put("t", System.currentTimeMillis()).put("p", process).put("n", label(name))
        if (labels.isNotEmpty()) {
            line.put("l", JSONObject().apply { labels.forEach { (k, v) -> put(label(k), label(v)) } })
        }
        value?.let { line.put("v", it) }
        write(eventsFile, line.toString())
    }

    /** The service's sample: its counters (with the epoch each counts from) and its memory. */
    fun sample() {
        val counters = JSONObject()
        fun counter(key: String, epoch: Long, value: Long) = counters.put(key, JSONArray().put(epoch).put(value))
        if (trafficEpoch > 0) {
            counter("niko_traffic_bytes_total;dir=up", trafficEpoch, trafficUp)
            counter("niko_traffic_bytes_total;dir=down", trafficEpoch, trafficDown)
        }
        // the service's own: the window's process has no sampler (its lines are in the log, not here)
        counter("niko_log_lines_total;level=warning,process=$process", boot, warnings.get())
        counter("niko_log_lines_total;level=error,process=$process", boot, errors.get())
        val gauges = JSONObject()
        rssMb()?.let { gauges.put("niko_process_rss_mb;process=$process", it) }
        val line = JSONObject().put("t", System.currentTimeMillis()).put("c", counters).put("g", gauges)
        write(samplesFile, line.toString())
    }

    /** the process's resident memory, from /proc/self/status (VmRSS, in kB) */
    private fun rssMb(): Long? = runCatching {
        File("/proc/self/status").useLines { lines ->
            lines.firstOrNull { it.startsWith("VmRSS:") }?.split(Regex("\\s+"))?.getOrNull(1)?.toLong()?.div(1024)
        }
    }.getOrNull()

    private var sampler: Job? = null

    /** A sample a minute while the service runs ([stopSampler] at its stop takes the last). */
    @OptIn(DelicateCoroutinesApi::class)
    @Synchronized
    fun startSampler() {
        if (sampler?.isActive == true) return
        sampler = GlobalScope.launch(Dispatchers.IO) {
            sample()
            while (isActive) {
                delay(60_000L)
                sample()
            }
        }
    }

    @Synchronized
    fun stopSampler() {
        val running = sampler ?: return
        running.cancel()
        sampler = null
        sample()
    }

    private fun write(file: File, line: String) {
        runCatching { writer.execute { append(file, line) } }
    }

    /** [line] at the end of [file], under a lock both processes take; past [TRIM_AT] only the last two hours stay. */
    private fun append(file: File, line: String) {
        runCatching {
            RandomAccessFile(file, "rw").use { raf ->
                raf.channel.lock().use {
                    if (raf.length() > TRIM_AT) {
                        val bytes = ByteArray(raf.length().toInt())
                        raf.seek(0)
                        raf.readFully(bytes)
                        val since = System.currentTimeMillis() - KEEP_MS
                        val kept = StatsReport.trimmed(String(bytes).lines(), since, (TRIM_AT / 2).toInt())
                            .joinToString("") { "$it\n" }
                        raf.setLength(0)
                        raf.seek(0)
                        raf.write(kept.toByteArray())
                    }
                    raf.seek(raf.length())
                    raf.write("$line\n".toByteArray())
                }
            }
        }
    }

    /**
     * [file]'s lines under a shared lock (the other process's trim does not show half a file), read on the writer's
     * thread: in one process a second lock on the file would throw rather than wait.
     */
    private fun lines(file: File): List<String> = runCatching {
        writer.submit<List<String>> {
            if (!file.exists()) return@submit emptyList()
            RandomAccessFile(file, "r").use { raf ->
                raf.channel.lock(0, Long.MAX_VALUE, true).use {
                    val bytes = ByteArray(raf.length().toInt())
                    raf.readFully(bytes)
                    String(bytes).lines().filter { it.isNotBlank() }
                }
            }
        }.get()
    }.getOrDefault(emptyList())

    /** The report for the last [minutes] (5 to 120) under [run], what the app is and does now in its head. */
    fun report(run: String, minutes: Int = 60): JSONObject {
        val now = System.currentTimeMillis()
        val from = now - minutes.coerceIn(5, 120) * 60_000L
        val events = lines(eventsFile).mapNotNull { StatsReport.parseEvent(it) }
        val samples = lines(samplesFile).mapNotNull { StatsReport.parseSample(it) }
        val since = (events.map { it.t } + samples.map { it.t }).minOrNull()
        return StatsReport.build(run, now, from, now, events, samples, since, head())
    }

    private fun head(): JSONObject {
        val app = JSONObject()
            .put("product", "niko")
            .put("version", BuildConfig.VERSION_NAME)
            .put("version_code", BuildConfig.VERSION_CODE)
            .put("android_api", Build.VERSION.SDK_INT)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull()?.let { label(it) } ?: "other")
        runCatching {
            libcore.Libcore.versionBox().lineSequence().firstOrNull { it.startsWith("sing-box: ") }
                ?.removePrefix("sing-box: ")?.trim()?.let { app.put("core", label(it)) }
        }
        val profile = runCatching { SagerDatabase.proxyDao.getById(DataStore.currentProfile) }.getOrNull()
        val fromNode = profile != null && runCatching {
            io.niko.raycontrol.RayNodes.list().any { it.profileIds?.values?.contains(profile.id) == true }
        }.getOrDefault(false)
        val state = JSONObject()
            .put("service", label(DataStore.serviceState.name))
            .put("mode", if (DataStore.serviceMode == io.niko.Key.MODE_VPN) "vpn" else "proxy")
            .put("profile", kind(profile))
            .put("from_node", fromNode)
        val settings = JSONObject()
            .put("auto_switch", DataStore.raycontrolAutoSwitch)
            .put("brutal", DataStore.raycontrolBrutal)
            .put("reset_on_network_change", DataStore.networkChangeResetConnections)
            .put("ipv6_mode", DataStore.ipv6Mode)
            .put("traffic_counted", DataStore.speedInterval > 0)
        return JSONObject().put("app", app).put("state", state).put("settings", settings)
    }
}

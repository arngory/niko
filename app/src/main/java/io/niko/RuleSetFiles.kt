package io.niko

import io.niko.database.DataStore
import io.niko.database.SagerDatabase
import io.niko.fmt.LOCAL_PROXY_USER
import io.niko.ktx.Logs
import io.niko.ktx.readableMessage
import io.niko.utils.Util
import io.niko.utils.listByLineOrComma
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libcore.Libcore
import java.io.File
import java.security.MessageDigest
import java.util.UUID

/**
 * Rule-sets the app downloads and the core reads from a file: `srs:https://…/geosite-x.srs` in a rule's lists.
 *
 * A remote rule-set (`rule-set:`) is downloaded by the core as it starts, and sing-box does not start when that first
 * download fails: through a DNS tunnel a list of 500 KB can take a minute. One of these is left out of the config
 * until its file is here (a rule it leaves without any address is left out whole), and the core reloads a file when
 * the app replaces it. The app fetches them through the proxy when it runs (as the route assets): those older than
 * the rule-set update interval after each start, and then every interval while the service runs.
 */
object RuleSetFiles {

    const val PREFIX = "srs:"

    /** what a rule-set file may weigh at most */
    private const val MAX_SIZE = 32L shl 20

    /** the files' directory; tests set their own */
    @Volatile
    var dir: () -> File = { File(SagerNet.application.filesDir, "rule-sets") }

    fun isItem(item: String) = item.startsWith(PREFIX)

    fun url(item: String) = item.removePrefix(PREFIX).trim()

    /** "source" for a JSON rule-set, "binary" (.srs) otherwise, as a remote one */
    fun format(item: String) = if (url(item).substringBefore('?').endsWith(".json")) "source" else "binary"

    /** the rule-set's file: named by its URL's SHA-256, so the same URL keeps its file */
    fun file(item: String): File {
        val digest = MessageDigest.getInstance("SHA-256").digest(url(item).toByteArray())
        val name = digest.take(16).joinToString("") { "%02x".format(it) }
        return File(dir(), name + if (format(item) == "source") ".json" else ".srs")
    }

    /** only https: a list fetched in the clear could be any list, one sending all traffic around the proxy */
    fun fetchable(item: String) = url(item).startsWith("https://", ignoreCase = true)

    fun available(item: String) = fetchable(item) && file(item).let { it.isFile && it.length() > 0 }

    /** the mark of a downloaded rule-set without any domain (IP CIDRs only): a DNS rule leaves it out */
    private fun noDomainMark(file: File) = File(file.parentFile, file.name + ".nodomain")

    /** whether a DNS rule can use the rule-set: downloaded, with domains (CheckRuleSet said so at the download) */
    fun usableInDns(item: String) = available(item) && !noDomainMark(file(item)).exists()

    /** whether a file begins as a rule-set of its format: the magic "SRS" of a binary one, a JSON object */
    fun looksValid(head: ByteArray, format: String): Boolean = if (format == "binary") {
        head.size >= 4 && head[0] == 'S'.code.toByte() && head[1] == 'R'.code.toByte() && head[2] == 'S'.code.toByte()
    } else {
        head.firstOrNull { !it.toInt().toChar().isWhitespace() } == '{'.code.toByte()
    }

    /** The list without the rule-sets not downloaded yet, and those it left out. */
    fun withoutMissing(list: List<String>): Pair<List<String>, List<String>> = list.partition { !isItem(it) || available(it) }

    /**
     * The rule-sets the running config left out, not downloaded when the core started (ConfigBuilder sets them): when
     * a service round gets one, the core restarts once to take it in.
     */
    @Volatile
    var missingInConfig: Set<String> = emptySet()

    /** the rule-sets in a rule's lists */
    fun itemsOf(lists: List<String>) = lists.flatMap { it.listByLineOrComma() }.filter(::isItem).distinct()

    /**
     * Downloads the rule-sets of items whose files are missing or older than maxAgeMillis; how many it got. A
     * cancelled caller stops it between files. serviceRound: the service's own, which stops as the service does and
     * goes as the service's mode says (see download).
     */
    suspend fun update(items: Collection<String>, maxAgeMillis: Long, serviceRound: Boolean = false): Int =
        withContext(Dispatchers.IO) {
            var got = 0
            for (item in items.filter(::isItem).distinct()) {
                ensureActive()
                if (serviceRound && !DataStore.serviceState.started) break
                if (!fetchable(item)) {
                    Logs.w("rule-set ${url(item)}: only https lists are fetched")
                    continue
                }
                val file = file(item)
                if (file.isFile && System.currentTimeMillis() - file.lastModified() < maxAgeMillis) continue
                try {
                    download(item, file, serviceRound)
                    got++
                } catch (e: Exception) {
                    Logs.w("rule-set ${url(item)}: ${e.readableMessage}")
                }
            }
            got
        }

    /**
     * Whether a download must go through the local proxy's port: in proxy mode while the service runs (never around
     * it: the device's own address), and the service's own round by its mode alone, so one starting as the service
     * stops does not go around either. In VPN mode the app's own requests go through the TUN, and the port is closed
     * unless the user set it up for other apps.
     */
    fun mustUseLocalProxy(serviceRound: Boolean, serviceMode: String, started: Boolean) =
        serviceMode != Key.MODE_VPN && (serviceRound || started)

    private fun download(item: String, file: File, serviceRound: Boolean) {
        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            trySocks5(DataStore.mixedPort, LOCAL_PROXY_USER, DataStore.localProxyPassword)
            if (mustUseLocalProxy(serviceRound, DataStore.serviceMode, DataStore.serviceState.started)) requireSocks5()
        }
        try {
            val response = client.newRequest().apply { setURL(url(item)) }.execute()
            // refused before a byte is saved when the server says it is larger (libcore bounds any body anyway)
            val length = Util.getStringBox(response.getHeader("Content-Length")).toLongOrNull()
            check(length == null || length <= MAX_SIZE) { "rule-set of $length bytes" }
            file.parentFile?.mkdirs()
            // the UI's process and the service's may fetch the same file at once: each writes its own
            val tmp = File(file.parentFile, "${file.name}.${UUID.randomUUID()}.tmp")
            try {
                response.writeTo(tmp.canonicalPath)
                val head = ByteArray(16)
                val read = tmp.inputStream().use { it.read(head) }
                check(tmp.length() in 5..MAX_SIZE && looksValid(head.copyOf(maxOf(read, 0)), format(item))) {
                    "not a rule-set (${tmp.length()} bytes)"
                }
                // read as the core reads it: a file it can not load (a newer format, say) would fail every start
                val kinds = Libcore.checkRuleSet(tmp.canonicalPath, format(item))
                check(tmp.renameTo(file)) { "can not replace ${file.name}" }
                val mark = noDomainMark(file)
                if (kinds and Libcore.RuleSetHasDomain == 0) mark.createNewFile() else mark.delete()
            } finally {
                tmp.delete()
            }
        } finally {
            client.close()
        }
    }

    /** the rule-sets of the enabled rules */
    fun enabledItems() = SagerDatabase.rulesDao.enabledRules().flatMap { itemsOf(listOf(it.domains, it.ip)) }.distinct()

    /** Removes the files no rule names any more (a rule turned off keeps its), and downloads left half done. */
    fun removeUnused() {
        val used = SagerDatabase.rulesDao.allRules().flatMap { itemsOf(listOf(it.domains, it.ip)) }.map { file(it).name }.toSet()
        val hourAgo = System.currentTimeMillis() - 3_600_000L
        dir().listFiles()?.forEach {
            val unused = when {
                it.name.endsWith(".tmp") -> it.lastModified() < hourAgo
                else -> it.name.removeSuffix(".nodomain") !in used
            }
            if (unused) it.delete()
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    /**
     * While the core runs: the enabled rules' rule-sets that are missing or stale, once the proxy has settled after
     * the start and then every rule-set update interval (the core reloads a file the app replaces). stop() ends it.
     */
    fun updateSoon() {
        job?.cancel()
        job = scope.launch {
            delay(20_000)
            while (true) {
                removeUnused()
                val interval = DataStore.ruleSetUpdateHours.coerceIn(1, 24 * 30) * 3_600_000L
                val items = enabledItems()
                // stale by the next round: what this one fetched is older than the interval by then, less a margin
                val got = if (items.isEmpty()) 0 else update(items, interval - 600_000L, serviceRound = true)
                if (got > 0) Logs.i("rule-sets: $got updated, the core reloads them")
                // a list the running config left out is here: one restart takes it in (and lets the preset's direct
                // rule in once its blocked lists are here); the next build sets what is still missing
                val arrived = missingInConfig.filter(::available)
                if (arrived.isNotEmpty() && DataStore.serviceState.started) {
                    missingInConfig = emptySet()
                    Logs.i("rule-sets: ${arrived.size} the core started without are here, restarting it")
                    SagerNet.reloadService(force = true)
                    return@launch
                }
                delay(interval)
            }
        }
    }

    /** The service stops: no more updates until the next start. */
    fun stop() {
        job?.cancel()
        job = null
    }
}

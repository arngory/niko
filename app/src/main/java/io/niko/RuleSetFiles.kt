package io.niko

import io.niko.database.DataStore
import io.niko.database.SagerDatabase
import io.niko.fmt.LOCAL_PROXY_USER
import io.niko.fmt.localProxyRequired
import io.niko.ktx.Logs
import io.niko.ktx.readableMessage
import io.niko.utils.Util
import io.niko.utils.listByLineOrComma
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
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

    fun available(item: String) = file(item).let { it.isFile && it.length() > 0 }

    /** whether a file begins as a rule-set of its format: the magic "SRS" of a binary one, a JSON object */
    fun looksValid(head: ByteArray, format: String): Boolean = if (format == "binary") {
        head.size >= 4 && head[0] == 'S'.code.toByte() && head[1] == 'R'.code.toByte() && head[2] == 'S'.code.toByte()
    } else {
        head.firstOrNull { !it.toInt().toChar().isWhitespace() } == '{'.code.toByte()
    }

    /** The list without the rule-sets not downloaded yet, and whether it left any out. */
    fun withoutMissing(list: List<String>): Pair<List<String>, Boolean> {
        val (kept, missing) = list.partition { !isItem(it) || available(it) }
        return kept to missing.isNotEmpty()
    }

    /** the rule-sets in a rule's lists */
    fun itemsOf(lists: List<String>) = lists.flatMap { it.listByLineOrComma() }.filter(::isItem).distinct()

    /** Downloads the rule-sets of items whose files are missing or older than maxAgeMillis; how many it got. */
    suspend fun update(items: Collection<String>, maxAgeMillis: Long): Int = withContext(Dispatchers.IO) {
        var got = 0
        for (item in items.filter(::isItem).distinct()) {
            val file = file(item)
            if (file.isFile && System.currentTimeMillis() - file.lastModified() < maxAgeMillis) continue
            try {
                download(item, file)
                got++
            } catch (e: Exception) {
                Logs.w("rule-set ${url(item)}: ${e.readableMessage}")
            }
        }
        got
    }

    private fun download(item: String, file: File) {
        val client = Libcore.newHttpClient().apply {
            modernTLS()
            keepAlive()
            trySocks5(DataStore.mixedPort, LOCAL_PROXY_USER, DataStore.localProxyPassword)
            // proxy mode with the service running: never around it (the device's own address)
            if (localProxyRequired()) requireSocks5()
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
                check(tmp.renameTo(file)) { "can not replace ${file.name}" }
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
            val unused = if (it.name.endsWith(".tmp")) it.lastModified() < hourAgo else it.name !in used
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
                val got = if (items.isEmpty()) 0 else update(items, interval - 600_000L)
                if (got > 0) Logs.i("rule-sets: $got updated, the core reloads them (a new one takes effect on the next start)")
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

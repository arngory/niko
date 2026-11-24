package io.niko.ui.about

import android.content.Context
import androidx.annotation.StringRes
import io.niko.R
import io.niko.ktx.Logs
import org.json.JSONArray
import org.json.JSONObject

/**
 * What the APK carries and under which licenses, from its own assets (A2 of the MVP plan, About → Licenses):
 * NOTICE, the parts of assets/licenses/components.json with their texts under assets/licenses/texts/, and the core's
 * Go modules of assets/licenses/go.json, which the library's build writes from the modules it was linked from.
 */
object Licenses {

    /** A line of the list; [files] its texts, a file's name to its text, read when the line is opened. */
    class Entry(val name: String, val detail: String, val url: String?, val files: () -> List<Pair<String, String>>)

    /** [title] null: the NOTICE, above the groups. */
    class Section(@StringRes val title: Int?, val entries: List<Entry>)

    // components.json's parts, in the order NOTICE has them
    private val PARTS = listOf(
        "app" to R.string.licenses_part_app,
        "library" to R.string.licenses_part_library,
        "native" to R.string.licenses_part_native,
        "data" to R.string.licenses_part_data,
        "font" to R.string.licenses_part_font,
        "icons" to R.string.licenses_part_icons,
    )

    @Volatile
    private var loaded: List<Section>? = null

    /** The sections, read once a process: go.json holds every module's texts. Not on the main thread. */
    fun sections(context: Context): List<Section> = loaded ?: synchronized(this) {
        loaded ?: read(context.applicationContext).also { loaded = it }
    }

    // licenses.py's source of a local replace: <host/owner/name>@<commit>[+dirty][/<subdirectory>]
    private val CHECKOUT = Regex("""^([^@]+)@([0-9a-f]{40})(?:\+dirty)?(/.*)?$""")

    /**
     * A module's page: a local replace's (the core's fork and its third_party modules) is its repository's tree at
     * that commit, not pkg.go.dev, which knows no commit of a fork's folder; a module replaced by another, the other's.
     */
    fun site(path: String, version: String, source: String): String? {
        if (path == "go") return "https://go.dev/"
        CHECKOUT.matchEntire(source)?.let { match ->
            val (repository, commit, folder) = match.destructured
            // a checkout without a remote names only its folder: no page then
            return if (repository.contains('/')) "https://$repository/tree/$commit$folder" else null
        }
        return "https://pkg.go.dev/" + source.ifBlank { "$path@$version" }
    }

    // a chunk of a long text closes at a blank line once it reaches this many characters, or at any line past four
    // times as many: Chromium's notice (260 KB) would take one TextView seconds to lay out, all of it at once
    private const val CHUNK = 2000

    /**
     * [text] in pieces of about [CHUNK] characters, at paragraph boundaries where it has them: a view each, laid out
     * as it scrolls in. Joined, the pieces are [text] again.
     */
    fun chunks(text: String): List<String> {
        val out = ArrayList<String>()
        val piece = StringBuilder()
        // a piece begun, empty lines too: its next line goes after a line break
        var begun = false
        for (line in text.split('\n')) {
            if (begun) piece.append('\n')
            piece.append(line)
            begun = true
            if (piece.length >= CHUNK && line.isBlank() || piece.length >= 4 * CHUNK) {
                out += piece.toString()
                piece.clear()
                begun = false
            }
        }
        if (begun || out.isEmpty()) out += piece.toString()
        return out
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).map { getString(it) }

    private fun read(context: Context): List<Section> {
        fun text(path: String) = context.assets.open(path).bufferedReader().use { it.readText() }
        val out = mutableListOf<Section>()
        out += Section(null, listOf(Entry("NOTICE", context.getString(R.string.licenses_notice_sum), null) {
            listOf("NOTICE" to text("NOTICE"))
        }))

        val components = JSONObject(text("licenses/components.json")).getJSONArray("components")
        val byPart = (0 until components.length()).map { components.getJSONObject(it) }.groupBy { it.optString("part") }
        fun entry(c: JSONObject): Entry {
            val texts = c.optJSONArray("texts").strings()
            return Entry(
                c.getString("name"),
                c.optJSONArray("licenses").strings().joinToString(" · "),
                c.optString("url").ifBlank { null },
            ) { texts.map { it to text("licenses/texts/$it") } }
        }
        for ((part, title) in PARTS) {
            byPart[part]?.let { out += Section(title, it.map(::entry)) }
        }
        // a part this list does not know yet: under a group of its own, not left out
        byPart.filterKeys { part -> PARTS.none { it.first == part } }.values.flatten().takeIf { it.isNotEmpty() }?.let {
            out += Section(R.string.licenses_part_other, it.map(::entry))
        }

        try {
            val go = JSONObject(text("licenses/go.json"))
            val texts = go.getJSONArray("texts")
            val modules = go.getJSONArray("modules")
            val entries = (0 until modules.length()).map { i ->
                val m = modules.getJSONObject(i)
                val path = m.getString("path")
                val version = m.optString("version")
                // a local replace's repository and commit (the core's fork), or the module that replaces this one
                val source = m.optString("source")
                val files = m.optJSONArray("files") ?: JSONArray()
                Entry(
                    path,
                    listOf(source.ifBlank { version }, m.optJSONArray("licenses").strings().joinToString(" · "))
                        .filter { it.isNotBlank() }.joinToString("  "),
                    site(path, version, source),
                ) {
                    (0 until files.length()).map { j ->
                        val f = files.getJSONObject(j)
                        f.getString("name") to texts.getString(f.getInt("text"))
                    }
                }
            }
            if (entries.isNotEmpty()) out += Section(R.string.licenses_part_go, entries)
        } catch (e: Exception) {
            // a library built before go.json: its modules stay in NOTICE's words only
            Logs.w(e)
        }
        return out
    }
}

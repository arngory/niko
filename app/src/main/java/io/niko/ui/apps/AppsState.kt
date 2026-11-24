package io.niko.ui.apps

// What the apps screen (P-Apps) shows and does to its choice, apart from the package manager and DataStore: the apps,
// the ones chosen (by uid, as the old screens chose them: two packages of one uid go together), the mode, the clipboard's
// text. Pure, for the tests and Paparazzi.

/** An installed app: its package, its label, its uid, whether the system's. */
class AppRow(val packageName: String, val name: String, val uid: Int, val system: Boolean)

/**
 * Через Niko (M-RULES-01, D-RULES-06): every app (DataStore.proxyApps off), only the ones chosen (on, bypass off), all
 * but the ones chosen (on, bypass on).
 */
enum class AppsMode {
    ALL, ONLY, EXCEPT;

    companion object {
        fun of(proxyApps: Boolean, bypass: Boolean) = when {
            !proxyApps -> ALL
            bypass -> EXCEPT
            else -> ONLY
        }
    }
}

/**
 * P-Apps: [forRule] the picker of a rule's apps (M-RULES-02, no mode) or the apps through the VPN; [apps] null while
 * read, empty when the system gives none (no access to the list); [chosen] the uids ticked; [pinned] the uids that head
 * the list (the ones chosen when it was read or last changed as a whole: a tick does not move its row away under the
 * finger); [missing] the packages of the choice that are not installed here (a list pasted from another phone, an app
 * removed): kept and written with the rest, so that they count once installed, and dropped only by «Снять выбор» or a
 * choice made anew; [system] the system's apps shown; [query] the search, over the name, the package and the uid.
 */
data class AppsState(
    val forRule: Boolean,
    val mode: AppsMode = AppsMode.ALL,
    val apps: List<AppRow>? = null,
    val chosen: Set<Int> = emptySet(),
    val pinned: Set<Int> = emptySet(),
    val missing: List<String> = emptyList(),
    val system: Boolean = !forRule,
    val query: String = "",
) {
    /** The apps the search and the system switch leave, the pinned ones first, each part by name. */
    val shown: Pair<List<AppRow>, List<AppRow>>
        get() {
            val q = query.trim()
            val left = apps.orEmpty().filter { app ->
                (system || !app.system) && (q.isEmpty() || app.name.contains(q, true) ||
                    app.packageName.contains(q, true) || app.uid.toString().contains(q))
            }
            return left.partition { it.uid in pinned }
        }

    /** How many packages are chosen: what is written, and what the main screen counts. */
    val count: Int get() = apps.orEmpty().count { it.uid in chosen }

    /**
     * The chosen packages as DataStore keeps them (individual) and a rule takes them: one a line, the installed ones in
     * the list's order, then the [missing] ones.
     */
    val packages: String
        get() = (apps.orEmpty().filter { it.uid in chosen }.map { it.packageName } + missing).joinToString("\n")

    fun toggle(uid: Int) = copy(chosen = if (uid in chosen) chosen - uid else chosen + uid)

    /** Every app the other way round (hidden ones too, as before); the new choice heads the list. */
    fun inverted(): AppsState {
        val all = apps.orEmpty().mapTo(HashSet()) { it.uid }
        val next = all - chosen
        return copy(chosen = next, pinned = next)
    }

    fun cleared() = copy(chosen = emptySet(), pinned = emptySet(), missing = emptyList())

    /** [packages] chosen in place of the choice, the ones not installed here kept as [missing]; they head the list. */
    fun choosing(packages: Collection<String>): AppsState {
        val wanted = packages.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        val installed = apps.orEmpty().associateBy { it.packageName }
        val next = wanted.mapNotNullTo(HashSet()) { installed[it]?.uid }
        return copy(chosen = next, pinned = next, missing = wanted.filter { it !in installed })
    }

    companion object {
        /** The apps by name; [chosen] the packages chosen, as their uids. */
        fun read(forRule: Boolean, mode: AppsMode, apps: List<AppRow>, chosen: Collection<String>, system: Boolean): AppsState {
            val sorted = apps.sortedBy { it.name.lowercase() }
            return AppsState(forRule, mode, sorted, system = system).choosing(chosen)
        }

        /**
         * The packages that auto-selection chooses (the old «Автоматический выбор прокси-приложений»): through the
         * VPN, the apps of [proxied] (the list in assets) and the system's uid 1000; all but them for [AppsMode.EXCEPT].
         */
        fun auto(apps: List<AppRow>, proxied: Set<String>, mode: AppsMode): List<String> = apps.filter { app ->
            val through = app.packageName in proxied || app.uid == 1000
            if (mode == AppsMode.EXCEPT) !through else through
        }.map { it.packageName }
    }
}

/**
 * The choice on the clipboard, as the old screens wrote it: the bypass ("true" or "false") on the first line, then the
 * packages a line. A rule's picker writes "false".
 */
object AppsClip {
    fun write(bypass: Boolean, packages: String) = "$bypass\n$packages"

    /** The bypass and the packages; null for no text. Text without a line break is the bypass alone, as before. */
    fun read(text: String?): Pair<Boolean, List<String>>? {
        if (text.isNullOrEmpty()) return null
        val i = text.indexOf('\n')
        val head = if (i < 0) text else text.substring(0, i)
        val rest = if (i < 0) "" else text.substring(i + 1)
        return head.trim().toBoolean() to rest.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
    }
}

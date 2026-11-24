package io.niko.ui.apps

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The apps screen's choice: by uid, the order written, the search, the old menu's actions, the clipboard's text. */
class AppsStateTest {

    private val apps = listOf(
        AppRow("org.telegram.messenger", "Telegram", 10_100, system = false),
        AppRow("ru.sberbankmobile", "Сбербанк Онлайн", 10_101, system = false),
        AppRow("com.android.chrome", "Chrome", 10_102, system = true),
        // two packages of one uid go together, as with the old screens
        AppRow("com.example.shared.a", "Shared A", 10_200, system = false),
        AppRow("com.example.shared.b", "Shared B", 10_200, system = false),
    )

    private fun read(vararg chosen: String, forRule: Boolean = false, system: Boolean = true) =
        AppsState.read(forRule, AppsMode.EXCEPT, apps, chosen.toList(), system)

    @Test
    fun modeFromTheOldKeys() {
        assertEquals(AppsMode.ALL, AppsMode.of(proxyApps = false, bypass = true))
        assertEquals(AppsMode.ALL, AppsMode.of(proxyApps = false, bypass = false))
        assertEquals(AppsMode.ONLY, AppsMode.of(proxyApps = true, bypass = false))
        assertEquals(AppsMode.EXCEPT, AppsMode.of(proxyApps = true, bypass = true))
    }

    @Test
    fun readSortsByNameAndPinsTheChosen() {
        val s = read("ru.sberbankmobile", "not.installed")
        assertEquals(listOf("Chrome", "Shared A", "Shared B", "Telegram", "Сбербанк Онлайн"), s.apps!!.map { it.name })
        val (pinned, rest) = s.shown
        assertEquals(listOf("ru.sberbankmobile"), pinned.map { it.packageName })
        assertEquals(4, rest.size)
        // a package not installed here is kept and written after the installed ones, but not counted
        assertEquals(listOf("not.installed"), s.missing)
        assertEquals("ru.sberbankmobile\nnot.installed", s.packages)
        assertEquals(1, s.count)
        // a tick keeps it; «Снять выбор» drops it
        assertEquals("org.telegram.messenger\nru.sberbankmobile\nnot.installed", s.toggle(10_100).packages)
        assertEquals("", s.cleared().packages)
    }

    @Test
    fun aTickDoesNotMoveItsRow() {
        val s = read("ru.sberbankmobile").toggle(10_100)
        assertEquals(setOf(10_100, 10_101), s.chosen)
        assertEquals(listOf("ru.sberbankmobile"), s.shown.first.map { it.packageName })
        assertEquals("org.telegram.messenger\nru.sberbankmobile", s.packages)
        assertEquals(setOf(10_101), s.toggle(10_100).toggle(10_100).toggle(10_100).chosen)
    }

    @Test
    fun oneUidChoosesItsPackagesTogether() {
        val s = read().toggle(10_200)
        assertEquals("com.example.shared.a\ncom.example.shared.b", s.packages)
        assertEquals(2, s.count)
        assertEquals(setOf(10_200), read("com.example.shared.b").chosen)
    }

    @Test
    fun searchMatchesNamePackageAndUid() {
        fun found(q: String, system: Boolean = true) =
            read(system = system).copy(query = q).shown.let { (a, b) -> (a + b).map { it.packageName } }
        assertEquals(listOf("org.telegram.messenger"), found("TELEGRAM"))
        assertEquals(listOf("ru.sberbankmobile"), found("sberbank"))
        assertEquals(listOf("com.android.chrome"), found("10102"))
        assertEquals(listOf("com.android.chrome"), found(" chrome "))
        // the system's apps hidden: Chrome is not found
        assertTrue(found("chrome", system = false).isEmpty())
    }

    @Test
    fun invertTakesEveryAppHiddenOnesToo() {
        val s = read("ru.sberbankmobile", system = false).copy(query = "telegram").inverted()
        assertEquals(setOf(10_100, 10_102, 10_200), s.chosen)
        assertEquals(s.chosen, s.pinned)
    }

    @Test
    fun clearEmptiesTheChoice() {
        val s = read("ru.sberbankmobile", "org.telegram.messenger").cleared()
        assertTrue(s.chosen.isEmpty())
        assertEquals("", s.packages)
        assertTrue(s.shown.first.isEmpty())
    }

    @Test
    fun autoChoosesTheListedAndUid1000OrAllButThem() {
        val withSystem = apps + AppRow("android", "Android System", 1000, system = true)
        val listed = setOf("org.telegram.messenger", "com.android.chrome")
        assertEquals(
            listOf("org.telegram.messenger", "com.android.chrome", "android"),
            AppsState.auto(withSystem, listed, AppsMode.ONLY),
        )
        assertEquals(
            listOf("ru.sberbankmobile", "com.example.shared.a", "com.example.shared.b"),
            AppsState.auto(withSystem, listed, AppsMode.EXCEPT),
        )
    }

    @Test
    fun clipboardAsTheOldScreensWroteIt() {
        assertEquals("true\na\nb", AppsClip.write(true, "a\nb"))
        assertEquals("false\n", AppsClip.write(false, ""))
        assertEquals(true to listOf("a", "b"), AppsClip.read("true\na\n\n b \n"))
        assertEquals(false to listOf("a"), AppsClip.read("false\na"))
        // the bypass alone, and text that is no bypass
        assertEquals(true to emptyList<String>(), AppsClip.read("true"))
        assertEquals(false to listOf("x"), AppsClip.read("org.example\nx"))
        assertNull(AppsClip.read(""))
        assertNull(AppsClip.read(null))
    }

    @Test
    fun importChoosesThePastedPackages() {
        val (_, packages) = AppsClip.read("true\norg.telegram.messenger\ncom.android.chrome\nfrom.another.phone")!!
        val s = read("ru.sberbankmobile", "old.missing").choosing(packages)
        assertEquals(setOf(10_100, 10_102), s.chosen)
        // what this phone lacks is kept, what was missing before is replaced with the rest of the choice
        assertEquals("com.android.chrome\norg.telegram.messenger\nfrom.another.phone", s.packages)
    }
}

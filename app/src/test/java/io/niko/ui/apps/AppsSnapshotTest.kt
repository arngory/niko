package io.niko.ui.apps

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.ui.compose.NikoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The apps screen as images, light and dark, on the canvas's data (P-Apps): the apps through the VPN with three chosen,
 * its mode sheet and ⋮, the search, a rule's picker, the list not given and still read.
 */
@RunWith(Parameterized::class)
class AppsSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")
    }

    @get:Rule
    val paparazzi = Paparazzi(
        // the canvas's artboard: 412 by 915, in Russian
        deviceConfig = DeviceConfig.PIXEL_5.copy(
            screenWidth = 412 * 3,
            screenHeight = 915 * 3,
            xdpi = 480,
            ydpi = 480,
            density = Density.XXHIGH,
            locale = "ru",
            nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT,
        ),
    )

    private val none = object : AppsActions {
        override fun back() {}
        override fun mode(mode: AppsMode) {}
        override fun toggle(uid: Int) {}
        override fun query(text: String) {}
        override fun system(on: Boolean) {}
        override fun invert() {}
        override fun clear() {}
        override fun auto() {}
        override fun export() {}
        override fun import() {}
        override fun openSettings() {}
    }

    // P-Apps: Сбербанк, Госуслуги, Яндекс Go chosen; Chrome, Telegram, YouTube not
    private val apps = listOf(
        AppRow("ru.sberbankmobile", "Сбербанк Онлайн", 10_101, system = false),
        AppRow("ru.rostel", "Госуслуги", 10_102, system = false),
        AppRow("ru.yandex.taxi", "Яндекс Go", 10_103, system = false),
        AppRow("com.android.chrome", "Chrome", 10_104, system = true),
        AppRow("org.telegram.messenger", "Telegram", 10_105, system = false),
        AppRow("com.google.android.youtube", "YouTube", 10_106, system = false),
    )

    private val vpn = AppsState.read(
        forRule = false, AppsMode.EXCEPT, apps, listOf("ru.sberbankmobile", "ru.rostel", "ru.yandex.taxi"), system = true,
    )

    private fun shot(state: AppsState, menu: Boolean = false, sheet: Boolean = false) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") { AppsScreen(state, none, menuOpen = menu, modeOpen = sheet) }
    }

    @Test
    fun apps() = shot(vpn)

    @Test
    fun modeSheet() = shot(vpn, sheet = true)

    /** M-RULES-03: the system's apps, invert, clear, auto-selection, the clipboard. */
    @Test
    fun menu() = shot(vpn, menu = true)

    @Test
    fun search() = shot(vpn.copy(query = "te"))

    /** M-RULES-02: a rule's apps, no mode; the system's apps hidden, as the old picker had them; no auto-selection. */
    @Test
    fun rule() = shot(AppsState.read(forRule = true, AppsMode.ALL, apps, listOf("org.telegram.messenger"), system = false))

    @Test
    fun ruleMenu() = shot(AppsState.read(forRule = true, AppsMode.ALL, apps, emptyList(), system = false), menu = true)

    /** The system gave no list: why, and the way to the app's settings. */
    @Test
    fun denied() = shot(AppsState(forRule = false, mode = AppsMode.ONLY, apps = emptyList()))

    @Test
    fun loading() = shot(AppsState(forRule = false, mode = AppsMode.ALL))
}

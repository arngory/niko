package io.niko.ui.about

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.android.resources.NightMode
import io.niko.R
import io.niko.ui.compose.NikoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * О программе (P-About) and the licenses as images, light and dark, on the canvas's data: the update found, being
 * checked and failed, the limits on the background as the phone has them, a plugin; the licenses' list and a text.
 */
@RunWith(Parameterized::class)
class AboutSnapshotTest(private val mode: String) {

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

    private val none = object : AboutActions {
        override fun back() {}
        override fun releases() {}
        override fun update() {}
        override fun preview(on: Boolean) {}
        override fun battery() {}
        override fun restricted() {}
        override fun vendor() {}
        override fun plugin(row: PluginRow) {}
        override fun licenses() {}
        override fun license() {}
        override fun source() {}
        override fun upstream() {}
        override fun donate() {}
    }

    // P-About: 1.56.0, the update 1.57.0, the optimization off, no restriction, a vendor's manager, a plugin
    private val about = AboutState(
        "1.56.0", "1.14.4", UpdateState.Found("1.57.0"), preview = false,
        batteryOptimized = false, restricted = false, vendor = true,
        plugins = listOf(PluginRow("trojan-go", "Trojan-Go", "com.example.plugin", "1.0.2", trusted = true)),
    )

    private fun shot(content: @androidx.compose.runtime.Composable () -> Unit) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") { content() }
    }

    @Test
    fun about() = shot { AboutScreen(about, none) }

    /** The check running, the optimization on and the use restricted: their rows lead to the system's settings. */
    @Test
    fun checking() = shot {
        AboutScreen(
            about.copy(update = UpdateState.Checking, preview = true, batteryOptimized = true, restricted = true, vendor = false, plugins = emptyList()),
            none,
        )
    }

    @Test
    fun failed() = shot { AboutScreen(about.copy(update = UpdateState.Failed("GitHub: context deadline exceeded")), none) }

    @Test
    fun licenses() = shot {
        LicensesScreen(
            listOf(
                LicenseRow(0, 0, null, "NOTICE", "Всё, что входит в APK, и их лицензии"),
                LicenseRow(1, 0, R.string.licenses_part_app, "Niko", "GPL-3.0"),
                LicenseRow(1, 1, R.string.licenses_part_app, "NekoBox for Android", "GPL-3.0"),
                LicenseRow(2, 0, R.string.licenses_part_library, "AndroidX", "Apache-2.0"),
                LicenseRow(2, 1, R.string.licenses_part_library, "Kryo", "BSD-3-Clause"),
                LicenseRow(3, 0, R.string.licenses_part_go, "github.com/sagernet/sing-box", "v1.14.4  GPL-3.0-or-later"),
            ),
            onBack = {},
            onOpen = {},
        )
    }

    @Test
    fun licenseText() = shot {
        LicenseTextScreen(
            "Kryo", "BSD-3-Clause",
            Licenses.chunks("Copyright (c) 2008-2023, Nathan Sweet\nAll rights reserved.\n\nRedistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:\n\n- Redistributions of source code must retain the above copyright notice."),
            onBack = {},
            site = {},
        )
    }
}

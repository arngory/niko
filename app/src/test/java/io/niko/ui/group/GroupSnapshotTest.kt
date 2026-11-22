package io.niko.ui.group

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.NightMode
import io.niko.GroupType
import io.niko.ui.compose.NikoTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** The group's settings (P-SubSettings) as images, light and dark: a provider's subscription, an own group. */
@RunWith(Parameterized::class)
class GroupSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT),
    )

    private val none = object : GroupActions {
        override fun back() {}
        override fun share() {}
        override fun delete() {}
        override fun copyLink(link: String) {}
        override fun pickProxy(front: Boolean) {}
    }

    private fun shot(form: GroupForm, info: GroupInfo) = paparazzi.snapshot {
        NikoTheme(dark = mode == "dark") { GroupScreen(form, info, {}, none) }
    }

    @Test
    fun subscription() = shot(
        GroupForm(
            name = "provider.net", type = GroupType.SUBSCRIPTION, link = "https://provider.net/sub/4f2a",
            autoUpdate = true, autoUpdateDelay = 720, deduplication = true,
        ),
        GroupInfo(existing = true),
    )

    @Test
    fun ownGroup() = shot(GroupForm(name = "Свои", autoSelect = true), GroupInfo(existing = true, frontName = "Helsinki · REALITY"))
}

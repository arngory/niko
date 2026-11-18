package io.niko.ui

import android.graphics.Typeface
import android.view.View
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.drawerlayout.widget.DrawerLayout
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.NightMode
import com.google.android.material.progressindicator.BaseProgressIndicator
import io.niko.R
import io.niko.bg.BaseService
import io.niko.widget.ServiceButton
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/**
 * The Niko theme's parts as images, in the light and the dark theme (Paparazzi, no emulator): CI compares them with
 * the goldens in app/src/test/snapshots and fails on a difference. To take new goldens, delete that folder in the pull
 * request: CI records them into the artifact paparazzi-goldens, to commit (CONTRIBUTING).
 */
@RunWith(Parameterized::class)
class NikoSnapshotTest(private val mode: String) {

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun modes(): List<String> = listOf("light", "dark")
    }

    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(nightMode = if (mode == "dark") NightMode.NIGHT else NightMode.NOTNIGHT),
        theme = "Theme.SagerNet.Niko",
        renderingMode = SessionParams.RenderingMode.SHRINK,
    )

    private fun View.text(id: Int, text: String) {
        findViewById<TextView>(id).text = text
    }

    private fun profile(selected: Boolean): View = paparazzi.inflate<View>(R.layout.layout_profile).apply {
        text(R.id.profile_name, "Home node")
        text(R.id.profile_address, "203.0.113.7:443")
        text(R.id.traffic_text, "↑ 1.2 MB ↓ 34 MB")
        text(R.id.profile_type, "Naïve")
        text(R.id.profile_status, "128 ms")
        findViewById<View>(R.id.selected_view).isVisible = selected
        if (selected) findViewById<TextView>(R.id.profile_name).setTypeface(null, Typeface.BOLD)
    }

    @Test
    fun profileCard() = paparazzi.snapshot(profile(selected = false))

    @Test
    fun profileCardSelected() = paparazzi.snapshot(profile(selected = true))

    @Test
    fun groupCard() = paparazzi.snapshot(paparazzi.inflate<View>(R.layout.layout_group_item).apply {
        text(R.id.group_name, "Home node")
        text(R.id.group_user, "3 profiles")
        text(R.id.group_traffic, "↑ 1.2 MB ↓ 34 MB")
        text(R.id.group_status, "Updated 5 min ago")
    })

    /** The main screen with its connect button in [state] ([failed]: after a start that failed). */
    private fun main(state: BaseService.State, failed: Boolean = false): View {
        // the whole screen, not shrunk to its content
        paparazzi.unsafeUpdateConfig(renderingMode = SessionParams.RenderingMode.NORMAL)
        return paparazzi.inflate<View>(R.layout.layout_main).apply {
            // the drawer of the colour themes' Black goes, as MainActivity does: one drawer to an edge
            (this as DrawerLayout).removeView(findViewById(R.id.nav_view_black))
            val fab = findViewById<ServiceButton>(R.id.fab)
            fab.initProgress(findViewById<BaseProgressIndicator<*>>(R.id.fabProgress))
            fab.changeState(state, BaseService.State.Stopped, animate = false, failed = failed)
        }
    }

    @Test
    fun mainStopped() = paparazzi.snapshot(main(BaseService.State.Stopped))

    @Test
    fun mainConnected() = paparazzi.snapshot(main(BaseService.State.Connected))

    @Test
    fun mainFailed() = paparazzi.snapshot(main(BaseService.State.Stopped, failed = true))
}

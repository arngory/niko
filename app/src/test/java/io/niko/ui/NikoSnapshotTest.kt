package io.niko.ui

import android.view.View
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceManager
import androidx.preference.SwitchPreference
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.ide.common.rendering.api.SessionParams
import com.android.resources.NightMode
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.niko.R
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

    /** A column of [content] on surface-0 at the screen's width, space-4 around it and space-2 between. */
    private fun column(content: LinearLayout.() -> Unit): View = LinearLayout(paparazzi.context).apply {
        orientation = LinearLayout.VERTICAL
        val space = resources.getDimensionPixelSize(R.dimen.niko_space_4)
        setPadding(space, space, space, space)
        setBackgroundColor(context.getColor(R.color.niko_surface_0))
        layoutParams = ViewGroup.LayoutParams(resources.displayMetrics.widthPixels, ViewGroup.LayoutParams.WRAP_CONTENT)
        content()
    }

    private fun LinearLayout.item(view: View) = addView(
        view,
        LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            bottomMargin = resources.getDimensionPixelSize(R.dimen.niko_space_2)
        },
    )

    /** Buttons (reskin step 2) from the theme: the primary, the tonal (a tool's action), the ghost, a disabled one. */
    @Test
    fun buttons() = paparazzi.snapshot(column {
        fun button(text: String, style: Int, enabled: Boolean = true) = item(MaterialButton(context, null, style).apply {
            this.text = text
            isEnabled = enabled
        })
        button("Open app settings", R.attr.materialButtonStyle)
        button("Start", R.attr.nikoTonalButtonStyle)
        button("Update", R.attr.borderlessButtonStyle)
        button("Open app settings", R.attr.materialButtonStyle, enabled = false)
    })

    /** Chips from the theme: a group to choose one of (the per-app mode), on and off (the system's apps), a tag. */
    @Test
    fun chips() = paparazzi.snapshot(column {
        item(ChipGroup(context).apply {
            isSingleSelection = true
            for (mode in listOf("Off", "Proxy", "Bypass")) addView(Chip(context).apply {
                text = mode
                isChecked = mode == "Bypass"
            })
        })
        item(Chip(context, null, R.attr.nikoFilterChipStyle).apply {
            text = "Show system apps"
            isChecked = true
        })
        item(Chip(context, null, R.attr.nikoFilterChipStyle).apply { text = "Show system apps" })
        item(Chip(context).apply {
            text = "Auto select"
            isCheckable = false
        })
    })

    /** Text fields (reskin step 3): a label alone, a value, an error, a plain EditText as dialogs build in code. */
    @Test
    fun textFields() = paparazzi.snapshot(column {
        fun field(label: String, value: String = "", error: String? = null) = addView(
            TextInputLayout(context).apply {
                hint = label
                addView(TextInputEditText(this.context).apply { setText(value) })
                this.error = error
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = resources.getDimensionPixelSize(R.dimen.niko_space_2)
            },
        )
        field("Server")
        field("Server", "node.example.com")
        field("Port", "70000", error = "Not a port")
        addView(EditText(context).apply { hint = "Ports, as 80,443" }, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT,
        ))
    })

    /**
     * Settings' rows (D-SET, reskin step 2) as PreferenceFragmentCompat shows them: its theme overlay, the library's
     * adapter and NikoPreferenceGroups' groups; a switch on and off, a choice, a text, a row that cannot change.
     */
    private fun settings(): View {
        val context = ContextThemeWrapper(paparazzi.context, R.style.PreferenceThemeOverlay_Niko)
        val screen = PreferenceManager(context).createPreferenceScreen(context)
        fun category(title: String) = PreferenceCategory(context).also {
            it.title = title
            screen.addPreference(it)
        }
        fun <T : Preference> T.row(title: String, icon: Int? = null): T = apply {
            this.title = title
            isPersistent = false
            if (icon != null) setIcon(icon)
        }
        category("General").apply {
            addPreference(SwitchPreference(context).row("Connect on start", R.drawable.ic_l_power).apply {
                summary = "Connects when the phone starts"
                isChecked = true
            })
            addPreference(SimpleMenuPreference(context).row("Service mode", R.drawable.ic_l_router).apply {
                entries = arrayOf("VPN", "Proxy only")
                entryValues = arrayOf("vpn", "proxy")
                value = "vpn"
                summaryProvider = ListPreference.SimpleSummaryProvider.getInstance()
            })
            addPreference(SwitchPreference(context).row("Traffic statistics").apply { isChecked = false })
        }
        category("Inbound").apply {
            addPreference(EditTextPreference(context).row("Mixed port", R.drawable.ic_l_cable).apply {
                summary = "2080"
            })
            addPreference(SwitchPreference(context).row("Allow connections from the LAN").apply {
                summary = "Only with a password"
                isChecked = true
                isEnabled = false
            })
        }
        return RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            adapter = PreferenceGroupAdapter(screen)
            layoutParams = ViewGroup.LayoutParams(resources.displayMetrics.widthPixels, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundColor(context.getColor(R.color.niko_surface_0))
            NikoPreferenceGroups.decorate(this) { screen }
        }
    }

    @Test
    fun settingsRows() = paparazzi.snapshot(settings())
}

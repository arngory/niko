package io.niko.ui

import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.View
import android.view.ContextThemeWrapper
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.isVisible
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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.progressindicator.BaseProgressIndicator
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.niko.R
import io.niko.bg.BaseService
import io.niko.ktx.danger
import io.niko.ktx.setField
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

    /** [layout] at the screen's width, as a list shows it: shrunk to its content, a row's spacers take nothing. */
    private fun card(layout: Int): View = paparazzi.inflate<View>(layout).apply {
        layoutParams = ViewGroup.LayoutParams(resources.displayMetrics.widthPixels, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    // as ktx's getColorAttr, kept here so the snapshots do not load ktx's Utils (it reaches the app's DataStore and core)
    private fun View.colorAttr(attr: Int): Int =
        context.getColor(TypedValue().also { context.theme.resolveAttribute(attr, it, true) }.resourceId)

    private fun profile(selected: Boolean): View = card(R.layout.layout_profile).apply {
        text(R.id.profile_name, "Home node")
        text(R.id.profile_address, "203.0.113.7:443")
        text(R.id.traffic_text, "↑ 1.2 MB ↓ 34 MB")
        text(R.id.profile_type, "Naïve")
        text(R.id.profile_status, "128 ms")
        // as ConfigurationFragment shows a profile with traffic and a ping: the address row, the status in ok's colour
        (findViewById<View>(R.id.traffic_text).parent as View).isVisible = true
        findViewById<TextView>(R.id.profile_status).setTextColor(colorAttr(R.attr.statusOkColor))
        findViewById<View>(R.id.selected_view).isVisible = selected
        if (selected) findViewById<TextView>(R.id.profile_name).setTypeface(null, Typeface.BOLD)
    }

    @Test
    fun profileCard() = paparazzi.snapshot(profile(selected = false))

    @Test
    fun profileCardSelected() = paparazzi.snapshot(profile(selected = true))

    @Test
    fun groupCard() = paparazzi.snapshot(card(R.layout.layout_group_item).apply {
        text(R.id.group_name, "Home node")
        text(R.id.group_user, "3 profiles")
        text(R.id.group_traffic, "↑ 1.2 MB ↓ 34 MB")
        text(R.id.group_status, "Updated 5 min ago")
    })

    @Test
    fun routeCard() = paparazzi.snapshot(card(R.layout.layout_route_item).apply {
        text(R.id.profile_name, "Russian sites")
        text(R.id.profile_type, "geosite:category-ru\ngeoip:ru")
        text(R.id.route_outbound, "Bypass")
        findViewById<SwitchCompat>(R.id.enable).isChecked = true
    })

    @Test
    fun assetCard() = paparazzi.snapshot(card(R.layout.layout_asset_item).apply {
        text(R.id.asset_name, "geosite.db")
        text(R.id.asset_status, "Local version: 20261118")
    })

    @Test
    fun appRow() = paparazzi.snapshot(card(R.layout.layout_apps_item).apply {
        // an app's icon: a plain tile (the framework's default icon is adaptive, which layoutlib does not draw)
        findViewById<ImageView>(R.id.itemicon).setImageDrawable(GradientDrawable().apply {
            cornerRadius = resources.getDimension(R.dimen.niko_radius_sm)
            setColor(context.getColor(R.color.niko_surface_3))
        })
        text(R.id.title, "Browser")
        text(R.id.desc, "org.example.browser (10123)")
        findViewById<SwitchCompat>(R.id.itemcheck).isChecked = true
    })

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

    /** A dialog (m-dialog) as the app builds one, shown in its window's frame: [build] adds to the builder. */
    private fun dialog(build: MaterialAlertDialogBuilder.() -> Unit, after: (androidx.appcompat.app.AlertDialog) -> Unit = {}): View {
        val dialog = MaterialAlertDialogBuilder(paparazzi.context).apply(build).create()
        dialog.create()
        after(dialog)
        return dialog.window!!.decorView
    }

    /** A confirmation that deletes: its action in danger (ktx's danger). */
    @Test
    fun dialogConfirm() = paparazzi.snapshot(dialog({
        setTitle("Clear the group?")
        setMessage("Its 12 profiles are removed from the phone.")
        setPositiveButton("Clear", null)
        setNegativeButton("Cancel", null)
    }) { it.danger() })

    /** A dialog with a field (ktx's setField): its title, a text field, two actions and a third at the start. */
    @Test
    fun dialogField() = paparazzi.snapshot(dialog({
        setTitle("Route test")
        setField(TextInputLayout(context).apply {
            hint = "Domain or address"
            addView(TextInputEditText(this.context).apply { setText("example.com") })
        })
        setPositiveButton("Test", null)
        setNegativeButton("Cancel", null)
        setNeutralButton("Clear", null)
    }))

    /** The main screen with its connect button in [state] ([failed]: after a start that failed). */
    private fun main(state: BaseService.State, failed: Boolean = false): View {
        // the whole screen, not shrunk to its content
        paparazzi.unsafeUpdateConfig(renderingMode = SessionParams.RenderingMode.NORMAL)
        return paparazzi.inflate<View>(R.layout.layout_main).apply {
            val fab = findViewById<ServiceButton>(R.id.fab)
            fab.initProgress(findViewById<BaseProgressIndicator<*>>(R.id.fabProgress))
            fab.changeState(state, BaseService.State.Stopped, animate = false, failed = failed)
        }
    }

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

    @Test
    fun mainStopped() = paparazzi.snapshot(main(BaseService.State.Stopped))

    @Test
    fun mainConnected() = paparazzi.snapshot(main(BaseService.State.Connected))

    @Test
    fun mainFailed() = paparazzi.snapshot(main(BaseService.State.Stopped, failed = true))
}

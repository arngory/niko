package io.niko.ui

import android.Manifest
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.fmt.MeshPorts
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ktx.snackbar
import io.niko.raycontrol.RayLanDirect
import io.niko.raycontrol.RayLanNet
import io.niko.raycontrol.RayNode
import io.niko.raycontrol.RayNodes

/**
 * The node's local network (lan-v1): whether this device shows itself to the user's other devices, the ones shown,
 * by name, and the ports they reach here. The row lookup reads the database: off the main thread, then the dialog.
 */
fun Fragment.showRayLanDialog(nodeId: String) = runOnDefaultDispatcher {
    val node = RayNodes.get(nodeId) ?: return@runOnDefaultDispatcher
    val hasRow = node.lan?.let { RayLanNet.row(node, it.svc) } != null
    onMainDispatcher { if (isAdded) lanDialog(node, hasRow) }
}

private fun Fragment.lanDialog(node: RayNode, hasRow: Boolean) {
    val context = requireContext()
    val density = resources.displayMetrics.density
    val show = MaterialSwitch(context).apply {
        setText(R.string.raycontrol_lan_show)
        isChecked = node.lanShow == true
    }
    val now = System.currentTimeMillis() / 1000
    val lan = node.lan
    val lines = mutableListOf(getString(R.string.raycontrol_lan_help))
    when {
        lan == null -> lines += getString(R.string.raycontrol_lan_not_offered)
        !hasRow -> lines += getString(R.string.raycontrol_lan_no_profile)
        node.lanShow == true && !lan.shown -> lines += getString(R.string.raycontrol_lan_waiting)
    }
    if (lan != null && node.lanShow == true && lan.shown) {
        lines += if (lan.relayMbps > 0) getString(R.string.raycontrol_lan_relay, lan.relayMbps)
        else getString(R.string.raycontrol_lan_relay_off)
        // the names as the config answers them (a clash of two nodes' labels resolved there)
        val names = RayLanNet.names(RayNodes.list(), now).entries
        val devices = lan.others(node.deviceId)
        lines += if (devices.isEmpty()) getString(R.string.raycontrol_lan_none)
        else devices.joinToString("\n") { device ->
            val name = names.firstOrNull { it.key.startsWith(device.label + ".") && it.value == device.addressAt(now + node.clockSkew) }?.key
            getString(R.string.raycontrol_lan_device, device.name, name ?: device.addressAt(now + node.clockSkew))
        }
    }
    val status = TextView(context).apply {
        text = lines.joinToString("\n\n")
        setTextIsSelectable(true)
        setPadding(0, (12 * density).toInt(), 0, (12 * density).toInt())
    }
    val ports = EditText(context).apply {
        hint = getString(R.string.raycontrol_lan_ports)
        setText(DataStore.lanInboundPorts.orEmpty())
        inputType = InputType.TYPE_CLASS_TEXT
    }
    val portsHelp = TextView(context).apply { setText(R.string.raycontrol_lan_ports_help) }
    val directSection = directSection(density)
    val view = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (20 * density).toInt()
        setPadding(pad, pad / 2, pad, 0)
        addView(show)
        addView(status)
        addView(ports)
        addView(portsHelp)
        addView(directSection.view)
    }
    MaterialAlertDialogBuilder(context)
        .setTitle(R.string.raycontrol_lan)
        .setView(android.widget.ScrollView(context).apply { addView(view) })
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val typed = ports.text.toString().trim()
            val portsChanged = typed != DataStore.lanInboundPorts.orEmpty().trim()
            if (typed.isNotEmpty() && MeshPorts.parse(typed) == null) {
                snackbar(getString(R.string.raycontrol_lan_ports_bad, typed)).show()
            } else if (portsChanged) {
                DataStore.lanInboundPorts = typed
                // the running core (another process) takes the ports at once, a closed one too; ignored when stopped
                runOnDefaultDispatcher {
                    if (RayNodes.list().any { RayLanNet.of(it) != null }) SagerNet.reloadService(force = true)
                }
            }
            directSection.save()
            val wanted = show.isChecked
            if (wanted != (node.lanShow == true)) runOnDefaultDispatcher {
                RayLanNet.setShow(node.spa.nodeId, wanted)
                onMainDispatcher {
                    if (isAdded) snackbar(if (wanted) R.string.raycontrol_lan_shown else R.string.raycontrol_lan_hidden).show()
                }
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
        .show()
}

/** lan-v1 §3 in the dialog: the switch, how a Wi-Fi is known, the trusted ones; [save] keeps what was changed. */
private class DirectSection(val view: View, val save: () -> Unit)

private fun Fragment.directSection(density: Float): DirectSection {
    val context = requireContext()
    val gap = (12 * density).toInt()
    val trusted = RayLanDirect.trusted().toMutableList()
    val direct = MaterialSwitch(context).apply {
        setText(R.string.raycontrol_lan_direct)
        isChecked = DataStore.lanDirect
    }
    val help = TextView(context).apply { setText(R.string.raycontrol_lan_direct_help) }
    val byFingerprint = RadioButton(context).apply {
        id = View.generateViewId()
        setText(R.string.raycontrol_lan_direct_by_fingerprint)
    }
    val bySsid = RadioButton(context).apply {
        id = View.generateViewId()
        setText(R.string.raycontrol_lan_direct_by_ssid)
    }
    val by = RadioGroup(context).apply {
        addView(byFingerprint)
        addView(bySsid)
        check(if (DataStore.lanDirectBy == RayLanDirect.BY_SSID) bySsid.id else byFingerprint.id)
    }
    val list = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
    val note = TextView(context).apply { setPadding(0, gap / 2, 0, 0) }
    val allow = MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
        setText(R.string.raycontrol_lan_allow)
    }
    val trust = MaterialButton(context, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
        setText(R.string.raycontrol_lan_trust)
        setIconResource(R.drawable.ic_l_wifi)
    }
    val details = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        addView(help)
        addView(by)
        addView(list)
        addView(note)
        addView(allow)
        addView(trust)
    }

    fun ssidMode() = by.checkedRadioButtonId == bySsid.id

    lateinit var refresh: () -> Unit
    refresh = {
        details.visibility = if (direct.isChecked) View.VISIBLE else View.GONE
        val current = RayLanDirect.current()
        list.removeAllViews()
        if (trusted.isEmpty()) list.addView(TextView(context).apply { setText(R.string.raycontrol_lan_trusted_none) })
        trusted.forEach { network ->
            list.addView(LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(TextView(context).apply { text = network.name }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(ImageButton(context).apply {
                    setImageResource(R.drawable.ic_l_x)
                    background = null
                    contentDescription = getString(R.string.raycontrol_lan_forget, network.name)
                    setOnClickListener {
                        trusted.remove(network)
                        refresh()
                    }
                })
            })
        }
        // the SSID: the permission while the app is open, "all the time" with it closed, and location on
        val missing = when {
            !ssidMode() -> null
            !RayLanDirect.locationGranted() -> R.string.raycontrol_lan_location
            !RayLanDirect.backgroundGranted() -> R.string.raycontrol_lan_location_background
            current != null && current.ssid == null -> R.string.raycontrol_lan_location_off
            else -> null
        }
        note.visibility = if (missing != null || current == null || RayLanDirect.matches(current, trusted, mode(ssidMode()))) View.VISIBLE else View.GONE
        note.text = when {
            missing != null -> getString(missing)
            current == null -> getString(R.string.raycontrol_lan_not_wifi)
            else -> getString(R.string.raycontrol_lan_this_trusted)
        }
        allow.visibility = if (missing == R.string.raycontrol_lan_location || missing == R.string.raycontrol_lan_location_background) View.VISIBLE else View.GONE
        val canTrust = current != null && !RayLanDirect.matches(current, trusted, mode(ssidMode())) && (!ssidMode() || current.ssid != null)
        trust.visibility = if (canTrust) View.VISIBLE else View.GONE
    }
    direct.setOnCheckedChangeListener { _, _ -> refresh() }
    by.setOnCheckedChangeListener { _, _ -> refresh() }
    allow.setOnClickListener {
        val activity = activity ?: return@setOnClickListener
        // the background permission is asked apart, after the foreground one (Android 11+ sends to the settings)
        val asked = if (!RayLanDirect.locationGranted()) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        else arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        ActivityCompat.requestPermissions(activity, asked, 0)
    }
    trust.setOnClickListener {
        val current = RayLanDirect.current() ?: return@setOnClickListener refresh()
        val name = current.ssid ?: getString(R.string.raycontrol_lan_wifi_n, trusted.size + 1)
        trusted.removeAll { it.key == current.key }
        trusted += RayLanDirect.Trusted(name, current.key, current.ssid)
        refresh()
    }
    val view = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, gap, 0, gap)
        addView(direct)
        addView(details)
        // back from the permission's dialog or the settings: what it changed is shown
        viewTreeObserver.addOnWindowFocusChangeListener { focused -> if (focused) refresh() }
    }
    refresh()
    return DirectSection(view) {
        val mode = mode(ssidMode())
        val before = Triple(DataStore.lanDirect, DataStore.lanDirectBy, RayLanDirect.trusted())
        if (before != Triple(direct.isChecked, mode, trusted.toList())) {
            DataStore.lanDirect = direct.isChecked
            DataStore.lanDirectBy = mode
            RayLanDirect.save(trusted)
            // the running core takes the direct link on or off at once; ignored when stopped
            runOnDefaultDispatcher { RayLanNet.reloadIfChanged(System.currentTimeMillis() / 1000) }
        }
    }
}

private fun mode(ssid: Boolean) = if (ssid) RayLanDirect.BY_SSID else RayLanDirect.BY_FINGERPRINT

package io.niko.ui

import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.materialswitch.MaterialSwitch
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.fmt.MeshPorts
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ktx.snackbar
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
    val view = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        val pad = (20 * density).toInt()
        setPadding(pad, pad / 2, pad, 0)
        addView(show)
        addView(status)
        addView(ports)
        addView(portsHelp)
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
                if (RayLanNet.running) SagerNet.reloadService(force = true)
            }
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

package io.niko.ui.node

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.fmt.MeshPorts
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.raycontrol.RayLanDirect
import io.niko.raycontrol.RayLanNet
import io.niko.raycontrol.RayNodes
import io.niko.ui.ThemedActivity
import io.niko.ui.compose.NikoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Локальная сеть узла on Compose (P-Lan), in place of the dialog: whether this device shows itself to the user's
 * other devices, the ones shown, by name, the ports they reach here, and the direct link in a trusted Wi-Fi (lan-v1
 * §3). A change is kept at once, where the dialog kept them all on «OK».
 */
class LanActivity : ThemedActivity() {

    companion object {
        private const val EXTRA_NODE = "node"
        private const val PORTS_EDIT = "portsEdit"

        fun intent(context: Context, nodeId: String) = Intent(context, LanActivity::class.java).putExtra(EXTRA_NODE, nodeId)
    }

    private var state by mutableStateOf(LanState())
    private val nodeId get() = intent.getStringExtra(EXTRA_NODE).orEmpty()

    override fun snackbarInternal(text: CharSequence): Snackbar =
        Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(PORTS_EDIT)?.let { state = state.copy(portsEdit = it) }
        setContent { NikoTheme { LanScreen(state, actions) } }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        state.portsEdit?.let { outState.putString(PORTS_EDIT, it) }
    }

    // back from the permission's dialog or the system's settings too: what they changed is shown
    override fun onResume() {
        super.onResume()
        read()
    }

    private fun mode(ssid: Boolean) = if (ssid) RayLanDirect.BY_SSID else RayLanDirect.BY_FINGERPRINT

    /** The node, the settings and the Wi-Fi as they are now; the node's rows are read off the main thread. */
    private fun read() {
        lifecycleScope.launch(Dispatchers.Default) {
            val node = RayNodes.get(nodeId)
            if (node == null) {
                withContext(Dispatchers.Main) { finish() }
                return@launch
            }
            val lan = node.lan
            val hasRow = lan?.let { RayLanNet.row(node, it.svc) } != null
            val now = System.currentTimeMillis() / 1000
            val shown = node.lanShow == true
            val notes = buildList {
                when {
                    lan == null -> add(getString(R.string.raycontrol_lan_not_offered))
                    !hasRow -> add(getString(R.string.raycontrol_lan_no_profile))
                    shown && !lan.shown -> add(getString(R.string.raycontrol_lan_waiting))
                }
                if (lan != null && shown && lan.shown) {
                    add(if (lan.relayMbps > 0) getString(R.string.raycontrol_lan_relay, lan.relayMbps) else getString(R.string.raycontrol_lan_relay_off))
                }
            }
            val devices = if (lan != null && shown && lan.shown) {
                // the names as the config answers them (a clash of two nodes' labels resolved there)
                val names = RayLanNet.names(RayNodes.list(), now).entries
                lan.others(node.deviceId).map { device ->
                    val address = device.addressAt(now + node.clockSkew)
                    val name = names.firstOrNull { it.key.startsWith(device.label + ".") && it.value == address }?.key
                    LanDevice(device.name, name ?: address)
                }
            } else null
            val bySsid = DataStore.lanDirectBy == RayLanDirect.BY_SSID
            val trusted = RayLanDirect.trusted()
            val current = RayLanDirect.current()
            val isTrusted = current != null && RayLanDirect.matches(current, trusted, mode(bySsid))
            val wifi = LanWifiState.of(
                bySsid, inWifi = current != null, hasSsid = current?.ssid != null, trusted = isTrusted,
                location = RayLanDirect.locationGranted(), background = RayLanDirect.backgroundGranted(),
            )
            val next = state.copy(
                node = node.displayName,
                show = shown,
                ports = DataStore.lanInboundPorts.orEmpty().trim(),
                notes = notes,
                devices = devices,
                direct = DataStore.lanDirect,
                bySsid = bySsid,
                trusted = trusted.map { LanWifi(it.name, it.key, current != null && RayLanDirect.matches(current, listOf(it), mode(bySsid))) },
                wifi = wifi,
                wifiName = current?.ssid,
            )
            withContext(Dispatchers.Main) {
                // the sheets are the user's, not the store's
                state = next.copy(portsEdit = state.portsEdit, portsError = state.portsError, byOpen = state.byOpen)
            }
        }
    }

    /** The direct link's settings changed: the running core takes it on or off at once; ignored when stopped. */
    private fun directChanged() {
        runOnDefaultDispatcher { RayLanNet.reloadIfChanged(System.currentTimeMillis() / 1000) }
        read()
    }

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { read() }

    private val actions = object : LanActions {
        override fun back() = finish()

        override fun show(on: Boolean) {
            if (on == state.show) return
            state = state.copy(show = on)
            lifecycleScope.launch(Dispatchers.Default) {
                RayLanNet.setShow(nodeId, on)
                withContext(Dispatchers.Main) {
                    snackbar(if (on) R.string.raycontrol_lan_shown else R.string.raycontrol_lan_hidden).show()
                    read()
                }
            }
        }

        override fun portsEdit(text: String?) {
            state = state.copy(portsEdit = text, portsError = null)
        }

        override fun portsSave() {
            val typed = state.portsEdit?.trim() ?: return
            if (typed.isNotEmpty() && MeshPorts.parse(typed) == null) {
                state = state.copy(portsError = getString(R.string.raycontrol_lan_ports_bad, typed))
                return
            }
            val changed = typed != DataStore.lanInboundPorts.orEmpty().trim()
            state = state.copy(portsEdit = null, portsError = null, ports = typed)
            if (changed) {
                DataStore.lanInboundPorts = typed
                // the running core (another process) takes the ports at once, a closed one too; ignored when stopped
                runOnDefaultDispatcher {
                    if (RayNodes.list().any { RayLanNet.of(it) != null }) SagerNet.reloadService(force = true)
                }
            }
        }

        override fun direct(on: Boolean) {
            if (on == DataStore.lanDirect) return
            DataStore.lanDirect = on
            state = state.copy(direct = on)
            directChanged()
        }

        override fun byOpen(open: Boolean) {
            state = state.copy(byOpen = open)
        }

        override fun by(ssid: Boolean) {
            state = state.copy(byOpen = false, bySsid = ssid)
            if (mode(ssid) != DataStore.lanDirectBy) {
                DataStore.lanDirectBy = mode(ssid)
                directChanged()
            }
        }

        override fun forget(key: String) {
            RayLanDirect.save(RayLanDirect.trusted().filterNot { it.key == key })
            directChanged()
        }

        override fun allow() {
            // the background permission is asked apart, after the foreground one (Android 11+ sends to the settings)
            askLocation.launch(
                if (!RayLanDirect.locationGranted()) arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
                else arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
            )
        }

        override fun trust() {
            val current = RayLanDirect.current() ?: return read()
            val trusted = RayLanDirect.trusted()
            val name = current.ssid ?: getString(R.string.raycontrol_lan_wifi_n, trusted.size + 1)
            RayLanDirect.save(trusted.filterNot { it.key == current.key } + RayLanDirect.Trusted(name, current.key, current.ssid))
            directChanged()
        }
    }
}

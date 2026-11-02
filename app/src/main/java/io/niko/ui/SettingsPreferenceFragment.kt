package io.niko.ui

import io.niko.ui.profile.ProfileSettingsActivity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.app.ActivityCompat
import androidx.preference.*
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.Key
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.database.preference.EditTextPreferenceModifiers
import io.niko.ktx.*
import io.niko.raycontrol.RayNodes
import io.niko.raycontrol.RayNode
import io.niko.raycontrol.RaySubscription
import io.niko.utils.Theme
import io.niko.ui.*
import libcore.Libcore

class SettingsPreferenceFragment : PreferenceFragmentCompat() {

    private lateinit var isProxyApps: SwitchPreference

    private lateinit var globalCustomConfig: EditConfigPreference


    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        listView.layoutManager = FixedLinearLayoutManager(listView)
    }

    private val reloadListener = Preference.OnPreferenceChangeListener { _, _ ->
        needReload()
        true
    }

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        preferenceManager.preferenceDataStore = DataStore.configurationStore
        DataStore.initGlobal()
        addPreferencesFromResource(R.xml.global_preferences)

        val appTheme = findPreference<ColorPickerPreference>(Key.APP_THEME)!!
        appTheme.setOnPreferenceChangeListener { _, newTheme ->
            if (DataStore.serviceState.started) {
                SagerNet.reloadService()
            }
            val theme = Theme.getTheme(newTheme as Int)
            app.setTheme(theme)
            requireActivity().apply {
                setTheme(theme)
                ActivityCompat.recreate(this)
            }
            true
        }

        val nightTheme = findPreference<SimpleMenuPreference>(Key.NIGHT_THEME)!!
        nightTheme.setOnPreferenceChangeListener { _, newTheme ->
            Theme.currentNightMode = (newTheme as String).toInt()
            Theme.applyNightTheme()
            true
        }
        val mixedPort = findPreference<EditTextPreference>(Key.MIXED_PORT)!!
        val mixedUsername = findPreference<EditTextPreference>(Key.MIXED_USERNAME)!!
        val mixedPassword = findPreference<EditTextPreference>(Key.MIXED_PASSWORD)!!
        val localProxyNoAuthInVpn = findPreference<SwitchPreference>(Key.LOCAL_PROXY_NO_AUTH_IN_VPN)!!
        val serviceMode = findPreference<Preference>(Key.SERVICE_MODE)!!
        val allowAccess = findPreference<Preference>(Key.ALLOW_ACCESS)!!
        val appendHttpProxy = findPreference<SwitchPreference>(Key.APPEND_HTTP_PROXY)!!

        val showDirectSpeed = findPreference<SwitchPreference>(Key.SHOW_DIRECT_SPEED)!!
        val ipv6Mode = findPreference<Preference>(Key.IPV6_MODE)!!
        val trafficSniffing = findPreference<Preference>(Key.TRAFFIC_SNIFFING)!!

        val bypassLan = findPreference<SwitchPreference>(Key.BYPASS_LAN)!!
        val bypassLanInCore = findPreference<SwitchPreference>(Key.BYPASS_LAN_IN_CORE)!!
        val tlsFragmentBypass = findPreference<Preference>(Key.TLS_FRAGMENT_BYPASS)!!

        val remoteDns = findPreference<EditTextPreference>(Key.REMOTE_DNS)!!
        val directDns = findPreference<EditTextPreference>(Key.DIRECT_DNS)!!
        val enableDnsRouting = findPreference<SwitchPreference>(Key.ENABLE_DNS_ROUTING)!!
        val enableFakeDns = findPreference<SwitchPreference>(Key.ENABLE_FAKEDNS)!!

        val logLevel = findPreference<LongClickListPreference>(Key.LOG_LEVEL)!!
        val mtu = findPreference<MTUPreference>(Key.MTU)!!
        globalCustomConfig = findPreference(Key.GLOBAL_CUSTOM_CONFIG)!!
        globalCustomConfig.useConfigStore(Key.GLOBAL_CUSTOM_CONFIG)

        logLevel.dialogLayoutResource = R.layout.layout_loglevel_help
        logLevel.setOnPreferenceChangeListener { _, _ ->
            needRestart()
            true
        }
        logLevel.setOnLongClickListener {
            if (context == null) return@setOnLongClickListener true

            val view = EditText(context).apply {
                inputType = EditorInfo.TYPE_CLASS_NUMBER
                var size = DataStore.logBufSize
                if (size == 0) size = 50
                setText(size.toString())
            }

            MaterialAlertDialogBuilder(requireContext()).setTitle("Log buffer size (kb)")
                .setView(view)
                .setPositiveButton(android.R.string.ok) { _, _ ->
                    DataStore.logBufSize = view.text.toString().toInt()
                    if (DataStore.logBufSize <= 0) DataStore.logBufSize = 50
                    needRestart()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }

        mixedPort.setOnBindEditTextListener(EditTextPreferenceModifiers.Port)

        val metedNetwork = findPreference<Preference>(Key.METERED_NETWORK)!!
        if (Build.VERSION.SDK_INT < 28) {
            metedNetwork.remove()
        }
        isProxyApps = findPreference(Key.PROXY_APPS)!!
        isProxyApps.setOnPreferenceChangeListener { _, newValue ->
            startActivity(Intent(activity, AppManagerActivity::class.java))
            if (newValue as Boolean) DataStore.dirty = true
            newValue
        }

        val profileTrafficStatistics =
            findPreference<SwitchPreference>(Key.PROFILE_TRAFFIC_STATISTICS)!!
        val speedInterval = findPreference<SimpleMenuPreference>(Key.SPEED_INTERVAL)!!
        profileTrafficStatistics.isEnabled = speedInterval.value.toString() != "0"
        speedInterval.setOnPreferenceChangeListener { _, newValue ->
            profileTrafficStatistics.isEnabled = newValue.toString() != "0"
            needReload()
            true
        }

        serviceMode.setOnPreferenceChangeListener { _, _ ->
            if (DataStore.serviceState.started) SagerNet.stopService()
            true
        }

        val tunImplementation = findPreference<SimpleMenuPreference>(Key.TUN_IMPLEMENTATION)!!
        val resolveDestination = findPreference<SwitchPreference>(Key.RESOLVE_DESTINATION)!!
        val acquireWakeLock = findPreference<SwitchPreference>(Key.ACQUIRE_WAKE_LOCK)!!
        val enableClashAPI = findPreference<SwitchPreference>(Key.ENABLE_CLASH_API)!!
        enableClashAPI.setOnPreferenceChangeListener { _, newValue ->
            (activity as MainActivity?)?.refreshNavMenu(newValue as Boolean)
            needReload()
            true
        }

        mixedPort.onPreferenceChangeListener = reloadListener
        mixedUsername.onPreferenceChangeListener = reloadListener
        mixedPassword.onPreferenceChangeListener = reloadListener
        mixedPassword.summaryProvider = ProfileSettingsActivity.PasswordSummaryProvider
        localProxyNoAuthInVpn.onPreferenceChangeListener = reloadListener
        appendHttpProxy.onPreferenceChangeListener = reloadListener
        showDirectSpeed.onPreferenceChangeListener = reloadListener
        trafficSniffing.onPreferenceChangeListener = reloadListener
        bypassLan.onPreferenceChangeListener = reloadListener
        bypassLanInCore.onPreferenceChangeListener = reloadListener
        tlsFragmentBypass.onPreferenceChangeListener = reloadListener
        findPreference<Preference>(Key.RULE_SET_UPDATE_HOURS)!!.onPreferenceChangeListener = reloadListener
        mtu.onPreferenceChangeListener = reloadListener

        enableFakeDns.onPreferenceChangeListener = reloadListener
        findPreference<Preference>(Key.DNS_OPTIMISTIC_CACHE)!!.onPreferenceChangeListener = reloadListener
        remoteDns.onPreferenceChangeListener = reloadListener
        directDns.onPreferenceChangeListener = reloadListener
        enableDnsRouting.onPreferenceChangeListener = reloadListener

        ipv6Mode.onPreferenceChangeListener = reloadListener
        allowAccess.onPreferenceChangeListener = reloadListener

        resolveDestination.onPreferenceChangeListener = reloadListener
        tunImplementation.onPreferenceChangeListener = reloadListener
        acquireWakeLock.onPreferenceChangeListener = reloadListener
        globalCustomConfig.onPreferenceChangeListener = reloadListener

        // Yggdrasil: the node address follows from this install's key, made on first enable
        val yggdrasilAddress = findPreference<Preference>(Key.YGGDRASIL_ADDRESS)!!
        fun showYggdrasilAddress(enabled: Boolean) {
            yggdrasilAddress.summary = if (enabled) {
                Libcore.yggdrasilAddress(DataStore.yggdrasilPrivateKey).ifEmpty { getString(R.string.yggdrasil_not_built) }
            } else {
                ""
            }
        }
        showYggdrasilAddress(DataStore.yggdrasilEnabled)
        yggdrasilAddress.setOnPreferenceClickListener {
            yggdrasilAddress.summary?.toString()?.takeIf { it.contains(':') }?.let {
                SagerNet.trySetPrimaryClip(it)
                snackbar(R.string.copy_success).show()
            }
            true
        }
        findPreference<SwitchPreference>(Key.YGGDRASIL_ENABLED)!!.setOnPreferenceChangeListener { _, newValue ->
            showYggdrasilAddress(newValue as Boolean)
            needReload()
            true
        }
        findPreference<Preference>(Key.YGGDRASIL_PEERS)!!.onPreferenceChangeListener = reloadListener
        findPreference<Preference>(Key.YGGDRASIL_ALLOW_INBOUND)!!.onPreferenceChangeListener = reloadListener
        findPreference<Preference>(Key.YGGDRASIL_PEERS_VIA_PROXY)!!.onPreferenceChangeListener = reloadListener
        findPreference<Preference>(Key.YGGDRASIL_TLS_CHROME)!!.onPreferenceChangeListener = reloadListener

        // raycontrol nodes: imported from rcdev1 (clipboard or QR); update the subscription or remove here
        raycontrolNodes = findPreference("raycontrolNodesView")!!
        showRaycontrolNodes()
        raycontrolNodes.setOnPreferenceClickListener {
            val nodes = RayNodes.list()
            if (nodes.isEmpty()) return@setOnPreferenceClickListener true
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.raycontrol_nodes)
                .setItems(nodes.map { it.host + " · " + it.services.joinToString { s -> s.name } }.toTypedArray()) { _, which ->
                    showNodeActions(nodes[which])
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
            true
        }
    }

    private lateinit var raycontrolNodes: Preference

    private fun showRaycontrolNodes() {
        val nodes = RayNodes.list()
        raycontrolNodes.summary = if (nodes.isEmpty()) {
            getString(R.string.raycontrol_nodes_empty)
        } else {
            nodes.joinToString("\n") { getString(R.string.raycontrol_node_line, it.host, it.deviceId, it.services.size) }
        }
    }

    private fun showNodeActions(node: RayNode) {
        val actions = mutableListOf<Pair<Int, () -> Unit>>()
        if (node.subscription != null) actions += R.string.raycontrol_sub_update to { updateSubscription(node) }
        actions += R.string.raycontrol_node_remove to { confirmRemove(node) }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(node.host)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, i -> actions[i].second() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun updateSubscription(node: RayNode) {
        snackbar(R.string.raycontrol_sub_updating).show()
        val context = requireContext().applicationContext
        runOnDefaultDispatcher {
            val message = RaySubscription.refreshForUser(context, node)
            onMainDispatcher { if (isAdded) snackbar(message).show() }
        }
    }

    private fun confirmRemove(node: RayNode) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(getString(R.string.raycontrol_node_remove_confirm, node.host))
            .setPositiveButton(R.string.yes) { _, _ ->
                RayNodes.remove(node.spa.nodeId)
                RaySubscription.schedule()
                showRaycontrolNodes()
                // the running core still holds the key and knocks: restart it now, not on "Apply"
                if (DataStore.serviceState.started) SagerNet.reloadService()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun onResume() {
        super.onResume()

        if (::isProxyApps.isInitialized) {
            isProxyApps.isChecked = DataStore.proxyApps
        }
        if (::globalCustomConfig.isInitialized) {
            globalCustomConfig.notifyChanged()
        }
    }

}
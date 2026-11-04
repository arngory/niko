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
import io.niko.database.SagerDatabase
import io.niko.database.preference.EditTextPreferenceModifiers
import io.niko.ktx.*
import io.niko.raycontrol.RayChannel
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
        if (node.isAdmin) actions += R.string.raycontrol_admin to {
            startActivity(Intent(requireContext(), RayAdminActivity::class.java).putExtra(RayAdminActivity.EXTRA_NODE, node.spa.nodeId))
        }
        if (node.subscription != null) actions += R.string.raycontrol_sub_update to { updateSubscription(node) }
        if (!node.profileIds.isNullOrEmpty()) actions += R.string.raycontrol_channel_check to { checkChannel(node) }
        // the summary with consent; the node's hints for this network come with the subscription either way
        val hasHints = RayChannel.network()?.let { RayChannel.hints(node.spa.nodeId, it) } != null
        if (DataStore.raycontrolReports || hasHints) actions += R.string.raycontrol_report_show to { showReport(node) }
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

    /** Probes every profile of [node] on this network, one by one, and shows the results as they come. */
    private fun checkChannel(node: RayNode) {
        val network = RayChannel.network() ?: run {
            snackbar(R.string.raycontrol_channel_no_network).show()
            return
        }
        val context = requireContext().applicationContext
        val lines = mutableListOf(getString(R.string.raycontrol_channel_checking))
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(getString(R.string.raycontrol_channel_title, node.host))
            .setMessage(lines.joinToString("\n"))
            .setPositiveButton(android.R.string.ok, null)
            .show()
        val job = runOnDefaultDispatcher {
            // the direct path first: which SPA carrier opens the node's port here, how large a UDP datagram passes
            RayChannel.directPath(node, network)?.let { path ->
                lines += when {
                    path.spaCarrier == null -> context.getString(R.string.raycontrol_direct_none)
                    !path.udpProbed -> context.getString(R.string.raycontrol_direct_spa, path.spaCarrier.uppercase())
                    path.udpMtu == 0 -> context.getString(R.string.raycontrol_direct_no_udp, path.spaCarrier.uppercase())
                    else -> context.getString(R.string.raycontrol_direct_mtu, path.spaCarrier.uppercase(), path.udpMtu) +
                        if (path.udpMtu < 1200) context.getString(R.string.raycontrol_direct_mtu_low) else ""
                }
                onMainDispatcher { dialog.setMessage(lines.joinToString("\n")) }
            }
            for ((key, id) in node.profileIds.orEmpty()) {
                val profile = SagerDatabase.proxyDao.getById(id) ?: continue
                val score = RayChannel.probe(profile, deep = true, mobile = network.mobile)
                if (score == null) {
                    lines += context.getString(R.string.raycontrol_channel_in_use, profile.displayName())
                } else {
                    RayChannel.record(node.spa.nodeId, network, key, score)
                    lines += RayChannel.describe(context, profile.displayName(), score)
                }
                onMainDispatcher { dialog.setMessage(lines.joinToString("\n")) }
                // a Yggdrasil profile: its public peers from here, each with a node of the app's own
                val via = profile.requireBean().yggVia.orEmpty().lines().map { it.trim() }.filter { it.isNotEmpty() }
                if (via.isNotEmpty()) {
                    val checks = io.niko.raycontrol.RayYggPeers.check(via)
                    val picked = io.niko.raycontrol.RayYggPeers.pick(checks)
                    lines += context.getString(R.string.raycontrol_ygg_peers, checks.count { it.up }, checks.size)
                    for (c in checks) {
                        val name = io.niko.raycontrol.RayYggPeers.label(c.uri)
                        lines += when {
                            c.up -> context.getString(
                                if (c.uri in picked) R.string.raycontrol_ygg_peer_taken else R.string.raycontrol_ygg_peer_up,
                                name, c.latencyMs.toInt()
                            )
                            else -> context.getString(R.string.raycontrol_ygg_peer_down, name, c.error ?: "—")
                        }
                    }
                    onMainDispatcher { dialog.setMessage(lines.joinToString("\n")) }
                }
            }
            lines[0] = context.getString(R.string.raycontrol_channel_done)
            // with consent the day's summary of this network goes out now, the results just measured in it
            reportLine(context, io.niko.raycontrol.RayReport.send(RayNodes.get(node.spa.nodeId) ?: node))?.let { lines += it }
            onMainDispatcher { dialog.setMessage(lines.joinToString("\n")) }
        }
        // a probe already running ends within its window; no new one starts
        dialog.setOnDismissListener { job.cancel() }
    }

    private fun reportLine(context: android.content.Context, outcome: io.niko.raycontrol.RayReport.Outcome?): String? = when (outcome) {
        null -> null
        io.niko.raycontrol.RayReport.Outcome.SENT -> context.getString(R.string.raycontrol_report_sent)
        io.niko.raycontrol.RayReport.Outcome.ALREADY_SENT -> context.getString(R.string.raycontrol_report_already)
        io.niko.raycontrol.RayReport.Outcome.NOT_TAKEN -> context.getString(R.string.raycontrol_report_not_taken)
        io.niko.raycontrol.RayReport.Outcome.REFUSED -> context.getString(R.string.raycontrol_report_refused)
        io.niko.raycontrol.RayReport.Outcome.FAILED -> context.getString(R.string.raycontrol_report_failed)
    }

    /**
     * The summary for [node] from this network's probes, exactly as it goes out (RayReport), whether today's went
     * out, and the node's hints for this network.
     */
    private fun showReport(node: RayNode) {
        val network = RayChannel.network() ?: run {
            snackbar(R.string.raycontrol_channel_no_network).show()
            return
        }
        val report = io.niko.raycontrol.RayReport.build(node, network)
        val message = buildString {
            append(report?.let { com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(it) }
                ?: getString(R.string.raycontrol_report_empty))
            val state = RayChannel.reportState(node.spa.nodeId, network)
            val today = System.currentTimeMillis() / 1000 / (24 * 3600)
            if (state?.get("day")?.asLong == today) when (state.get("status")?.asInt) {
                200, 204, 429 -> append("\n\n").append(getString(R.string.raycontrol_report_already))
                404 -> append("\n\n").append(getString(R.string.raycontrol_report_not_taken))
            }
            RayChannel.hints(node.spa.nodeId, network)?.let { h ->
                val names = node.services.associate { it.id to it.name.ifBlank { "svc ${it.id}" } }
                fun list(ids: List<Int>) = ids.joinToString(", ") { names[it] ?: "svc $it" }
                append("\n\n").append(getString(R.string.raycontrol_hints, h.devices, h.days))
                if (h.order.isNotEmpty()) append("\n").append(getString(R.string.raycontrol_hints_order, list(h.order)))
                if (h.avoid.isNotEmpty()) append("\n").append(getString(R.string.raycontrol_hints_avoid, list(h.avoid)))
                if (h.udpBlocked) append("\n").append(getString(R.string.raycontrol_hints_udp_blocked))
                if (h.tcpStallKib > 0) append("\n").append(getString(R.string.raycontrol_hints_stall, h.tcpStallKib))
            }
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.raycontrol_report_show)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
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
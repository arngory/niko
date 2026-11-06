package io.niko.ui.profile

import android.annotation.SuppressLint
import android.content.DialogInterface
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast
import androidx.activity.result.component1
import androidx.activity.result.component2
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.isVisible
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceDataStore
import androidx.preference.PreferenceFragmentCompat
import com.github.shadowsocks.plugin.Empty
import com.github.shadowsocks.plugin.fragment.AlertDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.*
import io.niko.database.DataStore
import io.niko.database.GroupManager
import io.niko.database.ProfileManager
import io.niko.database.SagerDatabase
import io.niko.database.preference.OnPreferenceDataStoreChangeListener
import io.niko.databinding.LayoutConnectionTweaksBinding
import io.niko.databinding.LayoutGroupItemBinding
import io.niko.fmt.AbstractBean
import io.niko.fmt.internal.ChainBean
import io.niko.fmt.DnsTunnel
import io.niko.fmt.parseCertificatePins
import io.niko.fmt.parseKnockSequence
import io.niko.ktx.*
import io.niko.ui.ThemedActivity
import io.niko.widget.ListListener
import kotlinx.parcelize.Parcelize
import kotlin.properties.Delegates

@Suppress("UNCHECKED_CAST")
abstract class ProfileSettingsActivity<T : AbstractBean>(
    @LayoutRes resId: Int = R.layout.layout_config_settings,
) : ThemedActivity(resId), OnPreferenceDataStoreChangeListener {

    class UnsavedChangesDialogFragment : AlertDialogFragment<Empty, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.unsaved_changes_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                runOnDefaultDispatcher {
                    (requireActivity() as ProfileSettingsActivity<*>).saveAndExit()
                }
            }
            setNegativeButton(R.string.no) { _, _ ->
                requireActivity().finish()
            }
            setNeutralButton(android.R.string.cancel, null)
        }
    }

    @Parcelize
    data class ProfileIdArg(val profileId: Long, val groupId: Long) : Parcelable
    class DeleteConfirmationDialogFragment : AlertDialogFragment<ProfileIdArg, Empty>() {
        override fun AlertDialog.Builder.prepare(listener: DialogInterface.OnClickListener) {
            setTitle(R.string.delete_confirm_prompt)
            setPositiveButton(R.string.yes) { _, _ ->
                runOnDefaultDispatcher {
                    ProfileManager.deleteProfile(arg.groupId, arg.profileId)
                }
                requireActivity().finish()
            }
            setNegativeButton(R.string.no, null)
        }
    }

    companion object {
        const val EXTRA_PROFILE_ID = "id"
        const val EXTRA_IS_SUBSCRIPTION = "sub"
    }

    abstract fun createEntity(): T
    abstract fun T.init()
    abstract fun T.serialize()

    val proxyEntity by lazy { SagerDatabase.proxyDao.getById(DataStore.editingId) }
    protected var isSubscription by Delegates.notNull<Boolean>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        confirmUnsavedChangesOnBack({ DataStore.dirty }) {
            UnsavedChangesDialogFragment().apply { key() }.show(supportFragmentManager, null)
        }
        setSupportActionBar(findViewById(R.id.toolbar))
        supportActionBar?.apply {
            setTitle(R.string.profile_config)
            setDisplayHomeAsUpEnabled(true)
            setHomeAsUpIndicator(R.drawable.ic_navigation_close)
        }

        if (savedInstanceState == null) {
            val editingId = intent.getLongExtra(EXTRA_PROFILE_ID, 0L)
            isSubscription = intent.getBooleanExtra(EXTRA_IS_SUBSCRIPTION, false)
            DataStore.editingId = editingId
            runOnDefaultDispatcher {
                if (editingId == 0L) {
                    DataStore.editingGroup = DataStore.selectedGroupForImport()
                    createEntity().applyDefaultValues().apply {
                        init()
                        loadConnectionTweaks()
                    }
                } else {
                    if (proxyEntity == null) {
                        onMainDispatcher {
                            finish()
                        }
                        return@runOnDefaultDispatcher
                    }
                    DataStore.editingGroup = proxyEntity!!.groupId
                    (proxyEntity!!.requireBean() as T).apply {
                        init()
                        loadConnectionTweaks()
                    }
                }

                onMainDispatcher {
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.settings, MyPreferenceFragmentCompat())
                        .commit()
                }
            }


        }

    }

    open suspend fun saveAndExit() {

        val editingId = DataStore.editingId
        if (editingId == 0L) {
            val editingGroup = DataStore.editingGroup
            ProfileManager.createProfile(editingGroup, createEntity().apply {
                serialize()
                saveConnectionTweaks()
            })
        } else {
            if (proxyEntity == null) {
                finish()
                return
            }
            if (proxyEntity!!.id == DataStore.selectedProxy) {
                SagerNet.stopService()
            }
            ProfileManager.updateProfile(proxyEntity!!.apply {
                (requireBean() as T).apply {
                    serialize()
                    saveConnectionTweaks()
                }
            })
        }
        finish()

    }

    // port knocking and TLS fragmentation are shared by every profile type

    private fun AbstractBean.loadConnectionTweaks() {
        DataStore.serverKnockSequence = knockSequence
        DataStore.serverKnockInterval = knockInterval
        DataStore.serverKnockTtl = knockTtl
        DataStore.serverKnockSilence = knockSilence
        DataStore.serverTlsFragment = tlsFragment
        DataStore.serverCertPins = certPins
        DataStore.serverDnsTunnel = dnsTunnel
        DataStore.serverDnsTunnelResolver = dnsTunnelResolver
        DataStore.serverDnsTunnelDomain = dnsTunnelDomain
        DataStore.serverDnsTunnelKey = dnsTunnelKey
        DataStore.serverDnsTunnelMethod = dnsTunnelMethod
        DataStore.serverDnsTunnelFixedUpstream = dnsTunnelFixedUpstream
        DataStore.serverUdpPrelude = udpPrelude
        DataStore.serverUdpPreludeSni = udpPreludeSni
        DataStore.serverUdpPreludeTtl = udpPreludeTtl
        DataStore.serverUdpReorderInitial = udpReorderInitial
    }

    private fun AbstractBean.saveConnectionTweaks() {
        knockSequence = DataStore.serverKnockSequence.trim()
        knockInterval = DataStore.serverKnockInterval
        knockTtl = DataStore.serverKnockTtl
        knockSilence = DataStore.serverKnockSilence
        tlsFragment = DataStore.serverTlsFragment
        certPins = DataStore.serverCertPins
        // dnsauth-v1 belongs to the node's tunnel the link gave: another server, domain or key is not that tunnel
        if (dnsTunnel != DataStore.serverDnsTunnel || dnsTunnelResolver != DataStore.serverDnsTunnelResolver ||
            dnsTunnelDomain != DataStore.serverDnsTunnelDomain || dnsTunnelKey != DataStore.serverDnsTunnelKey
        ) {
            dnsTunnelAuth = ""
        }
        // dnsseal-v1's key is the zone's, and the spare zones (dnszones-v1) the service's: another engine, zone or
        // server key is not that service (a resolver is)
        if (dnsTunnel != DataStore.serverDnsTunnel || dnsTunnelDomain != DataStore.serverDnsTunnelDomain ||
            dnsTunnelKey != DataStore.serverDnsTunnelKey
        ) {
            dnsTunnelSeal = ""
            dnsTunnelSpare = ""
        }
        dnsTunnel = DataStore.serverDnsTunnel
        dnsTunnelResolver = DataStore.serverDnsTunnelResolver
        dnsTunnelDomain = DataStore.serverDnsTunnelDomain
        dnsTunnelKey = DataStore.serverDnsTunnelKey
        dnsTunnelMethod = DataStore.serverDnsTunnelMethod
        dnsTunnelFixedUpstream = DataStore.serverDnsTunnelFixedUpstream
        udpPrelude = DataStore.serverUdpPrelude
        udpPreludeSni = DataStore.serverUdpPreludeSni
        udpPreludeTtl = DataStore.serverUdpPreludeTtl
        udpReorderInitial = DataStore.serverUdpReorderInitial
    }

    fun showConnectionTweaksDialog() {
        val binding = LayoutConnectionTweaksBinding.inflate(layoutInflater)
        binding.tlsFragment.check(
            when (DataStore.serverTlsFragment) {
                1 -> R.id.tls_fragment_record
                2 -> R.id.tls_fragment_tcp
                else -> R.id.tls_fragment_off
            }
        )
        binding.knockSequence.setText(DataStore.serverKnockSequence)
        binding.knockInterval.setText(DataStore.serverKnockInterval.toString())
        binding.knockTtl.setText(DataStore.serverKnockTtl.toString())
        binding.knockSilence.setText(DataStore.serverKnockSilence.toString())
        binding.certPins.setText(DataStore.serverCertPins)
        binding.dnsTunnelResolver.setText(DataStore.serverDnsTunnelResolver)
        binding.dnsTunnelDomain.setText(DataStore.serverDnsTunnelDomain)
        binding.dnsTunnelKey.setText(DataStore.serverDnsTunnelKey)
        binding.dnsTunnelMethod.setText(DataStore.serverDnsTunnelMethod.toString())
        binding.dnsTunnelFixedUpstream.isChecked = DataStore.serverDnsTunnelFixedUpstream
        binding.udpPrelude.setText(DataStore.serverUdpPrelude.toString())
        binding.udpPreludeSni.setText(DataStore.serverUdpPreludeSni)
        binding.udpPreludeTtl.setText(DataStore.serverUdpPreludeTtl.toString())
        binding.udpReorderInitial.isChecked = DataStore.serverUdpReorderInitial
        // the MasterDnsVPN settings only show with that engine
        fun showEngine(masterDnsVpn: Boolean) {
            binding.dnsTunnelMethodLayout.isVisible = masterDnsVpn
            binding.dnsTunnelFixedUpstream.isVisible = masterDnsVpn
        }
        val masterDnsVpn = DataStore.serverDnsTunnel == DnsTunnel.MASTERDNSVPN
        binding.dnsTunnelEngine.check(if (masterDnsVpn) R.id.dns_tunnel_masterdnsvpn else R.id.dns_tunnel_dnstt)
        showEngine(masterDnsVpn)
        binding.dnsTunnelEngine.setOnCheckedChangeListener { _, id -> showEngine(id == R.id.dns_tunnel_masterdnsvpn) }
        val dialog = MaterialAlertDialogBuilder(this).setTitle(R.string.connection_tweaks)
            .setView(binding.root)
            .setPositiveButton(android.R.string.ok, null)
            .setNegativeButton(android.R.string.cancel, null)
            .show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val sequence = binding.knockSequence.text.toString().trim()
            try {
                parseKnockSequence(sequence)
            } catch (e: IllegalArgumentException) {
                binding.knockSequence.error = getString(R.string.port_knocking_invalid, e.message)
                return@setOnClickListener
            }
            val pins = binding.certPins.text.toString()
            try {
                parseCertificatePins(pins)
            } catch (e: IllegalArgumentException) {
                binding.certPins.error = e.message
                return@setOnClickListener
            }
            val tunnel = DnsTunnel(
                engine = if (binding.dnsTunnelEngine.checkedRadioButtonId == R.id.dns_tunnel_masterdnsvpn) {
                    DnsTunnel.MASTERDNSVPN
                } else {
                    DnsTunnel.DNSTT
                },
                resolver = binding.dnsTunnelResolver.text.toString().trim(),
                domain = binding.dnsTunnelDomain.text.toString().trim(),
                key = binding.dnsTunnelKey.text.toString().trim(),
                method = binding.dnsTunnelMethod.text.toString().toIntOrNull() ?: -1,
                fixedUpstream = binding.dnsTunnelFixedUpstream.isChecked,
            )
            val tunnelError = tunnel.validate()
            if (tunnelError != null) {
                binding.dnsTunnelDomain.error = tunnelError
                return@setOnClickListener
            }
            val preludeSni = binding.udpPreludeSni.text.toString().trim()
            if (preludeSni.isNotEmpty() && !Regex("^[A-Za-z0-9.-]+$").matches(preludeSni)) {
                binding.udpPreludeSni.error = getString(R.string.udp_prelude_sni_invalid)
                return@setOnClickListener
            }
            DataStore.serverDnsTunnel = if (tunnel.enabled) tunnel.engine else ""
            DataStore.serverDnsTunnelResolver = tunnel.resolver
            DataStore.serverDnsTunnelDomain = tunnel.domain
            DataStore.serverDnsTunnelKey = tunnel.key
            DataStore.serverDnsTunnelMethod = tunnel.method.coerceIn(0, 5)
            DataStore.serverDnsTunnelFixedUpstream = tunnel.fixedUpstream
            DataStore.serverUdpPrelude = binding.udpPrelude.text.toString().toIntOrNull()?.coerceIn(0, 8) ?: 0
            DataStore.serverUdpPreludeSni = preludeSni
            DataStore.serverUdpPreludeTtl = binding.udpPreludeTtl.text.toString().toIntOrNull()?.coerceIn(0, 255) ?: 0
            DataStore.serverUdpReorderInitial = binding.udpReorderInitial.isChecked
            DataStore.serverCertPins = pins.trim()
            DataStore.serverTlsFragment = when (binding.tlsFragment.checkedRadioButtonId) {
                R.id.tls_fragment_record -> 1
                R.id.tls_fragment_tcp -> 2
                else -> 0
            }
            DataStore.serverKnockSequence = sequence
            DataStore.serverKnockInterval =
                binding.knockInterval.text.toString().toIntOrNull()?.coerceIn(10, 10_000) ?: 200
            DataStore.serverKnockTtl =
                binding.knockTtl.text.toString().toIntOrNull()?.coerceIn(1, 3600) ?: 10
            DataStore.serverKnockSilence =
                binding.knockSilence.text.toString().toIntOrNull()?.coerceIn(0, 3600) ?: 15
            dialog.dismiss()
        }
    }

    val child by lazy { supportFragmentManager.findFragmentById(R.id.settings) as MyPreferenceFragmentCompat }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.profile_config_menu, menu)
        menu.findItem(R.id.action_move)?.apply {
            if (DataStore.editingId != 0L // not new profile
                && SagerDatabase.groupDao.getById(DataStore.editingGroup)?.type == GroupType.BASIC // not in subscription group
                && SagerDatabase.groupDao.allGroups()
                    .filter { it.type == GroupType.BASIC }.size > 1 // have other basic group
            ) isVisible = true
        }
        menu.findItem(R.id.action_create_shortcut)?.apply {
            if (Build.VERSION.SDK_INT >= 26 && DataStore.editingId != 0L) {
                isVisible = true // not new profile
            }
        }
        // shared menu item
        // a chain is never dialed itself, its hops carry these settings
        menu.findItem(R.id.action_connection_tweaks)?.isVisible = createEntity() !is ChainBean
        menu.findItem(R.id.action_custom_outbound_json)?.isVisible = true
        menu.findItem(R.id.action_custom_config_json)?.isVisible = true
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem) = child.onOptionsItemSelected(item)

    override fun onSupportNavigateUp(): Boolean {
        if (!super.onSupportNavigateUp()) finish()
        return true
    }

    override fun onDestroy() {
        DataStore.profileCacheStore.unregisterChangeListener(this)
        super.onDestroy()
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        if (key != Key.PROFILE_DIRTY) {
            DataStore.dirty = true
        }
    }

    abstract fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    )

    open fun PreferenceFragmentCompat.viewCreated(view: View, savedInstanceState: Bundle?) {
    }

    open fun PreferenceFragmentCompat.displayPreferenceDialog(preference: Preference): Boolean {
        return false
    }

    class MyPreferenceFragmentCompat : PreferenceFragmentCompat() {

        var activity: ProfileSettingsActivity<*>? = null

        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            preferenceManager.preferenceDataStore = DataStore.profileCacheStore
            try {
                activity = (requireActivity() as ProfileSettingsActivity<*>).apply {
                    createPreferences(savedInstanceState, rootKey)
                }
            } catch (e: Exception) {
                Toast.makeText(
                    SagerNet.application,
                    "Error on createPreferences, please try again.",
                    Toast.LENGTH_SHORT
                ).show()
                Logs.e(e)
            }
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)

            ViewCompat.setOnApplyWindowInsetsListener(listView, ListListener)

            activity?.apply {
                viewCreated(view, savedInstanceState)
                DataStore.dirty = false
                DataStore.profileCacheStore.registerChangeListener(this)
            }
        }

        var callbackCustom: ((String) -> Unit)? = null
        var callbackCustomOutbound: ((String) -> Unit)? = null

        val resultCallbackCustom = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { (_, _) ->
            callbackCustom?.let { it(DataStore.serverCustom) }
        }

        val resultCallbackCustomOutbound = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { (_, _) ->
            callbackCustomOutbound?.let { it(DataStore.serverCustomOutbound) }
        }

        @SuppressLint("CheckResult")
        override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
            R.id.action_delete -> {
                if (DataStore.editingId == 0L) {
                    requireActivity().finish()
                } else {
                    DeleteConfirmationDialogFragment().apply {
                        arg(
                            ProfileIdArg(
                                DataStore.editingId, DataStore.editingGroup
                            )
                        )
                        key()
                    }.show(parentFragmentManager, null)
                }
                true
            }

            R.id.action_apply -> {
                runOnDefaultDispatcher {
                    activity?.saveAndExit()
                }
                true
            }

            R.id.action_connection_tweaks -> {
                (activity as? ProfileSettingsActivity<*>)?.showConnectionTweaksDialog()
                true
            }

            R.id.action_custom_outbound_json -> {
                activity?.proxyEntity?.apply {
                    val bean = requireBean()
                    DataStore.serverCustomOutbound = bean.customOutboundJson
                    callbackCustomOutbound = { bean.customOutboundJson = it }
                    resultCallbackCustomOutbound.launch(
                        Intent(
                            requireContext(),
                            ConfigEditActivity::class.java
                        ).apply {
                            putExtra("key", Key.SERVER_CUSTOM_OUTBOUND)
                        })
                }
                true
            }

            R.id.action_custom_config_json -> {
                activity?.proxyEntity?.apply {
                    val bean = requireBean()
                    DataStore.serverCustom = bean.customConfigJson
                    callbackCustom = { bean.customConfigJson = it }
                    resultCallbackCustom.launch(
                        Intent(
                            requireContext(),
                            ConfigEditActivity::class.java
                        ).apply {
                            putExtra("key", Key.SERVER_CUSTOM)
                        })
                }
                true
            }

            R.id.action_create_shortcut -> {
                val activity = requireActivity() as ProfileSettingsActivity<*>
                val ent = activity.proxyEntity!!
                if (!QuickToggleShortcut.pinProfileShortcut(activity, ent.id, ent.displayName())) {
                    Toast.makeText(activity, R.string.shortcut_pin_unsupported, Toast.LENGTH_LONG).show()
                }
                true
            }

            R.id.action_move -> {
                val activity = requireActivity() as ProfileSettingsActivity<*>
                val view = LinearLayout(context).apply {
                    val ent = activity.proxyEntity!!
                    orientation = LinearLayout.VERTICAL

                    SagerDatabase.groupDao.allGroups()
                        .filter { it.type == GroupType.BASIC && it.id != ent.groupId }
                        .forEach { group ->
                            LayoutGroupItemBinding.inflate(layoutInflater, this, true).apply {
                                edit.isVisible = false
                                options.isVisible = false
                                groupName.text = group.displayName()
                                groupUpdate.text = getString(R.string.move)
                                groupUpdate.setOnClickListener {
                                    runOnDefaultDispatcher {
                                        val oldGroupId = ent.groupId
                                        val newGroupId = group.id
                                        ent.groupId = newGroupId
                                        ProfileManager.updateProfile(ent)
                                        GroupManager.postUpdate(oldGroupId) // reload
                                        GroupManager.postUpdate(newGroupId)
                                        DataStore.editingGroup = newGroupId // post switch animation
                                        runOnMainDispatcher {
                                            activity.finish()
                                        }
                                    }
                                }
                            }
                        }
                }
                val scrollView = ScrollView(context).apply {
                    addView(view)
                }
                MaterialAlertDialogBuilder(activity).setView(scrollView).show()
                true
            }

            else -> false
        }

        override fun onDisplayPreferenceDialog(preference: Preference) {
            activity?.apply {
                if (displayPreferenceDialog(preference)) return
            }
            super.onDisplayPreferenceDialog(preference)
        }

    }

    object PasswordSummaryProvider : Preference.SummaryProvider<EditTextPreference> {

        override fun provideSummary(preference: EditTextPreference): CharSequence {
            val text = preference.text
            return if (text.isNullOrBlank()) {
                preference.context.getString(androidx.preference.R.string.not_set)
            } else {
                "\u2022".repeat(text.length)
            }
        }

    }

}
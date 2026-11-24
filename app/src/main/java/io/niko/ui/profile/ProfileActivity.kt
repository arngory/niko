package io.niko.ui.profile

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.BundleCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.GroupType
import io.niko.Key
import io.niko.QuickToggleShortcut
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.database.GroupManager
import io.niko.database.ProfileManager
import io.niko.database.ProxyEntity
import io.niko.database.SagerDatabase
import io.niko.fmt.AbstractBean
import io.niko.fmt.internal.ChainBean
import io.niko.fmt.v2ray.VMessBean
import io.niko.ktx.applyDefaultValues
import io.niko.ktx.danger
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ui.ProfileSelectActivity
import io.niko.ui.ThemedActivity
import io.niko.ui.compose.NikoTheme

/**
 * A profile's editor (P-Profile) on Compose, every type's: [ProfileScreen] over the type's [ProfileSpec] and a
 * [ProfileForm], saved by «Сохранить» (back with changes asks, as the old editors did). The JSON is edited in
 * ConfigEditActivity and a chain's links picked in ProfileSelectActivity, as before: through the old editors'
 * DataStore keys (serverCustomOutbound, serverCustom, serverConfig). [EXTRA_PROFILE_ID] the profile; none: a new one
 * of [EXTRA_TYPE] (a ProxyEntity type, [EXTRA_VLESS] for VLESS), made in the group new profiles go to when saved.
 */
class ProfileActivity : ThemedActivity() {

    companion object {
        const val EXTRA_PROFILE_ID = "id"
        const val EXTRA_TYPE = "type"
        const val EXTRA_VLESS = "vless"
        private const val FORM = "form"
        private const val ORIGINAL = "original"
        private const val PAGE = "page"
        private const val ERROR = "error"
        private const val GROUP = "group"
        private const val REPLACING = "replacing"
        private const val KIND = "kind"
        private const val KIND_VLESS = "kindVless"

        fun edit(context: Context, profileId: Long): Intent =
            Intent(context, ProfileActivity::class.java).putExtra(EXTRA_PROFILE_ID, profileId)

        fun create(context: Context, type: Int, vless: Boolean = false): Intent =
            Intent(context, ProfileActivity::class.java).putExtra(EXTRA_TYPE, type).putExtra(EXTRA_VLESS, vless)
    }

    private var profileId = 0L
    // the profile's ProxyEntity type, VLESS's VMess told apart: the spec, at once after the activity is made again
    private var type = -1
    private var vless = false
    private var groupId = 0L
    private val spec = mutableStateOf<ProfileSpec?>(null)
    private val form = mutableStateOf<ProfileForm?>(null)
    private val info = mutableStateOf(ProfileInfo(existing = false))
    private val page = mutableStateOf(ProfilePage.MAIN)
    private val error = mutableStateOf<FieldError?>(null)
    // as read: back with it unchanged asks nothing, a save of it writes nothing
    private var original: ProfileForm? = null
    // the link a pick replaces, -1: a new one
    private var replacing = -1
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        profileId = intent.getLongExtra(EXTRA_PROFILE_ID, 0L)
        type = savedInstanceState?.getInt(KIND, -1)?.takeIf { it >= 0 } ?: intent.getIntExtra(EXTRA_TYPE, -1)
        vless = savedInstanceState?.getBoolean(KIND_VLESS) ?: intent.getBooleanExtra(EXTRA_VLESS, false)
        val restored = savedInstanceState?.let { BundleCompat.getParcelable(it, FORM, ProfileForm::class.java) }
        original = savedInstanceState?.let { BundleCompat.getParcelable(it, ORIGINAL, ProfileForm::class.java) }
        error.value = savedInstanceState?.let { BundleCompat.getParcelable(it, ERROR, FieldError::class.java) }
        savedInstanceState?.getString(PAGE)?.let { page.value = it }
        groupId = savedInstanceState?.getLong(GROUP) ?: 0L
        replacing = savedInstanceState?.getInt(REPLACING, -1) ?: -1
        // the form as it was, at once: a picker's result after the activity was made again comes before the profile
        // is read, and would find no form to go into
        if (restored != null && type >= 0) {
            spec.value = ProfileSpecs.of(ProfileSpecs.newBean(type, vless))
            form.value = restored
        }
        if (profileId == 0L && type < 0) return finish()
        val first = savedInstanceState == null
        if (first) DataStore.editingId = profileId
        runOnDefaultDispatcher {
            val entity = if (profileId == 0L) null else SagerDatabase.proxyDao.getById(profileId)
            if (profileId != 0L && entity == null) {
                onMainDispatcher { finish() }
                return@runOnDefaultDispatcher
            }
            val bean: AbstractBean = entity?.requireBean() ?: ProfileSpecs.newBean(type, vless).applyDefaultValues()
            if (entity == null && groupId == 0L) groupId = DataStore.selectedGroupForImport()
            // the main list follows to the group of the profile edited, as it did
            if (first) DataStore.editingGroup = entity?.groupId ?: groupId
            val typeSpec = ProfileSpecs.of(bean)
            val kind = entity?.type ?: type
            val kindVless = bean is VMessBean && bean.isVLESS
            var read = typeSpec.load(bean)
            // a link whose profile is gone is dropped, as the old chain editor dropped it; the names of the links of
            // the form as it was before the activity was made again too
            val links = linksOf((read.chain + restored?.chain.orEmpty()).distinct())
            if (bean is ChainBean) read = read.copy(chain = read.chain.filter { it in links })
            val loaded = infoOf(entity, links)
            onMainDispatcher {
                type = kind
                vless = kindVless
                spec.value = typeSpec
                if (original == null) original = read
                if (form.value == null) form.value = restored ?: read
                info.value = loaded
            }
        }
        setContent {
            NikoTheme {
                val s = spec.value
                val f = form.value
                if (s != null && f != null) {
                    val shown = info.value.let { ProfileInfo(it.existing, it.groups, it.links, error.value) }
                    ProfileScreen(s, f, shown, page.value, { p -> page.value = p }, ::change, actions)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        form.value?.let { outState.putParcelable(FORM, it) }
        original?.let { outState.putParcelable(ORIGINAL, it) }
        error.value?.let { outState.putParcelable(ERROR, it) }
        outState.putString(PAGE, page.value)
        outState.putLong(GROUP, groupId)
        outState.putInt(REPLACING, replacing)
        outState.putInt(KIND, type)
        outState.putBoolean(KIND_VLESS, vless)
    }

    /** A change of the form; the error goes once its field is typed in. */
    private fun change(changed: ProfileForm) {
        val before = form.value
        form.value = changed
        val e = error.value ?: return
        if (before != null && (before.text[e.key] != changed.text[e.key] || before.tweaks != changed.tweaks)) error.value = null
    }

    private fun linksOf(ids: List<Long>): Map<Long, ChainLink> =
        if (ids.isEmpty()) emptyMap() else ProfileManager.getProfiles(ids).associate { it.id to ChainLink(it.displayName(), it.displayType()) }

    /** The groups a profile may move to: the user's own, another than its own, and only from an own group. */
    private fun infoOf(entity: ProxyEntity?, links: Map<Long, ChainLink>): ProfileInfo {
        if (entity == null) return ProfileInfo(existing = false, links = links)
        val groups = SagerDatabase.groupDao.allGroups()
        val own = groups.filter { it.type == GroupType.BASIC }
        val movable = groups.firstOrNull { it.id == entity.groupId }?.type == GroupType.BASIC && own.size > 1
        val targets = if (movable) own.filter { it.id != entity.groupId }.map { it.id to it.displayName() } else emptyList()
        return ProfileInfo(existing = true, groups = targets, links = links)
    }

    /**
     * Writes the form and closes: a new profile is made in its group, an existing one written over (the service
     * stops if it runs on it, as before); an existing one unchanged, closes. Tweaks that the old dialog would refuse
     * stop it on their page, with its words.
     */
    private fun save() {
        val f = form.value ?: return
        val read = original ?: return
        val typeSpec = spec.value ?: return
        if (leaving) return
        if (f.tweaks != read.tweaks) f.tweaks.check()?.let {
            error.value = it
            page.value = ProfilePage.of(it.key)
            return
        }
        if (profileId != 0L && f == read) return finish()
        leaving = true
        runOnDefaultDispatcher {
            if (profileId == 0L) {
                ProfileManager.createProfile(groupId, typeSpec.create().also { typeSpec.save(f, it, read) })
            } else {
                val entity = SagerDatabase.proxyDao.getById(profileId)
                if (entity != null) {
                    if (entity.id == DataStore.selectedProxy) SagerNet.stopService()
                    typeSpec.save(f, entity.requireBean(), read)
                    ProfileManager.updateProfile(entity)
                }
            }
            onMainDispatcher { finish() }
        }
    }

    private val pickLink = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val id = result.data?.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, 0L) ?: return@registerForActivityResult
        val at = replacing
        runOnDefaultDispatcher {
            val profile = ProfileManager.getProfile(id) ?: return@runOnDefaultDispatcher
            val chain = form.value?.chain.orEmpty()
            val allowed = linkAllowed(profile, chain)
            onMainDispatcher {
                if (!allowed) {
                    MaterialAlertDialogBuilder(this@ProfileActivity).setTitle(R.string.circular_reference)
                        .setMessage(R.string.circular_reference_sum)
                        .setPositiveButton(android.R.string.ok, null).show()
                    return@onMainDispatcher
                }
                val f = form.value ?: return@onMainDispatcher
                val links = f.chain.toMutableList()
                if (at in links.indices) links[at] = profile.id else links += profile.id
                change(f.copy(chain = links))
                info.value.let {
                    info.value = ProfileInfo(it.existing, it.groups, it.links + (profile.id to ChainLink(profile.displayName(), profile.displayType())))
                }
            }
        }
    }

    /**
     * A link may not be the chain itself, nor a chain that has it or is one of its links' (the old testProfileAllowed),
     * nor a chain that has the chain edited among its links, however deep.
     */
    private fun linkAllowed(profile: ProxyEntity, chain: List<Long>): Boolean {
        if (profileId != 0L && profile.id == profileId) return false
        val links = ProfileManager.getProfiles(chain)
        if (links.any { contains(it, profile) }) return false
        if (profileId != 0L && containsId(profile, profileId, mutableSetOf())) return false
        return true
    }

    private fun contains(profile: ProxyEntity, another: ProxyEntity): Boolean {
        if (profile.type != ProxyEntity.TYPE_CHAIN || another.type != ProxyEntity.TYPE_CHAIN) return false
        if (profile.id == another.id) return true
        val proxies = profile.chainBean!!.proxies
        if (proxies.contains(another.id)) return true
        return proxies.isNotEmpty() && ProfileManager.getProfiles(proxies).any { contains(it, another) }
    }

    private fun containsId(profile: ProxyEntity, id: Long, seen: MutableSet<Long>): Boolean {
        if (profile.type != ProxyEntity.TYPE_CHAIN || !seen.add(profile.id)) return false
        val proxies = profile.chainBean?.proxies.orEmpty()
        if (id in proxies) return true
        return proxies.isNotEmpty() && ProfileManager.getProfiles(proxies).any { containsId(it, id, seen) }
    }

    private val editOutbound = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        form.value?.let { change(it.copy(outboundJson = DataStore.serverCustomOutbound)) }
    }

    private val editConfig = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        form.value?.let { change(it.copy(configJson = DataStore.serverCustom)) }
    }

    private val editCustom = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        form.value?.let { change(it.withText("config", DataStore.serverConfig)) }
    }

    private val actions = object : ProfileActions {
        override fun back() {
            val f = form.value
            val read = original
            if (f == null || read == null || f == read || leaving) return finish()
            MaterialAlertDialogBuilder(this@ProfileActivity).setTitle(R.string.unsaved_changes_prompt)
                .setPositiveButton(R.string.yes) { _, _ -> save() }
                .setNegativeButton(R.string.no) { _, _ -> finish() }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        }

        override fun save() = this@ProfileActivity.save()

        override fun delete() {
            if (profileId == 0L) return finish()
            MaterialAlertDialogBuilder(this@ProfileActivity).setTitle(R.string.delete_confirm_prompt)
                .setPositiveButton(R.string.yes) { _, _ ->
                    leaving = true
                    runOnDefaultDispatcher {
                        val group = SagerDatabase.proxyDao.getById(profileId)?.groupId ?: DataStore.editingGroup
                        ProfileManager.deleteProfile(group, profileId)
                        onMainDispatcher { finish() }
                    }
                }
                .setNegativeButton(R.string.no, null)
                .show().danger()
        }

        /** The profile as stored goes to [groupId] (what is typed and not saved is left), and the editor closes. */
        override fun move(groupId: Long) {
            leaving = true
            runOnDefaultDispatcher {
                val entity = SagerDatabase.proxyDao.getById(profileId)
                if (entity == null) {
                    onMainDispatcher { finish() }
                    return@runOnDefaultDispatcher
                }
                val oldGroupId = entity.groupId
                entity.groupId = groupId
                ProfileManager.updateProfile(entity)
                GroupManager.postUpdate(oldGroupId) // reload
                GroupManager.postUpdate(groupId)
                DataStore.editingGroup = groupId // post switch animation
                onMainDispatcher { finish() }
            }
        }

        override fun shortcut() {
            runOnDefaultDispatcher {
                val entity = SagerDatabase.proxyDao.getById(profileId) ?: return@runOnDefaultDispatcher
                onMainDispatcher {
                    if (!QuickToggleShortcut.pinProfileShortcut(this@ProfileActivity, entity.id, entity.displayName())) {
                        Toast.makeText(this@ProfileActivity, R.string.shortcut_pin_unsupported, Toast.LENGTH_LONG).show()
                    }
                }
            }
        }

        override fun editJson(target: JsonTarget) {
            val f = form.value ?: return
            val intent = Intent(this@ProfileActivity, ConfigEditActivity::class.java)
            when (target) {
                JsonTarget.OUTBOUND -> {
                    DataStore.serverCustomOutbound = f.outboundJson
                    editOutbound.launch(intent.putExtra("key", Key.SERVER_CUSTOM_OUTBOUND))
                }
                JsonTarget.CONFIG -> {
                    DataStore.serverCustom = f.configJson
                    editConfig.launch(intent.putExtra("key", Key.SERVER_CUSTOM))
                }
                JsonTarget.CUSTOM -> {
                    DataStore.serverConfig = f.text("config")
                    editCustom.launch(intent)
                }
            }
        }

        override fun pickLink(replace: Int) {
            replacing = replace
            val intent = Intent(this@ProfileActivity, ProfileSelectActivity::class.java)
            val current = form.value?.chain?.getOrNull(replace)
            if (current == null) return pickLink.launch(intent)
            runOnDefaultDispatcher {
                val selected = ProfileManager.getProfile(current)
                onMainDispatcher {
                    if (selected != null) intent.putExtra(ProfileSelectActivity.EXTRA_SELECTED, selected)
                    pickLink.launch(intent)
                }
            }
        }
    }
}

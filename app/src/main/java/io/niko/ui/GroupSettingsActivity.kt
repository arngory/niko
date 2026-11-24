package io.niko.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.BundleCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.R
import io.niko.SagerNet
import io.niko.database.GroupManager
import io.niko.database.ProfileManager
import io.niko.database.ProxyGroup
import io.niko.database.SagerDatabase
import io.niko.fmt.toUniversalLink
import io.niko.ktx.danger
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ktx.showAllowingStateLoss
import io.niko.raycontrol.RayNodes
import io.niko.ui.compose.NikoTheme
import io.niko.ui.group.GroupActions
import io.niko.ui.group.GroupForm
import io.niko.ui.group.GroupInfo
import io.niko.ui.group.GroupScreen
import io.niko.widget.QRCodeDialog

/**
 * A group's settings, a provider's subscription's with them (P-SubSettings), on Compose: [GroupScreen] over a
 * [GroupForm]. What is changed is stored when the screen is left (back, the arrow), as the settings' pages do; a new
 * group is made then, unless nothing was typed. [EXTRA_GROUP_ID] the group, none: a new one.
 */
class GroupSettingsActivity : ThemedActivity() {

    companion object {
        const val EXTRA_GROUP_ID = "id"
        private const val FORM = "form"
        private const val ORIGINAL = "original"
    }

    private var groupId = 0L
    private val form = mutableStateOf<GroupForm?>(null)
    private val info = mutableStateOf(GroupInfo(existing = false))
    // as read: unchanged, nothing is written
    private var original: GroupForm? = null
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        groupId = intent.getLongExtra(EXTRA_GROUP_ID, 0L)
        val restored = savedInstanceState?.let { BundleCompat.getParcelable(it, FORM, GroupForm::class.java) }
        original = savedInstanceState?.let { BundleCompat.getParcelable(it, ORIGINAL, GroupForm::class.java) }
        runOnDefaultDispatcher {
            val group = if (groupId == 0L) ProxyGroup() else SagerDatabase.groupDao.getById(groupId)
            if (group == null) {
                onMainDispatcher { finish() }
                return@runOnDefaultDispatcher
            }
            val read = restored ?: GroupForm.of(group)
            val host = if (groupId == 0L) null else RayNodes.list().firstOrNull { it.groupId == groupId }?.host
            val loaded = GroupInfo(groupId != 0L, host, proxyName(read.frontProxy), proxyName(read.landingProxy))
            onMainDispatcher {
                if (original == null) original = GroupForm.of(group)
                info.value = loaded
                form.value = read
            }
        }
        setContent {
            NikoTheme {
                form.value?.let { GroupScreen(it, info.value, { changed -> form.value = changed }, actions) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        form.value?.let { outState.putParcelable(FORM, it) }
        original?.let { outState.putParcelable(ORIGINAL, it) }
    }

    private fun proxyName(id: Long) = if (id < 0) null else ProfileManager.getProfile(id)?.displayName()

    /** Stores the form, unless nothing changed or nothing was typed into a new group, then closes. */
    private fun leave() {
        val f = form.value ?: return finish()
        if (leaving) return
        if (groupId == 0L && f.blank) return finish()
        if (groupId != 0L && f == original) return finish()
        if (f.subscription && f.link.isBlank() && info.value.nodeHost == null) {
            Toast.makeText(this, R.string.gs_link_needed, Toast.LENGTH_SHORT).show()
            return
        }
        leaving = true
        val defaultName = getString(R.string.gs_kind_own)
        val locked = info.value.nodeHost != null
        runOnDefaultDispatcher {
            if (groupId == 0L) {
                GroupManager.createGroup(ProxyGroup().also { f.applyTo(it, defaultName) })
            } else {
                SagerDatabase.groupDao.getById(groupId)?.let { group ->
                    f.applyTo(group, defaultName, locked)
                    GroupManager.updateGroup(group)
                }
            }
            onMainDispatcher { finish() }
        }
    }

    // the config chosen in the picker, for the front or the landing proxy
    private var pickingFront = true
    private val pickProxy = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val id = result.data?.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, 0L) ?: return@registerForActivityResult
        val front = pickingFront
        runOnDefaultDispatcher {
            val name = ProfileManager.getProfile(id)?.displayName() ?: return@runOnDefaultDispatcher
            onMainDispatcher {
                val f = form.value ?: return@onMainDispatcher
                if (front) {
                    form.value = f.copy(frontProxy = id)
                    info.value = GroupInfo(info.value.existing, info.value.nodeHost, name, info.value.landingName)
                } else {
                    form.value = f.copy(landingProxy = id)
                    info.value = GroupInfo(info.value.existing, info.value.nodeHost, info.value.frontName, name)
                }
            }
        }
    }

    private val actions = object : GroupActions {
        override fun back() = leave()

        override fun share() {
            runOnDefaultDispatcher {
                val group = SagerDatabase.groupDao.getById(groupId) ?: return@runOnDefaultDispatcher
                if (RayNodes.ownsGroup(group.id)) return@runOnDefaultDispatcher // not shared, as the screen shows
                val link = group.toUniversalLink()
                onMainDispatcher {
                    if (!isFinishing) QRCodeDialog(link, group.displayName()).showAllowingStateLoss(supportFragmentManager)
                }
            }
        }

        override fun delete() {
            val name = form.value?.name?.ifBlank { null } ?: getString(R.string.gs_group)
            MaterialAlertDialogBuilder(this@GroupSettingsActivity).setTitle(R.string.confirm)
                .setMessage(getString(R.string.gs_delete_confirm, name))
                .setPositiveButton(R.string.yes) { _, _ ->
                    leaving = true
                    runOnDefaultDispatcher {
                        GroupManager.deleteGroup(groupId)
                        onMainDispatcher { finish() }
                    }
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show().danger()
        }

        override fun copyLink(link: String) {
            Toast.makeText(
                this@GroupSettingsActivity,
                if (SagerNet.trySetPrimaryClip(link)) R.string.action_export_msg else R.string.action_export_err,
                Toast.LENGTH_SHORT,
            ).show()
        }

        override fun pickProxy(front: Boolean) {
            pickingFront = front
            pickProxy.launch(
                ProfileSelectActivity.intent(this@GroupSettingsActivity, getString(if (front) R.string.pick_for_front else R.string.pick_for_landing)),
            )
        }
    }
}

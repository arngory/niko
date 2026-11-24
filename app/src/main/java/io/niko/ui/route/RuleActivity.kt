package io.niko.ui.route

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.os.BundleCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.R
import io.niko.database.DataStore
import io.niko.database.ProfileManager
import io.niko.database.RuleEntity
import io.niko.database.SagerDatabase
import io.niko.ktx.Logs
import io.niko.ktx.danger
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ui.ProfileSelectActivity
import io.niko.ui.ThemedActivity
import io.niko.ui.apps.AppsActivity
import io.niko.ui.compose.NikoTheme
import io.niko.ui.profile.ConfigEditActivity
import io.niko.utils.PackageCache

/**
 * A routing rule's editor (P-Rule) on Compose: [RuleScreen] over a [RuleForm], saved by «Сохранить» (back with
 * changes asks, as the old editor did). The apps are picked in AppsActivity, handed over and back in its intents; the
 * profile in ProfileSelectActivity and the rule's JSON in ConfigEditActivity, as before, the JSON through the old
 * editor's DataStore key (serverConfig). [EXTRA_RULE_ID] the rule, none: a new one, on when saved.
 */
class RuleActivity : ThemedActivity() {

    companion object {
        const val EXTRA_RULE_ID = "id"
        private const val FORM = "form"
        private const val ORIGINAL = "original"
        private const val PAGE = "page"
    }

    private var ruleId = 0L
    private val form = mutableStateOf<RuleForm?>(null)
    private val info = mutableStateOf(RuleInfo(existing = false))
    private val page = mutableStateOf(RulePage.MAIN)
    // as read: saving it unchanged writes nothing
    private var original: RuleForm? = null
    private var leaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ruleId = intent.getLongExtra(EXTRA_RULE_ID, 0L)
        val restored = savedInstanceState?.let { BundleCompat.getParcelable(it, FORM, RuleForm::class.java) }
        original = savedInstanceState?.let { BundleCompat.getParcelable(it, ORIGINAL, RuleForm::class.java) }
        savedInstanceState?.getString(PAGE)?.let { saved -> RulePage.entries.firstOrNull { it.name == saved } }?.let { page.value = it }
        // the form as it was, at once: a picker's result after the activity was made again comes before the rule is
        // read, and would find no form to go into
        if (restored != null) form.value = restored
        runOnDefaultDispatcher {
            val rule = if (ruleId == 0L) RuleEntity() else SagerDatabase.rulesDao.getById(ruleId)
            if (rule == null) {
                onMainDispatcher { finish() }
                return@runOnDefaultDispatcher
            }
            val read = restored ?: RuleForm.of(rule)
            val loaded = infoOf(read)
            onMainDispatcher {
                if (original == null) original = RuleForm.of(rule)
                if (form.value == null) form.value = read
                // a picker's result changed the form meanwhile: its labels and profile, not the ones read here
                if (form.value == read) info.value = loaded else reloadInfo()
            }
        }
        setContent {
            NikoTheme {
                form.value?.let {
                    RuleScreen(it, info.value, page.value, { p -> page.value = p }, { changed -> form.value = changed }, actions)
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        form.value?.let { outState.putParcelable(FORM, it) }
        original?.let { outState.putParcelable(ORIGINAL, it) }
        outState.putString(PAGE, page.value.name)
    }

    /** The profile's name and the apps' labels for [f] (the package manager: off the main thread). */
    private fun infoOf(f: RuleForm): RuleInfo {
        val profile = if (f.out == RuleOut.PROFILE) ProfileManager.getProfile(f.outbound)?.displayName() else null
        val labels = if (f.packages.isEmpty()) emptyMap() else runCatching {
            PackageCache.awaitLoadSync()
            f.packages.associateWith { PackageCache.loadLabel(it) }
        }.onFailure { Logs.w(it) }.getOrDefault(emptyMap())
        return RuleInfo(ruleId != 0L, profile, labels)
    }

    private fun reloadInfo() {
        val f = form.value ?: return
        runOnDefaultDispatcher {
            val loaded = infoOf(f)
            onMainDispatcher { info.value = loaded }
        }
    }

    /** Writes the form (a new rule is on) and closes; unchanged, closes; a new rule with nothing set, says so. */
    private fun save() {
        val f = form.value ?: return finish()
        val read = original ?: return finish()
        if (leaving) return
        when (ruleSave(f, read, ruleId != 0L)) {
            RuleSave.UNCHANGED -> finish()
            RuleSave.EMPTY -> MaterialAlertDialogBuilder(this).setTitle(R.string.empty_route)
                .setMessage(R.string.empty_route_notice)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            RuleSave.WRITE -> {
                leaving = true
                runOnDefaultDispatcher {
                    if (ruleId == 0L) {
                        ProfileManager.createRule(RuleEntity(enabled = true).also { f.applyTo(it) })
                    } else {
                        SagerDatabase.rulesDao.getById(ruleId)?.let { rule ->
                            f.applyTo(rule)
                            ProfileManager.updateRule(rule)
                        }
                    }
                    onMainDispatcher { finish() }
                }
            }
        }
    }

    // the picker hands the choice back only when it changed (AppsActivity.EXTRA_PACKAGES)
    private val pickApps = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val picked = result.data?.getStringExtra(AppsActivity.EXTRA_PACKAGES) ?: return@registerForActivityResult
        val f = form.value ?: return@registerForActivityResult
        form.value = f.copy(packages = RuleForm.packagesOf(picked))
        reloadInfo()
    }

    private val pickProfile = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val id = result.data?.getLongExtra(ProfileSelectActivity.EXTRA_PROFILE_ID, 0L) ?: return@registerForActivityResult
        val f = form.value ?: return@registerForActivityResult
        runOnDefaultDispatcher {
            val profile = ProfileManager.getProfile(id) ?: return@runOnDefaultDispatcher
            onMainDispatcher {
                form.value = f.copy(outbound = profile.id)
                info.value = RuleInfo(info.value.existing, profile.displayName(), info.value.labels)
            }
        }
    }

    private val editJson = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        val f = form.value ?: return@registerForActivityResult
        form.value = f.copy(config = DataStore.serverConfig)
    }

    private val actions = object : RuleActions {
        override fun back() {
            val f = form.value
            val read = original
            if (f == null || read == null || f == read || leaving) return finish()
            MaterialAlertDialogBuilder(this@RuleActivity).setTitle(R.string.unsaved_changes_prompt)
                .setPositiveButton(R.string.yes) { _, _ -> save() }
                .setNegativeButton(R.string.no) { _, _ -> finish() }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        }

        override fun save() = this@RuleActivity.save()

        override fun delete() {
            MaterialAlertDialogBuilder(this@RuleActivity).setTitle(R.string.delete_route_prompt)
                .setPositiveButton(R.string.yes) { _, _ ->
                    leaving = true
                    runOnDefaultDispatcher {
                        ProfileManager.deleteRule(ruleId)
                        onMainDispatcher { finish() }
                    }
                }
                .setNegativeButton(R.string.no, null)
                .show().danger()
        }

        override fun pickApps() {
            pickApps.launch(
                Intent(this@RuleActivity, AppsActivity::class.java).putExtra(AppsActivity.EXTRA_FOR_RULE, true)
                    .putExtra(AppsActivity.EXTRA_PACKAGES, form.value?.packages.orEmpty().joinToString("\n")),
            )
        }

        override fun pickProfile() {
            pickProfile.launch(Intent(this@RuleActivity, ProfileSelectActivity::class.java))
        }

        override fun editJson() {
            DataStore.serverConfig = form.value?.config.orEmpty()
            editJson.launch(Intent(this@RuleActivity, ConfigEditActivity::class.java))
        }
    }
}

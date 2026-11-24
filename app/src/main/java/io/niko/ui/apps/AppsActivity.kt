package io.niko.ui.apps

import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.LruCache
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.lifecycleScope
import com.google.android.material.snackbar.Snackbar
import io.niko.BuildConfig
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.ktx.Logs
import io.niko.ui.ThemedActivity
import io.niko.ui.compose.NikoDialog
import io.niko.ui.compose.NikoTheme
import io.niko.ui.route.LetterTile
import io.niko.utils.PackageCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Приложения (P-Apps) on Compose, in place of AppManagerActivity and AppListActivity: the apps through the VPN
 * (DataStore.proxyApps, bypass, individual) or, with [EXTRA_FOR_RULE], a rule's apps: [EXTRA_PACKAGES] in, the choice
 * back in the result's [EXTRA_PACKAGES] (RESULT_OK only when it changed), kept in the saved state, so that a process the
 * system killed meanwhile does not hand the rule an empty list (the old picker passed it through DataStore.routePackages,
 * which lives in memory). A change of the VPN's choice is written at once, as before; the main screen offers to apply it
 * to a running service (D-STATE-RELOAD).
 */
class AppsActivity : ThemedActivity() {

    companion object {
        const val EXTRA_FOR_RULE = "for_rule"
        /** a rule's packages, one a line: in, and back in the result */
        const val EXTRA_PACKAGES = "packages"
        private const val CHOSEN = "chosen"
        private const val CHANGED = "changed"
        private const val QUERY = "query"
        private const val SYSTEM = "system"

        // the icon's size in the row (LetterTile's)
        private const val ICON_DP = 32
        private const val ICON_CACHE_BYTES = 8 * 1024 * 1024
    }

    private var forRule = false
    // a rule's choice as it is now, and whether it changed (the result to hand back)
    private var ruleChoice = ""
    private var ruleChanged = false
    private val state = mutableStateOf(AppsState(forRule = false))
    private val menu = mutableStateOf(false)
    // the icons read, by package: a few megabytes at most (a phone has hundreds of apps), the least recent dropped
    private val icons = object : LruCache<String, Bitmap>(ICON_CACHE_BYTES) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forRule = intent.getBooleanExtra(EXTRA_FOR_RULE, false)
        if (forRule) {
            ruleChoice = savedInstanceState?.getString(CHOSEN) ?: intent.getStringExtra(EXTRA_PACKAGES).orEmpty()
            ruleChanged = savedInstanceState?.getBoolean(CHANGED) == true
            // the result is the activity's, not its saved state's: set again in an activity made anew
            if (ruleChanged) handBack()
        }
        val mode = if (forRule) AppsMode.ALL else AppsMode.of(DataStore.proxyApps, DataStore.bypass)
        state.value = AppsState(
            forRule, mode,
            system = savedInstanceState?.getBoolean(SYSTEM) ?: !forRule,
            query = savedInstanceState?.getString(QUERY).orEmpty(),
        )
        load()
        setContent {
            NikoTheme {
                AppsScreen(state.value, actions, menuOpen = menu.value, onMenu = { menu.value = it }) { AppIcon(it) }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(QUERY, state.value.query)
        outState.putBoolean(SYSTEM, state.value.system)
        if (forRule) {
            outState.putString(CHOSEN, ruleChoice)
            outState.putBoolean(CHANGED, ruleChanged)
        }
    }

    /** The installed apps with the internet (PackageCache's list, Niko itself left out), and the choice written. */
    private fun load() = lifecycleScope.launch {
        val rows = withContext(Dispatchers.IO) {
            runCatching {
                PackageCache.reload()
                PackageCache.installedPackages.values.mapNotNull { info ->
                    val ai = info.applicationInfo ?: return@mapNotNull null
                    if (info.packageName == BuildConfig.APPLICATION_ID) return@mapNotNull null
                    AppRow(
                        info.packageName, ai.loadLabel(packageManager).toString(), ai.uid,
                        (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }
            }.onFailure { Logs.w(it) }.getOrDefault(emptyList())
        }
        val chosen = chosenWritten().lineSequence().toList()
        val now = state.value
        state.value = AppsState.read(forRule, now.mode, rows, chosen, now.system).copy(query = now.query)
    }

    /** The choice written where its owner reads it; never before the list is read, nor from an empty list. */
    private fun write(next: AppsState) {
        state.value = next
        if (next.apps.isNullOrEmpty()) return
        store(next.packages)
    }

    /** [packages] as the choice: the VPN's in DataStore, a rule's handed back. */
    private fun store(packages: String) {
        if (!forRule) {
            DataStore.individual = packages
            return
        }
        ruleChoice = packages
        ruleChanged = true
        handBack()
    }

    private fun handBack() = setResult(RESULT_OK, Intent().putExtra(EXTRA_PACKAGES, ruleChoice))

    private fun say(text: Int) = Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG).show()

    private val actions = object : AppsActions {
        override fun back() = finish()

        override fun mode(mode: AppsMode) {
            when (mode) {
                AppsMode.ALL -> DataStore.proxyApps = false
                AppsMode.ONLY -> {
                    DataStore.bypass = false
                    DataStore.proxyApps = true
                }
                AppsMode.EXCEPT -> {
                    DataStore.bypass = true
                    DataStore.proxyApps = true
                }
            }
            state.value = state.value.copy(mode = mode)
        }

        override fun toggle(uid: Int) = write(state.value.toggle(uid))

        override fun query(text: String) {
            state.value = state.value.copy(query = text)
        }

        override fun system(on: Boolean) {
            state.value = state.value.copy(system = on)
        }

        override fun invert() = write(state.value.inverted())

        override fun clear() = write(state.value.cleared())

        override fun auto() {
            val apps = state.value.apps ?: return
            NikoDialog(this@AppsActivity).title(R.string.confirm)
                .message(R.string.auto_select_proxy_apps_message)
                .ghost(R.string.no)
                .primary(R.string.yes) { _, _ ->
                    lifecycleScope.launch {
                        val listed = withContext(Dispatchers.IO) {
                            runCatching {
                                assets.open("proxy_packagename.txt").bufferedReader().use { it.readText() }
                                    .lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toSet()
                            }.onFailure { Logs.w(it) }.getOrDefault(emptySet())
                        }
                        val now = state.value
                        write(now.choosing(AppsState.auto(apps, listed, now.mode)))
                    }
                }
                .show()
        }

        override fun export() {
            val now = state.value
            val bypass = !forRule && DataStore.bypass
            val text = AppsClip.write(bypass, if (now.apps.isNullOrEmpty()) chosenWritten() else now.packages)
            say(if (SagerNet.trySetPrimaryClip(text)) R.string.ap_copied else R.string.ap_copy_failed)
        }

        override fun import() {
            val read = AppsClip.read(runCatching { SagerNet.clipboard.primaryClip?.getItemAt(0)?.text?.toString() }.getOrNull())
            if (read == null) return say(R.string.ap_paste_failed)
            val (bypass, packages) = read
            // the VPN's choice brings its mode, as the old screen's did; a rule's has none
            if (!forRule) mode(if (bypass) AppsMode.EXCEPT else AppsMode.ONLY)
            // the list not read (or not given): the pasted packages are written as they are, as the old screen did
            if (state.value.apps.isNullOrEmpty()) store(packages.joinToString("\n")) else write(state.value.choosing(packages))
            say(R.string.ap_pasted)
        }

        override fun openSettings() {
            startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
        }
    }

    private fun chosenWritten() = if (forRule) ruleChoice else DataStore.individual

    /** An app's icon, read off the main thread once; its letter until then (and when it has none). */
    @Composable
    private fun AppIcon(app: AppRow) {
        val size = (ICON_DP * resources.displayMetrics.density).toInt()
        val bitmap by produceState(icons.get(app.packageName), app.packageName) {
            if (value == null) value = withContext(Dispatchers.IO) {
                runCatching {
                    packageManager.getApplicationIcon(app.packageName).toBitmap(size, size).also { icons.put(app.packageName, it) }
                }.getOrNull()
            }
        }
        val b = bitmap
        if (b == null) LetterTile(app.name) else Image(b.asImageBitmap(), null, Modifier.size(ICON_DP.dp))
    }

    // the TV's Menu key opens ⋮, as it did the old screens' menu
    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            menu.value = !menu.value
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onResume() {
        super.onResume()
        // back from the system's settings with the list allowed: read it again
        if (state.value.apps?.isEmpty() == true) load()
    }
}

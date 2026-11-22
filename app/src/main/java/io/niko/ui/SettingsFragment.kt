package io.niko.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import io.niko.BuildConfig
import io.niko.Key
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.database.SagerDatabase
import io.niko.database.preference.PublicDatabase
import io.niko.ktx.needReload
import io.niko.ktx.needRestart
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ktx.snackbar
import io.niko.ui.compose.NikoTheme
import io.niko.ui.profile.ConfigEditActivity
import io.niko.ui.settings.Choice
import io.niko.ui.settings.DpiInfo
import io.niko.ui.settings.SettingEffect
import io.niko.ui.settings.SettingKeys
import io.niko.ui.settings.SettingsActions
import io.niko.ui.settings.SettingsInfo
import io.niko.ui.settings.SettingsPage
import io.niko.ui.settings.SettingsScreen
import io.niko.ui.settings.StoreSettingValues
import io.niko.utils.TcpDesync
import io.niko.utils.Theme
import libcore.Libcore
import java.security.SecureRandom

/**
 * Настройки (P-Settings): the list and its pages on Compose (ui/settings), over DataStore's settings. A page's text is
 * written when the page is left (back, another screen, the app in the background); a change the running service has
 * to take asks for a reload, as the old screen did.
 */
class SettingsFragment : Fragment() {

    private val values = StoreSettingValues(DataStore.configurationStore)
    private var page by mutableStateOf(SettingsPage.ROOT)
    private var sheet by mutableStateOf<Choice?>(null)
    private var info by mutableStateOf(SettingsInfo(BuildConfig.VERSION_NAME, 0, null))

    private val main get() = requireActivity() as MainActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        DataStore.initGlobal()
        // a switch or a choice is written at once, and so is what it asks for
        values.onChange = { apply(listOf(it)) }
        values.writeMissingDefaults { PublicDatabase.kvPairDao[it] != null }
        savedInstanceState?.getString(PAGE)?.let { saved -> SettingsPage.entries.firstOrNull { it.name == saved } }?.let { page = it }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NikoTheme {
                    SettingsScreen(page, values, info, actions, sheet) { sheet = null }
                }
            }
        }

    /** Back (MainActivity's back): the choice open closes, a page goes back to the list, the list to the main screen. */
    fun back() = actions.back()

    override fun onResume() {
        super.onResume()
        // the custom config is edited in its own activity; the address follows the key made on the first enable
        val config = DataStore.globalCustomConfig
        info = SettingsInfo(
            BuildConfig.VERSION_NAME,
            if (config.isBlank()) 0 else config.split('\n').size,
            yggdrasilAddress(),
            info.dpi,
        )
        loadDpi()
    }

    /** The DPI page's: what the phone can send (probed once, off the main thread) and the rules with the flag. */
    private fun loadDpi() = runOnDefaultDispatcher {
        val dpi = DpiInfo(TcpDesync.capabilities, SagerDatabase.rulesDao.enabledRules().count { it.dpiBypass && it.outbound == -1L })
        onMainDispatcher { info = SettingsInfo(info.version, info.customConfigLines, info.yggdrasilAddress, dpi) }
    }

    override fun onPause() {
        super.onPause()
        apply(values.commit())
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(PAGE, page.name)
    }

    private fun yggdrasilAddress(): String? = if (!DataStore.yggdrasilEnabled) null else try {
        Libcore.yggdrasilAddress(DataStore.yggdrasilPrivateKey).ifEmpty { null }
    } catch (_: Throwable) {
        null
    }

    /** What the changed [keys] ask of the app and of a running service. */
    private fun apply(keys: List<String>) {
        if (keys.isEmpty()) return
        for (key in keys) when (key) {
            Key.NIGHT_THEME -> {
                Theme.currentNightMode = DataStore.nightTheme
                Theme.applyNightTheme()
            }
            // another mode is another service: the running one stops, as the old screen did
            Key.SERVICE_MODE -> if (DataStore.serviceState.started) SagerNet.stopService()
            Key.YGGDRASIL_ENABLED -> info = SettingsInfo(info.version, info.customConfigLines, yggdrasilAddress(), info.dpi)
        }
        val effects = keys.map(SettingKeys::effect)
        when {
            SettingEffect.RESTART in effects -> needRestart()
            SettingEffect.RELOAD in effects -> needReload()
        }
    }

    private val actions = object : SettingsActions {
        override fun back() {
            when {
                sheet != null -> sheet = null
                page != SettingsPage.ROOT -> {
                    apply(values.commit())
                    page = page.parent
                }

                else -> main.displayFragmentWithId(R.id.nav_home)
            }
        }

        override fun open(page: SettingsPage) {
            this@SettingsFragment.page = page
        }

        override fun choose(choice: Choice) {
            sheet = choice
        }

        override fun newPassword() {
            values.setText(Key.MIXED_PASSWORD, newPassword(16))
        }

        override fun customConfig() {
            startActivity(
                Intent(requireContext(), ConfigEditActivity::class.java)
                    .putExtra("useConfigStore", "1")
                    .putExtra("key", Key.GLOBAL_CUSTOM_CONFIG),
            )
        }

        override fun backup() {
            main.displayFragment(BackupFragment())
        }

        override fun about() {
            main.displayFragmentWithId(R.id.nav_about)
        }

        override fun copy(text: String) {
            SagerNet.trySetPrimaryClip(text)
            snackbar(R.string.copy_success).show()
        }
    }

    companion object {
        private const val PAGE = "page"
        private const val ALPHABET = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789"

        /** A new password of [length] letters and digits, none of the ones alike (l, 1, O, 0). */
        fun newPassword(length: Int): String {
            val random = SecureRandom()
            return String(CharArray(length) { ALPHABET[random.nextInt(ALPHABET.length)] })
        }
    }
}

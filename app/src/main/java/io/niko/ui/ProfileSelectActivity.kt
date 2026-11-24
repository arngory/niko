package io.niko.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.niko.database.ProxyEntity
import io.niko.ktx.Logs
import io.niko.ui.compose.NikoTheme
import io.niko.ui.home.ConfigsLoader
import io.niko.ui.pick.PickScreen
import io.niko.ui.pick.PickState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Выбор профиля on Compose (P-Pick, D-PICK-01), in place of the old list in its select mode: a profile for a chain's
 * link, a rule's outbound, a group's front or landing proxy. The profiles as the configs' sheet lists them; a tap
 * returns [EXTRA_PROFILE_ID]. [EXTRA_SELECTED] the profile the choice replaces, the list opened on it;
 * [EXTRA_PURPOSE] what it is picked for, under the title.
 */
open class ProfileSelectActivity : ThemedActivity() {

    companion object {
        const val EXTRA_SELECTED = "selected"
        const val EXTRA_PROFILE_ID = "id"
        const val EXTRA_PURPOSE = "purpose"
        private const val QUERY = "query"
        private const val SEARCHING = "searching"

        fun intent(context: Context, purpose: String) =
            Intent(context, ProfileSelectActivity::class.java).putExtra(EXTRA_PURPOSE, purpose)
    }

    private var state by mutableStateOf(PickState())

    /** The profile the list opens on, in bold: the one the choice replaces. */
    @Suppress("DEPRECATION")
    protected open fun current(): Long? = intent.getParcelableExtra<ProxyEntity>(EXTRA_SELECTED)?.id

    /** What the profile is picked for, under the title. */
    protected open fun purpose(): String? = intent.getStringExtra(EXTRA_PURPOSE)

    /** A profile tapped: back to the screen that asked, with it. */
    protected open fun picked(id: Long) {
        setResult(RESULT_OK, Intent().putExtra(EXTRA_PROFILE_ID, id))
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        state = PickState(
            current = current(),
            purpose = purpose(),
            searching = savedInstanceState?.getBoolean(SEARCHING) ?: false,
            query = savedInstanceState?.getString(QUERY).orEmpty(),
        )
        setContent {
            NikoTheme {
                PickScreen(
                    state, ::finish,
                    onSearch = { on -> state = state.copy(searching = on, query = if (on) state.query else "") },
                    onQuery = { state = state.copy(query = it) },
                    onPick = ::picked,
                )
            }
        }
        lifecycleScope.launch(Dispatchers.IO) {
            val groups = try {
                // no profile is «the chosen one» here: the radio's mark is the sheet's
                ConfigsLoader.load().map { g -> g.copy(profiles = g.profiles.map { it.copy(selected = false) }) }
            } catch (e: Exception) {
                Logs.w(e)
                emptyList()
            }
            withContext(Dispatchers.Main) { state = state.copy(groups = groups) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(SEARCHING, state.searching)
        outState.putString(QUERY, state.query)
    }
}

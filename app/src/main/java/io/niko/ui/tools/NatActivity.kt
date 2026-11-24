package io.niko.ui.tools

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.lifecycleScope
import io.niko.ktx.Logs
import io.niko.ktx.readableMessage
import io.niko.ui.ThemedActivity
import io.niko.ui.compose.NikoTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import libcore.Libcore

/**
 * Тип NAT on Compose, in place of StunActivity: the same check (Libcore.stunTest, past the tunnel), its text read
 * into rows; an error stays on the page, where the old screen closed after its dialog.
 */
class NatActivity : ThemedActivity() {

    companion object {
        private const val SERVER = "server"
    }

    private var state by mutableStateOf(NatState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(SERVER)?.let { state = state.copy(server = it) }
        setContent {
            NikoTheme { NatScreen(state, ::finish, { state = state.copy(server = it) }, ::check) }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(SERVER, state.server)
    }

    private fun check() {
        if (state.running) return
        val server = state.server.trim()
        state = state.copy(running = true, error = null)
        lifecycleScope.launch(Dispatchers.Default) {
            val (result, error) = try {
                val r = Libcore.stunTest(server)!!
                if (r.success) NatResult.parse(r.text) to null else null to r.text
            } catch (e: Exception) {
                Logs.w(e)
                null to e.readableMessage
            }
            withContext(Dispatchers.Main) { state = state.copy(running = false, result = result, error = error) }
        }
    }
}

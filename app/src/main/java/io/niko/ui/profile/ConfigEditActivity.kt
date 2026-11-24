package io.niko.ui.profile

import android.os.Bundle
import android.os.SystemClock
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.ViewModel
import io.niko.Key
import io.niko.R
import io.niko.database.DataStore
import io.niko.ktx.readableMessage
import io.niko.ktx.toStringPretty
import io.niko.ui.ThemedActivity
import io.niko.ui.compose.NikoTheme
import org.json.JSONObject

/**
 * Редактор JSON on Compose (P-Json), in place of the EditorKit view: the text of the DataStore key the caller names
 * (`key`; `useConfigStore` for a setting's), checked and formatted when saved, RESULT_OK then. Undo, redo and format
 * in the bar, the keys JSON needs over the keyboard, a parse error in place with its line; back over changes asks.
 */
class ConfigEditActivity : ThemedActivity() {

    /** The text and its history, kept over a rotation (a config may be too long for the saved state). */
    class Model : ViewModel() {
        var history: JsonHistory? = null
        var dirty = false
    }

    private val model: Model by viewModels()
    private var key = Key.SERVER_CONFIG
    private var useConfigStore = false
    private var state by mutableStateOf(JsonState())

    private fun JsonText.field() = TextFieldValue(text, TextRange(start.coerceIn(0, text.length), end.coerceIn(0, text.length)))

    private fun show(history: JsonHistory, error: JsonError? = state.error) {
        state = state.copy(value = history.current.field(), canUndo = history.canUndo, canRedo = history.canRedo, error = error)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        intent?.extras?.apply {
            getString("key")?.let { key = it }
            getString("useConfigStore")?.let { useConfigStore = true }
        }
        val history = model.history ?: JsonHistory(
            JsonText((if (useConfigStore) DataStore.configurationStore else DataStore.profileCacheStore).getString(key) ?: "", 0),
        ).also { model.history = it }
        show(history, null)
        onBackPressedDispatcher.addCallback(this) { actions.back() }
        setContent {
            NikoTheme { JsonScreen(getString(R.string.config_settings), state, actions) }
        }
    }

    /** The text as the store keeps it: empty, or the object pretty-printed; null with the error shown. */
    private fun formatted(): String? {
        val text = state.value.text
        if (text.isBlank()) return ""
        return try {
            JSONObject(text).toStringPretty()
        } catch (e: Exception) {
            state = state.copy(error = JsonError.of(e.readableMessage, text))
            null
        }
    }

    private fun saveAndExit() {
        val text = formatted() ?: return
        (if (useConfigStore) DataStore.configurationStore else DataStore.profileCacheStore).putString(key, text)
        // saved: the caller takes the text (back without saving leaves its own as it was)
        setResult(RESULT_OK)
        finish()
    }

    private val actions = object : JsonActions {
        override fun back() {
            if (model.dirty) state = state.copy(leaving = true) else finish()
        }

        override fun value(value: TextFieldValue) {
            val history = model.history ?: return
            if (value.text != history.current.text && !model.dirty) {
                model.dirty = true
                DataStore.dirty = true
            }
            val changed = value.text != history.current.text
            history.set(JsonText(value.text, value.selection.start, value.selection.end), SystemClock.uptimeMillis())
            // the field keeps its composing text; an edit clears the error it was about
            state = state.copy(value = value, canUndo = history.canUndo, canRedo = history.canRedo, error = if (changed) null else state.error)
        }

        override fun undo() {
            val history = model.history ?: return
            if (history.undo() != null) show(history, null)
        }

        override fun redo() {
            val history = model.history ?: return
            if (history.redo() != null) show(history, null)
        }

        override fun format() {
            val history = model.history ?: return
            val text = formatted() ?: return
            if (text != history.current.text) {
                history.set(JsonText(text, 0), SystemClock.uptimeMillis(), typed = false)
                model.dirty = true
                DataStore.dirty = true
            }
            show(history, null)
        }

        override fun key(text: String) {
            val history = model.history ?: return
            history.insert(text, SystemClock.uptimeMillis())
            if (!model.dirty) {
                model.dirty = true
                DataStore.dirty = true
            }
            show(history, null)
        }

        override fun save() = saveAndExit()

        override fun leave(save: Boolean?) {
            state = state.copy(leaving = false)
            when (save) {
                true -> saveAndExit()
                false -> finish()
                null -> {}
            }
        }
    }
}

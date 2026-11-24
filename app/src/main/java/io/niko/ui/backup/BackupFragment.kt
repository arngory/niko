package io.niko.ui.backup

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.BuildConfig
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.ktx.Logs
import io.niko.ktx.alert
import io.niko.ktx.app
import io.niko.ktx.danger
import io.niko.ktx.onMainDispatcher
import io.niko.ktx.readableMessage
import io.niko.ktx.runOnDefaultDispatcher
import io.niko.ktx.snackbar
import io.niko.ktx.startFilesForResult
import io.niko.ktx.triggerFullRestart
import io.niko.ui.MainActivity
import io.niko.ui.compose.NikoTheme
import org.json.JSONObject
import java.io.File
import java.util.Date

/**
 * Резервная копия on Compose, in place of the old BackupFragment: the same file (Backup, version 1), saved, shared or
 * restored part by part; the reset of the settings, nodes and profiles kept. Opened from the settings, back to them.
 */
class BackupFragment : Fragment() {

    companion object {
        private const val PARTS = "parts"
    }

    private var state by mutableStateOf(BackupState())

    // the file picked to restore, read; and the text a save writes
    private var picked: JSONObject? = null
    private var content = ""

    private val main get() = requireActivity() as MainActivity

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getStringArrayList(PARTS)?.let { saved ->
            state = state.copy(parts = BackupPart.entries.filterTo(LinkedHashSet()) { it.name in saved })
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { NikoTheme { BackupScreen(state, actions) } }
        }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(PARTS, ArrayList(state.parts.map { it.name }))
    }

    private fun fileName() = "niko_backup_${Date().toLocaleString()}.json"

    private val saveFile = registerForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@registerForActivityResult
        val text = content
        runOnDefaultDispatcher {
            try {
                app.contentResolver.openOutputStream(uri)!!.bufferedWriter().use { it.write(text) }
                onMainDispatcher { if (isAdded) snackbar(getString(R.string.action_export_msg)).show() }
            } catch (e: Exception) {
                Logs.w(e)
                onMainDispatcher { if (isAdded) snackbar(e.readableMessage).show() }
            }
        }
    }

    private val pickFile = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) runOnDefaultDispatcher { read(uri) }
    }

    /** The file at [uri]: a .json of version 1 offers its parts to restore; anything else says why not. */
    private suspend fun read(uri: Uri) {
        val name = app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME)) else null
        }?.takeIf { it.isNotBlank() } ?: uri.pathSegments.last().substringAfterLast('/').substringAfter(':')
        if (!name.endsWith(".json")) {
            onMainDispatcher { if (isAdded) snackbar(getString(R.string.backup_not_file, name)).show() }
            return
        }
        val text = try {
            app.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
        } catch (e: Exception) {
            Logs.w(e)
            null
        }
        val parsed = text?.let(Backup::parts)
        onMainDispatcher {
            if (!isAdded) return@onMainDispatcher
            if (parsed == null || parsed.second.isEmpty()) {
                snackbar(getString(R.string.invalid_backup_file)).show()
                return@onMainDispatcher
            }
            picked = parsed.first
            state = state.copy(restore = BackupRestore(parsed.second))
        }
    }

    private val actions = object : BackupActions {
        override fun back() {
            main.displayFragmentWithId(R.id.nav_settings)
        }

        override fun part(part: BackupPart, on: Boolean) {
            state = state.copy(parts = if (on) state.parts + part else state.parts - part)
        }

        override fun save() {
            val parts = state.parts
            runOnDefaultDispatcher {
                content = Backup.write(parts)
                onMainDispatcher { if (isAdded) startFilesForResult(saveFile, fileName()) }
            }
        }

        override fun share() {
            val parts = state.parts
            runOnDefaultDispatcher {
                val text = Backup.write(parts)
                app.cacheDir.mkdirs()
                val file = File(app.cacheDir, fileName())
                file.writeText(text)
                onMainDispatcher {
                    if (!isAdded) return@onMainDispatcher
                    startActivity(
                        Intent.createChooser(
                            Intent(Intent.ACTION_SEND).setType("application/json")
                                .setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                .putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(app, BuildConfig.APPLICATION_ID + ".cache", file)),
                            getString(R.string.share),
                        ),
                    )
                }
            }
        }

        override fun pick() = startFilesForResult(pickFile, "*/*")

        override fun restorePart(part: BackupPart, on: Boolean) {
            val r = state.restore ?: return
            state = state.copy(restore = r.copy(chosen = if (on) r.chosen + part else r.chosen - part))
        }

        override fun restore() {
            val json = picked ?: return
            val chosen = state.restore?.chosen?.takeIf { it.isNotEmpty() } ?: return
            if (state.restoring) return
            state = state.copy(restoring = true)
            SagerNet.stopService()
            val context = requireContext().applicationContext
            runOnDefaultDispatcher {
                try {
                    Backup.restore(json, chosen)
                    triggerFullRestart(context)
                } catch (e: Exception) {
                    Logs.w(e)
                    onMainDispatcher {
                        state = state.copy(restoring = false)
                        if (isAdded) alert(e.readableMessage).show()
                    }
                }
            }
        }

        override fun cancelRestore() {
            picked = null
            state = state.copy(restore = null)
        }

        override fun reset() {
            MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.confirm)
                .setMessage(R.string.reset_settings_message)
                .setNegativeButton(R.string.no, null)
                .setPositiveButton(R.string.yes) { _, _ ->
                    // the dialog promises the nodes stay: their registrations and keys live in this store too
                    DataStore.replaceSettings()
                    triggerFullRestart(requireContext())
                }
                .show().danger()
        }
    }
}

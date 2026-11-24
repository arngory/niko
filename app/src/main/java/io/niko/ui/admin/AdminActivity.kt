package io.niko.ui.admin

import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.runtime.SideEffect
import androidx.lifecycle.ViewModelProvider
import com.google.android.material.snackbar.Snackbar
import io.niko.R
import io.niko.SagerNet
import io.niko.raycontrol.RayNodes
import io.niko.ui.ThemedActivity
import io.niko.ui.compose.NikoTheme

/**
 * The admin panel of a raycontrol node (P-Admin) on Compose, in place of RayAdminActivity: [EXTRA_NODE] the node's id,
 * for a device that is its admin. The work is [AdminModel]'s, which a rotation keeps; this shows its state and does
 * what needs an activity (a snackbar, the share sheet, the clipboard, no screenshots of an invite or a user's links).
 */
class AdminActivity : ThemedActivity(), AdminModel.Host {

    companion object {
        const val EXTRA_NODE = "io.niko.raycontrol.NODE"
        private const val SAVED = "admin"
    }

    private lateinit var model: AdminModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val node = intent.getStringExtra(EXTRA_NODE)?.let { RayNodes.get(it) }?.takeIf { it.isAdmin } ?: run {
            finish()
            return
        }
        model = ViewModelProvider(this)[AdminModel::class.java]
        model.host = this
        if (!model.started) model.start(node, savedInstanceState?.getBundle(SAVED))
        setContent {
            NikoTheme {
                val state = model.state.value
                // an invite registers a device, the links hold a user's keys: no screenshots and no recent-apps
                // thumbnail while one is shown
                SideEffect {
                    if (state.secret) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                    else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
                AdminScreen(state, model)
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::model.isInitialized) outState.putBundle(SAVED, model.save())
    }

    override fun onDestroy() {
        if (::model.isInitialized && model.host === this) model.host = null
        super.onDestroy()
    }

    override fun text(id: Int, args: Array<out Any>): String = getString(id, *args)

    override fun size(bytes: Long): String = Formatter.formatFileSize(this, bytes)

    override fun ttls(): List<String> = resources.getStringArray(R.array.raycontrol_admin_ttls).toList()

    override fun say(text: String) = Snackbar.make(findViewById(android.R.id.content), text, Snackbar.LENGTH_LONG).show()

    override fun share(text: String) = startActivity(
        Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), getString(R.string.share)),
    )

    override fun copy(text: String) {
        if (SagerNet.trySetPrimaryClip(text, sensitive = true)) say(getString(R.string.copy_success))
    }

    override fun close() = finish()
}

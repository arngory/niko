package io.niko.ui.tools

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.viewinterop.AndroidView
import androidx.fragment.app.Fragment
import io.niko.BuildConfig
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.ktx.snackbar
import io.niko.ui.MainActivity
import io.niko.ui.compose.NikoTheme
import io.niko.utils.WebViewUtil

/**
 * Панель sing-box on Compose, in place of WebviewFragment: the same WebView (the Clash API's panel, its secret in the
 * query for the local address) under the new bar; the address and the secret in its ⋮. Opened from the log, back to it.
 */
class PanelFragment : Fragment() {

    companion object {
        private const val EDITING = "editing"

        // the local Clash API requires a secret; yacd picks hostname/port/secret up from the query
        fun dashboardUrl(stored: String, secret: String): String {
            val uri = Uri.parse(stored)
            if (uri.host != "127.0.0.1" || uri.port != 9090 || uri.getQueryParameter("secret") != null) {
                return uri.toString()
            }
            return uri.buildUpon()
                .appendQueryParameter("hostname", "127.0.0.1")
                .appendQueryParameter("port", "9090")
                .appendQueryParameter("secret", secret)
                .build().toString()
        }
    }

    private var state by mutableStateOf(PanelState(DataStore.yacdURL))
    private var web: WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.getString(EDITING)?.let { state = state.copy(editing = it) }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        state.editing?.let { outState.putString(EDITING, it) }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        val view = WebView(inflater.context).apply {
            // surface-0 until the dashboard paints, not the white of a WebView in the dark theme
            setBackgroundColor(context.getColor(R.color.niko_surface_0))
            settings.domStorageEnabled = true
            settings.javaScriptEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    WebViewUtil.onReceivedError(view, request, error)
                }
            }
            loadUrl(dashboardUrl(DataStore.yacdURL, DataStore.clashApiSecret))
        }
        web = view
        return ComposeView(inflater.context).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                NikoTheme { PanelScreen(state, actions) { AndroidView({ view }, Modifier.fillMaxSize()) } }
            }
        }
    }

    override fun onDestroyView() {
        web?.apply {
            removeAllViews()
            destroy()
        }
        web = null
        super.onDestroyView()
    }

    /** Back: a sheet open closes, then the log the panel was opened from. */
    fun back() = when {
        state.editing != null -> state = state.copy(editing = null)
        state.menu -> state = state.copy(menu = false)
        else -> (requireActivity() as MainActivity).displayFragmentWithId(R.id.nav_logcat).let { }
    }

    private val actions = object : PanelActions {
        override fun back() = this@PanelFragment.back()

        override fun menu(open: Boolean) {
            state = state.copy(menu = open)
        }

        override fun edit(text: String?) {
            state = state.copy(editing = text, menu = false)
        }

        override fun save() {
            val url = state.editing?.trim()?.takeIf { it.isNotEmpty() } ?: return
            DataStore.yacdURL = url
            state = state.copy(url = url, editing = null)
            web?.loadUrl(dashboardUrl(url, DataStore.clashApiSecret))
        }

        override fun copySecret() {
            state = state.copy(menu = false)
            if (SagerNet.trySetPrimaryClip(DataStore.clashApiSecret)) snackbar(R.string.copy_success).show()
        }
    }
}

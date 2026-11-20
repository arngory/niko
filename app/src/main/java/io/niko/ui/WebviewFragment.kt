package io.niko.ui

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.webkit.*
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.niko.BuildConfig
import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.databinding.LayoutWebviewBinding
import io.niko.ktx.setField
import io.niko.ktx.snackbar
import io.niko.utils.WebViewUtil

// Fragment必须有一个无参public的构造函数，否则在数据恢复的时候，会报crash

class WebviewFragment : ToolbarFragment(R.layout.layout_webview), Toolbar.OnMenuItemClickListener {

    lateinit var mWebView: WebView

    @SuppressLint("SetJavaScriptEnabled")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // layout
        toolbar.setTitle(R.string.menu_dashboard)
        toolbar.inflateMenu(R.menu.yacd_menu)
        toolbar.setOnMenuItemClickListener(this)

        val binding = LayoutWebviewBinding.bind(view)

        // webview
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)
        mWebView = binding.webview
        // surface-0 until the dashboard paints, not the white of a WebView in the dark theme
        mWebView.setBackgroundColor(requireContext().getColor(R.color.niko_surface_0))
        mWebView.settings.domStorageEnabled = true
        mWebView.settings.javaScriptEnabled = true
        mWebView.webViewClient = object : WebViewClient() {
            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                WebViewUtil.onReceivedError(view, request, error)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }
        mWebView.loadUrl(dashboardUrl())
    }

    // the local Clash API requires a secret; yacd picks hostname/port/secret up from the query
    private fun dashboardUrl(): String {
        val uri = Uri.parse(DataStore.yacdURL)
        if (uri.host != "127.0.0.1" || uri.port != 9090 || uri.getQueryParameter("secret") != null) {
            return uri.toString()
        }
        return uri.buildUpon()
            .appendQueryParameter("hostname", "127.0.0.1")
            .appendQueryParameter("port", "9090")
            .appendQueryParameter("secret", DataStore.clashApiSecret)
            .build().toString()
    }

    override fun onDestroyView() {
        mWebView.removeAllViews()
        mWebView.destroy()
        super.onDestroyView()
    }

    @SuppressLint("CheckResult")
    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_set_url -> {
                val view = EditText(context).apply {
                    inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
                    setText(DataStore.yacdURL)
                }
                MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.set_panel_url)
                    .setField(view)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        DataStore.yacdURL = view.text.toString()
                        mWebView.loadUrl(dashboardUrl())
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .show()
            }
            R.id.action_copy_secret -> {
                if (SagerNet.trySetPrimaryClip(DataStore.clashApiSecret)) {
                    snackbar(R.string.copy_success).show()
                }
            }
            R.id.close -> {
                // leave the dashboard; the WebView is destroyed with the view
                (requireActivity() as MainActivity).displayFragmentWithId(R.id.nav_configuration)
            }
        }
        return true
    }
}

package io.niko.ui

import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.snackbar.Snackbar
import io.niko.R
import io.niko.utils.Theme

abstract class ThemedActivity : AppCompatActivity {
    constructor() : super()
    constructor(contentLayoutId: Int) : super(contentLayoutId)

    var themeResId = 0
    var uiMode = 0
    open val isDialog = false

    override fun onCreate(savedInstanceState: Bundle?) {
        if (!isDialog) {
            Theme.apply(this)
        } else {
            Theme.applyDialog(this)
        }
        Theme.applyNightTheme()

        super.onCreate(savedInstanceState)

        uiMode = resources.configuration.uiMode
        if (!isDialog) nikoSystemBars()

        if (Build.VERSION.SDK_INT >= 35) {
            ViewCompat.setOnApplyWindowInsetsListener(findViewById(android.R.id.content)) { _, insets ->
                val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
                findViewById<AppBarLayout>(R.id.appbar)?.apply {
                    updatePadding(top = top)
//                Logs.w("appbar $top")
                }
//            findViewById<NavigationView>(R.id.nav_view)?.apply {
//                updatePadding(top = top)
//            }
                insets
            }
        }
    }

    /**
     * The Niko theme's system bars (reskin step 1, D-MAIN): surface-0 as the app's own bars, with dark icons in the
     * light theme and light ones in the dark. The navigation bar takes dark icons from API 27 only: below it, it stays
     * dark (ink of the light theme). On API 35 the window is edge to edge and the colours are not applied; the icons
     * still are.
     */
    @Suppress("DEPRECATION")
    private fun nikoSystemBars() {
        if (themeResId != R.style.Theme_SagerNet_Niko) return
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val surface = ContextCompat.getColor(this, R.color.niko_surface_0)
        window.statusBarColor = surface
        window.navigationBarColor = if (Build.VERSION.SDK_INT >= 27 || night) surface
        else ContextCompat.getColor(this, R.color.niko_ink)
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = !night
            if (Build.VERSION.SDK_INT >= 27) isAppearanceLightNavigationBars = !night
        }
    }

    override fun setTheme(resId: Int) {
        super.setTheme(resId)

        themeResId = resId
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        if (newConfig.uiMode != uiMode) {
            uiMode = newConfig.uiMode
            ActivityCompat.recreate(this)
        }
    }

    fun snackbar(@StringRes resId: Int): Snackbar = snackbar("").setText(resId)
    fun snackbar(text: CharSequence): Snackbar = snackbarInternal(text).apply {
        view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text).apply {
            maxLines = 10
        }
    }

    internal open fun snackbarInternal(text: CharSequence): Snackbar = throw NotImplementedError()


    /**
     * Back asks [confirm] first while [hasUnsavedChanges]. A callback instead of onBackPressed(),
     * which is no longer called with predictive back (targetSdk 36).
     */
    protected fun confirmUnsavedChangesOnBack(hasUnsavedChanges: () -> Boolean, confirm: () -> Unit) {
        onBackPressedDispatcher.addCallback(this) {
            if (hasUnsavedChanges()) {
                confirm()
            } else {
                isEnabled = false
                onBackPressedDispatcher.onBackPressed()
                isEnabled = true
            }
        }
    }
}

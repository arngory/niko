package io.niko.utils

import android.content.Context
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import io.niko.R
import io.niko.database.DataStore
import io.niko.ktx.app

object Theme {

    // the app has one look, Niko's, in its light and dark forms (nightTheme); the 21 colour themes and Black of
    // NekoBox were removed in 1.62.0, and a choice of one kept from before is taken as Niko
    fun apply(context: Context) {
        context.setTheme(getTheme())
    }

    fun getTheme(): Int = R.style.Theme_SagerNet_Niko

    var currentNightMode = -1
    fun getNightMode(): Int {
        if (currentNightMode == -1) {
            currentNightMode = DataStore.nightTheme
        }
        return getNightMode(currentNightMode)
    }

    fun getNightMode(mode: Int): Int {
        return when (mode) {
            0 -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            1 -> AppCompatDelegate.MODE_NIGHT_YES
            2 -> AppCompatDelegate.MODE_NIGHT_NO
            else -> AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY
        }
    }

    fun usingNightMode(): Boolean {
        return when (DataStore.nightTheme) {
            1 -> true
            2 -> false
            // dark while the battery saver is on, as AppCompat's MODE_NIGHT_AUTO_BATTERY (getNightMode) shows it: the
            // application's configuration does not follow it (ThroneForAndroid 9c8fd9d)
            3 -> io.niko.SagerNet.power.isPowerSaveMode
            else -> (app.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
    }

    fun applyNightTheme() {
        AppCompatDelegate.setDefaultNightMode(getNightMode())
    }

}
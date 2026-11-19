/*******************************************************************************
 *                                                                             *
 *  Copyright (C) 2017 by Max Lv <max.c.lv@gmail.com>                          *
 *  Copyright (C) 2017 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
 *                                                                             *
 *  This program is free software: you can redistribute it and/or modify       *
 *  it under the terms of the GNU General Public License as published by       *
 *  the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                        *
 *                                                                             *
 *  This program is distributed in the hope that it will be useful,            *
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 *  GNU General Public License for more details.                               *
 *                                                                             *
 *  You should have received a copy of the GNU General Public License          *
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                             *
 *******************************************************************************/

package io.niko

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutManager
import android.os.Bundle
import androidx.core.content.getSystemService
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import io.niko.aidl.ISagerNetService
import io.niko.bg.BaseService
import io.niko.bg.SagerConnection
import io.niko.database.DataStore

@Suppress("DEPRECATION")
class QuickToggleShortcut : Activity(), SagerConnection.Callback {
    private val connection = SagerConnection(SagerConnection.CONNECTION_ID_SHORTCUT)
    private var profileId = -1L

    companion object {
        private const val LEGACY_ALIAS = "io.niko.QuickToggleShortcutLegacy"
        private const val EXTRA_TOKEN = "token"

        /** Intent for a pinned shortcut that toggles [profileId], or the selected profile for -1. */
        fun shortcutIntent(context: Context, profileId: Long = -1L): Intent {
            val intent = Intent(context, QuickToggleShortcut::class.java)
            intent.action = Intent.ACTION_MAIN
            if (profileId >= 0) intent.putExtra("profile", profileId)
            return intent
        }

        /** Pins a shortcut that toggles [profileId]; false when the launcher takes no pinned shortcuts. */
        fun pinProfileShortcut(context: Context, profileId: Long, label: String): Boolean =
            ShortcutManagerCompat.requestPinShortcut(
                context, ShortcutInfoCompat.Builder(context, "shortcut-profile-$profileId")
                    .setShortLabel(label)
                    .setLongLabel(label)
                    .setIcon(IconCompat.createWithResource(context, R.drawable.ic_qu_toggle_launcher))
                    .setIntent(shortcutIntent(context, profileId))
                    .build(), null
            )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (intent.action == Intent.ACTION_CREATE_SHORTCUT) {
            setResult(RESULT_OK, ShortcutManagerCompat.createShortcutResultIntent(this,
                ShortcutInfoCompat.Builder(this, "toggle")
                    .setIntent(shortcutIntent(this))
                    .setIcon(IconCompat.createWithResource(this,
                        R.drawable.ic_qu_toggle_launcher))
                    .setShortLabel(getString(R.string.quick_toggle))
                    .build()))
            finish()
        } else if (intent.component?.className == LEGACY_ALIAS &&
            intent.getStringExtra(EXTRA_TOKEN) != DataStore.shortcutToken
        ) {
            // any app can start the exported alias: a shortcut pinned before Android 8 carries the token
            finish()
        } else {
            profileId = intent.getLongExtra("profile", -1L)
            connection.connect(this, this)
            getSystemService<ShortcutManager>()!!.reportShortcutUsed(if (profileId >= 0) "shortcut-profile-$profileId" else "toggle")
        }
    }

    override fun onServiceConnected(service: ISagerNetService) {
        val state = BaseService.State.values()[service.state]
        when {
            state.canStop -> {
                if (profileId == DataStore.selectedProxy || profileId == -1L) {
                    SagerNet.stopService()
                } else {
                    DataStore.selectedProxy = profileId
                    SagerNet.reloadService()
                }
            }
            state == BaseService.State.Stopped -> {
                if (profileId >= 0L) DataStore.selectedProxy = profileId
                SagerNet.startService()
            }
        }
        finish()
    }

    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) {}

    override fun onDestroy() {
        connection.disconnect(this)
        super.onDestroy()
    }
}

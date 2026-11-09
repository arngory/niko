/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.niko.ui.profile

import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceFragmentCompat
import io.niko.Key
import io.niko.R
import io.niko.database.DataStore
import io.niko.database.preference.EditTextPreferenceModifiers
import io.niko.fmt.mieru.MieruBean
import io.niko.ktx.applyDefaultValues
import io.niko.ui.SimpleMenuPreference

class MieruSettingsActivity : ProfileSettingsActivity<MieruBean>() {

    override fun createEntity() = MieruBean().applyDefaultValues()

    override fun MieruBean.init() {
        DataStore.profileName = name
        DataStore.serverAddress = serverAddress
        DataStore.serverPort = serverPort
        DataStore.serverProtocol = protocol
        DataStore.serverUsername = username
        DataStore.serverPassword = password
        DataStore.serverMTU = mtu
        // a link's MULTIPLEXING_HIGH or HANDSHAKE_NO_WAIT as the menu's "high" or "no_wait"; none: mieru's default
        DataStore.profileCacheStore.putString(MULTIPLEXING, menuValue(multiplexing, "MULTIPLEXING_", R.array.mieru_multiplexing_value))
        DataStore.profileCacheStore.putString(HANDSHAKE, menuValue(handshakeMode, "HANDSHAKE_", R.array.mieru_handshake_value))
        DataStore.profileCacheStore.putString(TRAFFIC_PATTERN, trafficPattern)
    }

    private fun menuValue(kept: String?, prefix: String, values: Int): String =
        kept.orEmpty().trim().removePrefix(prefix).lowercase().takeIf { it in resources.getStringArray(values) } ?: DEFAULT

    override fun MieruBean.serialize() {
        name = DataStore.profileName
        serverAddress = DataStore.serverAddress
        serverPort = DataStore.serverPort
        protocol = DataStore.serverProtocol
        username = DataStore.serverUsername
        password = DataStore.serverPassword
        mtu = DataStore.serverMTU
        multiplexing = DataStore.profileCacheStore.getString(MULTIPLEXING, DEFAULT).orEmpty().takeIf { it != DEFAULT }.orEmpty()
        handshakeMode = DataStore.profileCacheStore.getString(HANDSHAKE, DEFAULT).orEmpty().takeIf { it != DEFAULT }.orEmpty()
        trafficPattern = DataStore.profileCacheStore.getString(TRAFFIC_PATTERN, "").orEmpty().trim()
    }

    companion object {
        private const val MULTIPLEXING = "mieruMultiplexing"
        private const val HANDSHAKE = "mieruHandshake"
        private const val TRAFFIC_PATTERN = "mieruTrafficPattern"
        private const val DEFAULT = "default"
    }

    override fun PreferenceFragmentCompat.createPreferences(
        savedInstanceState: Bundle?,
        rootKey: String?,
    ) {
        addPreferencesFromResource(R.xml.mieru_preferences)
        findPreference<EditTextPreference>(Key.SERVER_PORT)!!.apply {
            setOnBindEditTextListener(EditTextPreferenceModifiers.Port)
        }
        findPreference<EditTextPreference>(Key.SERVER_PASSWORD)!!.apply {
            summaryProvider = PasswordSummaryProvider
        }
        val protocol = findPreference<SimpleMenuPreference>(Key.SERVER_PROTOCOL)!!
        val mtu = findPreference<EditTextPreference>(Key.SERVER_MTU)!!
        mtu.isVisible = protocol.value.equals("UDP")
        protocol.setOnPreferenceChangeListener { _, newValue ->
            mtu.isVisible = newValue.equals("UDP")
            true
        }
    }

}
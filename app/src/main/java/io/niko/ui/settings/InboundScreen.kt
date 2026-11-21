package io.niko.ui.settings

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.niko.Key
import io.niko.R
import io.niko.ui.home.ListGroup
import io.niko.ui.home.Section

/**
 * Настройки › Вход (P-SetInbound, D-SET-27) made by hand: variant А of docs/ui/platforms.md, the same screen as
 * ListScreen draws from shared/screens/set_inbound.json (docs/research/list-screens-probe.md).
 */
@Composable
fun InboundScreen(values: SettingValues, onBack: () -> Unit, onNewPassword: () -> Unit) =
    ListPage(stringResource(R.string.inbound_title), stringResource(R.string.screen_back), onBack) {
        @Composable
        fun field(key: String, label: Int, input: FieldInput, modifier: Modifier = Modifier, button: FieldButton? = null) =
            Field(stringResource(label), values.text(key), { values.setText(key, it) }, modifier, input, mono = true, button = button)

        @Composable
        fun switch(key: String, title: Int, divider: Boolean, enabled: Boolean = true) =
            SwitchRow(stringResource(title), values.flag(key), { values.setFlag(key, it) }, enabled, divider)

        FieldsRow {
            field(Key.MIXED_USERNAME, R.string.inbound_user, FieldInput.TEXT, Modifier.weight(1f))
            field(Key.MIXED_PORT, R.string.inbound_port, FieldInput.PORT, Modifier.width(NarrowField))
        }
        field(
            Key.MIXED_PASSWORD, R.string.inbound_password, FieldInput.SECRET,
            button = FieldButton(R.drawable.ic_l_rotate_cw, stringResource(R.string.inbound_new_password), onNewPassword),
        )
        ListGroup {
            switch(Key.ALLOW_ACCESS, R.string.inbound_lan, divider = false)
            switch(Key.LOCAL_PROXY_NO_AUTH_IN_VPN, R.string.inbound_no_auth_in_vpn, divider = true)
            switch(Key.APPEND_HTTP_PROXY, R.string.inbound_http_in_vpn, divider = true, enabled = values.flag(Key.LOCAL_PROXY_NO_AUTH_IN_VPN))
        }
        Section(stringResource(R.string.inbound_network)) {
            switch(Key.METERED_NETWORK, R.string.inbound_metered, divider = false)
            switch(Key.ACQUIRE_WAKE_LOCK, R.string.inbound_wake_lock, divider = true)
        }
    }

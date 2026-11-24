package io.niko.ui

import io.niko.R
import io.niko.SagerNet
import io.niko.database.DataStore
import io.niko.database.ProfileManager
import io.niko.ktx.runOnDefaultDispatcher

/**
 * «Сменить» of the service's notification (bg/ServiceNotification): the picker (P-Pick) over whatever is on the
 * screen, opened on the profile in use; the one tapped is the one to use, the service reloaded with it.
 */
class SwitchActivity : ProfileSelectActivity() {

    override fun current(): Long? = DataStore.selectedProxy.takeIf { it > 0 }

    override fun purpose(): String? = getString(R.string.action_switch)

    override fun picked(id: Long) {
        val old = DataStore.selectedProxy
        DataStore.selectedProxy = id
        runOnDefaultDispatcher {
            ProfileManager.postUpdate(old, true)
            ProfileManager.postUpdate(id, true)
        }
        SagerNet.reloadService()
        finish()
    }
}

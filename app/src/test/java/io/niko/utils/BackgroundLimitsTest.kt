package io.niko.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackgroundLimitsTest {

    // the vendor's autostart screen by Build.MANUFACTURER, whatever its case; none for a phone without one
    @Test
    fun vendorScreens() {
        assertEquals(
            "com.miui.securitycenter/com.miui.permcenter.autostart.AutoStartManagementActivity",
            BackgroundLimits.vendorScreen("Xiaomi"),
        )
        assertEquals("com.huawei.systemmanager/.startupmgr.ui.StartupNormalAppListActivity", BackgroundLimits.vendorScreen(" HUAWEI "))
        assertNull(BackgroundLimits.vendorScreen("Google"))
        assertNull(BackgroundLimits.vendorScreen(""))
    }
}

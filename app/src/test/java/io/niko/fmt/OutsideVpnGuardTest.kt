package io.niko.fmt

import org.junit.Assert.assertEquals
import org.junit.Test

class OutsideVpnGuardTest {

    // an app left out of the per-app VPN, bound to tun0: refused on the tunnel's inbound by the name libcore gives a
    // connection with no owner, a name no package can have
    @Test
    fun refusesTheOwnerlessOnTheTunnel() {
        val rule = outsideVpnGuard()
        assertEquals(listOf("tun-in"), rule.inbound)
        assertEquals(listOf(OUTSIDE_VPN_PACKAGE), rule.package_name)
        assertEquals("reject", rule.action)
        assertEquals("niko:outside-vpn", OUTSIDE_VPN_PACKAGE)
    }
}

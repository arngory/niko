package io.niko.fmt

import org.junit.Assert.assertEquals
import org.junit.Test

class OutsideVpnGuardTest {

    // an app left out of the per-app VPN, bound to tun0: refused on the tunnel's inbound by the name libcore gives a
    // connection with no owner, a name no package can have; on TCP alone, where no owner cannot be a lookup that
    // missed a socket already closed
    @Test
    fun refusesTheOwnerlessOnTheTunnel() {
        val rule = outsideVpnGuard()
        assertEquals(listOf("tun-in"), rule.inbound)
        assertEquals(listOf("tcp"), rule.network)
        assertEquals(listOf(OUTSIDE_VPN_PACKAGE), rule.package_name)
        assertEquals("reject", rule.action)
        assertEquals("niko:outside-vpn", OUTSIDE_VPN_PACKAGE)
    }
}

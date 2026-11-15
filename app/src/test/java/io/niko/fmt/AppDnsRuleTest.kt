package io.niko.fmt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppDnsRuleTest {

    // "app X → direct" sends X's names to the direct DNS too; "app X + 10.0.0.0/8 → direct" or "app X + port 443 →
    // block" must not: they match some of X's connections, and a DNS rule by the app alone would take all its names
    @Test
    fun onlyTheAppAloneTakesItsDns() {
        assertTrue(dnsFollowsAppRule("", "", "", "", "", ""))
        assertFalse(dnsFollowsAppRule("10.0.0.0/8", "", "", "", "", ""))
        assertFalse(dnsFollowsAppRule("", "443", "", "", "", ""))
        assertFalse(dnsFollowsAppRule("", "", "5000:6000", "", "", ""))
        assertFalse(dnsFollowsAppRule("", "", "", "udp", "", ""))
        assertFalse(dnsFollowsAppRule("", "", "", "", "192.168.1.0/24", ""))
        assertFalse(dnsFollowsAppRule("", "", "", "", "", "quic"))
        assertTrue(dnsFollowsAppRule(" ", "\n", "", "", "", ""))
    }
}

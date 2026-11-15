package io.niko.raycontrol

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RayWatchdogDohTest {

    // libcore's DohCheck: an HTTP status the node's DoH refused with, 0 an answer, -1 no word (a timeout, a site
    // that keeps silent), -2 no DoH server in the core. Only a 4xx is the node's word about the token (raycontrol
    // v0.27.0: 404 at once for a token it no longer takes, 403 or 404 by the mode of its site later on)
    @Test
    fun onlyA4xxIsTheNodesWordAboutTheToken() {
        assertTrue(RayWatchdog.dohRefused(404))
        assertTrue(RayWatchdog.dohRefused(403))
        assertTrue(RayWatchdog.dohRefused(429))
        for (code in listOf(0, -1, -2, 200, 399, 500, 502, 503)) assertFalse("$code", RayWatchdog.dohRefused(code))
    }
}

package io.niko.ui

import io.niko.R
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteTestTest {

    @Test
    fun targets() {
        assertEquals("example.com" to 443, parseRouteTestTarget(" example.com "))
        assertEquals("example.com" to 80, parseRouteTestTarget("example.com:80"))
        assertEquals("example.com" to 443, parseRouteTestTarget("https://example.com/path"))
        assertEquals("192.0.2.1" to 53, parseRouteTestTarget("192.0.2.1:53"))
        assertEquals("2001:db8::1" to 443, parseRouteTestTarget("[2001:db8::1]:443"))
        assertEquals("2001:db8::1" to 443, parseRouteTestTarget("2001:db8::1"))
        assertEquals("2001:db8::1" to 443, parseRouteTestTarget("[2001:db8::1]"))
        for (bad in listOf("", "  ", "example.com:x", "example.com:0", "example.com:70000", "[2001:db8::1", "a b:80")) {
            assertNull(bad, parseRouteTestTarget(bad))
        }
    }

    @Test
    fun apps() {
        assertEquals("org.telegram.messenger", packageOfChoice("Telegram (org.telegram.messenger)"))
        assertEquals("org.example.app", packageOfChoice(" org.example.app "))
        assertNull(packageOfChoice(""))
        assertNull(packageOfChoice("Telegram"))
    }

    @Test
    fun answers() {
        val names = mapOf(
            R.string.route_test_out_proxy to "the proxy", R.string.route_test_out_direct to "direct",
            R.string.route_test_out_block to "blocked", R.string.route_test_no_rule to "No rule",
            R.string.route_test_steps to "On the way:", R.string.route_test_failed to "No connection",
            R.string.route_test_dns to "DNS", R.string.route_test_seen to "Seen",
        )
        val string = { id: Int -> names[id] ?: "?" }
        val decided = describeRouteTest(
            JSONObject("""{"index":2,"rule":"domain_suffix=example.ru","action":"route(bypass)","outbound":"bypass",
                "steps":[{"index":0,"rule":"","action":"sniff","note":"sniff: taken as done"},
                         {"index":2,"rule":"domain_suffix=example.ru","action":"route(bypass)"}]}"""),
            string,
        )
        assertEquals("#2 domain_suffix=example.ru\n→ direct\n\nOn the way:\n#0 sniff (sniff: taken as done)", decided)
        val final = describeRouteTest(JSONObject("""{"index":-1,"action":"final","outbound":"proxy","steps":[]}"""), string)
        assertEquals("No rule → the proxy", final)
        val rejected = describeRouteTest(
            JSONObject("""{"index":5,"rule":"ip_cidr=192.0.2.0/24","action":"reject","steps":[
                {"index":4,"action":"resolve","resolved":["192.0.2.7"]},{"index":5,"action":"reject"}]}"""),
            string,
        )
        assertTrue(rejected, rejected.startsWith("#5 ip_cidr=192.0.2.0/24\n→ reject") && rejected.endsWith("#4 resolve: 192.0.2.7"))
        // through the VPN: the app's own query, a FakeDNS address, what was taken on trust
        val fake = describeRouteTest(
            JSONObject("""{"index":-1,"action":"final","outbound":"proxy","destination":"www.fake.example:443",
                "fake_ip":true,"resolved":["198.18.0.3"],"notes":["a note"],"steps":[]}"""),
            string,
        )
        assertEquals("No rule → the proxy\n\nDNS: 198.18.0.3\nSeen: www.fake.example:443 (FakeDNS)\n\na note", fake)
        // no connection: the app's query refused, or a resolve action's lookup failed
        val refused = describeRouteTest(
            JSONObject("""{"index":-1,"action":"dns","error":"lookup rejected","destination":"fail.example","steps":[]}"""),
            string,
        )
        assertEquals("No connection: lookup rejected", refused)
        val unresolved = describeRouteTest(
            JSONObject("""{"index":6,"rule":"domain=fail.example","action":"resolve","error":"rejected",
                "destination":"fail.example:443","steps":[{"index":6,"action":"resolve"}]}"""),
            string,
        )
        assertEquals("#6 domain=fail.example\n→ No connection: rejected", unresolved)
    }
}

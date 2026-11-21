package io.niko.group

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionHeadersTest {

    // the spare as the main address names it now: dropped when it names none; an answer of the spare keeps it
    @Test
    fun spareFollowsTheMainAddress() {
        assertEquals("https://b.example/s", spareAfterAnswer("https://a.example/s", "https://b.example/s", false))
        assertEquals("", spareAfterAnswer("https://a.example/s", null, false))
        assertEquals("https://a.example/s", spareAfterAnswer("https://a.example/s", null, true))
        assertEquals("", spareAfterAnswer(null, null, true))
    }

    @Test
    fun textHeaders() {
        assertEquals("My VPN", decodeSubscriptionHeader(" My VPN "))
        // "Мой VPN" in base64
        assertEquals("Мой VPN", decodeSubscriptionHeader("base64:0JzQvtC5IFZQTg=="))
        assertEquals("", decodeSubscriptionHeader(null))
        assertEquals("", decodeSubscriptionHeader("base64:@@@"))
    }

    @Test
    fun updateInterval() {
        assertEquals(12 * 60, subscriptionUpdateIntervalMinutes("12"))
        assertNull(subscriptionUpdateIntervalMinutes("0"))
        assertNull(subscriptionUpdateIntervalMinutes("soon"))
        assertNull(subscriptionUpdateIntervalMinutes(null))
    }

    @Test
    fun userinfo() {
        val usage = parseSubscriptionUserinfo("upload=100; download=900; total=10000; expire=2000000000")!!
        assertEquals(1000L, usage.used)
        assertEquals(9000L, usage.remaining)
        assertEquals(2000000000L, usage.expire)
        assertFalse(usage.runningOut(nowSeconds = 1000000000))
        assertTrue(usage.runningOut(nowSeconds = 2000000000 - 3600))

        val almostUsed = parseSubscriptionUserinfo("upload=0;download=9600;total=10000")!!
        assertTrue(almostUsed.runningOut(nowSeconds = 0))
        // "reupload" must not be read as upload
        assertEquals(5L, parseSubscriptionUserinfo("reupload=7; download=5")!!.used)
        assertNull(parseSubscriptionUserinfo("upload=0; download=0; total=0; expire=0"))
        assertNull(parseSubscriptionUserinfo(""))
    }

    @Test
    fun deviceIdPerProvider() {
        val a = subscriptionDeviceId("secret", "https://a.example/sub/1")
        assertEquals(32, a.length)
        assertEquals(a, subscriptionDeviceId("secret", "https://A.example/sub/other"))
        assertTrue(a != subscriptionDeviceId("secret", "https://b.example/sub/1"))
        assertTrue(a != subscriptionDeviceId("other", "https://a.example/sub/1"))
    }

    // Remnawave's refusals: from the headers of an answer, or from the error of a refused one (libcore's brackets)
    @Test
    fun hwidRefusals() {
        fun headers(vararg pairs: Pair<String, String>): (String) -> String? = { name -> pairs.toMap()[name] }
        assertEquals(HwidRefusal.LIMIT_REACHED, hwidRefusal(headers("x-hwid-max-devices-reached" to " TRUE ")))
        assertEquals(HwidRefusal.LIMIT_REACHED, hwidRefusal(headers("x-hwid-limit" to "true")))
        assertEquals(HwidRefusal.ID_REQUIRED, hwidRefusal(headers("x-hwid-not-supported" to "true", "x-hwid-active" to "true")))
        assertNull(hwidRefusal(headers("x-hwid-active" to "true", "x-hwid-limit" to "false")))
        assertEquals(HwidRefusal.ID_REQUIRED, hwidRefusal("HTTP 404 Not Found [x-hwid-not-supported]: "))
        assertEquals(HwidRefusal.LIMIT_REACHED, hwidRefusal("HTTP 404 Not Found [x-hwid-max-devices-reached] [x-hwid-limit]: x"))
        assertNull(hwidRefusal("HTTP 404 Not Found: x-hwid-not-supported in the body"))
        assertNull(hwidRefusal(null as String?))
    }

    // the provider's new address and its spare: headers first, then the body's "#name: value" lines
    @Test
    fun subscriptionMoves() {
        val current = "https://panel.example/sub/abc?x=1"
        fun none(@Suppress("UNUSED_PARAMETER") name: String): String? = null
        val byHeader = subscriptionMove(current, { if (it == "new-url") " https://new.example/s/abc " else null }, "")
        assertEquals("https://new.example/s/abc", byHeader.to)
        assertNull(byHeader.spare)
        // new-domain keeps the rest of the address; new-url wins over it
        assertEquals("https://mirror.example:8443/sub/abc?x=1",
            subscriptionMove("https://panel.example:8443/sub/abc?x=1", { if (it == "new-domain") "Mirror.Example." else null }, "").to)
        assertEquals("https://new.example/s",
            subscriptionMove(current, { mapOf("new-url" to "https://new.example/s", "new-domain" to "other.example")[it] }, "").to)
        // body lines, and the same lines in a base64 body
        val body = "#new-url: https://body.example/s\n#fallback-url: https://spare.example/s\nvless://x@1.2.3.4:443#n\n"
        val fromBody = subscriptionMove(current, ::none, body)
        assertEquals("https://body.example/s", fromBody.to)
        assertEquals("https://spare.example/s", fromBody.spare)
        val encoded = java.util.Base64.getEncoder().encodeToString(body.toByteArray())
        assertEquals("https://spare.example/s", subscriptionMove(current, ::none, encoded).spare)
        // the header wins over the body
        assertEquals("https://h.example/s", subscriptionMove(current, { if (it == "new-url") "https://h.example/s" else null }, body).to)
        // the same address is no move
        assertNull(subscriptionMove(current, { if (it == "new-url") current else null }, "").to)
    }

    @Test
    fun subscriptionAddresses() {
        val https = "https://panel.example/s"
        // no step down from https to plain http; http to https, and http to http, are fine
        assertNull(subscriptionAddress(https, "http://new.example/s"))
        assertEquals("https://new.example/s", subscriptionAddress("http://panel.example/s", "https://new.example/s"))
        assertEquals("http://new.example/s", subscriptionAddress("http://panel.example/s", "http://new.example/s"))
        // no other schemes, no user info, no relative or host-less addresses, nothing too long
        assertNull(subscriptionAddress(https, "content://x/y"))
        assertNull(subscriptionAddress(https, "javascript:alert(1)"))
        assertNull(subscriptionAddress(https, "https://user:pass@new.example/s"))
        assertNull(subscriptionAddress(https, "/sub/abc"))
        assertNull(subscriptionAddress(https, "https:///s"))
        assertNull(subscriptionAddress(https, "https://new.example/" + "a".repeat(2100)))
        assertNull(subscriptionAddress(https, "not a url"))
        // a domain is a domain: no path, port, scheme or spaces in it
        assertNull(withDomain(https, "new.example/evil"))
        assertNull(withDomain(https, "new.example:80"))
        assertNull(withDomain(https, "https://new.example"))
        assertNull(withDomain(https, "localhost"))
        assertNull(withDomain(https, "a b.example"))
        assertEquals("https://xn--80ak6aa92e.example/s", withDomain(https, "xn--80ak6aa92e.example"))
    }
}

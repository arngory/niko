package io.niko.ui.route

import io.niko.database.RuleEntity
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteStateTest {

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
        val installed = listOf("Telegram (org.telegram.messenger)", "Сбербанк Онлайн (ru.sberbankmobile)", "СберМаркет (ru.instamart)")
        assertEquals(installed.drop(1), appSuggestions(installed, "сбер"))
        assertEquals(listOf(installed[0]), appSuggestions(installed, "telegram"))
        // nothing typed, or one already picked: no list
        assertTrue(appSuggestions(installed, " ").isEmpty())
        assertTrue(appSuggestions(installed, installed[1]).isEmpty())
    }

    @Test
    fun query() {
        val form = CheckForm("example.com", udp = true, resolve = false)
        val plain = checkQuery("example.com", 443, form, null, null)
        assertEquals("udp", plain.getString("network"))
        assertEquals("example.com", plain.getString("host"))
        assertEquals(443, plain.getInt("port"))
        assertFalse(plain.getBoolean("resolve"))
        assertFalse(plain.has("packages"))
        val app = checkQuery("example.com", 443, CheckForm(), "org.example.app", 10123)
        assertEquals("tcp", app.getString("network"))
        assertTrue(app.getBoolean("resolve"))
        assertEquals("org.example.app", app.getJSONArray("packages").getString(0))
        assertEquals(10123, app.getInt("uid"))
        // an app that is not installed: by its package only
        assertFalse(checkQuery("example.com", 443, CheckForm(), "org.gone", null).has("uid"))
    }

    @Test
    fun answers() {
        val decided = CheckResult.of(
            JSONObject("""{"index":2,"rule":"domain_suffix=example.ru","action":"route(bypass)","outbound":"bypass",
                "steps":[{"index":0,"rule":"","action":"sniff","note":"sniff: taken as done"},
                         {"index":2,"rule":"domain_suffix=example.ru","action":"route(bypass)"}]}"""),
        )
        assertEquals(2, decided.index)
        assertEquals("domain_suffix=example.ru", decided.rule)
        assertEquals("bypass", decided.outbound)
        // the deciding rule is not a step on the way
        assertEquals(1, decided.steps.size)
        assertEquals(0, decided.steps[0].index)
        assertEquals("sniff", decided.steps[0].action)
        assertEquals("sniff: taken as done", decided.steps[0].note)

        val final = CheckResult.of(JSONObject("""{"index":-1,"action":"final","outbound":"proxy","steps":[]}"""))
        assertEquals(-1, final.index)
        assertTrue(final.steps.isEmpty())

        val rejected = CheckResult.of(
            JSONObject("""{"index":5,"rule":"ip_cidr=192.0.2.0/24","action":"reject","steps":[
                {"index":4,"action":"resolve","resolved":["192.0.2.7"]},{"index":5,"action":"reject"}]}"""),
        )
        assertEquals("", rejected.outbound)
        assertEquals(listOf("192.0.2.7"), rejected.steps.single().resolved)

        // through the VPN: the app's own query, a FakeDNS address, what was taken on trust
        val fake = CheckResult.of(
            JSONObject("""{"index":-1,"action":"final","outbound":"proxy","destination":"www.fake.example:443",
                "fake_ip":true,"resolved":["198.18.0.3"],"notes":["dns: not asked"],"steps":[]}"""),
        )
        assertTrue(fake.fakeIp)
        assertEquals("www.fake.example:443", fake.destination)
        assertEquals(listOf("198.18.0.3"), fake.resolved)
        assertEquals(listOf("dns: not asked"), fake.notes)

        val failed = CheckResult.of(JSONObject("""{"index":-1,"action":"","error":"dial: no route","steps":[]}"""))
        assertEquals("dial: no route", failed.error)
    }

    @Test
    fun conditions() {
        assertEquals(emptyList<String>(), ruleConditions(RuleEntity(packages = setOf("org.example.app"))))
        assertEquals(
            listOf("geosite-ru-blocked", "geoip-ru-blocked"),
            ruleConditions(
                RuleEntity(
                    domains = "srs:https://raw.githubusercontent.com/x/release/sing-box/rule-set-geosite/geosite-ru-blocked.srs",
                    ip = "srs:https://raw.githubusercontent.com/x/release/sing-box/rule-set-geoip/geoip-ru-blocked.srs",
                ),
            ),
        )
        assertEquals(
            listOf("JSON", "geosite:ru +2", "src ip: 10.0.0.0/8", "src port: 5000", "dst port: 443", "network: udp", "protocol: quic"),
            ruleConditions(
                RuleEntity(
                    config = "{}", domains = "geosite:ru\ndomain:su\ndomain:xn--p1ai", source = "10.0.0.0/8", sourcePort = "5000",
                    port = "443", network = "udp", protocol = "quic",
                ),
            ),
        )
        assertNull(shortList(" \n "))
        assertEquals("a.example +1", shortList("a.example, b.example"))
    }

    @Test
    fun rows() {
        val row = RuleRow.of(RuleEntity(id = 7, outbound = 12, packages = setOf("a.b", "c.d"), enabled = true), "Helsinki")
        assertEquals("Rule 7", row.name)
        assertEquals(2, row.apps)
        assertEquals(RuleOut.PROFILE, row.out)
        assertEquals("Helsinki", row.profile)
        assertEquals(RuleOut.PROXY, ruleOut(0))
        assertEquals(RuleOut.DIRECT, ruleOut(-1))
        assertEquals(RuleOut.BLOCK, ruleOut(-2))
    }

    private fun rules() = mutableListOf(
        RuleEntity(id = 1, userOrder = 1), RuleEntity(id = 2, userOrder = 2), RuleEntity(id = 3, userOrder = 5), RuleEntity(id = 4, userOrder = 6),
    )

    @Test
    fun order() {
        // a step down, as a drag makes it: the two swap their orders
        val list = rules()
        val changed = moveRule(list, 1, 2)
        assertEquals(listOf(1L, 3L, 2L, 4L), list.map { it.id })
        assertEquals(listOf(1L, 2L, 5L, 6L), list.map { it.userOrder })
        assertEquals(setOf(2L, 3L), changed.map { it.id }.toSet())
        // up, several at once: the orders stay where they were, the rules move between them
        val up = rules()
        moveRule(up, 3, 0)
        assertEquals(listOf(4L, 1L, 2L, 3L), up.map { it.id })
        assertEquals(listOf(1L, 2L, 5L, 6L), up.map { it.userOrder })
        assertTrue(moveRule(up, 2, 2).isEmpty())
        assertTrue(moveRule(up, 0, 9).isEmpty())
    }

    @Test
    fun saving() {
        val read = RuleForm.of(RuleEntity(id = 3, name = "Банки", outbound = -1, packages = setOf("ru.sberbankmobile")))
        // an existing rule saved as it was read: nothing to write, no «set some rules»
        assertEquals(RuleSave.UNCHANGED, ruleSave(read, read, existing = true))
        assertEquals(RuleSave.WRITE, ruleSave(read.copy(name = "Банк"), read, existing = true))
        val blank = RuleForm.of(RuleEntity())
        assertEquals(RuleSave.EMPTY, ruleSave(blank, blank, existing = false))
        assertEquals(RuleSave.WRITE, ruleSave(blank.copy(domains = "example.com"), blank, existing = false))
    }

    @Test
    fun form() {
        val rule = RuleEntity(id = 9, userOrder = 4, enabled = true, name = "n", domains = "d", outbound = 12, dpiBypass = true)
        val form = RuleForm.of(rule).copy(packages = listOf("a.b", " ", "c.d"), outbound = -1)
        form.applyTo(rule)
        assertEquals(setOf("a.b", "c.d"), rule.packages)
        assertEquals(-1L, rule.outbound)
        // the form does not touch the rule's id, order or switch
        assertEquals(9L, rule.id)
        assertEquals(4L, rule.userOrder)
        assertTrue(rule.enabled)
        assertEquals(listOf("a.b", "c.d"), RuleForm.packagesOf("a.b\n\nc.d\n"))
    }
}

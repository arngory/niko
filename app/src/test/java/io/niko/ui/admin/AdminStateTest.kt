package io.niko.ui.admin

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminStateTest {

    // a string as its id and arguments, so that a test sees which one was taken and with what
    private val t = AdminTexts { id, args -> "#$id" + args.joinToString(",", "(", ")") }
    private fun s(id: Int, vararg args: Any) = t(id, *args)
    private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject
    private val date = { seconds: Long -> "d$seconds" }
    private val size = { bytes: Long -> "${bytes}B" }

    @Test
    fun pagesOpenOverOneAnother() {
        val start = AdminState("node")
        assertTrue(start.page is AdminPage.Status)
        // the first page stays under any other
        assertEquals(start, start.pop())
        val users = start.copy(error = "no").push(AdminPage.Users())
        assertTrue(users.page is AdminPage.Users)
        assertNull(users.error)
        val user = users.push(AdminPage.User("alice"))
        assertEquals(AdminPage.User("alice", devices = emptyList()), user.top(AdminPage.User("alice", devices = emptyList())).page)
        assertEquals(3, user.pages.size)
        assertTrue(user.pop().page is AdminPage.Users)
    }

    @Test
    fun secretPages() {
        val state = AdminState("node")
        assertFalse(state.secret)
        assertFalse(state.push(AdminPage.Invite("alice")).secret)
        assertTrue(state.push(AdminPage.Invite("alice", result = InviteResult("RCINV2-FAKE", "told"))).secret)
        assertFalse(state.push(AdminPage.Detail(DetailKind.TEXT, "Ports", "TCP 443")).secret)
        assertTrue(state.push(AdminPage.Detail(DetailKind.TEXT, "Links", "shown", share = "fake://link")).secret)
    }

    @Test
    fun statusDrafts() {
        val page = AdminPage.Status(StatusData("alice/phone · admin", "node.example", "v0.35.1", "id", name = "home", note = "pay by the 1st"))
        assertEquals("home", page.nameShown)
        assertFalse(page.nameChanged)
        assertFalse(page.noteChanged)
        // typed back to what the node has: nothing to send
        assertFalse(page.copy(name = "home").nameChanged)
        val typed = page.copy(name = "", note = "new")
        assertEquals("", typed.nameShown)
        assertTrue(typed.nameChanged)
        assertEquals("new", typed.noteShown)
        assertTrue(typed.noteChanged)
    }

    @Test
    fun notesAndNames() {
        assertTrue(hasName(json("""{"name": ""}""")))
        assertTrue(hasName(json("""{"version": "v0.15.0"}""")))
        assertFalse(hasName(json("""{"version": "v0.14.0"}""")))
        assertTrue(hasNote(json("""{"version": "v0.21.0"}""")))
        assertFalse(hasNote(json("""{"version": "v0.20.3"}""")))
        assertEquals(s(R.string.raycontrol_admin_node_note_none), noteShown("", t))
        assertEquals("\u2068a b\u2069", noteShown("a\nb", t))
        // cleaned whole, not cut: the length is what the node would count
        val (note, length) = noteTyped("x".repeat(250) + "\n\u200b")
        assertEquals(250, length)
        assertEquals("x".repeat(250), note)
    }

    @Test
    fun typedLists() {
        assertEquals(listOf("203.0.113.0/24", "2001:db8::/32", "198.51.100.7/32"), cidrs("203.0.113.0/24, 2001:db8::/32\n\n198.51.100.7/32 "))
        assertEquals(emptyList<String>(), cidrs(" \n"))
        assertEquals("v0.30.1", updateVersion(" 0.30.1 "))
        assertEquals("v1.2.3", updateVersion("v1.2.3"))
        assertNull(updateVersion("latest"))
        assertNull(updateVersion("v1.2"))
    }

    @Test
    fun updates() {
        val update = json(
            """{"installed": "v0.30.0", "latest": "v0.30.1", "remote": "on", "auto": "patch", "window": "03:00-05:00",
                "timezone": "UTC", "held": ["v0.29.9"], "status": {"state": "failed", "release": "v0.30.1", "error": "no disk"}}""",
        )
        assertEquals("v0.30.1", newerRelease(update))
        assertFalse(nodeUpdating(update))
        assertEquals(
            listOf(
                s(R.string.raycontrol_admin_update_available, "v0.30.0", "v0.30.1"),
                s(R.string.raycontrol_admin_update_remote_on),
                s(R.string.raycontrol_admin_update_auto, "03:00-05:00", "UTC"),
                s(R.string.raycontrol_admin_update_failed, "v0.30.1") + ": no disk",
                s(R.string.raycontrol_admin_update_held, "v0.29.9"),
            ).joinToString("\n"),
            updateSummary(update, t, date),
        )
        val current = json("""{"installed": "v0.30.1", "latest": "v0.30.1", "remote": "off"}""")
        assertNull(newerRelease(current))
        assertEquals(
            s(R.string.raycontrol_admin_update_current, "v0.30.1") + "\n" + s(R.string.raycontrol_admin_update_remote_off),
            updateSummary(current, t, date),
        )
        assertTrue(nodeUpdating(json("""{"status": {"state": "running"}}""")))
        assertNull(updateState(json("{}"), t, date))
        assertEquals(s(R.string.raycontrol_admin_update_done, "a", "b", "d7"), updateState(json("""{"state": "done", "from": "a", "to": "b", "endedAt": "7"}"""), t, date))
        // an error only where the update did not go; a state this build does not know as it is
        assertEquals(s(R.string.raycontrol_admin_update_running, "a", "b"), updateState(json("""{"state": "running", "from": "a", "target": "b", "error": "x"}"""), t, date))
        assertEquals("paused", updateState(json("""{"state": "paused"}"""), t, date))
        assertNull(relaysText(update, t, 0) { "" })
    }

    @Test
    fun releasesAndSummaries() {
        assertEquals(s(R.string.raycontrol_admin_mirror_off), mirrorSummary(json("{}"), t, date, size))
        assertEquals(
            listOf(
                s(R.string.raycontrol_admin_mirror_on, "1.56.0 (10B)"),
                s(R.string.raycontrol_admin_mirror_checked, "d5"),
                s(R.string.raycontrol_admin_mirror_error, "timeout"),
            ).joinToString("\n"),
            mirrorSummary(json("""{"enabled": true, "releases": [{"tag": "1.56.0", "bytes": "10"}], "checked": "5", "error": "timeout"}"""), t, date, size),
        )
        assertEquals(s(R.string.raycontrol_admin_summary_off), summarySummary(json("{}"), t))
        val summary = json("""{"enabled": true, "networks": [{"asn": "64500", "name": "Example", "country": "RU", "reports": "3"}], "otherReports": "2"}""")
        assertEquals(s(R.string.raycontrol_admin_summary_on, 1, 2L, 7), summarySummary(summary, t))
        assertEquals("AS64500 Example RU: 3", summaryNetworks(summary))
        assertEquals("a 1\nb 2", metricSamples("# HELP a\n# TYPE a counter\na 1\n\nb 2\n"))
        assertEquals("# only", metricSamples("# only"))
    }

    @Test
    fun users() {
        val user = json("""{"name": "alice", "enabled": true, "services": ["naive", "hy2"], "expiresAt": "100", "trafficLimitBytes": "50", "note": "friend"}""")
        assertEquals(
            listOf("naive, hy2", s(R.string.raycontrol_admin_expires, "d100"), s(R.string.raycontrol_admin_limit, "50B"), "friend").joinToString(" · "),
            userFacts(user, t, date, size),
        )
        assertEquals(
            listOf(s(R.string.raycontrol_admin_no_expiry), s(R.string.raycontrol_admin_no_limit)).joinToString(" · "),
            userFacts(json("""{"name": "bob"}"""), t, date, size, services = false),
        )
        assertTrue(userFacts(json("""{"name": "bob"}"""), t, date, size).startsWith(s(R.string.raycontrol_admin_no_services)))
        // int64 as strings, as the node's JSON has them; nothing typed is no expiry and no limit
        assertEquals(
            json("""{"name": "bob", "expiresAt": "2592100", "trafficLimitBytes": "50000000000", "note": "n", "services": ["naive"]}"""),
            newUserBody("bob", "30", " 50 ", " n ", listOf("naive"), now = 100),
        )
        assertEquals(
            json("""{"name": "bob", "expiresAt": "0", "trafficLimitBytes": "0", "note": "", "services": []}"""),
            newUserBody("bob", "", "x", "", emptyList(), now = 100),
        )
    }

    @Test
    fun devices() {
        val rows = deviceRows(
            json(
                """{"devices": [{"id": "1", "name": "pixel", "role": "admin", "registered": true, "createdAt": "5"},
                    {"id": "2", "name": "old", "role": "user", "revokedAt": "9", "createdAt": "6"}],
                    "invites": [{"id": "3", "name": "tablet", "role": "user", "expiresAt": "8"}]}""",
            ),
            t, date,
        )
        assertEquals(
            listOf(
                DeviceRow("1", "pixel", "pixel · admin", s(R.string.raycontrol_admin_active) + " · " + s(R.string.raycontrol_admin_registered) + " · d5"),
                DeviceRow("2", "old", "old · user", s(R.string.raycontrol_admin_revoked) + " · d6", revocable = false),
                DeviceRow("3", "tablet", "tablet · user", s(R.string.raycontrol_admin_invite_pending, "d8"), invite = true),
            ),
            rows,
        )
        assertEquals(emptyList<DeviceRow>(), deviceRows(json("{}"), t, date))
    }

    private fun service(kind: String, settings: String = "{}") = JsonObject().apply {
        addProperty("name", kind)
        addProperty("kind", kind)
        addProperty("settingsJson", settings)
    }

    @Test
    fun serviceOptions() {
        assertEquals(listOf("auth", "auth_hint", "seal", "spare_zones"), optionsOf(service("dnstt")).map { it.key })
        val spare = optionsOf(service("masterdns", """{"auth": "required", "spare_zones": ["a.example", "b.example"], "zone": "t.example"}"""))
        assertEquals(listOf("auth", "auth_hint", "seal", "spare_zones", "zone"), spare.map { it.key })
        // a list by its size, the node's default as a dash; the main zone picked of the spare ones
        assertEquals(listOf("required", "—", "—", "2", "t.example"), spare.map { it.current })
        assertEquals(listOf("a.example", "b.example"), spare.last().values)
        assertEquals(listOf("rotate_sni", "open", "target"), optionsOf(service("reality")).map { it.key })
        // a service that steals from the node's site has no target of its own
        assertEquals(listOf("rotate_sni", "open"), optionsOf(service("reality", """{"site": "site"}""")).map { it.key })
        assertEquals(listOf("404", "403", "silent", "pages"), optionsOf(service("site")).single().values)
        assertEquals(listOf("up_mbps", "down_mbps"), optionsOf(service("hy2-obfs")).map { it.key })
        assertEquals(listOf("max_connections", "device_connections"), optionsOf(service("slipstream")).map { it.key })
        val peers = optionsOf(service("yggdrasil", """{"peers": ["tls://a.example:1", "tls://b.example:2"]}""")).single()
        assertEquals("tls://a.example:1, tls://b.example:2", peers.current)
        assertTrue(peers.text)
        assertEquals(emptyList<ServiceOption>(), optionsOf(service("ssh")))
        // settings that do not parse are no settings
        assertEquals("—", optionsOf(service("site", "not json")).single().current)
    }

    @Test
    fun serviceKeys() {
        assertEquals(listOf(ServiceKey.REALITY), keysOf("reality"))
        assertEquals(listOf(ServiceKey.SEAL), keysOf("dnstt"))
        assertEquals(listOf(ServiceKey.SERVICE), keysOf("masterdns"))
        assertEquals(emptyList<ServiceKey>(), keysOf("naive"))
        assertEquals(1700000000L, settingLong(service("dnstt", """{"seal_prev_until": 1700000000}"""), "seal_prev_until"))
        assertEquals(0L, settingLong(service("dnstt"), "seal_prev_until"))
        assertEquals(0L, settingLong(service("dnstt", "broken"), "seal_prev_until"))
    }

    @Test
    fun network() {
        // a node without the call, a node without a provider: records by hand, nothing to sync
        assertEquals(s(R.string.raycontrol_admin_dns_none) to false, dnsSummary(null, t))
        assertEquals(s(R.string.raycontrol_admin_dns_none) to false, dnsSummary(json("""{"status": {}}"""), t))
        assertNull(dnsSummary(json("{}"), t))
        assertEquals(
            listOf("cloudflare · example.org", s(R.string.raycontrol_admin_dns_records, 2), "⚠ no NS").joinToString("\n") to true,
            dnsSummary(json("""{"status": {"provider": "cloudflare", "zone": "example.org", "records": [{}, {}], "problems": ["no NS"]}}"""), t),
        )
        assertEquals(s(R.string.raycontrol_admin_dns_check_summary), zonesSummary(json("{}"), t, date))
        assertEquals(
            s(R.string.raycontrol_admin_dns_check_last, "d4", "ok 2, fail 1"),
            zonesSummary(json("""{"checkedAt": "4", "zones": [{"state": "ok"}, {"state": "ok"}, {"state": "fail"}]}"""), t, date),
        )
        assertEquals(s(R.string.raycontrol_admin_dns_in_sync), dnsSyncText(json("{}"), t))
        assertEquals("add A\n⚠ no NS", dnsSyncText(json("""{"changes": ["add A"], "problems": ["no NS"]}"""), t))
        val checked = zonesText(
            json(
                """{"checkedAt": "4", "limited": true, "zones": [{"zone": "t.example", "service": "dnstt", "kind": "dnstt", "state": "fail",
                    "error": "lame", "parent": {"state": "fail", "detail": "no NS", "fix": "add NS"},
                    "resolvers": [{"resolver": "192.0.2.1", "network": "AS64500", "soa": {"state": "ok"}, "large": {"state": "warn"}}]}]}""",
            ),
            t, date,
        )
        assertEquals(
            s(R.string.raycontrol_admin_dns_checked, "d4") + "\n" + s(R.string.raycontrol_admin_dns_check_limited) +
                "\n\nt.example (dnstt, dnstt): fail\n⚠ lame\n  parent: fail no NS → add NS\n  192.0.2.1 AS64500: warn",
            checked,
        )
        assertTrue(zonesText(json("{}"), t, date).endsWith(s(R.string.raycontrol_admin_empty)))
    }

    @Test
    fun links() {
        assertNull(linksText(listOf(json("""{"service": "ssh"}""")), "alice", t))
        val (share, shown) = linksText(
            listOf(json("""{"service": "naive", "link": "fake://one"}"""), json("""{"service": "ssh"}"""), json("""{"service": "hy2", "link": "fake://two"}""")),
            "alice", t,
        )!!
        assertEquals("fake://one\nfake://two", share)
        assertTrue(shown.startsWith(s(R.string.raycontrol_admin_links_help, "alice") + "\n\nnaive:\nfake://one"))
    }

    @Test
    fun failures() {
        val other = { e: Throwable -> "other: ${e.message}" }
        assertEquals(s(R.string.raycontrol_admin_denied), failureText(Exception("raycontrol api: permission_denied: no"), t, other))
        assertEquals(s(R.string.raycontrol_admin_unauthenticated), failureText(Exception("raycontrol api: unauthenticated: gone"), t, other))
        assertEquals(s(R.string.raycontrol_admin_lost), failureText(Exception("the answer was lost"), t, other))
        assertTrue(answerLost(Exception("the answer was lost")))
        assertEquals("other: boom", failureText(Exception("boom"), t, other))
    }
}

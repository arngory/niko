package io.niko.ui

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class RayAdminTest {

    // the strings as patterns of their own, so that a test sees which one was taken and with what
    private val patterns = mapOf(
        R.string.raycontrol_admin_revoked_invite to "invite %1\$s/%2\$s withdrawn",
        R.string.raycontrol_admin_revoked_already to "%1\$s/%2\$s revoked already",
        R.string.raycontrol_admin_revoked_no_credentials to "%1\$s/%2\$s revoked, %1\$s has none",
        R.string.raycontrol_admin_revoked_renewed to "%1\$s/%2\$s revoked, %1\$s new %3\$d",
        R.string.raycontrol_admin_renewed to "%1\$s new %2\$d",
        R.string.raycontrol_admin_renewed_none to "%s has none",
        R.string.raycontrol_admin_port_everyone to "everyone",
        R.string.raycontrol_admin_port_spa to "after the knock",
        R.string.raycontrol_admin_port_admin to "admin",
        R.string.raycontrol_admin_port_acme to "acme",
        R.string.raycontrol_admin_port_fixed to "fixed",
        R.string.raycontrol_admin_port_random to "random",
        R.string.raycontrol_admin_port_chosen to "chosen",
        R.string.raycontrol_admin_port_sshd to "sshd",
        R.string.raycontrol_admin_port_current to "now %1\$d until %2\$s",
        R.string.raycontrol_admin_relays_count to "relays: %s",
        R.string.raycontrol_admin_relay_waiting to "waiting",
        R.string.raycontrol_admin_relay_offered to "offered",
        R.string.raycontrol_admin_relay_requested to "requested",
        R.string.raycontrol_admin_relay_running to "running",
        R.string.raycontrol_admin_relay_done to "done",
        R.string.raycontrol_admin_relay_rolled_back to "rolled back",
        R.string.raycontrol_admin_relay_failed to "failed",
        R.string.raycontrol_admin_relay_current to "current",
        R.string.raycontrol_admin_relay_off to "off",
        R.string.raycontrol_admin_relay_unknown to "unknown",
        R.string.raycontrol_admin_relay_no_version to "no version",
        R.string.raycontrol_admin_relay_since to "since %s",
        R.string.raycontrol_admin_relay_never_heard to "never heard",
        R.string.raycontrol_admin_relay_last_heard to "last heard %s",
        R.string.raycontrol_admin_relay_hint_off to "hint: remote on",
        R.string.raycontrol_admin_relay_hint_unknown to "hint: update by hand",
        R.string.raycontrol_admin_relay_reason_download to "no download",
        R.string.raycontrol_admin_relay_reason_install to "no install",
        R.string.raycontrol_admin_relay_reason_no_word to "went silent",
    )
    private val t = AdminTexts { id, args -> String.format(Locale.US, patterns.getValue(id), *args) }

    private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject

    @Test
    fun revocations() {
        // a node before v0.27.0 answers {}: nothing to say, the list shows it
        assertNull(revokeText(json("{}"), "alice", "phone", t))
        assertEquals("invite alice/tablet withdrawn", revokeText(json("""{"invite":true}"""), "alice", "tablet", t))
        assertEquals("alice/phone revoked already",
            revokeText(json("""{"already":true,"user":"alice","device":"phone"}"""), "alice", "phone", t))
        assertEquals("alice/phone revoked, alice has none", revokeText(json("""{"user":"alice","device":"phone"}"""), "alice", "phone", t))
        // uint32 is a number in protojson; the names are the node's
        assertEquals("bob/laptop revoked, bob new 7",
            revokeText(json("""{"user":"bob","device":"laptop","credentials":7}"""), "alice", "phone", t))
        assertEquals("alice/phone revoked, alice new 3", revokeText(json("""{"user":"alice","credentials":"3"}"""), "alice", "phone", t))
    }

    @Test
    fun renewals() {
        assertEquals("alice new 5", renewText(json("""{"name":"alice","credentials":5}"""), "alice", t))
        assertEquals("alice has none", renewText(json("""{"name":"alice"}"""), "alice", t))
        assertEquals("alice has none", renewText(json("{}"), "alice", t))
    }

    @Test
    fun lastSsh() {
        assertTrue(lastSshRefused("ssh", "failed_precondition", force = false))
        assertFalse(lastSshRefused("ssh", "failed_precondition", force = true))
        assertFalse(lastSshRefused("ssh", "invalid_argument", force = false))
        assertFalse(lastSshRefused("ssh", null, force = false))
        assertFalse(lastSshRefused("reality", "failed_precondition", force = false))
    }

    @Test
    fun targets() {
        assertEquals("www.example.org:443", realityTarget(" www.example.org:443 "))
        assertEquals("127.0.0.1:8443", realityTarget("127.0.0.1:8443"))
        assertEquals("localhost:8443", realityTarget("localhost:8443"))
        assertEquals("[2001:db8::1]:443", realityTarget("[2001:db8::1]:443"))
        assertEquals("[::1]:65535", realityTarget("[::1]:65535"))
        assertEquals("sub_1.example-host.net:1", realityTarget("sub_1.example-host.net:1"))
        // the node refuses these itself, with its reason: the form is right
        assertEquals("0.0.0.0:443", realityTarget("0.0.0.0:443"))
        assertEquals("[::]:443", realityTarget("[::]:443"))
        for (bad in listOf("", "  ", "www.example.org", "www.example.org:", ":443", "www.example.org:0", "www.example.org:65536",
            "www.example.org:x", "www.example.org:443x", "www.example.org:-1", "2001:db8::1:443", "[2001:db8::1]", "[2001:db8::1]443",
            "[2001:db8::1:443", "[www.example.org]:443", "[]:443", "a b:443", "https://www.example.org:443", "/run/site.sock",
            "www.example.org:0443443")) {
            assertNull(bad, realityTarget(bad))
        }
    }

    @Test
    fun nodeErrors() {
        assertEquals("invalid target: refused", nodeMessage("raycontrol api: invalid_argument: invalid target: refused"))
        assertEquals("raycontrol api: the node did not answer", nodeMessage("raycontrol api: the node did not answer"))
        assertEquals("raycontrol api: invalid_argument", nodeMessage("raycontrol api: invalid_argument"))
        assertTrue(targetUnknown("raycontrol api: invalid_argument: invalid: reality-1 has no option \"target\" (site: answer; reality: rotate_sni, open)"))
        assertFalse(targetUnknown("raycontrol api: invalid_argument: invalid target \"x\": HOST:PORT, an address or a name and a port"))
        assertFalse(targetUnknown(null))
    }

    @Test
    fun ports() {
        val time = { s: Long -> "t$s" }
        assertEquals("TCP+UDP 40000-40099 spa: everyone, random; now 40042 until t1763200000",
            portLine(json("""{"name":"spa","protos":["tcp","udp"],"from":40000,"to":40099,"source":"random","access":"everyone",
                "current":40042,"currentUntil":"1763200000"}"""), t, time))
        assertEquals("TCP 22 ssh: admin, sshd", portLine(json("""{"name":"ssh","protos":["tcp"],"from":22,"to":22,"source":"sshd","access":"admin"}"""), t, time))
        assertEquals("TCP 80 acme: acme, fixed", portLine(json("""{"name":"acme","protos":["tcp"],"from":80,"to":80,"source":"fixed","access":"acme"}"""), t, time))
        assertEquals("UDP 50000-50999 hy2 rotation: after the knock, chosen; now 50123 until t9",
            portLine(json("""{"name":"hy2 rotation","protos":["udp"],"from":50000,"to":50999,"source":"chosen","access":"spa",
                "current":50123,"currentUntil":"9"}"""), t, time))
        // what this app does not know yet stays as the node says it
        assertEquals("TCP 8443 api: relay, other", portLine(json("""{"name":"api","protos":["tcp"],"from":8443,"source":"other","access":"relay"}"""), t, time))
    }

    @Test
    fun patches() {
        assertEquals("v0.27.3", latestPatch(json("""{"installed":"v0.27.0","latest":"v0.28.0","latestPatch":"v0.27.3"}""")))
        // the latest release itself: the summary names it already
        assertNull(latestPatch(json("""{"installed":"v0.27.0","latest":"v0.27.3","latestPatch":"v0.27.3"}""")))
        // a node before v0.27.0 has none
        assertNull(latestPatch(json("""{"installed":"v0.26.0","latest":"v0.27.0"}""")))
        assertNull(latestPatch(json("""{"installed":"v0.27.3","latest":"v0.28.0","latestPatch":"v0.27.3"}""")))
    }

    // ---- the relays' updates (relay-update-remote-v1 §8)

    @Test
    fun relays() {
        val now = 10_000L
        val time = { seconds: Long -> "t$seconds" }
        // a node before v0.28.0, or one without relays: no line, nothing to wait for
        assertNull(relaysSummary(json("{}"), t))
        assertFalse(relaysPending(json("{}")))
        val update = json("""{"relays":[
            {"name":"r1","version":"v0.27.0","state":"running","target":"v0.28.0","updateId":"7","changedAt":"9900","seenAt":"9990","remote":true},
            {"name":"r2","version":"v0.28.0","state":"current","seenAt":"9995","remote":true},
            {"name":"r3","version":"v0.27.0","state":"waiting","target":"v0.28.0","changedAt":"9800","seenAt":"7000","remote":true}]}""")
        assertTrue(relaysPending(update))
        assertEquals("relays: running 1, current 1, waiting 1", relaysSummary(update, t))
        assertFalse(relaysPending(json("""{"relays":[{"name":"r2","state":"current"},{"name":"r4","state":"failed"}]}""")))
        val (running, current, waiting) = update.getAsJsonArray("relays").map { it.asJsonObject }
        assertEquals("r1 · v0.27.0 · running → v0.28.0 · since t9900", relayLine(running, t, now, time))
        // the target is the version it has: not repeated; heard a moment ago: no mark
        assertEquals("r2 · v0.28.0 · current", relayLine(current, t, now, time))
        // not heard for over half an hour: said, whatever the state; 20 minutes is within the node's refresh of a
        // connected relay's seen_at (10 minutes) and some clock skew, no mark
        assertEquals("r3 · v0.27.0 · waiting → v0.28.0 · since t9800\nlast heard t7000", relayLine(waiting, t, now, time))
        assertEquals("r8 · v0.28.0 · current", relayLine(json("""{"name":"r8","version":"v0.28.0","state":"current","seenAt":"8800"}"""), t, now, time))
        // a failure: the reason in words and the node's text; a reason this build does not know, as it is
        assertEquals("r1 · v0.27.0 · failed → v0.28.0 · since t9900\nno download: \u2068github: 503\u2069",
            relayLine(json("""{"name":"r1","version":"v0.27.0","state":"failed","target":"v0.28.0","reason":"download",
                "error":"github: 503","changedAt":9900,"seenAt":9990}"""), t, now, time))
        assertEquals("r1 · v0.27.0 · failed · since t9900\nsome_new_code",
            relayLine(json("""{"name":"r1","version":"v0.27.0","state":"failed","reason":"some_new_code","changedAt":9900,"seenAt":9990}"""),
                t, now, time))
        // off and unknown: what root on the relay can do; never heard, no version
        assertEquals("r5 · v0.26.0 · off → v0.28.0\nhint: remote on",
            relayLine(json("""{"name":"r5","version":"v0.26.0","state":"off","target":"v0.28.0","seenAt":9990}"""), t, now, time))
        assertEquals("r6 · no version · unknown\nnever heard\nhint: update by hand",
            relayLine(json("""{"name":"r6","state":"unknown"}"""), t, now, time))
        // a state of a newer node, as it is
        assertEquals("r7 · v0.29.0 · paused", relayLine(json("""{"name":"r7","version":"v0.29.0","state":"paused","seenAt":9990}"""), t, now, time))
        assertEquals("went silent", relayReason("no_word", t))
        // what a relay says goes in one line, its control and direction characters gone: no lines of its own
        assertEquals("r9 · v0.28.0 · failed · since t9900\nno install: \u2068rayctl: nft: bad rule last heard t1\u2069",
            relayLine(json("""{"name":"r9\u202e","version":"v0.28.0\n","state":"failed","reason":"install",
                "error":"rayctl: nft: bad rule\nlast heard t1\u0007","changedAt":9900,"seenAt":9990}"""), t, now, time))
        assertEquals("a\u2026", relayState("a".repeat(40), t).let { it.take(1) + it.takeLast(1) })
    }
}

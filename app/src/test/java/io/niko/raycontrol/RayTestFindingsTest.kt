package io.niko.raycontrol

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.raycontrol.RayTestFindings.Carrier
import io.niko.raycontrol.RayTestFindings.Cause
import io.niko.raycontrol.RayTestFindings.Note
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RayTestFindingsTest {

    private fun steps(vararg lines: String): List<JsonObject> = lines.map { JsonParser.parseString(it).asJsonObject }

    @Test
    fun causesByTheirErrors() {
        mapOf(
            "stalled" to Cause.STALLS,
            "Get \"https://probe/down\": dial tcp 185.10.1.1:443: i/o timeout" to Cause.TIMEOUT,
            "Get \"https://probe/down\": context deadline exceeded (Client.Timeout exceeded while awaiting headers)" to Cause.TIMEOUT,
            "Get \"https://probe/down\": net/http: TLS handshake timeout" to Cause.TIMEOUT,
            "read tcp 10.0.0.2:5000->185.10.1.1:443: read: connection reset by peer" to Cause.RESET,
            "Get \"https://probe/down\": EOF" to Cause.RESET,
            "Get \"https://probe/down\": unexpected EOF" to Cause.RESET,
            "dial tcp 185.10.1.1:443: connect: connection refused" to Cause.REFUSED,
            "Get \"https://probe/down\": tls: failed to verify certificate: x509: certificate signed by unknown authority" to Cause.TLS,
            "reality verification failed" to Cause.TLS,
            // the lookup is the cause, the timeout how it ended
            "dial tcp: lookup node.example on 10.0.0.1:53: i/o timeout" to Cause.NAME,
            "dial tcp: lookup node.example: no such host" to Cause.NAME,
            "dial tcp 185.10.1.1:443: connect: network is unreachable" to Cause.UNREACHABLE,
            "503 Service Unavailable" to Cause.HTTP,
            "429 Too Many Requests" to Cause.BUSY,
            // the node's connection limit for the user (conn-limit-v1 §4.3): naive's CONNECT answer, Hysteria2's text
            "unexpected response status: 429" to Cause.LIMIT,
            // naive's CONNECT refused otherwise (a wrong password gets the site's 404)
            "unexpected response status: 404" to Cause.REFUSED,
            "Get \"https://probe/down\": remote error: too many connections" to Cause.LIMIT,
            // an address first is not a status
            "185.10.1.1:443 said something" to Cause.OTHER,
            "something new" to Cause.OTHER,
        ).forEach { (error, cause) -> assertEquals(error, cause, RayTestFindings.classify(error)) }
        assertEquals(Cause.OTHER, RayTestFindings.classify(null))
    }

    @Test
    fun aStalledDirectPathWithTheRelayGettingThrough() {
        val result = RayTestFindings.of(
            steps(
                """{"run":"r","step":"start","net":"wifi","profiles":3}""",
                """{"run":"r","step":"direct","spa_carrier":"udp","udp_probed":true,"udp_mtu":1452}""",
                """{"run":"r","step":"subscription","profiles":3,"skipped":0,"clock_skew_s":2}""",
                """{"run":"r","step":"profile_start","key":"reality","name":"REALITY","type":"VLESS","carrier":"tcp"}""",
                """{"run":"r","step":"profile","key":"reality","tcp_ok":false,"first_byte_ms":180,"kbps":0,"stalled_at":17408,"tcp_error":"stalled","udp_ok":false,"udp_loss_pct":100.0}""",
                """{"run":"r","step":"profile_start","key":"hy2","name":"Hysteria","type":"Hysteria2","carrier":"udp"}""",
                """{"run":"r","step":"profile","key":"hy2","tcp_ok":false,"first_byte_ms":0,"kbps":0,"tcp_error":"Get \"https://probe/down\": context deadline exceeded"}""",
                """{"run":"r","step":"profile_start","key":"reality@relay","name":"REALITY via relay","type":"VLESS","carrier":"tcp","relay":"msk"}""",
                """{"run":"r","step":"profile","key":"reality@relay","tcp_ok":true,"first_byte_ms":95,"kbps":24000,"udp_ok":true,"udp_loss_pct":0.0,"udp_rtt_ms":40.0}""",
                """{"run":"r","step":"end"}""",
            )
        )
        assertEquals(listOf("reality@relay"), result.working.map { it.key })
        assertEquals(listOf("reality", "hy2"), result.failing.map { it.key })
        val stalled = result.failing.first()
        assertEquals(Cause.STALLS, stalled.cause)
        assertEquals(17L, stalled.stalledAtKb)
        assertEquals(Carrier.UDP, result.failing[1].carrier)
        assertEquals(Cause.TIMEOUT, result.failing[1].cause)
        assertEquals("msk", result.working.single().relay)
        // the direct path answers and carries UDP: the relay note, no note about the network's UDP or the clock
        assertEquals(listOf(Note.RELAY_ONLY), result.notes.map { it.note })
        assertTrue(result.noUdp.isEmpty())
        // for the file: by the keys, never the names
        assertEquals(
            """{"run":"r","step":"findings","working":["reality@relay"],"failing":{"stalls":["reality"],"timeout":["hy2"]},"notes":[{"note":"relay_only"}]}""",
            RayTestFindings.summary("r", result).toString(),
        )
    }

    @Test
    fun theNetworkAndTheNode() {
        val result = RayTestFindings.of(
            steps(
                """{"run":"r","step":"start","net":"mobile","profiles":2}""",
                """{"run":"r","step":"direct","udp_probed":true,"error":"knock: no route"}""",
                """{"run":"r","step":"subscription","error":"timeout"}""",
                """{"run":"r","step":"dns","result":"dns doh: no answer"}""",
                """{"run":"r","step":"dns_pick","engine":"dnstt"}""",
                """{"run":"r","step":"profile_start","key":"dnstt","name":"DNS","type":"VLESS","carrier":"dns"}""",
                """{"run":"r","step":"profile","key":"dnstt","tcp_ok":true,"first_byte_ms":900,"kbps":120}""",
                """{"run":"r","step":"profile_start","key":"vless","name":"VLESS","type":"VLESS","carrier":"tcp"}""",
                """{"run":"r","step":"profile","key":"vless","error":"the profile is in use"}""",
                """{"run":"r","step":"end"}""",
            )
        )
        assertEquals(
            listOf(Note.NO_DIRECT, Note.NO_SUBSCRIPTION, Note.DNS_NONE),
            result.notes.map { it.note },
        )
        assertEquals("knock: no route", result.notes.first().detail)
        // the profile in use is not a failure, nor does it leave the DNS tunnel the only one working
        assertTrue(result.failing.isEmpty())
        assertEquals(Cause.IN_USE, result.profiles.last().cause)
        assertFalse(result.has(Note.DNS_ONLY))
        assertNull(result.working.single().udpOk)
    }

    @Test
    fun udpOfThePathAndThroughProfiles() {
        fun direct(mtu: Int) = RayTestFindings.of(
            steps("""{"run":"r","step":"direct","spa_carrier":"tcp","udp_probed":true,"udp_mtu":$mtu}""")
        ).notes
        assertEquals(listOf(RayTestFindings.Finding(Note.UDP_BLOCKED)), direct(0))
        assertEquals(listOf(RayTestFindings.Finding(Note.UDP_SMALL, number = 576)), direct(576))
        assertTrue(direct(1200).isEmpty())
        // not measured (the node's slot busy): nothing about the network
        assertTrue(
            RayTestFindings.of(steps("""{"run":"r","step":"direct","spa_carrier":"tcp","udp_probed":true,"udp_status":429}""")).notes.isEmpty()
        )
        val result = RayTestFindings.of(
            steps(
                """{"run":"r","step":"subscription","profiles":1,"clock_skew_s":-95}""",
                """{"run":"r","step":"profile_start","key":"t","name":"Trojan","type":"Trojan","carrier":"tcp"}""",
                """{"run":"r","step":"profile","key":"t","tcp_ok":true,"first_byte_ms":120,"kbps":8000,"udp_ok":false,"udp_loss_pct":100.0,"udp_error":"no echo"}""",
            )
        )
        assertEquals(listOf(RayTestFindings.Finding(Note.CLOCK, number = -95)), result.notes)
        assertEquals(listOf("t"), result.noUdp.map { it.key })
    }

    @Test
    fun aDnsTunnelThatDoesNotGetThrough() {
        val result = RayTestFindings.of(
            steps(
                """{"run":"r","step":"profile_start","key":"dnstt","name":"DNS","type":"VLESS","carrier":"dns"}""",
                """{"run":"r","step":"profile","key":"dnstt","tcp_ok":false,"tcp_error":"Get \"https://probe/down\": context deadline exceeded"}""",
                """{"run":"r","step":"profile_start","key":"vless","name":"VLESS","type":"VLESS","carrier":"tcp"}""",
                """{"run":"r","step":"profile","key":"vless","tcp_ok":false,"tcp_error":"Get \"https://probe/down\": EOF"}""",
            )
        )
        // its timeout is the resolvers' path, not a blocked address: apart from the causes of the others
        assertEquals(listOf("dnstt"), result.dnsFailing.map { it.key })
        assertEquals(listOf(Cause.RESET to listOf("vless")), result.byCause.map { (cause, profiles) -> cause to profiles.map { it.key } })
        assertTrue(result.notes.isEmpty())
    }

    @Test
    fun aSpareZone() {
        val result = RayTestFindings.of(steps("""{"run":"r","step":"dns_pick","engine":"dnstt","kind":"doh","zone":2}"""))
        assertEquals(listOf(RayTestFindings.Finding(Note.DNS_SPARE, number = 2)), result.notes)
        assertTrue(RayTestFindings.of(steps("""{"run":"r","step":"dns_pick","engine":"dnstt","kind":"doh"}""")).notes.isEmpty())
    }

    @Test
    fun noNetwork() {
        val result = RayTestFindings.of(
            steps("""{"run":"r","step":"start","net":"none","profiles":2}""", """{"run":"r","step":"end","error":"no network"}""")
        )
        assertEquals(listOf(Note.NO_NETWORK), result.notes.map { it.note })
        assertTrue(result.profiles.isEmpty())
    }
}

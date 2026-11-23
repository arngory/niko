package io.niko.golden

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.database.ProxyEntity
import io.niko.fmt.TypeMap
import io.niko.raycontrol.RayImport
import io.niko.raycontrol.RayInvite
import io.niko.raycontrol.RayRegistration
import io.niko.raycontrol.RayService
import io.niko.raycontrol.RaySubscription
import io.niko.raycontrol.Rcdev1
import io.niko.utils.JavaUtil.gson
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The golden fixtures of raycontrol nodes (shared/golden/raycontrol, docs/design/desktop-stage3.md §5), which
 * libcore/nikoray reads and must give the same of:
 *
 * - `invites/<name>.txt`: a text with an invite (rcinv1, RCINV2, RCINV3) or a device package (rcdev1), read as the
 *   app's import reads it (RayImport.parseInvite, Rcdev1.parse); `<name>.json` is what came of it: `invite` or
 *   `device` with the fields the app keeps, or `error` with the reason the app gives;
 * - `payloads/<case>/payload.json` and `node.json` (the node's services, `now` its clock): a subscription's payload as
 *   RaySubscription.parse reads it; `result.json` is its rows (key, type, bean, links), the node's DoH and what the
 *   node record takes, and when the next fetch is due (nextRefresh).
 *
 * As LinksGoldenTest: a missing output is written and the test fails, to be checked and committed;
 * NIKO_GOLDEN_UPDATE=1 writes them all anew. The keys and tokens in the fixtures are made up, one character repeated.
 */
class RaycontrolGoldenTest {

    private val dir = GoldenLinks.dir("raycontrol")
    private val update = System.getenv("NIKO_GOLDEN_UPDATE") == "1"

    private fun services(list: List<RayService>) = JsonArray().apply { list.forEach { add(it.toJson()) } }

    private fun strings(list: Collection<String>) = JsonArray().apply { list.forEach { add(it) } }

    private fun invite(i: RayInvite) = JsonObject().apply {
        addProperty("host", i.host)
        add("spa", i.spa.toJson())
        addProperty("invite_id", i.inviteId)
        addProperty("invite_key", i.inviteKey)
        add("services", services(i.services))
        add("api", i.api.toJson())
        addProperty("expires", i.expires)
        addProperty("pin_required", i.pinRequired)
        i.dnsName?.let { addProperty("dns_name", it) }
        add("addrs", strings(i.addrs))
    }

    private fun device(r: RayRegistration) = JsonObject().apply {
        addProperty("host", r.host)
        add("spa", r.spa.toJson())
        addProperty("device_id", r.deviceId)
        addProperty("device_key", r.deviceKey)
        add("services", services(r.services))
        r.subscription?.let { add("sub", it.toJson()) }
        r.api?.let { add("api", it.toJson()) }
        r.role?.let { addProperty("role", it) }
        r.dnsName?.let { addProperty("dns_name", it) }
        add("addrs", strings(r.addrs))
    }

    /** What the app's import reads of [text]. */
    private fun readPackage(text: String): JsonObject = JsonObject().apply {
        try {
            if (RayImport.isInvite(text)) add("invite", invite(RayImport.parseInvite(text)))
            else add("device", device(Rcdev1.parse(text)))
        } catch (e: IllegalArgumentException) {
            addProperty("error", e.message)
        }
    }

    private fun payload(case: File): JsonObject {
        val payload = JsonParser.parseString(File(case, "payload.json").readText()).asJsonObject
        val node = JsonParser.parseString(File(case, "node.json").readText()).asJsonObject
        val kinds = node.getAsJsonArray("services").associate { s ->
            s.asJsonObject.let { it.get("id").asInt to it.get("kind").asString }
        }
        val now = node.get("now").asLong
        val parsed = RaySubscription.parse(payload, kinds, now)
        return JsonObject().apply {
            add("rows", JsonArray().apply {
                for ((key, bean) in parsed.beans) {
                    add(JsonObject().apply {
                        addProperty("key", key)
                        addProperty("type", TypeMap.reversed[ProxyEntity().putBean(bean).type])
                        add("bean", gson.toJsonTree(bean))
                        add("links", parsed.links.getValue(key).toJson())
                    })
                }
            })
            // the absent ones left out: Gson writes no null member
            parsed.doh?.let { addProperty("doh", it) }
            parsed.dohPins?.let { addProperty("doh_pins", it) }
            addProperty("skipped", parsed.skipped)
            parsed.carriers?.let { add("carriers", strings(it)) }
            parsed.name?.let { addProperty("name", it) }
            parsed.note?.let { addProperty("note", it) }
            parsed.traffic?.let { add("traffic", it.toJson()) }
            parsed.client?.let { add("client", it.toJson()) }
            parsed.dnsName?.let { addProperty("dns_name", it) }
            add("addrs", strings(parsed.addrs))
            add("spa_versions", JsonArray().apply { parsed.spaVersions.forEach { add(it) } })
            addProperty("next_refresh", RaySubscription.nextRefresh(payload, now))
        }
    }

    private fun check(file: File, actual: JsonElement, problems: MutableList<String>, recorded: MutableList<String>) {
        val name = "${file.parentFile.name}/${file.name}"
        when {
            update -> file.writeText(gson.toJson(actual) + "\n")
            !file.exists() -> {
                file.writeText(gson.toJson(actual) + "\n")
                recorded += name
            }

            JsonParser.parseString(file.readText()) != actual -> problems += "$name: now ${gson.toJson(actual)}"
        }
    }

    @Test
    fun invitesAndPayloads() {
        val problems = mutableListOf<String>()
        val recorded = mutableListOf<String>()
        val invites = File(dir, "invites").listFiles { f -> f.name.endsWith(".txt") }.orEmpty().sortedBy { it.name }
        assertTrue("no invites in $dir", invites.isNotEmpty())
        for (txt in invites) {
            check(File(txt.parentFile, txt.name.removeSuffix(".txt") + ".json"), readPackage(txt.readText().trim()), problems, recorded)
        }
        val payloads = File(dir, "payloads").listFiles { f -> File(f, "payload.json").isFile }.orEmpty().sortedBy { it.name }
        assertTrue("no payloads in $dir", payloads.isNotEmpty())
        for (case in payloads) check(File(case, "result.json"), payload(case), problems, recorded)
        if (recorded.isNotEmpty()) problems += "written, check and commit: ${recorded.joinToString()} (in $dir)"
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

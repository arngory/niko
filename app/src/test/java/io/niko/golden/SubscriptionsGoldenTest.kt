package io.niko.golden

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.GroupType
import io.niko.database.ProxyEntity
import io.niko.database.ProxyGroup
import io.niko.database.SubscriptionBean
import io.niko.fmt.AbstractBean
import io.niko.fmt.KryoConverters
import io.niko.fmt.TypeMap
import io.niko.group.RawUpdater
import io.niko.group.SubscriptionMerge
import io.niko.utils.JavaUtil.gson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The golden fixtures of subscriptions (shared/golden/subscriptions, docs/design/desktop-stage3.md §5): each case is a
 * directory of a provider's answer, `body.*` (its body) and `headers.json` (its headers, a name to its value), and of
 * the group it updates, `group.json` (`{"name": …, "subscription": {SubscriptionBean's fields}}`, both optional) and
 * `exists.json` (its profiles before: `[{"id": 1, "order": 1, "link": "<a share link>", "patch": {bean fields}}]`).
 * The app's update runs on them as RawUpdater.doUpdate runs, the database and the network aside: parseRaw of the
 * body, the headers (SubscriptionMerge.applyAnswer), prepare and plan; a profile's bean is what the database has back
 * of it (its Kryo read again). `result.json` is what became of the group: its name and subscription, each profile
 * (`state`: kept, updated, reordered, added; no `id` for a new one) and the ids deleted, and what the user is told;
 * `error.txt` is "no profiles" where parseRaw finds none, "failed" where the update throws. libcore/nikosub reads the
 * same files and must give the same (golden_test.go), compared by meaning; a custom config's JSON text by its JSON
 * (JSON-java's object here, the platform's org.json on the phone: their names' order differs).
 *
 * As LinksGoldenTest: a missing `result.json` (or `error.txt`) is written and the test fails, to be checked and
 * committed; NIKO_GOLDEN_UPDATE=1 writes them all anew. The profiles' names are not resolved (forceResolve looks
 * them up in the DNS): nikosub's own test holds that.
 */
class SubscriptionsGoldenTest {

    private val dir = GoldenLinks.dir("subscriptions")

    private val update = System.getenv("NIKO_GOLDEN_UPDATE") == "1"

    private fun emptyBean(type: String): AbstractBean = when (type) {
        "socks" -> io.niko.fmt.socks.SOCKSBean()
        "http" -> io.niko.fmt.http.HttpBean()
        "ss" -> io.niko.fmt.shadowsocks.ShadowsocksBean()
        "vmess" -> io.niko.fmt.v2ray.VMessBean()
        "trojan" -> io.niko.fmt.trojan.TrojanBean()
        "trojan-go" -> io.niko.fmt.trojan_go.TrojanGoBean()
        "mieru" -> io.niko.fmt.mieru.MieruBean()
        "naive" -> io.niko.fmt.naive.NaiveBean()
        "hysteria" -> io.niko.fmt.hysteria.HysteriaBean()
        "ssh" -> io.niko.fmt.ssh.SSHBean()
        "wg" -> io.niko.fmt.wireguard.WireGuardBean()
        "tuic" -> io.niko.fmt.tuic.TuicBean()
        "anytls" -> io.niko.proxy.anytls.AnyTLSBean()
        "config" -> io.niko.proxy.config.ConfigBean()
        else -> error("no type $type")
    }

    /** A JSON object over another: its members replace the other's. */
    private fun overlay(base: JsonObject, over: JsonObject): JsonObject {
        for ((k, v) in over.entrySet()) base.add(k, v)
        return base
    }

    private fun existing(o: JsonObject): ProxyEntity {
        val link = o.get("link").asString
        val bean = GoldenLinks.parse(link) ?: error("$link: the app makes no profile of it")
        val patched = o.getAsJsonObject("patch")?.let {
            gson.fromJson(overlay(gson.toJsonTree(bean).asJsonObject, it), bean.javaClass).apply {
                initializeDefaultValues()
            }
        } ?: bean
        return ProxyEntity(id = o.get("id").asLong, groupId = 1, userOrder = o.get("order").asLong).putBean(patched)
    }

    /** The bean as the database gives it back: its Kryo read into a new bean, with its defaults. */
    private fun stored(entity: ProxyEntity): JsonElement {
        val bean = entity.requireBean()
        val type = TypeMap.reversed[entity.type] ?: error("no name of type ${entity.type}")
        val back = KryoConverters.deserialize(emptyBean(type), KryoConverters.serialize(bean))
        return gson.toJsonTree(back)
    }

    private fun profile(entity: ProxyEntity, state: String, withId: Boolean, bean: JsonElement) = JsonObject().apply {
        if (withId) addProperty("id", entity.id)
        addProperty("order", entity.userOrder)
        addProperty("state", state)
        addProperty("type", TypeMap.reversed[entity.type])
        add("bean", bean)
    }

    private fun strings(list: Collection<String>) = JsonArray().apply { list.forEach { add(it) } }

    /** The result of a case: result.json's text, or error.txt's. */
    private fun run(case: File): Pair<String, Boolean> {
        val body = case.listFiles { f -> f.name.startsWith("body.") }.orEmpty().singleOrNull()
            ?: error("${case.name}: one body.* file")
        val headers = File(case, "headers.json").takeIf { it.isFile }?.let {
            JsonParser.parseString(it.readText()).asJsonObject.entrySet().associate { (k, v) -> k.lowercase() to v.asString }
        }.orEmpty()
        val groupJson = File(case, "group.json").takeIf { it.isFile }?.let { JsonParser.parseString(it.readText()).asJsonObject }
        val subscription = gson.fromJson(
            overlay(gson.toJsonTree(SubscriptionBean().apply { initializeDefaultValues() }).asJsonObject,
                groupJson?.getAsJsonObject("subscription") ?: JsonObject()),
            SubscriptionBean::class.java,
        )
        val group = ProxyGroup(id = 1, type = GroupType.SUBSCRIPTION, subscription = subscription).apply {
            name = groupJson?.get("name")?.asString ?: "Subscription #1"
        }
        val exists = File(case, "exists.json").takeIf { it.isFile }?.let { f ->
            JsonParser.parseString(f.readText()).asJsonArray.map { existing(it.asJsonObject) }
        }.orEmpty()
        val before = exists.map { gson.toJsonTree(it.requireBean()) }
        return try {
            val parsed = runBlocking { RawUpdater.parseRaw(body.readText()) } ?: return "no profiles" to false
            SubscriptionMerge.applyAnswer(group, subscription) { headers[it.lowercase()].orEmpty() }
            val proxies = SubscriptionMerge.prepare(parsed)
            val added = mutableListOf<ProxyEntity>()
            val plan = SubscriptionMerge.plan(group.id, exists, proxies, subscription.deduplication) { added += it }
            val profiles = JsonArray()
            for ((i, entity) in exists.withIndex()) {
                when {
                    plan.toDelete.any { it === entity } -> continue
                    plan.reordered.any { it === entity } -> profiles.add(profile(entity, "reordered", true, stored(entity)))
                    plan.toUpdate.any { it === entity } -> profiles.add(profile(entity, "updated", true, stored(entity)))
                    else -> profiles.add(profile(entity, "kept", true, before[i]))
                }
            }
            added.forEach { profiles.add(profile(it, "added", false, stored(it))) }
            val result = JsonObject().apply {
                addProperty("name", group.name)
                add("subscription", JsonObject().apply {
                    addProperty("subscriptionUserinfo", subscription.subscriptionUserinfo)
                    addProperty("announce", subscription.announce)
                    addProperty("autoUpdate", subscription.autoUpdate)
                    addProperty("autoUpdateDelay", subscription.autoUpdateDelay)
                })
                add("profiles", profiles)
                add("deleted", JsonArray().apply { plan.toDelete.forEach { add(it.id) } })
                addProperty("changed", plan.changed)
                add("added", strings(plan.added))
                add("updated", JsonArray().apply {
                    plan.updated.forEach { (k, v) -> add(JsonArray().apply { add(k); add(v) }) }
                })
                add("deletedNames", strings(plan.deleted))
                add("duplicate", strings(plan.duplicate))
            }
            gson.toJson(result) to true
        } catch (e: Exception) {
            println("${case.name}: the update fails: $e")
            "failed" to false
        }
    }

    @Test
    fun subscriptions() {
        val cases = dir.listFiles { f -> f.isDirectory }.orEmpty().sortedBy { it.name }
        assertTrue("no cases in $dir", cases.isNotEmpty())
        val problems = mutableListOf<String>()
        val recorded = mutableListOf<String>()
        for (case in cases) {
            val (actual, isResult) = run(case)
            val result = File(case, "result.json")
            val error = File(case, "error.txt")
            val (file, other) = if (isResult) result to error else error to result
            when {
                update -> {
                    other.delete()
                    file.writeText(actual + "\n")
                }

                other.exists() -> problems += "${case.name}/${other.name}: expected, but the app gives ${file.name}: $actual"
                !file.exists() -> {
                    file.writeText(actual + "\n")
                    recorded += "${case.name}/${file.name}"
                }

                isResult -> {
                    val expected = JsonParser.parseString(file.readText())
                    val got = JsonParser.parseString(actual)
                    if (expected != got) problems += "${case.name}: the result now: $actual"
                }

                file.readText().trim() != actual -> problems += "${case.name}/error.txt: expected ${file.readText().trim()}, got $actual"
            }
        }
        if (recorded.isNotEmpty()) {
            problems += "written, check and commit: ${recorded.joinToString()} (in $dir)"
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }
}

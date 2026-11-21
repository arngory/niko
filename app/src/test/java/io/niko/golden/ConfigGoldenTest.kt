package io.niko.golden

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.niko.RuleSetFiles
import io.niko.database.ProxyEntity
import io.niko.database.ProxyGroup
import io.niko.database.RuleEntity
import io.niko.fmt.ConfigDevice
import io.niko.fmt.ConfigInput
import io.niko.fmt.ConfigRay
import io.niko.fmt.ConfigSettings
import io.niko.fmt.ConfigStore
import io.niko.fmt.ConfigWarning
import io.niko.fmt.buildConfig
import io.niko.utils.JavaUtil
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * The golden configs (shared/golden/config/<case>, docs/design/desktop-stage3.md §5): `input.json` is a ConfigInput
 * written by hand, `android.json` the config the app builds of it (buildConfig), `warnings.json` what the build told
 * the user, `error.txt` the build's exception where it throws. libcore/nikoconf builds the same from the same input.
 * Written and checked as LinksGoldenTest does, NIKO_GOLDEN_UPDATE=1 included; a case without its outputs gets them.
 *
 * input.json:
 * - `profile`: the id of the profile built;
 * - `profiles`: `{id, group, order, link}` with `link` a golden link's name (shared/golden/links/<link>.txt, parsed
 *   as LinksGoldenTest does), and `patch`, fields set on the bean after (the app's names, as the bean's JSON);
 * - `groups`: `{id, order, name, type, isSelector, autoSelect, frontProxy, landingProxy}` (ProxyGroup's names);
 * - `rules`: RuleEntity's fields;
 * - `settings`: ConfigSettings' fields over its defaults, `secrets` among them;
 * - `apps`: package name → uid (ConfigDevice.uid); `private_dns`: the strict Private DNS host;
 * - `for_test`, `for_export`: buildConfig's flags;
 * - `platform`: `windows` or `linux` for a computer's case, which this test skips: its config is libcore/nikoconf's
 *   alone (`linux.json`, `windows.json`; Go writes a `windows.json` of some of the app's cases too).
 *
 * The rest of ConfigDevice is fixed: ports from 20000 on, the clock at 1800000000, no plugin, libcore's protect server
 * not up, the one-time mesh key "golden-one-time-key". No raycontrol node (ConfigRay.None), no rule-set downloaded.
 */
class ConfigGoldenTest {

    private val links: File = GoldenLinks.dir("links")
    private val dir: File = File(links.parentFile, "config")
    private val update = System.getenv("NIKO_GOLDEN_UPDATE") == "1"
    private val gson = JavaUtil.gson

    private class Device(private val apps: Map<String, Int>, private val privateDns: String?) : ConfigDevice {
        private var next = 20000
        val warnings = mutableListOf<String>()
        override fun port() = next++
        override fun uid(packageName: String) = apps[packageName]
        override fun strictPrivateDnsHost() = privateDns
        override fun pluginBundled(id: String) = false
        override fun pluginExternal(id: String) = false
        override fun protectPathReady() = false
        override fun yggdrasilKey() = "golden-one-time-key"
        override fun yggKeyRunning(seed: String) = false
        override fun now() = 1_800_000_000L
        override fun warn(warning: ConfigWarning) {
            warnings += when (warning) {
                is ConfigWarning.RuleNeedsVpn -> "rule needs VPN: ${warning.rule}"
                is ConfigWarning.NoSuchOutbound -> "no such outbound: ${warning.rule}"
            }
        }
    }

    /** a JSON object over another: its members replace the other's, objects member by member */
    private fun overlay(base: JsonObject, over: JsonObject): JsonObject {
        for ((k, v) in over.entrySet()) {
            val b = base.get(k)
            base.add(k, if (b is JsonObject && v is JsonObject) overlay(b, v) else v)
        }
        return base
    }

    private fun profile(o: JsonObject): ProxyEntity {
        val name = o.get("link").asString
        val bean = GoldenLinks.parse(File(links, "$name.txt").readText().trim())
            ?: error("$name: the app makes no profile of it")
        val patched = o.getAsJsonObject("patch")?.let {
            gson.fromJson(overlay(gson.toJsonTree(bean).asJsonObject, it), bean.javaClass)
        } ?: bean
        return ProxyEntity(
            id = o.get("id").asLong, groupId = o.get("group").asLong, userOrder = o.get("order")?.asLong ?: 0,
        ).putBean(patched)
    }

    private fun build(input: JsonObject): Triple<String, List<String>, String?> {
        val profiles = input.getAsJsonArray("profiles").map { profile(it.asJsonObject) }
        val groups = input.getAsJsonArray("groups")?.map {
            gson.fromJson(overlay(gson.toJsonTree(ProxyGroup()).asJsonObject, it.asJsonObject), ProxyGroup::class.java)
        }.orEmpty()
        val rules = input.getAsJsonArray("rules")?.map {
            overlay(gson.toJsonTree(RuleEntity()).asJsonObject, it.asJsonObject).let { r -> gson.fromJson(r, RuleEntity::class.java) }
        }.orEmpty()
        val settings = gson.fromJson(
            overlay(gson.toJsonTree(ConfigSettings()).asJsonObject, input.getAsJsonObject("settings") ?: JsonObject()),
            ConfigSettings::class.java,
        )
        val apps = input.getAsJsonObject("apps")?.entrySet()?.associate { it.key to it.value.asInt }.orEmpty()
        val device = Device(apps, input.get("private_dns")?.asString)
        val id = input.get("profile").asLong
        val proxy = profiles.first { it.id == id }
        val forTest = input.get("for_test")?.asBoolean == true
        val forExport = input.get("for_export")?.asBoolean == true
        val built = ConfigInput(proxy, settings, ConfigStore.Of(profiles, groups, rules), ConfigRay.None, device)
        return try {
            Triple(buildConfig(built, forTest, forExport, notes = false).config, device.warnings, null)
        } catch (e: Exception) {
            Triple("", device.warnings, e.message ?: e.toString())
        }
    }

    @Test
    fun configs() {
        // no rule-set is downloaded: an empty directory of the test's
        val ruleSets = Files.createTempDirectory("rule-sets").toFile()
        RuleSetFiles.dir = { ruleSets }
        val cases = dir.listFiles { f -> File(f, "input.json").isFile }.orEmpty().sortedBy { it.name }
        assertTrue("no cases in $dir", cases.isNotEmpty())
        val problems = mutableListOf<String>()
        val recorded = mutableListOf<String>()
        fun check(file: File, actual: String?, json: Boolean) {
            val name = "${file.parentFile.name}/${file.name}"
            when {
                actual == null -> if (file.exists()) {
                    if (update) file.delete() else problems += "$name: the build has none now"
                }

                update -> file.writeText(actual + "\n")
                !file.exists() -> {
                    file.writeText(actual + "\n")
                    recorded += name
                }

                json && JsonParser.parseString(file.readText()) != JsonParser.parseString(actual) ->
                    problems += "$name: ${differences(JsonParser.parseString(file.readText()), JsonParser.parseString(actual))}"

                !json && file.readText().trim() != actual.trim() -> problems += "$name: expected ${file.readText().trim()}, got $actual"
            }
        }
        for (case in cases) {
            val input = try {
                JsonParser.parseString(File(case, "input.json").readText()).asJsonObject
            } catch (e: Exception) {
                problems += "${case.name}/input.json: $e"
                continue
            }
            if (input.get("platform")?.asString.let { it != null && it != "android" }) continue
            val (config, warnings, error) = try {
                build(input)
            } catch (e: Exception) {
                problems += "${case.name}/input.json: $e"
                continue
            }
            check(File(case, "android.json"), config.takeIf { error == null }, json = true)
            check(File(case, "warnings.json"), warnings.takeIf { it.isNotEmpty() }?.let { gson.toJson(it) }, json = true)
            check(File(case, "error.txt"), error, json = false)
        }
        if (recorded.isNotEmpty()) problems += "written, check and commit: ${recorded.joinToString()} (in $dir)"
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
    }

    /** the paths where two JSON values differ, the first few */
    private fun differences(expected: JsonElement, got: JsonElement, path: String = "", out: MutableList<String> = mutableListOf()): String {
        if (out.size >= 8) return out.joinToString("; ")
        when {
            expected is JsonObject && got is JsonObject -> for (k in (expected.keySet() + got.keySet()).sorted()) {
                val e = expected.get(k)
                val g = got.get(k)
                if (e != g) {
                    if (e == null || g == null) out += "$path.$k: expected $e, got $g" else differences(e, g, "$path.$k", out)
                }
            }

            expected.isJsonArray && got.isJsonArray && expected.asJsonArray.size() == got.asJsonArray.size() ->
                expected.asJsonArray.zip(got.asJsonArray).forEachIndexed { i, (e, g) ->
                    if (e != g) differences(e, g, "$path[$i]", out)
                }

            else -> out += "$path: expected $expected, got $got"
        }
        return out.joinToString("; ")
    }
}

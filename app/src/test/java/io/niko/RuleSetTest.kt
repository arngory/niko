package io.niko

import io.niko.SingBoxOptions.DNSRule_DefaultOptions
import io.niko.SingBoxOptions.RuleSet
import io.niko.SingBoxOptions.Rule_DefaultOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RuleSetTest {

    private val domainSet = "rule-set:https://example.com/geosite-ru-blocked.srs"
    private val ipSet = "rule-set:https://example.com/geoip-ru-blocked.srs"

    @Test
    fun routeRuleKeepsRuleSetsOfBothLists() {
        val rule = Rule_DefaultOptions()
        rule.makeSingBoxRule(listOf("geosite:ru", domainSet, "example.org"), false)
        rule.makeSingBoxRule(listOf("geoip:ru", ipSet, "10.0.0.0/8"), true)
        assertEquals(listOf("geosite:ru", domainSet, "geoip:ru", ipSet), rule.rule_set)
        assertEquals(listOf("example.org"), rule.domain_suffix)
        assertEquals(listOf("10.0.0.0/8"), rule.ip_cidr)
    }

    @Test
    fun dnsRuleSkipsIPRuleSets() {
        val rule = DNSRule_DefaultOptions()
        rule.makeSingBoxRule(listOf(domainSet, ipSet, "example.org"))
        assertEquals(listOf(domainSet), rule.rule_set)
        assertEquals(listOf("example.org"), rule.domain_suffix)

        val onlyIP = DNSRule_DefaultOptions()
        onlyIP.makeSingBoxRule(listOf(ipSet))
        assertNull(onlyIP.rule_set)
    }

    @Test
    fun remoteRuleSetDefinition() {
        val sets = mutableListOf<RuleSet>()
        generateRuleSet(
            listOf(domainSet, "rule-set:https://example.com/list.json?v=2", "geosite:ru"), sets, 72
        )
        val remote = sets[0]
        assertEquals("remote", remote.type)
        assertEquals(domainSet, remote.tag)
        assertEquals("https://example.com/geosite-ru-blocked.srs", remote.url)
        assertEquals("binary", remote.format)
        assertEquals("72h", remote.update_interval)
        assertEquals("source", sets[1].format)
        assertEquals("local", sets[2].type)
    }
}

/** Rule-sets the app downloads (srs:) and the Russian preset. */
class RuleSetFilesTest {

    private val dir = java.nio.file.Files.createTempDirectory("rule-sets").toFile()
    private val domains = "srs:https://example.com/geosite-ru-blocked.srs"
    private val ips = "srs:https://example.com/geoip-ru-blocked.srs"

    init {
        RuleSetFiles.dir = { dir }
    }

    @Test
    fun filesByURL() {
        val file = RuleSetFiles.file(domains)
        assertEquals(dir, file.parentFile)
        assertEquals(file, RuleSetFiles.file("srs: https://example.com/geosite-ru-blocked.srs"))
        org.junit.Assert.assertNotEquals(file, RuleSetFiles.file(ips))
        assertEquals("source", RuleSetFiles.format("srs:https://example.com/list.json?v=2"))
        assertEquals("binary", RuleSetFiles.format(domains))
        assertTrue(RuleSetFiles.file("srs:https://example.com/list.json").name.endsWith(".json"))
        assertTrue(RuleSetFiles.looksValid("SRS\u0001xx".toByteArray(), "binary"))
        org.junit.Assert.assertFalse(RuleSetFiles.looksValid("<html>".toByteArray(), "binary"))
        assertTrue(RuleSetFiles.looksValid("  {\"version\":2".toByteArray(), "source"))
        org.junit.Assert.assertFalse(RuleSetFiles.looksValid("SRS\u0001".toByteArray(), "source"))
        assertEquals(listOf(domains, ips), RuleSetFiles.itemsOf(listOf("$domains\ndomain:ru", "$ips,10.0.0.0/8", domains)))
    }

    @Test
    fun ruleSetsAsLocalFiles() {
        val rule = Rule_DefaultOptions()
        rule.makeSingBoxRule(listOf(domains, "example.org"), false)
        rule.makeSingBoxRule(listOf(ips), true)
        assertEquals(listOf(domains, ips), rule.rule_set)
        val sets = mutableListOf<RuleSet>()
        generateRuleSet(rule.rule_set, sets, 24)
        assertEquals("local", sets[0].type)
        assertEquals(RuleSetFiles.file(domains).absolutePath, sets[0].path)
        assertEquals("binary", sets[0].format)
        // a DNS rule takes a downloaded rule-set with domains (by what its check found, kept beside it), not one of IP
        // CIDRs only whatever its name, nor one not downloaded
        fun download(item: String, kinds: Int?) {
            RuleSetFiles.file(item).apply { parentFile?.mkdirs(); writeBytes("SRS\u0001data".toByteArray()) }
            val kindsFile = java.io.File(RuleSetFiles.file(item).path + ".kinds")
            if (kinds == null) kindsFile.delete() else kindsFile.writeText(kinds.toString())
        }
        download(domains, RuleSetFiles.HAS_DOMAIN)
        val cidrs = "srs:https://example.com/ru-cidr.srs"
        download(cidrs, RuleSetFiles.HAS_IP)
        RuleSetFiles.file(ips).delete()
        val dns = DNSRule_DefaultOptions()
        dns.makeSingBoxRule(listOf(domains, cidrs, ips))
        assertEquals(listOf(domains), dns.rule_set)
        assertTrue(dns.hasDomain())
        val none = DNSRule_DefaultOptions().apply { makeSingBoxRule(listOf(cidrs)) }
        org.junit.Assert.assertFalse(none.hasDomain())
        // a file whose kinds are not known (no check here, as before it) goes by its name
        val unknownIP = "srs:https://example.com/geoip-x.srs"
        download(unknownIP, null)
        download("srs:https://example.com/geosite-x.srs", null)
        org.junit.Assert.assertFalse(RuleSetFiles.usableInDns(unknownIP))
        assertTrue(RuleSetFiles.usableInDns("srs:https://example.com/geosite-x.srs"))
        // a kinds file older than its file is not about it
        val stale = java.io.File(RuleSetFiles.file(cidrs).path + ".kinds")
        stale.setLastModified(RuleSetFiles.file(cidrs).lastModified() - 60_000)
        assertEquals(null, RuleSetFiles.kinds(cidrs))
        download(cidrs, RuleSetFiles.HAS_IP)
        // an exported config names them by URL, as remote rule-sets (those over https only); its DNS rule by what is
        // known here, else by the name, whether downloaded here or not
        val exported = mutableListOf<RuleSet>()
        generateRuleSet(listOf(domains, "srs:http://example.com/geosite-x.srs"), exported, 24, forExport = true)
        assertEquals(1, exported.size)
        assertEquals("remote", exported[0].type)
        assertEquals("https://example.com/geosite-ru-blocked.srs", exported[0].url)
        val notHere = "srs:https://example.com/geosite-not-here.srs"
        val exportedDns = DNSRule_DefaultOptions().apply { makeSingBoxRule(listOf(notHere, cidrs, ips), forExport = true) }
        assertEquals(listOf(notHere), exportedDns.rule_set)
    }

    @Test
    fun missingRuleSetsLeftOut() {
        RuleSetFiles.file(domains).apply { parentFile?.mkdirs(); writeBytes("SRS\u0001data".toByteArray()) }
        RuleSetFiles.file(ips).delete()
        val (kept, missing) = RuleSetFiles.withoutMissing(listOf(domains, ips, "domain:ru"))
        assertEquals(listOf(domains, "domain:ru"), kept)
        assertEquals(listOf(ips), missing)
        assertEquals(listOf("domain:ru") to emptyList<String>(), RuleSetFiles.withoutMissing(listOf("domain:ru")))
        // only https: a list over http is never fetched, and a file under its name never taken
        val plain = "srs:http://example.com/geosite-x.srs"
        RuleSetFiles.file(plain).writeBytes("SRS\u0001data".toByteArray())
        org.junit.Assert.assertFalse(RuleSetFiles.fetchable(plain) || RuleSetFiles.available(plain))
        // a rule left without any address is left out whole; one with another address keeps it
        val left = Rule_DefaultOptions().apply { makeSingBoxRule(RuleSetFiles.withoutMissing(listOf(ips)).first, true) }
        org.junit.Assert.assertFalse(left.hasAddress())
        val kept2 = Rule_DefaultOptions().apply { makeSingBoxRule(kept, false) }
        assertTrue(kept2.hasAddress())
    }

    @Test
    fun russianDirectWaitsForTheBlockedLists() {
        val (blocked, direct) = RoutePresets.russia("blocked", "direct")
        assertTrue(RoutePresets.isRussiaBlocked(blocked) && !RoutePresets.isRussiaBlocked(direct))
        assertTrue(RoutePresets.isRussiaDirect(direct) && !RoutePresets.isRussiaDirect(blocked))
        val lists = listOf(RoutePresets.RU_BLOCKED_DOMAINS, RoutePresets.RU_BLOCKED_IPS)
        lists.forEach { RuleSetFiles.file(it).delete() }
        // the blocked rule on, its lists not here: the direct one waits (alone it would send blocked .ru sites directly)
        assertTrue(RoutePresets.russiaDirectWaits(listOf(blocked, direct)))
        RuleSetFiles.file(lists[0]).apply { parentFile?.mkdirs(); writeBytes("SRS\u0001data".toByteArray()) }
        assertTrue(RoutePresets.russiaDirectWaits(listOf(blocked, direct)))
        RuleSetFiles.file(lists[1]).writeBytes("SRS\u0001data".toByteArray())
        org.junit.Assert.assertFalse(RoutePresets.russiaDirectWaits(listOf(blocked, direct)))
        // the blocked rule turned off (not among the enabled): the user's choice, the direct one goes
        lists.forEach { RuleSetFiles.file(it).delete() }
        org.junit.Assert.assertFalse(RoutePresets.russiaDirectWaits(listOf(direct)))
    }

    @Test
    fun localProxyOnlyWhereTheAppGoesThroughIt() {
        // VPN mode: the app goes through the TUN, the port is closed by default; a service round must not need it
        org.junit.Assert.assertFalse(RuleSetFiles.mustUseLocalProxy(true, Key.MODE_VPN, true))
        org.junit.Assert.assertFalse(RuleSetFiles.mustUseLocalProxy(false, Key.MODE_VPN, true))
        // proxy mode: through the port while the service runs, and a service round even as it stops
        assertTrue(RuleSetFiles.mustUseLocalProxy(false, Key.MODE_PROXY, true))
        assertTrue(RuleSetFiles.mustUseLocalProxy(true, Key.MODE_PROXY, false))
        // the service not running, from the UI: as any app's request
        org.junit.Assert.assertFalse(RuleSetFiles.mustUseLocalProxy(false, Key.MODE_PROXY, false))
    }

    @Test
    fun russianPreset() {
        val (blocked, direct) = RoutePresets.russia("blocked", "direct")
        assertEquals(0L, blocked.outbound)
        assertEquals(-1L, direct.outbound)
        assertTrue(blocked.enabled && direct.enabled)
        assertEquals(RoutePresets.RU_BLOCKED_DOMAINS, blocked.domains)
        assertEquals(RoutePresets.RU_BLOCKED_IPS, blocked.ip)
        assertEquals(listOf(RoutePresets.RU_INSIDE_DOMAINS, "domain:ru", "domain:su", "domain:xn--p1ai"), direct.domains.split("\n"))
        assertEquals(RoutePresets.RU_IPS, direct.ip)
        assertTrue(RoutePresets.isRussia(blocked) && RoutePresets.isRussia(direct))
        org.junit.Assert.assertFalse(RoutePresets.isRussia(io.niko.database.RuleEntity(domains = "geosite:ru")))
        // a rule of the user's naming one of the lists is not the preset's: it is not replaced, nor held back
        val own = io.niko.database.RuleEntity(ip = RoutePresets.RU_IPS, outbound = -2)
        org.junit.Assert.assertFalse(RoutePresets.isRussia(own) || RoutePresets.isRussiaDirect(own))
        val ads = io.niko.database.RuleEntity(
            domains = "srs:https://raw.githubusercontent.com/runetfreedom/russia-v2ray-rules-dat/release/sing-box/rule-set-geosite/geosite-category-ads-all.srs",
            outbound = -2,
        )
        org.junit.Assert.assertFalse(RoutePresets.isRussia(ads))
        // the preset's direct rule sent elsewhere by the user is replaced on adding again, but not held back
        val moved = direct.copy(outbound = 5)
        assertTrue(RoutePresets.isRussia(moved))
        org.junit.Assert.assertFalse(RoutePresets.isRussiaDirect(moved))
        // every list is a downloaded rule-set of runetfreedom's
        for (item in RuleSetFiles.itemsOf(listOf(blocked.domains, blocked.ip, direct.domains, direct.ip))) {
            assertTrue(item, RuleSetFiles.url(item).startsWith("https://raw.githubusercontent.com/runetfreedom/russia-v2ray-rules-dat/release/sing-box/rule-set-"))
        }
    }
}

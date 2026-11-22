package io.niko.fmt

import org.junit.Assert.assertEquals
import org.junit.Test

/** DpiDirect beside its golden file (io.niko.golden.DpiGoldenTest): the old setting's migration, the platform's probe. */
class DpiDirectTest {

    @Test
    fun oldSetting() {
        assertEquals(DpiDirect.PRESET to "tlsrec_sni", DpiDirect.migrated("1"))
        assertEquals(DpiDirect.PRESET to "split_sni", DpiDirect.migrated("2"))
        assertEquals(DpiDirect.OFF to DpiDirect.DEFAULT_PRESET, DpiDirect.migrated("0"))
        assertEquals(DpiDirect.OFF to DpiDirect.DEFAULT_PRESET, DpiDirect.migrated("junk"))
        for (old in listOf("1", "2")) assertEquals(true, DpiDirect.preset(DpiDirect.migrated(old).second) != null)
    }

    @Test
    fun capabilities() {
        assertEquals(DpiDirect.Capabilities(ttl = true, oob = false), DpiDirect.capabilities("""{"ttl":true,"oob":false,"notsent":true}"""))
        assertEquals(null, DpiDirect.capabilities("""{"ttl":false,"oob":false,"notsent":false,"error":"socket: denied"}"""))
        assertEquals(null, DpiDirect.capabilities("not json"))
        val noTtl = DpiDirect.Capabilities(ttl = false, oob = true)
        assertEquals(listOf("TTL"), noTtl.lacks(DpiDirect.preset("disoob_sld")!!.strategy))
        assertEquals(emptyList<String>(), noTtl.lacks(DpiDirect.preset("split_sni")!!.strategy))
        assertEquals(listOf("TTL", "OOB"), DpiDirect.Capabilities(ttl = false, oob = false).lacks(DpiDirect.preset("disoob_sld")!!.strategy))
        assertEquals("split 1, split midsld", DpiDirect.describe(DpiDirect.preset("split_sni")!!.strategy))
        assertEquals("tlsrec sni+1", DpiDirect.describe(DpiDirect.preset("tlsrec_sni")!!.strategy))
    }
}

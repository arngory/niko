package io.niko.ui.settings

import androidx.preference.PreferenceDataStore
import io.niko.DEFAULT_REMOTE_DNS
import io.niko.Key
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** StoreSettingValues over a store in memory: defaults, writes at once and on commit, what may not be stored. */
class StoreSettingValuesTest {

    private class MemoryStore : PreferenceDataStore() {
        val map = mutableMapOf<String, Any>()
        override fun putString(key: String, value: String?) {
            if (value == null) map.remove(key) else map[key] = value
        }
        override fun getString(key: String, defValue: String?) = map[key] as? String ?: defValue
        override fun putBoolean(key: String, value: Boolean) {
            map[key] = value
        }
        override fun getBoolean(key: String, defValue: Boolean) = map[key] as? Boolean ?: defValue
        override fun putInt(key: String, value: Int) {
            map[key] = value
        }
        override fun getInt(key: String, defValue: Int) = map[key] as? Int ?: defValue
    }

    private val store = MemoryStore()
    private val heard = mutableListOf<String>()
    private val values = StoreSettingValues(store) { heard += it }

    @Test
    fun defaultsAsTheOldScreenWroteThem() {
        store.putString(Key.MTU, "1500")
        values.writeMissingDefaults { it in store.map }
        assertEquals("1500", store.map[Key.MTU])
        assertEquals("1000", store.map[Key.SPEED_INTERVAL])
        assertEquals(true, store.map[Key.ENABLE_FAKEDNS])
        assertEquals("1000", values.text(Key.SPEED_INTERVAL))
    }

    @Test
    fun switchesAndChoicesAtOnce() {
        values.setFlag(Key.BYPASS_LAN, true)
        values.setFlag(Key.BYPASS_LAN, true)
        values.setChoice(Key.IPV6_MODE, "2")
        assertEquals(true, store.map[Key.BYPASS_LAN])
        assertEquals("2", store.map[Key.IPV6_MODE])
        assertEquals(listOf(Key.BYPASS_LAN, Key.IPV6_MODE), heard)
    }

    @Test
    fun textOnCommitAndOnlyWhatMayBeStored() {
        store.putString(Key.MIXED_PORT, "2080")
        values.setText(Key.MIXED_PORT, "70000")
        values.setText(Key.REMOTE_DNS, "  ")
        values.setText(Key.DIRECT_DNS, " 1.1.1.1 ")
        values.setText(SettingKeys.CONNECTION_TEST_CONCURRENT, "8")
        assertEquals("70000", values.text(Key.MIXED_PORT))
        assertFalse(Key.DIRECT_DNS in store.map)
        assertEquals(listOf(Key.DIRECT_DNS, SettingKeys.CONNECTION_TEST_CONCURRENT), values.commit())
        assertEquals("2080", values.text(Key.MIXED_PORT))
        assertEquals(DEFAULT_REMOTE_DNS, values.text(Key.REMOTE_DNS))
        assertEquals("1.1.1.1", store.map[Key.DIRECT_DNS])
        assertEquals(8, store.map[SettingKeys.CONNECTION_TEST_CONCURRENT])
        assertTrue(heard.isEmpty())
        assertTrue(values.commit().isEmpty())
    }

    @Test
    fun dpiStringOnlyWhereItIsTaken() {
        values.setText(Key.DPI_DIRECT_CUSTOM, " -s 1 -r 1+s ")
        assertEquals(listOf(Key.DPI_DIRECT_CUSTOM), values.commit())
        assertEquals("-s 1 -r 1+s", store.map[Key.DPI_DIRECT_CUSTOM])
        values.setText(Key.DPI_DIRECT_CUSTOM, "-f -1")
        assertTrue(values.commit().isEmpty())
        assertEquals("-s 1 -r 1+s", values.text(Key.DPI_DIRECT_CUSTOM))
        values.setText(Key.DPI_DIRECT_CUSTOM, "")
        assertEquals(listOf(Key.DPI_DIRECT_CUSTOM), values.commit())
        assertEquals(SettingEffect.RELOAD, SettingKeys.effect(Key.DPI_DIRECT_CUSTOM))
        assertEquals(SettingEffect.RELOAD, SettingKeys.effect(Key.DPI_DIRECT))
        assertEquals(SettingEffect.RELOAD, SettingKeys.effect(Key.DPI_DIRECT_PRESET))
    }

    @Test
    fun effects() {
        assertEquals(SettingEffect.RESTART, SettingKeys.effect(Key.LOG_LEVEL))
        assertEquals(SettingEffect.RELOAD, SettingKeys.effect(Key.REMOTE_DNS))
        assertEquals(SettingEffect.NONE, SettingKeys.effect(Key.SHOW_BOTTOM_BAR))
        assertEquals("dns.google", dnsHost("https://dns.google/dns-query"))
        assertEquals("local", dnsHost("local"))
        assertEquals("1.1.1.1:53", dnsHost("udp://1.1.1.1:53"))
        assertEquals("cloudflare-dns.com +1", dnsHost(DEFAULT_REMOTE_DNS))
        assertEquals("1.1.1.1 +2", dnsHost("# mine\n\n tls://1.1.1.1 \nlocal\n# off: udp://9.9.9.9\nhttps://dns.quad9.net/dns-query\n"))
        assertEquals("", dnsHost(""))
    }
}

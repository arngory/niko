package io.niko.ui.home

import io.niko.database.ProxyEntity
import io.niko.fmt.AbstractBean
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.v2ray.VMessBean
import org.junit.Assert.assertEquals
import org.junit.Test

/** The configs' sheet: the tags a row shows and the search over the groups. */
class ConfigsLoaderTest {

    private fun entity(bean: AbstractBean) = ProxyEntity().putBean(bean.apply { initializeDefaultValues() })

    @Test
    fun aNodesRowNamedByItsMethodShowsTheTransportAlone() {
        val naive = NaiveBean().apply { name = "naive"; proto = "https" }
        assertEquals(listOf("HTTPS"), ConfigsLoader.tags(entity(naive)))
        val hy2 = HysteriaBean().apply { name = "Hysteria2"; protocolVersion = 2 }
        assertEquals(listOf("QUIC"), ConfigsLoader.tags(entity(hy2)))
    }

    @Test
    fun aServerNamedByItsPlaceShowsItsKindAndWhatSetsItApart() {
        val vless = VMessBean().apply {
            name = "Netherlands · Amsterdam"; alterId = -1; security = "reality"; type = "tcp"
        }
        assertEquals(listOf("VLESS", "REALITY"), ConfigsLoader.tags(entity(vless)))
        val ws = VMessBean().apply { name = "Germany"; alterId = -1; security = "tls"; type = "ws" }
        assertEquals(listOf("VLESS", "WS"), ConfigsLoader.tags(entity(ws)))
    }

    private val groups = listOf(
        ConfigGroup(
            1, "arngory", subscription = true,
            listOf(
                ConfigRow(11, "naive", listOf("HTTPS"), 38, unreachable = false, selected = false),
                ConfigRow(12, "Hysteria2", listOf("QUIC"), null, unreachable = false, selected = true),
            ),
        ),
        ConfigGroup(
            2, "provider.net", subscription = true,
            listOf(ConfigRow(21, "Amsterdam", listOf("VLESS", "REALITY"), 63, unreachable = false, selected = false)),
        ),
    )

    @Test
    fun theSearchKeepsTheRowsThatMatchAndDropsTheGroupsLeftEmpty() {
        assertEquals(groups, ConfigsLoader.filter(groups, "  "))
        val reality = ConfigsLoader.filter(groups, "reality")
        assertEquals(listOf(2L), reality.map { it.id })
        val quic = ConfigsLoader.filter(groups, "QUIC")
        assertEquals(listOf(12L), quic.single().profiles.map { it.id })
        // the group's own name keeps all its rows
        assertEquals(listOf(11L, 12L), ConfigsLoader.filter(groups, "arng").single().profiles.map { it.id })
        assertEquals(emptyList<ConfigGroup>(), ConfigsLoader.filter(groups, "tokyo"))
    }
}

package io.niko.ui.home

import io.niko.database.ProxyEntity
import io.niko.fmt.AbstractBean
import io.niko.GroupOrder
import io.niko.fmt.hysteria.HysteriaBean
import io.niko.fmt.internal.ChainBean
import io.niko.fmt.ssh.SSHBean
import io.niko.fmt.naive.NaiveBean
import io.niko.fmt.v2ray.VMessBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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

    @Test
    fun theSearchAlsoMatchesTheServersAddress() {
        val withAddresses = listOf(
            ConfigGroup(
                1, "own", subscription = false,
                listOf(
                    ConfigRow(11, "Home", listOf("VLESS"), null, unreachable = false, selected = false, address = "vpn.example.org:443"),
                    ConfigRow(12, "Work", listOf("SSH"), null, unreachable = false, selected = false, address = "192.0.2.10:22"),
                    ConfigRow(13, "Chain", listOf("CHAIN"), null, unreachable = false, selected = false),
                ),
            ),
        )
        assertEquals(listOf(11L), ConfigsLoader.filter(withAddresses, "EXAMPLE.org").single().profiles.map { it.id })
        assertEquals(listOf(12L), ConfigsLoader.filter(withAddresses, "192.0.2").single().profiles.map { it.id })
        assertEquals(emptyList<ConfigGroup>(), ConfigsLoader.filter(withAddresses, "198.51.100"))
    }

    @Test
    fun aRowKnowsItsServerAndAChainHasNone() {
        val ssh = entity(SSHBean().apply { name = "Work"; serverAddress = "192.0.2.10"; serverPort = 22 })
        val row = ConfigsLoader.row(ssh, 0, addresses = true)
        assertEquals("192.0.2.10:22", row.address)
        assertTrue(row.showAddress)
        // the setting off: searched by, not shown
        assertFalse(ConfigsLoader.row(ssh, 0).showAddress)
        // no name: the address is the name already
        val unnamed = entity(SSHBean().apply { serverAddress = "192.0.2.10"; serverPort = 22 })
        assertFalse(ConfigsLoader.row(unnamed, 0, addresses = true).showAddress)
        assertNull(ConfigsLoader.address(entity(ChainBean().apply { name = "chain" })))
    }

    @Test
    fun aRowsSecondLineSaysTheAddressAndTheTrafficWhenThereIsAny() {
        val bytes = { n: Long -> "$n B" }
        val row = ConfigRow(11, "Home", listOf("VLESS"), null, unreachable = false, selected = false, address = "192.0.2.10:443")
        assertNull(ConfigsLoader.detail(row, bytes))
        assertEquals("192.0.2.10:443", ConfigsLoader.detail(row.copy(showAddress = true), bytes))
        assertEquals("↑ 12 B ↓ 0 B", ConfigsLoader.detail(row.copy(tx = 12), bytes))
        assertEquals("192.0.2.10:443 · ↑ 12 B ↓ 300 B", ConfigsLoader.detail(row.copy(showAddress = true, tx = 12, rx = 300), bytes))
    }

    @Test
    fun aRunningTestCountsTheProfilesTestedOfAll() {
        val one = mapOf(11L to ConfigsTest.Result(38, unreachable = false))
        assertNull(ConfigsLoader.testProgress(one, 0))
        assertEquals(0 to 5, ConfigsLoader.testProgress(emptyMap(), 5))
        assertEquals(1 to 5, ConfigsLoader.testProgress(one, 5))
        // never more than all (results of a test before, not taken yet)
        assertEquals(1 to 1, ConfigsLoader.testProgress(one + (12L to ConfigsTest.Result(null, unreachable = true)), 1))
    }

    @Test
    fun aTestsResultsShowOverTheRowsRead() {
        val shown = ConfigsLoader.withResults(
            groups,
            mapOf(12L to ConfigsTest.Result(pingMs = 41, unreachable = false), 21L to ConfigsTest.Result(null, unreachable = true)),
        )
        assertEquals(38, shown[0].profiles[0].pingMs)
        assertEquals(41, shown[0].profiles[1].pingMs)
        assertEquals(null, shown[1].profiles[0].pingMs)
        assertEquals(true, shown[1].profiles[0].unreachable)
    }

    @Test
    fun aFailedTestKeepsWhatItSaid() {
        assertEquals("dial tcp 192.0.2.10:443: i/o timeout", ConfigsLoader.error(3, " dial tcp 192.0.2.10:443: i/o timeout\n"))
        assertEquals("Timeout", ConfigsLoader.error(2, "Timeout"))
        // passed or not tested: nothing to show, whatever is left in the store
        assertNull(ConfigsLoader.error(1, "old"))
        assertNull(ConfigsLoader.error(0, "old"))
        assertNull(ConfigsLoader.error(3, "  "))
        assertNull(ConfigsLoader.error(3, null))
        val failed = entity(NaiveBean().apply { name = "naive" }).apply { status = 3; error = "context deadline exceeded" }
        assertEquals("context deadline exceeded", ConfigsLoader.row(failed, 0).error)
        // a test running shows its error over the row read
        val shown = ConfigsLoader.withResults(groups, mapOf(11L to ConfigsTest.Result(null, unreachable = true, error = "refused")))
        assertEquals("refused", shown[0].profiles[0].error)
    }

    @Test
    fun aChainANodesRowAndAPluginsProfileAreNotShared() {
        val naive = entity(NaiveBean().apply { name = "naive" })
        assertTrue(ConfigsLoader.row(naive, 0).share)
        // salamander-device-v1 §6: a node's rows are this device's
        assertFalse(ConfigsLoader.row(naive, 0, nodeRow = true).share)
        assertFalse(ConfigsLoader.shared(entity(ChainBean().apply { name = "chain" }), nodeRow = false))
        assertEquals(emptyList<ShareAction>(), ConfigsLoader.shareMenu(ConfigsLoader.row(naive, 0, nodeRow = true)))
        assertFalse(ProfileAction.SHARE in ConfigsLoader.profileMenu(ConfigsLoader.row(naive, 0, nodeRow = true), up = false, down = false))
    }

    @Test
    fun theStandardLinkIsOfferedOnlyForTheKindsThatHaveOne() {
        val naive = ConfigsLoader.row(entity(NaiveBean().apply { name = "naive" }), 0)
        assertEquals(ShareAction.entries, ConfigsLoader.shareMenu(naive))
        val ssh = ConfigsLoader.row(entity(SSHBean().apply { name = "ssh" }), 0)
        assertEquals(
            listOf(ShareAction.QR_SN, ShareAction.LINK_SN, ShareAction.CONFIG_CLIPBOARD, ShareAction.CONFIG_FILE),
            ConfigsLoader.shareMenu(ssh),
        )
    }

    @Test
    fun theProfileTheServiceRunsIsNotDeleted() {
        val naive = entity(NaiveBean().apply { name = "naive" }).apply { id = 7 }
        val used = ConfigsLoader.row(naive, selected = 7, running = 7)
        assertTrue(used.inUse)
        assertEquals(listOf(ProfileAction.EDIT, ProfileAction.SHARE), ConfigsLoader.profileMenu(used, up = false, down = false))
        // chosen while the service runs another, or stopped: deleted as any
        val chosen = ConfigsLoader.row(naive, selected = 7, running = 0)
        assertFalse(chosen.inUse)
        assertEquals(
            listOf(ProfileAction.EDIT, ProfileAction.SHARE, ProfileAction.MOVE_UP, ProfileAction.MOVE_DOWN, ProfileAction.DELETE),
            ConfigsLoader.profileMenu(chosen, up = true, down = true),
        )
    }

    @Test
    fun aGroupsMenuHasNoSecondTestWhileOneRuns() {
        assertEquals(GroupAction.entries, ConfigsLoader.groupMenu(testing = false))
        assertEquals(
            listOf(GroupAction.CLEAR_RESULTS, GroupAction.DELETE_UNAVAILABLE, GroupAction.REMOVE_DUPLICATES, GroupAction.CLEAR_TRAFFIC),
            ConfigsLoader.groupMenu(testing = true),
        )
    }

    @Test
    fun aProfileMovesWithinItsGroupUnlessTheGroupIsSorted() {
        assertEquals(null to 12L, ConfigsLoader.neighbours(groups, 11))
        assertEquals(11L to null, ConfigsLoader.neighbours(groups, 12))
        // alone in its group: the next group's rows are not its neighbours
        assertEquals(null to null, ConfigsLoader.neighbours(groups, 21))
        val sorted = groups.map { it.copy(manual = false) }
        assertEquals(null to null, ConfigsLoader.neighbours(sorted, 11))
        assertEquals(null to null, ConfigsLoader.neighbours(groups, 99))
    }

    @Test
    fun theChosenProfilesPlaceInTheListCountsTheHeadsAndTheGaps() {
        // head, naive, Hysteria2 (chosen)
        assertEquals(2, ConfigsLoader.selectedIndex(groups))
        val second = groups.map { g -> g.copy(profiles = g.profiles.map { it.copy(selected = it.id == 21L) }) }
        // head, two rows, gap, head, Amsterdam
        assertEquals(5, ConfigsLoader.selectedIndex(second))
        assertEquals(-1, ConfigsLoader.selectedIndex(ConfigsLoader.filter(groups, "amsterdam")))
    }

    @Test
    fun aDeletedProfileLeavesTheListBeforeItsUndoIsOver() {
        assertEquals(groups, ConfigsLoader.without(groups, emptySet()))
        assertEquals(listOf(12L), ConfigsLoader.without(groups, setOf(11L))[0].profiles.map { it.id })
        // its group's last: the group goes with it
        assertEquals(listOf(1L), ConfigsLoader.without(groups, setOf(21L)).map { it.id })
    }

    @Test
    fun aGroupIsListedInItsOrder() {
        val b = entity(NaiveBean().apply { name = "b" }).apply { id = 1; status = 1; ping = 90 }
        val a = entity(NaiveBean().apply { name = "a" }).apply { id = 2 }
        val c = entity(NaiveBean().apply { name = "c" }).apply { id = 3; status = 1; ping = 30 }
        val stored = listOf(b, a, c)
        assertEquals(listOf(1L, 2L, 3L), ConfigsLoader.ordered(stored, GroupOrder.ORIGIN).map { it.id })
        assertEquals(listOf(2L, 1L, 3L), ConfigsLoader.ordered(stored, GroupOrder.BY_NAME).map { it.id })
        // the untested after the tested
        assertEquals(listOf(3L, 1L, 2L), ConfigsLoader.ordered(stored, GroupOrder.BY_DELAY).map { it.id })
    }

    @Test
    fun aTcpPingsFailureIsToldByTheSocketsMessage() {
        assertEquals(ConfigsTest.TcpFailure.TIMEOUT, ConfigsTest.tcpFailure("connect timed out"))
        assertEquals(
            ConfigsTest.TcpFailure.REFUSED,
            ConfigsTest.tcpFailure("failed to connect to /192.0.2.10 (port 443): connect failed: ECONNREFUSED (Connection refused)"),
        )
        assertEquals(ConfigsTest.TcpFailure.UNREACHABLE, ConfigsTest.tcpFailure("connect failed: ENETUNREACH (Network is unreachable)"))
        assertEquals(ConfigsTest.TcpFailure.OTHER, ConfigsTest.tcpFailure("connect failed: EACCES (Permission denied)"))
    }
}

package io.niko.ui.home

import io.niko.GroupType
import io.niko.database.ProxyGroup
import io.niko.database.SubscriptionBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The Subscriptions tab: what a row reads from its group, and what its menu offers. */
class SubsLoaderTest {

    private fun subscription(configure: SubscriptionBean.() -> Unit) = ProxyGroup(type = GroupType.SUBSCRIPTION).apply {
        name = "provider.net"
        subscription = SubscriptionBean().apply {
            initializeDefaultValues()
            configure()
        }
    }

    @Test
    fun theTrafficAndTheEndComeFromSubscriptionUserinfo() {
        val group = subscription {
            subscriptionUserinfo = "upload=1000; download=2000; total=10000; expire=1800000000"
            lastUpdated = 1_799_990_000
        }
        val row = SubsLoader.row(group, profiles = 2, node = null, updating = false)
        assertEquals(SubKind.SUBSCRIPTION, row.kind)
        assertEquals(3000L, row.used)
        assertEquals(10000L, row.total)
        assertEquals(1_800_000_000L, row.expire)
        assertEquals(1_799_990_000L, row.updatedAt)
        assertTrue(row.canUpdate)
        assertTrue(row.expired(1_800_000_000))
        assertFalse(row.expired(1_799_999_999))
    }

    @Test
    fun sip008GivesTheUsedAndWhatIsLeft() {
        val row = SubsLoader.row(subscription { bytesUsed = 300; bytesRemaining = 700 }, 1, null, false)
        assertEquals(300L, row.used)
        assertEquals(1000L, row.total)
    }

    @Test
    fun aBlankAnnounceIsNone() {
        assertEquals(null, SubsLoader.row(subscription { announce = "  " }, 1, null, false).announce)
        assertEquals("Maintenance at night", SubsLoader.row(subscription { announce = " Maintenance at night\n" }, 1, null, false).announce)
    }

    @Test
    fun anOwnGroupCanNotBeUpdated() {
        val row = SubsLoader.row(ProxyGroup(type = GroupType.BASIC).apply { name = "Свои" }, 4, null, false)
        assertEquals(SubKind.OWN, row.kind)
        assertFalse(row.canUpdate)
    }

    @Test
    fun theUngroupedOffersOnlyItsConfigs() {
        val row = SubRow(1, "Ungrouped", SubKind.UNGROUPED, profiles = 3)
        assertEquals(listOf(SubAction.EXPORT_CLIPBOARD, SubAction.EXPORT_FILE, SubAction.CLEAR), SubsLoader.menu(row))
        assertEquals(emptyList<SubAction>(), SubsLoader.menu(row.copy(profiles = 0)))
    }

    @Test
    fun aNodesMenuHasItsPanelAndItsNetworkWhenThere() {
        val row = SubRow(1, "arngory", SubKind.NODE, profiles = 3, nodeId = "n1", admin = true, lan = true)
        assertEquals(
            listOf(
                SubAction.SETTINGS, SubAction.ADMIN, SubAction.LAN, SubAction.ANOTHER_DEVICE, SubAction.CLEAR,
                SubAction.DELETE,
            ),
            SubsLoader.menu(row),
        )
        assertFalse(SubAction.ADMIN in SubsLoader.menu(row.copy(admin = false, lan = false)))
    }

    @Test
    fun aNodesRowsAreNotSharedOnlyItsWayToAnotherDevice() {
        // salamander-device-v1 §6: the rows are this device's, another device takes an invite of its own
        val node = SubsLoader.menu(SubRow(1, "arngory", SubKind.NODE, profiles = 3, nodeId = "n1"))
        for (action in listOf(SubAction.QR, SubAction.COPY_LINK, SubAction.EXPORT_CLIPBOARD, SubAction.EXPORT_FILE)) {
            assertFalse("$action", action in node)
        }
        assertTrue(SubAction.ANOTHER_DEVICE in node)
        val own = SubsLoader.menu(SubRow(2, "mine", SubKind.OWN, profiles = 3))
        assertTrue(SubAction.QR in own && SubAction.EXPORT_FILE in own)
        assertFalse(SubAction.ANOTHER_DEVICE in own)
    }

    @Test
    fun aGroupChangesPlacesWithTheOneNextToIt() {
        val a = SubRow(1, "a", SubKind.SUBSCRIPTION, profiles = 1)
        val b = SubRow(2, "b", SubKind.OWN, profiles = 1)
        val loose = SubRow(3, "Ungrouped", SubKind.UNGROUPED, profiles = 1)
        val rows = listOf(a, b, loose)
        assertEquals(null to b, SubsLoader.neighbours(rows, a))
        assertEquals(a to loose, SubsLoader.neighbours(rows, b))
        // as the old list's drag: the ungrouped and a group being fetched stay where they are
        assertEquals(null to null, SubsLoader.neighbours(rows, loose))
        assertEquals(null to null, SubsLoader.neighbours(rows, b.copy(updating = true)))
        val menu = SubsLoader.menu(b, up = true, down = false)
        assertTrue(SubAction.MOVE_UP in menu)
        assertFalse(SubAction.MOVE_DOWN in menu)
        assertFalse(SubAction.MOVE_UP in SubsLoader.menu(b))
    }
}

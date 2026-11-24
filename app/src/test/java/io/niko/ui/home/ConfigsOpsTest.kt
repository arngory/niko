package io.niko.ui.home

import io.niko.database.ProxyEntity
import io.niko.fmt.naive.NaiveBean
import org.junit.Assert.assertEquals
import org.junit.Test

/** The configs' sheet's menus: what a group's clean-up deletes, and the order a move writes. */
class ConfigsOpsTest {

    private fun entity(id: Long, name: String, host: String, port: Int = 443) = ProxyEntity().putBean(
        NaiveBean().apply {
            initializeDefaultValues()
            this.name = name
            serverAddress = host
            serverPort = port
        },
    ).also { it.id = id }

    @Test
    fun aDuplicateIsTheSameServerPortAndKindAgain() {
        val profiles = listOf(
            entity(1, "first", "a.example"),
            entity(2, "other port", "a.example", 8443),
            entity(3, "again", "a.example"),
            entity(4, "another", "b.example"),
            entity(5, "third time", "a.example"),
        )
        // the first stays, whatever the names
        assertEquals(listOf(3L, 5L), ConfigsOps.duplicates(profiles).map { it.id })
        assertEquals("again\nthird time", ConfigsOps.names(ConfigsOps.duplicates(profiles)))
    }

    @Test
    fun theQuestionListsTwentyNamesAndTrailsOff() {
        val many = (1..25L).map { entity(it, "n$it", "a.example") }
        val lines = ConfigsOps.names(many).lines()
        assertEquals(21, lines.size)
        assertEquals("n20", lines[19])
        assertEquals("......", lines[20])
        assertEquals(20, ConfigsOps.names(many.take(20)).lines().size)
    }

    @Test
    fun theUnavailableAreThoseWhoseTestFailed() {
        val profiles = listOf(
            entity(1, "untested", "a.example"),
            entity(2, "passed", "b.example").apply { status = 1; ping = 40 },
            entity(3, "no plugin", "c.example").apply { status = 2 },
            entity(4, "failed", "d.example").apply { status = 3 },
        )
        assertEquals(listOf(3L, 4L), ConfigsOps.unavailable(profiles).map { it.id })
    }

    @Test
    fun twoRowsTakeEachOthersOrder() {
        val orders = listOf(1L to 10L, 2L to 20L, 3L to 30L)
        assertEquals(mapOf(1L to 20L, 2L to 10L), ConfigsOps.swapped(orders, 1, 2))
        assertEquals(mapOf(2L to 30L, 3L to 20L), ConfigsOps.swapped(orders, 3, 2))
        // a row not listed between them (hidden in the sheet) keeps its place
        assertEquals(mapOf(1L to 30L, 3L to 10L), ConfigsOps.swapped(orders, 1, 3))
        assertEquals(emptyMap<Long, Long>(), ConfigsOps.swapped(orders, 1, 1))
        assertEquals(emptyMap<Long, Long>(), ConfigsOps.swapped(orders, 1, 9))
    }

    @Test
    fun ordersThatDoNotTellTheRowsApartAreNumberedAnew() {
        // all zero (an older import): a swap of equal orders would change nothing
        val orders = listOf(1L to 0L, 2L to 0L, 3L to 0L)
        assertEquals(mapOf(2L to 1L, 1L to 2L, 3L to 3L), ConfigsOps.swapped(orders, 1, 2))
    }
}

package io.niko.ui.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which files a restore reads, and which parts it offers: version 1 only, the parts the file holds. */
class BackupTest {

    @Test
    fun aVersionOneFileOffersTheParts() {
        val (_, parts) = Backup.parts("""{"version":1,"profiles":[],"groups":[],"settings":[]}""")!!
        assertEquals(setOf(BackupPart.PROFILES, BackupPart.SETTINGS), parts)
        assertEquals(BackupPart.entries.toSet(), Backup.parts("""{"version":1,"profiles":[],"groups":[],"rules":[],"settings":[]}""")!!.second)
        // a file without parts: read, nothing to restore (the screen says it is no backup)
        assertEquals(emptySet<BackupPart>(), Backup.parts("""{"version":1}""")!!.second)
    }

    @Test
    fun anotherVersionOrNoJsonIsRefused() {
        assertNull(Backup.parts("""{"version":2,"profiles":[]}"""))
        assertNull(Backup.parts("""{"profiles":[]}"""))
        assertNull(Backup.parts("not json"))
        assertNull(Backup.parts(""))
    }

    @Test
    fun theKeysAreTheOldScreens() {
        assertEquals(listOf("profiles", "rules", "settings"), BackupPart.entries.map(Backup::key))
    }
}

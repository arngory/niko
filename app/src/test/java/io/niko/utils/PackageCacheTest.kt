package io.niko.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PackageCacheTest {

    // the packages of a uid; an app in another user (work profile 10, a second space 999) by its app ID
    @Test
    fun packagesOfAnotherUsersUid() {
        val map = mapOf(10123 to setOf("org.example.app"), 1000 to setOf("android"))
        assertEquals(setOf("org.example.app"), PackageCache.packagesOf(map, 10123))
        assertEquals(setOf("org.example.app"), PackageCache.packagesOf(map, 1010123))
        assertEquals(setOf("org.example.app"), PackageCache.packagesOf(map, 99910123))
        assertNull(PackageCache.packagesOf(map, 10124))
        assertNull(PackageCache.packagesOf(map, 1010124))
    }
}

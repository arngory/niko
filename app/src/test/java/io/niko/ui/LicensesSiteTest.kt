package io.niko.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LicensesSiteTest {

    private val commit = "c1b117e5978a9e581c74ee3a4102d5d2448950c9"

    @Test
    fun aModuleIsItsPageOnPkgGoDev() {
        assertEquals(
            "https://pkg.go.dev/github.com/miekg/dns@v1.1.62",
            Licenses.site("github.com/miekg/dns", "v1.1.62", ""),
        )
    }

    @Test
    fun aModuleReplacedByAnotherIsTheOthersPage() {
        assertEquals(
            "https://pkg.go.dev/github.com/arngory/cronet-go@v0.0.0-20261116191843-4fe4c13fa9b2",
            Licenses.site("github.com/sagernet/cronet-go", "v0.0.0-x", "github.com/arngory/cronet-go@v0.0.0-20261116191843-4fe4c13fa9b2"),
        )
    }

    @Test
    fun aLocalReplaceIsItsRepositorysTreeAtTheCommit() {
        // licenses.py's git_source: no scheme, the commit, a folder of the checkout, +dirty for a changed one
        assertEquals(
            "https://github.com/arngory/sing-box/tree/$commit",
            Licenses.site("github.com/sagernet/sing-box", "v1.0.0", "github.com/arngory/sing-box@$commit"),
        )
        assertEquals(
            "https://github.com/arngory/sing-box/tree/$commit/third_party/sing-quic",
            Licenses.site("github.com/sagernet/sing-quic", "v0.0.0", "github.com/arngory/sing-box@$commit+dirty/third_party/sing-quic"),
        )
    }

    @Test
    fun aCheckoutWithoutARemoteHasNoPage() {
        assertNull(Licenses.site("github.com/sagernet/sing-box", "v1.0.0", "sing-box@$commit"))
    }

    @Test
    fun theToolchainIsGoDev() {
        assertEquals("https://go.dev/", Licenses.site("go", "go1.26.11", ""))
    }
}

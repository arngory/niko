package io.niko.utils

import io.niko.R
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class UpdatesTest {

    private val arm64 = "a".repeat(64)
    private val x86 = "B".repeat(64)
    private val sumsText = """
        $arm64  Niko-1.36.0-arm64-v8a.apk
        ${"c".repeat(64)}  Niko-1.36.0-armeabi-v7a.apk
        $x86 *Niko-1.36.0-x86_64.apk
        ${"d".repeat(64)}  ../Niko-1.36.0-x86.apk
        not a line
        ${"e".repeat(63)}  Niko-short.apk
    """.trimIndent().replace("\n", "\r\n")

    @Test
    fun sumsAsSha256sumWritesThem() {
        val sums = Updates.sums(sumsText)
        assertEquals(
            mapOf(
                "Niko-1.36.0-arm64-v8a.apk" to arm64,
                "Niko-1.36.0-armeabi-v7a.apk" to "c".repeat(64),
                // the binary marker, the hash in lower case
                "Niko-1.36.0-x86_64.apk" to "b".repeat(64),
            ),
            sums,
        )
    }

    @Test
    fun theApkOfTheFirstAbiListed() {
        val sums = Updates.sums(sumsText)
        assertEquals("Niko-1.36.0-arm64-v8a.apk" to arm64, Updates.apkFor(sums, listOf("arm64-v8a", "armeabi-v7a")))
        assertEquals("Niko-1.36.0-armeabi-v7a.apk", Updates.apkFor(sums, listOf("armeabi-v7a", "arm64-v8a"))?.first)
        assertEquals("Niko-1.36.0-x86_64.apk", Updates.apkFor(sums, listOf("mips", "x86_64"))?.first)
        assertNull(Updates.apkFor(sums, listOf("x86", "riscv64")))
        // another app's APK in the list is not ours
        assertNull(Updates.apkFor(mapOf("Other-1.0-arm64-v8a.apk" to arm64), listOf("arm64-v8a")))
    }

    @Test
    fun assetsOfThisAppsReleasesOnly() {
        fun asset(name: String, url: String) = JSONObject().put("name", name).put("browser_download_url", url)
        val release = JSONObject().put(
            "assets", JSONArray()
                .put(asset("SHA256SUMS", "https://github.com/arngory/niko/releases/download/v1.36.0/SHA256SUMS"))
                .put(asset("SHA256SUMS.minisig", "https://github.com/arngory/niko/releases/download/v1.36.0/SHA256SUMS.minisig"))
                .put(asset("Niko-1.36.0-arm64-v8a.apk", "https://github.com/arngory/niko/releases/download/v1.36.0/Niko-1.36.0-arm64-v8a.apk"))
                // another repository, another host, a query, plain HTTP: left out
                .put(asset("Niko-1.36.0-x86.apk", "https://github.com/someone/niko/releases/download/v1.36.0/Niko-1.36.0-x86.apk"))
                .put(asset("Niko-1.36.0-x86_64.apk", "https://example.com/arngory/niko/releases/download/v1.36.0/Niko-1.36.0-x86_64.apk"))
                .put(asset("Niko-1.36.0-armeabi-v7a.apk", "https://github.com/arngory/niko/releases/download/v1.36.0/x.apk?y=1"))
                .put(asset("plain", "http://github.com/arngory/niko/releases/download/v1.36.0/plain"))
        )
        assertEquals(
            setOf("SHA256SUMS", "SHA256SUMS.minisig", "Niko-1.36.0-arm64-v8a.apk"),
            Updates.assets(release).keys,
        )
        assertEquals(emptyMap<String, String>(), Updates.assets(JSONObject()))
    }

    @Test
    fun theSignatureForThisRelease() {
        val sums = sumsText.toByteArray()
        val listed = Updates.checkedSums("key", "v1.36.0", sums, "sig") { key, sig, data ->
            assertEquals("key", key)
            assertEquals("sig", sig)
            assertEquals(sumsText, String(data))
            "niko v1.36.0"
        }
        assertEquals(3, listed.size)
        fun refused(reason: Int, verify: (String, String, ByteArray) -> String, data: ByteArray = sums) {
            try {
                Updates.checkedSums("key", "v1.36.0", data, "sig", verify)
                fail("taken")
            } catch (e: Updates.Refused) {
                assertEquals(e.message, reason, e.reason)
            }
        }
        // signed, but for another release: a client is not sent back to an older one under a newer tag
        refused(R.string.update_other_release, { _, _, _ -> "niko v1.35.0" })
        refused(R.string.update_other_release, { _, _, _ -> "raycontrol v1.36.0" })
        refused(R.string.update_bad_signature, { _, _, _ -> throw Exception("minisign: the signature does not hold") })
        refused(R.string.update_no_apk, { _, _, _ -> "niko v1.36.0" }, "nothing\n".toByteArray())
    }
}
